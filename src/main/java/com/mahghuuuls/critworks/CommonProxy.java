package com.mahghuuuls.critworks;

import com.mahghuuuls.critworks.bonus.BonusCollector;
import com.mahghuuuls.critworks.core.AddonDefaults;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.WeaponRules;
import org.apache.logging.log4j.Logger;

/**
 * Side-specific start-up. A dedicated server adds nothing; the client proxy adds the tooltips. Forge picks
 * the class by name, so a dedicated server never loads the client package (REQ-131).
 */
public class CommonProxy {

    /** Called at the end of postInit, when the local settings and rules are read. */
    public void postInit(CritSettings settings, WeaponRules rules, BonusCollector bonuses, AddonDefaults defaults,
                         Logger logger) {
    }
}
