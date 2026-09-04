"""
Attack sprites for Twin Fate.

Every projectile and impact in the game is a pixel animation drawn here,
frame by frame, exactly the way the characters are - not a shape stroked onto
the canvas at runtime. Each effect is an 8-frame row in a 32x32 atlas.

Sprites are drawn in LUMINANCE: white core, then progressively darker greys.
The game multiplies that by whatever colour the shot is, so one katana-arc
animation serves Aoi's steel, Seryn's gold and Nyx's violet without three
copies of the artwork.

    python3 tools/make_fx.py          # rebuild assets/fx.png and re-embed it
    python3 tools/make_fx.py preview  # tools/fxpreview.png, the sheet at 6x
"""
import math, os, sys, base64

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_sprites import Cv, write_png

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S    = 32                      # frame size
N    = 8                       # frames per animation
C    = S/2.0                   # centre

W0, W1, W2, W3 = '#ffffff', '#d2d2d2', '#9a9a9a', '#5e5e5e'

def arc(c, cx, cy, r, a0, a1, col, thick=1):
    n = max(8, int(abs(a1-a0)*r*2))
    for i in range(n+1):
        a = a0 + (a1-a0)*i/n
        for t in range(thick):
            c.set(cx + math.cos(a)*(r-t), cy + math.sin(a)*(r-t), col)

def ring(c, cx, cy, r, col, thick=1):
    arc(c, cx, cy, r, 0, math.pi*2, col, thick)

def spark(c, cx, cy, n, r0, r1, col, seed=0):
    for k in range(n):
        a = seed + k/n*math.pi*2
        c.line(cx+math.cos(a)*r0, cy+math.sin(a)*r0,
               cx+math.cos(a)*r1, cy+math.sin(a)*r1, col, 1)

# ------------------------------------------------------------- projectiles ---
def fx_slash(c, f):
    """a blade arc opening out of nothing and thinning as it passes"""
    t = f/(N-1)
    r = 7 + t*6
    span = 1.30 - t*0.55
    mid  = -0.15 + t*0.30
    arc(c, C-4, C, r,   mid-span, mid+span, W2, 4)
    arc(c, C-4, C, r,   mid-span*.86, mid+span*.86, W1, 3)
    arc(c, C-4, C, r-1, mid-span*.60, mid+span*.60, W0, 2)
    if f < 3:                                    # the leading tip is sharpest
        a = mid+span
        c.line(C-4+math.cos(a)*r, C+math.sin(a)*r,
               C-4+math.cos(a)*(r+3), C+math.sin(a)*(r+3), W0, 1)

def fx_arrow(c, f):
    wob = math.sin(f/N*math.pi*2)*1.4
    c.taper(C-9, C+wob*.3, C+5, C, W2, 3, 2)          # shaft
    c.taper(C-8, C+wob*.3, C+4, C, W1, 2, 1)
    for dx, dy in ((6,0),(5,-1),(5,1),(7,0),(8,0),(4,-2),(4,2)):
        c.set(C+dx, C+dy, W0)                         # head
    c.set(C+9, C, W0)
    for k in range(4):                                # fletching flutters
        c.set(C-9+k, C-2+wob*.5 + k*.4, W1)
        c.set(C-9+k, C+2+wob*.5 - k*.4, W1)
    c.line(C-11, C+wob, C-6, C+wob*.4, W3, 1)

def fx_knife(c, f):
    a = f/N*math.pi*2                                 # tumbles end over end
    ux, uy = math.cos(a), math.sin(a)
    nx, ny = -uy, ux
    def put(k, j, col): c.set(C+ux*k+nx*j, C+uy*k+ny*j, col)
    for k in range(-3, 10):                           # blade tapers to a point
        h = max(0.0, 2.6 * (1 - max(0, k-1)/8.0))
        for j in range(int(-h), int(h)+1):
            put(k, j, W1 if abs(j) < h*0.55 else W2)
        put(k, -h, W0)                                # lit edge along the top
    for k in range(-8, -3):                           # grip
        for j in (-1, 0, 1): put(k, j, W3)
    for j in (-3, -2, 2, 3): put(-3, j, W2)           # guard
    put(10, 0, W0); put(9, 0, W0)

def fx_shard(c, f):
    p = 1 + math.sin(f/N*math.pi*2)*0.16              # glitters as it turns
    for k in range(-5, 9):
        h = max(0, 4 - abs(k-1)*0.55) * p
        col = W1 if k > 2 else W2
        for j in range(int(-h), int(h)+1): c.set(C+k, C+j, col)
    for k in range(-1, 7): c.set(C+k, C, W0)
    c.set(C+9, C, W0); c.set(C-6, C, W3)
    if f % 2 == 0:
        c.set(C+2, C-4, W0); c.set(C+4, C+3, W0)      # glint

