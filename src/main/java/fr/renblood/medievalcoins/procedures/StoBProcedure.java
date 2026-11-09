package fr.renblood.medievalcoins.procedures;
import net.minecraft.world.entity.Entity;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.bank.CoinExchange;
public class StoBProcedure { public static void execute(Entity entity) { CoinExchange.exchange(entity,7,10,Coins.SILVER_COIN.get(),1,Coins.BRONZE_COIN.get(),64); } }
