package com.mahghuuuls.critworks.devtest;

import com.mahghuuuls.critworks.api.CritAttackKind;
import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.api.CritStats;
import com.mahghuuuls.critworks.api.CritworksApi;
import com.mahghuuuls.critworks.api.IAttackIntegration;
import com.mahghuuuls.critworks.api.WeaponBonus;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EnumHand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Uses each loading-time part of the public API as an addon would: an item default for the stone sword
 * (chance 20, damage x2.00), a +20 increased chance bonus for any sword named "Bonus Blade", an attacker
 * policy that lets zombies crit when switched on, and an attack integration that strikes the nearest mob.
 */
final class DevTestApiUse {

    static final String BONUS_BLADE = "Bonus Blade";
    static final String BONUS_SOURCE = "critworks_devtest:bonus_blade";
    static final float STRIKE_DAMAGE = 4.0F;

    private static final Logger LOGGER = LogManager.getLogger("Critworks Dev Test");

    static volatile boolean zombiesMayCrit;

    static final IAttackIntegration STRIKE = new IAttackIntegration() {
        @Override
        public String getId() {
            return "critworks_devtest:strike";
        }
    };

    private DevTestApiUse() {
    }

    static void register() {
        CritworksApi.registerItemDefaults(Items.STONE_SWORD, 20.0D, 2.0D);
        CritworksApi.registerWeaponBonusProvider((player, stack) -> isBonusBlade(stack)
                ? Collections.singletonList(new WeaponBonus(CritStat.INCREASED_CHANCE, 20.0D, BONUS_SOURCE))
                : Collections.<WeaponBonus>emptyList());
        CritworksApi.registerAttackerPolicy(attacker -> zombiesMayCrit && attacker instanceof EntityZombie);
        CritworksApi.registerAttackIntegration(STRIKE);
    }

    private static boolean isBonusBlade(ItemStack stack) {
        return stack.getItem() instanceof ItemSword && stack.hasDisplayName() && BONUS_BLADE.equals(stack.getDisplayName());
    }

    /** One integration attack: the main-hand stack at full charge strikes the nearest living entity within 5 blocks. */
    static String strike(EntityPlayerMP player) {
        EntityLivingBase target = null;
        double best = 25.0D;
        List<EntityLivingBase> near = player.world.getEntitiesWithinAABB(EntityLivingBase.class,
                player.getEntityBoundingBox().grow(5.0D));
        for (EntityLivingBase living : near) {
            double distance = living.getDistanceSq(player);
            if (living != player && !(living instanceof EntityPlayer) && !(living instanceof EntityArmorStand)
                    && distance < best) {
                best = distance;
                target = living;
            }
        }
        if (target == null) {
            return "devtest strike found no target within 5 blocks";
        }
        CritworksApi.beginAttack(player, player.getHeldItemMainhand(), EnumHand.MAIN_HAND, CritAttackKind.MELEE, 1.0F);
        target.hurtResistantTime = 0;
        float before = target.getHealth() + target.getAbsorptionAmount();
        target.attackEntityFrom(new EntityDamageSource("critworks_devtest.strike", player), STRIKE_DAMAGE);
        float after = target.getHealth() + target.getAbsorptionAmount();
        CritworksApi.endAttack(player);
        return String.format(Locale.ROOT, "devtest strike %s#%d took %.2f", target.getName(), target.getEntityId(),
                before - after);
    }

    /** The main hand's stats as the public API reports them. */
    static String resolve(EntityPlayerMP player) {
        CritStats stats = CritworksApi.resolve(player, player.getHeldItemMainhand(), EnumHand.MAIN_HAND);
        StringBuilder bonuses = new StringBuilder();
        for (WeaponBonus bonus : stats.getBonuses()) {
            bonuses.append(bonuses.length() == 0 ? "" : ", ").append(bonus.getStat()).append(' ')
                    .append(String.format(Locale.ROOT, "%+.2f", bonus.getAmount())).append(" from ").append(bonus.getSource());
        }
        return String.format(Locale.ROOT,
                "devtest resolve eligible=%s (%s) chance=%.2f from %s damage=x%.2f from %s final=%.2f%% x%.2f bonuses=[%s]",
                stats.isEligible(), stats.getEligibility(), stats.getBaseChance(), stats.getBaseChanceSource(),
                stats.getBaseDamage(), stats.getBaseDamageSource(), stats.getFinalChance(), stats.getFinalMultiplier(),
                bonuses);
    }

    static void log(String text) {
        LOGGER.info("[devtest] {}", text);
    }
}
