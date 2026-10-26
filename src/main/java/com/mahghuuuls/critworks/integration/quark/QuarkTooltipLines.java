package com.mahghuuuls.critworks.integration.quark;

import net.minecraft.util.text.TextFormatting;

import java.util.List;

/**
 * What Critworks knows about Quark's tooltip lines and the JASS Integrations row, so a change in either
 * mod touches only this package. The Critworks row is one marker line: two format codes that the font
 * draws as nothing and that neither Minecraft nor Quark strips, so the line is never blank and Quark
 * never takes it, then spaces that reserve the row's width. Quark reserves its own rows as space-only
 * lines after the title and draws on the first blank one; JASS Integrations marks its row with
 * {@link #JASS_MARKER} and moves it right after Quark's lines.
 */
public final class QuarkTooltipLines {

    /** Critworks' own marker; JASS Integrations uses {@link #JASS_MARKER}, so the two rows never take each other's line. */
    public static final String MARKER = "§q§y";
    public static final String JASS_MARKER = "§z§j";

    private QuarkTooltipLines() {
    }

    /** The marker followed by enough spaces to cover {@code width} pixels. */
    public static String markerLine(int width, int spaceWidth) {
        StringBuilder line = new StringBuilder(MARKER);
        int step = spaceWidth > 0 ? spaceWidth : 4;
        for (int covered = 0; covered < width; covered += step) {
            line.append(' ');
        }
        return line.toString();
    }

    /**
     * Puts the marker line right after the title, Quark's space-only lines, and the JASS Integrations
     * line. If JASS Integrations moves its line later, it moves it to right after Quark's lines, which is
     * still above this one, because this line is not space-only to it.
     */
    public static void insert(List<String> tooltip, String markerLine) {
        int position = Math.min(1, tooltip.size());
        while (position < tooltip.size() && (isQuarkRowLine(tooltip.get(position)) || tooltip.get(position).contains(JASS_MARKER))) {
            position++;
        }
        tooltip.add(position, markerLine);
    }

    /** A line Quark reserves for a row: spaces only, never empty (an empty line is another mod's separator). */
    public static boolean isQuarkRowLine(String line) {
        String text = TextFormatting.getTextWithoutFormattingCodes(line);
        return text != null && !text.isEmpty() && text.trim().isEmpty();
    }

    /** The index of the Critworks marker line after the title, or -1. Other format codes may come before the marker. */
    public static int markerLineIndex(List<String> lines) {
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).contains(MARKER)) {
                return i;
            }
        }
        return -1;
    }
}
