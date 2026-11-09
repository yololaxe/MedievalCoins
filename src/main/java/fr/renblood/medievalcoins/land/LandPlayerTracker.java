package fr.renblood.medievalcoins.land;

import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID)
public final class LandPlayerTracker {
    private static final Map<UUID, PlayerLandPosition> LAST_POSITIONS = new HashMap<>();

    private LandPlayerTracker() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        LandConfig.LandConfigData config = LandConfig.get();
        if (!config.isEnableLandSystem() || !config.isShowActionBarOnEnter()) {
            return;
        }

        ChunkPos chunk = player.chunkPosition();
        String dimension = player.level().dimension().location().toString();
        UUID playerId = player.getUUID();
        PlayerLandPosition previous = LAST_POSITIONS.get(playerId);

        if (previous != null && previous.isSameChunk(dimension, chunk.x, chunk.z)) {
            return;
        }

        Optional<LandPlot> currentPlot = LandRegistry.findAt(player.level().dimension(), chunk.x, chunk.z);
        String currentPlotId = currentPlot.map(LandPlot::getId).orElse("");
        LAST_POSITIONS.put(playerId, new PlayerLandPosition(dimension, chunk.x, chunk.z, currentPlotId));

        if (previous == null || currentPlot.isEmpty() || currentPlotId.equals(previous.plotId())) {
            return;
        }

        LandPlot plot = currentPlot.get();
        player.displayClientMessage(Component.literal(plot.getDisplayName()
                + " \u2014 Propri\u00e9taire : " + ownerName(plot)), true);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_POSITIONS.remove(event.getEntity().getUUID());
    }

    public static void clear() {
        LAST_POSITIONS.clear();
    }

    private static String ownerName(LandPlot plot) {
        return plot.getOwner().isBlank() ? "-" : plot.getOwner();
    }

    private record PlayerLandPosition(String dimension, int chunkX, int chunkZ, String plotId) {
        private boolean isSameChunk(String currentDimension, int currentChunkX, int currentChunkZ) {
            return dimension.equals(currentDimension) && chunkX == currentChunkX && chunkZ == currentChunkZ;
        }
    }
}
