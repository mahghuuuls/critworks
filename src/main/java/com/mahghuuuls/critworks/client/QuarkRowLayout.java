package com.mahghuuuls.critworks.client;

import com.mahghuuuls.critworks.core.CritProfile;

import java.util.Arrays;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * What the Critworks icon row shows and how wide it is. Where its line goes in the tooltip is Quark
 * knowledge, in {@code integration.quark.QuarkTooltipLines}; the drawing is in {@link QuarkStatRow}.
 */
final class QuarkRowLayout {

    static final int CHANCE_ICON = 0;
    static final int DAMAGE_ICON = 1;
    /** As Quark and JASS Integrations: the number starts 12 pixels after its icon, the next icon 20 pixels after the number. */
    static final int TEXT_OFFSET = 12;
    static final int ENTRY_GAP = 20;

    private QuarkRowLayout() {
    }

    /** One icon and its number. */
    static final class Entry {
        final int icon;
        final String text;

        Entry(int icon, String text) {
            this.icon = icon;
            this.text = text;
        }
    }

    /** Base chance and base multiplier, as the one-line text shows them (REQ-084). */
    static List<Entry> entries(CritProfile profile) {
        return Arrays.asList(new Entry(CHANCE_ICON, TooltipLines.percent(profile.getBaseChance())),
                new Entry(DAMAGE_ICON, TooltipLines.multiplier(profile.getBaseMultiplier())));
    }

    static int rowWidth(List<Entry> entries, ToIntFunction<String> textWidth) {
        int width = 0;
        for (Entry entry : entries) {
            width += TEXT_OFFSET + textWidth.applyAsInt(entry.text);
        }
        return width + (ENTRY_GAP - TEXT_OFFSET) * Math.max(0, entries.size() - 1);
    }
}
