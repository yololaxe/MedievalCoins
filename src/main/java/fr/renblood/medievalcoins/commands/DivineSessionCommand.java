package fr.renblood.medievalcoins.commands;

import com.mojang.brigadier.CommandDispatcher;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.divine.DivineSessionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID)
public final class DivineSessionCommand {
    private DivineSessionCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("mc")
                .then(Commands.literal("admin")
                .then(Commands.literal("divine")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("next")
                        .executes(context -> {
                            int session = DivineSessionManager.nextSession(context.getSource().getServer());
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Nouvelle session divine : " + session),
                                    true
                            );
                            return session;
                        }))
                .then(Commands.literal("status")
                        .executes(context -> {
                            int session = DivineSessionManager.data(context.getSource().getServer()).sessionNumber();
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Session divine actuelle : " + session),
                                    false
                            );
                            return session;
                        })))));
    }
}
