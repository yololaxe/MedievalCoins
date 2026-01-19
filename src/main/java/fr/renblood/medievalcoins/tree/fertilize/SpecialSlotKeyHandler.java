package fr.renblood.medievalcoins.tree.fertilize;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.tree.keybinds.SpecialSlotKeybinds;
import fr.renblood.medievalcoins.tree.network.SpecialSlotKeyMessage;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID, value = Dist.CLIENT)
public class SpecialSlotKeyHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (Minecraft.getInstance().screen != null) return;

        while (SpecialSlotKeybinds.KEY.consumeClick()) {
            if (MedievalCoin.DEBUG_MODE) {
                MedievalCoin.LOGGER.info("Special Slot Key pressed! Sending packet...");
            }
            MedievalCoin.PACKET_HANDLER.sendToServer(new SpecialSlotKeyMessage());
        }
    }
}
