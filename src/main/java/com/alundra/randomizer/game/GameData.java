package com.alundra.randomizer.game;

import com.alundra.randomizer.RandomizerOptions;
import com.alundra.randomizer.tools.Json;
import com.alundra.randomizer.tools.MersenneTwister;
import com.alundra.randomizer.tools.RandomizerException;
import com.alundra.randomizer.tools.VecTools;
import com.alundra.randomizer.tools.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.alundra.randomizer.game.Flags.*;
import static com.alundra.randomizer.game.ItemCodes.*;

/**
 * Game-related data that is not part of the randomization logic: item definitions, starting flags...
 */
public class GameData {
    /** The various items that can be found in the game, indexed by ID */
    private final Item[] items = new Item[ITEM_COUNT];

    /** A list of starting flags that are activated on New Game */
    private final List<Flag> startingFlags = new ArrayList<>();

    /** A list containing the item IDs of the crests that will have to be found to complete the game */
    private final List<Integer> usedCrests = new ArrayList<>();

    public GameData() {
        initItems();
        initStartingFlags();
    }

    private void initItems() {
        for (int i = 0; i < items.length; ++i)
            items[i] = new Item(i);

        JsonNode itemsJson = Json.parseResource("/data/item.json");
        int itemId = 0;
        for (JsonNode itemJson : itemsJson) {
            items[itemId].applyJson(itemJson);
            ++itemId;
        }
    }

    private void initStartingFlags() {
        // Mark HUD as allowed (0x40 -> 0x1DD403)
        startingFlags.add(FLAG_HUD_ALLOWED);

        // Remove Septimus cutscene telling us Ronan has gone insane (0x01 -> 0x1DD358)
        startingFlags.add(FLAG_SEPTIMUS_WAKE_UP_TALKING_ABOUT_RONAN);

        // Remove Sybill cutscene showing Holy Sword cutscene (0x60 -> 0x1DD3F3)
        startingFlags.add(FLAG_SYBILL_HOLY_SWORD_VISION_1);
        startingFlags.add(FLAG_SYBILL_HOLY_SWORD_VISION_2);

        // Open Underground Waterway (0x10 -> 0x1DD370)
        startingFlags.add(FLAG_UNDERGROUND_WATERWAY_OPENED);

        // Remove the fallen tree on the way to Cliffs of Madness (0x20 -> 0x1DD35F)
        startingFlags.add(FLAG_REMOVED_TREE_TO_CLIFFS_OF_MADNESS);

        // Change the sign on the way to Cliffs of Madness to remove the fallen tree warning (0x08 -> 0x1DD34F)
        startingFlags.add(FLAG_CHANGE_SIGN_TO_CLIFFS_OF_MADNESS);

        // Make Merrick trade Gilded Falcons right away
        startingFlags.add(FLAG_LEARNED_ABOUT_MERRICK_WANTING_FALCONS);
        startingFlags.add(FLAG_TALKED_WITH_MERRICK);
        startingFlags.add(FLAG_TALKED_WITH_MERRICK_ABOUT_FALCONS);
        startingFlags.add(FLAG_ACCEPTED_MERRICK_DEAL_ABOUT_FALCONS);

        // Allow entering in Magyscar using a Bomb
        startingFlags.add(FLAG_MAGYSCAR_WALL_IS_CRACKED);

        // Activate Torla geysers on game start, giving access to Torla Mountain
        startingFlags.add(FLAG_TORLA_GEYSERS_ACTIVE);

        // Activating Nirude moai statues on start seems to break the boss reward, so we only remove the lower boulder
        // and let the normal dungeon flow do the rest.
        startingFlags.add(FLAG_NIRUDE_LOWER_BOULDER_REMOVED);

        // Flag allowing to exit Lars Crypt without having to beat the boss
        startingFlags.add(FLAG_CAN_EXIT_LARS_CRYPT);
        // Put flowers on other tombs to indicate you need the Bouquet to enter Lars Crypt
        startingFlags.add(FLAG_OTHER_FLOWERS_ARE_IN_GRAVEYARD);

        // Remove the specific cutscene when we enter Lurvy's shop for the first time
        startingFlags.add(FLAG_LURVY_WELCOMED_US_ONCE);
    }

