package fr.renblood.medievalcoins.music;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
public final class MusicCatalog {
    public static final String[] TITLES={"Lord of the Land","Pippin the Hunchback","Village Consort","Celtic Impulse","Folk Round","Minstrel Guild","Angevin","Angevin B","Hidden Past","The Britons","Errigal","Skye Cuillin","The Pyre","Rites","Canon in D for Two Renaissance Harps"};
    public static ResourceLocation id(int number){if(number<1||number>TITLES.length)throw new IllegalArgumentException("Piste inconnue");return new ResourceLocation("medieval_coins",String.format(java.util.Locale.ROOT,"music.renblood_%02d",number));}
    public static void register(DeferredRegister<SoundEvent> registry){for(int i=1;i<=TITLES.length;i++){ResourceLocation id=id(i);registry.register(id.getPath(),()->SoundEvent.createVariableRangeEvent(id));}}
}
