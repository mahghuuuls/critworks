# Critworks for pack makers

Critworks gives every sword, axe, and bow a critical chance and a critical damage multiplier, and gives players four bonus attributes that other mods can raise. This page explains the config files, the units, the formulas, the attribute names, the integrations, the supported attacks, and the known limits. It is written for pack makers and for coding agents that edit pack files.

Addon authors: see [api.md](api.md) for the Java API.

## Quick facts

- Minecraft 1.12.2 with Forge 14.23.5.2847 or newer. Mod id `critworks`.
- Required on the server and on every client.
- The server decides everything: eligibility, rolls, damage, guarantees, and events. Clients only show tooltips.
- Config files are read once at start. Every change needs a restart. There is no reload command.
- Critworks never rewrites a config file you have edited. It does rewrite `weapons.examples.json` on every start; that file is only a reference.
- Critworks replaces the vanilla jump crit and the vanilla arrow crit for the attacks it supports. Charge, jumping, and falling never make an attack critical by themselves. Only the roll or a guarantee does.

## Units

Every stat uses one unit everywhere: config, attributes, JAFS properties, tooltips, the inspection command, and the API.

| Stat | Unit | Example |
| --- | --- | --- |
| Critical chance (base and final) | percent, 0 to 100 | `15` is 15% |
| Critical damage (base and final) | multiplier, at least 1 | `2.0` is x2.00 |
| Increased chance | percent of the chance | `50` turns 10% into 15% |
| Additional chance | percentage points | `5` turns 10% into 15% |
| Increased damage | percent of the bonus part above x1.00 | `100` turns x1.50 into x2.00 |
| Additional damage | multiplier units | `0.25` turns x1.50 into x1.75 |
| Melee charge threshold | percent of attack cooldown recovery | `100` is a fully charged attack |
| Bow charge threshold | percent of a full bow draw | `100` is a fully drawn bow |
| Damage cap | multiplier, `0` for no maximum | `3` is at most x3.00 |

"Critical bonus damage" is the part of the multiplier above x1.00. At x2.00 the bonus part is 1.00.

## Formulas

Bonuses of the same stat add up, from every source, before the formula runs.

Final chance, in percent:

```text
final chance = base chance x (1 + total increased chance / 100) + total additional chance
then clamped to 0 to 100
```

Final multiplier:

```text
final multiplier = 1 + (base multiplier - 1) x (1 + total increased damage / 100) + total additional damage
then at least 1.00, and at most the damage cap when one is set
```

Worked examples:

- Base 10%, increased chance +50, additional chance +5: 10 x 1.5 + 5 = 20%.
- Base 0%, increased chance +500: 0%. An increased bonus never raises a base of 0.
- Base 0%, additional chance +3: 3%.
- Base 90%, additional chance +30: 100%. Base 5%, additional chance -10: 0%.
- Base x1.50, increased damage +100: 1 + 0.5 x 2 = x2.00.
- Base x1.50, additional damage +0.5: x2.00.
- Base x1.20, increased damage -200: x1.00.
- Damage cap 3, base x2.00, additional damage +5: x3.00.

Negative bonuses are allowed. A bonus or base value that is not a finite number is left out of the calculation and reported once in the log with its source.

Each attack rolls once: a number from 0 to just under 100. The attack is critical when the roll is below the final chance.

## Config files

### config/critworks.cfg

Created with the defaults when it does not exist. An existing file is only read, never saved over. A missing key uses its default and is not added to the file. A bad value gets one warning in the log and its default is used. If Forge cannot parse the file at all, every setting uses its default and the log says so.

Every value is stored as text, so the file shows each key as `S:key=value`.

Section `general`:

| Key | Unit | Range | Default |
| --- | --- | --- | --- |
| `fallbackChance` | percent | 0 to 100 | `15` |
| `fallbackDamage` | multiplier (`2.0` means x2.00) | 1.0 or more | `2.0` |
| `meleeChargeThreshold` | percent of attack cooldown recovery | 0 to 100 | `100` |
| `bowChargeThreshold` | percent of a full bow draw | 0 to 100 | `100` |
| `damageCap` | multiplier; `0` means no maximum | `0`, or 1 or more | `0` |
| `pvpCrits` | `true` or `false` | | `true` |
| `critSound` | `auto`, `none`, or a sound event id | | `auto` |
| `diagnosticMode` | `true` or `false` | | `false` |

