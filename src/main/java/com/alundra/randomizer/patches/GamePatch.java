package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;
import com.alundra.randomizer.tools.PsxExeFile;

/**
 * A modification of the game. Patches are applied in three passes: first {@link #alterGameData} is called on every
 * patch, then {@link #alterDatasFile}, then {@link #alterExeFile}.
 */
public abstract class GamePatch {
    /** Alters game data (starting flags...) before anything is written. */
    public void alterGameData(GameData gameData) {
    }

    /** Alters DATAS.BIN (maps, events, texts) or other data files. */
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
    }

    /** Alters the main executable (ALUN_CD.EXE) or other executables. */
    public void alterExeFile(PsxExeFile exe, GameFiles files, GameData gameData, RandomizerWorld world) {
    }
}
