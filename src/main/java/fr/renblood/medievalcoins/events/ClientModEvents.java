package fr.renblood.medievalcoins.events;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.client.model.BankerModel;
import fr.renblood.medievalcoins.client.renderer.BankerRenderer;
import fr.renblood.medievalcoins.init.BlockInit;
import fr.renblood.medievalcoins.init.EntityInit;
import fr.renblood.medievalcoins.init.MedievalCoinsModMenus;
import fr.renblood.medievalcoins.inventory.banker.BankerGuiScreen;
import fr.renblood.medievalcoins.inventory.banker.ChangeGUIScreen;
import fr.renblood.medievalcoins.inventory.banker.DepositGuiScreen;
import fr.renblood.medievalcoins.inventory.banker.WithdrawGuiScreen;
import fr.renblood.medievalcoins.inventory.purse.PurseScreen;
import fr.renblood.medievalcoins.land.LandMapScreen;
import fr.renblood.medievalcoins.market.counter.MerchantCounterAdminScreen;
import fr.renblood.medievalcoins.market.counter.MerchantCounterOwnerScreen;
import fr.renblood.medievalcoins.market.counter.MerchantCounterScreen;
import net.minecraft.client.gui.screens.MenuScreens;
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
        // Enregistrement des écrans (GUIs) - Uniquement côté client
        event.enqueueWork(() -> {
            MenuScreens.register(MedievalCoinsModMenus.BANKER_GUI.get(), BankerGuiScreen::new);
            MenuScreens.register(MedievalCoinsModMenus.CHANGE_GUI.get(), ChangeGUIScreen::new);
            MenuScreens.register(MedievalCoinsModMenus.DEPOSIT_MENU.get(), DepositGuiScreen::new);
            MenuScreens.register(MedievalCoinsModMenus.PURSE_CONTAINER.get(), PurseScreen::new);
            MenuScreens.register(MedievalCoinsModMenus.WITHDRAW_MENU.get(), WithdrawGuiScreen::new);
            MenuScreens.register(MedievalCoinsModMenus.LAND_MAP.get(), LandMapScreen::new);
            MenuScreens.register(MedievalCoinsModMenus.MERCHANT_COUNTER.get(), MerchantCounterScreen::new);
            MenuScreens.register(MedievalCoinsModMenus.MERCHANT_COUNTER_OWNER.get(), MerchantCounterOwnerScreen::new);
            MenuScreens.register(MedievalCoinsModMenus.MERCHANT_COUNTER_ADMIN.get(), MerchantCounterAdminScreen::new);
            
            // Définit le type de rendu CUTOUT pour les torches magiques (transparence)
            ItemBlockRenderTypes.setRenderLayer(BlockInit.MAGIC_TORCH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(BlockInit.MAGIC_WALL_TORCH.get(), RenderType.cutout());
        });
    }
}
