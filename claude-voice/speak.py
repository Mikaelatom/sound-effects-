#!/usr/bin/env python3
"""Claude Voice - open it, and it reads Claude Cowork's replies out loud.

No setup, no hooks, no settings. It watches the folder where Claude keeps its
conversations and speaks each new reply as it arrives, using the voice already
built into your computer.

Close the window (or press Ctrl+C) to stop.
"""

import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

# Where Claude Cowork / Claude Code store their conversations.
WATCH_DIRS = [
    Path.home() / ".claude" / "projects",
    Path.home() / ".config" / "claude" / "projects",
    Path.home() / "Library" / "Application Support" / "Claude" / "projects",
]

CHECK_EVERY = 0.7  # seconds


# --------------------------------------------------------------- saying things out loud

def speak(text):
    """Say it with whatever voice this computer already has."""
    if sys.platform == "darwin":
        subprocess.run(["say", "-r", "190", text], check=False)
        return True

    if os.name == "nt":
        powershell = shutil.which("powershell") or shutil.which("pwsh")
        subprocess.run([powershell, "-NoProfile", "-Command",
                        "Add-Type -AssemblyName System.Speech;"
                        "(New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak("
                        f"'{text.replace(chr(39), chr(39) * 2)}')"], check=False)
        return True

    espeak = shutil.which("espeak-ng") or shutil.which("espeak")
    if espeak:
        subprocess.run([espeak, "-s", "175", text], check=False)
        return True

    if shutil.which("spd-say"):
        subprocess.run(["spd-say", "-w", text], check=False)
        return True

    return False


def check_voice():
    if sys.platform == "darwin" or os.name == "nt":
        return True
    if shutil.which("espeak-ng") or shutil.which("espeak") or shutil.which("spd-say"):
        return True
    print("No voice installed. On Linux, run:  sudo apt install espeak-ng")
    return False


# ------------------------------------------------------- making the text sound like speech

def for_speech(text):
    text = re.sub(r"```.*?```", " code block. ", text, flags=re.S)   # code
    text = re.sub(r"`([^`\n]+)`", r"\1", text)                       # `inline code`
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)             # [links](url)
    text = re.sub(r"https?://\S+", " link ", text)                   # bare urls
    text = re.sub(r"(?m)^\s*#{1,6}\s*", "", text)                    # # headings
    text = re.sub(r"(?m)^\s*(?:[-*+]|\d+[.)])\s+", "", text)         # bullets
    text = re.sub(r"(?m)^\s*\|.*\|\s*$", "", text)                   # table rows
    text = re.sub(r"(\*{1,3}|_{2,3}|~~)(\S.*?\S|\S)\1", r"\2", text)  # **bold** etc
    text = re.sub(r"[\U0001f300-\U0001fAFF\U00002600-\U000027bf️]", " ", text)  # emoji
    text = re.sub(r"\s+", " ", text).strip()

    if len(text) > 600:  # don't monologue - stop at a sentence
        cut = text[:600].rsplit(". ", 1)[0]
        text = (cut if len(cut) > 200 else text[:600]) + "..."
    return text


def new_replies(path, offset):
    """Read whatever was added to a conversation file, return Claude's replies."""
    replies = []
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        f.seek(offset)
        for line in f:
            if not line.endswith("\n"):     # still being written - pick it up next time
                break
            offset += len(line.encode("utf-8"))
            try:
                entry = json.loads(line)
            except ValueError:
                continue
            if entry.get("type") != "assistant" or entry.get("isSidechain"):
                continue
            content = (entry.get("message") or {}).get("content")
            if isinstance(content, str):
                blocks = [content]
            elif isinstance(content, list):
                blocks = [b.get("text", "") for b in content
                          if isinstance(b, dict) and b.get("type") == "text"]
            else:
                continue
            said = for_speech("\n".join(b for b in blocks if b))
            if len(said) > 1:
                replies.append(said)
    return replies, offset


# ------------------------------------------------------------------------------- the loop

def conversation_files():
    for folder in WATCH_DIRS:
        if folder.is_dir():
            yield from folder.glob("**/*.jsonl")


def main():
    print("Claude Voice")
    print("Listening to Claude Cowork. Anything Claude says, I'll read out loud.")
    print("Close this window to stop.\n")

    if not check_voice():
        input("\nPress Enter to close.")
        return 1

    # Start from right now, so it doesn't read your whole history back to you.
    seen = {f: f.stat().st_size for f in conversation_files()}
    if not seen:
        print("Waiting for a conversation to start...\n")

    while True:
        for path in conversation_files():
            try:
                size = path.stat().st_size
            except OSError:
                continue
            if path not in seen:          # a brand new conversation
                seen[path] = 0
            if size <= seen[path]:
                seen[path] = min(seen[path], size)   # file was reset
                continue
            try:
                replies, seen[path] = new_replies(path, seen[path])
            except OSError:
                continue
            for reply in replies:
                print(f"  \N{SPEAKER WITH THREE SOUND WAVES} {reply[:90]}"
                      f"{'...' if len(reply) > 90 else ''}")
                speak(reply)
        time.sleep(CHECK_EVERY)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\nStopped.")
