package fr.renblood.medievalcoins.tree;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber
public class NoFallCommand {

    private static final Set<UUID> activePlayers = new HashSet<>();
    private static final Map<UUID, Long> lastCommandTime = new HashMap<>();
    private static final long COOLDOWN_MS = 3000; // 3 secondes

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        d.register(Commands.literal("nofall")
                .requires(src -> src.hasPermission(0))
                .executes(c -> {
                    if (isOnCooldown(c.getSource())) return 0;

                    CommandSourceStack src = c.getSource();
                    if (!(src.getEntity() instanceof ServerPlayer player)) {
                        src.sendFailure(Component.literal("❌ Cette commande doit être exécutée en jeu."));
                        return 0;
                    }

                    // Vérifie les permissions métier
                    if (!TreePermissionChecker.hasUnlocked(player, TreeAbility.NOFALL)) {
                        src.sendFailure(Component.literal("❌ Vous devez améliorer votre métier de charpentier."));
                        return 0;
                    }

                    UUID id = player.getUUID();
                    if (activePlayers.contains(id)) {
                        activePlayers.remove(id);
                        src.sendSuccess(() -> Component.literal("❌ Mode NoFall désactivé."), true);
                    } else {
                        activePlayers.add(id);
                        src.sendSuccess(() -> Component.literal("✅ Mode NoFall activé."), true);
                    }
                    return 1;
                }));
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        // Vérifie si c'est des dégâts de chute
        if (event.getSource().is(DamageTypes.FALL)) {
            if (activePlayers.contains(player.getUUID())) {
                event.setCanceled(true); // Annule les dégâts
            }
        }
    }

    private static boolean isOnCooldown(CommandSourceStack source) {
        if (source.getEntity() == null) return false;
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
