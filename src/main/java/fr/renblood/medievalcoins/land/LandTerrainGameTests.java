package fr.renblood.medievalcoins.land;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("medieval_coins")
@PrefixGameTestTemplate(false)
public final class LandTerrainGameTests {
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void originalPixelMatchesShaletonInBothAxes(GameTestHelper h) {
        var map=LandTerrainProjection.load();h.assertTrue(map.width()==7296&&map.height()==10112,"Original dimensions are retained");
        h.assertTrue(map.worldX(2868)==-716&&map.worldZ(3691)==-1300,"Exact confirmed anchor");
        h.assertTrue(map.worldX(2864)==-720&&map.worldZ(3679)==-1312,"True negative chunk corner");
        h.assertTrue(map.worldX(2880)==-704&&map.worldZ(3695)==-1296,"Adjacent chunk corner is sixteen blocks away");h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void terrainRequestsOnlyIntersectingResources(GameTestHelper h)throws Exception {
        var map=LandTerrainProjection.load();var center=map.visible(-45,-82,-45,-82);
        h.assertTrue(center.size()==1&&center.get(0).key().equals("5_7"),"Shaleton only needs its intersecting tile");
        h.assertTrue(map.visible(10000,10000,10010,10010).isEmpty()&&map.visible(-10000,-10000,-9990,-9990).isEmpty(),"Exterior views load no resource");
        for(var tile:map.visible(-49,-84,-41,-80))try(var resource=LandTerrainProjection.class.getResourceAsStream("/assets/medieval_coins/"+map.base()+"/"+tile.key()+".png")){h.assertTrue(resource!=null,"Every visible texture is packaged");}
        for(int x=-70;x<70;x++)h.assertTrue(map.visible(x,-84,x+8,-80).size()<=4,"Game view needs at most four native tiles");h.succeed();
    }
}
