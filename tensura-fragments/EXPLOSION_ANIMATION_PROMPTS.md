# Explosion style: Blockbench prompts

One prompt per animation for the Blockbench project's Claude. Paste the **shared part** first, then the animation's
own prompt. The names, modes and strike times must stay exactly as written: the mod already looks for these names, and
each move's blast goes off at the animation's `"strike"` time. Until an animation exists, the mod plays a similar one
from the other styles.

## Shared part (paste above every prompt)

```
Add a new player animation to the combat animation kit, in the same pipeline as the existing ones (read CLAUDE.md
first; don't change any existing animation, swish, shockwave or combat effect).
Bake it from Blockbench in the kit's format: 40 fps, parts head, body, right_arm, left_arm, right_leg, left_leg as
[px, py, pz, qx, qy, qz, qw] frames, keys "length", "fps", "mode" and, where given, "strike" (seconds: the exact moment
the blast goes off; keep it in the exported JSON). Add the file to combat_animations/ and its name to index.json.
The character fights with explosions from his palms (sweat that explodes): sharp, aggressive, wide stances, open palms
facing the target when he blasts, with a strong recoil from each blast.
If the prompt asks for effects, model them in player_moves.bbmodel as flat planes on the part named, timed to this
animation, and export them into combat_fx.json the same way as the other move effects. Explosion effects may use
explosion colours (white-yellow core, orange and red flame, grey-black smoke) instead of the pale blue palette.
```

## Stance and punches

**1. explosion_idle** — mode `loop`, length 1.2 s
```
explosion_idle: the Explosion style's guard. Feet wide, leaning forward, both hands low at the hips with palms half
open and fingers curled like claws, a cocky tilt of the head. Small crackling sparks pop in each palm now and then.
Effects: tiny orange spark pops on right_arm and left_arm at random moments, 2-3 per loop.
```

**2. explosion_blast_right** — mode `once`, length 0.45 s, **strike 0.15**
```
explosion_blast_right: a right-hand palm blast. The right arm snaps forward with the palm open toward the target,
body twisting into it; at 0.15 s the palm fires and the recoil throws the arm and shoulder back, then back to the guard.
Effects: on right_arm, a palm flash at 0.12-0.18 s and an orange blast cone with smoke shooting forward from the palm
from 0.15 to 0.35 s (about 1.5 blocks long).
```

**3. explosion_blast_left** — mode `once`, length 0.45 s, **strike 0.15**
```
explosion_blast_left: the mirror of explosion_blast_right with the left hand (same timing, strike 0.15 s, same effects
on left_arm).
```

**4. explosion_finisher** — mode `once`, length 0.9 s, **strike 0.35**
```
explosion_finisher: the combo finisher. Both hands come together in front of the chest, wind back to one side, then
thrust both palms forward point-blank; at 0.35 s a huge double blast fires and the recoil skids the body back a step,
arms flung up, then recovers to the guard by 0.9 s.
Effects: on body, a bright white flash at the palms at 0.33-0.4 s and a large orange explosion with a black smoke ring
expanding forward from 0.35 to 0.7 s (about 3 blocks wide).
```

## Air moves

**5. explosion_rising_blast** (jump + attack) — mode `once`, length 0.6 s, **strike 0.2**
```
explosion_rising_blast: an upward blast from below. Crouch slightly in the air, then swing both palms upward from the
hips; at 0.2 s they fire up and the body rockets upward after it, legs trailing, then settles into an airborne guard.
Effects: on body, a blast column of orange fire and smoke shooting upward from the palms from 0.2 to 0.45 s (about
2 blocks tall), plus small blasts under each hand pushing him up (right_arm and left_arm) from 0.2 to 0.35 s.
```

**6. explosion_dive** (sneak + attack in the air) — mode `loop`, length 0.4 s
```
explosion_dive: rocketing down head first at an angle, both arms pointed back like thrusters, palms firing behind him.
A tight looping pose with a little shake.
Effects: on right_arm and left_arm, looping thruster blasts of orange flame and smoke streaming back from the palms
(4 frames, 0.2 s loop).
```

**7. explosion_dive_land** — mode `once`, length 0.7 s
```
explosion_dive_land: landing the dive. He hits the ground in a three-point crouch with both palms slamming down, then
stands up into the guard by 0.7 s.
Effects: on the floor, a big orange explosion dome with a smoke ring at the feet from 0.05 to 0.6 s (about 5 blocks
wide).
```

