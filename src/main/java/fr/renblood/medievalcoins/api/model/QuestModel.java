package fr.renblood.medievalcoins.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class QuestModel {
    public String category;
    public String questId;
    public String parentId;
    public String name;
    
    public List<String> prerequisitesAll;
    public List<String> prerequisitesAny;
    
    public String npc;
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
        public String target;
        public int count;
        public String description;
    }
    
    public static class ItemRequirement {
        public String itemId;
        public int count;
    }

    public static class Reward {
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
