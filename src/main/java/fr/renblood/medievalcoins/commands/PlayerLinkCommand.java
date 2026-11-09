package fr.renblood.medievalcoins.commands;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.renblood.medievalcoins.config.ModConfig;
import fr.renblood.medievalcoins.network.ApiExecutor;
import fr.renblood.medievalcoins.network.ApiHttpClient;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Verifies the current game session; HTTP work never runs on the server tick thread. */
public final class PlayerLinkCommand {
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    private PlayerLinkCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("lier")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(
                            "Sur le site Renblood : Mon profil > Associer Minecraft. Puis /mc lier <code>."), false);
                    return 1;
                })
                .then(Commands.argument("code", StringArgumentType.word())
                        .executes(context -> confirm(context.getSource(), StringArgumentType.getString(context, "code"))));
    }

    private static int confirm(CommandSourceStack source, String code) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!code.matches("(?i)[A-Z2-9]{8}")) {
            source.sendFailure(Component.literal("Le code comporte 8 caractères. Copiez la commande depuis votre profil Renblood."));
            return 0;
        }
        UUID uuid = player.getUUID();
        ModConfig configuration = ModConfig.load();
        if (configuration.playerLinkServerId == null || configuration.playerLinkServerId.isBlank()
                || configuration.playerLinkKey == null || configuration.playerLinkKey.isBlank()) {
            source.sendFailure(Component.literal("L'association Minecraft n'est pas encore configurée sur ce serveur. Contactez l'administrateur."));
            return 0;
        }
        if (!PENDING.add(uuid)) {
            source.sendFailure(Component.literal("Une vérification est déjà en cours. Patientez un instant."));
            return 0;
        }
        String username = player.getGameProfile().getName();
        var server = source.getServer();
        source.sendSuccess(() -> Component.literal("Association à Renblood : vérification en cours…").withStyle(ChatFormatting.GOLD), false);
        try {
            ApiExecutor.supply(() -> {
                try {
                    ModConfig config = ModConfig.load();
                    JsonObject body = new JsonObject();
                    body.addProperty("code", code);
                    body.addProperty("username", username);
                    String endpoint = config.apiUrl.replaceAll("/+$", "") + "/players/onboarding/minecraft/confirm/";
                    return JsonParser.parseString(ApiHttpClient.minecraftPost(endpoint, body, uuid.toString())).getAsJsonObject();
                } catch (Exception exception) {
                    throw new java.util.concurrent.CompletionException(exception);
                }
            }).whenComplete((result, failure) -> {
                PENDING.remove(uuid);
                server.execute(() -> {
                    if (server.getPlayerList().getPlayer(uuid) != player) return;
                    if (failure != null) {
                        source.sendFailure(Component.literal(friendlyError(failure)));
                    } else {
                        source.sendSuccess(() -> Component.literal(result.get("message").getAsString()).withStyle(ChatFormatting.GREEN), false);
                        if (result.has("player_ready") && result.get("player_ready").getAsBoolean()) {
                            source.sendSuccess(() -> Component.literal("Votre personnage est prêt. Reconnectez-vous au monde pour charger ses données.").withStyle(ChatFormatting.GOLD), false);
                        }
                    }
                });
            });
        } catch (RuntimeException exception) {
            PENDING.remove(uuid);
            source.sendFailure(Component.literal("La vérification est occupée. Réessayez dans un instant."));
            return 0;
        }
        return 1;
    }

    private static String friendlyError(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        String message = failure.getMessage();
        if (message != null && message.contains("{")) {
            try {
                JsonObject body = JsonParser.parseString(message.substring(message.indexOf('{'))).getAsJsonObject();
                if (body.has("message")) return body.get("message").getAsString();
            } catch (RuntimeException ignored) {}
        }
        return "Impossible de confirmer l'association. Vérifiez la connexion du serveur et la validité du code, puis réessayez.";
    }
}
