// Finds, for every item source stored as a chest entity inside DATAS.BIN, the story flag the game sets when the chest
// is opened (the 16-bit value stored right before the item byte). Item sources given by scripts (shops, trades, NPC
// rewards) have no such flag and are left out.
// Usage: node scripts/ap/extract_flags.js <DATAS.BIN extracted from the disc>  -> writes scripts/ap/chest_flags.json
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '../..');
const datas = fs.readFileSync(process.argv[2]);
const sources = JSON.parse(fs.readFileSync(path.join(root, 'src/main/resources/data/item_source.json'), 'utf8'));
const SCRIPT_GIVE_ITEM_OPCODE = 0x82;

const flags = {};
const skipped = [];
for (const source of sources) {
    if (!source.address) { skipped.push(source.name); continue; }
    const addresses = [].concat(source.address).map(a => parseInt(a, 16));
    const values = addresses.map(a => (datas[a - 1] === SCRIPT_GIVE_ITEM_OPCODE ? 0 : datas.readUInt16LE(a - 2)));
    if (values.some(v => v === 0) || new Set(values).size > 1) { skipped.push(source.name); continue; }
    flags[source.name] = values[0];
}
const seen = new Map();
for (const [name, flag] of Object.entries(flags)) {
    if (seen.has(flag)) throw new Error(`Flag ${flag.toString(16)} used by both '${name}' and '${seen.get(flag)}'`);
    seen.set(flag, name);
}
fs.writeFileSync(path.join(__dirname, 'chest_flags.json'), JSON.stringify(flags, null, 1) + '\n');
console.log(`${Object.keys(flags).length} sources with a flag, ${skipped.length} skipped:`);
for (const name of skipped) console.log('  ' + name);
