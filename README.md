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

And the coin **lands on the side you actually got**. It used to spin exactly
eight whole turns every time, which meant it always came to rest showing TWIN
no matter what the result was; the losing flip now has its own keyframes that
stop half a turn further round.

The same rule runs at **both rarities** — you pick a featured 5★ *and* a
featured 4★, and each is guaranteed when its tier hits.

The coin is never "did I lose?" — it's "do I get a bonus?"

You choose the featured character yourself on the Summon screen, and you can
change your pick any time before a pull.

### Tuning

Every character carries a **signature perk**, and owning the character unlocks
it. Every character also has **two tuning slots**, and any perk you have
unlocked goes in either one — on anybody. The picker only lists perks you have
actually unlocked, so early on the list looks short; that is the roster you own,
not a gap in the perks. Ren's Killing Intent on a
sniper, Gorou's Stonehide on a glass cannon, Shion's Pack Bond on Odette's
puppets. That is what makes a 4★ you will never play worth pulling: you are not
only collecting a character, you are collecting a part.

**Fifty-six perks — one per character, every single one of them**, and they are real mechanics rather than
flat numbers — a regen tick, a chain to a second enemy, a shield on arrival, an
execute threshold, a nullified hit every nine seconds, summons that live half
again as long. Set them in **Collection**; the same perk cannot go in both slots
of one character, but it can be fitted to as many different characters as you
like.

### Balance

The roster is tuned against a measurement, not a feeling. `tools`-side there is
a headless harness that runs **every character for thirty seconds** against a
stationary single target and a pack of five, at the range their kit was built
for, plus a healing-throughput trial for the ones whose job is not damage. The
first time it ran, the spread was **27x** from top to bottom.

Most of that came from the Burst meter, which filled purely on damage dealt —
so it paid the characters who least needed paying, and the gap compounded every
fight. Most of the meter is now a **steady trickle everybody gets at the same
rate**, with damage only topping it up. A Burst is a few times a fight for
everyone rather than a rotation for half the roster and a rarity for the other
half.

The rest was per-character: damage numbers scaled toward a target band from the
measurements, and the handful of kits that were structurally rather than
numerically over-tuned changed by hand. Top to bottom is now about **5x**, most
of the roster sits between 180 and 400 pack dps, and the two dedicated healers
trade damage for the best throughput in the game.

### One ultimate at a time

A Burst is on a **twenty-second floor, per player**. The meter still says when
you have earned one; this says how often anyone is allowed to have one at all.
It is deliberately per *player* rather than per character — swapping to the next
member of the squad and firing theirs is exactly the thing the floor exists to
stop — and it runs on real seconds, so it keeps counting down while you are on
the map screen between rooms. A Burst you cannot fire yet is **not spent**: the
meter stays full and the button shows the seconds left.

Both raids keep their full health pools — twenty seconds is short enough that
a squad still gets a comparable number of ultimates into a five-minute fight.

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

## Two players, one keyboard

**Versus** is on the Home screen. Pick a character each and fight; first one
down loses, and there is a rematch button.

- **P1** — WASD to move, `SPACE` skill, `E` second skill, `R` Burst, `LEFT SHIFT` dash, `1 2 3` swap.
- **P2** — arrow keys to move, `,` skill, `.` second skill, `/` Burst, `RIGHT SHIFT` dash, `8 9 0` swap.

Those are only the defaults. **Settings** on the Home screen rebinds all
eleven actions for **both** players — click a key, press the one you want.
Binding a key that is already in use makes the two **trade places** rather
than clearing one, so no action is ever left unbound, and `ESC` cancels a
capture. Only the keys you actually changed are saved, so a future change to
the defaults still reaches anyone who never touched that one.

The pads the combat loop reads are **built from the save**, not written down,
and every keycap the game draws — the four on each player's panel, the numbers
on their character portraits, the control lines on the co-op and versus
screens, the toast when player two joins — is painted from that same table. The
screen cannot disagree with the keyboard.

### Co-op

**Co-op** is the other half of it, on the same Home screen: two players on one
keyboard, on the *same* side, through the **Spire or either raid**. Player one
brings the squad set in SQUAD; player two picks their own three on the co-op
screen. Both get their own three characters to swap between, their own health,
their own Bursts, their own perks and their own Link Strikes — two full squads
in one fight.

