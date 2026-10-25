package com.mahghuuuls.critworks.devtest;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.api.CritworksApi;
import com.mahghuuuls.critworks.api.GuaranteeSpec;
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
 * additional_damage. {@code /cwtest react off|damage|chain|swing}: how the test listener reacts to each
 * critical-hit event. {@code /cwtest guarantee ...}: guarantees on the calling player. {@code /cwtest zombies
 * on|off}, {@code optout}, {@code strike}, {@code resolve}: the other API parts (see {@link DevTestApiUse}).
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
        return "/cwtest bonus <stat> <amount> [source] | /cwtest bonus clear | /cwtest react off|damage|chain|swing | /cwtest guarantee <id> [seconds] [keep] | /cwtest guarantee remove <id> | /cwtest guarantee clear | /cwtest zombies on|off | /cwtest optout | /cwtest strike | /cwtest resolve";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 2 && "react".equals(args[0])) {
            try {
                DevTestEvents.reaction = DevTestEvents.Reaction.valueOf(args[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new WrongUsageException(getUsage(sender));
            }
            report(sender, "devtest reaction " + DevTestEvents.reaction.name().toLowerCase(Locale.ROOT));
            return;
        }
        if (args.length == 2 && "zombies".equals(args[0]) && ("on".equals(args[1]) || "off".equals(args[1]))) {
            DevTestApiUse.zombiesMayCrit = "on".equals(args[1]);
            report(sender, "devtest zombies may crit: " + DevTestApiUse.zombiesMayCrit);
            return;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (args.length == 1 && "optout".equals(args[0])) {
            CritworksApi.optOutNextAttack(player);
            report(sender, "devtest next attack of " + player.getName() + " opted out");
            return;
        }
        if (args.length == 1 && "strike".equals(args[0])) {
            report(sender, DevTestApiUse.strike(player));
            return;
        }
        if (args.length == 1 && "resolve".equals(args[0])) {
            report(sender, DevTestApiUse.resolve(player));
            return;
        }
        if (args.length >= 2 && "guarantee".equals(args[0])) {
            guarantee(sender, player, args);
            return;
        }
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

    /**
     * {@code guarantee <id> [seconds] [keep]}, {@code guarantee remove <id>}, {@code guarantee clear}: grants,
     * removes, or clears the caller's guarantees through the public API, then lists what is pending.
     */
    private void guarantee(ICommandSender sender, EntityPlayerMP player, String[] args) throws CommandException {
        if (args.length == 2 && "clear".equals(args[1])) {
            for (GuaranteeSpec spec : CritworksApi.getGuarantees(player)) {
                CritworksApi.removeGuarantee(player, spec.getId());
            }
        } else if ("remove".equals(args[1])) {
            if (args.length != 3) {
                throw new WrongUsageException(getUsage(sender));
            }
            CritworksApi.removeGuarantee(player, args[2]);
        } else if (args.length <= 4) {
            GuaranteeSpec spec = GuaranteeSpec.of(args[1]);
            int next = 2;
            if (args.length > next && !"keep".equals(args[next])) {
                spec = spec.lasting(parseInt(args[next], 1, 86400) * 20);
                next++;
            }
            if (args.length > next) {
                if (!"keep".equals(args[next]) || args.length > next + 1) {
                    throw new WrongUsageException(getUsage(sender));
                }
                spec = spec.keptOnDeath();
            }
            CritworksApi.grantGuarantee(player, spec);
        } else {
            throw new WrongUsageException(getUsage(sender));
        }
        StringBuilder pending = new StringBuilder();
        for (GuaranteeSpec spec : CritworksApi.getGuarantees(player)) {
            pending.append(pending.length() == 0 ? "" : ", ").append(spec.getId())
                    .append(spec.hasDuration() ? " " + spec.getDurationTicks() + " ticks" : " no time limit")
                    .append(spec.isKeptOnDeath() ? " keep" : "");
        }
        report(sender, "devtest guarantees of " + player.getName() + ": " + (pending.length() == 0 ? "none" : pending));
    }

    private static void report(ICommandSender sender, String text) {
        sender.sendMessage(new TextComponentString(text));
        LOGGER.info("[devtest] {}", text);
    }
}
