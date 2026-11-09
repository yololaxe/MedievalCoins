package fr.renblood.medievalcoins.events;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.divine.DivineSessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID)
public final class DivineSessionEvents {
    private DivineSessionEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerStatsHandler.reapplyCachedStats(player);
        DivineSessionManager.sendSessionMessage(player);
    }
}
