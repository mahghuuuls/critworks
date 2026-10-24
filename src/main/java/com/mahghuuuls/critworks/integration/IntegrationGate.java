package com.mahghuuuls.critworks.integration;

import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.CritSettings.Integration;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The one place that decides whether an optional integration is on: its mod is loaded, its setting is
 * on, and it has not failed this session. A failure turns that integration off with one warning; the
 * other integrations and the core crits keep working.
 */
public final class IntegrationGate {

    private final Predicate<String> modLoaded;
    private final CritSettings settings;
    private final Consumer<String> warn;
    private final Set<Integration> failed = EnumSet.noneOf(Integration.class);

    /**
     * @param modLoaded answers whether a mod id is loaded ({@code Loader::isModLoaded} in the game)
     * @param warn      receives the one warning of each failed integration
     */
    public IntegrationGate(Predicate<String> modLoaded, CritSettings settings, Consumer<String> warn) {
        this.modLoaded = modLoaded;
        this.settings = settings;
        this.warn = warn;
    }

    /** The mod id each integration needs. */
    public static String modId(Integration integration) {
        switch (integration) {
            case RLCOMBAT:
                return "bettercombatmod";
            case JAFS:
                return "jafs";
            case QUARK:
                return "quark";
            case SPARTAN_WEAPONRY:
                return "spartanweaponry";
            case QUALITY_TOOLS:
                return "qualitytools";
            default:
                throw new IllegalArgumentException(String.valueOf(integration));
        }
    }

    /** Whether the integration's mod is loaded, whatever its setting. */
    public boolean isLoaded(Integration integration) {
        return modLoaded.test(modId(integration));
    }

    public boolean isOn(Integration integration) {
        return !failed.contains(integration) && settings.isIntegrationEnabled(integration) && isLoaded(integration);
    }

    /** Turns the integration off for this session. Only the first failure of an integration is reported. */
    public void fail(Integration integration, Throwable cause) {
        if (failed.add(integration)) {
            warn.accept("Critworks " + integration.getKey() + " integration failed and is off until restart: " + cause);
        }
    }
}
