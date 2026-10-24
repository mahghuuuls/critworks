package com.mahghuuuls.critworks.command;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.bonus.Bonus;
import com.mahghuuuls.critworks.bonus.BonusCollector;
import com.mahghuuuls.critworks.core.CritFormula;
import com.mahghuuuls.critworks.core.CritProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Formats what {@code /critworks inspect} shows for one player: for each hand, eligibility and reason,
 * base values with their sources, bonuses, effective values, and the cap. Text only; it never reads or
 * changes game state.
 */
final class InspectionReport {

    private InspectionReport() {
    }

    static List<String> lines(String playerName, double cap, List<Bonus> playerBonuses, HandView mainHand,
                              HandView offHand) {
        List<String> lines = new ArrayList<String>();
        lines.add("Critworks inspect: " + playerName);
        lines.add("Damage cap: " + (cap == CritFormula.NO_CAP ? "none" : multiplier(cap)));
        StringBuilder totals = new StringBuilder("Player bonus totals:");
        for (CritStat stat : CritStat.values()) {
            totals.append(stat == CritStat.values()[0] ? " " : ", ").append(stat.getAttributeName()).append(' ')
                    .append(signed(BonusCollector.total(playerBonuses, stat)));
        }
        lines.add(totals.toString());
        hand(lines, "Main hand", mainHand);
        hand(lines, "Offhand", offHand);
        lines.add("Guarantees: none");
        return lines;
    }

    private static void hand(List<String> lines, String label, HandView view) {
        CritProfile profile = view.profile;
        if (!profile.isEligible()) {
            lines.add(label + ": " + view.itemName + " - not eligible: " + profile.getIneligibleReason());
            return;
        }
        lines.add(label + ": " + view.itemName + " - eligible: " + profile.getEligibilitySource());
        lines.add("  Base chance " + percent(profile.getBaseChance()) + " from " + profile.getBaseChanceSource());
        lines.add("  Base damage " + multiplier(profile.getBaseMultiplier()) + " from " + profile.getBaseMultiplierSource());
        if (profile.getBonuses().isEmpty()) {
            lines.add("  Bonuses: none");
        }
        for (Bonus bonus : profile.getBonuses()) {
            lines.add("  Bonus " + signed(bonus.getAmount()) + " " + bonus.getStat().getLabel() + " from "
                    + bonus.getSource());
        }
        lines.add("  Effective chance " + percent(profile.getFinalChance()) + ", damage "
                + multiplier(profile.getFinalMultiplier()));
    }

    static String signed(double value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }

    static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    static String multiplier(double value) {
        return String.format(Locale.ROOT, "x%.2f", value);
    }

    /** One hand's item name and resolved profile. */
    static final class HandView {
        final String itemName;
        final CritProfile profile;

        HandView(String itemName, CritProfile profile) {
            this.itemName = itemName;
            this.profile = profile;
        }
    }
}
