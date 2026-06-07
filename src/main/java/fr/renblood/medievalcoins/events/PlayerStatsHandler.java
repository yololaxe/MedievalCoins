package fr.renblood.medievalcoins.events;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.PlayerModel;
import fr.renblood.medievalcoins.divine.DivineSessionManager;
import fr.renblood.medievalcoins.network.ApiClient;
import fr.renblood.medievalcoins.network.PlayerCache;
import fr.renblood.medievalcoins.network.PlayerStatsUpdateMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID)
public class PlayerStatsHandler {

    // UUIDs fixes pour les modificateurs d'attributs afin de pouvoir les mettre à jour (supprimer/ajouter)
    private static final UUID LIFE_MODIFIER_ID = UUID.fromString("d0d0d0d0-0000-0000-0000-000000000001");
    private static final UUID STRENGTH_MODIFIER_ID = UUID.fromString("d0d0d0d0-0000-0000-0000-000000000002");
    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("d0d0d0d0-0000-0000-0000-000000000003");
    private static final UUID REACH_MODIFIER_ID = UUID.fromString("d0d0d0d0-0000-0000-0000-000000000004");
    private static final UUID RESISTANCE_MODIFIER_ID = UUID.fromString("d0d0d0d0-0000-0000-0000-000000000005");
    private static final UUID HASTE_MODIFIER_ID = UUID.fromString("d0d0d0d0-0000-0000-0000-000000000006");