Turn it on and player two is simply already there when a fight starts. Leave it
off and they can still **drop into any fight** at any moment with `J`, taking
the best three characters you own that player one is not using.

Each player owns their own corner of the screen: four keycaps along the bottom
carrying **their** skill names, **their** cooldowns and **their** Burst meter,
with **their** three characters sitting above them. P1 keeps the left, P2 takes
the right. Either player changes character mid-fight from their own bar —
`1 2 3` and `8 9 0` respectively, or by clicking a portrait. Versus gets the
same mirrored readout, so player two is never guessing at what their own
cooldowns are doing.

Two things fall out of a second player that need saying:

- **Rooms and bosses carry 70% more health in co-op.** Two people is roughly
  twice the damage, and the Sovereign's five-minute timer is calibrated for
  one. Co-op is not the easy mode; it is the same fight, sized for two.
- **While one of you is standing, the other gets picked back up.** A wiped
  squad comes back on its first character at 35% health, once every 45
  seconds. The fight is only lost when **both** players are down at the same
  time — so holding the room alone while your partner's timer runs is a real
  thing you will end up doing.

Enemies chase whichever of you is closer, so the two roles sort themselves out
without any co-op-specific AI. The raid's arena mechanics know about both of
you too: the sweeping arm cuts whoever is in its line, the dark quarter is dark
for both of you, an unbroken cast takes 55% off **both** squads, and the Grasp
picks one of you at random — so "GRASPED — SWAP OUT" is sometimes player two's
problem, and both people need to know their own swap keys.

Every ability works in Versus without a single special case, because each
player carries a **stand-in inside the enemy list**. Every skill already knows
how to hurt a mob; the stand-in just forwards what it takes to the person it
belongs to. Two people also means two heroes being stepped each frame, and each
is installed as *the* hero while it is being stepped — that is how they both get
their own stats, perks and passives without threading a parameter through two
thousand lines.

## The roguelike

Build a squad of up to 3 and climb the Spire — **3 floors, 24 rooms, 3 bosses**
(The Undercroft, The Glass Tiers, The Crown). Death is permanent for that run,
but you keep the shards.

- **Move** WASD / arrows
- **Attack** `V` — **on a button, and it chains.** Press it and you swing at the
  nearest enemy in range; press again inside half a second and the second hit
  lands wider; a third time and you get a finisher that hits for 185%, knocks
  the room back and slows what it touches. Miss the window and the string
  starts again. A press during the recovery is *buffered*, not eaten, so
  pressing slightly early still lands. Three pips under the keycap show where
  in the string you are. You still face the way you're *moving*; the target
  only turns you when you stand still
- **Attack, the old way** — automatic, swinging by itself whenever something is
  in range. **Settings → Attack** flips between the two and nothing else
  changes; this is a switch, not a rewrite. Player two gets their own attack
  key (`;` by default) and their own independent string
- **Skill** `SPACE`
- **Second skill** `E` — each character's is completely different
- **Burst** `R` — charges as you deal and take damage, empties on use. A Burst
  never charges itself: damage dealt during one (and damage taken during it)
  is locked out of the meter, so you can't chain R into R
- **Dash** `SHIFT` (brief invulnerability)
- **Swap character** `1` `2` `3` — swapping bursts nearby enemies, and each
  character keeps their own HP and Burst meter for the whole run. When one is
  KO'd you drop to the next. All three down and the run is over.

### Duels

Four rooms a run are a **Duel**: one character-sized boss, an empty arena, and
nothing else. A Rival *is* a character — Aoi the Unsheathed, Toya the
Limitless, Boro the Siege Engine — with that character's sprite, that
character's element, and **that character's actual moves**, on longer
cooldowns and hitting a great deal harder. It holds its own range, circles
rather than standing still, names the move it is about to throw, and then
throws it: Ren blinks behind you and marks you, Kassandra pins you on the end
of the lance, Boro walks mortars across the floor, Zephyra throws fans that
come back.

