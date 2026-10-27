package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.MipsCode;
import com.alundra.randomizer.tools.PsxExeFile;

/**
 * Makes every spin of the casino's 'Wheel of Fortune' a win, so its two rewards never depend on luck.
 * <p>
 * When the wheel stops, the game checks the color of the slot against the color the player picked and sets local
 * flag 0x8008 ("won", bit 8 of 0x801E6378) or clears it. The room script then reads that flag to pay out or say
 * "You lost". Removing the branch that clears it means the flag is always set. The wheel still shows where it
 * really stopped: only the outcome changes.
 */
public class PatchRigWheelOfFortune extends GamePatch {
    /** 0x80066ED4 in RAM: "beqz $t2, lost" right after the color check */
    private static final long WIN_CHECK_BRANCH = 0x476D4;

    @Override
    public void alterExeFile(PsxExeFile exe, GameFiles files, GameData gameData, RandomizerWorld world) {
        exe.setCode(WIN_CHECK_BRANCH, new MipsCode().nop());
    }
}
