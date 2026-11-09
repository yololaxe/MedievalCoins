package fr.renblood.medievalcoins.bank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import fr.renblood.medievalcoins.mixin.BankPlayerAccess;
import java.util.UUID;

/** Calls Minecraft's standard save, then verifies its result in read-only mode. */
public final class BankPlayerSnapshot {
    private BankPlayerSnapshot() {}
    public static void save(ServerPlayer player) throws Exception {
        long economyStarted=System.nanoTime();try{
        if(!player.getServer().isSameThread())throw new IllegalStateException("Player snapshot must run on server thread");
        player.getPersistentData().putString("renblood_bank_snapshot",UUID.randomUUID().toString());
        CompoundTag expected=player.saveWithoutId(new CompoundTag());
        BankPlayerAccess access=(BankPlayerAccess)player.getServer().getPlayerList();
        access.bank$save(player);
        java.io.File file=new java.io.File(access.bank$storage().getPlayerDataFolder(),player.getStringUUID()+".dat");
        CompoundTag saved=NbtIo.readCompressed(file);
        if(!saved.getList("Inventory",10).equals(expected.getList("Inventory",10)) || !saved.getCompound("ForgeData").equals(expected.getCompound("ForgeData")))
            throw new java.io.IOException("Minecraft player save could not be verified");
    
        }finally{EconomyTimings.save(economyStarted);}
}
}
