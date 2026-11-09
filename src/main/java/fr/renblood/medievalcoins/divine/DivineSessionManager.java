package fr.renblood.medievalcoins.divine;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.events.PlayerStatsHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import fr.renblood.medievalcoins.network.OpenPrayerScreenMessage;
import net.minecraftforge.network.PacketDistributor;

public final class DivineSessionManager {
    private static final UUID LIFE_ID = UUID.fromString("d1d1d1d1-0000-0000-0000-000000000001");
    private static final UUID FORCE_ID = UUID.fromString("d1d1d1d1-0000-0000-0000-000000000002");
    private static final UUID SPEED_ID = UUID.fromString("d1d1d1d1-0000-0000-0000-000000000003");
    private static final UUID REACH_ID = UUID.fromString("d1d1d1d1-0000-0000-0000-000000000004");
    private static final UUID RESISTANCE_ID = UUID.fromString("d1d1d1d1-0000-0000-0000-000000000005");
    private static final Map<UUID, PrayerAttempt> ACTIVE_PRAYERS = new ConcurrentHashMap<>();

    private DivineSessionManager() {
    }

    public static DivineSessionData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                DivineSessionData::load,
                DivineSessionData::new,
                DivineSessionData.DATA_NAME
        );
    }

    public static DivineSign signFor(ServerPlayer player) {
        return DivineSign.forPlayer(player.getUUID());
    }

    public static DivineEffect effectFor(ServerPlayer player) {
        return data(player.server).effectFor(signFor(player));
    }

    public static boolean hasPrayed(ServerPlayer player) {
        return data(player.server).hasPrayed(player.getUUID());
    }

    public static int nextSession(MinecraftServer server) {
        List<Integer> effects = new ArrayList<>();
        for (int i = 0; i < DivineEffect.values().length; i++) effects.add(i);
        Collections.shuffle(effects);

        int[] shuffled = effects.stream().mapToInt(Integer::intValue).toArray();
        DivineSessionData data = data(server);
        data.startNextSession(shuffled);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerStatsHandler.reapplyCachedStats(player);
            sendSessionMessage(player);
        }
        return data.sessionNumber();
    }

    public static void sendSessionMessage(ServerPlayer player) {
        DivineSign sign = signFor(player);
        DivineEffect effect = effectFor(player);
        boolean prayed = hasPrayed(player);
        player.sendSystemMessage(Component.literal("Session divine " + data(player.server).sessionNumber()
                + " - Signe : " + sign.displayName() + " (Mois du " + sign.monthRole() + ")")
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("Influence divine : " + effect.describe(prayed))
                .withStyle(effect.value(prayed) >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (!prayed) {
            player.sendSystemMessage(Component.literal("Priez sur un autel pour ameliorer ce bonus ou reduire ce malus.")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    public static boolean completePrayer(ServerPlayer player) {
        return completePrayer(player, -1);
    }

    public static boolean completePrayer(ServerPlayer player, int gameId) {
        PrayerAttempt attempt = ACTIVE_PRAYERS.remove(player.getUUID());
        if (attempt == null || player.level().getGameTime() - attempt.openedAt() > 300) {
            return false;
        }
        if (gameId >= 0 && attempt.game().id() != gameId) return false;

        boolean firstPrayer = data(player.server).markPrayed(player.getUUID());
        if (firstPrayer) {
            PlayerStatsHandler.reapplyCachedStats(player);
            player.sendSystemMessage(Component.literal("Votre priere a ete entendue : " + effectFor(player).describe(true))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            player.sendSystemMessage(Component.literal("Vous avez deja prie pendant cette session divine.")
                    .withStyle(ChatFormatting.GRAY));
        }
        return firstPrayer;
    }

    public static void beginPrayer(ServerPlayer player) {
        DivineMiniGame game = DivineMiniGame.values()[player.getRandom().nextInt(DivineMiniGame.values().length)];
        ACTIVE_PRAYERS.put(player.getUUID(), new PrayerAttempt(player.level().getGameTime(), game));
        MedievalCoin.PACKET_HANDLER.send(
                PacketDistributor.PLAYER.with(() -> player),
                new OpenPrayerScreenMessage(game.id(), game.displayName()));
    }

    private record PrayerAttempt(long openedAt, DivineMiniGame game) {
    }

    public static int regenerationDelta(ServerPlayer player) {
        DivineEffect effect = effectFor(player);
        if (effect != DivineEffect.REGENERATION_BONUS && effect != DivineEffect.REGENERATION_MALUS) return 0;
        return effect.value(hasPrayed(player));
    }

    public static void applyModifiers(ServerPlayer player) {
        removeModifier(player, Attributes.MAX_HEALTH, LIFE_ID);
        removeModifier(player, Attributes.ATTACK_DAMAGE, FORCE_ID);
        removeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
        removeModifier(player, ForgeMod.BLOCK_REACH.get(), REACH_ID);
        removeModifier(player, Attributes.ARMOR, RESISTANCE_ID);

        DivineEffect effect = effectFor(player);
        double value = effect.value(hasPrayed(player));
        switch (effect) {
            case LIFE_BONUS, LIFE_MALUS ->
                    addModifier(player, Attributes.MAX_HEALTH, LIFE_ID, "Divine Life", value, AttributeModifier.Operation.ADDITION);
            case FORCE_BONUS, FORCE_MALUS ->
                    addModifier(player, Attributes.ATTACK_DAMAGE, FORCE_ID, "Divine Force", value, AttributeModifier.Operation.ADDITION);
            case SPEED_BONUS, SPEED_MALUS ->
                    addModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, "Divine Speed", value / 100.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
            case REACH_MALUS ->
                    addModifier(player, ForgeMod.BLOCK_REACH.get(), REACH_ID, "Divine Reach", value, AttributeModifier.Operation.ADDITION);
            case RESISTANCE_BONUS ->
                    addModifier(player, Attributes.ARMOR, RESISTANCE_ID, "Divine Resistance", value, AttributeModifier.Operation.ADDITION);
            default -> {
            }
        }

        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private static void removeModifier(ServerPlayer player, Attribute attribute, UUID id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.removeModifier(id);
    }

    private static void addModifier(ServerPlayer player, Attribute attribute, UUID id, String name, double value, AttributeModifier.Operation operation) {
        if (Math.abs(value) < 0.001) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.addTransientModifier(new AttributeModifier(id, name, value, operation));
    }
}
