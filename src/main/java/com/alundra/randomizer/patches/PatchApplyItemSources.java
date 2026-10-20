package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.ItemSource;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;
import com.alundra.randomizer.tools.PsxExeFile;

import static com.alundra.randomizer.game.ItemCodes.ITEM_NONE;

/**
 * Writes the contents of the item sources inside the game files.
 */
public class PatchApplyItemSources extends GamePatch {
    private static final long ITEM_INFO_TABLE_ADDR = 0x800C7DE4L;

    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        for (ItemSource source : world.itemSources()) {
            // Apply item ID addresses
            int itemId = source.item() != null ? source.item().id() : ITEM_NONE;
            for (long addr : source.addresses())
                data.setByte(addr, itemId);

            // Apply sprite ID addresses
            int itemSpriteId = (itemId + 0x1E) & 0xFF;
            for (long addr : source.spriteAddresses()) {
                // If item is none, make it an inactive open chest
                if (itemId == ITEM_NONE) {
                    for (long subAddr = addr + 4; subAddr <= addr + 13; ++subAddr)
                        data.setByte(subAddr, 0x00);
                }
                data.setByte(addr, itemSpriteId);
            }

            // Apply item price addresses
            for (long addr : source.priceAddresses())
                data.setWordLE(addr, source.item().goldValue());
        }

        // Fix ground Book of Elna byte causing trouble
        data.setByte(0x1BA5D05, 0x00);

        // Change the "repeating" slot at Merrick's shop visually into an open chest
        data.setByte(0x1EBF9CE, 0x1E);
    }

    @Override
    public void alterExeFile(PsxExeFile exe, GameFiles files, GameData gameData, RandomizerWorld world) {
        // Handle Merrick shop item table, located inside the code. This is necessary to change the item name.
        for (ItemSource source : world.itemSources()) {
            long merrickItemAddress = source.merrickItemAddress();
            if (merrickItemAddress != 0) {
                int itemId = source.item() != null ? source.item().id() : ITEM_NONE;
                exe.setLongLE(merrickItemAddress, ITEM_INFO_TABLE_ADDR + 8L * itemId);
            }
        }
    }
}
