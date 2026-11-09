package fr.renblood.medievalcoins.bank;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID)
public final class EconomyFeedback {
    private static final StatusRefreshGate<Key> REFRESH = new StatusRefreshGate<>(256,
            TimeUnit.MILLISECONDS.toNanos(1500), System::nanoTime);
    private EconomyFeedback() { }

    public static void send(ServerPlayer player, String state, String text, String reference) {
        send(player, "transaction", state, text, reference);
    }
    private static void send(ServerPlayer player, String resource, String state, String text, String value) {
        if (!player.getServer().isSameThread()) { player.getServer().execute(() -> send(player, resource, state, text, value)); return; }
        if (player.hasDisconnected()) return;
        MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player),
                new EconomyFeedbackMessage(resource, state, text, value));
    }

    public static void refresh(ServerPlayer player, String business) {
        if (player == null || business == null || business.isBlank() || player.hasDisconnected()) return;
        MinecraftServer server = player.getServer();
        if (!server.isSameThread()) { server.execute(() -> refresh(player, business)); return; }
        Key key = new Key(server, player, business);
        StatusRefreshGate.Ticket ticket = REFRESH.begin(key);
        if (ticket == null) {
            if (!REFRESH.contains(key)) send(player, business, "UNKNOWN", "Registre occupé", "");
            return;
        }
        String uuid = player.getStringUUID();
        try {
            ApiExecutor.supply(() -> {
                try {
                    var result = CommerceProtocol.get(uuid, "commerce/status?business_id="
                            + URLEncoder.encode(business, StandardCharsets.UTF_8));
                    String value = result.has("budget") && !result.get("budget").isJsonNull()
                            ? result.get("budget").getAsString() : "";
                    return new Snapshot(result.get("state").getAsString(), value);
                } catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
            }).whenComplete((snapshot, failure) -> server.execute(() -> {
                if (!REFRESH.complete(key, ticket)) return;
                if (server.getPlayerList().getPlayer(player.getUUID()) != player) return;
                if (failure == null) send(player, business, snapshot.state, "Relevé du registre", snapshot.budget);
                else send(player, business, "UNKNOWN", "Registre indisponible", "");
            }));
        } catch (RuntimeException rejected) {
            REFRESH.rejected(key, ticket);
            send(player, business, "UNKNOWN", "Registre occupé", "");
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        REFRESH.removeIf(key -> key.server == event.getServer());
    }
    private record Snapshot(String state, String budget) { }
    private record Key(MinecraftServer server, ServerPlayer player, String business) {
        @Override public boolean equals(Object other) {
            return other instanceof Key key && server == key.server && player == key.player && business.equals(key.business);
        }
        @Override public int hashCode() {
            return 31 * (31 * System.identityHashCode(server) + System.identityHashCode(player)) + business.hashCode();
        }
    }
}
