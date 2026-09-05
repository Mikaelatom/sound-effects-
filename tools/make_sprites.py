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
FRAMES = 18
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
    leg='#4a5f96', leg2='#7388bf', boot='#283053',
    metal='#eaf0ff', grip='#2a2440', sleeve=0.52, face=dict(ew=5, eh=4, lash=2, droop=0, brow='angled', brow_h=1, nose=2, mouth=2),
    style='ponytail', outfit='skirt', wep='katana'),
 'kagura': dict(ink='#160d2c', hair='#4d2478', hair2='#8e51cc', hair3='#c79cf5', shine='#e7d2ff',
    skin='#ffe0c6', skin2='#daa385', skin3='#b8815f', blush='#ff9db0',
    eye='#ffd24d', eye2='#a86c12', brow='#4d2478',
    cloth='#33215e', cloth2='#5b3f9c', cloth3='#1b1136', trim='#8e51cc', accent='#ffcc4d',
    leg='#5c38a9', leg2='#876dc5', boot='#2f1d5d',
    metal='#4a3672', grip='#241a3e', sleeve=0.80, face=dict(ew=5, eh=4, lash=2, droop=1, brow='soft',   brow_h=2, nose=2, mouth=1),
    style='long', outfit='robe', wep='staff'),
 'ren': dict(ink='#0d0a17', hair='#201b38', hair2='#453c78', hair3='#7d70ae', shine='#a99ddb',
    skin='#f0cba9', skin2='#c2967a', skin3='#9c745c', blush='#e8807f',
    eye='#ff4d5d', eye2='#8f1d2c', brow='#201b38',
    cloth='#332b56', cloth2='#524791', cloth3='#191430', trim='#ff4d5d', accent='#ff4d5d',
    leg='#534997', leg2='#8379b9', boot='#2f2a51',
    metal='#d5cdfa', grip='#141020', sleeve=0.58, face=dict(ew=5, eh=3, lash=0, droop=0, brow='angled', brow_h=1, nose=3, mouth=1),
    style='spiky', outfit='coat', wep='daggers'),
 'suzume': dict(ink='#123a2a', hair='#2f7a5c', hair2='#57bd8f', hair3='#a6f0cd', shine='#dcffee',
    skin='#ffe0c2', skin2='#dfab84', skin3='#b8825f', blush='#ff9db0',
    eye='#ffc247', eye2='#9c6c0e', brow='#2f7a5c',
    cloth='#eaf7f0', cloth2='#ffffff', cloth3='#9fc7b4', trim='#57bd8f', accent='#ffc247',
    leg='#519076', leg2='#7ab89c', boot='#275347',
    metal='#c9a06a', grip='#4a3320', sleeve=0.62, face=dict(ew=6, eh=4, lash=1, droop=0, brow='flat',   brow_h=2, nose=2, mouth=1),
    style='braid', outfit='shorts', wep='bow'),
 'gorou': dict(ink='#1b1a26', hair='#5a3a1e', hair2='#8f5f2f', hair3='#c99a5c', shine='#e8c99a',
    skin='#e8b98e', skin2='#c08f66', skin3='#96694a', blush='#e08a6a',
    eye='#ff9d3d', eye2='#8a4410', brow='#5a3a1e',
    cloth='#5a6478', cloth2='#8892ab', cloth3='#363d4d', trim='#e0a52c', accent='#e0a52c',
    leg='#53658d', leg2='#7a8fb8', boot='#2c374e',
    metal='#cfd8e8', grip='#3a2a1a', sleeve=0.74, face=dict(ew=4, eh=3, lash=0, droop=0, brow='thick',  brow_h=1, nose=3, mouth=2),
    style='crop', outfit='armor', wep='hammer'),
 'hinata': dict(ink='#38290f', hair='#c99527', hair2='#ffdc7f', hair3='#fff5cf', shine='#ffffff',
    skin='#ffe6cf', skin2='#e0ad88', skin3='#bd8a66', blush='#ffa8a8',
    eye='#57e08d', eye2='#1a7a4c', brow='#c99527',
    cloth='#fffaf0', cloth2='#ffffff', cloth3='#dcc59a', trim='#7cffa8', accent='#7cffa8',
    leg='#b5842b', leg2='#ffbb33', boot='#594521',
    metal='#c04a6a', grip='#8d2f4c', sleeve=0.50, face=dict(ew=6, eh=5, lash=2, droop=0, brow='soft',   brow_h=2, nose=2, mouth=1),
    style='bob', outfit='dress', wep='tome'),
 'yura': dict(ink='#16324a', hair='#5f8fc9', hair2='#a8d4f5', hair3='#e0f4ff', shine='#ffffff',
    skin='#fff0e2', skin2='#e6c3ad', skin3='#c09680', blush='#ffb0c0',
    eye='#7fe0ff', eye2='#1d6f96', brow='#5f8fc9',
    cloth='#dff2ff', cloth2='#ffffff', cloth3='#8fb4d4', trim='#5fe6ff', accent='#5fe6ff',
    leg='#4a7697', leg2='#5e94d4', boot='#244356',
    metal='#7fc9ec', grip='#2c4358', sleeve=0.72, face=dict(ew=5, eh=4, lash=2, droop=1, brow='flat',   brow_h=2, nose=2, mouth=1),
    style='hime', outfit='robe', wep='icelance'),
 'kaito': dict(ink='#171628', hair='#8a7a20', hair2='#ffe14d', hair3='#fff6a8', shine='#ffffff',
    skin='#f2c9a4', skin2='#cfa07c', skin3='#a2795a', blush='#e88a7a',
    eye='#ffe14d', eye2='#8a6410', brow='#8a7a20',
    cloth='#2b2a3c', cloth2='#4a4866', cloth3='#191826', trim='#ffe14d', accent='#ffe14d',
    leg='#57538d', leg2='#807ab8', boot='#2f2c4e',
    metal='#cfd8e8', grip='#2b2a14', sleeve=0.45, face=dict(ew=6, eh=4, lash=0, droop=0, brow='angled', brow_h=1, nose=2, mouth=2),
    style='wolfcut', outfit='jacket', wep='gauntlet'),
 'momo': dict(ink='#33220f', hair='#7a4a1e', hair2='#c78a45', hair3='#e8c08a', shine='#fff0d0',
    skin='#ffd9bd', skin2='#dda989', skin3='#b8825f', blush='#ff9db0',
    eye='#ffcc4d', eye2='#8a6410', brow='#7a4a1e',
    cloth='#7d5a33', cloth2='#a67f4c', cloth3='#4a3320', trim='#ffcc4d', accent='#ffcc4d',
    leg='#92724f', leg2='#b8987a', boot='#4e3b2c',
    metal='#eae2c8', grip='#241a0e', sleeve=0.50, face=dict(ew=6, eh=5, lash=1, droop=0, brow='thick',  brow_h=2, nose=3, mouth=2),
    style='twin', outfit='skirt', wep='glaive'),
 'chiyo': dict(ink='#2a1014', hair='#96311f', hair2='#ff8b4d', hair3='#ffc79f', shine='#ffe8d0',
    skin='#ffdcc0', skin2='#e0ab88', skin3='#b8815f', blush='#ff9db0',
    eye='#ff8b4d', eye2='#8a3a12', brow='#96311f',
    cloth='#3a2028', cloth2='#5e3440', cloth3='#241318', trim='#ff8b4d', accent='#ff8b4d',
    leg='#8d5366', leg2='#b87a90', boot='#4e2c3a',
    metal='#c9563a', grip='#3a2018', sleeve=0.55, face=dict(ew=6, eh=5, lash=1, droop=0, brow='angled', brow_h=2, nose=2, mouth=1),
    style='bun', outfit='apron', wep='bombs'),
 'nari': dict(ink='#241428', hair='#7a3f8a', hair2='#d68ae6', hair3='#f4c2ff', shine='#ffffff',
    skin='#ffe6cf', skin2='#e2b492', skin3='#bd8a66', blush='#ffa8c0',
    eye='#ffd24d', eye2='#96690e', brow='#7a3f8a',
    cloth='#3a2246', cloth2='#5e3a6e', cloth3='#241428', trim='#ffd24d', accent='#ffd24d',
    leg='#7e4d94', leg2='#a57ab8', boot='#422952',
    metal='#c9a05a', grip='#4a3320', sleeve=0.60, face=dict(ew=5, eh=4, lash=2, droop=1, brow='soft',   brow_h=2, nose=2, mouth=1),
    style='curls', outfit='dress', wep='lute'),
 'seryn': dict(ink='#1a2338', hair='#b8912f', hair2='#ffe08a', hair3='#fff6cf', shine='#ffffff',
    skin='#ffe6cf', skin2='#e0b48f', skin3='#b8886a', blush='#ffa8b8',
    eye='#5fe0a8', eye2='#12704f', brow='#b8912f',
    cloth='#cfd8ea', cloth2='#f2f6ff', cloth3='#6f7f9c', trim='#3f6fd0', accent='#ffcc4d',
    leg='#53658d', leg2='#7a8db8', boot='#2c374e',
    metal='#eef3ff', grip='#2b3350',
    face=dict(ew=5, eh=4, lash=1, droop=0, brow='flat', brow_h=2, nose=2, mouth=1),
    sleeve=0.86, style='crown', outfit='armor', wep='greatsword'),
 'aldric': dict(ink='#2a1414', hair='#8a8a96', hair2='#d8d8e4', hair3='#ffffff', shine='#ffffff',
    skin='#c9906a', skin2='#a3714f', skin3='#7d5439', blush='#c96a5a',
    eye='#a8b4c8', eye2='#4a5568', brow='#8a8a96',
    cloth='#8f2020', cloth2='#c33a3a', cloth3='#4a1010', trim='#2b2b33', accent='#2b2b33',
    leg='#53538d', leg2='#7a7ab8', boot='#2c2c4e',
    metal='#e8ecf5', grip='#2b2b33',
    face=dict(ew=5, eh=3, lash=0, droop=0, brow='angled', brow_h=1, nose=3, mouth=1),
    sleeve=0.70, style='slick', outfit='longcoat', wep='twinswords'),
 'kassandra': dict(ink='#33101c', hair='#8f1f33', hair2='#e04a63', hair3='#ff9db0', shine='#ffd0d8',
    skin='#ffdcc0', skin2='#e0a184', skin3='#b87f62', blush='#ff8fa8',
    eye='#ffcc4d', eye2='#8a5a10', brow='#8f1f33',
    cloth='#3a1420', cloth2='#5e2436', cloth3='#220c14', trim='#e04a63', accent='#ffcc4d',
    leg='#a63a5d', leg2='#be7494', boot='#5c1f3d',
    metal='#ffd0d8', grip='#3a1420',
    face=dict(ew=6, eh=4, lash=1, droop=0, brow='angled', brow_h=1, nose=2, mouth=1),
    sleeve=0.55, style='sidetail', outfit='wrap', wep='spear'),
 'nyx': dict(ink='#100a1c', hair='#2a1c40', hair2='#5b3f86', hair3='#9c7fc9', shine='#c9b0e8',
    skin='#f0dcd0', skin2='#cbb0a4', skin3='#a08878', blush='#d68a9c',
    eye='#b07cff', eye2='#4a2578', brow='#2a1c40',
    cloth='#1e142e', cloth2='#3a2a56', cloth3='#120c1c', trim='#b07cff', accent='#b07cff',
    leg='#64429e', leg2='#9577bb', boot='#35215a',
    metal='#4a2f6b', grip='#241634',
    face=dict(ew=5, eh=4, lash=2, droop=1, brow='soft', brow_h=2, nose=2, mouth=1),
    sleeve=0.88, style='wave', outfit='longcoat', wep='grimoire'),
 'toma': dict(ink='#22221a', hair='#6b6320', hair2='#b8ab3d', hair3='#e8e0a8', shine='#fff8d0',
    skin='#f2c9a4', skin2='#cfa07c', skin3='#a2795a', blush='#e8907a',
    eye='#ffe14d', eye2='#8a6410', brow='#6b6320',
    cloth='#3a3a2a', cloth2='#5a5a40', cloth3='#22221a', trim='#ffe14d', accent='#ffe14d',
    leg='#8d8d53', leg2='#b8b87a', boot='#4e4e2c',
    metal='#8a8030', grip='#2b2a14', sleeve=0.68, face=dict(ew=4, eh=3, lash=0, droop=0, brow='flat',   brow_h=1, nose=2, mouth=1),
    style='mohawk', outfit='jacket', wep='coilrod'),

 'atom': dict(ink='#1a0e2e', hair='#7b3fd4', hair2='#b07cff', hair3='#dcc0ff', shine='#f3e9ff',
    skin='#ffdcc0', skin2='#e5a184', skin3='#c98166', blush='#d98bff',
    eye='#c08bff', eye2='#5a2a9c', brow='#7b3fd4',
    cloth='#241a3d', cloth2='#3a2b5e', cloth3='#150e26', trim='#b07cff', accent='#8f5fff',
    leg='#4a3d7a', leg2='#7365b0', boot='#241d3d',
    metal='#dcc9ff', grip='#3a2b5e', cape='#5a2a9c', cape2='#8f5fff',
    face=dict(ew=5, eh=4, lash=1, droop=0, brow='angled', brow_h=2, nose=2, mouth=1),
    sleeve=0.74, style='messy', outfit='coat', wep='atomblade'),

 'rei': dict(ink='#2a0f12', hair='#c9302f', hair2='#ff6b5a', hair3='#ffb0a0', shine='#ffe0d8',
    skin='#e8b892', skin2='#c4906a', skin3='#9c6c4c', blush='#ff8b8b',
    eye='#ffb347', eye2='#8f4a10', brow='#c9302f',
    cloth='#3d2018', cloth2='#5e3427', cloth3='#24130e', trim='#e0a52c', accent='#c9302f',
    leg='#8d5a3a', leg2='#b8845e', boot='#4e2f1c',
    metal='#d8d4cc', grip='#3d2018',
    face=dict(ew=6, eh=3, lash=0, droop=0, brow='thick', brow_h=1, nose=3, mouth=1),
    sleeve=0.34, style='topknot', outfit='wrap', wep='greataxe'),

 'odette': dict(ink='#160f2a', hair='#e8e2f5', hair2='#ffffff', hair3='#c9bde8', shine='#ffffff',
    skin='#f5e0d0', skin2='#d4b298', skin3='#ab8a72', blush='#e0a0c0',
    eye='#9fd8ff', eye2='#3a6a9c', brow='#c9bde8',
    cloth='#2b2145', cloth2='#413466', cloth3='#181231', trim='#9fd8ff', accent='#c08bff',
    leg='#5a4f8d', leg2='#8379b8', boot='#2b2451',
    metal='#c9d8f0', grip='#2b2145',
    face=dict(ew=5, eh=4, lash=2, droop=1, brow='soft', brow_h=2, nose=2, mouth=1),
    sleeve=0.90, style='veil', outfit='dress', wep='scythe'),

 'bao': dict(ink='#2b1508', hair='#2b2116', hair2='#4e3d28', hair3='#7d6444', shine='#c0a878',
    skin='#e0b088', skin2='#bc8a62', skin3='#946745', blush='#e08b6a',
    eye='#ff8b4d', eye2='#8f3a10', brow='#2b2116',
    cloth='#4a3520', cloth2='#6b4d30', cloth3='#2b1e12', trim='#ff8b4d', accent='#e0a52c',
    leg='#8d7453', leg2='#b8a07a', boot='#4e3f2c',
    metal='#b8b8c0', grip='#2b1e12',
    face=dict(ew=5, eh=3, lash=0, droop=1, brow='angled', brow_h=1, nose=3, mouth=1),
    sleeve=0.62, style='undercut', outfit='shorts', wep='rifle'),

 'iris': dict(ink='#0f2a1e', hair='#3fd48b', hair2='#7cffbc', hair3='#c0ffe0', shine='#e8fff4',
    skin='#ffd8bc', skin2='#e09d7e', skin3='#bd7a5e', blush='#ff9db0',
    eye='#7cffa8', eye2='#1a7d50', brow='#3fd48b',
    cloth='#1e3d33', cloth2='#2e5c4d', cloth3='#132620', trim='#7cffbc', accent='#ffe14d',
    leg='#4a8d70', leg2='#73b89c', boot='#24503e',
    metal='#d8e8e0', grip='#132620',
    face=dict(ew=6, eh=4, lash=1, droop=0, brow='angled', brow_h=1, nose=2, mouth=1),
    sleeve=0.44, style='pixie', outfit='apron', wep='hookblade'),

 'shion': dict(ink='#0b0a16', hair='#1c1b30', hair2='#3a3856', hair3='#6b6890', shine='#a8a4d0',
    skin='#f0d0b4', skin2='#cda488', skin3='#a37f66', blush='#e08b8b',
    eye='#4ad6c0', eye2='#12655c', brow='#1c1b30',
    cloth='#15141f', cloth2='#2a2838', cloth3='#0b0a12', trim='#4ad6c0', accent='#6b6890',
    leg='#3d3d5c', leg2='#666690', boot='#1c1c2e',
    metal='#d8dce8', grip='#15141f',
    face=dict(ew=5, eh=3, lash=0, droop=1, brow='angled', brow_h=1, nose=2, mouth=1),
    sleeve=0.92, style='shade', outfit='uniform', wep='sealcards'),

 'toya': dict(ink='#141726', hair='#dfe6f5', hair2='#ffffff', hair3='#b6c4e0', shine='#ffffff',
    skin='#f7ddc6', skin2='#d5b096', skin3='#ac8a72', blush='#e0a0a8',
    eye='#6fd4ff', eye2='#1e5f96', brow='#dfe6f5',
    cloth='#121422', cloth2='#232840', cloth3='#080a12', trim='#6fd4ff', accent='#4a9fe0',
    leg='#3a4166', leg2='#636b96', boot='#191c2e',
    metal='#e6ecff', grip='#232840',
    face=dict(ew=6, eh=4, lash=1, droop=0, brow='soft', brow_h=2, nose=2, mouth=1, blind=1),
    sleeve=1.00, style='halo', outfit='highcollar', wep='bare'),

 'yuuma': dict(ink='#2a1220', hair='#e8657f', hair2='#ff9db0', hair3='#ffd0d8', shine='#fff0f4',
    skin='#f5c9a4', skin2='#cfa07c', skin3='#a2795a', blush='#ff8b8b',
    eye='#ffb347', eye2='#8f4a10', brow='#e8657f',
    cloth='#2b2b3d', cloth2='#43435e', cloth3='#191925', trim='#e8657f', accent='#ffd24d',
    leg='#4a4a6b', leg2='#73739c', boot='#232333',
    metal='#e0e0ea', grip='#2b2b3d',
    face=dict(ew=6, eh=4, lash=0, droop=0, brow='thick', brow_h=1, nose=2, mouth=2),
    sleeve=0.60, style='bristle', outfit='hoodie', wep='wraps'),

 'suimu': dict(ink='#0d2a33', hair='#5fd8e6', hair2='#a8f0f7', hair3='#d8fbff', shine='#ffffff',
    skin='#fce3cc', skin2='#dcb897', skin3='#b2907a', blush='#ff9db0',
    eye='#ffd24d', eye2='#a06a10', brow='#5fd8e6',
    cloth='#e8f6fa', cloth2='#ffffff', cloth3='#9dc3d0', trim='#3fb8cc', accent='#ffd24d',
    leg='#4a7f92', leg2='#73a8bb', boot='#22414d',
    metal='#dff4fa', grip='#3fb8cc',
    face=dict(ew=6, eh=4, lash=1, droop=0, brow='soft', brow_h=2, nose=2, mouth=1),
    sleeve=0.95, style='dropcurl', outfit='cloak', wep='slimeorb'),

 'homura': dict(ink='#2b0d0d', hair='#d63a2a', hair2='#ff7a52', hair3='#ffb894', shine='#ffe0d0',
    skin='#f0c49c', skin2='#cd9a74', skin3='#a3735a', blush='#ff8b8b',
    eye='#ffd24d', eye2='#8f4a10', brow='#d63a2a',
    cloth='#1e1420', cloth2='#33223a', cloth3='#120b16', trim='#e0a52c', accent='#d63a2a',
    leg='#5c3a4a', leg2='#8a6274', boot='#2b1a24',
    metal='#e8ecf5', grip='#1e1420',
    face=dict(ew=5, eh=3, lash=0, droop=0, brow='angled', brow_h=1, nose=3, mouth=1),
    sleeve=0.30, style='oni', outfit='haori', wep='odachi'),

 'mirika': dict(ink='#2b0c22', hair='#ff6ab0', hair2='#ffa8d4', hair3='#ffd6ea', shine='#fff0f8',
    skin='#ffdcc6', skin2='#e0ab8f', skin3='#b98871', blush='#ff7ab0',
    eye='#ff2e6a', eye2='#8f0f33', brow='#ff6ab0',
    cloth='#2b1830', cloth2='#452a4e', cloth3='#170d1b', trim='#ffd24d', accent='#ff2e6a',
    leg='#6b3f66', leg2='#9c6b96', boot='#33203a',
    metal='#ffd6ea', grip='#2b1830',
    face=dict(ew=6, eh=4, lash=2, droop=0, brow='angled', brow_h=2, nose=2, mouth=1),
    sleeve=0.20, style='dragontail', outfit='harness', wep='dragongaunt'),

 'noir': dict(ink='#0a0810', hair='#141420', hair2='#2e2e42', hair3='#55556e', shine='#8a8aa8',
    skin='#e8cfae', skin2='#c4a888', skin3='#9b826a', blush='#c98b8b',
    eye='#ff3355', eye2='#7d0f22', brow='#141420',
    cloth='#0f0f18', cloth2='#22222f', cloth3='#070709', trim='#c9a54a', accent='#ff3355',
    leg='#2b2b3d', leg2='#4e4e6b', boot='#131320',
    metal='#f2eee0', grip='#0f0f18',
    face=dict(ew=5, eh=3, lash=1, droop=1, brow='angled', brow_h=2, nose=3, mouth=1),
    sleeve=1.00, style='combed', outfit='tailcoat', wep='gloves'),

 'alwyn': dict(ink='#152621', hair='#cfe8d8', hair2='#ffffff', hair3='#a8ccb8', shine='#ffffff',
    skin='#f7e2cf', skin2='#d6b89e', skin3='#ac9179', blush='#e0a0b0',
    eye='#7fd8c0', eye2='#1e6f5c', brow='#cfe8d8',
    cloth='#2a3d4a', cloth2='#3f5a6b', cloth3='#182630', trim='#c9a54a', accent='#7fd8c0',
    leg='#4a6b7a', leg2='#73949c', boot='#22333d',
    metal='#e0ecf0', grip='#2a3d4a',
    face=dict(ew=6, eh=4, lash=1, droop=1, brow='soft', brow_h=3, nose=2, mouth=1, elf=1),
    sleeve=1.00, style='elfbraid', outfit='mantle', wep='wand'),

 'jun': dict(ink='#1a1410', hair='#3a2e24', hair2='#5e4c3a', hair3='#8a7256', shine='#c0a882',
    skin='#e8bd94', skin2='#c49670', skin3='#9b7454', blush='#e08b7a',
    eye='#ff8b4d', eye2='#8f3a10', brow='#3a2e24',
    cloth='#3d3b33', cloth2='#5c594c', cloth3='#22211c', trim='#e0a52c', accent='#c9302f',
    leg='#5c5040', leg2='#8a7a62', boot='#2e2820',
    metal='#c8ccd4', grip='#22211c',
    face=dict(ew=6, eh=3, lash=0, droop=0, brow='thick', brow_h=1, nose=3, mouth=2),
    sleeve=0.46, style='shag', outfit='workshirt', wep='chainsaw'),

 'rai': dict(ink='#2b2408', hair='#ffe14d', hair2='#fff29a', hair3='#ffffff', shine='#ffffff',
    skin='#f7d5ab', skin2='#d2ab83', skin3='#a88666', blush='#ff9d9d',
    eye='#ffb347', eye2='#8f5a10', brow='#ffe14d',
    cloth='#e8e0c8', cloth2='#ffffff', cloth3='#b0a688', trim='#ff8b4d', accent='#ffe14d',
    leg='#8a7f5a', leg2='#b8ad82', boot='#4e4630',
    metal='#eef2fa', grip='#8a5a2a',
    face=dict(ew=6, eh=4, lash=1, droop=1, brow='soft', brow_h=2, nose=2, mouth=2),
    sleeve=0.88, style='bolt', outfit='kimono', wep='tachi'),

 'kotone': dict(ink='#1a1a26', hair='#4a3a2e', hair2='#6e5844', hair3='#9c8266', shine='#cbb595',
    skin='#f2cfa8', skin2='#cfa982', skin3='#a68465', blush='#e09090',
    eye='#8fb6ff', eye2='#2a4a8f', brow='#4a3a2e',
    cloth='#5a6270', cloth2='#7d8698', cloth3='#343a45', trim='#c9a54a', accent='#8fb6ff',
    leg='#5a6270', leg2='#848da0', boot='#2e343e',
    metal='#d8dce8', grip='#343a45',
    face=dict(ew=5, eh=4, lash=1, droop=0, brow='flat', brow_h=2, nose=2, mouth=1),
    sleeve=0.86, style='lowbun', outfit='platedress', wep='towershield'),

 'vex': dict(ink='#20180f', hair='#8a6a3a', hair2='#b8925c', hair3='#dcc08a', shine='#f4e2b8',
    skin='#dfae82', skin2='#bb8a62', skin3='#946b48', blush='#d98b6a',
    eye='#7cffa8', eye2='#1a7d50', brow='#8a6a3a',
    cloth='#4a3a2a', cloth2='#6b573e', cloth3='#2b2118', trim='#7cffa8', accent='#e0a52c',
    leg='#6b5a42', leg2='#96826a', boot='#3a2e22',
    metal='#c8ccd4', grip='#2b2118',
    face=dict(ew=5, eh=3, lash=0, droop=1, brow='angled', brow_h=1, nose=3, mouth=2),
    sleeve=0.72, style='rattail', outfit='duster', wep='revolver'),

 'jinya': dict(ink='#07060e', hair='#15141f', hair2='#2e2c40', hair3='#5a5878', shine='#9a97c4',
    skin='#f0d2b6', skin2='#ccab8d', skin3='#a28670', blush='#d98b8b',
    eye='#8f5fff', eye2='#3a1a7d', brow='#15141f',
    cloth='#0e0d16', cloth2='#1e1c2e', cloth3='#060509', trim='#8f5fff', accent='#b07cff',
    leg='#2b2940', leg2='#4e4b6e', boot='#121120',
    metal='#c9b8ff', grip='#0e0d16',
    face=dict(ew=5, eh=4, lash=1, droop=0, brow='angled', brow_h=2, nose=2, mouth=1),
    sleeve=1.00, style='monarch', outfit='blackcoat', wep='shadowblade'),

 'sora': dict(ink='#0e1626', hair='#c6d4e8', hair2='#eef4ff', hair3='#8fa2c0', shine='#ffffff',
    skin='#f2d8bf', skin2='#cfb094', skin3='#a68b73', blush='#e09aa8',
    eye='#5fe6ff', eye2='#1a5a8f', brow='#c6d4e8',
    cloth='#101a2b', cloth2='#1e2d47', cloth3='#080d17', trim='#5fe6ff', accent='#8fa2c0',
    leg='#38455e', leg2='#616f8c', boot='#161e2e',
    metal='#eaf2ff', grip='#101a2b',
    face=dict(ew=5, eh=4, lash=1, droop=0, brow='angled', brow_h=2, nose=2, mouth=1),
    sleeve=0.94, style='moonfall', outfit='shihakusho', wep='nodachi'),

 'astra': dict(ink='#2a1010', hair='#b4232b', hair2='#e85a4a', hair3='#ffa48c', shine='#ffe0d4',
    skin='#f5d4b0', skin2='#d0ab88', skin3='#a6866a', blush='#ff9090',
    eye='#7cd0ff', eye2='#1c5a8f', brow='#b4232b',
    cloth='#3f4654', cloth2='#5f6a7c', cloth3='#242932', trim='#e0a52c', accent='#b4232b',
    leg='#4a5266', leg2='#737c94', boot='#262b36',
    metal='#e6ecf5', grip='#242932',
    face=dict(ew=5, eh=4, lash=2, droop=0, brow='angled', brow_h=1, nose=2, mouth=1),
    sleeve=0.90, style='straightlong', outfit='fullplate', wep='sabres'),

 'kael': dict(ink='#131313', hair='#1d1d22', hair2='#3c3c46', hair3='#6a6a78', shine='#a0a0b0',
    skin='#ecc9a4', skin2='#c7a382', skin3='#9e8066', blush='#d18b8b',
    eye='#8a939e', eye2='#3a414a', brow='#1d1d22',
    cloth='#2d2f34', cloth2='#454851', cloth3='#191a1e', trim='#a8b0bc', accent='#5c9e5c',
    leg='#4a4d56', leg2='#71757f', boot='#232529',
    metal='#dfe4ec', grip='#191a1e',
    face=dict(ew=5, eh=3, lash=0, droop=1, brow='flat', brow_h=1, nose=2, mouth=1),
    sleeve=0.88, style='undershave', outfit='harnessgear', wep='twinsabre'),

 'solen': dict(ink='#3a2405', hair='#f5b93a', hair2='#ffe08a', hair3='#fff6cf', shine='#ffffff',
    skin='#f7d3a6', skin2='#d3a97e', skin3='#a98761', blush='#ff9d7a',
    eye='#ff8b2d', eye2='#8f3f05', brow='#f5b93a',
    cloth='#f0ead8', cloth2='#ffffff', cloth3='#c2b894', trim='#e0a52c', accent='#ff8b2d',
    leg='#a08f5e', leg2='#c9b98a', boot='#5c5130',
    metal='#fff0c0', grip='#8a6a20',
    face=dict(ew=5, eh=3, lash=0, droop=0, brow='thick', brow_h=1, nose=3, mouth=1),
    sleeve=0.40, style='mane', outfit='surcoat', wep='sunaxe'),

 'dain': dict(ink='#1a1a12', hair='#e8d878', hair2='#fff0b0', hair3='#ffffff', shine='#ffffff',
    skin='#f7d9ba', skin2='#d3b294', skin3='#a98e74', blush='#ff9d9d',
    eye='#4ad66a', eye2='#12652c', brow='#e8d878',
    cloth='#141822', cloth2='#242c3c', cloth3='#0b0e14', trim='#e8d878', accent='#4ad66a',
    leg='#39405c', leg2='#636a8a', boot='#191d2b',
    metal='#e0e6f0', grip='#141822',
    face=dict(ew=6, eh=4, lash=0, droop=0, brow='soft', brow_h=2, nose=2, mouth=2),
    sleeve=0.66, style='tousle', outfit='opencoat', wep='brokenblade'),

 'kuro': dict(ink='#0f2018', hair='#2e7a4a', hair2='#4fb06e', hair3='#8fe0a8', shine='#d0ffe0',
    skin='#e8bd92', skin2='#c4966f', skin3='#9b7455', blush='#e08b7a',
    eye='#2e2e2e', eye2='#101010', brow='#2e7a4a',
    cloth='#1c2620', cloth2='#2e3d33', cloth3='#0f1613', trim='#e0a52c', accent='#2e7a4a',
    leg='#39504a', leg2='#63817a', boot='#1b2622',
    metal='#e2e8ea', grip='#0f1613',
    face=dict(ew=5, eh=3, lash=0, droop=0, brow='thick', brow_h=1, nose=3, mouth=1),
    sleeve=0.24, style='bandana', outfit='haramaki', wep='threeswords'),

 'nix': dict(ink='#141a2a', hair='#dfe6f2', hair2='#ffffff', hair3='#b0bccd', shine='#ffffff',
    skin='#f7ddc8', skin2='#d5b79f', skin3='#ab9280', blush='#ff9db0',
    eye='#a8d8ff', eye2='#2a5c8f', brow='#dfe6f2',
    cloth='#2b3346', cloth2='#414c66', cloth3='#171c27', trim='#7fd8ff', accent='#ffe14d',
    leg='#414a63', leg2='#6d778f', boot='#20252f',
    metal='#e8f0ff', grip='#171c27',
    face=dict(ew=6, eh=4, lash=1, droop=0, brow='soft', brow_h=2, nose=2, mouth=1),
    sleeve=0.34, style='fluff', outfit='tee', wep='claws'),

 'pim': dict(ink='#2b1c10', hair='#c96a2f', hair2='#f0955a', hair3='#ffc79a', shine='#ffe8d0',
    skin='#f7d9b8', skin2='#d3b090', skin3='#a98b70', blush='#ff9d9d',
    eye='#4ad6c0', eye2='#12655c', brow='#c96a2f',
    cloth='#e8dcc0', cloth2='#ffffff', cloth3='#b8a880', trim='#c96a2f', accent='#4ad6c0',
    leg='#8a7a58', leg2='#b5a583', boot='#4e452c',
    metal='#e0e4ea', grip='#8a6a3a',
    face=dict(ew=7, eh=4, lash=1, droop=1, brow='soft', brow_h=3, nose=1, mouth=1),
    sleeve=0.92, style='earcap', outfit='smock', wep='satchel'),

 'corbin': dict(ink='#20180f', hair='#4a3018', hair2='#6e4a28', hair3='#9c7448', shine='#c9a878',
    skin='#dfae82', skin2='#bb8a62', skin3='#946b48', blush='#d98b6a',
    eye='#e0a52c', eye2='#8a5a10', brow='#4a3018',
    cloth='#3d4a2e', cloth2='#5a6b45', cloth3='#232b1a', trim='#e0a52c', accent='#8fbf4a',
    leg='#5a6b45', leg2='#849468', boot='#2e3722',
    metal='#c8ccd4', grip='#232b1a',
    face=dict(ew=5, eh=3, lash=0, droop=1, brow='angled', brow_h=1, nose=4, mouth=2),
    sleeve=0.58, style='knotted', outfit='vest', wep='slingshot'),

 'aurel': dict(ink='#221408', hair='#e8c04a', hair2='#ffe08a', hair3='#fff3c0', shine='#ffffff',
    skin='#f5d0a8', skin2='#d0a982', skin3='#a68664', blush='#ff9d9d',
    eye='#5fc8ff', eye2='#1a5a8f', brow='#e8c04a',
    cloth='#16161e', cloth2='#26262f', cloth3='#0c0c11', trim='#e8c04a', accent='#ff8b4d',
    leg='#26262f', leg2='#4a4a58', boot='#141419',
    metal='#e8ecf5', grip='#16161e',
    face=dict(ew=5, eh=4, lash=1, droop=1, brow='soft', brow_h=2, nose=2, mouth=1),
    sleeve=1.00, style='sidesweep', outfit='dresssuit', wep='emberboot'),

 'vira': dict(ink='#1c1220', hair='#2b1f30', hair2='#4a3a52', hair3='#7a6a86', shine='#b0a0bc',
    skin='#e0b48f', skin2='#bc9270', skin3='#957256', blush='#d98b8b',
    eye='#8fd8ff', eye2='#2a5c8f', brow='#2b1f30',
    cloth='#2e2038', cloth2='#453252', cloth3='#1a1220', trim='#c9a0ff', accent='#8fd8ff',
    leg='#4a3a5c', leg2='#736388', boot='#241a2e',
    metal='#e0d8ea', grip='#1a1220',
    face=dict(ew=5, eh=4, lash=2, droop=1, brow='soft', brow_h=2, nose=2, mouth=1),
    sleeve=0.96, style='wavelong', outfit='longdress', wep='handsigil'),

 'boro': dict(ink='#101c26', hair='#2f6f9c', hair2='#55a3d0', hair3='#8fd0ee', shine='#d0f0ff',
    skin='#e8b892', skin2='#c4906a', skin3='#9c6c4c', blush='#e08b8b',
    eye='#ffe14d', eye2='#8a6410', brow='#2f6f9c',
    cloth='#1e2a36', cloth2='#33475a', cloth3='#111a22', trim='#ffe14d', accent='#55a3d0',
    leg='#33475a', leg2='#5d7891', boot='#1a242e',
    metal='#c8d4dc', grip='#111a22',
    face=dict(ew=5, eh=3, lash=0, droop=0, brow='thick', brow_h=1, nose=3, mouth=2),
    sleeve=0.18, style='pompadour', outfit='rig', wep='armcannon'),
}

