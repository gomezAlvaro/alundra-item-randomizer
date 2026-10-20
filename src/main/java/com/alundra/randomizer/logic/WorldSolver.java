package com.alundra.randomizer.logic;

import com.alundra.randomizer.game.Item;
import com.alundra.randomizer.model.ItemSource;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.model.WorldNode;
import com.alundra.randomizer.model.WorldPath;
import com.alundra.randomizer.tools.Json;
import com.alundra.randomizer.tools.RandomizerException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks a world's solvability, or explores a world until the next blocking state so it can be filled iteratively
 * and be solvable in the end.
 * <p>
 * It works by setting a starting point and an end point, and optional constraints (forbidding to take item X, to take
 * items in node Y...) which are used to determine minimal requirements to solve the seed, or to generate hints.
 */
public class WorldSolver {
    private static final int UNLIMITED = 0xFFFF;

    private final RandomizerWorld world;

    // ------------- Solver input settings -------------

    private WorldNode startNode = null;
    private WorldNode endNode = null;
    private List<Item> startingInventory = new ArrayList<>();
    /** Item quantities for each type we refrain from taking */
    private final Map<Item, Integer> forbiddenItems = new HashMap<>();
    /** Nodes where it is forbidden to take any item from any item source */
    private final Set<WorldNode> forbiddenNodesToPickItems = new HashSet<>();

    // ------------- Solver state variables -------------

    private List<Item> inventory = new ArrayList<>();
    private final List<WorldNode> exploredNodes = new ArrayList<>();
    private final Set<WorldNode> exploredNodesSet = new HashSet<>();
    private final List<WorldNode> nodesToExplore = new ArrayList<>();
    private final List<WorldPath> blockedPaths = new ArrayList<>();
    private final List<ItemSource> reachableItemSources = new ArrayList<>();
    private int stepCount = 0;

    /** A debug log under the form of a dumpable JSON object (null when logging is disabled) */
    private Map<String, Object> debugLog;

    public WorldSolver(RandomizerWorld world) {
        this(world, false);
    }

    /**
     * @param withDebugLog if true, the solver keeps a detailed log of its exploration steps (used by the main solver
     *                     of the shuffler, not by the many sub-solvers used for analysis)
     */
    public WorldSolver(RandomizerWorld world, boolean withDebugLog) {
        this.world = world;
        this.debugLog = withDebugLog ? Json.object() : null;
    }

    /** Forbids taking the given amount of each item type while trying to solve the world. */
    public void forbidItems(Map<Item, Integer> itemQuantities) {
        for (Map.Entry<Item, Integer> entry : itemQuantities.entrySet())
            forbiddenItems.merge(entry.getKey(), entry.getValue(), Integer::sum);
    }

    /** Forbids taking any item of the given type while trying to solve the world. */
    public void forbidItemType(Item item) {
        forbiddenItems.put(item, UNLIMITED);
    }

    /** Forbids taking items from item sources inside the given nodes while trying to solve the world. */
    public void forbidTakingItemsFromNodes(Collection<WorldNode> forbiddenNodes) {
        forbiddenNodesToPickItems.clear();
        forbiddenNodesToPickItems.addAll(forbiddenNodes);
    }

    /** Sets the solver up, putting it back in its original state if it was already used. */
    public void setup(WorldNode startNode, WorldNode endNode, List<Item> startingInventory) {
        this.startNode = startNode;
        this.endNode = endNode;
        this.startingInventory = new ArrayList<>(startingInventory);

        nodesToExplore.clear();
        nodesToExplore.add(startNode);
        exploredNodes.clear();
        exploredNodesSet.clear();
        blockedPaths.clear();
        reachableItemSources.clear();
        inventory = new ArrayList<>(this.startingInventory);
        stepCount = 0;

        if (debugLog != null)
            debugLog.clear();
    }

    /**
     * Tries to reach endNode when starting at startNode and having the given starting items.
     *
     * @return true if endNode could be reached, false otherwise
     */
    public boolean tryToSolve(WorldNode startNode, WorldNode endNode, List<Item> startingInventory) {
        setup(startNode, endNode, startingInventory);
        runUntilBlocked();
        return reachedEnd();
    }

