# Peerless Skills - Renewed

A Starsector 0.98a mod by Oddisz. Your own fourteen combat skills get a third level past Elite:
**Peerless**. You buy it with Peerless skill points on the Second-in-Command Character screen (C).

Requires LunaLib, LazyLib, PatchLib and Second-in-Command. Achievements - Renewed and Console Commands
are optional. Do not run it together with the first Peerless Skills mod.

## Peerless skill points

- The Character screen shows "-Peerless skill points: N" under your level. Hover it for where points
  come from and the effect mode. Save and Cancel appear under the Combat icon while you have changes.
- Click an Elite skill to make it Peerless, then press Save. Each costs one point.
- Click a Peerless skill to undo it and get the point back. A skill that loses Elite (a respec, a
  Second-in-Command reset) also loses Peerless, and the point comes back.
- Peerless skills have a gold border and a gold diamond instead of the Elite stars. Their tooltip has a
  Peerless section after Elite.

## Where points come from

- **Achievements** (with Achievements - Renewed): reaching the goal of a listed achievement in this
  campaign pays its points, once per campaign. Achievements are kept for the whole profile, so the mod
  checks the campaign's own progress, not whether the achievement was ever done. The list is
  `data/config/peerless_renewed/achievement_points.csv`: 14 base game achievements (level 15, At The
  Gates, the Ziggurat, the planetkiller, Abyssal Space Oddity, Shrouded Dwellers, Omega, Threat, 10
  Remnant wins, every star system, 200 wins, five colonies, a million credits, beating a fleet twice your
  size) and 10 big story ends of other mods. A full campaign that does everything earns enough for all
  fourteen skills; a normal one earns 1 to 4.
- **Experience** (the default without Achievements - Renewed, or by choice): every few full experience
  bars past the level cap make a point (4 by default).
- The **point rate** setting multiplies every point earned. Fractions add up until they make a point.

## Effects

LunaLib setting **Peerless effects**: Renewed (default) or Original. Effects apply to the ship the
character pilots, like the base skills.

| Skill | Renewed | Original |
| --- | --- | --- |
| Helmsmanship | Accelerating backwards is twice as fast. | System use gives a speed burst of +100% top speed (at most +100) fading over 1 s, with doubled acceleration and turning. |
| Combat Endurance | Kept: once per battle, at 10% hull the hull cannot drop further for 5 s. | same |
| Impact Mitigation | Every projectile or missile that hits your armor or hull deals 20 less damage, before armor reduction. | +100% ship mass. |
| Damage Control | Kept: engines and weapons repair faster the higher your combat readiness. | same |
| Field Modulation | Kept: the first overload each battle is cancelled, the ship vents if it can, +200% maneuverability fading over 5 s. | same |
| Target Analysis | Every enemy ship starts with a weak spot on its outer edge: 1 to 4 neighboring armor cells (frigate to capital) with no armor. | Hits on armor make it decay over 5 s (10% of the hit, at most 100). |
| Systems Expertise | System use gives +50% top speed and maneuverability, fading over 5 s. | System use halves hull damage for 0.25 s, fading out by 0.5 s. |
| Point Defense | PD hits on missiles have a 10% chance to disarm the warhead. | PD hits on armor or hull add EMP equal to 10% of their damage. |
| Energy Weapon Mastery | Energy weapons deal 25% more damage to armor. | The energy damage bonus fades out by 2000 range instead of 1000. |
| Ballistic Mastery | Kept: while firing, weapons build up to perfect autofire accuracy and no recoil over 5 s. | same |
| Gunnery Implants | Ballistic and energy weapons with less base range than your longest-range one gain 10% of that base range on top of their final range. | +5% maximum fleet electronic warfare penalty. |
| Ordnance Expertise | Weapons cost 1 / 2 / 4 fewer ordnance points (small / medium / large). | +0.75 flux dissipation and +10 capacity per ordnance point spent on hull mods. |
| Polarized Armor | After 3 s without armor or hull damage, armor rebuilds to 50% of each cell's full value. | Engines and weapons repair twice as fast while venting. |
| Missile Specialization | Guided missiles turn 50% faster; unguided ones slowly steer toward a target. | Large launchers that ran dry reload 5% of their ammo per minute (at least 1). |

Ordnance Expertise (Renewed) lowers weapon costs only while you captain the ship: moving to another
ship can leave the old one over its ordnance point limit.

## Officers

Optional, LunaLib setting **Peerless for officers**: Off (default), Peerless points, Story points or
Credits. When it is on, an **Officers** button next to your Peerless skill points opens a window with
every officer in your fleet and their Elite combat skills (only the fourteen combat skills you can take
yourself; other skills officers can have are not listed). Click one to make it Peerless and pay its cost
(1 Peerless skill point, 2 story points or 100,000 credits by default; the last two can be changed).
Click a Peerless one to undo it and get back exactly what it cost. An officer whose skill loses Elite also
loses Peerless and gets the payment back. Officers with Peerless skills carry the same hidden skill as you;
with the setting Off their Peerless skills do nothing. The game's own officer screens show a Peerless
part in the skill's tooltip; the icons there keep the Elite stars, because only Second-in-Command's
Character screen is drawn by a mod.

## Console commands

- `PeerlessAddPoints [amount]` gives points (1 by default).
- `PeerlessStatus` shows points, Peerless skills and what has paid out in this campaign.
- `PeerlessUnlockAll` makes all fourteen combat skills Elite and Peerless, for testing.
- `PeerlessRefund` removes every Peerless upgrade, yours and your officers', and gives back what they cost.

## Removing the mod

The player keeps a hidden skill (`psr_peerless`) that carries the effects. Like other skill mods, do
not remove the mod from a running campaign.

## Building

`./gradlew releaseZip` compiles against the game, LunaLib, LazyLib, Second-in-Command, PatchLib's API
jar, Console Commands and `../Achievements - Renewed/jars`, runs the checks and writes
`PeerlessSkillsRenewed.zip` in this folder.
