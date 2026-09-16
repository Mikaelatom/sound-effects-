#!/usr/bin/env node
/* Claude Voice - an MCP server that speaks text aloud on this computer.
 *
 * Talks JSON-RPC 2.0 over stdio, one message per line. No dependencies, so the
 * bundle runs on the Node that ships with Claude Desktop.
 *
 * Nothing is ever written to stdout except protocol messages - notes go to stderr.
 */

'use strict';

const { spawn } = require('child_process');

const PROTOCOL_FALLBACK = '2025-06-18';
const SERVER_INFO = { name: 'claude-voice', version: '1.0.0' };

let current = null;   // the speech process that is playing right now

// --------------------------------------------------------------- speaking

function log(message) {
  process.stderr.write('[claude-voice] ' + message + '\n');
}

/** Strip the markdown so it sounds like speech, not punctuation soup. */
function forSpeech(text) {
  let t = String(text || '');
  t = t.replace(/```[\s\S]*?```/g, ' code block. ');
  t = t.replace(/`([^`\n]+)`/g, '$1');
  t = t.replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1');
  t = t.replace(/\[([^\]]+)\]\([^)]*\)/g, '$1');
  t = t.replace(/https?:\/\/\S+/g, ' link ');
  t = t.replace(/^[ \t]*#{1,6}[ \t]*/gm, '');
  t = t.replace(/^[ \t]*([-*+]|\d+[.)])[ \t]+/gm, '');
  t = t.replace(/^[ \t]*\|.*\|[ \t]*$/gm, '');
  t = t.replace(/(\*{1,3}|_{2,3}|~~)(\S[\s\S]*?\S|\S)\1/g, '$2');
  t = t.replace(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}\u{2B00}-\u{2BFF}]/gu, ' ');
  return t.replace(/\s+/g, ' ').trim();
}

function stopSpeaking() {
  if (!current) return false;
  try { current.kill(); } catch (e) { /* already gone */ }
  current = null;
  return true;
}

/* Text goes through the environment, never the command line - that way quotes,
   newlines and apostrophes in Claude's reply can't break the command. */
function startSpeech(text, rate, voice) {
  const env = Object.assign({}, process.env, { CLAUDE_VOICE_TEXT: text });

  if (process.platform === 'win32') {
    const psRate = Math.max(-10, Math.min(10, Math.round((rate - 1) * 5)));
    let script =
      'Add-Type -AssemblyName System.Speech;' +
      '$s = New-Object System.Speech.Synthesis.SpeechSynthesizer;' +
      '$s.Rate = ' + psRate + ';';
    if (voice) script += "$s.SelectVoice('" + String(voice).replace(/'/g, "''") + "');";
    script += '$s.Speak($env:CLAUDE_VOICE_TEXT)';
    return spawn('powershell', ['-NoProfile', '-NonInteractive', '-Command', script], { env });
  }

  if (process.platform === 'darwin') {
    const args = ['-r', String(Math.round(190 * rate))];
    if (voice) args.push('-v', String(voice));
    args.push(text);
    return spawn('say', args, { env });
  }

  const args = ['-s', String(Math.round(175 * rate))];
  if (voice) args.push('-v', String(voice));
  args.push(text);
  return spawn('espeak-ng', args, { env });
}

function speak(text, rate, voice) {
  const clean = forSpeech(text);
  if (!clean) return { ok: false, message: 'There was nothing to read.' };

  stopSpeaking();
  let child;
  try {
    child = startSpeech(clean, rate, voice);
  } catch (e) {
    return { ok: false, message: 'Could not start the speech voice: ' + e.message };
  }

  current = child;
  child.on('error', (e) => {
    log('speech failed: ' + e.message);
    if (current === child) current = null;
  });
  child.on('exit', () => { if (current === child) current = null; });

  const preview = clean.length > 60 ? clean.slice(0, 60) + '...' : clean;
  return { ok: true, message: 'Speaking: "' + preview + '"', spoken: clean.length };
}

function listVoices() {
  return new Promise((resolve) => {
    let command, args;
    if (process.platform === 'win32') {
      command = 'powershell';
      args = ['-NoProfile', '-NonInteractive', '-Command',
        'Add-Type -AssemblyName System.Speech;' +
        '(New-Object System.Speech.Synthesis.SpeechSynthesizer).GetInstalledVoices()' +
        ' | ForEach-Object { $_.VoiceInfo.Name }'];
    } else if (process.platform === 'darwin') {
      command = 'say'; args = ['-v', '?'];
    } else {
      command = 'espeak-ng'; args = ['--voices'];
    }

    let out = '';
    let child;
    try { child = spawn(command, args); } catch (e) { return resolve([]); }
    child.stdout.on('data', (d) => { out += d.toString(); });
    child.on('error', () => resolve([]));
    child.on('close', () => {
      const names = out.split('\n')
        .map((line) => line.trim())
        .filter(Boolean)
        .map((line) => (process.platform === 'darwin' ? line.split(/\s{2,}/)[0] : line));
      resolve(names.slice(0, 40));
    });
  });
}

// ------------------------------------------------------------------- tools

const TOOLS = [
  {
    name: 'speak',
    description:
      'Read text out loud on the user\'s computer, using the speech voice built into their system. ' +
      'Use this whenever the user asks to hear something, or whenever they have asked you to speak your replies. ' +
      'Pass the text you want heard - it returns as soon as speech starts, so it never holds up the conversation.',
    inputSchema: {
      type: 'object',
      properties: {
        text: { type: 'string', description: 'The text to read out loud.' },
        rate: {
          type: 'number',
          description: 'Speaking speed, where 1 is normal. 0.6 is slow, 1.6 is fast.',
          minimum: 0.5, maximum: 2
        },
        voice: { type: 'string', description: 'Name of a voice from list_voices. Optional.' }
      },
      required: ['text']
    }
  },
  {
    name: 'stop_speaking',
    description: 'Stop speech that is playing right now.',
    inputSchema: { type: 'object', properties: {} }
  },
  {
    name: 'list_voices',
    description: 'List the speech voices installed on this computer, so the user can pick one.',
    inputSchema: { type: 'object', properties: {} }
  }
];

async function callTool(name, args) {
  args = args || {};

  if (name === 'speak') {
    const rate = typeof args.rate === 'number' ? Math.max(0.5, Math.min(2, args.rate)) : 1;
    const result = speak(args.text, rate, args.voice);
    return { text: result.message, isError: !result.ok };
  }

  if (name === 'stop_speaking') {
    return { text: stopSpeaking() ? 'Stopped.' : 'Nothing was playing.' };
  }

  if (name === 'list_voices') {
    const voices = await listVoices();
    return {
      text: voices.length
        ? 'Voices on this computer:\n' + voices.map((v) => '- ' + v).join('\n')
        : 'No speech voices found on this computer.'
    };
  }

  return { text: 'Unknown tool: ' + name, isError: true };
}

// ---------------------------------------------------------------- protocol

function send(message) {
  process.stdout.write(JSON.stringify(message) + '\n');
}

function reply(id, result) {
  send({ jsonrpc: '2.0', id, result });
}

function replyError(id, code, message) {
  send({ jsonrpc: '2.0', id, error: { code, message } });
}

async function handle(msg) {
  const { id, method, params } = msg;

  if (method === 'initialize') {
    const asked = params && params.protocolVersion;
    return reply(id, {
      protocolVersion: typeof asked === 'string' ? asked : PROTOCOL_FALLBACK,
      capabilities: { tools: { listChanged: false } },
      serverInfo: SERVER_INFO
    });
  }

  if (method === 'notifications/initialized' || method === 'notifications/cancelled') return;
  if (method === 'ping') return reply(id, {});
  if (method === 'tools/list') return reply(id, { tools: TOOLS });

  if (method === 'tools/call') {
    const name = params && params.name;
    try {
      const result = await callTool(name, params && params.arguments);
      return reply(id, {
        content: [{ type: 'text', text: result.text }],
        isError: Boolean(result.isError)
      });
    } catch (e) {
      return reply(id, {
        content: [{ type: 'text', text: 'Speech failed: ' + e.message }],
        isError: true
      });
    }
  }

  // Things we don't offer, answered politely rather than left hanging.
  if (method === 'resources/list') return reply(id, { resources: [] });
  if (method === 'prompts/list') return reply(id, { prompts: [] });

  if (id !== undefined) replyError(id, -32601, 'Method not found: ' + method);
}

let buffer = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk) => {
  buffer += chunk;
  let cut;
  while ((cut = buffer.indexOf('\n')) >= 0) {
    const line = buffer.slice(0, cut).trim();
    buffer = buffer.slice(cut + 1);
    if (!line) continue;
    let msg;
    try { msg = JSON.parse(line); } catch (e) { log('ignored unparseable line'); continue; }
    Promise.resolve(handle(msg)).catch((e) => log('handler error: ' + e.message));
  }
});

process.stdin.on('end', () => { stopSpeaking(); process.exit(0); });
process.on('SIGTERM', () => { stopSpeaking(); process.exit(0); });
process.on('SIGINT', () => { stopSpeaking(); process.exit(0); });

log('ready');