    // Timer pour le refresh périodique (toutes les 60 secondes = 1200 ticks)
    private static int refreshTimer = 0;
    private static final int REFRESH_INTERVAL = 1200;

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Au login, on force un refresh des données depuis l'API
            refreshPlayerStats(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Au respawn, on réapplique les stats car les modificateurs peuvent être perdus
            // On utilise le cache local pour éviter un appel API inutile si les données n'ont pas changé
            String uuid = player.getGameProfile().getId().toString();
            PlayerModel pm = PlayerCache.getPlayer(uuid);
            if (pm != null) {
                applyStats(player, pm);
                // IMPORTANT : On renvoie les stats au client pour s'assurer que son cache est à jour (inventaire, etc.)
                MedievalCoin.PACKET_HANDLER.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new PlayerStatsUpdateMessage(pm)
                );
            } else {
                refreshPlayerStats(player);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        refreshTimer++;
        if (refreshTimer >= REFRESH_INTERVAL) {
            refreshTimer = 0;
            // Refresh pour tous les joueurs connectés
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                refreshPlayerStats(player);
            }
        }
    }
    
    // Gestion de la régénération custom
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        // Récupération de la stat regeneration
        String uuid = player.getGameProfile().getId().toString();
        PlayerModel pm = PlayerCache.getPlayer(uuid);
        int regeneration = pm != null ? pm.regeneration + DivineSessionManager.regenerationDelta(player) : 0;
        if (regeneration > 0) {
            // Formule : 1 PV toutes les (60 / X) secondes
            // En ticks : (60 / X) * 20
            int intervalTicks = (int) ((60.0 / regeneration) * 20);
            if (intervalTicks < 1) intervalTicks = 1; // Sécurité

            if (player.tickCount % intervalTicks == 0) {
                if (player.getHealth() < player.getMaxHealth()) {
                    player.heal(1.0f); // Soigne 1 PV (demi-cœur)
                }
            }
        }
    }

    // Gestion de la vitesse de minage (Dig Speed)
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        // Correction : on applique aussi côté client pour que l'animation soit synchro
        Player player = event.getEntity();
        String uuid = player.getGameProfile().getId().toString();
        PlayerModel pm = PlayerCache.getPlayer(uuid);
        if (pm != null) {
            // Base = 100. Si haste = 78, vitesse = 78% de la normale.
            // Si haste = 120, vitesse = 120% de la normale.
            float multiplier = pm.haste / 100.0f;
            event.setNewSpeed(event.getOriginalSpeed() * multiplier);
        }
    }

    public static void refreshPlayerStats(ServerPlayer player) {
        new Thread(() -> {
            try {
                String uuid = player.getGameProfile().getId().toString();
                // On récupère les données fraîches depuis l'API
                PlayerModel pm = ApiClient.getPlayer(uuid);
                if (pm != null) {
                    PlayerCache.updatePlayer(pm);
                    // On applique les stats sur le thread serveur principal
                    player.server.execute(() -> applyStats(player, pm));
                    
                    // IMPORTANT : On envoie aussi au client pour qu'il mette à jour son cache (pour BreakSpeed client-side)
                    MedievalCoin.PACKET_HANDLER.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new PlayerStatsUpdateMessage(pm)
                    );
                }
            } catch (Exception e) {
                MedievalCoin.LOGGER.error("Failed to refresh stats for " + player.getName().getString(), e);
            }
        }).start();
    }

    public static void reapplyCachedStats(ServerPlayer player) {
        PlayerModel pm = PlayerCache.getPlayer(player.getGameProfile().getId().toString());
        if (pm != null) {
            applyStats(player, pm);
        } else {
            DivineSessionManager.applyModifiers(player);
        }
    }

    private static void applyStats(ServerPlayer player, PlayerModel pm) {
        // 1. Vie (Life)
        double targetMaxHealth = pm.life;
        updateAttribute(player, Attributes.MAX_HEALTH, LIFE_MODIFIER_ID, "RP Life Bonus", targetMaxHealth - 20.0, AttributeModifier.Operation.ADDITION);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }

        // 2. Force (Strength) -> Attack Damage
        double strengthBonus = Math.max(0, pm.strength - 1);
        updateAttribute(player, Attributes.ATTACK_DAMAGE, STRENGTH_MODIFIER_ID, "RP Strength Bonus", strengthBonus, AttributeModifier.Operation.ADDITION);

        // 3. Vitesse (Speed) -> Movement Speed
        double speedBonus = (pm.speed - 100) / 100.0;
        updateAttribute(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_ID, "RP Speed Bonus", speedBonus, AttributeModifier.Operation.MULTIPLY_TOTAL);

        // 4. Portée (Reach) -> Block Reach & Entity Reach
        double reachBonus = pm.reach - 5.0;
        updateAttribute(player, ForgeMod.BLOCK_REACH.get(), REACH_MODIFIER_ID, "RP Reach Bonus", reachBonus, AttributeModifier.Operation.ADDITION);

        // 5. Résistance (Resistance) -> Armor
        double armorBonus = pm.resistance;
        updateAttribute(player, Attributes.ARMOR, RESISTANCE_MODIFIER_ID, "RP Resistance Bonus", armorBonus, AttributeModifier.Operation.ADDITION);

        // 6. Hâte (Haste) -> Attack Speed
        // Base = 100. Si haste = 78, vitesse = 78% de la normale.
        // On utilise un multiplicateur. Si haste = 78, on veut réduire de 22%.
        // (78 - 100) / 100.0 = -0.22.
        double attackSpeedBonus = (pm.haste - 100) / 100.0;
        updateAttribute(player, Attributes.ATTACK_SPEED, HASTE_MODIFIER_ID, "RP Haste Bonus", attackSpeedBonus, AttributeModifier.Operation.MULTIPLY_TOTAL);
        DivineSessionManager.applyModifiers(player);

        if (MedievalCoin.DEBUG_MODE) {
            MedievalCoin.LOGGER.info("Updated stats for {}: Life={}, Str={}, Spd={}, Reach={}, Res={}, Haste={}",
                    player.getName().getString(), pm.life, pm.strength, pm.speed, pm.reach, pm.resistance, pm.haste);
        }
    }

    private static void updateAttribute(ServerPlayer player, net.minecraft.world.entity.ai.attributes.Attribute attribute, UUID modifierId, String name, double value, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(modifierId);
            if (Math.abs(value) > 0.001) {
                instance.addTransientModifier(new AttributeModifier(modifierId, name, value, operation));
            }
        }
    }
}
