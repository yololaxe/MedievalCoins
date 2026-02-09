package fr.renblood.medievalcoins.events;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.client.gui.RadialMenuScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID, value = Dist.CLIENT)
public class KeyInputHandler {

    public static final KeyMapping RADIAL_MENU_KEY = new KeyMapping(
            "key.medieval_coins.radial_menu",
            GLFW.GLFW_KEY_G,
            "key.categories.medieval_coins"
    );

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (RADIAL_MENU_KEY.consumeClick()) {
            Minecraft.getInstance().setScreen(new RadialMenuScreen());
        }
    }
    
    @Mod.EventBusSubscriber(modid = MedievalCoin.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientRegistry {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(RADIAL_MENU_KEY);
        }
    }
}
