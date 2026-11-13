package fr.renblood.medievalcoins.music;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.network.MusicControlMessage;
import net.minecraft.commands.*;
import net.minecraft.network.chat.*;
import net.minecraftforge.network.PacketDistributor;
public final class MusicCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> command(){return Commands.literal("music")
        .executes(c->list(c.getSource()))
        .then(Commands.literal("list").executes(c->list(c.getSource())))
        .then(Commands.argument("piste",IntegerArgumentType.integer(1,MusicCatalog.TITLES.length)).executes(c->send(c.getSource(),0,IntegerArgumentType.getInteger(c,"piste"),0)))
        .then(Commands.literal("random").executes(c->send(c.getSource(),1,0,0)))
        .then(Commands.literal("stop").executes(c->send(c.getSource(),2,0,0)))
        .then(Commands.literal("volume").then(Commands.argument("pourcentage",IntegerArgumentType.integer(0,100)).executes(c->send(c.getSource(),3,0,IntegerArgumentType.getInteger(c,"pourcentage")))))
        .then(Commands.literal("credits").executes(c->{c.getSource().sendSuccess(()->Component.literal(MusicCatalog.TITLES.length+" musiques de Kevin MacLeod · CC BY 4.0 · crédits inclus dans le mod").withStyle(s->s.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,"https://incompetech.com/music/royalty-free/music.html"))),false);return 1;}));}
    private static int list(CommandSourceStack source){for(int i=0;i<MusicCatalog.TITLES.length;i++){int n=i+1;source.sendSuccess(()->Component.literal(n+" · "+MusicCatalog.TITLES[n-1]).withStyle(s->s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/mc music "+n))),false);}source.sendSuccess(()->Component.literal("/mc music random · stop · volume <0–100> · credits"),false);return 1;}
    private static int send(CommandSourceStack source,int action,int track,int volume)throws com.mojang.brigadier.exceptions.CommandSyntaxException{var player=source.getPlayerOrException();MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(()->player),new MusicControlMessage(action,track,volume));source.sendSuccess(()->Component.literal(action==0?"Musique : "+MusicCatalog.TITLES[track-1]:action==1?"Lecture aléatoire activée.":action==2?"Musique arrêtée.":"Volume Renblood : "+volume+" % (catégorie Musique dans les options du jeu)."),false);return 1;}
}
