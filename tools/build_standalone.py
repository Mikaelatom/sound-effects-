#!/usr/bin/env python3
"""
Bundle index.html into a single double-clickable file.

    python3 tools/build_standalone.py

index.html already carries the sprite atlas inline; this also inlines
assets/hit.mp3, so the result needs no internet and no sibling files. Useful
for sending the game to someone or opening it straight off a USB stick.
"""
import base64, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = open(os.path.join(ROOT, 'index.html')).read()
mp3 = base64.b64encode(open(os.path.join(ROOT, 'assets', 'hit.mp3'), 'rb').read()).decode()

out = src.replace('const HIT_SFX_SRC = "assets/hit.mp3";',
                  'const HIT_SFX_SRC = "data:audio/mpeg;base64,%s";' % mp3)
if 'data:audio/mpeg' not in out:
    raise SystemExit('HIT_SFX_SRC not found - did index.html change?')

dest = os.path.join(ROOT, 'TwinFate.html')
open(dest, 'w').write(out)
print('wrote %s (%d KB)' % (dest, os.path.getsize(dest) // 1024))
