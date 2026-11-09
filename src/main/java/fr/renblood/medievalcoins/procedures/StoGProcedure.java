package fr.renblood.medievalcoins.procedures;
import net.minecraft.world.entity.Entity;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.bank.CoinExchange;
public class StoGProcedure { public static void execute(Entity entity) { CoinExchange.exchange(entity,2,5,Coins.SILVER_COIN.get(),64,Coins.GOLD_COIN.get(),1); } }
