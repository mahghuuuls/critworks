# Critworks API

This page is for authors of addons that use Critworks from Java. Pack makers: see [pack-makers.md](pack-makers.md) for config files, attribute names, and integrations.

Everything here is in the package `com.mahghuuuls.critworks.api`. Nothing outside that package is part of the API.

## Overview

The API lets an addon:

- set default base stats for items,
- give players global bonuses,
- give stacks weapon-specific bonuses,
- grant guarantees (the next eligible attack is critical),
- mark one attack as unable to crit,
- let attackers other than players crit,
- connect another combat system's attacks,
- react to critical hits through an event,
- read the stats Critworks resolves for a player and a stack.

`CritworksApi.API_VERSION` is `"1.0"`. It is raised when methods are added.

## Setup and lifecycle

Declare a dependency so your mod loads after Critworks:

```java
@Mod(modid = "mymod", name = "My Mod", version = "1.0.0",
        dependencies = "required-after:critworks")
public class MyMod {
}
```

For an optional dependency use `after:critworks`, check `Loader.isModLoaded("critworks")` before you touch any Critworks class, and keep the calls in a class that loads only then. `CritworksApi.isBound()` is true once Critworks has bound the API.

Lifecycle rules:

- Critworks binds the API during its own pre-initialization. A call before that throws `IllegalStateException`. With the dependency above, your pre-initialization runs after Critworks' and the API is ready.
- Registration methods work only while mods load, up to and including every mod's post-initialization. When loading completes (`FMLLoadCompleteEvent`), registration closes, and later calls throw `IllegalStateException`. The registration methods are `registerItemDefaults`, `registerWeaponBonusProvider`, `registerAttackerPolicy`, and `registerAttackIntegration`.
- `resolve`, `beginAttack`, and `endAttack` work once Critworks has finished its post-initialization. Earlier calls throw `IllegalStateException`.
- The player methods (`setGlobalBonus`, `grantGuarantee`, and the others) need a player in a world, so they are for play time.
- Register during your mod's own loading events (pre-initialization, initialization, or post-initialization). Critworks names the source of a default or a provider by the mod that is loading when you register it; outside your loading events the source reads "an unknown mod".

Example registration:

```java
@Mod.EventHandler
public void init(FMLInitializationEvent event) {
    // Common code: this runs on the client and on the server.
    CritworksApi.registerItemDefaults(MyItems.RAPIER, 25.0D, null);
    CritworksApi.registerWeaponBonusProvider(new SharpenedBonus());
}
```

## Sides and threads

Critworks is installed on the server and on every client. The server decides eligibility, rolls, damage, guarantee use, and events. Clients resolve stats for tooltips and for client-side calls of `CritworksApi.resolve`, with the server's settings and rules while connected (before the server's settings arrive, or when they cannot be read, the client's own).

Register on both sides:

- Register addon defaults, weapon bonus providers, and attacker policies the same way on both sides. Put the calls in common code, not in a client-only or server-only proxy.
- Critworks sends its own config and weapon rules to the client, but it does not send addon defaults or providers. The client uses its own registrations for tooltips, so a registration that exists only on the server shows wrong numbers in tooltips.
- Weapon bonus providers and item default predicates also run on the client for tooltips. There they get the client player and the shown stack.
- Attacker policies are asked on the server, for each hit.

Threads:

- In single player, the client and the integrated server run in one game. Your providers and predicates are then called from the client thread and from the server thread, sometimes at the same time.
- Keep them cheap, keep no shared mutable state in them, and never change the world, the player, or the stack from them.

Calls that change player state (bonuses, guarantees, opt-out, attacks) belong on the logical server. A call on the client side is ignored, and Critworks logs one warning the first time.

## Units

Every amount uses the Critworks units, the same as in config files, attributes, tooltips, and the inspection command:

