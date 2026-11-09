package fr.renblood.medievalcoins.client.economy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
/** Small optional client bridge; no client class is referenced by the server API. */
public final class EconomyUi {
    public static final int GOLD=0xFFE8C978,TEXT=0xFFEEE5CF,MUTED=0xFFACA69A,POSITIVE=0xFFA9D2AF,ERROR=0xFFEFB1A4;
    private static java.lang.reflect.Method draw,pending,request;
    private static boolean loaded;
    private static void load(){if(loaded)return;loaded=true;try{
        Class<?> type=Class.forName("fr.renblood.medievalcoins.client.economy.EconomyHud");
        draw=type.getMethod("draw",GuiGraphics.class,int.class,int.class,int.class,String.class);pending=type.getMethod("pending");
        request=type.getMethod("request",String.class);
    }catch(Exception ignored){}}
    public static boolean pending(){load();try{return pending!=null&&(boolean)pending.invoke(null);}catch(Exception ignored){return false;}}
    public static void request(String resource){load();try{if(request!=null)request.invoke(null,resource);}catch(Exception ignored){}}
    public static void draw(GuiGraphics graphics,int x,int y,int width,String resource){
        load();try{if(draw!=null){draw.invoke(null,graphics,x,y,width,resource);return;}}catch(Exception ignored){}
        graphics.fill(x,y,x+width,y+19,0xF011110F);graphics.drawString(Minecraft.getInstance().font,"Service économique indisponible",x+6,y+5,MUTED,false);
    }
}