ORDER = ['aoi', 'kagura', 'ren', 'hinata', 'suzume', 'gorou',
         'yura', 'kaito', 'momo', 'chiyo', 'nari', 'toma',
         'seryn', 'aldric', 'kassandra', 'nyx',
         'atom', 'rei', 'odette', 'bao', 'iris', 'shion', 'toya', 'yuuma',
         'suimu', 'homura', 'mirika', 'noir', 'alwyn',
         'jun', 'rai', 'kotone', 'vex', 'jinya',
         'sora', 'astra', 'kael', 'solen', 'dain', 'kuro',
         'nix', 'pim', 'corbin', 'aurel', 'vira', 'boro']

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

 # 2-9 walk: the full eight-frame cycle, twice through
 # contact - down - passing - up, once for each leg. Feet carry a third
 # number, their PITCH: negative lifts the toe for the heel strike, positive
 # lifts the heel as the foot rolls off. And a fourth, the KNEE: how hard that
 # leg is folded this frame. The knee is the difference between a run and a
 # pair of legs sliding past each other.
 # The near leg leads first; on frame 6 the far leg takes over, which is why
 # the two halves are not mirrors of each other but the same poses swapped.
 # contact A - near heel lands out front, far toe still pushing off
 dict(bob=1,  lean=2,  feet=((-11,1,3,1), (11,0,-2,0)),  hb=(8,23),  hf=(-8,28), wep=0.10, eye='open', sway=4, hd=1, sh=2),
 # down A - the front knee folds hard to take the weight
 dict(bob=3,  lean=3,  feet=((-13,3,3,4), (6,0,0,5)),    hb=(5,24),  hf=(-5,27), wep=0.06, eye='open', sway=5, hd=2, hdy=1, sh=1),
 # passing A - the far knee comes up under the body, foot tucked behind it
 dict(bob=-2, lean=1,  feet=((-2,10,2,7), (0,0,0,1)),    hb=(0,26),  hf=(0,26),  wep=0.00, eye='open', sway=2, sh=0),
 # up A - that leg swings out in front, still bent, reaching for the ground
 dict(bob=-1, lean=0, feet=((8,5,-3,3), (-7,0,2,0)),    hb=(-4,27), hf=(4,24),  wep=0.04, eye='open', sway=1, hd=-1, hdy=-1, sh=-2),
 # contact B - the same four, with the legs swapped over
 dict(bob=1,  lean=2,  feet=((11,0,-2,0), (-11,1,3,1)),  hb=(-8,28), hf=(8,23),  wep=0.10, eye='open', sway=4, hd=1, sh=2),
 dict(bob=3,  lean=3,  feet=((6,0,0,5),   (-13,3,3,4)),  hb=(-5,27), hf=(5,24),  wep=0.06, eye='open', sway=5, hd=2, hdy=1, sh=1),
 dict(bob=-2, lean=1,  feet=((0,0,0,1),   (-2,10,2,7)),  hb=(0,26),  hf=(0,26),  wep=0.00, eye='open', sway=2, sh=0),
 dict(bob=-1, lean=0, feet=((-7,0,2,0),  (8,5,-3,3)),   hb=(4,24),  hf=(-4,27), wep=0.04, eye='open', sway=1, hd=-1, hdy=-1, sh=-2),

 # 10-14 the attack, in five: coil, wind, strike, follow-through, settle.
 # Three frames made a swing that arrived without ever being thrown; the coil
 # and the follow-through are what give it weight.
 # 10 coil: weight shifting back, blade starting to come up
 dict(bob=0,  lean=-2, feet=((-7,0), (5,0)),   hb=(-4,24), hf=(-5,16), wep=-0.5, eye='fierce', sway=-3, hd=-1),
 # 11 wind: fully loaded, blade behind the shoulder, front foot light
 dict(bob=1,  lean=-5, feet=((-9,0), (6,2,-2)), hb=(-6,22), hf=(-10,6), wep=-1.0, eye='fierce', sway=-7, hd=-3),
 # 12 strike: everything arrives at once, onto the front foot
 dict(bob=1,  lean=7,  feet=((-12,1,3), (12,0,-1)), hb=(-4,26), hf=(10,3), wep=1.00, eye='fierce', sway=9, hd=5),
 # 13 follow-through: the blade keeps going past the target, she is overextended
 dict(bob=2,  lean=5,  feet=((-11,0,2), (11,0,0)), hb=(-2,27), hf=(6,20), wep=0.80, eye='fierce', sway=6, hd=3),
 # 14 settle: back to guard
 dict(bob=2,  lean=2,  feet=((-8,0), (8,0)),   hb=(-3,26), hf=(7,28),  wep=0.45, eye='fierce', sway=3, hd=1),
 # 15 dash: airborne, back leg trailing, front knee tucked
 dict(bob=3,  lean=9,  feet=((-13,7,4), (6,11,-3)), hb=(-8,28), hf=(6,18), wep=0.25, eye='fierce', sway=11, hd=5, hdy=-1),
 # 16 cast: both hands raised
 dict(bob=-2, lean=0,  feet=((-5,0), (5,0)),   hb=(-3,6),  hf=(3,6),   wep=-0.6, eye='closed', sway=-3, hd=0, hdy=-1),
 # 17 hurt: knocked back onto the heels
 dict(bob=2,  lean=-6, feet=((-7,0,-2), (8,2,-2)), hb=(-7,16), hf=(7,16), wep=0.20, eye='hurt', sway=-9, hd=-3, hdy=1),
]

