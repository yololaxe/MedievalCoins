package fr.renblood.medievalcoins.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.PlayerModel;
import fr.renblood.medievalcoins.network.PlayerCache;
import fr.renblood.medievalcoins.tree.TreeAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class RadialMenuScreen extends Screen {

    private static final ResourceLocation WHEEL = new ResourceLocation(MedievalCoin.MODID, "textures/gui/radial_menu.png");
    private static final ResourceLocation LOCK_ICON = new ResourceLocation(MedievalCoin.MODID, "textures/gui/lock.png");
    
    private final List<RadialOption> options = new ArrayList<>();
    private int selectedIndex = -1;

    public RadialMenuScreen() {
        super(Component.literal("Radial Menu"));
        
        options.add(new RadialOption("Torche", "torch", new ResourceLocation(MedievalCoin.MODID, "textures/gui/icons/torch.png"), TreeAbility.TORCH));
        options.add(new RadialOption("Fertilize", "fertilize", new ResourceLocation(MedievalCoin.MODID, "textures/gui/icons/fertilize.png"), TreeAbility.FERTILIZE));
        options.add(new RadialOption("Magnet", "magnet", new ResourceLocation(MedievalCoin.MODID, "textures/gui/icons/magnet.png"), TreeAbility.MAGNET));
        options.add(new RadialOption("Jump", "jump-boost", new ResourceLocation(MedievalCoin.MODID, "textures/gui/icons/jump.png"), TreeAbility.JUMPBOOST));
        options.add(new RadialOption("NoFall", "nofall", new ResourceLocation(MedievalCoin.MODID, "textures/gui/icons/nofall.png"), TreeAbility.NOFALL));
        options.add(new RadialOption("Vanish", "vanish", new ResourceLocation(MedievalCoin.MODID, "textures/gui/icons/vanish.png"), TreeAbility.VANISH));
        options.add(new RadialOption("Camp", "firecamp", new ResourceLocation(MedievalCoin.MODID, "textures/gui/icons/firecamp.png"), TreeAbility.FIRECAMP));
        options.add(new RadialOption("Unbark", "unbark", new ResourceLocation(MedievalCoin.MODID, "textures/gui/icons/unbark.png"), TreeAbility.UNBARK));
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
                gg.drawCenteredString(font, opt.name, centerX, centerY, 0xFFFFFF);
            } else if (i == selectedIndex && isLocked) {
                gg.fill(x - 24, y - 24, x + 24, y + 24, 0x60FF0000);
                gg.drawCenteredString(font, "Verrouillé", centerX, centerY, 0xFF0000);
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
        if (keyCode == GLFW.GLFW_KEY_G) { 
             if (selectedIndex != -1) {
                 RadialOption opt = options.get(selectedIndex);
                 if (!isLocked(opt) && this.minecraft.player != null) {
                     this.minecraft.player.connection.sendCommand(opt.command);
                 }
             }
             this.onClose();
             return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
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

    private record RadialOption(String name, String command, ResourceLocation icon, TreeAbility ability) {}
}
