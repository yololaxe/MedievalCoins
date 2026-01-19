package fr.renblood.medievalcoins.tree.network;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.init.BlockInit;
import fr.renblood.medievalcoins.tree.capability.SpecialSlotCapabilityHandler;
import fr.renblood.medievalcoins.tree.capability.SpecialSlotInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class SpecialSlotKeyMessage {

    private static final Map<UUID, Long> lastTorchTime = new HashMap<>();
    private static final long TORCH_COOLDOWN_MS = 3000; // 3 secondes

    public SpecialSlotKeyMessage() {}

    public static void encode(SpecialSlotKeyMessage msg, FriendlyByteBuf buf) {}
    public static SpecialSlotKeyMessage decode(FriendlyByteBuf buf) {
        return new SpecialSlotKeyMessage();
    }

    public static void handle(SpecialSlotKeyMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            LazyOptional<SpecialSlotInventory> cap = player.getCapability(SpecialSlotCapabilityHandler.SPECIAL_SLOT_CAP);
            
            if (cap.isPresent()) {
                cap.ifPresent(inv -> {
                    ItemStack stack = inv.getStackInSlot(0);
                    
                    if (stack.isEmpty()) return;

                    // --- LOGIQUE BONE MEAL ---
                    if (stack.getItem() == Items.BONE_MEAL) {
                        handleBoneMeal(player, inv, stack);
                    }
                    // --- LOGIQUE TORCHE (Item magique) ---
                    // On vérifie si c'est notre item de torche magique
                    else if (stack.getItem() == BlockInit.MAGIC_TORCH.get().asItem()) {
                        handleTorch(player, inv, stack);
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleBoneMeal(ServerPlayer player, SpecialSlotInventory inv, ItemStack stack) {
        double reach = player.getAttributeValue(ForgeMod.BLOCK_REACH.get());
        HitResult hit = player.pick(reach, 0.0F, false);

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos pos = blockHit.getBlockPos();
            Level level = player.level();
            BlockState blockState = level.getBlockState(pos);

            if (!blockState.is(BlockTags.SAPLINGS)) return;

            boolean success = false;
            if (blockState.getBlock() instanceof BonemealableBlock bonemealableBlock) {
                if (bonemealableBlock.isValidBonemealTarget(level, pos, blockState, level.isClientSide)) {
                    if (level instanceof ServerLevel serverLevel) {
                        if (bonemealableBlock.isBonemealSuccess(level, level.random, pos, blockState)) {
                            bonemealableBlock.performBonemeal(serverLevel, level.random, pos, blockState);
                            level.levelEvent(1505, pos, 0);
                            success = true;
                        }
                    }
                }
            }

            if (!success) {
                ItemStack dummyStack = stack.copy();
                dummyStack.setCount(1);
                if (BoneMealItem.applyBonemeal(dummyStack, level, pos, player)) {
                    success = true;
                }
            }

            if (success) {
                if (!player.isCreative()) {
                    stack.shrink(1);
                    inv.setStackInSlot(0, stack);
                    MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new FertilizerSlotMessage(inv.getStackInSlot(0)));
                }
                player.swing(InteractionHand.MAIN_HAND, true);
            }
        }
    }

    private static void handleTorch(ServerPlayer player, SpecialSlotInventory inv, ItemStack stack) {
        // Vérification du cooldown
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        if (lastTorchTime.containsKey(uuid)) {
            long last = lastTorchTime.get(uuid);
            if (now - last < TORCH_COOLDOWN_MS) {
                player.sendSystemMessage(Component.literal("⏳ Veuillez attendre 3 secondes entre chaque torche."));
                return;
            }
        }
        
        double reach = player.getAttributeValue(ForgeMod.BLOCK_REACH.get());
        HitResult hit = player.pick(reach, 0.0F, false);

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos pos = blockHit.getBlockPos();
            Direction face = blockHit.getDirection();
            BlockPos placePos = pos.relative(face);
            Level level = player.level();

            // Vérifie si on peut placer une torche ici
            if (level.getBlockState(placePos).canBeReplaced()) {
                // On utilise une fausse stack de notre bloc magique pour le contexte
                ItemStack magicTorchStack = new ItemStack(BlockInit.MAGIC_TORCH.get().asItem());
                BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, magicTorchStack, blockHit);
                
                if (context.canPlace()) {
                    BlockState stateToPlace = null;
                    
                    // Logique de placement murale vs sol
                    if (face.getAxis().isHorizontal()) {
                        stateToPlace = BlockInit.MAGIC_WALL_TORCH.get().getStateForPlacement(context);
                    }
                    
                    // Si pas mural ou échec, on tente au sol
                    if (stateToPlace == null) {
                        stateToPlace = BlockInit.MAGIC_TORCH.get().getStateForPlacement(context);
                    }
                    
                    if (stateToPlace != null && stateToPlace.canSurvive(level, placePos)) {
                        level.setBlock(placePos, stateToPlace, 3);
                        // Son de placement
                        level.levelEvent(2001, placePos, net.minecraft.world.level.block.Block.getId(stateToPlace));
                        player.swing(InteractionHand.MAIN_HAND, true);
                        
                        // Mise à jour du cooldown seulement si succès
                        lastTorchTime.put(uuid, now);
                        
                        // Pas de consommation pour la torche infinie
                    }
                }
            }
        }
    }
}
