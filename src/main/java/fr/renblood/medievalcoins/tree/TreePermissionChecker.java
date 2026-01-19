package fr.renblood.medievalcoins.tree;

import fr.renblood.medievalcoins.client.model.PlayerModel;
import fr.renblood.medievalcoins.network.ApiClient;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class TreePermissionChecker {

    public static boolean hasUnlocked(ServerPlayer player, TreeAbility ability) {
        try {
            PlayerModel model = ApiClient.getPlayer(player.getStringUUID());
            if (model == null || model.experiences == null || model.experiences.jobs == null) {
                player.sendSystemMessage(Component.literal("❌ Impossible de récupérer vos données joueur."));
                return false;
            }

            PlayerModel.JobExperience job = model.experiences.jobs.get(ability.getJobId());
            if (job == null || job.progression == null || ability.getProgressionIndex() >= job.progression.size()) {
                player.sendSystemMessage(Component.literal("❌ Métier " + ability.getJobId() + " invalide ou progression non disponible."));
                return false;
            }

            boolean unlocked = job.progression.get(ability.getProgressionIndex());
            if (!unlocked) {
                player.sendSystemMessage(Component.literal(
                        "⚠ Vous devez améliorer votre métier de " + ability.getJobId() + " pour débloquer cette commande."
                ));
            }
            return unlocked;

        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("❌ Erreur lors de la vérification des permissions : " + e.getMessage()));
            return false;
        }
    }
}
