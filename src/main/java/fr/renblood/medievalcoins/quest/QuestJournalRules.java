package fr.renblood.medievalcoins.quest;

import fr.renblood.medievalcoins.api.model.QuestModel;
import fr.renblood.medievalcoins.api.model.PlayerQuestStateModel;
import java.util.*;

/** Personal journal policy: no rank exception and no catalogue fallback. */
public final class QuestJournalRules {
    private QuestJournalRules(){}
    public record Visible(QuestModel quest,String status){}
    public static String key(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT);}
    public static boolean eligible(QuestModel quest,Set<String> completed){
        return (quest.parentId==null||quest.parentId.isBlank()||completed.contains(key(quest.parentId)))
            &&(quest.prerequisitesAll==null||quest.prerequisitesAll.stream().allMatch(id->completed.contains(key(id))))
            &&(quest.prerequisitesAny==null||quest.prerequisitesAny.isEmpty()||quest.prerequisitesAny.stream().anyMatch(id->completed.contains(key(id))));
    }
    public static List<Visible> select(List<QuestModel> catalogue,List<PlayerQuestStateModel> states,Set<String> discovered,String category){
        Map<String,String> statuses=new HashMap<>();Set<String> completed=new HashSet<>();Set<String> seen=new HashSet<>();
        for(String id:discovered)seen.add(key(id));
        for(var state:states){
            if(state==null||state.quest_id==null)continue;
            String id=key(state.quest_id),status=state.status==null?"":state.status.toUpperCase(Locale.ROOT);
            if(!"COMPLETED".equals(statuses.get(id)))statuses.put(id,status);
            if("COMPLETED".equals(status))completed.add(id);
        }
        List<Visible> result=new ArrayList<>();
        for(QuestModel quest:catalogue){
            if(quest==null||quest.questId==null)continue;
            String id=key(quest.questId),status=statuses.getOrDefault(id,"");
            if("Completed".equals(category)){if("COMPLETED".equals(status))result.add(new Visible(quest,status));continue;}
            if(!category.equals(quest.category)||"COMPLETED".equals(status)||"FAILED".equals(status))continue;
            if("IN_PROGRESS".equals(status)){result.add(new Visible(quest,status));continue;}
            if(seen.contains(id)&&eligible(quest,completed))result.add(new Visible(quest,"AVAILABLE"));
        }
        return List.copyOf(result);
    }
}
