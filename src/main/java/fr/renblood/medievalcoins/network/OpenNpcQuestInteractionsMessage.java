package fr.renblood.medievalcoins.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.renblood.medievalcoins.api.model.NpcQuestInteractionModel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record OpenNpcQuestInteractionsMessage(String npcId, String npcName, String texture, List<NpcQuestInteractionModel> quests) {
    private static final Gson GSON = new Gson();

    public static void encode(OpenNpcQuestInteractionsMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.npcId);
        buffer.writeUtf(message.npcName);
        buffer.writeUtf(message.texture);
        buffer.writeUtf(GSON.toJson(message.quests));
    }

    public static OpenNpcQuestInteractionsMessage decode(FriendlyByteBuf buffer) {
        return new OpenNpcQuestInteractionsMessage(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                GSON.fromJson(buffer.readUtf(32767), new TypeToken<List<NpcQuestInteractionModel>>() {}.getType()));
    }

    public static void handle(OpenNpcQuestInteractionsMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleOpenNpcQuestInteractions(message));
        });
        context.setPacketHandled(true);
    }
}