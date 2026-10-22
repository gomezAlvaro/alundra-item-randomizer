// Reachability logic of the randomizer, ported from RandomizerWorld / WorldSolver so the tracker page can run offline.
// `world` is { paths, items }: world_path.json and item.json as shipped in the randomizer.

const SPAWN_NODE = 'inoa';
const END_NODE = 'end';
const UPGRADE_SLOTS = {
    'Sword upgrade': 1, 'Bow upgrade': 2, 'Flail upgrade': 3, 'Armor upgrade': 7,
    'Earth Magic upgrade': 16, 'Water Magic upgrade': 17, 'Fire Magic upgrade': 18, 'Wind Magic upgrade': 19,
};
const CREST_NODES = {
    'Ruby Crest': 'crest_ruby', 'Sapphire Crest': 'crest_sapphire', 'Topaz Crest': 'crest_topaz', 'Agate Crest': 'crest_agate',
    'Emerald Crest': 'crest_emerald', 'Garnet Crest': 'crest_garnet', 'Diamond Crest': 'crest_diamond',
};

/**
 * @param settings { progressiveItems, splitBootsEffects, skipLastDungeon, magyscarRequiresBomb, usedCrests: [crest item names] }
 * @returns the paths with their requirements adjusted the same way the randomizer does for a given seed
 */
function buildPaths(world, settings) {
    const paths = world.paths.map(p => ({
        from: p.fromId,
        to: p.toId,
        twoWay: !!p.twoWay,
        items: (p.requiredItems || []).flatMap(i => typeof i === 'string' ? [i] : Array(i.quantity).fill(i.name)),
        nodes: [...(p.requiredNodes || [])],
    }));

    if (settings.progressiveItems) {
        const slots = { ...UPGRADE_SLOTS };
        if (!settings.splitBootsEffects) slots['Boots upgrade'] = 9;
        for (const [upgrade, slot] of Object.entries(slots)) {
            const toConvert = world.items.filter(i => i.slot === slot && i.tier !== 64);
            for (const path of paths) {
                let highestTier = 0;
                for (const item of toConvert) {
                    const at = path.items.indexOf(item.name);
                    if (at >= 0) {
                        path.items.splice(at, 1);
                        highestTier = Math.max(highestTier, (item.tier + 1) & 0xFF);
                    }
                }
                for (let i = 0; i < highestTier; i++) path.items.push(upgrade);
            }
        }
    }

    // Crests that are not in the seed are not required to reach the Lake Shrine
    const toLake = paths.find(p => p.from === 'overworld_b1' && p.to === 'lake_shrine_exterior');
    for (const [crest, node] of Object.entries(CREST_NODES)) {
        if (!settings.usedCrests.includes(crest)) toLake.nodes = toLake.nodes.filter(n => n !== node);
    }

    if (!settings.magyscarRequiresBomb) {
        const intoMagyscar = paths.find(p => p.from === 'overworld_c1' && p.to === 'magyscar');
        intoMagyscar.items = intoMagyscar.items.filter(i => i !== 'Bomb');
    }
    if (settings.skipLastDungeon) {
        paths.push({ from: 'lake_shrine_exterior', to: 'melzas_fight', twoWay: false, items: [], nodes: [] });
    }
    return paths;
}

/** @param inventory Map-like object { itemName: count }. @returns Set of the node ids the player can reach. */
function exploreNodes(paths, inventory) {
    const explored = new Set([SPAWN_NODE]);
    let changed = true;
    while (changed) {
        changed = false;
        for (const p of paths) {
            const fromHere = explored.has(p.from), fromThere = p.twoWay && explored.has(p.to);
            if ((!fromHere && !fromThere) || (explored.has(p.from) && explored.has(p.to))) continue;
            if (!p.nodes.every(n => explored.has(n))) continue;
            const need = {};
            for (const i of p.items) need[i] = (need[i] || 0) + 1;
            if (!Object.entries(need).every(([name, n]) => (inventory[name] || 0) >= n)) continue;
            explored.add(fromHere ? p.to : p.from);
            changed = true;
        }
    }
    return explored;
}

/** Every item name (and the largest count) that some path asks for: what the tracker lets the player count. */
function requiredItemCounts(paths) {
    const max = {};
    for (const p of paths) {
        const need = {};
        for (const i of p.items) need[i] = (need[i] || 0) + 1;
        for (const [name, n] of Object.entries(need)) max[name] = Math.max(max[name] || 0, n);
    }
    return max;
}

if (typeof module !== 'undefined') module.exports = { buildPaths, exploreNodes, requiredItemCounts, END_NODE };
