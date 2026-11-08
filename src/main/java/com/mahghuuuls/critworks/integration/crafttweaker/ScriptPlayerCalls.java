package com.mahghuuuls.critworks.integration.crafttweaker;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.api.CritStats;
import com.mahghuuuls.critworks.api.CritworksApi;
import com.mahghuuuls.critworks.api.GuaranteeSpec;
import com.mahghuuuls.critworks.core.CritSettings.Integration;
import com.mahghuuuls.critworks.integration.IntegrationGate;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import crafttweaker.api.player.IPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import stanhebben.zenscript.annotations.Optional;
import stanhebben.zenscript.annotations.ZenExpansion;
import stanhebben.zenscript.annotations.ZenGetter;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.List;

/**
 * The player calls scripts use: {@code player.grantCritGuarantee("pack:focus", 200)},
 * {@code player.setCritBonus("pack:rage", "critworks.additionalCritChance", 10)}, {@code player.getCritChance(item)}
 * and the rest. Each one goes through the public Java API, so scripts get its rules: changes happen on the server
 * only, guarantees and bonuses behave as for Java addons. The script layer adds its own argument checks, so that
 * every mistake, including an empty id the API itself would accept, writes one error line with the script file and
 * line, and the call does nothing; the rest of the script runs on. CraftTweaker also shows that line in chat, so the
 * same mistake is reported once per game session. A failure that is not a script mistake is logged
 * with its stack trace. With the crafttweaker setting off, the calls that change a player do nothing, and the
 * reading calls still answer.
 */
@ZenRegister
@ZenExpansion("crafttweaker.player.IPlayer")
public final class ScriptPlayerCalls {

    private static final ScriptMistakes REPORTED = new ScriptMistakes();
    private static volatile IntegrationGate gate;

    private ScriptPlayerCalls() {
    }

    /** Called once Critworks has its settings, before any world exists. */
    static void bind(IntegrationGate integrationGate) {
        gate = integrationGate;
    }

    /** Makes the player's next eligible attack critical. Without ticks, or with 0, it has no time limit. */
    @ZenMethod
    public static void grantCritGuarantee(IPlayer player, String id, @Optional int ticks, @Optional boolean keptOnDeath) {
        try {
            GuaranteeSpec spec = ScriptArgs.guarantee(id, ticks, keptOnDeath);
            EntityPlayer entity = entity(player);
            if (changesAllowed("grantCritGuarantee")) {
                CritworksApi.grantGuarantee(entity, spec);
            }
        } catch (RuntimeException mistake) {
            report("grantCritGuarantee", mistake);
        }
    }

    /** True when a guarantee with this id was pending and is now removed. */
    @ZenMethod
    public static boolean removeCritGuarantee(IPlayer player, String id) {
        try {
            String checked = ScriptArgs.id(id, "the guarantee id");
            EntityPlayer entity = entity(player);
            return changesAllowed("removeCritGuarantee") && CritworksApi.removeGuarantee(entity, checked);
        } catch (RuntimeException mistake) {
            report("removeCritGuarantee", mistake);
            return false;
        }
    }

    /** The ids of the pending guarantees, in the order they will be used. */
    @ZenGetter("critGuarantees")
    public static String[] critGuarantees(IPlayer player) {
        try {
            List<GuaranteeSpec> pending = CritworksApi.getGuarantees(entity(player));
            String[] ids = new String[pending.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = pending.get(i).getId();
            }
            return ids;
        } catch (RuntimeException mistake) {
            report("critGuarantees", mistake);
            return new String[0];
        }
    }

    /** Sets a global crit bonus in the stat's own unit; the same source and stat again replaces it, 0 removes it. */
    @ZenMethod
    public static void setCritBonus(IPlayer player, String source, String stat, double amount) {
        try {
            String checkedSource = ScriptArgs.id(source, "the bonus source");
            CritStat checkedStat = ScriptArgs.stat(stat);
            double checkedAmount = ScriptArgs.amount(amount);
            EntityPlayer entity = entity(player);
            if (changesAllowed("setCritBonus")) {
                CritworksApi.setGlobalBonus(entity, checkedSource, checkedStat, checkedAmount);
            }
        } catch (RuntimeException mistake) {
            report("setCritBonus", mistake);
        }
    }

    @ZenMethod
    public static void removeCritBonus(IPlayer player, String source, String stat) {
        try {
            String checkedSource = ScriptArgs.id(source, "the bonus source");
            CritStat checkedStat = ScriptArgs.stat(stat);
            EntityPlayer entity = entity(player);
            if (changesAllowed("removeCritBonus")) {
                CritworksApi.removeGlobalBonus(entity, checkedSource, checkedStat);
            }
        } catch (RuntimeException mistake) {
            report("removeCritBonus", mistake);
        }
    }

    /** The player's next attack cannot crit, whatever its chance or guarantees. */
    @ZenMethod
    public static void blockNextCrit(IPlayer player) {
        try {
            EntityPlayer entity = entity(player);
            if (changesAllowed("blockNextCrit")) {
                CritworksApi.optOutNextAttack(entity);
            }
        } catch (RuntimeException mistake) {
            report("blockNextCrit", mistake);
        }
    }

    /** The final crit chance, in percent, an attack with this item in this hand would use now; 0 on a mistake. */
    @ZenMethod
    public static double getCritChance(IPlayer player, IItemStack item, @Optional String hand) {
        try {
            return resolve(player, item, hand).getFinalChance();
        } catch (RuntimeException mistake) {
            report("getCritChance", mistake);
            return 0.0D;
        }
    }

    /** The final crit damage multiplier an attack with this item in this hand would use now; 1 on a mistake. */
    @ZenMethod
    public static double getCritMultiplier(IPlayer player, IItemStack item, @Optional String hand) {
        try {
            return resolve(player, item, hand).getFinalMultiplier();
        } catch (RuntimeException mistake) {
            report("getCritMultiplier", mistake);
            return 1.0D;
        }
    }

    private static CritStats resolve(IPlayer player, IItemStack item, String hand) {
        EnumHand checkedHand = ScriptArgs.hand(hand);
        ItemStack stack = item == null ? ItemStack.EMPTY : CraftTweakerMC.getItemStack(item);
        return CritworksApi.resolve(entity(player), stack, checkedHand);
    }

    private static EntityPlayer entity(IPlayer player) {
        EntityPlayer entity = player == null ? null : CraftTweakerMC.getPlayer(player);
        if (entity == null) {
            throw new IllegalArgumentException("the player is null");
        }
        return entity;
    }

    private static boolean changesAllowed(String call) {
        IntegrationGate current = gate;
        if (current == null) {
            throw new IllegalStateException("Critworks is still loading; call " + call + " from an event during play");
        }
        return current.isOn(Integration.CRAFTTWEAKER);
    }

    private static void report(String call, RuntimeException failure) {
        String where = CraftTweakerAPI.getScriptFileAndLine();
        if (!REPORTED.firstTime(call, where, String.valueOf(failure.getMessage()))) {
            return;
        }
        String message = "Critworks " + call + ": " + failure.getMessage() + " (" + where + ")";
        if (failure instanceof IllegalArgumentException || failure instanceof IllegalStateException) {
            CraftTweakerAPI.logError(message);
        } else {
            CraftTweakerAPI.logError(message, failure);
        }
    }
}
