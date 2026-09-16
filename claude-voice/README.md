# Claude Voice

Reads Claude Cowork's replies out loud. Open it and it talks.

## Windows — nothing to install

Put **`Claude Voice.bat`** and **`speak.ps1`** in the same folder, then
double-click **`Claude Voice.bat`**.

It uses the voice already built into Windows. No Python, no install, no setup.

## Mac

Put **`Claude Voice.command`** and **`speak.py`** in the same folder. In Terminal, once:

```
chmod +x "Claude Voice.command"
```

Then double-click it any time. (Or just run `python3 speak.py` — Macs already have Python.)

## That's it

A window opens and stays there. Anything Claude says from that moment on gets
read aloud. Close the window to stop.

- It changes nothing about Claude. It only reads the conversation files Claude
  already writes to `.claude\projects` in your user folder.
- Start it before, during or after a conversation — it reads what comes next,
  never your old history.
- It reads Claude Cowork **on this computer**. If you're chatting with Claude in
  a web browser there's no local file to watch, and it'll just sit there quietly.

## Tweaks

Windows — in `speak.ps1`, inside `New-SpeechVoice`:

- **Speed** — `$voice.Rate = 1` (from `-10` slow to `10` fast)
- **Volume** — `$voice.Volume = 100`
- **Voice** — add `$voice.SelectVoice('Microsoft Zira Desktop')`

Mac — in `speak.py`, inside `speak()`:

- **Speed** — the `-r 190` (higher is faster)
- **Voice** — add a name: `["say", "-v", "Samantha", ...]`, see `say -v '?'`

Either one — `600` is how many characters of a long answer it reads before stopping.
