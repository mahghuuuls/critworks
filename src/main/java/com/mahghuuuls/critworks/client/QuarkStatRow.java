package com.mahghuuuls.critworks.client;

import com.mahghuuuls.critworks.Tags;
import com.mahghuuuls.critworks.core.CritProfile;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.integration.IntegrationGate;
import com.mahghuuuls.critworks.integration.quark.QuarkStatDisplayProbe;
import com.mahghuuuls.critworks.integration.quark.QuarkTooltipLines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.List;

/**
 * The Critworks icon row in Quark's Visual Stat Display style (REQ-084): used instead of the one-line
 * text while Quark is loaded, its Critworks switch is on, Visual Stat Display is on, and Shift is up.
 * A failure to ask Quark or to draw turns the row off for the session with one warning; tooltips then
 * use the text (REQ-181). Public, because Forge's event wrapper can only reach public classes.
 */
@SideOnly(Side.CLIENT)
public final class QuarkStatRow {

    private static final ResourceLocation ICONS = new ResourceLocation(Tags.MOD_ID, "textures/gui/stat_icons.png");
    private static final int ICON_SIZE = 9;
    private static final int ATLAS_WIDTH = 18;
    private static final int WHITE = 0xFFFFFF;

    private final IntegrationGate gate;
    private final QuarkStatDisplayProbe probe;
    /** The row of the tooltip being shown; client thread only. */
    private ItemStack rowStack;
    private List<QuarkRowLayout.Entry> rowEntries = Collections.emptyList();

    QuarkStatRow(IntegrationGate gate) {
        this(gate, new QuarkStatDisplayProbe());
    }

    QuarkStatRow(IntegrationGate gate, QuarkStatDisplayProbe probe) {
        this.gate = gate;
        this.probe = probe;
    }

    /** Whether the row replaces the one-line text now. */
    boolean shows() {
        if (!gate.isOn(CritSettings.Integration.QUARK)) {
            return false;
        }
        try {
            return probe.isOn();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            gate.fail(CritSettings.Integration.QUARK, e);
            return false;
        }
    }

    /**
     * Adds the row's marker line for this tooltip; the icons are drawn after the text. False when building
     * the row failed: the integration is then off and the caller shows the text instead.
     */
    boolean add(List<String> tooltip, ItemStack stack, CritProfile profile) {
        try {
            List<QuarkRowLayout.Entry> entries = QuarkRowLayout.entries(profile);
            FontRenderer font = fontFor(stack);
            String line = QuarkTooltipLines.markerLine(QuarkRowLayout.rowWidth(entries, font::getStringWidth),
                    font.getCharWidth(' '));
            QuarkTooltipLines.insert(tooltip, line);
            rowStack = stack;
            rowEntries = entries;
            return true;
        } catch (RuntimeException | LinkageError e) {
            gate.fail(CritSettings.Integration.QUARK, e);
            return false;
        }
    }

    /** Called for every tooltip, so a tooltip without the row never draws the last one's icons. */
    void clear() {
        rowStack = null;
    }

    @SubscribeEvent
    public void onPostText(RenderTooltipEvent.PostText event) {
        if (rowStack == null || event.getStack() != rowStack) {
            return;
        }
        try {
            int line = QuarkTooltipLines.markerLineIndex(event.getLines());
            if (line > 0) {
                draw(event.getFontRenderer(), event.getX(), event.getY() + 10 * line + 1);
            }
        } catch (RuntimeException | LinkageError e) {
            rowStack = null;
            gate.fail(CritSettings.Integration.QUARK, e);
        }
    }

    private void draw(FontRenderer font, int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(0.0F, 0.0F, mc.getRenderItem().zLevel);
            for (QuarkRowLayout.Entry entry : rowEntries) {
                GlStateManager.color(1.0F, 1.0F, 1.0F);
                mc.getTextureManager().bindTexture(ICONS);
                Gui.drawModalRectWithCustomSizedTexture(x, y, entry.icon * ICON_SIZE, 0, ICON_SIZE, ICON_SIZE, ATLAS_WIDTH, ICON_SIZE);
                font.drawStringWithShadow(entry.text, x + QuarkRowLayout.TEXT_OFFSET, y + 1, WHITE);
                x += font.getStringWidth(entry.text) + QuarkRowLayout.ENTRY_GAP;
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    /** The font the tooltip is drawn with, so the reserved width matches the drawn row. */
    private static FontRenderer fontFor(ItemStack stack) {
        FontRenderer font = stack.getItem().getFontRenderer(stack);
        return font != null ? font : Minecraft.getMinecraft().fontRenderer;
    }
}
