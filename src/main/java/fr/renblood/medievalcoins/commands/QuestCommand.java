package fr.renblood.medievalcoins.commands;

import com.mojang.brigadier.CommandDispatcher;
import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class QuestCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        d.register(Commands.literal("quest")
                .executes(c -> {
                    // Cette commande est exécutée côté serveur.
                    // Pour ouvrir un GUI client, il faut envoyer un packet au joueur.
                    // Comme je n'ai pas encore créé le packet OpenQuestScreenMessage,
                    // je vais simplement envoyer un message pour l'instant.
                    // TODO: Implémenter OpenQuestScreenMessage et l'envoyer ici.
                    
                    c.getSource().sendSuccess(() -> Component.literal("Utilisez la touche 'H' pour ouvrir le menu des quêtes."), false);
                    return 1;
                }));
    }
}
