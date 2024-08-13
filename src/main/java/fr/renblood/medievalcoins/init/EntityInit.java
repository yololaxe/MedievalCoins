package fr.renblood.medievalcoins.init;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.entity.Banker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class EntityInit {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MedievalCoin.MODID);

    public static final RegistryObject<EntityType<Banker>> BANKER = ENTITY_TYPES.register("banker",
            () -> EntityType.Builder.of((EntityType<Banker> type, Level world) -> new Banker(type, world), MobCategory.CREATURE)
                    .sized(1.0F, 1.0F)
                    .build(new ResourceLocation(MedievalCoin.MODID, "banker").toString()));

}