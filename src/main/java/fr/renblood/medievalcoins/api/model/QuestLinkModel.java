package fr.renblood.medievalcoins.api.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class QuestLinkModel {
    @SerializedName(value = "questId", alternate = {"quest_id"})
    public String questId;
    public String name;
    public List<String> roles;

    public boolean hasRole(String role) {
        return role != null && roles != null && roles.stream().anyMatch(role::equalsIgnoreCase);
    }
}
