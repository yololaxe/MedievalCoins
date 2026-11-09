package fr.renblood.medievalcoins.events;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.config.ModConfig;
import fr.renblood.medievalcoins.network.ApiExecutor;
import fr.renblood.medievalcoins.network.ApiHttpClient;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber
public final class AdminAuditService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PENDING_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("medieval_coins").resolve("admin_audit_pending.json");
    private static final List<JsonObject> PENDING = new ArrayList<>();
    private static boolean loaded;
    private static long ticks;

    private AdminAuditService() {
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        if (!source.hasPermission(2)) return;
        String raw = event.getParseResults().getReader().getString().trim();
        if (raw.isBlank()) return;

        JsonObject audit = new JsonObject();
        audit.addProperty("command", redact(raw));
        audit.addProperty("created_at", Instant.now().toString());
        audit.addProperty("source", source.getTextName());
        if (source.getEntity() instanceof ServerPlayer player) {
            audit.addProperty("minecraft_uuid", player.getStringUUID());
            audit.addProperty("dimension", player.level().dimension().location().toString());
            audit.addProperty("x", player.getX());
            audit.addProperty("y", player.getY());
            audit.addProperty("z", player.getZ());
        }
        ApiExecutor.execute(() -> sendOrQueue(audit));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 1200 != 0) return;
        ApiExecutor.execute(AdminAuditService::flush);
    }

    private static void sendOrQueue(JsonObject audit) {
        try {
            send(audit);
        } catch (Exception exception) {
            synchronized (PENDING) {
                load();
                PENDING.add(audit);
                while (PENDING.size() > 500) PENDING.remove(0);
                save();
            }
            MedievalCoin.LOGGER.warn("Audit admin mis en attente: {}", exception.getMessage());
        }
    }

    private static void flush() {
        synchronized (PENDING) {
            load();
            if (PENDING.isEmpty()) return;
            List<JsonObject> sent = new ArrayList<>();
            for (JsonObject audit : PENDING) {
                try {
                    send(audit);
                    sent.add(audit);
                } catch (Exception exception) {
                    break;
                }
            }
            PENDING.removeAll(sent);
            save();
        }
    }

    private static void send(JsonObject audit) throws Exception {
        ModConfig config = ModConfig.load();
        String base = config.apiUrl.endsWith("/") ? config.apiUrl.substring(0, config.apiUrl.length() - 1) : config.apiUrl;
        String path = config.adminAuditEndpoint.startsWith("/") ? config.adminAuditEndpoint : "/" + config.adminAuditEndpoint;
        ApiHttpClient.rawJson("POST", base + path, audit.toString());
    }

    private static String redact(String command) {
        String lower = command.toLowerCase();
        if (lower.startsWith("mc admin config api key ") || lower.startsWith("mc config api key ")) {
            return command.substring(0, command.lastIndexOf(' ') + 1) + "<redacted>";
        }
        return command;
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(PENDING_PATH)) return;
            JsonArray values = GSON.fromJson(Files.readString(PENDING_PATH, StandardCharsets.UTF_8), JsonArray.class);
            if (values != null) values.forEach(value -> {
                if (value.isJsonObject()) PENDING.add(value.getAsJsonObject());
            });
        } catch (Exception exception) {
            MedievalCoin.LOGGER.error("Lecture des audits admin en attente impossible", exception);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(PENDING_PATH.getParent());
            Files.writeString(PENDING_PATH, GSON.toJson(PENDING), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            MedievalCoin.LOGGER.error("Sauvegarde des audits admin en attente impossible", exception);
        }
    }
}
