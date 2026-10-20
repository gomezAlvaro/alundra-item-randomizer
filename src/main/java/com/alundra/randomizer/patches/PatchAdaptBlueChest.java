package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;
import com.alundra.randomizer.tools.ByteArray;

import static com.alundra.randomizer.game.MapCodes.MAP_JESS_HOUSE_RANDOMIZER;

/**
 * The blue chest in Jess's house is heavily scripted and cannot be used as-is for randomization.
 * This patch adapts the chest to make it actually usable in the randomizer.
 */
public class PatchAdaptBlueChest extends GamePatch {
    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        // Change opening conditions to having the Small Key, instead of requiring Jess's death
        data.setWordLE(0x3AEA9C3, 0x3140);
        data.setWordLE(0x3AEAB71, 0x3140);

        // Remove music fadeout and music change when opening the chest
        data.setBytes(0x3AEAB86, 0x10, 0x10);       // Immobilize Alundra twice (no effect)
        data.setBytes(0x3AEAB92, 0x02, 0x03, 0x00); // Jump 3 bytes forward

        // Remove Jess Diary contents reading
        data.setBytes(0x3AEABB1, 0x02, 0x03, 0x00); // Jump 3 bytes forward

        // Remove Broken Armor announcement, and replace it by a map variant change for Jess house to a variant with
        // the chest opened
        data.setBytes(0x3AEABB7, 0x38, 0xF2, 0x00, 0xF2, 0x00);

        // Get rid of HUD removal on warp
        data.setBytes(0x3AEABEF, 0x02, 0x03, 0x00); // Jump 3 bytes forward

        ByteArray warpBytes = new ByteArray();
        warpBytes.addByte(0x11); // Stop immobilizing Alundra
        // Warp to the same place to reinit the map with a closed chest
        warpBytes.addByte(0x53);
        warpBytes.addWordLE(MAP_JESS_HOUSE_RANDOMIZER);
        warpBytes.addBytes(0x07, 0x08, 0x01);
        data.setBytes(0x3AEABF9, warpBytes);

        // Remove the chest in the map variant where the chest has been opened
        data.setWord(0x36F3907, 0xFFFF);
        // Remove the map variant change at Inoa
        data.setBytes(0x3AEABF4, 0x02, 0x05, 0x00);
    }
}
