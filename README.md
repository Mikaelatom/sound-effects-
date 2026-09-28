# Tensura: Soul Gacha

An add-on for **Tensura: Reincarnated** (Minecraft 1.21.1, NeoForge).

## What it adds

### Soul Gacha Ticket
Right-click to roll a random **Unique Skill** you don't own yet. The pool is every Unique Skill in the game, including ones from Tensura itself and from other add-ons.

| Outcome | Default chance |
| --- | --- |
| **Jackpot:** the Unique Skill *Gambler* (announced to the server) | 2% (goes up with the Luck attribute) |
| **Astral Core** (a race-change item) | 5% |
| A random Unique Skill | the rest |

- **Pity:** after 50 rolls without the jackpot, Gambler is guaranteed.
- If there's nothing left to roll, the ticket isn't used up.
- Recipe: gold ingots in the corners, amethyst shards on the sides, paper in the middle.

### Race Gacha Ticket
Sneak and right-click to be reincarnated as a random race. The pool is every registered race, including Tensura's, this add-on's and other add-ons'. Your current race is never rolled. **This replaces your current race.**

| Rarity | Race difficulty | Default weight |
| --- | --- | --- |
| Common | Easy | 60 |
| Rare | Intermediate | 28 |
| Epic | Hard | 10 |
| **Legendary** (announced to the server) | Extreme | 2 |

- **Pity:** after 20 rolls without an Epic or Legendary race, the next roll is guaranteed to be one.
- By default only base races are rolled, not evolutions (`startingRacesOnly`).
- Recipe: a Soul Gacha Ticket in the middle, slime balls on the sides, diamonds in the corners.

### Unique Skill: Gambler
Press the skill key to spin three reels:
- **Three of a kind:** a strong buff (Strength, Regeneration, Resistance, Speed or Absorption, level III).
- **A pair:** a weaker level I version of that buff.
- **No match:** Slowness for 5 seconds.

Buffs last longer as the skill's mastery goes up. The cooldown is 30.

### Races: Astral Slime → Celestial Slime
- **Astral Slime** (Intermediate): no fall damage, +3 Luck, faster movement, higher jumps, −2 hearts. It evolves once you reach **XP level 30**.
- **Celestial Slime** (Hard): no fall damage, +7 Luck, +5 hearts, +3 attack damage, even faster, and it starts with **Gambler** as an intrinsic skill.
- To become an Astral Slime, sneak and right-click an **Astral Core**. You can get one from the gacha or craft it: slime balls in the corners, eyes of ender top and bottom, diamonds on the sides, and a nether star in the middle.

### Sound
The gacha, Gambler and Astral Core all play the squish-pop sound (`pixelcrusher-squish-pop-256410.mp3`, converted to `.ogg`).

## Config
Server config `tensuragacha-server.toml` (in each world's `serverconfig/` folder): `jackpotChance`, `astralCoreChance`, `pityThreshold`, `blacklist` (skill ids the gacha never rolls, e.g. `"tensura:great_sage"`).

Race gacha settings are in the `[raceGacha]` section: `weightEasy`, `weightIntermediate`, `weightHard`, `weightExtreme`, `startingRacesOnly`, `pityThreshold`, `blacklist` (race ids, e.g. `"tensura:human"`).

## Building
Requires Java 21.

```
./gradlew build
```

The jar ends up in `build/libs/`. Every push also builds on GitHub Actions: open the **Actions** tab, pick the latest run and download `tensuragacha-jar`.

Put the jar in your `mods` folder next to Tensura: Reincarnated (2.0.1.0+) and ManasCore (4.0.0.2+).

## Commands for testing
```
/give @s tensuragacha:soul_gacha_ticket 64
/give @s tensuragacha:race_gacha_ticket 64
/give @s tensuragacha:astral_core
```
