package fr.renblood.medievalcoins.api.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Map;

public class PlayerModel {
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(JobExperience.class, new JobExperienceAdapter())
            .create();

    @SerializedName(value = "id", alternate = {"_id", "player_id", "playerId"})
    public String id;

    @SerializedName("id_minecraft")
    public String id_minecraft;

    @SerializedName("pseudo_minecraft")
    public String pseudo_minecraft;

    public int money;
    public String rank;

    // Stats RPG
    public int life;
    public int strength;
    public int speed;
    public int reach;
    public int resistance;
    public int regeneration;
    public int haste;
    public int place;
    public int skill;

    public Experiences experiences;

    public static class Experiences {
        public Map<String, JobExperience> jobs;
    }

    public static class JobExperience {
        public long xp;
        public int level;
        public java.util.List<Boolean> progression;
        public String choose_lvl_10;
        public java.util.List<String> inter_choice;
        public java.util.List<String> mastery;
    }

    // Adapter pour gérer le cas où un job est vide/null ou mal formaté
    public static class JobExperienceAdapter extends TypeAdapter<JobExperience> {
        @Override
        public void write(JsonWriter out, JobExperience value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.beginObject();
            out.name("xp").value(value.xp);
            out.name("level").value(value.level);
            
            if (value.progression != null) {
                out.name("progression");
                out.beginArray();
                for (Boolean b : value.progression) {
                    out.value(b);
                }
                out.endArray();
            }
            
            if (value.choose_lvl_10 != null) {
                out.name("choose_lvl_10").value(value.choose_lvl_10);
            }
            
            if (value.inter_choice != null) {
                out.name("inter_choice");
                out.beginArray();
                for (String s : value.inter_choice) {
                    out.value(s);
                }
                out.endArray();
            }

            if (value.mastery != null) {
                out.name("mastery");
                out.beginArray();
                for (String s : value.mastery) {
                    out.value(s);
                }
                out.endArray();
            }

            out.endObject();
        }

        @Override
        public JobExperience read(JsonReader in) throws IOException {
            JobExperience job = new JobExperience();
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "xp": job.xp = in.nextLong(); break;
                    case "level": job.level = in.nextInt(); break;
                    case "progression": 
                        job.progression = new java.util.ArrayList<>();
                        in.beginArray();
                        while (in.hasNext()) job.progression.add(in.nextBoolean());
                        in.endArray();
                        break;
                    case "choose_lvl_10":
                        job.choose_lvl_10 = in.nextString();
                        break;
                    case "inter_choice":
                        job.inter_choice = new java.util.ArrayList<>();
                        in.beginArray();
                        while (in.hasNext()) job.inter_choice.add(in.nextString());
                        in.endArray();
                        break;
                    case "mastery":
                        job.mastery = new java.util.ArrayList<>();
                        in.beginArray();
                        while (in.hasNext()) job.mastery.add(in.nextString());
                        in.endArray();
                        break;
                    default: in.skipValue(); break;
                }
            }
            in.endObject();
            return job;
        }
    }

    public static PlayerModel fromJson(String json) {
        return GSON.fromJson(json, PlayerModel.class);
    }
}