- `fallbackChance` and `fallbackDamage`: the base stats of an eligible weapon that has no pack rule and no addon default. A new file writes `fallbackDamage` as `2`, which is the same value as `2.0`.
- `meleeChargeThreshold`: a melee attack can crit only when its attack cooldown recovery at the moment of the attack is at least this value. `0` lets every melee attack with an eligible weapon crit.
- `bowChargeThreshold`: an arrow can crit only when the bow draw at release is at least this value.
- `damageCap`: the highest final multiplier any attack can reach. A value below 1 other than `0`, or text that is not a number, gets a warning and no cap applies.
- `pvpCrits`: whether Critworks crits can happen against players. Normal PvP rules still decide whether a hit lands at all. This setting never makes a hit possible that PvP rules block.
- `critSound`: `auto` plays RLCombat's crit sound (`bettercombatmod:player.criticalstrike`) when RLCombat is installed, otherwise the vanilla crit sound (`minecraft:entity.player.attack.crit`). `none` plays no crit sound. An unknown sound id gets one warning and the `auto` sound is used. Critworks bundles no sound file.
- `diagnosticMode`: one server log line per supported attack. See [Diagnostic mode](#diagnostic-mode).

Section `integrations`. Each integration turns on by itself when its mod is installed. Set it to `false` to turn it off.

| Key | What it switches | Default |
| --- | --- | --- |
| `rlcombat` | RLCombat main-hand and offhand attacks | `true` |
| `jafs` | Just Another Food System crit properties | `true` |
| `quark` | Quark Visual Stat Display icon row in tooltips | `true` |
| `spartanweaponry` | Spartan Weaponry weapon-type rules and the longbow's own draw time | `true` |
| `qualitytools` | Quality Tools qualities counted only for their own weapon | `true` |

If an integration fails while the game runs, it turns off for the session with one warning. Core crits keep working.

Which side's file counts:

- The server's values decide all gameplay. At login the server sends its fallback values, thresholds, damage cap, integration switches, and weapon rules to the client, so tooltips show the server's numbers.
- The `quark` switch is the exception: each client uses its own, because it only changes how that client draws tooltips.
- A client's own `diagnosticMode` adds tooltip lines to that client's log.

Example: a server with fewer crits, a cap, and no crits against players.

```text
general {
    S:fallbackChance=5
    S:fallbackDamage=1.5
    S:damageCap=3
    S:pvpCrits=false
}
```

### config/critworks/weapons.json

Pack rules for single items or for Spartan weapon types. When the file is missing, Critworks creates it as an empty list `[]`. An existing file is never changed.

`config/critworks/weapons.examples.json` is written again on every start and is never read. Copy rules from it into `weapons.json`.

The file is a JSON list of rule objects. Keys:

| Key | Value | Meaning |
| --- | --- | --- |
| `item` | item id text, such as `"minecraft:iron_sword"` | The item the rule matches. Required unless `spartan_type` is set. |
| `meta` | whole number, 0 or more | Matches only that metadata. Optional. |
| `nbt` | item data as text, such as `"{display:{Name:\"Lucky Blade\"}}"` | Matches only stacks whose item data contains these tags. Other tags may be present. Optional. |
| `spartan_type` | a Spartan weapon type name, such as `"dagger"` | Matches every item of that type. Cannot be combined with `item`, `meta`, or `nbt`. |
| `eligible` | `true` or `false` | `true` makes any item able to crit. `false` turns the item off: it never crits and has no crit tooltip. |
| `chance` | percent, 0 to 100 | Base critical chance. |
| `damage` | multiplier, at least 1 | Base critical damage multiplier. `2.5` is x2.50. |

Each rule must set at least one of `eligible`, `chance`, and `damage`. A key that is not in this table makes the rule invalid.

How rules combine:

- Each value (eligible, chance, damage) is decided on its own. A rule that sets only `chance` leaves damage to the next source.
- Each base stat comes from the first of: a pack rule, an addon default, the fallback in `critworks.cfg`.
- When several rules match one stack, the most specific rule that sets the value wins: id with metadata and item data, then id with item data, then id with metadata, then id only, then a Spartan type rule.
- Two equally specific rules that can match the same stack and set the same value: the first one in the file wins, and the log warns once at load naming both rules.

Bad rules:

- A rule that is invalid (unknown item, unknown key, a number out of range, bad item data) is skipped with one warning. The other rules still load.
- A rule that sets `chance` or `damage` for an item that is not a sword, axe, or bow, with no rule that makes it eligible, gets a warning: add `"eligible": true` to make it a weapon.
- A file that is not valid JSON, or not a list, loads no rules and gets one warning.
- Warnings look like `Critworks config weapons.json: rule #3 skipped: unknown item "nomod:nothing"`.

Example `weapons.json`:

```json
[
  { "item": "minecraft:iron_sword", "chance": 20, "damage": 2.5 },
  { "item": "minecraft:golden_sword", "nbt": "{display:{Name:\"Lucky Blade\"}}", "chance": 100 },
  { "item": "minecraft:diamond_sword", "eligible": false },
  { "item": "minecraft:stick", "eligible": true, "chance": 50, "damage": 2.0 },
  { "item": "minecraft:wool", "meta": 14, "eligible": true, "chance": 20 },
  { "spartan_type": "dagger", "chance": 30, "damage": 1.75 }
]
```

What this example does:

- Iron swords: 20% chance, x2.50 damage.
- Golden swords named "Lucky Blade": 100% chance. Their damage stays at the fallback.
- Diamond swords: never crit, no crit tooltip.
- Sticks: can crit, 50% chance, x2.00 damage.
- Red wool (metadata 14): can crit, 20% chance.
- Every Spartan dagger: 30% chance, x1.75 damage. Needs Spartan Weaponry.

About the shipped `weapons.examples.json`: two of its rules sit below the default fallback of 15% and x2.00. Its iron sword rule sets chance 10 (10%), and its dagger type rule sets damage 1.8 (x1.80). Copying those rules unchanged makes iron swords crit less often and daggers hit softer than an unconfigured weapon.

## Weapons and eligibility

- Recognized weapons: every sword and axe for melee, and every bow for arrows, by item type. Modded items of the same type count too. No config is needed.
- A bow is a weapon for its arrows only. Hitting with a bow in melee does not crit, unless a pack rule sets `"eligible": true` for it.
- A pickaxe, a stick, or an empty hand cannot crit unless a pack rule makes the item eligible. An empty hand cannot be made eligible.
- An eligible attack must also pass the charge threshold, the target rule (the target must be a living entity), and the PvP setting.
- Ordinary weapons store no Critworks data. A config change applies to existing weapons after a restart.

## Player bonus attributes

Players have four attributes. Each starts at 0 and is the sum of its bonuses.

| Attribute name | Stat | Unit |
| --- | --- | --- |
| `critworks.increasedCritChance` | increased chance | percent |
| `critworks.additionalCritChance` | additional chance | percentage points |
| `critworks.increasedCritDamage` | increased damage | percent |
| `critworks.additionalCritDamage` | additional damage | multiplier units |

- Write the names exactly as shown.
- Only attribute modifiers with operation 0 (add) count. Operations 1 and 2 have no effect on these attributes.
- Any operation 0 modifier on these attributes counts as a global bonus, whatever adds it: armor, an item's attribute data, Setworks, JAFS, Quality Tools armor qualities, or an addon.
- The source shown for such a bonus is the modifier's name.

### Global and weapon-specific bonuses

- A global bonus counts for every eligible attack of its player: both hands, melee and arrows.
- A weapon-specific bonus counts only for attacks made with its own weapon. Quality Tools qualities on a held weapon, and addon weapon bonuses, are weapon-specific.
- A modifier that a held item puts on the player through its own attribute data is a global bonus while it is on the player. For a bonus that only one weapon should get, use a pack rule, a Quality Tools hand quality, or an addon.

## JAFS (Just Another Food System)

With JAFS installed and the `jafs` switch on, Critworks adds two JAFS properties:

| Property | Operation | Becomes | Unit | Allowed amounts |
| --- | --- | --- | --- | --- |
| `critworks:crit_chance` | `flat` | additional chance | percentage points | -100 to 100 |
| `critworks:crit_chance` | `percent` | increased chance | percent | -100 to 1000 |
| `critworks:crit_damage` | `flat` | additional damage | multiplier units | -10 to 10 |
| `critworks:crit_damage` | `percent` | increased damage | percent | -100 to 1000 |

- JAFS checks the amounts. An amount outside the range is rejected by JAFS.
- The sums of a player's active foods become global bonuses with the source "JAFS". When a food expires, its bonus is gone.
- With the `jafs` switch off, or JAFS removed, Critworks removes leftover "JAFS" bonuses at the player's next login.

Example JAFS food for `config/jafs/foods.json`:

```json
[
  {
    "id": "mypack_hunters_stew",
    "match": [ { "item": "minecraft:rabbit_stew" } ],
    "fullDuration": 600,
    "modifiers": [
      { "property": "critworks:crit_chance", "operation": "flat", "amount": 10 },
      { "property": "critworks:crit_damage", "operation": "percent", "amount": 50 }
    ]
  }
]
```

While this food is active (600 seconds at JAFS's normal depletion rate), an iron sword at the default fallback has 15 + 10 = 25% chance and 1 + 1.00 x 1.5 = x2.50 damage.

JAFS draws its own food tooltip in its own number format. It shows a flat chance amount as "+10 Crit Chance" (percentage points, no unit sign), a percent amount as "+50% Crit Chance", and rounds any amount that is not a whole number to one decimal.

## Quality Tools

With Quality Tools installed, a quality that adds to one of the four attributes with operation 0 gives a Critworks bonus.

- A quality on armor or another slot that is not a hand is a global bonus.
- A quality on the attacking weapon counts only for attacks with that weapon from that hand. Arrows fired while the weapon sits in the hotbar get nothing from it.
- A quality on the item in the other hand does not count.
- A quality that also lists the `inventory` slot applies anywhere in the main inventory, so it stays a global bonus there.
- Operations 1 and 2 have no effect on the Critworks attributes. Only operation 0 counts.
- The source shown for a hand quality is "Quality: " and the quality's name. Quality Tools stores the name as a language key, translated where the language has it.
- Global qualities show the modifier's own name, which Quality Tools sets to "qualitytools".
- With the `qualitytools` switch off, hand qualities are no longer kept to their own weapon: Quality Tools puts them on the player, and Critworks counts them as global bonuses.

Quality files live in `config/qualitytools/Quailities/` (Quality Tools spells the folder this way).

Example quality file for swords:

```json
{
  "slot": "mainhand",
  "whitelist": [
    { "class": "ItemSword" }
  ],
  "blacklist": [],
  "qualities": [
    {
      "name": "quality.sharp.name",
      "color": "aqua",
      "weight": 100,
      "attributes": [
        { "name": "critworks.additionalCritChance", "amount": 10, "operation": 0 }
      ]
    }
  ]
}
```

A sword with this quality gets +10 percentage points of additional chance for its own attacks only.

Example quality file for helmets, a global bonus:

```json
{
  "slot": "head",
  "whitelist": [
    { "slot": "head" }
  ],
  "blacklist": [],
  "qualities": [
    {
      "name": "quality.lucky.name",
      "color": "green",
      "weight": 10,
      "attributes": [
        { "name": "critworks.additionalCritChance", "amount": 5, "operation": 0 },
        { "name": "critworks.increasedCritDamage", "amount": 20, "operation": 0 }
      ]
    }
  ]
}
```

A helmet with this quality gives +5 percentage points of additional chance and +20% increased damage to every attack of its wearer.

## Setworks

A Setworks set bonus that uses `add` on a Critworks attribute counts as a global bonus while the Setworks bonus is active. It ends when the bonus is no longer active; for a full-set bonus, when one piece is removed. Critworks needs no Setworks code.

- Only `add` counts. `multiply_base` and `multiply_total` (operations 1 and 2) have no effect on the Critworks attributes.
- The source shown is "setworks " and the effect's instance id, for example `setworks mypack:iron_armor/mypack:iron_focus/0`.
- A Setworks bonus is a global bonus, even for a weapon set: it counts for both hands and for arrows.

Example Setworks script (`scripts/` folder, with CraftTweaker):

```zenscript
#loader setworks
import mods.setworks.Setworks;

Setworks.armorSet("mypack:iron_armor", "Iron Armor",
    <minecraft:iron_helmet>, <minecraft:iron_chestplate>, <minecraft:iron_leggings>, <minecraft:iron_boots>);
Setworks.fullSetBonus("mypack:iron_focus", "mypack:iron_armor", "Iron Focus: +10 additional crit chance")
    .attribute("critworks.additionalCritChance", 10, "add");
```

With the full set worn, every attack of the player gets +10 percentage points of additional chance.

## Spartan Weaponry

With Spartan Weaponry installed and the `spartanweaponry` switch on:

- Every Spartan melee weapon is a sword by item type, and the longbow is a bow, so all of them can crit with no config.
- A `spartan_type` rule in `weapons.json` applies to every item of that type, from Spartan Weaponry or from an addon that makes weapons through its API.
- An `item` rule that matches the same stack wins over a type rule.
- Without Spartan Weaponry, or with the switch off, type rules are skipped with one warning. The other rules load.
- The longbow is measured against its own full draw time, not the vanilla 20 ticks.
- Inspection names a type rule as, for example, "pack rule #6 (spartan type dagger)".

Type names: `dagger`, `parrying_dagger`, `longsword`, `katana`, `saber`, `rapier`, `greatsword`, `hammer`, `warhammer`, `spear`, `halberd`, `pike`, `lance`, `glaive`, `quarterstaff`, `scythe`, `battleaxe`, `mace`, `club`, `caestus`, `longbow`.

Crossbows and throwing weapons have no type name: their attacks are not supported.

## RLCombat

With RLCombat installed and the `rlcombat` switch on:

- Main-hand and offhand attacks are supported attacks. Each uses the weapon and the charge of the attacking hand.
- RLCombat's own crit roll, jump crit, and crit sound never apply to these attacks.
- Nothing else of RLCombat changes: animations, cooldowns, sweeps, and other sounds stay as they are.

## Quark

With Quark (or Quark: RotN Edition) Visual Stat Display on and the client's `quark` switch on, an eligible weapon's tooltip shows one compact row with a chance icon and value and a damage icon and value. The row shows the base values. Holding Shift shows the text lines instead.

## Supported attacks

Supported:

- Player melee swings, with every target of the sweep. All targets of one swing share one critical result.
- RLCombat main-hand and offhand attacks, when RLCombat is installed.
- Arrows a player fires from a bow, including the Spartan longbow. An arrow's eligibility, chance, multiplier, and result are fixed when it is fired. Later changes to the player's equipment or bonuses do not change them.
- Attacks of other combat mods that use the Critworks API, and melee hits of other attackers that an addon allows.

Not supported, so they keep their own behavior and get no Critworks crit, sound, or event:

- Crossbows and their bolts.
- Thrown weapons, such as Spartan javelins.
- Arrows of mobs, dispensers, and other non-player shooters.
- Burning, explosions, potions, magic, thorns, and other damage over time.

Only players crit by default. An addon can allow other attackers. Their melee hits then crit with the weapon in their main hand.

## How the multiplier changes damage

- A critical hit's damage is multiplied before armor and other defenses. Vanilla armor never blocks a larger share of a larger hit, so at x2.00 an armored target loses at least twice the health of a normal hit (for example, with iron armor a 6-damage hit removes about 3.1 health and a 12-damage hit about 7.7).
- The multiplier includes enchantment damage carried by the hit, such as Sharpness and Power. Example: iron sword (6) with Sharpness I (+1), fully charged, x2.00, unarmored target: 2 x (6 + 1) = 14.
- Separate damage caused by the hit is not multiplied: Fire Aspect burning, poison, lightning.
- Every target of a critical sweep takes critical damage. A critical attack still sweeps exactly when a normal attack would.

### Damage changes from other mods

Critworks multiplies the hit in Forge's `LivingHurtEvent` at HIGHEST priority, with a handler it registers while the game finishes loading.

- Multiplied: everything already in the hit when Critworks sees it. That is the weapon's attack damage and its attribute bonuses, enchantment damage, and damage changes other mods made before that point, including another mod's `LivingHurtEvent` handler at HIGHEST priority that runs before Critworks.
- Not multiplied: damage changes other mods make after that point. That is `LivingHurtEvent` handlers at HIGH, NORMAL, LOW, or LOWEST priority, HIGHEST handlers that run after Critworks, and every change in `LivingDamageEvent`, which runs after armor.
- The order of handlers at the same priority depends on the order in which the mods registered them. So for those handlers, whether another mod's bonus is multiplied is load-order dependent.

### Damage immunity time

After a target is hit, vanilla gives it a short immunity time (10 ticks, half a second). During that time a new hit lands only when its damage is higher than the earlier hit's damage, and then only the difference gets through.

- A critical hit during immunity time multiplies the part that gets through, not the whole hit.
- Vanilla compares the damage before the multiplier, so a crit never makes a blocked hit land, and a crit does not raise the bar for the next hit.
- A hit fully stopped by immunity time is not a successful hit: no critical-hit event, and it uses no melee guarantee.
- In diagnostic mode, the log shows the damage that reached the target before and after the multiplier.

## Guarantees

Addons can grant a player a guarantee: the player's next eligible attack is critical.

- A melee attack uses the guarantee at its first successful hit. A miss keeps it.
- An arrow uses the guarantee when it is fired, hit or miss.
- An attack that is not eligible (charge too low, not a weapon) is not critical and uses no guarantee.
- Guarantees survive logout, restart, and dimension travel. Timed guarantees count down only while the player is online. Death clears them unless the addon asked to keep one.

## Tooltips

- An eligible weapon shows one line with its base chance and multiplier, for example `Crit: 15% | x2.00`. A non-eligible item shows nothing from Critworks.
- Holding Shift shows a current preview, which does not apply to arrows already fired: the effective chance and multiplier, and each bonus with its source.
- Each bonus line shows the value first, then the unit: "%" for increased chance, increased damage, and additional chance; nothing for additional damage. For additional chance, "%" means percentage points.

Example Shift tooltip of an iron sword with the example JAFS food above, default config:

```text
Crit: 15% | x2.00
Current preview, not for arrows already fired:
 Effective: 25% | x2.50
 +10% Additional Crit Chance (JAFS)
 +50% Increased Crit Damage (JAFS)
```

- An additional damage bonus reads, for example, `+0.5 Additional Crit Damage (mypack:whetstone)`.
- The preview for a weapon that is not in hand shows global bonuses plus that weapon's own bonuses, never another weapon's.
- Tooltips never change game state and never use a guarantee.

## Inspection command

`/critworks inspect [player]` needs operator level 2. Without a name it inspects the player who runs it. It changes nothing and uses no guarantee. Every line also goes to the server log with the prefix `[inspect]`.

It reports, for the main-hand item and the offhand item: whether it is eligible and why, the base chance and damage with the source of each (a pack rule, an addon default, or the fallback), each bonus with its source, and the effective chance and damage. It also reports the damage cap, the player's four bonus totals, and pending guarantees with time left.

Example output:

```text
Critworks inspect: Steve
Damage cap: none
Player bonus totals: critworks.increasedCritChance +0.00, critworks.additionalCritChance +10.00, critworks.increasedCritDamage +50.00, critworks.additionalCritDamage +0.00
Main hand: Iron Sword (minecraft:iron_sword) - eligible: recognized weapon
  Base chance 15.00% from fallback
  Base damage x2.00 from fallback
  Bonus +10.00 additional chance from JAFS
  Bonus +50.00 increased damage from JAFS
  Effective chance 25.00%, damage x2.50
Offhand: empty - not eligible: not a recognized weapon
Guarantees: none
```

Bonus amounts in the report are in each stat's own unit: percent for increased bonuses, percentage points for additional chance, multiplier units for additional damage.

## Diagnostic mode

Set `diagnosticMode=true` in the server's `critworks.cfg` and restart. The server log then gets:

- One start line: `[diag] diagnostic mode on; crit sound ...; rlcombat integration on; ...`.
- One line per resolved supported attack, eligible or not: `[diag] attack melee`, `[diag] attack arrow`, `[diag] attack integration`, or `[diag] attack mob`.
- For a critical arrow, one short line per target it hits successfully: `[diag] hit arrow`.

Nothing is logged per tick. With the setting off, nothing is logged per attack.

Line fields: `attacker`, `weapon`, `hand` (`main` or `off`), `charge` (percent), `chance` (final chance, percent), `roll` (0 to 100, `-` when guaranteed or not eligible), `result` (`critical`, `normal`, or `"not eligible: <reason>"`), `by` (`roll` or `guarantee`), `multiplier`, `vanillaCrit`, `guarantee`, and for melee and mob lines `targets`. An arrow line also has `arrow`, a short id that its hit lines repeat. A mob line has no `hand`, `charge`, `vanillaCrit`, or `guarantee`, and its `attacker` reads `name#id`; and an integration line's target entries read `:target:` instead of `:primary:`.

A melee target entry reads `name#id:primary:6.00->12.00:hit:event`: the target, primary or sweep, the damage before and after the multiplier (before armor), whether the hit was successful, and whether an event fired.

Example lines:

```text
[diag] attack melee attacker=Steve weapon=minecraft:iron_sword hand=main charge=100.00 chance=15.00 roll=7.31 result=critical by=roll multiplier=2.00 vanillaCrit=no guarantee=- targets=[Zombie#212:primary:6.00->12.00:hit:event]
[diag] attack melee attacker=Steve weapon=minecraft:iron_sword hand=main charge=95.00 chance=15.00 roll=- result="not eligible: charge" by=roll multiplier=2.00 vanillaCrit=no guarantee=- targets=[Zombie#212:primary:5.53->5.53:hit]
```

A client with its own `diagnosticMode` on writes one `[diag] tooltip` line each time the hovered item or tooltip mode changes.

## Compatibility

Other mods that add their own critical hits may stack with Critworks crits or conflict with them. Critworks does not turn them off automatically. It only replaces the vanilla jump crit, the vanilla arrow crit, and RLCombat's crit, for the attacks it supports.

Versions Critworks was checked with:

| Mod | Version | Notes |
| --- | --- | --- |
| Minecraft Forge | 14.23.5.2847 or newer | required |
| RLCombat | 2.2.4 | optional |
| Just Another Food System (JAFS) | 1.0.0 | optional |
| Quark | r1.6-180 | optional, checked against its source, not run in game |
| Quark: RotN Edition | r1.6-192 | optional |
| Quality Tools | 1.0.7 | optional |
| Spartan Weaponry | 1.6.1 | optional |
| Setworks | 1.0.0 | optional, checked against its source, no dependency |

Other versions of these mods may work, but they were not checked. Critworks needs no other mod.

## For coding agents

- Edit `config/critworks.cfg` and `config/critworks/weapons.json` on the server side. Restart after every change.
- Never edit `weapons.examples.json` to change behavior; it is rewritten on every start and never read.
- After a restart, search the log for `Critworks config` warnings and for `Critworks loaded N weapon rule(s)`.
- Check the result in game with `/critworks inspect <player>`. Every base stat names its source.
- Bonus amounts always use the stat's own unit: percent for increased, percentage points for additional chance, multiplier units for additional damage.

## Known limits

- Config changes need a restart. There is no live reload.
- An existing `critworks.cfg` keeps the values written in it. Changed defaults reach only new files and missing keys.
- Crossbows, thrown weapons, and arrows of non-player shooters are not supported.
- Other attackers that an addon allows crit with melee hits only. Each hit is its own attack, at full charge, with no bonuses except weapon-specific ones and no guarantees.
- Other mods' damage changes after the Critworks handler are not multiplied, and the order is load-order dependent. See [Damage changes from other mods](#damage-changes-from-other-mods).
- During damage immunity time only the part of the hit that gets through is multiplied.
- Other damage of the vanilla player-attack type that the same player deals in the same tick after a swing counts as part of that swing and gets its multiplier, with no second event.
- A modded bow that fires several arrows at once: only the first arrow of a release gets the Critworks result.
- An arrow fired at a player while `pvpCrits` is off still uses a pending guarantee when it is fired.
- An arrow whose spawn another mod cancels at a lower event priority still uses its guarantee.
- An opt-out from an addon is used up by the next attack, even a punch or a stick swing.
- Quality Tools: a hand-quality item moved out of the hand can still show in the tooltip preview for up to 7 ticks. A weapon that is not held previews as if it were in the main hand.
- JAFS: an addon that uses "JAFS" as its own bonus source shares the food bonus.
- Tooltips: a new bonus can show "unknown source" for up to half a second. The Critworks lines come after the F3+H advanced lines. The preview line names arrows also on melee weapons.
- Quark row: with diagnostic mode on, rebuilding the creative search index logs one tooltip line per eligible weapon.
- A Spartan longbow failure turns the Spartan integration off for the session, but type rules loaded at start stay.
