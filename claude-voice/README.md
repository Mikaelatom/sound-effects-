# Claude Voice

Gives Claude Cowork / Claude Code a voice. Every time Claude finishes a reply,
its answer is read out loud automatically — no copy-pasting, no extra app, no
button to press.

It plugs straight into Claude through hooks, so it works in whatever session you
open in this project.

## How it works

```
Claude finishes a reply
        ↓  (Stop hook)
   voice.py hook            reads the last assistant message from the transcript
        ↓
   clean_for_speech()       strips markdown, code blocks, URLs, emoji, file paths
        ↓
   background worker        speaks it while you keep working (never blocks Claude)
        ↓
   say / espeak-ng / SAPI / ElevenLabs / OpenAI
```

Three hooks get installed:

| Hook          | What it does                                                    |
|---------------|-----------------------------------------------------------------|
| `Stop`        | Speaks Claude's reply when the turn ends                         |
| `Notification`| Speaks permission prompts and idle notices ("Claude needs your…") |
| `SessionEnd`  | Stops any speech still playing                                   |

## Setup

1. Have a speech engine available (pick one):
   - **macOS** — nothing to do, the built-in `say` is used.
   - **Windows** — nothing to do, built-in SAPI via PowerShell.
   - **Linux** — `sudo apt install espeak-ng` (or `speech-dispatcher` for `spd-say`).
   - **Any platform, nicer voice** — export `ELEVENLABS_API_KEY` or `OPENAI_API_KEY`,
     plus an audio player (`mpv`, `ffplay` or `mpg123`) if you're not on macOS.

2. Install the hooks:

   ```bash
   python3 claude-voice/voice.py install
   ```

   This project already ships them in `.claude/settings.json`, so you only need
   this if you want the voice in a different project (`voice.py install
   ~/.claude/settings.json` turns it on everywhere).

3. Check it:

   ```bash
   python3 claude-voice/voice.py doctor   # config + engines it can see
   python3 claude-voice/voice.py test     # says a line out loud
   ```

4. Start a new Claude session (hooks are read at startup) and say hello.

## Controls

From the terminal:

```bash
python3 claude-voice/voice.py off     # mute (sticks across sessions)
python3 claude-voice/voice.py on      # unmute
python3 claude-voice/voice.py stop    # shut it up right now
python3 claude-voice/voice.py say "read this to me"
```

From inside Claude, the bundled slash command does the same thing:

```
/voice off
/voice test
/voice doctor
```

## Configuration

Edit `claude-voice/voice.config.json`, or drop the same keys in
`~/.claude/voice.config.json` to override them for every project.

| Key | Meaning |
|---|---|
| `enabled` | Master switch |
| `engine` | `auto`, `say`, `espeak`, `spd-say`, `powershell`, `elevenlabs`, `openai` |
| `voice` | Engine voice name/ID — e.g. `Samantha` on macOS, a voice ID for ElevenLabs |
| `rate` | Words per minute (mapped to each engine's own scale) |
| `volume` | 0.0 – 1.0 |
| `max_chars` | Long answers are cut at a sentence boundary (default 700) |
| `min_chars` | Anything shorter is skipped |
| `interrupt_previous` | A new reply cuts off the one still being spoken |
| `speak_notifications` | Read permission prompts aloud too |

Environment overrides: `CLAUDE_VOICE_ENGINE`, `CLAUDE_VOICE_VOICE`,
`CLAUDE_VOICE_CONFIG`, `CLAUDE_VOICE_DISABLED=1`, `CLAUDE_VOICE_STATE`.

Good macOS voices: `Samantha`, `Daniel`, `Karen`, `Moira`. List them all with
`say -v '?'`.

## Notes

- Pure standard-library Python 3.8+ — nothing to `pip install`.
- Speech runs in a detached worker, so Claude never waits on audio.
- The hook always exits 0. If a voice engine is missing or an API call fails,
  the session carries on silently and the reason is logged to
  `~/.claude/voice-state/worker.log`.
- Each answer is spoken once, even if `Stop` fires more than once per turn.
