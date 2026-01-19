package fr.renblood.medievalcoins.tree;

import com.mojang.brigadier.CommandDispatcher;
import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber
public class MagnetCommand {

    private static final Set<UUID> activePlayers = new HashSet<>();
    private static final Map<UUID, Long> lastCommandTime = new HashMap<>();
    private static final long COOLDOWN_MS = 3000; // 3 secondes
    private static final double RANGE = 3.0;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        d.register(Commands.literal("magnet")
                .requires(src -> src.hasPermission(0))
                .executes(c -> {
                    if (isOnCooldown(c.getSource())) return 0;

                    CommandSourceStack src = c.getSource();
                    if (!(src.getEntity() instanceof ServerPlayer player)) {
                        src.sendFailure(Component.literal("❌ Cette commande doit être exécutée en jeu."));
                        return 0;
                    }

                    // Vérifie les permissions métier
                    if (!TreePermissionChecker.hasUnlocked(player, TreeAbility.MAGNET)) {
                        src.sendFailure(Component.literal("❌ Vous devez améliorer votre métier de charpentier."));
                        return 0;
                    }

                    UUID id = player.getUUID();
                    if (activePlayers.contains(id)) {
                        activePlayers.remove(id);
                        src.sendSuccess(() -> Component.literal("❌ Mode Magnet désactivé."), true);
                    } else {
                        activePlayers.add(id);
                        src.sendSuccess(() -> Component.literal("✅ Mode Magnet activé."), true);
                    }
                    return 1;
                }));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (!activePlayers.contains(event.player.getUUID())) return;

        // Logique Magnet
        ServerPlayer player = (ServerPlayer) event.player;
        
        // Si le joueur est mort ou déconnecté, on peut éventuellement le retirer de la liste,
        // mais ici on laisse actif pour qu'il le retrouve au respawn s'il veut.
        if (player.isSpectator()) return;

        AABB box = player.getBoundingBox().inflate(RANGE);
        List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class, box);

        for (ItemEntity item : items) {
            if (item.isAlive() && !item.hasPickUpDelay()) {
                // Attire l'item vers le joueur
                Vec3 playerPos = player.position().add(0, 0.5, 0); // un peu au-dessus des pieds
                Vec3 itemPos = item.position();
                Vec3 direction = playerPos.subtract(itemPos).normalize().scale(0.5); // vitesse d'attraction
                
                item.setDeltaMovement(direction);
            }
        }
    }

    private static boolean isOnCooldown(CommandSourceStack source) {
        if (source.getEntity() == null) return false;
        UUID uuid = source.getEntity().getUUID();
        long now = System.currentTimeMillis();
        if (lastCommandTime.containsKey(uuid)) {
            long last = lastCommandTime.get(uuid);
            if (now - last < COOLDOWN_MS) {
                source.sendFailure(Component.literal("⏳ Veuillez attendre 3 secondes entre chaque commande."));
                return true;
            }
        }
        lastCommandTime.put(uuid, now);
        return false;
    }

    // Méthode utilitaire pour le nettoyage à la déconnexion
    public static void removePlayer(UUID id) {
        activePlayers.remove(id);
    }
}