def head_pos(pose):
    """Where the head sits. It carries the same forward TURN the torso does -
    without it the skull sits back behind a turned body and the face reads as
    pointing somewhere the shoulders and legs are not. `hd` is the per-frame
    lead: the head leads into a step and settles as the weight lands."""
    return (CX + pose['lean']*0.55 + TURN*0.8 + pose.get('hd', 0),
            HEADY + pose['bob'] + pose.get('hdy', 0))

def shoulder(pose, back):
    # A turned body foreshortens: lead shoulder swings forward, trailing one
    # hides. `sh` counter-rotates the shoulders against the hips - the torso
    # twisting the other way from the legs is a large part of why a run reads
    # as a run and not as a pair of legs under a plank.
    sw = pose.get('sh', 0) * (-1 if back else 1)
    dx = (-3 + TURN*0.4) if back else (4 + TURN*1.0)
    return (CX + dx + pose['lean']*0.4 + sw,
            SHOULDER + 2 + pose['bob'] + (1 if back else 0) - abs(sw)*0.2)

def hand(pose, back):
    """absolute hand position, always measured from its own shoulder"""
    sx, sy = shoulder(pose, back)
    d = pose['hb'] if back else pose['hf']
    return (sx + d[0], sy + d[1])

def shade(col, f):
    """same colour, dimmed - used to push the far limb back in depth"""
    r, g, b = (int(col[i:i+2], 16) for i in (1, 3, 5))
    return '#%02x%02x%02x' % (int(r*f), int(g*f), int(b*f))

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
    hipy = HIP + pose['bob']
    hipdx = pose['lean']*0.25 + TURN*0.5
    for i, f in enumerate(pose['feet']):
        fx, lift = f[0], f[1]
        pitch = f[2] if len(f) > 2 else 0        # -toe up (heel strike), +heel up (roll off)
        knee  = f[3] if len(f) > 3 else 0        # how hard this leg is folded, this frame
        side = -1 if i == 0 else 1
        far = i == 0                                          # trailing leg, in shadow
        lg  = shade(p['leg'],  0.62) if far else p['leg']
        lg2 = shade(p['leg2'], 0.62) if far else p['leg2']
        bt  = shade(p['boot'], 0.66) if far else p['boot']
        hx = CX + hipdx + side*4
        # trailing foot sits further back than the lead one on a turned body
        fxx = CX + fx + TURN*0.35 + (-1.2 if side < 0 else 1.2)
        fyy = FEET - 4 - lift
        # A leg is a hinge and it only folds one way. bent() pushes the joint
        # along the normal of the hip-to-foot line, and with the hip above the
        # foot that normal points BACKWARDS - so the fold has to be negative or
        # the character runs on knees that bend the wrong way.
        fold = 1.8 + lift*0.75 + knee
        bend = -fold
        kx, ky = bent(c, hx, hipy, fxx, fyy, bend, lg, 10, 6)
        c.ellipse(hx, hipy+1, 4.6, 4.0, lg)                   # hip/thigh mass
        c.ellipse(kx, ky, 3.0, 2.8, lg)                       # knee
        c.taper(hx-1, hipy, kx-1, ky, lg2, 4, 2)              # lit edge down the thigh
        c.taper(kx-1, ky, fxx-1, fyy, lg2, 2, 1)              # shin highlight
        # the foot rolls: heel lands first, then the whole sole, then the heel
        # peels off the ground and the toe is last to leave
        hy = fyy + 4 - max(0, pitch)                           # heel height
        ty = fyy + 4 - max(0, -pitch)                          # toe height
        c.rect(fxx-4, fyy, 7, 5, bt)                           # boot shaft
        c.rect(fxx-4, fyy, 7, 2, lg2)                          # cuff catches the light
        c.taper(fxx-4, hy, fxx+6, ty, bt, 3, 3)                # sole, tilted by the roll
        c.rect(fxx-5, hy-2, 5, 3, bt)                          # heel
        c.rect(fxx+2, ty-2, 6, 3, bt)                          # toe box
        c.rect(fxx+5, ty-2, 3, 1, lg2)                         # toe cap highlight
        if lift > 2: c.rect(fxx-5, max(hy, ty)+1, 13, 1, p['ink'])   # airborne edge

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
    # What hangs off the hips is its own choice, nothing to do with the hair.
    # Keying the skirt off the hairstyle is how three characters ended up in the
    # same outfit for no reason at all.
    of = p.get('outfit', 'coat')
    if of == 'skirt':                                     # pleated, flared
        c.rect(xh-7, HIP+dy, 15, 6, cl)
        c.rect(xh-9, HIP+5+dy, 18, 4, cl2)
        c.rect(xh-9, HIP+8+dy, 18, 2, cl3)
        for i in range(0, 18, 5): c.rect(xh-9+i, HIP+8+dy, 2, 2, tr)
    elif of == 'robe':                                    # long, slit, panelled
        c.rect(xh-7, HIP+dy, 14, 11, cl)
        c.rect(xh-8, HIP+9+dy, 16, 4, cl3)
        for i in range(0, 16, 5): c.rect(xh-8+i, HIP+4+dy, 2, 8, tr)
        c.rect(xh+4, HIP+2+dy, 4, 14, cl2)
    elif of == 'dress':                                   # softer, a rounded hem
        for k in range(11):
            w = 7 + k*0.7
            c.rect(xh-w, HIP+dy+k, w*2, 1, cl if k < 8 else cl3)
        c.rect(xh-9, HIP+3+dy, 18, 1, tr)
    elif of == 'shorts':                                  # cut short, with a belt
        c.rect(xh-7, HIP+dy, 14, 6, cl)
        c.rect(xh-8, HIP-1+dy, 16, 3, cl3)
        c.rect(xh-8, HIP-1+dy, 16, 1, tr)
        c.rect(xh+2, HIP+1+dy, 4, 3, tr)                  # a pouch on the hip
    elif of == 'armor':                                   # plates over a short skirt
        c.rect(xh-8, HIP+dy, 16, 5, p['metal'])
        c.rect(xh-8, HIP+dy, 16, 2, cl2)
        for i in range(0, 16, 4): c.rect(xh-8+i, HIP+5+dy, 3, 7, p['metal'])
        for i in range(0, 16, 4): c.rect(xh-8+i, HIP+11+dy, 3, 1, tr)
    elif of == 'jacket':                                  # cropped, hem flicked up
        c.rect(xh-7, HIP+dy, 14, 3, cl)
        c.rect(xh-9, HIP+2+dy, 6, 5, cl2)
        c.rect(xh-10, HIP+5+dy, 5, 3, cl3)
        c.rect(xh+5, HIP+1+dy, 3, 4, cl2)
    elif of == 'apron':                                   # a working apron and straps
        c.rect(xh-6, HIP+dy, 12, 12, cl2)
        c.rect(xh-6, HIP+dy, 12, 2, tr)
        c.rect(xh-7, HIP+10+dy, 14, 2, cl3)
        c.rect(xh-8, HIP+1+dy, 2, 9, cl3)
    elif of == 'longcoat':                                # split, and it streams
        c.rect(xh-7, HIP+dy, 14, 4, cl)
        c.rect(xh-10, HIP+3+dy, 8, 20, cl3)
        c.rect(xh-10, HIP+3+dy, 8, 2, tr)
        c.rect(xh-12, HIP+15+dy, 5, 9, cl3)
        c.rect(xh+3, HIP+3+dy, 5, 11, cl2)
    elif of == 'wrap':                                    # cloth wound at the waist
        c.rect(xh-7, HIP+dy, 14, 5, cl)
        for k in range(4):
            c.rect(xh-8+k, HIP+3+dy+k*2, 16-k, 2, cl2 if k % 2 else cl3)
        c.taper(xh-6, HIP+9+dy, xh-12, HIP+20+dy, tr, 4, 2)
    elif of == 'shihakusho':                              # wide black robes over a hakama
        c.rect(xh-8, HIP+dy, 16, 18, cl)
        c.rect(xh-10, HIP+3+dy, 20, 4, cl2)
        c.rect(xh-9, HIP+16+dy, 18, 3, cl3)
        for i in range(0, 18, 4): c.rect(xh-9+i, HIP+7+dy, 2, 9, cl3)
        c.rect(xh-8, HIP-2+dy, 16, 3, tr)
    elif of == 'fullplate':                               # tassets over a mail skirt
        c.rect(xh-8, HIP+dy, 16, 5, p['metal'])
        c.rect(xh-8, HIP+dy, 16, 2, cl2)
        for i in range(0, 16, 4):
            c.rect(xh-8+i, HIP+5+dy, 3, 10, p['metal'])
            c.rect(xh-8+i, HIP+13+dy, 3, 2, tr)
        c.rect(xh-6, HIP+15+dy, 12, 4, cl3)
    elif of == 'harnessgear':                             # straps, buckles, a gas canister
        c.rect(xh-7, HIP+dy, 14, 5, cl)
        for i in range(0, 14, 4): c.rect(xh-7+i, HIP+dy, 2, 12, cl3)
        c.rect(xh-9, HIP+2+dy, 18, 2, cl2)
        c.rect(xh-11, HIP+4+dy, 4, 10, p['metal'])        # the canister on the hip
        c.rect(xh-11, HIP+4+dy, 4, 2, tr)
    elif of == 'surcoat':                                 # a long tabard, split at the front
        c.rect(xh-7, HIP+dy, 14, 4, cl)
        c.rect(xh-6, HIP+3+dy, 5, 20, cl2)
        c.rect(xh+1, HIP+3+dy, 5, 20, cl2)
        c.rect(xh-6, HIP+3+dy, 12, 1, tr)
        c.rect(xh-6, HIP+21+dy, 12, 2, cl3)
    elif of == 'opencoat':                                # hanging open, shirt underneath
        c.rect(xh-7, HIP+dy, 14, 5, cl2)
        c.rect(xh-10, HIP+2+dy, 5, 16, cl)
        c.rect(xh+5, HIP+2+dy, 5, 14, cl)
        c.rect(xh-10, HIP+2+dy, 5, 2, tr)
        c.rect(xh-3, HIP+dy, 6, 8, cl3)
    elif of == 'haramaki':                                # a wide sash and loose trousers
        c.rect(xh-8, HIP-3+dy, 16, 7, tr)
        c.rect(xh-8, HIP-3+dy, 16, 1, cl2)
        c.rect(xh-7, HIP+4+dy, 14, 12, cl)
        c.rect(xh-8, HIP+14+dy, 16, 3, cl3)
    elif of == 'tee':                                     # a plain shirt and shorts
        c.rect(xh-7, HIP+dy, 14, 4, cl2)
        c.rect(xh-7, HIP+3+dy, 14, 6, cl)
        c.rect(xh-8, HIP+2+dy, 16, 2, tr)
    elif of == 'smock':                                   # a working coat, pockets on it
        c.rect(xh-7, HIP+dy, 14, 13, cl2)
        c.rect(xh-8, HIP+11+dy, 16, 3, cl3)
        c.rect(xh-6, HIP+3+dy, 5, 4, cl3)
        c.rect(xh+2, HIP+3+dy, 5, 4, cl3)
        c.rect(xh-8, HIP-1+dy, 16, 1, tr)
    elif of == 'vest':                                    # short, over a belt of pouches
        c.rect(xh-7, HIP+dy, 14, 5, cl)
        c.rect(xh-9, HIP+3+dy, 18, 3, cl3)
        for i in range(0, 18, 5): c.rect(xh-9+i, HIP+3+dy, 3, 4, tr)
        c.rect(xh+4, HIP+dy, 4, 4, cl2)
    elif of == 'dresssuit':                               # a suit, sharp, with a long tail
        c.rect(xh-7, HIP+dy, 14, 4, cl)
        c.rect(xh-9, HIP+3+dy, 6, 22, cl3)
        c.rect(xh+4, HIP+3+dy, 5, 12, cl2)
        c.rect(xh-2, HIP+dy, 3, 6, tr)
        c.rect(xh-9, HIP+3+dy, 6, 1, tr)
    elif of == 'longdress':                               # to the floor, slit up one side
        for k in range(20):
            w = 7 + k*0.28
            c.rect(xh-w, HIP+dy+k, w*2, 1, cl if k < 16 else cl3)
        c.rect(xh+2, HIP+4+dy, 3, 15, cl2)
        c.rect(xh-8, HIP+2+dy, 16, 1, tr)
    elif of == 'rig':                                     # bare plating bolted on
        c.rect(xh-8, HIP+dy, 16, 6, p['metal'])
        c.rect(xh-8, HIP+dy, 16, 2, cl2)
        for i in range(0, 16, 5): c.set(xh-7+i, HIP+4+dy, tr)
        c.rect(xh-7, HIP+6+dy, 14, 7, cl)
        c.rect(xh-8, HIP+12+dy, 16, 2, cl3)
    elif of == 'blackcoat':                               # long, closed, and it drinks light
        c.rect(xh-7, HIP+dy, 14, 6, cl)
        c.rect(xh-9, HIP+4+dy, 18, 18, cl3)
        c.rect(xh-9, HIP+4+dy, 18, 1, tr)
        c.rect(xh-10, HIP+16+dy, 6, 10, cl3)
        c.rect(xh+5, HIP+16+dy, 5, 8, cl2)
        for k in range(3): c.rect(xh-8+k*7, HIP+8+dy, 1, 12, cl2)
    elif of == 'cloak':                                   # short and soft, rounded hem
        for k in range(9):
            w = 8 + k*0.5
            c.rect(xh-w, HIP+dy+k, w*2, 1, cl if k < 7 else cl3)
        c.rect(xh-9, HIP+1+dy, 18, 1, tr)
        c.rect(xh-11, HIP+2+dy, 5, 9, cl2)                # it lifts behind
    elif of == 'haori':                                   # split panels over a hakama
        c.rect(xh-7, HIP+dy, 14, 16, cl)
        c.rect(xh-8, HIP+14+dy, 16, 3, cl3)
        c.rect(xh-1, HIP+dy, 2, 16, cl3)                  # the split down the middle
        c.rect(xh-9, HIP+2+dy, 3, 13, cl2)
        c.rect(xh+6, HIP+2+dy, 3, 13, cl2)
        c.rect(xh-8, HIP-1+dy, 16, 2, tr)
    elif of == 'harness':                                 # straps and a short skirt
        c.rect(xh-7, HIP+dy, 14, 5, cl)
        c.rect(xh-9, HIP+4+dy, 18, 4, cl2)
        for i in range(0, 18, 6): c.rect(xh-9+i, HIP+4+dy, 2, 4, cl3)
        c.rect(xh-8, HIP-2+dy, 16, 2, tr)
        c.rect(xh+2, HIP-1+dy, 3, 4, p['metal'])
    elif of == 'tailcoat':                                # short in front, long behind
        c.rect(xh-7, HIP+dy, 14, 3, cl)
        c.rect(xh-10, HIP+2+dy, 7, 26, cl3)
        c.rect(xh-10, HIP+2+dy, 7, 2, tr)
        c.rect(xh-12, HIP+20+dy, 5, 10, cl3)
        c.rect(xh+4, HIP+1+dy, 4, 5, cl2)
        c.rect(xh-8, HIP-1+dy, 16, 1, tr)
    elif of == 'mantle':                                  # long, layered, trimmed
        c.rect(xh-7, HIP+dy, 14, 13, cl)
        c.rect(xh-9, HIP+6+dy, 18, 5, cl2)
        c.rect(xh-9, HIP+11+dy, 18, 2, tr)
        c.rect(xh-8, HIP+13+dy, 16, 5, cl3)
        c.rect(xh+3, HIP+1+dy, 4, 10, cl2)
    elif of == 'workshirt':                               # tucked in, with a tool belt
        c.rect(xh-7, HIP+dy, 14, 5, cl)
        c.rect(xh-8, HIP-1+dy, 16, 3, cl3)
        c.rect(xh-8, HIP-1+dy, 16, 1, tr)
        c.rect(xh-6, HIP+2+dy, 3, 4, tr)
        c.rect(xh+2, HIP+2+dy, 4, 5, cl2)
    elif of == 'kimono':                                  # wrapped, with a wide obi
        c.rect(xh-7, HIP+dy, 14, 14, cl)
        c.rect(xh-8, HIP-3+dy, 16, 5, tr)                 # the obi rides high
        c.rect(xh-8, HIP-3+dy, 16, 1, cl2)
        c.rect(xh-2, HIP+dy, 3, 14, cl3)
        c.rect(xh-8, HIP+12+dy, 16, 3, cl3)
    elif of == 'platedress':                              # plates over a long skirt
        c.rect(xh-7, HIP+dy, 14, 4, p['metal'])
        for k in range(11):
            w = 7 + k*0.6
            c.rect(xh-w, HIP+4+dy+k, w*2, 1, cl if k < 8 else cl3)
        for i in range(0, 14, 5): c.rect(xh-7+i, HIP+dy, 3, 4, cl2)
        c.rect(xh-8, HIP+3+dy, 16, 1, tr)
    elif of == 'duster':                                  # a long open coat, both sides
        c.rect(xh-7, HIP+dy, 14, 4, cl)
        c.rect(xh-10, HIP+3+dy, 6, 22, cl3)
        c.rect(xh+4, HIP+3+dy, 6, 18, cl2)
        c.rect(xh-10, HIP+3+dy, 6, 2, tr)
        c.rect(xh-11, HIP+18+dy, 4, 8, cl3)
    elif of == 'highcollar':                              # long, severe, split at the hip
        c.rect(xh-7, HIP+dy, 14, 5, cl)
        c.rect(xh-9, HIP+4+dy, 7, 17, cl3)
        c.rect(xh+2, HIP+4+dy, 6, 13, cl2)
        c.rect(xh-9, HIP+4+dy, 17, 1, tr)
        c.rect(xh-10, HIP+18+dy, 5, 6, cl3)
    elif of == 'hoodie':                                  # loose, with a drawstring hem
        c.rect(xh-8, HIP+dy, 16, 8, cl2)
        c.rect(xh-8, HIP+6+dy, 16, 3, cl3)
        c.rect(xh-8, HIP+dy, 16, 1, cl)
        c.rect(xh-4, HIP+2+dy, 8, 3, cl3)                 # the pocket
        c.rect(xh+3, HIP+1+dy, 2, 4, tr)
    elif of == 'uniform':                                 # a school jacket, hem square
        c.rect(xh-7, HIP+dy, 14, 7, cl)
        c.rect(xh-8, HIP+5+dy, 16, 3, cl2)
        c.rect(xh-8, HIP+7+dy, 16, 1, cl3)
        c.rect(xh+4, HIP+1+dy, 3, 6, tr)                  # a stripe down the front panel
    else:                                                 # a coat with a tail behind
        c.rect(xh-7, HIP+dy, 14, 4, cl)
        c.rect(xh-9, HIP+3+dy, 7, 12, cl3)
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

