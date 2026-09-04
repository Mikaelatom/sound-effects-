#!/usr/bin/env python3
"""
Twin Fate sprite generator.

Draws every character and enemy frame pixel by pixel, packs them into one
atlas PNG, and patches the base64 data URI straight into index.html.

    python3 tools/make_sprites.py            # atlas + patch index.html
    python3 tools/make_sprites.py closeup    # tools/closeup.png, heroes at 10x
    python3 tools/make_sprites.py preview    # tools/preview.png, whole sheet at 5x

Frames are 36x48. Row = actor, column = frame.
  Characters  0 idle-a  1 idle-b  2-5 walk  6 windup  7 strike  8 recover
              9 dash  10 cast  11 hurt
  Enemies     0-1 idle  2-5 move  6 telegraph  7 attack  8 special  9 hurt
"""
import zlib, struct, base64, os, sys, math

W, H = 36, 52
PAD = 4        # every actor draws 4px lower, leaving room for hair and horns
FRAMES = 12
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FEET = 46          # baseline every actor stands on
CX   = 18.0        # centre column

# ------------------------------------------------------------------ canvas ---
class Cv:
    def __init__(s, w, h, pad=0):
        s.w, s.h = w, h
        s.pad = pad
        s.px = [[None]*w for _ in range(h)]
    def set(s, x, y, c):
        # floor(v+.5), not round(): round() is banker's rounding, which collapses
        # the .5 offsets a taper limb emits onto even rows and stripes the sprite
        x, y = int(math.floor(x + 0.5)), int(math.floor(y + 0.5)) + s.pad
        if c is None or x < 0 or y < 0 or x >= s.w or y >= s.h: return
        s.px[y][x] = c
    def get(s, x, y):
        x, y = int(x), int(y)        # raw atlas coords; pad is already applied
        if x < 0 or y < 0 or x >= s.w or y >= s.h: return None
        return s.px[y][x]
    def rect(s, x, y, w, h, c):
        for j in range(int(round(h))):
            for i in range(int(round(w))): s.set(x+i, y+j, c)
    def ellipse(s, cx, cy, rx, ry, c, ymax=None, ymin=None):
        for y in range(int(cy-ry)-1, int(cy+ry)+2):
            if ymax is not None and y > ymax: continue
            if ymin is not None and y < ymin: continue
            for x in range(int(cx-rx)-1, int(cx+rx)+2):
                if ((x-cx)/rx)**2 + ((y-cy)/ry)**2 <= 1.0: s.set(x, y, c)
    def line(s, x0, y0, x1, y1, c, thick=1):
        n = int(max(abs(x1-x0), abs(y1-y0))) + 1
        for i in range(n):
            t = i/max(1, n-1)
            x, y = x0+(x1-x0)*t, y0+(y1-y0)*t
            for dy in range(thick):
                for dx in range(thick): s.set(x+dx, y+dy, c)
    def taper(s, x0, y0, x1, y1, c, w0, w1):
        """a limb: thick at the shoulder, thinner at the hand"""
        n = int(max(abs(x1-x0), abs(y1-y0))) + 1
        for i in range(n):
            t = i/max(1, n-1)
            x, y = x0+(x1-x0)*t, y0+(y1-y0)*t
            w = max(1, round(w0 + (w1-w0)*t))
            for dx in range(w):
                for dy in range(w):
                    s.set(x+dx-(w-1)/2, y+dy-(w-1)/2, c)
    def outline(s, col):
        add = []
        for y in range(s.h):
            for x in range(s.w):
                if s.px[y][x] is not None: continue
                for dx, dy in ((1,0),(-1,0),(0,1),(0,-1),(1,1),(-1,1),(1,-1),(-1,-1)):
                    n = s.get(x+dx, y+dy)
                    if n is not None and n != col:
                        add.append((x, y)); break
        for x, y in add: s.set(x, y, col)

def write_png(path, px, w, h):
    raw = b''
    for y in range(h):
        row = bytearray()
        for x in range(w):
            c = px[y][x]
            if c is None: row += b'\x00\x00\x00\x00'
            else:
                r, g, b = hx(c); row += bytes((r, g, b, 255))
        raw += b'\x00' + bytes(row)
    def chunk(tag, data):
        return (struct.pack('>I', len(data)) + tag + data
                + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff))
    png = (b'\x89PNG\r\n\x1a\n'
           + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
           + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))
    open(path, 'wb').write(png)

def hx(c):
    c = c.lstrip('#')
    return (int(c[0:2],16), int(c[2:4],16), int(c[4:6],16))

