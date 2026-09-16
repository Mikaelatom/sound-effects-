/* Claude Voice - reads Claude's replies out loud as they arrive.
   Runs on claude.ai. No network, no storage beyond an on/off flag. */

(function () {
  'use strict';
  if (window.__claudeVoiceLoaded) return;
  window.__claudeVoiceLoaded = true;

  var SETTLE_MS = 1100;   // a reply is "finished" once it stops changing this long
  var MIN_CHARS = 2;

  // How Claude's page marks a finished assistant reply, most specific first.
  var ASSISTANT = [
    '[data-testid="assistant-message"]',
    '.font-claude-response',
    '.font-claude-message',
    '[data-is-streaming]'
  ];
  var USER = ['[data-testid="user-message"]', '.font-user-message', '[data-testid="user-turn"]'];
  var COMPOSER = ['form', 'textarea', '[contenteditable="true"]', '[role="textbox"]'];

  var state = {
    on: true,
    lastSpoken: '',
    pending: null,
    timer: null,
    seen: new WeakSet()
  };

  try { state.on = localStorage.getItem('claude-voice-off') !== '1'; } catch (e) {}

  // ------------------------------------------------------------- finding replies

  function matchesAny(el, selectors) {
    for (var i = 0; i < selectors.length; i++) {
      try { if (el.closest(selectors[i])) return true; } catch (e) {}
    }
    return false;
  }

  function assistantNodes() {
    for (var i = 0; i < ASSISTANT.length; i++) {
      var found = document.querySelectorAll(ASSISTANT[i]);
      if (found.length) return Array.prototype.slice.call(found);
    }
    return [];
  }

  function isReplyCandidate(el) {
    if (!el || el.nodeType !== 1) return false;
    if (el.closest('#claude-voice-pill')) return false;
    if (matchesAny(el, USER)) return false;
    if (matchesAny(el, COMPOSER)) return false;
    var text = (el.innerText || '').trim();
    return text.length > 20;
  }

  /* Read a reply the way it should be heard: code blocks are announced,
     not spelled out character by character. */
  function textOf(el) {
    var clone = el.cloneNode(true);
    var blocks = clone.querySelectorAll('pre');
    for (var i = 0; i < blocks.length; i++) {
      blocks[i].replaceWith(document.createTextNode(' code block. '));
    }
    var buttons = clone.querySelectorAll('button, svg');
    for (var j = 0; j < buttons.length; j++) buttons[j].remove();

    var text = (clone.innerText || clone.textContent || '');
    text = text.replace(/https?:\/\/\S+/g, ' link ');
    text = text.replace(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}\u{2B00}-\u{2BFF}]/gu, ' ');
    return text.replace(/\s+/g, ' ').trim();
  }

  // -------------------------------------------------------------------- speaking

  function speak(text) {
    var synth = window.speechSynthesis;
    if (!synth || !state.on || !text || text.length < MIN_CHARS) return;
    synth.cancel();

    // Split on sentences so long replies don't get cut off part-way.
    var parts = text.match(/[^.!?]+[.!?]*\s*/g) || [text];
    var chunks = [];
    parts.forEach(function (p) {
      if (chunks.length && (chunks[chunks.length - 1] + p).length < 200) chunks[chunks.length - 1] += p;
      else chunks.push(p);
    });

    chunks.forEach(function (chunk, i) {
      var u = new SpeechSynthesisUtterance(chunk.trim());
      u.rate = 1;
      if (i === 0) u.onstart = function () { setPill('speaking', text); };
      if (i === chunks.length - 1) {
        u.onend = u.onerror = function () { setPill(state.on ? 'on' : 'off'); };
      }
      synth.speak(u);
    });
  }

  function queue(el) {
    if (!state.on) return;
    state.pending = el;
    clearTimeout(state.timer);
    state.timer = setTimeout(function () {
      var node = state.pending;
      if (!node || !node.isConnected) return;
      var text = textOf(node);
      if (!text || text === state.lastSpoken) return;
      state.lastSpoken = text;
      speak(text);
    }, SETTLE_MS);
  }

  // ------------------------------------------------------------------ the pill

  var pill, pillText;

  function buildPill() {
    pill = document.createElement('div');
    pill.id = 'claude-voice-pill';
    pill.setAttribute('role', 'button');
    pill.setAttribute('tabindex', '0');
    pill.title = 'Claude Voice - click to turn the voice on or off';
    pill.style.cssText = [
      'position:fixed', 'right:18px', 'bottom:18px', 'z-index:2147483647',
      'display:flex', 'align-items:center', 'gap:8px',
      'padding:9px 14px', 'border-radius:100px', 'cursor:pointer',
      'font:600 13px/1.2 ui-sans-serif,system-ui,sans-serif',
      'background:#0d6e66', 'color:#fff', 'border:none',
      'box-shadow:0 2px 6px rgba(0,0,0,.2),0 10px 24px rgba(0,0,0,.18)',
      'max-width:320px', 'user-select:none'
    ].join(';');

    var icon = document.createElement('span');
    icon.textContent = '🔊';
    icon.style.cssText = 'font-size:15px;flex:none';

    pillText = document.createElement('span');
    pillText.style.cssText = 'white-space:nowrap;overflow:hidden;text-overflow:ellipsis';

    pill.appendChild(icon);
    pill.appendChild(pillText);
    pill.addEventListener('click', toggle);
    pill.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle(); }
    });
    document.body.appendChild(pill);
    setPill(state.on ? 'on' : 'off');
  }

  function setPill(mode, text) {
    if (!pill) return;
    if (mode === 'speaking') {
      pill.style.background = '#b45f06';
      pillText.textContent = text.length > 34 ? text.slice(0, 34) + '…' : text;
    } else if (mode === 'on') {
      pill.style.background = '#0d6e66';
      pillText.textContent = 'Voice on';
    } else {
      pill.style.background = '#5b6b68';
      pillText.textContent = 'Voice off';
    }
  }

  function toggle() {
    state.on = !state.on;
    try { localStorage.setItem('claude-voice-off', state.on ? '0' : '1'); } catch (e) {}
    if (!state.on && window.speechSynthesis) window.speechSynthesis.cancel();
    setPill(state.on ? 'on' : 'off');
    // Clicking counts as the gesture Chrome wants before it will speak.
    if (state.on && window.speechSynthesis) {
      var u = new SpeechSynthesisUtterance('Voice on.');
      u.rate = 1;
      window.speechSynthesis.speak(u);
    }
  }

  // --------------------------------------------------------------------- start

  function start() {
    buildPill();

    // Everything already on screen counts as heard, so history isn't replayed.
    var existing = assistantNodes();
    existing.forEach(function (n) { state.seen.add(n); });
    if (existing.length) state.lastSpoken = textOf(existing[existing.length - 1]);

    var observer = new MutationObserver(function (records) {
      if (!state.on) return;
      var known = assistantNodes();

      if (known.length) {
        var latest = known[known.length - 1];
        if (latest && (latest.innerText || '').trim().length > MIN_CHARS) queue(latest);
        return;
      }

      // No known marker on this page - fall back to the newest block of prose
      // that isn't the user's turn or the box they type in.
      for (var i = 0; i < records.length; i++) {
        var rec = records[i];
        var node = rec.type === 'characterData' ? rec.target.parentElement : rec.target;
        var added = rec.addedNodes && rec.addedNodes.length ? rec.addedNodes[0] : null;
        var candidate = (added && added.nodeType === 1) ? added : node;
        if (isReplyCandidate(candidate)) { queue(candidate); return; }
      }
    });

    observer.observe(document.body, {
      childList: true, subtree: true, characterData: true
    });
  }

  if (document.body) start();
  else document.addEventListener('DOMContentLoaded', start);
})();
