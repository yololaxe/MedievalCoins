package fr.renblood.medievalcoins.tutorial;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.NpcSpawnModel;
import fr.renblood.medievalcoins.init.ItemInit;
import fr.renblood.medievalcoins.market.counter.MerchantCounterBlockEntity;
import fr.renblood.medievalcoins.market.service.MarketPermissions;
import fr.renblood.medievalcoins.network.ApiClient;
import fr.renblood.medievalcoins.network.ApiExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID)
public final class TutorialManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SET_TYPE = new TypeToken<Set<String>>() {}.getType();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("medievalcoins").resolve("tutorial_completed.json");
    private static final String PROGRESS_TAG = "RenbloodTutorial";
    private static final String NPC_SPAWN_ID_TAG = "ApiNpcSpawnId";
    private static final String QUEST_NPC_ID = "npc_1781857753283";
    private static final int COMPLETE_DISTANCE = 5;
    private static final int GUIDE_INTERVAL_TICKS = 12;
    private static final int NARRATION_DELAY_TICKS = 55;
    private static final long BREAK_CONFIRM_WINDOW_MS = TimeUnit.SECONDS.toMillis(20);

    private static final BlockPos BANK_TARGET = new BlockPos(-727, 72, -1283);
    private static final BlockPos PRAYER_TARGET = new BlockPos(-706, 75, -1227);

    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final Map<UUID, PendingBreak> PENDING_BREAKS = new HashMap<>();
    private static final Set<BreakKey> CONFIRMED_BREAKS = new HashSet<>();
    private static volatile List<NpcSpawnModel> npcSpawns = List.of();
    private static volatile long npcSpawnsLoadedAt;
    private static volatile boolean npcRefreshInProgress;
    private static Set<String> completed = new HashSet<>();
    private static boolean loaded;
    private static long ticks;

    private static final Step[] STEPS = {
            new Step("Les premieres pieces", "Avant les grandes histoires, Nagaki t'envoie voir la banque. Prends le temps d'ouvrir le comptoir et de regarder comment ton argent est range.", Objective.BANK),
            new Step("Un etal a toi", "Nagaki te confie un comptoir de test. Pose-le pres de toi, comme si tu preparais ton premier petit marche.", Objective.MERCHANT_PLACE),
            new Step("Mettre un prix", "Ouvre ton comptoir avec Maj + clic droit. Dans Stock, sélectionne une pile et clique sur Envoyer. Dans Prix, règle son tarif puis applique.", Objective.MERCHANT_CONFIGURE),
            new Step("Ranger apres l'essai", "Un comptoir de test ne doit pas rester dans le monde. Casse-le, puis confirme la suppression quand Nagaki te le demande.", Objective.MERCHANT_REMOVE),
            new Step("Les quetes du royaume", "Nagaki te montre maintenant comment les PNJ portent les histoires. Va aider Romero, puis reviens valider sa quete.", Objective.QUEST_NPC),
            new Step("Le lieu calme", "Quand une histoire demande une pause, l'autel sert de repere. Rejoins l'autel de priere.", Objective.PRAYER_LOCATION),
            new Step("La priere", "Lance la priere et reussis le petit rituel. Nagaki considerera alors que tu connais les bases.", Objective.PRAYER)
    };

    private TutorialManager() {
    }

    public static int begin(ServerPlayer player, boolean forced) {
        ensureLoaded();
        refreshNpcSpawns();
        UUID uuid = player.getUUID();
        if (!forced && completed.contains(uuid.toString())) {
            player.sendSystemMessage(prefix("Tu as deja termine le tutoriel. Un admin peut le relancer avec /mc tuto begin " + player.getGameProfile().getName() + "."));
            return 0;
        }
        completed.remove(uuid.toString());
        Session session = forced ? new Session() : ACTIVE.get(uuid);
        if (session == null) session = restoreProgress(player);
        ACTIVE.put(uuid, session);
        saveProgress(player, session);
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(prefix("Tutoriel lance. Utilise le bouton de guidage entre chaque objectif."));
        skipUnavailableNpcSteps(player, ACTIVE.get(uuid));
        sendStep(player);
        return 1;
    }

    public static int end(ServerPlayer player) {
        ensureLoaded();
        ACTIVE.remove(player.getUUID());
        progress(player).remove(PROGRESS_TAG);
        completed.add(player.getUUID().toString());
        save();
        player.sendSystemMessage(prefix("Tutoriel termine. Tu peux continuer ton aventure."));
        return 1;
    }

    public static int status(ServerPlayer player) {
        ensureLoaded();
        Session session = ACTIVE.get(player.getUUID());
        if (session != null) {
            player.sendSystemMessage(prefix("Tutoriel en cours : etape " + (session.step + 1) + "/" + STEPS.length + "."));
            sendGuide(player, session, false);
        } else if (completed.contains(player.getUUID().toString())) {
            player.sendSystemMessage(prefix("Tutoriel deja termine."));
        } else {
            player.sendSystemMessage(prefix("Tutoriel disponible avec /mc tuto start."));
        }
        return 1;
    }

    public static int toggleGuide(ServerPlayer player) {
        Session session = ACTIVE.get(player.getUUID());
        if (session == null) {
            player.sendSystemMessage(prefix("Aucun tutoriel en cours."));
            return 0;
        }
        session.guideEnabled = !session.guideEnabled;
        saveProgress(player, session);
        player.sendSystemMessage(prefix(session.guideEnabled ? "Guidage active." : "Guidage desactive."));
        if (session.guideEnabled) {
            sendGuide(player, session, false);
            emitGuideArrow(player, session);
        }
        return 1;
    }

    public static int skipNext(ServerPlayer player) {
        ensureLoaded();
        Session session = ACTIVE.get(player.getUUID());
        if (session == null) {
            player.sendSystemMessage(prefix("Aucun tutoriel en cours pour ce joueur."));
            return 0;
        }
        advance(player, session, "Etape passee par un admin.");
        return 1;
    }

    public static void recordBankWithdraw(ServerPlayer player) {
        Session session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        session.bankWithdrawDone = true;
        if (currentObjective(session) == Objective.BANK) {
            advance(player, session, "Retrait bancaire effectue.");
        }
    }

    public static void recordCounterPlaced(ServerPlayer player, BlockPos pos) {
        Session session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        session.counterPos = pos.immutable();
        if (currentObjective(session) == Objective.MERCHANT_PLACE) advance(player, session, "Comptoir pose.");
    }

    public static void recordCounterConfigured(ServerPlayer player, BlockPos pos) {
        Session session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        if (session.counterPos == null || session.counterPos.equals(pos)) {
            session.counterConfigured = true;
            if (currentObjective(session) == Objective.MERCHANT_CONFIGURE) advance(player, session, "Comptoir configure.");
        }
    }

    public static void recordPrayerCompleted(ServerPlayer player) {
        Session session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        session.prayerCompleted = true;
        if (currentObjective(session) == Objective.PRAYER) complete(player);
    }

    public static void recordRomeroQuestCompleted(ServerPlayer player) {
        Session session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        session.romeroQuestCompleted = true;
        if (currentObjective(session) == Objective.QUEST_NPC) {
            advance(player, session, "Quete de Romero validee.");
        }
    }

    public static void requestCounterBreakConfirmation(ServerPlayer player, BlockPos pos) {
        PENDING_BREAKS.put(player.getUUID(), new PendingBreak(pos.immutable(), System.currentTimeMillis()));
        player.sendSystemMessage(prefix("Confirme la suppression du comptoir de test. ")
                .append(Component.literal("[SUPPRIMER]")
                        .withStyle(style -> style.withColor(ChatFormatting.RED)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/mc tuto confirm-counter-break " + pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Supprimer ce comptoir marchand"))))));
    }

    public static int confirmCounterBreak(ServerPlayer player, BlockPos pos) {
        PendingBreak pending = PENDING_BREAKS.get(player.getUUID());
        if (pending == null || !pending.pos.equals(pos)
                || System.currentTimeMillis() - pending.createdAt > BREAK_CONFIRM_WINDOW_MS) {
            player.sendSystemMessage(prefix("Confirmation expiree. Recasse le comptoir pour confirmer."));
            return 0;
        }
        if (!(player.level().getBlockEntity(pos) instanceof MerchantCounterBlockEntity counter)) {
            player.sendSystemMessage(prefix("Aucun comptoir marchand a cette position."));
            return 0;
        }
        if (!MarketPermissions.isAdmin(player) && !counter.isOwner(player.getUUID())) {
            player.sendSystemMessage(prefix("Tu ne peux pas supprimer ce comptoir."));
            return 0;
        }
        PENDING_BREAKS.remove(player.getUUID());
        BreakKey key = new BreakKey(player.getUUID(), pos.immutable());
        CONFIRMED_BREAKS.add(key);
        if (player.level().destroyBlock(pos, true, player)) {
            recordCounterRemoved(player, pos);
        }
        CONFIRMED_BREAKS.remove(key);
        return 1;
    }

    public static boolean consumeConfirmedCounterBreak(ServerPlayer player, BlockPos pos) {
        return CONFIRMED_BREAKS.remove(new BreakKey(player.getUUID(), pos.immutable()));
    }

    public static void recordCounterRemoved(ServerPlayer player, BlockPos pos) {
        Session session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        if (session.counterPos == null || session.counterPos.equals(pos)) {
            session.counterRemoved = true;
            if (currentObjective(session) == Objective.MERCHANT_REMOVE) advance(player, session, "Comptoir supprime.");
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ensureLoaded();
        if (!completed.contains(player.getUUID().toString())) {
            player.sendSystemMessage(prefix("Tutoriel de Nagaki disponible avec /mc tuto start."));
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        String npcId = resolveNpcId(event.getTarget());
        if (npcId == null) return;
        if (QUEST_NPC_ID.equalsIgnoreCase(npcId)) {
            session.metQuestNpc = true;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        if (ACTIVE.isEmpty()) return;
        if (ticks % 200 == 0) refreshNpcSpawns();

        MinecraftServer server = event.getServer();
        ACTIVE.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) return true;
            Session session = entry.getValue();
            drainNarration(player, session);
            skipUnavailableNpcSteps(player, session);
            if (isCurrentObjectiveComplete(player, session)) {
                advance(player, session, "Objectif valide.");
                return false;
            }
            if (session.guideEnabled && ticks - session.lastGuideTick >= GUIDE_INTERVAL_TICKS) {
                session.lastGuideTick = ticks;
                sendGuide(player, session, true);
                emitGuideArrow(player, session);
            }
            return false;
        });
    }

    private static void advance(ServerPlayer player, Session session, String reason) {
        player.sendSystemMessage(prefix(reason));
        session.step++;
        if (session.step >= STEPS.length) {
            complete(player);
            return;
        }
        skipUnavailableNpcSteps(player, session);
        sendStep(player);
    }

    private static void sendStep(ServerPlayer player) {
        Session session = ACTIVE.get(player.getUUID());
        if (session == null || session.step >= STEPS.length) return;
        Step step = STEPS[session.step];
        session.narration.clear();
        session.nextNarrationTick = ticks;
        if (step.objective == Objective.MERCHANT_PLACE && !session.counterGiven) {
            giveCounter(player, session);
        }
        saveProgress(player, session);
        queueNarration(session, "[Nagaki] " + (session.step + 1) + "/" + STEPS.length + " - " + step.title, true);
        for (String line : splitNarration(step.message)) {
            queueNarration(session, line, false);
        }
        queueNarration(session, "__GUIDE__", false);
    }

    private static void giveCounter(ServerPlayer player, Session session) {
        ItemStack counter = new ItemStack(ItemInit.MERCHANT_COUNTER.get());
        if (!player.getInventory().add(counter)) {
            player.drop(counter, false);
        }
        session.counterGiven = true;
        player.sendSystemMessage(prefix("Nagaki t'a donne un comptoir marchand de test. Supprime-le apres l'objectif."));
    }

    private static void sendGuide(ServerPlayer player, Session session, boolean actionBar) {
        Step step = STEPS[Math.min(session.step, STEPS.length - 1)];
        Target target = targetFor(player, step.objective, session);
        String message = target == null
                ? step.title + " : effectue l'action demandee."
                : "-> " + target.label + " : " + target.pos.getX() + " " + target.pos.getY() + " " + target.pos.getZ();
        player.sendSystemMessage(prefix(message), actionBar);
    }

    private static boolean isCurrentObjectiveComplete(ServerPlayer player, Session session) {
        Objective objective = currentObjective(session);
        return switch (objective) {
            case BANK -> session.bankWithdrawDone;
            case MERCHANT_PLACE -> session.counterPos != null;
            case MERCHANT_CONFIGURE -> session.counterConfigured;
            case MERCHANT_REMOVE -> session.counterRemoved;
            case QUEST_NPC -> session.romeroQuestCompleted;
            case PRAYER_LOCATION -> near(player, targetFor(player, objective, session), COMPLETE_DISTANCE);
            case PRAYER -> session.prayerCompleted;
        };
    }

    private static void queueNarration(Session session, String text, boolean header) {
        session.narration.add(new NarrationLine(text, header));
    }

    private static void drainNarration(ServerPlayer player, Session session) {
        if (session.narration.isEmpty() || ticks < session.nextNarrationTick) return;
        NarrationLine line = session.narration.poll();
        session.nextNarrationTick = ticks + NARRATION_DELAY_TICKS;
        if ("__GUIDE__".equals(line.text)) {
            player.sendSystemMessage(Component.literal("[Activer la fleche]")
                    .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mc tuto guide"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Afficher et suivre la prochaine cible du tutoriel")))));
            sendGuide(player, session, false);
            playLineSound(player);
            return;
        }
        player.sendSystemMessage(Component.literal(line.text).withStyle(line.header ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
        playLineSound(player);
    }

    private static List<String> splitNarration(String text) {
        if (text == null || text.isBlank()) return List.of();
        return List.of(text.split("(?<=\\.)\\s+"));
    }

    private static void showStepTitle(ServerPlayer player, int index, Step step) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 55, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(index + "/" + STEPS.length)
                .withStyle(ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(step.title)
                .withStyle(ChatFormatting.YELLOW)));
        player.playNotifySound(SoundEvents.UI_TOAST_IN, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    private static void playLineSound(ServerPlayer player) {
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.25F, 1.35F);
    }

    private static void skipUnavailableNpcSteps(ServerPlayer player, Session session) {
        while (session.step < STEPS.length) {
            Objective objective = currentObjective(session);
            if (objective != Objective.QUEST_NPC) return;
            String npcId = QUEST_NPC_ID;
            if (npcSpawnsLoadedAt == 0 || hasActiveNpcSpawn(player, npcId)) return;
            player.sendSystemMessage(prefix("PNJ indisponible dans ce monde, etape ignoree : " + STEPS[session.step].title + "."));
            session.step++;
        }
    }

    private static Target targetFor(ServerPlayer player, Objective objective, Session session) {
        return switch (objective) {
            case BANK -> new Target("Banque", BANK_TARGET);
            case MERCHANT_PLACE -> new Target("Pose le comptoir ici", player.blockPosition());
            case MERCHANT_CONFIGURE, MERCHANT_REMOVE -> session.counterPos == null ? null : new Target("Ton comptoir", session.counterPos);
            case QUEST_NPC -> targetNpc(player, QUEST_NPC_ID, "PNJ de quete", null);
            case PRAYER_LOCATION, PRAYER -> new Target("Autel de priere", PRAYER_TARGET);
        };
    }

    private static Target targetNpc(ServerPlayer player, String npcId, String label, BlockPos fallback) {
        NpcSpawnModel spawn = findActiveSpawn(player, npcId);
        if (spawn != null) return new Target(label, new BlockPos((int) Math.round(spawn.x), (int) Math.round(spawn.y), (int) Math.round(spawn.z)));
        return fallback == null ? null : new Target(label, fallback);
    }

    private static boolean hasActiveNpcSpawn(ServerPlayer player, String npcId) {
        return findActiveSpawn(player, npcId) != null;
    }

    private static NpcSpawnModel findActiveSpawn(ServerPlayer player, String npcId) {
        for (NpcSpawnModel spawn : npcSpawns) {
            if (spawn == null || !spawn.active || spawn.npcId == null || !spawn.npcId.equalsIgnoreCase(npcId)) continue;
            if (matchesWorld(player, spawn)) return spawn;
        }
        return null;
    }

    private static boolean matchesWorld(ServerPlayer player, NpcSpawnModel spawn) {
        String dimension = player.level().dimension().location().toString();
        return blank(spawn.dimension) && blank(spawn.world)
                || equalsIgnoreCase(spawn.dimension, dimension)
                || equalsIgnoreCase(spawn.world, dimension)
                || equalsIgnoreCase(spawn.world, player.level().dimension().location().getPath());
    }

    private static boolean near(ServerPlayer player, Target target, int distance) {
        return target != null && player.blockPosition().distSqr(target.pos) <= distance * distance;
    }

    private static Objective currentObjective(Session session) {
        return STEPS[Math.min(session.step, STEPS.length - 1)].objective;
    }

    private static String resolveNpcId(Entity entity) {
        String spawnId = entity.getPersistentData().getString(NPC_SPAWN_ID_TAG);
        if (spawnId == null || spawnId.isBlank()) return null;
        for (NpcSpawnModel spawn : npcSpawns) {
            if (spawn != null && equalsIgnoreCase(spawn.spawnId, spawnId)) return spawn.npcId;
        }
        return spawnId;
    }

    private static void emitGuideArrow(ServerPlayer player, Session session) {
        Target target = targetFor(player, currentObjective(session), session);
        if (target == null || !(player.level() instanceof ServerLevel level)) return;
        Vec3 start = player.position().add(0.0, 1.35, 0.0);
        Vec3 toTarget = Vec3.atCenterOf(target.pos).subtract(start);
        Vec3 horizontal = new Vec3(toTarget.x, 0.0, toTarget.z);
        if (horizontal.lengthSqr() < 0.01) return;
        Vec3 forward = horizontal.normalize();
        Vec3 side = new Vec3(-forward.z, 0.0, forward.x);

        for (int i = 1; i <= 7; i++) {
            Vec3 point = start.add(forward.scale(0.45 * i));
            level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        Vec3 tip = start.add(forward.scale(3.6));
        for (int i = 0; i < 4; i++) {
            Vec3 left = tip.subtract(forward.scale(0.22 * i)).add(side.scale(0.16 * i));
            Vec3 right = tip.subtract(forward.scale(0.22 * i)).subtract(side.scale(0.16 * i));
            level.sendParticles(ParticleTypes.END_ROD, left.x, left.y, left.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, right.x, right.y, right.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void refreshNpcSpawns() {
        if (npcRefreshInProgress) return;
        npcRefreshInProgress = true;
        ApiExecutor.execute(() -> {
            try {
                npcSpawns = ApiClient.getNpcSpawns(null, true);
                npcSpawnsLoadedAt = System.currentTimeMillis();
            } catch (Exception exception) {
                MedievalCoin.LOGGER.warn("Tutoriel: spawns PNJ indisponibles: {}", exception.getMessage());
            } finally {
                npcRefreshInProgress = false;
            }
        });
    }

    private static void complete(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
        progress(player).remove(PROGRESS_TAG);
        completed.add(player.getUUID().toString());
        save();
        player.sendSystemMessage(prefix("Tutoriel termine. Tu peux continuer ton aventure."));
    }

    private static MutableComponent prefix(String message) {
        return Component.literal("[Nagaki] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(message).withStyle(ChatFormatting.YELLOW));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean equalsIgnoreCase(String first, String second) {
        return first != null && second != null && first.equalsIgnoreCase(second);
    }

    private static net.minecraft.nbt.CompoundTag progress(ServerPlayer player) {
        var data = player.getPersistentData();
        String key = net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG;
        if (!data.contains(key)) data.put(key, new net.minecraft.nbt.CompoundTag());
        return data.getCompound(key);
    }

    private static Session restoreProgress(ServerPlayer player) {
        var tag = progress(player).getCompound(PROGRESS_TAG);
        var session = new Session();
        session.step = Math.max(0, Math.min(STEPS.length - 1, tag.getInt("step")));
        session.guideEnabled = tag.getBoolean("guide");
        session.counterGiven = tag.getBoolean("counterGiven");
        if (tag.contains("counterPos")) session.counterPos = BlockPos.of(tag.getLong("counterPos"));
        return session;
    }

    private static void saveProgress(ServerPlayer player, Session session) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("step", session.step);
        tag.putBoolean("guide", session.guideEnabled);
        tag.putBoolean("counterGiven", session.counterGiven);
        if (session.counterPos != null) tag.putLong("counterPos", session.counterPos.asLong());
        progress(player).put(PROGRESS_TAG, tag);
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(FILE)) {
            completed = new HashSet<>();
            return;
        }
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Set<String> values = GSON.fromJson(reader, SET_TYPE);
            completed = values == null ? new HashSet<>() : new HashSet<>(values);
        } catch (Exception e) {
            completed = new HashSet<>();
            MedievalCoin.LOGGER.warn("Impossible de charger les joueurs ayant termine le tutoriel", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(completed, SET_TYPE, writer);
            }
        } catch (Exception e) {
            MedievalCoin.LOGGER.warn("Impossible de sauvegarder les joueurs ayant termine le tutoriel", e);
        }
    }

    private enum Objective {
        BANK,
        MERCHANT_PLACE,
        MERCHANT_CONFIGURE,
        MERCHANT_REMOVE,
        QUEST_NPC,
        PRAYER_LOCATION,
        PRAYER
    }

    private record Step(String title, String message, Objective objective) {
    }

    private record Target(String label, BlockPos pos) {
    }

    private record PendingBreak(BlockPos pos, long createdAt) {
    }

    private record BreakKey(UUID playerId, BlockPos pos) {
    }

    private record NarrationLine(String text, boolean header) {
    }

    private static final class Session {
        private int step;
        private boolean guideEnabled;
        private boolean counterGiven;
        private boolean bankWithdrawDone;
        private boolean metQuestNpc;
        private boolean romeroQuestCompleted;
        private boolean counterConfigured;
        private boolean counterRemoved;
        private boolean prayerCompleted;
        private long lastGuideTick;
        private long nextNarrationTick;
        private BlockPos counterPos;
        private final Queue<NarrationLine> narration = new ArrayDeque<>();
    }
}