They are not scripted patterns. The rival runs the **real skill code** — the
same `castSkill` your characters use, all hundred and ten mechanics of it. The
trick is the one Versus already pays for: while a rival casts, the players are
installed as its enemy list, so every skill in the game works pointed the other
way without a single special case, and whatever the cast leaves behind (a shot,
a burning field, a called strike, work deferred half a second) is stamped as
the rival's on the way out. A move that moves the caster moves the *rival* —
blinks and lunges are real.

Twelve of them, never the same one twice in a run. Against a headless squad
that plays properly they take **twenty to seventy seconds** and cost between a
tenth and three quarters of the squad's health.

Rooms come in six flavours: battle, elite, **duel**, rest, treasure, boss. Each floor
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

Fifty-six characters across two rarities. They play completely differently.

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

Fifty-six characters, and **no two share a single move.** There are
**a hundred and ten distinct skill mechanics and fifty-six distinct Bursts**
behind fifty-six characters — a hundred and sixty-eight slots in total, with
only two duplicates in the whole roster, and both of those are deliberate:
Shion's two hounds and Jinya's two named shadows are the same mechanic tuned
differently on purpose, because a matched pair *is* the character.

That was not true until recently. Sixteen slots used to point at a mechanic
another character already owned — Kaito's second skill was Aoi's dash, Kassandra's
thrust was Kagura's beam, Jun's leap was Rei's, Vex's rifle was Bao's. They were
the same move in a different colour, which is the one thing a roster this size
cannot afford. Every one of them was rebuilt into something only that character
does: a rail of current you plant your feet for, a lance that pins and carries on
into the next body when the first one dies, a saw that holds one enemy still and
feeds on it, two rounds and a bet that pays the whole cooldown back if either
crits.

They are mechanics, not reskins: a
parry that turns a hit into a counter, a mark that banks damage and pays it out
in a lump, a chain that gets stronger with every body it jumps, a channelled
spin you can walk around inside, planted mines, planted standards, a tether that
drains, a plague that spreads on death, a vortex that drags a pack into one
place, called-down strikes, a projected sword that fights on its own, and a
field of slowed time, electron shells that cut, rounds that split and split
again, rage that scales with missing health, a hookshot that drags a target to
your feet, a rifle that wants distance, a rifle that wants distance, summoned hounds and shadow soldiers that hunt on
their own, a dice roll, a pact paid in blood, a singularity, a repulsion that
deletes bullets, a punch that lands twice, a strike that is always a crit, a net that pins
a pack in place, a stone that skips between four heads getting heavier each
time, a sheathed stance that answers the whole room in ice, eight blade shards
that break enemy bullets out of the air, and a cannon whose recoil moves the
person firing it. Every character also has their own
passive and their own Burst — every one of the fifty-six Bursts is a shape
nobody else has.

### 5★ — The Third Wave

| | Character | Skill (SPACE) | Skill (E) | Burst (R) |
|---|---|---|---|---|
| ◍ | **Suimu** · *The Devourer* | **Predation** — everything near him under 30% health is eaten, and he keeps what he eats: +8% damage a meal | **Split Off** — a piece of him walks away and hunts on its own | **Body Split** — three of him at once, and the original heals a quarter of his health |
| 🜂 | **Homura Akatsuki** · *The Flame Ogre* | **Kagutsuchi** — a fan of fire, and the ground it crosses keeps burning | **Ember Step** — through them, not around them | **Inferno** — six seconds of a fire that does not stop growing until it has the room |
| 🜏 | **Mirika Nagatsuki** · *The Dragonoid* | **Dragon Fist** — half a second of windup, and then whatever was there is not | **Nova Push** — everything thrown off its feet, every enemy shot deleted | **Dragon Roar** — the whole room knocked flat, set alight and slowed |
| ✠ | **Noir Vandelay** · *The Primordial* | **The Pact** — 15% of his own health up front and 4.5% more every second, for 85% more damage over 7s. One at a time; it will not layer | **Rot** — infect one, and it spreads when it dies | **Primordial** — two shades cut loose, everything cursed, him at +35%. His meter fills at a third of anyone else's, and nothing he does under the Pact fills it at all |
| ⧗ | **Alwyn Fael** · *The Long Memory* | **Echo** — whatever she cast last happens again for free | **Still Hour** — the room at half speed, and not her | **Stop** — four seconds where nothing moves at all and she walks between them |
| ✜ | **Jinya Kurose** · *The Shadow Monarch* | **Ashen Knight** — the first one he ever raised. Slow, heavy, and it stands for the rest of the fight | **Hive Marshal** — the other one. Far too fast, hits far too often, also stays | **Arise** — everything he has killed this fight gets back up on his side, in the shape it died in |

