package fr.renblood.medievalcoins.events;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.client.model.PlayerModel;
import fr.renblood.medievalcoins.network.PlayerCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID, value = Dist.CLIENT)
public class ClientInventoryEvents {

    private static final ResourceLocation LOCK_TEXTURE = new ResourceLocation(MedievalCoin.MODID, "textures/gui/lock.png");

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

    @SubscribeEvent
    public static void onRenderTooltip(RenderTooltipEvent.Pre event) {
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen screen)) return;
        if (isLockedSlot(screen.getSlotUnderMouse())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        Slot slot = screen.getSlotUnderMouse();
        
        if (isLockedSlot(slot)) {
            event.setCanceled(true);
            return;
        }

        if (event.getButton() == 0 && hasShiftDown() && slot != null && slot.hasItem()) {
            Player player = Minecraft.getInstance().player;
            if (player != null && slot.container == player.getInventory()) {
                int limit = getInventoryLimit(player);
                if (limit >= 36) return;

                int sourceIndex = slot.getContainerSlot();
                boolean fromHotbar = sourceIndex >= 0 && sourceIndex < 9;
                boolean fromMain = sourceIndex >= 9 && sourceIndex < 36;
                
                if (fromHotbar || fromMain) {
                    ItemStack stackToMove = slot.getItem();
                    boolean canFit = false;
                    
                    int targetStart = fromHotbar ? 9 : 0;
                    int targetEnd = fromHotbar ? 36 : 9;
                    
                    for (int i = targetStart; i < targetEnd; i++) {
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
                        event.setCanceled(true);
                    }
                }
            }
        }
    }
    
    @SubscribeEvent
    public static void onMouseDrag(ScreenEvent.MouseDragged.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        if (isLockedSlot(screen.getSlotUnderMouse())) {
            event.setCanceled(true);
        }
    }
    
    @SubscribeEvent
    public static void onMouseRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        if (isLockedSlot(screen.getSlotUnderMouse())) {
            event.setCanceled(true);
        }
    }

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
    
    private static boolean hasShiftDown() {
        return net.minecraft.client.gui.screens.Screen.hasShiftDown();
    }
}