# ----------------------------------------------------------------- palettes --
CHARS = {
 'aoi': dict(ink='#191338', hair='#2a4fa8', hair2='#5a86ea', hair3='#a8caff', shine='#dbe8ff',
    skin='#ffdcc0', skin2='#e5a184', blush='#ff9db0', eye='#3fc9ff', eye2='#12608f', brow='#2a4fa8',
    cloth='#f2f6ff', cloth2='#ffffff', cloth3='#8fa4d6', trim='#3fc9ff', accent='#ff5fa2',
    metal='#eaf0ff', grip='#2a2440', style='ponytail', wep='katana'),
 'kagura': dict(ink='#160d2c', hair='#4d2478', hair2='#8e51cc', hair3='#c79cf5', shine='#e7d2ff',
    skin='#ffe0c6', skin2='#daa385', blush='#ff9db0', eye='#ffd24d', eye2='#a86c12', brow='#4d2478',
    cloth='#33215e', cloth2='#5b3f9c', cloth3='#1b1136', trim='#8e51cc', accent='#ffcc4d',
    metal='#4a3672', grip='#241a3e', style='long', wep='staff'),
 'ren': dict(ink='#0d0a17', hair='#201b38', hair2='#453c78', hair3='#7d70ae', shine='#a99ddb',
    skin='#f0cba9', skin2='#c2967a', blush='#e8807f', eye='#ff4d5d', eye2='#8f1d2c', brow='#201b38',
    cloth='#332b56', cloth2='#524791', cloth3='#191430', trim='#ff4d5d', accent='#ff4d5d',
    metal='#d5cdfa', grip='#141020', style='spiky', wep='daggers'),
 'hinata': dict(ink='#38290f', hair='#c99527', hair2='#ffdc7f', hair3='#fff5cf', shine='#ffffff',
    skin='#ffe6cf', skin2='#e0ad88', blush='#ffa8a8', eye='#57e08d', eye2='#1a7a4c', brow='#c99527',
    cloth='#fffaf0', cloth2='#ffffff', cloth3='#d9c398', trim='#7cffa8', accent='#7cffa8',
    metal='#c04a6a', grip='#8d2f4c', style='bob', wep='tome'),
}
ORDER = ['aoi', 'kagura', 'ren', 'hinata']

# -------------------------------------------------------------------- poses --
# bob = vertical bounce, lean = upper-body shift, hb/hf = back/front hand
POSES = [
 dict(bob=0,  lean=0,  legs='stand',   hb=(11,32), hf=(25,32), wep=0.00, eye='open',   sway=0),
 dict(bob=1,  lean=0,  legs='stand',   hb=(11,33), hf=(25,33), wep=0.05, eye='open',   sway=1),
 dict(bob=0,  lean=1,  legs='walk0',   hb=(9,31),  hf=(27,30), wep=0.10, eye='open',   sway=3),
 dict(bob=1,  lean=0,  legs='walk1',   hb=(11,33), hf=(25,33), wep=0.00, eye='open',   sway=0),
 dict(bob=0,  lean=-1, legs='walk2',   hb=(11,30), hf=(26,31), wep=0.10, eye='open',   sway=-3),
 dict(bob=1,  lean=0,  legs='walk3',   hb=(11,33), hf=(25,33), wep=0.00, eye='open',   sway=0),
 dict(bob=0,  lean=-3, legs='brace',   hb=(8,30),  hf=(13,23), wep=-1.0, eye='fierce', sway=-4),
 dict(bob=0,  lean=4,  legs='lunge',   hb=(13,33), hf=(29,21), wep=1.00, eye='fierce', sway=5),
 dict(bob=1,  lean=2,  legs='lunge',   hb=(12,33), hf=(29,34), wep=0.55, eye='fierce', sway=3),
 dict(bob=2,  lean=6,  legs='dash',    hb=(8,34),  hf=(28,29), wep=0.25, eye='fierce', sway=7),
 dict(bob=-1, lean=0,  legs='stand',   hb=(9,23),  hf=(27,23), wep=-0.6, eye='closed', sway=-2),
 dict(bob=2,  lean=-4, legs='stagger', hb=(7,28),  hf=(28,28), wep=0.20, eye='hurt',   sway=-6),
]

# --------------------------------------------------------------- body parts --
def draw_legs(c, p, kind):
    sk, sk2 = p['skin'], p['skin2']
    boot, boot2 = p['cloth3'], p['cloth2']
    def leg(x, top, fdx, bend=0):
        c.rect(x, top, 4, FEET-3-top, sk)
        c.rect(x, top, 1, FEET-3-top, sk2)              # inner shading
        c.rect(x, FEET-3, 4, 3, boot)                   # boot
        c.rect(x, FEET-3, 4, 1, boot2)                  # boot cuff
        c.rect(x+fdx, FEET-1, 5, 2, boot)               # toe
    if kind == 'stand':     leg(12, 36, -1); leg(20, 36, 0)
    elif kind == 'walk0':   leg(9,  36, -2); leg(22, 38, 1)
    elif kind == 'walk1':   leg(13, 38, -1); leg(19, 38, 0)
    elif kind == 'walk2':   leg(22, 36, 1);  leg(10, 38, -2)
    elif kind == 'walk3':   leg(13, 38, -1); leg(19, 38, 0)
    elif kind == 'lunge':   leg(8,  38, -2); leg(23, 35, 1)
    elif kind == 'brace':   leg(10, 36, -2); leg(21, 36, 1)
    elif kind == 'stagger': leg(10, 38, -2); leg(21, 38, 1)
    elif kind == 'dash':
        c.rect(8, 39, 7, 4, sk); c.rect(5, FEET-3, 8, 3, boot)
        c.rect(20, 35, 5, 6, sk); c.rect(20, FEET-3, 8, 3, boot)

