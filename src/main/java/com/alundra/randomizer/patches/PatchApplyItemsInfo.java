package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.game.Item;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.PsxExeFile;

import static com.alundra.randomizer.game.ItemCodes.ITEM_COUNT;

/**
 * Writes the inventory slot and tier of every item inside the executable's item info table.
 */
public class PatchApplyItemsInfo extends GamePatch {
    private static final long ITEM_INFO_TABLE_START = 0x9C650;
    private static final int ITEM_INFO_STRUCT_SIZE = 10;

    @Override
    public void alterExeFile(PsxExeFile exe, GameFiles files, GameData gameData, RandomizerWorld world) {
        for (int itemId = 1; itemId < ITEM_COUNT; ++itemId) {
            Item item = gameData.item(itemId);
            long baseAddr = ITEM_INFO_TABLE_START + ((long) itemId * ITEM_INFO_STRUCT_SIZE);
            exe.setWordLE(baseAddr, item.slotId());
            exe.setWordLE(baseAddr + 4, item.tier());
        }
    }
}