def fx_bolt(c, f):
    fork = f % 4
    ys = [0, -4, 3, -3, 2, 0]
    xs = [-11, -7, -3, 1, 5, 10]
    for i in range(len(xs)-1):
        y0 = ys[i] * (1 if (i+fork) % 2 else -0.7)
        y1 = ys[i+1] * (1 if (i+1+fork) % 2 else -0.7)
        c.line(C+xs[i], C+y0, C+xs[i+1], C+y1, W2, 3)
    for i in range(len(xs)-1):
        y0 = ys[i] * (1 if (i+fork) % 2 else -0.7)
        y1 = ys[i+1] * (1 if (i+1+fork) % 2 else -0.7)
        c.line(C+xs[i], C+y0, C+xs[i+1], C+y1, W0, 1)
    if fork % 2 == 0:                                  # a branch splits off
        c.line(C, C, C+4, C-7, W1, 1)
    spark(c, C, C, 5, 8, 11 + (f % 3), W3, f*0.7)

def fx_orb(c, f):
    p = 1 + math.sin(f/N*math.pi*2)*0.18
    c.ellipse(C, C, 6.4*p, 6.4*p, W3)
    c.ellipse(C, C, 4.8*p, 4.8*p, W2)
    c.ellipse(C, C, 3.0*p, 3.0*p, W1)
    c.ellipse(C-0.8, C-0.8, 1.6, 1.6, W0)
    a = f/N*math.pi*2                                  # a mote orbits it
    c.ellipse(C+math.cos(a)*8, C+math.sin(a)*8, 1.6, 1.6, W0)
    c.ellipse(C+math.cos(a+2.1)*9, C+math.sin(a+2.1)*9, 1.1, 1.1, W2)

def fx_star(c, f):
    a0 = f/N*math.pi*2*0.5                             # spins as it flies
    for k in range(5):
        a = a0 + k/5*math.pi*2
        c.taper(C, C, C+math.cos(a)*9, C+math.sin(a)*9, W2, 5, 1)
    for k in range(5):
        a = a0 + k/5*math.pi*2
        c.taper(C, C, C+math.cos(a)*7, C+math.sin(a)*7, W1, 3, 1)
    c.ellipse(C, C, 3.0, 3.0, W0)
    c.ellipse(C, C, 4.4, 4.4, W1, ymax=int(C-3))

def fx_lance(c, f):
    ext = min(1.0, (f+1)/4.0)                          # extends out of nothing
    L = int(13*ext)
    for k in range(-L, L+1):
        h = 4.2 * (1 - abs(k)/(L+2.0))
        for j in range(int(-h), int(h)+1):
            c.set(C+k, C+j, W2 if abs(j) > h*0.5 else W1)
    for k in range(-L, L+1): c.set(C+k, C, W0)
    if L > 4:
        c.set(C+L+1, C, W0); c.set(C+L+2, C, W1)
        for j in (-1, 1): c.set(C+L, C+j, W0)
    spark(c, C, C, 4, L*0.7, L+2, W3, f*0.9)

def fx_bomb(c, f):
    c.ellipse(C-1, C+1, 6.0, 6.0, W2)
    c.ellipse(C-2, C, 4.2, 4.2, W1)
    c.ellipse(C-3, C-1, 1.8, 1.8, W0)
    c.rect(C-1, C-7, 2, 3, W3)                         # fuse cap
    fz = math.sin(f/N*math.pi*2)*2
    c.line(C, C-8, C+3+fz*0.4, C-12, W2, 1)
    r = 1.6 + (f % 3)*0.7                              # the fuse sputters
    c.ellipse(C+4+fz*0.4, C-13, r, r, W0)
    spark(c, C+4+fz*0.4, C-13, 4, r, r+2.5, W1, f)

def fx_note(c, f):
    bob = math.sin(f/N*math.pi*2)*1.5
    c.ellipse(C-3, C+4+bob, 4.0, 3.2, W1)              # head
    c.ellipse(C-4, C+3+bob, 2.0, 1.4, W0)
    c.rect(C, C-7+bob, 2, 12, W1)                      # stem
    c.taper(C+2, C-7+bob, C+7, C-3+bob, W2, 3, 2)      # flag
    for k in range(3):                                 # ripples
        c.set(C+8+k*2, C-8+bob + k, W3 if k else W2)