def draw_torso(c, p, pose):
    cl, cl2, cl3, tr = p['cloth'], p['cloth2'], p['cloth3'], p['trim']
    dx, dy = pose['lean']//2, pose['bob']
    x0 = 12 + dx
    c.rect(x0, 24+dy, 12, 12, cl)                        # chest
    c.rect(x0, 24+dy, 12, 3, cl2)                        # lit shoulders
    c.rect(x0, 30+dy, 12, 6, cl3)                        # shadow under the ribs
    c.rect(x0+2, 24+dy, 8, 12, cl)
    c.rect(x0+4, 23+dy, 4, 3, p['skin2'])                # collarbone shadow
    c.rect(x0+1, 25+dy, 1, 11, tr)                       # piping
    c.rect(x0+10, 25+dy, 1, 11, tr)
    c.rect(x0, 33+dy, 12, 3, p['accent'])                # sash / belt
    c.rect(x0, 33+dy, 12, 1, cl2)
    if p['style'] == 'bob':                              # dress
        c.rect(x0-2, 36+dy, 16, 4, cl)
        c.rect(x0-3, 39+dy, 18, 2, cl2)
        c.rect(x0-3, 40+dy, 18, 1, cl3)
        for i in range(0, 18, 4): c.set(x0-3+i, 40+dy, tr)
    elif p['style'] == 'long':                           # robe
        c.rect(x0-1, 36+dy, 14, 5, cl)
        c.rect(x0-2, 40+dy, 16, 2, cl3)
        for i in range(0, 16, 5): c.rect(x0-2+i, 39+dy, 1, 3, tr)
    else:                                                # coat tails
        c.rect(x0-1, 36+dy, 14, 2, cl)
        c.rect(x0-1, 37+dy, 5, 5, cl3)
        c.rect(x0+9, 37+dy, 5, 5, cl3)
        c.rect(x0-1, 37+dy, 5, 1, tr); c.rect(x0+9, 37+dy, 5, 1, tr)

def draw_arm(c, p, pose, hand, back):
    sk  = p['skin2'] if back else p['skin']
    cl  = p['cloth3'] if back else p['cloth']
    cl2 = p['cloth3'] if back else p['cloth2']
    dy  = pose['bob']
    sx  = (14 if back else 22) + pose['lean']//2
    sy  = 26 + dy
    hxp, hyp = hand[0], hand[1] + dy
    mx, my = (sx+hxp)/2, (sy+hyp)/2 - 1
    c.taper(sx, sy, mx, my, cl, 5, 4)                    # sleeve
    c.taper(sx, sy, mx, my-1, cl2, 2, 1)                 # sleeve highlight
    c.taper(mx, my, hxp, hyp, sk, 4, 3)                  # forearm
    c.rect(hxp-1, hyp-1, 3, 3, sk)                       # hand
    c.set(hxp-1, hyp+1, p['skin2'])

def eye(c, p, x, y, outer, mode):
    """a 5x6 anime eye. outer = -1 for the left eye, +1 for the right"""
    ink, ec, ed = p['ink'], p['eye'], p['eye2']
    if mode == 'closed':
        for i in range(5): c.set(x+i, y+3, ink)
        c.set(x+(0 if outer < 0 else 4), y+2, ink)
        c.set(x+(1 if outer < 0 else 3), y+2, ink)
        return
    if mode == 'hurt':
        for i in range(5): c.set(x+i, y+2, ink); c.set(x+i, y+4, ink)
        return
    c.rect(x, y+1, 5, 4, '#ffffff')                      # sclera
    c.rect(x+1, y+1, 3, 4, ec)                           # iris
    c.rect(x+1, y+3, 3, 2, ed)                           # iris shadow
    c.rect(x+2, y+2, 1, 3, ink)                          # pupil
    for i in range(5): c.set(x+i, y, ink)                # upper lash
    c.set(x+(0 if outer < 0 else 4), y+1, ink)           # outer corner, thicker
    c.set(x+(0 if outer < 0 else 4), y+2, ink)
    hx2 = x + (3 if outer < 0 else 1)                    # catchlight
    c.set(hx2, y+1, '#ffffff'); c.set(hx2, y+2, '#ffffff')
    c.set(x+(3 if outer > 0 else 1), y+4, ed)            # small lower glint
    for i in range(1, 4): c.set(x+i, y+5, p['skin2'])    # lower lid

