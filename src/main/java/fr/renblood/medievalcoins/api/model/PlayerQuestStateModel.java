package fr.renblood.medievalcoins.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class PlayerQuestStateModel {
    @SerializedName("_id")
    public String id;
    
    public String player_id;
    
    @SerializedName(value = "quest_id", alternate = {"questId", "id"})
    public String quest_id;
    
    public List<String> members;
    public String status;
    public String startedAt;
    public String completedAt;
    
    public transient QuestModel questDetails;
}
