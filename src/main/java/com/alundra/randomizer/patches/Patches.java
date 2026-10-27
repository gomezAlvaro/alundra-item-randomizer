package com.alundra.randomizer.patches;

import com.alundra.randomizer.RandomizerOptions;
import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and applies the list of patches making the game randomizer-friendly.
 */
public final class Patches {
    private Patches() {
    }

    public static List<GamePatch> buildRandomizerPatches(RandomizerOptions options) {
        return buildRandomizerPatches(options, null);
    }

    /** @param archipelago if not null, the game is built to be played through an Archipelago client (no hints) */
    public static List<GamePatch> buildRandomizerPatches(RandomizerOptions options, PatchArchipelago archipelago) {
        List<GamePatch> patches = new ArrayList<>();
        // Must come first: it expects to be the first one injecting code inside the executable
        if (archipelago != null)
            patches.add(archipelago);

        patches.add(new PatchNewGame(options));
        patches.add(new PatchFixMapInconsistencies());
        patches.add(new PatchFixWrongMapTransitions());
        patches.add(new PatchApplyItemSources());
        patches.add(new PatchNeutralizeMapVariantChanges());
        patches.add(new PatchNeutralizeFlags());
        patches.add(new PatchChangeFlagChecks());
        patches.add(new PatchAllowUsingConsumablesWhileFull());
        patches.add(new PatchRemoveUselessCephas());
        patches.add(new PatchRemoveCutscenes());
        patches.add(new PatchSetKingSnowDeathCount(options.kingSnowDeathCount()));
        patches.add(new PatchRemoveItemsFanfare());
        patches.add(new PatchFixItemNamesInTextboxes());
        if (archipelago == null)
            patches.add(new PatchApplyHintText());
        patches.add(new PatchRemoveNavaInstances());
        patches.add(new PatchRemoveVideos());
        patches.add(new PatchEditCredits());
        patches.add(new PatchApplyShopsText());
        patches.add(new PatchApplyItemsInfo());
        patches.add(new PatchEditGlobalStrings());
        patches.add(new PatchAdaptBlueChest());
        patches.add(new PatchSetCasinoWinCount(options.casinoWinCountForLastReward()));
        patches.add(new PatchRigWheelOfFortune());

        if (options.splitBootsEffects())
            patches.add(new PatchSplitBootsEffects());
        if (options.progressiveItems())
            patches.add(new PatchProgressiveItems(!options.splitBootsEffects()));
        if (options.originalGameBalance())
            patches.add(new PatchOriginalGameBalance());

        return patches;
    }

    /** Applies the patches in three passes: game data, then data files, then executables. */
    public static void execute(List<GamePatch> patches, GameFiles files, GameData gameData, RandomizerWorld world) {
        for (GamePatch patch : patches)
            patch.alterGameData(gameData);
        for (GamePatch patch : patches)
            patch.alterDatasFile(files.datas(), files, gameData, world);
        for (GamePatch patch : patches)
            patch.alterExeFile(files.exe(), files, gameData, world);
    }

    public static void applyRandomizerPatches(GameFiles files, GameData gameData, RandomizerWorld world, RandomizerOptions options) {
        execute(buildRandomizerPatches(options), files, gameData, world);
    }

    public static void applyArchipelagoPatches(GameFiles files, GameData gameData, RandomizerWorld world, RandomizerOptions options,
                                               PatchArchipelago archipelago) {
        execute(buildRandomizerPatches(options, archipelago), files, gameData, world);
    }
}
