package com.mahghuuuls.critworks.state;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.function.Consumer;

/**
 * The {@code critworks:player} capability that holds {@link PlayerCritState} on every player, its copy to
 * the new player object after death or leaving the End, and the online countdown of timed guarantees.
 */
public final class PlayerCritCapability {

    @CapabilityInject(PlayerCritState.class)
    private static Capability<PlayerCritState> capability = null;

    private static final ResourceLocation KEY = new ResourceLocation("critworks", "player");

    private static Consumer<String> warnOnce = message -> { };

    private PlayerCritCapability() {
    }

    /** Registers the capability and its handlers. Call once in preInit. */
    public static void register(final Consumer<String> warn) {
        warnOnce = new Consumer<String>() {
            private boolean warned;

            @Override
            public void accept(String message) {
                if (!warned) {
                    warned = true;
                    warn.accept(message);
                }
            }
        };
        CapabilityManager.INSTANCE.register(PlayerCritState.class, new Capability.IStorage<PlayerCritState>() {
            @Override
            public NBTBase writeNBT(Capability<PlayerCritState> cap, PlayerCritState instance, EnumFacing side) {
                return instance.write();
            }

            @Override
            public void readNBT(Capability<PlayerCritState> cap, PlayerCritState instance, EnumFacing side, NBTBase nbt) {
                if (nbt instanceof NBTTagCompound) {
                    instance.read((NBTTagCompound) nbt, warnOnce);
                }
            }
        }, PlayerCritState::new);
        MinecraftForge.EVENT_BUS.register(new PlayerCritCapability.Handlers());
    }

    /** The player's state, or null when the capability is not registered. */
    public static PlayerCritState of(EntityPlayer player) {
        return capability == null ? null : player.getCapability(capability, null);
    }

    /** Attach, copy on clone, and count down. */
    public static final class Handlers {
        @SubscribeEvent
        public void onAttach(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof EntityPlayer) {
                event.addCapability(KEY, new Provider());
            }
        }

        /** A new player object replaces the old one after death and after leaving the End. */
        @SubscribeEvent
        public void onClone(PlayerEvent.Clone event) {
            PlayerCritState old = of(event.getOriginal());
            PlayerCritState current = of(event.getEntityPlayer());
            if (old != null && current != null) {
                current.copyFrom(old, event.isWasDeath());
            }
        }

        /** Only online players tick, so a timed guarantee pauses while its player is away. */
        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) {
                return;
            }
            PlayerCritState state = of(event.player);
            if (state != null && state.hasTimed()) {
                state.tick();
            }
        }
    }

    private static final class Provider implements ICapabilitySerializable<NBTTagCompound> {
        private final PlayerCritState state = new PlayerCritState();

        @Override
        public boolean hasCapability(Capability<?> cap, EnumFacing facing) {
            return cap == capability;
        }

        @Override
        public <T> T getCapability(Capability<T> cap, EnumFacing facing) {
            return cap == capability ? capability.cast(state) : null;
        }

        @Override
        public NBTTagCompound serializeNBT() {
            return state.write();
        }

        @Override
        public void deserializeNBT(NBTTagCompound nbt) {
            state.read(nbt, warnOnce);
        }
    }
}
