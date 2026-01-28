package fr.renblood.medievalcoins.events;

import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID)
public class TimeManager {

    // Facteur de ralentissement. 3.0 = 3x plus long (60 min total)
    public static double dayLengthMultiplier = 3.0;
    
    // Pourcentage de joueurs devant dormir pour passer la nuit (0.0 à 1.0)
    public static double sleepPercentage = 0.8;

    private static double timeAccumulator = 0;
    private static long lastGameTime = -1;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();

        // 1. Désactiver le cycle vanilla
        if (level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        }

        // 2. Gestion du sommeil
        if (shouldSkipNight(level)) {
            if (level.getGameTime() % 20 == 0) {
                long currentTime = level.getDayTime();
                long timeToMorning = 24000 - (currentTime % 24000);
                
                level.setDayTime(currentTime + timeToMorning);
                
                resetWeather(level);
                handleWakeUp(level); // Soin + Malus
                
                timeAccumulator = 0;
                lastGameTime = level.getDayTime();
                return;
            }
        }

        long currentDayTime = level.getDayTime();

        // Détection saut temporel externe
        if (lastGameTime != -1 && Math.abs(currentDayTime - lastGameTime) > 100) {
            timeAccumulator = 0;
        } else {
            // Avancement personnalisé
            double increment = 1.0 / dayLengthMultiplier;
            timeAccumulator += increment;

            if (timeAccumulator >= 1.0) {
                long ticksToAdd = (long) timeAccumulator;
                level.setDayTime(currentDayTime + ticksToAdd);
                timeAccumulator -= ticksToAdd;
            }
        }

        lastGameTime = level.getDayTime();
    }

    private static boolean shouldSkipNight(ServerLevel level) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return false;

        int totalPlayers = 0;
        int sleepingCount = 0;

        for (ServerPlayer player : players) {
            if (player.isSpectator()) continue;
            totalPlayers++;
            if (player.isSleeping()) {
                sleepingCount++;
            }
        }

        if (totalPlayers == 0) return false;

        // Vérifie si le pourcentage est atteint
        return (double) sleepingCount / totalPlayers >= sleepPercentage;
    }

    private static void handleWakeUp(ServerLevel level) {
        // Durée du malus : 15 minutes (si x3) = 900 secondes = 18000 ticks
        // Formule : 5 minutes * multiplier
        int malusDuration = (int) (5 * 60 * 20 * dayLengthMultiplier);

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;

            if (player.isSleeping()) {
                // Joueur qui a dormi : Soin complet + Réveil
                player.stopSleeping();
                player.heal(player.getMaxHealth()); // Soin complet
                player.getFoodData().setFoodLevel(20); // Saturation max (optionnel, sympa pour le matin)
                player.sendSystemMessage(Component.literal("§aVous vous réveillez en pleine forme !"));
            } else {
                // Joueur qui n'a pas dormi : Malus Lenteur
                // On utilise le constructeur (effect, duration, amplifier, ambient, visible)
                // visible = false désactive les particules
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, malusDuration, 0, false, false));
                player.sendSystemMessage(Component.literal("§cVous n'avez pas dormi... La fatigue vous pèse."));
            }
        }
    }

    private static void resetWeather(ServerLevel level) {
        level.setWeatherParameters(0, 0, false, false);
    }
}