def draw_head(c, p, pose):
    sk, sk2 = p['skin'], p['skin2']
    dy = pose['bob'] + (-1 if pose['legs'] in ('walk0','walk2') else 0)
    cx = CX + pose['lean']*0.55
    cy = 12 + dy
    c.ellipse(cx, cy, 9.6, 10.2, sk)
    c.ellipse(cx, cy+3, 8.2, 7.4, sk)                    # cheeks
    c.rect(cx-9, cy-1, 2, 4, sk2); c.rect(cx+7, cy-1, 2, 4, sk2)   # ears
    for x in range(int(cx-6), int(cx+7)): c.set(x, cy+8, sk2)      # jaw shadow
    c.rect(cx-3, cy+10, 6, 3, sk)                                   # neck
    c.rect(cx-3, cy+10, 6, 1, sk2)
    m = pose['eye']
    ey = cy - 1
    eye(c, p, cx-8, ey, -1, m)
    eye(c, p, cx+3, ey, +1, m)
    if m != 'closed':                                    # brows
        for i in range(4):
            c.set(cx-8+i, ey-3 + (1 if m == 'fierce' and i > 1 else 0), p['brow'])
            c.set(cx+4+i, ey-3 + (1 if m == 'fierce' and i < 2 else 0), p['brow'])
    c.set(cx-0.5, ey+4, sk2)                             # nose
    mouth = ey+7
    if m == 'fierce':
        c.rect(cx-1.5, mouth, 3, 2, p['ink']); c.rect(cx-1, mouth+1, 2, 1, '#ffffff')
    else:
        c.rect(cx-1, mouth, 2, 1, p['skin2'])
    for i in range(3):                                   # blush
        c.set(cx-9+i, ey+4, p['blush']); c.set(cx+6+i, ey+4, p['blush'])
        c.set(cx-8+i, ey+5, p['blush']); c.set(cx+5+i, ey+5, p['blush'])
    return cx, cy

