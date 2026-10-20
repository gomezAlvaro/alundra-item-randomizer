package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;
import com.alundra.randomizer.tools.RandomizerException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Replaces BALANCE.BIN with a version using the japanese game balance (enemy HP and damage) instead of the
 * harder balance edited by Working Designs for the US release.
 */
public class PatchOriginalGameBalance extends GamePatch {
    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        try (InputStream stream = PatchOriginalGameBalance.class.getResourceAsStream("/assets/patched_balance.bin")) {
            if (stream == null)
                throw new RandomizerException("Missing resource 'patched_balance.bin'");
            files.replace(GameFiles.BALANCE_BIN, stream.readAllBytes());
        } catch (IOException e) {
            throw new RandomizerException("Could not read resource 'patched_balance.bin'", e);
        }
    }
}
