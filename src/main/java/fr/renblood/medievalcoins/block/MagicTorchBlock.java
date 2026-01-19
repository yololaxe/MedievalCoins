package fr.renblood.medievalcoins.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
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
    
    // Pas de drop (géré par loot table vide ou méthode override)
    // En 1.20.1, le plus simple est de ne pas avoir de loot table JSON, ou d'en avoir une vide.
    // Mais on peut aussi forcer ici si besoin, bien que deprecated.
}