# The front edge of the face, row by row from the crown down to the jaw, as
# offsets from the head centre. A circle with a bump on it reads as a ball with
# a nose; a real profile needs a forehead that rolls back, a brow ridge, the dip
# under it, a nose, the philtrum, two lips and a chin that pulls back to the jaw.
HEAD_PROFILE = [
    (-4.0,  2.0),   # 0  crown
    (-6.2,  4.4),   # 1
    (-7.4,  5.6),   # 2
    (-8.1,  6.2),   # 3  forehead
    (-8.5,  6.6),   # 4
    (-8.7,  6.9),   # 5  brow ridge
    (-8.8,  6.5),   # 6  the dip under it - brow line
    (-8.7,  6.6),   # 7  lash line
    (-8.5,  6.8),   # 8  eye
    (-8.3,  7.1),   # 9  eye
    (-8.0,  7.2),   # 10 bridge of the nose
    (-7.6,  7.6),   # 11 nose
    (-7.2,  7.7),   # 12 tip - small. The cut back below it does the work.
    (-6.7,  6.4),   # 13 philtrum - the cut back here is what makes it a nose
    (-6.2,  6.9),   # 14 mouth
    (-5.6,  6.8),   # 15 lower lip
    (-5.0,  6.1),   # 16 chin
    (-4.3,  4.9),   # 17
    (-3.5,  3.2),   # 18 jaw
    (-2.8,  1.8),   # 19 into the neck
]
HEAD_TOP = -10          # row 0 sits here, relative to the head centre
EYE_ROW  = 7            # the lash line. The eye sits mid-face, not up on the brow.

