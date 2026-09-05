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
| **Heads — TWIN FATE** | Your character **and** a second one |
| **Tails — SOLO** | Your character |

The same rule runs at **both rarities** — you pick a featured 5★ *and* a
featured 4★, and each is guaranteed when its tier hits.

The coin is never "did I lose?" — it's "do I get a bonus?"

You choose the featured character yourself on the Summon screen, and you can
change your pick any time before a pull.

### Rates
- 5★: 3% base, soft pity climbs from pull 25, **guaranteed at 35**
- 4★: 9% base, **guaranteed within 10**
- Every pull that is neither pays out **Echoes**. Bank 10 and spend them in
  Collection to raise *anyone's* **Resonance**, so no pull is ever wasted.
- Duplicate characters raise Resonance directly; at R6 they refund shards.

**Resonance is a real power curve, not a rounding error.** Each rank is +13%
attack and +9% HP, and **R6 pays a bonus on top** — a 30% attack spike and a
12% health spike:

| Resonance | Damage | Health |
|---|---|---|
| R0 | ×1.00 | ×1.00 |
| R3 | ×1.39 | ×1.27 |
| R5 | ×1.65 | ×1.45 |
| **R6** | **×2.31** | **×1.72** |

An R6 character hits for **more than double** what they did at R0. The Raid
is tuned assuming you have three of them.

---

## The roguelike

Build a squad of up to 3 and climb the Spire — **3 floors, 24 rooms, 3 bosses**
(The Undercroft, The Glass Tiers, The Crown). Death is permanent for that run,
but you keep the shards.

- **Move** WASD / arrows
- **Attack** automatic — you auto-target the nearest enemy in range. You face
  the way you're *moving*; the target only turns you when you stand still
- **Skill** `SPACE`
- **Second skill** `E` — each character's is completely different
- **Burst** `R` — charges as you deal and take damage, empties on use. A Burst
  never charges itself: damage dealt during one (and damage taken during it)
  is locked out of the meter, so you can't chain R into R
- **Dash** `SHIFT` (brief invulnerability)
- **Swap character** `1` `2` `3` — swapping bursts nearby enemies, and each
  character keeps their own HP and Burst meter for the whole run. When one is
  KO'd you drop to the next. All three down and the run is over.

Rooms come in five flavours: battle, elite, rest, treasure, boss. Each floor
ends on a boss and the enemies keep scaling, so floor 3 is a different game
from floor 1. After each fight you pick 1 of 3 **relics** — 20 of them,
stacking into some genuinely stupid builds (Split Volley + Piercing Shot +
Arc Current is a shotgun that clears the screen).

### Link Strikes

Land a **Skill** or a **Burst** and you tear an **opening** — a two-second
window, called out on screen. Swap during it and the incoming character does
not just walk on: they arrive with an **Entrance move** of their own, and it
hits for a multiplier.

| Chain | Damage |
|---|---|
| Link 1 | ×1.6 |
| Link 2 | ×2.1 |
| Link 3 | ×2.6 |

A chain runs **three links, once**. Each Entrance refreshes the window — but
only for a second, and each character can only come in once per chain — so
**1 → skill → 2 → 3** is a rotation you have to hit, not a button you can
lean on. Once the chain is spent, or the window closes, Link Strikes go on a
**nine-second cooldown** and no opening appears at all until it is back. A
Link Strike also ignores the swap cooldown and grants a moment of
invulnerability on arrival, so it doubles as an escape.

Every character has their own entrance: Aoi cuts in on a dash, Kagura drops a
star, Ren blinks behind them, Kassandra charges the lance in, Hinata arrives
healing and shielding, Suzume opens with a seven-arrow burst. Swapping with
**no** opening is just a normal swap — the combo counter stays at zero.

---

## The cast

Twenty-one characters across two rarities. They play completely differently.

### 5★

