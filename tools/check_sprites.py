#!/usr/bin/env python3
"""Measure the atlas against the things that make pixel art read as pixel art.

Every check here is a number somebody can argue with, which is the point: the
animation work kept going round in circles on "it looks wrong" until the
problems were measured instead. Run it after every change to the generator.

    python3 tools/check_sprites.py [char ...]

Pillow is not installed in this environment, so the PNG is decoded here - the
same format tools/make_sprites.py writes, read back the other way.
"""
import os, sys, zlib, struct

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def read_png(path):
    """(pixels as a flat RGBA bytearray, width, height).

    8-bit RGB or RGBA - the generator writes RGBA, a browser screenshot comes
    back RGB, and both get read here so the same checks can run on either."""
    raw = open(path, 'rb').read()
    assert raw[:8] == b'\x89PNG\r\n\x1a\n', 'not a PNG'
    pos, idat, w, h, ch = 8, bytearray(), 0, 0, 4
    while pos < len(raw):
        ln,  = struct.unpack('>I', raw[pos:pos+4])
        typ  = raw[pos+4:pos+8]
        body = raw[pos+8:pos+8+ln]
        if typ == b'IHDR':
            w, h, depth, colour = struct.unpack('>IIBB', body[:10])
            assert depth == 8 and colour in (2, 6), 'expected 8-bit RGB or RGBA'
            ch = 3 if colour == 2 else 4
        elif typ == b'IDAT': idat += body
        elif typ == b'IEND': break
        pos += 12 + ln
    data = zlib.decompress(bytes(idat))
    stride, prev = w*ch, bytearray(w*ch)
    out = bytearray(w*h*ch)
    p = 0
    for y in range(h):
        f = data[p]; p += 1
        line = bytearray(data[p:p+stride]); p += stride
        if f == 1:
            for i in range(ch, stride): line[i] = (line[i] + line[i-ch]) & 255
        elif f == 2:
            for i in range(stride): line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                a = line[i-ch] if i >= ch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i-ch] if i >= ch else 0
                c = prev[i-ch] if i >= ch else 0
                b = prev[i]
                pa, pb, pc = abs(b-c), abs(a-c), abs(a+b-2*c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out[y*stride:(y+1)*stride] = line
        prev = line
    if ch == 3:                       # pad RGB out to RGBA so callers see one shape
        rgba = bytearray(w*h*4)
        for i in range(w*h):
            rgba[i*4:i*4+3] = out[i*3:i*3+3]; rgba[i*4+3] = 255
        out = rgba
    return out, w, h

def load_layout():
    """FW, FH, FRAMES and the row order, straight out of the generator."""
    ns = {'__file__': os.path.join(ROOT, 'tools', 'make_sprites.py')}
    src = open(ns['__file__']).read().replace("if __name__ == '__main__':", 'if False:')
    exec(compile(src, 'gen', 'exec'), ns)
    return ns['W'], ns['H'], ns['FRAMES'], ns['ORDER'] + ns['MOB_ORDER'], ns

class Sheet:
    def __init__(s, px, aw, FW, FH):
        s.px, s.aw, s.FW, s.FH = px, aw, FW, FH
    def alpha(s, row, frame, x, y):
        X, Y = frame*s.FW + x, row*s.FH + y
        return s.px[(Y*s.aw + X)*4 + 3]
    def rgba(s, row, frame, x, y):
        i = ((row*s.FH + y)*s.aw + frame*s.FW + x)*4
        return tuple(s.px[i:i+4])
    def opaque_rows(s, row, frame):
        return [y for y in range(s.FH)
                if any(s.alpha(row, frame, x, y) for x in range(s.FW))]
    def diff(s, row, a, b, y0=0, y1=None):
        y1 = s.FH if y1 is None else y1
        return sum(1 for y in range(y0, y1) for x in range(s.FW)
                   if s.rgba(row, a, x, y) != s.rgba(row, b, x, y))

def main():
    FW, FH, FRAMES, rows, ns = load_layout()
    idle  = ns.get('IDLE_FRAMES', [0, 1, 2, 3])
    blink = ns.get('BLINK_FRAME', 4)
    walk  = ns.get('WALK_FRAMES', list(range(5, 13)))
    chars = ns['ORDER']
    only  = [a for a in sys.argv[1:] if not a.startswith('-')]
    if only: chars = [c for c in chars if c in only]
    px, aw, ah = read_png(os.path.join(ROOT, 'assets', 'atlas.png'))
    sh = Sheet(px, aw, FW, FH)
    R = {k: ns for k in ()}
    rowof = {k: i for i, k in enumerate(rows)}
    HEAVY = {'gorou', 'seryn', 'rei', 'kotone', 'solen', 'teo'}

    print('atlas %dx%d  =  %d frames x %d rows  (%dx%d each)'
          % (aw, ah, aw//FW, ah//FH, FW, FH))
    print('checking %d characters\n' % len(chars))
    fails = {}

    # 1. edge clipping ------------------------------------------------------
    bad = []
    for c in chars:
        r = rowof[c]; hit = []
        for f in range(FRAMES):
            if (any(sh.alpha(r, f, 0, y) for y in range(FH)) or
                any(sh.alpha(r, f, FW-1, y) for y in range(FH)) or
                any(sh.alpha(r, f, x, 0) for x in range(FW))):
                hit.append(f)
        if hit: bad.append('%s %s' % (c, hit))
    fails['edge clipping'] = bad
    print('1. EDGE CLIPPING   opaque pixels on column 0, column %d or row 0' % (FW-1))
    print('   %s' % ('none' if not bad else '%d characters: %s' % (len(bad), '; '.join(bad[:8]))))

    # 2. idle stability -----------------------------------------------------
    bad, worst = [], 0
    lo = int(FH*0.60)              # hips down = the lower 40% of the frame
    for c in chars:
        r = rowof[c]
        pairs = list(zip(idle, idle[1:] + idle[:1]))
        tot = [sh.diff(r, a, b) for a, b in pairs]
        low = [sh.diff(r, a, b, lo, FH) for a, b in pairs]
        worst = max(worst, max(tot))
        if max(low) > 0 or max(tot) > 400:
            bad.append('%s total=%s lower=%s' % (c, tot, low))
    fails['idle stability'] = bad
    print('2. IDLE STABILITY  changed pixels 0>1>2>3>0, and in the lower 40%')
    print('   worst total %d (target <400); lower body non-zero on %d characters'
          % (worst, len(bad)))
    for b in bad[:4]: print('     ', b)

    # 3. blink purity -------------------------------------------------------
    bad = []
    for c in chars:
        d = sh.diff(rowof[c], blink, idle[0])
        if d > 40: bad.append('%s %d px' % (c, d))
    fails['blink purity'] = bad
    print('3. BLINK PURITY    frame %d vs frame %d' % (blink, idle[0]))
    print('   %s' % ('all within a few eye pixels' if not bad
                     else '%d over 40 px: %s' % (len(bad), '; '.join(bad[:6]))))

    # 4. walk bob -----------------------------------------------------------
    bad = []
    for c in chars:
        r = rowof[c]
        tops = [min(sh.opaque_rows(r, f) or [0]) for f in walk]
        rng = max(tops) - min(tops)
        jump = max(abs(tops[i] - tops[(i+1) % len(tops)]) for i in range(len(tops)))
        lim = 3 if c in HEAVY else 2
        if rng > lim or jump > 1:
            bad.append('%s tops=%s range=%d jump=%d' % (c, tops, rng, jump))
    fails['walk bob'] = bad
    print('4. WALK BOB        head-top y across frames %d-%d' % (walk[0], walk[-1]))
    print('   %d characters over the limit (range<=2, <=3 heavy; jump<=1)' % len(bad))
    for b in bad[:4]: print('     ', b)

    # 5. alpha --------------------------------------------------------------
    seen = set()
    for i in range(3, len(px), 4*997):      # every 997th pixel is plenty
        seen.add(px[i])
    soft = sorted(v for v in seen if v not in (0, 255))
    fails['alpha'] = ['values %s' % soft] if soft else []
    print('5. ALPHA           only 0 and 255 allowed')
    print('   %s' % ('clean' if not soft else 'SOFT PIXELS: %s' % soft))

    # summary ---------------------------------------------------------------
    print('\n' + '-'*66)
    for k, v in fails.items():
        print('%-16s %s' % (k.upper(), 'PASS' if not v else 'FAIL (%d)' % len(v)))
    print('-'*66)
    return 1 if any(fails.values()) else 0

if __name__ == '__main__':
    sys.exit(main())
