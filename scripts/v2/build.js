// Builds docs/v2/index.html: the atlas-image version of the world map.
// Usage: node scripts/v2/build.js   (run ChestMatcher.java first to produce docs/v2/candidates.json)
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '../..');
const dataDir = path.join(root, 'src/main/resources/data');
const outDir = path.join(root, 'docs/v2');
const load = f => JSON.parse(fs.readFileSync(path.join(dataDir, f), 'utf8'));

const sources = load('item_source.json');
const regions = load('world_region.json');
const candidates = JSON.parse(fs.readFileSync(path.join(outDir, 'candidates.json'), 'utf8'));
const assignments = require('./assignments.js');

// Atlas image used for each randomizer region ("world" = the whole overworld). Regions without an entry (shops, casino) have no map.
const MAPS = [
    { id: 'world', file: 'World.png', title: 'Overworld', regions: /^Overworld/, only: s => !/^overworld_d2_(watermill|cave)/.test(s.nodeId) },
    { id: 'watermill', file: 'Watermill.png', title: 'Watermill & caverns', regions: /^Overworld D2/, only: s => /^overworld_d2_(watermill|cave)/.test(s.nodeId) },
    { id: 'inoa', file: 'InoaVillage.png', title: 'Inoa Village', regions: /^Inoa$/ },
    { id: 'lars', file: 'LarsCrypt.png', title: "Lars' Crypt", regions: /^Lars Crypt/ },
    { id: 'coal', file: 'InsideCoalMine.png', title: 'Coal Mine', regions: /^Coal Mine/ },
    { id: 'waterway', file: 'UndergroundWaterway.png', title: 'Underground Waterway', regions: /^Undeground Waterway/ },
    { id: 'tarn', file: 'TarnsManor.png', title: "Tarn's Manor", regions: /^Tarn Manor/ },
    { id: 'coastal', file: 'CoastalCave.png', title: 'Coastal Cave', regions: /^Coastal Cave/ },
    { id: 'reptile', file: 'ReptileLair.png', title: 'Reptile Lair', regions: /^Reptile Lair/ },
    { id: 'fairy', file: 'FairyPond.png', title: 'Fairy Pond', regions: /^Fairy Pond/ },
    { id: 'nava', file: 'NavasKeep.png', title: "Nava's Keep", regions: /^Nava's Keep/ },
    { id: 'shrine', file: 'AncientShrine.png', title: 'Ancient Shrine', regions: /^Ancient Shrine/ },
    { id: 'magyscar', file: 'Magyscar.png', title: 'Magyscar', regions: /^Magyscar$/ },
    { id: 'torla', file: 'TorlaMountain.png', title: 'Torla Mountain', regions: /^Torla Mountain/ },
    { id: 'ice', file: 'MtTorlaIceManor.png', title: 'Mt. Torla & Ice Manor', regions: /^Ice Manor/ },
    { id: 'nirude', file: 'NirudesLair.png', title: "Nirude's Lair", regions: /^Nirude's Lair/ },
    { id: 'firemanor', file: 'FireManor.png', title: 'Fire Manor', regions: /^Fire Manor/ },
    { id: 'tower', file: 'TowerOfTheGreatTree.png', title: 'Tower of the Great Tree', regions: /^Tower of the Great Tree/ },
    { id: 'lakeext', file: 'LakeShrine.png', title: 'Lake Shrine (exterior)', regions: /^Lake Shrine$/, only: s => s.nodeId.startsWith('lake_shrine_exterior') },
    { id: 'lakeint', file: 'LakeShrineInterior.png', title: 'Lake Shrine (interior)', regions: /^Lake Shrine$/, only: s => !s.nodeId.startsWith('lake_shrine_exterior') },
];

const regionOfNode = {};
regions.forEach(r => r.nodeIds.forEach(id => regionOfNode[id] = r.name));

const pngSize = file => {
    const b = fs.readFileSync(path.join(outDir, 'maps', file)).subarray(0, 24);
    return [b.readUInt32BE(16), b.readUInt32BE(20)];
};

const maps = MAPS.map(m => {
    const [w, h] = pngSize(m.file);
    const ids = [];
    sources.forEach((s, i) => {
        if (m.regions.test(regionOfNode[s.nodeId] || '') && (!m.only || m.only(s))) ids.push(i);
    });
    return { id: m.id, file: m.file, title: m.title, w, h, sources: ids, candidates: candidates[m.file] || [] };
});

const placements = {};
for (const [mapId, xOrCandidate, y, nodeId, prefix] of assignments) {
    const index = sources.findIndex((s, i) => (s.nodeId === nodeId || s.nodeId.startsWith(nodeId + '_')) && s.name.replace(/^[^:]+: /, '').startsWith(prefix) && !placements[i]);
    if (index < 0) throw new Error(`No source matches ${nodeId} / ${prefix}`);
    // '#n' refers to the n-th (1-based) chest ChestMatcher detected on that map, in the numbering of candidates.json
    const [x, py] = typeof xOrCandidate === 'string' ? maps.find(m => m.id === mapId).candidates[Number(xOrCandidate.slice(1)) - 1] : [xOrCandidate, y];
    placements[index] = { map: mapId, x, y: py };
}

const data = {
    maps,
    placements,
    sources: sources.map(s => ({ name: s.name.replace(/^[^:]+: /, ''), node: s.nodeId, item: s.vanillaItem })),
    regionOf: sources.map(s => regionOfNode[s.nodeId] || ''),
};
const html = fs.readFileSync(path.join(__dirname, 'template.html'), 'utf8').replace('/*DATA*/null', () => JSON.stringify(data));
fs.writeFileSync(path.join(outDir, 'index.html'), html);
console.log(`Wrote docs/v2/index.html: ${Object.keys(placements).length}/${sources.length} sources placed`);