Jinya **keeps every enemy he kills**. Arise spends them, so his Burst is only
ever as big as the fight has been — walk into a boss room having killed nothing
and it raises nothing. Up to ten shadows at once, each in the shape of whatever
it used to be, and they stay for the rest of the room.

Mirika cannot be knocked back or slowed by anything. Suimu heals on every kill.
Alwyn's skills come back 20% faster. Noir does 30% more below half health —
which is where the Pact leaves him, because it keeps taking for the whole seven
seconds and cannot be signed twice. It will not kill him, but it will put him
on his last point of health.

Noir is also the one character with a **Burst economy of his own**. A character
whose entire kit is a damage multiplier will otherwise use the multiplier to
pay for the next Burst and the loop never closes — so his meter fills at 30% of
everyone else's rate, and **damage dealt under the Pact charges nothing at
all**. The Pact is the price of the Burst, not the engine for it. Expect to
earn Primordial once in a fight.

### 5★ — The Second Wave

| | Character | Skill (SPACE) | Skill (E) | Burst (R) |
|---|---|---|---|---|
| ⚛ | **Atom Vale** · *The Half-Life* | **Orbital** — three electrons take up shells around him for 5s and cut everything they pass through, and he keeps fighting inside it | **Fission** — a round that splits in two when it lands, and each half splits again | **Critical Mass** — the shells collapse inward for 2.5s, then the room goes |
| ⚔ | **Rei Amagiri** · Blade | **Red Mind** — for 8s she hits harder the closer she is to dying, up to +140% at the edge | **Skyfall** — up, across, and down on top of whatever she picked | **Bloodtide** — 7s of double damage where every body in reach heals her |
| ✧ | **Odette Lune** · *The Marionettist* | **Three Sisters** — three puppets cut loose to hunt on their own for 8s | **Strung Up** — five enemies held in place, and held things take 40% more from everyone | **Curtain Call** — six puppets at once, and the room is strung up while they work |

| ☗ | **Shion Kagemori** · *The Ten Shades* | **Pale Fang** — the white one. Fast, and it runs things down for 18s without waiting for you | **Black Fang** — the black one. Slower, heavier, and it does not leave: it stays out for the rest of the fight | **The Wheel** — the wheel-crowned one, out for the rest of the fight, tearing through the room. Including you, if you stand next to it |

| ∞ | **Toya Shirakami** · *The Limitless* | **Lapse: Blue** — a point of pull. Everything falls into it, then it shuts and crushes what it caught | **Reversal: Red** — the opposite: everything thrown away at once, and every enemy shot in the radius simply gone | **Hollow Purple** — one line walked the length of the room that unmakes a corridor through it |
| ✊ | **Yuuma Sakaki** · *The Vessel* | **Divergent Fist** — the punch lands, then lands again a beat later for 150% | **Black Flash** — one strike that always crits, splashes, and leaves him 30% hotter for 6s | **The Vessel Wakes** — 9s of double damage and 60% more attack speed, and everything in reach feeds him |

Toya's passive is **Infinity**: the first hit he would take every four seconds
does not land at all. Yuuma's is cursed energy building on one target — every
third hit on the same enemy detonates it — so he wants to stay on one thing
while Toya wants the whole room.

Shion's shikigami **pick their own targets** — they close, they bite, and they
keep going while he fights. Every one on the field makes the others hit 20%
harder, so the black one plus the white one plus the Wheel is a pack. The Wheel
is **not on your side**; it is just out, and standing next to it costs you
health.

They are also **not free**. Each shikigami has its own health bar, takes hits
from whatever it is fighting, will eat an enemy projectile aimed at you, and
can be killed outright — and calling one you already have out **recalls the
same one** rather than stacking a second. Three summons is a pack, not an army.

