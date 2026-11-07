#!/usr/bin/env node
// Gemini MCP server: lets Claude Code talk to Google Gemini.
//
// Two ways to connect your Gemini account:
//   1. "api" backend  - set GEMINI_API_KEY (get one at https://aistudio.google.com/apikey)
//   2. "cli" backend  - install the Gemini CLI (`npm i -g @google/gemini-cli`), run `gemini`
//                       once and choose "Login with Google". This uses your Google account
//                       (including Google AI Pro / Ultra subscription limits).
// The backend is picked automatically (API key wins), or force one with GEMINI_BACKEND=api|cli.

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { spawn, spawnSync } from "node:child_process";
import { readFile, stat } from "node:fs/promises";
import path from "node:path";

const API_KEY = process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY || "";
const DEFAULT_MODEL = process.env.GEMINI_MODEL || "gemini-2.5-pro";
const CLI_BIN = process.env.GEMINI_CLI_PATH || "gemini";
const TIMEOUT_MS = Number(process.env.GEMINI_TIMEOUT_MS || 300_000);
const MAX_FILE_BYTES = Number(process.env.GEMINI_MAX_FILE_BYTES || 2_000_000);
const API_BASE = "https://generativelanguage.googleapis.com/v1beta";

function cliAvailable() {
  try {
    return spawnSync(CLI_BIN, ["--version"], { stdio: "ignore", timeout: 15_000 }).status === 0;
  } catch {
    return false;
  }
}

function pickBackend() {
  const forced = (process.env.GEMINI_BACKEND || "").toLowerCase();
  if (forced === "api" || forced === "cli") return forced;
  if (API_KEY) return "api";
  if (cliAvailable()) return "cli";
  return "none";
}

const BACKEND = pickBackend();

const SETUP_HELP =
  "Gemini is not connected. Either set GEMINI_API_KEY (from https://aistudio.google.com/apikey) " +
  "in the MCP server's env, or install the Gemini CLI (`npm i -g @google/gemini-cli`), run `gemini` " +
  "once and pick 'Login with Google' to use your Google AI Pro account.";

// ---------- backends ----------

async function callApi({ prompt, model, system, temperature }) {
  const body = {
    contents: [{ role: "user", parts: [{ text: prompt }] }],
  };
  if (system) body.systemInstruction = { parts: [{ text: system }] };
  if (temperature !== undefined) body.generationConfig = { temperature };

  const res = await fetch(`${API_BASE}/models/${encodeURIComponent(model)}:generateContent`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": API_KEY },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(`Gemini API error ${res.status}: ${data?.error?.message || res.statusText}`);
  }
  const cand = data.candidates?.[0];
  const text = (cand?.content?.parts || [])
    .filter((p) => typeof p.text === "string" && !p.thought)
    .map((p) => p.text)
    .join("");
  if (!text) {
    const reason = cand?.finishReason || data.promptFeedback?.blockReason || "unknown";
    throw new Error(`Gemini returned no text (reason: ${reason}).`);
  }
  return text;
}

function callCli({ prompt, model, system }) {
  const fullPrompt = system ? `System instructions:\n${system}\n\n---\n\n${prompt}` : prompt;
  // The prompt goes over stdin (no shell, no argv size limits); -p switches the CLI to headless mode.
  const args = ["-p", "Respond to the request above."];
  if (model) args.push("-m", model);

  return new Promise((resolve, reject) => {
    const child = spawn(CLI_BIN, args, { stdio: ["pipe", "pipe", "pipe"], env: process.env });
    let out = "";
    let err = "";
    const timer = setTimeout(() => {
      child.kill("SIGTERM");
      reject(new Error(`Gemini CLI timed out after ${TIMEOUT_MS} ms.`));
    }, TIMEOUT_MS);
    child.stdout.on("data", (d) => (out += d));
    child.stderr.on("data", (d) => (err += d));
    child.on("error", (e) => {
      clearTimeout(timer);
      reject(new Error(`Could not start Gemini CLI (${CLI_BIN}): ${e.message}`));
    });
    child.on("close", (code) => {
      clearTimeout(timer);
      if (code === 0 && out.trim()) resolve(out.trim());
      else reject(new Error(`Gemini CLI exited with code ${code}: ${(err || out).trim().slice(0, 2000)}`));
    });
    child.stdin.end(fullPrompt);
  });
}

async function askGemini(opts) {
  if (BACKEND === "api") return callApi({ ...opts, model: opts.model || DEFAULT_MODEL });
  if (BACKEND === "cli") return callCli({ ...opts, model: opts.model || process.env.GEMINI_MODEL });
  throw new Error(SETUP_HELP);
}

