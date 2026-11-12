package fr.renblood.medievalcoins.land;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
/** Read navigation and explicit, confirmed administrative operations on the open map. */
public record LandMapAction(int menuId,int action,int x,int z,String target,String expected) {
    public static void encode(LandMapAction p,FriendlyByteBuf b){b.writeVarInt(p.menuId);b.writeVarInt(p.action);b.writeInt(p.x);b.writeInt(p.z);b.writeUtf(p.target,128);b.writeUtf(p.expected,128);}
    public static LandMapAction decode(FriendlyByteBuf b){return new LandMapAction(b.readVarInt(),b.readVarInt(),b.readInt(),b.readInt(),b.readUtf(128),b.readUtf(128));}
    public static void handle(LandMapAction p,Supplier<NetworkEvent.Context> context){var ctx=context.get();ctx.enqueueWork(()->{var player=ctx.getSender();if(player!=null&&player.containerMenu instanceof LandMapMenu menu&&menu.containerId==p.menuId)menu.handle(player,p);});ctx.setPacketHandled(true);}
}
