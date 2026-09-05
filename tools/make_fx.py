"""
Attack sprites for Twin Fate.

Every projectile and impact in the game is a pixel animation drawn here, frame
by frame, exactly the way the characters are - not a shape stroked onto the
canvas at runtime. Each effect is a ten-frame row in a 48x48 atlas.

What makes these read as animation rather than as a moving picture:

  * SMEAR. A fast thing is not a sharp thing in a different place. The leading
    edge stays crisp and the trailing edge is stretched and thinned, so the eye
    fills in the motion between frames.
  * ANTICIPATION AND OVERSHOOT. One-shot effects start small and tight, punch
    past their resting size, and settle back.
  * AN IMPACT FRAME. One frame of near-solid white at the moment of contact.
    It is on screen for a sixtieth of a second and it is most of the punch.
  * SECONDARY DETAIL. Sparks, embers and debris that outlive the thing that
    threw them, on their own timing.
  * SIX LUMINANCE TIERS instead of four, so the shading has somewhere to go.

Sprites are drawn in luminance - white core, progressively darker outward. The
game multiplies that by whatever colour the shot is, so one katana-arc serves
Aoi's steel, Seryn's gold and Nyx's violet without three copies of the artwork.

    python3 tools/make_fx.py          # rebuild assets/fx.png and re-embed it
    python3 tools/make_fx.py preview  # tools/fxpreview.png, the sheet at 4x
"""
import math, os, sys, base64

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_sprites import Cv, write_png

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S    = 48                      # frame size
N    = 10                      # frames per animation
C    = S/2.0                   # centre

# six tiers, not four - W0 is the core, W5 is the last thing before nothing
W0, W1, W2, W3, W4, W5 = '#ffffff', '#e4e4e4', '#c0c0c0', '#969696', '#6c6c6c', '#464646'
TIER = (W0, W1, W2, W3, W4, W5)

def shade(v):
    """0 at the core, 1 at the edge -> a tier"""
    return TIER[max(0, min(5, int(v*6)))]

def arc(c, cx, cy, r, a0, a1, col, thick=1):
    n = max(10, int(abs(a1-a0)*r*2.2))
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

def blob(c, cx, cy, r, tiers=(0.0, 0.45, 0.75, 1.0)):
    """a soft round mass, shaded from the middle out"""
    for y in range(int(cy-r)-1, int(cy+r)+2):
        for x in range(int(cx-r)-1, int(cx+r)+2):
            d = math.hypot(x-cx, y-cy)/max(0.01, r)
            if d <= 1.0: c.set(x, y, shade(d*0.9))

def smear(c, x0, y0, x1, y1, w0, w1, tier0=0.0, tier1=0.75):
    """the shape a fast thing leaves: crisp and wide at the head, stretched and
       thin at the tail. This is the single biggest difference between a sprite
       that moves and a sprite that looks like it is moving."""
    n = int(max(abs(x1-x0), abs(y1-y0))*2) + 2
    for i in range(n+1):
        t = i/n
        x, y = x0+(x1-x0)*t, y0+(y1-y0)*t
        w = w0 + (w1-w0)*t
        for j in range(int(-w), int(w)+1):
            v = abs(j)/max(1.0, w)
            c.set(x, y+j, shade(tier0 + (tier1-tier0)*t + v*0.35))

# ------------------------------------------------------------- projectiles ---
def fx_slash(c, f):
    """a blade arc: it opens out of nothing, overshoots, and thins away"""
    t = f/(N-1)
    if f == 0:                                       # anticipation, one frame
        arc(c, C-8, C, 9, -0.5, 0.5, W3, 2); return
    e = (t-0.1)/0.9
    r = 10 + e*11 - (0 if e < .7 else (e-.7)*8)      # punch past, then settle
    span = 1.45 - e*0.6
    mid  = -0.12 + e*0.34
    for k in range(5):                               # the trailing smear
        s = k/5.0
        arc(c, C-6, C, r - s*4, mid-span*(1-s*.3), mid+span*(1-s*.5),
            shade(0.25 + s*0.6), 3)
    arc(c, C-6, C, r,   mid-span*.9,  mid+span*.9,  W1, 3)
    arc(c, C-6, C, r-1, mid-span*.55, mid+span*.55, W0, 2)
    a = mid+span                                     # the tip, sharpest of all
    c.line(C-6+math.cos(a)*r, C+math.sin(a)*r,
           C-6+math.cos(a)*(r+4), C+math.sin(a)*(r+4), W0, 1)
    if f in (1, 2):                                  # the impact frame
        arc(c, C-6, C, r, mid-span*.7, mid+span*.7, W0, 5)

