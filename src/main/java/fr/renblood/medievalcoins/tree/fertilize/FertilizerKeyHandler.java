package fr.renblood.medievalcoins.tree.fertilize;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.tree.keybinds.FertilizeKeybinds;
import fr.renblood.medievalcoins.tree.network.FertilizeKeyMessage;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID, value = Dist.CLIENT)
public class FertilizerKeyHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        // Vérifie que le jeu est actif et qu'on n'est pas dans un GUI
        if (Minecraft.getInstance().screen != null) return;

        // On utilise isDown() ou consumeClick() selon le besoin.
        // consumeClick() est mieux pour une action unique par appui.
        while (FertilizeKeybinds.KEY.consumeClick()) {
            if (MedievalCoin.DEBUG_MODE) {
                MedievalCoin.LOGGER.info("Key pressed! Sending packet...");
            }
            // 👉 Envoi au serveur quand on appuie sur la touche
            MedievalCoin.PACKET_HANDLER.sendToServer(new FertilizeKeyMessage());
        }
    }
}
