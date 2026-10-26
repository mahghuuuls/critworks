package com.mahghuuuls.critworks.integration.quark;

import java.lang.reflect.Method;

/**
 * Asks Quark, by reflection, whether its Visual Stat Display feature is on. The answer can change in
 * game through Quark's config screen, so it is asked for every tooltip; the method is looked up once.
 * Nothing outside this package names a Quark class.
 */
public final class QuarkStatDisplayProbe {

    /** Quark's module registry and its Visual Stat Display feature; the same names in Quark and Quark: RotN Edition. */
    public static final String MODULE_LOADER_CLASS = "vazkii.quark.base.module.ModuleLoader";
    public static final String VISUAL_STAT_DISPLAY_CLASS = "vazkii.quark.client.feature.VisualStatDisplay";

    private final String moduleLoaderClass;
    private final String featureClass;
    private Method isFeatureEnabled;
    private Class<?> feature;

    public QuarkStatDisplayProbe() {
        this(MODULE_LOADER_CLASS, VISUAL_STAT_DISPLAY_CLASS);
    }

    /** With other class names: for tests, and for a Quark release that renames them. */
    public QuarkStatDisplayProbe(String moduleLoaderClass, String featureClass) {
        this.moduleLoaderClass = moduleLoaderClass;
        this.featureClass = featureClass;
    }

    /**
     * Whether Visual Stat Display is on now.
     *
     * @throws ReflectiveOperationException when Quark's classes or method are not as expected; the caller
     *                                      turns the integration off
     */
    public boolean isOn() throws ReflectiveOperationException {
        if (isFeatureEnabled == null) {
            ClassLoader loader = QuarkStatDisplayProbe.class.getClassLoader();
            feature = Class.forName(featureClass, false, loader);
            isFeatureEnabled = Class.forName(moduleLoaderClass, false, loader).getMethod("isFeatureEnabled", Class.class);
        }
        return Boolean.TRUE.equals(isFeatureEnabled.invoke(null, feature));
    }
}
