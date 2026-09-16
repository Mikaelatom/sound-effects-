---
description: Control Claude Voice (on, off, stop, test, doctor)
allowed-tools: Bash(python3 "$CLAUDE_PROJECT_DIR/claude-voice/voice.py":*)
argument-hint: "[on|off|stop|test|doctor]"
---

Run `python3 "$CLAUDE_PROJECT_DIR/claude-voice/voice.py" $1` (use `doctor` when no
argument was given) and report its output back in one short line.
