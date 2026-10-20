package com.alundra.randomizer.logic;

import com.alundra.randomizer.RandomizerOptions;
import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.game.Item;
import com.alundra.randomizer.model.HintSource;
import com.alundra.randomizer.model.ItemSource;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.model.WorldPath;
import com.alundra.randomizer.model.WorldRegion;
import com.alundra.randomizer.tools.Json;
import com.alundra.randomizer.tools.MersenneTwister;
import com.alundra.randomizer.tools.RandomizerException;
import com.alundra.randomizer.tools.VecTools;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.alundra.randomizer.game.ItemCodes.*;

/**
 * Fills the world's item sources and hint sources.
 * <p>
 * The item placement is "assumed-forward": a {@link WorldSolver} explores the world until it is blocked, then the
 * shuffler randomly chooses one of the blocked paths and places the items required to open it inside item sources
 * that are already reachable. This repeats until the end of the game can be reached, and all remaining items are then
 * placed randomly. This guarantees every generated seed can be completed.
 */
public class WorldShuffler {
    private static final int DEFAULT_ITEM_SOURCE_WEIGHT = 5;
    private static final int MINIMAL_ITEM_SOURCE_WEIGHT = 1;

    private final RandomizerWorld world;
    private final GameData gameData;
    private final RandomizerOptions options;
    private final PrintStream out;

    /** The random number generator used to randomize the world */
    private final MersenneTwister rng;

    /** The solver used to progressively explore the world, to know where items can be placed */
    private final WorldSolver solver;

    /** The pool of items to place (contains duplicates to represent the amount of instances to place) */
    private final List<Item> itemPool = new ArrayList<>();

    /**
     * The amount of times an item source was considered for placing a progression item, allowing for dynamic
     * weighting to prevent item sources in the first sphere to statistically contain better items
     */
    private final Map<ItemSource, Integer> itemSourceSightings = new HashMap<>();

    /** Item sources containing progression items, in the order they were filled */
    private final List<ItemSource> logicalPlaythrough = new ArrayList<>();

    /** The smallest inventory the solver could find to beat the seed */
    private List<Item> minimalItemsToComplete = new ArrayList<>();

    private final List<WorldRegion> hintableRegionRequirements = new ArrayList<>();
    private final List<Integer> hintableItemLocations = new ArrayList<>();

    public WorldShuffler(RandomizerWorld world, GameData gameData, RandomizerOptions options) {
        this(world, gameData, options, System.out);
    }

    public WorldShuffler(RandomizerWorld world, GameData gameData, RandomizerOptions options, PrintStream out) {
        this.world = world;
        this.gameData = gameData;
        this.options = options;
        this.out = out;
        this.rng = new MersenneTwister(options.seed());
        this.solver = new WorldSolver(world, true);
    }

    // ------------------------------------------------------------------------------------------------------
    //  Items
    // ------------------------------------------------------------------------------------------------------

    public void randomizeItems() {
        initItemPool();

        solver.setup(world.spawnNode(), world.endNode(), world.startingInventory());

        boolean exploredNewNodes = true;
        while (exploredNewNodes) {
            // Run a solver step to reach a "blocked" state where something needs to be placed in order to continue
            exploredNewNodes = solver.runUntilBlocked();
            // Place one or several key items in order to open a blocked path
            openRandomBlockedPath();
            // Item sources changed, force the solver to update its inventory
            solver.updateCurrentInventory();
        }

        // Place the remaining items from the item pool in the remaining sources
        placeRemainingItems();

        performSpecificTweaks();

        if (!solver.reachedEnd())
            throw new RandomizerException("Could not generate a completable world with the given settings. "
                    + "Try another seed or check the preset (items distribution, fixed item sources...).");

        // Analyse items required to complete the seed
        minimalItemsToComplete = solver.findMinimalInventory();
        List<Object> requiredItems = Json.childArray(solver.debugLog(), "requiredItems");
        for (Item item : minimalItemsToComplete)
            requiredItems.add(item.name());
    }

