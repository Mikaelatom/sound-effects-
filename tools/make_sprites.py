#!/usr/bin/env python3
"""
Twin Fate sprite generator.

Draws every character and enemy frame pixel by pixel, packs them into one
atlas PNG, and patches the base64 data URI straight into index.html.

    python3 tools/make_sprites.py            # atlas + patch index.html
    python3 tools/make_sprites.py preview    # also tools/preview.png at 6x

Frames are 24x28. Row = actor, column = animation frame.
Characters: 0 idle-a  1 idle-b  2-5 walk  6 windup  7 strike  8 recover
            9 dash  10 cast  11 hurt
Enemies:    0-1 idle  2 move  3 telegraph
"""
import zlib, struct, base64, os, sys

W, H = 24, 31
PAD = 3          # headroom: everything draws 3px lower than authored
FRAMES = 12
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ------------------------------------------------------------------ canvas ---
class Cv:
    def __init__(s, w, h, pad=0):
        s.w, s.h = w, h
        s.pad = pad
        s.px = [[None]*w for _ in range(h)]
    def set(s, x, y, c):
        x, y = int(round(x)), int(round(y)) + s.pad
        if c is None or x < 0 or y < 0 or x >= s.w or y >= s.h: return
        s.px[y][x] = c
    def get(s, x, y):
        x, y = int(x), int(y)   # raw atlas coords, pad already applied
        if x < 0 or y < 0 or x >= s.w or y >= s.h: return None
        return s.px[y][x]
    def rect(s, x, y, w, h, c):
        for j in range(int(h)):
            for i in range(int(w)): s.set(x+i, y+j, c)
    def ellipse(s, cx, cy, rx, ry, c, ymax=None):
        for y in range(int(cy-ry)-1, int(cy+ry)+2):
            if ymax is not None and y > ymax: continue
            for x in range(int(cx-rx)-1, int(cx+rx)+2):
                if ((x-cx)/rx)**2 + ((y-cy)/ry)**2 <= 1.0: s.set(x, y, c)
    def line(s, x0, y0, x1, y1, c, thick=1):
        n = int(max(abs(x1-x0), abs(y1-y0))) + 1
        for i in range(n):
            t = i/max(1, n-1)
            x, y = x0+(x1-x0)*t, y0+(y1-y0)*t
            for dy in range(thick):
                for dx in range(thick): s.set(x+dx, y+dy, c)
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
 'aoi': dict(ink='#191338', hair='#2a4fa8', hair2='#5a86ea', hair3='#a6c8ff',
    skin='#ffdcc0', skin2='#e3a184', eye='#3fc9ff', eye2='#0d3a63',
    cloth='#eef3ff', cloth2='#ffffff', cloth3='#93a6d6', accent='#ff5fa2',
    metal='#e9eeff', grip='#2a2440', style='ponytail', wep='katana'),
 'kagura': dict(ink='#160d2c', hair='#5a2c86', hair2='#9c5fd6', hair3='#d3a9ff',
    skin='#ffe0c6', skin2='#daa385', eye='#ffd24d', eye2='#5e3d0a',
    cloth='#2f1f57', cloth2='#553c92', cloth3='#170f2e', accent='#ffcc4d',
    metal='#4a3672', grip='#241a3e', style='long', wep='staff'),
 'ren': dict(ink='#0d0a17', hair='#241f3d', hair2='#4a4180', hair3='#8478b5',
    skin='#e8c4a6', skin2='#bf9174', eye='#ff4d5d', eye2='#54101c',
    cloth='#3b3363', cloth2='#5b51a0', cloth3='#1d1832', accent='#ff4d5d',
    metal='#cdc4f5', grip='#141020', style='spiky', wep='daggers'),
 'hinata': dict(ink='#33260f', hair='#c08f28', hair2='#ffd873', hair3='#fff3c6',
    skin='#ffe6cf', skin2='#e0ad88', eye='#57e08d', eye2='#12523a',
    cloth='#fff8e8', cloth2='#ffffff', cloth3='#d8bd8e', accent='#7cffa8',
    metal='#c04a6a', grip='#8d2f4c', style='bob', wep='tome'),
}
ORDER = ['aoi', 'kagura', 'ren', 'hinata']