def fx_smearline(c, f):
    """pure motion: a wide flat streak that thins and fades. Layered behind a
       melee hit it does what a two-frame swing cannot do on its own."""
    t = f/(N-1)
    L = 16 + t*8
    w = 7*(1-t*0.85)
    if w < 0.6: return
    smear(c, C+L*0.5, C, C-L*0.9, C, w, w*0.25, 0.05 + t*0.4, 0.9)
    for k in range(3):
        c.set(C+L*0.55+k, C + (k-1), shade(t*0.5))

def fx_arrow(c, f):
    wob = math.sin(f/N*math.pi*2)*1.8
    smear(c, C-16, C+wob*.4, C+4, C, 2.0, 3.0, 0.75, 0.15)   # the trail
    c.taper(C-10, C+wob*.2, C+7, C, W1, 4, 3)                # shaft
    c.taper(C-9,  C+wob*.2, C+6, C, W0, 2, 1)
    for dx, dy in ((9,0),(8,-1),(8,1),(11,0),(13,0),(6,-3),(6,3),(7,-2),(7,2)):
        c.set(C+dx, C+dy, W0)                                # head
    c.set(C+14, C, W0); c.set(C+15, C, W2)
    for k in range(5):                                       # fletching, fluttering
        c.set(C-13+k, C-3+wob*.6 + k*.5, W1)
        c.set(C-13+k, C+3+wob*.6 - k*.5, W1)
        c.set(C-13+k, C-2+wob*.6 + k*.5, W3)

def fx_knife(c, f):
    a = f/N*math.pi*2
    ux, uy = math.cos(a), math.sin(a)
    nx, ny = -uy, ux
    for k in range(8):                                       # the tumble smear
        b = a - k*0.18
        c.set(C+math.cos(b)*11, C+math.sin(b)*11, shade(0.35+k*0.08))
        c.set(C-math.cos(b)*9,  C-math.sin(b)*9,  shade(0.45+k*0.08))
    def put(k, j, col): c.set(C+ux*k+nx*j, C+uy*k+ny*j, col)
    for k in range(-5, 15):
        h = max(0.0, 3.6 * (1 - max(0, k-2)/12.0))
        for j in range(int(-h), int(h)+1):
            put(k, j, shade(abs(j)/max(1, h)*0.7))
        put(k, -h, W0)
    for k in range(-12, -5):
        for j in (-1, 0, 1): put(k, j, W4)
    for j in range(-6, 7): put(-5, j, W2)
    for j in (-6, 6): put(-5, j, W1)
    put(15, 0, W0); put(16, 0, W1)

def fx_shard(c, f):
    p = 1 + math.sin(f/N*math.pi*2)*0.2
    smear(c, C-4, C, C-17, C, 3.5, 1.0, 0.5, 0.95)
    for k in range(-7, 14):
        h = max(0, 6 - abs(k-2)*0.62) * p
        for j in range(int(-h), int(h)+1):
            c.set(C+k, C+j, shade(abs(j)/max(1,h)*0.75 + 0.05))
    for k in range(-2, 11): c.set(C+k, C, W0)
    c.set(C+14, C, W0); c.set(C+15, C, W2)
    if f % 3 == 0:
        c.set(C+3, C-6, W0); c.set(C+6, C+4, W0); c.set(C-1, C+5, W1)

def fx_bolt(c, f):
    fork = f % 5
    xs = [-17, -11, -5, 1, 7, 15]
    ys = [0, -6, 5, -4, 3, 0]
    def pts():
        return [(C+xs[i], C+ys[i]*(1 if (i+fork) % 2 else -0.65)) for i in range(len(xs))]
    P = pts()
    for w, col in ((5, W4), (4, W3), (3, W1), (1, W0)):
        for i in range(len(P)-1):
            c.line(P[i][0], P[i][1], P[i+1][0], P[i+1][1], col, w)
    if fork % 2 == 0:
        c.line(C, C, C+6, C-11, W2, 2); c.line(C, C, C+6, C-11, W0, 1)
    if fork == 1:
        c.line(C-5, C+2, C-11, C+9, W2, 2)
    spark(c, C, C, 7, 12, 16 + (f % 4), W4, f*0.7)
    if f in (0, 5): blob(c, C, C, 5)                         # the strike frame