    /**
     * Initializes the item pool which contains all items that need to be placed. The item pool must have the exact
     * size of the empty item sources that need to be filled. It is built from the items distribution, minus the items
     * already placed (fixed item sources).
     */
    private void initItemPool() {
        itemPool.clear();

        int filledItemSourcesCount = 0;
        for (ItemSource source : world.itemSources())
            if (!source.isEmpty())
                filledItemSourcesCount++;
        if (filledItemSourcesCount == world.itemSources().size())
            return;

        int[] distributionCopy = world.itemsDistribution().clone();

        // Count quantities already in place
        for (ItemSource source : world.itemSources()) {
            if (source.isEmpty())
                continue;

            int itemId = source.item().id();
            if (distributionCopy[itemId] == 0)
                throw new RandomizerException("There are more " + source.item().name()
                        + " already placed than the expected number in the item pool");
            distributionCopy[itemId] -= 1;
        }

        // Build the item pool from the quantities in the items distribution
        for (int itemId = 0; itemId < distributionCopy.length; ++itemId)
            for (int i = 0; i < distributionCopy[itemId]; ++i)
                itemPool.add(gameData.item(itemId));

        // Count the empty item sources, and compare this count to the item pool size to handle invalid cases
        int emptyItemSourcesCount = 0;
        for (ItemSource source : world.itemSources())
            if (source.isEmpty())
                emptyItemSourcesCount += 1;

        if (itemPool.size() > emptyItemSourcesCount) {
            throw new RandomizerException("The number of items in item pool is not the same as the number of empty item sources ("
                    + itemPool.size() + " =/= " + emptyItemSourcesCount + ")");
        } else if (itemPool.size() < emptyItemSourcesCount) {
            int missingItemCount = emptyItemSourcesCount - itemPool.size();
            out.println("Warning: Item pool (" + itemPool.size() + " items) is smaller than the empty item sources pool ("
                    + emptyItemSourcesCount + " item sources).\nRemaining sources will remain empty.\n");
            for (int i = 0; i < missingItemCount; ++i)
                itemPool.add(gameData.item(ITEM_NONE));
        }

        VecTools.shuffle(itemPool, rng);
    }

    /**
     * Finds a random reachable ItemSource where the given progression item can be placed.
     *
     * @param excludedSources item sources that are instantly rejected
     */
    private ItemSource findItemSourceForProgressionItem(Item item, List<ItemSource> excludedSources) {
        List<ItemSource> reachableSources = solver.reachableItemSources();

        List<ItemSource> weightedSources = new ArrayList<>(reachableSources.size() * DEFAULT_ITEM_SOURCE_WEIGHT);
        for (ItemSource source : reachableSources) {
            if (excludedSources.contains(source))
                continue;
            if (!source.isEmpty())
                continue;
            if (!source.canContainProgression())
                continue;
            if (!testItemSourceCompatibility(source, item))
                continue;

            // The more an item source has been considered to place a progression item, the less likely it becomes
            // that it is actually selected. This counterbalances the fact that item sources in the first sphere are
            // considered many more times than late game ones (since they are reachable from the start).
            int sightings = itemSourceSightings.merge(source, 1, Integer::sum);
            int weight = Math.max(DEFAULT_ITEM_SOURCE_WEIGHT - sightings, MINIMAL_ITEM_SOURCE_WEIGHT);
            for (int i = 0; i < weight; ++i)
                weightedSources.add(source);
        }

        if (weightedSources.isEmpty())
            throw new RandomizerException("Could not place " + item.name() + " in any of the "
                    + reachableSources.size() + " possible sources.");

        VecTools.shuffle(weightedSources, rng);
        return weightedSources.get(0);
    }

    /** Fills the given item source with the first compatible item from the (shuffled) item pool. */
    private void fillItemSourceRandomly(ItemSource source) {
        for (int i = 0; i < itemPool.size(); ++i) {
            Item item = itemPool.get(i);
            if (testItemSourceCompatibility(source, item)) {
                source.item(item);
                itemPool.remove(i);
                return;
            }
        }

        out.println("[WARNING] Item source '" + source.name() + "' could not be filled with any item of the item pool.");
        source.item(gameData.item(ITEM_NONE));
    }

