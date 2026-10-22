// Generates docs/world_map.html: an interactive map of the logic graph with every item source (chest, shop, reward).
// Usage: node scripts/gen_world_map.js
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const dataDir = path.join(root, 'src/main/resources/data');
const load = f => JSON.parse(fs.readFileSync(path.join(dataDir, f), 'utf8'));

const data = {
    nodes: load('world_node.json'),
    sources: load('item_source.json'),
    paths: load('world_path.json'),
    regions: load('world_region.json'),
};

const html = fs.readFileSync(path.join(__dirname, 'world_map.template.html'), 'utf8')
    .replace('/*DATA*/null', () => JSON.stringify(data));

fs.mkdirSync(path.join(root, 'docs'), { recursive: true });
fs.writeFileSync(path.join(root, 'docs/world_map.html'), html);
console.log('Wrote docs/world_map.html');
