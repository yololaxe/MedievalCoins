package fr.renblood.medievalcoins.client.economy;
import fr.renblood.medievalcoins.network.EconomyFeedbackMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.Util;
import java.util.LinkedHashMap;
import java.util.Map;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = fr.renblood.medievalcoins.MedievalCoin.MODID,
        value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class EconomyHud {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLogin(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) { STATUS.clear(); }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) { STATUS.clear(); }
    private record State(EconomyFeedbackMessage message,long received){}
    private static final Map<String,State> STATUS=new LinkedHashMap<>(){
        @Override protected boolean removeEldestEntry(Map.Entry<String,State> entry){return size()>64;}
    };
    public static void receive(EconomyFeedbackMessage message){STATUS.put(message.resource(),new State(message,Util.getMillis()));}
    public static void request(String resource){if(resource.equals("transaction"))return;fr.renblood.medievalcoins.MedievalCoin.PACKET_HANDLER.sendToServer(new fr.renblood.medievalcoins.network.EconomyStatusRequestMessage(resource));}
    public static void localPending(){receive(new EconomyFeedbackMessage("transaction","PENDING","Transmission de la demande…",""));}
    public static boolean pending(){State state=STATUS.get("transaction");return state!=null&&state.message.state().equals("PENDING");}
    public static void draw(GuiGraphics graphics,int x,int y,int width,String resource){
        State transaction=STATUS.get("transaction"),business=STATUS.get(resource);long now=Util.getMillis();
        String text="Les règlements sont confirmés par le registre.";int color=0xFFACA69A;
        if(transaction!=null&&(pending()||now-transaction.received<4000)){
            text=transaction.message.detail();color=switch(transaction.message.state()){case "SUCCESS"->0xFFA9D2AF;case "ERROR","REVIEW"->0xFFEFB1A4;default->0xFFE8C978;};
            if(pending()&&now-transaction.received>20000)text="Réponse attendue : vérifiez le reçu avant de recommencer.";
        }else if(business!=null){
            text=switch(business.message.state()){case "EMPTY"->"Budget de rachat épuisé";case "CLOSED"->"Établissement suspendu";case "NOT_REGISTERED"->"Budget à préparer par l’intendance";case "AVAILABLE"->"Budget de rachat disponible";case "UNLIMITED"->"Budget de rachat illimité";default->"Disponibilité à vérifier";};
            if(now-business.received>30000)text="Dernier relevé ancien : le registre vérifiera le règlement.";
        }
        var font=Minecraft.getInstance().font;
        graphics.fill(x,y,x+width,y+22,0xF011110F);graphics.fill(x,y,x+2,y+22,color);
        graphics.drawString(font,font.plainSubstrByWidth(text,Math.max(40,width-14)),x+7,y+7,color,false);
        if(transaction!=null&&pending()){
            double motion=Minecraft.getInstance().options.screenEffectScale().get();
            int length=motion<=0?width-4:(int)((width-4)*Math.min(1,(now-transaction.received)/250D));
            graphics.fill(x+2,y+21,x+2+length,y+22,0xFFE8C978);
        }
    }
}