    /** @return true if the given item can be placed inside the given item source */
    private boolean testItemSourceCompatibility(ItemSource source, Item item) {
        // Forbid precious items in sources that can be taken repeatedly
        if (source.forbidPreciousItems() && item.isPrecious())
            return false;

        // Forbid items that have no gold value in shops
        if (!source.priceAddresses().isEmpty() && item.goldValue() == 0)
            return false;

        // Forbid falcons in Merrick's shop, since this can cause flag shenanigans
        if (source.merrickItemAddress() != 0 && item.id() == ITEM_GILDED_FALCON)
            return false;

        // If nodes should have unique items, check that other ItemSources in node do not contain this Item
        if (source.node().shouldHaveUniqueItems() && item.id() != ITEM_NONE) {
            for (ItemSource otherSource : source.node().itemSources())
                if (otherSource.item() == item)
                    return false;
        }

        return true;
    }

    /** @return true if the item pool contains at least the given items (with duplicates taken into account) */
    private boolean itemPoolContainsItems(List<Item> items) {
        Map<Item, Integer> quantities = new HashMap<>();
        for (Item item : items)
            quantities.merge(item, 1, Integer::sum);

        for (Map.Entry<Item, Integer> entry : quantities.entrySet())
            if (VecTools.count(itemPool, entry.getKey()) < entry.getValue())
                return false;
        return true;
    }

    /**
     * Builds a shuffled list of the paths currently blocked, which can be opened by placing items from the item pool.
     * Each path appears as many times as its weight.
     */
    private List<WorldPath> buildWeightedBlockedPathsList() {
        List<WorldPath> weightedBlockedPaths = new ArrayList<>();
        Map<String, Object> debugLog = solver.debugLogForCurrentStep();

        for (WorldPath path : solver.blockedPaths()) {
            // If items are not the (only) blocking point for taking this path, let it go
            if (!solver.missingNodesToTakePath(path).isEmpty())
                continue;

            // If all items cannot be placed since the item pool is running out of that item type, do not try to open
            // this path. It will open by itself once the item (as placed by a fixed item source) is reached.
            Map<Integer, Integer> quantitiesToPlace = new TreeMap<>();
            for (Item item : solver.missingItemsToTakePath(path))
                quantitiesToPlace.merge(item.id(), 1, Integer::sum);

            boolean canPlaceAllItems = true;
            for (Map.Entry<Integer, Integer> entry : quantitiesToPlace.entrySet()) {
                if (VecTools.count(itemPool, gameData.item(entry.getKey())) < entry.getValue()) {
                    canPlaceAllItems = false;
                    break;
                }
            }

            if (canPlaceAllItems) {
                for (int i = 0; i < path.weight(); ++i)
                    weightedBlockedPaths.add(path);
                Json.childArray(debugLog, "blockedPaths").add(path.origin().id() + " --> " + path.destination().id());
            }
        }

        VecTools.shuffle(weightedBlockedPaths, rng);
        return weightedBlockedPaths;
    }

    /** Opens one of the blocked paths encountered by the solver by placing randomly the items required to cross it. */
    private void openRandomBlockedPath() {
        Map<String, Object> debugLog = solver.debugLogForCurrentStep();
        debugLog.put("blockedPaths", new ArrayList<>());

        List<WorldPath> weightedBlockedPaths = buildWeightedBlockedPathsList();
        if (weightedBlockedPaths.isEmpty())
            return;

        for (WorldPath pathToOpen : weightedBlockedPaths) {
            List<Item> itemsToPlace = solver.missingItemsToTakePath(pathToOpen);

            // Check if all items required to cross the path are available in the item pool
            if (!itemPoolContainsItems(itemsToPlace))
                continue;

            // Find a set of item sources accepting all the required progression items to open the path
            List<ItemSource> pickedItemSources = new ArrayList<>();
            for (Item item : itemsToPlace)
                pickedItemSources.add(findItemSourceForProgressionItem(item, pickedItemSources));

            Json.childArray(debugLog, "chosenPath").add(pathToOpen.origin().id() + " --> " + pathToOpen.destination().id());

            // Place all missing key items for the player to be able to open this blocking path
            Map<String, Object> placedKeyItems = Json.child(debugLog, "placedKeyItems");
            for (int i = 0; i < itemsToPlace.size(); ++i) {
                Item item = itemsToPlace.get(i);
                ItemSource source = pickedItemSources.get(i);
                source.item(item);
                VecTools.eraseFirst(itemPool, item);

                logicalPlaythrough.add(source);
                placedKeyItems.put(source.name(), item.name());
            }
            return;
        }
    }

