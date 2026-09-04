#!/usr/bin/env python3
"""
Twin Fate sprite generator.

Draws every character and enemy frame pixel by pixel, packs them into one
atlas PNG, and patches the base64 data URI straight into index.html.

    python3 tools/make_sprites.py            # atlas + patch index.html
    python3 tools/make_sprites.py closeup    # tools/closeup.png, heroes at 6x
    python3 tools/make_sprites.py mobs       # tools/mobs.png, enemies at 5x
    python3 tools/make_sprites.py preview    # tools/preview.png, whole sheet at 3x

Frames are 56x96. Row = actor, column = frame.
  Characters  0 idle-a  1 idle-b  2-5 walk  6 windup  7 strike  8 recover
              9 dash  10 cast  11 hurt
  Enemies     0-1 idle  2-5 move  6 telegraph  7 attack  8 special  9 hurt

Figures are drawn at roughly 4.7 heads, so proportions read as anime rather
than chibi: head 18px, shoulders at 28, waist at 40, legs from 50 to 92.
"""
import zlib, struct, base64, os, sys, math

W, H = 56, 96
FRAMES = 12
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CX    = 28.0       # centre column
FEET  = 92         # baseline every actor stands on
HEADY = 16         # head centre
SHOULDER = 29      # top of the torso
HIP   = 50         # where the legs start
TURN  = 2.5        # how far the figure leads with its front (+x)
PROFILE = 0.62     # a body seen from the side is far narrower than head-on

