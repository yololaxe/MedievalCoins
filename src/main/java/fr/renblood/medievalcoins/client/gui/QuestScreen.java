package fr.renblood.medievalcoins.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.api.model.PlayerQuestStateModel;
import fr.renblood.medievalcoins.api.model.QuestModel;
import fr.renblood.medievalcoins.network.ApiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuestScreen extends Screen {

    private static final String[] CATEGORIES = {"Main", "Secondary", "Tertiary", "FullRP", "SemiRP"};
    private String currentCategory = "Main";
    
    private QuestList list;
    private QuestModel selectedQuest;
    
    private final Map<String, List<PlayerQuestStateModel>> questCache = new HashMap<>();

    public QuestScreen() {
        super(Component.literal("Quêtes"));
    }

    @Override
    protected void init() {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = this.minecraft.font;
        
        super.init();
        
        int x = this.width / 2 - 150;
        int y = 30;

        for (int i = 0; i < CATEGORIES.length; i++) {
            String cat = CATEGORIES[i];
            this.addRenderableWidget(Button.builder(Component.literal(cat), b -> loadCategory(cat))
                    .bounds(x + (i * 60), 10, 58, 20)
                    .build());
        }

        this.list = new QuestList(this.minecraft, 150, this.height - 40, y, this.height - 10, 20);
        this.list.setLeftPos(20);
        this.addRenderableWidget(this.list);

        loadCategory(currentCategory);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void loadCategory(String category) {
        this.currentCategory = category;
        if (this.list != null) this.list.clear();
        this.selectedQuest = null;

        if (questCache.containsKey(category)) {
            populateList(questCache.get(category));
        } else {
            new Thread(() -> {
                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null) return;
                    String uuid = mc.player.getGameProfile().getId().toString();
                    List<PlayerQuestStateModel> quests = ApiClient.getActiveQuests(uuid, category);
                    
                    if (quests != null) {
                        for (PlayerQuestStateModel pq : quests) {
                            try {
                                pq.questDetails = ApiClient.getQuestDetails(pq.quest_id);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        
                        questCache.put(category, quests);
                        
                        mc.execute(() -> populateList(quests));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private void populateList(List<PlayerQuestStateModel> quests) {
        if (this.list == null) return;
        this.list.clear();
        for (PlayerQuestStateModel pq : quests) {
            if (pq.questDetails != null) {
                this.list.add(new QuestEntry(pq));
            }
        }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = this.minecraft.font;
        
        this.renderBackground(gg);
        
        for (Renderable renderable : this.renderables) {
            renderable.render(gg, mouseX, mouseY, partialTick);
        }
        
        Font fontRenderer = this.font;
        if (fontRenderer == null) return;
        
        int detailX = 190; 
        int detailY = 40;
        
        if (selectedQuest != null) {
            String playerName = this.minecraft.player != null ? this.minecraft.player.getName().getString() : "Joueur";
            String npcName = selectedQuest.npc != null ? selectedQuest.npc : "PNJ";

            gg.drawString(fontRenderer, "§l" + selectedQuest.name, detailX, detailY, 0xFFFFFF);
            detailY += 15;
            
            String desc = selectedQuest.getDescription();
            if (desc != null && !desc.isEmpty()) {
                desc = desc.replace("{player}", playerName).replace("{npc}", npcName);
                for (FormattedCharSequence line : fontRenderer.split(Component.literal(desc), this.width - detailX - 20)) {
                    gg.drawString(fontRenderer, line, detailX, detailY, 0xAAAAAA);
                    detailY += 10;
                }
            }
            
            detailY += 10;
            gg.drawString(fontRenderer, "§nObjectifs :", detailX, detailY, 0xFFFFFF);
            detailY += 12;
            
            if (selectedQuest.objectives != null) {
                for (QuestModel.Objective obj : selectedQuest.objectives) {
                    if (obj.description != null && !obj.description.isEmpty()) {
                        String objDesc = obj.description.replace("{player}", playerName).replace("{npc}", npcName);
                        gg.drawString(fontRenderer, "- " + objDesc, detailX, detailY, 0xCCCCCC);
                        detailY += 10;
                    } else {
                        if ("ITEM".equals(obj.type) && obj.items != null) {
                            for (QuestModel.ItemRequirement req : obj.items) {
                                String itemName = req.itemId;
                                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(req.itemId));
                                if (item != null) {
                                    itemName = item.getDescription().getString();
                                }
                                gg.drawString(fontRenderer, "- Récolter " + req.count + "x " + itemName, detailX, detailY, 0xCCCCCC);
                                detailY += 10;
                            }
                        } else if ("LOCATION".equals(obj.type)) {
                            gg.drawString(fontRenderer, "- Se rendre en " + obj.coord, detailX, detailY, 0xCCCCCC);
                            detailY += 10;
                        } else if ("TALK".equals(obj.type)) {
                            gg.drawString(fontRenderer, "- Parler à " + (obj.target != null ? obj.target : "PNJ"), detailX, detailY, 0xCCCCCC);
                            detailY += 10;
                        } else {
                            gg.drawString(fontRenderer, "- " + obj.type + " " + (obj.target != null ? obj.target : ""), detailX, detailY, 0xCCCCCC);
                            detailY += 10;
                        }
                    }
                }
            }
            
            detailY += 10;
            gg.drawString(fontRenderer, "§nRécompenses :", detailX, detailY, 0xFFFFFF);
            detailY += 12;
            
            if (selectedQuest.money > 0) {
                gg.drawString(fontRenderer, "+ " + selectedQuest.money + " Pièces", detailX, detailY, 0xFFD700);
                detailY += 10;
            }
            if (selectedQuest.xp != null && selectedQuest.xp.amount > 0) {
                gg.drawString(fontRenderer, "+ " + selectedQuest.xp.amount + " XP " + selectedQuest.xp.job, detailX, detailY, 0x00FF00);
                detailY += 10;
            }
            if (selectedQuest.rewards != null) {
                for (QuestModel.Reward reward : selectedQuest.rewards) {
                    String itemName = reward.itemId;
                    Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(reward.itemId));
                    if (item != null) {
                        itemName = item.getDescription().getString();
                    }
                    gg.drawString(fontRenderer, "+ " + reward.count + "x " + itemName, detailX, detailY, 0x55FFFF);
                    detailY += 10;
                }
            }
        } else {
            gg.drawCenteredString(fontRenderer, "Sélectionnez une quête", this.width / 2 + 50, this.height / 2, 0x888888);
        }
    }

    class QuestList extends ObjectSelectionList<QuestEntry> {
        public QuestList(Minecraft mc, int width, int height, int y0, int y1, int itemHeight) {
            super(mc, width, height, y0, y1, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return 140;
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
            String name = state.questDetails != null ? state.questDetails.name : state.quest_id;
            int color = 0xFFFFFF;
            if ("COMPLETED".equals(state.status)) color = 0x00FF00;
            
            Font fontRenderer = QuestScreen.this.font;
            if (fontRenderer == null) fontRenderer = Minecraft.getInstance().font;
            
            if (fontRenderer != null) {
                String displayName = fontRenderer.plainSubstrByWidth(name, width - 10);
                if (displayName.length() < name.length()) displayName += "...";
                gg.drawString(fontRenderer, displayName, left + 5, top + 5, color);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            QuestScreen.this.list.setSelected(this);
            QuestScreen.this.selectedQuest = state.questDetails;
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(state.quest_id);
        }
    }
}
