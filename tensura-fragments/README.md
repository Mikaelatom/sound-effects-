# Tensura: Fragments

An addon for **Tensura: Reincarnated** (Minecraft 1.21.1, NeoForge). It replaces Tensura's Unique and Ultimate
skills with new ones built out of Tensura's parts (visuals, sounds, effects), and keeps Tensura's magic and other
skills learnable. Tensura has to be installed; this addon ships
none of its assets except two recoloured copies of the Demon Lord Haki texture (Spirit Release's blue and rainbow
auras).

Abilities are meant to take practice: momentum, placement, aim, time limits and gauge management.

## Your starting skill

After you pick your race (the first time you join a world), a screen asks you to **pick one of this addon's skills**
to start with: Gambit Cards, Shikigami Control, Sealing Grimoire, Rainbow Magic, Flame Emperor, Spirit Control,
Spirit Communion, Energy Magic, Soul Reaper or Rune Magic (hover a button for what it does). You get only that one.
Players who already had this addon's skills from before keep them all and aren't asked. Turn the choice off with
`pickOneSkill` in the `[startingSkill]` section of the server config, and everyone gets every skill again (each has
its own `grant...` option).

## Skill 1: Gambit Cards

Gambit Cards is a real Tensura skill (Unique). It shows up in Tensura's skill menu, goes on Tensura's skill keys,
and you switch between its four modes the same way as any Tensura skill. It's one of the starting skills you can pick (see [Your starting skill](#your-starting-skill)).

| Mode | What it does |
|---|---|
| **Throw** | A card flies where you aim and sticks to the first block or mob it hits. It inherits your momentum. |
| **Teleport** | Warp to the card under your crosshair (or your newest card). You keep your speed, redirected where you look. |
| **Detonate** | Blow up every card (Spell Cards too). Sneak while using it to detonate only the card you're aiming at. |
| **Inscribe** | Turn magicules into a **Spell Card** (an item). Sneak while using it to choose which spell. |