def fx_fire(c, f):
    """a tongue of flame: a solid body with a tip that licks and curls"""
    lick = f/N*math.pi*2
    for k in range(-9, 11):                            # k runs back to front
        t = (k+9)/19.0
        wob  = math.sin(lick + t*3.4)*2.6*t            # the tip whips, the base holds
        h    = 6.2*math.sin(min(1.0, (t+0.12))*math.pi*0.94)
        if h <= 0: continue
        for j in range(int(-h), int(h)+1):
            v = abs(j)/h
            col = W2 if v > .74 else (W1 if v > .40 else W0)
            c.set(C+k, C+j+wob, col)
        c.set(C+k, C-h+wob, W3); c.set(C+k, C+h+wob, W3)   # cooler rim
    for k in range(4):                                 # embers break off behind
        e = C - 11 - k*2.5
        c.set(e, C + math.sin(lick + k*1.4)*5, W1 if k < 2 else W2)

def fx_sword(c, f):
    """a thrown longsword, turning end over end - not a dagger"""
    a = f/N*math.pi*2
    ux, uy = math.cos(a), math.sin(a)
    nx, ny = -uy, ux
    def put(k, j, col): c.set(C+ux*k+nx*j, C+uy*k+ny*j, col)
    for k in range(-4, 14):                          # a long, straight blade
        h = 3.0 if k < 10 else max(0.0, 3.0 - (k-10)*0.9)
        for j in range(int(-h), int(h)+1):
            put(k, j, W1 if abs(j) < h*0.5 else W2)
        put(k, -h, W0)                               # lit edge
    for k in range(-11, -4):                         # grip and pommel
        for j in (-1, 0, 1): put(k, j, W3)
    put(-12, 0, W2)
    for j in range(-5, 6): put(-4, j, W2)            # crossguard
    for j in (-5, 5): put(-4, j, W1)
    put(14, 0, W0)

def fx_atom(c, f):
    """a nucleus with three electron shells, turning - Atom's whole visual"""
    a0 = f/N*math.pi*2
    for k, tilt in enumerate((0.0, 1.05, 2.10)):
        ca, sa = math.cos(tilt), math.sin(tilt)
        n = 40
        for i in range(n):                                # an ellipse, rotated
            t = i/n*math.pi*2
            ex, ey = math.cos(t)*11, math.sin(t)*4.2
            c.set(C + ex*ca - ey*sa, C + ex*sa + ey*ca, W2)
        t = a0 + k*2.1                                    # and the electron on it
        ex, ey = math.cos(t)*11, math.sin(t)*4.2
        px, py = C + ex*ca - ey*sa, C + ex*sa + ey*ca
        c.ellipse(px, py, 2.0, 2.0, W0)
        c.ellipse(px, py, 3.2, 3.2, W1, ymax=int(py))
    c.ellipse(C, C, 4.2, 4.2, W1)                         # nucleus
    c.ellipse(C, C, 2.6, 2.6, W0)
    c.set(C-1, C-1, W0)

def fx_burstatom(c, f):
    """critical mass: the shells collapse inward, then the whole thing goes"""
    t = f/(N-1)
    if t < 0.6:
        k = 1 - t/0.6                                     # collapsing
        for j, tilt in enumerate((0.0, 1.05, 2.10)):
            ca, sa = math.cos(tilt), math.sin(tilt)
            for i in range(48):
                u = i/48*math.pi*2
                ex, ey = math.cos(u)*(4+11*k), math.sin(u)*(1.6+4.2*k)
                c.set(C + ex*ca - ey*sa, C + ex*sa + ey*ca, W1 if j == 0 else W2)
        c.ellipse(C, C, 3+5*(1-k), 3+5*(1-k), W0)
    else:
        k = (t-0.6)/0.4                                   # and detonating
        ring(c, C, C, 4 + k*13, W0 if k < .5 else W1, 3)
        ring(c, C, C, 2 + k*9, W1, 2)
        spark(c, C, C, 10, 4+k*8, 6+k*13, W2, 0.3)
        c.ellipse(C, C, max(0.5, 6*(1-k)), max(0.5, 6*(1-k)), W0)

# ------------------------------------------------------------- impacts -------
def fx_hit(c, f):
    t = f/(N-1)
    r = 2 + t*12
    ring(c, C, C, r, W2, 2)
    ring(c, C, C, r-1.5, W1 if t < .6 else W2, 1)
    if t < .45:
        c.ellipse(C, C, 5.5*(1-t*2), 5.5*(1-t*2), W0)
    spark(c, C, C, 6, r*0.6, r + 4*(1-t), W1 if t < .5 else W3, 0.4)
    if t < .3:
        for a in (0, math.pi/2, math.pi, math.pi*1.5):  # cross flash
            c.line(C, C, C+math.cos(a)*(13*(1-t*2)), C+math.sin(a)*(13*(1-t*2)), W0, 1)

