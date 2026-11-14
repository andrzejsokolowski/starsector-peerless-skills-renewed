# Peerless Skills - Renewed

These instructions apply to this repository, including when it is opened without the parent mod workspace.
The workspace `AGENTS.md` one folder up still governs releases, packaging, Git identity and communication.

## Notion

Tasks, history and test lists: the **Peerless Skills - Renewed board** in Andrzej's Notion, under
**Starsector mods** (`collection://41820ca7-5d20-81f5-9fe3-000ba590f8ce`). Ticket ids start with PSR.
Read the board with `notion-query-data-sources` mode `view` on `view://41820ca7-5d20-8175-b136-000c9886ae2a`.
PSR-1 holds Andrzej's original request word for word.

## Build

`./gradlew releaseZip` writes `PeerlessSkillsRenewed.zip` here (staging folder `PeerlessSkillsRenewed`).

## How it hangs together

- `patches/` are PatchLib hooks into Second-in-Command 2.1.1: after `SCPlayerPanel.recreatePlayerPanel`
  (adds the points line under the level at y 246 and Save/Cancel under the Combat icon), a redirect of
  `VanillaSkillTooltip.addToTooltip` inside `recreateAptitudePanel` (our tooltip subclass),
  a Before on the skill-click Kotlin lambda matched by its 7 parameter types, and an
  After on `SkillWidgetElement.render` (gold border). Check these after a Second-in-Command update.
- The effects ride on a hidden npc_only skill `psr_peerless` kept at level 1 on the player
  (`PeerlessShipEffect`, `PeerlessCharacterEffect`, battle logic in `PeerlessShip`).
- State lives in sector persistent data under `psr_*` keys, plain Java types only.
- Achievements - Renewed is optional: only `points/AchievementPoints` may name it (build check).
  Achievements are profile-wide, so it pays when `Achievements.progress(id) >= goal` in this campaign.
