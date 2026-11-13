package fr.renblood.medievalcoins.quest;
import com.google.gson.Gson;
import fr.renblood.medievalcoins.api.model.QuestModel;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
@GameTestHolder("medieval_coins")
@PrefixGameTestTemplate(false)
public final class QuestGameTests {
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void editorItemsAndLegacyItems(GameTestHelper h){
        Gson gson=new Gson();
        var nested=gson.fromJson("{\"type\":\"ITEM\",\"target\":{\"items\":[{\"itemId\":\"minecraft:bread\",\"count\":12},{\"item_id\":\"minecraft:torch\",\"quantity\":4}]}}",QuestModel.Objective.class);
        var rows=nested.getItemRequirements();h.assertTrue(rows.size()==2,"Editor target.items must not disappear");h.assertTrue(rows.get(0).itemId.equals("minecraft:bread")&&rows.get(0).count==12,"Requested bread count preserved");h.assertTrue(rows.get(1).count==4,"Alternate item count supported");
        var flat=gson.fromJson("{\"type\":\"ITEM\",\"items\":[{\"itemId\":\"minecraft:bread\",\"count\":7}]}",QuestModel.Objective.class);h.assertTrue(flat.getItemRequirements().get(0).count==7,"Historical flat data still loads");
        var primitive=gson.fromJson("{\"type\":\"ITEM\",\"target\":\"minecraft:bread\",\"count\":5}",QuestModel.Objective.class);h.assertTrue(primitive.getItemRequirements().get(0).count==5,"Historical target string still loads");h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void inventoryValidationAndDelivery(GameTestHelper h)throws Exception{
        var player=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"quest-fixture"));
        var objective=new Gson().fromJson("{\"type\":\"ITEM\",\"target\":{\"items\":[{\"itemId\":\"minecraft:bread\",\"count\":5}]}}",QuestModel.Objective.class);
        var type=fr.renblood.medievalcoins.events.QuestObjectiveHandler.class;
        var check=type.getDeclaredMethod("hasRequiredItems",net.minecraft.server.level.ServerPlayer.class,java.util.List.class);check.setAccessible(true);
        var remove=type.getDeclaredMethod("removeRequiredItems",net.minecraft.server.level.ServerPlayer.class,java.util.List.class);remove.setAccessible(true);
        var rows=objective.getItemRequirements();player.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD,4));h.assertTrue(!(Boolean)check.invoke(null,player,rows),"Insufficient inventory must fail");
        player.getInventory().setItem(1,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD,2));h.assertTrue((Boolean)check.invoke(null,player,rows),"Multiple stacks satisfy the same requirement");remove.invoke(null,player,rows);h.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.BREAD)==1,"Delivery consumes exactly the requested amount");h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void duplicateItemRequirementsAreAdded(GameTestHelper h){
        var objective=new Gson().fromJson("{\"type\":\"ITEM\",\"target\":{\"items\":[{\"itemId\":\"minecraft:bread\",\"count\":3},{\"itemId\":\"minecraft:bread\",\"count\":2}]}}",QuestModel.Objective.class);
        h.assertTrue(objective.getItemRequirements().size()==1&&objective.getItemRequirements().get(0).count==5,"Repeated rows require their total quantity, not the largest row");h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void musicRegistry(GameTestHelper h){
        h.assertTrue(fr.renblood.medievalcoins.music.MusicCatalog.TITLES.length==16,"Sixteen indexed tracks");
        for(int n=1;n<=16;n++)h.assertTrue(net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.containsKey(fr.renblood.medievalcoins.music.MusicCatalog.id(n)),"Every track is registered");h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void roleplayUsesAuthoredInstructions(GameTestHelper h){
        var q=new Gson().fromJson("{\"objectives\":[{\"id\":\"o1\",\"type\":\"RP\"}],\"implementation\":{\"status\":\"DONE\",\"summary\":\"Ramener Elys Drakon auprès de DON\",\"tasks\":[]}}",QuestModel.class);
        h.assertTrue(QuestPresentation.instructions(q,q.objectives.get(0)).equals("Ramener Elys Drakon auprès de DON"),"RP displays the editor's implementation summary");
        q.objectives.get(0).description="Une consigne spécifique";
        h.assertTrue(QuestPresentation.instructions(q,q.objectives.get(0)).equals("Une consigne spécifique"),"Explicit objective wording wins");h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void implementationTasksAreInstructionsNotProgress(GameTestHelper h){
        var q=new Gson().fromJson("{\"objectives\":[{\"type\":\"CONSTRUCTION\"}],\"implementation\":{\"tasks\":[{\"label\":\"Construire le pont\",\"status\":\"DONE\",\"notes\":\"secret technique\"}]}}",QuestModel.class);
        h.assertTrue(QuestPresentation.instructions(q,q.objectives.get(0)).equals("Construire le pont"),"Only authored labels are shown, without technical notes/status");h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void ordinaryDialogueDoesNotUseQuestEnding(GameTestHelper h){
        var npc=new fr.renblood.medievalcoins.api.model.NpcModel();npc.dialogue=java.util.List.of("Bonjour voyageur.","Bienvenue à Shaleton.");
        h.assertTrue(QuestPresentation.knownDialogue(npc).contains("Bonjour voyageur."),"Known dialogue is displayed");
        h.assertTrue(!QuestPresentation.knownDialogue(null).isBlank(),"Missing dialogue has a neutral fallback, never quest ending");h.succeed();
    }
}
