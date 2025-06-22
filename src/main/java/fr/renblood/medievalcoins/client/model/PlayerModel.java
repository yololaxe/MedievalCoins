package fr.renblood.medievalcoins.client.model;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public class PlayerModel {
    private static final Gson GSON = new Gson();

    // MongoDB ObjectId
    public static class Oid {
        @SerializedName("$oid")
        public String oid;
    }
    public Oid _id;

    public String id;
    public String id_minecraft;
    public String pseudo_minecraft;
    public String name;
    public String surname;
    public String description;
    public String rank;
    public double money;
    public String divin;

    // Caractéristiques physiques
    public int life;
    public int strength;
    public int speed;
    public int reach;
    public int resistance;
    public int place;
    public int haste;
    public int regeneration;

    // Compétences
    public int dodge;
    public int discretion;
    public int charisma;
    public int rethoric;
    public int mana;
    public int negotiation;
    public int influence;
    public int skill;

    // Expériences
    public static class JobExperience {
        public long xp;
        public String choose_lvl_10;
        public List<Boolean> progression;
        public int level;
    }
    public static class Experiences {
        public Map<String, JobExperience> jobs;
    }
    public Experiences experiences;

    // Traits
    public static class Trait {
        public int id;
        @SerializedName("Name")
        public String name;
        public Map<String, Integer> Bonus;
        @Override
        public String toString() {
            return name + Bonus;
        }
    }
    public List<Trait> traits;

    // Actions
    public static class Action {
        public int id;
        @SerializedName("Name")
        public String name;
        @SerializedName("Description")
        public String description;
        @SerializedName("Mana")
        public int mana;
        @SerializedName("Chance")
        public int chance;
        @Override
        public String toString() {
            return name + "(" + chance + "%)";
        }
    }
    public List<Action> actions;

    @Override
    public String toString() {
        return String.format("%s (%s) – Jobs: %s – Traits: %s – Actions: %s",
                pseudo_minecraft, rank, experiences.jobs.keySet(), traits, actions);
    }

    /** Désérialise un JSON en PlayerModel */
    public static PlayerModel fromJson(String json) {
        return GSON.fromJson(json, PlayerModel.class);
    }
}