| | Character | Skill (SPACE) | Skill (E) | Burst (R) |
|---|---|---|---|---|
| ⚔ | **Aoi Shirogane** · Blade | Crescent Rush — dash through a line | **Iai Stance** — a parry. Get hit inside half a second and everything near her eats 650% instead | **Thousand Cuts** — 2s untouchable blender |
| ✦ | **Kagura Hoshimi** · Arcane | **Starfall** — eight stars called down onto wherever the enemies are | Astral Lance — piercing beam | **Constellation** — 24 homing stars |
| ☾ | **Ren Kurosawa** · Shadow | **Death Mark** — everything done to the target is banked, then 60% of it detonates | Shadowstep — blink and gut | **Nightfall** — cut everything 3× |
| ✚ | **Hinata Amakusa** · Radiant | Dawn Aegis — heal and shield | Judgment Ray — beam that heals you | **Rebirth Hymn** — revive a fallen ally |
| ➶ | **Suzume Ayakawa** · Gale | Split Volley — seven arrows | **Snare Arrows** — five broadheads planted in the ground | **Tempest Volley** — 3s of falling arrows |
| ⛊ | **Gorou Tachibana** · Stone | **Seismic Slam** — seven eruptions walking away from him, throwing things back | Bulwark — touching him hurts | **Unbreakable** — 5s untouchable |
| ❄ | **Yura Shinomiya** · Frost | Glacier Bloom — freeze the room | **Still Air** — every enemy at half speed for 4s. Not her | **Absolute Zero** — 4s deep freeze |
| ⚡ | **Kaito Amemiya** · Volt | **Thunder Chain** — jumps six bodies, 28% harder each jump | Thunder Rush — rocket forward | **Overcharge** — 6s double attack speed |

### 5★ — The Revenants

Heroes called back out of legend. Their own faction, their own naming, and the
sharpest kits in the game.

| | Character | Skill (SPACE) | Skill (E) | Burst (R) |
|---|---|---|---|---|
| ♛ | **Seryn Alba** · *The Sworn King* | Oathbreaker — one enormous arc | **Aegis of the Crown** — plants a standard: it burns them and feeds you shield and health | **Dawnbreaker** — a column of light across the arena |
| ⚔ | **Aldric Vane** · *The Faithless* | Broken Phantasm — nine thrown blades | **Unlimited Blade** — a projected sword hunts on its own for 9s | **Endless Armoury** — 3s of swords falling |
| ✹ | **Kassandra Rue** · *The Crimson Lance* | **Lance Dance** — 2.6s of spinning steel you can keep moving through | Crimson Thrust — a thrust that arrives as a line | **Thousand Thrusts** — five seconds of a maelstrom that drags them in |
| ☾ | **Nyx Morrow** · *The Ashen Witch* | **Rotwork** — infects one enemy; kill it infected and the rot jumps | **Witch's Tithe** — a tether that bleeds them and feeds her | **Grave Choir** — the whole screen catches it at once |

Kassandra **always crits anything under 40% HP**, and anything Nyx damages is
**Cursed** — it takes 15% more damage from *everyone* for 3s, so she sets the
squad up the way Yura does.

### No two of them play the same

Twenty-one characters, and **no two share a pair of skills.** There are
thirty-six distinct skill mechanics behind them and they are mechanics, not reskins: a
parry that turns a hit into a counter, a mark that banks damage and pays it out
in a lump, a chain that gets stronger with every body it jumps, a channelled
spin you can walk around inside, planted mines, planted standards, a tether that
drains, a plague that spreads on death, a vortex that drags a pack into one
place, called-down strikes, a projected sword that fights on its own, and a
field of slowed time, electron shells that cut, rounds that split and split
again, rage that scales with missing health, a hookshot that drags a target to
your feet, and a rifle that wants distance. Every character also has their own
passive and their own Burst — ten Bursts are unique shapes nobody else has.

### 5★ — The Second Wave

