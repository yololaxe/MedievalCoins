package fr.renblood.medievalcoins.api.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class QuestModel {
    public String category;

    @SerializedName(value = "questId", alternate = {"quest_id", "id", "_id"})
    public String questId;

    @SerializedName(value = "parentId", alternate = {"parent_id", "parent"})
    public String parentId;

    public String name;
    
    @SerializedName(value = "prerequisitesAll", alternate = {"prerequisites_all"})
    public List<String> prerequisitesAll;

    @SerializedName(value = "prerequisitesAny", alternate = {"prerequisites_any"})
    public List<String> prerequisitesAny;
    
    public String npc;

    @SerializedName(value = "npcId", alternate = {"npc_id"})
    public String npcId;

    @SerializedName(value = "npcName", alternate = {"npc_name"})
    public String npcName;

    @SerializedName(value = "startNpcId", alternate = {"start_npc_id"})
    public String startNpcId;

    @SerializedName(value = "completionNpcId", alternate = {"completion_npc_id"})
    public String completionNpcId;

    public String type;
    
    public Map<String, String> description;
    
    public List<Objective> objectives;
    
    public XpReward xp;
    public int money;
    
    public List<Reward> rewards;
    
    public Map<String, String> beginText;
    public Map<String, String> endText;

    public static class Objective {
        public String type;
        public List<ItemRequirement> items;
        public String coord;
        public JsonElement target;
        @SerializedName(value = "npcId", alternate = {"npc_id"})
        public String npcId;
        public int count;
        public String description;

        public String getTargetNpcId() {
            if (target != null && target.isJsonObject()) {
                JsonObject object = target.getAsJsonObject();
                JsonElement value = object.get("npcId");
                if (value == null) value = object.get("npc_id");
                if (value != null && value.isJsonPrimitive()) return value.getAsString();
            }
            if (npcId != null && !npcId.isBlank()) return npcId;
            return target != null && target.isJsonPrimitive() ? target.getAsString() : null;
        }
    }

    public String getStartNpcId() {
        return startNpcId != null && !startNpcId.isBlank() ? startNpcId : npcId;
    }

    public String getCompletionNpcId() {
        return completionNpcId != null && !completionNpcId.isBlank() ? completionNpcId : npcId;
    }
    
    public static class ItemRequirement {
        @SerializedName(value = "itemId", alternate = {"item_id", "item"})
        public String itemId;
        public int count;
    }

    public static class Reward {
        @SerializedName(value = "itemId", alternate = {"item_id", "item"})
        public String itemId;
        public int count;
    }
    
    public static class XpReward {
        public String job;
        public int amount;
    }
    
    public String getDescription() {
        if (description == null) return "";
        return description.getOrDefault("fr", description.getOrDefault("en", ""));
    }
}
