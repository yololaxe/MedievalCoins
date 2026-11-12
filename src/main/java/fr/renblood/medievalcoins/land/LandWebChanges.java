package fr.renblood.medievalcoins.land;

import com.google.gson.JsonObject;
import java.util.List;

/** Called under LandStorage's lock on a freshly read, isolated copy. */
public final class LandWebChanges {
    private LandWebChanges() {}
    private static String text(JsonObject value,String key,int max,boolean required) {
        if(!value.has(key)||!value.get(key).isJsonPrimitive()||!value.get(key).getAsJsonPrimitive().isString())throw new IllegalArgumentException("Champ invalide : "+key);
        String text=value.get(key).getAsString().trim();if(text.length()>max||required&&text.isEmpty())throw new IllegalArgumentException("Champ invalide : "+key);return text;
    }
    public static void apply(List<LandPlot> plots,JsonObject change) {
        String action=text(change,"action",16,true),id=text(change,"plotId",128,true),dimension=text(change,"dimension",128,true);
        if(!id.matches("[A-Za-z0-9_.+\\-]+")||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Identifiant ou dimension invalide.");
        LandPlot plot=plots.stream().filter(p->p.getId().equalsIgnoreCase(id)).findFirst().orElse(null);
        if(action.equals("create")) {
            if(plot!=null)throw new IllegalArgumentException("Cette parcelle existe déjà.");
            var metadata=change.getAsJsonObject("metadata");plot=new LandPlot(id,text(metadata,"name",128,true));plot.setDimension(dimension);plots.add(plot);
        } else if(plot==null)throw new IllegalArgumentException("Parcelle introuvable.");
        if(!dimension.equals(plot.getDimension())&&!plot.getDimension().isBlank())throw new IllegalArgumentException("La dimension de la parcelle a changé.");
        if(action.equals("create")||action.equals("update")) {
            JsonObject metadata=change.getAsJsonObject("metadata");String ownerType=text(metadata,"ownerType",32,true),color=text(metadata,"color",7,true);
            if(!LandOwnerType.isValid(ownerType)||!color.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Type de propriétaire ou couleur invalide.");
            plot.setName(text(metadata,"name",128,true));plot.setOwner(LandOwnerType.fromJson(ownerType),text(metadata,"owner",128,false));
            plot.setCity(text(metadata,"city",128,false));plot.setDescription(text(metadata,"description",500,false));plot.setColor(color);
        }
        if(!List.of("create","update","assign","unclaim").contains(action))throw new IllegalArgumentException("Action inconnue.");
        var chunks=change.getAsJsonArray("chunks");if(chunks==null||chunks.size()>256)throw new IllegalArgumentException("Sélection trop grande.");
        if((action.equals("assign")||action.equals("unclaim"))&&chunks.isEmpty())throw new IllegalArgumentException("Sélection vide.");
        for(var cell:chunks) {
            var chunk=cell.getAsJsonObject();int x=chunk.get("x").getAsInt(),z=chunk.get("z").getAsInt();
            if(Math.abs((long)x)>LandDiscoveryData.LIMIT||Math.abs((long)z)>LandDiscoveryData.LIMIT)throw new IllegalArgumentException("Chunk hors du monde.");
            LandPlot assigned=plots.stream().filter(p->p.getDimension().equals(dimension)&&p.hasChunk(x,z)).findFirst().orElse(null);
            if(action.equals("unclaim")) {
                if(assigned==null||!assigned.getId().equalsIgnoreCase(id))throw new IllegalArgumentException("Un chunk n’appartient plus à cette parcelle.");
                plot.removeChunk(x,z);
            } else if(!action.equals("update")) {
                if(assigned!=null&&!assigned.getId().equalsIgnoreCase(id))throw new IllegalArgumentException("Un chunk appartient déjà à une autre parcelle.");
                if(plot.getDimension().isBlank())plot.setDimension(dimension);plot.addChunk(x,z);
            }
        }
    }
}
