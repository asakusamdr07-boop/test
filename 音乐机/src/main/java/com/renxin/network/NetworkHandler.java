package com.renxin.network;

import com.renxin.audio.ServerTrackRegistry;
import com.renxin.item.BlankDiscItem;
import com.renxin.item.CustomMusicDiscItem;
import com.renxin.registry.CpItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务器端音频上传分片重组 + 刻录逻辑。
 */
public final class NetworkHandler {

    private static final Logger LOGGER = LogManager.getLogger("cp-mod-network");

    /** 服务器强制上限，防止恶意或错误请求把内存打爆（这里设为 8MB） */
    private static final int HARD_MAX_SIZE = 8 * 1024 * 1024;

    private NetworkHandler() {}

    /**
     * 一次上传会话。
     */
    private static class UploadSession {
        final UUID uploadId;
        final UUID playerUuid;
        final BlockPos burnerPos;
        final String fileName;
        final int totalSize;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        UploadSession(ServerPlayerEntity player,
                      BlockPos burnerPos,
                      UUID uploadId,
                      String fileName,
                      int totalSize) {
            this.uploadId = uploadId;
            this.playerUuid = player.getUuid();
            this.burnerPos = burnerPos.toImmutable();
            this.fileName = fileName;
            this.totalSize = totalSize;
        }
    }

    /** 所有正在进行中的上传，以 uploadId 为 key */
    private static final Map<UUID, UploadSession> UPLOAD_SESSIONS = new ConcurrentHashMap<>();

    // ========= Start / Chunk / Complete 三步 =========

    public static void handleUploadStart(ServerPlayerEntity player,
                                         BlockPos burnerPos,
                                         UUID uploadId,
                                         String fileName,
                                         int totalSize) {
        if (totalSize <= 0 || totalSize > HARD_MAX_SIZE) {
            LOGGER.warn("Reject upload {} from {}: invalid totalSize={}",
                    uploadId, player.getName().getString(), totalSize);
            return;
        }

        UploadSession session = new UploadSession(player, burnerPos, uploadId, fileName, totalSize);
        UPLOAD_SESSIONS.put(uploadId, session);

        LOGGER.info("Start upload {} from {} at {} (file='{}', size={} bytes)",
                uploadId, player.getName().getString(), burnerPos, fileName, totalSize);
    }

    public static void handleUploadChunk(ServerPlayerEntity player,
                                         UUID uploadId,
                                         int index,
                                         byte[] chunk) {
        UploadSession session = UPLOAD_SESSIONS.get(uploadId);
        if (session == null) {
            LOGGER.debug("Received chunk for unknown upload {} (index={}) from {}",
                    uploadId, index, player.getName().getString());
            return;
        }

        if (!player.getUuid().equals(session.playerUuid)) {
            LOGGER.warn("Player {} tried to send chunk for upload {} owned by {}",
                    player.getName().getString(), uploadId, session.playerUuid);
            return;
        }

        try {
            session.buffer.write(chunk);
        } catch (IOException e) {
            LOGGER.error("Error buffering upload chunk {} (index={}) from {}",
                    uploadId, index, player.getName().getString(), e);
            UPLOAD_SESSIONS.remove(uploadId);
            return;
        }

        int current = session.buffer.size();
        if (current > session.totalSize || current > HARD_MAX_SIZE) {
            LOGGER.warn("Upload {} from {} exceeded size limit (current={}, total={}), aborting",
                    uploadId, player.getName().getString(), current, session.totalSize);
            UPLOAD_SESSIONS.remove(uploadId);
        }
    }

    public static void handleUploadComplete(ServerPlayerEntity player,
                                            BlockPos burnerPos,
                                            UUID uploadId) {
        UploadSession session = UPLOAD_SESSIONS.remove(uploadId);
        if (session == null) {
            LOGGER.warn("UploadComplete for unknown upload {} from {}",
                    uploadId, player.getName().getString());
            return;
        }

        if (!player.getUuid().equals(session.playerUuid)) {
            LOGGER.warn("Player {} tried to finish upload {} owned by {}",
                    player.getName().getString(), uploadId, session.playerUuid);
            return;
        }

        byte[] data = session.buffer.toByteArray();
        int size = data.length;

        if (size <= 0) {
            LOGGER.warn("Upload {} from {} finished with empty data",
                    uploadId, player.getName().getString());
            return;
        }

        if (size != session.totalSize) {
            LOGGER.warn("Upload {} size mismatch: reported {} bytes, received {} bytes",
                    uploadId, session.totalSize, size);
        }

        LOGGER.info("Upload {} from {} completed at {} (file='{}', size={} bytes)",
                uploadId, player.getName().getString(),
                session.burnerPos, session.fileName, size);

        // ===== 1. 把数据写入磁盘，得到 trackId =====
        UUID trackId;
        try {
            trackId = ServerTrackRegistry.registerUploadedTrack(
                    player, session.fileName, data
            );
        } catch (IOException e) {
            LOGGER.error("Failed to save uploaded track {} from {}", uploadId, player.getName().getString(), e);
            return;
        }

        // ===== 2. 在刻录机中刻录唱片 =====
        ServerWorld world = player.getServerWorld();
        BlockEntity be = world.getBlockEntity(session.burnerPos);
        if (!(be instanceof Inventory inv)) {
            LOGGER.warn("Block entity at {} is not an Inventory, cannot burn disc", session.burnerPos);
            return;
        }

        int inputSlot = 0;  // 约定：0 号槽为空唱片输入
        int outputSlot = 1; // 约定：1 号槽为成品输出

        ItemStack in = inv.getStack(inputSlot);
        if (in.isEmpty() || !(in.getItem() instanceof BlankDiscItem)) {
            LOGGER.warn("Upload complete but no BlankDiscItem in input slot at {}", session.burnerPos);
            return;
        }

        // 生成成品唱片
        ItemStack result = new ItemStack(CpItems.CUSTOM_MUSIC_DISC);
        CustomMusicDiscItem.setTrack(result, trackId, session.fileName);

        // 消耗一张空唱片
        in.decrement(1);
        inv.setStack(inputSlot, in);

        // 放到输出槽或玩家身上
        ItemStack out = inv.getStack(outputSlot);
        if (out.isEmpty()) {
            inv.setStack(outputSlot, result);
        } else {
            // 输出槽被占满，尝试塞进玩家背包，否则掉落到地上
            if (!player.getInventory().insertStack(result)) {
                player.dropItem(result, false);
            }
        }

        inv.markDirty();
        BlockState state = world.getBlockState(session.burnerPos);
        world.updateListeners(session.burnerPos, state, state, 3);

        LOGGER.info("Burned custom music disc at {} for track {}", session.burnerPos, trackId);
    }
}
