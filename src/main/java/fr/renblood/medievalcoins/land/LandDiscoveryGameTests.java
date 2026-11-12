package fr.renblood.medievalcoins.land;

import com.google.gson.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder("medieval_coins")
@PrefixGameTestTemplate(false)
public final class LandDiscoveryGameTests {
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void knowledgeHasEightChunkRadiusAndSurvivesRestart(GameTestHelper h) {
        var data=new LandDiscoveryData();UUID a=UUID.randomUUID(),b=UUID.randomUUID();data.visit(a,"minecraft:overworld",-45,-82);
        h.assertTrue(data.known(a,"minecraft:overworld",-53,-90)&&data.known(a,"minecraft:overworld",-37,-74),"All four corners of the 17 by 17 square are discovered");
        h.assertTrue(!data.known(a,"minecraft:overworld",-54,-90)&&!data.known(b,"minecraft:overworld",-45,-82)&&!data.known(a,"minecraft:the_nether",-45,-82),"Knowledge is bounded, personal and dimension specific");
        var restored=LandDiscoveryData.load(data.save(new CompoundTag()));
        h.assertTrue(restored.worldId().equals(data.worldId())&&restored.known(a,"minecraft:overworld",-53,-90)&&restored.pending().size()==17,"World identity, discoveries and unsent deltas survive NBT persistence");h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void syncAcknowledgementCannotLoseNewExploration(GameTestHelper h) {
        var data=new LandDiscoveryData();UUID player=UUID.randomUUID();data.visit(player,"minecraft:overworld",0,0);var batch=data.pending();
        data.visit(player,"minecraft:overworld",1,0);data.acknowledge(batch);
        h.assertTrue(!data.pending().isEmpty()&&data.known(player,"minecraft:overworld",9,0),"Moving while HTTP is in flight keeps newly extended intervals pending");
        data.acknowledge(data.pending());h.assertTrue(data.pending().isEmpty(),"Successful current batch is acknowledged");
        data.visit(player,"minecraft:overworld",1,0);h.assertTrue(data.pending().isEmpty(),"Standing still or revisiting adds no work");h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void largeDiscoveryRowsArePublishedInBoundedPages(GameTestHelper h) {
        var data=new LandDiscoveryData();UUID player=UUID.randomUUID();
        for(int i=0;i<520;i++)data.visit(player,"minecraft:overworld",i*20,0);
        var first=data.pending();h.assertTrue(first.size()==17&&first.get(0).spans().size()==512,"First page is bounded");
        data.acknowledge(first);var second=data.pending();h.assertTrue(second.size()==17&&second.get(0).spans().size()==8,"Remaining disjoint islands are not dropped");
        data.acknowledge(second);h.assertTrue(data.pending().isEmpty(),"All pages can be acknowledged");h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void websiteEditsAreAtomicAndIdempotent(GameTestHelper h)throws Exception {
        var file=LandStorage.getFile();h.assertTrue(file.toAbsolutePath().toString().contains("run-quest-tests"),"Disposable configuration only");var bytes=java.nio.file.Files.readAllBytes(file);var registry=LandRegistry.getPlots();
        try {
            LandStorage.save(new ArrayList<>());String hash=LandStorage.webSnapshot().get("hash").getAsString();
            JsonObject command=JsonParser.parseString("{\"change\":{\"action\":\"create\",\"plotId\":\"web-test\",\"dimension\":\"minecraft:overworld\",\"metadata\":{\"name\":\"Recette\",\"owner\":\"Test\",\"ownerType\":\"PLAYER\",\"color\":\"#AABBCC\",\"city\":\"Shaleton\",\"description\":\"\"},\"chunks\":[{\"x\":-45,\"z\":-82}]}}").getAsJsonObject();
            command.addProperty("id",UUID.randomUUID().toString());command.addProperty("baseHash",hash);
            h.assertTrue(LandStorage.applyWeb(command).get("state").getAsString().equals("APPLIED"),"Valid command applies on Minecraft");
            byte[] once=java.nio.file.Files.readAllBytes(file);
            h.assertTrue(LandStorage.applyWeb(command).get("state").getAsString().equals("APPLIED")&&Arrays.equals(once,java.nio.file.Files.readAllBytes(file)),"Retry after lost acknowledgement does not write or duplicate claims");
            command.addProperty("id",UUID.randomUUID().toString());
            h.assertTrue(LandStorage.applyWeb(command).get("state").getAsString().equals("CONFLICT")&&Arrays.equals(once,java.nio.file.Files.readAllBytes(file)),"An edit based on a stale cadastre preserves current claims");
        }finally{java.nio.file.Files.write(file,bytes);LandRegistry.replaceAll(registry);}h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void conflictingRectangleDoesNotWritePartialClaim(GameTestHelper h)throws Exception {
        var first=new LandPlot("first","First");first.setDimension("minecraft:overworld");first.addChunk(1,0);
        var other=new LandPlot("second","Second");other.setDimension("minecraft:overworld");var plots=new ArrayList<>(List.of(first,other));
        JsonObject edit=JsonParser.parseString("{\"action\":\"assign\",\"plotId\":\"second\",\"dimension\":\"minecraft:overworld\",\"chunks\":[{\"x\":0,\"z\":0},{\"x\":1,\"z\":0}]}").getAsJsonObject();
        boolean refused=false;try{LandWebChanges.apply(plots,edit);}catch(IllegalArgumentException expected){refused=true;}
        h.assertTrue(refused&&first.hasChunk(1,0),"Foreign ownership must be refused; LandStorage only writes after successful validation");h.succeed();
    }
}