**8. explosion_hover** (hold jump in the air) — mode `loop`, length 0.6 s
```
explosion_hover: hovering on blasts. Both arms pointed down and slightly back, palms firing in rhythm, legs bent and
swaying, body leaning forward as if flying. Loops smoothly.
Effects: on right_arm and left_arm, alternating downward blasts from the palms (orange flash, smoke puff) in a 0.6 s
loop.
```

## Grab and dash

**9. explosion_grab** (J) — mode `hold`, length 0.45 s
```
explosion_grab: a fast lunge, the right hand clamps onto the target's face/collar at 0.2 s while the left hand pulls
back, palm sparking. Holds the last frame.
Effects: on left_arm, crackling sparks in the palm from 0.2 s on.
```

**10. explosion_hold** — mode `loop`, length 1.0 s
```
explosion_hold: holding what he grabbed out in front with the right hand, the left palm cocked back beside his head
and crackling, grinning, ready to blow it up. Loops.
Effects: on left_arm, looping crackling sparks and small flame flickers in the palm.
```

**11. explosion_grab_blast** (J or attack again while holding) — mode `once`, length 0.7 s, **strike 0.3**
```
explosion_grab_blast: the left palm slams into what he's holding; at 0.3 s it detonates point-blank, the right hand
lets go, and the recoil throws both arms out wide; back to the guard by 0.7 s.
Effects: on body, a big orange explosion with black smoke in front of him from 0.3 to 0.6 s (about 2.5 blocks wide).
```

**12. explosion_burst_dash** (K) — mode `once`, length 0.35 s
```
explosion_burst_dash: blasted forward. Both arms fling back with palms firing behind him, body stretched forward in a
streak, then he catches himself. It's used in any direction (up and down too), so keep it a clean forward streak.
Effects: on the starting point (start), an orange blast with smoke where he leaves from 0-0.25 s, and on right_arm and
left_arm short thruster flares from 0-0.2 s.
```

## The skill key

**13. explosion_ap_shot** (AP Shot) — mode `once`, length 0.7 s, **strike 0.3**
```
explosion_ap_shot: a focused long-range shot. He braces his right wrist with his left hand, right arm straight out
aiming, palm forward; at 0.3 s a narrow piercing blast fires and the recoil kicks his arm up; back to the guard by
0.7 s.
Effects: on right_arm, a charge glow in the palm from 0.1 to 0.3 s, then a thin bright orange beam with a white core
shooting forward from 0.3 to 0.5 s (about 6 blocks long), with a ring of smoke at the palm.
```

**14. explosion_stun_grenade** (Stun Grenade) — mode `once`, length 0.7 s, **strike 0.35**
```
explosion_stun_grenade: both hands come together in front of the face, palms cupped toward the enemy, eyes shut and
head turned away; at 0.35 s a blinding flash goes off from the palms; he lowers his arms by 0.7 s.
Effects: on body, an expanding pure white flash in front of the hands from 0.35 to 0.55 s (about 3 blocks wide), with
short white rays.
```

**15. explosion_howitzer** (Howitzer Impact) — mode `once`, length 1.4 s, **strike 0.9**
```
explosion_howitzer: the ultimate. He launches up, then spins horizontally like a drill with both arms out and palms
firing behind him (two full turns from 0.1 to 0.8 s), then throws both palms forward together and at 0.9 s releases a
huge explosion; he hangs in the air in the recoil and recovers to the guard by 1.4 s.
Effects: on body, a spiralling ring of small orange blasts and smoke around him while he spins (0.1-0.8 s), then a
massive orange-white explosion with a thick black smoke ring in front of him from 0.9 to 1.3 s (about 6 blocks wide).
```

## Optional: hit clips

```
Add two triggerable effect clips for hits (like fx_hit_spark): fx_blast_small (a small orange explosion with smoke on
the target's chest, 0.8 blocks, 0.35 s) and fx_blast_big (a big one, 2.5 blocks, 0.6 s). Export them into
combat_fx.json's clips like fx_hit_spark.
```
(Send these too and I'll trigger them on blast hits, alongside the smoke and fire the mod already shows.)

When they're done, send me the updated kit zip and I'll install it. The new animations take over from the stand-ins
straight away, and each blast will follow the animation's strike time.
