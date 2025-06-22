// src/main/java/fr/renblood/medievalcoins/init/MedievalCoinsModMenus.java
package fr.renblood.medievalcoins.init;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.inventory.banker.BankerGuiMenu;
import fr.renblood.medievalcoins.inventory.banker.ChangeGUIMenu;
import fr.renblood.medievalcoins.inventory.banker.DepositGuiMenu;
import fr.renblood.medievalcoins.inventory.purse.PurseContainer;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class MedievalCoinsModMenus {
	public static final DeferredRegister<MenuType<?>> REGISTRY =
			DeferredRegister.create(ForgeRegistries.MENU_TYPES, MedievalCoin.MODID);

	public static final RegistryObject<MenuType<BankerGuiMenu>> BANKER_GUI =
			REGISTRY.register("banker_gui",
					() -> IForgeMenuType.create(BankerGuiMenu::new)
			);

	public static final RegistryObject<MenuType<ChangeGUIMenu>> CHANGE_GUI =
			REGISTRY.register("change_gui",
					() -> IForgeMenuType.create(ChangeGUIMenu::new)
			);

	public static final RegistryObject<MenuType<DepositGuiMenu>> DEPOSIT_MENU =
			REGISTRY.register("deposit_gui",
					() -> IForgeMenuType.create(DepositGuiMenu::fromNetwork)
			);

	public static final RegistryObject<MenuType<PurseContainer>> PURSE_CONTAINER =
			REGISTRY.register("purse_container",
					() -> IForgeMenuType.create(PurseContainer::fromNetwork)
			);
}