def fx_cut(c, f):
    """a melee hit: two crossed strokes that whip through and fade"""
    t = f/(N-1)
    L = 6 + t*10
    for sgn, ph in ((1, 0.0), (-1, 0.25)):
        k = max(0.0, min(1.0, (t-ph)/0.7))
        if k <= 0: continue
        a = -0.7*sgn
        x0, y0 = C-math.cos(a)*L*k, C-math.sin(a)*L*k
        x1, y1 = C+math.cos(a)*L*k, C+math.sin(a)*L*k
        c.taper(x0, y0, x1, y1, W2, 5, 1)
        c.taper(x0, y0, x1, y1, W0 if k < .8 else W1, 2, 1)
    if t < .35: c.ellipse(C, C, 4*(1-t*2.4), 4*(1-t*2.4), W0)

def fx_nova(c, f):
    t = f/(N-1)
    r = 3 + t*13
    ring(c, C, C, r, W1 if t < .5 else W2, 3)
    ring(c, C, C, r-2, W0 if t < .35 else W2, 1)
    ring(c, C, C, max(1, r-6), W3, 1)
    spark(c, C, C, 8, r, r+3*(1-t), W2, 0.2)

def fx_frost(c, f):
    t = f/(N-1)
    r = 3 + t*11
    for k in range(6):                                 # six crystal spokes
        a = k/6*math.pi*2 + 0.2
        c.taper(C, C, C+math.cos(a)*r, C+math.sin(a)*r, W1, 4, 1)
        bx, by = C+math.cos(a)*r*0.62, C+math.sin(a)*r*0.62
        c.line(bx, by, bx+math.cos(a+0.9)*r*0.3, by+math.sin(a+0.9)*r*0.3, W2, 1)
        c.line(bx, by, bx+math.cos(a-0.9)*r*0.3, by+math.sin(a-0.9)*r*0.3, W2, 1)
    c.ellipse(C, C, 3.2*(1-t*0.6), 3.2*(1-t*0.6), W0)

FX = [('slash', fx_slash), ('arrow', fx_arrow), ('knife', fx_knife),
      ('shard', fx_shard), ('bolt',  fx_bolt),  ('orb',   fx_orb),
      ('star',  fx_star),  ('lance', fx_lance), ('bomb',  fx_bomb),
      ('note',  fx_note),  ('fire',  fx_fire),  ('sword', fx_sword),
      ('atom', fx_atom),
      ('burstatom', fx_burstatom),
      ('hit',   fx_hit),   ('cut',   fx_cut),   ('nova',  fx_nova),
      ('frost', fx_frost)]

def build():
    aw, ah = S*N, S*len(FX)
    px = [[None]*aw for _ in range(ah)]
    for r, (name, fn) in enumerate(FX):
        for f in range(N):
            c = Cv(S, S)
            fn(c, f)
            for y in range(S):
                for x in range(S):
                    px[r*S+y][f*S+x] = c.px[y][x]
    return px, aw, ah

def zoom(px, aw, ah, s, path):
    big = [[None]*(aw*s) for _ in range(ah*s)]
    for y in range(ah):
        for x in range(aw):
            v = px[y][x] or ('#241b38' if ((x//S)+(y//S)) % 2 else '#1b1428')
            for j in range(s):
                for i in range(s): big[y*s+j][x*s+i] = v
    write_png(path, big, aw*s, ah*s)
    print(path.split('/')[-1], '%dx%d' % (aw*s, ah*s))

def main():
    px, aw, ah = build()
    os.makedirs(os.path.join(ROOT, 'assets'), exist_ok=True)
    out = os.path.join(ROOT, 'assets', 'fx.png')
    write_png(out, px, aw, ah)
    print('fx %dx%d -> assets/fx.png (%d bytes)' % (aw, ah, os.path.getsize(out)))
    if 'preview' in sys.argv:
        zoom(px, aw, ah, 6, os.path.join(ROOT, 'tools', 'fxpreview.png'))
    uri = 'data:image/png;base64,' + base64.b64encode(open(out, 'rb').read()).decode()
    idx = os.path.join(ROOT, 'index.html')
    src = open(idx).read()
    import re
    new, n = re.subn(r'const FX_SRC = "[^"]*";', lambda _: 'const FX_SRC = "%s";' % uri, src)
    if n:
        open(idx, 'w').write(new)
        print('patched index.html (%d KB of fx)' % (len(uri)//1024))
    else:
        print('index.html has no FX_SRC -- add it, then re-run')

if __name__ == '__main__':
    main()
