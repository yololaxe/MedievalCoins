package fr.renblood.medievalcoins.client.gui;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.NpcQuestInteractionModel;
import fr.renblood.medievalcoins.api.model.QuestModel;
import fr.renblood.medievalcoins.network.NpcQuestActionMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NpcQuestInteractionsScreen extends Screen {
    private static final int BACKDROP = 0xD10B0A08;
    private static final int PANEL = 0xED17130F;
    private static final int PANEL_SOFT = 0xE0241D15;
    private static final int BORDER = 0xFF5B4630;
    private static final int BORDER_LIGHT = 0xFF9D7A45;
    private static final int TEXT = 0xFFEFE2C2;
    private static final int MUTED = 0xFFB9A98F;
    private static final int DIM = 0xFF7D715F;
    private static final int GOLD = 0xFFFFC857;
    private static final int GREEN = 0xFF75D06A;
    private static final int BLUE = 0xFF73C7E8;
    private static final int RED = 0xFFE37B63;
    private static final int AUTO_REFRESH_TICKS = 20 * 60;

    private final String npcId;
    private final String npcName;
    private final String texture;
    private final List<NpcQuestInteractionModel> quests;
    private NpcQuestInteractionModel selected;
    private int detailScroll;
    private int detailContentHeight;
    private int listScroll;
    private int autoRefreshTicks;

    public NpcQuestInteractionsScreen(String npcId, String npcName, String texture, List<NpcQuestInteractionModel> quests) {
        super(Component.literal("Quêtes du PNJ"));
        this.npcId = npcId;
        this.npcName = npcName;
        this.texture = texture;
        this.quests = quests == null ? List.of() : quests.stream()
                .sorted((left, right) -> Boolean.compare(isAvailable(right), isAvailable(left)))
                .toList();
        this.selected = this.quests.isEmpty() ? null : this.quests.get(0);
    }

    @Override
    protected void init() {
        int panelLeft = 14;
        int panelTop = 42;
        int listWidth = listWidth();
        int y = panelTop + 28;
        int maxY = height - 38;

        addRenderableWidget(Button.builder(Component.literal("Recharger"), button -> refresh())
                .bounds(width - 94, 14, 78, 20)
                .build());

        int visibleEntries = Math.max(1, (maxY - y) / 24);
        int end = Math.min(quests.size(), listScroll + visibleEntries);
        for (int index = listScroll; index < end; index++) {
            NpcQuestInteractionModel quest = quests.get(index);
            if (y + 22 > maxY) break;
            String label = isAvailable(quest)
                    ? "! NOUVELLE - " + safe(quest.name(), quest.questId())
                    : statusSymbol(quest.status()) + " " + safe(quest.name(), quest.questId());
            addRenderableWidget(Button.builder(Component.literal(label), button -> {
                        selected = quest;
                        detailScroll = 0;
                        rebuildWidgets();
                    })
                    .bounds(panelLeft + 8, y, listWidth - 16, 20)
                    .build());
            y += 24;
        }

        if (selected == null) return;

        String status = normalize(selected.status());
        if ("AVAILABLE".equals(status) && "START".equalsIgnoreCase(selected.npcAction())) {
            addRenderableWidget(Button.builder(Component.literal("Commencer la quête"), button -> send("START"))
                    .bounds(width - 252, height - 32, 114, 20).build());
        } else if ("IN_PROGRESS".equals(status) && "COMPLETE".equalsIgnoreCase(selected.npcAction())) {
            Button complete = Button.builder(Component.literal(selected.canComplete() ? "Restituer la quête" : "Vérifier et remettre"), button -> send("COMPLETE"))
                    .bounds(width - 252, height - 32, 114, 20).build();
            // The server validates current inventory on click; a stale preview must not block delivery.
            complete.active = true;
            addRenderableWidget(complete);
        }

        addRenderableWidget(Button.builder(Component.literal("Parler à " + safe(npcName, "ce PNJ")),
                        button -> send("DIALOGUE"))
                .bounds(width - 132, height - 32, 114, 20).build());
    }

    private void send(String action) {
        if (selected != null) {
            MedievalCoin.PACKET_HANDLER.sendToServer(
                    new NpcQuestActionMessage(npcId, npcName, texture, selected.questId(), action));
        }
    }

    private void refresh() {
        MedievalCoin.PACKET_HANDLER.sendToServer(
                new NpcQuestActionMessage(npcId, npcName, texture, "", "REFRESH"));
    }

    private void autoRefresh() {
        MedievalCoin.PACKET_HANDLER.sendToServer(
                new NpcQuestActionMessage(npcId, npcName, texture, "", "AUTO_REFRESH"));
    }

    @Override
    public void tick() {
        super.tick();
        if (++autoRefreshTicks >= AUTO_REFRESH_TICKS) {
            autoRefreshTicks = 0;
            autoRefresh();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, BACKDROP);

        int left = 14;
        int top = 42;
        int bottom = height - 38;
        int listWidth = listWidth();
        int detailLeft = left + listWidth + 10;
        int detailRight = width - 14;

        drawPanel(graphics, 8, 8, width - 8, height - 8, PANEL);
        graphics.drawCenteredString(font, safe(npcName, "PNJ"), width / 2, 15, GOLD);
        graphics.drawCenteredString(font, quests.size() + (quests.size() > 1 ? " quêtes disponibles" : " quête disponible"),
                width / 2, 28, MUTED);

        drawPanel(graphics, left, top, left + listWidth, bottom, PANEL_SOFT);
        graphics.fill(left + 1, top + 1, left + listWidth - 1, top + 22, 0x66302017);
        graphics.drawString(font, "QUÊTES", left + 9, top + 7, GOLD, false);

        drawPanel(graphics, detailLeft, top, detailRight, bottom, PANEL_SOFT);
        graphics.fill(detailLeft + 1, top + 1, detailRight - 1, top + 22, 0x66302017);
        graphics.drawString(font, selected == null ? "INFORMATIONS" : safe(selected.name(), selected.questId()),
                detailLeft + 10, top + 7, TEXT, false);

        if (selected == null) {
            drawWrapped(graphics, quests.isEmpty()
                            ? "Aucune quête n'est actuellement assignée à ce personnage."
                            : "Sélectionnez une quête pour afficher ses informations.",
                    detailLeft + 12, top + 38, detailRight - detailLeft - 24, MUTED);
        } else {
            renderDetails(graphics, detailLeft + 12, top + 32, detailRight - detailLeft - 24, bottom - top - 42);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderDetails(GuiGraphics graphics, int x, int viewportY, int contentWidth, int viewportHeight) {
        graphics.enableScissor(x - 2, viewportY, x + contentWidth + 2, viewportY + viewportHeight);
        int y = viewportY - detailScroll;

        y = drawMeta(graphics, x, y, contentWidth);
        y = section(graphics, "Description", x, y + 8, contentWidth, GOLD);
        y = drawWrapped(graphics, resolveNpc(safe(selected.description(), "Aucune description renseignée.")),
                x, y, contentWidth, MUTED) + 5;

        y = section(graphics, "Objectifs", x, y + 5, contentWidth, BLUE);
        if (selected.objectives() == null || selected.objectives().isEmpty()) {
            y = drawWrapped(graphics, "Aucun objectif renseigne.", x, y, contentWidth, DIM) + 4;
        } else {
            for (NpcQuestInteractionModel.ObjectiveProgress objective : selected.objectives()) {
                int color = objective.completed() ? GREEN : MUTED;
                y = drawWrapped(graphics, (objective.completed() ? "[OK] " : "[  ] ")
                                + resolveNpc(safe(objective.description(), "Objectif")),
                        x, y, contentWidth, color) + 3;
            }
        }

        if (!isBlank(selected.missingText())) {
            y = section(graphics, "Encore nécessaire", x, y + 5, contentWidth, RED);
            y = drawWrapped(graphics, resolveNpc(selected.missingText()), x, y, contentWidth, RED) + 4;
        }

        y = section(graphics, "Récompenses", x, y + 5, contentWidth, GOLD);
        y = renderRewards(graphics, x, y, contentWidth);

        if (hasPrerequisites()) {
            y = section(graphics, "Prérequis", x, y + 5, contentWidth, BLUE);
            if (selected.prerequisitesAll() != null && !selected.prerequisitesAll().isEmpty()) {
                y = drawWrapped(graphics, "Tous requis : " + String.join(", ", selected.prerequisitesAll()),
                        x, y, contentWidth, MUTED) + 3;
            }
            if (selected.prerequisitesAny() != null && !selected.prerequisitesAny().isEmpty()) {
                y = drawWrapped(graphics, "Un requis : " + String.join(", ", selected.prerequisitesAny()),
                        x, y, contentWidth, MUTED) + 3;
            }
        }

        detailContentHeight = Math.max(0, y - (viewportY - detailScroll));
        graphics.disableScissor();
    }

    private int drawMeta(GuiGraphics graphics, int x, int y, int width) {
        String status = statusLabel(selected.status());
        graphics.drawString(font, status, x, y, statusColor(selected.status()), false);
        y += 13;

        List<String> meta = new ArrayList<>();
        if (!isBlank(selected.type())) meta.add("Type : " + selected.type());
        if (!isBlank(selected.category())) meta.add("Catégorie : " + selected.category());
        if (!isBlank(selected.questId())) meta.add("ID : " + selected.questId());
        if (!isBlank(selected.parentId())) meta.add("Quête parente : " + selected.parentId());
        for (String line : meta) {
            y = drawWrapped(graphics, line, x, y, width, DIM) + 2;
        }
        return y;
    }

    private int renderRewards(GuiGraphics graphics, int x, int y, int width) {
        boolean hasReward = false;
        if (selected.money() > 0) {
            y = drawWrapped(graphics, selected.money() + " pièces", x, y, width, GOLD) + 3;
            hasReward = true;
        }
        if (selected.xp() != null && selected.xp().amount > 0) {
            y = drawWrapped(graphics, selected.xp().amount + " XP - " + safe(selected.xp().job, "metier"),
                    x, y, width, BLUE) + 3;
            hasReward = true;
        }
        if (selected.itemRewards() != null) {
            for (QuestModel.Reward reward : selected.itemRewards()) {
                if (reward == null || reward.count <= 0) continue;
                y = drawWrapped(graphics, reward.count + " x " + readableItemName(reward.itemId),
                        x, y, width, TEXT) + 3;
                hasReward = true;
            }
        }
        if (!hasReward) y = drawWrapped(graphics, "Aucune récompense renseignée.", x, y, width, DIM) + 3;
        return y;
    }

    private int section(GuiGraphics graphics, String title, int x, int y, int width, int color) {
        graphics.drawString(font, title.toUpperCase(Locale.ROOT), x, y, color, false);
        int lineStart = x + font.width(title.toUpperCase(Locale.ROOT)) + 7;
        if (lineStart < x + width) graphics.fill(lineStart, y + 4, x + width, y + 5, BORDER);
        return y + 14;
    }

    private int drawWrapped(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(Component.literal(safe(text, "")), Math.max(20, width));
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, y, color, false);
            y += 11;
        }
        return y;
    }

    private void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom, int color) {
        graphics.fill(left, top, right, bottom, color);
        graphics.fill(left, top, right, top + 1, BORDER_LIGHT);
        graphics.fill(left, bottom - 1, right, bottom, BORDER);
        graphics.fill(left, top, left + 1, bottom, BORDER);
        graphics.fill(right - 1, top, right, bottom, BORDER);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int panelLeft = 14;
        int panelTop = 42;
        int listWidth = listWidth();
        if (mouseX >= panelLeft && mouseX <= panelLeft + listWidth) {
            int visibleEntries = Math.max(1, (height - panelTop - 66) / 24);
            int maxListScroll = Math.max(0, quests.size() - visibleEntries);
            int next = Math.max(0, Math.min(maxListScroll, listScroll - (int) delta));
            if (next != listScroll) {
                listScroll = next;
                rebuildWidgets();
                return true;
            }
        }
        int maxScroll = Math.max(0, detailContentHeight - (height - 90));
        detailScroll = Math.max(0, Math.min(maxScroll, detailScroll - (int) (delta * 18)));
        return maxScroll > 0 || super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int listWidth() {
        return Math.min(220, Math.max(150, width / 3));
    }

    private boolean hasPrerequisites() {
        return selected.prerequisitesAll() != null && !selected.prerequisitesAll().isEmpty()
                || selected.prerequisitesAny() != null && !selected.prerequisitesAny().isEmpty();
    }

    private static String readableItemName(String itemId) {
        if (isBlank(itemId)) return "objet";
        int separator = itemId.indexOf(':');
        String value = separator >= 0 ? itemId.substring(separator + 1) : itemId;
        return value.replace('_', ' ');
    }

    private static String statusSymbol(String status) {
        return switch (normalize(status)) {
            case "COMPLETED" -> "[OK]";
            case "IN_PROGRESS" -> "[>]";
            default -> "[+]";
        };
    }

    private static String statusLabel(String status) {
        return switch (normalize(status)) {
            case "COMPLETED" -> "TERMINÉE";
            case "IN_PROGRESS" -> "EN COURS";
            case "AVAILABLE" -> "DISPONIBLE";
            default -> safe(status, "STATUT INCONNU");
        };
    }

    private static int statusColor(String status) {
        return switch (normalize(status)) {
            case "COMPLETED" -> GREEN;
            case "IN_PROGRESS" -> BLUE;
            case "AVAILABLE" -> GOLD;
            default -> MUTED;
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isAvailable(NpcQuestInteractionModel quest) {
        return quest != null && "AVAILABLE".equals(normalize(quest.status()));
    }

    private String resolveNpc(String text) {
        return NpcQuestInteractionModel.resolveNpcText(text, npcName);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safe(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
