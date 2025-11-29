package com.renxin.cpmod.network;

import com.renxin.cpmod.CpModConstants;
import com.renxin.network.UploadChunkC2SPacket;
import com.renxin.network.UploadCompleteC2SPacket;
import com.renxin.network.UploadStartC2SPacket;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 本模组所有网络通道的统一注册与工具方法。
 *
 * - C2S：upload_* 三个分片上传包
 * - S2C：track_data_* 三个音轨数据包 + play_control 播放控制包
 */
public final class CpModNetworking {

    private CpModNetworking() {}

    private static Identifier id(String path) {
        return new Identifier(CpModConstants.MOD_ID, path);
    }

    // ===== C2S：上传音频 =====

    public static final Identifier UPLOAD_START   = id("upload_start");
    public static final Identifier UPLOAD_CHUNK   = id("upload_chunk");
    public static final Identifier UPLOAD_COMPLETE = id("upload_complete");

    // ===== S2C：下发音轨数据与播放控制 =====

    public static final Identifier TRACK_DATA_START    = id("track_data_start");
    public static final Identifier TRACK_DATA_CHUNK    = id("track_data_chunk");
    public static final Identifier TRACK_DATA_COMPLETE = id("track_data_complete");

    public static final Identifier PLAY_CONTROL = id("play_control");

    /** 在公共初始化阶段调用一次，注册所有 C2S 处理器。 */
    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_START, UploadStartC2SPacket::receive);
        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_CHUNK, UploadChunkC2SPacket::receive);
        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_COMPLETE, UploadCompleteC2SPacket::receive);
    }

    /** 对旧代码的兼容入口。 */
    public static void registerServerReceivers() {
        registerC2SPackets();
    }

    // ===== S2C 工具：发送 OGG 分片给指定玩家 =====

    /**
     * 将服务器上的某一首曲目（完整 .ogg 文件）按分片形式发送给客户端。
     *
     * @param player   目标玩家
     * @param trackId  曲目的 UUID
     * @param trackPath 服务器端 .ogg 文件路径
     */
    public static void sendTrackDataToPlayer(ServerPlayerEntity player, UUID trackId, Path trackPath) {
        byte[] allBytes;
        try {
            allBytes = Files.readAllBytes(trackPath);
        } catch (IOException e) {
            CpModConstants.LOGGER.error("Failed to read track file {} for {}", trackPath, trackId, e);
            return;
        }

        int totalSize = allBytes.length;
        // 每个分片最多 16KB，保证单个包不会触发 32767 bytes 限制
        int chunkSize = 16 * 1024;

        // START
        PacketByteBuf startBuf = PacketByteBufs.create();
        startBuf.writeUuid(trackId);
        startBuf.writeInt(totalSize);
        ServerPlayNetworking.send(player, TRACK_DATA_START, startBuf);

        // CHUNK
        int offset = 0;
        while (offset < totalSize) {
            int len = Math.min(chunkSize, totalSize - offset);

            PacketByteBuf chunkBuf = PacketByteBufs.create();
            chunkBuf.writeUuid(trackId);
            chunkBuf.writeInt(len);
            chunkBuf.writeBytes(allBytes, offset, len);

            ServerPlayNetworking.send(player, TRACK_DATA_CHUNK, chunkBuf);
            offset += len;
        }

        // COMPLETE
        PacketByteBuf completeBuf = PacketByteBufs.create();
        completeBuf.writeUuid(trackId);
        ServerPlayNetworking.send(player, TRACK_DATA_COMPLETE, completeBuf);
    }

    // ===== S2C：播放控制广播 =====

    /**
     * 广播播放/停止控制给世界中所有玩家。
     *
     * @param world   服务器世界
     * @param pos     声源（音乐机方块）位置
     * @param trackId 曲目 ID；停止时可以为 null
     * @param playing true = 开始播放；false = 停止播放
     */
    public static void sendPlayControl(ServerWorld world, BlockPos pos, UUID trackId, boolean playing) {
        if (world == null) {
            return;
        }

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeBoolean(playing);
        if (playing && trackId != null) {
            buf.writeUuid(trackId);
        }

        for (ServerPlayerEntity player : world.getPlayers()) {
            ServerPlayNetworking.send(player, PLAY_CONTROL, buf);
        }
    }

    /** 兼容旧签名：不传 trackId 时仅用于“停止播放”。 */
    public static void sendPlayControl(ServerWorld world, BlockPos pos, boolean playing) {
        sendPlayControl(world, pos, null, playing);
    }
}
