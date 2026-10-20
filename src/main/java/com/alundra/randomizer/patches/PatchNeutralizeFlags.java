package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;
import com.alundra.randomizer.tools.MipsCode;
import com.alundra.randomizer.tools.PsxExeFile;

import static com.alundra.randomizer.tools.MipsRegister.RA;
import static com.alundra.randomizer.tools.MipsRegister.V0;

public class PatchNeutralizeFlags extends GamePatch {
    private static final int INSTRUCTION_CODE_SKIP_FLAG_SET = 0xC6;

    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        // Remove the flag set when entering Lars Crypt that prevents from re-entering
        data.setByte(0x3654C4, INSTRUCTION_CODE_SKIP_FLAG_SET);
        data.setByte(0x494F5F8, INSTRUCTION_CODE_SKIP_FLAG_SET);

        // Remove Alundra being immobilized when seeing Magyscar cutscene to prevent a softlock
        data.setByte(0x1683D14, 0x11);
    }

    @Override
    public void alterExeFile(PsxExeFile exe, GameFiles files, GameData gameData, RandomizerWorld world) {
        // Add a "do nothing" event instruction (with code 0xC6) to replace inconvenient instructions
        MipsCode func = new MipsCode();
        func.jr(RA, false);
        func.set_(V0, 0x3);
        long funcAddr = exe.injectCode(func);
        exe.setLongLE(0x7C32C, funcAddr);
    }
}
