package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;

import static com.alundra.randomizer.game.Flags.FLAG_JESS_TALKED_LEAVING_HOUSE_ON_GAME_START;

public class PatchFixMapInconsistencies extends GamePatch {
    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        uniformizeOverworldC4Variants(data);
        splitFlagsBetweenAncientShrineAndMagyscar(data);
    }

    /**
     * Map 407 (Overworld C4 with Merrick's shop open) has two bugged chests having different chest placement
     * & contents compared to map 12 (Overworld C4 with Merrick's shop closed).
     * This patch changes map 407 so it follows map 12 which becomes the standard.
     * Chest contents are not changed here, since the ItemSource handles synchronizing both values.
     */
    private static void uniformizeOverworldC4Variants(BinaryFile data) {
        // Left coast chest
        // Map 12:  01 40 1E 0D 4E 06 00 00 00 00 00 00 D6 06 26
        // Map 407: 01 40 1E 0C 4E 06 00 00 00 00 00 00 40 05 29
        data.setByte(0x584D6BF, 0x0D);
        data.setWord(0x584D6C8, 0xD606);
        data.setByte(0x584D6CA, 0x26);

        // Right chest in the forest
        // Map 12:  01 40 1E 5F 3E 0C 00 00 00 00 00 00 D7 06 25
        // Map 407: 01 40 1E 5E 3E 0C 00 00 00 00 00 00 42 05 26
        data.setByte(0x584D6AB, 0x5F);
        data.setWord(0x584D6B4, 0xD706);
        data.setByte(0x584D6B6, 0x25);
    }

    /**
     * Flag 0x1DD3F8, bit 7 is set under any of the two conditions:
     * - the "Symbols pillar room" in Ancient Shrine is cleared (empties the room when coming back)
     * - a bush not far from Magyscar entrance leading to a room with a Life Vessel (removes the bush when coming back)
     * <p>
     * An unknown mechanism must clear this flag at some point in the vanilla game to be able to re-use it, but this
     * doesn't happen during a randomizer playthrough. This patch changes the flag used in Magyscar to make it different
     * from the one in Ancient Shrine.
     */
    private static void splitFlagsBetweenAncientShrineAndMagyscar(BinaryFile data) {
        // Flag set in Magyscar
        data.setWordLE(0x14E1FAE, FLAG_JESS_TALKED_LEAVING_HOUSE_ON_GAME_START.eventCode());
        // Flag check in Magyscar
        data.setWordLE(0x14E1FA1, FLAG_JESS_TALKED_LEAVING_HOUSE_ON_GAME_START.eventCode());
    }
}
