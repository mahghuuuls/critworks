package com.mahghuuuls.critworks.api;

/**
 * Another combat system that makes its own attacks, such as a mod with its own swing or throw. Register it
 * once during loading with {@link CritworksApi#registerAttackIntegration}; then wrap each attack in
 * {@link CritworksApi#beginAttack} and {@link CritworksApi#endAttack}. Critworks decides the attack once at
 * the begin call. Until the end call, damage whose attacker is that player belongs to the attack, except
 * arrows, thorns, explosions, fire, and magic: it is multiplied when critical, and each successful target
 * fires {@link CritworksHitEvent} with kind {@link CritworksHitEvent.Kind#INTEGRATION}. A vanilla swing the
 * integration makes in between is part of it and has no crit of its own. Attacks do not overlap: a begin
 * ends the player's earlier swing of the same tick (a begin from an attack event handler during a swing
 * therefore takes that swing over), and a begin while that swing is dealing a hit (from a hurt or damage
 * event handler) opens nothing. An end call from a {@link CritworksHitEvent} handler is ignored; the attack
 * then ends with the server tick.
 */
public interface IAttackIntegration {

    /** A stable id such as "mymod:throw"; registration checks that it is unique and logs it. */
    String getId();
}
