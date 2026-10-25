package com.mahghuuuls.critworks.command;

import com.mahghuuuls.critworks.bonus.BonusCollector;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.StatResolver;
import com.mahghuuuls.critworks.api.GuaranteeSpec;
import com.mahghuuuls.critworks.state.PlayerCritCapability;
import com.mahghuuuls.critworks.state.PlayerCritState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * {@code /critworks inspect [player]}: an operator command (level 2) that reports a player's resolved
 * critical stats for both hands. It only reads; it never changes state or uses a guarantee. The report
 * also goes to the server log, so it can be read without the chat window.
 */
public final class CritworksCommand extends CommandBase {

    private final StatResolver resolver;
    private final BonusCollector bonuses;
    private final CritSettings settings;
    private final Logger logger;

    public CritworksCommand(StatResolver resolver, BonusCollector bonuses, CritSettings settings, Logger logger) {
        this.resolver = resolver;
        this.bonuses = bonuses;
        this.settings = settings;
        this.logger = logger;
    }

    @Override
    public String getName() {
        return "critworks";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/critworks inspect [player]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1 || args.length > 2 || !"inspect".equalsIgnoreCase(args[0])) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityPlayerMP player = args.length == 2 ? getPlayer(server, sender, args[1]) : getCommandSenderAsPlayer(sender);
        PlayerCritState state = PlayerCritCapability.of(player);
        List<String> lines = InspectionReport.lines(player.getName(), settings.getDamageCap(),
                bonuses.collect(player, ItemStack.EMPTY, EnumHand.MAIN_HAND), view(player, EnumHand.MAIN_HAND),
                view(player, EnumHand.OFF_HAND),
                state == null ? Collections.<GuaranteeSpec>emptyList() : state.list());
        for (String line : lines) {
            sender.sendMessage(new TextComponentString(line));
            logger.info("[inspect] {}", line);
        }
    }

    private InspectionReport.HandView view(EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        String name = stack.isEmpty() ? "empty" : stack.getDisplayName() + " (" + stack.getItem().getRegistryName()
                + (stack.getHasSubtypes() ? " meta " + stack.getMetadata() : "") + ")";
        return new InspectionReport.HandView(name, resolver.resolve(player, stack, hand));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "inspect");
        }
        if (args.length == 2 && "inspect".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }
}
