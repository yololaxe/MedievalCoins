package fr.renblood.medievalcoins.tree;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber
public class VanishCommand {

    private static final Set<UUID> activePlayers = new HashSet<>();
    private static final Map<UUID, Long> lastCommandTime = new HashMap<>();
    private static final long COOLDOWN_MS = 3000; // 3 secondes

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        d.register(Commands.literal("vanish")
                .requires(src -> src.hasPermission(0))
                .executes(c -> {
                    if (isOnCooldown(c.getSource())) return 0;

                    CommandSourceStack src = c.getSource();
                    if (!(src.getEntity() instanceof ServerPlayer player)) {
                        src.sendFailure(Component.literal("❌ Cette commande doit être exécutée en jeu."));
                        return 0;
                    }

                    // Vérifie les permissions métier
                    if (!TreePermissionChecker.hasUnlocked(player, TreeAbility.VANISH)) {
                        src.sendFailure(Component.literal("❌ Vous devez améliorer votre métier de verrier."));
                        return 0;
                    }

                    UUID id = player.getUUID();
                    if (activePlayers.contains(id)) {
                        activePlayers.remove(id);
                        player.removeEffect(MobEffects.INVISIBILITY);
                        src.sendSuccess(() -> Component.literal("❌ Mode Vanish désactivé."), true);
                    } else {
                        activePlayers.add(id);
                        src.sendSuccess(() -> Component.literal("✅ Mode Vanish activé."), true);
                    }
                    return 1;
                }));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (!activePlayers.contains(event.player.getUUID())) return;

        ServerPlayer player = (ServerPlayer) event.player;
        
        // Applique l'effet Invisibilité pour 5 secondes (100 ticks)
        // showParticles: false pour être totalement invisible (pas de bulles)
        if (!player.hasEffect(MobEffects.INVISIBILITY) || player.getEffect(MobEffects.INVISIBILITY).getDuration() < 20) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0, false, false, true));
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
