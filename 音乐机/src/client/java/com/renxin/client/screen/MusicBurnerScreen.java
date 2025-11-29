package com.renxin.client.screen;

import com.renxin.cpmod.CpModConstants;
import com.renxin.cpmod.network.CpModNetworking;
import com.renxin.network.UploadStartC2SPacket;
import com.renxin.network.UploadChunkC2SPacket;
import com.renxin.network.UploadCompleteC2SPacket;
import com.renxin.screen.MusicBurnerScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.UUID;

/**
 * 刻录机界面（客户端）：
 *  - 上半部分自定义布局
 *  - 下半部分沿用熔炉的物品栏
 *  - 点击“刻录”按钮后，按分片方式上传 OGG 文件到服务器
 */
public class MusicBurnerScreen extends HandledScreen<MusicBurnerScreenHandler> {

    private static final Identifier FURNACE_TEXTURE =
            new Identifier("minecraft", "textures/gui/container/furnace.png");

    private ButtonWidget burnButton;

    // 底部渐隐提示
    private Text overlayText;
    private int overlayTimer;
    private static final int OVERLAY_DURATION = 60; // 约 3 秒

    public MusicBurnerScreen(MusicBurnerScreenHandler handler,
                             PlayerInventory inventory,
                             Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;

        int buttonWidth = 52;
        int buttonHeight = 20;
        int buttonX = x + (this.backgroundWidth - buttonWidth) / 2;
        int buttonY = y + 60; // 在两个物品槽与玩家物品栏之间

        this.burnButton = ButtonWidget.builder(
                        Text.translatable("cp-mod.screen.burn"),
                        button -> onBurnButtonPressed()
                )
                .dimensions(buttonX, buttonY, buttonWidth, buttonHeight)
                .build();

        this.addDrawableChild(this.burnButton);
    }

    /** 在屏幕底部显示一行渐隐提示 */
    private void showOverlay(Text text) {
        this.overlayText = text;
        this.overlayTimer = OVERLAY_DURATION;
    }

    /** 点击“刻录”按钮 */
    private void onBurnButtonPressed() {
        // 必须先有空唱片
        if (!this.handler.hasBlankDisc()) {
            showOverlay(Text.translatable("cp-mod.upload.no_blank_disc"));
            return;
        }

        File chosen = null;

        // 1) 优先使用 tinyfd 原生对话框
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String[] patterns = new String[]{"*.ogg"};
            PointerBuffer filterPatterns = stack.mallocPointer(patterns.length);
            for (String p : patterns) {
                filterPatterns.put(stack.UTF8(p));
            }
            filterPatterns.flip();

            String path = TinyFileDialogs.tinyfd_openFileDialog(
                    "选择要刻录的 OGG 文件",
                    null,
                    filterPatterns,
                    "OGG 音频文件 (*.ogg)",
                    false
            );

            if (path != null && !path.isEmpty()) {
                chosen = new File(path);
            }
        } catch (Throwable t) {
            CpModConstants.LOGGER.error("Failed to open tinyfd file dialog", t);
        }

        // 2) 如果 tinyfd 没拿到，就在非 headless 环境下用 Swing JFileChooser 兜底
        if (chosen == null) {
            if (!GraphicsEnvironment.isHeadless()) {
                try {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setDialogTitle("选择要刻录的 OGG 文件");
                    chooser.setFileFilter(new FileNameExtensionFilter("OGG 音频文件 (*.ogg)", "ogg"));
                    int result = chooser.showOpenDialog(null);
                    if (result == JFileChooser.APPROVE_OPTION) {
                        File file = chooser.getSelectedFile();
                        if (file != null) {
                            chosen = file;
                        }
                    } else {
                        // 用户取消，不算错误，直接返回
                        return;
                    }
                } catch (Throwable t) {
                    CpModConstants.LOGGER.error("Failed to open Swing file chooser", t);
                    showOverlay(Text.translatable("cp-mod.upload.dialog_failed"));
                    return;
                }
            } else {
                showOverlay(Text.translatable("cp-mod.upload.dialog_failed"));
                return;
            }
        }

        if (chosen == null) {
            showOverlay(Text.translatable("cp-mod.upload.no_file"));
            return;
        }

