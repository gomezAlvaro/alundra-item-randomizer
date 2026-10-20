package com.alundra.randomizer.model;

import com.alundra.randomizer.RandomizerOptions;
import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.game.Item;
import com.alundra.randomizer.tools.Json;
import com.alundra.randomizer.tools.RandomizerException;
import com.alundra.randomizer.tools.VecTools;
import com.alundra.randomizer.tools.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.alundra.randomizer.game.ItemCodes.*;

/**
 * The randomizer's model of the game world: a graph of nodes connected by paths, item sources inside the nodes,
 * regions grouping nodes, and hint sources.
 */
public class RandomizerWorld {
    /** All item sources (events / locations where to get an item) */
    private final List<ItemSource> itemSources = new ArrayList<>();
    /** All logic nodes, sorted by ID */
    private final Map<String, WorldNode> nodes = new TreeMap<>();
    /** All logic paths, which connect nodes to form an explorable graph */
    private final List<WorldPath> paths = new ArrayList<>();
    /** All logic regions, grouping nodes into coherent sets that represent a concrete location inside the game */
    private final List<WorldRegion> regions = new ArrayList<>();
    /** Hint sources, sorted by ID */
    private final Map<String, HintSource> hintSources = new TreeMap<>();
    /** An editable copy of the items distribution from RandomizerOptions */
    private int[] itemsDistribution = new int[ITEM_COUNT];
    /** Items owned by player on game start */
    private final List<Item> startingInventory = new ArrayList<>();

    public RandomizerWorld(GameData gameData) {
        initNodes();
        initRegions();
        initPaths(gameData);
        initItemSources(gameData);
        initHintSources();
    }

    // ------------------------------------------------------------------------------------------------------
    //  Accessors
    // ------------------------------------------------------------------------------------------------------

    public List<ItemSource> itemSources() {
        return Collections.unmodifiableList(itemSources);
    }

    public ItemSource itemSource(String name) {
        for (ItemSource source : itemSources)
            if (source.prettyName().equals(name))
                return source;
        throw new RandomizerException("No ItemSource with name '" + name + "'");
    }

    public ItemSource itemSource(int id) {
        if (id < 0 || id >= itemSources.size())
            throw new RandomizerException("No ItemSource with ID " + id);
        return itemSources.get(id);
    }

    public int itemSourceId(ItemSource source) {
        int index = itemSources.indexOf(source);
        if (index < 0)
            throw new RandomizerException("ItemSource was not found when looking for its ID");
        return index;
    }

    public List<ItemSource> itemSourcesWithItem(Item item) {
        List<ItemSource> sourcesWithItem = new ArrayList<>();
        for (ItemSource source : itemSources)
            if (source.item() == item)
                sourcesWithItem.add(source);
        return sourcesWithItem;
    }

    public Map<String, WorldNode> nodes() {
        return Collections.unmodifiableMap(nodes);
    }

    public WorldNode node(String id) {
        WorldNode node = nodes.get(id);
        if (node == null)
            throw new RandomizerException("No node with ID '" + id + "'");
        return node;
    }

    public WorldNode spawnNode() {
        return node("inoa");
    }

    public WorldNode endNode() {
        return node("end");
    }

    public List<WorldPath> paths() {
        return Collections.unmodifiableList(paths);
    }

    public void addPath(WorldPath path) {
        paths.add(path);
    }

    public List<WorldPath> pathsStartingFromNode(WorldNode node) {
        List<WorldPath> result = new ArrayList<>();
        for (WorldPath path : paths)
            if (path.origin() == node || (path.isTwoWay() && path.destination() == node))
                result.add(path);
        return result;
    }

    public List<WorldRegion> regions() {
        return Collections.unmodifiableList(regions);
    }

    public WorldRegion region(String name) {
        for (WorldRegion region : regions)
            if (region.name().equals(name))
                return region;
        return null;
    }

    public Map<String, HintSource> hintSources() {
        return Collections.unmodifiableMap(hintSources);
    }

    public HintSource hintSource(String id) {
        HintSource source = hintSources.get(id);
        if (source == null)
            throw new RandomizerException("No hint source with ID '" + id + "'");
        return source;
    }

    public int[] itemsDistribution() {
        return itemsDistribution;
    }

    public List<Item> startingInventory() {
        return Collections.unmodifiableList(startingInventory);
    }

    // ------------------------------------------------------------------------------------------------------
    //  Initialization
    // ------------------------------------------------------------------------------------------------------

    private void initItemSources(GameData gameData) {
        for (JsonNode sourceJson : Json.parseResource("/data/item_source.json"))
            itemSources.add(ItemSource.fromJson(sourceJson, gameData, this));
    }

    private void initNodes() {
        for (Map.Entry<String, JsonNode> entry : Json.fields(Json.parseResource("/data/world_node.json")))
            nodes.put(entry.getKey(), WorldNode.fromJson(entry.getValue(), entry.getKey()));
    }

    private void initPaths(GameData gameData) {
        for (JsonNode pathJson : Json.parseResource("/data/world_path.json"))
            addPath(WorldPath.fromJson(pathJson, nodes, gameData));
    }

    private void initRegions() {
        for (JsonNode regionJson : Json.parseResource("/data/world_region.json"))
            regions.add(WorldRegion.fromJson(regionJson, this));

        // Check for orphan nodes
        for (WorldNode node : nodes.values())
            if (node.region() == null)
                throw new RandomizerException("Node '" + node.id() + "' doesn't belong to any region");
    }

