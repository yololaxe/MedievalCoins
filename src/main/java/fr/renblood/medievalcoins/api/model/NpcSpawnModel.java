package fr.renblood.medievalcoins.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class NpcSpawnModel {
    public static final String SPAWN_RULE_STATIC = "STATIC";
    public static final String SPAWN_RULE_TIMER = "TIMER";
    public static final String SPAWN_RULE_ROAD = "ROAD";
    public static final String SPAWN_RULE_ADMIN = "ADMIN";

    @SerializedName(value = "spawn_id", alternate = {"spawnId"})
    public String spawnId;
    
    @SerializedName(value = "npc_id", alternate = {"npcId"})
    public String npcId;
    
    public String world;
    public String dimension;
    
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    
    @SerializedName(value = "spawn_rule", alternate = {"spawnRule"})
    public String spawnRule;
    
    public boolean active;
    
    public Map<String, Object> meta;

    @SerializedName(value = "npc_name", alternate = {"npcName", "name"})
    public String npcName;

    @SerializedName(value = "npc_type", alternate = {"npcType", "type"})
    public String npcType;

    @SerializedName(value = "npc_skin", alternate = {"npcSkin", "skin", "texture"})
    public String npcSkin;

    public List<String> dialogue;

    @SerializedName(value = "quest_ids", alternate = {"questIds"})
    public List<String> questIds;

    public boolean hasStableId() {
        return spawnId != null && !spawnId.trim().isEmpty();
    }
}
