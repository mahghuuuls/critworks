package com.mahghuuuls.critworks.client;

import com.mahghuuuls.critworks.core.CritProfile;
import com.mahghuuuls.critworks.integration.quark.QuarkTooltipLines;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Adds the Critworks lines to item tooltips, last (LOWEST): the Quark icon row when it shows and Shift is
 * up, the text otherwise, never both (REQ-084, REQ-085). A weapon not in hand gets the preview an attack
 * with it would use now (REQ-094). Resolving never changes game state, so hovering uses no guarantee
 * (REQ-095). In diagnostic mode, a change of the hovered item or of the mode writes one log line.
 */
@SideOnly(Side.CLIENT)
public final class TooltipPresenter {

    private final ClientStats stats;
    private final QuarkStatRow quarkRow;
    private final Logger diagnostics;
    private String lastLogged;

    /** @param diagnostics the log for the tooltip line, or null when diagnostic mode is off */
    TooltipPresenter(ClientStats stats, QuarkStatRow quarkRow, Logger diagnostics) {
        this.stats = stats;
        this.quarkRow = quarkRow;
        this.diagnostics = diagnostics;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTooltip(ItemTooltipEvent event) {
        quarkRow.clear();
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        EnumHand hand = player != null && player.getHeldItemOffhand() == stack ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
        CritProfile profile = stats.resolver().resolve(player, stack, hand);
        if (!profile.isEligible()) {
            return;
        }
        boolean shift = GuiScreen.isShiftKeyDown();
        List<String> tooltip = event.getToolTip();
        boolean row = !shift && quarkRow.shows() && quarkRow.add(tooltip, stack, profile);
        if (!row) {
            tooltip.addAll(TooltipLines.lines(profile, shift, I18n::format));
        }
        log(stack, row ? "row" : shift ? "shift" : "text", tooltip);
    }

    private void log(ItemStack stack, String mode, List<String> tooltip) {
        if (diagnostics == null) {
            return;
        }
        String item = stack.getItem().getRegistryName() + "@" + stack.getMetadata();
        String key = item + " " + mode;
        if (!key.equals(lastLogged)) {
            lastLogged = key;
            diagnostics.info("[diag] tooltip {} mode={} lines={} rowLine={}", item, mode, tooltip.size(),
                    QuarkTooltipLines.markerLineIndex(tooltip));
        }
    }
}
