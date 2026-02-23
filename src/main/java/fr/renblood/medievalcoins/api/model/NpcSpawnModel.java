package fr.renblood.medievalcoins.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class NpcSpawnModel {
    @SerializedName("spawn_id")
    public String spawnId;
    
    @SerializedName("npc_id")
    public String npcId;
    
    public String world;
    public String dimension;
    
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    
    @SerializedName("spawn_rule")
    public String spawnRule;
    
    public boolean active;
    
    public Map<String, Object> meta;
}
