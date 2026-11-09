package fr.renblood.medievalcoins.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import java.util.function.Supplier;

public record EconomyFeedbackMessage(String resource,String state,String detail,String value){
    public static void encode(EconomyFeedbackMessage message,FriendlyByteBuf buffer){buffer.writeUtf(message.resource,200);buffer.writeUtf(message.state,32);buffer.writeUtf(message.detail,256);buffer.writeUtf(message.value,200);}
    public static EconomyFeedbackMessage decode(FriendlyByteBuf buffer){return new EconomyFeedbackMessage(buffer.readUtf(200),buffer.readUtf(32),buffer.readUtf(256),buffer.readUtf(200));}
    public static void handle(EconomyFeedbackMessage message,Supplier<NetworkEvent.Context> context){
        context.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->fr.renblood.medievalcoins.client.economy.EconomyHud.receive(message)));
        context.get().setPacketHandled(true);
    }
}