# ------------------------------------------------------------------ canvas ---
class Cv:
    def __init__(s, w, h):
        s.w, s.h = w, h
        s.px = [[None]*w for _ in range(h)]
    def set(s, x, y, c):
        # floor(v+.5), never round(): round() is banker's rounding, which
        # collapses the half-pixel offsets a tapered limb emits onto even rows
        x, y = int(math.floor(x + 0.5)), int(math.floor(y + 0.5))
        if c is None or x < 0 or y < 0 or x >= s.w or y >= s.h: return
        s.px[y][x] = c
    def get(s, x, y):
        x, y = int(x), int(y)
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
        """a limb: thick at the joint, thinner at the tip"""
        n = int(max(abs(x1-x0), abs(y1-y0))) * 2 + 1
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
    skin='#ffdcc0', skin2='#e5a184', skin3='#c98166', blush='#ff9db0',
    eye='#3fc9ff', eye2='#12608f', brow='#2a4fa8',
    cloth='#f2f6ff', cloth2='#ffffff', cloth3='#98abd8', trim='#3fc9ff', accent='#ff5fa2',
    leg='#4a5f96', leg2='#6d83bd', boot='#2a3358',
    metal='#eaf0ff', grip='#2a2440', sleeve=0.52, face=dict(ew=5, eh=4, lash=2, droop=0, brow='angled', brow_h=1, nose=2, mouth=2),
    style='ponytail', wep='katana'),
 'kagura': dict(ink='#160d2c', hair='#4d2478', hair2='#8e51cc', hair3='#c79cf5', shine='#e7d2ff',
    skin='#ffe0c6', skin2='#daa385', skin3='#b8815f', blush='#ff9db0',
    eye='#ffd24d', eye2='#a86c12', brow='#4d2478',
    cloth='#33215e', cloth2='#5b3f9c', cloth3='#1b1136', trim='#8e51cc', accent='#ffcc4d',
    leg='#2b1a4f', leg2='#463079', boot='#1b1136',
    metal='#4a3672', grip='#241a3e', sleeve=0.80, face=dict(ew=5, eh=4, lash=2, droop=1, brow='soft',   brow_h=2, nose=2, mouth=1),
    style='long', wep='staff'),
 'ren': dict(ink='#0d0a17', hair='#201b38', hair2='#453c78', hair3='#7d70ae', shine='#a99ddb',
    skin='#f0cba9', skin2='#c2967a', skin3='#9c745c', blush='#e8807f',
    eye='#ff4d5d', eye2='#8f1d2c', brow='#201b38',
    cloth='#332b56', cloth2='#524791', cloth3='#191430', trim='#ff4d5d', accent='#ff4d5d',
    leg='#201c3a', leg2='#38315e', boot='#12101f',
    metal='#d5cdfa', grip='#141020', sleeve=0.58, face=dict(ew=5, eh=3, lash=0, droop=0, brow='angled', brow_h=1, nose=3, mouth=1),
    style='spiky', wep='daggers'),
 'suzume': dict(ink='#123a2a', hair='#2f7a5c', hair2='#57bd8f', hair3='#a6f0cd', shine='#dcffee',
    skin='#ffe0c2', skin2='#dfab84', skin3='#b8825f', blush='#ff9db0',
    eye='#ffc247', eye2='#9c6c0e', brow='#2f7a5c',
    cloth='#eaf7f0', cloth2='#ffffff', cloth3='#9fc7b4', trim='#57bd8f', accent='#ffc247',
    leg='#3c6b58', leg2='#5b9179', boot='#20443a',
    metal='#c9a06a', grip='#4a3320', sleeve=0.62, face=dict(ew=6, eh=4, lash=1, droop=0, brow='flat',   brow_h=2, nose=2, mouth=1),
    style='braid', wep='bow'),
 'gorou': dict(ink='#1b1a26', hair='#5a3a1e', hair2='#8f5f2f', hair3='#c99a5c', shine='#e8c99a',
    skin='#e8b98e', skin2='#c08f66', skin3='#96694a', blush='#e08a6a',
    eye='#ff9d3d', eye2='#8a4410', brow='#5a3a1e',
    cloth='#5a6478', cloth2='#8892ab', cloth3='#363d4d', trim='#e0a52c', accent='#e0a52c',
    leg='#3b4252', leg2='#5a6478', boot='#22262f',
    metal='#cfd8e8', grip='#3a2a1a', sleeve=0.74, face=dict(ew=4, eh=3, lash=0, droop=0, brow='thick',  brow_h=1, nose=3, mouth=2),
    style='crop', wep='hammer'),
 'hinata': dict(ink='#38290f', hair='#c99527', hair2='#ffdc7f', hair3='#fff5cf', shine='#ffffff',
    skin='#ffe6cf', skin2='#e0ad88', skin3='#bd8a66', blush='#ffa8a8',
    eye='#57e08d', eye2='#1a7a4c', brow='#c99527',
    cloth='#fffaf0', cloth2='#ffffff', cloth3='#dcc59a', trim='#7cffa8', accent='#7cffa8',
    leg='#f0dfc0', leg2='#fff6e4', boot='#c9a86e',
    metal='#c04a6a', grip='#8d2f4c', sleeve=0.50, face=dict(ew=6, eh=5, lash=2, droop=0, brow='soft',   brow_h=2, nose=2, mouth=1),
    style='bob', wep='tome'),
 'yura': dict(ink='#16324a', hair='#5f8fc9', hair2='#a8d4f5', hair3='#e0f4ff', shine='#ffffff',
    skin='#fff0e2', skin2='#e6c3ad', skin3='#c09680', blush='#ffb0c0',
    eye='#7fe0ff', eye2='#1d6f96', brow='#5f8fc9',
    cloth='#dff2ff', cloth2='#ffffff', cloth3='#8fb4d4', trim='#5fe6ff', accent='#5fe6ff',
    leg='#46708f', leg2='#6f9fd8', boot='#254559',
    metal='#7fc9ec', grip='#2c4358', sleeve=0.72, face=dict(ew=5, eh=4, lash=2, droop=1, brow='flat',   brow_h=2, nose=2, mouth=1),
    style='long', wep='icelance'),
 'kaito': dict(ink='#171628', hair='#8a7a20', hair2='#ffe14d', hair3='#fff6a8', shine='#ffffff',
    skin='#f2c9a4', skin2='#cfa07c', skin3='#a2795a', blush='#e88a7a',
    eye='#ffe14d', eye2='#8a6410', brow='#8a7a20',
    cloth='#2b2a3c', cloth2='#4a4866', cloth3='#191826', trim='#ffe14d', accent='#ffe14d',
    leg='#232231', leg2='#3d3b52', boot='#141320',
    metal='#cfd8e8', grip='#2b2a14', sleeve=0.45, face=dict(ew=6, eh=4, lash=0, droop=0, brow='angled', brow_h=1, nose=2, mouth=2),
    style='spiky', wep='gauntlet'),
 'momo': dict(ink='#33220f', hair='#7a4a1e', hair2='#c78a45', hair3='#e8c08a', shine='#fff0d0',
    skin='#ffd9bd', skin2='#dda989', skin3='#b8825f', blush='#ff9db0',
    eye='#ffcc4d', eye2='#8a6410', brow='#7a4a1e',
    cloth='#7d5a33', cloth2='#a67f4c', cloth3='#4a3320', trim='#ffcc4d', accent='#ffcc4d',
    leg='#4a3a28', leg2='#6b5540', boot='#2a2018',
    metal='#eae2c8', grip='#241a0e', sleeve=0.50, face=dict(ew=6, eh=5, lash=1, droop=0, brow='thick',  brow_h=2, nose=3, mouth=2),
    style='twin', wep='glaive'),
 'chiyo': dict(ink='#2a1014', hair='#96311f', hair2='#ff8b4d', hair3='#ffc79f', shine='#ffe8d0',
    skin='#ffdcc0', skin2='#e0ab88', skin3='#b8815f', blush='#ff9db0',
    eye='#ff8b4d', eye2='#8a3a12', brow='#96311f',
    cloth='#3a2028', cloth2='#5e3440', cloth3='#241318', trim='#ff8b4d', accent='#ff8b4d',
    leg='#2e1c22', leg2='#4a2e38', boot='#1a1014',
    metal='#c9563a', grip='#3a2018', sleeve=0.55, face=dict(ew=6, eh=5, lash=1, droop=0, brow='angled', brow_h=2, nose=2, mouth=1),
    style='bob', wep='bombs'),
 'nari': dict(ink='#241428', hair='#7a3f8a', hair2='#d68ae6', hair3='#f4c2ff', shine='#ffffff',
    skin='#ffe6cf', skin2='#e2b492', skin3='#bd8a66', blush='#ffa8c0',
    eye='#ffd24d', eye2='#96690e', brow='#7a3f8a',
    cloth='#3a2246', cloth2='#5e3a6e', cloth3='#241428', trim='#ffd24d', accent='#ffd24d',
    leg='#2e1c36', leg2='#4a2e56', boot='#1a1020',
    metal='#c9a05a', grip='#4a3320', sleeve=0.60, face=dict(ew=5, eh=4, lash=2, droop=1, brow='soft',   brow_h=2, nose=2, mouth=1),
    style='braid', wep='lute'),
 'seryn': dict(ink='#1a2338', hair='#b8912f', hair2='#ffe08a', hair3='#fff6cf', shine='#ffffff',
    skin='#ffe6cf', skin2='#e0b48f', skin3='#b8886a', blush='#ffa8b8',
    eye='#5fe0a8', eye2='#12704f', brow='#b8912f',
    cloth='#cfd8ea', cloth2='#f2f6ff', cloth3='#6f7f9c', trim='#3f6fd0', accent='#ffcc4d',
    leg='#3f4a63', leg2='#5f6d8c', boot='#232b3d',
    metal='#eef3ff', grip='#2b3350',
    face=dict(ew=5, eh=4, lash=1, droop=0, brow='flat', brow_h=2, nose=2, mouth=1),
    sleeve=0.86, style='braid', wep='greatsword'),
 'aldric': dict(ink='#2a1414', hair='#8a8a96', hair2='#d8d8e4', hair3='#ffffff', shine='#ffffff',
    skin='#c9906a', skin2='#a3714f', skin3='#7d5439', blush='#c96a5a',
    eye='#a8b4c8', eye2='#4a5568', brow='#8a8a96',
    cloth='#8f2020', cloth2='#c33a3a', cloth3='#4a1010', trim='#2b2b33', accent='#2b2b33',
    leg='#2b2b33', leg2='#45454f', boot='#16161c',
    metal='#e8ecf5', grip='#2b2b33',
    face=dict(ew=5, eh=3, lash=0, droop=0, brow='angled', brow_h=1, nose=3, mouth=1),
    sleeve=0.70, style='spiky', wep='twinswords'),
 'kassandra': dict(ink='#33101c', hair='#8f1f33', hair2='#e04a63', hair3='#ff9db0', shine='#ffd0d8',
    skin='#ffdcc0', skin2='#e0a184', skin3='#b87f62', blush='#ff8fa8',
    eye='#ffcc4d', eye2='#8a5a10', brow='#8f1f33',
    cloth='#3a1420', cloth2='#5e2436', cloth3='#220c14', trim='#e04a63', accent='#ffcc4d',
    leg='#2b0f18', leg2='#452030', boot='#180810',
    metal='#ffd0d8', grip='#3a1420',
    face=dict(ew=6, eh=4, lash=1, droop=0, brow='angled', brow_h=1, nose=2, mouth=1),
    sleeve=0.55, style='ponytail', wep='spear'),
 'nyx': dict(ink='#100a1c', hair='#2a1c40', hair2='#5b3f86', hair3='#9c7fc9', shine='#c9b0e8',
    skin='#f0dcd0', skin2='#cbb0a4', skin3='#a08878', blush='#d68a9c',
    eye='#b07cff', eye2='#4a2578', brow='#2a1c40',
    cloth='#1e142e', cloth2='#3a2a56', cloth3='#120c1c', trim='#b07cff', accent='#b07cff',
    leg='#181026', leg2='#2e2040', boot='#0d0816',
    metal='#4a2f6b', grip='#241634',
    face=dict(ew=5, eh=4, lash=2, droop=1, brow='soft', brow_h=2, nose=2, mouth=1),
    sleeve=0.88, style='long', wep='grimoire'),
 'toma': dict(ink='#22221a', hair='#6b6320', hair2='#b8ab3d', hair3='#e8e0a8', shine='#fff8d0',
    skin='#f2c9a4', skin2='#cfa07c', skin3='#a2795a', blush='#e8907a',
    eye='#ffe14d', eye2='#8a6410', brow='#6b6320',
    cloth='#3a3a2a', cloth2='#5a5a40', cloth3='#22221a', trim='#ffe14d', accent='#ffe14d',
    leg='#2e2e22', leg2='#4a4a36', boot='#1a1a14',
    metal='#8a8030', grip='#2b2a14', sleeve=0.68, face=dict(ew=4, eh=3, lash=0, droop=0, brow='flat',   brow_h=1, nose=2, mouth=1),
    style='crop', wep='staff'),
}
ORDER = ['aoi', 'kagura', 'ren', 'hinata', 'suzume', 'gorou',
         'yura', 'kaito', 'momo', 'chiyo', 'nari', 'toma',
         'seryn', 'aldric', 'kassandra', 'nyx']

# torso silhouette: half-width per row from SHOULDER down. Broad shoulders,
# nipped waist, hips again -- the shape that reads as a figure and not a box.
TORSO = [8, 9, 10, 10, 10, 10, 9, 9, 9, 9, 8, 8, 8, 7, 7, 7, 7, 7, 8, 8, 9, 9]

