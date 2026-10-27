package com.mahghuuuls.critworks.config;

import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.CritFormula;
import net.minecraftforge.common.config.Configuration;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/**
 * Reads {@code config/critworks.cfg}. The file is written only when it does not exist yet; an existing
 * file is never saved over, so a user's file stays exactly as they wrote it. Every value is stored as
 * text and checked here, because Forge's typed properties would replace a bad value with the default
 * without a word. A bad value is reported once and its default is used; a missing key uses its default.
 * Forge renames a file it cannot parse to {@code .errored}, so an existing file is read through a
 * temporary copy and the user's file is never touched.
 */
public final class SettingsReader {

    private static final String GENERAL = "general";
    private static final String INTEGRATIONS = "integrations";

    private final Consumer<String> warnings;

    public SettingsReader(Consumer<String> warnings) {
        this.warnings = warnings;
    }

    public CritSettings read(File file) {
        if (!file.exists()) {
            return read(file, new Configuration(file), true);
        }
        File copy = null;
        try {
            copy = File.createTempFile("critworks-cfg-", ".cfg");
            Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Configuration config = new Configuration(copy);
            if (wasRenamedAsErrored(copy)) {
                // Forge could not parse the copy, renamed it to .errored, and loaded an empty file instead.
                warnings.accept("Critworks config " + file.getName() + " could not be parsed; every setting uses its"
                        + " default until the file is fixed");
            }
            return read(file, config, false);
        } catch (IOException e) {
            warnings.accept("Critworks config " + file.getName() + " could not be read (" + e.getMessage()
                    + "); using the defaults");
            return CritSettings.defaults();
        } finally {
            if (copy != null) {
                deleteQuietly(copy);
            }
        }
    }

    private static boolean wasRenamedAsErrored(File copy) {
        File[] errored = copy.getParentFile().listFiles(
                (dir, name) -> name.startsWith(copy.getName() + "_") && name.endsWith(".errored"));
        return errored != null && errored.length > 0;
    }

    /** Removes the copy and anything Forge made from it (its save buffer and its {@code .errored} rename). */
    private static void deleteQuietly(File copy) {
        File[] leftovers = copy.getParentFile().listFiles((dir, name) -> name.startsWith(copy.getName()));
        if (leftovers == null) {
            return;
        }
        for (File leftover : leftovers) {
            try {
                Files.deleteIfExists(leftover.toPath());
            } catch (IOException ignored) {
                // A leftover temporary file is harmless.
            }
        }
    }

    private CritSettings read(File file, Configuration config, boolean create) {
        CritSettings defaults = CritSettings.defaults();
        config.setCategoryComment(GENERAL, "Critworks general settings. Every change needs a restart.");
        config.setCategoryComment(INTEGRATIONS, "Optional mod integrations. Each one turns on by itself when its mod is"
                + " installed; set it to false to turn it off.");

        double fallbackChance = readNumber(config, file, GENERAL, "fallbackChance", defaults.getFallbackChance(),
                CritFormula.MIN_CHANCE, CritFormula.MAX_CHANCE,
                "Critical chance, in percent, of an eligible weapon that has no pack rule and no addon default.\n"
                        + "Range: 0 to 100. Default: 15.");
        double fallbackMultiplier = readNumber(config, file, GENERAL, "fallbackDamage", defaults.getFallbackMultiplier(),
                CritFormula.MIN_MULTIPLIER, Double.MAX_VALUE,
                "Critical damage multiplier of an eligible weapon that has no pack rule and no addon default.\n"
                        + "2.0 means x2.00. Minimum: 1.0. Default: 2.0.");
        double meleeThreshold = readNumber(config, file, GENERAL, "meleeChargeThreshold",
                defaults.getMeleeChargeThreshold(), 0.0D, 100.0D,
                "Minimum attack cooldown recovery, in percent, for a melee attack to be able to crit.\n"
                        + "100 means a fully charged attack. Range: 0 to 100. Default: 100.");
        double bowThreshold = readNumber(config, file, GENERAL, "bowChargeThreshold", defaults.getBowChargeThreshold(),
                0.0D, 100.0D,
                "Minimum bow draw, in percent of a full draw, for an arrow to be able to crit.\n"
                        + "100 means a fully drawn bow. Range: 0 to 100. Default: 100.");
        double damageCap = readCap(config, file);
        boolean pvpCrits = readBoolean(config, file, GENERAL, "pvpCrits", defaults.isPvpCrits(),
                "Whether Critworks crits can happen against players. Normal PvP rules still decide whether a hit\n"
                        + "lands at all. Default: true.");
        String critSound = config.get(GENERAL, "critSound", defaults.getCritSound(),
                "Sound of a critical attack: \"auto\" (RLCombat's crit sound when RLCombat is installed, otherwise\n"
                        + "the vanilla crit sound), \"none\", or a sound event id such as minecraft:entity.player.attack.crit.\n"
                        + "Default: auto.").getString().trim();
        boolean diagnosticMode = readBoolean(config, file, GENERAL, "diagnosticMode", defaults.isDiagnosticMode(),
                "Write one server log line per supported attack with its critical decision.\n"
                        + "For testing and troubleshooting; leave off in normal play. Default: false.");

        CritSettings.Integration[] all = CritSettings.Integration.values();
        boolean[] integrations = new boolean[all.length];
        for (CritSettings.Integration integration : all) {
            integrations[integration.ordinal()] = readBoolean(config, file, INTEGRATIONS, integration.getKey(), true,
                    integration.getDescription() + ". Default: true.");
        }

        if (create) {
            config.save();
        }
        return new CritSettings(fallbackChance, fallbackMultiplier, meleeThreshold, bowThreshold, damageCap, pvpCrits,
                critSound, diagnosticMode, integrations);
    }

    private double readCap(Configuration config, File file) {
        String raw = config.get(GENERAL, "damageCap", "0",
                "Highest critical damage multiplier any attack can reach, for example 3 for x3.00.\n"
                        + "0 means no maximum. Otherwise at least 1. Default: 0.").getString().trim();
        Double value = parseNumber(raw);
        if (value == null) {
            warn(file, "damageCap", raw, "not a number");
            return CritFormula.NO_CAP;
        }
        if (value == 0.0D) {
            return CritFormula.NO_CAP;
        }
        if (value < CritFormula.MIN_MULTIPLIER) {
            warn(file, "damageCap", raw, "below 1 (use 0 for no maximum)");
            return CritFormula.NO_CAP;
        }
        return value;
    }

    private double readNumber(Configuration config, File file, String category, String key, double defaultValue,
                              double min, double max, String comment) {
        String raw = config.get(category, key, format(defaultValue), comment).getString().trim();
        Double value = parseNumber(raw);
        if (value == null) {
            warn(file, key, raw, "not a number");
            return defaultValue;
        }
        if (value < min || value > max) {
            warn(file, key, raw, "outside " + format(min) + " to " + (max == Double.MAX_VALUE ? "any" : format(max)));
            return defaultValue;
        }
        return value;
    }

    private boolean readBoolean(Configuration config, File file, String category, String key, boolean defaultValue,
                                String comment) {
        String raw = config.get(category, key, String.valueOf(defaultValue), comment).getString().trim();
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        warn(file, key, raw, "not true or false");
        return defaultValue;
    }

    /** A finite number, or null. */
    static Double parseNumber(String raw) {
        try {
            double value = Double.parseDouble(raw);
            return CritFormula.isUsable(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private void warn(File file, String key, String raw, String reason) {
        warnings.accept("Critworks config " + file.getName() + ": " + key + " = \"" + raw + "\" is " + reason
                + "; using the default");
    }
}
