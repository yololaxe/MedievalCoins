package fr.renblood.medievalcoins.land;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("medieval_coins")
@PrefixGameTestTemplate(false)
public final class LandGameTests {
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void hugeCadastreDoesNotOverflowTheOpeningPacket(GameTestHelper h){
        var previous=LandRegistry.getPlots();try{
            var plots=new java.util.ArrayList<LandPlot>();for(int i=0;i<1500;i++)plots.add(new LandPlot("fixture_"+i,"N".repeat(128)));LandRegistry.replaceAll(plots);
            var player=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"land-budget"));var menu=new LandMapMenu(99,player.getInventory(),player);
            int bytes=menu.choices().stream().mapToInt(p->p.id().getBytes(java.nio.charset.StandardCharsets.UTF_8).length+p.name().getBytes(java.nio.charset.StandardCharsets.UTF_8).length+8).sum();
            h.assertTrue(!menu.choices().isEmpty()&&menu.choices().size()<1500&&bytes<=24000,"Large registries keep the menu opening metadata within the transport budget");
        }finally{LandRegistry.replaceAll(previous);}h.succeed();
    }

    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void visibleChunksAreBounded(GameTestHelper h){
        h.assertTrue(LandMapRules.visible(-5,-4,-1,-2,9,5),"Negative corner belongs to the displayed grid");h.assertTrue(!LandMapRules.visible(-6,-4,-1,-2,9,5),"Outside grid must not be editable");
        h.assertTrue(!LandMapRules.nearby(Integer.MAX_VALUE,0,Integer.MIN_VALUE,0),"Distance cannot overflow into an allowed request");h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void assignmentCannotOverwriteAnotherOwner(GameTestHelper h){
        var a=new LandPlot("first","First");a.setDimension("minecraft:overworld");a.addChunk(0,0);var b=new LandPlot("second","Second");var all=List.of(a,b);boolean refused=false;
        try{LandClaimEditor.apply(all,"minecraft:overworld",0,0,"second","first",false);}catch(IllegalArgumentException expected){refused=true;}
        h.assertTrue(refused&&a.hasChunk(0,0)&&b.getChunks().isEmpty(),"Moving ownership requires an explicit removal first");
        refused=false;try{LandClaimEditor.apply(all,"minecraft:overworld",0,0,"first","",true);}catch(IllegalArgumentException expected){refused=true;}h.assertTrue(refused&&a.hasChunk(0,0),"Stale confirmation cannot remove a changed assignment");h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void unclaimPreservesTheRestOfThePlot(GameTestHelper h){
        var plot=new LandPlot("fixture","Fixture");plot.setOwner(LandOwnerType.FAMILY,"Maison de recette");var all=List.of(plot);
        LandClaimEditor.apply(all,"minecraft:overworld",-1,-2,"fixture","",false);LandClaimEditor.apply(all,"minecraft:overworld",0,-2,"fixture","",false);
        LandClaimEditor.apply(all,"minecraft:overworld",-1,-2,"","fixture",true);h.assertTrue(!plot.hasChunk(-1,-2)&&plot.hasChunk(0,-2)&&plot.getOwner().equals("Maison de recette"),"Only the selected chunk is removed");
        boolean refused=false;try{LandClaimEditor.apply(all,"minecraft:the_nether",0,0,"fixture","",false);}catch(IllegalArgumentException expected){refused=true;}h.assertTrue(refused,"Claims cannot mix dimensions");h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void layoutFitsSmallAndLargeScreens(GameTestHelper h){
        for(int[] size:new int[][]{{320,240},{640,360},{854,480},{1920,1080}}){var p=LandMapLayout.fit(size[0],size[1],9,5);h.assertTrue((p.x()+p.width())*p.scale()<=size[0]&&(p.y()+p.height())*p.scale()<=size[1],"Frame stays inside the screen");h.assertTrue(p.mapX()+9*p.cell()<p.infoX()&&p.infoX()+p.infoWidth()<=p.x()+p.width(),"Map and ownership card remain separate");h.assertTrue(p.infoWidth()>=200,"Ownership card remains readable in virtual units");}h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void mapMetadataUsesRealChunkAndOwner(GameTestHelper h){
        var previous=LandRegistry.getPlots();try{
            var plot=new LandPlot("fixture","Domaine de recette");plot.setDimension("minecraft:overworld");plot.setOwner(LandOwnerType.FAMILY,"Maison de recette");plot.setCity("Creed");plot.setColor("12ABEF");plot.addChunk(-1,-2);LandRegistry.replaceAll(List.of(plot));
            var player=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"land-fixture"));player.setPos(-1,64,-17);
            var menu=new LandMapMenu(99,player.getInventory(),player);var tag=menu.cell(menu.getColumns()/2+menu.getRows()/2*menu.getColumns());
            h.assertTrue(tag.getInt("x")==-1&&tag.getInt("z")==-2&&tag.getString("owner").equals("Maison de recette")&&tag.getInt("color")==0x12ABEF,"Selected center has the server's exact owner and color");
            var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{LandMapMenu.writeOpenData(buffer,player);var client=new LandMapMenu(99,player.getInventory(),buffer);h.assertTrue(client.centerX()==-1&&client.centerZ()==-2&&client.dimension().equals("minecraft:overworld"),"Opening metadata preserves negative coordinates and dimension");h.assertTrue(h.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("mc").getChild("land").getCommand()!=null,"/mc land opens directly without an extra argument");}finally{buffer.release();}
        }finally{LandRegistry.replaceAll(previous);}h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void landActionCodecPreservesConfirmation(GameTestHelper h){
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{var request=new LandMapAction(99,3,-1,-2,"target","old-owner");LandMapAction.encode(request,buffer);h.assertTrue(request.equals(LandMapAction.decode(buffer)),"Confirmation identity and coordinates survive the network");}finally{buffer.release();}h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void corruptCadastreIsNeverOverwritten(GameTestHelper h)throws Exception{
        var file=LandStorage.getFile();h.assertTrue(file.toAbsolutePath().toString().contains("run-quest-tests"),"File test only runs in the disposable test configuration");var original=java.nio.file.Files.readAllBytes(file);var previous=LandRegistry.getPlots();
        try{
            java.nio.file.Files.writeString(file,"{invalid-json");boolean refused=false;try{LandStorage.save(List.of());}catch(java.io.IOException expected){refused=true;}
            h.assertTrue(refused&&java.nio.file.Files.readString(file).equals("{invalid-json"),"An unreadable cadastre is preserved rather than replaced by an empty list");
        }finally{java.nio.file.Files.write(file,original);LandRegistry.replaceAll(previous);}h.succeed();
    }
}
