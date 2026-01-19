package fr.renblood.medievalcoins.events;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.tree.*;
import fr.renblood.medievalcoins.tree.capability.SpecialSlotCapabilityHandler;
import fr.renblood.medievalcoins.tree.fertilize.FertilizeCommand;
import fr.renblood.medievalcoins.tree.network.FertilizerSlotMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID)
public class PlayerConnectionHandler {

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();

        // --- Nettoyage Mode Torche ---
        if (TorchCommand.activePlayers.contains(id)) {
            TorchCommand.activePlayers.remove(id);
            // On vide le slot spécial (côté serveur, la synchro client n'est pas nécessaire car il se déco)
            player.getCapability(SpecialSlotCapabilityHandler.SPECIAL_SLOT_CAP).ifPresent(inv -> {
                inv.setStackInSlot(0, ItemStack.EMPTY);
            });
        }

        // --- Nettoyage Mode Fertilize ---
        if (FertilizeCommand.fertilizingPlayers.contains(id)) {
            FertilizeCommand.fertilizingPlayers.remove(id);
            player.getCapability(SpecialSlotCapabilityHandler.SPECIAL_SLOT_CAP).ifPresent(inv -> {
                inv.setStackInSlot(0, ItemStack.EMPTY);
            });
        }

        // --- Nettoyage Mode Magnet ---
        // Il faut rendre le Set accessible dans MagnetCommand (actuellement private)
        // Je vais devoir modifier MagnetCommand pour exposer activePlayers ou ajouter une méthode remove
        // Pour l'instant, supposons qu'on va le rendre public ou ajouter une méthode statique
        MagnetCommand.removePlayer(id);

        // --- Nettoyage Mode Jump Boost ---
        if (JumpBoostCommand.removePlayer(id)) {
            player.removeEffect(MobEffects.JUMP);
        }

        // --- Nettoyage Mode NoFall ---
        NoFallCommand.removePlayer(id);

        // --- Nettoyage Mode Vanish ---
        if (VanishCommand.removePlayer(id)) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
    }
}