    public void applyOptions(RandomizerOptions options) {
        if (options.megalithsEnabledOnStart())
            startingFlags.add(FLAG_CAN_ACTIVATE_MEGALITHS);

        if (options.skipLastDungeon()) {
            startingFlags.add(FLAG_OPENED_LAKE_SHRINE_EXTERIOR_MAIN_GATE);
            startingFlags.add(FLAG_LAKE_SHRINE_EXTERIOR_RIGHT_GARDEN_BUTTON);
            startingFlags.add(FLAG_LAKE_SHRINE_EXTERIOR_LEFT_GARDEN_BUTTON);
            startingFlags.add(FLAG_LAKE_SHRINE_EXTERIOR_GARDEN_MIDDLE_GATE);
            startingFlags.add(FLAG_LAKE_SHRINE_INTERIOR_MELZAS_INTRO_CUTSCENE_1);
            startingFlags.add(FLAG_LAKE_SHRINE_INTERIOR_MELZAS_INTRO_CUTSCENE_2);
            startingFlags.add(FLAG_LAKE_SHRINE_NW_SEAL_BROKEN);
            startingFlags.add(FLAG_LAKE_SHRINE_W_SEAL_BROKEN);
            startingFlags.add(FLAG_LAKE_SHRINE_SW_SEAL_BROKEN);
            startingFlags.add(FLAG_LAKE_SHRINE_SE_SEAL_BROKEN);
            startingFlags.add(FLAG_LAKE_SHRINE_E_SEAL_BROKEN);
            startingFlags.add(FLAG_LAKE_SHRINE_NE_SEAL_BROKEN);
        }

        handleCrests(options);
    }

    private void handleCrests(RandomizerOptions options) {
        List<Integer> crests = new ArrayList<>(List.of(
                ITEM_RUBY_CREST, ITEM_SAPPHIRE_CREST, ITEM_TOPAZ_CREST, ITEM_AGATE_CREST,
                ITEM_EMERALD_CREST, ITEM_GARNET_CREST, ITEM_DIAMOND_CREST));
        MersenneTwister rng = new MersenneTwister(options.seed());
        VecTools.shuffle(crests, rng);

        // Add random crests to the "used crests" list
        for (int i = 0; i < options.crestCount(); ++i)
            usedCrests.add(crests.get(i));

        // For each excluded crest, add the flag to automatically place it on its pedestal on game start
        Map<Integer, Flag> crestFlags = Map.of(
                ITEM_RUBY_CREST, FLAG_PLACED_RUBY_CREST,
                ITEM_SAPPHIRE_CREST, FLAG_PLACED_SAPPHIRE_CREST,
                ITEM_TOPAZ_CREST, FLAG_PLACED_TOPAZ_CREST,
                ITEM_AGATE_CREST, FLAG_PLACED_AGATE_CREST,
                ITEM_EMERALD_CREST, FLAG_PLACED_EMERALD_CREST,
                ITEM_GARNET_CREST, FLAG_PLACED_GARNET_CREST,
                ITEM_DIAMOND_CREST, FLAG_PLACED_DIAMOND_CREST);

        for (int i = options.crestCount(); i < crests.size(); ++i)
            startingFlags.add(crestFlags.get(crests.get(i)));
    }

    public Item[] items() {
        return items;
    }

    public Item item(int id) {
        return items[id];
    }

    public Item item(String name) {
        for (Item item : items)
            if (item.name().equals(name))
                return item;
        throw new RandomizerException("Could not find item with name '" + name + "'");
    }

    public List<Flag> startingFlags() {
        return Collections.unmodifiableList(startingFlags);
    }

    public void addStartingFlag(Flag flag) {
        startingFlags.add(flag);
    }

    public List<Integer> usedCrests() {
        return Collections.unmodifiableList(usedCrests);
    }
}
