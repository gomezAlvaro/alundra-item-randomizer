package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.game.Item;
import com.alundra.randomizer.game.RoomStrings;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;

import static com.alundra.randomizer.game.MapCodes.MAP_258;
import static com.alundra.randomizer.game.MapCodes.MAP_398;
import static com.alundra.randomizer.tools.TextboxFormatter.format;

/**
 * Updates shopkeepers' dialogues to name the randomized items and their prices.
 */
public class PatchApplyShopsText extends GamePatch {
    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        applyNaomiShopText(data, world);
        applyLurvyShopText(data, world);

        freezeMerrickItems(data);
        skipLurvyShopEntranceCutscene(data);
    }

    private static Item item(RandomizerWorld world, String sourceName) {
        return world.itemSource(sourceName).item();
    }

    private static void applyLurvyShopText(BinaryFile data, RandomizerWorld world) {
        Item lurvy1 = item(world, "Lurvy's Shop: Item #1 (Herbs)");
        Item lurvy2 = item(world, "Lurvy's Shop: Item #2 (Strength Tonic)");
        Item lurvy3 = item(world, "Lurvy's Shop: Item #3 (Strength Elixyr)");
        Item lurvy4 = item(world, "Lurvy's Shop: Item #4 (Magic Elixyr)");
        Item lurvy51 = item(world, "Lurvy's Shop: Item #5 (Leather Armor)");
        Item lurvy52 = item(world, "Lurvy's Shop: Item #5 replacement when bought once (Life Vessel)");
        Item lurvy53 = item(world, "Lurvy's Shop: Item #5 replacement when bought twice (Wonder Essence)");

        RoomStrings strings = new RoomStrings(MAP_398, data);

        strings.string(7, format("\\CThat there " + lurvy1.name() + "'ll run you " + lurvy1.goldValue() + " Gilder. Okay? \\0100\\Y"));
        strings.string(8, format("\\C" + lurvy2.name() + "'ll run ya " + lurvy2.goldValue() + " Gilder these days. Soun' like a deal?\\0100\\Y"));
        strings.string(34, format("\\C" + lurvy3.name() + " costs " + lurvy3.goldValue() + " Gilder 'round here.\\0100\\Y"));
        strings.string(9, format("\\C" + lurvy4.name() + " costs " + lurvy4.goldValue() + " Gilder. Ya still in the market for one?\\0100\\Y"));
        strings.string(17, format("\\CThat there's a " + lurvy51.name() + ". It's a steal of a deal, at only " + lurvy51.goldValue() + " Gilder. Deal?\\0100\\Y"));
        strings.string(18, format("\\CI don't need that " + lurvy52.name() + ". " + lurvy52.goldValue() + " Gilder'n the thing's yurs. Want it?\\0100\\Y"));
        strings.string(19, format("\\C" + lurvy53.name() + ", hm? How 'bout " + lurvy53.goldValue() + " Gilder?\\0100\\Y"));

        strings.string(35, "\\BReceived " + lurvy51.name() + ".");
        strings.string(36, "\\BReceived " + lurvy52.name() + ".");

        strings.applyOnData(data);
    }

    private static void applyNaomiShopText(BinaryFile data, RandomizerWorld world) {
        Item naomi1 = item(world, "Naomi's Shop: Item #1 (Herbs)");
        Item naomi2 = item(world, "Naomi's Shop: Item #2 (Strength Tonic)");
        Item naomi3 = item(world, "Naomi's Shop: Item #3 (Magic Elixyr)");
        Item naomi4 = item(world, "Naomi's Shop: Item #4 (Life Vessel)");

        RoomStrings strings = new RoomStrings(MAP_258, data);

        strings.string(1, format("\\DIf you'd like to purchase " + naomi1.name() + ", the cost will be "
                + naomi1.goldValue() + " Gilder. Is that satisfactory? \\0100\\Y"));
        strings.string(2, format("\\DAh, yes! " + naomi2.name() + "! The prime choice of adventurers such as yourself! It costs, "
                + naomi2.goldValue() + " Gilder, ok? \\0100\\Y"));
        strings.string(3, format("\\D" + naomi3.name() + "! For just " + naomi3.goldValue()
                + " Gilder, it can be put to work to make all your dreams come true! Want it? \\0100\\Y"));
        strings.string(4, format("\\D" + naomi4.name() + "! "
                + "Hmm, pricey, but a prime choice for the especially discriminating. "
                + "It's " + naomi4.goldValue() + " Gilder, ok? \\0100\\Y"));

        strings.string(57, "\\BReceived a " + naomi4.name() + ".");

        strings.applyOnData(data);
    }

    /**
     * Prevents Merrick items from being taken and thrown to remove the need for any text change and remove any
     * crash-prone interaction with the "open chest" slot.
     */
    private static void freezeMerrickItems(BinaryFile data) {
        data.setLong(0x1EBF9D2, 0x00000000);
        data.setByte(0x1EBF9E8, 0x00);
        data.setByte(0x1EBF9FC, 0x00);
        data.setByte(0x1EBFA10, 0x00);
    }

    private static void skipLurvyShopEntranceCutscene(BinaryFile data) {
        // Remove camera pan
        data.setBytes(0x56BFC9A, 0x02, 0x08, 0x00);
        // Remove waiting condition for camera pan & text
        data.setBytes(0x56BFCB9, 0x02, 0x0A, 0x00);
        // Remove Alundra walking up inside the shop
        data.setBytes(0x56BFCC7, 0x02, 0x09, 0x00);
        // Remove Alundra walking right inside the shop
        data.setBytes(0x56BFCD0, 0x02, 0x04, 0x00);
    }
}