- **Deck:** 5 cards; one is drawn back every 3 seconds. At most 3 cards can be out; throwing a 4th makes the oldest fizzle.
- **Magicules:** throwing costs 40, teleporting costs 60, charged by Tensura like any skill (nothing is charged if the action can't happen).
- **Charge:** a card's blast grows from 50% to 150% over 5 seconds. It sparks and glows when fully charged.
- **Time limit:** cards fizzle after 20 seconds and blink during their last 3.
- **Blast:** 20 damage at the centre (10 to 30 with charge), falling off with distance across a 5 block radius. It
  **hits you too** (at 35%) if you're inside the radius.
  The knockback also hits you, so a careful self-blast doubles as a launch. Blasts set off other cards nearby.

### Spell Cards

Inscribe makes Spell Cards out of magicules. **Right-click** one to throw it: it sticks like a Gambit card (and
you can teleport to it), and casts its spell when you detonate it. Spell Cards never hurt you, don't take the
plain cards' 3 slots (up to 8 can be out), and last a minute.

| Card | Magicules | Spell |
|---|---|---|
| Flame | 120 | A fire burst (20 damage, 4 blocks) that sets everything ablaze |
| Frost | 120 | A freezing burst (14 damage, 4 blocks): heavy slowness and frost |
| Thunder | 200 | Lightning on the target (28), leaping to 4 more enemies within 8 blocks (14 each) |
| Gale | 250 | A **tornado** for 5 seconds: drags enemies in from 8 blocks, lifts and grinds what it catches |
| Quake | 250 | A **wide** ground slam (18 damage, 8 blocks) that throws everything up |
| Meteor | 600 | A meteor falls a second later: a **massive** blast (50 damage, 10 blocks) that sets everything alight |

**Chains:** Spell Cards stuck on the same creature (or the same spot) go off together as a chain, one after
another, each link **+50%** stronger than the last (100%, 150%, 200%, ...). Some mixes finish with a **combo**:

| Combo | Cards | Finisher |
|---|---|---|
| Fire Tornado | Flame + Gale | A big burning tornado that travels away from you, dragging and burning everything |
| Thunderstorm | Thunder + Gale | A storm tornado that calls lightning down on everything around it |
| Steam Explosion | Flame + Frost | A huge blinding steam blast (30 damage, 9 blocks) |
| Cataclysm | Quake + Meteor | The biggest blast in the kit: a second meteor, 80 damage across 16 blocks |
| Three of a Kind | 3 of the same | That spell again at double power |

Longer chains make the combo stronger too. `spellCardPower`, `spellCardCostMultiplier` and `maxSpellCards` are in
the `[cards]` section of the server config.

Uses Tensura's shockwave particle for the blast and teleport. Everything above is tunable in `serverconfig/tensurafragments-server.toml`.

The HUD (bottom right, only while Gambit Cards is on your active skill preset) shows your deck, the next draw, and a timer bar per active card (gold = fully charged).

## Skill 2: Shikigami Control

Based on Seika from *The Reincarnation of the Strongest Exorcist*. A Unique skill, one of the starting skills. **Paper**
(vanilla `minecraft:paper`) in your inventory is the ammo; creative players don't use any.

**Leaves** (any kind) work as a weaker stand-in once you're out of paper, at half strength by default
(`leafPotency`): leaf shikigami have half the health, damage and lifetime; leaf talismans make a smaller, weaker
blast; a barrier lasts for the average strength of its anchors; and a leaf doll blocks only half of the hit. Paper is
always used first. Things made from leaves have a green talisman.

| Mode | What it does | Uses |
|---|---|---|
| **Shikigami** | Turn the block you're looking at (up to 32 blocks away) into a helper that follows you and attacks what you hit (nothing else). Hard blocks make slow, tough, hard-hitting shikigami; soft blocks make quick, fragile ones. When it dies or its 2 minutes run out it turns back into the block (dropped as an item). There's no limit on how many you can have out (server owners can set one with `shikigamiLimit`). **Sneak** to dismiss them all. | 1 paper + 50 magicules + 20 per point of block hardness |
| **Spell Talisman** | Throw your selected spell talisman. Thrown fast (3 blocks a tick) and nearly flat, so it lands where you aim, up to about 290 blocks away. It goes off the moment it touches the ground or a creature. **Sneak** to switch spell. See the spell table below. | 1 paper + the spell's magicules |
| **Barrier** | Plant a talisman anchor on the block you're looking at (up to 64 blocks away). **Sneak** to raise the barrier once you have 3 or more (placing the 6th raises it automatically), or to dispel it. While up it's a solid wall: mobs can't walk through it (anything inside when it goes up is pushed out, and hostile mobs caught inside burn), and projectiles that aren't yours are destroyed, even fast ones. You, your shikigami, your pets and other players can pass (`barrierBlocksAllMobs`, `barrierBlocksPlayers` to change). Lasts 30 seconds. | 1 paper + 15 magicules per anchor, then 8 magicules/second |
| **Paper Beast** | Fold paper into the selected beast, just in front of you. It follows you and fights your enemies on its own until you possess it. It's paper: fire does triple damage, falls do nothing, and it falls apart if you leave. No limit on how many. **Sneak** to switch beast (see below). | The beast's paper + 40 magicules |
| **Possess** | See through a paper beast's eyes and control it: the one you're looking at, or your nearest. Your movement keys and mouse steer it, attack is its attack, and your body stands still (and helpless) where you left it. Press again to come back. There's no range: go as far as you like, and the world loads around the beast while your body waits where you left it. Taking damage or running out of magicules snaps you back. **Sneak** (when not possessing) to unfold all your beasts. | 2 magicules/second |
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
the paper is gone), the Substitution doll (glows while it's on), the selected spell talisman and paper beast (with the paper it takes), and your barrier anchors (◇ placed, ◆ barrier up). All numbers are in the `[shikigami]` section of the server config.

Uses Tensura's earth-cast, space-cast, barrier-break, uncast and golem sounds and its shockwave particle.

**Making paper:** fill a crafting table with planks (any wood, mixed is fine) for **20 paper**.

## Skill 3: Sealing Grimoire

An empty book that seals creatures and magic with paper talismans, so you can call them back out later. A Unique
skill, one of the starting skills. The first time you use it, one ordinary **book** in your inventory becomes the
**Sealing Grimoire** item. Everything sealed lives in that book (9 pages by default), so if you lose the book, you
lose what's in it. Paper is the ammo, and leaves work at reduced strength as with Shikigami Control.

| Mode | What it does | Uses |
|---|---|---|
| **Seal Creature** | Seal the creature you're looking at. It only works once you've weakened it to **35% health or less** (less with leaves). A failed attempt burns the talisman. Bosses (the `tensurafragments:unsealable` tag, which includes `#c:bosses`) and creatures over 300 max health can't be sealed, nor can other people's pets. Using it on one of your own released creatures sends it back to the book for free. | 1 paper + 30 magicules + 2 per point of the creature's max health (only charged on success) |
| **Seal Magic** | Open the book for half a second. The first spell or projectile that comes within 3 blocks is caught, including Tensura's spell projectiles. A good catch is ready again straight away; a mistimed one goes on a 2 second cooldown. | 1 paper per catch |
| **Release** | Release the selected page. A **creature** comes out at full health and fights for you for 60 seconds, attacking whatever you hit or whatever hits you. It never targets you or your other creatures, and it goes back into the book when its time is up; if it dies, the page is gone. **Magic** is fired where you're looking, as your own. **Sneak** to turn to the next page. | 40 magicules (creature) / 20 (magic) |

The HUD (right side, only while the skill is on your active preset) lists the pages (green for creatures, blue for
magic) and marks the selected one; the item tooltip shows the same. All numbers are in the `[grimoire]` section of
the server config.

## Skill 4: Rainbow Magic

A Unique skill of its own, one of the starting skills. It casts **Tensura's own spells**, recoloured in a moving rainbow
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

A Unique skill, one of the starting skills, with two modes.

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

Every blow calls a spirit. A Unique skill, one of the starting skills. While it's on your active skill preset, **each of
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
and command them, casting far beyond her own power (fire and wind are her affinity). A Unique skill, one of the starting
player. Spirits are found **in the world, near their element**, so where you fight decides what you can cast:

| Spirit | Gathers |
|---|---|
| **Fire** | by fires, campfires, torches, lanterns, lava, magma, and everywhere in the Nether |
| **Water** | by water |
| **Wind** | high up (y 100+), and under open sky |
| **Earth** | deep underground (below y 50, no sky) |

Up to 6 wild spirits gather around you at a time; they wander off after a minute.

| Mode | What it does | Uses |
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

## Skill 8: Energy Magic

Pour your experience into a spell. A Unique skill, one of the starting skills. It casts **Tensura's own spells, glowing
green and twice as powerful**, and instead of magicules it costs **experience levels** (the vanilla green XP bar).
**Sneak** to switch spell. A small line left of the hotbar (while it's on your active preset) shows the selected
spell and its cost, in red when you don't have the levels. Energy spells glow with a green outline, leave a green
trail and burst into green sparks where they land.

| Energy spell (Tensura's) | Damage (normal x2) | Levels |
|---|---|---|
| Fire Bolt | 10, burns | 1 |
| Wind Blade | 10 | 1 |
| Water Blade | 12 | 1 |
| Stone Shot | 14 | 1 |
| Water Ball | 14 | 2 |
| Ice Lance | 14 | 2 |
| Fire Ball | 16, burns, explodes | 2 |
| Frost Ball | 16 | 2 |
| Lightning Lance | 18 | 2 |
| Boulder Shot | 22 | 3 |
| Fire Lance | 24, burns | 3 |
| Thunder Lance | 26 | 3 |
| Flame Sphere | 28, burns | 4 |
| Plasma Ball | 40, burns | 5 |

`energyPower` (2) and `energyLevelCostMultiplier` are in the `[energy]` section of the server config.

## Skill 9: Soul Reaper

Every life you take leaves its soul in your hands. A Unique skill, one of the starting skills.

- **Every kill gives you souls**, whether or not you're a Demon Lord Seed. They're Tensura's own soul points (the
  "Souls" in Tensura's menu, 1000 points = 1 soul), so they still count toward awakening as a True Demon Lord, and
  dying still costs you some. A kill gives 1 soul plus more the stronger it was (½ point per EP), and **captures its
  soul**: what it was, its name and its EP. You keep up to 27; past that the weakest is let go. Kills by your
  summoned souls and possessed creatures count as yours.
- **Each captured soul can be used once**, by any of the modes below: using it takes it off your list and takes its
  worth (what its kill gave you) off your soul count, in creative too. Recalling a summoned soul gives both back.
  Kill ten zombies and you have ten zombie souls to use.
- **Sneak** with any mode to switch which captured soul is selected. A small line right of the hotbar (while it's on
  your active preset) shows your souls and the selected soul.

| Mode | What it does | Uses |
|---|---|---|
| Soul Summon | The selected soul comes back as a glowing blue ghost of what it was (a player's soul comes back as a zombie wearing their head) and attacks what you hit for 60 seconds. It never turns on you, doesn't burn in the sun and drops nothing. Up to 3 out at once. When its time is up it passes on. | The soul |
| Soul Recall | Calls all your summoned souls back to you. Each goes back on your list and its worth back onto your soul count, ready to summon again later. (Souls that possessed a summoned soul are lost with it.) | Nothing |
| Soul Absorb | You devour the selected soul and take **half its EP**, at most a quarter of your own max EP per soul (your max magicules and aura go up). 30 second cooldown between absorbs. | The soul |
| Soul Possession | The selected soul flies into the creature you're looking at (up to 24 blocks). If the soul was at least as strong (EP) as the creature, **it becomes yours for good**: it attacks what you hit, never turns on you, and gets the soul's EP plus +50% health, +50% damage and +10% speed. Send more souls into your own creatures (summoned ones too, which then stay for good) to stack it, up to 5 souls each. Possessed creatures glow violet. | The soul |

All the numbers are in the `[souls]` section of the server config.

## Skill 10: Rune Magic

Power lives in the lines. A Unique skill, one of the starting skills; it comes with the **Rune Codex**, a book showing
every rune and what it does (right-click to read it; a lost one is crafted from a book, a paper and an ink sac).

**Drawing a rune.** With **paper** in your inventory, press the Rune Magic key: a sheet of 25 dots opens. Press on a
dot and drag through the others to draw lines (straight across, down or diagonal); lift and press again for another
stroke. Undo, Clear, and Codex (look up a rune) are there; **Inscribe** when it's done. If your lines match a rune
exactly (in any order or direction), the paper becomes that **Rune Paper** (30 magicules); if not, you keep the paper.
The sheet shows the rune's name once your lines match.

**Using a rune paper.**

- **On a creature:** right-click it. Foes get the harmful side, you and your allies the helpful one.
- **On yourself:** right-click the air.
- **On a weapon:** right-click the rune paper onto a weapon in your inventory (or the weapon onto the paper), or hold
  the weapon and use the paper from your off hand. The weapon's hits carry the rune for 32 hits (shown on its
  tooltip).

| Rune | On a foe | On you or a friend | On a weapon |
|---|---|---|---|
| Fire | burns and scorches | fire resistance (3 min) | sets what it hits alight |
| Frost | freezes and slows hard | puts out fire, resistance (1 min) | slows and chills |
| Thunder | lightning strike | speed and haste (1 min) | every 4th hit calls lightning |
| Life | heals and regenerates (anyone) | heals and regenerates | heals you for part of the damage |
| Guard | weakness | extra hearts and resistance | each hit gives you a little shield |
| Wind | blown up and away | speed, jumps, slow falling (1 min) | knocks far back |
| Bind | rooted in place (5 s) | cures bad effects | briefly roots |
| Sight | glows through walls (1 min) | night vision (3 min) | makes what it hits glow |

**Runes without the skill.** Anyone can use runes, the slow way. **Rune Tomes** turn up in loot chests all over the
world (dungeons, temples, mineshafts, strongholds, ancient cities, Tensura's chests and other mods': any chest loot);
right-click one to **learn its rune for good** (you also get a Rune Codex, which shows only the runes you know).
Sneak and right-click the codex (with paper on you) to open the drawing sheet. You can only draw runes you've
learned, and without Rune Magic a drawn rune takes **2 minutes to finish inscribing** (the paper shows the time left
and can't be used until it's done; it keeps counting in your inventory). Chests also sometimes hold a ready rune
paper. Rune Magic users know every rune and draw them instantly.

Numbers are in the `[runes]` section of the server config (`drawMagicules`, `weaponCharges`, `runePower`,
`inscribeSecondsWithoutSkill`, `runeTomeChestChance` 15%, `runePaperChestChance` 10%).

## EP scaling

Every skill this addon adds, Tensura's own skills and magic, and Combat Mode's punches hit harder the more EP you
have. It covers every attack each
skill has: spells, cards, talismans, runes, Hell Storm and its burn, Spirit Control's spirits, Yifa's spirits and
jutsu, Shikigami Control's beasts and block golems, and creatures you release from a Sealing Grimoire or summon with
Soul Reaper (theirs scales with *your* EP). The multiplier goes up by the same step each time your EP
grows tenfold:

| Your EP | Damage |
|---|---|
| 100 or less | 1x |
| 1,000 | 1.6x |
| 10,000 | 2.2x |
| 100,000 | 2.8x |
| 1,000,000 | 3.4x |
| 10,000,000 | 4.0x |

It tops out at 10x. The base EP, the step and the cap (or turning it off) are in the `[epScaling]` section of the
server config.

**Tensura's own skills and magic scale the same way** when a player uses them: anything dealing one of Tensura's
damage types (Black Lightning, the elemental magics, breaths, aura slashes, Megiddo, gravity...) or hitting through
one of Tensura's projectiles, beams or magic fields. Monsters' skills aren't scaled (that would make high-EP bosses
far deadlier), and reflected damage isn't either.

## Combat Mode

A melee fighting style you can turn on and off: press **G** or use `/combat`. "Combat" shows under the crosshair
while it's on. Its keys (all rebindable in Controls, under Tensura: Fragments): **G** on/off, **H** block (hold),
**J** grab / throw, **K** dash, **Y** next fighting style.

- **Boxing stance and hooks:** while Combat Mode is on (and you're not holding a weapon or tool) you stand in a
  boxing guard, and every punch is a **right or left hook**, whichever hand threw it, with your body twisting into
  it; the Brawler's jumping punch is a real **uppercut**. Everyone around sees it. (Animations from the Punch Swish mod; you don't need that mod installed.)
- **EP:** your punches, slams and throws hit harder the more EP you have (see EP scaling above).
- **Combos:** your hits chain into a combo (shown as x1, x2, x3 by the crosshair) as long as each comes within 1.5
  seconds of the last. Your punches alternate between your right and left hands (unless you're holding a weapon or
  tool); every hit steps you in a little and leaves a **white swish** in the air: a solid
  white pixel arc that sweeps across in front of you (guards and slam shockwaves are drawn the same way).
- **Hit stun:** whatever you hit is stunned for a moment (about half a second): it can't move or hurt anyone. Combo
  hits barely knock it back, so you can keep going. It works in the air too: something stunned in mid-air hangs
  there, sinking slowly, instead of falling, and each punch you throw in mid-air keeps you up as well, so you can
  juggle it.
- **Finisher:** the 4th hit of a combo (x4!) hits much harder (1.5 times, plus 3) and launches the target up and
  away, with a big swish and a crack.
- **Uppercut:** **jump and punch** on the way up to throw an uppercut: it hits a bit harder, launches the target
  straight up into the air (higher still if it's your finisher) and stuns it a little longer, with a rising swish.
  **You go up with it**, just below it, ready to keep hitting it in the air. No fall damage on the way back down.
- **Block:** hold **H** to put your guard up ("Blocking" under the crosshair): your forearms come up crossed in
  front of your face (in first person too, and everyone around sees it). Hits from the front do a quarter of
  their damage, barely knock you back and don't stun you; you shuffle slowly and can't punch while blocking. Block
  **just as a hit comes** (within a third of a second) to **parry** it: no damage at all, and the attacker is stunned.
  A combo finisher **breaks** a guard: full damage, and the blocker is stunned.
- **Grab:** press **J** to grab the creature (or player) you're looking at, within reach. You hold it up in front of
  you with both arms out (seen in first and third person), stunned, for up to 2 seconds; press **J** again or punch to **throw** it, hard, forward and up. A grab goes
  straight through a block. Bosses and very big creatures can't be grabbed, and neither can your allies.
- **Dash:** press **K** to dash a few blocks the way you're moving (forward if you're standing still), on the ground
  or in mid-air. For the first moment of the dash (8 ticks) **nothing can hurt you** (invulnerability frames), so
  dash through an attack. One dash a second.
- **Down slam:** in mid-air, **sneak and attack** to dive straight down. Landing sends out a shockwave (white ring,
  dust, a thud) that hurts and knocks up everything within 4 blocks, harder the higher you came from, and stuns it.
  You take no fall damage from a slam.

- **Down power:** every hit also fills the target's **down gauge** (the bar next to your combo count). When it fills
  up, the target is **knocked down** ("DOWN!"): dropped out of the air, the stun ends, and for a moment
  (1.5 seconds) it can't be stunned, juggled or grabbed and takes half damage from Combat Mode hits. That's what
  stops infinite combos: a single combo string doesn't fill it, a second one does. The gauge empties if the target
  goes 2 seconds without being hit.

### Fighting styles

Press **Y** (or `/combat style <name>`) to switch styles. Each changes your punches and what your moves do (H block
works the same in all of them):

| | Brawler | Swift | Titan | Ki |
|---|---|---|---|---|
| Punches | normal | faster, 0.8x damage | slower, 1.35x damage, more knockback | 0.9x damage |
| Finisher | 4th hit: launch | 5th hit: **whirlwind**, hits everything around you | 3rd hit: **ground pound**, flings it and a shockwave hits those around it | 4th hit: **ki blast**, a beam that throws it far |
| Jump + attack | **uppercut**, you ride up with it | **spin kick**, the target and everything around you | **hammer fist**, spikes it out of the air or bounces it off the ground | **ki palm**, blasts it straight away |
| Sneak + attack in mid-air | **down slam** | **dive kick**, kicks down at a slant (homing in on the nearest enemy in front of you, within 7 blocks), spikes the first thing it meets (even as you land beside it) and bounces you off | **meteor slam**, a bigger, harder slam | **ki bomb**, a blast at the ground below while you hang in the air |
| J | **grab & throw** | **counter stance**: a hit in the next moment is turned aside, and you appear behind the attacker and strike | **tackle**: charge forward with super armour, bowling over everything | **ki burst**: knocks down everything around and throws it off; works even while you're stunned or held, so it **breaks combos** (10 s cooldown) |
| K | **dash** with i-frames | **quick step**: a short dash, ready again almost at once | **iron body**: 2 seconds of no stun, no knockback and 40% less damage | **vanish**: reappear behind what you're looking at (up to 16 blocks), untouchable for a moment |

Your allies and their creatures are never hit by it. Numbers (and turning it off for the whole server) are in the
`[combat]` section of the server config.

## Race: Spirit

A new race, picked from Tensura's race selection menu (as **Lesser Spirit**), built on Tensura's own spiritual-form
and Possession systems, and modelled on its Lesser Daemon.

- **Born in the Spirit Realm**, a new dimension of moss-covered calcite islands floating in a pale sky. You spawn and
  respawn there in your own form, carrying the **Book of Passage** (it never drops; you get it back on respawn).
- **Read the book** to cross into the overworld as a bodiless spirit (Tensura's spiritual form). A clock runs above
  your hotbar: **5 minutes** to take a body with Tensura's **Possession** skill (every spirit has it: look at a
  weakened creature within 5 blocks; you take on that body's health and stats). Take one and the clock stops: you
  can stay. Run out and your spirit fades: you die and wake up in the Spirit Realm, in your own form again. Reading
  the book in the overworld takes you home early. Spirits don't lose magicules in spiritual form (Tensura drains
  them from other spiritual forms outside its spirit dimensions).
- **Race ability (R):** toggle flight, in any form.
- **Spirit Release** (a skill only spirits have, at every stage): while you're possessing a body, unleash your own
  spirit's power through it instead of being held to the body's, wrapped in Tensura's Demon Lord Haki aura, which
  grows and changes colour with the mode: **blue** at 10%, a shifting **rainbow** at 50% and Tensura's own **purple**
  at 100%. Pick a mode:

  | Mode | Stats (attack, max health, armor, toughness, speed, attack speed) | Lasts | When it ends |
  |---|---|---|---|
  | 10% | +10% | 5 minutes | the body loses half its health |
  | 50% | +50% | 5 minutes | the body loses half its health |
  | 100% | doubled | 2 minutes | your spirit tears free of the body (back to spiritual form, and the clock starts again) |

  It needs a body you possessed out in the material world (not in the Spirit Realm).

  Press it again to end it early, at the same cost. It needs a minute to recover afterwards. Durations and the
  cooldown are in `[spiritRace]` (`releaseSeconds`, `fullReleaseSeconds`, `releaseCooldownSeconds`).
- Spirits learn magic as they grow, the same spells as Tensura's daemons at each stage.

| Stage | Race | How | Brings |
|---|---|---|---|
| 1 | Lesser Spirit | Race menu | Possession, Magic Resistance, Spirit Release |
| 2 | Greater Spirit | 20,000 EP | Stronger stats |
| 3 | Flame, Water, Wind, Earth, Light, Darkness or Space Spirit (your choice) | 140,000 EP | That element's Transform skill and attack resistance |
| 4 | Heroic Spirit | Awaken as a True Hero | Holy Attack Nullification, Spiritual Attack Resistance, Divine Ki Release; keeps its element |
| 4 | Demonic Spirit | Awaken as a True Demon Lord | Darkness Attack Nullification, Spiritual Attack Resistance, Drain; keeps its element |

Until awakening a spirit is Chaos-aligned, so either awakening is open to it. Stats follow Tensura's Daemon line at
the same stage. `spiritRaceInMenu` and `secondsWithoutBody` (300) are in the `[spiritRace]` section of the server
config.

## Named companions

Name anything you summoned (a shikigami, a paper beast, a Soul Reaper soul, summoned or a creature your souls
possessed, or a creature released from your Sealing Grimoire) any of three ways:

- **Tensura's own Naming** (its naming key while looking at it). Your summons (and your allies') always accept, and you
  don't need more EP than them; the naming costs magicules as usual and gives Tensura's naming rewards.
- A **name tag** renamed in an anvil: right-click it.
- A **blank name tag**: right-click it, then type the name in chat (it isn't sent to anyone).

The summoner or one of their [allies](#allies) can name it (it stays the summoner's); nobody else can. Once named,
it's theirs for good:

- no time limit (it doesn't pass on, go back into the book, or turn back into its block or paper);
- recalling, dismissing (sneak) and summon limits leave it alone and don't count it;
- it stays with you: it catches up when it falls more than 24 blocks behind (unless it's in a fight), follows you to
  other dimensions and back after you die, and leaves with you when you log off, coming back when you log in.

Only being killed ends it.

**Follow, stay or wander.** Sneak and right-click a named companion with an empty hand to switch what it does
(you or an ally can):

- **Follow** (the default): it comes with you, as above.
- **Stay**: it keeps still where it is and stays out of fights, and doesn't come to you, even when you change
  dimension or log off (it's saved there with the world).
- **Wander**: it roams around that spot (about 10 blocks), fights what you hit only near there, and otherwise
  stays there like Stay.

This works for everything named with a name tag or Tensura's Naming. Any creature you name with Tensura's Naming
(that isn't one of Tensura's own monsters) becomes your named companion this way: it only attacks what you hit and
never you. Tensura's own monsters keep Tensura's own subordinate commands.

## Tensura's Naming works on any creature

Tensura only lets you name creatures on its own list (its own monsters and a few animals). Here its Naming works on any
creature, vanilla or modded; its other rules still apply (it has to submit to you: be your subordinate, weakened,
afraid of you, or much weaker than you; you need the EP; it can't already have a name).

## No EP lost to dying or naming

Players keep their max EP when they die (Tensura normally takes `epDeathPenalty` percent, 5% by default) and when they
name something (Tensura's Naming normally has a chance to take from your maximum magicules). Naming still spends the
magicules you have at the time, and dying still cuts soul points as usual.

## Allies

To become allies with another player, **sneak and right-click them with an empty hand**. They get a message; when
they sneak and right-click you back (or click **[Accept]** in chat), you're allies. It's saved with the world, so it
lasts until one of you ends it.

- `/ally <player>`: the same as the right-click (ask, or accept their request)
- `/ally list`: your allies
- `/ally remove <name>`: stop being allies

Allies can name each other's summons, and summons never attack an ally or an ally's summons and tamed animals (even
if you hit them by accident).

**Riding allies.** Right-click an ally with an empty hand (not sneaking) to climb onto their shoulders; you go wherever
they go, flying included. One rider at a time, and no stacking. **Sneak** to get off (careful in mid-air: you fall).
The one carrying can let you off with `/ally drop`. Logging off while riding or carrying lets the rider off first.

## Summons only attack what you hit

Every summoned or bound creature (shikigami, paper beasts, Soul Reaper's summoned and possessed creatures, the
Sealing Grimoire's released creatures) only ever attacks what **you** hit: with your hands, a weapon, a projectile
or a skill. Not what attacks you, not what attacks it, and not what it would naturally hunt. They keep after it
until it dies, you hit something else, or 30 seconds pass without you hitting it; otherwise they follow you.
(Spirit Control's spirits already strike only the target of your attack.)

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

The in-world tests (including every Spell Card, chains and combos) in `src/main/java/com/tensurafragments/test/` cover every skill: card blasts (range, self damage,
chaining), teleporting, the skill icons, turning blocks into shikigami (and needing paper), block hardness scaling,
dismissing, substitution (timed and mistimed), talisman blasts, the barrier keeping out mobs and arrows, and the
grimoire (making it from a book, sealing only weakened creatures, released creatures serving and returning, catching
and re-firing magic, bosses being unsealable), Rainbow Magic with real Tensura spells, all ten Flame Emperor fire
spells, Draconic Hell Storm (charge, damage, and a burn that can't be cured), Energy Magic (paying in levels, twice the
damage, not casting without the levels, every spell casting), paper beasts (folding each one, paper cost,
possession steering the hound and owl, the world following a possessed beast, attacking what the beast faces, snapping
back when hurt, fire tearing paper), Rune Magic (every rune matching however it's drawn, wrong drawings keeping the paper, runes on foes and on
yourself, a weapon carrying a rune and using charges, Rune Tomes teaching runes, drawing without the skill only
learned runes and waiting 2 minutes, chest loot holding tomes and rune papers), Combat Mode (combos counting up and stunning, the finisher hitting harder and launching, a jumping punch uppercutting straight up and carrying you with it, stunned
creatures hanging in mid-air, mid-air hits keeping you up, blocks and parries, grabs and throws (and grabs breaking
blocks), dashes being untouchable and then on cooldown, down power knocking down after a combo and a bit (no stun or
grab while down), each style's damage and finisher, Swift's counter, whirlwind and dive kick, Titan's tackle, iron
body and hammer fist, Ki's burst (breaking a stun), palm, vanish and bomb, stunned creatures
unable to hurt anyone, the down slam's shockwave, and nothing happening with it off), the starting skill choice (one pick, Rune Magic bringing the
codex, players with earlier skills keeping them), named companions (naming only your own or an ally's summons, with Tensura's Naming (any creature, summons always
accepting) or a name tag (anvil-named or typed in chat), no timer, ignoring recall and
dismissal, catching up, leaving and coming back with you, sneak-clicking through follow, stay and wander, staying
put and staying behind on log-off, wandering near its spot, Tensura-named creatures becoming companions), allies (asking each other, allies naming each other's
summons, summons never going after an ally, riding an ally but not a stranger, one rider at a time, letting off,
and a rider logging off not taking the carrier with them), naming never taking from your max EP (with the chance forced to 100%),
Spirit Control (every spirit hurting its target and vanishing, the turn order, the spam limit, Spirit Link, sparing
allies) and Soul Reaper (souls from kills without being a Seed, summoning, each soul being usable once (even in creative), recalling, kills by your souls counting,
player souls, absorbing EP, possession taking over and stacking, too-weak souls failing, souls and grimoire creatures
with brain-driven AI staying loyal) and the Spirit race (the races and the Spirit Realm loading, Possession and the
book, the clock and fading, a body stopping the clock, the whole evolution line with the hero/demon lord split,
and Spirit Release: needing a body, the boost, half health at the end, losing the body at 100%, ending early and
the cooldown).

`./gradlew runClient -PvisualCheck` opens a world called `visualtest` (copy any world into `run/client/saves/`),
casts Energy Magic spells, summons souls with Soul Reaper, becomes a Lesser Spirit in the Spirit Realm and reads the
Book of Passage, saves screenshots to `run/client/screenshots/`, and quits.

Tensura: Reincarnated, ManasCore, Architectury, GeckoLib, SmartBrainLib and TerraBlender (runtime only) are pulled from CurseForge via
CurseMaven (versions in `build.gradle`). If you update Tensura, bump the file id in `tensuraDep`.

The card place sound is `pixelcrusher-squish-pop-256410.mp3` from the repo root, converted to Ogg.
