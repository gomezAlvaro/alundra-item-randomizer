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
    { id: 'world', file: 'World.png', title: 'Overworld', regions: /^Overworld/ },
    { id: 'watermill', file: 'Watermill.png', title: 'Watermill & caverns', regions: /^Overworld D2/, only: s => /^overworld_d2_(watermill|cave)/.test(s.nodeId) },
    { id: 'sanctuary', file: 'SanctuaryArea.png', title: 'Sanctuary area & caves', regions: /^Overworld C2/ },
    { id: 'coalarea', file: 'CoalMineArea.png', title: 'Coal Mine area & caves', regions: /^Overworld B2/, only: s => /^overworld_b2_(upper_mines|cave_to_lake)/.test(s.nodeId) },
    { id: 'murgg', file: 'MurggWoods.png', title: 'Murgg Woods caves', regions: /^Overworld A2/, only: s => /bridge cave|circular cave|Stone Golem/.test(s.name) },
    { id: 'mimics', file: 'Shack.png', title: 'Ruins & Mimics cave (A3)', regions: /^Overworld A3/, only: s => s.nodeId.startsWith('overworld_a3_mimics_cave') },
    { id: 'cliffs', file: 'CliffsOfMadness.png', title: 'Cliffs of Madness well & Lurvy', regions: /^Overworld E4/, only: s => /^(overworld_e4_well|lurvy_shop)/.test(s.nodeId) },
    { id: 'desertn', file: 'DespairDesertN.png', title: 'Despair Desert N caves', regions: /^Overworld E2/, only: s => /cave/.test(s.name) },
    { id: 'deserts', file: 'DespairDesertS.png', title: 'Despair Desert S cave', regions: /^Overworld E3/, only: s => /cave/.test(s.name) },
    { id: 'navacabin', file: 'BeachNavasCabin.png', title: "Nava's cabin", regions: /^Overworld B4/, only: s => /Nava's cabin/.test(s.name) },
    { id: 'casino', file: 'PubPool.png', title: 'Riverside Casino', regions: /^Riverside Casino/ },
    { id: 'waterway', file: 'UndergroundWaterway.png', title: 'Underground Waterway', regions: /^(Undeground Waterway|Overworld D2)/, only: s => s.nodeId.startsWith('underground_waterway') || /next to Underground Waterway/.test(s.name) },
    { id: 'firemanor', file: 'FireManor.png', title: 'Fire Manor', regions: /^(Fire Manor|Overworld A4)/ },
    { id: 'inoa', file: 'InoaVillage.png', title: 'Inoa Village', regions: /^Inoa$/ },
    { id: 'lars', file: 'LarsCrypt.png', title: "Lars' Crypt", regions: /^Lars Crypt/ },
    { id: 'coal', file: 'InsideCoalMine.png', title: 'Coal Mine', regions: /^Coal Mine/ },
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

const claims = (m, s) => m.regions.test(regionOfNode[s.nodeId] || '') && (!m.only || m.only(s));
const ownerOf = s => (MAPS.find(m => m.id !== 'world' && claims(m, s)) || MAPS[0]);
const maps = MAPS.map(m => {
    const [w, h] = pngSize(m.file);
    const ids = [];
    sources.forEach((s, i) => { if (ownerOf(s) === m) ids.push(i); });
    return { id: m.id, file: m.file, title: m.title, w, h, sources: ids, candidates: candidates[m.file] || [] };
});

const placements = {};
for (const [mapId, xOrCandidate, y, nodeId, prefix, forced] of assignments) {
    const index = sources.findIndex((s, i) => (s.nodeId === nodeId || s.nodeId.startsWith(nodeId + '_')) && s.name.replace(/^[^:]+: /, '').startsWith(prefix) && !placements[i]);
    if (index < 0) throw new Error(`No source matches ${nodeId} / ${prefix}`);
    // '#n' refers to the n-th (1-based) chest ChestMatcher detected on that map, in the numbering of candidates.json
    const [x, py] = typeof xOrCandidate === 'string' ? maps.find(m => m.id === mapId).candidates[Number(xOrCandidate.slice(1)) - 1] : [xOrCandidate, y];
    placements[index] = { map: mapId, x, y: py, c: forced || '' };
}

// Confidence colours: g = exact chest identified, y = a chest is there but which source it is is a guess, r = approximate pin.
// A source is 'y' when other sources on the same map hold the same vanilla item and nothing (position on the open overworld) tells them apart.
const reviewed = require('./confidence.js');
const itemKey = s => JSON.stringify(s.vanillaItem);
const POSITIONAL = /\b(left|right|top|bottom|upper|lower|first|second|third|middle|1st|2nd|3rd|leftmost|rightmost|elevated)\b/i;
for (const i of Object.keys(placements)) {
    const p = placements[i], s = sources[i];
    if (p.c) continue;
    const reviewedColor = reviewed[`${s.nodeId}|${s.name.replace(/^[^:]+: /, '')}`];
    if (reviewedColor) { p.c = reviewedColor; continue; }
    const twins = Object.keys(placements).filter(j => j !== i && placements[j].map === p.map && itemKey(sources[j]) === itemKey(s)).length;
    const identifiable = p.map === 'world' && POSITIONAL.test(s.name);
    p.c = twins > 0 && !identifiable ? 'y' : 'g';
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
