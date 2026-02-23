package fr.renblood.medievalcoins.client.model;

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
        public String type; // ITEM, LOCATION, TALK, KILL...
        
        // Pour type ITEM
        public List<ItemRequirement> items;
        
        // Pour type LOCATION
        public String coord;
        
        // Pour type TALK / KILL (anciens champs, à garder au cas où)
        public String target;
        public int count;
        
        public String description; // Optionnel, override l'affichage auto
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
