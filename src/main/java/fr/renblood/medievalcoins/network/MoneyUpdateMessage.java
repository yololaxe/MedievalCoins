// src/main/java/fr/renblood/medievalcoins/network/MoneyUpdateMessage.java

package fr.renblood.medievalcoins.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MoneyUpdateMessage {
    public final String mcId;
    public final double newMoney;

    public MoneyUpdateMessage(String mcId, double newMoney) {
        this.mcId    = mcId;
        this.newMoney = newMoney;
    }

    public static void encode(MoneyUpdateMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.mcId, 255);
        buf.writeDouble(msg.newMoney);
    }

    public static MoneyUpdateMessage decode(FriendlyByteBuf buf) {
        String id = buf.readUtf(255);
        double m  = buf.readDouble();
        return new MoneyUpdateMessage(id, m);
    }

    public static void handle(MoneyUpdateMessage msg, Supplier<NetworkEvent.Context> ctx) {
        // *** côté client uniquement ***
        ctx.get().enqueueWork(() -> {
            // on doit être sur le fil client
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof fr.renblood.medievalcoins.inventory.banker.BankerGuiScreen screen) {
                screen.updateMoney(msg.newMoney);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
