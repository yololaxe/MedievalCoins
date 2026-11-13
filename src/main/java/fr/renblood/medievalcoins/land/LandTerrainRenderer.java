package fr.renblood.medievalcoins.land;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Client-only, asynchronous texture loading; at most sixteen 512px GPU textures. */
public final class LandTerrainRenderer {
    private static final LandTerrainProjection MAP=LandTerrainProjection.load();
    private static final LinkedHashMap<ResourceLocation,CompletableFuture<Void>> CACHE=new LinkedHashMap<>(16,.75f,true);
    private static final int CAPACITY=16;
    private LandTerrainRenderer(){}
    private static boolean ready(ResourceLocation id,Set<ResourceLocation> visible) {
        CompletableFuture<Void> future=CACHE.get(id);
        if(future==null) {
            if(CACHE.size()>=CAPACITY) {
                var iterator=CACHE.entrySet().iterator();boolean freed=false;
                while(iterator.hasNext()) {var old=iterator.next();if(!visible.contains(old.getKey())&&old.getValue().isDone()){Minecraft.getInstance().getTextureManager().release(old.getKey());iterator.remove();freed=true;break;}}
                if(!freed)return false;
            }
            future=Minecraft.getInstance().getTextureManager().preload(id,Util.backgroundExecutor());CACHE.put(id,future);
        }
        return future.isDone()&&!future.isCompletedExceptionally()&&!future.isCancelled();
    }
    public static void render(GuiGraphics graphics,LandMapLayout layout,LandMapMenu menu) {
        if(!MAP.dimension().equals(menu.dimension()))return;
        int columns=menu.getColumns(),rows=menu.getRows(),minX=menu.centerX()-columns/2,minZ=menu.centerZ()-rows/2;
        List<LandTerrainProjection.Tile> tiles=MAP.visible(minX,minZ,minX+columns-1,minZ+rows-1);
        var visible=new HashSet<ResourceLocation>();for(var tile:tiles)visible.add(new ResourceLocation(MedievalCoin.MODID,MAP.base()+"/"+tile.key()+".png"));
        double scale=layout.scale();graphics.enableScissor((int)Math.ceil(layout.mapX()*scale),(int)Math.ceil(layout.mapY()*scale),(int)Math.floor((layout.mapX()+columns*layout.cell())*scale),(int)Math.floor((layout.mapY()+rows*layout.cell())*scale));
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        try {
            for(var tile:tiles) {
                ResourceLocation id=new ResourceLocation(MedievalCoin.MODID,MAP.base()+"/"+tile.key()+".png");if(!ready(id,visible))continue;
                graphics.pose().pushPose();
                graphics.pose().translate(layout.mapX()+(tile.chunkX()-minX)*layout.cell(),layout.mapY()+(tile.chunkZ()-minZ)*layout.cell(),0);
                float pixels=(float)(layout.cell()*MAP.blocksPerPixel()/16);graphics.pose().scale(pixels,pixels,1);
                graphics.blit(id,0,0,0,0,MAP.tileSize(),MAP.tileSize(),MAP.tileSize(),MAP.tileSize());graphics.pose().popPose();
            }
        }finally{RenderSystem.disableBlend();graphics.disableScissor();}
    }
}
