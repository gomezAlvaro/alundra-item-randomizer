package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;
import com.alundra.randomizer.tools.MipsCode;
import com.alundra.randomizer.tools.MipsRegister;
import com.alundra.randomizer.tools.PsxExeFile;

import static com.alundra.randomizer.game.ItemCodes.*;
import static com.alundra.randomizer.tools.MipsRegister.*;

/**
 * Implements "upgrade" items: when obtained, an upgrade item is turned into the next tier of equipment for its
 * category (sword, flail, bow, armor, magic and optionally boots).
 */
public class PatchProgressiveItems extends GamePatch {
    private static final long FUN_FIND_OWNED_ITEM_OF_BEST_TIER_FOR_SLOT = 0x4F810;
    private static final long ITEM_INFO_TABLE = 0x800BBE50L;

    private final boolean progressiveBoots;

    public PatchProgressiveItems(boolean progressiveBoots) {
        this.progressiveBoots = progressiveBoots;
    }

    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        replaceItemSprite(data, ITEM_SWORD_UPGRADE, ITEM_SWORD);
        replaceItemSprite(data, ITEM_FLAIL_UPGRADE, ITEM_IRON_FLAIL);
        replaceItemSprite(data, ITEM_BOW_UPGRADE, ITEM_HUNTERS_BOW);
        replaceItemSprite(data, ITEM_ARMOR_UPGRADE, ITEM_ANCIENT_ARMOR);
        replaceItemSprite(data, ITEM_BOOTS_UPGRADE, ITEM_MERMAN_BOOTS);
        replaceItemSprite(data, ITEM_EARTH_MAGIC_UPGRADE, ITEM_EARTH_SCROLL);
        replaceItemSprite(data, ITEM_WATER_MAGIC_UPGRADE, ITEM_WATER_SCROLL);
        replaceItemSprite(data, ITEM_FIRE_MAGIC_UPGRADE, ITEM_FIRE_SCROLL);
        replaceItemSprite(data, ITEM_WIND_MAGIC_UPGRADE, ITEM_WIND_SCROLL);
    }

    @Override
    public void alterExeFile(PsxExeFile exe, GameFiles files, GameData gameData, RandomizerWorld world) {
        // Inject and call a function to alter obtained item ID if needed
        long funcAddr = injectFuncAlterItemId(exe);
        exe.setCode(0x303F4, new MipsCode().jal(funcAddr));

        // Modify the behavior of TryIncreasingItemQuantityInInventory which can behave weirdly
        // when trying to increase a progressive item quantity
        exe.setCode(0x14DFC, new MipsCode().addiu(V0, 0x1));
    }

    private long injectFuncAlterItemId(PsxExeFile exe) {
        MipsCode func = new MipsCode();

        final MipsRegister input = A0; // input is item ID
        final MipsRegister returnValue = V0;

        final MipsRegister itemInfoOffset = T0;
        final MipsRegister currentSlot = T0;
        final MipsRegister currentTier = T0;
        final MipsRegister currentItemId = T1;

        final MipsRegister inputItemSlot = T8;
        final MipsRegister bestItemId = T7;
        final MipsRegister tierToFind = T6;

        final MipsRegister storageA0 = T5;
        final MipsRegister storageA1 = T4;
        final MipsRegister storageRA = T3;

        func.set_(storageA0, A0);
        func.set_(storageA1, A1);
        func.set_(storageRA, RA);

        if (!progressiveBoots) {
            // When boots effects are split, boots are not progressive
            // if(item_id > ITEM_WIND_MAGIC_UPGRADE) return
            func.bgt_(A0, ITEM_WIND_MAGIC_UPGRADE, "return");

            // if(item_id >= ITEM_SHORT_BOOTS && item_id <= ITEM_CHARM_BOOTS) return
            func.blt_(A0, ITEM_SHORT_BOOTS, "do_process");
            func.bgt_(A0, ITEM_CHARM_BOOTS, "do_process");
            func.bra_("return");
        } else {
            // if(item_id > ITEM_BOOTS_UPGRADE) return
            func.bgt_(A0, ITEM_BOOTS_UPGRADE, "return");
        }

        // Get slot for current item
        func.label_("do_process");
        func.multiply_(itemInfoOffset, input, 10);       // item_info_offset = input * 10
        func.set_(AT, ITEM_INFO_TABLE);
        func.addu(AT, AT, itemInfoOffset);
        func.lh(inputItemSlot, AT);                      // input_item_slot = *(ITEM_INFO_TABLE + item_info_offset)

        // Find the item of best tier for this slot currently owned by the player
        func.set_(input, inputItemSlot);
        func.jal(FUN_FIND_OWNED_ITEM_OF_BEST_TIER_FOR_SLOT);
        func.set_(bestItemId, returnValue);              // best_item_id = FindOwnedItemOfBestTierForSlot(input_item_slot)

        func.bgez(bestItemId, "found_best_item");
        // If we don't own any item for this category, tier to find is 0
        func.clr_(tierToFind);
        func.bra_("tier_found");

        // Else, if we have an item, tier to find is owned tier + 1
        func.label_("found_best_item");
        func.multiply_(itemInfoOffset, bestItemId, 10);  // item_info_offset = best_item_id * 10
        func.set_(AT, ITEM_INFO_TABLE);
        func.addu(AT, AT, itemInfoOffset);
        func.lh(tierToFind, AT, 4);                      // tier_to_find = *(ITEM_INFO_TABLE + item_info_offset + 4)
        func.addiu(tierToFind, 1);                       // tier_to_find++

        func.label_("tier_found");

        // Try to find an item with slot == input_item_slot and tier == tier_to_find
        func.set_(AT, ITEM_INFO_TABLE);
        func.set_(currentItemId, 0);

        // while(current_item_id <= 60)
        func.label_("loop");
        func.lh(currentSlot, AT);
        func.bne(currentSlot, inputItemSlot, "next_iter");
        func.lh(currentTier, AT, 4);
        func.bne(currentTier, tierToFind, "next_iter");
        // We found the item we were looking for: change the input values for the calling function
        func.set_(A0, currentItemId);
        func.set_(A1, currentItemId);
        func.bra_("return");

        func.label_("next_iter");
        func.addiu(AT, AT, 10);                          // current_addr += 10
        func.addiu(currentItemId, currentItemId, 1);     // current_item_id++
        func.ble_(currentItemId, 60, "loop");

        // If we reached here, we didn't find any better item: restore the original input values
        func.set_(A0, storageA0);
        func.set_(A1, storageA1);

        func.label_("return");
        func.set_(V0, 0x8012E234L);                      // Code portion removed to inject the call
        func.lw(V0, V0);
        func.set_(RA, storageRA);
        func.jr(RA);                                     // return

        return exe.injectCode(func);
    }

    private static void replaceItemSprite(BinaryFile data, int itemToReplace, int itemSpriteToUse) {
        final long startingAddr = 0x830;

        // Get sprite pointer from source item
        long sourceItemOffsetAddr = startingAddr + ((itemSpriteToUse + 0x1E) * 4L);
        long sourceItemOffset = data.getLongLE(sourceItemOffsetAddr);
        long sourceItemPtrTable = 0x800 + sourceItemOffset;
        long sourceItemSpritePtr = data.getLong(sourceItemPtrTable + 12);

        // Copy sprite pointer on destination item
        long destItemOffsetAddr = startingAddr + ((itemToReplace + 0x1E) * 4L);
        long destItemOffset = data.getLongLE(destItemOffsetAddr);
        long destItemPtrTable = 0x800 + destItemOffset;
        data.setLong(destItemPtrTable + 12, sourceItemSpritePtr);
    }
}
