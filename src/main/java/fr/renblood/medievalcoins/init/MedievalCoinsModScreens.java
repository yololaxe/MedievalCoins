//package fr.renblood.medievalcoins.init;
///*
// *	MCreator note: This file will be REGENERATED on each build.
// */
//
//import fr.renblood.medievalcoins.inventory.banker.BankerGuiScreen;
//import fr.renblood.medievalcoins.inventory.banker.ChangeGUIScreen;
//import net.minecraft.client.gui.screens.MenuScreens;
//import net.minecraftforge.api.distmarker.Dist;
//import net.minecraftforge.eventbus.api.SubscribeEvent;
//import net.minecraftforge.fml.common.Mod;
//import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//
//
//@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//public class MedievalCoinsModScreens {
//	@SubscribeEvent
//	public static void clientLoad(FMLClientSetupEvent event) {
//		event.enqueueWork(() -> {
//			MenuScreens.register(MedievalCoinsModMenus.BANKER_GUI.get(), BankerGuiScreen::new);
//			MenuScreens.register(MedievalCoinsModMenus.CHANGE_GUI.get(), ChangeGUIScreen::new);
//		});
//	}
//}
