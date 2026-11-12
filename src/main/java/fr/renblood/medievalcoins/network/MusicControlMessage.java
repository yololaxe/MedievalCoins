package fr.renblood.medievalcoins.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record MusicControlMessage(int action,int track,int volume) {
    public static void encode(MusicControlMessage p,FriendlyByteBuf b){b.writeVarInt(p.action);b.writeVarInt(p.track);b.writeVarInt(p.volume);}
    public static MusicControlMessage decode(FriendlyByteBuf b){return new MusicControlMessage(b.readVarInt(),b.readVarInt(),b.readVarInt());}
    public static void handle(MusicControlMessage p,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->fr.renblood.medievalcoins.music.MusicPlayer.handle(p)));ctx.get().setPacketHandled(true);}
}
