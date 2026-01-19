package fr.renblood.medievalcoins.commands;

import fr.renblood.medievalcoins.procedures.CommandProcedureProcedure;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@Mod.EventBusSubscriber
public class BankCommand {
	private static final Map<UUID, Long> lastCommandTime = new HashMap<>();
	private static final long COOLDOWN_MS = 3000; // 3 secondes

	@SubscribeEvent
	public static void registerCommand(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("bank").requires(s -> s.hasPermission(4)).executes(arguments -> {
			if (isOnCooldown(arguments.getSource())) return 0;
			Level world = arguments.getSource().getUnsidedLevel();
			double x = arguments.getSource().getPosition().x();
			double y = arguments.getSource().getPosition().y();
			double z = arguments.getSource().getPosition().z();
			Entity entity = arguments.getSource().getEntity();
			if (entity == null && world instanceof ServerLevel _servLevel)
				entity = FakePlayerFactory.getMinecraft(_servLevel);
			Direction direction = Direction.DOWN;
			if (entity != null)
				direction = entity.getDirection();

			CommandProcedureProcedure.execute(world, x, y, z, entity);
			return 0;
		}));
	}

	private static boolean isOnCooldown(CommandSourceStack source) {
		if (source.getEntity() == null) return false; // Console ou bloc de commande : pas de cooldown
		UUID uuid = source.getEntity().getUUID();
		long now = System.currentTimeMillis();
		if (lastCommandTime.containsKey(uuid)) {
			long last = lastCommandTime.get(uuid);
			if (now - last < COOLDOWN_MS) {
				source.sendFailure(Component.literal("⏳ Veuillez attendre 3 secondes entre chaque commande."));
				return true;
			}
		}
		lastCommandTime.put(uuid, now);
		return false;
	}
}
