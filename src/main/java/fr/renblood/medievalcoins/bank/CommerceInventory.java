package fr.renblood.medievalcoins.bank;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class CommerceInventory {
    private CommerceInventory() {}
    /** All inventory changes in a single server task; a refusal restores full NBT. */
    public static boolean mutate(ServerPlayer player,BooleanSupplier change) {
        List<ItemStack> before=new ArrayList<>();for(ItemStack stack:player.getInventory().items)before.add(stack.copy());
        try{
            if(change.getAsBoolean()){player.getInventory().setChanged();return true;}
        }catch(RuntimeException failure){restore(player,before);throw failure;}
        restore(player,before);return false;
    }
    private static void restore(ServerPlayer player,List<ItemStack> before){
        for(int i=0;i<before.size();i++)player.getInventory().items.set(i,before.get(i));player.getInventory().setChanged();
    }
}
