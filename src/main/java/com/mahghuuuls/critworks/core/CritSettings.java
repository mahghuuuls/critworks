package com.mahghuuuls.critworks.core;

/**
 * The general settings read from {@code config/critworks.cfg} at start. Immutable: a change needs a
 * restart, and nothing reads the file during play.
 */
public final class CritSettings {

    public static final double DEFAULT_FALLBACK_CHANCE = 5.0D;
    public static final double DEFAULT_FALLBACK_MULTIPLIER = 1.5D;
    public static final double DEFAULT_MELEE_THRESHOLD = 100.0D;
    public static final double DEFAULT_BOW_THRESHOLD = 100.0D;
    public static final String SOUND_AUTO = "auto";
    public static final String SOUND_NONE = "none";

    /** The optional mod integrations that have an on/off setting. */
    public enum Integration {
        RLCOMBAT("rlcombat", "RLCombat main-hand and offhand attacks"),
        JAFS("jafs", "Just Another Food System crit properties"),
        QUARK("quark", "Quark Visual Stat Display icon row in tooltips"),
        SPARTAN_WEAPONRY("spartanweaponry", "Spartan Weaponry weapon-type rules"),
        QUALITY_TOOLS("qualitytools", "Quality Tools qualities counted only for their own weapon");

        private final String key;
        private final String description;

        Integration(String key, String description) {
            this.key = key;
            this.description = description;
        }

        public String getKey() {
            return key;
        }

        public String getDescription() {
            return description;
        }
    }

    private final double fallbackChance;
    private final double fallbackMultiplier;
    private final double meleeChargeThreshold;
    private final double bowChargeThreshold;
    private final double damageCap;
    private final boolean pvpCrits;
    private final String critSound;
    private final boolean diagnosticMode;
    private final boolean[] integrations;

    public CritSettings(double fallbackChance, double fallbackMultiplier, double meleeChargeThreshold,
                        double bowChargeThreshold, double damageCap, boolean pvpCrits, String critSound,
                        boolean diagnosticMode, boolean[] integrations) {
        this.fallbackChance = fallbackChance;
        this.fallbackMultiplier = fallbackMultiplier;
        this.meleeChargeThreshold = meleeChargeThreshold;
        this.bowChargeThreshold = bowChargeThreshold;
        this.damageCap = damageCap;
        this.pvpCrits = pvpCrits;
        this.critSound = critSound;
        this.diagnosticMode = diagnosticMode;
        this.integrations = integrations.clone();
    }

    public static CritSettings defaults() {
        boolean[] all = new boolean[Integration.values().length];
        java.util.Arrays.fill(all, true);
        return new CritSettings(DEFAULT_FALLBACK_CHANCE, DEFAULT_FALLBACK_MULTIPLIER, DEFAULT_MELEE_THRESHOLD,
                DEFAULT_BOW_THRESHOLD, CritFormula.NO_CAP, true, SOUND_AUTO, false, all);
    }

    /** Global fallback critical chance, in percent. */
    public double getFallbackChance() {
        return fallbackChance;
    }

    /** Global fallback critical damage multiplier. */
    public double getFallbackMultiplier() {
        return fallbackMultiplier;
    }

    /** Minimum melee cooldown recovery for an eligible attack, in percent. */
    public double getMeleeChargeThreshold() {
        return meleeChargeThreshold;
    }

    /** Minimum bow draw for an eligible arrow, in percent of full draw. */
    public double getBowChargeThreshold() {
        return bowChargeThreshold;
    }

    /** Maximum final multiplier, or {@link CritFormula#NO_CAP}. */
    public double getDamageCap() {
        return damageCap;
    }

    /** Whether Critworks crits may happen against players. */
    public boolean isPvpCrits() {
        return pvpCrits;
    }

    /** {@link #SOUND_AUTO}, {@link #SOUND_NONE}, or a sound event id. */
    public String getCritSound() {
        return critSound;
    }

    public boolean isDiagnosticMode() {
        return diagnosticMode;
    }

    public boolean isIntegrationEnabled(Integration integration) {
        return integrations[integration.ordinal()];
    }
}
