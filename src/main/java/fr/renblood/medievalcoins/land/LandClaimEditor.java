package fr.renblood.medievalcoins.land;
import java.util.List;
public final class LandClaimEditor {
    public static void apply(List<LandPlot> plots,String dimension,int x,int z,String targetId,String expected,boolean remove){
        var existing=plots.stream().filter(p->p.getDimension().equalsIgnoreCase(dimension)&&p.hasChunk(x,z)).findFirst();String actual=existing.map(LandPlot::getId).orElse("");
        if(!actual.equals(expected))throw new IllegalArgumentException("L’attribution a changé. Actualise le cadastre avant de confirmer.");
        if(remove){if(existing.isEmpty())throw new IllegalArgumentException("Ce chunk est déjà non attribué.");existing.get().removeChunk(x,z);return;}
        var target=plots.stream().filter(p->p.getId().equals(targetId)).findFirst().orElseThrow(()->new IllegalArgumentException("Parcelle cible introuvable. Rouvre la carte après sa création."));
        if(!target.getDimension().isBlank()&&!target.getDimension().equalsIgnoreCase(dimension))throw new IllegalArgumentException("La parcelle appartient à une autre dimension.");
        if(existing.isPresent()&&!actual.equals(target.getId()))throw new IllegalArgumentException("Retire d’abord l’ancienne attribution avant de changer de parcelle.");
        target.setDimension(dimension);target.addChunk(x,z);
    }
}
