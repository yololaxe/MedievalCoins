package fr.renblood.medievalcoins.events;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.client.model.PlayerModel;
import fr.renblood.medievalcoins.network.ApiClient;
import fr.renblood.medievalcoins.network.PlayerCache;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID)
public class WelcomeHandler {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        new Thread(() -> {
            try {
                String uuid = player.getGameProfile().getId().toString();
                // On essaie de récupérer les infos fraîches, sinon cache
                PlayerModel pm = ApiClient.getPlayer(uuid);
                if (pm == null) pm = PlayerCache.getPlayer(uuid);

                if (pm != null) {
                    String rank = pm.rank != null ? pm.rank : "Voyageur";
                    String name = pm.pseudo_minecraft;

                    // On doit exécuter l'envoi de packet sur le thread serveur principal
                    player.getServer().execute(() -> {
                        // Titre : Bienvenue
                        player.connection.send(new ClientboundSetTitleTextPacket(
                                Component.literal("§6Bienvenue")
                        ));
                        // Sous-titre : [Rang] Pseudo
                        player.connection.send(new ClientboundSetSubtitleTextPacket(
                                Component.literal("§e[" + rank + "] §f" + name)
                        ));
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
