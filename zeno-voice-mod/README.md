# Zeno Voice — a Claude Code mod

Reads Claude's replies out loud the moment each turn ends. Nothing to copy,
nothing to click. It's a Claude Code plugin, so it just runs whenever you talk
to Claude Code.

## Install

Copy this folder onto your computer, then start Claude Code pointing at it:

```
claude --plugin-dir /path/to/zeno-voice-mod
```

To keep it on permanently, put the folder in `~/.claude/plugins/` instead — it
loads on every session from then on.

## Using it

Nothing to do. Ask Claude Code something; when it finishes answering, it speaks.

| Command | What it does |
|---|---|
| `/voice` | Says whether it's on, and which voice |
| `/voice off` | Stops it |
| `/voice on` | Starts it again |
| `/voice Samantha` | Switches to a named system voice, and reads a line back in it |

The choice sticks across sessions.

## What it does and doesn't read

It reads the answer you'd actually read on screen, cleaned up for the ear:

- Code blocks become "code block" instead of being spelled out character by character
- Links become "link"; `src/app/main.py` is read as "main.py"
- Headings, bullets, tables, bold and emoji markup are dropped
- Long answers stop at a sentence rather than droning on

It stays quiet for a subagent's report, a turn you interrupted, and a turn with
no visible answer. It never holds up your prompt — speech runs in the background,
and a machine with no voice installed just stays silent.

## Where the sound comes out

It uses the speech voice built into the computer Claude Code is running on. If
you run Claude Code on your own machine, that's your speakers. If you're using
Claude in a browser tab, the engine is on a server and there's nothing to hear —
the browser extension in `claude-voice-extension/` covers that case instead.

## Developing it

```
claude plugin validate zeno-voice-mod
claude plugin test zeno-voice-mod
```
