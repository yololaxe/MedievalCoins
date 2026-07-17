package fr.renblood.medievalcoins.tree;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
    private static final Map<UUID, Double> playerRanges = new HashMap<>(); // Stocke la portée préférée
    private static final long COOLDOWN_MS = 3000; // 3 secondes
    private static final double DEFAULT_RANGE = 3.0;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        d.register(Commands.literal("mc").then(Commands.literal("ability").then(Commands.literal("magnet")
                .requires(src -> src.hasPermission(0))
                .executes(c -> toggleMagnet(c.getSource(), -1)) // -1 signifie "utiliser la valeur par défaut/stockée"
                .then(Commands.argument("range", DoubleArgumentType.doubleArg(2.0, 8.0))
                        .executes(c -> toggleMagnet(c.getSource(), DoubleArgumentType.getDouble(c, "range")))
                )
        )));
    }

    private static int toggleMagnet(CommandSourceStack src, double rangeArg) {
        if (isOnCooldown(src)) return 0;

        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("❌ Cette commande doit être exécutée en jeu."));
            return 0;
        }

        if (!TreePermissionChecker.hasUnlocked(player, TreeAbility.MAGNET)) {
            src.sendFailure(Component.literal("❌ Vous devez améliorer votre métier de charpentier."));
            return 0;
        }

        UUID id = player.getUUID();
        
        // Si une portée est spécifiée, on la met à jour
        if (rangeArg != -1) {
            playerRanges.put(id, rangeArg);
        }

        // Si le joueur n'a pas de portée définie, on met la défaut
        if (!playerRanges.containsKey(id)) {
            playerRanges.put(id, DEFAULT_RANGE);
        }

        double currentStoredRange = playerRanges.get(id);

        if (activePlayers.contains(id)) {
            // Le mode est ACTIF.
            
            // Cas 1 : Commande simple (/magnet) -> On désactive.
            if (rangeArg == -1) {
                activePlayers.remove(id);
                src.sendSuccess(() -> Component.literal("❌ Mode Magnet désactivé."), true);
            } 
            // Cas 2 : Commande avec portée (/magnet 5).
            else {
                // Si la portée demandée est la même que celle active (ou stockée), on désactive (toggle).
                // Note : Ici on compare avec la valeur qu'on vient de mettre dans la map, donc c'est toujours égal.
                // Il faut savoir si la portée a CHANGÉ par rapport à avant l'appel.
                // Mais comme on a déjà mis à jour la map au début...
                
                // Simplification :
                // Si on spécifie une portée, on FORCE l'activation avec cette portée (mise à jour).
                // Sauf si on veut que /magnet 5 désactive si on est déjà en magnet 5.
                
                // Logique "Toggle intelligent" :
                // Si actif et range == -1 -> OFF
                // Si actif et range != -1 -> UPDATE (reste ON avec nouvelle portée)
                
                src.sendSuccess(() -> Component.literal("✅ Mode Magnet mis à jour (Portée: " + currentStoredRange + " blocs)."), true);
            }
        } else {
            // Le mode est INACTIF -> On active.
            activePlayers.add(id);
            src.sendSuccess(() -> Component.literal("✅ Mode Magnet activé (Portée: " + currentStoredRange + " blocs)."), true);
        }
        return 1;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (!activePlayers.contains(event.player.getUUID())) return;

        ServerPlayer player = (ServerPlayer) event.player;
        if (player.isSpectator()) return;

        double range = playerRanges.getOrDefault(player.getUUID(), DEFAULT_RANGE);
        AABB box = player.getBoundingBox().inflate(range);
        List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class, box);

        for (ItemEntity item : items) {
            if (item.isAlive() && !item.hasPickUpDelay()) {
                Vec3 playerPos = player.position().add(0, 0.5, 0);
                Vec3 itemPos = item.position();
                Vec3 direction = playerPos.subtract(itemPos).normalize().scale(0.5);
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

    public static void removePlayer(UUID id) {
        activePlayers.remove(id);
        playerRanges.remove(id);
    }

    public static boolean isActive(UUID id) {
        return activePlayers.contains(id);
    }

    public static long getCooldownRemainingMs(UUID id) {
        return Math.max(0, COOLDOWN_MS - (System.currentTimeMillis() - lastCommandTime.getOrDefault(id, 0L)));
    }
}