| Value | Unit | Example |
| --- | --- | --- |
| Critical chance | percent, 0 to 100 | `15.0D` is 15% |
| Critical damage | multiplier, at least 1 | `2.0D` is x2.00 |
| Increased chance | percent | `20.0D` is +20% of the chance |
| Additional chance | percentage points | `5.0D` turns 10% into 15% |
| Increased damage | percent of the bonus part above x1.00 | `100.0D` turns x1.50 into x2.00 |
| Additional damage | multiplier units | `0.25D` turns x1.50 into x1.75 |
| Attack charge | fraction, 0 to 1 | `1.0F` is fully charged |
| Guarantee duration | ticks, 20 per second | `1200` is 60 seconds |

Formulas, with each bonus summed per stat first:

```text
final chance = base chance x (1 + increased chance / 100) + additional chance, clamped to 0 to 100
final multiplier = 1 + (base multiplier - 1) x (1 + increased damage / 100) + additional damage,
    then at least 1.00 and at most the server's damage cap
```

## Stats and attribute names

`CritStat` names the four bonus stats. `getAttributeName()` gives the player attribute name, for configs of other mods. `getLabel()` gives a short readable name.

| Constant | Attribute name | Unit |
| --- | --- | --- |
| `CritStat.INCREASED_CHANCE` | `critworks.increasedCritChance` | percent |
| `CritStat.ADDITIONAL_CHANCE` | `critworks.additionalCritChance` | percentage points |
| `CritStat.INCREASED_DAMAGE` | `critworks.increasedCritDamage` | percent |
| `CritStat.ADDITIONAL_DAMAGE` | `critworks.additionalCritDamage` | multiplier units |

Only attribute modifiers with operation 0 (add) count toward these stats. Operations 1 and 2 have no effect.

## Item default stats

```java
CritworksApi.registerItemDefaults(Item item, Double chance, Double damage)
CritworksApi.registerItemDefaults(Predicate<ItemStack> stacks, Double chance, Double damage)
```

