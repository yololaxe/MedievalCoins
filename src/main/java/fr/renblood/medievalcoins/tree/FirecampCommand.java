package fr.renblood.medievalcoins.tree;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber
public class FirecampCommand {

    private static final Map<UUID, Long> lastCommandTime = new HashMap<>();
    
    // Configuration par défaut
    private static long cooldownMs = 30 * 60 * 1000; // 30 minutes
    private static int durationTicks = 5 * 60 * 20; // 5 minutes
    
    // Stocke les camps actifs : UUID du joueur -> Info du camp
    private static final Map<UUID, CampInfo> activeCamps = new HashMap<>();

    private static class CampInfo {
        final List<BlockPos> blocks = new ArrayList<>();
        int ticksRemaining;

        CampInfo(int durationTicks) {
            this.ticksRemaining = durationTicks;
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        // Commande principale /firecamp
        d.register(Commands.literal("firecamp")
                .requires(src -> src.hasPermission(0))
                .executes(c -> {
                    CommandSourceStack src = c.getSource();
                    if (!(src.getEntity() instanceof ServerPlayer player)) {
                        src.sendFailure(Component.literal("❌ Cette commande doit être exécutée en jeu."));
                        return 0;
                    }

                    // Vérifie les permissions métier
                    if (!TreePermissionChecker.hasUnlocked(player, TreeAbility.FIRECAMP)) {
                        src.sendFailure(Component.literal("❌ Vous devez améliorer votre métier de pêcheur."));
                        return 0;
                    }

                    // Vérifie le cooldown
                    UUID id = player.getUUID();
                    long now = System.currentTimeMillis();
                    if (lastCommandTime.containsKey(id)) {
                        long last = lastCommandTime.get(id);
                        long diff = now - last;
                        if (diff < cooldownMs) {
                            long minutesLeft = (cooldownMs - diff) / 60000;
                            src.sendFailure(Component.literal("⏳ Veuillez attendre " + (minutesLeft + 1) + " minutes avant de refaire un camp."));
                            return 0;
                        }
                    }

                    // Si un camp est déjà actif, on l'annule (ou on empêche d'en faire un autre, ici on remplace)
                    if (activeCamps.containsKey(id)) {
                        removeCamp(player.serverLevel(), id);
                    }

                    // Cherche une position valide
                    BlockPos startPos = player.blockPosition().relative(player.getDirection(), 2);
                    Direction facing = player.getDirection();
                    
                    // On veut placer :
                    // Lit (2 blocs) + Feu de camp (1 bloc)
                    // Disons : Lit devant, Feu à côté
                    // Lit : Head (startPos), Foot (startPos + facing)
                    // Feu : startPos + left/right
                    
                    BlockPos headPos = startPos;
                    BlockPos footPos = startPos.relative(facing);
                    BlockPos firePos = startPos.relative(facing.getClockWise());

                    ServerLevel level = player.serverLevel();

                    if (isAir(level, headPos) && isAir(level, footPos) && isAir(level, firePos)) {
                        // Place les blocs
                        CampInfo camp = new CampInfo(durationTicks);

                        // Lit Rouge
                        BlockState bedHead = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, facing.getOpposite());
                        BlockState bedFoot = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, facing.getOpposite());
                        
                        level.setBlock(headPos, bedHead, 3);
                        level.setBlock(footPos, bedFoot, 3);
                        
                        // Feu de camp
                        BlockState campfire = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.FACING, facing);
                        level.setBlock(firePos, campfire, 3);

                        camp.blocks.add(headPos);
                        camp.blocks.add(footPos);
                        camp.blocks.add(firePos);

                        activeCamps.put(id, camp);
                        lastCommandTime.put(id, now);
                        
                        src.sendSuccess(() -> Component.literal("✅ Campement installé pour " + (durationTicks / 1200) + " minutes !"), true);
                        return 1;
                    } else {
                        src.sendFailure(Component.literal("❌ Pas assez d'espace libre ici (besoin de 3 blocs d'air)."));
                        return 0;
                    }
                }));

        // Commande admin pour configurer : /firecamp_config set_duration <minutes> | set_cooldown <minutes>
        d.register(Commands.literal("firecamp_config")
                .requires(src -> src.hasPermission(2)) // Admin
                .then(Commands.literal("set_duration")
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                .executes(c -> {
                                    int minutes = IntegerArgumentType.getInteger(c, "minutes");
                                    durationTicks = minutes * 60 * 20;
                                    c.getSource().sendSuccess(() -> Component.literal("✅ Durée du campement définie à " + minutes + " minutes."), true);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("set_cooldown")
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                .executes(c -> {
                                    int minutes = IntegerArgumentType.getInteger(c, "minutes");
                                    cooldownMs = minutes * 60 * 1000L;
                                    c.getSource().sendSuccess(() -> Component.literal("✅ Cooldown du campement défini à " + minutes + " minutes."), true);
                                    return 1;
                                })
                        )
                )
        );
    }

    private static boolean isAir(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        long now = System.currentTimeMillis();

        // Gestion de la durée de vie des camps
        Iterator<Map.Entry<UUID, CampInfo>> it = activeCamps.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CampInfo> entry = it.next();
            CampInfo camp = entry.getValue();
            camp.ticksRemaining--;
            
            if (camp.ticksRemaining <= 0) {
                // Supprime le camp
                ServerLevel level = MedievalCoin.getServerLevel(); // Méthode utilitaire à créer ou récupérer via ServerLifecycleHooks
                if (level != null) {
                    for (BlockPos pos : camp.blocks) {
                        // On vérifie que c'est toujours nos blocs avant de casser (au cas où)
                        BlockState state = level.getBlockState(pos);
                        if (state.getBlock() == Blocks.RED_BED || state.getBlock() == Blocks.CAMPFIRE) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                    // Notifie le joueur si connecté
                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("⛺ Votre campement a disparu."));
                    }
                }
                it.remove();
            }
        }
        
        // Gestion de la notification de fin de cooldown
        // On parcourt les joueurs qui ont un cooldown actif
        // Note: lastCommandTime contient le timestamp de la dernière commande
        // On pourrait optimiser en ne vérifiant pas à chaque tick, mais pour quelques joueurs ça va
        if (event.getServer().getTickCount() % 20 == 0) { // Vérifie toutes les secondes
            Iterator<Map.Entry<UUID, Long>> cooldownIt = lastCommandTime.entrySet().iterator();
            while (cooldownIt.hasNext()) {
                Map.Entry<UUID, Long> entry = cooldownIt.next();
                long last = entry.getValue();
                
                // Si le cooldown vient juste d'expirer (dans la dernière seconde)
                // On utilise une petite marge pour ne pas spammer ou rater le moment
                if (now - last >= cooldownMs && now - last < cooldownMs + 1000) {
                    ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("✅ Vous pouvez à nouveau installer un campement !"));
                    }
                    // On ne retire pas de la map pour éviter de renvoyer le message, 
                    // mais on pourrait le faire si on veut nettoyer la mémoire (avec un flag "notified")
                    // Ici, la condition de temps (fenêtre de 1s) suffit pour ne l'envoyer qu'une fois.
                }
            }
        }
    }

    // Empêche de casser les blocs du camp
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        BlockPos pos = event.getPos();
        for (CampInfo camp : activeCamps.values()) {
            if (camp.blocks.contains(pos)) {
                event.setCanceled(true);
                event.getPlayer().displayClientMessage(Component.literal("❌ Ce campement est temporaire et incassable."), true);
                return;
            }
        }
    }

    private static void removeCamp(ServerLevel level, UUID id) {
        CampInfo camp = activeCamps.remove(id);
        if (camp != null && level != null) {
            for (BlockPos pos : camp.blocks) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
}
