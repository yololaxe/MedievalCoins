package fr.renblood.medievalcoins.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.client.quest.ClientNpcQuestAvailability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class NpcQuestMarkerRenderer {
    private static final Map<Class<?>, Optional<Method>> SPAWN_ID_METHODS = new ConcurrentHashMap<>();

    private NpcQuestMarkerRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        String spawnId = getSpawnId(entity);
        if (!ClientNpcQuestAvailability.hasAvailableQuest(spawnId)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.distanceToSqr(entity) > 4096.0D) return;

        PoseStack poseStack = event.getPoseStack();
        float bob = (float) Math.sin((entity.tickCount + event.getPartialTick()) * 0.1F) * 0.08F;
        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() + 0.7D + bob, 0.0D);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.05F, -0.05F, 0.05F);

        Font font = minecraft.font;
        MultiBufferSource buffer = event.getMultiBufferSource();
        Matrix4f matrix = poseStack.last().pose();
        float x = -font.width("!") / 2.0F;
        font.drawInBatch("!", x, 0.0F, 0xFFFFD740, false, matrix, buffer,
                Font.DisplayMode.SEE_THROUGH, 0x70000000, 15728880);
        font.drawInBatch("!", x, 0.0F, 0xFFFFD740, false, matrix, buffer,
                Font.DisplayMode.NORMAL, 0, event.getPackedLight());
        poseStack.popPose();
    }

    private static String getSpawnId(LivingEntity entity) {
        Optional<Method> method = SPAWN_ID_METHODS.computeIfAbsent(entity.getClass(), type -> {
            try {
                return Optional.of(type.getMethod("getApiNpcSpawnId"));
            } catch (NoSuchMethodException ignored) {
                return Optional.empty();
            }
        });
        if (method.isEmpty()) return "";

        try {
            Object value = method.get().invoke(entity);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }
}
