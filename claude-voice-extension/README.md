# Claude Voice (browser extension)

Reads Claude's replies out loud automatically, as they arrive. No copying.

## Install (Chrome or Edge, about a minute, one time)

1. Make a new folder anywhere — say `claude-voice` on your Desktop.
2. Put **`manifest.json`** and **`content.js`** in it. Both files, same folder, nothing else.
3. In your browser go to **`chrome://extensions`** (Edge: **`edge://extensions`**).
4. Turn on **Developer mode** — the switch in the top-right corner.
5. Click **Load unpacked** and choose the folder from step 1.
6. Open **claude.ai**. A green **🔊 Voice on** button appears in the bottom-right.
7. **Click that button once.** Browsers won't make sound until you click something,
   so this first click is what switches the sound on. It says "Voice on" back to you.

That's the whole setup. From then on every reply Claude writes gets read out loud.

## Using it

- The button turns **orange** and shows the first few words while it's reading.
- **Click it** any time to turn the voice off, and again to turn it back on.
  It remembers your choice.
- It reads a reply once Claude has finished writing it, so you hear it in one go
  rather than word by word.
- Code blocks are announced as "code block" instead of being spelled out.
- Replies already on screen when you open a conversation are never re-read.

## If it reads the wrong thing — or nothing

The extension finds Claude's replies two ways: by the markers Claude's page puts
on them, and if those aren't found, by watching for new text that isn't yours and
isn't the box you type in. Both are tested, but Claude's page can change.

If it misbehaves, the useful thing to send back is a screenshot of the page with
the reply visible, or the page's HTML around one reply (right-click a reply →
Inspect → right-click the highlighted element → Copy → Copy outerHTML).

## Privacy

Runs only on claude.ai. No network calls, no data collected, nothing stored except
whether you left the voice on or off. It uses the speech voice built into your
computer.
