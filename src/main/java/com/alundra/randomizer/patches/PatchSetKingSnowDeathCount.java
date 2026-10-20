package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;

/**
 * Changes the amount of deaths required to get King Snow's item (default is 20).
 */
public class PatchSetKingSnowDeathCount extends GamePatch {
    private final int targetDeathCount;

    public PatchSetKingSnowDeathCount(int targetDeathCount) {
        this.targetDeathCount = targetDeathCount;
    }

    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        data.setByte(0x29B4BD, targetDeathCount);
    }
}
