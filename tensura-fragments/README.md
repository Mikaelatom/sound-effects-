# Tensura: Fragments

An addon for **Tensura: Reincarnated** (Minecraft 1.21.1, NeoForge). It removes the original Tensura skills and
builds new abilities out of their parts (visuals, sounds, effects). Tensura has to be installed; this addon ships
none of its assets.

Abilities are meant to take practice: momentum, placement, aim, time limits and gauge management.

## Skill 1: Gambit Cards

| Key (default) | Action |
|---|---|
| `Z` | **Throw** a card where you aim. It sticks to the first block or mob it hits and inherits your momentum. |
| `X` | **Teleport** to the card under your crosshair (or your newest card). You keep your speed, redirected where you look. |
| `C` | **Detonate** every card. Sneak + `C` detonates only the card you're aiming at. |

- **Deck:** 5 cards; one is drawn back every 3 seconds. At most 3 cards can be out; throwing a 4th makes the oldest fizzle.
- **Magicules:** throwing costs 40, teleporting costs 60 (Tensura's magicule pool).
- **Charge:** a card's blast grows from 50% to 150% over 5 seconds. It sparks and glows when fully charged.
- **Time limit:** cards fizzle after 20 seconds and blink during their last 3.
- **Blast:** damage falls off with distance and **hits you too** if you're inside the radius (4 blocks).
  The knockback also hits you, so a careful self-blast doubles as a launch. Blasts set off other cards nearby.

Everything above is tunable in `serverconfig/tensurafragments-server.toml`.

The HUD (bottom right) shows your deck, the next draw, and a timer bar per active card (gold = fully charged).

## Original skills

By default every skill in the `tensura` namespace is removed from players (checked on login and every 2 seconds).
Races are untouched. Turn this off with `stripOriginalSkills = false` in the server config.

## Building

Requires JDK 21.

```
./gradlew build          # jar in build/libs/
./gradlew runClient      # test client with Tensura + dependencies
./gradlew catalogTensuraAssets   # writes docs/tensura-assets.md, a list of every Tensura asset to pick from
```

Tensura: Reincarnated, ManasCore, Architectury, GeckoLib and SmartBrainLib are pulled from CurseForge via
CurseMaven (versions in `build.gradle`). If you update Tensura, bump the file id in `tensuraDep`.

The card place sound is `pixelcrusher-squish-pop-256410.mp3` from the repo root, converted to Ogg.
