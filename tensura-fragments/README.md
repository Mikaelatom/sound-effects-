# Tensura: Fragments

An addon for **Tensura: Reincarnated** (Minecraft 1.21.1, NeoForge). It replaces Tensura's Unique and Ultimate
skills with new ones built out of Tensura's parts (visuals, sounds, effects), and keeps Tensura's magic and other
skills learnable. Tensura has to be installed; this addon ships
none of its assets.

Abilities are meant to take practice: momentum, placement, aim, time limits and gauge management.

## Skill 1: Gambit Cards

Gambit Cards is a real Tensura skill (Unique). It shows up in Tensura's skill menu, goes on Tensura's skill keys,
and you switch between its three modes the same way as any Tensura skill. Every player is given it automatically.

| Mode | What it does |
|---|---|
| **Throw** | A card flies where you aim and sticks to the first block or mob it hits. It inherits your momentum. |
| **Teleport** | Warp to the card under your crosshair (or your newest card). You keep your speed, redirected where you look. |
| **Detonate** | Blow up every card. Sneak while using it to detonate only the card you're aiming at. |

- **Deck:** 5 cards; one is drawn back every 3 seconds. At most 3 cards can be out; throwing a 4th makes the oldest fizzle.
- **Magicules:** throwing costs 40, teleporting costs 60, charged by Tensura like any skill (nothing is charged if the action can't happen).
- **Charge:** a card's blast grows from 50% to 150% over 5 seconds. It sparks and glows when fully charged.
- **Time limit:** cards fizzle after 20 seconds and blink during their last 3.
- **Blast:** damage falls off with distance and **hits you too** if you're inside the radius (4 blocks).
  The knockback also hits you, so a careful self-blast doubles as a launch. Blasts set off other cards nearby.

Uses Tensura's shockwave particle for the blast and teleport. Everything above is tunable in `serverconfig/tensurafragments-server.toml`.

The HUD (bottom right, only while Gambit Cards is on your active skill preset) shows your deck, the next draw, and a timer bar per active card (gold = fully charged).

## Skill 2: Shikigami Control

Based on Seika from *The Reincarnation of the Strongest Exorcist*. A Unique skill, given to every player. **Paper**
(vanilla `minecraft:paper`) in your inventory is the ammo; creative players don't use any.

**Leaves** (any kind) work as a weaker stand-in once you're out of paper, at half strength by default
(`leafPotency`): leaf shikigami have half the health, damage and lifetime; leaf talismans make a smaller, weaker
blast; a barrier lasts for the average strength of its anchors; and a leaf doll blocks only half of the hit. Paper is
always used first. Things made from leaves have a green talisman.

| Mode | What it does | Cost |
|---|---|---|
| **Shikigami** | Turn the block you're looking at (up to 32 blocks away) into a helper that follows you and fights your enemies. Hard blocks make slow, tough, hard-hitting shikigami; soft blocks make quick, fragile ones. When it dies or its 2 minutes run out it turns back into the block (dropped as an item). There's no limit on how many you can have out (server owners can set one with `shikigamiLimit`). **Sneak** to dismiss them all. | 1 paper + 50 magicules + 20 per point of block hardness |
| **Spell Talisman** | Throw your selected spell talisman. Thrown fast (3 blocks a tick) and nearly flat, so it lands where you aim, up to about 290 blocks away. It goes off the moment it touches the ground or a creature. **Sneak** to switch spell. See the spell table below. | 1 paper + the spell's magicules |
| **Barrier** | Plant a talisman anchor on the block you're looking at (up to 64 blocks away). **Sneak** to raise the barrier once you have 3 or more (placing the 6th raises it automatically), or to dispel it. While up it's a solid wall: mobs can't walk through it (anything inside when it goes up is pushed out, and hostile mobs caught inside burn), and projectiles that aren't yours are destroyed, even fast ones. You, your shikigami, your pets and other players can pass (`barrierBlocksAllMobs`, `barrierBlocksPlayers` to change). Lasts 30 seconds. | 1 paper + 15 magicules per anchor, then 8 magicules/second |
| **Paper Beast** | Fold paper into the selected beast, just in front of you. It follows you and fights your enemies on its own until you possess it. It's paper: fire does triple damage, falls do nothing, and it falls apart if you leave. No limit on how many. **Sneak** to switch beast (see below). | The beast's paper + 40 magicules |
| **Possess** | See through a paper beast's eyes and control it: the one you're looking at, or your nearest. Your movement keys and mouse steer it, attack is its attack, and your body stands still (and helpless) where you left it. Press again to come back. Taking damage, going over 128 blocks away, or running out of magicules snaps you back. **Sneak** (when not possessing) to unfold all your beasts. | 2 magicules/second |
| **Substitution** | Automatic: while it's on and you have paper, every attack that hits you is taken by a paper doll instead. You take no damage, lose 1 paper and blink 3 blocks away from the attacker. Only real attacks count (mobs, players, projectiles, blasts), so burning, drowning and falling don't eat your paper. Use the mode to turn it **on/off** to save paper (on by default). | 1 paper per hit blocked |

**Paper beasts** are drawn with Tensura's own creature models and animations, folded from paper:

| Beast | Paper | When you control it |
|---|---|---|
| **Owl** (Tensura's one-eyed owl) | 2 | Flies freely (jump: up, sneak: down, it flies the way you look). You see in the dark. Its peck (3) marks what it hits, glowing through walls for 10 seconds: a scout. |
| **Hound** (Tensura's hound dog) | 3 | Fast runner with a hard bite (7 and knockback). Attacking with nothing in reach lunges forward. |
| **Winged Cat** (Tensura's winged cat) | 2 | Strikes twice (4 + 4). Hold jump in the air to glide on its wings, faster than it runs; the glide gauge (under its name at the top of the screen) refills on the ground. |
| **Horned Rabbit** (Tensura's horned rabbit) | 1 | Tiny and jumps very high. Its horn charge hits harder the faster it's moving (momentum!); attacking with nothing in reach charges forward. |

Leaves work here too, and make a weaker beast. While you possess one, the top of the screen shows which beast and its
health. Substitution still guards your body while you're away (a doll takes the hit and you stay in the beast).

**Spell talismans** (after Seika's five-phase onmyōdō). Only Explosive can hurt you; the elemental ones spare you, your
shikigami, your pets and your released grimoire creatures. Leaves make every spell weaker.

| Talisman | On contact | Magicules |
|---|---|---|
| **Explosive** (red) | The blast: damage and knockback in 3 blocks, hurts you too if you're close, sets off cards | 20 |
| **Fire 火** (orange) | Flame burst: 4 damage and 5 seconds of burning to enemies within 3 blocks | 25 |
| **Water 水** (blue) | Water surge: strong knockback, 2 damage and Slowness II to enemies within 4 blocks; puts out burning creatures and fire blocks | 20 |
| **Wood 木** (green) | Roots: enemies within 3 blocks are held in place and weakened for 3 seconds; you and your allies there heal 4 and get Regeneration | 25 |
| **Lightning 金** (yellow) | Lightning strikes the nearest enemy within 4 blocks for 8, then chains to up to 2 more, weaker each jump | 35 |
| **Earth 土** (brown) | Stone eruption: enemies within 3 blocks take 5 and are launched into the air | 30 |
| **Ice 氷** (pale blue) | Frost: enemies within 3 blocks take 3, are frozen solid (like powder snow) and slowed; nearby water freezes over for a few seconds and fires go out | 25 |
| **Wind 風** (pale green) | Gust: enemies within 4 blocks take 2 and are blown away; projectiles that aren't yours are thrown back; if you're caught in it you're launched upward (throw it at your feet for a wind jump) | 20 |
| **Teleport 転** (purple) | Phase transfer: you appear where the talisman landed (up to 256 blocks away, half with leaves; it flies dead straight, no drop). Hurts no one. | 30 |

Costs scale with `spellCostMultiplier`.

The HUD (bottom left, only while Shikigami Control is on your active skill preset) shows your paper (or leaves once
the paper is gone), the Substitution doll (glows while it's on), a health and
time bar per shikigami, the selected spell talisman and paper beast (with the paper it takes), and your barrier anchors (◇ placed, ◆ barrier up). All numbers are in the `[shikigami]` section of the server config.

Uses Tensura's earth-cast, space-cast, barrier-break, uncast and golem sounds and its shockwave particle.

## Skill 3: Sealing Grimoire

An empty book that seals creatures and magic with paper talismans, so you can call them back out later. A Unique
skill, given to every player. The first time you use it, one ordinary **book** in your inventory becomes the
**Sealing Grimoire** item. Everything sealed lives in that book (9 pages by default), so if you lose the book, you
lose what's in it. Paper is the ammo, and leaves work at reduced strength as with Shikigami Control.

| Mode | What it does | Cost |
|---|---|---|
| **Seal Creature** | Seal the creature you're looking at. It only works once you've weakened it to **35% health or less** (less with leaves). A failed attempt burns the talisman. Bosses (the `tensurafragments:unsealable` tag, which includes `#c:bosses`) and creatures over 300 max health can't be sealed, nor can other people's pets. Using it on one of your own released creatures sends it back to the book for free. | 1 paper + 30 magicules + 2 per point of the creature's max health (only charged on success) |
| **Seal Magic** | Open the book for half a second. The first spell or projectile that comes within 3 blocks is caught, including Tensura's spell projectiles. A good catch is ready again straight away; a mistimed one goes on a 2 second cooldown. | 1 paper per catch |
| **Release** | Release the selected page. A **creature** comes out at full health and fights for you for 60 seconds, attacking whatever you hit or whatever hits you. It never targets you or your other creatures, and it goes back into the book when its time is up; if it dies, the page is gone. **Magic** is fired where you're looking, as your own. **Sneak** to turn to the next page. | 40 magicules (creature) / 20 (magic) |

The HUD (right side, only while the skill is on your active preset) lists the pages (green for creatures, blue for
magic) and marks the selected one; the item tooltip shows the same. All numbers are in the `[grimoire]` section of
the server config.

## Skill 4: Rainbow Magic

A Unique skill of its own, given to every player. It casts **Tensura's own spells**, recoloured in a moving rainbow
(the spell's model is redrawn in rainbow bands and leaves a rainbow trail), and every hit strikes with **every element
at once**: the spell's own damage plus 12 extra (half of all the elements' damage together,
`rainbowDamageMultiplier`), and the target is set on fire, frozen and slowed. When the spell ends it bursts into
rainbow rings. **Sneak** to switch spell.

| Spell (Tensura's) | Damage | Magicules |
|---|---|---|
| Fire Ball | 6 (explodes) | 60 |
| Water Blade | 6 | 60 |
| Wind Blade | 5 | 50 |
| Lightning Lance | 9 | 90 |
| Stone Shot | 7 | 60 |
| Ice Lance | 7 | 70 |

Magicules are the spell's base cost times `rainbowCostMultiplier` (2). No paper needed. The HUD (bottom left, while
it's on your active preset) shows the selected spell in rainbow letters. Only spells cast through Rainbow Magic are
recoloured; Tensura's spells cast any other way look and hit as normal.

**Rainbow Spirit** (the second mode, and also the last choice when you **sneak** to switch spell, after Ice Lance): summons the next spirit from Spirit Control's line-up (Ifrit, Sylphide, Undine, War
Gnome, Blade Tiger) at whatever you're aiming at, recoloured in rainbow. It does its one attack and vanishes like a
normal spirit, but its attack (and any spell it throws) strikes with every element at once, just like a rainbow spell.
Rainbow spirits have their own turn order, separate from Spirit Control's, and cost the spirit's 30 magicules times
`rainbowCostMultiplier`. You don't need Spirit Control for this.

## Skill 5: Flame Emperor

A Unique skill, given to every player, with two modes.

**Fire Magic.** Cast any of Tensura's own fire spells. **Sneak** to switch; the HUD (bottom right, while it's on
your active preset) shows the selected one.

| Spell | Damage | Burns | Magicules |
|---|---|---|---|
| Fire Bolt | 5 | 4s | 20 |
| Fire Ball | 8 | 5s | 35 |
| Magma Shot | 9 | 6s | 40 |
| Fire Lance | 12 | 6s | 55 |
| Flame Orb | 10 | 8s | 60 |
| Flame Sphere | 14 | 8s | 80 |
| Heat Sphere | 16 | 10s | 100 |
| Plasma Ball | 20 | 10s | 140 |
| Black Flame Ball | 24 | 12s | 180 |
| Hell Flare | 30 | 15s | 250 |

**Draconic Hell Storm.** Tensura's flame magic circle opens on your hand and charges for 1.5 seconds, then
Gluttony's mist (Tensura's own model and animation) pours out of it as hellfire for 3 seconds: a cone straight ahead
of you, thin at your hand and 5 blocks wide (radius) at its far end, 18 blocks out. What burns is exactly the mist you
see. It follows where you look, so you can sweep it across a crowd, and stops at walls. Anything in it takes **50
hellfire damage four times a second** (200 a second) and gets **Draconic Hellfire**: a burn that **never goes out**,
dealing 8 damage every second until the target dies. Hellfire is its own damage type: armour, shields, Resistance,
Fire Resistance, fire immunity (blazes, fire races) and Tensura's dodges and barriers don't stop it, and water, milk
and totems don't put it out. It never touches you, your shikigami, pets or released grimoire creatures. 800
magicules, 30 second cooldown.

All numbers are in the `[flame]` section of the server config.

## Skill 6: Spirit Control

Every blow calls a spirit. A Unique skill, given to every player. While it's on your active skill preset, **each of
your melee hits** calls the next spirit in line onto what you hit; it does one of its attacks and vanishes. They come
in turn, using Tensura's own spirit models, textures and attack animations:

| Spirit | Its attack | Damage |
|---|---|---|
| **Ifrit** | Appears beside you and hurls one of Tensura's fire balls (sets targets alight) | 12 |
| **Sylphide** | Appears beside you and throws one of Tensura's wind blades | 12 |
| **Undine** | Appears beside you and throws one of Tensura's water balls | 12 |
| **War Gnome** | Rises beside the target and stomps: the ground bursts, everything within 3 blocks is hurt and thrown into the air | 14 |
| **Blade Tiger** | Appears a few blocks off and pounces straight through the target, cutting everything on its path | 17 |

**The skill key** sends the next spirit at whatever you're aiming at, up to 32 blocks away (a creature, or the spot you
point at). **Sneak** + key turns **Spirit Link** (the melee part) off or on, so you can hit things without calling
spirits. Spirits never hurt you, your shikigami, pets or released grimoire creatures.

30 magicules per spirit, at most one every half second (so fast clicking doesn't flood the field). All numbers are in
the `[spirits]` section of the server config.

## Skill 7: Spirit Communion (Yifa)

After Yifa, Seika's elf companion: she has little magic of her own, but her elf blood lets her see elemental spirits
and command them, casting far beyond her own power (fire and wind are her affinity). A Unique skill, given to every
player. Spirits are found **in the world, near their element**, so where you fight decides what you can cast:

| Spirit | Gathers |
|---|---|
| **Fire** | by fires, campfires, torches, lanterns, lava, magma, and everywhere in the Nether |
| **Water** | by water |
| **Wind** | high up (y 100+), and under open sky |
| **Earth** | deep underground (below y 50, no sky) |

Up to 6 wild spirits gather around you at a time; they wander off after a minute.

| Mode | What it does | Cost |
|---|---|---|
| **Spirit Sight** | On/off. Only with it on can you see wild spirits (drifting glowing orbs); it also shows anything invisible nearby as soul sparks. | 1 magicule/second |
| **Call Spirit** | Bind the spirit you're looking at (within 16 blocks). **Sneak** to gather every spirit within 8 blocks. They orbit you for 3 minutes; up to 5 at once. | none |
| **Spirit Magic** | Release every bound spirit as one spell at what you aim at. The mix decides the spell (below), and more spirits make it bigger. | 5 magicules per spirit |
| **Spirit Jutsu** | The weak fallback with no spirits: a small fire bolt or wind blade from your own magicules. **Sneak** to switch fire / wind. | 15 magicules |

| Spirits released | Spell |
|---|---|
| **Fire + Wind** | **Fire Whirl**: Tensura's magic tornado, burning orange, travels the way you looked for 4+ seconds, dragging enemies in, lifting and burning them (3 per spirit, twice a second) |
| **Fire** | **Flame Burst**: a Tensura fire ball flies where you aim, and where it lands everything takes 5 per spirit and is set alight; wider with more spirits |
| **Wind** | **Gale**: a Tensura wind blade flies where you aim, and where it lands everything takes 2 per spirit and is blown up and away, and projectiles that aren't yours are thrown back |
| **Water** | **Healing Spring**: heals you and your allies within 6 blocks (4 per spirit) with Regeneration, and puts out fires |
| **Earth** | **Earth Bind** where you aim: 2 per spirit, and enemies are held in place and weakened for 3 seconds |

Water in a fire or wind mix also heals you (2 per water spirit); earth in a mix slows everything it hits.

A small HUD just right of the hotbar (while it's on your active preset) shows a dot that lights up while Spirit Sight
is on, then your bound spirits with a thin bar for their time left. Switching jutsu element shows it above the hotbar.

**Magisteel items** that draw spirits to you (both in the Tools & Utilities creative tab):

| Item | Recipe | What it does |
|---|---|---|
| **Magisteel Spirit Bell** | string on top, 3 low magisteel ingots across the middle, low magisteel / amethyst shard / low magisteel on the bottom | Ring it (use): every wild spirit within 32 blocks comes flying and binds itself to you (while you have room). If there are hardly any, the ringing calls a couple out of their element nearby first. 10 second cooldown. |
| **Magisteel Spirit Lantern** | high magisteel ingot above and below a soul lantern, with low magisteel ingots either side | Carry it anywhere in your inventory: twice as many spirits gather around you, and wild ones within 16 blocks drift in to wait at your side, ready to call. |

All numbers are in the `[yifa]` section of the server config.

## Tensura's own skills and magic

Tensura's **magic** and its **resistance, intrinsic, common and extra skills** are all kept and learned the normal
Tensura way: Grimoires from wizard-tower chests, Magic Tomes, Learn Points in Tensura's ability screen, and skills
that become available as you use things. Tensura charges for new skills out of your maximum magicules, as usual.

Only Tensura's **Unique and Ultimate** skills are removed (checked on login and every 2 seconds): this addon makes
the new Unique skills, and they'll evolve into its own Ultimates. Set `strippedSkillTypes` (or turn off
`stripOriginalSkills`) in the `[originals]` section of the server config to change that. Races are untouched.

### Learning magic by surviving it

Every time a Tensura spell you don't know hits you and you live, you get closer to understanding it; the action bar
shows your progress ("Survived Fire Ball: 3/5"). **Survive it 5 times and you learn it** (through Tensura's own
learning, so its magicule cost applies). It works whoever cast it: a monster, another player, or a spell thrown by
one of this addon's skills. Set `survivalsToLearnSpell` in the `[magic]` section (0 turns it off).

See `docs/tensura-reference.md` for the Tensura code and magic entities we can reuse.

## Building

Requires JDK 21.

```
./gradlew build          # jar in build/libs/
./gradlew runClient      # test client with Tensura + dependencies
./gradlew runGameTestServer      # headless launch with Tensura that runs the in-world tests, then exits
./gradlew catalogTensuraAssets   # writes docs/tensura-assets.md, a list of every Tensura asset to pick from
```

The in-world tests in `src/main/java/com/tensurafragments/test/` cover both skills: card blasts (range, self damage,
chaining), teleporting, the skill icons, turning blocks into shikigami (and needing paper), block hardness scaling,
dismissing, substitution (timed and mistimed), talisman blasts, the barrier keeping out mobs and arrows, and the
grimoire (making it from a book, sealing only weakened creatures, released creatures serving and returning, catching
and re-firing magic, bosses being unsealable), Rainbow Magic with real Tensura spells, all ten Flame Emperor fire
spells, Draconic Hell Storm (charge, damage, and a burn that can't be cured), paper beasts (folding each one, paper cost,
possession steering the hound and owl, attacking what the beast faces, snapping back when hurt, fire tearing paper) and
Spirit Control (every spirit hurting its target and vanishing, the turn order, the spam limit, Spirit Link, sparing
allies).

`./gradlew runClient -PvisualCheck` opens a world called `visualtest` (copy any world into `run/client/saves/`),
casts Draconic Hell Storm and a Rainbow Magic spell, saves screenshots to `run/client/screenshots/`, and quits.

Tensura: Reincarnated, ManasCore, Architectury, GeckoLib, SmartBrainLib and TerraBlender (runtime only) are pulled from CurseForge via
CurseMaven (versions in `build.gradle`). If you update Tensura, bump the file id in `tensuraDep`.

The card place sound is `pixelcrusher-squish-pop-256410.mp3` from the repo root, converted to Ogg.
