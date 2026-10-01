# Unreal MCP: a connector between Claude and Unreal Engine

> **New here? Follow [START-HERE.md](START-HERE.md)** for the double-click setup. The rest of this page is the manual/technical version.

This is an [MCP](https://modelcontextprotocol.io) server that lets Claude (Claude Desktop or Claude Code) work inside a **running Unreal Editor**. It can inspect the level, spawn and move actors, edit properties, import sounds and meshes, run console commands, take viewport screenshots, and run any Python against the editor's `unreal` API.

```
Claude ──MCP (stdio)──► unreal-mcp ──UDP multicast discovery + TCP──► Unreal Editor
                                     (Unreal's own Python remote-execution protocol)
```

It uses the remote-execution protocol from Unreal's **Python Editor Script Plugin**, so you don't need a C++ plugin or a project rebuild.

## 1. Set up Unreal (once per project)

1. **Edit → Plugins**: enable **Python Editor Script Plugin**, then restart the editor.
2. **Edit → Project Settings → Plugins → Python**: tick **Enable Remote Execution**.
   Leave the other network settings at their defaults (multicast group `239.0.0.1:6766`, TTL `0` = this machine only).
3. Keep the editor open while you use Claude.

## 2. Install the server

You need Python 3.10+ and [uv](https://docs.astral.sh/uv/) (`pip install uv` or see uv's site).

Nothing else needs installing: `uvx` fetches the server straight from this repo.

```bash
uvx --from "git+https://github.com/mikaelatom/sound-effects-#subdirectory=unreal-mcp" unreal-mcp
```

(Started by hand like this, it waits silently for a client. That means it works; press Ctrl+C.)

For a local checkout, use `pip install ./unreal-mcp`, then run `unreal-mcp`.

## 3. Connect Claude

### Claude Code

```bash
claude mcp add unreal -- uvx --from "git+https://github.com/mikaelatom/sound-effects-#subdirectory=unreal-mcp" unreal-mcp
```

### Claude Desktop

Open **Settings → Developer → Edit Config** and add:

```json
{
  "mcpServers": {
    "unreal": {
      "command": "uvx",
      "args": [
        "--from",
        "git+https://github.com/mikaelatom/sound-effects-#subdirectory=unreal-mcp",
        "unreal-mcp"
      ]
    }
  }
}
```

Restart Claude Desktop. The `unreal_*` tools should appear in the tools menu.

> Claude Desktop sometimes can't find `uvx` on its PATH. If so, use the full path from `which uvx` (macOS/Linux) or `where uvx` (Windows) as `command`.

Then ask something like *"What's in my Unreal level?"*

## Tools

| Tool | What it does |
| --- | --- |
| `unreal_project_info` | Project, engine version, open level, actor count |
| `unreal_list_instances` / `unreal_select_instance` | Find running editors and pick one when several are open |
| `unreal_list_actors`, `unreal_get_actor` | Inspect the level (filter by class, name or selection) |
| `unreal_spawn_actor` | Spawn from a class (`PointLight`, `StaticMeshActor`, …) or an asset (mesh, Blueprint, sound) |
| `unreal_transform_actor` | Move, rotate, scale, rename |
| `unreal_delete_actors`, `unreal_select_actors` | Delete or select (and focus) actors |
| `unreal_get_property`, `unreal_set_property` | Read or write any editor property on an actor, component or asset |
| `unreal_list_assets` | Browse the content browser |
| `unreal_import_files` | Import files from disk (audio, FBX, textures, …) |
| `unreal_open_level`, `unreal_save_all` | Level and save management |
| `unreal_console_command` | Run any console command |
| `unreal_screenshot` | Capture the viewport and show it to Claude |
| `unreal_run_python`, `unreal_evaluate` | Run arbitrary Python in the editor. Use these for anything the other tools don't cover |

Example: *"Import ~/sfx/squish-pop.wav into /Game/Audio, then place it as an ambient sound next to the PlayerStart."*
WAV is the safest audio format for import. Convert MP3s first (e.g. `ffmpeg -i in.mp3 out.wav`) if your engine version rejects them.

## Configuration (environment variables)

| Variable | Default | Purpose |
| --- | --- | --- |
| `UNREAL_MCP_PROJECT` | – | Prefer the editor with this project name |
| `UNREAL_MCP_MULTICAST_GROUP` / `_PORT` | `239.0.0.1` / `6766` | Must match Project Settings → Python |
| `UNREAL_MCP_MULTICAST_BIND` | `0.0.0.0` | Interface used for discovery |
| `UNREAL_MCP_MULTICAST_TTL` | `0` | Raise it (and Unreal's) to reach an editor on another machine |
| `UNREAL_MCP_COMMAND_HOST` / `_PORT` | `127.0.0.1` / `0` (any) | Where Unreal connects back to |
| `UNREAL_MCP_COMMAND_TIMEOUT` | `300` | Seconds to wait for a command |
| `UNREAL_MCP_SCREENSHOT_DIR` | system temp | Where screenshots are written |

In Claude Desktop, set these under `"env": { ... }` in the server entry.

## Troubleshooting

- **"No Unreal Editor found"**: check that the plugin is enabled, *Enable Remote Execution* is ticked, and the editor is open. A firewall prompt for UnrealEditor or Python may need approving. VPN software sometimes blocks local multicast.
- **Several editors open**: call `unreal_list_instances`, then `unreal_select_instance`, or set `UNREAL_MCP_PROJECT`.
- **Editor appears frozen while a command runs**: commands run on the editor's main thread, which is normal for long Python jobs.

## Safety

`unreal_run_python` runs arbitrary code with your user account's permissions, the same as typing it into Unreal's Python console. By default everything stays on your machine (multicast TTL 0, command socket bound to `127.0.0.1`). Use source control and save often. Claude Desktop/Code will ask before each tool call unless you allow it.

## Development

```bash
cd unreal-mcp
pip install -e ".[test]"
pytest            # uses a fake editor, so no Unreal install is needed
npx @modelcontextprotocol/inspector unreal-mcp   # poke at the tools interactively
```
