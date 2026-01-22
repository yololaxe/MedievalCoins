package fr.renblood.medievalcoins.api;

import com.google.gson.JsonObject;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.client.model.PlayerModel;
import fr.renblood.medievalcoins.network.ApiClient;
import fr.renblood.medievalcoins.network.PlayerStatsUpdateMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * API publique pour permettre aux autres mods d'interagir avec MedievalCoins.
 */
public class MedievalCoinsAPI {

    /**
     * Ajoute de l'expérience à un métier pour un joueur.
     * Cette méthode est asynchrone (appel API web).
     *
     * @param player Le joueur concerné
     * @param job Le nom du métier (ex: "miner", "lumberjack")
     * @param amount La quantité d'XP à ajouter
     */
    public static void addJobXp(ServerPlayer player, String job, int amount) {
        modifyJobXp(player, "add", job, amount);
    }

    /**
     * Définit l'expérience d'un métier pour un joueur.
     */
    public static void setJobXp(ServerPlayer player, String job, int amount) {
        modifyJobXp(player, "set", job, amount);
    }

    /**
     * Retire de l'expérience à un métier pour un joueur.
     */
    public static void removeJobXp(ServerPlayer player, String job, int amount) {
        modifyJobXp(player, "remove", job, amount);
    }

    private static void modifyJobXp(ServerPlayer player, String action, String job, int amount) {
        new Thread(() -> {
            try {
                String uuid = player.getGameProfile().getId().toString();
                JsonObject response = ApiClient.manageJobXp(uuid, action, job, amount);

                String jobName = response.get("job").getAsString();
                int newXp = response.get("new_xp").getAsInt();
                int level = response.get("level").getAsInt();

                // On récupère le PlayerModel complet mis à jour pour le client
                PlayerModel updatedPlayerModel = ApiClient.getPlayer(uuid);
                
                player.getServer().execute(() -> {
                    // Envoie le PlayerModel complet au client pour synchro
                    MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new PlayerStatsUpdateMessage(updatedPlayerModel));

                    // Feedback visuel pour le joueur (Action Bar)
                    String sign = action.equals("remove") ? "-" : (action.equals("add") ? "+" : "=");
                    String color = action.equals("remove") ? "§c" : "§a"; // Rouge si remove, Vert sinon
                    
                    String amountStr = action.equals("set") ? String.valueOf(amount) : sign + amount;

                    player.sendSystemMessage(Component.literal(
                            String.format("%s%s XP §6%s §7(Total: §b%d §7- Niveau §e%d§7)", 
                                    color, amountStr, jobName, newXp, level)
                    ), true);
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
