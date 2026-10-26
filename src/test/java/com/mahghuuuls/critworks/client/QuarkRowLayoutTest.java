package com.mahghuuuls.critworks.client;

import com.mahghuuuls.critworks.core.CritFormula;
import com.mahghuuuls.critworks.core.CritProfile;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.integration.IntegrationGate;
import com.mahghuuuls.critworks.integration.quark.QuarkStatDisplayProbe;
import com.mahghuuuls.critworks.integration.quark.QuarkTooltipLines;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Critworks row's place in a tooltip with Quark's rows and the JASS Integrations row in either order
 * (REQ-085), its marker, its values (REQ-084), and the switch-off on a failed Quark probe (REQ-181).
 */
class QuarkRowLayoutTest {

    private static final String TITLE = "Iron Sword";
    private static final String QUARK_ROW = "          ";
    private static final String JASS_ROW = QuarkTooltipLines.JASS_MARKER + "        ";
    private static final String ROW = QuarkTooltipLines.markerLine(40, 4);

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    private static List<String> tooltip(String... lines) {
        return new ArrayList<String>(Arrays.asList(lines));
    }

    /** JASS Integrations' own move, as it ships: after the title and Quark's space-only lines. */
    private static void jassMovesItsRow(List<String> tooltip) {
        int marker = -1;
        for (int i = 1; i < tooltip.size(); i++) {
            if (tooltip.get(i).contains(QuarkTooltipLines.JASS_MARKER)) {
                marker = i;
                break;
            }
        }
        String line = tooltip.remove(marker);
        int position = 1;
        while (position < tooltip.size() && QuarkTooltipLines.isQuarkRowLine(tooltip.get(position))) {
            position++;
        }
        tooltip.add(Math.min(position, tooltip.size()), line);
    }

    @Test
    void theRowGoesBelowQuarksRows() {
        List<String> lines = tooltip(TITLE, QUARK_ROW, QUARK_ROW, "When in main hand:", " 6 Attack Damage");
        QuarkTooltipLines.insert(lines, ROW);
        assertEquals(3, lines.indexOf(ROW));
    }

    @Test
    void theRowGoesRightBelowTheTitleWithoutQuarkRows() {
        List<String> lines = tooltip(TITLE, "", "Quality: Fine");
        QuarkTooltipLines.insert(lines, ROW);
        assertEquals(1, lines.indexOf(ROW), "an empty separator line is not a Quark row");
    }

    @Test
    void theRowGoesBelowJassWhenJassRanFirst() {
        List<String> lines = tooltip(TITLE, QUARK_ROW, JASS_ROW, "When in main hand:");
        QuarkTooltipLines.insert(lines, ROW);
        assertEquals(Arrays.asList(TITLE, QUARK_ROW, JASS_ROW, ROW, "When in main hand:"), lines);
    }

    @Test
    void theRowStaysBelowJassWhenJassRunsAfterwards() {
        List<String> lines = tooltip(TITLE, QUARK_ROW, "When in main hand:", JASS_ROW);
        QuarkTooltipLines.insert(lines, ROW);
        jassMovesItsRow(lines);
        assertEquals(Arrays.asList(TITLE, QUARK_ROW, JASS_ROW, ROW, "When in main hand:"), lines);
    }

    @Test
    void theMarkerKeepsTheLineFromBeingBlankAndIsFoundBehindOtherCodes() {
        assertFalse(QuarkTooltipLines.isQuarkRowLine(ROW), "Quark and JASS Integrations must not take the row's line");
        assertTrue(TextFormatting.getTextWithoutFormattingCodes(ROW).startsWith(QuarkTooltipLines.MARKER));
        assertFalse(QuarkTooltipLines.MARKER.equals(QuarkTooltipLines.JASS_MARKER));
        List<String> creative = tooltip(TITLE, TextFormatting.GRAY + ROW);
        assertEquals(1, QuarkTooltipLines.markerLineIndex(creative));
        assertEquals(-1, QuarkTooltipLines.markerLineIndex(tooltip(ROW)), "the title is never the row");
    }

    @Test
    void theRowShowsTheBaseValuesAndReservesItsWidth() {
        CritProfile profile = CritProfile.eligible("recognized weapon", 5.0D, "fallback", 1.5D, "fallback", 15.0D, 2.0D,
                CritFormula.NO_CAP, Collections.emptyList());
        List<QuarkRowLayout.Entry> entries = QuarkRowLayout.entries(profile);
        assertEquals("5%", entries.get(0).text);
        assertEquals(QuarkRowLayout.CHANCE_ICON, entries.get(0).icon);
        assertEquals("x1.50", entries.get(1).text);
        assertEquals(QuarkRowLayout.DAMAGE_ICON, entries.get(1).icon);
        int width = QuarkRowLayout.rowWidth(entries, String::length);
        assertEquals(12 + 2 + 8 + 12 + 5, width);
        String line = QuarkTooltipLines.markerLine(width, 4);
        assertEquals(QuarkTooltipLines.MARKER.length() + (width + 3) / 4, line.length());
    }

    @Test
    void aFailedQuarkProbeTurnsTheRowOffWithOneWarning() {
        List<String> warnings = new ArrayList<String>();
        IntegrationGate gate = new IntegrationGate(id -> true, CritSettings.defaults(), warnings::add);
        QuarkStatRow row = new QuarkStatRow(gate, new QuarkStatDisplayProbe("missing.ModuleLoader", "missing.Feature"));
        assertFalse(row.shows(), "a probe that cannot find Quark's classes");
        assertFalse(row.shows());
        assertEquals(1, warnings.size());
        assertFalse(gate.isOn(CritSettings.Integration.QUARK));
    }

    @Test
    void theCritworksSwitchKeepsTheRowOff() {
        boolean[] integrations = new boolean[CritSettings.Integration.values().length];
        Arrays.fill(integrations, true);
        integrations[CritSettings.Integration.QUARK.ordinal()] = false;
        CritSettings settings = new CritSettings(5.0D, 1.5D, 100.0D, 100.0D, CritFormula.NO_CAP, true,
                CritSettings.SOUND_AUTO, false, integrations);
        List<String> warnings = new ArrayList<String>();
        QuarkStatRow row = new QuarkStatRow(new IntegrationGate(id -> true, settings, warnings::add),
                new QuarkStatDisplayProbe("missing.ModuleLoader", "missing.Feature"));
        assertFalse(row.shows());
        assertEquals(0, warnings.size(), "a switched-off integration never asks Quark");
    }

    @Test
    void aRowThatCannotBeBuiltLeavesTheTooltipAndTurnsTheRowOff() {
        List<String> warnings = new ArrayList<String>();
        IntegrationGate gate = new IntegrationGate(id -> true, CritSettings.defaults(), warnings::add);
        QuarkStatRow row = new QuarkStatRow(gate, new QuarkStatDisplayProbe("missing.ModuleLoader", "missing.Feature"));
        CritProfile profile = CritProfile.eligible("recognized weapon", 5.0D, "fallback", 1.5D, "fallback", 5.0D, 1.5D,
                CritFormula.NO_CAP, Collections.emptyList());
        List<String> lines = tooltip(TITLE);
        // No game client in a unit test, so finding the tooltip font fails as a broken font would.
        assertFalse(row.add(lines, new ItemStack(Items.IRON_SWORD), profile));
        assertEquals(Collections.singletonList(TITLE), lines);
        assertEquals(1, warnings.size());
        assertFalse(gate.isOn(CritSettings.Integration.QUARK));
    }
}