    /**
     * Runs the solver until it is fully blocked.
     *
     * @return true if something happened, false if the solver is in the exact same state as before
     */
    public boolean runUntilBlocked() {
        stepCount++;
        int exploredNodesCountAtStart = exploredNodes.size();
        int blockedPathsCountAtStart = blockedPaths.size();

        boolean unlockedNewPaths = true;
        while (unlockedNewPaths) {
            // 1) Expand the "exploration zone" by taking paths we can take with current inventory and exploration.
            expandExplorationZone();
            // 2) Update the inventory by collecting all item sources reachable by the player
            updateCurrentInventory();
            // 3) While expanding the exploration zone, we encountered blocked paths. Find if we can open some of them.
            unlockedNewPaths = tryUnlockingPaths();
        }

        boolean newNodesExplored = exploredNodes.size() > exploredNodesCountAtStart;
        boolean pathsUnlocked = blockedPaths.size() < blockedPathsCountAtStart;
        return newNodesExplored || pathsUnlocked;
    }

    /**
     * Updates the solver inventory, by taking everything in reachable item sources (minus the forbidden items).
     */
    public void updateCurrentInventory() {
        inventory = new ArrayList<>(startingInventory.size() + reachableItemSources.size());
        inventory.addAll(startingInventory);

        Map<Item, Integer> forbiddenItemsCopy = new HashMap<>(forbiddenItems);

        for (ItemSource source : reachableItemSources) {
            // If item is located in forbidden node, don't take it
            if (forbiddenNodesToPickItems.contains(source.node()))
                continue;

            Item item = source.item();
            if (item == null)
                continue;

            Integer forbiddenCount = forbiddenItemsCopy.get(item);
            if (forbiddenCount != null && forbiddenCount > 0) {
                // If item is a forbidden item, don't take it
                forbiddenItemsCopy.put(item, forbiddenCount - 1);
                continue;
            }

            inventory.add(item);
        }
    }

    public boolean canTakePath(WorldPath path) {
        return missingItemsToTakePath(path).isEmpty() && missingNodesToTakePath(path).isEmpty();
    }

    /** @return the items that are missing in the inventory for the solver to be able to cross the given path */
    public List<Item> missingItemsToTakePath(WorldPath path) {
        List<Item> inventoryCopy = new ArrayList<>(inventory);
        List<Item> missingItems = new ArrayList<>();
        for (Item item : path.requiredItems()) {
            // Remove found items from the inventory copy for them not to count several times (case where quantity > 1)
            if (!inventoryCopy.remove(item))
                missingItems.add(item);
        }
        return missingItems;
    }

    /** @return the nodes that are yet to be visited for the solver to be able to cross the given path */
    public List<WorldNode> missingNodesToTakePath(WorldPath path) {
        List<WorldNode> missingNodes = new ArrayList<>();
        for (WorldNode node : path.requiredNodes())
            if (!exploredNodesSet.contains(node))
                missingNodes.add(node);
        return missingNodes;
    }

    public boolean reachedEnd() {
        return exploredNodesSet.contains(endNode);
    }

    /**
     * Computes a minimal list of items which are strictly required to complete the seed. If an item is in this list,
     * it means the solver tried to solve the seed without it and didn't succeed. It needs to be called on an already
     * completed WorldSolver.
     */
    public List<Item> findMinimalInventory() {
        if (!reachedEnd())
            throw new RandomizerException("Tried to find minimal inventory on an incomplete WorldSolver");

        List<Item> minimalInventory = new ArrayList<>();
        Map<Item, Integer> forbidden = new HashMap<>();
        for (Item item : inventory) {
            Map<Item, Integer> forbiddenPlusOne = new HashMap<>(forbidden);
            forbiddenPlusOne.merge(item, 1, Integer::sum);

            WorldSolver solver = new WorldSolver(world);
            solver.forbidItems(forbiddenPlusOne);
            if (solver.tryToSolve(startNode, endNode, startingInventory)) {
                // Item can be freely removed: keep it removed for further solves
                forbidden = forbiddenPlusOne;
            } else {
                // Item cannot be removed: it means it's required
                minimalInventory.add(item);
            }
        }
        return minimalInventory;
    }

