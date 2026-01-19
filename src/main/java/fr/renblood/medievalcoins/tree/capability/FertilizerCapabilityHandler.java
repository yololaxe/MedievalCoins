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
 * Capability qui ajoute 1 slot fertilizer persistant à chaque joueur.
 */
@Mod.EventBusSubscriber
public class FertilizerCapabilityHandler {

    public static final Capability<FertilizerInventory> FERTILIZER_CAP =
            CapabilityManager.get(new CapabilityToken<>() {});

    private static final ResourceLocation ID = new ResourceLocation("medieval_coins", "fertilizer");

    @SubscribeEvent
    public static void attachCapability(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(ID, new Provider());
            // On évite d'appeler getName() ici car le joueur n'est pas encore totalement initialisé
            // ce qui cause le NullPointerException
            if (MedievalCoin.DEBUG_MODE) {
                MedievalCoin.LOGGER.info("Attached Fertilizer Capability to player entity.");
            }
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        // On doit copier les données même si ce n'est pas une mort (ex: retour du End)
        // Mais généralement on veut persister après la mort
        event.getOriginal().getCapability(FERTILIZER_CAP).ifPresent(oldInv ->
                event.getEntity().getCapability(FERTILIZER_CAP).ifPresent(newInv ->
                        newInv.deserializeNBT(oldInv.serializeNBT())));
    }

    public static class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
        private final FertilizerInventory instance = new FertilizerInventory();
        private final LazyOptional<FertilizerInventory> holder = LazyOptional.of(() -> instance);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
            return cap == FERTILIZER_CAP ? holder.cast() : LazyOptional.empty();
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
