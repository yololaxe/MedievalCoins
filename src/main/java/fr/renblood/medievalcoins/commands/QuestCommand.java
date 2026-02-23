package fr.renblood.medievalcoins.commands;

import com.mojang.brigadier.CommandDispatcher;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.client.gui.QuestScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class QuestCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        d.register(Commands.literal("quest")
                .executes(c -> {
                    if (c.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer) {
                        // La commande est exécutée côté serveur (chat), il faut dire au client d'ouvrir l'écran.
                        // Mais /quest est souvent une commande client si enregistrée côté client ?
                        // Non, RegisterCommandsEvent est serveur.
                        // On ne peut pas ouvrir un écran client depuis le serveur directement sans packet !
                        
                        // Si on est en solo (Integrated Server), DistExecutor marche car le client et le serveur sont dans la même JVM.
                        // Mais en multi, ça ne marchera pas.
                        
                        // IL FAUT UN PACKET pour dire au client "Ouvre le menu de quêtes".
                        // Ou alors, c'est une commande client-side (ClientCommandHandler) ? Forge n'a plus de ClientCommandHandler simple en 1.19+.
                        
                        // Solution rapide pour le solo/dev :
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> openScreen());
                        
                        // Pour le multi, il faudra un packet OpenQuestScreenMessage.
                        // Je vais supposer que vous testez en solo pour l'instant.
                    }
                    return 1;
                }));
    }
    
    private static void openScreen() {
        // Exécuter sur le thread render pour éviter les soucis
        Minecraft.getInstance().execute(() -> {
            if (MedievalCoin.DEBUG_MODE) MedievalCoin.LOGGER.info("Opening Quest Screen...");
            Minecraft.getInstance().setScreen(new QuestScreen());
        });
    }
}
