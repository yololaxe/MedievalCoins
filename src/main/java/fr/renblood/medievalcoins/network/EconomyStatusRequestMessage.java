package fr.renblood.medievalcoins.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record EconomyStatusRequestMessage(String business){
    public static void encode(EconomyStatusRequestMessage message,FriendlyByteBuf buffer){buffer.writeUtf(message.business,200);}
    public static EconomyStatusRequestMessage decode(FriendlyByteBuf buffer){return new EconomyStatusRequestMessage(buffer.readUtf(200));}
    public static void handle(EconomyStatusRequestMessage message,Supplier<NetworkEvent.Context> context){
        context.get().enqueueWork(()->{
            var player=context.get().getSender();if(player==null)return;
            if(!message.business.matches("(?:market|inn):[a-fA-F0-9-]{36}|npc-contracts"))return;
            fr.renblood.medievalcoins.bank.EconomyFeedback.refresh(player,message.business);
        });context.get().setPacketHandled(true);
    }
}
