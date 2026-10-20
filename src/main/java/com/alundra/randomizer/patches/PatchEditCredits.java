package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;
import com.alundra.randomizer.tools.PsxExeFile;

import java.nio.charset.StandardCharsets;

/**
 * Adds a "RANDOMIZER" line crediting the randomizer's original author inside the ending credits.
 */
public class PatchEditCredits extends GamePatch {
    @Override
    public void alterExeFile(PsxExeFile exe, GameFiles files, GameData gameData, RandomizerWorld world) {
        BinaryFile creditsExe = files.file(GameFiles.CLOSING_EXE);

        for (long addr = 0xBB0; addr < 0xBFA; ++addr)
            creditsExe.setByte(addr, 0x0);
        for (long addr = 0x1044C; addr < 0x10474; ++addr)
            creditsExe.setByte(addr, 0x0);

        creditsExe.setBytes(0xD04, "RANDOMIZER ".getBytes(StandardCharsets.US_ASCII));
        creditsExe.setLongLE(0x10440, 0xD04 + 0x8001F800L);

        creditsExe.setBytes(0xBB0, "Dinopony".getBytes(StandardCharsets.US_ASCII));
        creditsExe.setLongLE(0x10448, 0xBB0 + 0x8001F800L);
    }
}