    private void initHintSources() {
        for (Map.Entry<String, JsonNode> entry : Json.fields(Json.parseResource("/data/hint_source.json")))
            hintSources.put(entry.getKey(), HintSource.fromJson(entry.getValue(), this));
    }

    // ------------------------------------------------------------------------------------------------------
    //  Options
    // ------------------------------------------------------------------------------------------------------

    public void applyOptions(RandomizerOptions options, GameData gameData) {
        // Apply item sources which contents were set inside the preset configuration
        for (Map.Entry<Integer, Integer> entry : options.fixedItemSources().entrySet())
            itemSource(entry.getKey()).item(gameData.item(entry.getValue()));

        // Mark item sources that cannot contain progression with a specific flag
        for (int sourceId : options.itemSourcesWithoutProgression())
            itemSource(sourceId).canContainProgression(false);

        // Keep an editable copy of the items distribution from rando options
        itemsDistribution = options.itemsDistribution().clone();

        // Apply starting inventory
        for (int itemId : options.startingInventory())
            startingInventory.add(gameData.item(itemId));

        // Handle progressive items in distribution
        if (options.progressiveItems())
            handleProgressiveItems(options, gameData);

        handleCrests(gameData);

        if (options.skipLastDungeon())
            paths.add(new WorldPath(node("lake_shrine_exterior"), node("melzas_fight")));
    }

    private void handleCrests(GameData gameData) {
        Map<Integer, String> crestNodes = Map.of(
                ITEM_RUBY_CREST, "crest_ruby",
                ITEM_SAPPHIRE_CREST, "crest_sapphire",
                ITEM_TOPAZ_CREST, "crest_topaz",
                ITEM_AGATE_CREST, "crest_agate",
                ITEM_EMERALD_CREST, "crest_emerald",
                ITEM_GARNET_CREST, "crest_garnet",
                ITEM_DIAMOND_CREST, "crest_diamond");

        List<Integer> excludedCrests = new ArrayList<>(List.of(
                ITEM_RUBY_CREST, ITEM_SAPPHIRE_CREST, ITEM_TOPAZ_CREST, ITEM_AGATE_CREST,
                ITEM_EMERALD_CREST, ITEM_GARNET_CREST, ITEM_DIAMOND_CREST));

        // Add randomly selected crests to item distribution
        for (int crestId : gameData.usedCrests()) {
            itemsDistribution[crestId] += 1;
            VecTools.eraseFirst(excludedCrests, crestId);
        }

        WorldNode origin = node("overworld_b1");
        WorldNode destination = node("lake_shrine_exterior");
        WorldPath pathToLakeShrine = null;
        for (WorldPath path : paths) {
            if (path.origin() == origin && path.destination() == destination) {
                pathToLakeShrine = path;
                break;
            }
        }

        if (pathToLakeShrine == null)
            throw new RandomizerException("Could not find path to Lake Shrine on crests initialization");

        // Remove each excluded crest from the logic requirements to reach Lake Shrine
        for (int crestId : excludedCrests)
            VecTools.eraseFirst(pathToLakeShrine.requiredNodes(), node(crestNodes.get(crestId)));
    }

    private void handleProgressiveItems(RandomizerOptions options, GameData gameData) {
        List<Integer> slotsToProcess = new ArrayList<>(List.of(
                gameData.item(ITEM_SWORD_UPGRADE).slotId(),
                gameData.item(ITEM_BOW_UPGRADE).slotId(),
                gameData.item(ITEM_FLAIL_UPGRADE).slotId(),
                gameData.item(ITEM_ARMOR_UPGRADE).slotId(),
                gameData.item(ITEM_EARTH_MAGIC_UPGRADE).slotId(),
                gameData.item(ITEM_WATER_MAGIC_UPGRADE).slotId(),
                gameData.item(ITEM_FIRE_MAGIC_UPGRADE).slotId(),
                gameData.item(ITEM_WIND_MAGIC_UPGRADE).slotId()));

        // Boots are only made progressive if their effects are not split
        if (!options.splitBootsEffects())
            slotsToProcess.add(gameData.item(ITEM_BOOTS_UPGRADE).slotId());

        for (int slotId : slotsToProcess) {
            Item upgradeItem = null;
            List<Item> itemsToConvert = new ArrayList<>();
            for (int itemId = 1; itemId < ITEM_COUNT; ++itemId) {
                Item item = gameData.item(itemId);
                if (item.slotId() != slotId)
                    continue;

                if (item.tier() == 64) {
                    upgradeItem = item;
                    continue;
                }
                itemsToConvert.add(item);
            }

            if (upgradeItem == null)
                throw new RandomizerException("Could not find a valid upgrade item for slot " + slotId);

            // Update the items distribution to remove specific item tiers and replace them by item upgrades
            for (Item item : itemsToConvert) {
                int amount = itemsDistribution[item.id()];
                itemsDistribution[upgradeItem.id()] = (itemsDistribution[upgradeItem.id()] + amount) & 0xFF;
                itemsDistribution[item.id()] -= amount;
            }

            // Update logic paths to require a certain count of item upgrades rather than specific items
            for (WorldPath path : paths) {
                List<Item> requiredItems = path.requiredItems();
                int highestTier = 0;
                for (Item item : itemsToConvert) {
                    if (requiredItems.remove(item)) {
                        int itemTier = (item.tier() + 1) & 0xFF;
                        highestTier = Math.max(highestTier, itemTier);
                    }
                }

                for (int i = 1; i <= highestTier; ++i)
                    requiredItems.add(upgradeItem);
            }
        }
    }
}
