package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.events.QuestObjectiveHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record QuestJournalRequestMessage(int requestId,String category,boolean refresh){
    public static void encode(QuestJournalRequestMessage message,FriendlyByteBuf buffer){buffer.writeVarInt(message.requestId);buffer.writeUtf(message.category,32);buffer.writeBoolean(message.refresh);}
    public static QuestJournalRequestMessage decode(FriendlyByteBuf buffer){return new QuestJournalRequestMessage(buffer.readVarInt(),buffer.readUtf(32),buffer.readBoolean());}
    public static void handle(QuestJournalRequestMessage message,Supplier<NetworkEvent.Context> context){
        var ctx=context.get();ctx.enqueueWork(()->{var player=ctx.getSender();if(player!=null)QuestObjectiveHandler.sendJournalAsync(player,message);});ctx.setPacketHandled(true);
    }
}