    /** Places all remaining items from the item pool in the item sources that are still empty. */
    private void placeRemainingItems() {
        List<ItemSource> emptyItemSources = new ArrayList<>();
        for (ItemSource source : world.itemSources())
            if (source.isEmpty())
                emptyItemSources.add(source);
        VecTools.shuffle(emptyItemSources, rng);

        // Sort sources (stable, keeping the random order in case of equality) to process the most restricted first
        emptyItemSources.sort(Comparator.comparingInt(ItemSource::fillPriority).reversed());

        for (ItemSource source : emptyItemSources)
            fillItemSourceRandomly(source);

        for (Item item : itemPool)
            out.println("[WARNING] Item '" + item.name() + "' is remaining in the item pool at end of generation.");

        itemPool.clear();
    }

    private void performSpecificTweaks() {
        // In Lurvy's shop, if unique items contain "Nothing", those cannot be bought and that makes the subsequent
        // unique items unreachable. For that reason, we "push" the "Nothing" items towards the end of the unique items
        // list to avoid any uncompletable seed.
        ItemSource lurvyUnique1 = world.itemSource("Lurvy's Shop: Item #5 (Leather Armor)");
        ItemSource lurvyUnique2 = world.itemSource("Lurvy's Shop: Item #5 replacement when bought once (Life Vessel)");
        ItemSource lurvyUnique3 = world.itemSource("Lurvy's Shop: Item #5 replacement when bought twice (Wonder Essence)");

        if (lurvyUnique2.item().id() == ITEM_NONE) {
            lurvyUnique2.item(lurvyUnique3.item());
            lurvyUnique3.item(gameData.item(ITEM_NONE));
        }

        if (lurvyUnique1.item().id() == ITEM_NONE) {
            lurvyUnique1.item(lurvyUnique2.item());
            lurvyUnique2.item(lurvyUnique3.item());
            lurvyUnique3.item(gameData.item(ITEM_NONE));
        }
    }

    /** @return the item sources holding the items strictly needed to beat the seed, in logical order */
    public Map<String, Object> playthroughAsJson() {
        Map<String, Object> json = Json.object();
        for (ItemSource source : logicalPlaythrough) {
            Item keyItemInSource = source.item();
            if (minimalItemsToComplete.contains(keyItemInSource))
                json.put(source.name(), keyItemInSource.name());
        }
        return json;
    }

    public Map<String, Object> debugLogAsJson() {
        return solver.debugLog();
    }

    // ------------------------------------------------------------------------------------------------------
    //  Hints
    // ------------------------------------------------------------------------------------------------------

    public void randomizeHints() {
        initHintCollections();

        List<HintSource> hintSourcesPool = new ArrayList<>(world.hintSources().values());
        VecTools.shuffle(hintSourcesPool, rng);

        int remainingRegionRequirementHints = 5;

        for (HintSource hintSource : hintSourcesPool) {
            if (remainingRegionRequirementHints > 0 && generateRegionRequirementHint(hintSource)) {
                remainingRegionRequirementHints--;
                continue;
            }
            generateItemLocationHint(hintSource);
        }
    }

