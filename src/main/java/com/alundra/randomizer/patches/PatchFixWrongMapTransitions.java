package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;

import static com.alundra.randomizer.game.MapCodes.MAP_INOA_RANDOMIZER;

public class PatchFixWrongMapTransitions extends GamePatch {
    private static final int[] NEVER_BRANCH = {0x30, 0x00, 0x00, 0x00, 0x05};

    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        // Leaving the inn map uses a forced transition to the wrong variant of Inoa, replace the map ID in the events
        data.setBytes(0x3A6729E, NEVER_BRANCH);
        data.setWordLE(0x3A672A4, MAP_INOA_RANDOMIZER);

        // There is an event in Lutas's house second floor that overwrites the map variant for the ground floor,
        // in order to ensure the stairs will bring us to the right map. The problem is, this relies on story flags,
        // so we just force the variant to be the one that is good for us.
        data.setBytes(0x4996860, NEVER_BRANCH);
        data.setWordLE(0x4996868, 0x00F5);
    }
}