Atom's passive is **Half-Life**: everything he damages keeps taking 25% of that
hit again over the next two seconds.

Atom has a **Burst economy of his own**, for the same reason Noir does. Orbital
is a five-second damage multiplier that would otherwise pay for the next
Critical Mass and never stop: his meter fills at **38% of everyone else's rate**,
and **while the shells are up it fills at 25%** — the shells are not a charging
station. Critical Mass itself detonates for 210% rather than 240%. One Critical
Mass a fight, earned. Rei takes **25% less damage below half
health** — she is hardest to kill when she is nearly dead. Odette's puppets fire
25% faster and anything they kill leaves a mote that heals her.


### 5★ — The Fourth Wave

| | Character | Skill (SPACE) | Skill (E) | Burst (R) |
|---|---|---|---|---|
| ☽ | **Sora Tsukimi** · *The Late Moon* · Frost | **Moonbreak** — three crescents thrown one behind the other, each wider and colder than the last | **Cold Sheath** — a second and a half where the first hit is refused and the room freezes for asking | **Moonfall** — three seconds of crescents falling out of the sky, everything under them frozen |
| ⚔ | **Astra Valen** · *Oath of the Vanguard* · Radiant | **Breaker Charge** — shield up, straight through the middle, untouchable the whole way | **Sworn Guard** — a 45% shield, and while it holds every hit she takes is paid straight back into the room | **Holy Vow** — eight seconds of a planted standard: squad heals, room burns, and she hits 60% harder |
| ☀ | **Solen Vaird** · *The Sunbreaker* · Radiant | **Sun Pillar** — a column of daylight, half a second late and far too heavy, and he takes a third of it back | **Cleave** — one overhead that splits into two shockwave lines | **Sunrise** — seven seconds of a sun over the arena, burning them and healing you every second |
| ╱ | **Dain Ashgrove** · *The Half Blade* · Blade | **Phantom Edge** — the missing half is still there: three times the reach, and it cuts twice | **Shard Guard** — eight pieces of the broken blade hang around him and break enemy shots out of the air | **Reforge** — eight seconds where the blade is whole: every swing a full-length piercing sweep |
| 🔥 | **Aurel Sancti** · *The Burning Suit* · Flame | **Flurry** — six kicks into a cone in under a second, and the floor keeps burning | **Sky Kick** — the nearest one goes up, and comes down considerably faster | **Hell Memory** — eight seconds lit: fire where he walks, burning arcs, 50% faster |
| ● | **Vira Nocturne** · *The Heavy Hour* · Arcane | **Heavy Air** — everything close pinned flat for three seconds and crushed the whole time | **Sigil Chain** — three sigils in a triangle, and the lines between them cut | **Singularity** — four seconds of collapse, then nothing at that point at all |

Sora deals **25% more to anything frozen or slowed** — his own crescents set
that up, and Yura's freeze doubles down on it. Astra takes 15% less damage and
the whole squad takes 8% less while her shield is up. Solen leaves a **patch of
light on every kill** that heals whoever stands in it. Dain's phantom reach
comes free **below 60% health** — the further into the fight he is, the further
he reaches. Aurel never uses his hands, so **everything he does burns**. Vira
does **30% more to anything she has pinned**, which is the whole reason Heavy
Air comes before Sigil Chain.

### 4★ — The Fourth Wave

