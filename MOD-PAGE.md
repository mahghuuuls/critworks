Critworks gives every sword, axe, and bow a critical chance and a critical damage multiplier. Each attack rolls once against its weapon's chance. A critical hit multiplies the damage. Jumping, falling, and a full bow draw no longer make a hit critical by themselves.

<span style="color:#d6a100">**AI usage disclaimer:** This mod was developed with AI-agent assistance using [this agent workflow](https://github.com/mahghuuuls/minecraft-1.12.2-mod-agent-workflow). The project owner reviewed the work during development.</span>

## Features

- **Weapon stats:** a weapon with no other values has 15% chance and x2.00 damage. Pack makers can change the values for one item, for a Spartan weapon type, or for every weapon.
- **Player bonuses:** four bonus stats raise a player's crits (see Crit stats below). Armor, items, foods, set bonuses, and other mods can add to them.
- **Tooltips:** a weapon shows its crit chance and damage, for example `Crit: 15% | x2.00`. Hold Shift to see the chance and damage with your current bonuses, and where each bonus comes from.
- **Sweeps and arrows:** one swing rolls once for every target it sweeps. An arrow keeps the result it rolled when it was fired.
- **Sound:** a critical hit plays the vanilla crit sound, or RLCombat's crit sound when RLCombat is installed.
- **Inspect command:** `/critworks inspect` (operators only) shows where each value of the held weapons comes from.
- **Addon API:** other mods can set weapon values, give bonuses, grant guaranteed crits, and connect their own attacks.

## Crit stats

Every weapon has two base stats:

- **Crit chance:** how often an attack is critical, in percent. 15% means about 3 attacks in 20.
- **Crit damage:** the multiplier for a critical hit. x2.00 means double damage.

Players have four bonus stats. Each starts at 0, and bonuses from every source add up.

- **Increased Crit Chance** (`critworks.increasedCritChance`): raises the weapon's crit chance by a percent of itself. +100 doubles it.
- **Additional Crit Chance** (`critworks.additionalCritChance`): adds percentage points to the crit chance. +10 adds 10%.
- **Increased Crit Damage** (`critworks.increasedCritDamage`): raises the bonus part of the multiplier (the part above x1.00) by a percent. +100 doubles the bonus part.
- **Additional Crit Damage** (`critworks.additionalCritDamage`): adds to the multiplier. +0.5 adds 0.50.

Increased bonuses apply first, then additional bonuses are added.

Chance examples on a weapon with 15%:

- Increased Crit Chance +100: 15% x 2 = 30%.
- Additional Crit Chance +10: 15% + 10 = 25%.
- Both: 15% x 2 + 10 = 40%.

Damage examples on a weapon with x2.00 (bonus part 1.00):

- Increased Crit Damage +100: the bonus part doubles to 2.00, so x3.00.
- Additional Crit Damage +0.5: x2.00 + 0.50 = x2.50.
- Both: x3.00 + 0.50 = x3.50.

Increased bonuses help most on weapons with high base values. Additional bonuses give every weapon the same amount. Examples with the Spartan Weaponry dagger (30%, x1.50) and greatsword (10%, x2.50):

- Increased Crit Chance +50: a dagger goes from 30% to 45%, a greatsword from 10% to 15%.
- Additional Crit Chance +10: a dagger goes from 30% to 40%, a greatsword from 10% to 20%.
- Increased Crit Damage +100: a dagger goes from x1.50 to x2.00, a greatsword from x2.50 to x4.00.
- Additional Crit Damage +0.5: a dagger goes from x1.50 to x2.00, a greatsword from x2.50 to x3.00.

A weapon with 0% crit chance stays at 0% with any Increased Crit Chance; only Additional Crit Chance can raise it. Crit chance is never more than 100%. Bonuses can be negative.

## Configuration

`config/critworks.cfg` sets the default chance and damage, how charged an attack must be to crit, a damage cap, crits against players, and the crit sound. `config/critworks/weapons.json` sets values for single items or Spartan weapon types:

```json
[
  { "item": "minecraft:iron_sword", "chance": 20, "damage": 2.5 },
  { "item": "minecraft:diamond_sword", "eligible": false },
  { "item": "minecraft:stick", "eligible": true, "chance": 50, "damage": 2.0 }
]
```

Changes need a restart. The full reference for pack makers is in the [pack maker guide](https://github.com/mahghuuuls/critworks/blob/main/docs/pack-makers.md).

## Installation

Install Critworks on the server and on every client.

## Integrations

Each integration turns on by itself when its mod is installed, and each can be turned off in the config.

### [RLCombat](https://www.curseforge.com/minecraft/mc-mods/rlcombat)

- Main-hand and offhand attacks use Critworks crits, each with the weapon in its own hand.
- RLCombat's own crit roll and jump crit no longer apply. Critworks crits play RLCombat's crit sound.

### [Spartan Weaponry](https://www.curseforge.com/minecraft/mc-mods/spartan-weaponry)

- Every Spartan melee weapon and the longbow can crit.
- Each weapon type has its own built-in values. Light weapons crit more often for less damage; heavy weapons crit less often for more damage.
- **Dagger, caestus:** 30%, x1.50
- **Rapier:** 25%, x1.60
- **Parrying dagger, saber, katana, quarterstaff:** 20%, x1.75
- **Longsword, spear, mace, club:** 15%, x2.00
- **Halberd, glaive, pike:** 12%, x2.25
- **Greatsword, battleaxe, hammer, warhammer, scythe, lance, longbow:** 10%, x2.50
- `weapons.json` can change the values for a whole weapon type. This also covers weapons from Spartan Weaponry addons.
- Crossbows and throwing weapons do not crit.

### [Just Another Food System](https://www.curseforge.com/minecraft/mc-mods/just-another-food-system)

For pack makers: create JAFS foods that give crit bonuses while they are active. Critworks adds two JAFS properties:

- **`critworks:crit_chance`:** `flat` adds percentage points of crit chance. `percent` raises the crit chance by a percent of itself.
- **`critworks:crit_damage`:** `flat` adds to the damage multiplier, for example `0.5` turns x2.00 into x2.50. `percent` raises the bonus part of the multiplier (the part above x1.00).

Example food in `config/jafs/foods.json`:

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

While this stew is active, an iron sword has 25% crit chance (15 + 10) and x2.50 crit damage (the 1.00 bonus part raised by 50%). The bonus counts for every attack of the player and ends when the food expires.

### [Quality Tools](https://www.curseforge.com/minecraft/mc-mods/quality-tools)

For pack makers: create qualities that give crit bonuses with the four Critworks attributes (see Crit stats above for the names). Use operation `0`; the other operations do nothing on these attributes.

Example quality file for swords in `config/qualitytools/Quailities/` (Quality Tools spells the folder this way):

```json
{
  "slot": "mainhand",
  "whitelist": [ { "class": "ItemSword" } ],
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

With this quality, a sword at the default 15% crit chance has 25%. A quality on a weapon counts only for attacks with that weapon. A quality on armor counts for every attack of the player who wears it.

### [Setworks](https://www.curseforge.com/minecraft/mc-mods/setworks)

For pack makers: create armor sets whose set bonuses give crit bonuses with the four Critworks attributes (see Crit stats above for the names). Use `add`; `multiply_base` and `multiply_total` do nothing on these attributes. Critworks needs no extra setup for this.

Example Setworks script in the `scripts` folder (needs [CraftTweaker](https://www.curseforge.com/minecraft/mc-mods/crafttweaker)):

```zenscript
#loader setworks
import mods.setworks.Setworks;

Setworks.armorSet("mypack:iron_armor", "Iron Armor",
    <minecraft:iron_helmet>, <minecraft:iron_chestplate>, <minecraft:iron_leggings>, <minecraft:iron_boots>);
Setworks.fullSetBonus("mypack:iron_focus", "mypack:iron_armor", "Iron Focus: +10 additional crit chance")
    .attribute("critworks.additionalCritChance", 10, "add");
```

With the full iron set worn, every attack of the player gets +10 percentage points of crit chance. The bonus ends when one piece comes off.

### [Quark](https://www.curseforge.com/minecraft/mc-mods/quark) and [Quark: RotN Edition](https://www.curseforge.com/minecraft/mc-mods/quark-rotn-edition)

- With Visual Stat Display on, the crit chance and damage show as one icon row in the weapon tooltip.

## Limitations

- Crossbows, thrown weapons, and arrows from mobs or dispensers do not get Critworks crits.
- Other mods that add their own critical hits are not turned off. Their crits can stack with Critworks crits.

## Source

[GitHub](https://github.com/mahghuuuls/critworks)
