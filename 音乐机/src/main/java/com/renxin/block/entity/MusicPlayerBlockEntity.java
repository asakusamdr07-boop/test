package com.renxin.block.entity;

import com.renxin.audio.ServerTrackRegistry;
import com.renxin.cpmod.network.CpModNetworking;
import com.renxin.item.CustomMusicDiscItem;
import com.renxin.registry.CpBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Path;
import java.util.UUID;

/**
 * 音乐机方块实体：
 *  - 只有一个物品槽，放一张自定义唱片
 *  - 通过点击或红石信号控制播放/停止
 *  - 播放时从服务器读取对应的 OGG 文件并推送给客户端
 */
public class MusicPlayerBlockEntity extends BlockEntity implements Inventory {

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(1, ItemStack.EMPTY);

    private boolean powered = false;
    private boolean playing = false;

    public MusicPlayerBlockEntity(BlockPos pos, BlockState state) {
        super(CpBlockEntities.MUSIC_PLAYER_ENTITY, pos, state);
    }

    // ===== Inventory 实现 =====

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.get(0).isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(items, slot, amount);
        if (!result.isEmpty()) {
            markDirty();
        }
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack result = Inventories.removeStack(items, slot);
        if (!result.isEmpty()) {
            markDirty();
        }
        return result;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
        markDirty();
    }

    @Override
    public void clear() {
        items.clear();
        markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        if (world == null || world.getBlockEntity(pos) != this) {
            return false;
        }
        return player.squaredDistanceTo(
                (double) pos.getX() + 0.5D,
                (double) pos.getY() + 0.5D,
                (double) pos.getZ() + 0.5D
        ) <= 64.0D;
    }

    // ===== NBT 持久化 =====

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        Inventories.readNbt(nbt, items);
        powered = nbt.getBoolean("Powered");
        playing = nbt.getBoolean("Playing");
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, items);
        nbt.putBoolean("Powered", powered);
        nbt.putBoolean("Playing", playing);
    }

    // ===== 红石控制 =====

    public void onRedstoneUpdate(ServerWorld world, boolean newPowered) {
        if (newPowered == this.powered) {
            return;
        }
        this.powered = newPowered;

        if (newPowered) {
            // Redstone on -> start if we have a disc
            if (!getStack(0).isEmpty()) {
                startPlayback(world);
            }
        } else {
            // Redstone off -> stop
            stopPlayback(world);
        }
    }

    // ===== 播放控制 =====

    /**
     * 开始播放：从唱片上拿 trackId，给客户端发 OGG 分片，再发播放指令。
     */
    public void startPlayback(ServerWorld world) {
        ItemStack stack = getStack(0);
        if (stack.isEmpty() || !(stack.getItem() instanceof CustomMusicDiscItem)) {
            return;
        }
        if (playing) {
            return;
        }

        // 1. 从唱片 NBT 中拿出 trackId
        UUID trackId = CustomMusicDiscItem.getTrackId(stack);
        if (trackId == null) {
            return;
        }

        // 2. 找到服务器上对应的 .ogg 文件
        Path trackPath = ServerTrackRegistry.getTrackPath(trackId);
        if (trackPath == null || !trackPath.toFile().exists()) {
            return;
        }

        playing = true;
        markDirty();

        // 3. 先把该曲目的 OGG 分片发给当前世界的所有玩家
        for (ServerPlayerEntity player : world.getPlayers()) {
            // 以后可以在这里加距离判断，只给附近玩家发
            CpModNetworking.sendTrackDataToPlayer(player, trackId, trackPath);
        }

        // 4. 再广播“开始播放”控制包（携带 trackId）
        CpModNetworking.sendPlayControl(world, pos, trackId, true);
    }

    /**
     * 停止播放：只需要广播停止指令即可。
     */
    public void stopPlayback(ServerWorld world) {
        if (!playing) {
            return;
        }
        playing = false;
        markDirty();

        // 停止时 trackId 可以为 null，客户端只根据位置停掉声音即可
        CpModNetworking.sendPlayControl(world, pos, null, false);
    }
}
