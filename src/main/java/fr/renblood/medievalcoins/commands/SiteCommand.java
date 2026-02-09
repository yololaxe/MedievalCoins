package fr.renblood.medievalcoins.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class SiteCommand {

    private static final String SITE_URL = "https://renblood-website.web.app/";

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        d.register(Commands.literal("site")
                .executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("§aCliquez ici pour visiter notre site web : ")
                            .append(Component.literal("§b§n" + SITE_URL)
                                    .withStyle(Style.EMPTY
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, SITE_URL))
                                            .withUnderlined(true))), false);
                    return 1;
                }));
    }
}
