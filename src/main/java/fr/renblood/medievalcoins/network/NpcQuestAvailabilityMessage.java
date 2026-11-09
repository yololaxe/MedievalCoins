package fr.renblood.medievalcoins.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public record NpcQuestAvailabilityMessage(Set<String> spawnIds) {
    public static void encode(NpcQuestAvailabilityMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.spawnIds.size());
        message.spawnIds.forEach(buffer::writeUtf);
    }

    public static NpcQuestAvailabilityMessage decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Set<String> spawnIds = new HashSet<>();
        for (int i = 0; i < size; i++) {
            spawnIds.add(buffer.readUtf());
        }
        return new NpcQuestAvailabilityMessage(spawnIds);
    }

    public static void handle(NpcQuestAvailabilityMessage message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleNpcQuestAvailability(message));
        });
        context.setPacketHandled(true);
    }
}