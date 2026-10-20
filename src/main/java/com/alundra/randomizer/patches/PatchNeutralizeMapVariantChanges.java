package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;
import com.alundra.randomizer.tools.MipsCode;
import com.alundra.randomizer.tools.PsxExeFile;

import static com.alundra.randomizer.tools.MipsRegister.RA;
import static com.alundra.randomizer.tools.MipsRegister.V0;

/**
 * Prevents story events from changing Inoa's map variant, which would break the randomizer's version of the village.
 */
public class PatchNeutralizeMapVariantChanges extends GamePatch {
    private static final int INSTRUCTION_CODE_SKIP_MAP_VARIANT_SET = 0xC5;

    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        // Murgg watchtower cutscene
        data.setByte(0x105E29, INSTRUCTION_CODE_SKIP_MAP_VARIANT_SET);
        // Beating Ancient Shrine boss
        data.setByte(0x82E6DC, INSTRUCTION_CODE_SKIP_MAP_VARIANT_SET);
        // Clearing Coastal Cave
        data.setByte(0x127B08A, INSTRUCTION_CODE_SKIP_MAP_VARIANT_SET);
        // Clearing Tower of the Great Tree
        data.setByte(0x22EAD1F, INSTRUCTION_CODE_SKIP_MAP_VARIANT_SET);
        // Finishing the Crypt
        data.setByte(0x72E55F, INSTRUCTION_CODE_SKIP_MAP_VARIANT_SET);
        // Finishing Reptile Lair
        data.setByte(0x1442734, INSTRUCTION_CODE_SKIP_MAP_VARIANT_SET);
        data.setByte(0x5931734, INSTRUCTION_CODE_SKIP_MAP_VARIANT_SET);
        // Finishing Nirude Lair
        data.setByte(0x5478DA, INSTRUCTION_CODE_SKIP_MAP_VARIANT_SET);
        data.setByte(0x5F3C8E6, INSTRUCTION_CODE_SKIP_MAP_VARIANT_SET);
        // Finishing Torla Mountain
        data.setByte(0x4817A26, INSTRUCTION_CODE_SKIP_MAP_VARIANT_SET);
    }

    @Override
    public void alterExeFile(PsxExeFile exe, GameFiles files, GameData gameData, RandomizerWorld world) {
        // Add a "do nothing" event instruction (with code 0xC5) to replace inconvenient instructions
        MipsCode func = new MipsCode();
        func.jr(RA, false);
        func.set_(V0, 0x5);
        long funcAddr = exe.injectCode(func);
        exe.setLongLE(0x7C328, funcAddr);
    }
}
