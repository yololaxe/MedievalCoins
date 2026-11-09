package fr.renblood.medievalcoins.events;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.counter.MerchantCounterBlockEntity;
import fr.renblood.medievalcoins.market.service.MarketPermissions;
import fr.renblood.medievalcoins.tutorial.TutorialManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import fr.renblood.medievalcoins.block.MerchantCounterBlock;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID)
public final class MarketSecurityEvents {
    private MarketSecurityEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCounterUse(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || player.isSpectator()
                || event.getUseBlock() == Event.Result.DENY
                || !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof MerchantCounterBlock)) {
            return;
        }

        // Forge normally skips block use while sneaking with an item in hand.
        event.setUseBlock(Event.Result.ALLOW);
        event.setUseItem(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void onCounterBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof MerchantCounterBlockEntity counter)) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (MarketPermissions.isAdmin(player) || counter.isOwner(player.getUUID())) {
            if (TutorialManager.consumeConfirmedCounterBreak(player, event.getPos())) {
                TutorialManager.recordCounterRemoved(player, event.getPos());
                return;
            }
            event.setCanceled(true);
            TutorialManager.requestCounterBreakConfirmation(player, event.getPos());
            return;
        }
        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("Vous ne pouvez pas casser le comptoir d'un autre joueur."));
        MedievalCoin.LOGGER.warn("Tentative casse non autorisee: joueur={} comptoir={}",
                player.getUUID(), counter.getCounterId());
    }
}
