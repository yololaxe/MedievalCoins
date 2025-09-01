
import com.mojang.blaze3d.platform.InputConstants;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.tree.capability.FertilizerCapabilityHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID, value = Dist.CLIENT)
public class FertilizeKeybinds {

    public static final KeyMapping KEY = new KeyMapping(
            "key.medieval_coins.fertilize", // traduction (lang file)
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R, // touche R
            "key.categories.gameplay"
    );

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Vérifie si la touche est pressée
        if (KEY.isDown()) {
            mc.player.getCapability(FertilizerCapabilityHandler.FERTILIZER_CAP).ifPresent(inv -> {
                ItemStack slot = inv.getStackInSlot(0);

                if (!slot.isEmpty() && slot.getItem() == Items.BONE_MEAL) {
                    // Met l’item en main principale
                    mc.player.setItemInHand(InteractionHand.MAIN_HAND, slot.copy());

                    // Feedback
                    mc.player.displayClientMessage(Component.literal("🌱 Fertilizer équipé !"), true);
                } else {
                    mc.player.displayClientMessage(Component.literal("❌ Aucun fertilizer disponible"), true);
                }
            });
        }
    }
}
