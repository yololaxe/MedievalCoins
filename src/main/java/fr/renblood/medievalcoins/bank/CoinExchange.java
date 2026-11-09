package fr.renblood.medievalcoins.bank;
import fr.renblood.medievalcoins.inventory.banker.ChangeGUIMenu;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
public final class CoinExchange {
 public static void exchange(Entity entity,int input,int output,Item from,int take,Item to,int give){
  if(!(entity instanceof Player player)||player.level().isClientSide||!(player.containerMenu instanceof ChangeGUIMenu menu))return;
  Slot in=menu.get().get(input),out=menu.get().get(output);
  if(in==null||out==null)return;
  ItemStack source=in.getItem(),target=out.getItem();
  if(!source.is(from)||source.hasTag()||source.getCount()<take)return;
  if(!target.isEmpty()&&(!target.is(to)||target.hasTag()||target.getCount()+give>target.getMaxStackSize()))return;
  if(give>to.getMaxStackSize())return;
  ItemStack nextSource=source.copy();nextSource.shrink(take);
  ItemStack nextTarget=target.isEmpty()?new ItemStack(to,give):target.copy();
  if(!target.isEmpty())nextTarget.grow(give);
  in.set(nextSource);out.set(nextTarget);menu.broadcastChanges();
 }
}
