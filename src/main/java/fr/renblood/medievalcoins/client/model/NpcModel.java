package fr.renblood.medievalcoins.client.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class NpcModel {
    @SerializedName("npc_id")
    public String npcId; // Optionnel à la création
    
    public String name;
    
    // DECO, SHOPKEEPER, QUEST
    public String type;
    
    @SerializedName("skin")
    public String texture;
    
    public List<String> dialogue;
    
    public List<String> tags;
    
    public boolean enabled;
    
    // Champs spécifiques Shopkeeper
    @SerializedName("shop_id")
    public String shopId;
    public String currency;
    @SerializedName("open_message")
    public String openMessage;
    @SerializedName("trade_category")
    public String tradeCategory;
    
    // Champs spécifiques Quest
    @SerializedName(value = "quest_links", alternate = {"questLinks"})
    public List<fr.renblood.medievalcoins.api.model.QuestLinkModel> questLinks;
    public Map<String, Object> implementation;
    @SerializedName("dialogue_by_state")
    public Map<String, String> dialogueByState;
    
    // Champs spécifiques Deco
    @SerializedName("idle_behavior")
    public String idleBehavior;
}
