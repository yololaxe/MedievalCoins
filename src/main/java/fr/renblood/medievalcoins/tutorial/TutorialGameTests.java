package fr.renblood.medievalcoins.tutorial;

import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import java.util.*;

@GameTestHolder("medieval_coins")
@PrefixGameTestTemplate(false)
public final class TutorialGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        return new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"tutorial-test")) {
            @Override public void sendSystemMessage(Component text) {}
            @Override public void sendSystemMessage(Component text,boolean overlay) {}
        };
    }
    private static Object session(ServerPlayer player)throws Exception {
        Class<?> type=Class.forName(TutorialManager.class.getName()+"$Session");
        var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);Object session=constructor.newInstance();
        var given=type.getDeclaredField("counterGiven");given.setAccessible(true);given.setBoolean(session,true);
        sessions().put(player.getUUID(),session);return session;
    }
    @SuppressWarnings("unchecked") private static Map<UUID,Object> sessions()throws Exception {
        var field=TutorialManager.class.getDeclaredField("ACTIVE");field.setAccessible(true);return (Map<UUID,Object>)field.get(null);
    }
    private static int step(Object session)throws Exception {
        var field=session.getClass().getDeclaredField("step");field.setAccessible(true);return field.getInt(session);
    }

    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void bankOpeningCompletesFirstStepWithoutMoney(GameTestHelper h)throws Exception {
        var player=player(h);var session=session(player);
        var data=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            data.writeBlockPos(h.absolutePos(new net.minecraft.core.BlockPos(1,1,1)));
            var menu=new fr.renblood.medievalcoins.inventory.banker.BankerGuiMenu(1,player.getInventory(),data);
            TutorialManager.onBankOpened(new net.minecraftforge.event.entity.player.PlayerContainerEvent.Open(player,menu));
            h.assertTrue(step(session)==1&&player.getInventory().isEmpty(),"Opening the actual bank, without money or withdrawal, completes step one");
            TutorialManager.onBankOpened(new net.minecraftforge.event.entity.player.PlayerContainerEvent.Open(player,menu));
            h.assertTrue(step(session)==1,"Repeated bank opening does not skip the next objective");
        }finally{data.release();sessions().remove(player.getUUID());}h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void unrelatedMenuDoesNotValidateBank(GameTestHelper h)throws Exception {
        var player=player(h);var session=session(player);
        try {
            var menu=net.minecraft.world.inventory.ChestMenu.oneRow(1,player.getInventory());
            TutorialManager.onBankOpened(new net.minecraftforge.event.entity.player.PlayerContainerEvent.Open(player,menu));
            h.assertTrue(step(session)==0,"A chest or unrelated menu is not the bank objective");
        }finally{sessions().remove(player.getUUID());}h.succeed();
    }
    @GameTest(template="quests_empty",timeoutTicks=30)
    public static void nagakiIdentityAndLateRefreshFlag(GameTestHelper h) {
        h.assertTrue(TutorialRules.isNagaki(TutorialRules.NAGAKI_ID,"Nom adapté")&&TutorialRules.isNagaki("spawn-identity","Nagaki"),"Nagaki recognized by persistent profile or validated NPC name");
        h.assertTrue(!TutorialRules.isNagaki("npc_1781857753283","Romero Boisserie"),"Romero is not the tutorial giver");
        var data=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            var message=new fr.renblood.medievalcoins.network.OpenNpcQuestInteractionsMessage(TutorialRules.NAGAKI_ID,"Nagaki","",List.of(),true);
            fr.renblood.medievalcoins.network.OpenNpcQuestInteractionsMessage.encode(message,data);
            h.assertTrue(fr.renblood.medievalcoins.network.OpenNpcQuestInteractionsMessage.decode(data).updateOnly(),"A late API update cannot be mistaken for a fresh interaction");
        }finally{data.release();}h.succeed();
    }
}
