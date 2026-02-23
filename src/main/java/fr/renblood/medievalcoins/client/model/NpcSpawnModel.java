package fr.renblood.medievalcoins.client.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class NpcSpawnModel {
    @SerializedName("spawn_id")
    public String spawnId; // Optionnel à la création
    
    @SerializedName("npc_id")
    public String npcId;
    
    public String world;
    public String dimension; // Optionnel, souvent "minecraft:overworld"
    
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    
    // STATIC, TIMER, ROAD, ADMIN
    @SerializedName("spawn_rule")
    public String spawnRule;
    
    public boolean active;
    
    public Map<String, Object> meta;
}
