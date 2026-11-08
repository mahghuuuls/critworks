# Changelog

## 1.1.0

### Added

- CraftTweaker scripts (CraftTweaker 4.1.20 or newer): `events.onCritworksHit` (or `mods.critworks.Events.onHit`) runs on the server for each critical hit, with the attacker, player, target, weapon, hand, kind, multiplier, guarantee flag, damage, and depth.
- Player calls for scripts: `grantCritGuarantee`, `removeCritGuarantee`, `critGuarantees`, `setCritBonus`, `removeCritBonus`, `blockNextCrit`, `getCritChance`, and `getCritMultiplier`.
- `config/critworks.cfg`: integration switch `crafttweaker` (default `true`).

### Compatibility

- CraftTweaker 4.1.20: a script bonus counts and shows like an addon's global bonus. A mistaken call does nothing and writes one error line to `crafttweaker.log` with the script file and line; the same mistake is reported once until the game restarts.

### Upgrading

- A `critworks.cfg` from 1.0.0 does not gain the `crafttweaker` key; a missing key means `true`. To turn scripts off, add `S:crafttweaker=false` under `integrations`.

## 1.0.0

First release.

### Added

- Critical chance and critical damage multiplier for every sword, axe, and bow, with a fallback of 15% and x2.00.
- One roll per attack: a melee swing with every target of its sweep, or one fired arrow. An arrow keeps the result and values it had when fired.
- Replacement of the vanilla jump crit and the vanilla arrow crit for the attacks Critworks supports.
- Player attributes `critworks.increasedCritChance`, `critworks.additionalCritChance`, `critworks.increasedCritDamage`, and `critworks.additionalCritDamage`.
- Weapon tooltips with the base chance and multiplier, and a Shift preview with the effective values and each bonus with its source.
- `config/critworks.cfg`: fallback stats, melee and bow charge thresholds, damage cap, PvP crits, crit sound, diagnostic mode, and integration switches.
- `config/critworks/weapons.json`: pack rules per item id, metadata, item data, or Spartan weapon type; `weapons.examples.json` as a reference.
- `/critworks inspect [player]` for operators.
- Diagnostic mode: one server log line per supported attack.
- Java API (`com.mahghuuuls.critworks.api`, version 1.0): item default stats, global bonuses, weapon-specific bonuses, guarantees, attack opt-out, other attackers, attack integrations, a critical-hit event, and resolved stats.

### Compatibility

- RLCombat: main-hand and offhand attacks use Critworks crits; RLCombat's crit roll and jump crit are replaced.
- Spartan Weaponry: built-in values per weapon type, type rules in `weapons.json`, and the longbow's own draw time.
- Just Another Food System: food properties `critworks:crit_chance` and `critworks:crit_damage`.
- Quality Tools: hand qualities count only for their own weapon.
- Setworks: set bonuses with `add` on the Critworks attributes count as global bonuses.
- Quark and Quark: RotN Edition: an icon row in weapon tooltips with Visual Stat Display.

### Known Issues

- Crossbows, thrown weapons, and arrows of non-player shooters are not supported.
- Damage changes that other mods make after Critworks' damage handler are not multiplied.
