package fr.renblood.medievalcoins.procedures;
import net.minecraft.world.entity.Entity;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.bank.CoinExchange;
public class BtoSProcedure { public static void execute(Entity entity) { CoinExchange.exchange(entity,1,4,Coins.BRONZE_COIN.get(),64,Coins.SILVER_COIN.get(),1); } }
