package fr.renblood.medievalcoins.tree.network;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.tree.*;
import fr.renblood.medievalcoins.tree.fertilize.FertilizeCommand;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class RequestAbilityStatusMessage {
    public static void encode(RequestAbilityStatusMessage message, FriendlyByteBuf buffer) {
    }

    public static RequestAbilityStatusMessage decode(FriendlyByteBuf buffer) {
        return new RequestAbilityStatusMessage();
    }

    public static void handle(RequestAbilityStatusMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            UUID id = player.getUUID();
            Map<TreeAbility, AbilityStatusMessage.AbilityStatus> statuses = new EnumMap<>(TreeAbility.class);
            statuses.put(TreeAbility.TORCH, status(TorchCommand.isActive(id), 0));
            statuses.put(TreeAbility.FERTILIZE, status(FertilizeCommand.isActive(id), FertilizeCommand.getCooldownRemainingMs(id)));
            statuses.put(TreeAbility.MAGNET, status(MagnetCommand.isActive(id), MagnetCommand.getCooldownRemainingMs(id)));
            statuses.put(TreeAbility.JUMPBOOST, status(JumpBoostCommand.isActive(id), JumpBoostCommand.getCooldownRemainingMs(id)));
            statuses.put(TreeAbility.NOFALL, status(NoFallCommand.isActive(id), NoFallCommand.getCooldownRemainingMs(id)));
            statuses.put(TreeAbility.VANISH, status(VanishCommand.isActive(id), VanishCommand.getCooldownRemainingMs(id)));
            statuses.put(TreeAbility.FIRECAMP, status(FirecampCommand.isActive(id), FirecampCommand.getCooldownRemainingMs(id)));
            statuses.put(TreeAbility.UNBARK, status(false, UnbarkCommand.getCooldownRemainingMs(id)));

            MedievalCoin.PACKET_HANDLER.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new AbilityStatusMessage(statuses)
            );
        });
        context.setPacketHandled(true);
    }

    private static AbilityStatusMessage.AbilityStatus status(boolean active, long cooldownRemainingMs) {
        return new AbilityStatusMessage.AbilityStatus(active, cooldownRemainingMs);
    }
}
