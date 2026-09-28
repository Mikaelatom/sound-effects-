# What we can reuse from Tensura: Reincarnated

This is collected from open-source 1.21.1 NeoForge Tensura addons, because the Tensura jar itself couldn't be
downloaded in the build environment. Run `./gradlew catalogTensuraAssets` locally to get the full list of textures,
sounds and models from the jar (`docs/tensura-assets.md`).

## Code we call

| What | Class / method | Used for |
|---|---|---|
| Skill base class | `io.github.manasmods.tensura.ability.skill.Skill` (`SkillType.UNIQUE`, `getModes`, `getModeId`, `nextMode`, `getMagiculeCost`, `onPressed`, `onHeld`, `onRelease`, `addMasteryPoint`, `instance.setCoolDown(seconds, mode)`) | Gambit Cards is a real Tensura skill: it shows in Tensura's skill menu and uses Tensura's skill keys and mode switching. Tensura charges `getMagiculeCost` when the key is pressed. |
| Skill registry | `io.github.manasmods.manascore.skill.impl.SkillRegistry.SKILLS` | Registering our skills |
| Granting skills | `io.github.manasmods.tensura.ability.SkillHelper.learnSkill(entity, skill)` | Giving players our skills |
| Player skills | `SkillAPI.getSkillsFrom(player)` → `getLearnedSkills()`, `getSkill(id)`, `forgetSkill(id, message)` | Removing the original skills |
| Magicules | `TensuraStorages.getExistenceFrom(entity)` → `getMagicule()` / `setMagicule()`; `EnergyHelper.getMaxMagicule`, `EnergyHelper.isOutOfEnergy` | Gauges |
| Particles | `TensuraParticleHelper.spawnServerParticles(level, particle, x, y, z)`, `TensuraParticleHelper.addServerParticlesAroundSelf(entity, particle, spread, count)`, `TensuraParticleUtils.getColorlessReversedWave(speed, size)` | Card blast shockwave, teleport ring |
| Damage | `TensuraDamageTypes` (e.g. `LIGHT_ELEMENTAL`), `skill.createSource(instance, entity, type, mode)` | Elemental skill damage (not used yet) |
| Effects | `TensuraMobEffects` (`SILENCE`, `FRAGILITY`, `STRENGTHEN`, `INSANITY`, ...), `TensuraMobEffect.addEffect(target, effect, source, skill, mode)` | Debuffs for later skills |
| Races | `io.github.manasmods.manascore.race.api.RaceAPI`, `TensuraRaces` | Races are kept as-is for now |
| Aim helper | `ObjectSelectionHelper.getTargetingEntity(entity, range, ...)` | Target under crosshair |

## Magic entities worth taking apart

Every one of these is a registered `tensura:` entity (a spell projectile, field or effect), which means it comes with
its own model, texture and renderer. These are the "parts" we can reuse for new skills.

- **Circles & blasts:** `magic_circle`, `explosion_circle`, `flare_circle`, `magic_explosion`, `electro_blast`, `black_lightning_blast`
- **Space:** `warp_portal`, `spatial_arrow`, `spatial_ray`, `space_cut_projectile`, `dimension_cut_projectile`, `dark_cube`
- **Fields & barriers:** `magic_shield`, `aura_shield`, `ranged_barrier`, `gravity_field`, `holy_field`, `haki_field`, `anti_magic_area`, `boss_barrier`
- **Fire:** `fire_ball`, `fire_bolt`, `fire_lance`, `fire_pillar`, `fire_storm`, `flame_orb`, `flame_sphere`, `hell_flare`, `hellfire`, `black_flame_ball`
- **Lightning:** `lightning_bolt`, `lightning_lance`, `lightning_sphere`, `thunder_lance`, `thunder_sphere`, `thunder_rain`, `black_lightning_bolt`
- **Water & ice:** `water_ball`, `water_blade`, `water_jail`, `frost_ball`, `ice_lance`, `ice_pillar`, `icicle_spike`, `blizzard`
- **Wind:** `wind_blade`, `wind_sphere`, `wind_tornado`, `death_tornado`
- **Earth:** `earth_pillar`, `earth_spike`, `earth_storm`, `stone_shot`, `boulder_shot`, `magma_shot`, `mud_hands`
- **Gravity & dark:** `gravity_sphere`, `darkness_cannon`, `disintegration`, `shadow_bind_hands`, `curse_bind_hands`
- **Aura / battlewill:** `aura_bullet`, `aura_slash`, `severer_blade_projectile`, `severance_cutter`
- **Light & holy:** `light_arrow`, `solar_beam`, `solar_grenade`, `megiddo_bubble`, `holy_water`
- **Mist & poison:** `predator_mist`, `gluttony_mist`, `sleep_mist`, `blood_mist`, `poison_ball`, `acid_ball`, `acid_rain`
- **Misc:** `kunai`, `thrown_item`, `marionette_lines`, `mad_orbs`, `float_sphere`, `plasmas_ball`, `reflector_echo`

Next step for the cards: once we can build against the jar, try spawning `tensura:magic_circle` under a fully
charged card and `tensura:magic_explosion` (or `explosion_circle`) as the detonation visual.
