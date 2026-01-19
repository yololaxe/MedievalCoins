// src/main/java/fr/renblood/medievalcoins/tree/keybinds/FertilizeKeybinds.java
package fr.renblood.medievalcoins.tree.keybinds;

import com.mojang.blaze3d.platform.InputConstants;
import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class FertilizeKeybinds {

    public static final KeyMapping KEY = new KeyMapping(
            "key.medieval_coins.fertilize",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.medieval_coins" // Catégorie personnalisée pour mieux la retrouver
    );

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(KEY);
    }
}
