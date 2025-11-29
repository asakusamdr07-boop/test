package com.renxin.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * 已刻录的自定义唱片。
 * NBT 中记录：
 *  - trackId：对应服务器上的 OGG 文件
 *  - trackName：原始文件名（用于显示）
 *
 * 继承 MusicDiscItem 以便与原版唱片体系和其它模组（如精妙背包）兼容。
 */
public class CustomMusicDiscItem extends MusicDiscItem {

    private static final String NBT_TRACK_ID = "cp_mod_track_id";
    private static final String NBT_TRACK_NAME = "cp_mod_track_name";

    /** 比较器输出，保持和大部分唱片一致 */
    private static final int COMPARATOR_OUTPUT = 15;
    /** 供原版用的预估长度（秒），实际播放长度由 OGG 文件决定 */
    private static final int DEFAULT_LENGTH_SECONDS = 180;

    public CustomMusicDiscItem(Settings settings) {
        // 这里使用 cat 作为占位声音；真正的音频走我们自定义的 OGG 流。
        super(COMPARATOR_OUTPUT, SoundEvents.MUSIC_DISC_CAT, settings, DEFAULT_LENGTH_SECONDS);
    }

    /** 在唱片上写入 track 信息 */
    public static void setTrack(ItemStack stack, UUID trackId, String fileName) {
        if (stack == null || stack.isEmpty() || trackId == null) {
            return;
        }
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putUuid(NBT_TRACK_ID, trackId);
        if (fileName != null && !fileName.isEmpty()) {
            nbt.putString(NBT_TRACK_NAME, fileName);
        } else {
            nbt.remove(NBT_TRACK_NAME);
        }
    }

    /** 从唱片读出 trackId */
    public static UUID getTrackId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.containsUuid(NBT_TRACK_ID)) return null;
        return nbt.getUuid(NBT_TRACK_ID);
    }

    /** 从唱片读出曲名（原始文件名），可能为空字符串。 */
    public static String getTrackName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_TRACK_NAME)) return "";
        return nbt.getString(NBT_TRACK_NAME);
    }

    @Override
    public Text getName(ItemStack stack) {
        String trackName = getTrackName(stack);
        if (trackName != null && !trackName.isEmpty()) {
            // item.cp-mod.custom_music_disc.named = "唱片（%s）"
            return Text.translatable("item.cp-mod.custom_music_disc.named", trackName);
        }
        // 默认名字：“唱片”
        return Text.translatable("item.cp-mod.custom_music_disc");
    }
}
