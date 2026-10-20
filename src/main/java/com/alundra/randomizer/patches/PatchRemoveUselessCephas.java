package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.game.RoomStrings;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;

import static com.alundra.randomizer.game.MapCodes.MAP_OVERWORLD_C2;

/**
 * In vanilla game, there are many different Cephas instances inside the graveyard that appear on different flag
 * conditions. In randomizer, since the game is non-linear, we can have many Cephas appearing at once, causing glitchy
 * dialogues and even sometimes blocking the graveyard entrance.
 * This patch removes all the Cephas instances but one, that gives the hint to his house's basement riddle.
 */
public class PatchRemoveUselessCephas extends GamePatch {
    private static final int[] ALWAYS_BRANCH = {0x31, 0x00, 0x00};

    private static final long[] BASE_VARIANT_ADDRESSES = {
            0x364B44, 0x364B4C, 0x364B54, 0x364B5C, 0x364B64, 0x364B6C,   // Flags C9 to CE
            // Flag D0 (0x364B75) is kept
            0x364B7D, 0x364B85, 0x364B8D, 0x364B95, 0x364B9D, 0x364BA5, 0x364BAD, // Flags D1 to D7
            0x364BCE, 0x364BD6, 0x364BDE, 0x364BE6, 0x364BEE             // Flags D9 to DD
    };

    private static final long[] OTHER_VARIANT_ADDRESSES = {
            0x494EC78, 0x494EC80, 0x494EC88, 0x494EC90, 0x494EC98, 0x494ECA0, // Flags C9 to CE
            // Flag D0 (0x494ECA9) is kept
            0x494ECB1, 0x494ECB9, 0x494ECC1, 0x494ECC9, 0x494ECD1, 0x494ECD9, 0x494ECE1, // Flags D1 to D7
            0x494ED02, 0x494ED0A, 0x494ED12, 0x494ED1A, 0x494ED22         // Flags D9 to DD
    };

    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        for (long addr : BASE_VARIANT_ADDRESSES)
            data.setBytes(addr, ALWAYS_BRANCH);
        for (long addr : OTHER_VARIANT_ADDRESSES)
            data.setBytes(addr, ALWAYS_BRANCH);

        RoomStrings graveyardStrings = new RoomStrings(MAP_OVERWORLD_C2, data);
        graveyardStrings.string(9,
                "\\CIf you go to the basement of my house\\Nthere is a treasure to be obtained.\\A"
                        + "Light the lamps starting with upper\\Nleft, then lower right, bottom left,\\Ncenter bottom, middle top, top right.\\A"
                        + "I'm certain you'll be quite pleased\\Nwith the fruits of your labor\\W2\\N");
        graveyardStrings.string(78, "");
        graveyardStrings.applyOnData(data);
    }
}
