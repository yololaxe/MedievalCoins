package fr.renblood.medievalcoins.client.gui;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.network.PrayerSuccessMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class PrayerMinigameScreen extends Screen {
    private static final int MAX_TICKS = 260;
    private static final int TARGET_SIZE = 24;
    private final Random random = new Random();
    private final int gameId;
    private final String gameName;
    private final List<TrialPoint> points = new ArrayList<>();
    private final List<Integer> sequence = new ArrayList<>();
    private int targetX;
    private int targetY;
    private int progress;
    private int ticksLeft = MAX_TICKS;
    private int rhythmTicks;
    private int revealTicks;
    private int balance;
    private int stableTicks;
    private boolean finished;
    private boolean failed;

    public PrayerMinigameScreen() {
        this(0, "Rune Hunt");
    }

    public PrayerMinigameScreen(int gameId, String gameName) {
        super(Component.literal(gameName == null ? "Divine Trial" : gameName));
        this.gameId = Math.max(0, Math.min(3, gameId));
        this.gameName = gameName == null ? "Divine Trial" : gameName;
    }

    @Override
    protected void init() {
        progress = 0;
        ticksLeft = MAX_TICKS;
        rhythmTicks = 0;
        stableTicks = 0;
        failed = false;
        finished = false;
        points.clear();
        sequence.clear();

        if (gameId == 1) {
            setupMemorySeals();
        } else if (gameId == 2) {
            balance = random.nextBoolean() ? 32 : -32;
        } else {
            moveTarget();
        }
    }

    @Override
    public void tick() {
        if (finished) return;

        ticksLeft--;
        rhythmTicks++;

        if (gameId == 1 && revealTicks > 0) {
            revealTicks--;
        }

        if (gameId == 2) {
            int drift = random.nextInt(5) - 2;
            balance = clamp(balance + drift + (balance > 0 ? 1 : -1), -70, 70);
            if (Math.abs(balance) <= 7) {
                stableTicks++;
                progress = stableTicks / 12;
                if (stableTicks >= 72) {
                    complete();
                }
            } else {
                stableTicks = Math.max(0, stableTicks - 2);
                progress = stableTicks / 12;
            }
        }

        if (ticksLeft <= 0) {
            failed = true;
            finished = true;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (finished) return super.mouseClicked(mouseX, mouseY, button);

        switch (gameId) {
            case 1 -> clickMemorySeal(mouseX, mouseY);
            case 2 -> pushBalance(mouseX);
            case 3 -> rhythmHit();
            default -> runeHit(mouseX, mouseY);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!finished && gameId == 3 && keyCode == 32) {
            rhythmHit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void runeHit(double mouseX, double mouseY) {
        if (!insideTarget(mouseX, mouseY)) return;
        progress++;
        if (progress >= 7) {
            complete();
        } else {
            moveTarget();
        }
    }

    private void clickMemorySeal(double mouseX, double mouseY) {
        if (revealTicks > 0 || progress >= sequence.size()) return;

        for (int i = 0; i < points.size(); i++) {
            TrialPoint point = points.get(i);
            if (point.contains(mouseX, mouseY)) {
                if (sequence.get(progress) == i) {
                    progress++;
                    if (progress >= sequence.size()) complete();
                } else {
                    failed = true;
                    finished = true;
                }
                return;
            }
        }
    }

    private void pushBalance(double mouseX) {
        balance = clamp(balance + (mouseX < width / 2.0D ? -16 : 16), -70, 70);
    }

    private void rhythmHit() {
        int phase = rhythmTicks % 42;
        if (phase >= 18 && phase <= 24) {
            progress++;
            if (progress >= 6) complete();
        } else {
            progress = Math.max(0, progress - 1);
        }
    }

    private void complete() {
        finished = true;
        failed = false;
        MedievalCoin.PACKET_HANDLER.sendToServer(new PrayerSuccessMessage(gameId));
    }

    private boolean insideTarget(double mouseX, double mouseY) {
        return mouseX >= targetX && mouseX <= targetX + TARGET_SIZE
                && mouseY >= targetY && mouseY <= targetY + TARGET_SIZE;
    }

    private void moveTarget() {
        int margin = 34;
        int availableWidth = Math.max(1, width - TARGET_SIZE - margin * 2);
        int availableHeight = Math.max(1, height - TARGET_SIZE - 92);
        targetX = margin + random.nextInt(availableWidth);
        targetY = 70 + random.nextInt(availableHeight);
    }

    private void setupMemorySeals() {
        int centerX = width / 2;
        int centerY = height / 2 + 10;
        int spacing = 42;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                points.add(new TrialPoint(centerX - spacing + col * spacing, centerY - spacing + row * spacing));
            }
        }

        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) candidates.add(i);
        Collections.shuffle(candidates, random);
        sequence.addAll(candidates.subList(0, 5));
        revealTicks = 78;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xD20B0714);
        graphics.drawCenteredString(font, gameName, width / 2, 18, 0xFFFFD56A);
        graphics.drawCenteredString(font, instruction(), width / 2, 34, 0xFFE8DDF5);
        graphics.drawString(font, "Progress: " + progress, 14, 14, 0xFFFFFFFF);
        graphics.drawString(font, "Time: " + Math.max(0, ticksLeft / 20), 14, 28, 0xFFFFFFFF);

        if (finished) {
            graphics.drawCenteredString(font, failed ? "La priere s'est brisee." : "La priere est complete.",
                    width / 2, height / 2, failed ? 0xFFFF7777 : 0xFF8CFF9C);
            graphics.drawCenteredString(font, "Ferme cet ecran", width / 2, height / 2 + 16, 0xFFFFFFFF);
            return;
        }

        switch (gameId) {
            case 1 -> renderMemorySeals(graphics);
            case 2 -> renderBalance(graphics);
            case 3 -> renderRhythm(graphics);
            default -> renderRuneHunt(graphics);
        }
    }

    private void renderRuneHunt(GuiGraphics graphics) {
        graphics.fill(targetX - 3, targetY - 3, targetX + TARGET_SIZE + 3, targetY + TARGET_SIZE + 3, 0xFF251635);
        graphics.fill(targetX - 1, targetY - 1, targetX + TARGET_SIZE + 1, targetY + TARGET_SIZE + 1, 0xFFFFE08A);
        graphics.fill(targetX, targetY, targetX + TARGET_SIZE, targetY + TARGET_SIZE, 0xFFB66CFF);
        graphics.drawCenteredString(font, "*", targetX + TARGET_SIZE / 2, targetY + 8, 0xFFFFFFFF);
    }

    private void renderMemorySeals(GuiGraphics graphics) {
        for (int i = 0; i < points.size(); i++) {
            TrialPoint point = points.get(i);
            boolean next = revealTicks <= 0 && progress < sequence.size() && sequence.get(progress) == i;
            int color = next ? 0xFF53E6C1 : 0xFF51316B;
            graphics.fill(point.x - 14, point.y - 14, point.x + 14, point.y + 14, 0xFF170E23);
            graphics.fill(point.x - 11, point.y - 11, point.x + 11, point.y + 11, color);
            graphics.drawCenteredString(font, sealLabel(i), point.x, point.y - 4, 0xFFFFFFFF);
        }

        if (revealTicks > 0) {
            int step = Math.min(sequence.size() - 1, (78 - revealTicks) / 14);
            TrialPoint point = points.get(sequence.get(step));
            graphics.fill(point.x - 18, point.y - 18, point.x + 18, point.y + 18, 0x99FFE08A);
            graphics.drawCenteredString(font, String.valueOf(step + 1), point.x, point.y + 17, 0xFFFFF2B0);
        }
    }

    private void renderBalance(GuiGraphics graphics) {
        int barX = width / 2 - 95;
        int barY = height / 2;
        int markerX = width / 2 + balance;
        graphics.drawCenteredString(font, "Clic gauche/droite de l'ecran pour garder la flamme au centre", width / 2, barY - 44, 0xFFE8DDF5);
        graphics.fill(barX, barY, barX + 190, barY + 10, 0xFF2A1A38);
        graphics.fill(width / 2 - 13, barY - 6, width / 2 + 13, barY + 16, 0xFF2E7D5B);
        graphics.fill(markerX - 5, barY - 15, markerX + 5, barY + 25, 0xFFFFB84D);
        graphics.fill(markerX - 2, barY - 21, markerX + 2, barY - 15, 0xFFFFF1A8);
        graphics.drawCenteredString(font, "Stabilite: " + stableTicks + "/72", width / 2, barY + 34, 0xFFFFFFFF);
    }

    private void renderRhythm(GuiGraphics graphics) {
        int phase = rhythmTicks % 42;
        int trackX = 70;
        int trackW = width - 140;
        int marker = trackX + phase * Math.max(1, trackW / 42);
        graphics.fill(trackX, height / 2, trackX + trackW, height / 2 + 8, 0xFF3A2E46);
        graphics.fill(width / 2 - 24, height / 2 - 6, width / 2 + 24, height / 2 + 14, 0xFF75D06A);
        graphics.fill(marker, height / 2 - 10, marker + 5, height / 2 + 18, 0xFFFFC857);
        graphics.drawCenteredString(font, "Clic ou espace dans la zone verte", width / 2, height / 2 + 32, 0xFFE8DDF5);
    }

    private String instruction() {
        return switch (gameId) {
            case 1 -> revealTicks > 0 ? "Observe l'ordre des sceaux." : "Rejoue la memoire des sceaux.";
            case 2 -> "Maintiens la flamme en equilibre.";
            case 3 -> "Frappe au rythme de la lueur divine.";
            default -> "Capture les runes avant la fin du temps.";
        };
    }

    private String sealLabel(int index) {
        return switch (index) {
            case 0 -> "I";
            case 1 -> "V";
            case 2 -> "X";
            case 3 -> "+";
            case 4 -> "O";
            case 5 -> "^";
            case 6 -> "L";
            case 7 -> "S";
            default -> "Z";
        };
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record TrialPoint(int x, int y) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x - 14 && mouseX <= x + 14 && mouseY >= y - 14 && mouseY <= y + 14;
        }
    }
}
