package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.MipsCode;
import com.alundra.randomizer.tools.PsxExeFile;

import static com.alundra.randomizer.tools.MipsRegister.V0;

/**
 * Makes each pair of boots only give its own effect, instead of higher tiers including all lower tiers effects.
 */
public class PatchSplitBootsEffects extends GamePatch {
    @Override
    public void alterExeFile(PsxExeFile exe, GameFiles files, GameData gameData, RandomizerWorld world) {
        // Change the check for Charm Boots
        exe.setCode(0x128E8, new MipsCode().lui(V0, 0x801E).lw(V0, V0, 0xD8A0, false));
        exe.setByte(0x128F4, 0x1);

        // Change the jump check for Long Boots
        exe.setCode(0x1790C, new MipsCode().lui(V0, 0x801E).lw(V0, V0, 0xD898, false));

        // Change the speed check for Long Boots
        exe.setCode(0x17A68, new MipsCode().lui(V0, 0x801E).lw(V0, V0, 0xD898, false));

        // Change the check for Merman Boots
        exe.setCode(0x185C4, new MipsCode().lui(V0, 0x801E).lw(V0, V0, 0xD89C, false));
        exe.setByte(0x185D4, 0x1);
    }
}
