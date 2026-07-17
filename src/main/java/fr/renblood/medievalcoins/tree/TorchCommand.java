package fr.renblood.medievalcoins.tree;

import com.mojang.brigadier.CommandDispatcher;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.init.BlockInit;
import fr.renblood.medievalcoins.tree.capability.SpecialSlotCapabilityHandler;
import fr.renblood.medievalcoins.tree.fertilize.FertilizeCommand;
import fr.renblood.medievalcoins.tree.network.FertilizeStateMessage;
import fr.renblood.medievalcoins.tree.network.FertilizerSlotMessage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber
public class TorchCommand {

    public static final Set<UUID> activePlayers = new HashSet<>();

    public static boolean isActive(UUID id) {
        return activePlayers.contains(id);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        d.register(Commands.literal("mc").then(Commands.literal("ability").then(Commands.literal("torch")
                .requires(src -> src.hasPermission(0))
                .executes(c -> {
                    CommandSourceStack src = c.getSource();
                    if (!(src.getEntity() instanceof ServerPlayer player)) {
                        src.sendFailure(Component.literal("❌ Cette commande doit être exécutée en jeu."));
                        return 0;
                    }

                    if (!TreePermissionChecker.hasUnlocked(player, TreeAbility.TORCH)) {
                        src.sendFailure(Component.literal("❌ Vous devez améliorer votre métier de mineur."));
                        return 0;
                    }

                    UUID id = player.getUUID();
                    boolean isActive;
                    if (activePlayers.contains(id)) {
                        activePlayers.remove(id);
                        clearTorchSlot(player);
                        isActive = false;
                        src.sendSuccess(() -> Component.literal("❌ Mode Torche désactivé."), true);
                    } else {
                        // Désactive le mode Fertilize si actif
                        if (FertilizeCommand.fertilizingPlayers.contains(id)) {
                            FertilizeCommand.fertilizingPlayers.remove(id);
                            src.sendSuccess(() -> Component.literal("⚠️ Mode Fertilisation désactivé automatiquement."), false);
                        }
                        
                        activePlayers.add(id);
                        isActive = true;
                        src.sendSuccess(() -> Component.literal("✅ Mode Torche activé."), true);
                        giveInfiniteTorch(player);
                    }
                    // On réutilise le message d'état du HUD (qui sert pour le slot spécial en général)
                    MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new FertilizeStateMessage(isActive));
                    return 1;
                }))));
    }

    private static void giveInfiniteTorch(ServerPlayer player) {
        player.getCapability(SpecialSlotCapabilityHandler.SPECIAL_SLOT_CAP).ifPresent(inv -> {
            // On donne une torche magique (item)
            inv.setStackInSlot(0, new ItemStack(BlockInit.MAGIC_TORCH.get().asItem(), 1));
            // Synchro client
            MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new FertilizerSlotMessage(inv.getStackInSlot(0)));
        });
    }

    private static void clearTorchSlot(ServerPlayer player) {
        player.getCapability(SpecialSlotCapabilityHandler.SPECIAL_SLOT_CAP).ifPresent(inv -> {
            inv.setStackInSlot(0, ItemStack.EMPTY);
            MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new FertilizerSlotMessage(ItemStack.EMPTY));
        });
    }
}
