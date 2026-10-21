package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;

public class PatchChangeFlagChecks extends GamePatch {
    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        // Change the condition to open Riverside Bar
        data.setWordLE(0x29B3A6, 0x067E);
        data.setWordLE(0x29AF35, 0x067E);
        data.setWordLE(0x29B5E5, 0x067E);

        // Make the bouquet the condition to be able to open Lars Crypt
        data.setWordLE(0x364BFE, 0x3120);
        data.setWordLE(0x494ED32, 0x3120);

        // Remove Nirude having to be broken for Torla geysers to work
        data.setWordLE(0x43F774, 0x0005);
        data.setWordLE(0x43F7DB, 0x0005);
        data.setWordLE(0x43F83F, 0x0005);
        data.setWordLE(0x43F8A3, 0x0005);
        data.setWordLE(0x43F907, 0x0005);

        // Make the Sluice Key door check that Sluice Key is owned instead of checking that the chest containing
        // Sluice Key in vanilla game was opened
        data.setWordLE(0x567029A, 0x3100);

        // Skip Septimus spawning on Overworld C2 once Magyscar is done (he blocks the Sanctuary stairs along with Ronan).
        // Vanilla is "if the post-Magyscar story flag is off, skip this spawn" (opcode 0x31): replacing it with an
        // unconditional goto (0x02) always skips it. Maps 331 and its Lars Crypt variant run the same script.
        data.setBytes(0x364D4E, 0x02, 0x8B, 0x00);
        data.setBytes(0x3652AE, 0x02, 0x2E, 0x00);
        data.setBytes(0x494EE82, 0x02, 0x8B, 0x00);
        data.setBytes(0x494F3E2, 0x02, 0x2E, 0x00);
    }
}