# -------------------------------------------------------------------- poses --
# A pose is a skeleton, not a set of absolute pixels. Everything hangs off the
# same bob/lean, so the body can never come apart at the joints:
#   feet = ((x offset from centre, how far off the ground), ...) for L and R
#   hb / hf = back and front hand, as an offset FROM THAT SHOULDER
POSES = [
 # 0-1 idle: a slow breath, weight on both feet
 dict(bob=0,  lean=0,  feet=((-5,0), (5,0)),   hb=(-1,26), hf=(1,26),  wep=0.00, eye='open',   sway=0),
 dict(bob=1,  lean=0,  feet=((-5,0), (5,0)),   hb=(-1,25), hf=(1,25),  wep=0.05, eye='open',   sway=2),
 # 2-5 walk: contact, passing, contact (mirrored), passing (mirrored).
 # Arms swing opposite the legs, the way they actually do.
 dict(bob=1,  lean=1,  feet=((-11,0), (9,5)),  hb=(9,23),  hf=(-8,27), wep=0.10, eye='open',   sway=4),
 dict(bob=-1, lean=0,  feet=((-4,7), (4,0)),   hb=(2,25),  hf=(-1,26), wep=0.00, eye='open',   sway=1),
 dict(bob=1,  lean=-1, feet=((-9,5), (11,0)),  hb=(-8,27), hf=(9,23),  wep=0.10, eye='open',   sway=-4),
 dict(bob=-1, lean=0,  feet=((-4,0), (4,7)),   hb=(-1,26), hf=(2,25),  wep=0.00, eye='open',   sway=-1),
 # 6 windup: weight back, blade drawn behind
 dict(bob=1,  lean=-4, feet=((-9,0), (5,0)),   hb=(-6,22), hf=(-9,8),  wep=-1.0, eye='fierce', sway=-6),
 # 7 strike: lunge onto the front foot, back foot trailing
 dict(bob=1,  lean=6,  feet=((-11,1), (11,0)), hb=(-4,26), hf=(9,4),   wep=1.00, eye='fierce', sway=8),
 # 8 recover: settling out of the lunge
 dict(bob=2,  lean=2,  feet=((-8,0), (8,0)),   hb=(-3,26), hf=(7,28),  wep=0.55, eye='fierce', sway=4),
 # 9 dash: airborne, back leg trailing, front knee tucked
 dict(bob=3,  lean=9,  feet=((-13,7), (6,11)), hb=(-8,28), hf=(6,18),  wep=0.25, eye='fierce', sway=11),
 # 10 cast: both hands raised
 dict(bob=-2, lean=0,  feet=((-5,0), (5,0)),   hb=(-3,6),  hf=(3,6),   wep=-0.6, eye='closed', sway=-3),
 # 11 hurt: knocked back onto the heels
 dict(bob=2,  lean=-6, feet=((-7,0), (8,2)),   hb=(-7,16), hf=(7,16),  wep=0.20, eye='hurt',   sway=-9),
]

def shoulder(pose, back):
    # a turned body foreshortens: lead shoulder swings forward, trailing one hides
    dx = (-3 + TURN*0.4) if back else (4 + TURN*1.0)
    return (CX + dx + pose['lean']*0.4, SHOULDER + 2 + pose['bob'] + (1 if back else 0))

def hand(pose, back):
    """absolute hand position, always measured from its own shoulder"""
    sx, sy = shoulder(pose, back)
    d = pose['hb'] if back else pose['hf']
    return (sx + d[0], sy + d[1])

def bent(c, x0, y0, x1, y1, bend, col, w0, w1):
    """two-segment limb with a knee/elbow pushed out perpendicular to the line"""
    dx, dy = x1-x0, y1-y0
    L = math.hypot(dx, dy) or 1
    nx, ny = -dy/L, dx/L
    kx, ky = (x0+x1)/2 + nx*bend, (y0+y1)/2 + ny*bend
    wm = (w0+w1)/2
    c.taper(x0, y0, kx, ky, col, w0, wm)
    c.taper(kx, ky, x1, y1, col, wm, w1)
    return kx, ky

# --------------------------------------------------------------- body parts --
def draw_legs(c, p, pose):
    lg, lg2, bt = p['leg'], p['leg2'], p['boot']
    hipy = HIP + pose['bob']
    hipdx = pose['lean']*0.25 + TURN*0.5
    for i, (fx, lift) in enumerate(pose['feet']):
        side = -1 if i == 0 else 1
        hx = CX + hipdx + side*4
        # trailing foot sits further back than the lead one on a turned body
        fxx = CX + fx + TURN*0.35 + (-1.2 if side < 0 else 1.2)
        fyy = FEET - 4 - lift
        # knee leads the hip when the foot is forward, so the leg reads as bent
        bend = (fxx - hx) * 0.30 + 2.4
        kx, ky = bent(c, hx, hipy, fxx, fyy, bend, lg, 10, 6)
        c.ellipse(hx, hipy+1, 4.6, 4.0, lg)                   # hip/thigh mass
        c.ellipse(kx, ky, 3.0, 2.8, lg)                       # knee
        c.taper(hx-1, hipy, kx-1, ky, lg2, 4, 2)              # lit edge down the thigh
        c.taper(kx-1, ky, fxx-1, fyy, lg2, 2, 1)              # shin highlight
        c.rect(fxx-4, fyy, 7, 5, bt)                           # ankle
        c.rect(fxx-4, fyy, 7, 2, lg2)                          # cuff
        c.rect(fxx+1, fyy+2, 6, 3, bt)                         # toe, pointing forward
        c.rect(fxx-5, fyy+4, 13, 2, bt)                        # sole runs to the toe
        c.rect(fxx+5, fyy+3, 3, 1, lg2)
        if lift > 2: c.rect(fxx-5, fyy+5, 13, 1, p['ink'])     # airborne edge

def draw_torso(c, p, pose):
    """Torso in profile. The detail that sells it is putting the shirt opening,
    collar and trim on the FRONT EDGE - a placket down the centre of the chest
    is a head-on detail and makes the whole body read as facing the camera."""
    cl, cl2, cl3, tr = p['cloth'], p['cloth2'], p['cloth3'], p['trim']
    dy = pose['bob']
    edges = []
    for i, hw in enumerate(TORSO):
        y = SHOULDER + i + dy
        t = 1 - i/len(TORSO)*0.45
        x = CX + pose['lean']*0.4*t + TURN*0.9*t
        w = hw*PROFILE
        chest = 1.6 if 1 <= i <= 8 else (0.6 if i > 14 else 0)   # chest forward, waist tucked
        front, back = x + w + chest, x - w*0.92
        c.rect(back, y, front-back, 1, cl)
        c.rect(back, y, 2, 1, cl3)                        # spine side in shadow
        c.rect(front-2, y, 2, 1, cl2)                     # chest edge catches the light
        edges.append((y, front, back))
    for k, (y, front, back) in enumerate(edges):          # placket down the front edge
        if 2 <= k <= 16: c.rect(front-3, y, 2, 1, tr)
    y0, f0, b0 = edges[0]
    c.rect(b0+1, y0-1, (f0-b0)-2, 2, cl2)                 # shoulder line
    c.rect(f0-5, y0, 5, 3, cl2)                           # collar opens forward
    c.rect(f0-5, y0+2, 4, 1, tr)
    x = CX + pose['lean']*0.5 + TURN*0.8
    c.rect(x-2, 25+dy, 5, 5, p['skin'])                   # neck
    c.rect(x-2, 25+dy, 5, 2, p['skin3'])
    ys, fs, bs = edges[17]
    c.rect(bs, 46+dy, (fs-bs), 4, p['accent'])            # sash follows the body
    c.rect(bs, 46+dy, (fs-bs), 1, cl2)
    xh = CX + pose['lean']*0.25 + TURN*0.5
    if p['style'] == 'bob':                               # skirts hang off the hips
        c.rect(xh-7, HIP+dy, 15, 6, cl)
        c.rect(xh-9, HIP+5+dy, 18, 4, cl2)
        c.rect(xh-9, HIP+8+dy, 18, 2, cl3)
        for i in range(0, 18, 5): c.rect(xh-9+i, HIP+8+dy, 2, 2, tr)
    elif p['style'] == 'long':
        c.rect(xh-7, HIP+dy, 14, 9, cl)
        c.rect(xh-8, HIP+7+dy, 16, 3, cl3)
        for i in range(0, 16, 5): c.rect(xh-8+i, HIP+4+dy, 2, 6, tr)
    else:
        c.rect(xh-7, HIP+dy, 14, 4, cl)
        c.rect(xh-9, HIP+3+dy, 7, 12, cl3)                # coat tail streams behind
        c.rect(xh-9, HIP+3+dy, 7, 2, tr)
        c.rect(xh-10, HIP+9+dy, 4, 6, cl3)

