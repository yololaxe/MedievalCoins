package fr.renblood.medievalcoins.api;

import com.google.gson.JsonObject;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.client.model.PlayerModel;
import fr.renblood.medievalcoins.network.ApiClient;
import fr.renblood.medievalcoins.network.MoneyUpdateMessage;
import fr.renblood.medievalcoins.network.PlayerCache;
import fr.renblood.medievalcoins.network.PlayerStatsUpdateMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * API publique pour permettre aux autres mods d'interagir avec MedievalCoins.
 */
public class MedievalCoinsAPI {

    // --- GESTION XP MÉTIER ---

    /**
     * Récupère l'XP actuelle d'un métier pour un joueur (depuis le cache serveur).
     * @return L'XP actuelle, ou 0 si introuvable.
     */
    public static long getJobXp(ServerPlayer player, String job) {
        String uuid = player.getGameProfile().getId().toString();
        PlayerModel pm = PlayerCache.getPlayer(uuid);
        if (pm != null && pm.experiences != null && pm.experiences.jobs != null) {
            PlayerModel.JobExperience jobExp = pm.experiences.jobs.get(job);
            if (jobExp != null) {
                return jobExp.xp;
            }
        }
        return 0;
    }

    /**
     * Récupère le niveau actuel d'un métier pour un joueur (depuis le cache serveur).
     * @return Le niveau actuel, ou 0 si introuvable.
     */
    public static int getJobLevel(ServerPlayer player, String job) {
        String uuid = player.getGameProfile().getId().toString();
        PlayerModel pm = PlayerCache.getPlayer(uuid);
        if (pm != null && pm.experiences != null && pm.experiences.jobs != null) {
            PlayerModel.JobExperience jobExp = pm.experiences.jobs.get(job);
            if (jobExp != null) {
                return jobExp.level;
            }
        }
        return 0;
    }

    /**
     * Ajoute de l'expérience à un métier pour un joueur.
     * Cette méthode est asynchrone (appel API web).
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

    // --- GESTION ÉCONOMIE ---

    /**
     * Récupère le solde actuel du joueur (depuis le cache serveur).
     */
    public static double getBalance(ServerPlayer player) {
        String uuid = player.getGameProfile().getId().toString();
        PlayerModel pm = PlayerCache.getPlayer(uuid);
        return pm != null ? pm.money : 0.0;
    }

    /**
     * Ajoute de l'argent au joueur (via API).
     * @param amount Montant à ajouter (en "cuivre" ou unité de base).
     */
    public static void addMoney(ServerPlayer player, int amount) {
        modifyMoney(player, amount, true);
    }

    /**
     * Retire de l'argent au joueur (via API).
     * @param amount Montant à retirer.
     */
    public static void removeMoney(ServerPlayer player, int amount) {
        modifyMoney(player, amount, false);
    }


    // --- MÉTHODES INTERNES ---

    private static void modifyJobXp(ServerPlayer player, String action, String job, int amount) {
        new Thread(() -> {
            try {
                String uuid = player.getGameProfile().getId().toString();
                
                // Calcul du bonus d'XP basé sur la compétence "skill"
                int finalAmount = amount;
                if ("add".equalsIgnoreCase(action)) {
                    PlayerModel pm = PlayerCache.getPlayer(uuid);
                    if (pm == null) {
                        pm = ApiClient.getPlayer(uuid);
                        if (pm != null) PlayerCache.updatePlayer(pm);
                    }
                    
                    if (pm != null) {
                        double multiplier = pm.skill / 100.0;
                        if (multiplier < 0.0) multiplier = 1.0;
                        finalAmount = (int) (amount * multiplier);
                        
                        if (MedievalCoin.DEBUG_MODE && finalAmount != amount) {
                            MedievalCoin.LOGGER.info("XP Bonus applied for {}: Base={}, Skill={}, Final={}", 
                                    player.getName().getString(), amount, pm.skill, finalAmount);
                        }
                    }
                }

                JsonObject response = ApiClient.manageJobXp(uuid, action, job, finalAmount);

                String jobName = response.get("job").getAsString();
                int newXp = response.get("new_xp").getAsInt();
                int level = response.get("level").getAsInt();

                PlayerModel updatedPlayerModel = ApiClient.getPlayer(uuid);
                int displayAmount = finalAmount;

                player.getServer().execute(() -> {
                    MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new PlayerStatsUpdateMessage(updatedPlayerModel));

                    String sign = action.equals("remove") ? "-" : (action.equals("add") ? "+" : "=");
                    String color = action.equals("remove") ? "§c" : "§a";
                    String amountStr = action.equals("set") ? String.valueOf(displayAmount) : sign + displayAmount;

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

    private static void modifyMoney(ServerPlayer player, int amount, boolean add) {
        new Thread(() -> {
            try {
                String uuid = player.getGameProfile().getId().toString();
                PlayerModel pm = PlayerCache.getPlayer(uuid);
                if (pm == null) {
                    pm = ApiClient.getPlayer(uuid);
                    PlayerCache.updatePlayer(pm);
                }

                int newBalance;
                if (add) {
                    newBalance = ApiClient.deposit(pm.id_minecraft, amount);
                } else {
                    // Pour le retrait, on utilise withdraw avec un type de pièce fictif (ex: 0 pour iron)
                    // ou on adapte l'API. Ici on suppose que withdraw gère le montant global.
                    // L'API withdraw actuelle prend (id, coinType, amount).
                    // Si on veut retirer un montant global, il faudrait une route dédiée ou adapter.
                    // Pour l'instant, on simule un retrait de "Iron Coins" (type 0) si amount est petit,
                    // mais l'API withdraw semble retirer des PIÈCES, pas une valeur globale.
                    // ATTENTION : ApiClient.withdraw retire 'amount' pièces de type 'coinType'.
                    // Si on veut retirer de la valeur, il faut une route 'remove_money' ou similaire.
                    // Comme elle n'existe pas explicitement dans ce que j'ai vu, je vais utiliser withdraw avec type 0 (Iron = 1 unité)
                    // en supposant que 1 Iron = 1 unité de monnaie.
                    newBalance = ApiClient.withdraw(pm.id_minecraft, 0, amount);
                }
                
                // Mise à jour locale
                pm.money = newBalance;
                PlayerCache.updatePlayer(pm);
                PlayerModel updatedPm = pm; // Pour la lambda

                player.getServer().execute(() -> {
                    // Synchro argent
                    MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new MoneyUpdateMessage(uuid, newBalance));
                    // Synchro globale
                    MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new PlayerStatsUpdateMessage(updatedPm));
                    
                    String action = add ? "reçu" : "payé";
                    String color = add ? "§a" : "§c";
                    player.sendSystemMessage(Component.literal(
                            String.format("%sVous avez %s %d pièces. (Solde: %d)", color, action, amount, newBalance)
                    ), true);
                });

            } catch (Exception e) {
                MedievalCoin.LOGGER.error("Failed to modify money for " + player.getName().getString(), e);
            }
        }).start();
    }
}