    // ------------------------------------------------------------------------------------------------------
    //  Internal functions
    // ------------------------------------------------------------------------------------------------------

    private boolean isExploredOrPending(WorldNode node) {
        return exploredNodesSet.contains(node) || nodesToExplore.contains(node);
    }

    /**
     * "Expands" the exploration zone by recursively taking all available paths that the solver is allowed to cross
     * given the items it currently owns and the regions it has already visited.
     */
    private void expandExplorationZone() {
        List<Object> log = explorationLog();

        while (!nodesToExplore.isEmpty()) {
            WorldNode node = nodesToExplore.remove(0);

            if (exploredNodesSet.contains(node))
                throw new RandomizerException("Exploring the same node '" + node.id() + "' twice");
            exploredNodes.add(node);
            exploredNodesSet.add(node);

            if (log != null)
                log.add("Explored " + node.id());

            // Process all outgoing paths
            for (WorldPath path : world.pathsStartingFromNode(node)) {
                if (canTakePath(path)) {
                    takePath(path);
                } else {
                    // If destination has already been explored or is already pending exploration, ignore it
                    WorldNode destination = path.destination();
                    if (path.isTwoWay() && destination == node)
                        destination = path.origin();

                    if (isExploredOrPending(destination))
                        continue;

                    if (log != null)
                        log.add("Found blocked path to " + path.destination().id());

                    // For uncrossable blocked paths, add them to a pending list
                    blockedPaths.add(path);
                }
            }

            // Add all item sources in this node to the reachable item sources list
            reachableItemSources.addAll(node.itemSources());
        }
    }

    /**
     * Looks at all the known blocked paths, and unlocks any path that can now be crossed thanks to new items or nodes.
     *
     * @return true if at least one path was opened
     */
    private boolean tryUnlockingPaths() {
        boolean openedAtLeastOnePath = false;
        List<Object> log = explorationLog();

        for (int i = 0; i < blockedPaths.size(); ++i) {
            WorldPath path = blockedPaths.get(i);
            if (canTakePath(path)) {
                takePath(path);
                blockedPaths.remove(i);
                --i;

                openedAtLeastOnePath = true;
                if (log != null)
                    log.add("Unlocked path " + path.origin().id() + " --> " + path.destination().id());
            }
        }
        return openedAtLeastOnePath;
    }

    private void takePath(WorldPath path) {
        WorldNode destination = path.destination();
        if (!isExploredOrPending(destination)) {
            addNodeToExplore(destination);
        } else if (path.isTwoWay()) {
            // If one way was useless, maybe it was because we were coming from the destination towards the origin
            destination = path.origin();
            if (!isExploredOrPending(destination))
                addNodeToExplore(destination);
        }
    }

    private void addNodeToExplore(WorldNode node) {
        nodesToExplore.add(node);
        List<Object> log = explorationLog();
        if (log != null)
            log.add("Added " + node.id() + " to accessible nodes");
    }

    // ------------------------------------------------------------------------------------------------------
    //  Accessors
    // ------------------------------------------------------------------------------------------------------

    public List<Item> startingInventory() { return startingInventory; }
    public List<WorldPath> blockedPaths() { return blockedPaths; }
    public List<ItemSource> reachableItemSources() { return reachableItemSources; }
    public List<Item> inventory() { return inventory; }

    /** @return the debug log, or null if this solver was created without one */
    public Map<String, Object> debugLog() { return debugLog; }

    /** @return the debug log section for the current step, or null if this solver was created without a debug log */
    public Map<String, Object> debugLogForCurrentStep() {
        if (debugLog == null)
            return null;
        return Json.child(Json.child(debugLog, "steps"), String.valueOf(stepCount));
    }

    private List<Object> explorationLog() {
        Map<String, Object> step = debugLogForCurrentStep();
        return step == null ? null : Json.childArray(step, "exploration");
    }
}