def draw_arm(c, p, pose, back):
    sk  = p['skin2'] if back else p['skin']
    cl  = p['cloth3'] if back else p['cloth']
    cl2 = p['cloth3'] if back else p['cloth2']
    sx, sy = shoulder(pose, back)
    hxp, hyp = hand(pose, back)
    t = p['sleeve']
    side = -1 if back else 1
    # shoulder cap, tapering upper arm, elbow, then a slimmer forearm
    ex, ey = bent(c, sx, sy, sx+(hxp-sx)*t, sy+(hyp-sy)*t, side*2.2, cl, 10, 7)
    c.ellipse(sx, sy, 4.4, 4.0, cl)                    # deltoid
    c.ellipse(sx-1, sy-1, 2.6, 2.2, cl2)               # lit top of the shoulder
    c.taper(sx, sy+1, ex, ey, cl2, 4, 2)
    cx2, cy2 = sx+(hxp-sx)*t, sy+(hyp-sy)*t
    c.ellipse(cx2, cy2, 3.0, 2.8, cl)                  # cuff at the elbow
    bent(c, cx2, cy2, hxp, hyp, side*1.6, sk, 6, 4)
    c.taper(cx2, cy2, hxp, hyp, p['skin3'], 2, 1)      # underside shading
    c.ellipse(hxp, hyp+1, 3.2, 3.4, sk)
    c.ellipse(hxp-1, hyp, 1.8, 1.8, p['skin2'])
    c.rect(hxp-2, hyp+2, 4, 1, p['skin3'])