| | Character | Skill (SPACE) | Skill (E) | Burst (R) |
|---|---|---|---|---|
| ☠ | **Kael Riven** · Shadow | **Garrote** — behind it, around it, and it bleeds for three seconds without moving | **Smoke** — gone for a second, everything close loses him, next hit is a certainty | **Bloodwork** — everything already under 30% stops at once, and each one comes apart into a wave |
| ≡ | **Kuro Zenji** · Blade | **Three Cuts** — three blades, three angles, one moment, meeting where the enemy is | **Flying Cut** — a crescent thrown off the blade that crosses the room getting bigger | **Asura** — nine blades in three seconds, everything in front cut and cut and cut |
| ❈ | **Nix** · Gale | **Pounce** — onto the nearest one and off again, four sets of claws deep | **Catstep** — three dashes back to back; land them all and most of the cooldown comes back | **Nine Lives** — three and a half untouchable seconds, and she comes out of it at full health |
| ⚗ | **Pim Ottoline** · Arcane | **Flask** — enemies in the splash dissolve, anyone on your side standing in it heals | **Tonic** — 15% of her own health for a 40% squad shield and nothing slowing them | **Panacea** — everyone to full, a six-second healing field, and three flasks left throwing |
| ◈ | **Corbin Vale** · Earth | **Snare** — nothing under the net moves for two and a half seconds, and everything hurts it more | **Skipping Stone** — one stone bouncing between four of them, harder every bounce | **Snarefield** — the whole floor to netting, pinned four seconds while the ground comes up |
| ☉ | **Boro Kessel** · Volt | **Slug** — half a second of winding up, then a round through everything, and the recoil moves him | **Mortar** — three shells lobbed onto the three nearest heads | **Siege** — four seconds of continuous fire walked across the room |

Kael does **+30% to anything above 80% health** — he would rather start the job
than finish it, which is exactly the opposite of Kassandra. Kuro's **every third
attack cuts with all three swords**. Nix's dash comes back 40% faster and does
not cost her the swing. Pim tops **every heal she causes** with a shield. Corbin
is a squad passive: anything rooted, slowed or frozen takes **20% more from
everyone**, so he is a support who never has to hold the bow. Boro gets **+35%
damage for standing still for a second**, and loses it the moment he moves.

### The Fourth Wave

| | Character | Skill (SPACE) | Skill (E) | Burst (R) |
|---|---|---|---|---|
| ⛓ | **Ryo Tsukigami** · *The Chained Moon* · 5★ Shadow | **Chain Reach** — the weight goes out, catches whatever is furthest away and drags it to his feet, cutting the line it travels | **Reap** — the sickle all the way round him, twice, and every body it catches gives some of itself back | **Iron Rain** — four seconds of the sickle running a wide circle at speed, dragging everything inward with it |
| ❦ | **Sable Duquesne** · *The Red Countess* · 5★ Shadow | **Lunge** — one thrust through everything on a line, and a quarter of it comes back to her | **Blood Mist** — she stops being solid: untouchable, twice as fast, and everything she passes through is opened up | **Crimson Court** — eight seconds where everything feeds her and anything dying near her gets up on her side |
| ≋ | **Zephyra Al-Nour** · *The Long Wind* · 5★ Gale | **Fan Storm** — cutting wind thrown out, which turns at the far end and comes back through them a second time | **Sand Veil** — a blinding cloud: whatever is inside cannot find her, and she is much harder to touch while it hangs | **Simoom** — a sandstorm that follows her for seven seconds, cutting and dragging everything to a crawl |
| ✵ | **Orin Halloway** · *The Chartkeeper* · 5★ Arcane | **True North** — the astrolabe opens and sweeps a beam right the way around her, twice | **Reverse The Tide** — every enemy shot on screen stops, turns and goes back the way it came, now yours and harder | **Reckoning** — an impact on every enemy on the field, in the order she wrote them down |
| ⚙ | **Elle Vantage** · 4★ Volt | **Wind Up** — the next five swings come out at triple speed and jump to a second target | **Spring Trap** — three loaded gears that bounce off the walls for six seconds and off whatever they hit | **Mainspring** — the whole squad off cooldown at once, and eight seconds at double attack speed |
| ◉ | **Bram Kolt** · 4★ Stone | **War Beat** — three rings on the beat, each wider and heavier than the last | **March Tempo** — eight seconds where the whole squad swings 25% faster and moves 20% quicker | **Thunder Drum** — five seconds where every beat is a shockwave across the whole arena |

Ryo's passive makes anything he has **pulled** take 30% more from him for four
seconds, so the chain is a setup rather than a finisher. Sable heals off
**everything** she does — and nobody else can heal her, so she is the one
character a support cannot carry. Zephyra's dash has **two charges** and each
one leaves a cutting gust. Orin slows enemy shots that pass near her, which is
what makes Reverse The Tide land with something to reverse. Elle's every cast
takes a second off her other cooldown. Bram's every third swing is a shockwave
that knocks whatever it hits off its feet.

