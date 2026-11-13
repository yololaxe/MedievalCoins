package fr.renblood.medievalcoins.land;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.util.*;

/** Coordinates use the ORIGINAL export, never the attachment's resized preview. */
public record LandTerrainProjection(String dimension,String base,int width,int height,int tileSize,double blocksPerPixel,double originX,double originZ) {
    public record Tile(int column,int row,double chunkX,double chunkZ,double size) {
        public String key(){return column+"_"+row;}
    }
    public static LandTerrainProjection load() {
        try(var stream=LandTerrainProjection.class.getResourceAsStream("/assets/medieval_coins/maps/cadastre.json")) {
            if(stream==null)throw new IllegalStateException("Calage du terrain absent");
            var data=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();var anchor=data.getAsJsonObject("anchor");double scale=data.get("blocksPerPixel").getAsDouble();
            return new LandTerrainProjection(data.get("dimension").getAsString(),data.get("minecraftBase").getAsString(),data.get("width").getAsInt(),data.get("height").getAsInt(),data.get("tileSize").getAsInt(),scale,anchor.get("worldX").getAsDouble()-anchor.get("pixelX").getAsDouble()*scale,anchor.get("worldZ").getAsDouble()-anchor.get("pixelY").getAsDouble()*scale);
        }catch(java.io.IOException failure){throw new IllegalStateException("Calage du terrain illisible",failure);}
    }
    public double worldX(double pixelX){return originX+pixelX*blocksPerPixel;}
    public double worldZ(double pixelY){return originZ+pixelY*blocksPerPixel;}
    public List<Tile> visible(int minX,int minZ,int maxX,int maxZ) {
        double x1=minX*16.0,z1=minZ*16.0,x2=(maxX+1.0)*16,z2=(maxZ+1.0)*16;
        if(maxX<minX||maxZ<minZ||x2<=originX||z2<=originZ||x1>=originX+width*blocksPerPixel||z1>=originZ+height*blocksPerPixel)return List.of();
        double span=tileSize*blocksPerPixel;int columns=(width+tileSize-1)/tileSize,rows=(height+tileSize-1)/tileSize;
        int startX=Math.max(0,(int)Math.floor((x1-originX)/span)),startY=Math.max(0,(int)Math.floor((z1-originZ)/span));
        int endX=Math.min(columns-1,(int)Math.ceil((x2-originX)/span)-1),endY=Math.min(rows-1,(int)Math.ceil((z2-originZ)/span)-1);
        var result=new ArrayList<Tile>();for(int y=startY;y<=endY;y++)for(int x=startX;x<=endX;x++)result.add(new Tile(x,y,(originX+x*span)/16,(originZ+y*span)/16,span/16));return List.copyOf(result);
    }
}
