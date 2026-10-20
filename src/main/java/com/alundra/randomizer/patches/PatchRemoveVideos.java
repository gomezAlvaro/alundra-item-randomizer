package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;
import com.alundra.randomizer.tools.PsxExeFile;

/**
 * Prevents the following videos from being loaded and played:
 * <ul>
 *     <li>intro (logos + US opening)</li>
 *     <li>intro after waiting on title screen (JP opening)</li>
 *     <li>game ending anime</li>
 * </ul>
 * The video files themselves are left untouched inside the disc image.
 */
public class PatchRemoveVideos extends GamePatch {
    @Override
    public void alterExeFile(PsxExeFile exe, GameFiles files, GameData gameData, RandomizerWorld world) {
        removeIntroVideos(files.file(GameFiles.BOOT_EXE));
        removeEndingVideo(files.file(GameFiles.END_EXE));
    }

    private static void removeIntroVideos(BinaryFile bootExe) {
        // 5DDC - 5E08 filled with nops --> No opening videos played
        for (long addr = 0x5DDC; addr < 0x5E08; addr += 0x4)
            bootExe.setLong(addr, 0x0);

        // 5A90 - 5AC0 filled with nops --> No opening videos loaded
        for (long addr = 0x5A90; addr < 0x5AC0; addr += 0x4)
            bootExe.setLong(addr, 0x0);
    }

    private static void removeEndingVideo(BinaryFile endExe) {
        // 1B0C - 1B24 filled with nops --> No ending video played
        for (long addr = 0x1B0C; addr < 0x1B24; addr += 0x4)
            endExe.setLong(addr, 0x0);

        // 1AC4 - 1AD8 filled with nops --> No ending video loaded
        for (long addr = 0x1AC4; addr < 0x1AD8; addr += 0x4)
            endExe.setLong(addr, 0x0);
    }
}
