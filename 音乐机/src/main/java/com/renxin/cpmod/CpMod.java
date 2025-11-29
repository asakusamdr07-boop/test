package com.renxin.cpmod;

import com.renxin.registry.CpBlockEntities;
import com.renxin.registry.CpBlocks;
import com.renxin.registry.CpItems;
import com.renxin.registry.CpScreenHandlers;
import com.renxin.registry.CpSounds;
import com.renxin.cpmod.network.CpModNetworking;
import net.fabricmc.api.ModInitializer;

/**
 * 模组公共入口（服务端 / 单人世界均会调用）。
 */
public class CpMod implements ModInitializer {

    @Override
    public void onInitialize() {
        CpModConstants.LOGGER.info("Initializing {}", CpModConstants.MOD_NAME);

        // Registry bootstrap
        CpBlocks.init();
        CpItems.init();
        CpBlockEntities.init();
        CpScreenHandlers.init();
        CpSounds.init();

        // 网络通道注册（上传 + 播放控制 + 音轨分片）
        CpModNetworking.registerServerReceivers();
    }
}
