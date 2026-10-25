package com.mahghuuuls.critworks.core;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Owns who may crit: players always; any other attacker only when an addon's policy allows it. A policy
 * that throws is reported once and then counts as "no".
 */
public final class AttackerPolicies {

    private final List<Policy> policies = new CopyOnWriteArrayList<Policy>();
    private final Consumer<String> warn;

    public AttackerPolicies(Consumer<String> warn) {
        this.warn = warn;
    }

    public void register(Predicate<EntityLivingBase> policy, String source) {
        policies.add(new Policy(policy, source));
    }

    public boolean isEmpty() {
        return policies.isEmpty();
    }

    public boolean allows(EntityLivingBase attacker) {
        if (attacker instanceof EntityPlayer) {
            return true;
        }
        for (Policy policy : policies) {
            if (policy.allows(attacker)) {
                return true;
            }
        }
        return false;
    }

    private final class Policy {
        private final Predicate<EntityLivingBase> predicate;
        private final String source;
        private volatile boolean broken;

        Policy(Predicate<EntityLivingBase> predicate, String source) {
            this.predicate = predicate;
            this.source = source;
        }

        boolean allows(EntityLivingBase attacker) {
            if (broken) {
                return false;
            }
            try {
                return predicate.test(attacker);
            } catch (RuntimeException | LinkageError e) {
                broken = true;
                warn.accept("Critworks: an attacker policy from " + source + " failed and is skipped: " + e);
                return false;
            }
        }
    }
}
