package fr.renblood.medievalcoins.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class NpcModel {
    @SerializedName(value = "npc_id", alternate = {"npcId"})
    public String npcId;
    
    public String name;
    public String type;
    
    @SerializedName(value = "skin", alternate = {"npc_skin", "npcSkin", "texture"})
    public String texture;
    
    public List<String> dialogue;
    public List<String> tags;
    public boolean enabled;
    
    @SerializedName("shop_id")
    public String shopId;
    public String currency;
    @SerializedName("open_message")
    public String openMessage;
    @SerializedName("trade_category")
    public String tradeCategory;
    
    @SerializedName("quest_giver")
    public boolean questGiver;
    @SerializedName("quest_validator")
    public boolean questValidator;
    @SerializedName("quest_ids")
    public List<String> questIds;
    @SerializedName("dialogue_by_state")
    public Map<String, String> dialogueByState;
    
    @SerializedName("idle_behavior")
    public String idleBehavior;
    
    @SerializedName("ambient_lines")
    public List<String> ambientLines;
}
