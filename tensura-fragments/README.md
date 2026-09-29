# Tensura: Fragments

An addon for **Tensura: Reincarnated** (Minecraft 1.21.1, NeoForge). It removes the original Tensura skills and
builds new abilities out of their parts (visuals, sounds, effects). Tensura has to be installed; this addon ships
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
| **Shikigami** | Turn the block you're looking at into a helper that follows you and fights your enemies. Hard blocks make slow, tough, hard-hitting shikigami; soft blocks make quick, fragile ones. When it dies or its 2 minutes run out it turns back into the block (dropped as an item). There's no limit on how many you can have out (server owners can set one with `shikigamiLimit`). **Sneak** to dismiss them all. | 1 paper + 50 magicules + 20 per point of block hardness |
| **Spell Talisman** | Throw your selected spell talisman. It goes off the moment it touches the ground or a creature. **Sneak** to switch spell. See the spell table below. | 1 paper + the spell's magicules |
| **Barrier** | Plant a talisman anchor on the block you're looking at. **Sneak** to raise the barrier once you have 3 or more (placing the 6th raises it automatically), or to dispel it. While up it's a solid wall: mobs can't walk through it (anything inside when it goes up is pushed out, and hostile mobs caught inside burn), and projectiles that aren't yours are destroyed, even fast ones. You, your shikigami, your pets and other players can pass (`barrierBlocksAllMobs`, `barrierBlocksPlayers` to change). Lasts 30 seconds. | 1 paper + 15 magicules per anchor, then 8 magicules/second |
| **Substitution** | Automatic: while it's on and you have paper, every attack that hits you is taken by a paper doll instead. You take no damage, lose 1 paper and blink 3 blocks away from the attacker. Only real attacks count (mobs, players, projectiles, blasts), so burning, drowning and falling don't eat your paper. Use the mode to turn it **on/off** to save paper (on by default). | 1 paper per hit blocked |

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
| **Teleport 転** (purple) | Phase transfer: you appear where the talisman landed (up to 48 blocks away, half with leaves). Hurts no one. | 30 |

Costs scale with `spellCostMultiplier`.

The HUD (bottom left, only while Shikigami Control is on your active skill preset) shows your paper (or leaves once
the paper is gone), the Substitution doll (glows while it's on), a health and
time bar per shikigami, the selected spell talisman, and your barrier anchors (◇ placed, ◆ barrier up). All numbers are in the `[shikigami]` section of the server config.

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

## Skill 4: Rainbow Talismans

A Unique skill of its own, given to every player, and it doesn't need Shikigami Control. Throw the **rainbow version**
of any talisman spell (Explosive, Fire, Water, Wood, Lightning, Earth, Ice, Wind, Teleport). It has the same shape and
effect as the normal spell (rainbow Wood still roots, rainbow Wind still launches you, rainbow Teleport still moves
you), is drawn in rainbow colours, and every enemy it reaches also takes **every element's damage at once**: set on
fire, frozen and slowed together, plus 12 extra damage (half the elements' combined damage,
`rainbowDamageMultiplier`). **Sneak** to switch spell; it keeps its own selection, separate from Shikigami Control's.

Each throw uses 1 paper (or leaf) and twice the spell's magicules (`rainbowCostMultiplier`). The HUD (bottom left,
while it's on your active preset) shows the selected rainbow talisman.

## Original skills

By default every skill in the `tensura` namespace is removed from players (checked on login and every 2 seconds).
Races are untouched. Turn this off with `stripOriginalSkills = false` in the server config.

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
and re-firing magic, bosses being unsealable).

Tensura: Reincarnated, ManasCore, Architectury, GeckoLib, SmartBrainLib and TerraBlender (runtime only) are pulled from CurseForge via
CurseMaven (versions in `build.gradle`). If you update Tensura, bump the file id in `tensuraDep`.

The card place sound is `pixelcrusher-squish-pop-256410.mp3` from the repo root, converted to Ogg.
