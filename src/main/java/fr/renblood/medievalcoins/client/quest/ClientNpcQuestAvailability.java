package fr.renblood.medievalcoins.client.quest;

import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientNpcQuestAvailability {
    private static final Set<String> AVAILABLE_SPAWN_IDS = ConcurrentHashMap.newKeySet();

    private ClientNpcQuestAvailability() {
    }

    public static void replace(Collection<String> spawnIds) {
        AVAILABLE_SPAWN_IDS.clear();
        if (spawnIds != null) {
            spawnIds.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .forEach(AVAILABLE_SPAWN_IDS::add);
        }
    }

    public static boolean hasAvailableQuest(String spawnId) {
        return spawnId != null && AVAILABLE_SPAWN_IDS.contains(spawnId);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        AVAILABLE_SPAWN_IDS.clear();
    }
}