# --------------------------------------------------------------------- hair --
def hair_back(c, p, pose, cx, cy):
    st, h1, h2, sway = p['style'], p['hair'], p['hair2'], pose['sway']
    if st == 'ponytail':
        c.taper(cx+6, cy-6, cx+11+sway, cy+1, h1, 7, 6)
        c.taper(cx+10+sway//2, cy, cx+13+sway, cy+18, h1, 6, 4)
        c.taper(cx+11+sway//2, cy+1, cx+13+sway, cy+14, h2, 3, 2)
        c.taper(cx+12+sway, cy+16, cx+14+sway, cy+22, h1, 3, 2)
    elif st == 'long':
        c.rect(cx-11, cy-6, 5, 26, h1)                   # left curtain
        c.rect(cx+6, cy-6, 5, 26, h1)                    # right curtain
        c.rect(cx-7, cy-6, 14, 14, h1)                   # mass behind the head
        c.rect(cx-10, cy+4, 3, 14, h2)
        c.rect(cx+7, cy+4, 3, 14, h2)
        c.taper(cx-9, cy+18, cx-11+sway, cy+25, h1, 5, 3)
        c.taper(cx+8, cy+18, cx+10+sway, cy+25, h1, 5, 3)
    elif st == 'spiky':
        for sx, sy in ((-10,-4), (-6,-9), (0,-12), (6,-9), (10,-4)):
            c.taper(cx+sx*0.7, cy+sy*0.5, cx+sx+sway*0.4, cy+sy-2, h1, 5, 2)
        c.rect(cx-11, cy-4, 4, 12, h1); c.rect(cx+7, cy-4, 4, 12, h1)
        c.taper(cx-5, cy+11, cx-11+sway, cy+17, p['accent'], 4, 3)   # scarf
        c.taper(cx-9+sway//2, cy+15, cx-13+sway, cy+22, p['accent'], 3, 2)
    elif st == 'bob':
        c.ellipse(cx, cy-1, 11.6, 11.4, h1)
        c.rect(cx-11, cy-2, 4, 12, h1); c.rect(cx+7, cy-2, 4, 12, h1)

def hair_front(c, p, pose, cx, cy):
    st, h1, h2, h3 = p['style'], p['hair'], p['hair2'], p['hair3']
    sh, ac, sway = p['shine'], p['accent'], pose['sway']
    top = cy - 5                                          # never below the brows
    c.ellipse(cx, cy-2.5, 10.6, 10.2, h1, ymax=int(top))
    c.ellipse(cx, cy-4.5, 9.0, 8.0, h2, ymax=int(top-2))
    # the anime hair-shine band: broken segments across the crown
    for i, (sx, w) in enumerate(((-8,3), (-3,4), (2,3), (6,2))):
        c.rect(cx+sx, cy-8+abs(i-1)*0.5, w, 1, h3)
        c.rect(cx+sx, cy-9+abs(i-1)*0.5, max(1, w-1), 1, sh)
    # fringe strands, pointed, hanging between and beside the eyes
    for fx, fl in ((-10,4), (-6,6), (-1,7), (3,6), (8,4)):
        c.rect(cx+fx, cy-9, 2, fl, h1)
        c.set(cx+fx, cy-9+fl, h1)
        c.set(cx+fx, cy-8, h2)
    # side locks framing the face
    c.rect(cx-11, cy-5, 3, 9, h1); c.rect(cx+8, cy-5, 3, 9, h1)
    c.rect(cx-11, cy-5, 1, 7, h2); c.rect(cx+10, cy-5, 1, 7, h2)
    c.set(cx-10, cy+4, h1); c.set(cx+9, cy+4, h1)
    if st == 'ponytail':
        c.rect(cx+4, cy-10, 4, 3, ac); c.rect(cx+5, cy-11, 2, 1, ac)
    elif st == 'long':
        c.rect(cx-2, cy-12, 5, 3, ac)
        c.rect(cx-1, cy-13, 3, 1, ac)
        c.rect(cx-13, cy-4, 3, 13, h1); c.rect(cx+10, cy-4, 3, 13, h1)
    elif st == 'spiky':
        for sx, sy in ((-9,-7), (-4,-10), (1,-11), (6,-9)):
            c.taper(cx+sx, cy+sy+4, cx+sx+sway//3, cy+sy-1, h2, 4, 2)
    elif st == 'bob':
        c.rect(cx-13, cy-5, 3, 11, h1); c.rect(cx+10, cy-5, 3, 11, h1)
        c.rect(cx-14, cy-8, 3, 5, ac);  c.rect(cx+11, cy-8, 3, 5, ac)
        c.rect(cx-14, cy-8, 3, 1, '#ffffff'); c.rect(cx+11, cy-8, 3, 1, '#ffffff')

# ------------------------------------------------------------------ weapons --
def draw_weapon(c, p, pose):
    w, dy = p['wep'], pose['bob']
    hxp, hyp = pose['hf'][0], pose['hf'][1] + dy
    ph = pose['wep']
    deg = -25 + (ph * 70 if ph >= 0 else ph * 110)
    ang = math.radians(deg)
    ux, uy = math.cos(ang), math.sin(ang)
    if w == 'katana':
        L = 15
        hxp = min(hxp, 27)
        c.taper(hxp-ux*6, hyp-uy*6, hxp, hyp, p['grip'], 3, 3)              # tsuka
        c.line(hxp-ux*2-uy*2, hyp-uy*2+ux*2, hxp-ux*2+uy*2, hyp-uy*2-ux*2, p['accent'], 2)
        nx, ny = -uy, ux                                                    # blade normal
        c.taper(hxp+ux*2+nx*1.6, hyp+uy*2+ny*1.6,
                hxp+ux*L+nx*1.2, hyp+uy*L+ny*1.2, p['ink'], 2, 2)            # spine
        c.taper(hxp+ux*2, hyp+uy*2, hxp+ux*L, hyp+uy*L, p['metal'], 3, 2)   # blade
        c.line(hxp+ux*3-nx, hyp+uy*3-ny, hxp+ux*(L-1)-nx, hyp+uy*(L-1)-ny, '#ffffff', 1)
        c.set(hxp+ux*L, hyp+uy*L, '#ffffff')
    elif w == 'staff':
        sx = min(max(hxp + 5, 24), 32)      # never let the shaft cross her face
        tilt = ux*3
        c.taper(sx, min(hyp+11, FEET-1), sx+tilt, hyp-15, p['metal'], 3, 3)
        c.line(sx+1, min(hyp+10, FEET-2), sx+tilt+1, hyp-14, p['grip'], 1)
        ox, oy = sx+tilt, hyp-19
        c.ellipse(ox, oy, 4.2, 4.2, p['accent'])
        c.ellipse(ox, oy, 2.6, 2.6, '#ffe9a8')
        c.ellipse(ox-1, oy-1, 1.2, 1.2, '#ffffff')
        for dx2, dy2 in ((-6,0), (6,0), (0,-6), (0,6)): c.set(ox+dx2, oy+dy2, p['hair3'])
    elif w == 'daggers':
        nx, ny = -uy, ux
        c.taper(hxp+nx*1.4, hyp+ny*1.4, hxp+ux*8+nx, hyp+uy*8+ny, p['ink'], 2, 2)
        c.taper(hxp, hyp, hxp+ux*8, hyp+uy*8, p['metal'], 3, 2)
        c.line(hxp+ux*2, hyp+uy*2, hxp+ux*7, hyp+uy*7, '#ffffff', 1)
        c.rect(hxp-1, hyp, 3, 2, p['grip'])
        bx, by = pose['hb'][0], pose['hb'][1] + dy
        c.taper(bx, by, bx-abs(ux)*7, by-uy*5, p['metal'], 3, 2)
        c.rect(bx-1, by, 3, 2, p['grip'])
    elif w == 'tome':
        bx, by = min(hxp-2, 26), hyp - 5
        c.rect(bx, by, 10, 9, p['metal'])                 # cover
        c.rect(bx+1, by+1, 8, 7, '#fffaf0')               # pages
        for k in range(2, 8): c.set(bx+2, by+k-1, p['cloth3'])
        for k in range(2, 8): c.set(bx+7, by+k-1, p['cloth3'])
        c.rect(bx+4, by, 2, 9, p['grip'])                 # spine
        c.rect(bx+4, by-1, 2, 1, p['accent'])
        c.rect(bx+4, by+9, 2, 1, p['accent'])
        c.set(bx+2, by+3, p['accent']); c.set(bx+7, by+5, p['accent'])

def draw_char(key, frame):
    p, pose = CHARS[key], POSES[frame]
    c = Cv(W, H, PAD)
    dy = pose['bob'] + (-1 if pose['legs'] in ('walk0','walk2') else 0)
    cx, cy = CX + pose['lean']*0.55, 12 + dy
    hair_back(c, p, pose, cx, cy)
    draw_arm(c, p, pose, pose['hb'], True)
    draw_legs(c, p, pose['legs'])
    draw_torso(c, p, pose)
    draw_head(c, p, pose)
    hair_front(c, p, pose, cx, cy)
    draw_arm(c, p, pose, pose['hf'], False)
    draw_weapon(c, p, pose)
    c.outline(p['ink'])
    return c

# ------------------------------------------------------------------ enemies --
MOBS = {
 'slime': dict(ink='#0c2b1c', body='#43c882', body2='#8bf0bc', body3='#1f7d51',
               eye='#0c2b1c', glow='#d6ffe9'),
 'bat':   dict(ink='#180a24', body='#8154d6', body2='#c2a2ff', body3='#4d2b8f',
               eye='#ffd24d', glow='#ffe9a8'),
 'imp':   dict(ink='#2e0c0c', body='#ff8b4d', body2='#ffc79f', body3='#c4562a',
               eye='#ffe14d', glow='#fff3c0', cloth='#8f2020', cloth2='#c93b3b'),
 'brute': dict(ink='#1b1020', body='#c05a4d', body2='#ec9484', body3='#83332c',
               eye='#ffe14d', glow='#fff3c0', cloth='#4a2a3a', cloth2='#6d4257'),
 'boss':  dict(ink='#120818', body='#a86bff', body2='#dcc0ff', body3='#6b3fbd',
               eye='#ff5d5d', glow='#ffc0c0', cloth='#2a1b4d', cloth2='#4a3480'),
}
MOB_ORDER = ['slime', 'bat', 'imp', 'brute', 'boss']

# per-frame animation parameters, one entry per frame 0..9
SLIME_A = [(0,0), (1,0), (3,-1), (-2,-6), (-3,-9), (2,-2), (4,1), (-4,-3), (0,0), (5,2)]
BAT_A   = [(1,0), (4,1), (0,0), (5,2), (8,3), (4,1), (-2,-2), (7,4), (2,0), (3,-3)]
IMP_A   = [(0,0), (1,0), (0,1), (1,2), (0,1), (-1,0), (-2,0), (2,0), (0,0), (3,0)]
BRUTE_A = [(0,0), (1,0), (0,-1), (2,0), (0,-1), (2,0), (-2,0), (3,0), (0,-2), (2,0)]

def draw_slime(c, m, f):
    sq, hop = SLIME_A[f]
    base = FEET + (0 if f not in (3,4,7) else 0)
    w, h = 13 + sq, 9 - sq*0.6
    cy = base - h - max(0, -hop)
    c.ellipse(CX, cy, w, h, m['body'])
    c.ellipse(CX, cy - h*0.35, w*0.72, h*0.55, m['body2'])       # gloss
    c.ellipse(CX - w*0.35, cy - h*0.5, 2.4, 1.6, m['glow'])      # highlight
    c.ellipse(CX, cy + h*0.5, w*0.85, h*0.35, m['body3'])        # underside
    ex = 4.5
    if f == 9:                                                    # hurt: X eyes
        for i in range(-2, 3):
            c.set(CX-ex+i, cy-1+i, m['eye']); c.set(CX-ex+i, cy-1-i, m['eye'])
            c.set(CX+ex+i, cy-1+i, m['eye']); c.set(CX+ex+i, cy-1-i, m['eye'])
    else:
        squint = 1 if f in (6, 7) else 0
        for dx in (-ex, ex):
            c.rect(CX+dx-1.5, cy-2+squint, 3, 4-squint*2, m['eye'])
            if not squint: c.set(CX+dx+0.5, cy-1, '#ffffff')
        c.rect(CX-2, cy+3, 4, 1, m['eye'])                        # little mouth
    if f in (3, 4):                                               # airborne drips
        c.ellipse(CX-6, base-2, 2, 1.4, m['body3'])
        c.ellipse(CX+6, base-1, 2.4, 1.2, m['body3'])

def draw_bat(c, m, f):
    flap, bob = BAT_A[f]
    cy = 22 - bob
    SPAN = 9
    tipy = cy - 2 - flap                                          # wing tip rises as it flaps
    for sgn in (-1, 1):
        for k in range(1, SPAN+1):
            t  = k/SPAN
            x  = CX + sgn*(4+k)
            up = (cy-4) + (tipy - (cy-4))*t
            lo = up + max(2, 8 - 5*t)
            if k % 3 == 0: lo -= 1.5                              # scalloped trailing edge
            c.line(x, up, x, lo, m['body3'], 1)
            c.set(x, up, m['body'])
            c.set(x, up+1, m['body'])
            c.set(x, lo, m['body'])
        for k in (3, 6, SPAN):                                    # wing fingers
            t  = k/SPAN
            x  = CX + sgn*(4+k)
            up = (cy-4) + (tipy - (cy-4))*t
            c.line(x, up, x, up + max(2, 8 - 5*t), m['body'], 1)
    c.ellipse(CX, cy, 5.2, 6.4, m['body'])                        # body
    c.ellipse(CX, cy-1, 3.8, 4.6, m['body2'])
    c.ellipse(CX, cy+4, 3.0, 2.2, m['body3'])
    c.taper(CX-3, cy-5, CX-6, cy-11, m['body'], 3, 2)             # ears
    c.taper(CX+3, cy-5, CX+6, cy-11, m['body'], 3, 2)
    if f == 9:
        for i in range(-1, 2):
            c.set(CX-3+i, cy-1+i, m['eye']); c.set(CX-3+i, cy-1-i, m['eye'])
            c.set(CX+3+i, cy-1+i, m['eye']); c.set(CX+3+i, cy-1-i, m['eye'])
    else:
        for dx in (-4, 1):
            c.rect(CX+dx, cy-2, 3, 3, m['eye'])
            c.set(CX+dx+2, cy-2, m['glow'])
    if f in (6, 7):
        c.rect(CX-2, cy+2, 1, 3, '#ffffff'); c.rect(CX+1, cy+2, 1, 3, '#ffffff')
    else:
        c.rect(CX-2, cy+2, 4, 1, m['ink'])

def draw_imp(c, m, f):
    lean, bob = IMP_A[f]
    hy = 13 + bob
    c.ellipse(CX+lean*0.4, hy, 7.6, 6.8, m['body'])               # head
    c.ellipse(CX+lean*0.4, hy-2, 6.0, 4.4, m['body2'])
    for sgn in (-1, 1):                                           # horns
        c.taper(CX+sgn*6, hy-4, CX+sgn*9, hy-11, m['body3'], 4, 2)
    c.rect(CX-9, hy+1, 3, 4, m['body3']); c.rect(CX+6, hy+1, 3, 4, m['body3'])  # ears
    if f == 9:
        for i in range(-1, 2):
            c.set(CX-4+i, hy+i, m['eye']); c.set(CX-4+i, hy-i, m['eye'])
            c.set(CX+3+i, hy+i, m['eye']); c.set(CX+3+i, hy-i, m['eye'])
    else:
        for dx in (-5, 2):
            c.rect(CX+dx, hy-1, 3, 3, m['eye'])
            c.set(CX+dx+2, hy-1, m['glow'])
    c.rect(CX-3, hy+4, 6, 1, m['ink'])
    c.rect(CX-6, hy+8, 12, 12, m['cloth'])                        # robe
    c.rect(CX-5, hy+9, 10, 4, m['cloth2'])
    c.rect(CX-7, hy+18, 14, 4, m['cloth'])
    c.rect(CX-7, hy+21, 14, 1, m['ink'])
    c.taper(CX-6, hy+10, CX-9-lean, hy+16, m['body'], 4, 3)       # arms
    c.taper(CX+6, hy+10, CX+9+lean, hy+16, m['body'], 4, 3)
    if f in (6, 7, 8):                                            # conjured bolt
        ox, oy = CX + 11 + lean*2, hy + 15
        r = 2.5 if f == 6 else 4.0
        c.ellipse(ox, oy, r, r, m['eye'])
        c.ellipse(ox, oy, r*0.5, r*0.5, m['glow'])

def draw_brute(c, m, f, boss=False):
    lean, bob = BRUTE_A[f]
    hy = 12 + bob
    cxx = CX + lean*0.5
    c.ellipse(cxx, hy, 9.4, 8.4, m['body'])                       # head
    c.ellipse(cxx, hy-2, 7.6, 5.6, m['body2'])
    for sgn in (-1, 1):                                           # horns
        c.taper(cxx+sgn*7, hy-5, cxx+sgn*(12 if boss else 10), hy-(15 if boss else 12), m['body3'], 5, 2)
        if boss: c.taper(cxx+sgn*9, hy-10, cxx+sgn*6, hy-16, m['body3'], 3, 2)
    if f == 9:
        for i in range(-2, 3):
            c.set(cxx-5+i, hy+i, m['eye']); c.set(cxx-5+i, hy-i, m['eye'])
            c.set(cxx+4+i, hy+i, m['eye']); c.set(cxx+4+i, hy-i, m['eye'])
    else:
        for dx in (-6, 3):
            c.rect(cxx+dx, hy-1, 4, 3, m['eye'])
            c.rect(cxx+dx, hy-1, 4, 1, m['glow'])
    if f in (6, 7, 8):                                            # open jaw
        c.rect(cxx-4, hy+4, 8, 4, m['ink'])
        for i in range(0, 8, 2): c.set(cxx-4+i, hy+4, '#ffffff')
    else:
        c.rect(cxx-4, hy+5, 8, 1, m['ink'])
    c.rect(cxx-8, hy+10, 16, 14, m['cloth'])                      # torso
    c.rect(cxx-6, hy+11, 12, 6, m['body2'])
    c.rect(cxx-8, hy+20, 16, 4, m['cloth2'])
    ax = 12 if f in (6, 8) else 9
    c.taper(cxx-8, hy+12, cxx-ax-lean, hy+(6 if f in (6,8) else 21), m['body'], 6, 5)
    c.taper(cxx+8, hy+12, cxx+ax+lean, hy+(6 if f in (6,8) else 21), m['body'], 6, 5)
    c.rect(cxx-8, hy+24, 6, FEET-(hy+24), m['cloth'])             # legs
    c.rect(cxx+2, hy+24, 6, FEET-(hy+24), m['cloth'])
    c.rect(cxx-9, FEET-3, 8, 3, m['ink']); c.rect(cxx+1, FEET-3, 8, 3, m['ink'])
    if f == 7:                                                    # impact dust
        for dx in (-14, -10, 10, 14): c.ellipse(cxx+dx, FEET-2, 2.5, 1.5, m['body3'])

def draw_mob(key, f):
    m = MOBS[key]; c = Cv(W, H, PAD)
    if   key == 'slime': draw_slime(c, m, f)
    elif key == 'bat':   draw_bat(c, m, f)
    elif key == 'imp':   draw_imp(c, m, f)
    elif key == 'brute': draw_brute(c, m, f, False)
    elif key == 'boss':  draw_brute(c, m, f, True)
    c.outline(m['ink'])
    return c

# ------------------------------------------------------------------- atlas ---
MOB_FRAMES = 10

def build():
    rows = ORDER + MOB_ORDER
    aw, ah = W*FRAMES, H*len(rows)
    px = [[None]*aw for _ in range(ah)]
    for r, key in enumerate(rows):
        for f in range(FRAMES):
            if key in CHARS:            c = draw_char(key, f)
            elif f < MOB_FRAMES:        c = draw_mob(key, f)
            else:                       continue
            for y in range(H):
                for x in range(W):
                    px[r*H+y][f*W+x] = c.px[y][x]
    return px, aw, ah, rows

def zoom(px, cols, rows_idx, s, path):
    cw, ch = W*len(cols)*s, H*len(rows_idx)*s
    big = [[None]*cw for _ in range(ch)]
    for ri, r in enumerate(rows_idx):
        for ci, f in enumerate(cols):
            for y in range(H):
                for x in range(W):
                    c = px[r*H+y][f*W+x]
                    bg = '#241b38' if (ci+ri) % 2 else '#1b1428'
                    for j in range(s):
                        for i in range(s):
                            big[(ri*H+y)*s+j][(ci*W+x)*s+i] = c or bg
    write_png(path, big, cw, ch)
    print(path.split('/')[-1], '%dx%d' % (cw, ch))

def main():
    px, aw, ah, rows = build()
    os.makedirs(os.path.join(ROOT, 'assets'), exist_ok=True)
    atlas = os.path.join(ROOT, 'assets', 'atlas.png')
    write_png(atlas, px, aw, ah)
    print('atlas %dx%d -> assets/atlas.png (%d bytes)' % (aw, ah, os.path.getsize(atlas)))

    if 'closeup' in sys.argv:
        zoom(px, [0, 3, 7, 9, 10], [0,1,2,3], 10, os.path.join(ROOT, 'tools', 'closeup.png'))
    if 'mobs' in sys.argv:
        zoom(px, [0, 2, 3, 6, 7, 9], [4,5,6,7,8], 8, os.path.join(ROOT, 'tools', 'mobs.png'))
    if 'preview' in sys.argv:
        zoom(px, list(range(FRAMES)), list(range(len(rows))), 5, os.path.join(ROOT, 'tools', 'preview.png'))

    uri = 'data:image/png;base64,' + base64.b64encode(open(atlas, 'rb').read()).decode()
    idx = os.path.join(ROOT, 'index.html')
    src = open(idx).read()
    import re
    new, n = re.subn(r'const ATLAS_SRC = "[^"]*";', lambda _: 'const ATLAS_SRC = "%s";' % uri, src)
    if n:
        open(idx, 'w').write(new)
        print('patched index.html (%d KB of atlas)' % (len(uri)//1024))
    else:
        print('index.html has no ATLAS_SRC -- add it, then re-run')

if __name__ == '__main__':
    main()
