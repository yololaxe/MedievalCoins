package fr.renblood.medievalcoins.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.PlayerModel;
import fr.renblood.medievalcoins.network.PlayerCache;
import fr.renblood.medievalcoins.init.KeybindInit;
import fr.renblood.medievalcoins.tree.TreeAbility;
import fr.renblood.medievalcoins.tree.network.AbilityStatusMessage;
import fr.renblood.medievalcoins.tree.network.RequestAbilityStatusMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class RadialMenuScreen extends Screen {

    private static final ResourceLocation WHEEL = new ResourceLocation(MedievalCoin.MODID, "textures/gui/radial_menu.png");
    private static final ResourceLocation LOCK_ICON = new ResourceLocation(MedievalCoin.MODID, "textures/gui/lock.png");
    
    private final List<RadialOption> options = new ArrayList<>();
    private static final Map<TreeAbility, AbilityStatusMessage.AbilityStatus> STATUSES = new EnumMap<>(TreeAbility.class);
    private static long statusReceivedAt;
    private int selectedIndex = -1;

    public RadialMenuScreen() {
        super(Component.translatable("screen.medieval_coins.radial"));
        
        options.add(new RadialOption("gui.medieval_coins.ability.torch", "mc ability torch", new ResourceLocation(MedievalCoin.MODID, "textures/gui/icons/torch.png"), TreeAbility.TORCH));
        options.add(new RadialOption("gui.medieval_coins.ability.fertilize", "mc ability fertilize", new ResourceLocation(MedievalCoin.MODID, "textures/gui/icons/fertilize.png"), TreeAbility.FERTILIZE));
        options.add(new RadialOption("gui.medieval_coins.ability.magnet", "mc ability magnet", new ResourceLocation(MedievalCoin.MODID, "textures/gui/icons/magnet.png"), TreeAbility.MAGNET));
        options.add(new RadialOption("gui.medieval_coins.ability.jump", "mc ability jump-boost", new ResourceLocation(MedievalCoin.MODID, "textures/gui/icons/jump.png"), TreeAbility.JUMPBOOST));
        options.add(new RadialOption("gui.medieval_coins.ability.nofall", "mc ability nofall", new ResourceLocation(MedievalCoin.MODID, "textures/gui/icons/nofall.png"), TreeAbility.NOFALL));
        options.add(new RadialOption("gui.medieval_coins.ability.vanish", "mc ability vanish", new ResourceLocation(MedievalCoin.MODID, "textures/gui/icons/vanish.png"), TreeAbility.VANISH));
        options.add(new RadialOption("gui.medieval_coins.ability.firecamp", "mc ability firecamp", new ResourceLocation(MedievalCoin.MODID, "textures/gui/icons/firecamp.png"), TreeAbility.FIRECAMP));
        options.add(new RadialOption("gui.medieval_coins.ability.unbark", "mc ability unbark", new ResourceLocation(MedievalCoin.MODID, "textures/gui/icons/unbark.png"), TreeAbility.UNBARK));
    }

    @Override
    protected void init() {
        super.init();
        MedievalCoin.PACKET_HANDLER.sendToServer(new RequestAbilityStatusMessage());
    }

    public static void updateStatuses(Map<TreeAbility, AbilityStatusMessage.AbilityStatus> statuses) {
        STATUSES.clear();
        STATUSES.putAll(statuses);
        statusReceivedAt = System.currentTimeMillis();
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        gg.blit(WHEEL, centerX - 128, centerY - 128, 0, 0, 256, 256);
        
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        
        double offsetAngle = 22.5;
        
        if (distance > 20) {
            double angle = Math.toDegrees(Math.atan2(dy, dx));
            if (angle < 0) angle += 360;
            float sectorSize = 360f / options.size();
            selectedIndex = (int) (((angle + sectorSize / 2) % 360) / sectorSize);
        } else {
            selectedIndex = -1;
        }

        int radius = 80;
        
        for (int i = 0; i < options.size(); i++) {
            RadialOption opt = options.get(i);
            boolean isLocked = isLocked(opt);
            
            double angleRad = Math.toRadians((i * (360.0 / options.size())) + offsetAngle);
            int x = centerX + (int) (Math.cos(angleRad) * radius);
            int y = centerY + (int) (Math.sin(angleRad) * radius);
            
            if (i == selectedIndex && !isLocked) {
                gg.fill(x - 24, y - 24, x + 24, y + 24, 0x60FFFFFF);
                gg.drawCenteredString(font, Component.translatable(opt.nameKey), centerX, centerY, 0xFFFFFF);
                AbilityStatusMessage.AbilityStatus status = STATUSES.get(opt.ability);
                Component stateText = Component.translatable(status != null && status.active()
                        ? "gui.medieval_coins.radial.active"
                        : "gui.medieval_coins.radial.inactive");
                int stateColor = status != null && status.active() ? 0x66FF66 : 0xBBBBBB;
                gg.drawCenteredString(font, stateText, centerX, centerY + 14, stateColor);
                long remaining = cooldownRemaining(status);
                if (remaining > 0) {
                    long seconds = (remaining + 999) / 1000;
                    gg.drawCenteredString(font, Component.translatable("gui.medieval_coins.radial.cooldown", seconds), centerX, centerY + 28, 0xFFCC55);
                }
            } else if (i == selectedIndex && isLocked) {
                gg.fill(x - 24, y - 24, x + 24, y + 24, 0x60FF0000);
                gg.drawCenteredString(font, Component.translatable("gui.medieval_coins.radial.locked"), centerX, centerY, 0xFF5555);
                gg.drawCenteredString(font, requirementText(opt), centerX, centerY + 14, 0xFFCC55);
            }
            
            RenderSystem.setShaderTexture(0, opt.icon);
            if (isLocked) {
                RenderSystem.setShaderColor(0.5F, 0.5F, 0.5F, 1.0F);
            }
            gg.blit(opt.icon, x - 16, y - 16, 0, 0, 32, 32, 32, 32);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            
            if (isLocked) {
                gg.blit(LOCK_ICON, x - 8, y - 8, 0, 0, 16, 16, 16, 16);
            }
        }
        
        RenderSystem.disableBlend();
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (!KeybindInit.RADIAL_MENU_KEY.isDown()) {
            activateSelected();
            this.onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && selectedIndex != -1) {
            activateSelected();
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void activateSelected() {
        if (selectedIndex == -1 || this.minecraft == null || this.minecraft.player == null) return;
        RadialOption opt = options.get(selectedIndex);
        AbilityStatusMessage.AbilityStatus status = STATUSES.get(opt.ability);
        if (!isLocked(opt) && cooldownRemaining(status) <= 0) {
            this.minecraft.player.connection.sendCommand(opt.command);
        }
    }

    private long cooldownRemaining(AbilityStatusMessage.AbilityStatus status) {
        if (status == null) return 0;
        return Math.max(0, status.cooldownRemainingMs() - (System.currentTimeMillis() - statusReceivedAt));
    }

    private Component requirementText(RadialOption opt) {
        return Component.translatable("gui.medieval_coins.radial.requirement",
                opt.ability.getJobId(), opt.ability.getProgressionIndex() + 1);
    }

    private boolean isLocked(RadialOption opt) {
        if (this.minecraft.player == null) return true;
        String uuid = this.minecraft.player.getGameProfile().getId().toString();
        PlayerModel pm = PlayerCache.getPlayer(uuid);
        
        if (pm == null || pm.experiences == null || pm.experiences.jobs == null) {
            return true; 
        }
        
        String job = opt.ability.getJobId();
        int index = opt.ability.getProgressionIndex();
        
        PlayerModel.JobExperience jobExp = pm.experiences.jobs.get(job);
        if (jobExp == null || jobExp.progression == null) return true;
        
        if (index >= 0 && index < jobExp.progression.size()) {
            return !jobExp.progression.get(index);
        }
        
        return true;
    }

    private record RadialOption(String nameKey, String command, ResourceLocation icon, TreeAbility ability) {}
}
