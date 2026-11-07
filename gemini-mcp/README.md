# Gemini MCP for Claude Code

An MCP server that lets Claude Code call Google Gemini. Claude can then ask Gemini for a second opinion, hand it big files, or compare answers.

## Tools

| Tool | What it does |
|---|---|
| `ask_gemini` | Send a prompt (with an optional system instruction, model and temperature) and get Gemini's answer |
| `gemini_analyze_files` | Read local files and send them to Gemini with an instruction ("review this code", "summarize") |
| `gemini_list_models` | List the models your API key can use (API key only) |
| `gemini_status` | Show which login method is active |

## 1. Install

```bash
git clone https://github.com/mikaelatom/sound-effects-.git
cd sound-effects-/gemini-mcp
npm install
```

## 2. Connect your Gemini account (pick one)

### Option A: Log in with your Google account (uses your Google AI Pro plan)

Your Google AI Pro subscription does not come with an API key, but the official Gemini CLI can log in with your Google account and use your plan's limits. This server runs the CLI for you.

```bash
npm install -g @google/gemini-cli
gemini            # choose "Login with Google", sign in, then exit with /quit
```

Then add the server to Claude Code:

```bash
claude mcp add gemini --scope user -- node "$(pwd)/server.js"
```

### Option B: API key (works with any Google account)

1. Create a key at https://aistudio.google.com/apikey
2. Add the server with the key:

```bash
claude mcp add gemini --scope user -e GEMINI_API_KEY=YOUR_KEY -- node "$(pwd)/server.js"
```

API usage is billed (or uses the free tier) through Google AI Studio. It is separate from the Google AI Pro subscription.

## 3. Use it

Restart Claude Code, run `/mcp` and check that `gemini` shows as connected. Then just ask:

> Ask Gemini to review src/app.js and compare its suggestions with yours.

## Settings (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `GEMINI_API_KEY` | none | API key. If set, the API is used |
| `GEMINI_BACKEND` | auto | Force `api` or `cli` |
| `GEMINI_MODEL` | `gemini-2.5-pro` (API) / CLI default | Default model |
| `GEMINI_CLI_PATH` | `gemini` | Path to the Gemini CLI |
| `GEMINI_TIMEOUT_MS` | `300000` | Timeout per request |
| `GEMINI_MAX_FILE_BYTES` | `2000000` | Size limit per file for `gemini_analyze_files` |

Pass any of them with `-e NAME=value` in `claude mcp add`.

## Troubleshooting

- **`gemini_status` says backend `none`**: no API key was set and the `gemini` command was not found. Do option A or B.
- **CLI asks you to log in or hangs**: run `gemini` by itself once and finish the Google login.
- **Model not found**: run `gemini_list_models` (API key) and set `GEMINI_MODEL` to one it lists.
