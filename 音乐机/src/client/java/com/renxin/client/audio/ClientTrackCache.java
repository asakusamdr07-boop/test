package com.renxin.client.audio;

import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.OggAudioStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端音轨缓存：
 *  - 接收服务器分片下发的 OGG 数据
 *  - 在需要播放时提供一个 OggAudioStream
 */
public class ClientTrackCache {

    private static final Map<UUID, ByteArrayOutputStream> BUILDING = new ConcurrentHashMap<>();
    private static final Map<UUID, byte[]> TRACKS = new ConcurrentHashMap<>();

    public static void begin(UUID trackId, int expectedSize) {
        int cap = expectedSize > 0 ? expectedSize : 1024;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(cap);
        BUILDING.put(trackId, baos);
    }

    public static void append(UUID trackId, byte[] data) {
        ByteArrayOutputStream baos = BUILDING.get(trackId);
        if (baos == null || data == null || data.length == 0) {
            return;
        }
        try {
            baos.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void finish(UUID trackId) {
        ByteArrayOutputStream baos = BUILDING.remove(trackId);
        if (baos == null) {
            return;
        }
        TRACKS.put(trackId, baos.toByteArray());
    }

    public static boolean has(UUID trackId) {
        return TRACKS.containsKey(trackId);
    }

    public static void clear() {
        BUILDING.clear();
        TRACKS.clear();
    }

    /**
     * 为指定曲目创建一个新的 OggAudioStream。
     * 调用方负责关闭该 stream。
     */
    public static AudioStream createStream(UUID trackId) throws IOException {
        byte[] data = TRACKS.get(trackId);
        if (data == null || data.length == 0) {
            throw new IOException("No cached data for track " + trackId);
        }
        return new OggAudioStream(new ByteArrayInputStream(data));
    }
}
