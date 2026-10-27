package com.mahghuuuls.critworks.client;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.bonus.Bonus;
import com.mahghuuuls.critworks.core.CritProfile;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The Critworks tooltip text for one resolved profile: one line with the base chance and multiplier
 * (REQ-092), and with Shift the effective values labeled as a current preview, with each bonus and its
 * source, each value with its unit (REQ-093). Text only; it reads the profile and nothing else.
 */
final class TooltipLines {

    /** The name vanilla gives every attribute modifier it sends to the client. */
    static final String UNNAMED_SYNCED_MODIFIER = "Unknown synced attribute modifier";

    private TooltipLines() {
    }

    static List<String> lines(CritProfile profile, boolean expanded, Translator translator) {
        if (!profile.isEligible()) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<String>();
        lines.add(TextFormatting.GRAY + translator.format("critworks.tooltip.stats", percent(profile.getBaseChance()),
                multiplier(profile.getBaseMultiplier())));
        if (!expanded) {
            return lines;
        }
        lines.add(TextFormatting.DARK_GRAY + translator.format("critworks.tooltip.preview"));
        lines.add(TextFormatting.GRAY + " " + translator.format("critworks.tooltip.effective",
                percent(profile.getFinalChance()), multiplier(profile.getFinalMultiplier())));
        if (profile.getBonuses().isEmpty()) {
            lines.add(TextFormatting.DARK_GRAY + " " + translator.format("critworks.tooltip.no_bonuses"));
        }
        for (Bonus bonus : profile.getBonuses()) {
            String source = UNNAMED_SYNCED_MODIFIER.equals(bonus.getSource())
                    ? translator.format("critworks.tooltip.unknown_source") : bonus.getSource();
            lines.add(TextFormatting.DARK_GRAY + " " + translator.format("critworks.tooltip.bonus",
                    bonusValue(bonus), translator.format("attribute.name." + bonus.getStat().getAttributeName()),
                    source));
        }
        return lines;
    }

    /** At most two decimals, without trailing zeros: 5%, 12.5%. */
    static String percent(double value) {
        return number(value) + "%";
    }

    static String multiplier(double value) {
        return String.format(Locale.ROOT, "x%.2f", value);
    }

    /** The bonus amount with its unit, value first: "%" for every stat but additional damage, which is in multiplier units. */
    static String bonusValue(Bonus bonus) {
        String value = signed(bonus.getAmount());
        return bonus.getStat() == CritStat.ADDITIONAL_DAMAGE ? value : value + "%";
    }

    static String signed(double value) {
        return (value < 0.0D ? "-" : "+") + number(Math.abs(value));
    }

    private static String number(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        text = text.replaceAll("0+$", "");
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    /** Looks up a language key; the client uses {@code I18n.format}. */
    interface Translator {
        String format(String key, Object... args);
    }
}
