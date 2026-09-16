#!/usr/bin/env python3
"""Claude Voice - speaks Claude's replies out loud.

Wired into Claude Cowork / Claude Code through hooks (see .claude/settings.json).
On every Stop event this reads the last assistant message out of the session
transcript, cleans it up for speech, and hands it to a text-to-speech engine.

Usage:
    voice.py hook              read a hook payload on stdin (used by the hooks)
    voice.py say "some text"   speak text directly
    voice.py test              speak a test line and report what engine was used
    voice.py doctor            show config + which engines are available
    voice.py on | off          enable / disable speech (survives restarts)
    voice.py stop              stop whatever is being spoken right now
    voice.py install           add the hooks to .claude/settings.json

Nothing here is allowed to break a session: the hook path always exits 0.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
STATE_DIR = Path(os.environ.get("CLAUDE_VOICE_STATE", Path.home() / ".claude" / "voice-state"))
PID_FILE = STATE_DIR / "current.pid"
DISABLED_FLAG = STATE_DIR / "disabled"

DEFAULT_CONFIG = {
    "enabled": True,
    # auto | say | espeak | spd-say | powershell | elevenlabs | openai
    "engine": "auto",
    "voice": "",
    "rate": 185,
    "volume": 1.0,
    "max_chars": 700,
    "min_chars": 2,
    "interrupt_previous": True,
    "speak_notifications": True,
    "elevenlabs": {
        "api_key_env": "ELEVENLABS_API_KEY",
        "voice_id": "21m00Tcm4TlvDq8ikWAM",
        "model_id": "eleven_turbo_v2_5",
    },
    "openai": {
        "api_key_env": "OPENAI_API_KEY",
        "model": "gpt-4o-mini-tts",
        "voice": "alloy",
    },
}

LOCAL_ENGINES = ("say", "espeak", "spd-say", "powershell")
CLOUD_ENGINES = ("elevenlabs", "openai")


# --------------------------------------------------------------------------- config


def config_paths() -> list[Path]:
    """Lowest priority first."""
    paths = [HERE / "voice.config.json", Path.home() / ".claude" / "voice.config.json"]
    override = os.environ.get("CLAUDE_VOICE_CONFIG")
    if override:
        paths.append(Path(override))
    return paths


def load_config() -> dict:
    cfg = json.loads(json.dumps(DEFAULT_CONFIG))  # deep copy
    for path in config_paths():
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            continue
        for key, value in data.items():
            if isinstance(value, dict) and isinstance(cfg.get(key), dict):
                cfg[key].update(value)
            else:
                cfg[key] = value
    if os.environ.get("CLAUDE_VOICE_ENGINE"):
        cfg["engine"] = os.environ["CLAUDE_VOICE_ENGINE"]
    if os.environ.get("CLAUDE_VOICE_VOICE"):
        cfg["voice"] = os.environ["CLAUDE_VOICE_VOICE"]
    return cfg


def speech_enabled(cfg: dict) -> bool:
    if os.environ.get("CLAUDE_VOICE_DISABLED", "").strip() not in ("", "0", "false"):
        return False
    if DISABLED_FLAG.exists():
        return False
    return bool(cfg.get("enabled", True))


# ----------------------------------------------------------------------- transcript


def last_assistant_text(transcript_path: str) -> str:
    """Pull the text of the most recent assistant turn out of a JSONL transcript."""
    try:
        lines = Path(transcript_path).read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return ""

    for line in reversed(lines):
        line = line.strip()
        if not line:
            continue
        try:
            entry = json.loads(line)
        except ValueError:
            continue
        if entry.get("type") != "assistant":
            continue
        if entry.get("isMeta") or entry.get("isSidechain"):
            continue

        content = (entry.get("message") or {}).get("content")
        if isinstance(content, str):
            text = content
        elif isinstance(content, list):
            parts = [
                block.get("text", "")
                for block in content
                if isinstance(block, dict) and block.get("type") == "text"
            ]
            text = "\n".join(p for p in parts if p)
        else:
            continue

        if text.strip():
            return text
    return ""


# -------------------------------------------------------------------- text cleaning

FENCE_RE = re.compile(r"```.*?```", re.S)
INDENT_BLOCK_RE = re.compile(r"(?m)^(?: {4,}|\t)\S.*$")
IMAGE_RE = re.compile(r"!\[([^\]]*)\]\([^)]*\)")
LINK_RE = re.compile(r"\[([^\]]+)\]\([^)]*\)")
BARE_URL_RE = re.compile(r"https?://\S+|www\.\S+")
# file-ish tokens (src/app/main.py, ./a/b, ~/notes.md) - spoken as just the file name
PATH_RE = re.compile(r"(?<![\w:/])(?:~|\.{1,2})?/?[\w.\-]+(?:/[\w.\-]+)+/?")
HEADER_RE = re.compile(r"(?m)^\s{0,3}#{1,6}\s*")
BULLET_RE = re.compile(r"(?m)^\s*(?:[-*+]|\d+[.)])\s+")
QUOTE_RE = re.compile(r"(?m)^\s*>\s?")
RULE_RE = re.compile(r"(?m)^\s*(?:[-*_]\s*){3,}$")
EMPHASIS_RE = re.compile(r"(\*{1,3}|_{1,3}|~{2})(\S.*?\S|\S)\1", re.S)
INLINE_CODE_RE = re.compile(r"`([^`\n]+)`")
TABLE_ROW_RE = re.compile(r"(?m)^\s*\|.*\|\s*$")
HTML_TAG_RE = re.compile(r"<[^>\n]{1,200}>")
EMOJI_RE = re.compile(
    "[\U0001f300-\U0001fAFF\U00002600-\U000027bf\U0001f1e6-\U0001f1ff⬀-⯿️]"
)
SENTENCE_END_RE = re.compile(r"(?<=[.!?])\s")


def _shorten_path(match: re.Match) -> str:
    """Say "main.py" instead of spelling out "src/app/main.py"."""
    token = match.group(0)
    if "." not in token and not token.startswith(("/", "./", "../", "~/")):
        return token  # probably prose like "and/or", leave it alone
    return token.rstrip("/").split("/")[-1] or " "


def clean_for_speech(text: str, max_chars: int) -> str:
    """Turn a markdown answer into something that sounds sane when spoken."""
    text = FENCE_RE.sub(" (code block) ", text)
    text = TABLE_ROW_RE.sub(" ", text)
    text = INDENT_BLOCK_RE.sub(" ", text)
    text = IMAGE_RE.sub(r"\1", text)
    text = LINK_RE.sub(r"\1", text)
    text = BARE_URL_RE.sub(" link ", text)
    text = RULE_RE.sub(" ", text)
    text = HEADER_RE.sub("", text)
    text = QUOTE_RE.sub("", text)
    text = BULLET_RE.sub("", text)
    text = INLINE_CODE_RE.sub(r"\1", text)
    text = EMPHASIS_RE.sub(r"\2", text)
    text = HTML_TAG_RE.sub(" ", text)
    text = PATH_RE.sub(_shorten_path, text)
    text = EMOJI_RE.sub(" ", text)
    text = text.replace("&nbsp;", " ").replace("&amp;", "and")

    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{2,}", "\n", text)
    text = "\n".join(line.strip() for line in text.splitlines() if line.strip())
    text = text.strip()

    if max_chars and len(text) > max_chars:
        head = text[:max_chars]
        pieces = SENTENCE_END_RE.split(head)
        if len(pieces) > 1:
            head = " ".join(pieces[:-1])
        text = head.rstrip() + "..."
    return text


# ---------------------------------------------------------------------- tts engines


def available_engines() -> list[str]:
    found = []
    if sys.platform == "darwin" and shutil.which("say"):
        found.append("say")
    if shutil.which("espeak-ng") or shutil.which("espeak"):
        found.append("espeak")
    if shutil.which("spd-say"):
        found.append("spd-say")
    if os.name == "nt" and (shutil.which("powershell") or shutil.which("pwsh")):
        found.append("powershell")
    for name in CLOUD_ENGINES:
        key_env = DEFAULT_CONFIG[name]["api_key_env"]
        if os.environ.get(key_env):
            found.append(name)
    return found


def resolve_engine(cfg: dict) -> str:
    engine = (cfg.get("engine") or "auto").strip()
    if engine != "auto":
        return engine
    have = available_engines()
    for preferred in ("say", "elevenlabs", "openai", "espeak", "spd-say", "powershell"):
        if preferred in have:
            return preferred
    return ""


def player_command(path: str) -> list[str] | None:
    for player, args in (
        ("afplay", [path]),
        ("mpv", ["--no-video", "--really-quiet", path]),
        ("ffplay", ["-nodisp", "-autoexit", "-loglevel", "quiet", path]),
        ("mpg123", ["-q", path]),
        ("cvlc", ["--play-and-exit", "--intf", "dummy", path]),
    ):
        if shutil.which(player):
            return [player, *args]
    return None


def http_post(url: str, headers: dict, payload: dict, timeout: int = 60) -> bytes:
    import urllib.error
    import urllib.request

    request = urllib.request.Request(
        url, data=json.dumps(payload).encode("utf-8"), headers=headers, method="POST"
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.read()
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f"{url} returned {exc.code}: {exc.read()[:200]!r}") from exc


def speak_cloud(engine: str, text: str, cfg: dict) -> None:
    settings = cfg.get(engine, {})
    api_key = os.environ.get(settings.get("api_key_env", ""), "")
    if not api_key:
        raise RuntimeError(f"{engine}: ${settings.get('api_key_env')} is not set")

    if engine == "elevenlabs":
        voice_id = cfg.get("voice") or settings.get("voice_id")
        audio = http_post(
            f"https://api.elevenlabs.io/v1/text-to-speech/{voice_id}",
            {"xi-api-key": api_key, "Content-Type": "application/json", "Accept": "audio/mpeg"},
            {"text": text, "model_id": settings.get("model_id", "eleven_turbo_v2_5")},
        )
    else:
        audio = http_post(
            "https://api.openai.com/v1/audio/speech",
            {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
            {
                "model": settings.get("model", "gpt-4o-mini-tts"),
                "voice": cfg.get("voice") or settings.get("voice", "alloy"),
                "input": text,
                "response_format": "mp3",
            },
        )

    with tempfile.NamedTemporaryFile(prefix="claude-voice-", suffix=".mp3", delete=False) as handle:
        handle.write(audio)
        audio_path = handle.name
    try:
        command = player_command(audio_path)
        if not command:
            raise RuntimeError("no audio player found (install mpv, ffplay or mpg123)")
        subprocess.run(command, check=False)
    finally:
        try:
            os.unlink(audio_path)
        except OSError:
            pass


def speak_local(engine: str, text: str, cfg: dict) -> None:
    voice = cfg.get("voice") or ""
    rate = int(cfg.get("rate", 185))

    if engine == "say":
        command = ["say", "-r", str(rate)]
        if voice:
            command += ["-v", voice]
        subprocess.run([*command, text], check=False)

    elif engine == "espeak":
        binary = shutil.which("espeak-ng") or shutil.which("espeak")
        if not binary:
            raise RuntimeError("espeak-ng is not installed")
        command = [binary, "-s", str(rate), "-a", str(int(float(cfg.get("volume", 1.0)) * 100))]
        command += ["-v", voice or "en-us"]
        subprocess.run([*command, text], check=False)

    elif engine == "spd-say":
        # spd-say wants -100..100, where 0 is roughly 175 words per minute.
        relative = max(-100, min(100, int((rate - 175) / 2)))
        command = ["spd-say", "-w", "-r", str(relative)]
        if voice:
            command += ["-y", voice]
        subprocess.run([*command, text], check=False)

    elif engine == "powershell":
        binary = shutil.which("pwsh") or shutil.which("powershell")
        if not binary:
            raise RuntimeError("powershell is not available")
        escaped = text.replace("'", "''")
        script = (
            "Add-Type -AssemblyName System.Speech;"
            "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer;"
            f"$s.Rate = {max(-10, min(10, int((rate - 185) / 20)))};"
            f"$s.Volume = {max(0, min(100, int(float(cfg.get('volume', 1.0)) * 100)))};"
        )
        if voice:
            script += f"$s.SelectVoice('{voice}');"
        script += f"$s.Speak('{escaped}')"
        subprocess.run([binary, "-NoProfile", "-Command", script], check=False)

    else:
        raise RuntimeError(f"unknown engine {engine!r}")


def speak_now(text: str, cfg: dict) -> str:
    """Speak in this process, blocking until done. Returns the engine used."""
    engine = resolve_engine(cfg)
    if not engine:
        raise RuntimeError(
            "no speech engine found - install espeak-ng, or set ELEVENLABS_API_KEY / OPENAI_API_KEY"
        )
    if engine in CLOUD_ENGINES:
        speak_cloud(engine, text, cfg)
    else:
        speak_local(engine, text, cfg)
    return engine


# ------------------------------------------------------------- background speech job


def stop_current_speech() -> bool:
    try:
        pid = int(PID_FILE.read_text().strip())
    except (OSError, ValueError):
        return False
    stopped = False
    for killer in (os.killpg, os.kill):
        try:
            killer(pid, signal.SIGTERM)
            stopped = True
            break
        except (ProcessLookupError, PermissionError, OSError):
            continue
    try:
        PID_FILE.unlink()
    except OSError:
        pass
    return stopped


def speak_detached(text: str, cfg: dict) -> None:
    """Hand the text to a background worker so the session is never held up."""
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    if cfg.get("interrupt_previous", True):
        stop_current_speech()

    log = open(STATE_DIR / "worker.log", "ab", buffering=0)  # noqa: SIM115
    process = subprocess.Popen(
        [sys.executable, str(Path(__file__).resolve()), "_worker"],
        stdin=subprocess.PIPE,
        stdout=log,
        stderr=log,
        start_new_session=True,
    )
    PID_FILE.write_text(str(process.pid))
    assert process.stdin is not None
    process.stdin.write(text.encode("utf-8"))
    process.stdin.close()


def run_worker() -> int:
    text = sys.stdin.read()
    if not text.strip():
        return 0
    try:
        speak_now(text, load_config())
    except Exception as exc:  # noqa: BLE001 - worker must never explode loudly
        sys.stderr.write(f"claude-voice: {exc}\n")
        return 1
    finally:
        try:
            if PID_FILE.exists() and PID_FILE.read_text().strip() == str(os.getpid()):
                PID_FILE.unlink()
        except OSError:
            pass
    return 0


# -------------------------------------------------------------------- hook handling


def already_spoken(session_id: str, text: str) -> bool:
    """Stop can fire more than once per turn - only say each answer once."""
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
    marker = STATE_DIR / f"last-{re.sub(r'[^A-Za-z0-9_-]', '', session_id) or 'default'}"
    try:
        if marker.read_text().strip() == digest:
            return True
    except OSError:
        pass
    try:
        marker.write_text(digest)
    except OSError:
        pass
    return False


def handle_hook() -> int:
    raw = sys.stdin.read()
    try:
        payload = json.loads(raw) if raw.strip() else {}
    except ValueError:
        payload = {}

    cfg = load_config()
    if not speech_enabled(cfg):
        return 0

    event = payload.get("hook_event_name", "Stop")
    if event == "Notification":
        if not cfg.get("speak_notifications", True):
            return 0
        text = str(payload.get("message", "")).strip()
    elif event == "SessionEnd":
        stop_current_speech()
        return 0
    else:
        if payload.get("stop_hook_active"):
            return 0
        text = last_assistant_text(payload.get("transcript_path", ""))

    text = clean_for_speech(text, int(cfg.get("max_chars", 700)))
    if len(text) < int(cfg.get("min_chars", 2)):
        return 0
    if event != "Notification" and already_spoken(str(payload.get("session_id", "")), text):
        return 0

    speak_detached(text, cfg)
    return 0


# ------------------------------------------------------------------------- installer

HOOK_MARKER = "claude-voice"


def hook_entry(script: str) -> dict:
    return {"hooks": [{"type": "command", "command": f'"{script}" hook', "timeout": 10}]}


def hook_command_path(settings_path: Path) -> str:
    """Prefer a $CLAUDE_PROJECT_DIR-relative path so the hook survives a clone."""
    script = (HERE / "voice.py").resolve()
    project_dir = settings_path.resolve().parent.parent
    try:
        return f"$CLAUDE_PROJECT_DIR/{script.relative_to(project_dir)}"
    except ValueError:
        return str(script)


def install_hooks(settings_path: Path) -> None:
    script = hook_command_path(settings_path)
    settings: dict = {}
    if settings_path.exists():
        try:
            settings = json.loads(settings_path.read_text(encoding="utf-8"))
        except ValueError:
            print(f"{settings_path} is not valid JSON - fix it first", file=sys.stderr)
            raise SystemExit(1)

    hooks = settings.setdefault("hooks", {})
    for event in ("Stop", "Notification", "SessionEnd"):
        existing = [
            group
            for group in hooks.get(event, [])
            if HOOK_MARKER not in json.dumps(group)
        ]
        existing.append(hook_entry(script))
        hooks[event] = existing

    settings_path.parent.mkdir(parents=True, exist_ok=True)
    settings_path.write_text(json.dumps(settings, indent=2) + "\n", encoding="utf-8")
    print(f"Hooks installed in {settings_path}")
    print("Start a new Claude session (or run /hooks) to pick them up.")


# ------------------------------------------------------------------------------ cli


def cmd_doctor() -> int:
    cfg = load_config()
    print("Claude Voice")
    print(f"  enabled        : {speech_enabled(cfg)}")
    print(f"  engine (config): {cfg.get('engine')}")
    print(f"  engine (in use): {resolve_engine(cfg) or 'NONE FOUND'}")
    print(f"  voice          : {cfg.get('voice') or '(engine default)'}")
    print(f"  rate           : {cfg.get('rate')}")
    print(f"  max chars      : {cfg.get('max_chars')}")
    print(f"  available      : {', '.join(available_engines()) or 'none'}")
    print(f"  audio player   : {(player_command('x') or ['none'])[0]}")
    print(f"  state dir      : {STATE_DIR}")
    for path in config_paths():
        print(f"  config         : {path} {'(found)' if path.exists() else '(absent)'}")
    return 0


def main(argv: list[str]) -> int:
    command = argv[0] if argv else "hook"

    if command == "hook":
        try:
            return handle_hook()
        except Exception as exc:  # noqa: BLE001 - a hook must never fail the session
            sys.stderr.write(f"claude-voice: {exc}\n")
            return 0

    if command == "_worker":
        return run_worker()

    if command == "say":
        text = " ".join(argv[1:]) or sys.stdin.read()
        cfg = load_config()
        text = clean_for_speech(text, int(cfg.get("max_chars", 700)))
        if not text:
            return 0
        speak_now(text, cfg)
        return 0

    if command == "test":
        cfg = load_config()
        engine = speak_now("Claude Voice is working. I will read replies out loud.", cfg)
        print(f"Spoke using: {engine}")
        return 0

    if command == "stop":
        print("Stopped." if stop_current_speech() else "Nothing was being spoken.")
        return 0

    if command in ("on", "off"):
        STATE_DIR.mkdir(parents=True, exist_ok=True)
        if command == "off":
            DISABLED_FLAG.write_text("off")
            stop_current_speech()
            print("Voice off.")
        else:
            DISABLED_FLAG.unlink(missing_ok=True)
            print("Voice on.")
        return 0

    if command == "doctor":
        return cmd_doctor()

    if command == "install":
        target = Path(argv[1]) if len(argv) > 1 else HERE.parent / ".claude" / "settings.json"
        install_hooks(target)
        return 0

    print(__doc__)
    return 0 if command in ("-h", "--help", "help") else 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
