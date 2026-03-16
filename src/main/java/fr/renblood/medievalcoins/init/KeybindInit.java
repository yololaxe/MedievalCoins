package fr.renblood.medievalcoins.init;

import com.mojang.blaze3d.platform.InputConstants;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.client.gui.QuestScreen;
import fr.renblood.medievalcoins.client.gui.RadialMenuScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeybindInit {

    public static final KeyMapping QUEST_KEY = new KeyMapping(
            "key.medieval_coins.quest",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.medieval_coins"
    );

    public static final KeyMapping RADIAL_MENU_KEY = new KeyMapping(
            "key.medieval_coins.radial_menu",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.medieval_coins"
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(QUEST_KEY);
        event.register(RADIAL_MENU_KEY);
    }

    @Mod.EventBusSubscriber(modid = MedievalCoin.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (QUEST_KEY.consumeClick()) {
                Minecraft.getInstance().setScreen(new QuestScreen());
            }
            if (RADIAL_MENU_KEY.isDown()) {
                if (!(Minecraft.getInstance().screen instanceof RadialMenuScreen)) {
                    Minecraft.getInstance().setScreen(new RadialMenuScreen());
                }
            }
        }
    }
}