    private void initHintCollections() {
        // A shuffled list of regions, used for the "barren / useful region" hints
        for (WorldRegion region : world.regions())
            if (region.canBeHintedAsRequired())
                hintableRegionRequirements.add(region);
        VecTools.shuffle(hintableRegionRequirements, rng);

        // A shuffled list of items which location is interesting, used for the "item X is in Y" hints
        hintableItemLocations.addAll(List.of(
                ITEM_RUBY_CREST, ITEM_SAPPHIRE_CREST,
                ITEM_TOPAZ_CREST, ITEM_AGATE_CREST,
                ITEM_GARNET_CREST, ITEM_EMERALD_CREST,
                ITEM_DIAMOND_CREST,

                ITEM_BOMB, ITEM_SAND_CAPE,
                ITEM_SPRING_BEAN,
                ITEM_FIRE_WAND, ITEM_ICE_WAND,
                ITEM_BOUQUET, ITEM_TREE_GEM,
                ITEM_SECRET_PASS, ITEM_POWER_GLOVE));

        // Handle hints for flail location
        hintableItemLocations.add(options.progressiveItems() ? ITEM_FLAIL_UPGRADE : ITEM_IRON_FLAIL);

        // Handle hints for boots location
        if (options.progressiveItems() && !options.splitBootsEffects()) {
            hintableItemLocations.add(ITEM_BOOTS_UPGRADE);
        } else {
            hintableItemLocations.add(ITEM_LONG_BOOTS);
            hintableItemLocations.add(ITEM_MERMAN_BOOTS);
        }

        VecTools.shuffle(hintableItemLocations, rng);
    }

    private boolean generateRegionRequirementHint(HintSource hintSource) {
        WorldRegion hintedRegion = null;
        for (WorldRegion region : hintableRegionRequirements) {
            // Check that taking items from region is not mandatory to reach the hint source
            WorldSolver hintSolver = new WorldSolver(world);
            hintSolver.forbidTakingItemsFromNodes(region.nodes());
            if (!hintSolver.tryToSolve(world.spawnNode(), hintSource.node(), world.startingInventory()))
                continue;

            hintedRegion = region;
            hintableRegionRequirements.remove(region);
            break;
        }

        if (hintedRegion == null)
            return false;

        if (isRegionAvoidable(hintedRegion))
            hintSource.text("nothing of interest can be found " + hintedRegion.hintName());
        else
            hintSource.text("something useful lies " + hintedRegion.hintName());
        return true;
    }

    private boolean generateItemLocationHint(HintSource hintSource) {
        Item hintedItem = null;
        for (int itemId : hintableItemLocations) {
            Item testedItem = gameData.item(itemId);

            WorldSolver hintSolver = new WorldSolver(world);
            hintSolver.forbidItemType(testedItem);
            if (hintSolver.tryToSolve(world.spawnNode(), hintSource.node(), world.startingInventory())) {
                // If item is not mandatory to reach the hint source, we can hint it
                hintedItem = testedItem;
                VecTools.eraseFirst(hintableItemLocations, itemId);
                break;
            }
        }

        if (hintedItem == null)
            return false;

        hintSource.text(hintedItem.name() + " can be found " + randomHintForItem(hintedItem));
        return true;
    }

    private boolean isRegionAvoidable(WorldRegion region) {
        WorldSolver regionSolver = new WorldSolver(world);
        regionSolver.forbidTakingItemsFromNodes(region.nodes());
        return regionSolver.tryToSolve(world.spawnNode(), world.endNode(), world.startingInventory());
    }

    private String randomHintForItem(Item item) {
        List<ItemSource> sources = world.itemSourcesWithItem(item);
        if (sources.isEmpty())
            return "in an unknown place";

        VecTools.shuffle(sources, rng);
        return randomHintForItemSource(sources.get(0));
    }

    private String randomHintForItemSource(ItemSource itemSource) {
        List<String> allHints = new ArrayList<>();
        allHints.add(itemSource.node().region().hintName());
        allHints.addAll(itemSource.node().hints());
        allHints.addAll(itemSource.hints());

        VecTools.shuffle(allHints, rng);
        return allHints.get(0);
    }
}