def fx_orb(c, f):
    p = 1 + math.sin(f/N*math.pi*2)*0.2
    smear(c, C-3, C, C-15, C, 4.0, 1.2, 0.55, 0.95)
    blob(c, C, C, 8.0*p)
    blob(c, C-1, C-1, 4.0*p, )
    c.ellipse(C-1.4, C-1.4, 2.0, 2.0, W0)
    a = f/N*math.pi*2
    for k, rr in ((0, 12), (2.1, 13), (4.2, 11)):
        c.ellipse(C+math.cos(a+k)*rr, C+math.sin(a+k)*rr, 1.8, 1.8, W0)
        c.ellipse(C+math.cos(a+k+.4)*rr, C+math.sin(a+k+.4)*rr, 1.0, 1.0, W3)

def fx_star(c, f):
    a0 = f/N*math.pi*2*0.5
    for k in range(5):                                       # the smear behind it
        b = a0 - k*0.14
        for j in range(5):
            a = b + j/5*math.pi*2
            c.set(C+math.cos(a)*11, C+math.sin(a)*11, shade(0.5+k*0.09))
    for k in range(5):
        a = a0 + k/5*math.pi*2
        c.taper(C, C, C+math.cos(a)*14, C+math.sin(a)*14, W3, 7, 1)
    for k in range(5):
        a = a0 + k/5*math.pi*2
        c.taper(C, C, C+math.cos(a)*11, C+math.sin(a)*11, W1, 4, 1)
    blob(c, C, C, 5.0)
    c.ellipse(C-1, C-1, 1.8, 1.8, W0)

def fx_lance(c, f):
    ext = min(1.0, (f+1)/3.5)
    over = 1 + (0.14 if f in (3, 4) else 0)                  # it overshoots
    L = int(20*ext*over)
    for k in range(-L, L+1):
        h = 6.0 * (1 - abs(k)/(L+3.0))
        for j in range(int(-h), int(h)+1):
            c.set(C+k, C+j, shade(abs(j)/max(1,h)*0.8))
    for k in range(-L, L+1): c.set(C+k, C, W0)
    if L > 5:
        for k in range(3): c.set(C+L+1+k, C, TIER[k])
        for j in (-1, 1): c.set(C+L, C+j, W0)
    spark(c, C, C, 6, L*0.6, L+4, W4, f*0.9)
    if f in (2, 3): 
        for k in range(-L, L+1): c.set(C+k, C-1, W0); c.set(C+k, C+1, W1)

def fx_bomb(c, f):
    blob(c, C-2, C+2, 8.0)
    c.ellipse(C-4, C-1, 2.6, 2.4, W0)
    c.rect(C-1, C-10, 3, 4, W4)
    fz = math.sin(f/N*math.pi*2)*3
    c.line(C, C-12, C+5+fz*0.5, C-18, W3, 1)
    r = 2.2 + (f % 3)*1.0
    blob(c, C+6+fz*0.5, C-19, r)
    spark(c, C+6+fz*0.5, C-19, 6, r, r+4, W2, f)
    for k in range(3):                                       # embers falling off
        c.set(C+6+fz*0.5 + math.sin(f+k)*5, C-16+k*3, TIER[k+1])

def fx_note(c, f):
    bob = math.sin(f/N*math.pi*2)*2.2
    blob(c, C-5, C+6+bob, 5.4)
    c.ellipse(C-6, C+5+bob, 2.6, 2.0, W0)
    c.rect(C-1, C-10+bob, 3, 17, W1)
    c.rect(C-1, C-10+bob, 1, 17, W0)
    c.taper(C+2, C-10+bob, C+10, C-4+bob, W2, 4, 2)
    for k in range(4):
        c.set(C+11+k*2, C-12+bob + k*1.5, TIER[k+1])

def fx_fire(c, f):
    lick = f/N*math.pi*2
    for k in range(-13, 16):
        t = (k+13)/28.0
        wob = math.sin(lick + t*3.6)*3.4*t
        h = 8.6*math.sin(min(1.0, (t+0.10))*math.pi*0.94)
        if h <= 0: continue
        for j in range(int(-h), int(h)+1):
            c.set(C+k, C+j+wob, shade(abs(j)/h*0.85))
        c.set(C+k, C-h+wob, W4); c.set(C+k, C+h+wob, W5)
    for k in range(5):
        e = C - 16 - k*3
        c.set(e, C + math.sin(lick + k*1.4)*7, TIER[min(5, k+1)])

