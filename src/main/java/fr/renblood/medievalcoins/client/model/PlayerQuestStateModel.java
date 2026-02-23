package fr.renblood.medievalcoins.client.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class PlayerQuestStateModel {
    @SerializedName("_id")
    public String id;
    
    public String player_id;
    
    // Mappe le champ JSON "questId" (ou "quest_id") vers cette variable
    @SerializedName(value = "quest_id", alternate = {"questId", "id"})
    public String quest_id;
    
    public List<String> members;
    
    public String status; // LOCKED, AVAILABLE, IN_PROGRESS, COMPLETED
    
    public String startedAt;
    public String completedAt;
    
    // Champ transient pour stocker les détails de la quête une fois récupérés
    public transient QuestModel questDetails;
}
