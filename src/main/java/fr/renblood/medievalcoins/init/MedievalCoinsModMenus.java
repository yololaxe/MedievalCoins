package fr.renblood.medievalcoins.init;
/*
 *	MCreator note: This file will be REGENERATED on each build.
 */


import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.inventory.banker.BankerGuiMenu;
import fr.renblood.medievalcoins.inventory.banker.ChangeGUIMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;


public class MedievalCoinsModMenus {
	public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MedievalCoin.MODID);
	public static final RegistryObject<MenuType<BankerGuiMenu>> BANKER_GUI = REGISTRY.register("banker_gui", () -> IForgeMenuType.create(BankerGuiMenu::new));
	public static final RegistryObject<MenuType<ChangeGUIMenu>> CHANGE_GUI = REGISTRY.register("change_gui", () -> IForgeMenuType.create(ChangeGUIMenu::new));
}
