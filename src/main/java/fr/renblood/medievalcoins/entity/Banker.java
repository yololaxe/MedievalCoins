package fr.renblood.medievalcoins.entity;

import fr.renblood.medievalcoins.init.EntityInit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.jetbrains.annotations.Nullable;

public class Banker extends Villager {

    public Banker(EntityType<Banker> type, Level level) {
        super(type, level);
        this.registerGoals();
    }
    public Banker(Level level, double x, double y, double z){
        this(EntityInit.BANKER.get(), level);
        setPos(x,y,z);
    }
    public Banker(Level level, BlockPos position){
        this(level, position.getX(), position.getY(), position.getZ());

    }




    @Nullable
    @Override
    public Villager getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return new Banker(level, this.blockPosition());
    }


    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new RandomStrollGoal(this, 0.6D));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 35.0D);
    }

    public static boolean canSpawn(EntityType<Banker> entityType, LevelAccessor level, MobSpawnType spawnType, BlockPos position, RandomSource random){
        return Villager.checkMobSpawnRules(entityType, level, spawnType, position, random);
    }
}
