// src/main/java/fr/renblood/medievalcoins/MedievalCoin.java
package fr.renblood.medievalcoins;

import fr.renblood.medievalcoins.client.renderer.BankerRenderer;
import fr.renblood.medievalcoins.creative.CreativeTab;
import fr.renblood.medievalcoins.init.BlockInit;
import fr.renblood.medievalcoins.init.ItemInit;
import fr.renblood.medievalcoins.init.MedievalCoinsModMenus;
import fr.renblood.medievalcoins.inventory.banker.BankerGuiScreen;
import fr.renblood.medievalcoins.inventory.banker.ChangeGUIScreen;
import fr.renblood.medievalcoins.inventory.banker.DepositGuiScreen;
import fr.renblood.medievalcoins.inventory.banker.WithdrawGuiScreen;
import fr.renblood.medievalcoins.inventory.purse.PurseScreen;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.network.*;
import fr.renblood.medievalcoins.procedures.OpenDepositGuiMessage;

import fr.renblood.medievalcoins.procedures.OpenWithdrawGuiMessage;
import fr.renblood.medievalcoins.tree.network.FertilizeStateMessage;
import fr.renblood.medievalcoins.tree.network.FertilizerSlotMessage;
import fr.renblood.medievalcoins.tree.network.SpecialSlotKeyMessage;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static fr.renblood.medievalcoins.init.EntityInit.BANKER;
import static fr.renblood.medievalcoins.init.EntityInit.ENTITY_TYPES;

@Mod(MedievalCoin.MODID)
public class MedievalCoin {
    public static final String MODID = "medieval_coins";
    public static final Logger LOGGER = LogManager.getLogger();
    private static final String PROTOCOL_VERSION = "1";
    
    // Variable globale de debug
    public static boolean DEBUG_MODE = true;

    // 1) Channel réseau
    public static final SimpleChannel PACKET_HANDLER = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, MODID),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    private static int messageID = 0;

    public MedievalCoin() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 2) Enregistrement des DeferredRegister (items, entités, menus…)
        Coins.register(modBus);
        ItemInit.REGISTRY.register(modBus);
        BlockInit.REGISTRY.register(modBus);
        CreativeTab.TABS.register(modBus);
        ENTITY_TYPES.register(modBus);
        MedievalCoinsModMenus.REGISTRY.register(modBus);
        modBus.addListener(this::commonSetup);
        // 3) Écoute du bus Forge
        MinecraftForge.EVENT_BUS.register(this);

        // 4) Réseau
        registerNetworkMessages();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 5) Register des écrans côté client
            LOGGER.info("[MedievalCoins] Enregistrement des écrans client");
            MenuScreens.register(MedievalCoinsModMenus.BANKER_GUI.get(),  BankerGuiScreen::new);
            MenuScreens.register(MedievalCoinsModMenus.CHANGE_GUI.get(),  ChangeGUIScreen::new);
            MenuScreens.register(MedievalCoinsModMenus.DEPOSIT_MENU.get(), DepositGuiScreen::new);
            MenuScreens.register(MedievalCoinsModMenus.PURSE_CONTAINER.get(), PurseScreen::new);
            MenuScreens.register(MedievalCoinsModMenus.WITHDRAW_MENU.get(), WithdrawGuiScreen::new);

            // 6) Renderer de l'entité banquier
            EntityRenderers.register(BANKER.get(), BankerRenderer::new);
        });
    }

    private void registerNetworkMessages() {
        // 1) Refresh GUI banquier
        PACKET_HANDLER.registerMessage(
                messageID++,
                BankerGuiRefreshMessage.class,
                BankerGuiRefreshMessage::encode,
                BankerGuiRefreshMessage::decode,
                BankerGuiRefreshMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        // 2) Mise à jour du solde
        PACKET_HANDLER.registerMessage(
                messageID++,
                MoneyUpdateMessage.class,
                MoneyUpdateMessage::encode,
                MoneyUpdateMessage::decode,
                MoneyUpdateMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        // 3) Ouvrir GUI dépôt
        PACKET_HANDLER.registerMessage(
                messageID++,
                OpenDepositGuiMessage.class,
                OpenDepositGuiMessage::encode,
                OpenDepositGuiMessage::decode,
                OpenDepositGuiMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        // 4) Soumettre dépôt
        PACKET_HANDLER.registerMessage(
                messageID++,
                SubmitDepositMessage.class,
                SubmitDepositMessage::encode,
                SubmitDepositMessage::decode,
                SubmitDepositMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        // 5) Boutons divers (ex : change money)
        PACKET_HANDLER.registerMessage(
                messageID++,
                BankerGuiButtonMessage.class,
                BankerGuiButtonMessage::buffer,
                BankerGuiButtonMessage::new,
                BankerGuiButtonMessage::handler,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // 6) **Changer de GUI** (tu oubliais celui‐ci)
        PACKET_HANDLER.registerMessage(
                messageID++,
                ChangeGUIButtonMessage.class,
                ChangeGUIButtonMessage::buffer,   // ← au lieu de ::encode
                ChangeGUIButtonMessage::new,      // ← au lieu de ::decode
                ChangeGUIButtonMessage::handler,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // 7) Ouvrir le GUI de retrait
        PACKET_HANDLER.registerMessage(
                messageID++,
                OpenWithdrawGuiMessage.class,
                OpenWithdrawGuiMessage::encode,
                OpenWithdrawGuiMessage::decode,
                OpenWithdrawGuiMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // 8) Soumettre retrait
        PACKET_HANDLER.registerMessage(
                messageID++,
                SubmitWithdrawMessage.class,
                SubmitWithdrawMessage::encode,
                SubmitWithdrawMessage::decode,
                SubmitWithdrawMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        PACKET_HANDLER.registerMessage(
                messageID++,
                SpecialSlotKeyMessage.class,
                SpecialSlotKeyMessage::encode,
                SpecialSlotKeyMessage::decode,
                SpecialSlotKeyMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        PACKET_HANDLER.registerMessage(
                messageID++,
                FertilizerSlotMessage.class,
                FertilizerSlotMessage::encode,
                FertilizerSlotMessage::decode,
                FertilizerSlotMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        PACKET_HANDLER.registerMessage(
                messageID++,
                FertilizeStateMessage.class,
                FertilizeStateMessage::encode,
                FertilizeStateMessage::decode,
                FertilizeStateMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        PACKET_HANDLER.registerMessage(
                messageID++,
                PlayerStatsUpdateMessage.class,
                PlayerStatsUpdateMessage::encode,
                PlayerStatsUpdateMessage::decode,
                PlayerStatsUpdateMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

    }

    // Méthode utilitaire pour récupérer le niveau serveur (Overworld par défaut)
    public static ServerLevel getServerLevel() {
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            return ServerLifecycleHooks.getCurrentServer().overworld();
        }
        return null;
    }

    // (facultatif) file de tâches côté serveur
    private static final Collection<Map.Entry<Runnable,Integer>> workQueue = new ConcurrentLinkedQueue<>();
    public static void queueServerWork(int tick, Runnable action) {
        if (Thread.currentThread().getThreadGroup().getName().contains("Server"))
            workQueue.add(new AbstractMap.SimpleEntry<>(action, tick));
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase == TickEvent.Phase.END) {
            var toRun = new ArrayList<Map.Entry<Runnable,Integer>>();
            for (var e : workQueue) {
                int t = e.getValue() - 1;
                if (t <= 0) toRun.add(e);
                else e.setValue(t);
            }
            toRun.forEach(e -> e.getKey().run());
            workQueue.removeAll(toRun);
        }
    }
}
