package com.renxin.cpmod.network;

import com.renxin.client.audio.ClientTrackCache;
import com.renxin.client.audio.MusicClientManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * 客户端侧网络工具：
 *  - 注册 S2C 包接收器（播放控制 + 音轨分片）
 *  - （可选）提供上传相关 C2S 工具
 */
public final class CpModClientNetworking {

    private CpModClientNetworking() {}

    // 复用服务端定义的通道 ID
    public static final Identifier PLAY_CONTROL        = CpModNetworking.PLAY_CONTROL;
    public static final Identifier UPLOAD_START        = CpModNetworking.UPLOAD_START;
    public static final Identifier UPLOAD_CHUNK        = CpModNetworking.UPLOAD_CHUNK;
    public static final Identifier UPLOAD_COMPLETE     = CpModNetworking.UPLOAD_COMPLETE;
    public static final Identifier TRACK_DATA_START    = CpModNetworking.TRACK_DATA_START;
    public static final Identifier TRACK_DATA_CHUNK    = CpModNetworking.TRACK_DATA_CHUNK;
    public static final Identifier TRACK_DATA_COMPLETE = CpModNetworking.TRACK_DATA_COMPLETE;

    // ===== S2C 接收器注册 =====

    public static void registerClientReceivers() {
        // 播放 / 停止 控制
        ClientPlayNetworking.registerGlobalReceiver(
                PLAY_CONTROL,
                (client, handler, buf, responseSender) -> {
                    BlockPos pos = buf.readBlockPos();
                    boolean playing = buf.readBoolean();
                    UUID trackId = null;
                    if (playing && buf.readableBytes() >= 16) {
                        trackId = buf.readUuid();
                    }
                    UUID finalTrackId = trackId;
                    client.execute(() -> {
                        if (playing) {
                            MusicClientManager.playBlockTrack(pos, finalTrackId);
                        } else {
                            MusicClientManager.stopBlockTrack(pos);
                        }
                    });
                });

        // 音轨数据：开始
        ClientPlayNetworking.registerGlobalReceiver(
                TRACK_DATA_START,
                (client, handler, buf, responseSender) -> {
                    UUID trackId = buf.readUuid();
                    int totalSize = buf.readInt();
                    client.execute(() -> ClientTrackCache.begin(trackId, totalSize));
                });

        // 音轨数据：分片
        ClientPlayNetworking.registerGlobalReceiver(
                TRACK_DATA_CHUNK,
                (client, handler, buf, responseSender) -> {
                    UUID trackId = buf.readUuid();
                    int len = buf.readInt();
                    byte[] data = new byte[len];
                    buf.readBytes(data);
                    client.execute(() -> ClientTrackCache.append(trackId, data));
                });

        // 音轨数据：结束
        ClientPlayNetworking.registerGlobalReceiver(
                TRACK_DATA_COMPLETE,
                (client, handler, buf, responseSender) -> {
                    UUID trackId = buf.readUuid();
                    client.execute(() -> ClientTrackCache.finish(trackId));
                });
    }

    // ===== C2S 上传工具（你的 GUI 现在如果没用，可以忽略） =====

    public static void sendUploadStart(BlockPos burnerPos, UUID uploadId, String fileName, int totalSize) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(burnerPos);
        buf.writeUuid(uploadId);
        buf.writeString(fileName);
        buf.writeInt(totalSize);
        ClientPlayNetworking.send(UPLOAD_START, buf);
    }

    public static void sendUploadChunk(BlockPos burnerPos, UUID uploadId, byte[] data, int length) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(burnerPos);
        buf.writeUuid(uploadId);
        buf.writeInt(length);
        buf.writeBytes(data, 0, length);
        ClientPlayNetworking.send(UPLOAD_CHUNK, buf);
    }

    public static void sendUploadComplete(BlockPos burnerPos, UUID uploadId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(burnerPos);
        buf.writeUuid(uploadId);
        ClientPlayNetworking.send(UPLOAD_COMPLETE, buf);
    }
}
