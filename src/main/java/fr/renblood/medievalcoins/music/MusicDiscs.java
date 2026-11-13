package fr.renblood.medievalcoins.music;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.*;

/** Stable item identities, including the original Pippin disc. */
public final class MusicDiscs {
    public static final String[] IDS={"lord_of_the_land_music_disc","pippin_music_disc","village_consort_music_disc","celtic_impulse_music_disc","folk_round_music_disc","minstrel_guild_music_disc","angevin_music_disc","angevin_b_music_disc","hidden_past_music_disc","the_britons_music_disc","errigal_music_disc","skye_cuillin_music_disc","the_pyre_music_disc","rites_music_disc","renaissance_harps_music_disc","teller_of_the_tales_music_disc"};
    public static final Map<Integer,RegistryObject<Item>> DISCS=new LinkedHashMap<>();
    private MusicDiscs(){}
    public static Map<Integer,RegistryObject<Item>> register(DeferredRegister<Item> registry,RegistryObject<Item> pippin) {
        for(int number=1;number<=IDS.length;number++) {
            final int track=number;
            DISCS.put(number,number==2?pippin:registry.register(IDS[number-1],()->new RecordItem(Math.min(15,track),()->Objects.requireNonNull(ForgeRegistries.SOUND_EVENTS.getValue(MusicCatalog.id(track))),new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),MusicCatalog.LENGTH_TICKS[track-1])));
        }
        return Collections.unmodifiableMap(DISCS);
    }
}