# -------------------------------------------------------------------- poses --
POSES = [
 dict(bob=0, lean=0, legs='stand',   hb=(6,19),  hf=(17,19), wep=0.0,  eye='open',   sway=0),
 dict(bob=1, lean=0, legs='stand',   hb=(6,20),  hf=(17,20), wep=0.05, eye='open',   sway=1),
 dict(bob=0, lean=1, legs='walk0',   hb=(5,19),  hf=(18,18), wep=0.1,  eye='open',   sway=2),
 dict(bob=1, lean=0, legs='walk1',   hb=(6,20),  hf=(17,20), wep=0.0,  eye='open',   sway=0),
 dict(bob=0, lean=-1, legs='walk2',  hb=(6,18),  hf=(18,19), wep=0.1,  eye='open',   sway=-2),
 dict(bob=1, lean=0, legs='walk3',   hb=(6,20),  hf=(17,20), wep=0.0,  eye='open',   sway=0),
 dict(bob=0, lean=-2, legs='brace',  hb=(4,18),  hf=(8,14),  wep=-1.0, eye='fierce', sway=-3),
 dict(bob=0, lean=3, legs='lunge',   hb=(8,20),  hf=(20,13), wep=1.0,  eye='fierce', sway=3),
 dict(bob=1, lean=1, legs='lunge',   hb=(7,20),  hf=(20,21), wep=0.55, eye='fierce', sway=2),
 dict(bob=1, lean=4, legs='dash',    hb=(4,21),  hf=(19,18), wep=0.25, eye='fierce', sway=5),
 dict(bob=-1, lean=0, legs='stand',  hb=(5,14),  hf=(18,14), wep=-0.6, eye='closed', sway=-1),
 dict(bob=2, lean=-3, legs='stagger',hb=(3,17),  hf=(19,17), wep=0.2,  eye='closed', sway=-4),
]

# --------------------------------------------------------------- body parts --
def draw_legs(c, p, kind):
    sk, boot, boot2 = p['skin'], p['cloth3'], p['ink']
    def leg(x, top, fdx):
        c.rect(x, top, 3, 25-top, sk)
        c.rect(x, 24, 3, 2, boot)
        c.rect(x+fdx, 25, 3, 2, boot)
    if kind == 'stand':    leg(8, 20, -1); leg(13, 20, 0)
    elif kind == 'walk0':  leg(6, 20, -1); leg(14, 21, 1)
    elif kind == 'walk1':  leg(9, 21, -1); leg(12, 21, 0)
    elif kind == 'walk2':  leg(14, 20, 1); leg(7, 21, -1)
    elif kind == 'walk3':  leg(9, 21, -1); leg(12, 21, 0)
    elif kind == 'lunge':  leg(5, 21, -1); leg(15, 20, 1)
    elif kind == 'brace':  leg(6, 20, -1); leg(14, 20, 1)
    elif kind == 'stagger':leg(6, 21, -1); leg(14, 21, 1)
    elif kind == 'dash':
        c.rect(5, 22, 5, 3, sk); c.rect(3, 24, 6, 2, boot)
        c.rect(13, 20, 4, 4, sk); c.rect(13, 24, 6, 2, boot)

def draw_torso(c, p, pose):
    cl, cl2, cl3 = p['cloth'], p['cloth2'], p['cloth3']
    dx, dy = pose['lean'], pose['bob']
    x0 = 8 + dx//2
    c.rect(x0, 13+dy, 8, 8, cl)                 # chest
    c.rect(x0, 13+dy, 8, 3, cl2)                # collar highlight
    c.rect(x0+3, 14+dy, 2, 7, cl3)              # centre seam
    if p['style'] == 'bob':                     # dress skirt
        c.rect(x0-1, 19+dy, 10, 3, cl)
        c.rect(x0-2, 21+dy, 12, 2, cl2)
        c.rect(x0-2, 22+dy, 12, 1, cl3)
        for i in range(0, 12, 3): c.set(x0-2+i, 22+dy, p['accent'])
    elif p['style'] == 'long':                  # robe
        c.rect(x0-1, 19+dy, 10, 4, cl)
        c.rect(x0-2, 22+dy, 12, 2, cl3)
    else:                                        # coat tails
        c.rect(x0-1, 20+dy, 10, 2, cl)
        c.rect(x0-1+(0 if dx>=0 else 1), 21+dy, 4, 3, cl3)
        c.rect(x0+6, 21+dy, 4, 3, cl3)
    c.rect(x0+1, 18+dy, 6, 2, p['accent'])      # belt / sash