def fx_sword(c, f):
    a = f/N*math.pi*2
    ux, uy = math.cos(a), math.sin(a)
    nx, ny = -uy, ux
    for k in range(7):                                       # tumble smear
        b = a - k*0.16
        c.set(C+math.cos(b)*16, C+math.sin(b)*16, shade(0.4+k*0.09))
        c.set(C-math.cos(b)*14, C-math.sin(b)*14, shade(0.5+k*0.08))
    def put(k, j, col): c.set(C+ux*k+nx*j, C+uy*k+ny*j, col)
    for k in range(-6, 20):
        h = 4.0 if k < 15 else max(0.0, 4.0 - (k-15)*0.9)
        for j in range(int(-h), int(h)+1):
            put(k, j, shade(abs(j)/max(1,h)*0.7))
        put(k, -h, W0)
    for k in range(-16, -6):
        for j in (-1, 0, 1): put(k, j, W4)
    put(-17, 0, W3)
    for j in range(-7, 8): put(-6, j, W3)
    for j in (-7, 7): put(-6, j, W1)
    put(20, 0, W0)

def fx_atom(c, f):
    a0 = f/N*math.pi*2
    for k, tilt in enumerate((0.0, 1.05, 2.10)):
        ca, sa = math.cos(tilt), math.sin(tilt)
        for i in range(56):
            t = i/56*math.pi*2
            ex, ey = math.cos(t)*16, math.sin(t)*6.2
            c.set(C + ex*ca - ey*sa, C + ex*sa + ey*ca, W3)
        for tr in range(4):                                  # the electron smears
            t = a0 + k*2.1 - tr*0.22
            ex, ey = math.cos(t)*16, math.sin(t)*6.2
            px, py = C + ex*ca - ey*sa, C + ex*sa + ey*ca
            if tr == 0: blob(c, px, py, 3.2)
            else: c.set(px, py, shade(0.4+tr*0.15))
    blob(c, C, C, 6.0)
    c.ellipse(C-1, C-1, 2.0, 2.0, W0)

def fx_burstatom(c, f):
    t = f/(N-1)
    if t < 0.55:
        k = 1 - t/0.55
        for j, tilt in enumerate((0.0, 1.05, 2.10)):
            ca, sa = math.cos(tilt), math.sin(tilt)
            for i in range(64):
                u = i/64*math.pi*2
                ex, ey = math.cos(u)*(5+16*k), math.sin(u)*(2+6.2*k)
                c.set(C + ex*ca - ey*sa, C + ex*sa + ey*ca, W2 if j == 0 else W4)
        blob(c, C, C, 4+7*(1-k))
    else:
        k = (t-0.55)/0.45
        if k < 0.18: blob(c, C, C, 22)                       # the impact frame
        ring(c, C, C, 5 + k*19, W0 if k < .5 else W2, 4)
        ring(c, C, C, 3 + k*13, W1, 3)
        ring(c, C, C, max(1, 1+k*8), W3, 2)
        spark(c, C, C, 14, 5+k*11, 8+k*19, W3, 0.3)
        blob(c, C, C, max(0.5, 9*(1-k)))

# ------------------------------------------------------------- impacts -------
def fx_hit(c, f):
    t = f/(N-1)
    if t < 0.14: blob(c, C, C, 13); return                   # the impact frame
    r = 3 + t*18
    ring(c, C, C, r, W1 if t < .5 else W3, 3)
    ring(c, C, C, r-2, W0 if t < .35 else W2, 2)
    if t < 0.5: blob(c, C, C, 8*(1-t*2))
    spark(c, C, C, 8, r*0.5, r + 7*(1-t), W2 if t < .5 else W4, 0.4)
    for k in range(6):                                       # debris, on its own clock
        a = 0.7 + k*1.05
        d = 6 + t*24 + k
        c.set(C+math.cos(a)*d, C+math.sin(a)*d + t*t*8, shade(t*0.8))

