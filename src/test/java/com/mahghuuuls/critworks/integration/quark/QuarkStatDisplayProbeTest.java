package com.mahghuuuls.critworks.integration.quark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The probe asks Quark's static {@code isFeatureEnabled(Class)} each time, and reports missing classes. */
class QuarkStatDisplayProbeTest {

    /** Stands in for Quark's module registry. */
    public static final class ModuleLoader {
        static boolean enabled;
        static Class<?> asked;

        public static boolean isFeatureEnabled(Class<?> feature) {
            asked = feature;
            return enabled;
        }
    }

    /** Stands in for Quark's Visual Stat Display feature class. */
    public static final class Feature {
    }

    @Test
    void theAnswerFollowsQuarkEachTime() throws Exception {
        QuarkStatDisplayProbe probe = new QuarkStatDisplayProbe(ModuleLoader.class.getName(), Feature.class.getName());
        ModuleLoader.enabled = true;
        assertTrue(probe.isOn());
        assertTrue(ModuleLoader.asked == Feature.class);
        ModuleLoader.enabled = false;
        assertFalse(probe.isOn(), "a change in Quark's config screen shows in the next tooltip");
    }

    @Test
    void missingQuarkClassesAreReported() {
        assertThrows(ClassNotFoundException.class,
                () -> new QuarkStatDisplayProbe("missing.ModuleLoader", Feature.class.getName()).isOn());
        assertThrows(ClassNotFoundException.class,
                () -> new QuarkStatDisplayProbe(ModuleLoader.class.getName(), "missing.Feature").isOn());
        assertThrows(NoSuchMethodException.class,
                () -> new QuarkStatDisplayProbe(Feature.class.getName(), Feature.class.getName()).isOn());
    }
}