| | Character | Skill (SPACE) | Skill (E) | Burst (R) |
|---|---|---|---|---|
| ⚛ | **Atom Vale** · *The Half-Life* | **Orbital** — three electrons take up shells around him for 5s and cut everything they pass through, and he keeps fighting inside it | **Fission** — a round that splits in two when it lands, and each half splits again | **Critical Mass** — the shells collapse inward for 2.5s, then the room goes |
| ⚔ | **Rei Amagiri** · Blade | **Red Mind** — for 8s she hits harder the closer she is to dying, up to +140% at the edge | **Skyfall** — up, across, and down on top of whatever she picked | **Bloodtide** — 7s of double damage where every body in reach heals her |
| ✧ | **Odette Lune** · *The Marionettist* | **Three Sisters** — three puppets cut loose to hunt on their own for 8s | **Strung Up** — five enemies held in place, and held things take 40% more from everyone | **Curtain Call** — six puppets at once, and the room is strung up while they work |

Atom's passive is **Half-Life**: everything he damages keeps taking 25% of that
hit again over the next two seconds. Rei takes **25% less damage below half
health** — she is hardest to kill when she is nearly dead. Odette's puppets fire
25% faster and anything they kill leaves a mote that heals her.

### 4★

| | Character | Skill (SPACE) | Skill (E) | Burst (R) |
|---|---|---|---|---|
| ◎ | **Bao Xun** · Flame | **Called Shot** — pierces everything, up to 150% harder the further the target | **Flak** — eleven shells in a wall off the barrel | **Full Auto** — 3s of emptying everything he carries |
| ⟡ | **Iris Solene** · Gale | **Hookshot** — the hook goes out and something comes back | **Updraft** — everything nearby goes up, and the landing is what hurts | **Skydance** — 3 untouchable seconds, never landing twice in the same place |
| ⛏ | **Momo Tachibana** · Earth | **Glaive Cyclone** — drags everything into one point, then lets go | Earthshaker — huge sweep | **Landslide** — a maelstrom |
| 🔥 | **Chiyo Onodera** · Flame | **Bomblets** — four armed bombs rolled out ahead | Firewall — burning ground | **Firestorm** — 3s of called shells |
| ♪ | **Nari Tsukuda** · Radiant | Chorus Mote — a turret | Refrain — heal and shield | **Encore** — four motes, squad heal |
| ⌁ | **Toma Aida** · Volt | **Shock Chain** — leaps five bodies | **Ball Lightning** — thrown out, comes back, hits twice | **Thunderhead** — 3.4s of chaining |

Bao does **+50% damage past half a screen and 45% less at point blank** — he is
the one character you have to play backwards. Iris leaves a **cutting gust
behind every dash**.

Frozen enemies take **40% extra damage from anyone**, so Yura sets up everyone
else's damage.

---

## The Raid — The Sovereign

One fight, from the Home screen, separate from the Spire. No relics: just your
squad, their kits, and whether you can read what he's doing. **26,000 HP** and
**five minutes** before he enrages.

This fight assumes a **full R6 squad**. At R0 the damage simply is not there —
you will hit the enrage timer with him still standing.

- **Phase 1** — he summons **Wardens**, and takes **75% less damage** while
  any of them are standing. Kill them first or you are hitting a wall.
- **Phase 2** — the floor lights up in three of four quadrants and detonates.
  Stand in the one that stays dark.
- **The Grasp** — at any point he seizes whoever is standing in front of him.
  Four seconds later it detonates. The only way out is to **be someone else by
  then**, so swapping is a survival tool here, not just a damage rotation.
- **Phase 3** — he starts a **cast**. Deal 10% of his health during it and he
  **breaks**, taking 50% extra damage for five seconds. Fail and the whole
  squad loses 55% of its health. This is what your Bursts are for — hold them.

There is a **fourth phase**. Below 16% he stops pausing: the Last Rite summons
three Wardens on the spot and every mechanic comes back at roughly double the
cadence, with no idle time between them.

The Wardens **come back**: he re-summons them every 22 seconds from phase 2 on,
three at a time and four in the last phases, at 1,400 HP each, so the damage
gate is something you clear over and over, not once. He hits harder than anything in
the Spire, and a rotating beam sweeps throughout; it telegraphs thin before it
goes live.

