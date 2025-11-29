package com.renxin.registry;

import com.renxin.block.entity.MusicBurnerBlock;
import com.renxin.block.entity.MusicPlayerBlock;
import com.renxin.cpmod.CpModConstants;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class CpBlocks {

    public static final Block MUSIC_BURNER = register("music_burner",
            new MusicBurnerBlock(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).requiresTool()));
    public static final Block MUSIC_PLAYER = register("music_player",
            new MusicPlayerBlock(FabricBlockSettings.copyOf(Blocks.NOTE_BLOCK).requiresTool()));

    private static Block register(String name, Block block) {
        Identifier id = new Identifier(CpModConstants.MOD_ID, name);
        return Registry.register(Registries.BLOCK, id, block);
    }

    public static void init() {
        // Trigger class loading; actual work is done in static initializers.
        CpModConstants.LOGGER.debug("CpBlocks initialized");
    }
}
