package fr.renblood.medievalcoins.tree.network;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.tree.capability.FertilizerCapabilityHandler;
import fr.renblood.medievalcoins.tree.capability.FertilizerInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class FertilizeKeyMessage {

    public FertilizeKeyMessage() {}

    public static void encode(FertilizeKeyMessage msg, FriendlyByteBuf buf) {}
    public static FertilizeKeyMessage decode(FriendlyByteBuf buf) {
        return new FertilizeKeyMessage();
    }

    public static void handle(FertilizeKeyMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (MedievalCoin.DEBUG_MODE) {
                MedievalCoin.LOGGER.info("FertilizeKeyMessage received from " + player.getName().getString());
            }

            LazyOptional<FertilizerInventory> cap = player.getCapability(FertilizerCapabilityHandler.FERTILIZER_CAP);
            
            if (cap.isPresent()) {
                cap.ifPresent(inv -> {
                    ItemStack stack = inv.getStackInSlot(0);
                    
                    if (MedievalCoin.DEBUG_MODE) {
                        MedievalCoin.LOGGER.info("Current fertilizer stack: " + stack + " (Count: " + stack.getCount() + ")");
                    }

                    if (!stack.isEmpty() && stack.getItem() == Items.BONE_MEAL) {
                        // Récupère la portée du joueur
                        double reach = player.getAttributeValue(ForgeMod.BLOCK_REACH.get());
                        HitResult hit = player.pick(reach, 0.0F, false);
                        
                        if (MedievalCoin.DEBUG_MODE) {
                            MedievalCoin.LOGGER.info("Hit result: " + hit.getType());
                        }

                        if (hit.getType() == HitResult.Type.BLOCK) {
                            BlockHitResult blockHit = (BlockHitResult) hit;
                            BlockPos pos = blockHit.getBlockPos();
                            Level level = player.level();
                            BlockState blockState = level.getBlockState(pos);
                            
                            if (MedievalCoin.DEBUG_MODE) {
                                MedievalCoin.LOGGER.info("Target block pos: " + pos + " Block: " + blockState);
                            }

                            // Vérifie si c'est une pousse d'arbre (sapling)
                            if (!blockState.is(BlockTags.SAPLINGS)) {
                                if (MedievalCoin.DEBUG_MODE) {
                                    MedievalCoin.LOGGER.info("Target is not a sapling. Aborting.");
                                }
                                return;
                            }

                            boolean success = false;

                            // On vérifie manuellement si on peut bonemeal pour forcer l'action
                            if (blockState.getBlock() instanceof BonemealableBlock bonemealableBlock) {
                                if (bonemealableBlock.isValidBonemealTarget(level, pos, blockState, level.isClientSide)) {
                                    if (level instanceof ServerLevel serverLevel) {
                                        if (bonemealableBlock.isBonemealSuccess(level, level.random, pos, blockState)) {
                                            bonemealableBlock.performBonemeal(serverLevel, level.random, pos, blockState);
                                            // Particules manuelles car performBonemeal ne les fait pas toujours si appelé directement
                                            level.levelEvent(1505, pos, 0); 
                                            success = true;
                                            
                                            if (MedievalCoin.DEBUG_MODE) {
                                                MedievalCoin.LOGGER.info("BoneMeal applied manually!");
                                            }
                                        }
                                    }
                                }
                            }

                            // Fallback sur la méthode item si le bloc n'est pas géré directement (rare pour saplings)
                            if (!success) {
                                ItemStack dummyStack = stack.copy();
                                dummyStack.setCount(1);
                                
                                if (BoneMealItem.applyBonemeal(dummyStack, level, pos, player)) {
                                    success = true;
                                    if (MedievalCoin.DEBUG_MODE) {
                                        MedievalCoin.LOGGER.info("BoneMeal applied via Item method!");
                                    }
                                }
                            }

                            if (success) {
                                // Consommation
                                if (!player.isCreative()) {
                                    stack.shrink(1);
                                    inv.setStackInSlot(0, stack);
                                    // Synchronise avec le client
                                    MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new FertilizerSlotMessage(inv.getStackInSlot(0)));
                                    
                                    if (MedievalCoin.DEBUG_MODE) {
                                        MedievalCoin.LOGGER.info("Consumed 1 fertilizer. New count: " + stack.getCount());
                                    }
                                }
                                // Animation
                                player.swing(InteractionHand.MAIN_HAND, true);
                            } else {
                                if (MedievalCoin.DEBUG_MODE) {
                                    MedievalCoin.LOGGER.info("BoneMeal application failed.");
                                }
                            }
                        } else {
                            if (MedievalCoin.DEBUG_MODE) {
                                MedievalCoin.LOGGER.info("Hit result is NOT a block.");
                            }
                        }
                    } else {
                        if (MedievalCoin.DEBUG_MODE) {
                            MedievalCoin.LOGGER.info("No BoneMeal in custom slot or stack is empty.");
                        }
                    }
                });
            } else {
                if (MedievalCoin.DEBUG_MODE) {
                    MedievalCoin.LOGGER.error("Fertilizer Capability NOT FOUND on player " + player.getName().getString());
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
