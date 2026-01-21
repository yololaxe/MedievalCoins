package fr.renblood.medievalcoins.client.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerModel {
    // Configuration Gson personnalisée pour gérer le cas [] -> {} pour real_charact
    // Rendu public pour être utilisé par ApiClient
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(new TypeToken<Map<String, RealCharactEntry>>(){}.getType(), new RealCharactDeserializer())
            .create();

    // MongoDB ObjectId (si utilisé par votre backend, sinon ignoré)
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
    public String rank;
    public double money;
    public String divin;

    // Attributs physiques
    public int life;
    public int strength;
    public int speed;
    public int reach;
    public int resistance;
    public int place;
    public int haste;
    public int regeneration;

    // Traits et Actions
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

    // Compétences diverses
    public int dodge;
    public int discretion;
    public int charisma;
    public int rethoric;
    public int mana;
    public int negotiation;
    public int influence;
    public int skill;

    // Discord & Patreon
    public String discord_id;
    public String discord_username;
    public String discord_discriminator;
    public String discord_avatar;
    public int patreon;

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

    // Real Characteristics (Bonus réels)
    public static class RealCharactEntry {
        public int count;
        public String type;
    }
    public Map<String, RealCharactEntry> real_charact;

    @Override
    public String toString() {
        return String.format("%s (%s) – Money: %.2f – Life: %d",
                pseudo_minecraft, rank, money, life);
    }

    /** Désérialise un JSON en PlayerModel */
    public static PlayerModel fromJson(String json) {
        // Si le JSON commence par [, c'est une liste, on prend le premier élément si possible
        if (json.trim().startsWith("[")) {
            try {
                List<PlayerModel> list = GSON.fromJson(json, new TypeToken<List<PlayerModel>>(){}.getType());
                if (list != null && !list.isEmpty()) {
                    return list.get(0);
                }
                return null; // ou throw exception
            } catch (Exception e) {
                // Fallback si ce n'est pas une liste de PlayerModel
            }
        }
        return GSON.fromJson(json, PlayerModel.class);
    }

    // Deserializer personnalisé pour gérer le cas où real_charact est un tableau vide [] au lieu d'un objet {}
    public static class RealCharactDeserializer implements JsonDeserializer<Map<String, RealCharactEntry>> {
        @Override
        public Map<String, RealCharactEntry> deserialize(JsonElement json, Type typeOfT, com.google.gson.JsonDeserializationContext context) {
            if (json.isJsonArray()) {
                // Si c'est un tableau (probablement vide), on retourne une map vide
                return new HashMap<>();
            } else if (json.isJsonObject()) {
                Map<String, RealCharactEntry> map = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                    JsonElement value = entry.getValue();
                    // Protection supplémentaire : si la valeur est un tableau (vide ou non), on l'ignore
                    // car RealCharactEntry doit être un objet
                    if (value.isJsonObject()) {
                        map.put(entry.getKey(), context.deserialize(value, RealCharactEntry.class));
                    }
                }
                return map;
            }
            return new HashMap<>(); // Retourne une map vide par défaut si ni objet ni tableau
        }
    }
}
