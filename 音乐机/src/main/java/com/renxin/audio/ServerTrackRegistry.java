package com.renxin.audio;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 服务器端音轨存储工具。
 *
 * 目前设计非常简单：
 *  - 每一首歌对应一个 UUID = trackId
 *  - 文件路径为 <游戏目录>/cp-mod/tracks/<trackId>.ogg
 *  - 唱片 NBT 里只记录 trackId + 原始文件名
 *
 * 以后要做 track 列表 / 元数据再扩展就行。
 */
public final class ServerTrackRegistry {

    private static final Logger LOGGER = LogManager.getLogger("cp-mod-tracks");

    private ServerTrackRegistry() {}

    /** 存储目录：.minecraft/cp-mod/tracks */
    private static Path getTracksDir() {
        return FabricLoader.getInstance()
                .getGameDir()
                .resolve("cp-mod")
                .resolve("tracks");
    }

    /**
     * 将上传好的音频数据写入磁盘，返回新的 trackId。
     *
     * @param player   上传者
     * @param fileName 原始文件名（用于显示）
     * @param data     OGG 文件字节
     */
    public static UUID registerUploadedTrack(ServerPlayerEntity player,
                                             String fileName,
                                             byte[] data) throws IOException {
        UUID trackId = UUID.randomUUID();

        Path dir = getTracksDir();
        Files.createDirectories(dir);

        Path filePath = dir.resolve(trackId.toString() + ".ogg");
        Files.write(filePath, data);

        LOGGER.info("Saved uploaded track {} from {} (file='{}', size={} bytes) -> {}",
                trackId,
                player.getName().getString(),
                fileName,
                data.length,
                filePath.toAbsolutePath());

        return trackId;
    }

    /**
     * 根据 trackId 得到对应文件路径。
     * 播放时可以用这个路径去读 OGG 数据。
     */
    public static Path getTrackPath(UUID trackId) {
        return getTracksDir().resolve(trackId.toString() + ".ogg");
    }
}
