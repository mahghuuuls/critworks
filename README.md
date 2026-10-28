# Critworks

Critworks is a critical strike system for Minecraft 1.12.2 Forge. Swords, axes, and bows get a critical chance and a critical damage multiplier. Players get four bonus attributes that armor, foods, set bonuses, and other mods can raise. Addons use a Java API to set defaults, give bonuses, grant guaranteed crits, and connect their own attacks.

- Player-facing description: [MOD-PAGE.md](MOD-PAGE.md)
- Pack makers (config files, units, formulas, attributes, integrations): [docs/pack-makers.md](docs/pack-makers.md)
- Addon authors (Java API): [docs/api.md](docs/api.md)
- Release history: [CHANGELOG.md](CHANGELOG.md)
- License: [MIT](LICENSE). Third-party notices: [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)

## Identity

- Mod id: `critworks`
- Minecraft 1.12.2, Forge 14.23.5.2847 or newer. Also runs on Cleanroom.
- Required on the server and on every client. The server decides eligibility, rolls, damage, guarantees, and events; clients show tooltips.
- No required dependencies.

## Configuration

Config files are read once at start. Every change needs a restart.

- `config/critworks.cfg`, section `general`: `fallbackChance` (15), `fallbackDamage` (2.0), `meleeChargeThreshold` (100), `bowChargeThreshold` (100), `damageCap` (0, no cap), `pvpCrits` (true), `critSound` (auto), `diagnosticMode` (false).
- `config/critworks.cfg`, section `integrations`: `rlcombat`, `jafs`, `quark`, `spartanweaponry`, `qualitytools`, each `true` by default and active only when its mod is installed.
- `config/critworks/weapons.json`: pack rules per item id (with optional metadata and item data) or per Spartan weapon type. They set eligibility, base chance, and base damage.
- `config/critworks/weapons.examples.json`: a reference file, rewritten on every start and never read.
- Player attributes: `critworks.increasedCritChance`, `critworks.additionalCritChance`, `critworks.increasedCritDamage`, `critworks.additionalCritDamage`. Only operation 0 (add) counts.
- `/critworks inspect [player]` (operator level 2) reports eligibility, base stats with their sources, bonuses, and pending guarantees.

The server sends its values and weapon rules to each client at login, so tooltips show the server's numbers.

## Compatibility

Every integration is optional and turns on by itself when its mod is installed. Setworks needs no Critworks code: its `add` attribute bonuses apply directly. Details are in [docs/pack-makers.md](docs/pack-makers.md).

| Mod | Version checked | Notes |
| --- | --- | --- |
| [RLCombat](https://www.curseforge.com/minecraft/mc-mods/rlcombat) | 2.2.4 | |
| [Just Another Food System](https://www.curseforge.com/minecraft/mc-mods/just-another-food-system) | 1.0.0 | |
| [Quark](https://www.curseforge.com/minecraft/mc-mods/quark) | r1.6-180 | checked against its source, not run in game |
| [Quark: RotN Edition](https://www.curseforge.com/minecraft/mc-mods/quark-rotn-edition) | r1.6-192 | |
| [Quality Tools](https://www.curseforge.com/minecraft/mc-mods/quality-tools) | 1.0.7 | |
| [Spartan Weaponry](https://www.curseforge.com/minecraft/mc-mods/spartan-weaponry) | 1.6.1 | |
| [Setworks](https://www.curseforge.com/minecraft/mc-mods/setworks) | 1.0.0 | no dependency |

Other versions of these mods may work, but they were not checked.

Other mods that add their own critical hits may stack with Critworks crits or conflict with them. Critworks does not turn them off. It only replaces the vanilla jump crit, the vanilla arrow crit, and RLCombat's crit, for the attacks it supports.

## Scope

- Supported attacks: player melee swings with their sweep, RLCombat main-hand and offhand attacks, player-fired bow arrows, attacks connected through the API, and melee hits of other attackers an addon allows.
- Not supported: crossbows, thrown weapons, arrows of non-player shooters, and damage over time.
- There is no Maven repository. Compile an addon against the release jar; only the package `com.mahghuuuls.critworks.api` is API.

## License

MIT, see [LICENSE](LICENSE). The build scripts come from CleanroomMC ForgeDevEnv under the MIT License; see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
