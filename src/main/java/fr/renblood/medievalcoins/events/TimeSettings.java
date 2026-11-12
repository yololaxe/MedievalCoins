package fr.renblood.medievalcoins.events;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-specific clock settings; no credentials and no client-side configuration. */
public final class TimeSettings extends SavedData {
    double multiplier = 3.0, sleepPercentage = 0.8;
    int sleepSeconds = 5;
    public static TimeSettings get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TimeSettings::load, TimeSettings::new, "medieval_coins_time");
    }
    public static TimeSettings load(CompoundTag tag) {
        TimeSettings settings = new TimeSettings();
        double mult = tag.getDouble("multiplier"), sleep = tag.getDouble("sleepPercentage");
        if (Double.isFinite(mult) && mult >= 0.1 && mult <= 100) settings.multiplier = mult;
        if (tag.contains("sleepPercentage") && Double.isFinite(sleep) && sleep >= 0 && sleep <= 1) settings.sleepPercentage = sleep;
        if (tag.contains("sleepSeconds")) settings.sleepSeconds = Math.max(0, Math.min(600, tag.getInt("sleepSeconds")));
        return settings;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putDouble("multiplier", multiplier); tag.putDouble("sleepPercentage", sleepPercentage); tag.putInt("sleepSeconds", sleepSeconds);
        return tag;
    }
    public static void capture(MinecraftServer server) {
        TimeSettings settings = get(server);
        settings.multiplier = TimeManager.dayLengthMultiplier;
        settings.sleepPercentage = TimeManager.sleepPercentage;
        settings.sleepSeconds = TimeManager.sleepDurationSeconds;
        settings.setDirty();
    }
}