// ---------- helpers ----------

const ok = (text) => ({ content: [{ type: "text", text }] });
const fail = (e) => ({ content: [{ type: "text", text: String(e?.message || e) }], isError: true });

async function readFilesAsContext(paths) {
  const chunks = [];
  for (const p of paths) {
    const abs = path.resolve(p);
    const info = await stat(abs);
    if (!info.isFile()) throw new Error(`Not a file: ${abs}`);
    if (info.size > MAX_FILE_BYTES) {
      throw new Error(`${abs} is ${info.size} bytes, over the ${MAX_FILE_BYTES}-byte limit (GEMINI_MAX_FILE_BYTES).`);
    }
    const text = await readFile(abs, "utf8");
    chunks.push(`===== FILE: ${abs} =====\n${text}\n===== END FILE: ${abs} =====`);
  }
  return chunks.join("\n\n");
}

// ---------- MCP server ----------

const server = new McpServer({ name: "gemini", version: "1.0.0" });

const modelArg = z
  .string()
  .optional()
  .describe("Gemini model id, e.g. gemini-2.5-pro or gemini-2.5-flash. Defaults to GEMINI_MODEL or the backend default.");

server.registerTool(
  "ask_gemini",
  {
    title: "Ask Gemini",
    description:
      "Send a prompt to Google Gemini and return its answer. Useful for a second opinion, " +
      "brainstorming, or tasks that benefit from Gemini's large context window.",
    inputSchema: {
      prompt: z.string().min(1).describe("The full prompt to send. Include any context Gemini needs; it cannot see this conversation."),
      system: z.string().optional().describe("Optional system instruction for Gemini."),
      model: modelArg,
      temperature: z.number().min(0).max(2).optional().describe("Sampling temperature (API backend only)."),
    },
  },
  async ({ prompt, system, model, temperature }) => {
    try {
      return ok(await askGemini({ prompt, system, model, temperature }));
    } catch (e) {
      return fail(e);
    }
  }
);

server.registerTool(
  "gemini_analyze_files",
  {
    title: "Gemini: analyze files",
    description:
      "Read local text files and send them to Gemini with an instruction (e.g. code review, " +
      "summarize, find bugs). Paths are resolved relative to the server's working directory.",
    inputSchema: {
      paths: z.array(z.string()).min(1).describe("File paths to include."),
      instruction: z.string().min(1).describe("What Gemini should do with the files."),
      model: modelArg,
    },
  },
  async ({ paths, instruction, model }) => {
    try {
      const context = await readFilesAsContext(paths);
      return ok(await askGemini({ prompt: `${context}\n\n${instruction}`, model }));
    } catch (e) {
      return fail(e);
    }
  }
);

server.registerTool(
  "gemini_list_models",
  {
    title: "Gemini: list models",
    description: "List the Gemini models your API key can use (API backend only).",
    inputSchema: {},
  },
  async () => {
    if (BACKEND !== "api") return fail("Listing models needs the API backend (set GEMINI_API_KEY).");
    try {
      const names = [];
      let pageToken = "";
      do {
        const url = `${API_BASE}/models?pageSize=1000${pageToken ? `&pageToken=${pageToken}` : ""}`;
        const res = await fetch(url, { headers: { "x-goog-api-key": API_KEY } });
        const data = await res.json();
        if (!res.ok) throw new Error(`Gemini API error ${res.status}: ${data?.error?.message}`);
        for (const m of data.models || []) {
          if (m.supportedGenerationMethods?.includes("generateContent")) {
            names.push(`${m.name.replace(/^models\//, "")} - ${m.displayName || ""}`);
          }
        }
        pageToken = data.nextPageToken || "";
      } while (pageToken);
      return ok(names.join("\n") || "No models found.");
    } catch (e) {
      return fail(e);
    }
  }
);

server.registerTool(
  "gemini_status",
  {
    title: "Gemini: connection status",
    description: "Show how this server is connected to Gemini (API key or Gemini CLI login).",
    inputSchema: {},
  },
  async () => {
    const lines = [`Backend: ${BACKEND}`];
    if (BACKEND === "api") lines.push(`Default model: ${DEFAULT_MODEL}`, "Auth: GEMINI_API_KEY");
    if (BACKEND === "cli") lines.push(`CLI: ${CLI_BIN}`, "Auth: Gemini CLI login (Google account)");
    if (BACKEND === "none") lines.push(SETUP_HELP);
    return ok(lines.join("\n"));
  }
);

await server.connect(new StdioServerTransport());
console.error(`[gemini-mcp] running (backend: ${BACKEND})`);
