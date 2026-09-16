# Claude Voice

Reads Claude Cowork's replies out loud. Open it and it talks — that's the whole thing.

## Use it

**Mac:** double-click **`Claude Voice.command`**
**Windows:** double-click **`Claude Voice.bat`**
**Or from a terminal:** `python3 speak.py`

A small window opens and stays there. Anything Claude says from that moment on
gets read aloud. Close the window to stop.

## That's it

- No setup, no install, no API key, no settings file.
- It doesn't change anything about Claude — it just listens in on the
  conversation files Claude already writes to `~/.claude/projects`.
- It uses the voice already built into your computer.
- Start it before, during, or after a conversation; it reads what comes next,
  not your old history.

Linux only: if it says no voice is installed, run `sudo apt install espeak-ng` once.

## If you want to tweak it

Everything is in `speak.py`, about 100 lines:

- **Speed** — the `-r 190` in `speak()` (higher is faster).
- **Voice** — on Mac, add a name: `["say", "-v", "Samantha", ...]`. Run
  `say -v '?'` to see the list.
- **Length** — `if len(text) > 600` decides how much of a long answer it reads.
