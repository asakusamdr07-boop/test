package com.renxin.block.entity;

import com.renxin.item.CustomMusicDiscItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class MusicPlayerBlock extends BlockWithEntity {

    // 新增：朝向属性
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public MusicPlayerBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MusicPlayerBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof MusicPlayerBlockEntity playerBe)) {
            return ActionResult.PASS;
        }

        ItemStack held = player.getStackInHand(hand);
        ItemStack stored = playerBe.getStack(0);

        // 已经有唱片 -> 弹出并停止播放
        if (!stored.isEmpty()) {
            ItemStack toGive = stored.copy();
            playerBe.setStack(0, ItemStack.EMPTY);
            if (!player.getInventory().insertStack(toGive)) {
                player.dropItem(toGive, false);
            }
            if (world instanceof ServerWorld serverWorld) {
                playerBe.stopPlayback(serverWorld);
            }
            return ActionResult.CONSUME;
        }

        // 没唱片 -> 如果手里拿的是已刻录唱片，就塞进去并开始播放
        if (!held.isEmpty() && held.getItem() instanceof CustomMusicDiscItem) {
            ItemStack one = held.split(1);
            playerBe.setStack(0, one);
            if (world instanceof ServerWorld serverWorld) {
                playerBe.startPlayback(serverWorld);
            }
            return ActionResult.CONSUME;
        }

        return ActionResult.PASS;
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos,
                               Block sourceBlock, BlockPos sourcePos, boolean notify) {
        // 保留原版行为
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);

        if (world.isClient) return;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof MusicPlayerBlockEntity playerBe && world instanceof ServerWorld serverWorld) {
            boolean powered = world.isReceivingRedstonePower(pos);
            playerBe.onRedstoneUpdate(serverWorld, powered);
        }
    }

    // ===== 朝向相关 =====

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // 让正面朝向玩家
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }
}