        startUpload(chosen);
    }

    /** 检查文件并以分片方式发送到服务器 */
    private void startUpload(File file) {
        if (!this.handler.hasBlankDisc()) {
            showOverlay(Text.translatable("cp-mod.upload.no_blank_disc"));
            return;
        }

        if (file == null || !file.isFile()) {
            showOverlay(Text.translatable("cp-mod.upload.read_failed"));
            return;
        }

        String lowerName = file.getName().toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".ogg")) {
            showOverlay(Text.translatable("cp-mod.upload.invalid_extension"));
            return;
        }

        long maxSize = 8L * 1024L * 1024L;
        if (file.length() > maxSize) {
            showOverlay(Text.translatable("cp-mod.upload.too_large"));
            return;
        }

        byte[] data;
        try {
            data = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            CpModConstants.LOGGER.error("Failed to read file for upload", e);
            showOverlay(Text.translatable("cp-mod.upload.read_failed"));
            return;
        }

        BlockPos pos = this.handler.getBlockPos();
        UUID uploadId = UUID.randomUUID();
        int totalSize = data.length;

        // === 1. 发送 UploadStart C2S ===
        PacketByteBuf startBuf = PacketByteBufs.create();
        startBuf.writeBlockPos(pos);
        startBuf.writeUuid(uploadId);
        startBuf.writeString(file.getName());
        startBuf.writeInt(totalSize);
        ClientPlayNetworking.send(CpModNetworking.UPLOAD_START, startBuf);

        // === 2. 分片发送 UploadChunk C2S ===
        final int chunkSize = UploadChunkC2SPacket.MAX_CHUNK_SIZE;
        int index = 0;
        for (int offset = 0; offset < totalSize; offset += chunkSize) {
            int len = Math.min(chunkSize, totalSize - offset);

            PacketByteBuf chunkBuf = PacketByteBufs.create();
            chunkBuf.writeUuid(uploadId);
            chunkBuf.writeVarInt(index);
            chunkBuf.writeVarInt(len);
            chunkBuf.writeBytes(data, offset, len);

            ClientPlayNetworking.send(CpModNetworking.UPLOAD_CHUNK, chunkBuf);
            index++;
        }

        // === 3. 发送 UploadComplete C2S ===
        PacketByteBuf doneBuf = PacketByteBufs.create();
        doneBuf.writeBlockPos(pos);
        doneBuf.writeUuid(uploadId);
        ClientPlayNetworking.send(CpModNetworking.UPLOAD_COMPLETE, doneBuf);

        showOverlay(Text.translatable("cp-mod.upload.sending"));
    }


    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;

        // 背景：整张熔炉 GUI（包括下方物品栏）
        context.drawTexture(FURNACE_TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight);

        // 覆盖上半部分，变成我们的灰色区域
        int topAreaTop = y + 16;
        int topAreaBottom = y + 72;
        int left = x + 7;
        int right = x + this.backgroundWidth - 7;
        int bgColor = 0xFFC6C6C6;
        context.fill(left, topAreaTop, right, topAreaBottom, bgColor);

        // 在灰色区域中画两个槽位背景，位置和 ScreenHandler 中的槽对齐
        int slotSize = 18;
        int slotBg = 0xFFE0E0E0;
        int slotEdge = 0xFF555555;

        int inputX = x + MusicBurnerScreenHandler.INPUT_SLOT_X - 1;
        int inputY = y + MusicBurnerScreenHandler.INPUT_SLOT_Y - 1;
        int outputX = x + MusicBurnerScreenHandler.OUTPUT_SLOT_X - 1;
        int outputY = y + MusicBurnerScreenHandler.OUTPUT_SLOT_Y - 1;

        // 输入槽
        context.fill(inputX, inputY, inputX + slotSize, inputY + slotSize, slotBg);
        context.fill(inputX, inputY, inputX + slotSize, inputY + 1, slotEdge);
        context.fill(inputX, inputY + slotSize - 1, inputX + slotSize, inputY + slotSize, slotEdge);
        context.fill(inputX, inputY, inputX + 1, inputY + slotSize, slotEdge);
        context.fill(inputX + slotSize - 1, inputY, inputX + slotSize, inputY + slotSize, slotEdge);

        // 输出槽
        context.fill(outputX, outputY, outputX + slotSize, outputY + slotSize, slotBg);
        context.fill(outputX, outputY, outputX + slotSize, outputY + 1, slotEdge);
        context.fill(outputX, outputY + slotSize - 1, outputX + slotSize, outputY + slotSize, slotEdge);
        context.fill(outputX, outputY, outputX + 1, outputY + slotSize, slotEdge);
        context.fill(outputX + slotSize - 1, outputY, outputX + slotSize, outputY + slotSize, slotEdge);

        // 中间画一个箭头（文本形式）
        String arrow = ">>";
        int arrowWidth = this.textRenderer.getWidth(arrow);
        int arrowX = x + (this.backgroundWidth - arrowWidth) / 2;
        int arrowY = y + 32;
        context.drawText(this.textRenderer, arrow, arrowX, arrowY, 0xFF3F3F3F, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);

        // 最后绘制底部提示，确保在最上层
        if (this.overlayText != null && this.overlayTimer > 0) {
            float alpha = this.overlayTimer / (float) OVERLAY_DURATION;
            int a = (int) (alpha * 255.0f) & 0xFF;
            int color = (a << 24) | 0x00FFFFFF;

            int x = this.width / 2 - this.textRenderer.getWidth(this.overlayText) / 2;
            int y = this.height - 40;
            context.drawTextWithShadow(this.textRenderer, this.overlayText, x, y, color);
            this.overlayTimer--;
        }
    }
}
