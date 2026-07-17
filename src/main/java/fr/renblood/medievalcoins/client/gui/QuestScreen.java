package fr.renblood.medievalcoins.client.gui;

import fr.renblood.medievalcoins.api.model.PlayerQuestStateModel;
import fr.renblood.medievalcoins.api.model.PlayerModel;
import fr.renblood.medievalcoins.api.model.QuestModel;
import fr.renblood.medievalcoins.network.ApiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.HashSet;

@OnlyIn(Dist.CLIENT)
public class QuestScreen extends Screen {

    private static final String[] CATEGORIES = {"Main", "Secondary", "Tertiary", "FullRP", "SemiRP"};
    private static final int BACKDROP = 0xCC0B0A08;
    private static final int PANEL = 0xDD17130F;
    private static final int PANEL_SOFT = 0xAA241D15;
    private static final int PANEL_BORDER = 0xFF5B4630;
    private static final int PANEL_BORDER_LIGHT = 0xFF9D7A45;
    private static final int TEXT = 0xFFEFE2C2;
    private static final int TEXT_MUTED = 0xFFB9A98F;
    private static final int TEXT_DIM = 0xFF7D715F;
    private static final int GOLD = 0xFFFFC857;
    private static final int GREEN = 0xFF75D06A;
    private static final int BLUE = 0xFF73C7E8;
    private static final int RED = 0xFFE37B63;
    private static final int AUTO_REFRESH_TICKS = 20 * 60;

    private String currentCategory = "Main";
    private QuestList list;
    private QuestModel selectedQuest;
    private PlayerQuestStateModel selectedState;
    private Button finishButton;
    private Button refreshButton;
    private boolean refreshing;
    private int autoRefreshTicks;
    private String statusMessage = "Cliquez sur le bouton de mise a jour pour recuperer les quetes.";

    private static final Map<String, List<PlayerQuestStateModel>> QUEST_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Set<Integer>> OBJECTIVE_PROGRESS = new ConcurrentHashMap<>();

    public static void updateObjectiveProgress(Map<String, List<Integer>> progress) {
        OBJECTIVE_PROGRESS.clear();
        if (progress == null) return;
        progress.forEach((questId, indexes) -> OBJECTIVE_PROGRESS.put(
                questId,
                indexes == null ? Set.of() : new HashSet<>(indexes)
        ));
    }

    public QuestScreen() {
        super(Component.translatable("screen.medieval_coins.quests"));
    }

    @Override
    protected void init() {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = this.minecraft.font;

        super.init();

        int categoryWidth = Math.min(72, Math.max(58, (this.width - 44) / CATEGORIES.length - 4));
        int totalCategoryWidth = CATEGORIES.length * categoryWidth + (CATEGORIES.length - 1) * 4;
        int x = this.width / 2 - totalCategoryWidth / 2;
        int y = 46;

        for (int i = 0; i < CATEGORIES.length; i++) {
            String cat = CATEGORIES[i];
            this.addRenderableWidget(Button.builder(Component.literal(cat), b -> loadCategory(cat))
                    .bounds(x + (i * (categoryWidth + 4)), 18, categoryWidth, 20)
                    .build());
        }

        int listWidth = Math.min(205, Math.max(160, this.width / 3));
        this.list = new QuestList(this.minecraft, listWidth, this.height, y, this.height - 18, 28);
        this.list.setLeftPos(18);
        this.addRenderableWidget(this.list);

        this.finishButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.medieval_coins.quests.finish"), b -> openFinishCommand())
                .bounds(this.width - 112, 49, 88, 20)
                .build());
        this.finishButton.visible = false;

        this.refreshButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.medieval_coins.quests.refresh"), b -> refreshCurrentCategory())
                .bounds(this.width - 126, 18, 108, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.medieval_coins.quests.completed"), b -> loadCategory("Completed"))
                .bounds(this.width - 136, this.height - 42, 118, 20)
                .build());

