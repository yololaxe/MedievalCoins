package fr.renblood.medievalcoins.network;

import com.google.gson.Gson;
import fr.renblood.medievalcoins.api.model.QuestModel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.List;
import java.util.function.Supplier;

public record QuestJournalSnapshotMessage(int requestId,String category,String error,List<Entry> entries){
    public record Entry(QuestModel quest,String status,List<Integer> completedObjectives){}
    private static final Gson GSON=new Gson();private static final int LIMIT=262144;
    public static void encode(QuestJournalSnapshotMessage message,FriendlyByteBuf buffer){
        String json=GSON.toJson(message);
        if(json.length()>LIMIT)json=GSON.toJson(new QuestJournalSnapshotMessage(message.requestId,message.category,"Journal trop volumineux. Demande de l’aide à un administrateur.",List.of()));
        buffer.writeUtf(json,LIMIT);
    }
    public static QuestJournalSnapshotMessage decode(FriendlyByteBuf buffer){return GSON.fromJson(buffer.readUtf(LIMIT),QuestJournalSnapshotMessage.class);}
    public static void handle(QuestJournalSnapshotMessage message,Supplier<NetworkEvent.Context> context){
        context.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientPacketHandler.handleQuestJournal(message)));context.get().setPacketHandled(true);
    }
}