- `chance`: base chance in percent, 0 to 100, or `null` to leave it to the fallback.
- `damage`: base damage multiplier, at least 1, or `null` to leave it to the fallback.
- Set a chance, a damage, or both. Both `null`, a value out of range, or a null item or predicate throws `IllegalArgumentException`.
- Each base stat is resolved on its own: a pack rule wins, then an addon default, then the server's fallback.
- A default does not make an item eligible. Swords, axes, and bows are eligible by type; other items become eligible only through a pack rule.
- When several defaults set the same stat for a stack, item defaults win over predicate defaults, and within each kind the first registered wins.
- The source shown by inspection is "addon default from " and your mod id.
- A predicate that throws is reported once and then skipped.
- Register on both sides (see [Sides and threads](#sides-and-threads)).

Example: rapiers crit often, and every stack named "Glass Edge" hits hard.

```java
CritworksApi.registerItemDefaults(MyItems.RAPIER, 25.0D, null);
CritworksApi.registerItemDefaults(
        stack -> stack.hasDisplayName() && "Glass Edge".equals(stack.getDisplayName()),
        null, 3.0D);
```

## Global bonuses

```java
CritworksApi.setGlobalBonus(EntityPlayer player, String sourceId, CritStat stat, double amount)
CritworksApi.removeGlobalBonus(EntityPlayer player, String sourceId, CritStat stat)
```

- A global bonus counts for every eligible attack of that player: both hands, melee and arrows.
- `sourceId` is a stable id such as `"mymod:rage"`. It is shown as the bonus's source. It must not be empty.
- `amount` is in the stat's own unit. It must be a finite number, or the call throws `IllegalArgumentException`.
- Setting the same source and stat again replaces the old amount. An amount of 0 removes it.
- The bonus is an operation 0 attribute modifier on the player. It is saved with the player and sent to the player's client.
- Minecraft does not keep it on the new player after death or after leaving the End. Your mod decides when the bonus starts and ends, including then.
- A call on the client side is ignored.
- Do not use `"JAFS"` as a source id: the JAFS food bonus uses it, and the two would share one modifier.

Example: a rage effect gives +10 percentage points of additional chance.

```java
// On the server, when the rage starts:
CritworksApi.setGlobalBonus(player, "mymod:rage", CritStat.ADDITIONAL_CHANCE, 10.0D);
// When it ends:
CritworksApi.removeGlobalBonus(player, "mymod:rage", CritStat.ADDITIONAL_CHANCE);
```

## Weapon-specific bonuses

```java
CritworksApi.registerWeaponBonusProvider(IWeaponBonusProvider provider)
```

`IWeaponBonusProvider.getBonuses(@Nullable EntityPlayer player, ItemStack stack)` returns the stack's bonuses, or an empty list.

- A weapon-specific bonus counts only for attacks made with that stack, in either hand, and for that stack's tooltip.
- Critworks asks every provider whenever it resolves a stack, on the server and on the client. A provider must be cheap and must not change any state.
- `player` is the attacker or the tooltip's player. It is null when the attacker is not a player.
- `stack` is the attacking or shown stack. It is never empty.
- Its bonuses are never written to the player's attributes.
- A provider that throws is reported once and then skipped. The client's tooltips keep their own record, so a provider that fails only for a tooltip still counts for attacks.
- A bonus amount that is not a finite number is left out and reported once per source.

`WeaponBonus(CritStat stat, double amount, String source)`:

- `amount` is in the stat's own unit: percent for increased bonuses, percentage points for additional chance, multiplier units for additional damage.
- `source` is a short label such as `"mymod:sharpened"`, shown by inspection and tooltips. A null stat or an empty source throws `IllegalArgumentException`.

Example: sharpened weapons get +20% increased chance.

```java
public final class SharpenedBonus implements IWeaponBonusProvider {
    @Override
    public List<WeaponBonus> getBonuses(@Nullable EntityPlayer player, ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.getBoolean("mymod:sharpened")) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new WeaponBonus(CritStat.INCREASED_CHANCE, 20.0D, "mymod:sharpened"));
    }
}
```

## Guarantees

A guarantee makes the player's next eligible attack critical.

```java
CritworksApi.grantGuarantee(EntityPlayer player, GuaranteeSpec spec)
CritworksApi.removeGuarantee(EntityPlayer player, String id)   // true when one was pending
CritworksApi.getGuarantees(EntityPlayer player)                // in the order they will be used
```

Build a spec with `GuaranteeSpec`:

- `GuaranteeSpec.of(id)`: no time limit, removed by death. `id` is a stable id such as `"mymod:focus"`. An empty id throws `IllegalArgumentException`.
- `.lasting(ticks)`: lasts that many online ticks (20 per second). Less than 1 tick throws `IllegalArgumentException`.
- `.keptOnDeath()`: kept when the player dies.
- `GuaranteeSpec.NO_DURATION` (`-1`) is the duration of a guarantee with no time limit.

Rules:

- Granting the same id again replaces that guarantee. Guarantees with different ids coexist.
- The next guarantee used is the one that expires first, then the oldest.
- A melee attack uses it at its first successful hit, so a miss keeps it. An arrow uses it when it is fired, hit or miss.
- An attack that is not eligible is not critical and uses no guarantee.
- Saved with the player. Counts down only while the player is online. Survives logout, restart, and dimension travel. Death removes it unless it was kept on death.
- `getGuarantees` returns specs whose duration is the time left.
- Grant and remove on the server. A call on the client side is ignored.

Example: a focus that lasts one minute and survives death.

```java
CritworksApi.grantGuarantee(player, GuaranteeSpec.of("mymod:focus").lasting(20 * 60).keptOnDeath());
```

## Opting out of an attack

```java
CritworksApi.optOutNextAttack(EntityPlayer player)
```

- The player's next attack cannot crit, whatever its chance or guarantees.
- The next melee swing, arrow, or integration attack uses up the mark, even one that could not crit anyway, such as a punch or a stick swing.
- An opted-out attack uses no guarantee.
- The mark is not saved. A call on the client side is ignored.

## Other attackers

```java
CritworksApi.registerAttackerPolicy(Predicate<EntityLivingBase> policy)
```

- By default only players crit. A policy lets other attackers crit too. Players always can.
- An allowed attacker's melee hit is damage of type "mob" dealt by the attacker itself. It resolves the stack in the attacker's main hand like a player's attack, at full charge, with no bonuses except weapon-specific ones and no guarantees.
- Each hit is decided on its own: it rolls once and is its own attack.
- Arrows of other attackers are not supported attacks.
- The policy is asked on the server for each hit, so it must be cheap.
- A policy that throws is reported once and then counts as "no".
- The `pvpCrits` setting applies to these hits too.
- Weapon bonus providers get a null player for these hits.

Example:

```java
CritworksApi.registerAttackerPolicy(attacker -> attacker instanceof EntityWitherSkeleton);
```

## Attack integrations

An attack integration connects another combat system that makes its own attacks, such as a mod with its own swing or throw.

```java
CritworksApi.registerAttackIntegration(IAttackIntegration integration)
CritworksApi.beginAttack(EntityPlayer player, ItemStack stack, EnumHand hand, CritAttackKind kind, float charge)
CritworksApi.endAttack(EntityPlayer player)
```

- Register the integration once during loading. `getId()` returns a stable id such as `"mymod:throw"`. A missing id or an id that is already registered throws `IllegalArgumentException`.
- Wrap each attack in `beginAttack` and `endAttack` on the server. Call `beginAttack` just before your attack deals damage.
- Critworks decides the attack once, at `beginAttack`, with the same rules as a player's own attack.
- `kind`: `CritAttackKind.MELEE` recognizes swords and axes and uses the melee charge threshold. `CritAttackKind.RANGED` recognizes bows and uses the bow charge threshold.
- `charge` is the attack's charge from 0 to 1. A value outside that range throws `IllegalArgumentException`.
- Until `endAttack`, damage whose attacker is that player belongs to the attack, except arrows, thorns, explosions, fire, and magic. It is multiplied when the attack is critical.
- Each target hit successfully by a critical integration attack fires `CritworksHitEvent` with kind `INTEGRATION`.
- A vanilla swing the integration makes in between is part of it and has no crit of its own.
- Attacks do not overlap. A begin ends the player's earlier swing of the same tick, so a begin from an attack event handler during a swing takes that swing over. A begin while that swing is dealing a hit (from a hurt or damage event handler) opens nothing.
- An attack still open at the end of the server tick ends then.
- A begin from inside a `CritworksHitEvent` handler is a reaction and opens no attack. An end call from a `CritworksHitEvent` handler is ignored; the attack then ends with the server tick.
- `endAttack` does nothing when no attack is open.

Example:

```java
public static final IAttackIntegration THROW = () -> "mymod:throw";

// During loading:
CritworksApi.registerAttackIntegration(THROW);

// On the server, for each attack:
CritworksApi.beginAttack(player, stack, EnumHand.MAIN_HAND, CritAttackKind.MELEE, 1.0F);
try {
    target.attackEntityFrom(DamageSource.causePlayerDamage(player), damage);
} finally {
    CritworksApi.endAttack(player);
}
```

## The critical-hit event

`CritworksHitEvent` is fired on `MinecraftForge.EVENT_BUS`, on the logical server only, once for each target that a critical attack hits successfully.

- A successful hit: the target lost health or absorption hearts.
- A miss, a cancel, a shield block, a hit stopped by immunity time, or a hit armor reduces to nothing fires no event.
- The event also fires for a critical attack at x1.00.
- It cannot be canceled.
- It fires while the hit is applied: absorption hearts are already taken, and the target's health is reduced right after the handlers return. `getTarget().getHealth()` still shows the health before the hit.

Getters:

| Method | Value |
| --- | --- |
| `getAttacker()` | The attacker. Null for an arrow whose shooter is unknown, for example after the world was reloaded. |
| `getTarget()` | The target. |
| `getWeapon()` | A copy of the attacking stack: the melee weapon or the bow. Empty for an arrow loaded from a save. |
| `getHand()` | The attacking hand. |
| `getKind()` | `MELEE_PRIMARY`, `MELEE_SWEEP`, `PROJECTILE`, or `INTEGRATION`. |
| `getMultiplier()` | The final damage multiplier the hit used, 1.00 or more. |
| `isGuaranteed()` | True when a guarantee made the attack critical, false when the roll did. |
| `getDamage()` | Health lost (at most the remaining health) plus absorption hearts lost, in health points. |
| `getDepth()` | 1 for the attack's own hit; 2 to `MAX_CHAIN_DEPTH` for hits by opted-in reactions. |
| `getChainLimit()` | The deepest level reactions to this event may reach: 0 until `allowChain` is called. |

A hit by another attacker that a policy allows has kind `MELEE_PRIMARY` and hand `MAIN_HAND`.

Reactions:

- Damage a handler deals while the event runs is a reaction. It cannot crit and fires no event.
- A swing a handler makes the attacker do opens no attack of its own. Its damage counts as a reaction.
- `allowChain(levels)` lets damage that handlers deal from then on, while this event runs, take part in the attack: it can crit and fire the event again, one level deeper. It applies to every handler that runs after the call. `levels` counts from the attack's own hit, at most `CritworksHitEvent.MAX_CHAIN_DEPTH` (4). Call it again from the deeper event to continue the chain.
- A reaction counts for the attack whose damage source it uses: player damage for that player's melee swing.

Example: heal the attacker by 1 health point on each critical hit that is not an arrow (melee and integration hits).

```java
@SubscribeEvent
public void onCriticalHit(CritworksHitEvent event) {
    if (event.getDepth() == 1 && event.getKind() != CritworksHitEvent.Kind.PROJECTILE
            && event.getAttacker() != null) {
        event.getAttacker().heal(1.0F);
    }
}
```

## Reading resolved stats

```java
CritStats stats = CritworksApi.resolve(EntityPlayer player, ItemStack stack, EnumHand hand)
```

- Resolves the stats an attack of the player with that stack in that hand would use now. It changes nothing and uses no guarantee.
- It resolves the stack's own attack kind: arrows for a bow, melee for everything else.
- Works on both sides once Critworks has finished loading.
- On the client it uses what the client's tooltips use: the server's settings and weapon rules while connected to a server, so it shows the numbers the player sees. Call it from the client thread there. The numbers an attack uses are always decided on the server.

`CritStats` getters:

| Method | Value |
| --- | --- |
| `isEligible()` | Whether the stack can crit. |
| `getEligibility()` | Why it can crit (for example "recognized weapon" or a pack rule), or why it cannot. |
| `getBaseChance()` | Base chance in percent; 0 when not eligible. |
| `getBaseChanceSource()` | A pack rule, an addon default, or "fallback". |
| `getBaseDamage()` | Base damage multiplier; 1.00 when not eligible. |
| `getBaseDamageSource()` | A pack rule, an addon default, or "fallback". |
| `getFinalChance()` | Final chance in percent, after bonuses, 0 to 100. |
| `getFinalMultiplier()` | Final multiplier, after bonuses and the cap, at least 1.00. |
| `getDamageCap()` | The damage cap the stats were resolved with (on a client connected to a server, the server's), or positive infinity when there is none. |
| `getBonuses()` | Every bonus counted: the player's global bonuses and the stack's weapon-specific bonuses. |

## Errors and failures

- Bad arguments throw `IllegalArgumentException` with the reason.
- Calls at the wrong time throw `IllegalStateException` (see [Setup and lifecycle](#setup-and-lifecycle)).
- A provider, predicate, or policy that throws is reported once in the log and then skipped. It never stops an attack.
- A bonus or base value that is not a finite number is left out and reported once with its source.

## Known limits for addon authors

- Addon defaults and providers are not sent to clients. Register them on both sides.
- In single player, providers and predicates run on the client thread and the server thread.
- `allowChain` applies to every handler that runs after the call on that event, not only to yours.
- An opted-in reaction uses the open melee swing of its damage source's player.
- A reaction that kills the target makes vanilla post `LivingDeathEvent` twice. This is vanilla behavior when damage lands inside a damage event.
- A begin from an attack event handler during a swing takes that swing over.
- An end call from a hit-event handler leaves the attack open until the server tick ends.
- A punch or a stick swing uses up an opt-out.
- Other damage of the vanilla player-attack type that the same player deals in the same tick after a swing counts as part of that swing.
