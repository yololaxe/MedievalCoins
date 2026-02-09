package fr.renblood.medievalcoins.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID, value = Dist.CLIENT)
public class RegionRenderer {

    private static final List<Highlight> highlights = new ArrayList<>();

    public static void addHighlight(BlockPos min, BlockPos max, int color, int durationTicks) {
        highlights.add(new Highlight(min, max, color, durationTicks));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Iterator<Highlight> it = highlights.iterator();
            while (it.hasNext()) {
                Highlight h = it.next();
                h.ticksRemaining--;
                if (h.ticksRemaining <= 0) {
                    it.remove();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        PoseStack poseStack = event.getPoseStack();
        Minecraft mc = Minecraft.getInstance();
        Vec3 cameraPos = event.getCamera().getPosition();

        VertexConsumer buffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        RenderSystem.lineWidth(3.0F);

        for (Highlight h : highlights) {
            AABB box = new AABB(h.min, h.max.offset(1, 1, 1));
            
            float a = ((h.color >> 24) & 0xFF) / 255.0F;
            float r = ((h.color >> 16) & 0xFF) / 255.0F;
            float g = ((h.color >> 8) & 0xFF) / 255.0F;
            float b = (h.color & 0xFF) / 255.0F;

            LevelRenderer.renderLineBox(poseStack, buffer, box, r, g, b, a);
        }

        poseStack.popPose();
    }

    private static class Highlight {
        final BlockPos min;
        final BlockPos max;
        final int color;
        int ticksRemaining;

        Highlight(BlockPos min, BlockPos max, int color, int durationTicks) {
            this.min = min;
            this.max = max;
            this.color = color;
            this.ticksRemaining = durationTicks;
        }
    }
}
