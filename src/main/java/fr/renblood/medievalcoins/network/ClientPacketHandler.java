package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.client.gui.*;
import fr.renblood.medievalcoins.client.quest.ClientNpcQuestAvailability;
import fr.renblood.medievalcoins.tree.capability.SpecialSlotCapabilityHandler;
import fr.renblood.medievalcoins.tree.network.AbilityStatusMessage;
import fr.renblood.medievalcoins.tree.network.FertilizerSlotMessage;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {

    public static void handleMoneyUpdate(MoneyUpdateMessage msg) {
        Minecraft mc = Minecraft.getInstance();
        var screen = mc.screen;
        if (screen instanceof fr.renblood.medievalcoins.inventory.banker.BankerGuiScreen banker) {
            banker.updateMoney(msg.newMoney);
        } else if (screen instanceof fr.renblood.medievalcoins.inventory.banker.WithdrawGuiScreen withdraw) {
            withdraw.updateMoney(msg.newMoney);
        }
    }

    public static void handleFertilizerSlot(FertilizerSlotMessage msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.getCapability(SpecialSlotCapabilityHandler.SPECIAL_SLOT_CAP).ifPresent(inv -> {
            inv.setStackInSlot(0, msg.getStack());
        });
    }

    public static void handlePlayerStatsUpdate(PlayerStatsUpdateMessage msg) {
        PlayerCache.updatePlayer(msg.getPlayerModel());
    }

    public static void handleOpenQuestScreen(OpenQuestScreenMessage msg) {
        Minecraft.getInstance().setScreen(new QuestScreen());
    }

    public static void handleQuestProgress(QuestProgressMessage msg) {
        QuestScreen.updateObjectiveProgress(msg.getProgress());
    }

    public static void handleOpenNpcDialogue(OpenNpcDialogueMessage msg) {
        Minecraft.getInstance().setScreen(new NpcDialogueScreen(msg.npcName(), msg.text(), msg.texture()));
    }

    public static void handleOpenNpcQuestInteractions(OpenNpcQuestInteractionsMessage msg) {
        Minecraft.getInstance().setScreen(new NpcQuestInteractionsScreen(msg.npcId(), msg.npcName(), msg.texture(), msg.quests()));
    }

    public static void handleNpcQuestAvailability(NpcQuestAvailabilityMessage msg) {
        ClientNpcQuestAvailability.replace(msg.spawnIds());
    }

    public static void handleOpenPrayerScreen(OpenPrayerScreenMessage msg) {
        Minecraft.getInstance().setScreen(new PrayerMinigameScreen());
    }

    public static void handleAbilityStatus(AbilityStatusMessage msg) {
        RadialMenuScreen.updateStatuses(msg.getStatuses());
    }

    public static void handleMerchantCounterHistory(MerchantCounterHistoryResponseMessage msg) {
        if (Minecraft.getInstance().screen instanceof fr.renblood.medievalcoins.market.counter.MarketHistoryReceiver receiver) {
            receiver.receiveHistory(msg.transactions(), msg.backendUnavailable());
        }
    }
}
