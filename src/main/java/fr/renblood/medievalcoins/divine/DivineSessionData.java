package fr.renblood.medievalcoins.divine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DivineSessionData extends SavedData {
    public static final String DATA_NAME = "medieval_coins_divine_session";

    private int sessionNumber;
    private int[] effectsBySign = defaultEffects();
    private final Set<UUID> prayedPlayers = new HashSet<>();

    public static DivineSessionData load(CompoundTag tag) {
        DivineSessionData data = new DivineSessionData();
        data.sessionNumber = tag.getInt("SessionNumber");

        int[] savedEffects = tag.getIntArray("EffectsBySign");
        if (savedEffects.length == DivineSign.values().length) {
            data.effectsBySign = savedEffects;
        }

        ListTag prayed = tag.getList("PrayedPlayers", Tag.TAG_STRING);
        for (Tag entry : prayed) {
            try {
                data.prayedPlayers.add(UUID.fromString(entry.getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("SessionNumber", sessionNumber);
        tag.putIntArray("EffectsBySign", effectsBySign);

        ListTag prayed = new ListTag();
        for (UUID playerId : prayedPlayers) {
            prayed.add(StringTag.valueOf(playerId.toString()));
        }
        tag.put("PrayedPlayers", prayed);
        return tag;
    }

    public int sessionNumber() {
        return sessionNumber;
    }

    public DivineEffect effectFor(DivineSign sign) {
        int ordinal = effectsBySign[sign.ordinal()];
        DivineEffect[] effects = DivineEffect.values();
        return ordinal >= 0 && ordinal < effects.length ? effects[ordinal] : effects[sign.ordinal()];
    }

    public boolean hasPrayed(UUID playerId) {
        return prayedPlayers.contains(playerId);
    }

    public boolean markPrayed(UUID playerId) {
        boolean changed = prayedPlayers.add(playerId);
        if (changed) setDirty();
        return changed;
    }

    public void startNextSession(int[] shuffledEffects) {
        sessionNumber++;
        effectsBySign = Arrays.copyOf(shuffledEffects, shuffledEffects.length);
        prayedPlayers.clear();
        setDirty();
    }

    private static int[] defaultEffects() {
        int[] effects = new int[DivineSign.values().length];
        for (int i = 0; i < effects.length; i++) effects[i] = i;
        return effects;
    }
}
