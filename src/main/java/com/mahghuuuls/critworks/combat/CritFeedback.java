package com.mahghuuuls.critworks.combat;

import com.mahghuuuls.critworks.core.CritSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.play.server.SPacketAnimation;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.WorldServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.logging.log4j.Logger;

/**
 * Owns crit feedback: which sound a critical attack plays (chosen once at load) and the vanilla crit
 * particles on each target. Both reach nearby players through vanilla packets.
 */
public final class CritFeedback {

    private static final ResourceLocation RLCOMBAT_CRIT = new ResourceLocation("bettercombatmod", "player.criticalstrike");

    private final SoundEvent sound;

    private CritFeedback(SoundEvent sound) {
        this.sound = sound;
    }

    /**
     * Chooses the crit sound from the setting. Call after all mods registered their sounds.
     * An unknown id is reported once and the default is used.
     */
    public static CritFeedback choose(CritSettings settings, boolean rlcombatLoaded, Logger logger) {
        String setting = settings.getCritSound();
        if (CritSettings.SOUND_NONE.equalsIgnoreCase(setting)) {
            return new CritFeedback(null);
        }
        if (!CritSettings.SOUND_AUTO.equalsIgnoreCase(setting)) {
            SoundEvent configured = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(setting));
            if (configured != null) {
                return new CritFeedback(configured);
            }
            logger.warn("Critworks config critworks.cfg: critSound = \"{}\" is not a known sound; using the default",
                    setting);
        }
        return new CritFeedback(defaultSound(rlcombatLoaded));
    }

    private static SoundEvent defaultSound(boolean rlcombatLoaded) {
        if (rlcombatLoaded) {
            SoundEvent rlcombat = ForgeRegistries.SOUND_EVENTS.getValue(RLCOMBAT_CRIT);
            if (rlcombat != null) {
                return rlcombat;
            }
        }
        return SoundEvents.ENTITY_PLAYER_ATTACK_CRIT;
    }

    void sound(EntityPlayer attacker) {
        if (sound != null) {
            attacker.world.playSound(null, attacker.posX, attacker.posY, attacker.posZ, sound,
                    attacker.getSoundCategory(), 1.0F, 1.0F);
        }
    }

    void particles(EntityPlayer attacker, Entity target) {
        attacker.onCriticalHit(target);
    }

    /** An arrow's crit sound plays at the target it hit, since the shooter may be far away or gone. */
    void soundAt(Entity target) {
        if (sound != null) {
            target.world.playSound(null, target.posX, target.posY, target.posZ, sound, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
    }

    /** The vanilla crit particles on a target hit by an arrow, sent to every player who sees the target. */
    void particlesOn(Entity target) {
        if (target.world instanceof WorldServer) {
            ((WorldServer) target.world).getEntityTracker().sendToTrackingAndSelf(target, new SPacketAnimation(target, 4));
        }
    }

    /** The chosen sound id for the diagnostic log, or "none". */
    public String describeSound() {
        return sound == null ? CritSettings.SOUND_NONE : String.valueOf(sound.getRegistryName());
    }
}
