package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;
import com.alundra.randomizer.tools.ByteArray;

import static com.alundra.randomizer.game.Flags.*;
import static com.alundra.randomizer.game.MapCodes.MAP_JESS_HOUSE_RANDOMIZER;
import static com.alundra.randomizer.game.MapCodes.MAP_OVERWORLD_E1;

public class PatchRemoveCutscenes extends GamePatch {
    @Override
    public void alterGameData(GameData gameData) {
        // This skips the need to talk twice to the master sage, given that he pretty much says the same thing
        // when you talk again to validate...
        gameData.addStartingFlag(FLAG_LARS_CRYPT_TALKED_WITH_MASTER_SAGE_ONCE);
    }

    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        removeCutsceneAfterDarkDragonFight(data);
        removePostCoalMineCutscenes(data);
        removeMagyscarEntranceCutscene(data);
        removeLarsCrypt5SagesCutscene(data);
        removePostWildaNightCutscene(data);
        removePostNirudeCutscene(data);
    }

    private static void removeCutsceneAfterDarkDragonFight(BinaryFile data) {
        // Remove cutscene between Dark Dragon and Melzas 1
        final long warpToSanctuaryCutsceneAddr = 0x47C9E15;
        final long warpToMelzas1Addr = 0x67C51C7;
        ByteArray warpToMelzas1Instr = data.getBytes(warpToMelzas1Addr, warpToMelzas1Addr + 8);
        data.setBytes(warpToSanctuaryCutsceneAddr, warpToMelzas1Instr);

        // Get rid of the HUD removal on this map change
        data.setBytes(0x47C9E12, 0x02, 0x03, 0x00); // Always branch 3 bytes forward
    }

    private static void removePostCoalMineCutscenes(BinaryFile data) {
        // Remove "Murggs running in woods" cutscene in Overworld A2
        data.setBytes(0x13F339, 0x02, 0x0D, 0x00); // Always branch 0xD bytes forward

        // Remove the watchtower cutscene in Overworld A1
        data.setBytes(0x105DB4, 0x02, 0x7C, 0x00); // Always branch 0x7C bytes forward

        // Remove flag set on meatballs fall so they disappear on map exit + re-enter.
        // This prevents Coal Mine entrance from being blocked, and back exit from being a softlock source if triggered
        // before doing Coal Mine
        data.setBytes(0x245A04, 0x02, 0x03, 0x00);

        // Big rocks disappearing initially requires 2 flags: meatballs fall flag & watchtower cutscene flag.
        // Remove the meatballs flag requirement for big rocks disappearance by shortening the branch, so the second
        // branch condition is not ignored anymore
        data.setByte(0x245A0B, 0x47);

        // Change the watchtower requirement to a "beat Coal Mine boss" requirement for big rocks disappearance
        data.setWordLE(0x245A50, FLAG_COAL_MINE_BOSS_BEATEN.eventCode());

        // Change the watchtower requirement to a "beat Coal Mine boss" requirement for villagers cutscene to trigger
        // in Overworld B2
        data.setWordLE(0x2458A1, FLAG_COAL_MINE_BOSS_BEATEN.eventCode());
    }

    /** Sometimes, a cutscene with villagers praying is triggered at Magyscar entrance. This removes that cutscene. */
    private static void removeMagyscarEntranceCutscene(BinaryFile data) {
        // Replace "branch if flag triggering cutscene not set" by "always branch" so the cutscene never triggers
        data.setBytes(0x2122C20, 0x02, 0x17, 0x00);
        data.setBytes(0x2122C38, 0x02, 0x2F, 0x00);
    }

    private static void removeLarsCrypt5SagesCutscene(BinaryFile data) {
        // Open the door when master sage has been talked to, instead of after seeing the full cutscene.
        // This removes the cutscene where all the sages appear one by one.
        data.setWordLE(0x63AC96, FLAG_LARS_CRYPT_SAGES_CUTSCENE_ACTIVE.eventCode());
    }

    /**
     * In the vanilla game, defeating Wilda teleports you to a nighttime variant of Inoa where Bergus got kidnapped.
     * We need the bossfight to teleport us to our regular bedroom instead.
     */
    private static void removePostWildaNightCutscene(BinaryFile data) {
        // Change the warp destination map
        data.setWordLE(0x4817A36, MAP_JESS_HOUSE_RANDOMIZER);

        // Unfreeze Alundra so he doesn't get stuck in cutscene mode (since he is meant to be unfrozen by another
        // cutscene in nighttime bedroom)
        data.setByte(0x4817A02, 0x11);
    }

    private static void removePostNirudeCutscene(BinaryFile data) {
        ByteArray eventBytes = new ByteArray();

        eventBytes.addByte(0x38);                  // Set map variant
        eventBytes.addWordLE(MAP_OVERWORLD_E1);    // for Nirude exterior
        eventBytes.addWordLE(MAP_OVERWORLD_E1);    // to basic (broken) Nirude exterior instead of "dungeon in progress" variant

        eventBytes.addByte(0x53);                  // Warp
        eventBytes.addWordLE(MAP_OVERWORLD_E1);    // to Nirude exterior
        eventBytes.addBytes(0x19, 0x28, 0x00);     // at coords 19, 28, 0

        // Trigger this event sequence on boss death
        data.setBytes(0x47E7B57, eventBytes);
    }
}
