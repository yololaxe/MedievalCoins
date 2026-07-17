package fr.renblood.medievalcoins.tree.fertilize;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.tree.TorchCommand;
import fr.renblood.medievalcoins.tree.TreeAbility;
import fr.renblood.medievalcoins.tree.TreePermissionChecker;
import fr.renblood.medievalcoins.tree.capability.SpecialSlotCapabilityHandler;
import fr.renblood.medievalcoins.tree.capability.SpecialSlotInventory;
import fr.renblood.medievalcoins.tree.network.FertilizeStateMessage;
import fr.renblood.medievalcoins.tree.network.FertilizerSlotMessage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber
public class FertilizeCommand {

    public static final Set<UUID> fertilizingPlayers = new HashSet<>();
    private static int intervalTicks = 20 * 30; // 30s par défaut
    private static final int MAX_FERTILIZER = 16;
    
    private static final Map<UUID, Long> lastCommandTime = new HashMap<>();
    private static final long COOLDOWN_MS = 3000; // 3 secondes

    public static boolean fertilizeActiveHUD = false; // affichage côté client

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        // Commande principale /fertilize
        d.register(Commands.literal("mc").then(Commands.literal("ability").then(Commands.literal("fertilize")
                .requires(src -> src.hasPermission(0))
                .executes(c -> {
                    if (isOnCooldown(c.getSource())) return 0;
                    CommandSourceStack src = c.getSource();
                    if (!(src.getEntity() instanceof ServerPlayer player)) {
                        src.sendFailure(Component.literal("❌ Cette commande doit être exécutée en jeu."));
                        return 0;
                    }

                    // Vérifie les permissions métier
                    if (!TreePermissionChecker.hasUnlocked(player, TreeAbility.FERTILIZE)) {
                        src.sendFailure(Component.literal("❌ Vous devez améliorer votre métier de bûcheron."));
                        return 0;
                    }

                    UUID id = player.getUUID();
                    boolean isActive;
                    if (fertilizingPlayers.contains(id)) {
                        fertilizingPlayers.remove(id);
                        clearFertilizerSlot(player);
                        isActive = false;
                        src.sendSuccess(() -> Component.literal("❌ Mode fertilisation désactivé."), true);
                    } else {
                        // Désactive le mode Torche si actif
                        if (TorchCommand.activePlayers.contains(id)) {
                            TorchCommand.activePlayers.remove(id);
                            src.sendSuccess(() -> Component.literal("⚠️ Mode Torche désactivé automatiquement."), false);
                        }

                        fertilizingPlayers.add(id);
                        isActive = true;
                        src.sendSuccess(() -> Component.literal("✅ Mode fertilisation activé."), true);
                        // Donne immédiatement un fertilizer pour commencer
                        giveFertilizer(player);
                    }
                    // Envoie l'état au client
                    MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new FertilizeStateMessage(isActive));
                    return 1;
                }))));

        // Commande admin pour configurer le délai : /fertilize_config set_time <seconds>
        d.register(Commands.literal("mc").then(Commands.literal("admin").then(Commands.literal("config")
                .then(Commands.literal("ability").then(Commands.literal("fertilize")
                .requires(src -> src.hasPermission(2)) // Permission admin (OP niveau 2+)
                .then(Commands.literal("set-time")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                .executes(c -> {
                                    if (isOnCooldown(c.getSource())) return 0;
                                    int seconds = IntegerArgumentType.getInteger(c, "seconds");
                                    intervalTicks = seconds * 20;
                                    c.getSource().sendSuccess(() -> Component.literal("✅ Délai de fertilisation défini à " + seconds + " secondes."), true);
                                    return 1;
                                })
                        )
                )
        )))));
    }

    // Tick serveur : ajoute 1 bone meal toutes les X secondes
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerPlayer player : playerListCopy()) {
            if (!fertilizingPlayers.contains(player.getUUID())) continue;

            if (player.tickCount % intervalTicks == 0) {
                giveFertilizer(player);
            }
        }
    }

    private static Iterable<ServerPlayer> playerListCopy() {
        return Set.copyOf(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
                .getPlayerList().getPlayers());
    }

    /** Donne 1 bone meal vanilla dans le slot spécial */
    private static void giveFertilizer(ServerPlayer player) {
        LazyOptional<SpecialSlotInventory> cap = player.getCapability(SpecialSlotCapabilityHandler.SPECIAL_SLOT_CAP);
        if (cap.isPresent()) {
            cap.ifPresent(inv -> {
                ItemStack current = inv.getStackInSlot(0);
                // Si le slot est vide ou contient déjà de la bone meal
                if (current.isEmpty() || current.getItem() == Items.BONE_MEAL) {
                    if (current.isEmpty()) {
                        inv.setStackInSlot(0, new ItemStack(Items.BONE_MEAL, 1));
                    } else if (current.getCount() < MAX_FERTILIZER) {
                        current.grow(1);
                        inv.setStackInSlot(0, current);
                    }
                    // Synchronise avec le client
                    MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new FertilizerSlotMessage(inv.getStackInSlot(0)));
                    
                    if (MedievalCoin.DEBUG_MODE) {
                        MedievalCoin.LOGGER.info("Gave fertilizer to " + player.getName().getString() + ". New count: " + inv.getStackInSlot(0).getCount());
                    }
                } else {
                    // Si le slot contient autre chose (ex: torche), on écrase pour le mode Fertilize
                    inv.setStackInSlot(0, new ItemStack(Items.BONE_MEAL, 1));
                    MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new FertilizerSlotMessage(inv.getStackInSlot(0)));
                }
            });
        } else {
            if (MedievalCoin.DEBUG_MODE) {
                MedievalCoin.LOGGER.error("Failed to give fertilizer: Capability not present on player " + player.getName().getString());
            }
        }
    }

    /** Nettoie le slot spécial à la désactivation */
    private static void clearFertilizerSlot(ServerPlayer player) {
        player.getCapability(SpecialSlotCapabilityHandler.SPECIAL_SLOT_CAP).ifPresent(inv -> {
            inv.setStackInSlot(0, ItemStack.EMPTY);
            MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new FertilizerSlotMessage(ItemStack.EMPTY));
        });
    }

    // Synchronise l'état lors de la connexion
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            boolean isActive = fertilizingPlayers.contains(player.getUUID());
            MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new FertilizeStateMessage(isActive));
            
            // Synchronise aussi l'inventaire
            player.getCapability(SpecialSlotCapabilityHandler.SPECIAL_SLOT_CAP).ifPresent(inv -> {
                MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new FertilizerSlotMessage(inv.getStackInSlot(0)));
            });
        }
    }
    
    private static boolean isOnCooldown(CommandSourceStack source) {
        if (source.getEntity() == null) return false; // Console ou bloc de commande : pas de cooldown
        UUID uuid = source.getEntity().getUUID();
        long now = System.currentTimeMillis();
        if (lastCommandTime.containsKey(uuid)) {
            long last = lastCommandTime.get(uuid);
            if (now - last < COOLDOWN_MS) {
                source.sendFailure(Component.literal("⏳ Veuillez attendre 3 secondes entre chaque commande."));
                return true;
            }
        }
        lastCommandTime.put(uuid, now);
        return false;
    }

    public static boolean isActive(UUID id) {
        return fertilizingPlayers.contains(id);
    }

    public static long getCooldownRemainingMs(UUID id) {
        return Math.max(0, COOLDOWN_MS - (System.currentTimeMillis() - lastCommandTime.getOrDefault(id, 0L)));
    }
}