Link Strikes are the answer to the gate. Skill into a swap into a swap and you
are doing triple damage exactly when the Wardens drop and the break window
opens.

**Reward: 30,000 shards** for a kill — enough for a serious run at the banner.
Losing still pays out on the damage you did.

---

## The art

Sprites are generated by `tools/make_sprites.py` — every frame is drawn pixel
by pixel in Python and packed into one atlas, which is embedded in
`index.html` as a data URI so the game stays a single file.

```
python3 tools/make_sprites.py            # rebuild the atlas and re-embed it
python3 tools/make_sprites.py closeup    # tools/closeup.png, heroes at 10x
python3 tools/make_sprites.py mobs       # tools/mobs.png, enemies at 8x
python3 tools/make_sprites.py preview    # tools/preview.png, whole sheet at 5x
```

Frames are 56×96. Characters have 18: idle (2), an 8-frame walk cycle, a
5-frame attack (coil, wind, strike, follow-through, settle), dash, cast and
hurt. Three attack frames made a swing that arrived without ever having been
thrown; the coil and the follow-through are what give it weight. Enemies have 10: idle (2), a
4-frame move cycle, telegraph, attack, special and hurt — so a brute visibly
rears back before it charges, and a slime squashes on landing.

Figures are drawn at roughly **4.7 heads**, which is anime proportions rather
than chibi: an 18px head on an 85px body, broad shoulders, a nipped waist,
and legs that are half the figure. The torso silhouette is a per-row
half-width table (`TORSO`), so reshaping the body is editing a list of
numbers.

**No two characters share a haircut, an outfit or a weapon.** Twenty-one
hairstyles — ponytail, curtain, spiky, bob, braid, crop, twin-tails, hime,
wolfcut, slicked-back with an undercut, waves, a bun, ringlets, a mohawk, a
circlet, a sidetail, messy, a topknot, floor-length, an undercut and a pixie —
each with its own fringe and its own ornament. Ten outfit shapes (skirt, robe,
dress, shorts, plate, jacket, apron, longcoat, wrap, coat) chosen **separately
from the hair**; keying the skirt off the hairstyle, which is what the code
used to do, is how three characters ended up dressed the same for no reason.
Twenty-one weapons, one each. A roster where three people share a haircut in
different colours reads as one character with palette swaps, which is the
opposite of what a gacha needs.

Twenty-one characters share one rig, and a pose is a **skeleton rather than a set of
absolute pixels**: `POSES` gives a body bounce, a lean, two foot positions
(offset from centre plus how far off the ground), and two hand positions
measured *from their own shoulder*. Limbs are drawn as two segments with a
knee or elbow pushed out perpendicular, so they bend instead of stretching,
and nothing can come apart at the joints when the body moves. Per-character
hair, outfit, sleeve length and weapon layer on top.

The walk is a real **eight-frame cycle** — contact, down, passing, up, then
the same four with the legs swapped — with the arms swinging opposite the
legs. Feet carry a **pitch** as well as a height: the toe lifts for the heel
strike, the whole sole plants, then the heel peels off and the toe is last to
leave the ground. That roll is most of what sells a walk. The knee folds up
hard while a leg is swinging through, so it bends rather than skating.

The head carries the **same forward turn the torso does**, and leads into each
step before settling as the weight lands. Without that shared turn the skull
sits back behind a turned body and the face reads as pointing somewhere the
shoulders and the legs are not — which looks exactly like a head that will not
turn with the body. One function, `head_pos`, decides where the head is, so the
face and the hair can never drift apart from each other or from the torso. Arms swing from a
shaded deltoid through an elbow cuff, legs carry a thigh mass and a lit knee,
so a limb reads as a limb rather than a tapered stick. The **trailing** arm and
leg are drawn in a dimmed copy of their own colours, which is what stops the
two legs merging into one column — and every character's leg palette is held
at a readable mid-tone against their outfit, so the cycle reads on a witch in
black robes as clearly as it does on Aoi.

