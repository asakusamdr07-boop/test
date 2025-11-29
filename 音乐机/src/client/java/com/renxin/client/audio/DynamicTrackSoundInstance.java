package com.renxin.client.audio;

import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 使用客户端缓存的 OGG 数据来播放的动态声音实例。
 */
public class DynamicTrackSoundInstance extends AbstractSoundInstance implements FabricSoundInstance {

    private final UUID trackId;

    public DynamicTrackSoundInstance(SoundEvent soundEvent, BlockPos pos, UUID trackId) {
        super(soundEvent.getId(), SoundCategory.RECORDS, SoundInstance.createRandom());
        this.trackId = trackId;

        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;

        this.volume = 1.0F;
        this.pitch = 1.0F;
        this.repeat = false;
        this.repeatDelay = 0;
        this.attenuationType = AttenuationType.LINEAR;
        this.relative = false;
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundLoader loader, Identifier id, boolean repeatInstantly) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return ClientTrackCache.createStream(trackId);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }, Util.getMainWorkerExecutor());
    }
}
