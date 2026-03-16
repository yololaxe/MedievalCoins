package fr.renblood.medievalcoins.events;

import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

// Cette classe semble faire doublon avec KeybindInit.
// Je vais commenter son contenu pour éviter les conflits et les références aux écrans.
// Si elle est utilisée ailleurs, il faudra la supprimer proprement.

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID, value = Dist.CLIENT)
public class KeyInputHandler {
    /*
    public static final KeyMapping RADIAL_MENU_KEY = new KeyMapping(
            "key.medieval_coins.radial_menu",
            GLFW.GLFW_KEY_G,
            "key.categories.medieval_coins"
    );

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (RADIAL_MENU_KEY.consumeClick()) {
            // Minecraft.getInstance().setScreen(new RadialMenuScreen()); // CAUSE DU CRASH
        }
    }
    
    @Mod.EventBusSubscriber(modid = MedievalCoin.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientRegistry {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(RADIAL_MENU_KEY);
        }
    }
    */
}
