package fr.renblood.medievalcoins.music;
import fr.renblood.medievalcoins.network.MusicControlMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
@Mod.EventBusSubscriber(modid="medieval_coins",value=Dist.CLIENT)
public final class MusicPlayer {
    private static MusicSound current;private static boolean random;private static int volume=60,last,age;private static boolean loaded;
    private static java.nio.file.Path settings(){return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("medieval_coins/music_client.json");}
    public static void handle(MusicControlMessage message){
        if(!loaded){loaded=true;try{var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(settings())).getAsJsonObject();volume=Math.max(0,Math.min(100,json.get("volume").getAsInt()));}catch(Exception missing){}}
        switch(message.action()){
            case 0->{random=false;play(message.track());}
            case 1->{random=true;next();}
            case 2->{random=false;stop();}
            case 3->{volume=Math.max(0,Math.min(100,message.volume()));if(current!=null)current.volume(volume);else if(random&&volume>0)next();try{java.nio.file.Files.createDirectories(settings().getParent());java.nio.file.Files.writeString(settings(),"{\"volume\":"+volume+"}");}catch(java.io.IOException failure){if(Minecraft.getInstance().player!=null)Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.literal("Volume appliqué, mais préférence non sauvegardée."),false);}}
        }
    }
    private static void stop(){if(current!=null)Minecraft.getInstance().getSoundManager().stop(current);current=null;age=0;}
    private static void play(int number){if(number<1||number>MusicCatalog.TITLES.length)return;stop();last=number;Minecraft.getInstance().getMusicManager().stopPlaying();current=new MusicSound(number,volume);Minecraft.getInstance().getSoundManager().play(current);}
    private static void next(){int n;do{n=java.util.concurrent.ThreadLocalRandom.current().nextInt(1,MusicCatalog.TITLES.length+1);}while(n==last);play(n);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.level==null){random=false;stop();return;}if(mc.isPaused())return;if(current!=null&&++age>60&&!mc.getSoundManager().isActive(current)){current=null;if(random&&volume>0)next();}}
    @SubscribeEvent public static void sound(PlaySoundEvent event){if(current!=null&&event.getSound()!=null&&event.getSound()!=current&&event.getSound().getSource()==SoundSource.MUSIC)event.setSound(null);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){random=false;stop();}
    private static final class MusicSound extends AbstractTickableSoundInstance {
        MusicSound(int number,int value){super(SoundEvent.createVariableRangeEvent(MusicCatalog.id(number)),SoundSource.MUSIC,RandomSource.create());relative=true;attenuation=SoundInstance.Attenuation.NONE;volume(value);}
        void volume(int value){volume=value/100f;}
        @Override public void tick(){}
    }
}