def fx_cut(c, f):
    """a melee hit: two crossed strokes that whip through, with real smear"""
    t = f/(N-1)
    if t < 0.12: blob(c, C, C, 11); return
    L = 9 + t*15
    for sgn, ph in ((1, 0.0), (-1, 0.22)):
        k = max(0.0, min(1.0, (t-ph)/0.72))
        if k <= 0: continue
        a = -0.72*sgn
        x0, y0 = C-math.cos(a)*L*k, C-math.sin(a)*L*k
        x1, y1 = C+math.cos(a)*L*k, C+math.sin(a)*L*k
        smear(c, x1, y1, x0, y0, 4.5*(1-k*.5), 1.0, 0.05, 0.8)
        c.line(x0, y0, x1, y1, W0 if k < .8 else W2, 1)
    if t < 0.4: blob(c, C, C, 6*(1-t*2.2))

def fx_nova(c, f):
    t = f/(N-1)
    if t < 0.1: blob(c, C, C, 15); return
    r = 4 + t*19
    ring(c, C, C, r, W0 if t < .4 else W2, 4)
    ring(c, C, C, r-3, W1 if t < .6 else W3, 2)
    ring(c, C, C, max(1, r-9), W4, 2)
    spark(c, C, C, 12, r, r+5*(1-t), W3, 0.2)
    for k in range(8):                                       # a ring of dust behind it
        a = k/8*math.pi*2 + 0.4
        c.set(C+math.cos(a)*(r-6), C+math.sin(a)*(r-6)*0.6, shade(0.4+t*0.5))

def fx_frost(c, f):
    t = f/(N-1)
    if t < 0.1: blob(c, C, C, 10); return
    r = 4 + t*16
    for k in range(6):
        a = k/6*math.pi*2 + 0.2
        c.taper(C, C, C+math.cos(a)*r, C+math.sin(a)*r, W1, 5, 1)
        bx, by = C+math.cos(a)*r*0.6, C+math.sin(a)*r*0.6
        for sgn in (1, -1):
            c.line(bx, by, bx+math.cos(a+sgn*0.9)*r*0.34,
                   by+math.sin(a+sgn*0.9)*r*0.34, W2, 1)
        tx, ty = C+math.cos(a)*r*0.86, C+math.sin(a)*r*0.86
        for sgn in (1, -1):
            c.line(tx, ty, tx+math.cos(a+sgn*0.9)*r*0.2,
                   ty+math.sin(a+sgn*0.9)*r*0.2, W3, 1)
    blob(c, C, C, 4.6*(1-t*0.5))

def fx_shock(c, f):
    """a ground shockwave: a flat ellipse that races out and a lip of dust"""
    t = f/(N-1)
    if t < 0.1: blob(c, C, C, 12); return
    r = 5 + t*20
    for k in range(3):
        rr = r - k*2.5
        if rr <= 0: continue
        for i in range(80):
            a = i/80*math.pi*2
            c.set(C+math.cos(a)*rr, C+math.sin(a)*rr*0.42, TIER[min(5, k + int(t*3))])
    for k in range(10):
        a = k/10*math.pi*2 + 0.3
        d = r*0.8
        c.set(C+math.cos(a)*d, C+math.sin(a)*d*0.42 - t*7, shade(0.3+t*0.6))

def fx_beam(c, f):
    """a thick beam segment: solid core, soft falloff, and it flickers"""
    t = f/(N-1)
    w = 9 - abs(math.sin(f*1.7))*1.6
    for k in range(-24, 25):
        for j in range(int(-w), int(w)+1):
            c.set(C+k, C+j, shade(abs(j)/w*0.9))
        c.set(C+k, C, W0)
        if k % 6 == int(f*2) % 6: c.set(C+k, C-1, W0); c.set(C+k, C+1, W0)
    spark(c, C, C, 8, w+2, w+7, W4, f*0.5)

FX = [('slash', fx_slash), ('smearline', fx_smearline), ('arrow', fx_arrow),
      ('knife', fx_knife), ('shard', fx_shard), ('bolt', fx_bolt),
      ('orb', fx_orb), ('star', fx_star), ('lance', fx_lance),
      ('bomb', fx_bomb), ('note', fx_note), ('fire', fx_fire),
      ('sword', fx_sword), ('atom', fx_atom), ('burstatom', fx_burstatom),
      ('hit', fx_hit), ('cut', fx_cut), ('nova', fx_nova), ('frost', fx_frost),
      ('shock', fx_shock), ('beam', fx_beam)]

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
        zoom(px, aw, ah, 4, os.path.join(ROOT, 'tools', 'fxpreview.png'))
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
