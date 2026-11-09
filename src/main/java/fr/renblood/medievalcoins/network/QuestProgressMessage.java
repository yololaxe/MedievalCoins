package fr.renblood.medievalcoins.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class QuestProgressMessage {
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<Map<String, List<Integer>>>() {}.getType();
    private final Map<String, List<Integer>> progress;

    public QuestProgressMessage(Map<String, List<Integer>> progress) {
        this.progress = progress;
    }

    public Map<String, List<Integer>> getProgress() {
        return progress;
    }

    public static void encode(QuestProgressMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(GSON.toJson(message.progress));
    }

    public static QuestProgressMessage decode(FriendlyByteBuf buffer) {
        Map<String, List<Integer>> progress = GSON.fromJson(buffer.readUtf(), TYPE);
        return new QuestProgressMessage(progress);
    }

    public static void handle(QuestProgressMessage message, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleQuestProgress(message));
        });
        context.get().setPacketHandled(true);
    }
}