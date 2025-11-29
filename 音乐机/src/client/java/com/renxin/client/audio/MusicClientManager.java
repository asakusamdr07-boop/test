package com.renxin.client.audio;

import com.renxin.registry.CpSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 管理客户端侧的音乐播放。
 *  - 记录每个方块位置正在播放的 DynamicTrackSoundInstance
 *  - 接收网络指令后开始/停止播放
 */
public class MusicClientManager {

    private static final Map<BlockPos, DynamicTrackSoundInstance> PLAYING = new HashMap<>();

    public static void init() {
        // 目前不需要额外初始化逻辑
    }

    public static void playBlockTrack(BlockPos pos, UUID trackId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        SoundManager soundManager = client.getSoundManager();
        if (soundManager == null || trackId == null) return;

        // 停掉旧的
        stopBlockTrack(pos);

        // 如果客户端还没收到这首歌的数据，就先不播
        if (!ClientTrackCache.has(trackId)) {
            return;
        }

        DynamicTrackSoundInstance instance =
                new DynamicTrackSoundInstance(CpSounds.DYNAMIC_DISC, pos, trackId);
        PLAYING.put(pos.toImmutable(), instance);
        soundManager.play(instance);
    }

    public static void stopBlockTrack(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        SoundManager soundManager = client.getSoundManager();
        if (soundManager == null) return;

        DynamicTrackSoundInstance instance = PLAYING.remove(pos);
        if (instance != null) {
            soundManager.stop(instance);
        }
    }

    public static void stopAll() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        SoundManager soundManager = client.getSoundManager();
        if (soundManager == null) return;

        for (DynamicTrackSoundInstance instance : PLAYING.values()) {
            soundManager.stop(instance);
        }
        PLAYING.clear();
    }
}
