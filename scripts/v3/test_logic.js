// Sanity check: playing a spoiler greedily (taking every reachable chest) must reach every source and the end node.
// Usage: node scripts/v3/test_logic.js [spoiler.json]
const fs = require('fs');
const path = require('path');
const { buildPaths, exploreNodes, END_NODE } = require('./logic.js');

const data = f => JSON.parse(fs.readFileSync(path.join(__dirname, '../../src/main/resources/data', f), 'utf8'));
const world = { paths: data('world_path.json'), items: data('item.json') };
const sources = data('item_source.json');
const spoilerFile = process.argv[2] || path.join(__dirname, '../../src/test/resources/golden/cpp_reference_spoiler.json');
const spoiler = JSON.parse(fs.readFileSync(spoilerFile, 'utf8'));

const contents = {};
for (const region of Object.values(spoiler.itemSources)) Object.assign(contents, region);
const itemOf = s => contents[`${s.name} (${typeof s.vanillaItem === 'string' ? s.vanillaItem : s.vanillaItem.name})`];

const rs = spoiler.randomizerSettings, gs = spoiler.gameSettings;
const crests = ['Ruby Crest', 'Sapphire Crest', 'Topaz Crest', 'Agate Crest', 'Emerald Crest', 'Garnet Crest', 'Diamond Crest'];
const settings = {
    progressiveItems: gs.progressiveItems, splitBootsEffects: gs.splitBootsEffects, skipLastDungeon: gs.skipLastDungeon,
    magyscarRequiresBomb: !!rs.magyscarRequiresBomb, usedCrests: crests.filter(c => Object.values(contents).includes(c)),
};
const paths = buildPaths(world, settings);

const have = {};
rs.startingInventory.forEach(i => have[i] = (have[i] || 0) + 1);
const taken = new Set();
for (let round = 1; ; round++) {
    const explored = exploreNodes(paths, have);
    const fresh = sources.filter((s, i) => explored.has(s.nodeId) && !taken.has(i));
    if (!fresh.length) {
        console.log(`stable after ${round - 1} spheres: ${taken.size}/${sources.length} sources, end reachable: ${explored.has(END_NODE)}`);
        const stuck = sources.filter((s, i) => !taken.has(i)).map(s => s.name);
        if (stuck.length) console.log('unreachable:', stuck.slice(0, 15));
        process.exit(taken.size === sources.length && explored.has(END_NODE) ? 0 : 1);
    }
    for (const s of fresh) { const i = sources.indexOf(s); taken.add(i); const it = itemOf(s); have[it] = (have[it] || 0) + 1; }
}
