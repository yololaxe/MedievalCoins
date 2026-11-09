package fr.renblood.medievalcoins.tree.network;

import fr.renblood.medievalcoins.network.ClientPacketHandler;
import fr.renblood.medievalcoins.tree.TreeAbility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

public class AbilityStatusMessage {
    private final Map<TreeAbility, AbilityStatus> statuses;

    public AbilityStatusMessage(Map<TreeAbility, AbilityStatus> statuses) {
        this.statuses = statuses;
    }

    public Map<TreeAbility, AbilityStatus> getStatuses() {
        return statuses;
    }

    public static void encode(AbilityStatusMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(TreeAbility.values().length);
        for (TreeAbility ability : TreeAbility.values()) {
            AbilityStatus status = message.statuses.getOrDefault(ability, new AbilityStatus(false, 0));
            buffer.writeEnum(ability);
            buffer.writeBoolean(status.active());
            buffer.writeLong(status.cooldownRemainingMs());
        }
    }

    public static AbilityStatusMessage decode(FriendlyByteBuf buffer) {
        Map<TreeAbility, AbilityStatus> statuses = new EnumMap<>(TreeAbility.class);
        int size = buffer.readVarInt();
        for (int i = 0; i < size; i++) {
            TreeAbility ability = buffer.readEnum(TreeAbility.class);
            statuses.put(ability, new AbilityStatus(buffer.readBoolean(), buffer.readLong()));
        }
        return new AbilityStatusMessage(statuses);
    }

    public static void handle(AbilityStatusMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleAbilityStatus(message));
        });
        context.setPacketHandled(true);
    }

    public record AbilityStatus(boolean active, long cooldownRemainingMs) {
    }
}