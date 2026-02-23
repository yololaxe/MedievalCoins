package fr.renblood.medievalcoins.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import fr.renblood.medievalcoins.events.TimeManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class TimeCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        d.register(Commands.literal("daylength")
                .requires(src -> src.hasPermission(2)) // OP
                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.1, 100.0))
                        .executes(c -> {
                            double mult = DoubleArgumentType.getDouble(c, "multiplier");
                            TimeManager.dayLengthMultiplier = mult;

                            double totalMinutes = 20.0 * mult;
                            c.getSource().sendSuccess(() -> Component.literal(
                                    String.format("✅ Durée du jour définie à x%.1f (Total: %.1f minutes)", mult, totalMinutes)
                            ), true);
                            return 1;
                        })
                )
        );

        d.register(Commands.literal("sleep_percentage")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("percentage", DoubleArgumentType.doubleArg(0.0, 100.0))
                        .executes(c -> {
                            double percent = DoubleArgumentType.getDouble(c, "percentage");
                            TimeManager.sleepPercentage = percent / 100.0;
                            c.getSource().sendSuccess(() -> Component.literal(
                                    String.format("✅ Pourcentage de sommeil requis : %.0f%%", percent)
                            ), true);
                            return 1;
                        })
                )
        );
    }
}
