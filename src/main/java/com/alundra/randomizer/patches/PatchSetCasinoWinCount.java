package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.Flag;
import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;

import static com.alundra.randomizer.game.Flags.*;

/**
 * Changes the number of wins required on each casino minigame to obtain the second reward (vanilla is 4).
 */
public class PatchSetCasinoWinCount extends GamePatch {
    private final int winCountForSecondReward;

    public PatchSetCasinoWinCount(int winCount) {
        this.winCountForSecondReward = winCount;
    }

    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        Flag slime;
        Flag shooting;
        Flag roulette;
        switch (winCountForSecondReward) {
            case 1 -> { slime = FLAG_CASINO_SLIME_1_WIN; shooting = FLAG_CASINO_SHOOTING_RANGE_1_WIN; roulette = FLAG_CASINO_ROULETTE_1_WIN; }
            case 2 -> { slime = FLAG_CASINO_SLIME_2_WINS; shooting = FLAG_CASINO_SHOOTING_RANGE_2_WINS; roulette = FLAG_CASINO_ROULETTE_2_WINS; }
            case 3 -> { slime = FLAG_CASINO_SLIME_3_WINS; shooting = FLAG_CASINO_SHOOTING_RANGE_3_WINS; roulette = FLAG_CASINO_ROULETTE_3_WINS; }
            default -> { return; } // 4 wins is the vanilla behavior
        }

        data.setWordLE(0x65978E8, slime.eventCode());
        data.setWordLE(0x65C7A6E, shooting.eventCode());
        data.setWordLE(0x65FD0B0, roulette.eventCode());
    }
}