def eye_profile(c, p, x, y, mode):
    """The single visible eye of a head seen from the side, facing +x.
       Shape comes from the character's own face parameters, so nobody
       shares an expression."""
    ink, ec, ed = p['ink'], p['eye'], p['eye2']
    F = p['face']
    w, hgt = F['ew'], F['eh']
    if mode == 'closed':
        for i in range(w): c.set(x+i, y+hgt//2, ink)
        c.set(x+w-1, y+hgt//2-1, ink)
        return
    if mode == 'hurt':
        for i in range(w):
            c.set(x+i, y, ink); c.set(x+i, y+hgt-1, ink)
        return
    c.rect(x, y, w, hgt, '#ffffff')                       # sclera
    c.rect(x+w-3, y, 3, hgt, ec)                          # iris sits toward the front
    c.rect(x+w-3, y+hgt-1, 3, 1, ed)
    c.rect(x+w-2, y+1, 1, max(1, hgt-2), ink)             # pupil
    for i in range(w): c.set(x+i, y-1, ink)               # upper lash
    for k in range(F['lash']):                            # lash thickness at the back
        c.set(x, y+k, ink); c.set(x+1, y-1-k, ink)
    if F['droop']:
        c.set(x, y+1, ink); c.set(x+1, y+1, ink)          # outer corner falls
    c.set(x+w-1, y, '#ffffff')                            # catchlight
    for i in range(1, w-1): c.set(x+i, y+hgt, p['skin2']) # lower lid

def draw_head(c, p, pose):
    """Head in TRUE profile facing +x: one eye, a nose on the leading edge,
       the ear behind. A front-facing head mirrored left and right cannot read
       as facing anywhere, which is the whole reason this exists."""
    sk, sk2, sk3 = p['skin'], p['skin2'], p['skin3']
    F = p['face']
    dy = pose['bob']
    cx = CX + pose['lean']*0.55
    cy = HEADY + dy
    c.ellipse(cx-1, cy, 8.0, 9.2, sk)                     # skull, set back
    c.ellipse(cx+1, cy+3, 6.6, 6.2, sk)                   # cheek/muzzle forward
    # nose and lips on the leading edge
    nx = cx + 6
    c.rect(nx, cy+1, F['nose'], 2, sk)
    c.set(nx+F['nose'], cy+2, sk)
    c.set(nx+F['nose'], cy+3, sk2)
    c.rect(cx+4, cy+5, 3, 2, sk)                          # chin
    c.set(cx+6, cy+6, sk3)
    for x in range(int(cx-4), int(cx+6)): c.set(x, cy+8, sk3)   # jaw shadow
    c.rect(cx-6, cy+1, 3, 4, sk2)                         # ear, behind
    c.set(cx-5, cy+2, sk3)
    c.rect(cx-2, cy+9, 6, 4, sk)                          # neck
    c.rect(cx-2, cy+9, 6, 1, sk3)
    m = pose['eye']
    eye_profile(c, p, cx+1, cy, m)
    if m != 'closed':                                     # brow, per character
        by = cy - 3 - F['brow_h']
        for i in range(F['ew']):
            lift = 0
            if F['brow'] == 'angled': lift = -1 if i > F['ew']-3 else 0
            if F['brow'] == 'soft':   lift =  1 if i > F['ew']-3 else 0
            c.set(cx+1+i, by+lift, p['brow'])
            if F['brow'] == 'thick': c.set(cx+1+i, by+1+lift, p['brow'])
    mw = F['mouth']                                       # mouths stay small
    if m == 'fierce':
        c.rect(cx+5, cy+5, mw, 1, p['ink'])
        c.set(cx+5, cy+6, p['ink'])
    else:
        c.rect(cx+5, cy+5, mw, 1, sk3)
    c.rect(cx+1, cy+3, 3, 1, p['blush'])                  # blush on the cheek
    c.rect(cx+1, cy+4, 2, 1, p['blush'])
    return cx, cy

# --------------------------------------------------------------------- hair --
def hair_back(c, p, pose, cx, cy):
    st, h1, h2 = p['style'], p['hair'], p['hair2']
    sway = -pose['sway']          # trail: opposite the direction of travel
    if st == 'ponytail':
        c.taper(cx-6, cy-8, cx-13+sway*0.5, cy+2, h1, 9, 8)
        c.taper(cx-12+sway*0.4, cy, cx-16+sway, cy+30, h1, 8, 5)
        c.taper(cx-13+sway*0.4, cy+2, cx-16+sway, cy+24, h2, 4, 3)
        c.taper(cx-15+sway, cy+28, cx-18+sway*1.3, cy+38, h1, 4, 2)
    elif st == 'long':
        c.rect(cx-13, cy-8, 7, 44, h1)
        c.rect(cx+6, cy-8, 7, 44, h1)
        c.rect(cx-9, cy-8, 18, 22, h1)
        c.rect(cx-12, cy+8, 4, 24, h2)
        c.rect(cx+8, cy+8, 4, 24, h2)
        c.taper(cx-11, cy+34, cx-14+sway, cy+44, h1, 6, 3)
        c.taper(cx+11, cy+34, cx+14+sway, cy+44, h1, 6, 3)
    elif st == 'spiky':
        for sx, sy in ((-13,-5), (-8,-12), (0,-16), (8,-12), (13,-5)):
            c.taper(cx+sx*0.6, cy+sy*0.45, cx+sx+sway*0.35, cy+sy-2, h1, 7, 3)
        c.rect(cx-13, cy-5, 5, 18, h1); c.rect(cx+8, cy-5, 5, 18, h1)
        c.taper(cx-7, cy+16, cx-15+sway, cy+26, p['accent'], 6, 4)   # scarf
        c.taper(cx-13+sway*0.5, cy+24, cx-19+sway, cy+36, p['accent'], 4, 3)
    elif st == 'bob':
        c.ellipse(cx, cy+1, 12.4, 12.6, h1)
        c.rect(cx-13, cy-2, 5, 18, h1); c.rect(cx+8, cy-2, 5, 18, h1)
    elif st == 'braid':
        c.ellipse(cx, cy-1, 11.0, 11.0, h1)
        bx = cx - 14 + sway*0.35                          # clear of the torso, so it shows
        for k in range(7):                                    # plaited rope down one side
            yy = cy + 4 + k*6
            xx = bx + math.sin(k*1.0 + sway*0.15)*2.2
            c.ellipse(xx, yy, 4.6 - k*0.3, 3.6, h1)
            c.ellipse(xx-1, yy-1, 2.8 - k*0.22, 2.0, h2)
            c.set(xx+2, yy+2, p['ink'])                       # plait notch
        c.rect(bx-3, cy+45, 6, 4, p['accent'])                # tie
        c.rect(cx+8, cy-2, 4, 12, h1)
    elif st == 'crop':
        c.ellipse(cx, cy-2, 10.4, 9.6, h1)
        c.rect(cx-11, cy-4, 4, 9, h1); c.rect(cx+7, cy-4, 4, 9, h1)
    elif st == 'twin':
        c.ellipse(cx, cy-1, 10.6, 10.4, h1)
        for sgn, sc in ((-1, 1.3), (1, 0.8)):             # trailing tail hangs longer
            bx = cx + sgn*12
            c.taper(cx+sgn*7, cy-6, bx, cy+2, h1, 9, 8)
            c.taper(bx, cy+2, bx+sgn*3 + sway*0.35, cy+2+26*sc, h1, 8, 4)
            c.taper(bx, cy+4, bx+sgn*2 + sway*0.3, cy+4+18*sc, h2, 4, 2)

def hair_front(c, p, pose, cx, cy):
    st, h1, h2, h3 = p['style'], p['hair'], p['hair2'], p['hair3']
    T = TURN                                     # follow the three-quarter turn
    sh, ac = p['shine'], p['accent']
    sway = -pose['sway']          # trail: opposite the direction of travel
    top = cy - 4                                          # never below the brows
    c.ellipse(cx-1, cy-3, 8.8, 9.4, h1, ymax=int(top))
    c.ellipse(cx-1.5, cy-5, 7.2, 7.2, h2, ymax=int(top-2))
    # a swept fringe mass over the forehead, stopping above the eye. Drawing
    # it as separate vertical strands made a comb of spikes on the crown.
    c.ellipse(cx-1, cy-6.5, 8.4, 5.6, h1, ymax=int(cy-5))
    c.ellipse(cx-2.5, cy-7.5, 6.2, 3.8, h2, ymax=int(cy-7))
    c.taper(cx+4, cy-8, cx+7, cy-4, h1, 3, 2)                      # one swept tip
    for sx, w in ((-7,4), (-1,5), (5,3)):                          # soft shine streak
        c.rect(cx+sx, cy-9, w, 1, h3)
        c.rect(cx+sx+1, cy-10, max(1, w-2), 1, sh)
    c.rect(cx-10, cy-5, 4, 13, h1)                                 # lock hangs behind
    c.rect(cx-10, cy-5, 2, 10, h2)
    c.set(cx-8, cy+8, h1)
    if st == 'ponytail':
        c.rect(cx-9, cy-13, 5, 4, ac); c.rect(cx-8, cy-14, 3, 1, ac)
    elif st == 'long':
        c.rect(cx-3+T, cy-16, 6, 3, ac); c.rect(cx-2+T, cy-17, 4, 1, ac)
        c.rect(cx-14, cy-5, 4, 20, h1); c.rect(cx+12, cy-5, 2, 18, h1)
    elif st == 'spiky':
        for sx, sy in ((-11,-9), (-5,-13), (2,-14), (8,-11)):
            c.taper(cx+sx, cy+sy+5, cx+sx+sway*0.25, cy+sy-1, h2, 5, 2)
    elif st == 'bob':
        c.rect(cx-14, cy-6, 4, 17, h1); c.rect(cx+11, cy-6, 3, 15, h1)
        c.rect(cx-16, cy-10, 4, 7, ac); c.rect(cx+12, cy-10, 4, 7, ac)
        c.rect(cx-16, cy-10, 4, 2, '#ffffff'); c.rect(cx+12, cy-10, 4, 2, '#ffffff')
    elif st == 'braid':
        c.rect(cx-12, cy-6, 3, 13, h1)
        c.rect(cx-3, cy-15, 7, 3, ac)                         # headband
        c.rect(cx-3, cy-15, 7, 1, '#ffffff')
        for i in range(-8, 9, 4): c.set(cx+i, cy-12, h3)
    elif st == 'crop':
        for sx2 in (-8, -3, 2, 7):                            # short cropped spikes
            c.taper(cx+sx2, cy-8, cx+sx2+sway*0.15, cy-13, h2, 4, 2)
    elif st == 'twin':
        c.rect(cx-15, cy-9, 5, 6, ac); c.rect(cx+11, cy-9, 4, 5, ac)
        c.rect(cx-15, cy-9, 5, 2, '#ffffff'); c.rect(cx+11, cy-9, 4, 2, '#ffffff')
        c.rect(cx-11, cy-4, 3, 7, h1); c.rect(cx+8, cy-4, 3, 7, h1)

# ------------------------------------------------------------------ weapons --
def draw_weapon(c, p, pose):
    w = p['wep']
    hxp, hyp = hand(pose, False)
    ph = pose['wep']
    deg = -25 + (ph * 70 if ph >= 0 else ph * 110)
    ang = math.radians(deg)
    ux, uy = math.cos(ang), math.sin(ang)
    nx, ny = -uy, ux
    if w == 'katana':
        L, hxp = 26, min(hxp, 44)
        c.taper(hxp-ux*10, hyp-uy*10, hxp, hyp, p['grip'], 4, 4)               # tsuka
        c.line(hxp-ux*3-nx*4, hyp-uy*3-ny*4, hxp-ux*3+nx*4, hyp-uy*3+ny*4, p['accent'], 3)
        c.taper(hxp+ux*3+nx*2.4, hyp+uy*3+ny*2.4,
                hxp+ux*L+nx*1.8, hyp+uy*L+ny*1.8, p['ink'], 3, 2)              # spine
        c.taper(hxp+ux*3, hyp+uy*3, hxp+ux*L, hyp+uy*L, p['metal'], 4, 2)      # blade
        c.line(hxp+ux*5-nx, hyp+uy*5-ny, hxp+ux*(L-2)-nx, hyp+uy*(L-2)-ny, '#ffffff', 1)
    elif w == 'staff':
        # the shaft runs THROUGH the hand, so she is actually gripping it
        tilt = ux*4
        top = (hxp + tilt*1.6 + 5, hyp - 30)   # orb rides clear of her face
        bot = (hxp - tilt*0.8, min(hyp + 20, FEET-2))
        c.taper(bot[0], bot[1], top[0], top[1], p['metal'], 5, 5)
        c.line(bot[0]+1, bot[1]-2, top[0]+1, top[1]+2, p['grip'], 2)
        c.rect(hxp-3, hyp-3, 6, 7, p['grip'])          # grip wrap at the hand
        ox, oy = top[0], top[1] - 6
        c.ellipse(ox, oy, 6.4, 6.4, p['accent'])
        c.ellipse(ox, oy, 4.0, 4.0, '#ffe9a8')
        c.ellipse(ox-1.5, oy-1.5, 1.8, 1.8, '#ffffff')
        for dx2, dy2 in ((0,-9), (0,9), (8,-5)): c.rect(ox+dx2, oy+dy2, 2, 2, p['hair3'])
    elif w == 'daggers':
        c.taper(hxp+nx*2, hyp+ny*2, hxp+ux*14+nx*1.5, hyp+uy*14+ny*1.5, p['ink'], 3, 2)
        c.taper(hxp, hyp, hxp+ux*14, hyp+uy*14, p['metal'], 4, 2)
        c.line(hxp+ux*4, hyp+uy*4, hxp+ux*12, hyp+uy*12, '#ffffff', 1)
        c.rect(hxp-2, hyp, 5, 3, p['grip'])
        bx, by = hand(pose, True)
        c.taper(bx, by, bx-abs(ux)*12, by-uy*8, p['metal'], 4, 2)
        c.rect(bx-2, by, 5, 3, p['grip'])
    elif w == 'bow':
        gx, gy, R2 = hxp, hyp, 19
        for k in range(1, R2+1):                              # limbs curve away from the string
            t2 = k/R2
            dx2 = -t2*t2*7
            for sgn in (-1, 1):
                c.rect(gx+dx2, gy+sgn*k, 3, 1, p['metal'])
                if k > R2-3: c.rect(gx+dx2, gy+sgn*k, 3, 1, p['grip'])
        c.rect(gx-1, gy-2, 3, 5, p['metal'])                  # riser
        c.line(gx+3, gy-R2+1, gx+3, gy+R2-1, p['cloth3'], 1)  # string
        c.taper(gx-9, gy, gx+12, gy, p['grip'], 2, 2)         # nocked arrow
        c.rect(gx+12, gy-1, 4, 3, p['metal'])
        c.rect(gx-11, gy-3, 4, 7, p['accent'])                # fletching
        c.rect(gx-2, gy-4, 5, 9, p['grip'])                   # grip
    elif w == 'hammer':
        L = 20
        c.taper(hxp-ux*8, hyp-uy*8, hxp+ux*L, hyp+uy*L, p['grip'], 5, 4)   # haft
        hx2, hy2 = hxp+ux*L, hyp+uy*L
        px2, py2 = -uy, ux
        # half-pixel steps, or the rotation leaves holes and the head looks speckled
        for ai in range(-20, 21):
            for bi in range(-12, 13):
                a2, b2 = ai/2, bi/2
                col = p['metal']
                if abs(a2) > 8 or abs(b2) > 4.5: col = p['trim']
                elif b2 < -1.5: col = p['cloth2']
                c.set(hx2 + px2*a2 + ux*b2, hy2 + py2*a2 + uy*b2, col)
        c.rect(hxp-3, hyp-3, 6, 7, p['grip'])                 # grip wrap
    elif w == 'icelance':
        L, hxp = 22, min(hxp, 34)
        c.taper(hxp-ux*9, hyp-uy*9, hxp+ux*(L-6), hyp+uy*(L-6), p['grip'], 4, 3)
        c.taper(hxp+ux*5, hyp+uy*5, hxp+ux*L, hyp+uy*L, p['metal'], 6, 2)
        c.line(hxp+ux*7, hyp+uy*7, hxp+ux*(L-2), hyp+uy*(L-2), '#ffffff', 1)
        for k in (9, 14, 19):                                 # frost barbs
            bx3, by3 = hxp+ux*k, hyp+uy*k
            c.line(bx3, by3, bx3-uy*4, by3+ux*4, p['accent'], 1)
            c.line(bx3, by3, bx3+uy*4, by3-ux*4, p['accent'], 1)
        c.rect(hxp-3, hyp-3, 6, 7, p['grip'])
    elif w == 'gauntlet':
        for hx3, hy3 in (hand(pose, False), hand(pose, True)):
            c.ellipse(hx3, hy3+1, 5.4, 5.0, p['cloth2'])
            c.ellipse(hx3, hy3, 4.0, 3.6, p['metal'])
            c.rect(hx3-5, hy3+3, 10, 3, p['cloth3'])
            for k in (-3, 0, 3): c.set(hx3+k, hy3-3, p['trim'])
        hx4, hy4 = hand(pose, False)
        for k in range(4):                                    # arc of current
            c.set(hx4+7+k*2, hy4-6+((k%2)*4-2), p['accent'])
    elif w == 'glaive':
        L, hxp = 21, min(hxp, 33)
        c.taper(hxp-ux*14, hyp-uy*14, hxp+ux*(L-8), hyp+uy*(L-8), p['grip'], 4, 4)
        tx, ty = hxp+ux*L, hyp+uy*L
        c.taper(hxp+ux*(L-9), hyp+uy*(L-9), tx, ty, p['metal'], 8, 2)
        c.line(hxp+ux*(L-7)-uy*3, hyp+uy*(L-7)+ux*3, tx, ty, '#ffffff', 1)
        c.taper(hxp+ux*(L-10)-uy*2, hyp+uy*(L-10)+ux*2,
                hxp+ux*(L-3)-uy*6, hyp+uy*(L-3)+ux*6, p['metal'], 4, 2)
        c.rect(hxp-3, hyp-3, 6, 7, p['grip'])
    elif w == 'bombs':
        bx3, by3 = hxp, hyp
        c.ellipse(bx3, by3+2, 6.4, 6.4, p['cloth3'])
        c.ellipse(bx3-1.5, by3+0.5, 3.0, 3.0, p['metal'])
        c.rect(bx3-1, by3-6, 3, 4, p['grip'])                 # fuse cap
        c.line(bx3, by3-7, bx3+4, by3-13, p['grip'], 2)
        c.ellipse(bx3+5, by3-14, 2.6, 2.6, p['accent'])       # spark
        c.ellipse(bx3+5, by3-14, 1.2, 1.2, '#ffffff')
    elif w == 'lute':
        bx3, by3 = hxp-3, hyp-4
        c.ellipse(bx3, by3+6, 8.0, 9.0, p['metal'])           # body
        c.ellipse(bx3, by3+6, 5.6, 6.4, p['cloth2'])
        c.ellipse(bx3+1, by3+5, 2.4, 2.4, p['cloth3'])        # sound hole
        c.taper(bx3+4, by3-1, bx3+17, by3-15, p['grip'], 4, 3)  # neck
        c.rect(bx3+16, by3-18, 4, 4, p['grip'])
        for k in range(3):
            c.line(bx3+2+k, by3+2, bx3+15+k, by3-13, p['accent'], 1)
    elif w == 'greatsword':
        L, hxp = 30, min(hxp, 30)
        c.taper(hxp-ux*11, hyp-uy*11, hxp, hyp, p['grip'], 5, 5)              # long grip
        c.line(hxp-ux*2-nx*7, hyp-uy*2-ny*7, hxp-ux*2+nx*7, hyp-uy*2+ny*7, p['accent'], 4)
        c.taper(hxp+ux*4+nx*2.6, hyp+uy*4+ny*2.6,
                hxp+ux*L+nx*1.6, hyp+uy*L+ny*1.6, p['ink'], 4, 2)             # spine
        c.taper(hxp+ux*4, hyp+uy*4, hxp+ux*L, hyp+uy*L, p['metal'], 7, 3)     # broad blade
        c.line(hxp+ux*7-nx, hyp+uy*7-ny, hxp+ux*(L-2)-nx, hyp+uy*(L-2)-ny, '#ffffff', 1)
        for k in (10, 17, 24):                                                # runes
            c.set(hxp+ux*k, hyp+uy*k, p['trim'])
    elif w == 'twinswords':
        for sgn, hd in ((1, (hxp, hyp)), (-1, hand(pose, True))):
            bx3, by3 = hd
            ax, ay = (ux, uy) if sgn > 0 else (-abs(ux), uy*0.6)
            c.taper(bx3-ax*5, by3-ay*5, bx3, by3, p['grip'], 4, 4)
            c.taper(bx3+ax*2, by3+ay*2, bx3+ax*17, by3+ay*17, p['metal'], 5, 2)
            c.line(bx3+ax*4, by3+ay*4, bx3+ax*15, by3+ay*15, '#ffffff', 1)
            c.rect(bx3-2, by3-3, 4, 2, p['cloth2'])
    elif w == 'spear':
        L, hxp = 34, min(hxp, 28)
        c.taper(hxp-ux*15, hyp-uy*15, hxp+ux*(L-9), hyp+uy*(L-9), p['grip'], 4, 4)
        c.taper(hxp+ux*(L-11), hyp+uy*(L-11), hxp+ux*L, hyp+uy*L, p['metal'], 7, 2)
        c.line(hxp+ux*(L-9), hyp+uy*(L-9), hxp+ux*(L-1), hyp+uy*(L-1), '#ffffff', 1)
        c.line(hxp+ux*(L-12)-nx*4, hyp+uy*(L-12)-ny*4,
               hxp+ux*(L-12)+nx*4, hyp+uy*(L-12)+ny*4, p['accent'], 2)
        for k in range(3):                                                    # ribbon
            c.set(hxp-ux*(9+k*3), hyp-uy*(9+k*3)+k, p['trim'])
    elif w == 'grimoire':
        bx3, by3 = hxp - 4, hyp - 9
        c.rect(bx3, by3, 15, 15, p['metal'])
        c.rect(bx3+1, by3+1, 13, 13, p['cloth3'])
        c.rect(bx3+6, by3, 3, 15, p['grip'])
        c.ellipse(bx3+7, by3+7, 3.4, 3.4, p['accent'])
        c.ellipse(bx3+7, by3+7, 1.6, 1.6, '#ffffff')
        for k in range(4):                                                    # motes
            c.set(bx3+16+k*2, by3+2+k*3, p['accent'])
    elif w == 'tome':
        bx, by = hxp - 4, hyp - 9        # cradled against the palm
        c.rect(bx, by, 15, 15, p['metal'])
        c.rect(bx+1, by+1, 13, 13, '#fffaf0')
        for k in range(2, 13): c.rect(bx+3, by+k, 1, 1, p['cloth3'])
        for k in range(2, 13): c.rect(bx+11, by+k, 1, 1, p['cloth3'])
        c.rect(bx+6, by, 3, 15, p['grip'])
        c.rect(bx+6, by-2, 3, 2, p['accent']); c.rect(bx+6, by+15, 3, 2, p['accent'])
        c.rect(bx+3, by+4, 2, 2, p['accent']); c.rect(bx+10, by+8, 2, 2, p['accent'])

def grip_hand(c, p, pose):
    """redraw the front hand over the weapon so it reads as held, not floating"""
    hxp, hyp = hand(pose, False)
    c.ellipse(hxp, hyp+1, 3.0, 3.2, p['skin'])
    c.rect(hxp-2, hyp+2, 4, 1, p['skin3'])
    c.set(hxp-2, hyp-1, p['skin2'])

def draw_char(key, frame):
    p, pose = CHARS[key], POSES[frame]
    c = Cv(W, H)
    cx, cy = CX + pose['lean']*0.55, HEADY + pose['bob']
    hair_back(c, p, pose, cx, cy)
    draw_arm(c, p, pose, True)
    draw_legs(c, p, pose)
    draw_torso(c, p, pose)
    draw_head(c, p, pose)
    hair_front(c, p, pose, cx, cy)
    draw_arm(c, p, pose, False)
    draw_weapon(c, p, pose)
    grip_hand(c, p, pose)
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
 'sovereign': dict(ink='#0a0610', body='#2b2340', body2='#5b4d80', body3='#171128',
               eye='#ff3355', glow='#ffd24d', cloth='#120e20', cloth2='#3a2f5c'),
 'boss':  dict(ink='#120818', body='#a86bff', body2='#dcc0ff', body3='#6b3fbd',
               eye='#ff5d5d', glow='#ffc0c0', cloth='#2a1b4d', cloth2='#4a3480'),
}
MOB_ORDER = ['slime', 'bat', 'imp', 'brute', 'boss', 'sovereign']

SLIME_A = [(0,0), (2,0), (5,-2), (-3,-11), (-5,-17), (3,-4), (7,2), (-7,-6), (0,0), (9,3)]
BAT_A   = [(2,0), (7,2), (0,0), (9,4), (14,6), (7,2), (-3,-3), (12,7), (4,0), (5,-5)]
IMP_A   = [(0,0), (2,0), (0,2), (2,4), (0,2), (-2,0), (-4,0), (4,0), (0,0), (6,0)]
BRUTE_A = [(0,0), (2,0), (0,-2), (4,0), (0,-2), (4,0), (-4,0), (6,0), (0,-4), (4,0)]

def draw_slime(c, m, f):
    sq, hop = SLIME_A[f]
    w, h = 22 + sq, 15 - sq
    cy = FEET - h - max(0, -hop)
    c.ellipse(CX, cy, w, h, m['body'])
    c.ellipse(CX, cy - h*0.35, w*0.72, h*0.55, m['body2'])
    c.ellipse(CX - w*0.34, cy - h*0.52, 4.4, 2.8, m['glow'])
    c.ellipse(CX, cy + h*0.52, w*0.86, h*0.34, m['body3'])
    ex = 8
    if f == 9:
        for i in range(-4, 5):
            c.set(CX-ex+i, cy-2+i, m['eye']); c.set(CX-ex+i, cy-2-i, m['eye'])
            c.set(CX+ex+i, cy-2+i, m['eye']); c.set(CX+ex+i, cy-2-i, m['eye'])
    else:
        squint = 2 if f in (6, 7) else 0
        for dx in (-ex, ex):
            c.rect(CX+dx-2, cy-4+squint, 5, 7-squint*3, m['eye'])
            if not squint: c.rect(CX+dx, cy-3, 2, 2, '#ffffff')
        c.rect(CX-4, cy+4, 8, 2, m['eye'])
    if f in (3, 4):
        c.ellipse(CX-11, FEET-3, 4, 2.4, m['body3'])
        c.ellipse(CX+11, FEET-2, 4.4, 2.2, m['body3'])

def draw_bat(c, m, f):
    flap, bob = BAT_A[f]
    cy = 46 - bob
    SPAN = 17
    tipy = cy - 4 - flap
    for sgn in (-1, 1):
        for k in range(1, SPAN+1):
            t  = k/SPAN
            x  = CX + sgn*(7+k)
            up = (cy-7) + (tipy - (cy-7))*t
            lo = up + max(4, 15 - 9*t)
            if k % 5 == 0: lo -= 3
            c.line(x, up, x, lo, m['body3'], 1)
            c.line(x, up, x, up+2, m['body'], 1)
            c.set(x, lo, m['body'])
        for k in (5, 11, SPAN):
            t  = k/SPAN
            x  = CX + sgn*(7+k)
            up = (cy-7) + (tipy - (cy-7))*t
            c.line(x, up, x, up + max(4, 15 - 9*t), m['body'], 1)
    c.ellipse(CX, cy, 8.6, 10.4, m['body'])
    c.ellipse(CX, cy-2, 6.4, 7.4, m['body2'])
    c.ellipse(CX, cy+6, 5.0, 3.6, m['body3'])
    c.taper(CX-5, cy-8, CX-10, cy-19, m['body'], 5, 2)
    c.taper(CX+5, cy-8, CX+10, cy-19, m['body'], 5, 2)
    if f == 9:
        for i in range(-2, 3):
            c.set(CX-5+i, cy-2+i, m['eye']); c.set(CX-5+i, cy-2-i, m['eye'])
            c.set(CX+5+i, cy-2+i, m['eye']); c.set(CX+5+i, cy-2-i, m['eye'])
    else:
        for dx in (-7, 2):
            c.rect(CX+dx, cy-4, 5, 5, m['eye'])
            c.rect(CX+dx+3, cy-4, 2, 2, m['glow'])
    if f in (6, 7):
        c.rect(CX-3, cy+3, 2, 5, '#ffffff'); c.rect(CX+2, cy+3, 2, 5, '#ffffff')
    else:
        c.rect(CX-4, cy+4, 8, 2, m['ink'])

def draw_imp(c, m, f):
    lean, bob = IMP_A[f]
    hy = 26 + bob
    c.ellipse(CX+lean*0.4, hy, 13.0, 11.6, m['body'])
    c.ellipse(CX+lean*0.4, hy-4, 10.2, 7.6, m['body2'])
    for sgn in (-1, 1):
        c.taper(CX+sgn*10, hy-7, CX+sgn*16, hy-20, m['body3'], 7, 3)
    c.rect(CX-16, hy+1, 5, 7, m['body3']); c.rect(CX+11, hy+1, 5, 7, m['body3'])
    if f == 9:
        for i in range(-2, 3):
            c.set(CX-7+i, hy+i, m['eye']); c.set(CX-7+i, hy-i, m['eye'])
            c.set(CX+5+i, hy+i, m['eye']); c.set(CX+5+i, hy-i, m['eye'])
    else:
        for dx in (-9, 4):
            c.rect(CX+dx, hy-2, 6, 5, m['eye'])
            c.rect(CX+dx+4, hy-2, 2, 2, m['glow'])
    c.rect(CX-5, hy+7, 10, 2, m['ink'])
    c.rect(CX-11, hy+14, 22, 22, m['cloth'])
    c.rect(CX-9, hy+16, 18, 7, m['cloth2'])
    c.rect(CX-13, hy+33, 26, 7, m['cloth'])
    c.rect(CX-13, hy+38, 26, 2, m['ink'])
    c.taper(CX-11, hy+17, CX-16-lean, hy+29, m['body'], 6, 5)
    c.taper(CX+11, hy+17, CX+16+lean, hy+29, m['body'], 6, 5)
    if f in (6, 7, 8):
        ox, oy = CX + 20 + lean*2, hy + 28
        r = 4.5 if f == 6 else 7.0
        c.ellipse(ox, oy, r, r, m['eye'])
        c.ellipse(ox, oy, r*0.5, r*0.5, m['glow'])

def draw_brute(c, m, f, boss=False, crown=False):
    lean, bob = BRUTE_A[f]
    hy = 22 + bob
    cxx = CX + lean*0.5
    if crown:                                             # cape behind everything
        for k in range(26):
            w = 15 + k*0.55
            c.rect(cxx-w, hy+12+k, w*2, 1, m['cloth'])
            c.rect(cxx-w, hy+12+k, 3, 1, m['cloth2'])
            c.rect(cxx+w-3, hy+12+k, 3, 1, m['cloth2'])
    c.ellipse(cxx, hy, 16.0, 14.4, m['body'])
    c.ellipse(cxx, hy-4, 13.0, 9.6, m['body2'])
    for sgn in (-1, 1):
        c.taper(cxx+sgn*12, hy-9, cxx+sgn*(21 if boss else 17), hy-(27 if boss else 21),
                m['body3'], 8, 3)
        if boss: c.taper(cxx+sgn*16, hy-18, cxx+sgn*10, hy-29, m['body3'], 5, 3)
    if f == 9:
        for i in range(-3, 4):
            c.set(cxx-8+i, hy+i, m['eye']); c.set(cxx-8+i, hy-i, m['eye'])
            c.set(cxx+7+i, hy+i, m['eye']); c.set(cxx+7+i, hy-i, m['eye'])
    else:
        for dx in (-11, 5):
            c.rect(cxx+dx, hy-2, 7, 5, m['eye'])
            c.rect(cxx+dx, hy-2, 7, 2, m['glow'])
    if f in (6, 7, 8):
        c.rect(cxx-7, hy+6, 14, 8, m['ink'])
        for i in range(0, 14, 3): c.rect(cxx-7+i, hy+6, 2, 2, '#ffffff')
        for i in range(0, 14, 3): c.rect(cxx-7+i, hy+12, 2, 2, '#ffffff')
    else:
        c.rect(cxx-7, hy+8, 14, 2, m['ink'])
    c.rect(cxx-14, hy+16, 28, 26, m['cloth'])
    c.rect(cxx-11, hy+18, 22, 11, m['body2'])
    c.rect(cxx-14, hy+37, 28, 6, m['cloth2'])
    ax = 22 if f in (6, 8) else 16
    c.taper(cxx-14, hy+19, cxx-ax-lean, hy+(8 if f in (6,8) else 38), m['body'], 9, 7)
    c.taper(cxx+14, hy+19, cxx+ax+lean, hy+(8 if f in (6,8) else 38), m['body'], 9, 7)
    c.rect(cxx-14, hy+42, 11, FEET-(hy+42), m['cloth'])
    c.rect(cxx+3, hy+42, 11, FEET-(hy+42), m['cloth'])
    c.rect(cxx-16, FEET-5, 14, 5, m['ink']); c.rect(cxx+2, FEET-5, 14, 5, m['ink'])
    if crown:                                             # a crown of shards
        for k, (sx, sh2) in enumerate(((-13,7), (-7,12), (0,15), (7,12), (13,7))):
            c.taper(cxx+sx, hy-13, cxx+sx, hy-13-sh2, m['glow'], 4, 2)
        c.rect(cxx-15, hy-14, 30, 3, m['glow'])
        c.rect(cxx-15, hy-14, 30, 1, '#ffffff')
        for sgn in (-1, 1):                               # shoulder plates
            c.taper(cxx+sgn*15, hy+16, cxx+sgn*23, hy+22, m['cloth2'], 9, 5)
    if f == 7:
        for dx in (-24, -18, 18, 24): c.ellipse(cxx+dx, FEET-3, 4.5, 2.5, m['body3'])

def draw_mob(key, f):
    m = MOBS[key]; c = Cv(W, H)
    if   key == 'slime': draw_slime(c, m, f)
    elif key == 'bat':   draw_bat(c, m, f)
    elif key == 'imp':   draw_imp(c, m, f)
    elif key == 'brute': draw_brute(c, m, f, False)
    elif key == 'boss':  draw_brute(c, m, f, True)
    elif key == 'sovereign': draw_brute(c, m, f, True, True)
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
            if key in CHARS:      c = draw_char(key, f)
            elif f < MOB_FRAMES:  c = draw_mob(key, f)
            else:                 continue
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
        zoom(px, [0, 3, 7, 9, 10], [0,1,2,3], 6, os.path.join(ROOT, 'tools', 'closeup.png'))
    if 'mobs' in sys.argv:
        base = len(ORDER)
        zoom(px, [0, 2, 3, 6, 7, 9], list(range(base, base+len(MOB_ORDER))), 5,
             os.path.join(ROOT, 'tools', 'mobs.png'))
    if 'preview' in sys.argv:
        zoom(px, list(range(FRAMES)), list(range(len(rows))), 3, os.path.join(ROOT, 'tools', 'preview.png'))
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
