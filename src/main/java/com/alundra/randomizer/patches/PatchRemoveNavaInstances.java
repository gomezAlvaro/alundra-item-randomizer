package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;

/**
 * In vanilla game, there are many different Nava instances inside his house, blocking the way to Nava's Keep.
 * In randomizer, we want to be able to access this dungeon at any time, so we just remove him from his cabin.
 */
public class PatchRemoveNavaInstances extends GamePatch {
    private static final int[] ALWAYS_BRANCH = {0x31, 0x00, 0x00};

    private static final long[] ADDRESSES = {
            0x20EA7E1, // Flag CA
            0x20EA7EF, // Flag CB
            0x20EA7FD, // Flag CC
            0x20EA80B, // Flag CD
            0x20EA819, // Flag CE
            0x20EA827, // Flag CF
            0x20EA835, // Flag D0
            0x20EA843, // Flag D1
            0x20EA851, // Flag D2
            0x20EA85F, // Flag D3
            0x20EA86D, // Flag D4
            0x20EA87B, // Flag D5
            0x20EA889, // Flag D6
            0x20EA897, // Flag D7
            0x20EA8A5, // Flag D8
            0x20EA8B3, // Flag D9
            0x20EA8C1, // Flag DA
            0x20EA8CF, // Flag DB
            0x20EA8DD, // Flag DC
            0x20EA8EB, // Flag DD
            0x20EA8F9, // Flag DE
            0x20EA8FF, // Flag DF
            0x20EA90D, // Flag E0
    };

    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        for (long addr : ADDRESSES)
            data.setBytes(addr, ALWAYS_BRANCH);
    }
}
