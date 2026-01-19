package fr.renblood.medievalcoins.events;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.client.model.BankerModel;
import fr.renblood.medievalcoins.client.renderer.BankerRenderer;
import fr.renblood.medievalcoins.init.BlockInit;
import fr.renblood.medievalcoins.init.EntityInit;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(EntityInit.BANKER.get(), BankerRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinition(EntityRenderersEvent.RegisterLayerDefinitions event){
        event.registerLayerDefinition(BankerModel.LAYER_LOCATION, BankerModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Définit le type de rendu CUTOUT pour les torches magiques (transparence)
        ItemBlockRenderTypes.setRenderLayer(BlockInit.MAGIC_TORCH.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(BlockInit.MAGIC_WALL_TORCH.get(), RenderType.cutout());
    }
}
