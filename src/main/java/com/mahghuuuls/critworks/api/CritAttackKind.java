package com.mahghuuuls.critworks.api;

/** Which weapons count for an integration attack, and which charge threshold applies. */
public enum CritAttackKind {
    /** Swords and axes are recognized; the melee charge threshold applies. */
    MELEE,
    /** Bows are recognized; the bow charge threshold applies. */
    RANGED
}
