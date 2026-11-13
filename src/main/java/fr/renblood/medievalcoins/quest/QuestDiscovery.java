package fr.renblood.medievalcoins.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import java.util.HashSet;
import java.util.Set;

/** Discovered offers are persistent player data, independent of quest progress. */
public final class QuestDiscovery {
    private static final String TAG="RenbloodDiscoveredQuests";
    private QuestDiscovery(){}
    public static Set<String> read(CompoundTag playerData){return Set.copyOf(playerData.getCompound(Player.PERSISTED_NBT_TAG).getCompound(TAG).getAllKeys());}
    public static void discover(CompoundTag playerData,String id){
        if(id==null||id.isBlank())return;
        CompoundTag persisted=playerData.getCompound(Player.PERSISTED_NBT_TAG),seen=persisted.getCompound(TAG);
        if(seen.size()>=4096||seen.contains(QuestJournalRules.key(id)))return;
        seen.putBoolean(QuestJournalRules.key(id),true);persisted.put(TAG,seen);playerData.put(Player.PERSISTED_NBT_TAG,persisted);
    }
}
