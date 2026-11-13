package fr.renblood.medievalcoins.quest;

import fr.renblood.medievalcoins.api.model.NpcModel;
import fr.renblood.medievalcoins.api.model.QuestModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared wording for the journal, NPC objectives and missing-objective list. */
public final class QuestPresentation {
    private QuestPresentation() {}

    public static String instructions(QuestModel quest, QuestModel.Objective objective) {
        if (objective == null) return "";
        String description=clean(objective.description);
        String type=clean(objective.type).toLowerCase(Locale.ROOT).replace("_", "");
        boolean manual=List.of("rp","roleplay","construction","build").contains(type);
        if (!manual) return description;
        if (!description.isBlank() && !List.of("rp","construction","build").contains(description.toLowerCase(Locale.ROOT))) return description;
        List<String> lines=new ArrayList<>();
        if (quest!=null && quest.implementation!=null) {
            String summary=clean(quest.implementation.summary);
            if (!summary.isBlank()) lines.add(summary);
            long manualCount=quest.objectives==null?0:quest.objectives.stream().filter(o->o!=null&&List.of("rp","roleplay","construction","build").contains(clean(o.type).toLowerCase(Locale.ROOT).replace("_",""))).count();
            if (quest.implementation.tasks!=null) for (var task:quest.implementation.tasks) {
                if(task==null)continue;
                boolean linked=objective.id!=null&&objective.id.equals(task.objectiveId);
                if (!linked && (manualCount!=1 || !clean(task.objectiveId).isBlank()))continue;
                String label=clean(task.label);
                if (!label.isBlank()&&!lines.contains(label)&&!label.equals(summary)) lines.add(label);
            }
        }
        // Implementation status/technical notes never validate player progress.
        return lines.isEmpty()?(type.equals("build")||type.equals("construction")?"Réaliser la construction demandée, puis demander sa validation.":"Réaliser l’action demandée, puis demander sa validation."):String.join("\n",lines);
    }

    public static String knownDialogue(NpcModel npc) {
        if(npc==null||npc.dialogue==null)return "Je n’ai rien à ajouter pour le moment.";
        String text=npc.dialogue.stream().filter(s->s!=null&&!s.isBlank()).map(String::trim).collect(java.util.stream.Collectors.joining("\n\n"));
        return text.isBlank()?"Je n’ai rien à ajouter pour le moment.":text;
    }

    private static String clean(String value){return value==null?"":value.trim();}
}