def draw_arm(c, p, pose, hand, back):
    sk = p['skin2'] if back else p['skin']
    cl = p['cloth3'] if back else p['cloth']
    dy = pose['bob']
    sx = (9 if back else 15) + pose['lean']//2
    sy = 15 + dy
    hxp, hyp = hand[0], hand[1] + dy
    c.line(sx, sy, (sx+hxp)/2, (sy+hyp)/2 - 1, cl, 3)      # upper arm, sleeve
    c.line(sx, sy+2, (sx+hxp)/2, (sy+hyp)/2, p['cloth3'], 1)   # sleeve shading
    c.line((sx+hxp)/2, (sy+hyp)/2 - 1, hxp, hyp, sk, 2)    # forearm
    c.rect(hxp-1, hyp, 2, 2, sk)                            # hand

def draw_head(c, p, pose):
    sk, sk2 = p['skin'], p['skin2']
    dy = pose['bob'] + (-1 if pose['legs'] in ('walk0','walk2') else 0)
    cx = 11.5 + pose['lean']*0.6
    cy = 7 + dy
    c.ellipse(cx, cy, 6.6, 6.4, sk)
    for x in range(int(cx-4), int(cx+5)): c.set(x, cy+5, sk2)
    ex = pose['eye']
    ey = cy + 0.5
    for sgn, ox in ((-1, cx-4.5), (1, cx+1.5)):
        if ex == 'closed':                       # happy//casting closed eye
            for i in range(3): c.set(ox+i, ey, p['eye2'])
            c.set(ox + (0 if sgn < 0 else 2), ey-1, p['eye2'])
            continue
        for i in range(3):                       # upper lash line
            c.set(ox+i, ey-1, p['ink'])
        c.set(ox, ey, p['eye']); c.set(ox+1, ey, p['eye'])
        c.set(ox+2, ey, '#ffffff')               # catchlight
        c.set(ox, ey+1, p['eye2']); c.set(ox+1, ey+1, p['eye2'])
        c.set(ox+2, ey+1, p['eye'])
        if ex == 'fierce':                       # angled brow
            c.set(ox + (0 if sgn < 0 else 2), ey-2, p['ink'])
            c.set(ox+1, ey-2, p['ink'])
    c.set(cx-0.5, cy+3, sk2)                     # mouth
    c.set(cx-3.5, cy+2.5, p['skin2']); c.set(cx+2.5, cy+2.5, p['skin2'])   # blush
    return cx, cy

