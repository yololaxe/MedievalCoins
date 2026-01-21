package fr.renblood.medievalcoins.events;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.client.model.PlayerModel;
import fr.renblood.medievalcoins.network.PlayerCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID)
public class InventoryLimitHandler {

    private static final ResourceLocation LOCK_TEXTURE = new ResourceLocation(MedievalCoin.MODID, "textures/gui/lock.png");

    // --- PARTIE SERVEUR ---

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int limit = getInventoryLimit(player);
        if (limit >= 36) return;

        if (!canFitInAllowedSlots(player.getInventory(), event.getItem().getItem(), limit)) {
            event.setResult(Event.Result.DENY);
            event.setCanceled(true);
        }
    }

    private static boolean canFitInAllowedSlots(Inventory inv, ItemStack stack, int limit) {
        int remaining = stack.getCount();
        for (int i = 0; i < limit; i++) {
            ItemStack slotStack = inv.getItem(i);
            if (!slotStack.isEmpty() && ItemStack.isSameItemSameTags(slotStack, stack) && slotStack.isStackable()) {
                int space = slotStack.getMaxStackSize() - slotStack.getCount();
                if (space > 0) {
                    remaining -= space;
                    if (remaining <= 0) return true;
                }
            }
        }
        for (int i = 0; i < limit; i++) {
            if (inv.getItem(i).isEmpty()) {
                remaining -= stack.getMaxStackSize();
                if (remaining <= 0) return true;
            }
        }
        return remaining <= 0;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;

        int limit = getInventoryLimit(player);
        Inventory inv = player.getInventory();

        for (int i = 0; i < 36; i++) {
            if (i >= limit) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty()) {
                    player.drop(stack.copy(), true);
                    inv.setItem(i, ItemStack.EMPTY);
                }
            }
        }
    }

    // --- PARTIE CLIENT ---

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onRenderContainer(ContainerScreenEvent.Render.Foreground event) {
        if (!(event.getContainerScreen() instanceof InventoryScreen)) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        int limit = getInventoryLimit(player);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        
        event.getGuiGraphics().pose().pushPose();
        event.getGuiGraphics().pose().translate(0, 0, 200); 

        for (Slot slot : event.getContainerScreen().getMenu().slots) {
            if (slot.container == player.getInventory()) {
                int slotIndex = slot.getContainerSlot();
                if (slotIndex >= 0 && slotIndex < 36 && slotIndex >= limit) {
                    int x = slot.x;
                    int y = slot.y;
                    
                    event.getGuiGraphics().fill(x, y, x + 16, y + 16, 0xA0000000); 
                    event.getGuiGraphics().blit(LOCK_TEXTURE, x, y, 0, 0, 16, 16, 16, 16);
                }
            }
        }
        event.getGuiGraphics().pose().popPose();
        RenderSystem.disableBlend();
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onRenderTooltip(RenderTooltipEvent.Pre event) {
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen screen)) return;
        if (isLockedSlot(screen.getSlotUnderMouse())) {
            event.setCanceled(true);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        Slot slot = screen.getSlotUnderMouse();
        
        // 1. Clic direct sur slot verrouillé
        if (isLockedSlot(slot)) {
            event.setCanceled(true);
            return;
        }

        // 2. Shift-Click vers slot verrouillé
        // Si on fait shift-click sur un slot valide, on doit vérifier si l'item irait dans un slot verrouillé
        if (event.getButton() == 0 && hasShiftDown() && slot != null && slot.hasItem()) {
            Player player = Minecraft.getInstance().player;
            if (player != null && slot.container == player.getInventory()) {
                int limit = getInventoryLimit(player);
                // Si tout est ouvert, pas de souci
                if (limit >= 36) return;

                // Simulation simplifiée :
                // Si on est dans la hotbar (0-8), ça va vers l'inventaire (9-35)
                // Si on est dans l'inventaire (9-35), ça va vers la hotbar (0-8)
                // On vérifie si la destination a de la place dans ses slots AUTORISÉS
                
                int sourceIndex = slot.getContainerSlot();
                boolean fromHotbar = sourceIndex >= 0 && sourceIndex < 9;
                boolean fromMain = sourceIndex >= 9 && sourceIndex < 36;
                
                if (fromHotbar || fromMain) {
                    // On vérifie si l'item peut aller dans la zone cible RESTREINTE
                    // Zone cible :
                    // Si hotbar -> Main (9 à 35)
                    // Si main -> Hotbar (0 à 8)
                    
                    // Mais attention, la limite coupe l'inventaire globalement (0 à limit-1 autorisé)
                    // Donc on doit vérifier si l'item peut aller dans un slot i tel que :
                    // i < limit ET i est dans la zone cible
                    
                    ItemStack stackToMove = slot.getItem();
                    boolean canFit = false;
                    
                    int targetStart = fromHotbar ? 9 : 0;
                    int targetEnd = fromHotbar ? 36 : 9;
                    
                    for (int i = targetStart; i < targetEnd; i++) {
                        // Si le slot cible est verrouillé, on ne peut pas y aller
                        if (i >= limit) continue;
                        
                        ItemStack targetStack = player.getInventory().getItem(i);
                        if (targetStack.isEmpty()) {
                            canFit = true;
                            break;
                        } else if (ItemStack.isSameItemSameTags(targetStack, stackToMove) && targetStack.getCount() < targetStack.getMaxStackSize()) {
                            canFit = true;
                            break;
                        }
                    }
                    
                    if (!canFit) {
                        // Si pas de place dans la zone cible autorisée, on annule le shift-click
                        // Sinon le jeu vanilla essaierait de mettre dans les slots verrouillés (si vides)
                        event.setCanceled(true);
                    }
                }
            }
        }
    }
    
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onMouseDrag(ScreenEvent.MouseDragged.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        if (isLockedSlot(screen.getSlotUnderMouse())) {
            event.setCanceled(true);
        }
    }
    
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onMouseRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        if (isLockedSlot(screen.getSlotUnderMouse())) {
            event.setCanceled(true);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onKeyPress(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        if (isLockedSlot(screen.getSlotUnderMouse())) {
            event.setCanceled(true);
        }
    }

    private static boolean isLockedSlot(Slot slot) {
        if (slot == null) return false;
        Player player = Minecraft.getInstance().player;
        if (player != null && slot.container == player.getInventory()) {
            int slotIndex = slot.getContainerSlot();
            int limit = getInventoryLimit(player);
            return slotIndex >= 0 && slotIndex < 36 && slotIndex >= limit;
        }
        return false;
    }

    private static int getInventoryLimit(Player player) {
        String uuid = player.getGameProfile().getId().toString();
        PlayerModel pm = PlayerCache.getPlayer(uuid);
        if (pm != null) {
            return Math.max(1, Math.min(36, pm.place));
        }
        return 36;
    }
    
    @OnlyIn(Dist.CLIENT)
    private static boolean hasShiftDown() {
        return net.minecraft.client.gui.screens.Screen.hasShiftDown();
    }
}