        loadCategory(currentCategory);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (++autoRefreshTicks >= AUTO_REFRESH_TICKS) {
            autoRefreshTicks = 0;
            refreshCurrentCategory(false);
        }
    }

    private void loadCategory(String category) {
        this.currentCategory = category;
        this.selectedQuest = null;
        this.selectedState = null;
        updateFinishButtonState();
        if (this.list != null) this.list.clear();

        List<PlayerQuestStateModel> cached = QUEST_CACHE.get(category);
        if (cached != null) {
            populateList(cached);
            return;
        }

        refreshCurrentCategory();
    }

    private void refreshCurrentCategory() {
        refreshCurrentCategory(true);
    }

    private void refreshCurrentCategory(boolean clearList) {
        if (refreshing) return;

        String category = currentCategory;
        refreshing = true;
        statusMessage = "Mise a jour des quetes...";
        updateRefreshButtonState();
        if (clearList && this.list != null) this.list.clear();

        fr.renblood.medievalcoins.network.ApiExecutor.execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            try {
                if (mc.player == null) {
                    mc.execute(() -> finishRefresh(category, null, "Joueur introuvable."));
                    return;
                }

                String uuid = mc.player.getGameProfile().getId().toString();
                List<PlayerQuestStateModel> quests;
                if ("Completed".equals(category)) {
                    PlayerModel profile = ApiClient.getPlayer(uuid);
                    String playerId = profile != null && profile.id != null ? profile.id : uuid;
                    quests = ApiClient.getPlayerQuests(playerId);
                    if (quests == null) quests = new ArrayList<>();
                    quests.removeIf(state -> state == null || !"COMPLETED".equalsIgnoreCase(state.status));
                    attachQuestDetails(quests, "");
                } else {
                    quests = ApiClient.getActiveQuests(uuid, category);
                }
                if (quests == null) quests = new ArrayList<>();

                if (quests.isEmpty() && !"Completed".equals(category)) {
                    quests = buildStatesFromAllQuests(category);
                } else if (!"Completed".equals(category)) {
                    attachQuestDetails(quests, category);
                }

                List<PlayerQuestStateModel> loadedQuests = quests;
                QUEST_CACHE.put(category, loadedQuests);
                mc.execute(() -> finishRefresh(category, loadedQuests, null));
            } catch (Exception e) {
                e.printStackTrace();
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                mc.execute(() -> finishRefresh(category, null, "Erreur API: " + message));
            }
        });
    }

    private void finishRefresh(String category, List<PlayerQuestStateModel> quests, String error) {
        refreshing = false;
        updateRefreshButtonState();
        if (!category.equals(currentCategory)) return;

        if (error != null) {
            showStatus(error);
        } else {
            populateList(quests);
        }
    }

    private void attachQuestDetails(List<PlayerQuestStateModel> quests, String category) {
        Map<String, QuestModel> allQuests = new HashMap<>();
        try {
            allQuests = indexAllQuests(category);
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (PlayerQuestStateModel pq : quests) {
            if (pq == null || pq.quest_id == null || pq.quest_id.isEmpty()) continue;
            pq.questDetails = allQuests.get(pq.quest_id);
            if (pq.questDetails != null) continue;
            try {
                pq.questDetails = ApiClient.getQuestDetails(pq.quest_id);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private List<PlayerQuestStateModel> buildStatesFromAllQuests(String category) throws Exception {
        List<PlayerQuestStateModel> states = new ArrayList<>();
        List<QuestModel> allQuests = ApiClient.getAllQuests(category);
        if (allQuests == null) return states;

        for (QuestModel quest : allQuests) {
            if (quest == null) continue;
            PlayerQuestStateModel state = new PlayerQuestStateModel();
            state.quest_id = quest.questId != null ? quest.questId : quest.name;
            state.status = "AVAILABLE";
            state.questDetails = quest;
            states.add(state);
        }
        return states;
    }

    private Map<String, QuestModel> indexAllQuests(String category) throws Exception {
        Map<String, QuestModel> result = new HashMap<>();
        List<QuestModel> allQuests = ApiClient.getAllQuests(category);
        if (allQuests == null) return result;

        for (QuestModel quest : allQuests) {
            if (quest == null) continue;
            if (quest.questId != null) result.put(quest.questId, quest);
            if (quest.name != null) result.putIfAbsent(quest.name, quest);
        }
        return result;
    }

    private void populateList(List<PlayerQuestStateModel> quests) {
        if (this.list == null) return;
        this.list.clear();
        this.selectedQuest = null;
        this.selectedState = null;
        updateFinishButtonState();

        int added = 0;
        for (PlayerQuestStateModel pq : quests) {
            if (pq != null && pq.questDetails != null) {
                this.list.add(new QuestEntry(pq));
                added++;
            }
        }

        if (added == 0) {
            showStatus("Aucune quete dans la categorie " + currentCategory + ".");
        } else {
            this.statusMessage = "";
        }
    }

    private void showStatus(String message) {
        if (this.list != null) this.list.clear();
        this.selectedQuest = null;
        this.selectedState = null;
        this.statusMessage = message;
        updateFinishButtonState();
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = this.minecraft.font;

        this.renderBackground(gg);
        gg.fill(0, 0, this.width, this.height, BACKDROP);

        renderFrame(gg, font);
        for (Renderable renderable : this.renderables) {
            renderable.render(gg, mouseX, mouseY, partialTick);
        }

        Font fontRenderer = this.font;
        if (fontRenderer == null) return;

        int listWidth = this.list != null ? this.list.getWidth() : Math.min(205, Math.max(160, this.width / 3));
        int detailX = 18 + listWidth + 18;
        int detailY = 60;
        int detailWidth = this.width - detailX - 34;
        updateFinishButtonPosition(detailX, detailWidth);
        updateFinishButtonState();

        if (selectedQuest != null) {
            renderQuestDetails(gg, fontRenderer, detailX, detailY, detailWidth);
        } else {
            String message = statusMessage == null || statusMessage.isEmpty()
                    ? "Selectionnez une quete"
                    : statusMessage;
            gg.drawString(fontRenderer, "Aucune quete selectionnee", detailX, detailY, TEXT, false);
            detailY += 18;
            drawWrappedText(gg, fontRenderer, message, detailX, detailY, detailWidth, TEXT_MUTED, 11);
        }
    }

    private void renderFrame(GuiGraphics gg, Font fontRenderer) {
        int listWidth = this.list != null ? this.list.getWidth() : Math.min(205, Math.max(160, this.width / 3));
        int left = 12;
        int top = 10;
        int bottom = this.height - 10;
        int listLeft = 18;
        int listTop = 46;
        int listBottom = this.height - 18;
        int detailLeft = listLeft + listWidth + 12;
        int detailRight = this.width - 18;

        drawPanel(gg, left, top, this.width - 12, bottom, PANEL);
        gg.drawCenteredString(fontRenderer, "Journal de quetes", this.width / 2, 8, GOLD);

        drawPanel(gg, listLeft - 4, listTop - 4, listLeft + listWidth + 4, listBottom + 4, PANEL_SOFT);
        gg.drawString(fontRenderer, Component.translatable("gui.medieval_coins.quests.category"), listLeft, 34, TEXT_DIM, false);
        gg.drawString(fontRenderer, currentCategory, listLeft + 58, 34, GOLD, false);

        drawPanel(gg, detailLeft, listTop - 4, detailRight, listBottom + 4, PANEL_SOFT);
        gg.fill(detailLeft + 1, listTop - 3, detailRight - 1, listTop + 18, 0x66302017);
        gg.drawString(fontRenderer, selectedQuest != null
                ? Component.translatable("gui.medieval_coins.quests.details")
                : Component.translatable("screen.medieval_coins.quests"), detailLeft + 12, listTop + 3, TEXT_MUTED, false);
    }

    private void drawPanel(GuiGraphics gg, int left, int top, int right, int bottom, int color) {
        gg.fill(left, top, right, bottom, color);
        gg.fill(left, top, right, top + 1, PANEL_BORDER_LIGHT);
        gg.fill(left, bottom - 1, right, bottom, PANEL_BORDER);
        gg.fill(left, top, left + 1, bottom, PANEL_BORDER);
        gg.fill(right - 1, top, right, bottom, PANEL_BORDER);
    }

    private void renderQuestDetails(GuiGraphics gg, Font fontRenderer, int detailX, int detailY, int detailWidth) {
        String playerName = this.minecraft.player != null ? this.minecraft.player.getName().getString() : "Joueur";
        String npcName = selectedQuest.npcName != null ? selectedQuest.npcName
                : selectedQuest.npc != null ? selectedQuest.npc : "PNJ";
        String questName = selectedQuest.name != null ? selectedQuest.name : "Quete sans nom";
        String questId = selectedState != null ? selectedState.quest_id : selectedQuest.questId;

        gg.drawString(fontRenderer, fontRenderer.plainSubstrByWidth(questName, detailWidth), detailX, detailY, TEXT, false);
        detailY += 12;
        gg.drawString(fontRenderer, "ID: " + safeText(questId, "non renseigne"), detailX, detailY, TEXT_DIM, false);
        detailY += 12;
        renderMetaLine(gg, fontRenderer, detailX, detailY, detailWidth);
        detailY += 18;

        String desc = selectedQuest.getDescription();
        if (desc != null && !desc.isEmpty()) {
            desc = desc.replace("{player}", playerName).replace("{npc}", npcName);
            detailY = drawWrappedText(gg, fontRenderer, desc, detailX, detailY, detailWidth, TEXT_MUTED, 11);
        }

        detailY += 12;
        detailY = renderSectionTitle(gg, fontRenderer, "Objectifs", detailX, detailY, detailWidth, BLUE);

        if (selectedQuest.objectives != null && !selectedQuest.objectives.isEmpty()) {
            for (int index = 0; index < selectedQuest.objectives.size(); index++) {
                QuestModel.Objective obj = selectedQuest.objectives.get(index);
                detailY = renderObjective(gg, fontRenderer, obj, index, detailX, detailY, detailWidth, playerName, npcName);
            }
        } else {
            detailY = drawBulletLine(gg, fontRenderer, "Aucun objectif renseigne", detailX, detailY, detailWidth, TEXT_DIM);
        }

        detailY += 10;
        detailY = renderSectionTitle(gg, fontRenderer, "Recompenses", detailX, detailY, detailWidth, GOLD);

        boolean hasReward = false;
        if (selectedQuest.money > 0) {
            detailY = drawBulletLine(gg, fontRenderer, selectedQuest.money + " pieces", detailX, detailY, detailWidth, GOLD);
            hasReward = true;
        }
        if (selectedQuest.xp != null && selectedQuest.xp.amount > 0) {
            detailY = drawBulletLine(gg, fontRenderer, selectedQuest.xp.amount + " XP " + selectedQuest.xp.job, detailX, detailY, detailWidth, GREEN);
            hasReward = true;
        }
        if (selectedQuest.rewards != null) {
            for (QuestModel.Reward reward : selectedQuest.rewards) {
                String itemName = resolveItemName(reward.itemId);
                detailY = drawBulletLine(gg, fontRenderer, reward.count + "x " + itemName, detailX, detailY, detailWidth, BLUE);
                hasReward = true;
            }
        }
        if (!hasReward) {
            drawBulletLine(gg, fontRenderer, "Aucune recompense renseignee", detailX, detailY, detailWidth, TEXT_DIM);
        }
    }

    private void renderMetaLine(GuiGraphics gg, Font fontRenderer, int x, int y, int width) {
        String status = selectedState != null ? safeText(selectedState.status, "ACTIVE") : "ACTIVE";
        int statusColor = statusColor(status);
        gg.fill(x, y + 1, x + 6, y + 7, statusColor);
        gg.drawString(fontRenderer, statusLabel(status), x + 10, y, statusColor, false);

        String meta = safeText(selectedQuest.npcName, safeText(selectedQuest.npc, "PNJ non renseigne"));
        if (selectedQuest.type != null && !selectedQuest.type.isEmpty()) meta += "  |  " + selectedQuest.type;
        gg.drawString(fontRenderer, fontRenderer.plainSubstrByWidth(meta, width - 86), x + 86, y, TEXT_DIM, false);
    }

    private int renderSectionTitle(GuiGraphics gg, Font fontRenderer, String title, int x, int y, int width, int accentColor) {
        gg.fill(x, y + 5, x + 14, y + 6, accentColor);
        gg.drawString(fontRenderer, title, x + 20, y, TEXT, false);
        gg.fill(x + 20 + fontRenderer.width(title) + 8, y + 5, x + width, y + 6, 0x665B4630);
        return y + 14;
    }

    private int renderObjective(GuiGraphics gg, Font fontRenderer, QuestModel.Objective obj, int index, int x, int y, int width, String playerName, String npcName) {
        String questId = selectedState != null ? selectedState.quest_id : selectedQuest.questId;
        boolean completed = selectedState != null && "COMPLETED".equalsIgnoreCase(selectedState.status)
                || OBJECTIVE_PROGRESS.getOrDefault(questId, Set.of()).contains(index);
        String prefix = completed ? "[OK] " : "[ ] ";
        int color = completed ? GREEN : TEXT_MUTED;

        if (obj.description != null && !obj.description.isEmpty()) {
            String objDesc = obj.description.replace("{player}", playerName).replace("{npc}", npcName);
            y = drawBulletLine(gg, fontRenderer, prefix + objDesc, x, y, width, color);
        }

        if ("ITEM".equals(obj.type) && obj.items != null) {
            for (QuestModel.ItemRequirement req : obj.items) {
                y = drawBulletLine(gg, fontRenderer, prefix + "Ramener " + req.count + "x " + resolveItemName(req.itemId), x, y, width, color);
            }
            return y;
        }
        if (obj.description != null && !obj.description.isEmpty()) return y;
        if ("LOCATION".equals(obj.type)) {
            return drawBulletLine(gg, fontRenderer, prefix + "Se rendre en " + safeText(obj.coord, "position inconnue"), x, y, width, color);
        }
        if ("TALK".equals(obj.type)) {
            return drawBulletLine(gg, fontRenderer, prefix + "Parler au PNJ " + safeText(obj.getTargetNpcId(), "inconnu"), x, y, width, color);
        }
        if ("CONSTRUCTION".equalsIgnoreCase(obj.type) || "BUILD".equalsIgnoreCase(obj.type)) {
            return drawBulletLine(gg, fontRenderer, prefix + "Construction", x, y, width, color);
        }
        if ("RP".equalsIgnoreCase(obj.type) || "ROLEPLAY".equalsIgnoreCase(obj.type)
                || "ROLE_PLAY".equalsIgnoreCase(obj.type)) {
            return drawBulletLine(gg, fontRenderer, prefix + "RP", x, y, width, color);
        }

        return drawBulletLine(gg, fontRenderer, prefix + safeText(obj.type, "Objectif") + " "
                + safeText(obj.getTargetNpcId(), ""), x, y, width, color);
    }

    private int drawBulletLine(GuiGraphics gg, Font fontRenderer, String text, int x, int y, int width, int color) {
        gg.fill(x, y + 4, x + 4, y + 8, color);
        return drawWrappedText(gg, fontRenderer, text, x + 12, y, width - 12, color, 11);
    }

    private int drawWrappedText(GuiGraphics gg, Font fontRenderer, String text, int x, int y, int width, int color, int lineHeight) {
        for (FormattedCharSequence line : fontRenderer.split(Component.literal(text), width)) {
            gg.drawString(fontRenderer, line, x, y, color, false);
            y += lineHeight;
        }
        return y;
    }

    private int statusColor(String status) {
        if ("COMPLETED".equals(status)) return GREEN;
        if ("AVAILABLE".equals(status)) return TEXT_MUTED;
        if ("FAILED".equals(status)) return RED;
        return BLUE;
    }

    private String statusLabel(String status) {
        if ("COMPLETED".equals(status)) return "Terminee";
        if ("AVAILABLE".equals(status)) return "Disponible";
        if ("FAILED".equals(status)) return "Echouee";
        return "En cours";
    }

    private String resolveItemName(String itemId) {
        if (itemId == null || itemId.isEmpty()) return "item inconnu";
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
            if (item != null) return item.getDescription().getString();
        } catch (Exception ignored) {
        }
        return itemId;
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private void openFinishCommand() {
        if (this.minecraft == null || selectedQuest == null) return;
        String questId = selectedState != null ? selectedState.quest_id : selectedQuest.questId;
        if (questId == null || questId.isEmpty()) return;
        this.minecraft.setScreen(new ChatScreen("/mc quest finish " + quoteCommandArg(questId) + " "));
    }

    private void updateFinishButtonPosition(int detailX, int detailWidth) {
        if (this.finishButton == null) return;
        this.finishButton.setX(Math.max(detailX, detailX + detailWidth - 88));
        this.finishButton.setY(49);
    }

    private void updateFinishButtonState() {
        if (this.finishButton == null) return;
        boolean visible = selectedQuest != null && isManualApprovalQuest(selectedQuest);
        this.finishButton.visible = visible;
        this.finishButton.active = visible;
    }

    private void updateRefreshButtonState() {
        if (this.refreshButton == null) return;
        this.refreshButton.active = !refreshing;
        this.refreshButton.setMessage(Component.translatable(refreshing
                ? "gui.medieval_coins.quests.refreshing"
                : "gui.medieval_coins.quests.refresh"));
    }

    private boolean isManualApprovalQuest(QuestModel quest) {
        if (quest == null || quest.objectives == null) return false;
        for (QuestModel.Objective objective : quest.objectives) {
            if (objective == null) continue;
            String type = normalize(objective.type);
            if (type.contains("construct") || type.contains("rp")) return true;
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replace(" ", "").replace("_", "").replace("-", "");
    }

    private String quoteCommandArg(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    class QuestList extends ObjectSelectionList<QuestEntry> {
        public QuestList(Minecraft mc, int width, int height, int y0, int y1, int itemHeight) {
            super(mc, width, height, y0, y1, itemHeight);
            this.setRenderBackground(false);
            this.setRenderTopAndBottom(false);
        }

        @Override
        public int getRowWidth() {
            return this.width - 12;
        }

        public void clear() {
            this.clearEntries();
        }

        public void add(QuestEntry entry) {
            this.addEntry(entry);
        }
    }

    class QuestEntry extends ObjectSelectionList.Entry<QuestEntry> {
        private final PlayerQuestStateModel state;

        public QuestEntry(PlayerQuestStateModel state) {
            this.state = state;
        }

        @Override
        public void render(GuiGraphics gg, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
            String name = state.questDetails != null && state.questDetails.name != null ? state.questDetails.name : safeText(state.quest_id, "Quete");
            boolean selected = QuestScreen.this.list != null && QuestScreen.this.list.getSelected() == this;
            int color = selected ? TEXT : TEXT_MUTED;
            int statusColor = statusColor(state.status);

            Font fontRenderer = QuestScreen.this.font;
            if (fontRenderer == null) fontRenderer = Minecraft.getInstance().font;

            if (fontRenderer != null) {
                int rowColor = selected ? 0x663E2D1B : (isHovered ? 0x442D2419 : 0x00000000);
                if (rowColor != 0) gg.fill(left + 1, top + 1, left + width - 1, top + height - 1, rowColor);
                gg.fill(left + 5, top + 7, left + 10, top + 12, statusColor);

                String displayName = fontRenderer.plainSubstrByWidth(name, width - 24);
                if (displayName.length() < name.length()) displayName += "...";
                gg.drawString(fontRenderer, displayName, left + 16, top + 4, color, false);
                gg.drawString(fontRenderer, statusLabel(state.status), left + 16, top + 15, TEXT_DIM, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            QuestScreen.this.list.setSelected(this);
            QuestScreen.this.selectedQuest = state.questDetails;
            QuestScreen.this.selectedState = state;
            QuestScreen.this.statusMessage = "";
            QuestScreen.this.updateFinishButtonState();
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(safeText(state.quest_id, "quete"));
        }
    }
}
