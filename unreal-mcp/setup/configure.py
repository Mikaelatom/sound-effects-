"""Hook the Unreal connector up to Claude, and optionally to an Unreal project.

Run by SETUP-Windows.bat / SETUP-Mac.command; you shouldn't need to run it yourself.
It does three things:
  1. Adds the connector to Claude Desktop (and Claude Code, if installed).
  2. Optionally turns on Python + Remote Execution in your Unreal project.
  3. Checks whether an open Unreal Editor can be reached.
"""

from __future__ import annotations

import json
import os
import platform
import shutil
import subprocess
import sys
import time
from pathlib import Path

SERVER_NAME = "unreal"
PY_SECTION = "[/Script/PythonScriptPlugin.PythonScriptPluginSettings]"


def say(msg: str = "") -> None:
    print(msg, flush=True)


def backup(path: Path) -> None:
    if path.exists():
        stamp = time.strftime("%Y%m%d-%H%M%S")
        shutil.copy2(path, path.with_name(f"{path.name}.backup-{stamp}"))


# -- Claude ------------------------------------------------------------------


def desktop_config_paths() -> list[Path]:
    system = platform.system()
    home = Path.home()
    if system == "Windows":
        appdata = Path(os.environ.get("APPDATA") or home / "AppData" / "Roaming")
        local = Path(os.environ.get("LOCALAPPDATA") or home / "AppData" / "Local")
        dirs = [appdata / "Claude"]
        # Microsoft Store installs keep their config in a separate folder.
        dirs += [p / "LocalCache" / "Roaming" / "Claude" for p in (local / "Packages").glob("Claude_*")]
    elif system == "Darwin":
        dirs = [home / "Library" / "Application Support" / "Claude"]
    else:
        dirs = [Path(os.environ.get("XDG_CONFIG_HOME") or home / ".config") / "Claude"]
    existing = [d for d in dirs if d.is_dir()]
    return [d / "claude_desktop_config.json" for d in (existing or dirs[:1])]


def add_to_claude_desktop(exe: str) -> bool:
    ok = False
    for path in desktop_config_paths():
        try:
            config = json.loads(path.read_text(encoding="utf-8-sig")) if path.exists() else {}
        except json.JSONDecodeError:
            say(f"  ! {path} isn't valid JSON, so I left it alone. Fix it or delete it, then run setup again.")
            continue
        config.setdefault("mcpServers", {})[SERVER_NAME] = {"command": exe, "args": []}
        backup(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
        say(f"  ✓ Claude Desktop: added to {path}")
        ok = True
    return ok


def add_to_claude_code(exe: str) -> bool:
    claude = shutil.which("claude")
    if not claude:
        return False
    subprocess.run([claude, "mcp", "remove", "--scope", "user", SERVER_NAME], capture_output=True)
    res = subprocess.run(
        [claude, "mcp", "add", "--scope", "user", SERVER_NAME, "--", exe], capture_output=True, text=True
    )
    if res.returncode == 0:
        say("  ✓ Claude Code: added (available in every folder)")
        return True
    say(f"  ! Claude Code: couldn't add it automatically:\n    {res.stderr.strip()}")
    return False


# -- Unreal project ----------------------------------------------------------


def enable_python_plugin(uproject: Path) -> None:
    data = json.loads(uproject.read_text(encoding="utf-8-sig"))
    plugins = data.setdefault("Plugins", [])
    for plugin in plugins:
        if plugin.get("Name") == "PythonScriptPlugin":
            plugin["Enabled"] = True
            break
    else:
        plugins.append({"Name": "PythonScriptPlugin", "Enabled": True})
    backup(uproject)
    uproject.write_text(json.dumps(data, indent="\t") + "\n", encoding="utf-8")


def enable_remote_execution(ini: Path) -> None:
    lines = ini.read_text(encoding="utf-8-sig").splitlines() if ini.exists() else []
    out, in_section, found_section, wrote = [], False, False, False
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("["):
            if in_section and not wrote:
                out.append("bRemoteExecution=True")
                wrote = True
            in_section = stripped == PY_SECTION
            found_section = found_section or in_section
        elif in_section and stripped.split("=")[0].strip() == "bRemoteExecution":
            if not wrote:
                out.append("bRemoteExecution=True")
                wrote = True
            continue
        out.append(line)
    if in_section and not wrote:
        out.append("bRemoteExecution=True")
    if not found_section:
        out += ["", PY_SECTION, "bRemoteExecution=True"]
    backup(ini)
    ini.parent.mkdir(parents=True, exist_ok=True)
    ini.write_text("\n".join(out) + "\n", encoding="utf-8")


def setup_unreal_project(raw: str) -> None:
    path = Path(raw.strip().strip('"').strip("'")).expanduser()
    if path.is_dir():
        found = list(path.glob("*.uproject"))
        path = found[0] if found else path
    if path.suffix.lower() != ".uproject" or not path.is_file():
        say(f"  ! Couldn't find a .uproject file at {path}. Skipping this step.")
        say("    You can switch it on by hand instead. See START-HERE.md, step 3.")
        return
    enable_python_plugin(path)
    enable_remote_execution(path.parent / "Config" / "DefaultEngine.ini")
    say(f"  ✓ Turned on Python + Remote Execution for {path.stem}")
    say("    (If that project is open in Unreal right now, close and reopen it.)")


# -- check -------------------------------------------------------------------


def check_unreal() -> None:
    try:
        from unreal_mcp.remote_execution import UnrealRemote
    except ImportError:
        return
    remote = UnrealRemote()
    try:
        nodes = remote.discover(timeout=2.0)
    except OSError as exc:
        say(f"  ? Couldn't search for Unreal ({exc}).")
        return
    finally:
        remote.close()
    if nodes:
        for node in nodes:
            say(f"  ✓ Found Unreal Editor with project '{node.project_name}'. It's ready.")
    else:
        say("  - No open Unreal Editor found right now. That's fine if it's closed.")
        say("    Open your project in Unreal before you ask Claude to use it.")


def main() -> int:
    exe = sys.argv[1]
    project_arg = sys.argv[2] if len(sys.argv) > 2 else ""

    say("\nStep 1 of 3: connecting to Claude")
    desktop = add_to_claude_desktop(exe)
    code = add_to_claude_code(exe)
    if not (desktop or code):
        say("  ! Couldn't add it to Claude. Is Claude Desktop installed?")

    say("\nStep 2 of 3: setting up your Unreal project")
    if not project_arg:
        say("  Drag your project's .uproject file into this window and press Enter.")
        say("  (Or just press Enter to skip if you already turned on Remote Execution.)")
        try:
            project_arg = input("  > ")
        except EOFError:
            project_arg = ""
    if project_arg.strip():
        setup_unreal_project(project_arg)
    else:
        say("  - Skipped.")

    say("\nStep 3 of 3: looking for Unreal")
    check_unreal()

    say("\nAll done!")
    say("  → Fully quit Claude Desktop (also from the system tray / menu bar), then open it again.")
    say("  → Open your project in Unreal.")
    say("  → Ask Claude: \"What's in my Unreal level?\"")
    return 0


if __name__ == "__main__":
    sys.exit(main())
