package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.game.RoomStrings;
import com.alundra.randomizer.model.ItemSource;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;

import static com.alundra.randomizer.game.MapCodes.*;
import static com.alundra.randomizer.tools.StringTools.replaceFirst;

/**
 * The game hardcodes lots of text, meaning that some item changes would not be reflected inside textboxes when
 * given by NPCs. This patch changes the incriminated strings to make everything look consistent.
 */
public class PatchFixItemNamesInTextboxes extends GamePatch {
    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        fixKingSnowItem(data, world);
        fixPostNirudeItems(data, world);
        fixCasinoItems(data, world);
        fixPostNavaItem(data, world);
        fixLurvyRepairingBrokenArmor(data, world);
        fixJessBlueChestItems(data, world);
    }

    private static String itemName(RandomizerWorld world, String sourceName) {
        return world.itemSource(sourceName).item().name();
    }

    private static void fixKingSnowItem(BinaryFile data, RandomizerWorld world) {
        String item = itemName(world, "Overworld B3: King Snow reward for dying a specified amount of times (Legend Sword)");

        RoomStrings strings = new RoomStrings(MAP_OVERWORLD_B3, data);
        strings.string(0, replaceFirst(strings.string(0), "Legendary\\NSword", item));
        strings.string(3, replaceFirst(strings.string(3), "the Legendary Sword!", item));
        strings.applyOnData(data);
    }

    private static void fixPostNirudeItems(BinaryFile data, RandomizerWorld world) {
        String topazCrestItem = itemName(world, "Nirude's Lair: Boss reward chest (Topaz Crest)");
        String aquaCapeItem = itemName(world, "Nirude's Lair: Boss reward item given by Miming (Aqua Cape)");

        // Fix Nirude exterior rewards after Nirude's Lair, on both map variants
        for (int mapId : new int[]{MAP_OVERWORLD_E1, MAP_OVERWORLD_E1_VARIANT}) {
            RoomStrings strings = new RoomStrings(mapId, data);
            strings.string(34, replaceFirst(strings.string(34), "the Topaz Crest", topazCrestItem));
            strings.string(36, replaceFirst(strings.string(36), "the Aqua Cape", aquaCapeItem));
            strings.applyOnData(data);
        }
    }

    private static void fixCasinoItems(BinaryFile data, RandomizerWorld world) {
        String roulette1 = itemName(world, "Riverside Casino: 'Wheel of Fortune' chest (Life Vessel)");
        String roulette2 = itemName(world, "Riverside Casino: 'Wheel of Fortune' second reward (Gilded Falcon)");
        String shooting1 = itemName(world, "Riverside Casino: 'Hit Your Mark' chest (Life Vessel)");
        String shooting2 = itemName(world, "Riverside Casino: 'Hit Your Mark' second reward (Gilded Falcon)");
        String arena1 = itemName(world, "Riverside Casino: 'Slime Smash' chest (Life Vessel)");
        String arena2 = itemName(world, "Riverside Casino: 'Slime Smash' second reward (Gilded Falcon)");

        RoomStrings rouletteStrings = new RoomStrings(MAP_CASINO_ROULETTE, data);
        rouletteStrings.string(44, replaceFirst(rouletteStrings.string(44), "Life Vessel", roulette1));
        rouletteStrings.string(24, replaceFirst(rouletteStrings.string(24), "a Gilded Falcon", roulette2));
        rouletteStrings.applyOnData(data);

        RoomStrings shootStrings = new RoomStrings(MAP_CASINO_SHOOTING_RANGE, data);
        shootStrings.string(20, replaceFirst(shootStrings.string(20), "Life Vessel", shooting1));
        shootStrings.string(24, replaceFirst(shootStrings.string(24), "a Gilded Falcon", shooting2));
        shootStrings.applyOnData(data);

        RoomStrings arenaStrings = new RoomStrings(MAP_CASINO_SLIME_ARENA, data);
        arenaStrings.string(80, replaceFirst(arenaStrings.string(80), "Life Vessel", arena1));
        arenaStrings.string(81, replaceFirst(arenaStrings.string(81), "fantastic Gilded\\NFalcon!", arena2 + "!\\N"));
        arenaStrings.string(24, replaceFirst(arenaStrings.string(24), "a Gilded Falcon", arena2));
        arenaStrings.applyOnData(data);

        RoomStrings barStrings = new RoomStrings(MAP_RIVERSIDE_BAR, data);
        barStrings.string(22, "\\C'Slime Smash' rewards:\\N- " + arena1 + "\\N- " + arena2);
        barStrings.string(23, "\\C'Hit Your Mark' rewards:\\N- " + shooting1 + "\\N- " + shooting2);
        barStrings.string(24, "\\C'Wheel of Fortune' rewards:\\N- " + roulette1 + "\\N- " + roulette2);
        barStrings.applyOnData(data);
    }

    private static void fixPostNavaItem(BinaryFile data, RandomizerWorld world) {
        String item = itemName(world, "Nava's Keep: Falling item revealed using Zolist's Stone (Diamond Crest)");

        RoomStrings strings = new RoomStrings(MAP_432, data);
        strings.string(70, replaceFirst(strings.string(70), "the Diamond Crest", item));
        strings.applyOnData(data);
    }

    private static void fixLurvyRepairingBrokenArmor(BinaryFile data, RandomizerWorld world) {
        ItemSource lurvyRepair = world.itemSource("Lurvy's Shop: Item obtained after repairing Broken Armor (Ancient Armor)");

        RoomStrings strings = new RoomStrings(MAP_398, data);
        strings.string(31, "\\BReceived " + lurvyRepair.item().name() + ".");
        strings.applyOnData(data);
    }

    private static void fixJessBlueChestItems(BinaryFile data, RandomizerWorld world) {
        String item1 = itemName(world, "Inoa: First item in Jess's blue chest (Jess's Letter)");
        String item2 = itemName(world, "Inoa: Second item in Jess's blue chest (Broken Armor)");

        RoomStrings strings = new RoomStrings(MAP_JESS_HOUSE_CHEST_OPENABLE, data);
        strings.string(13, "\\BReceived " + item1 + ".");
        strings.string(15, "\\BReceived " + item2 + ".");
        strings.applyOnData(data);
    }
}
