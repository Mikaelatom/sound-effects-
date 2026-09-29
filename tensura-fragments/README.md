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

The HUD (bottom right) shows your deck, the next draw, and a timer bar per active card (gold = fully charged).

## Skill 2: Shikigami Control

Based on Seika from *The Reincarnation of the Strongest Exorcist*. A Unique skill, given to every player. **Paper**
(vanilla `minecraft:paper`) in your inventory is the ammo; creative players don't use any.

| Mode | What it does | Cost |
|---|---|---|
| **Shikigami** | Turn the block you're looking at into a helper that follows you and fights your enemies. Hard blocks make slow, tough, hard-hitting shikigami; soft blocks make quick, fragile ones. When it dies or its 2 minutes run out it turns back into the block (dropped as an item). Up to 3; a 4th replaces the oldest. **Sneak** to dismiss them all. | 1 paper + 50 magicules + 20 per point of block hardness |
| **Talisman** | Throw a paper talisman that explodes on impact (3-block radius). Same blast rules as the cards: it hurts you if you're close, and it sets off cards. | 1 paper + 20 magicules |
| **Barrier** | Plant a talisman anchor on the block you're looking at. **Sneak** to raise the barrier once you have 3 or more (placing the 6th raises it automatically), or to dispel it. While up, the wall pushes hostile mobs out and burns them, and destroys projectiles that aren't yours. Lasts 30 seconds. | 1 paper + 15 magicules per anchor, then 8 magicules/second |
| **Substitution** | Ready a paper doll for half a second. If you're hit in that window, the doll takes the hit: no damage, you lose 1 paper and blink 3 blocks away from the attacker. A good dodge is ready again at once; a mistimed one goes on a 2 second cooldown. | 1 paper when it triggers |

The HUD (bottom left) shows your paper, the Substitution doll (glows while ready, grey bar on cooldown), a health and
time bar per shikigami, and your barrier anchors (◇ placed, ◆ barrier up). Server owners who prefer a passive dodge
can set `autoSubstitution = true`. All numbers are in the `[shikigami]` section of the server config.

Uses Tensura's earth-cast, space-cast, barrier-break, uncast and golem sounds and its shockwave particle.

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
dismissing, substitution (timed and mistimed), talisman blasts, and the barrier keeping out mobs and arrows.

Tensura: Reincarnated, ManasCore, Architectury, GeckoLib, SmartBrainLib and TerraBlender (runtime only) are pulled from CurseForge via
CurseMaven (versions in `build.gradle`). If you update Tensura, bump the file id in `tensuraDep`.

The card place sound is `pixelcrusher-squish-pop-256410.mp3` from the repo root, converted to Ogg.
