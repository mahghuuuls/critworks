package com.mahghuuuls.critworks.state;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityArrow;
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
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.function.Consumer;

/** The {@code critworks:arrow} capability that holds {@link ArrowCritState} on every arrow entity. */
public final class ArrowCritCapability {

    @CapabilityInject(ArrowCritState.class)
    private static Capability<ArrowCritState> capability = null;

    private static final ResourceLocation KEY = new ResourceLocation("critworks", "arrow");

    private static Consumer<String> warnOnce = message -> { };

    private ArrowCritCapability() {
    }

    /** Registers the capability and attaches it to arrows. Call once in preInit. */
    public static void register(final Consumer<String> warn) {
        // One warning per session for unreadable arrow data, however many arrows carry it.
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
        CapabilityManager.INSTANCE.register(ArrowCritState.class, new Capability.IStorage<ArrowCritState>() {
            @Override
            public NBTBase writeNBT(Capability<ArrowCritState> cap, ArrowCritState instance, EnumFacing side) {
                return instance.write();
            }

            @Override
            public void readNBT(Capability<ArrowCritState> cap, ArrowCritState instance, EnumFacing side, NBTBase nbt) {
                if (nbt instanceof NBTTagCompound) {
                    instance.read((NBTTagCompound) nbt, warnOnce);
                }
            }
        }, ArrowCritState::new);
        MinecraftForge.EVENT_BUS.register(new ArrowCritCapability.Attacher());
    }

    /** The arrow's state, or null when the capability is not registered. */
    public static ArrowCritState of(EntityArrow arrow) {
        return capability == null ? null : arrow.getCapability(capability, null);
    }

    /** Attaches an empty state to each new arrow. */
    public static final class Attacher {
        @SubscribeEvent
        public void onAttach(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof EntityArrow) {
                event.addCapability(KEY, new Provider());
            }
        }
    }

    private static final class Provider implements ICapabilitySerializable<NBTTagCompound> {
        private final ArrowCritState state = new ArrowCritState();

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
