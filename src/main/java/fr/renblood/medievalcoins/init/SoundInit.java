package fr.renblood.medievalcoins.init;

import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class SoundInit {
    public static final DeferredRegister<SoundEvent> REGISTRY =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MedievalCoin.MODID);

    public static final RegistryObject<SoundEvent> PIPPIN = REGISTRY.register("pippin",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MedievalCoin.MODID, "pippin")));

    static { fr.renblood.medievalcoins.music.MusicCatalog.register(REGISTRY); }

    private SoundInit() {
    }
}