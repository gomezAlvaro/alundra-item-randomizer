// Builds docs/v3/index.html: the seed tracker. Reads the shared atlas pins written by scripts/v2/build.js.
// Usage: node scripts/v2/build.js && node scripts/v3/build.js
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '../..');
const read = f => fs.readFileSync(f, 'utf8');
const dataDir = path.join(root, 'src/main/resources/data');

const atlas = JSON.parse(read(path.join(root, 'docs/data/atlas.json')));
const world = {
    paths: JSON.parse(read(path.join(dataDir, 'world_path.json'))),
    items: JSON.parse(read(path.join(dataDir, 'item.json'))).map(i => ({ name: i.name, slot: i.slot, tier: i.tier })),
};

const html = read(path.join(__dirname, 'template.html'))
    .replace('/*ATLAS*/null', () => JSON.stringify(atlas))
    .replace('/*WORLD*/null', () => JSON.stringify(world))
    .replace('/*LOGIC*/', () => read(path.join(__dirname, 'logic.js')));

fs.mkdirSync(path.join(root, 'docs/v3'), { recursive: true });
fs.writeFileSync(path.join(root, 'docs/v3/index.html'), html);
console.log('Wrote docs/v3/index.html');
