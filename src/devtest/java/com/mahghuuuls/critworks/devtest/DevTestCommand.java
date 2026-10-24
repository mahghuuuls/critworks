package com.mahghuuuls.critworks.devtest;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.api.CritworksApi;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;

/**
 * {@code /cwtest bonus <stat> <amount> [source]} and {@code /cwtest bonus clear}: test bonuses on the
 * calling player through the public API. Stats: increased_chance, additional_chance, increased_damage,
 * additional_damage.
 */
final class DevTestCommand extends CommandBase {

    private static final Logger LOGGER = LogManager.getLogger("Critworks Dev Test");
    private static final String DEFAULT_SOURCE = "critworks_devtest:test";
    private static final String[] SOURCES = {DEFAULT_SOURCE, "critworks_devtest:second"};

    @Override
    public String getName() {
        return "cwtest";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/cwtest bonus <stat> <amount> [source] | /cwtest bonus clear";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (args.length == 2 && "bonus".equals(args[0]) && "clear".equals(args[1])) {
            for (String source : SOURCES) {
                for (CritStat stat : CritStat.values()) {
                    CritworksApi.removeGlobalBonus(player, source, stat);
                }
            }
            report(sender, "devtest bonuses cleared for " + player.getName());
            return;
        }
        if ((args.length == 3 || args.length == 4) && "bonus".equals(args[0])) {
            CritStat stat;
            try {
                stat = CritStat.valueOf(args[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new WrongUsageException(getUsage(sender));
            }
            double amount = parseDouble(args[2]);
            String source = args.length == 4 ? "critworks_devtest:" + args[3] : DEFAULT_SOURCE;
            CritworksApi.setGlobalBonus(player, source, stat, amount);
            report(sender, "devtest bonus " + stat + " " + amount + " from " + source + " on " + player.getName());
            return;
        }
        throw new WrongUsageException(getUsage(sender));
    }

    private static void report(ICommandSender sender, String text) {
        sender.sendMessage(new TextComponentString(text));
        LOGGER.info("[devtest] {}", text);
    }
}
