package fr.renblood.medievalcoins.tree.capability;

import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * Capability qui ajoute 1 slot spécial persistant à chaque joueur (Fertilizer ou Torch).
 */
@Mod.EventBusSubscriber
public class SpecialSlotCapabilityHandler {

    public static final Capability<SpecialSlotInventory> SPECIAL_SLOT_CAP =
            CapabilityManager.get(new CapabilityToken<>() {});

    private static final ResourceLocation ID = new ResourceLocation("medieval_coins", "special_slot");

    @SubscribeEvent
    public static void attachCapability(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(ID, new Provider());
            if (MedievalCoin.DEBUG_MODE) {
                MedievalCoin.LOGGER.info("Attached Special Slot Capability to player entity.");
            }
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        event.getOriginal().getCapability(SPECIAL_SLOT_CAP).ifPresent(oldInv ->
                event.getEntity().getCapability(SPECIAL_SLOT_CAP).ifPresent(newInv ->
                        newInv.deserializeNBT(oldInv.serializeNBT())));
    }

    public static class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
        private final SpecialSlotInventory instance = new SpecialSlotInventory();
        private final LazyOptional<SpecialSlotInventory> holder = LazyOptional.of(() -> instance);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
            return cap == SPECIAL_SLOT_CAP ? holder.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return instance.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            instance.deserializeNBT(nbt);
        }
    }
}