def eye_profile(c, p, x, y, mode):
    """One eye, seen from the side, facing +x.

    In profile an anime eye is a wedge, not a box: tall at the outer corner,
    closing to a point at the inner one. The iris fills nearly the whole
    opening - the sclera is a sliver at the front - and it is shaded top to
    bottom with a catchlight punched through it, which is what makes an eye
    read as a wet sphere instead of a sticker. `y` is the lash line; the
    opening is the three rows under it."""
    ink, ec, ed = p['ink'], p['eye'], p['eye2']
    F = p['face']
    w = F['ew'] + 1

    if mode == 'closed':                          # a closed lid still curves
        for i in range(w):
            c.set(x+i, y + 2 + (0 if i < w-2 else -1), ink)
        c.set(x-1, y+1, ink)
        return
    if mode == 'hurt':                            # the squeezed-shut cross
        for i in range(w-1):
            c.set(x+i, y+1, ink); c.set(x+i, y+3, ink)
        c.set(x+w//2, y, ink); c.set(x+w//2, y+4, ink)
        return

    # How deep the opening is separates a wide round eye from a narrow hard one,
    # which is most of what tells two characters apart at this size.
    d = 3 if F['eh'] >= 4 else 2
    for i in range(w):
        t  = i/(w-1.0)
        hi = y+d if t <= 0.60 else (y+d-1 if t <= 0.84 else y+1)
        for yy in range(int(y+1), int(hi)+1):
            c.set(x+i, yy, ec)
        c.set(x+i, y+1, ed)                       # the lid's shadow across the top
    c.set(x+w-1, y+1, '#ffffff')                  # sclera, a sliver at the inner corner
    pu = max(1, round(w*0.46))                    # pupil, set back from that corner
    for yy in range(int(y+2), int(y+d+1)): c.set(x+pu, yy, ink)
    c.set(x+pu-1, y+2, ed)
    c.set(x+1, y+1, '#ffffff')                    # the big catchlight, high and back
    c.set(x+2, y+d, p['shine'])                   # a small one low and forward
    for i in range(w):                            # the lash, heaviest at the corner
        c.set(x+i, y, ink)
    c.set(x, y+1, ink); c.set(x+1, y, ink)
    for k in range(F['lash']):
        c.set(x-1-k, y-k, ink)                    # the flick off the outer corner
    if F['droop']: c.set(x-1, y+2, ink)
    for i in range(1, w-2):                       # lower lid
        c.set(x+i, y+d+1 + (-1 if i > w-4 else 0), p['skin2'])

def draw_head(c, p, pose):
    """Head in TRUE profile facing +x: one eye, a contoured front edge, the ear
       behind. A front-facing head mirrored left and right cannot read as facing
       anywhere, which is the whole reason this exists."""
    sk, sk2, sk3 = p['skin'], p['skin2'], p['skin3']
    F = p['face']
    cx, cy = head_pos(pose)
    nose = F['nose'] - 2                          # per character, off the default

    rows = []
    for i, (bk, fr) in enumerate(HEAD_PROFILE):
        y = cy + HEAD_TOP + i
        f = fr + (nose*0.45 if 11 <= i <= 12 else nose*0.18 if i in (10, 13) else 0)
        rows.append((y, cx + bk, cx + f))

    # --- the mass. Light comes from the front, so the back of the skull turns
    # into shadow and the forehead, bridge and cheekbone keep the light.
    for i, (y, bx, fx) in enumerate(rows):
        c.rect(bx, y, fx-bx, 1, sk)
        c.rect(bx, y, 2, 1, sk2)
        c.set(bx, y, sk3)
    for i, (y, bx, fx) in enumerate(rows):
        if 8 <= i <= 9:   c.rect(bx+1, y, 3, 1, sk2)   # temple, behind the eye
        if i == 13:       c.rect(fx-2, y, 2, 1, sk2)   # under the nose
        if i == 15:       c.set(fx-1, y, sk2)          # under the lower lip
        if i in (17, 18): c.rect(bx, y, 3, 1, sk2)     # the jaw turning under
        if i == 19:       c.rect(bx, y, fx-bx, 1, sk2)

    # --- ear, tucked behind the jaw
    ey0 = cy + HEAD_TOP + 8
    c.rect(cx-6.5, ey0, 3, 4, sk)
    c.set(cx-6.5, ey0+1, sk2); c.set(cx-5.5, ey0+2, sk3)
    c.set(cx-4.5, ey0+1, sk2)

    # --- neck, with the shadow the jaw throws across it
    ny = cy + HEAD_TOP + 19
    c.rect(cx-2.5, ny, 6, 4, sk)
    c.rect(cx-2.5, ny, 6, 1, sk3)
    c.rect(cx-2.5, ny+1, 5, 1, sk2)
    c.rect(cx+2.5, ny, 1, 4, sk2)

    m  = pose['eye']
    ex = cx + 0.5
    ey = cy + HEAD_TOP + EYE_ROW
    if F.get('blind'):
        # a band over the eyes. It reads harder than any eye could, and the
        # glow leaking out from under it is the whole character.
        c.rect(cx-8, ey-1, 16, 5, p['cloth3'])
        c.rect(cx-8, ey-1, 16, 1, p['cloth2'])
        c.rect(cx-8, ey+3, 16, 1, p['ink'])
        for k in range(3): c.set(cx+2+k*2, ey+1, p['eye'])       # light through the weave
        c.set(cx+6, ey+2, p['trim'])
        c.rect(cx-11, ey, 4, 3, p['cloth3'])                     # the strap round the back
        return cx, cy
    eye_profile(c, p, ex, ey, m)

    if m != 'closed':                             # brow, riding the ridge
        by = ey - 2 - F['brow_h']
        for i in range(F['ew']):
            t = i/max(1, F['ew']-1)
            lift = 0
            if F['brow'] == 'angled': lift = -1 if t > 0.55 else 0
            if F['brow'] == 'soft':   lift =  1 if t > 0.55 else 0
            c.set(ex+i, by+lift, p['brow'])
            if F['brow'] == 'thick': c.set(ex+i, by+1+lift, p['brow'])

    # --- mouth: small, on the lip contour, with a corner that reads
    mw = F['mouth']
    my = cy + HEAD_TOP + 14
    mx = rows[14][2] - 1 - mw
    if m == 'fierce':
        c.rect(mx, my, mw+1, 1, p['ink'])
        c.set(mx, my+1, p['ink'])
    else:
        c.set(mx+mw-1, my, p['ink'])              # a definite corner, then it softens
        c.rect(mx, my, mw-1, 1, sk3)
        c.set(mx-1, my, sk2)
    c.set(rows[15][2]-1, cy+HEAD_TOP+15, sk2)     # the shadow under the lower lip

    # --- blush, low on the cheekbone and small enough to be a flush
    byy = cy + HEAD_TOP + 12
    c.rect(cx+0.5, byy,   3, 1, p['blush'])
    c.rect(cx+1.0, byy+1, 2, 1, p['blush'])
    return cx, cy

# --------------------------------------------------------------------- hair --
def hair_back(c, p, pose, cx, cy):
    """Everything that falls BEHIND the head. Twenty-one characters, twenty-one
    silhouettes - a roster where three people share a haircut in different
    colours reads as one character with palette swaps, which is the opposite of
    what a gacha needs."""
    st, h1, h2 = p['style'], p['hair'], p['hair2']
    sway = -pose['sway']          # trail: opposite the direction of travel

    if st == 'ponytail':                                   # high, single, whipping
        c.taper(cx-6, cy-8, cx-13+sway*0.5, cy+2, h1, 9, 8)
        c.taper(cx-12+sway*0.4, cy, cx-16+sway, cy+30, h1, 8, 5)
        c.taper(cx-13+sway*0.4, cy+2, cx-16+sway, cy+24, h2, 4, 3)
        c.taper(cx-15+sway, cy+28, cx-18+sway*1.3, cy+38, h1, 4, 2)

    elif st == 'long':                                     # a curtain, straight down
        c.rect(cx-16, cy-8, 11, 46, h1)
        c.rect(cx-15, cy+8, 4, 26, h2)
        c.rect(cx-9, cy-8, 17, 20, h1)
        c.taper(cx-12, cy+34, cx-17+sway, cy+46, h1, 7, 3)
        c.rect(cx+5, cy-2, 3, 15, h1)

    elif st == 'spiky':                                    # Ren, and Ren alone
        for sx, sy in ((-15,-3), (-10,-11), (-3,-16), (5,-14), (11,-7)):
            c.taper(cx+sx*0.6, cy+sy*0.45, cx+sx+sway*0.35, cy+sy-2, h1, 7, 3)
        c.rect(cx-14, cy-5, 6, 19, h1)
        c.rect(cx+7, cy-4, 3, 11, h1)
        c.taper(cx-7, cy+16, cx-15+sway, cy+26, p['accent'], 6, 4)   # scarf
        c.taper(cx-13+sway*0.5, cy+24, cx-19+sway, cy+36, p['accent'], 4, 3)

    elif st == 'bob':                                      # a heavy blunt bob
        c.ellipse(cx-1, cy+1, 12.0, 12.6, h1)
        c.rect(cx-15, cy-2, 7, 20, h1)
        c.rect(cx+7, cy-2, 3, 13, h1)

    elif st == 'braid':                                    # one plaited rope
        c.ellipse(cx, cy-1, 11.0, 11.0, h1)
        bx = cx - 14 + sway*0.35
        for k in range(7):
            yy = cy + 4 + k*6
            xx = bx + math.sin(k*1.0 + sway*0.15)*2.2
            c.ellipse(xx, yy, 4.6 - k*0.3, 3.6, h1)
            c.ellipse(xx-1, yy-1, 2.8 - k*0.22, 2.0, h2)
            c.set(xx+2, yy+2, p['ink'])
        c.rect(bx-3, cy+45, 6, 4, p['accent'])
        c.rect(cx+7, cy-2, 3, 11, h1)

    elif st == 'crop':                                     # short, close to the skull
        c.ellipse(cx-1, cy-2, 10.4, 9.6, h1)
        c.rect(cx-12, cy-4, 6, 11, h1)
        c.rect(cx+7, cy-4, 2, 8, h1)

    elif st == 'twin':                                     # two tails, one edge-on
        c.ellipse(cx, cy-1, 10.6, 10.4, h1)
        for sgn, sc, off in ((-1, 1.4, 0), (1, 0.42, -4)):
            bx = cx + sgn*12 + off
            c.taper(cx+sgn*7, cy-6, bx, cy+2, h1, 9, 8)
            c.taper(bx, cy+2, bx+sgn*3 + sway*0.35, cy+2+26*sc, h1, 8, 4)
            c.taper(bx, cy+4, bx+sgn*2 + sway*0.3, cy+4+18*sc, h2, 4, 2)

    elif st == 'hime':                                     # blunt, with sharp sidelocks
        c.rect(cx-14, cy-8, 10, 40, h1)
        c.rect(cx-9, cy-9, 17, 19, h1)
        c.rect(cx-13, cy+6, 3, 22, h2)
        for k in range(10): c.set(cx-15+k, cy+32+abs(k-5)//2, h1)   # a blunt cut hem
        c.taper(cx+6, cy-1, cx+7, cy+16, h1, 4, 5)                  # the sidelock
        c.taper(cx+6, cy+14, cx+7, cy+19, h2, 4, 2)

    elif st == 'wolfcut':                                  # shaggy, layered, spiky hem
        c.ellipse(cx-1, cy-1, 11.4, 10.6, h1)
        for k, (sx, ln) in enumerate(((-13, 16), (-11, 22), (-8, 26), (-4, 20))):
            c.taper(cx+sx, cy+2, cx+sx-3+sway*0.4, cy+2+ln, h1, 6, 3)
            if k % 2: c.taper(cx+sx-1, cy+4, cx+sx-3+sway*0.3, cy+ln, h2, 3, 2)
        c.rect(cx+6, cy-3, 3, 9, h1)

    elif st == 'slick':                                    # swept flat back, undercut
        c.ellipse(cx-2, cy-3, 10.8, 8.6, h1)
        for k in range(6):
            c.taper(cx-4+k, cy-10+k*0.6, cx-15+sway*0.5, cy-4+k*2.2, h1, 4, 2)
        c.rect(cx-13, cy-4, 5, 9, h2)
        c.rect(cx-11, cy+4, 4, 7, p['ink'])                # the shaved panel
        c.rect(cx+5, cy-4, 2, 6, h1)

    elif st == 'wave':                                     # long, and it moves
        for k in range(34):
            t = k/33.0
            xx = cx - 13 + math.sin(t*6.0 + sway*0.2)*3.4 - t*2
            c.rect(xx-4, cy-8+k, 9, 1, h1)
            c.rect(xx-4, cy-8+k, 2, 1, h2)
        c.rect(cx-9, cy-9, 17, 18, h1)
        c.taper(cx-14, cy+26, cx-19+sway, cy+38, h1, 6, 3)
        c.rect(cx+5, cy-2, 3, 12, h1)

    elif st == 'bun':                                      # up, wrapped, loose strands
        c.ellipse(cx, cy-1, 10.8, 10.2, h1)
        c.ellipse(cx-9, cy-11, 6.4, 5.6, h1)               # the bun itself
        c.ellipse(cx-10, cy-12, 4.0, 3.2, h2)
        c.rect(cx-12, cy-9, 7, 2, p['accent'])             # the wrap
        c.taper(cx-9, cy-2, cx-14+sway*0.5, cy+14, h1, 3, 2)   # loose strands
        c.taper(cx-7, cy+2, cx-11+sway*0.4, cy+18, h1, 2, 1)
        c.rect(cx+6, cy-2, 2, 8, h1)

    elif st == 'curls':                                    # ringlets, stacked
        c.ellipse(cx, cy-1, 10.8, 10.4, h1)
        for k in range(6):
            yy = cy + 2 + k*5
            xx = cx - 12 + math.sin(k*1.6)*2.6 + sway*0.2
            c.ellipse(xx, yy, 5.0, 4.2, h1)
            c.ellipse(xx-1.4, yy-1.4, 2.6, 2.2, h2)
        c.ellipse(cx+7, cy+3, 3.4, 3.0, h1)                # one ringlet at the cheek
        c.ellipse(cx+6.4, cy+2.4, 1.6, 1.4, h2)

    elif st == 'mohawk':                                   # shaved sides; the crest
        c.rect(cx-9, cy-5, 15, 9, p['ink'])                #  goes on top in hair_front
        c.taper(cx-8, cy-4, cx-14+sway*0.6, cy+8, h1, 5, 2)

    elif st == 'crown':                                    # half up, a circlet, long
        c.rect(cx-14, cy-6, 9, 38, h1)
        c.rect(cx-13, cy+8, 3, 20, h2)
        c.rect(cx-9, cy-9, 17, 19, h1)
        c.taper(cx-11, cy+30, cx-15+sway, cy+40, h1, 6, 3)
        c.taper(cx+6, cy-2, cx+8, cy+14, h1, 4, 3)         # a framing lock
        for k in range(5):                                 # the circlet, seen edge-on
            c.rect(cx-6+k*3, cy-11-((k%2)*2), 2, 3, p['accent'])
        c.rect(cx-7, cy-9, 15, 2, p['accent'])

    elif st == 'sidetail':                                 # low, off one side
        c.ellipse(cx-1, cy-1, 11.0, 10.6, h1)
        bx = cx - 12 + sway*0.4
        c.taper(cx-8, cy+4, bx, cy+10, h1, 8, 8)
        c.taper(bx, cy+10, bx-4+sway*0.8, cy+38, h1, 8, 4)
        c.taper(bx, cy+12, bx-3+sway*0.7, cy+30, h2, 4, 2)
        c.rect(bx-4, cy+8, 7, 3, p['accent'])              # the tie, low on the neck
        c.rect(cx+6, cy-2, 3, 10, h1)

    elif st == 'messy':                                    # long, unbrushed, a cowlick
        c.ellipse(cx-1, cy-2, 11.4, 10.8, h1)
        for k, (sx, sy, ln) in enumerate(((-13,-2,20), (-11,3,26), (-7,6,22), (-14,-8,12))):
            c.taper(cx+sx, cy+sy, cx+sx-4+sway*0.5, cy+sy+ln, h1, 6, 3)
            if k == 1: c.taper(cx+sx-1, cy+sy+2, cx+sx-4+sway*0.4, cy+sy+ln-4, h2, 3, 2)
        c.taper(cx-2, cy-11, cx+3, cy-18+sway*0.2, h1, 5, 2)   # the cowlick
        c.taper(cx-1, cy-12, cx+2, cy-16, h2, 3, 1)
        c.rect(cx+6, cy-3, 3, 10, h1)

    elif st == 'topknot':                                  # very high, wrapped tight
        c.ellipse(cx-1, cy, 10.6, 10.0, h1)
        c.taper(cx-3, cy-9, cx-6, cy-16, h1, 7, 6)
        c.rect(cx-9, cy-17, 7, 3, p['accent'])             # the wrap
        c.taper(cx-6, cy-16, cx-13+sway, cy+4, h1, 7, 4)
        c.taper(cx-7, cy-14, cx-12+sway*0.8, cy, h2, 3, 2)
        c.taper(cx-11+sway*0.6, cy+2, cx-15+sway*1.2, cy+16, h1, 4, 2)
        c.rect(cx+6, cy-2, 2, 7, h1)

    elif st == 'veil':                                     # floor length, ornamented
        c.rect(cx-15, cy-9, 10, 52, h1)
        c.rect(cx-14, cy+6, 4, 32, h2)
        c.rect(cx-9, cy-10, 17, 20, h1)
        c.rect(cx-16, cy+41, 12, 3, h1)                    # the hem, cut straight
        c.taper(cx+6, cy-2, cx+8, cy+20, h1, 4, 3)
        c.rect(cx-4, cy-13, 3, 4, p['accent'])             # a pin in the crown
        c.rect(cx-5, cy-15, 5, 2, p['accent'])

    elif st == 'undercut':                                 # short back, long fringe
        c.ellipse(cx-1, cy-3, 10.4, 8.8, h1)
        c.rect(cx-12, cy-2, 5, 6, h1)
        c.rect(cx-11, cy+3, 4, 6, p['ink'])                # shaved underneath
        c.taper(cx+3, cy-10, cx+8, cy+4, h1, 6, 4)         # the fringe sweeps forward
        c.taper(cx+4, cy-9, cx+8, cy+1, h2, 3, 2)

    elif st == 'moonfall':                                 # very long, high tail, pale
        c.ellipse(cx-1, cy-1, 10.8, 10.0, h1)
        bx = cx - 5
        c.taper(cx-4, cy-11, bx-3, cy-14, h1, 8, 8)
        c.taper(bx-3, cy-14, bx-10+sway*0.8, cy+16, h1, 8, 5)
        c.taper(bx-4, cy-12, bx-9+sway*0.7, cy+10, h2, 4, 3)
        c.taper(bx-9+sway*0.8, cy+14, bx-13+sway*1.3, cy+34, h1, 5, 2)
        c.rect(bx-6, cy-16, 6, 3, p['accent'])
        c.rect(cx+6, cy-3, 3, 11, h1)

    elif st == 'straightlong':                             # heavy, straight, to the waist
        c.rect(cx-15, cy-8, 10, 40, h1)
        c.rect(cx-14, cy+8, 3, 22, h2)
        c.rect(cx-9, cy-9, 17, 18, h1)
        for k in range(10): c.set(cx-16+k, cy+31+(k%3), h1)
        c.taper(cx+6, cy-2, cx+7, cy+12, h1, 4, 3)

    elif st == 'undershave':                               # short, flat, shaved beneath
        c.ellipse(cx-2, cy-4, 10.2, 8.0, h1)
        c.rect(cx-12, cy-4, 5, 6, h1)
        c.rect(cx-11, cy+2, 5, 8, p['ink'])
        for k in range(5): c.set(cx-11+k, cy+3+k%2, h2)
        c.rect(cx+6, cy-4, 2, 5, h1)

    elif st == 'mane':                                     # enormous, swept back
        c.ellipse(cx-2, cy-3, 13.0, 11.4, h1)
        for k, (sx, sy, ln) in enumerate(((-15,-6,14), (-13,0,20), (-9,5,17),
                                          (-16,-12,10), (-5,-15,12))):
            c.taper(cx+sx*0.7, cy+sy*0.6, cx+sx-3+sway*0.5, cy+sy+ln, h1, 8, 4)
            if k % 2: c.taper(cx+sx*0.7, cy+sy*0.6, cx+sx-2, cy+sy+ln-5, h2, 5, 3)
        c.rect(cx+7, cy-4, 3, 8, h1)

    elif st == 'tousle':                                   # short, unbothered, sticks up
        c.ellipse(cx-1, cy-2, 11.0, 9.8, h1)
        for sx, sy in ((-11,-9), (-5,-14), (2,-13), (8,-9), (-14,-3)):
            c.taper(cx+sx*0.7, cy+sy*0.5, cx+sx+sway*0.2, cy+sy-2, h1, 6, 3)
        c.rect(cx-12, cy-2, 4, 8, h1)
        c.rect(cx+7, cy-3, 3, 8, h1)

    elif st == 'bandana':                                  # cropped, with a tied cloth
        c.ellipse(cx-1, cy-2, 10.4, 9.4, h1)
        c.rect(cx-11, cy-9, 22, 5, p['accent'])
        c.rect(cx-11, cy-9, 22, 1, p['hair3'])
        c.taper(cx-10, cy-5, cx-16+sway, cy+6, p['accent'], 5, 3)
        c.taper(cx-13+sway*0.6, cy+4, cx-18+sway, cy+14, p['accent'], 4, 2)
        c.rect(cx-12, cy-3, 4, 8, h1); c.rect(cx+7, cy-3, 3, 7, h1)

    elif st == 'fluff':                                    # light, weightless, spiked out
        c.ellipse(cx-1, cy-2, 11.6, 10.2, h1)
        for k in range(9):
            a2 = -2.6 + k*0.42
            c.taper(cx+math.cos(a2)*7, cy+math.sin(a2)*6-2,
                    cx+math.cos(a2)*15+sway*0.3, cy+math.sin(a2)*13-3, h1, 6, 2)
            if k % 2: c.set(cx+math.cos(a2)*13, cy+math.sin(a2)*11-3, h2)
        c.rect(cx+7, cy-3, 3, 7, h1)

    elif st == 'earcap':                                   # a hood with two ears on it
        c.ellipse(cx-1, cy-1, 11.4, 10.4, h1)
        c.ellipse(cx-2, cy-3, 8.4, 6.6, h2)
        for sgn, sc in ((-1, 1.0), (1, 0.6)):
            c.ellipse(cx+sgn*9, cy-11, 4.0*sc, 5.4*sc, h1)
            c.ellipse(cx+sgn*9, cy-11, 2.2*sc, 3.2*sc, p['hair3'])
        c.rect(cx-13, cy+1, 5, 10, h1)
        c.rect(cx+7, cy-1, 3, 8, h1)

    elif st == 'knotted':                                  # tied back in a stub
        c.ellipse(cx-1, cy-2, 10.6, 9.4, h1)
        c.ellipse(cx-12, cy+1, 4.2, 4.0, h1)
        c.rect(cx-15, cy-1, 4, 3, p['accent'])
        for k in range(4):
            c.taper(cx-14, cy+2, cx-18+sway*0.5, cy+6+k*3, h1, 3, 1)
        c.rect(cx+6, cy-3, 3, 8, h1)

    elif st == 'sidesweep':                                # long over one eye, neat
        c.ellipse(cx-2, cy-3, 10.8, 9.6, h1)
        c.rect(cx-13, cy-3, 5, 12, h1)
        c.taper(cx-2, cy-11, cx+9, cy+6, h1, 7, 4)         # the sweep, right over the eye
        c.taper(cx-1, cy-11, cx+7, cy+2, h2, 4, 2)
        c.rect(cx-11, cy+7, 4, 7, h1)

    elif st == 'wavelong':                                 # dark, long, gently waved
        for k in range(38):
            t = k/37.0
            xx = cx - 12 + math.sin(t*5.4 + sway*0.2)*3.0 - t*2
            c.rect(xx-4, cy-8+k, 9, 1, h1)
            c.rect(xx-4, cy-8+k, 2, 1, h2)
        c.rect(cx-9, cy-9, 17, 17, h1)
        c.taper(cx+6, cy-2, cx+8, cy+16, h1, 4, 3)
        c.taper(cx-13, cy+28, cx-18+sway, cy+40, h1, 6, 3)

    elif st == 'pompadour':                                # swept up and forward, tall
        c.ellipse(cx-2, cy-1, 10.4, 9.0, h1)
        for k in range(8):
            sx = -7 + k*2.2
            hgt = 15 - abs(k-4)*1.1
            c.taper(cx+sx, cy-6, cx+sx+4+sway*0.2, cy-6-hgt, h1, 6, 4)
            if k % 2: c.taper(cx+sx, cy-7, cx+sx+3, cy-6-hgt*0.7, h2, 4, 2)
        c.rect(cx-12, cy-2, 4, 7, h1)
        c.rect(cx+7, cy-2, 3, 6, h1)

    elif st == 'monarch':                                  # sleek, parted, faintly lit
        c.ellipse(cx-1, cy-2, 11.2, 10.0, h1)
        for k in range(7):
            c.taper(cx-8+k*2.2, cy-10, cx-14+sway*0.4, cy-4+k*1.4, h1, 5, 2)
        c.rect(cx-13, cy-4, 5, 13, h1)
        c.rect(cx-13, cy-4, 2, 9, h2)
        c.taper(cx+6, cy-4, cx+8, cy+7, h1, 4, 3)          # one lock past the eye
        for k in range(4):                                  # shadow bleeding off the crown
            c.set(cx-6+k*4, cy-15-((k%2)*2), p['accent'])

    elif st == 'dropcurl':                                 # soft and round, one floating curl
        c.ellipse(cx-1, cy-1, 11.4, 10.6, h1)
        c.ellipse(cx-4, cy-3, 7.0, 6.0, h2)
        for k in range(5):
            c.taper(cx-11+k*1.5, cy+3, cx-14+k*1.2+sway*0.3, cy+12+k, h1, 5, 3)
        for k in range(9):                                 # the curl, off the crown
            a2 = -1.5 + k*0.55
            c.ellipse(cx + 2 + math.cos(a2)*6, cy - 14 + math.sin(a2)*5 - k*0.4,
                      2.2 - k*0.1, 2.0 - k*0.1, h1 if k % 2 else h2)

    elif st == 'oni':                                      # long and wild, one horn
        c.ellipse(cx-1, cy-2, 11.4, 10.4, h1)
        for sx, sy, ln in ((-14,-4,26), (-11,2,32), (-7,6,24)):
            c.taper(cx+sx, cy+sy, cx+sx-4+sway*0.5, cy+sy+ln, h1, 7, 3)
            c.taper(cx+sx, cy+sy+2, cx+sx-3+sway*0.4, cy+sy+ln-6, h2, 4, 2)
        for k, (sx, sy) in enumerate(((-9,-12), (-2,-16), (6,-13))):
            c.taper(cx+sx*0.6, cy+sy*0.5, cx+sx+sway*0.2, cy+sy-3, h1, 6, 3)
        c.taper(cx+4, cy-13, cx+9, cy-24, p['skin2'], 5, 2)     # the horn
        c.taper(cx+5, cy-14, cx+8, cy-22, p['skin'], 3, 1)
        c.rect(cx+7, cy-2, 3, 12, h1)

    elif st == 'dragontail':                               # two very long tails
        c.ellipse(cx-1, cy-1, 10.8, 10.2, h1)
        for sgn, sc in ((-1, 1.0), (1, 0.5)):
            bx = cx + sgn*11
            c.taper(cx+sgn*7, cy-7, bx, cy-2, h1, 8, 7)
            for k in range(int(9*sc)):                     # the tail, beaded down
                yy = cy + 2 + k*5
                xx = bx + sgn*2 + math.sin(k*0.7)*2 + sway*0.25
                c.ellipse(xx, yy, 4.4 - k*0.25, 3.8 - k*0.2, h1)
                c.ellipse(xx-1, yy-1, 2.4 - k*0.14, 1.8, h2)
            c.rect(bx + sgn*1 - 3, cy - 4, 6, 3, p['accent'])
        for k in range(3): c.set(cx-3+k*3, cy-13, p['trim'])

    elif st == 'combed':                                   # neat, parted, two long strands
        c.ellipse(cx-2, cy-3, 10.6, 9.4, h1)
        for k in range(7):
            c.taper(cx-6+k*2, cy-11, cx-13+sway*0.4, cy-5+k*1.2, h1, 4, 2)
        c.rect(cx-13, cy-4, 5, 12, h1)
        c.taper(cx+5, cy-9, cx+8, cy+8, h1, 4, 2)          # one strand past the eye
        c.taper(cx+2, cy-10, cx+5, cy+4, h2, 3, 2)
        c.rect(cx-11, cy+7, 4, 8, h1)

    elif st == 'elfbraid':                                 # very long, and pointed ears
        c.rect(cx-14, cy-8, 9, 44, h1)
        c.rect(cx-13, cy+6, 3, 24, h2)
        c.rect(cx-9, cy-9, 17, 18, h1)
        for k in range(8):                                 # a thin plait down the front
            yy = cy + 2 + k*4
            c.ellipse(cx+7, yy, 2.4, 2.0, h1)
            c.set(cx+6, yy, h2)
        c.rect(cx+5, cy+34, 5, 3, p['accent'])
        c.taper(cx-11, cy+32, cx-15+sway, cy+42, h1, 6, 3)
        c.taper(cx-7, cy+1, cx-13, cy-3, p['skin'], 4, 2)  # the ear, long and swept back
        c.taper(cx-8, cy+1, cx-12, cy-2, p['skin2'], 2, 1)

    elif st == 'shag':                                     # choppy and uneven
        c.ellipse(cx-1, cy-2, 11.0, 9.8, h1)
        for k, (sx, sy, ln) in enumerate(((-13,-3,12), (-10,2,19), (-6,5,14), (-14,-8,8))):
            c.taper(cx+sx, cy+sy, cx+sx-2+sway*0.4, cy+sy+ln, h1, 5, 2)
            if k % 2: c.taper(cx+sx, cy+sy+1, cx+sx-2, cy+sy+ln-3, h2, 3, 1)
        c.rect(cx+6, cy-3, 3, 9, h1)

    elif st == 'bolt':                                     # bright, with one white streak
        c.ellipse(cx-1, cy-2, 10.8, 9.8, h1)
        for k in range(6):
            sx = -10 + k*3
            c.taper(cx+sx, cy-5, cx+sx-2+sway*0.3, cy-5-(9-abs(k-2.5)*1.4), h1, 5, 3)
        c.taper(cx-2, cy-6, cx-4+sway*0.3, cy-18, p['hair3'], 3, 2)   # the streak
        c.rect(cx-12, cy-3, 4, 9, h1)
        c.rect(cx+7, cy-3, 3, 8, h1)

    elif st == 'lowbun':                                   # tidy, gathered low
        c.ellipse(cx-1, cy-1, 11.0, 10.2, h1)
        c.ellipse(cx-11, cy+9, 5.6, 5.0, h1)               # the bun at the nape
        c.ellipse(cx-12, cy+8, 3.2, 2.6, h2)
        c.rect(cx-14, cy+5, 3, 8, h1)
        c.taper(cx-13, cy+11, cx-9, cy+5, p['metal'], 2, 2)     # the pin through it
        c.taper(cx-8, cy+2, cx-11+sway*0.3, cy+10, h1, 3, 2)
        c.rect(cx+6, cy-2, 3, 10, h1)

    elif st == 'rattail':                                  # short, one thin long braid
        c.ellipse(cx-1, cy-3, 10.2, 8.8, h1)
        c.rect(cx-12, cy-3, 4, 7, h1)
        for k in range(10):
            yy = cy + 3 + k*3.4
            xx = cx - 12 + math.sin(k*0.9)*1.8 + sway*0.25
            c.ellipse(xx, yy, 1.9, 1.7, h1 if k % 2 else h2)
        c.rect(cx-14, cy+38, 4, 3, p['accent'])
        c.rect(cx+6, cy-4, 2, 6, h1)

    elif st == 'halo':                                     # soft, voluminous, swept up
        c.ellipse(cx-2, cy-3, 12.4, 11.0, h1)
        for k, (sx, sy, ln) in enumerate(((-13,-6,10), (-9,-12,13), (-3,-16,14),
                                          (4,-15,12), (10,-9,9))):
            c.taper(cx+sx*0.6, cy+sy*0.5, cx+sx-2+sway*0.3, cy+sy-4, h1, 8, 4)
            if k % 2: c.taper(cx+sx*0.6, cy+sy*0.5, cx+sx-2, cy+sy-2, h2, 5, 3)
        c.taper(cx-12, cy-2, cx-17+sway*0.6, cy+9, h1, 6, 3)
        c.rect(cx+7, cy-4, 3, 8, h1)

    elif st == 'bristle':                                  # short, upright, blunt
        c.ellipse(cx-1, cy-2, 10.8, 9.6, h1)
        for k in range(8):
            sx = -10 + k*2.6
            hgt = 8 - abs(k-3.5)*0.7
            c.taper(cx+sx, cy-6, cx+sx-1+sway*0.2, cy-6-hgt, h1, 5, 4)
            if k % 2: c.set(cx+sx, cy-7-hgt, h2)
        c.rect(cx-12, cy-3, 4, 8, h1)
        c.rect(cx+7, cy-3, 3, 7, h1)

    elif st == 'shade':                                    # heavy, layered, sharp
        c.ellipse(cx-1, cy-2, 11.6, 10.4, h1)
        for sx, sy, ln in ((-14,-4,10), (-12,1,16), (-9,4,13)):
            c.taper(cx+sx, cy+sy, cx+sx-3+sway*0.4, cy+sy+ln, h1, 6, 3)
        for k, (sx, sy) in enumerate(((-11,-9), (-5,-15), (2,-17), (9,-12), (13,-5))):
            c.taper(cx+sx*0.7, cy+sy*0.5, cx+sx+sway*0.25, cy+sy, h1, 6, 3)
            if k % 2: c.taper(cx+sx*0.7, cy+sy*0.5, cx+sx, cy+sy+1, h2, 3, 2)
        c.taper(cx+7, cy-2, cx+8, cy+13, h1, 5, 4)         # long sidelock past the jaw
        c.taper(cx+7, cy+8, cx+8, cy+14, h2, 3, 2)
        c.taper(cx-13, cy-2, cx-16+sway*0.5, cy+9, h1, 4, 2)

    elif st == 'pixie':                                    # very short, feathered
        c.ellipse(cx-1, cy-3, 10.0, 8.4, h1)
        for k in range(5):
            sx = -10 + k*4
            c.taper(cx+sx, cy-1, cx+sx-3+sway*0.3, cy+5+k, h1, 4, 2)
        c.taper(cx-10, cy+1, cx-14+sway*0.5, cy+9, h1, 4, 2)
        c.rect(cx+6, cy-4, 2, 6, h1)


def hair_front(c, p, pose, cx, cy):
    """The cap over the skull and the fringe. The fringe is per style too -
    one shared fringe on twenty-one heads is what made them look related."""
    st, h1, h2, h3 = p['style'], p['hair'], p['hair2'], p['hair3']
    sh, ac = p['shine'], p['accent']
    sway = -pose['sway']
    top = cy - 4                                          # never below the brows

    if st == 'mohawk':                                    # a bare skull, then a crest
        c.ellipse(cx-1, cy-3, 8.6, 9.0, p['skin'], ymax=int(top))
        c.ellipse(cx-4, cy-5, 6.4, 6.0, p['skin2'], ymax=int(top-2))
        for k in range(10):
            sx  = -7 + k*1.6
            hgt = 16 - abs(k-4.5)*1.7
            c.taper(cx+sx, cy-7, cx+sx-2+sway*0.35, cy-7-hgt, h1, 4, 2)
            if k % 2: c.taper(cx+sx, cy-9, cx+sx-2+sway*0.3, cy-7-hgt*0.7, h2, 2, 1)
        c.rect(cx-9, cy-4, 3, 3, ac)
        return
    c.ellipse(cx-1, cy-3, 8.8, 9.4, h1, ymax=int(top))    # the cap
    c.ellipse(cx-1.5, cy-5, 7.2, 7.2, h2, ymax=int(top-2))

    # ---- the fringe, shaped by style
    if st in ('spiky', 'wolfcut', 'messy', 'mohawk'):     # broken into strands
        for sx, sy in ((-8,-10), (-3,-13), (3,-12), (7,-8)):
            c.taper(cx+sx, cy+sy+5, cx+sx+2+sway*0.2, cy+sy-1, h1, 5, 2)
            c.taper(cx+sx, cy+sy+4, cx+sx+2, cy+sy, h2, 3, 1)
    elif st in ('hime', 'veil', 'bob'):                   # cut straight across
        c.rect(cx-9, cy-8, 17, 4, h1)
        c.rect(cx-8, cy-9, 15, 2, h2)
        for k in range(0, 17, 4): c.set(cx-9+k, cy-4, h1)
    elif st in ('dropcurl', 'lowbun', 'elfbraid'):        # soft and rounded
        for sx in (-8, -3, 2, 7):
            c.ellipse(cx+sx, cy-7, 3.6, 3.2, h1)
            c.ellipse(cx+sx-0.8, cy-8, 2.0, 1.6, h2)
    elif st in ('oni', 'shag', 'bolt'):                   # torn into hard strands
        for sx, sy in ((-9,-12), (-3,-15), (3,-14), (8,-10)):
            c.taper(cx+sx, cy+sy+6, cx+sx+2+sway*0.2, cy+sy-1, h1, 6, 3)
            c.taper(cx+sx, cy+sy+5, cx+sx+2, cy+sy+1, h2, 3, 1)
    elif st == 'monarch':                                 # a clean part, swept back
        c.taper(cx-8, cy-11, cx+7, cy-5, h1, 6, 4)
        c.taper(cx-7, cy-12, cx+4, cy-7, h2, 4, 2)
        c.taper(cx+1, cy-9, cx+6, cy-1, h1, 4, 2)
    elif st in ('moonfall', 'straightlong', 'wavelong'):  # cut straight across
        c.rect(cx-9, cy-8, 17, 4, h1)
        c.rect(cx-8, cy-9, 15, 2, h2)
        c.taper(cx+4, cy-4, cx+7, cy+3, h1, 3, 2)
    elif st in ('mane', 'tousle', 'fluff', 'pompadour'):  # torn into hard strands
        for sx, sy in ((-9,-12), (-3,-16), (3,-15), (9,-10)):
            c.taper(cx+sx, cy+sy+6, cx+sx+3+sway*0.2, cy+sy-2, h1, 7, 3)
            c.taper(cx+sx, cy+sy+5, cx+sx+2, cy+sy, h2, 4, 2)
    elif st in ('undershave', 'bandana', 'knotted'):      # short and swept flat
        c.taper(cx-7, cy-9, cx+8, cy-4, h1, 6, 4)
        c.taper(cx-6, cy-10, cx+5, cy-6, h2, 4, 2)
    elif st == 'sidesweep':                               # the whole fringe over one eye
        c.taper(cx-6, cy-11, cx+9, cy+2, h1, 8, 5)
        c.taper(cx-5, cy-11, cx+7, cy-2, h2, 5, 3)
    elif st == 'earcap':                                  # soft, rounded, under a hood
        for sx in (-7, -2, 3, 7):
            c.ellipse(cx+sx, cy-7, 3.6, 3.0, h1)
            c.ellipse(cx+sx-0.8, cy-8, 2.0, 1.6, h2)
    elif st in ('combed', 'rattail'):                     # parted and swept to one side
        c.taper(cx-7, cy-10, cx+8, cy-4, h1, 6, 4)
        c.taper(cx-6, cy-11, cx+5, cy-6, h2, 4, 2)
    elif st == 'dragontail':                              # a blunt fringe with two points
        c.rect(cx-9, cy-8, 17, 4, h1)
        c.rect(cx-8, cy-9, 15, 2, h2)
        c.taper(cx-6, cy-4, cx-8, cy+2, h1, 3, 2)
        c.taper(cx+5, cy-4, cx+7, cy+2, h1, 3, 2)
    elif st == 'halo':                                    # a heavy soft sweep upward
        for sx, sy in ((-8,-13), (-2,-16), (4,-15), (9,-10)):
            c.taper(cx+sx, cy+sy+6, cx+sx-1+sway*0.2, cy+sy-2, h1, 7, 4)
            c.taper(cx+sx, cy+sy+5, cx+sx-1, cy+sy, h2, 4, 2)
    elif st == 'bristle':                                 # blunt spikes, low over the brow
        for sx in (-8, -3, 2, 7):
            c.taper(cx+sx, cy-5, cx+sx, cy-11, h1, 5, 4)
            c.set(cx+sx, cy-12, h2)
    elif st == 'shade':                                   # heavy, falling over the brow
        for sx, sy in ((-9,-11), (-4,-14), (2,-14), (7,-10)):
            c.taper(cx+sx, cy+sy+6, cx+sx+3+sway*0.2, cy+sy-1, h1, 6, 3)
        c.taper(cx+2, cy-8, cx+8, cy-2, h1, 5, 3)         # a lock hanging past the eye
        c.taper(cx+3, cy-8, cx+7, cy-3, h2, 3, 2)
    elif st in ('undercut', 'slick'):                     # one long sweep forward
        c.taper(cx-6, cy-9, cx+8, cy-3, h1, 7, 4)
        c.taper(cx-5, cy-10, cx+6, cy-5, h2, 4, 2)
    elif st == 'curls':                                   # soft, rounded
        for sx in (-7, -2, 3, 7):
            c.ellipse(cx+sx, cy-7, 3.4, 3.0, h1)
            c.ellipse(cx+sx-0.8, cy-8, 1.8, 1.4, h2)
    else:                                                 # a swept mass
        c.ellipse(cx-1, cy-6.5, 8.4, 5.6, h1, ymax=int(cy-5))
        c.ellipse(cx-2.5, cy-7.5, 6.2, 3.8, h2, ymax=int(cy-7))
        c.taper(cx+4, cy-8, cx+7, cy-4, h1, 3, 2)

    for sx, w in ((-7,4), (-1,5), (5,3)):                 # the shine band
        c.rect(cx+sx, cy-9, w, 1, h3)
        c.rect(cx+sx+1, cy-10, max(1, w-2), 1, sh)
    c.rect(cx-10, cy-5, 4, 13, h1)                        # the lock behind the ear
    c.rect(cx-10, cy-5, 2, 10, h2)
    c.set(cx-8, cy+8, h1)

    # ---- the ornament that belongs to that character alone
    if   st == 'ponytail': c.rect(cx-9, cy-13, 5, 4, ac); c.rect(cx-8, cy-14, 3, 1, ac)
    elif st == 'long':     c.rect(cx-3+TURN, cy-16, 6, 3, ac); c.rect(cx-2+TURN, cy-17, 4, 1, ac)
    elif st == 'bob':      c.rect(cx-17, cy-10, 4, 7, ac); c.rect(cx-17, cy-10, 4, 2, '#ffffff')
    elif st == 'braid':    c.rect(cx-3, cy-15, 7, 3, ac); c.rect(cx-3, cy-15, 7, 1, '#ffffff')
    elif st == 'twin':     c.rect(cx-15, cy-9, 5, 6, ac); c.rect(cx-15, cy-9, 5, 2, '#ffffff')
    elif st == 'hime':     c.rect(cx-2, cy-14, 8, 2, ac); c.set(cx+6, cy-13, ac)
    elif st == 'wolfcut':  c.rect(cx-11, cy-8, 3, 3, ac)
    elif st == 'wave':     c.rect(cx+2, cy-13, 4, 3, ac); c.rect(cx+3, cy-14, 2, 1, '#ffffff')
    elif st == 'bun':      c.rect(cx-13, cy-14, 2, 6, ac)      # the pin through it
    elif st == 'curls':    c.rect(cx-6, cy-13, 10, 2, ac); c.set(cx-1, cy-14, '#ffffff')
    elif st == 'crown':    c.rect(cx-1, cy-15, 3, 4, ac); c.rect(cx-2, cy-16, 5, 1, '#ffffff')
    elif st == 'sidetail': c.rect(cx+1, cy-13, 5, 3, ac)
    elif st == 'topknot':  c.rect(cx-6, cy-13, 3, 3, ac)
    elif st == 'veil':     c.rect(cx+1, cy-14, 4, 4, ac); c.rect(cx+2, cy-15, 2, 1, '#ffffff')
    elif st == 'pixie':    c.rect(cx-4, cy-13, 3, 2, ac); c.rect(cx+2, cy-12, 2, 2, ac)
    elif st == 'shade':    c.set(cx-11, cy-4, ac); c.set(cx-11, cy-2, ac)
    elif st == 'halo':     pass                           # the blindfold is the ornament
    elif st == 'dropcurl': c.rect(cx-4, cy-13, 4, 2, ac)
    elif st == 'oni':      c.rect(cx-10, cy-6, 3, 3, ac)
    elif st == 'dragontail': c.rect(cx-2, cy-14, 6, 3, ac); c.rect(cx-1, cy-15, 4, 1, '#ffffff')
    elif st == 'combed':   c.rect(cx-11, cy-6, 2, 5, ac)
    elif st == 'elfbraid': c.rect(cx-1, cy-15, 5, 3, ac); c.rect(cx, cy-16, 3, 1, '#ffffff')
    elif st == 'shag':     c.rect(cx-9, cy-8, 3, 2, ac)
    elif st == 'bolt':     c.rect(cx+3, cy-13, 3, 3, ac)
    elif st == 'lowbun':   c.rect(cx-14, cy+2, 2, 5, ac)
    elif st == 'rattail':  c.rect(cx-10, cy-6, 3, 2, ac)
    elif st == 'monarch':  c.set(cx-12, cy-7, ac); c.set(cx-12, cy-5, ac)
    elif st == 'moonfall': c.rect(cx-2, cy-14, 5, 2, ac)
    elif st == 'straightlong': c.rect(cx-4, cy-14, 8, 2, ac)
    elif st == 'undershave': c.set(cx-10, cy-6, ac)
    elif st == 'mane':     c.rect(cx-1, cy-15, 4, 2, ac)
    elif st == 'tousle':   c.set(cx-9, cy-11, ac)
    elif st == 'bandana':  pass
    elif st == 'fluff':    c.rect(cx-8, cy-11, 3, 2, ac)
    elif st == 'earcap':   c.rect(cx-3, cy-13, 6, 2, ac)
    elif st == 'knotted':  c.set(cx-9, cy-9, ac)
    elif st == 'sidesweep':c.rect(cx-9, cy-9, 3, 2, ac)
    elif st == 'wavelong': c.rect(cx+1, cy-14, 4, 3, ac)
    elif st == 'pompadour':c.set(cx-10, cy-4, ac)
    elif st == 'bristle':  c.rect(cx-10, cy-2, 3, 2, ac)
    elif st == 'mohawk':   c.rect(cx-10, cy-3, 3, 3, ac)
    elif st == 'slick':    c.set(cx-12, cy-6, ac); c.set(cx-12, cy-4, ac)
    elif st == 'messy':    c.rect(cx-8, cy-12, 3, 2, ac)
    elif st == 'undercut': c.rect(cx-9, cy-7, 3, 2, ac)
    elif st == 'crop':     pass
    elif st == 'spiky':    pass


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
    elif w == 'atomblade':
        # a straight sword with the nucleus set in the guard and a ring around it
        L, hxp = 28, min(hxp, 40)
        c.taper(hxp-ux*10, hyp-uy*10, hxp, hyp, p['grip'], 4, 4)
        c.line(hxp-ux*3-nx*6, hyp-uy*3-ny*6, hxp-ux*3+nx*6, hyp-uy*3+ny*6, p['accent'], 3)
        c.taper(hxp+ux*3+nx*2.2, hyp+uy*3+ny*2.2,
                hxp+ux*L+nx*1.4, hyp+uy*L+ny*1.4, p['ink'], 4, 2)
        c.taper(hxp+ux*3, hyp+uy*3, hxp+ux*L, hyp+uy*L, p['metal'], 5, 2)
        c.line(hxp+ux*6, hyp+uy*6, hxp+ux*(L-2), hyp+uy*(L-2), '#ffffff', 1)
        gx2, gy2 = hxp-ux*3, hyp-uy*3
        c.ellipse(gx2, gy2, 2.2, 2.2, p['trim'])                   # nucleus
        c.ellipse(gx2, gy2, 1.0, 1.0, '#ffffff')
        for k in range(20):                                        # one electron ring
            a2 = k/20*math.pi*2
            c.set(gx2 + math.cos(a2)*5.4, gy2 + math.sin(a2)*2.4, p['accent'])
        for k in (8, 15, 22):
            c.set(hxp+ux*k+nx*0.5, hyp+uy*k+ny*0.5, p['trim'])     # runes down the fuller
    elif w == 'greataxe':
        L, hxp = 19, min(hxp, 26)          # keep the head on the frame, not past it
        c.taper(hxp-ux*15, hyp-uy*15, hxp+ux*L, hyp+uy*L, p['grip'], 5, 4)   # haft
        hx2, hy2 = hxp+ux*(L-4), hyp+uy*(L-4)
        # half-pixel steps, or the rotation leaves holes and the bit looks speckled
        for ki in range(-22, 23):
            k = ki/2.0
            d = 11 - abs(k)*0.5
            for ji in range(int(d*2)):
                j = ji/2.0
                col = p['metal'] if j > 2.5 else p['trim']
                c.set(hx2 + nx*k + ux*j, hy2 + ny*k + uy*j, col)
        for ki in range(-18, 19):                                  # lit outer edge
            k = ki/2.0
            e = 10.5 - abs(k)*0.5
            c.set(hx2 + nx*k + ux*e, hy2 + ny*k + uy*e, '#ffffff')
        c.rect(hxp-3, hyp-3, 6, 7, p['grip'])
    elif w == 'scythe':
        L, hxp = 20, min(hxp, 26)
        c.taper(hxp-ux*18, hyp-uy*18, hxp+ux*L, hyp+uy*L, p['grip'], 4, 3)   # snath
        tx, ty = hxp+ux*L, hyp+uy*L - 16               # the head rides high on the snath
        c.taper(hxp+ux*(L-4), hyp+uy*(L-4), tx, ty, p['grip'], 4, 3)
        for ki in range(60):                           # a long blade sweeping forward
            t2 = ki/59.0
            a2 = 2.9 - t2*1.55                         # from behind the haft round to the tip
            bx3 = tx + math.cos(a2)*19*(0.35+t2*0.65)
            by3 = ty + math.sin(a2)*11*(0.35+t2*0.65) + 4
            wdt = 5.0 - t2*3.6
            for ji in range(int(wdt*2)):
                c.set(bx3, by3 - ji/2.0, p['metal'])
            c.set(bx3, by3 - wdt/2.0, '#ffffff')       # lit cutting edge
        c.ellipse(tx, ty+3, 3.0, 3.0, p['accent'])
        c.ellipse(tx, ty+3, 1.4, 1.4, '#ffffff')
        c.rect(hxp-3, hyp-3, 6, 7, p['grip'])
    elif w == 'rifle':
        L = 24
        c.taper(hxp-ux*9, hyp-uy*9, hxp+ux*L, hyp+uy*L, p['metal'], 4, 3)    # barrel
        c.line(hxp+ux*4, hyp+uy*4, hxp+ux*(L-2), hyp+uy*(L-2), p['cloth3'], 1)
        c.taper(hxp-ux*10, hyp-uy*10, hxp-ux*17, hyp-uy*17+4, p['grip'], 6, 5)  # stock
        c.rect(hxp-2, hyp+1, 4, 6, p['grip'])                                # grip
        c.rect(hxp+4, hyp-4, 5, 3, p['cloth3'])                              # sight
        c.set(hxp+ux*(L+1), hyp+uy*(L+1), p['accent'])                       # muzzle glow
        c.set(hxp+ux*(L+2), hyp+uy*(L+2), p['trim'])
    elif w == 'hookblade':
        L = 18
        c.taper(hxp-ux*6, hyp-uy*6, hxp, hyp, p['grip'], 4, 4)
        c.taper(hxp+ux*2, hyp+uy*2, hxp+ux*L, hyp+uy*L, p['metal'], 4, 2)
        for k in range(7):                                         # the hook curls back
            a2 = ang - 1.5 + k*0.26
            c.set(hxp+ux*L + math.cos(a2)*5, hyp+uy*L + math.sin(a2)*5, p['metal'])
        c.line(hxp+ux*4, hyp+uy*4, hxp+ux*(L-1), hyp+uy*(L-1), '#ffffff', 1)
        for k in range(7):                                         # a short chain, coiled
            lx = hxp - ux*6 - k*1.6
            ly = hyp + 3 + math.sin(k*1.1)*2.2
            c.set(lx, ly, p['trim']); c.set(lx, ly+1, p['metal'])
    elif w == 'nodachi':
        L, hxp = 33, min(hxp, 28)                          # long, slim, slightly curved
        c.taper(hxp-ux*11, hyp-uy*11, hxp, hyp, p['grip'], 4, 4)
        c.line(hxp-ux*3-nx*4, hyp-uy*3-ny*4, hxp-ux*3+nx*4, hyp-uy*3+ny*4, p['accent'], 2)
        for k in range(4, L):
            t2 = (k-4)/(L-4.0)
            bx3, by3 = hxp+ux*k-nx*t2*t2*4, hyp+uy*k-ny*t2*t2*4
            for j in range(3): c.set(bx3+nx*j, by3+ny*j, p['metal'] if j else p['ink'])
            c.set(bx3-nx, by3-ny, '#ffffff')
        c.rect(hxp-3, hyp-3, 6, 7, p['grip'])
    elif w == 'sabres':
        for sgn, hd in ((1, (hxp, hyp)), (-1, hand(pose, True))):
            bx3, by3 = hd
            ax, ay = (ux, uy) if sgn > 0 else (-abs(ux), uy*0.5)
            c.taper(bx3-ax*5, by3-ay*5, bx3, by3, p['grip'], 4, 4)
            for k in range(2, 19):                          # a curved sabre
                t2 = (k-2)/17.0
                c.set(bx3+ax*k - ay*t2*t2*4, by3+ay*k + ax*t2*t2*4, p['metal'])
                if k % 2: c.set(bx3+ax*k - ay*t2*t2*4, by3+ay*k+ax*t2*t2*4-1, '#ffffff')
            c.line(bx3-ay*4, by3+ax*4, bx3+ay*4, by3-ax*4, p['trim'], 2)
    elif w == 'twinsabre':
        for sgn, hd in ((1, (hxp, hyp)), (-1, hand(pose, True))):
            bx3, by3 = hd
            ax, ay = (ux, uy) if sgn > 0 else (-abs(ux), uy*0.6)
            c.taper(bx3-ax*6, by3-ay*6, bx3, by3, p['grip'], 5, 4)
            c.taper(bx3+ax*2, by3+ay*2, bx3+ax*20, by3+ay*20, p['metal'], 4, 2)
            c.line(bx3+ax*4, by3+ay*4, bx3+ax*18, by3+ay*18, '#ffffff', 1)
            c.rect(bx3-2, by3-3, 4, 2, p['cloth2'])
        for k in range(5):                                  # the gas line, trailing back
            c.set(hxp-ux*(9+k*3)-4, hyp-uy*(9+k*3)+math.sin(k)*2, p['trim'])
    elif w == 'sunaxe':
        L, hxp = 20, min(hxp, 26)
        c.taper(hxp-ux*14, hyp-uy*14, hxp+ux*L, hyp+uy*L, p['grip'], 5, 4)
        hx2, hy2 = hxp+ux*(L-4), hyp+uy*(L-4)
        for ki in range(-20, 21):                           # a broad sun-bit
            k = ki/2.0
            d = 10 - abs(k)*0.42
            for ji in range(int(d*2)):
                j = ji/2.0
                c.set(hx2+nx*k+ux*j, hy2+ny*k+uy*j, p['metal'] if j > 2.5 else p['trim'])
        for ki in range(-16, 17):
            k = ki/2.0
            c.set(hx2+nx*k+ux*(9.5-abs(k)*0.42), hy2+ny*k+uy*(9.5-abs(k)*0.42), '#ffffff')
        for k in range(6):                                  # rays off the head
            a2 = ang + k*1.05
            c.set(hx2 + math.cos(a2)*14, hy2 + math.sin(a2)*13, p['accent'])
        c.rect(hxp-3, hyp-3, 6, 7, p['grip'])
    elif w == 'brokenblade':
        L, hxp = 17, min(hxp, 38)                           # snapped off halfway
        c.taper(hxp-ux*9, hyp-uy*9, hxp, hyp, p['grip'], 4, 4)
        c.line(hxp-ux*2-nx*5, hyp-uy*2-ny*5, hxp-ux*2+nx*5, hyp-uy*2+ny*5, p['accent'], 3)
        c.taper(hxp+ux*3, hyp+uy*3, hxp+ux*L, hyp+uy*L, p['metal'], 6, 5)
        c.line(hxp+ux*5-nx, hyp+uy*5-ny, hxp+ux*(L-1)-nx, hyp+uy*(L-1)-ny, '#ffffff', 1)
        for k in range(4):                                  # a ragged break, not a point
            c.set(hxp+ux*L+nx*(k-2), hyp+uy*L+ny*(k-2), p['ink'])
            c.set(hxp+ux*(L+1)+nx*(k-2), hyp+uy*(L+1)+ny*(k-2), p['metal'] if k%2 else None)
        c.rect(hxp-3, hyp-3, 6, 7, p['grip'])
    elif w == 'threeswords':
        for sgn, hd in ((1, (hxp, hyp)), (-1, hand(pose, True))):
            bx3, by3 = hd
            ax, ay = (ux, uy) if sgn > 0 else (-abs(ux), uy*0.6)
            c.taper(bx3-ax*5, by3-ay*5, bx3, by3, p['grip'], 4, 4)
            c.taper(bx3+ax*2, by3+ay*2, bx3+ax*18, by3+ay*18, p['metal'], 4, 2)
            c.line(bx3+ax*4, by3+ay*4, bx3+ax*16, by3+ay*16, '#ffffff', 1)
        mx2, my2 = CX + 3 + TURN, HEADY + pose['bob'] + 12  # and the third, in his teeth
        c.taper(mx2, my2, mx2+16, my2-3, p['metal'], 4, 2)
        c.line(mx2+3, my2-1, mx2+14, my2-3, '#ffffff', 1)
        c.rect(mx2-3, my2-1, 4, 3, p['grip'])
    elif w == 'claws':
        for hx3, hy3 in (hand(pose, False), hand(pose, True)):
            c.ellipse(hx3, hy3+1, 4.0, 4.0, p['cloth2'])
            for k in (-3, 0, 3):
                c.taper(hx3+2, hy3+k*0.6, hx3+9, hy3+k*1.3, p['metal'], 3, 1)
                c.set(hx3+9, hy3+k*1.3, '#ffffff')
        hx4, hy4 = hand(pose, False)
        for k in range(5):                                  # current running off them
            c.set(hx4+6+k*2, hy4-4+((k%2)*5-2), p['trim'])
    elif w == 'satchel':
        bx3, by3 = hxp-3, hyp-4
        c.rect(bx3-5, by3, 11, 12, p['cloth3'])             # a doctor's bag
        c.rect(bx3-5, by3, 11, 3, p['metal'])
        c.rect(bx3-1, by3-3, 3, 4, p['metal'])
        c.rect(bx3-4, by3+4, 9, 5, p['cloth2'])
        c.rect(bx3-1, by3+5, 3, 3, p['accent'])
        c.rect(bx3-2, by3+4, 5, 1, p['accent'])
    elif w == 'slingshot':
        gx, gy = hxp, hyp
        c.taper(gx-2, gy+6, gx, gy-2, p['grip'], 4, 3)      # the handle
        c.taper(gx, gy-2, gx+7, gy-10, p['grip'], 3, 2)     # two prongs
        c.taper(gx, gy-2, gx+7, gy+6, p['grip'], 3, 2)
        c.line(gx+7, gy-10, gx+2, gy-2, p['cloth3'], 1)     # the band, drawn back
        c.line(gx+7, gy+6, gx+2, gy-2, p['cloth3'], 1)
        c.ellipse(gx+1, gy-2, 2.0, 2.0, p['metal'])
        bx3, by3 = hand(pose, True)                          # a pouch of shot
        c.ellipse(bx3, by3+2, 3.4, 3.0, p['cloth3'])
    elif w == 'emberboot':
        for k in range(9):                                   # the fire is on his leg
            fy = FEET - 6 - k*2
            c.set(CX + 8 + math.sin(k*0.8 + ph*3)*3, fy, p['accent'] if k%2 else p['trim'])
        c.ellipse(CX+9, FEET-6, 4.6, 3.0, p['accent'])
        c.ellipse(CX+9, FEET-6, 2.6, 1.8, '#ffffff')
        for hx3, hy3 in (hand(pose, False), hand(pose, True)):
            c.ellipse(hx3, hy3+1, 3.2, 3.4, p['skin'])       # his hands stay in his pockets
    elif w == 'handsigil':
        for hx3, hy3 in (hand(pose, False), hand(pose, True)):
            c.ellipse(hx3, hy3+1, 3.4, 3.6, p['skin'])
        hx4, hy4 = hand(pose, False)
        for k in range(18):                                  # a ring of sigil marks
            a2 = ph*2 + k/18*math.pi*2
            c.set(hx4+4 + math.cos(a2)*9, hy4 + math.sin(a2)*8, p['trim'])
        for k in range(3):                                   # and a hand growing out of it
            c.taper(hx4+4, hy4, hx4+11+k, hy4-5+k*5, p['skin2'], 3, 2)
    elif w == 'armcannon':
        L = 22
        c.taper(hxp-ux*6, hyp-uy*6, hxp+ux*L, hyp+uy*L, p['metal'], 8, 7)
        c.taper(hxp-ux*4, hyp-uy*4, hxp+ux*(L-2), hyp+uy*(L-2), p['cloth2'], 5, 4)
        for k in range(3):
            c.line(hxp+ux*(6+k*5)-nx*5, hyp+uy*(6+k*5)-ny*5,
                   hxp+ux*(6+k*5)+nx*5, hyp+uy*(6+k*5)+ny*5, p['cloth3'], 1)
        c.ellipse(hxp+ux*(L+1), hyp+uy*(L+1), 4.2, 4.2, p['cloth3'])   # the muzzle
        c.ellipse(hxp+ux*(L+1), hyp+uy*(L+1), 2.4, 2.4, p['accent'])
        c.rect(hxp-8, hyp-6, 5, 12, p['metal'])                        # the shoulder block
        c.rect(hxp-8, hyp-6, 5, 3, p['trim'])
    elif w == 'shadowblade':
        # black steel with a lit edge, and it smokes
        L, hxp = 29, min(hxp, 38)
        c.taper(hxp-ux*10, hyp-uy*10, hxp, hyp, p['grip'], 4, 4)
        c.line(hxp-ux*3-nx*5, hyp-uy*3-ny*5, hxp-ux*3+nx*5, hyp-uy*3+ny*5, p['accent'], 3)
        c.taper(hxp+ux*3, hyp+uy*3, hxp+ux*L, hyp+uy*L, p['ink'], 5, 2)
        c.line(hxp+ux*4-nx*1.5, hyp+uy*4-ny*1.5,
               hxp+ux*(L-1)-nx*1.5, hyp+uy*(L-1)-ny*1.5, p['metal'], 1)
        for k in range(5):                                  # shadow coming off the flat
            t2 = 6 + k*4
            c.set(hxp+ux*t2+nx*(3+math.sin(k+ph*3)*2),
                  hyp+uy*t2+ny*(3+math.sin(k+ph*3)*2), p['trim'])
        c.rect(hxp-3, hyp-3, 6, 7, p['grip'])
    elif w == 'slimeorb':
        # a blob of himself, held loose. It never has quite the same outline.
        wob = math.sin(ph*3.1)*1.6
        c.ellipse(hxp+2, hyp+wob, 6.4, 6.0, p['trim'])
        c.ellipse(hxp+1, hyp-1+wob, 4.4, 4.0, p['metal'])
        c.ellipse(hxp, hyp-2+wob, 2.0, 1.6, '#ffffff')
        for k in range(4):
            a2 = ang + k*1.6 + ph
            c.set(hxp+2 + math.cos(a2)*8, hyp+wob + math.sin(a2)*7, p['trim'])
        c.ellipse(hxp+6, hyp+3+wob, 2.0, 1.6, p['trim'])   # a drip pulling away
    elif w == 'odachi':
        L, hxp = 34, min(hxp, 30)                          # very long, gently curved
        c.taper(hxp-ux*12, hyp-uy*12, hxp, hyp, p['grip'], 4, 4)
        c.line(hxp-ux*3-nx*5, hyp-uy*3-ny*5, hxp-ux*3+nx*5, hyp-uy*3+ny*5, p['accent'], 3)
        for k in range(4, L):
            t2 = (k-4)/(L-4.0)
            bx3 = hxp + ux*k - nx*t2*t2*5
            by3 = hyp + uy*k - ny*t2*t2*5
            for j in range(4 - int(t2*2)):
                c.set(bx3 + nx*j, by3 + ny*j, p['metal'] if j else p['ink'])
            c.set(bx3 - nx, by3 - ny, '#ffffff')
        c.rect(hxp-3, hyp-3, 6, 7, p['grip'])
    elif w == 'dragongaunt':
        for hx3, hy3 in (hand(pose, False), hand(pose, True)):
            c.ellipse(hx3, hy3+1, 5.6, 5.2, p['cloth2'])
            c.ellipse(hx3, hy3, 4.0, 3.6, p['metal'])
            for k in (-4, 0, 4):                            # claws
                c.taper(hx3+3, hy3+k*0.6, hx3+9, hy3+k, p['metal'], 3, 1)
            c.rect(hx3-6, hy3+3, 12, 3, p['cloth3'])
        hx4, hy4 = hand(pose, False)
        for k in range(6):
            a2 = ang + k*1.05
            c.set(hx4+3 + math.cos(a2)*9, hy4 + math.sin(a2)*8, p['accent'])
    elif w == 'gloves':
        for hx3, hy3 in (hand(pose, False), hand(pose, True)):
            c.ellipse(hx3, hy3+1, 3.8, 4.0, '#f2eee0')     # white gloves
            c.rect(hx3-3, hy3+3, 7, 2, '#ffffff')
            c.set(hx3-2, hy3+4, p['cloth3'])
        hx4, hy4 = hand(pose, False)
        for k in range(11):                                 # dark fire off the palm
            t2 = k/10.0
            c.set(hx4 + 4 + t2*9, hy4 - 2 + math.sin(t2*7 + ph*4)*4*t2,
                  p['accent'] if k % 3 == 0 else p['cloth2'])
    elif w == 'wand':
        L, hxp = 17, min(hxp, 38)
        c.taper(hxp-ux*7, hyp-uy*7, hxp+ux*L, hyp+uy*L, p['grip'], 4, 2)
        c.line(hxp-ux*4, hyp-uy*4, hxp+ux*(L-2), hyp+uy*(L-2), p['metal'], 1)
        tx2, ty2 = hxp+ux*(L+2), hyp+uy*(L+2)
        c.ellipse(tx2, ty2, 3.0, 3.0, p['accent'])
        c.ellipse(tx2-0.8, ty2-0.8, 1.4, 1.4, '#ffffff')
        for k in range(5):                                  # runes hanging in the air
            a2 = ph*2 + k*1.3
            c.set(tx2 + math.cos(a2)*8, ty2 + math.sin(a2)*7, p['trim'])
        c.rect(hxp-3, hyp-3, 6, 7, p['grip'])
    elif w == 'chainsaw':
        L, hxp = 24, min(hxp, 30)
        c.rect(hxp-6, hyp-5, 10, 11, p['cloth3'])           # the housing
        c.rect(hxp-5, hyp-4, 8, 5, p['cloth2'])
        c.rect(hxp-8, hyp-1, 3, 6, p['grip'])
        bar = 5
        for k in range(6, L):                               # the bar
            for j in range(-bar//2, bar//2+1):
                c.set(hxp+ux*k+nx*j, hyp+uy*k+ny*j, p['metal'] if abs(j) < 2 else p['ink'])
        for k in range(6, L, 3):                            # teeth, offset by the phase
            off = 3 if (k + int(ph*8)) % 6 < 3 else -3
            c.set(hxp+ux*k+nx*off, hyp+uy*k+ny*off, '#ffffff')
        c.set(hxp+ux*L, hyp+uy*L, p['accent'])
    elif w == 'tachi':
        L, hxp = 27, min(hxp, 36)                           # slim and straight
        c.taper(hxp-ux*9, hyp-uy*9, hxp, hyp, p['grip'], 3, 3)
        c.line(hxp-ux*2-nx*4, hyp-uy*2-ny*4, hxp-ux*2+nx*4, hyp-uy*2+ny*4, p['accent'], 2)
        c.taper(hxp+ux*3, hyp+uy*3, hxp+ux*L, hyp+uy*L, p['metal'], 3, 2)
        c.line(hxp+ux*5-nx, hyp+uy*5-ny, hxp+ux*(L-1)-nx, hyp+uy*(L-1)-ny, '#ffffff', 1)
        for k in range(3):                                  # current running the edge
            c.set(hxp+ux*(9+k*6)+nx*2, hyp+uy*(9+k*6)+ny*2, p['accent'])
    elif w == 'towershield':
        bx3, by3 = hxp - 2, hyp - 12
        for k in range(26):                                 # a tall kite shield
            w2 = 9 - max(0, (k-18))*1.3
            c.rect(bx3-w2, by3+k, w2*2, 1, p['metal'])
            c.rect(bx3-w2, by3+k, 2, 1, p['cloth3'])
            c.rect(bx3+w2-2, by3+k, 2, 1, p['cloth2'])
        c.rect(bx3-9, by3, 18, 2, p['trim'])
        c.rect(bx3-2, by3+6, 4, 10, p['trim'])              # the device on the face
        c.rect(bx3-5, by3+9, 10, 3, p['trim'])
        c.ellipse(hxp, hyp, 3.0, 3.2, p['skin'])
    elif w == 'revolver':
        L = 13
        c.taper(hxp-ux*4, hyp-uy*4, hxp+ux*L, hyp+uy*L, p['metal'], 4, 3)
        c.ellipse(hxp+ux*4, hyp+uy*4, 3.4, 3.4, p['cloth3'])   # the cylinder
        c.ellipse(hxp+ux*4, hyp+uy*4, 1.6, 1.6, p['trim'])
        c.rect(hxp-2, hyp+1, 4, 7, p['grip'])                  # the grip
        c.rect(hxp-3, hyp+7, 6, 2, p['grip'])
        c.set(hxp+ux*(L+1), hyp+uy*(L+1), p['accent'])
        bx3, by3 = hand(pose, True)                            # a card fanned in the off hand
        c.rect(bx3-3, by3-4, 6, 8, '#f2eee0')
        c.rect(bx3-1, by3-2, 2, 4, p['accent'])
    elif w == 'bare':
        # he does not carry anything. The hands are the weapon, so they glow.
        for hx3, hy3 in (hand(pose, False), hand(pose, True)):
            c.ellipse(hx3, hy3+1, 3.4, 3.6, p['skin'])
            c.rect(hx3-2, hy3+2, 4, 1, p['skin3'])
        hx4, hy4 = hand(pose, False)
        c.ellipse(hx4+3, hy4, 4.6, 4.6, p['accent'])       # the field around the hand
        c.ellipse(hx4+3, hy4, 2.8, 2.8, p['trim'])
        c.ellipse(hx4+2, hy4-1, 1.2, 1.2, '#ffffff')
        for k in range(6):
            a2 = ang + k
            c.set(hx4+3 + math.cos(a2)*7, hy4 + math.sin(a2)*7, p['trim'])
    elif w == 'wraps':
        # bandaged fists, and the knuckles already split
        for hx3, hy3 in (hand(pose, False), hand(pose, True)):
            c.ellipse(hx3, hy3+1, 4.0, 4.2, '#efe7d8')
            for k in range(-3, 4, 2): c.set(hx3+k, hy3-1, p['cloth3'])
            c.rect(hx3-3, hy3+3, 7, 2, '#efe7d8')
            c.rect(hx3-3, hy3+4, 7, 1, p['cloth3'])
        hx4, hy4 = hand(pose, False)
        c.set(hx4+3, hy4-2, p['trim']); c.set(hx4+4, hy4, p['trim'])
    elif w == 'sealcards':
        # talisman slips fanned between the fingers, and one already burning off
        for k in range(4):
            a2 = ang - 0.5 + k*0.30
            ax2, ay2 = math.cos(a2), math.sin(a2)
            c.taper(hxp, hyp, hxp+ax2*11, hyp+ay2*11, '#f0ead8', 5, 4)
            c.taper(hxp+ax2*3, hyp+ay2*3, hxp+ax2*10, hyp+ay2*10, p['trim'], 2, 1)
            c.set(hxp+ax2*11, hyp+ay2*11, p['ink'])
        c.ellipse(hxp, hyp, 3.0, 3.0, p['grip'])
        bx3, by3 = hand(pose, True)                        # the other hand, mid-sign
        c.ellipse(bx3, by3, 3.2, 3.4, p['skin2'])
        c.rect(bx3-1, by3-4, 2, 4, p['skin2'])
        for k in range(5):                                 # shadow leaking off the fingers
            c.set(bx3 - 2 - k, by3 + 3 + math.sin(k)*2, p['accent'])
    elif w == 'coilrod':
        # a tesla rod: a wound coil on a short shaft, arcing between its rings
        L, hxp = 20, min(hxp, 36)
        c.taper(hxp-ux*8, hyp-uy*8, hxp+ux*L, hyp+uy*L, p['grip'], 4, 3)
        for k in range(4):                                 # the windings
            bx3, by3 = hxp+ux*(7+k*3), hyp+uy*(7+k*3)
            c.line(bx3-nx*5, by3-ny*5, bx3+nx*5, by3+ny*5, p['metal'], 2)
        tx2, ty2 = hxp+ux*(L+2), hyp+uy*(L+2)
        c.ellipse(tx2, ty2, 4.4, 4.4, p['metal'])
        c.ellipse(tx2, ty2, 2.6, 2.6, p['accent'])
        c.ellipse(tx2-0.8, ty2-0.8, 1.2, 1.2, '#ffffff')
        for k in range(4):                                 # current jumping the gap
            c.set(tx2 + math.cos(k*1.6)*7, ty2 + math.sin(k*1.6)*7, p['accent'])
        c.rect(hxp-3, hyp-3, 6, 7, p['grip'])
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

def draw_cape(c, p, pose):
    """hangs off the shoulders and streams behind - the trail is driven by the
    same sway the hair uses, so a cape and a ponytail never fight each other"""
    sway = pose['sway']
    x = CX + pose['lean']*0.3 + TURN*0.4
    top = SHOULDER + pose['bob'] - 2
    for k in range(46):
        t  = k/45.0
        # it leaves the shoulder narrow, bells out, then tapers to a ragged hem
        w  = 4 + 9*math.sin(min(1.0, t*1.15)*math.pi*0.8)
        bx = x - 3 - t*(6 + sway*0.9) - t*t*4
        y  = top + k
        if y >= FEET: break
        c.rect(bx-w, y, w*1.6, 1, p['cape'])
        c.rect(bx-w, y, 2, 1, p['cape2'])              # lit fold on the trailing edge
        if k % 9 == 4: c.rect(bx-w+2, y, 2, 1, p['cape2'])
    c.rect(x-5, top-1, 11, 3, p['cape2'])              # the collar clasp
    c.rect(x-5, top-2, 11, 1, p['trim'])

def draw_char(key, frame):
    p, pose = CHARS[key], POSES[frame]
    c = Cv(W, H)
    cx, cy = head_pos(pose)
    if p.get('cape'): draw_cape(c, p, pose)
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
 'dogw':  dict(ink='#2a2a3d', body='#e8ecf5', body2='#ffffff', body3='#a8b0c8',
               eye='#5fe6ff', glow='#d6f4ff'),
 'dogb':  dict(ink='#0a0812', body='#2b2740', body2='#4a4468', body3='#151223',
               eye='#b07cff', glow='#dcc0ff'),
 'wheel': dict(ink='#0d0a14', body='#54506b', body2='#8b86a8', body3='#2e2b3d',
               eye='#ff5d5d', glow='#ffd24d', cloth='#8f2020', cloth2='#c33a3a'),
 'knight': dict(ink='#07060e', body='#1a1830', body2='#3a3560', body3='#0d0b18',
               eye='#b07cff', glow='#dcc0ff', cloth='#5a1030', cloth2='#a01f4a'),
 'antking': dict(ink='#07060e', body='#1e1a2e', body2='#463c66', body3='#0e0c18',
               eye='#8f5fff', glow='#c9a0ff', cloth='#2a1040', cloth2='#54207a'),
 'unmaker': dict(ink='#0a0410', body='#1e1830', body2='#4a3f70', body3='#0f0b1c',
               eye='#ffffff', glow='#c9a0ff', cloth='#2b0f3d', cloth2='#6b2fa0'),
 'echo':  dict(ink='#0a0410', body='#241d3a', body2='#4a3f70', body3='#120e22',
               eye='#c9a0ff', glow='#e8d4ff', cloth='#1d1030', cloth2='#442a68'),
 'boss':  dict(ink='#120818', body='#a86bff', body2='#dcc0ff', body3='#6b3fbd',
               eye='#ff5d5d', glow='#ffc0c0', cloth='#2a1b4d', cloth2='#4a3480'),
}
MOB_ORDER = ['slime', 'bat', 'imp', 'brute', 'boss', 'sovereign',
             'dogw', 'dogb', 'wheel', 'unmaker', 'echo', 'knight', 'antking']

HOUND_A = [(0,0), (0,-1), (3,-2), (6,-4), (3,-2), (0,0), (-2,1), (8,-5), (2,-1), (0,2)]

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

def draw_hound(c, m, f):
    """A shikigami hound in profile facing +x. Four legs, and the far pair is
    drawn in shadow so the body has depth instead of reading as a cut-out."""
    reach, bob = HOUND_A[f]
    cy = FEET - 22 + bob
    bd, bd2, bd3 = m['body'], m['body2'], m['body3']
    lunge = f in (7, 8)

    c.taper(CX-14, cy+2, CX-24-reach*0.4, cy-6-reach*0.5, bd3, 5, 2)   # tail, streaming
    c.taper(CX-16, cy, CX-22-reach*0.3, cy-3, bd, 4, 2)

    for far in (True, False):                                          # legs, back pair first
        col = bd3 if far else bd
        col2 = bd3 if far else bd2
        off = -2 if far else 1
        # hind legs drive, front legs reach
        hy = cy + 6
        hx = CX - 8 + off
        fk = reach * (0.9 if far else 1.2)
        c.taper(hx, hy, hx - 3 - fk*0.4, hy + 8, col, 6, 4)
        c.taper(hx - 3 - fk*0.4, hy + 8, hx - 5 - fk, FEET - 2, col2, 4, 3)
        c.rect(hx - 7 - fk, FEET - 3, 6, 3, col)
        fx2 = CX + 8 + off
        c.taper(fx2, hy, fx2 + 2 + fk*0.5, hy + 8, col, 5, 4)
        c.taper(fx2 + 2 + fk*0.5, hy + 8, fx2 + 3 + fk, FEET - 2, col2, 4, 3)
        c.rect(fx2 + 2 + fk, FEET - 3, 6, 3, col)
        if far: continue
        # --- body, only once, over the near legs
        c.ellipse(CX, cy + 3, 15.0, 8.0, bd)
        c.ellipse(CX - 2, cy + 1, 12.0, 5.4, bd2)
        c.ellipse(CX + 9, cy + 4, 6.4, 6.0, bd)                        # chest
        c.rect(CX - 14, cy + 2, 28, 1, bd3)                            # spine shadow
        # --- head, thrust forward on a lunge
        hxx = CX + 16 + (4 if lunge else 0)
        hyy = cy - 3 - (2 if lunge else 0)
        c.taper(CX + 10, cy, hxx - 2, hyy + 2, bd, 8, 7)                # neck
        c.ellipse(hxx, hyy, 6.6, 5.4, bd)
        c.ellipse(hxx - 1, hyy - 1.5, 5.0, 3.4, bd2)
        c.taper(hxx + 3, hyy + 1, hxx + 11, hyy + 3, bd, 5, 3)         # muzzle
        c.set(hxx + 11, hyy + 2, m['ink'])                              # nose
        c.taper(hxx - 3, hyy - 4, hxx - 6, hyy - 12, bd, 5, 2)          # ears, swept back
        c.taper(hxx - 1, hyy - 5, hxx - 3, hyy - 12, bd2, 4, 2)
        if f == 9:
            for i in range(-2, 3):
                c.set(hxx + 1 + i, hyy + i, m['ink']); c.set(hxx + 1 + i, hyy - i, m['ink'])
        else:
            c.rect(hxx + 1, hyy - 2, 4, 3, m['eye'])
            c.rect(hxx + 3, hyy - 2, 2, 1, m['glow'])
        if lunge:                                                       # jaws open
            c.taper(hxx + 4, hyy + 4, hxx + 12, hyy + 8, bd, 4, 2)
            for k in range(3):
                c.set(hxx + 6 + k*2, hyy + 4, '#ffffff')
                c.set(hxx + 6 + k*2, hyy + 7, '#ffffff')
        if f in (6, 8):                                                 # it flares
            for k in range(7):
                a = k/7*math.pi*2
                c.set(hxx + math.cos(a)*11, hyy + math.sin(a)*10, m['glow'])

def draw_wheel(c, m, f):
    """The wheel-crowned one. Bigger than anything else on the field, and it
    does not care whose side you are on."""
    lean, bob = BRUTE_A[f]
    hy  = 20 + bob
    cxx = CX + lean*0.5
    bd, bd2, bd3 = m['body'], m['body2'], m['body3']
    # --- the wheel behind the head, turning
    spin = f*0.5
    for k in range(28):
        a = spin + k/28*math.pi*2
        c.set(cxx - 1 + math.cos(a)*13, hy - 8 + math.sin(a)*13, m['glow'])
    for k in range(8):
        a = spin + k/8*math.pi*2
        c.line(cxx-1, hy-8, cxx - 1 + math.cos(a)*12, hy - 8 + math.sin(a)*12,
               m['cloth2'] if k % 2 else m['glow'], 1)
    c.ellipse(cxx-1, hy-8, 5.0, 5.0, m['cloth'])
    c.ellipse(cxx-1, hy-8, 2.6, 2.6, m['glow'])
    # --- body
    c.rect(cxx-13, hy+14, 26, 26, bd)
    c.rect(cxx-10, hy+16, 20, 10, bd2)
    c.rect(cxx-13, hy+35, 26, 5, bd3)
    for i in range(-10, 11, 5): c.rect(cxx+i, hy+18, 2, 16, bd3)        # ribs
    # --- head, low and forward
    c.ellipse(cxx+2, hy+4, 12.0, 9.6, bd)
    c.ellipse(cxx+3, hy+2, 9.0, 6.4, bd2)
    c.taper(cxx+8, hy+6, cxx+18, hy+9, bd, 8, 4)                        # jaw thrust out
    if f == 9:
        for i in range(-3, 4):
            c.set(cxx+6+i, hy+3+i, m['ink']); c.set(cxx+6+i, hy+3-i, m['ink'])
    else:
        c.rect(cxx+5, hy+1, 6, 4, m['eye'])
        c.rect(cxx+5, hy+1, 6, 1, m['glow'])
    for k in range(4):                                                  # teeth
        c.set(cxx+11+k*2, hy+10, '#ffffff'); c.set(cxx+11+k*2, hy+13, '#ffffff')
    # --- arms
    ax = 24 if f in (6, 8) else 17
    c.taper(cxx-13, hy+17, cxx-ax-lean, hy+(6 if f in (6,8) else 36), bd, 9, 6)
    c.taper(cxx+13, hy+17, cxx+ax+lean, hy+(6 if f in (6,8) else 36), bd, 9, 6)
    c.rect(cxx+ax+lean-4, hy+(4 if f in (6,8) else 34), 8, 5, bd3)      # claws
    # --- legs
    c.rect(cxx-13, hy+40, 10, FEET-(hy+40), bd)
    c.rect(cxx+3, hy+40, 10, FEET-(hy+40), bd)
    c.rect(cxx-15, FEET-5, 13, 5, bd3); c.rect(cxx+2, FEET-5, 13, 5, bd3)
    if f in (6, 7, 8):
        for k in range(8):
            a = k/8*math.pi*2
            c.set(cxx + math.cos(a)*26, hy+20 + math.sin(a)*22, m['glow'])

def draw_unmaker(c, m, f, small=False):
    """The Unmaker. A tall shrouded thing with too many arms and a crown of
    voids where a face should be - the Sovereign is a king, this is a hole."""
    lean, bob = BRUTE_A[f]
    sc  = 0.6 if small else 1.0
    hy  = (26 if small else 14) + bob
    cxx = CX + lean*0.5
    bd, bd2, bd3 = m['body'], m['body2'], m['body3']
    # --- the shroud, widening all the way to the floor
    for k in range(int((FEET - hy - 10)/1)):
        y = hy + 10 + k
        if y >= FEET: break
        w = min(25.0, (9 + k*0.34)) * sc          # the frame is only 56 wide
        c.rect(cxx - w, y, w*2, 1, bd)
        c.rect(cxx - w, y, 2, 1, bd3)
        c.rect(cxx + w - 2, y, 2, 1, bd2)
        if k % 11 == 5: c.rect(cxx - w + 3, y, w*2 - 6, 1, bd3)     # torn bands
    # --- arms: four of them, two long and two short
    for sgn in (-1, 1):
        for k, (ln, drop) in enumerate(((14, 30), (9, 14))):
            ax = cxx + sgn*(7 + ln)*sc
            ay = hy + drop*sc + (6 if f in (6, 8) else 0)
            c.taper(cxx + sgn*8*sc, hy + 12*sc, ax, ay, bd, 7*sc, 4*sc)
            for j in range(3):                                       # long fingers
                c.taper(ax, ay, ax + sgn*(5+j*2)*sc, ay + (4 - j*3)*sc, bd3, 3*sc, 1)
    # --- head: a smooth shroud with a crown of voids
    c.ellipse(cxx, hy, 11.5*sc, 10.0*sc, bd)
    c.ellipse(cxx, hy - 2*sc, 10.0*sc, 7.4*sc, bd2)
    c.taper(cxx, hy + 8*sc, cxx, hy + 16*sc, bd, 14*sc, 12*sc)
    if f == 9:
        for i in range(-3, 4):
            c.set(cxx+i, hy+i, m['ink']); c.set(cxx+i, hy-i, m['ink'])
    else:
        for k in range(3):                                           # three white slits
            c.rect(cxx - 7*sc + k*6*sc, hy - 1*sc, 2*sc, 4*sc, m['eye'])
    for k, (sx, h2) in enumerate(((-9, 6), (-5, 10), (0, 13), (5, 10), (9, 6))):
        c.taper(cxx + sx*sc, hy - 9*sc, cxx + sx*sc, (hy - 9 - h2)*sc + hy*0,
                m['cloth2'] if k % 2 else m['cloth'], 4*sc, 1)
    if f in (6, 7, 8):
        for k in range(10):
            a = k/10*math.pi*2
            c.set(cxx + math.cos(a)*30*sc, hy + 16*sc + math.sin(a)*24*sc, m['glow'])

def draw_knight(c, m, f):
    """A shadow in armour, with a plume. Big, slow, and it holds a line."""
    lean, bob = BRUTE_A[f]
    hy = 24 + bob
    cxx = CX + lean*0.5
    bd, bd2, bd3 = m['body'], m['body2'], m['body3']
    for k in range(22):                                    # cape
        w = 10 + k*0.5
        c.rect(cxx-w-2, hy+14+k, w*1.4, 1, m['cloth'])
        c.rect(cxx-w-2, hy+14+k, 2, 1, m['cloth2'])
    ax = 20 if f in (6, 8) else 14                         # arms first, then the plate
    c.taper(cxx-11, hy+16, cxx-ax-lean, hy+(6 if f in (6,8) else 32), bd3, 8, 6)
    c.taper(cxx+11, hy+16, cxx+ax+lean, hy+(6 if f in (6,8) else 32), bd, 8, 6)
    c.rect(cxx-11, hy+13, 22, 24, bd)                      # cuirass
    c.rect(cxx-9, hy+15, 18, 9, bd2)
    c.rect(cxx-11, hy+33, 22, 4, bd3)
    c.rect(cxx-13, hy+13, 5, 6, bd2)                       # pauldrons
    c.rect(cxx+8, hy+13, 5, 6, bd2)
    c.ellipse(cxx+1, hy+2, 9.6, 8.6, bd)                   # helm
    c.ellipse(cxx+2, hy, 7.4, 5.8, bd2)
    c.rect(cxx+3, hy, 6, 3, m['eye'])                      # the visor slit
    c.rect(cxx+5, hy, 3, 1, m['glow'])
    for k, (sx, h2) in enumerate(((-3, 9), (0, 13), (3, 10))):   # plume
        c.taper(cxx+sx, hy-8, cxx+sx-3, hy-8-h2, m['cloth2'] if k == 1 else m['cloth'], 4, 2)
    sx2, sy2 = cxx+ax+lean, hy+(4 if f in (6,8) else 30)   # the sword
    c.taper(sx2, sy2, sx2+4, sy2-24, m['body2'], 5, 2)
    c.line(sx2+1, sy2-4, sx2+4, sy2-22, m['glow'], 1)
    c.rect(cxx-11, hy+37, 9, FEET-(hy+37), bd)
    c.rect(cxx+2, hy+37, 9, FEET-(hy+37), bd)
    c.rect(cxx-13, FEET-5, 12, 5, bd3); c.rect(cxx+2, FEET-5, 12, 5, bd3)
    if f == 9:
        for i in range(-3, 4): c.set(cxx+3+i, hy+1+i, m['ink'])

def draw_antking(c, m, f):
    """A shadow with too many joints. Fast, and it does not stand upright."""
    reach, bob = HOUND_A[f]
    cy = FEET - 30 + bob
    global CX
    bd, bd2, bd3 = m['body'], m['body2'], m['body3']
    for sgn in (-1, 1):                                    # four legs, jointed high
        for k, off in enumerate((-6, 6)):
            jx = CX + off + sgn*3
            kx2 = jx + sgn*(9 + reach*0.4)
            c.taper(jx, cy+6, kx2, cy - 4 + k*3, bd3 if sgn < 0 else bd, 5, 3)
            c.taper(kx2, cy - 4 + k*3, kx2 + sgn*4, FEET-2, bd3 if sgn < 0 else bd2, 3, 2)
    c.ellipse(CX-8, cy+10, 10.0, 7.0, bd)                  # abdomen
    c.ellipse(CX-10, cy+9, 6.6, 4.4, bd2)
    c.ellipse(CX+3, cy+2, 7.6, 6.6, bd)                    # thorax
    c.ellipse(CX+4, cy, 5.2, 4.0, bd2)
    hxx = CX + 11 + (3 if f in (7, 8) else 0)
    c.ellipse(hxx, cy-5, 6.0, 5.0, bd)                     # head
    c.rect(hxx+1, cy-7, 4, 3, m['eye'])
    c.rect(hxx+3, cy-7, 2, 1, m['glow'])
    for sgn in (-1, 1):                                    # mandibles
        c.taper(hxx+4, cy-3+sgn, hxx+11, cy-3+sgn*5, bd2, 3, 1)
    for sgn in (-1, 1):                                    # antennae
        c.taper(hxx, cy-9, hxx+5+reach*0.3, cy-9+sgn*9-6, bd3, 3, 1)
    c.taper(CX-17, cy+9, CX-25-reach*0.35, cy+2, bd3, 5, 2) # tail
    if f in (6, 8):
        for k in range(8):
            a = k/8*math.pi*2
            c.set(hxx + math.cos(a)*13, cy-5 + math.sin(a)*12, m['glow'])
    if f == 9:
        for i in range(-2, 3): c.set(hxx+2+i, cy-5+i, m['ink'])

def draw_mob(key, f):
    m = MOBS[key]; c = Cv(W, H)
    if   key == 'slime': draw_slime(c, m, f)
    elif key == 'bat':   draw_bat(c, m, f)
    elif key == 'imp':   draw_imp(c, m, f)
    elif key == 'brute': draw_brute(c, m, f, False)
    elif key == 'boss':  draw_brute(c, m, f, True)
    elif key == 'sovereign': draw_brute(c, m, f, True, True)
    elif key in ('dogw', 'dogb'): draw_hound(c, m, f)
    elif key == 'wheel': draw_wheel(c, m, f)
    elif key == 'unmaker': draw_unmaker(c, m, f)
    elif key == 'echo': draw_unmaker(c, m, f, True)
    elif key == 'knight': draw_knight(c, m, f)
    elif key == 'antking': draw_antking(c, m, f)
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
