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

Rarity comes from how far up its evolution line a race is. The game first picks a tier, then a random race from that tier.

| Rarity | Evolution stage | Examples | Chance |
| --- | --- | --- | --- |
| Common | Base race | Human, Slime, Goblin, Orc, Lesser Daemon | 60% |
| Rare | 1st evolution | Enlightened Human, Metal Slime, Hobgoblin, High Orc | 28% |
| Epic | 2nd evolution | Human Saint, Demon Slime, Orc Lord, Arch Daemon | 10% |
| **Legendary** (announced to the server) | 3rd evolution or higher | Divine Human, God Slime, Orc Disaster, Devil Lord | 2% |

- **Pity:** after 20 rolls without an Epic or Legendary race, the next roll is guaranteed to be one.
- You can move any race into a different tier in the config, for example to make Lesser Daemon Rare.
- Recipe: a Soul Gacha Ticket in the middle, slime balls on the sides, diamonds in the corners.

### Soul Market (shop)
Buy **any item in the game** with Soul Coins, including both gacha tickets, and sell items to earn them. Open it by right-clicking a **Soul Market** block, or anywhere with `/shop` if the server allows it.

- **Buy:** left-click an item to buy 1, right-click to buy 8, shift-click to buy a full stack.
- **Sell:** shift-click a stack in your own inventory, or hold it and type `/shop sell`. Selling pays 50% of the buy price, and damaged gear sells for less.
- **Browse:** use the arrows to change page and the hopper to filter by mod (All, this add-on, Tensura, Minecraft, others). `/shop search <name>` finds items by name.
- **Prices depend on rarity.** Raw materials have hand-set values (dirt 1, iron 64, diamond 1,024, nether star 32,768). Everything else is priced from its cheapest recipe, so prices follow crafting chains automatically, including other mods' items. Items with no recipe or base value are priced by their rarity. Buying, crafting and selling back can never make a profit.
- Creative-only items (command blocks, bedrock, barriers, spawn eggs...) aren't sold.
- Recipe: gold ingots around the edge, an emerald on top, amethyst shards on the sides and a chest in the middle.

| Command | What it does |
| --- | --- |
| `/shop` | Open the shop |
| `/shop search <name>` | Open the shop showing only matching items |
| `/shop sell` | Sell the stack in your hand |
| `/shop price` | Show the buy and sell price of the item in your hand |
| `/shop balance` | Show your Soul Coins |
| `/shop coins add\|set <players> <amount>` | Give or set coins (operators) |
| `/shop reload` | Recalculate every price (operators) |

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

Race gacha settings are in the `[raceGacha]` section: `weightCommon`, `weightRare`, `weightEpic`, `weightLegendary`, `pityThreshold`, `blacklist` (race ids the gacha never rolls, e.g. `"tensura:devil_lord"`), and `commonRaces` / `rareRaces` / `epicRaces` / `legendaryRaces` to move races into a different tier.

Shop settings are in the `[shop]` section: `allowShopCommand`, `sellMultiplier`, `priceMultiplier`, `allowSpawnEggs`, `blacklist`, and `valueOverrides` to set any price yourself, e.g. `"minecraft:diamond=2000"` or `"#minecraft:logs=4"`.

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