### The Support Line

Four characters whose whole job is the other two slots. None of them wins a
fight on their own damage and none of them is meant to — they sit at the bottom
of the damage table on purpose, and near the top of everything else.

| | Character | Skill (SPACE) | Skill (E) | Burst (R) |
|---|---|---|---|---|
| ☼ | **Wren Ashcombe** · *The Lamplighter* · 5★ Radiant | **Wardlight** — a lantern set down: anyone standing in the light heals every second and takes a quarter less | **Mend** — a hard heal to the whole squad, and whatever is burning, frozen or slowed on the one in front stops | **Vigil** — ten seconds where nobody in the squad can be taken below one point of health |
| ⚡ | **Noa Ellery** · *The Battery* · 5★ Volt | **Jump Start** — somebody down gets up at 40%; nobody down and the one in front swings 45% faster | **Charge** — thirty per cent of a Burst, handed to every character in the squad at once | **Power Surge** — every cooldown in the squad gone, half a meter to everyone, and the room takes the discharge |
| ♬ | **Cassia Brightwell** · 4★ Arcane | **Amplify** — eight seconds where everything the squad does lands 32% harder | **Resonate** — one enemy tuned: every hit on it rings out to another for 45% | **Crescendo** — nine seconds that start at +20% and climb to +90% |
| ⛨ | **Teodor Kray** · 4★ Stone | **Aegis** — the shield turns for six seconds, eating enemy shots and throwing them back at whoever sent them | **On Me** — everything in the room comes at him instead, and he takes a 60% shield to be worth hitting | **Bastion** — ten seconds where the whole squad takes half damage and nothing can move him |

Wren's heals also lay a **shield worth a third of them**, which stacks with
Pim's. Noa fills the whole squad's **Burst meter 20% faster** just by being in
it, which matters more now that a Burst is on a floor. Cassia's buffs last 25%
longer than they say. Teodor takes 18% less himself and gives the **whole
squad** 8% on top, so he is the only character who defends the two slots he is
not standing in.

### 4★

| | Character | Skill (SPACE) | Skill (E) | Burst (R) |
|---|---|---|---|---|
| ◎ | **Bao Xun** · Flame | **Called Shot** — pierces everything, up to 150% harder the further the target | **Flak** — eleven shells in a wall off the barrel | **Full Auto** — 3s of emptying everything he carries |
| ⟡ | **Iris Solene** · Gale | **Hookshot** — the hook goes out and something comes back | **Updraft** — everything nearby goes up, and the landing is what hurts | **Skydance** — 3 untouchable seconds, never landing twice in the same place |
| ⚙ | **Jun Kirisaki** · Blade | **Rev** — three seconds of leaning on it, and every body in reach feeds him back | **Drop In** — up, across, and down | **Overdrive** — six seconds where it never stops turning |
| ⚡ | **Rai Sudo** · Volt | **First Form** — a line drawn across the room a third of a second before the sword moves, always critical | **Godspeed** — he is behind them now | **Sixfold** — six draws in three seconds, untouchable through all of them |
| ⛨ | **Kotone Shiba** · Stone | **Shield Wall** — five slabs that enemies bounce off and shots break on | **Brace** — a 55% shield, and touching her hurts | **Fortress** — a full ring of wall, a 60% shield, and 30% of the squad's health back |
| ⚄ | **Vex Halloran** · Gale | **Roll** — one of six things happens, and she does not know either | **Called Shot** — pierces everything, harder the further away | **Jackpot** — six rolls in three seconds, free |
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

Two fights, from the Home screen, separate from the Spire. You find no relics
inside either, so you **pick three on the way in — any three, out of all twenty**.
It is the one place in the game where you build a loadout instead of finding
one. After that it is your squad, their kits, and whether you can read what it
is doing.

### The Sovereign **26,000 HP** and
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

### The Unmaker — Raid II

Locked until the Sovereign falls. **58,000 HP, six minutes**, and it hits half
again as hard. It has everything he has, plus three of its own:

- **Sunder** — it comes apart into **three echoes**. While they stand it takes
  *nothing at all*, and if you do not kill all three inside sixteen seconds they
  go back in and it heals 8%. Clear them in time and it staggers instead.