def hair_back(c, p, pose, cx, cy):
    st, h1, h2, sway = p['style'], p['hair'], p['hair2'], pose['sway']
    if st == 'ponytail':
        c.line(cx+4, cy-3, cx+8+sway, cy+2, h1, 4)
        c.line(cx+7+sway//2, cy+1, cx+9+sway, cy+11, h1, 4)
        c.line(cx+8+sway//2, cy+2, cx+9+sway, cy+9, h2, 2)
    elif st == 'long':
        c.rect(cx-7, cy-2, 4, 15, h1)          # left curtain
        c.rect(cx+4, cy-2, 4, 15, h1)          # right curtain
        c.rect(cx-4, cy-2, 9, 8, h1)           # back of the head
        c.rect(cx-6, cy+4, 3, 7, h2)
        c.rect(cx+4, cy+4, 3, 7, h2)
        c.line(cx-6, cy+12, cx-7+sway, cy+16, h1, 3)
        c.line(cx+5, cy+12, cx+6+sway, cy+16, h1, 3)
    elif st == 'spiky':
        c.line(cx-6, cy-1, cx-7+sway//2, cy+3, h1, 2)
        c.line(cx+5, cy-1, cx+6+sway//2, cy+3, h1, 2)
        c.line(cx-3, cy+6, cx-6+sway, cy+10, p['accent'], 2)    # scarf tail
        c.line(cx-5+sway//2, cy+9, cx-7+sway, cy+12, p['accent'], 2)
    elif st == 'bob':
        c.ellipse(cx, cy+0.5, 7.6, 7.0, h1)

def hair_front(c, p, pose, cx, cy):
    st, h1, h2, h3, ac = p['style'], p['hair'], p['hair2'], p['hair3'], p['accent']
    top = cy - 0.5                     # nothing below this line, so eyes stay clear
    c.ellipse(cx, cy-1.2, 6.8, 6.2, h1, ymax=int(top))
    c.ellipse(cx, cy-2.6, 5.6, 4.4, h2, ymax=int(top-1))
    for i in range(5): c.set(cx-2.5+i, cy-5.5+abs(i-2)*0.5, h3)
    # side locks framing the cheeks
    c.rect(cx-7, cy-2, 2, 5, h1); c.rect(cx+5, cy-2, 2, 5, h1)
    # fringe points, kept above the lash line so they never cut the eyes
    for fx, fl in ((-6, 3), (-2.5, 2), (1, 2), (4.5, 3)):
        c.rect(cx+fx, cy-2.5, 1, fl, h1)
    if st == 'ponytail':
        c.rect(cx+3, cy-4, 3, 2, ac)
    elif st == 'long':
        c.rect(cx-1.5, cy-6.5, 3, 2, ac)
        c.rect(cx-8, cy-1, 2, 8, h1); c.rect(cx+6, cy-1, 2, 8, h1)
    elif st == 'spiky':
        for sx, sy in ((-6,-3), (-3,-5), (0,-6), (3,-5), (6,-3)):
            c.line(cx+sx, cy+sy+2, cx+sx+pose['sway']//3, cy+sy-2, h2, 2)
    elif st == 'bob':
        c.rect(cx-8, cy-2, 2, 7, h1); c.rect(cx+6, cy-2, 2, 7, h1)
        c.rect(cx-9, cy-4, 2, 3, ac); c.rect(cx+7, cy-4, 2, 3, ac)

def sy_bottom(hy):
    return min(hy + 7, 26)

def draw_weapon(c, p, pose):
    w, dy = p['wep'], pose['bob']
    hxp, hyp = pose['hf'][0], pose['hf'][1] + dy
    ph = pose['wep']
    import math
    # rest points forward-and-down; windup goes up behind; strike cuts down-forward
    deg = -25 + (ph * 70 if ph >= 0 else ph * 110)
    ang = math.radians(deg)
    ux, uy = math.cos(ang), math.sin(ang)
    if w == 'katana':
        L = 10
        hxp = min(hxp, 18)
        c.line(hxp-ux*4, hyp-uy*4, hxp, hyp, p['grip'], 2)          # hilt
        c.line(hxp-ux*1.5, hyp-uy*1.5, hxp+ux*0.5, hyp+uy*0.5, p['accent'], 3)  # tsuba
        c.line(hxp+ux*2, hyp+uy*2, hxp+ux*L, hyp+uy*L, p['metal'], 2)
        c.line(hxp+ux*3, hyp+uy*3, hxp+ux*(L-1), hyp+uy*(L-1), '#ffffff', 1)
        c.set(hxp+ux*L, hyp+uy*L, '#ffffff')                        # tip
    elif w == 'staff':
        sx = min(hxp + 1, 20)                      # held upright beside her
        tilt = ux * 2
        c.line(sx, sy_bottom(hyp), sx+tilt, hyp-8, p['metal'], 2)
        c.line(sx+1, sy_bottom(hyp), sx+tilt+1, hyp-8, p['grip'], 1)
        ox, oy = sx+tilt, hyp-11
        c.ellipse(ox, oy, 2.8, 2.8, p['accent'])
        c.ellipse(ox-0.6, oy-0.6, 1.2, 1.2, '#ffffff')
        for dx2, dy2 in ((-3,0), (3,0), (0,-3)): c.set(ox+dx2, oy+dy2, p['hair3'])
    elif w == 'daggers':
        c.line(hxp, hyp, hxp+ux*5, hyp+uy*5, p['metal'], 2)
        c.set(hxp, hyp+1, p['grip'])
        bx, by = pose['hb'][0], pose['hb'][1] + dy
        c.line(bx, by, bx-abs(ux)*4, by-uy*3, p['metal'], 2)
        c.set(bx, by+1, p['grip'])
    elif w == 'tome':
        bx, by = min(hxp - 1, 17), hyp - 3
        c.rect(bx, by, 7, 6, p['metal'])           # cover
        c.rect(bx+1, by+1, 5, 4, '#fff8e8')        # pages
        for k in range(1, 4): c.set(bx+2, by+k, p['cloth3'])
        for k in range(1, 4): c.set(bx+5, by+k, p['cloth3'])
        c.rect(bx+3, by, 1, 6, p['grip'])          # spine
        c.set(bx+3, by-1, p['accent']); c.set(bx+3, by+6, p['accent'])

def draw_char(key, frame):
    p = CHARS[key]
    pose = POSES[frame]
    c = Cv(W, H, PAD)
    dy = pose['bob'] + (-1 if pose['legs'] in ('walk0','walk2') else 0)
    cx, cy = 11.5 + pose['lean']*0.6, 7 + dy
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
 'slime': dict(body='#4fd18a', body2='#a6f5cc', ink='#0d2a1a', eye='#0d2a1a'),
 'bat':   dict(body='#8f5fd6', body2='#c9a6ff', ink='#1a0d22', eye='#ffd24d'),
 'imp':   dict(body='#ff8b4d', body2='#ffc9a0', ink='#2a0d0d', eye='#ffe14d', cloth='#8f2020'),
 'brute': dict(body='#c05a4d', body2='#e89080', ink='#1a1020', eye='#ffe14d', cloth='#4a2a3a'),
 'boss':  dict(body='#b07cff', body2='#ddc2ff', ink='#12081a', eye='#ff5d5d', cloth='#2a1b4d'),
}
MOB_ORDER = ['slime', 'bat', 'imp', 'brute', 'boss']

def draw_mob(key, f):
    m = MOBS[key]; c = Cv(W, H, PAD)
    t = [0, 1, 2, 1][f] if f < 4 else 0
    if key == 'slime':
        squash = [0, 1, -1, 1][f]
        c.ellipse(12, 22-squash, 7+squash*0.5, 5-squash*0.5, m['body'])
        c.ellipse(12, 20-squash, 5+squash*0.4, 3, m['body2'])
        c.rect(9, 20, 2, 2, m['eye']); c.rect(14, 20, 2, 2, m['eye'])
        c.ellipse(12, 25, 7, 1.6, m['body'])
    elif key == 'bat':
        flap = [0, 3, 5, 3][f]
        c.ellipse(12, 16, 3.2, 3.6, m['body'])
        for sgn in (-1, 1):
            bx = 12 + sgn*3
            c.line(bx, 15, bx+sgn*7, 12-flap, m['body'], 2)      # leading edge
            c.line(bx+sgn*7, 12-flap, bx+sgn*7, 17-flap, m['body'], 2)
            c.line(bx, 18, bx+sgn*7, 17-flap, m['body'], 2)      # trailing edge
            for k in range(1, 7):                                 # membrane fill
                c.line(bx+sgn*k, 15+k//3, bx+sgn*k, 17-flap+1, m['body2'], 1)
            c.line(bx, 15, bx+sgn*4, 16-flap, m['body'], 1)
        c.ellipse(12, 16, 3.0, 3.4, m['body'])
        c.set(10, 15, m['eye']); c.set(13, 15, m['eye'])
        c.line(10, 13, 9, 11, m['body'], 1); c.line(14, 13, 15, 11, m['body'], 1)
    elif key == 'imp':
        bob = [0, 1, 0, -1][f]
        c.ellipse(12, 10+bob, 5, 4.6, m['body'])
        c.line(8, 7+bob, 5, 3+bob, m['body'], 2); c.line(16, 7+bob, 19, 3+bob, m['body'], 2)
        c.line(7, 17+bob, 5, 21+bob, m['body'], 2)          # arms down
        c.line(17, 17+bob, 19, 21+bob, m['body'], 2)
        c.rect(9, 9+bob, 2, 2, m['eye']); c.rect(13, 9+bob, 2, 2, m['eye'])
        c.rect(8, 15+bob, 8, 7, m['cloth']); c.rect(9, 16+bob, 6, 3, m['body2'])
        c.rect(8, 21+bob, 3, 3, m['cloth']); c.rect(13, 21+bob, 3, 3, m['cloth'])
        if f == 3: c.ellipse(19, 17+bob, 2.6, 2.6, m['eye'])
    elif key in ('brute', 'boss'):
        big = 1 if key == 'boss' else 0
        bob = [0, 1, -1, 0][f]
        c.ellipse(12, 7+bob, 6.4+big, 5.6+big, m['body'])
        c.rect(8, 6+bob, 3, 2, m['eye']); c.rect(13, 6+bob, 3, 2, m['eye'])
        c.line(6, 3+bob, 4, 0+bob, m['body2'], 2); c.line(18, 3+bob, 20, 0+bob, m['body2'], 2)
        c.rect(7, 13+bob, 10, 9, m['cloth'])
        c.rect(8, 14+bob, 8, 4, m['body2'])
        c.rect(4, 14+bob, 3, 7, m['body']); c.rect(17, 14+bob, 3, 7, m['body'])
        c.rect(7, 22, 4, 5, m['cloth']); c.rect(13, 22, 4, 5, m['cloth'])
        if f == 3:
            for x in range(3, 21, 3): c.set(x, 2+bob, m['eye'])
    c.outline(m['ink'])
    return c

# ------------------------------------------------------------------- atlas ---
def build():
    rows = ORDER + MOB_ORDER
    aw, ah = W*FRAMES, H*len(rows)
    px = [[None]*aw for _ in range(ah)]
    for r, key in enumerate(rows):
        for f in range(FRAMES):
            if key in CHARS: c = draw_char(key, f)
            elif f < 4:      c = draw_mob(key, f)
            else:            continue
            for y in range(H):
                for x in range(W):
                    px[r*H+y][f*W+x] = c.px[y][x]
    return px, aw, ah, rows

def main():
    px, aw, ah, rows = build()
    os.makedirs(os.path.join(ROOT, 'assets'), exist_ok=True)
    atlas = os.path.join(ROOT, 'assets', 'atlas.png')
    write_png(atlas, px, aw, ah)
    print('atlas %dx%d -> %s (%d bytes)' % (aw, ah, atlas, os.path.getsize(atlas)))

    if 'closeup' in sys.argv:
        s, cols = 12, [0, 3, 7, 9, 10]
        cw, ch = W*len(cols)*s, H*len(ORDER)*s
        big = [[None]*cw for _ in range(ch)]
        for r in range(len(ORDER)):
            for ci, f in enumerate(cols):
                for y in range(H):
                    for x in range(W):
                        c = px[r*H+y][f*W+x]
                        for j in range(s):
                            for i in range(s):
                                big[(r*H+y)*s+j][(ci*W+x)*s+i] = c or ('#241b38' if (ci+r) % 2 else '#1b1428')
        write_png(os.path.join(ROOT, 'tools', 'closeup.png'), big, cw, ch)
        print('closeup -> tools/closeup.png')

    if 'preview' in sys.argv:
        s = 6
        pw, ph = aw*s, ah*s
        big = [[None]*pw for _ in range(ph)]
        for y in range(ph):
            for x in range(pw):
                c = px[y//s][x//s]
                big[y][x] = c if c else ('#20182f' if ((x//s//W)+(y//s//H)) % 2 else '#171122')
        write_png(os.path.join(ROOT, 'tools', 'preview.png'), big, pw, ph)
        print('preview -> tools/preview.png')

    b64 = base64.b64encode(open(atlas, 'rb').read()).decode()
    uri = 'data:image/png;base64,' + b64
    idx = os.path.join(ROOT, 'index.html')
    src = open(idx).read()
    import re
    new, n = re.subn(r'const ATLAS_SRC = "[^"]*";',
                     'const ATLAS_SRC = "%s";' % uri, src)
    if n:
        open(idx, 'w').write(new)
        print('patched index.html (%d KB of atlas)' % (len(uri)//1024))
    else:
        print('index.html has no ATLAS_SRC yet - add it, then re-run')

if __name__ == '__main__':
    main()
