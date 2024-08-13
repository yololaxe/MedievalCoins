package fr.renblood.medievalcoins.client.renderer;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.entity.Banker;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class BankerRenderer extends HumanoidMobRenderer<Banker, PlayerModel<Banker>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MedievalCoin.MODID, "textures/entity/banker.png");

    public BankerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(Banker entity) {
        return TEXTURE;
    }
}
