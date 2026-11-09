package fr.renblood.medievalcoins.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public final class BackCommand {
    private static final String TAG = "MedievalCoinsBack";

    private BackCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("mc")
                .then(Commands.literal("back")
                        .executes(context -> back(context.getSource()))));
    }

    @SubscribeEvent
    public static void beforeCommand(CommandEvent event) {
        String command = event.getParseResults().getReader().getString().trim().toLowerCase();
        if (!isTeleportCommand(command)) return;
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player) {
            remember(player);
        }
    }

    public static void remember(ServerPlayer player) {
        CompoundTag back = new CompoundTag();
        back.putString("dimension", player.level().dimension().location().toString());
        back.putDouble("x", player.getX());
        back.putDouble("y", player.getY());
        back.putDouble("z", player.getZ());
        back.putFloat("yaw", player.getYRot());
        back.putFloat("pitch", player.getXRot());
        player.getPersistentData().put(TAG, back);
    }

    private static int back(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Cette commande doit etre executee en jeu."));
            return 0;
        }
        CompoundTag back = player.getPersistentData().getCompound(TAG);
        if (!back.contains("dimension")) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Aucune position precedente enregistree."));
            return 0;
        }

        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION,
                new ResourceLocation(back.getString("dimension"))
        );
        ServerLevel targetLevel = player.getServer().getLevel(dimension);
        if (targetLevel == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("La dimension precedente est indisponible."));
            return 0;
        }

        CompoundTag current = new CompoundTag();
        current.putString("dimension", player.level().dimension().location().toString());
        current.putDouble("x", player.getX());
        current.putDouble("y", player.getY());
        current.putDouble("z", player.getZ());
        current.putFloat("yaw", player.getYRot());
        current.putFloat("pitch", player.getXRot());

        player.teleportTo(targetLevel, back.getDouble("x"), back.getDouble("y"), back.getDouble("z"),
                back.getFloat("yaw"), back.getFloat("pitch"));
        player.getPersistentData().put(TAG, current);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Retour a la position precedente."), false);
        return 1;
    }

    private static boolean isTeleportCommand(String command) {
        return command.equals("tp") || command.startsWith("tp ")
                || command.equals("teleport") || command.startsWith("teleport ")
                || command.equals("minecraft:tp") || command.startsWith("minecraft:tp ")
                || command.equals("minecraft:teleport") || command.startsWith("minecraft:teleport ");
    }
}
