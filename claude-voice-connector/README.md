# Claude Voice — connector for the Claude app

Gives Claude a voice inside the Claude desktop app. Claude gains a `speak` tool
and reads things out loud through your computer's own voice.

**Nothing to install.** Claude Desktop ships with Node built in, so the connector
runs as-is — no Python, no PowerShell scripts, no terminal.

## Install

1. Download **`claude-voice.mcpb`**.
2. Open the **Claude desktop app** → **Settings** → **Extensions**.
3. Drag `claude-voice.mcpb` onto that window. (Or double-click the file.)
4. Click **Install**, then **Enable**.

Claude Voice now shows up in your connector list.

## Make Claude narrate everything

A connector gives Claude a tool; Claude decides when to use it. To have it
narrate the whole conversation, tell it to — once:

> From now on, after writing each reply, call the `speak` tool with that reply
> so I can hear it.

Put that in a project's custom instructions (or your personal preferences in
Settings) and it applies to every conversation, permanently.

For one-off use, just ask: *"read that out loud"*, *"say that again slower"*.

## What Claude can do with it

| Tool | What it does |
|---|---|
| `speak` | Reads text aloud. Takes an optional speed (1 is normal) and voice name. |
| `stop_speaking` | Stops mid-sentence. |
| `list_voices` | Lists the voices installed on your computer, so you can pick one. |

Try: *"list my voices"*, then *"use Microsoft Zira from now on"*.

## Notes

- Uses the speech voice built into Windows, macOS or Linux. Offline, nothing uploaded.
- Markdown is cleaned up before speaking — code blocks are announced rather than
  spelled out, links become "link", emoji are dropped.
- `speak` returns the moment speech starts, so Claude never sits waiting on audio.
- Linux needs `espeak-ng` installed (`sudo apt install espeak-ng`). Windows and
  macOS work as-is.

## Building it again

The `.mcpb` is a zip of `manifest.json` and `server/`:

```
zip -r claude-voice.mcpb manifest.json server -x '*.DS_Store'
```
