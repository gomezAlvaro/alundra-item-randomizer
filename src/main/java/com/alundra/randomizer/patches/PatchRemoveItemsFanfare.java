package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.PsxExeFile;

import static com.alundra.randomizer.game.ItemCodes.ITEM_COUNT;

/**
 * In vanilla game, rare items trigger a fanfare when obtained from a chest. This patch removes this behavior.
 */
public class PatchRemoveItemsFanfare extends GamePatch {
    private static final long ITEM_INFO_2_TABLE_START = 0xA85E4;

    @Override
    public void alterExeFile(PsxExeFile exe, GameFiles files, GameData gameData, RandomizerWorld world) {
        for (int i = 0; i < ITEM_COUNT; ++i) {
            long itemAddress = ITEM_INFO_2_TABLE_START + (i * 8L);
            // If item was using the fanfare effect (0x00), use the common item effect instead (0x34)
            if (exe.getByte(itemAddress + 5) == 0x00)
                exe.setByte(itemAddress + 5, 0x34);
        }
    }
}