- **Nullify** — it takes Bursts off the table. Every meter in the squad drops to
  zero and stays there for eight seconds, right when you needed one.
- **Devour** — it eats a summon or a turret off the field and heals off it.
  Bring them anyway; just expect to pay for them.

**Reward: 80,000 shards.**

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

**No two characters share a haircut, an outfit or a weapon.** Fifty-six
hairstyles — ponytail, curtain, spiky, bob, braid, crop, twin-tails, hime,
wolfcut, slicked-back with an undercut, waves, a bun, ringlets, a mohawk, a
circlet, a sidetail, messy, a topknot, floor-length, an undercut, a pixie, a heavy layered shade, a swept
white halo and short blunt bristles — each with its own fringe and its own
ornament. One character has no weapon at all: Toya fights bare-handed, so his
hands glow instead. Another wears a **blindfold** rather than an eye, and the
light leaking out from under it is the whole character. Forty-five outfit shapes (skirt, robe,
dress, shorts, plate, jacket, apron, longcoat, wrap, coat, haori, kimono,
hoodie, duster, mantle, tailcoat, a shinigami's shihakusho, full plate,
harness gear, a surcoat, an open coat, a haramaki, a plain tee, a smock, a
vest, a dress suit, a long dress and a gunner's rig among them) chosen
**separately from the hair**; keying the skirt off the hairstyle, which is what the code
used to do, is how three characters ended up dressed the same for no reason.
Fifty-five weapons and one pair of bare hands, one each. A roster where three people share a haircut in
different colours reads as one character with palette swaps, which is the
opposite of what a gacha needs.

Fifty-six characters share one rig, and a pose is a **skeleton rather than a set of
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

Feet also carry a **knee** value per frame: how folded that leg is right now.
The support leg is nearly straight, the front knee folds to absorb the landing,
and the swing leg folds up under the body before it reaches out again — which
is the difference between a run and two legs sliding past each other. The
shoulders also **counter-rotate against the hips**, which is a large part of
why a run reads as a run and not as a pair of legs under a plank. One
subtlety worth writing down: `bent()` pushes a joint along the *normal* of the
hip-to-foot line, and with the hip above the foot that normal points backwards,
so the fold has to be **negative** or the character runs on knees that bend the
wrong way.

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
`tools/make_fx.py` generates a second atlas, `assets/fx.png`: twenty-one
effects, **ten frames each, at 48×48** — including a turning **atom** (nucleus
and three electron shells) for Atom Vale, and a tumbling **longsword** for
Aldric, who throws swords and should look like it. A slash sweeps its arc open,
a lance extends out of nothing, a knife tumbles end over end, lightning
re-forks, a bomb's fuse sputters, a flame's tip licks and curls. Impacts are
animations too — a burst, a crossed cut for melee, an expanding nova, a growing
frost crystal, and a **shock ring** for anything that knocks a body off its
feet.

Every one of them is drawn to the same four beats an animator would use:
**anticipation** (a frame or two of the shape gathering before it commits),
the **action**, an **overshoot** past where it was going, and a **settle**.
Impacts get one frame of near-solid white — the flash frame — and then debris
on its own slower timing, so the hit and the fallout are not the same
animation played twice.

The single largest thing separating a sprite that *moves* from a sprite that
*looks like it is moving* is the **smear**: `smear()` draws the shape a fast
object leaves behind it, crisp and wide at the head and stretched thin at the
tail. Every melee swing in the game also throws a **motion streak** — a shot
that does no damage at all and exists purely to be the streak behind the
blade — and the character themselves leaves **afterimages** while dashing and
through the strike frames of a swing, tinted their own element and fading
over a fifth of a second.

The attack cycle is also **not played at an even rate**. The coil and the wind
hold, the strike is over in a blink, the follow-through hangs, and then it
settles: `heroFrame` splits the swing at 30/46/56/74% rather than into five
equal slices. Five equal frames read as a flipbook; unequal ones read as a
swing.

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
the dip under it, a nose (a small one — the cut back at the philtrum below it is
what actually makes a nose read as a nose, not its length), two lips, and a chin that pulls in to the jaw. A
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
