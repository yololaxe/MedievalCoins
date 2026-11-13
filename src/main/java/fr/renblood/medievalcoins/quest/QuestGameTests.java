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
    private static QuestModel journalQuest(String id,String parent){
        QuestModel q=new QuestModel();q.questId=id;q.parentId=parent;q.name=id;q.category="Main";return q;
    }
    private static fr.renblood.medievalcoins.api.model.PlayerQuestStateModel journalState(String id,String status){
        var state=new fr.renblood.medievalcoins.api.model.PlayerQuestStateModel();state.quest_id=id;state.status=status;return state;
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void journalNeverFallsBackToTheCatalogue(GameTestHelper h){
        var first=journalQuest("p.1",null);var sequel=journalQuest("p.1.1","p.1");
        var catalogue=java.util.List.of(first,sequel);
        h.assertTrue(QuestJournalRules.select(catalogue,java.util.List.of(),java.util.Set.of(),"Main").isEmpty(),"An empty journal remains empty for every rank");
        var active=java.util.List.of(journalState("p.1","IN_PROGRESS"));
        var visible=QuestJournalRules.select(catalogue,active,java.util.Set.of("p.1.1"),"Main");
        h.assertTrue(visible.size()==1&&visible.get(0).quest().questId.equals("p.1"),"Only started quest appears, locked sequel stays hidden");h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void completedQuestsAreOnlyInHistory(GameTestHelper h){
        var first=journalQuest("p.1",null);var sequel=journalQuest("p.1.1","p.1");var catalogue=java.util.List.of(first,sequel);
        var done=java.util.List.of(journalState("p.1","COMPLETED"));
        h.assertTrue(QuestJournalRules.select(catalogue,done,java.util.Set.of("p.1"),"Main").isEmpty(),"Completed quest is not shown as available and unseen sequel stays hidden");
        h.assertTrue(QuestJournalRules.select(catalogue,done,java.util.Set.of(),"Completed").size()==1,"Own completed quest is available in explicit history");
        var discovered=QuestJournalRules.select(catalogue,done,java.util.Set.of("p.1.1"),"Main");
        h.assertTrue(discovered.size()==1&&discovered.get(0).status().equals("AVAILABLE"),"Sequel appears only after discovering it with completed parent");h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void journalDiscoveryPersistsWithoutCompletingQuest(GameTestHelper h){
        var data=new net.minecraft.nbt.CompoundTag();QuestDiscovery.discover(data," P.1 ");QuestDiscovery.discover(data,"p.1");
        var reloaded=data.copy();h.assertTrue(QuestDiscovery.read(reloaded).equals(java.util.Set.of("p.1")),"Discovery is idempotent and survives player NBT save/load");
        h.assertTrue(!data.contains("MedievalCoinsQuestObjectives"),"Discovery never validates an objective");h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void journalPacketContainsOnlyPersonalSelection(GameTestHelper h){
        var q=journalQuest("p.1",null);q.name="Quête découverte";
        var packet=new fr.renblood.medievalcoins.network.QuestJournalSnapshotMessage(42,"Main","",java.util.List.of(new fr.renblood.medievalcoins.network.QuestJournalSnapshotMessage.Entry(q,"AVAILABLE",java.util.List.of(0))));
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{
            fr.renblood.medievalcoins.network.QuestJournalSnapshotMessage.encode(packet,buffer);
            var copy=fr.renblood.medievalcoins.network.QuestJournalSnapshotMessage.decode(buffer);
            h.assertTrue(copy.requestId()==42&&copy.entries().size()==1&&copy.entries().get(0).quest().name.equals(q.name),"Filtered snapshot and accents roundtrip");
        }finally{buffer.release();}h.succeed();
    }
}
