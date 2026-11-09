package fr.renblood.medievalcoins.procedures;
import net.minecraft.world.entity.Entity;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.bank.CoinExchange;
public class CtoBProcedure { public static void execute(Entity entity) { CoinExchange.exchange(entity,0,3,Coins.IRON_COIN.get(),64,Coins.BRONZE_COIN.get(),1); } }