**Attacks are pixel animations, drawn the same way the characters are.**
`tools/make_fx.py` generates a second atlas, `assets/fx.png`: eighteen effects,
eight frames each, at 32×32 — including a turning **atom** (nucleus and three
electron shells) for Atom Vale, and a tumbling **longsword** for Aldric, who
throws swords and should look like it. A slash sweeps its arc open, a lance extends out
of nothing, a knife tumbles end over end, lightning re-forks, a bomb's fuse
sputters, a flame's tip licks and curls. Impacts are animations too — a burst,
a crossed cut for melee, an expanding nova, a growing frost crystal.

The art is drawn in **luminance** — white core, darker greys outward — and
multiplied by the shot's colour when it is drawn, so one arc animation serves
Aoi's steel, Seryn's gold and Nyx's violet without three copies of it. Each
(effect, frame, colour) is tinted once and cached; the multiply pass is far
too expensive to redo per shot per frame.

```
python3 tools/make_fx.py            # rebuild assets/fx.png and re-embed it
python3 tools/make_fx.py preview    # tools/fxpreview.png, the sheet at 6x
```

Figures are drawn in **true side profile** facing +x, and mirrored for left.
This matters more than it sounds: a front-facing sprite that is merely
mirrored reads as facing *nowhere*, no matter how much the features are
nudged. In profile there is one visible eye, a nose on the leading edge, the
ear behind, a torso narrowed to about 62% of its head-on width, shoulders
close together, and hair and scarves trailing behind. **Hair masses behind
the head**, never curtaining down both sides: a symmetrical hairstyle — twin
ribbons, a fringe fanned across the whole crown, a curtain past each cheek —
is a head-on detail, and one of those alone is enough to make a figure read
as facing the camera while it walks the other way. Only a short lock in front
of the ear survives on the leading side. The shirt opening,
collar and trim run down the **front edge** of the torso rather than the
centre of the chest — a centred placket is a head-on detail and makes the
whole body read as square to the camera even when the head is not. Feet have
a toe forward and a heel behind. Press D and the head,
torso and legs all lead with the front of the body.

The **face is built from a contour**, not from circles. `HEAD_PROFILE` is a
per-row table of where the front and back edges of the head sit, one pixel row
at a time from the crown to the jaw: a forehead that rolls back, a brow ridge,
the dip under it, a nose, the cut back at the philtrum that is what actually
makes a nose read as a nose, two lips, and a chin that pulls in to the jaw. A
circle with a bump on it reads as a ball with a nose. Light comes from the
front, so the back of the skull turns into shadow, the temple behind the eye
darkens, and the jaw turns under.

The **eye is a wedge, not a box**: three rows deep at the outer corner, closing
to a point at the inner one, with the lid throwing a shadow across the top row,
the iris filling nearly the whole opening, a sliver of sclera at the front, a
pupil set back, a big catchlight high and back and a small one low and forward.
That vertical shading with a catchlight punched through it is what makes an eye
read as a wet sphere instead of something printed on. How *deep* the opening is
comes from the character — a wide round eye versus a narrow hard one is most of
what tells two of them apart at this size.

Every character has their own **face parameters** — eye width and depth, lash
thickness, whether the outer corner droops, brow shape and height, nose length,
mouth width — so Ren's narrow hard stare, Hinata's wide soft one and Gorou's
heavy brow are all the same code with different numbers. Mouths are
deliberately tiny, 1–2 pixels.

## Sending it to someone

`index.html` needs `assets/hit.mp3` next to it. To get a single file that
needs nothing at all:

```
python3 tools/build_standalone.py     # writes TwinFate.html
```

That bundles the sound in alongside the already-inlined sprite atlas, so the
result opens by double-clicking, with no internet and no sibling files.

## Technical notes

Single self-contained `index.html`: no dependencies, no bundler, no server
required. Combat renders at an internal 384×216 and integer-scales up so the
pixels stay square.

Progress saves to `localStorage` (`twinfate.save.v2`). "Erase Save" on the
title screen wipes it.

`assets/hit.mp3` is the impact sound; everything else is generated with the
Web Audio API at runtime.
