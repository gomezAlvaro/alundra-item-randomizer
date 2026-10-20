package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.MipsCode;
import com.alundra.randomizer.tools.PsxExeFile;

import static com.alundra.randomizer.tools.MipsRegister.V0;

/**
 * In the vanilla game, having max HP / MP prevents from using consumables meant to refill the corresponding
 * resource. This patch removes this condition, to prevent softlocks where you are forced to open a chest in order to
 * proceed, but are stuck because consumables cannot be used.
 */
public class PatchAllowUsingConsumablesWhileFull extends GamePatch {
    @Override
    public void alterExeFile(PsxExeFile exe, GameFiles files, GameData gameData, RandomizerWorld world) {
        // Herbs
        exe.setCode(0x15658, new MipsCode().nop());
        // Strength Tonic
        exe.setCode(0x15844, new MipsCode().nop());
        // Strength Elixyr
        exe.setCode(0x156C8, new MipsCode().nop());
        // Magic Elixyr
        exe.setCode(0x1573C, new MipsCode().nop());
        // Wonder Essence
        exe.setCode(0x157B0, new MipsCode().set_(V0, 0x1));
    }
}
