package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.market.counter.MerchantCounterBlockEntity;
import fr.renblood.medievalcoins.market.counter.MerchantCounterMenu;
import fr.renblood.medievalcoins.market.service.MarketPurchaseService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MerchantCounterPurchaseMessage(BlockPos pos, String itemId, int quantity, long expectedUnitPrice) {
    public static void encode(MerchantCounterPurchaseMessage message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.pos);
        buffer.writeUtf(message.itemId);
        buffer.writeVarInt(message.quantity);
        buffer.writeLong(message.expectedUnitPrice);
    }

    public static MerchantCounterPurchaseMessage decode(FriendlyByteBuf buffer) {
        return new MerchantCounterPurchaseMessage(buffer.readBlockPos(), buffer.readUtf(), buffer.readVarInt(), buffer.readLong());
    }

    public static void handle(MerchantCounterPurchaseMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if(player==null)return;
            if(!(player.containerMenu instanceof MerchantCounterMenu menu)){fr.renblood.medievalcoins.bank.EconomyFeedback.send(player,"ERROR","Le comptoir a été fermé","");return;}
            if(!menu.getPos().equals(message.pos)){fr.renblood.medievalcoins.bank.EconomyFeedback.send(player,"ERROR","Le comptoir consulté a changé","");return;}
            if (player.distanceToSqr(message.pos.getX() + .5, message.pos.getY() + .5, message.pos.getZ() + .5) > 64){fr.renblood.medievalcoins.bank.EconomyFeedback.send(player,"ERROR","Vous êtes trop loin du comptoir","");return;}
            if (player.level().getBlockEntity(message.pos) instanceof MerchantCounterBlockEntity counter) {
                long current=counter.purchasableItems().stream().filter(row->row.purchaseKey().equals(message.itemId)).mapToLong(row->row.unitPrice()).findFirst().orElse(0);
                if(current<1||message.expectedUnitPrice!=current){
                    fr.renblood.medievalcoins.bank.EconomyFeedback.send(player,"ERROR","Le prix a changé : consultez la nouvelle offre","");
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Le prix a changé. Consultez la nouvelle offre avant de confirmer."));
                    fr.renblood.medievalcoins.market.counter.MerchantCounterMenus.open(player,counter,false);return;
                }
                MarketPurchaseService.purchase(player, counter, message.itemId, message.quantity);
            }else{fr.renblood.medievalcoins.bank.EconomyFeedback.send(player,"ERROR","Le comptoir n’est plus disponible","");}
        });
        context.setPacketHandled(true);
    }
}
