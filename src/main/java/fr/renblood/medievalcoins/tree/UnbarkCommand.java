package fr.renblood.medievalcoins.tree;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber
public class UnbarkCommand {
    private static final Map<UUID, Long> lastCommandTime = new HashMap<>();
    private static final long COOLDOWN_MS = 3000; // 3 secondes

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        d.register(Commands.literal("mc").then(Commands.literal("ability").then(Commands.literal("unbark")
                .requires(src -> src.hasPermission(0)) // accessible à tous
                .executes(c -> {
                    if (isOnCooldown(c.getSource())) return 0;

                    CommandSourceStack src = c.getSource();
                    if (!(src.getEntity() instanceof ServerPlayer player)) {
                        src.sendFailure(Component.literal("❌ Cette commande doit être exécutée en jeu."));
                        return 0;
                    }
                    if (!TreePermissionChecker.hasUnlocked(player, TreeAbility.UNBARK)) {
                        return 0; // on stoppe la commande si pas débloquée
                    }
                    ItemStack axe = player.getMainHandItem();
                    ItemStack logStack = player.getOffhandItem();

                    if (!(axe.getItem() instanceof AxeItem)) {
                        src.sendFailure(Component.literal("❌ Vous devez tenir une hache dans la main principale."));
                        return 0;
                    }

                    Block block = Block.byItem(logStack.getItem());
                    if (block == null) {
                        src.sendFailure(Component.literal("❌ Vous devez tenir une bûche dans la main secondaire."));
                        return 0;
                    }

                    // On simule l’action "écorçage"
                    BlockHitResult fakeHit = new BlockHitResult(player.position(), player.getDirection(), BlockPos.ZERO, false);
                    UseOnContext fakeContext = new UseOnContext(player, InteractionHand.MAIN_HAND, fakeHit);

                    BlockState strippedState = block.getToolModifiedState(
                            block.defaultBlockState(),
                            fakeContext,
                            ToolActions.AXE_STRIP,
                            false
                    );

                    if (strippedState == null) {
                        src.sendFailure(Component.literal("❌ Ce bois ne peut pas être écorcé."));
                        return 0;
                    }

                    int logsCount = logStack.getCount();
                    int axeDurability = axe.getMaxDamage() - axe.getDamageValue();

                    // On peut transformer au maximum (durabilité -1 pour éviter casse immédiate)
                    int maxTransformable = Math.min(logsCount, axeDurability - 1);

                    if (maxTransformable <= 0) {
                        src.sendFailure(Component.literal("❌ Durabilité insuffisante ! (" + axeDurability + " restant)"));
                        return 0;
                    }

                    // Consomme la durabilité de la hache
                    axe.hurtAndBreak(maxTransformable, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));

                    // Crée la pile de bûches écorcées
                    ItemStack strippedStack = new ItemStack(strippedState.getBlock().asItem(), maxTransformable);

                    // Retire les bûches d’origine
                    logStack.shrink(maxTransformable);

                    // Ajoute les nouvelles au joueur (ou drop si inventaire plein)
                    if (!player.addItem(strippedStack)) {
                        player.drop(strippedStack, false);
                    }

                    src.sendSuccess(() -> Component.literal("✅ " + maxTransformable + " bûche(s) écorcée(s) !"), true);
                    return 1;
                }))));
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

    public static long getCooldownRemainingMs(UUID id) {
        return Math.max(0, COOLDOWN_MS - (System.currentTimeMillis() - lastCommandTime.getOrDefault(id, 0L)));
    }
}
