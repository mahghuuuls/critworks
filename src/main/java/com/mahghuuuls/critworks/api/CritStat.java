package com.mahghuuuls.critworks.api;

/**
 * The four player bonus stats. Units: increased bonuses are percent (20 = +20%); additional chance is
 * percentage points (5 = +5 points); additional damage is multiplier units (0.25 turns x1.50 into x1.75).
 */
public enum CritStat {
    INCREASED_CHANCE("critworks.increasedCritChance", "increased chance"),
    ADDITIONAL_CHANCE("critworks.additionalCritChance", "additional chance"),
    INCREASED_DAMAGE("critworks.increasedCritDamage", "increased damage"),
    ADDITIONAL_DAMAGE("critworks.additionalCritDamage", "additional damage");

    private final String attributeName;
    private final String label;

    CritStat(String attributeName, String label) {
        this.attributeName = attributeName;
        this.label = label;
    }

    /** The player attribute name, for configs of other mods such as Quality Tools. */
    public String getAttributeName() {
        return attributeName;
    }

    /** Short readable name for reports. */
    public String getLabel() {
        return label;
    }
}
