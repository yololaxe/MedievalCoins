package fr.renblood.medievalcoins.commands.tree.fertilize;

import com.mojang.brigadier.CommandDispatcher;
import fr.renblood.medievalcoins.commands.tree.TreeAbility;
import fr.renblood.medievalcoins.commands.tree.TreePermissionChecker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber
public class FertilizeCommand {

    private static final Set<UUID> fertilizingPlayers = new HashSet<>();
    private static final int INTERVAL_TICKS = 20 * 30; // 30s
    private static final int MAX_FERTILIZER = 16;
    private static final int FERTILIZER_SLOT = 8; // dernier slot hotbar

    public static boolean fertilizeActiveHUD = false; // affichage côté client

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        d.register(Commands.literal("fertilize")
                .requires(src -> src.hasPermission(0))
                .executes(c -> {
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
                    if (fertilizingPlayers.contains(id)) {
                        fertilizingPlayers.remove(id);
                        clearFertilizerSlot(player);
                        fertilizeActiveHUD = false;
                        src.sendSuccess(() -> Component.literal("❌ Mode fertilisation désactivé."), true);
                    } else {
                        fertilizingPlayers.add(id);
                        fertilizeActiveHUD = true;
                        src.sendSuccess(() -> Component.literal("✅ Mode fertilisation activé."), true);
                    }
                    return 1;
                }));
    }

    // Tick serveur : ajoute 1 bone meal toutes les 30s
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerPlayer player : playerListCopy()) {
            if (!fertilizingPlayers.contains(player.getUUID())) continue;

            if (player.tickCount % INTERVAL_TICKS == 0) {
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
        ItemStack slot = player.getInventory().getItem(FERTILIZER_SLOT);

        if (!slot.isEmpty() && slot.getItem() == Items.BONE_MEAL) {
            if (slot.getCount() < MAX_FERTILIZER) {
                slot.grow(1);
            }
            return;
        }

        if (slot.isEmpty()) {
            ItemStack newStack = new ItemStack(Items.BONE_MEAL, 1);
            newStack.setHoverName(Component.literal("🌱 Fertilizer"));
            player.getInventory().setItem(FERTILIZER_SLOT, newStack);
        }
    }

    /** Nettoie le slot spécial à la désactivation */
    private static void clearFertilizerSlot(ServerPlayer player) {
        ItemStack slot = player.getInventory().getItem(FERTILIZER_SLOT);
        if (!slot.isEmpty() && slot.getItem() == Items.BONE_MEAL) {
            player.getInventory().setItem(FERTILIZER_SLOT, ItemStack.EMPTY);
        }
    }

    // Empêche de drop la bone meal spéciale
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (event.getEntity().getItem().getItem() == Items.BONE_MEAL) {
            event.setCanceled(true);
            if (event.getPlayer() != null) {
                event.getPlayer().displayClientMessage(
                        Component.literal("⚠️ Impossible de drop la Fertilizer !"), true
                );
            }
        }
    }

    // Surveille les déplacements : on le ramène toujours dans slot 8
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof ServerPlayer player)) return;
        if (event.phase != TickEvent.Phase.END) return;

        for (int i = 0; i < player.getInventory().items.size(); i++) {
            if (i == FERTILIZER_SLOT) continue;
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.getItem() == Items.BONE_MEAL) {
                // supprime et replace dans slot verrouillé
                player.getInventory().removeItem(i, stack.getCount());
                if (player.getInventory().getItem(FERTILIZER_SLOT).isEmpty()) {
                    player.getInventory().setItem(FERTILIZER_SLOT, stack);
                }
            }
        }
    }

    // HUD côté client
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(value = Dist.CLIENT)
    public static class FertilizeHUD {
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void renderHUD(RenderGuiOverlayEvent.Post event) {
            if (!fertilizeActiveHUD) return;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.client.gui.GuiGraphics gui = event.getGuiGraphics();
            int x = mc.getWindow().getGuiScaledWidth() / 2;
            int y = mc.getWindow().getGuiScaledHeight() - 70;
            gui.drawString(mc.font, "🌱 Fertilizer Mode ON", x - 40, y, 0x00FF00, true);
        }
    }
}
