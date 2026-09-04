# Twin Fate

A pixel anime **roguelike gacha** where the 50/50 never loses.

Open `index.html` in a browser. That's it — no install, no build step.

---

## The gacha twist

Normal gacha: you hit a 5★, then a coin flip decides whether you get the
character you wanted *or* some random one instead. Half the time you lose.

**Twin Fate doesn't do that.**

Every 5★ you pull **is the character you picked**. Guaranteed, every time.
Then the coin flips:

| Result | What you get |
|---|---|
| **Heads — TWIN FATE** | Your character **and** a second random 5★ |
| **Tails — SOLO** | Your character |

So the coin is never "did I lose?" — it's "do I get a bonus?" The same rule
runs at 4★ with your chosen 4★ featured character.

You choose the featured 5★ and 4★ yourself on the Summon screen, and you can
change them any time before a pull.

### Rates
- 5★: 1.5% base, soft pity climbs from pull 40, **guaranteed at 50**
- 4★: 8% base, **guaranteed within 10**
- Duplicates become **Resonance** (up to R6): +8% HP and ATK each. At R6 a dupe
  refunds Fate Shards instead.

---

## The roguelike

Build a squad of up to 3 and descend the Spire — 12 rooms, two bosses, and
death is permanent for that run (you keep the shards).

- **Move** WASD / arrows
- **Attack** automatic — you auto-target the nearest enemy in range
- **Skill** `SPACE`
- **Dash** `SHIFT` (brief invulnerability)
- **Swap character** `1` `2` `3` — swapping bursts nearby enemies, and each
  character keeps their own HP for the whole run. When one is KO'd you drop to
  the next. All three down and the run is over.

Rooms come in five flavours: battle, elite, rest, treasure, boss. After each
fight you pick 1 of 3 **relics** — 20 of them, stacking multiplicatively into
some genuinely stupid builds (Split Volley + Piercing Shot + Arc Current is a
shotgun that clears the screen).

Shards earned in a run are permanent. Spend them on the character you actually
want, which is the whole point.

---

## The roster

16 characters across 3 rarities, each with their own skill and passive:

**5★** — Aoi Shirogane (dash duelist), Kagura Hoshimi (piercing mage),
Yuki Nagumo (freeze control), Ren Kurosawa (crit assassin),
Hinata Amakusa (healer), Tsubaki Ryuen (burn bruiser)

**4★** — Momo, Sora, Rui, Chiyo, Kaede, Nari &nbsp;·&nbsp; **3★** — Kiri, Bun, Sasa, Toma

---

## Technical notes

Single self-contained `index.html`: no dependencies, no bundler, no server
required. Sprites are hand-authored 16×16 pixel maps recolored per character
at runtime and cached to offscreen canvases. Combat renders at an internal
384×216 and integer-scales up so the pixels stay square.

Progress saves to `localStorage` (`twinfate.save.v2`). "Erase Save" on the
title screen wipes it.

`assets/hit.mp3` is the impact sound; everything else is generated with the
Web Audio API at runtime.
