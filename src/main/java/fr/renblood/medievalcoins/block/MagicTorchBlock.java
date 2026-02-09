package fr.renblood.medievalcoins.block;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public class MagicTorchBlock extends TorchBlock {
    public MagicTorchBlock() {
        super(BlockBehaviour.Properties.of()
                .noCollission()
                .instabreak()
                .lightLevel((state) -> 14)
                .sound(net.minecraft.world.level.block.SoundType.WOOD)
                .pushReaction(PushReaction.DESTROY), 
                ParticleTypes.FLAME);
    }
}
