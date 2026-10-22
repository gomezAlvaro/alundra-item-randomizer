// One-off helper that wrote confidence.js from the manual review of the yellow markers (kept for reference).
const fs = require('fs');
const sources = JSON.parse(fs.readFileSync(__dirname + '/../../src/main/resources/data/item_source.json', 'utf8'));
const s = fs.readFileSync(__dirname + '/../../docs/v2/index.html', 'utf8');
const d = JSON.parse(s.match(/const D = (\{.*\});\nconst CAT/s)[1]);
// still unsure which of several identical chests this is, or the room itself is a guess
const stillYellow = new Set([38, 49, 50, 120, 121, 122, 124, 126, 129, 130, 131, 132, 147, 153, 154, 155, 169, 170, 178, 196, 197, 210, 218, 222, 223, 241, 273, 274, 275, 283, 284, 287]);
const out = {};
for (const [i, p] of Object.entries(d.placements)) {
    const src = sources[i];
    const key = `${src.nodeId}|${src.name.replace(/^[^:]+: /, '')}`;
    if (p.c === 'y' && !stillYellow.has(Number(i))) out[key] = 'g';
    if (stillYellow.has(Number(i))) out[key] = 'y';
}
fs.writeFileSync(__dirname + '/confidence.js', '// Manual review of the automatic yellow/green rule, keyed by "nodeId|source name". Overrides the rule in build.js.\nmodule.exports = ' + JSON.stringify(out, null, 4) + ';\n');
console.log(Object.keys(out).length, Object.values(out).filter(v => v === 'g').length + ' to green');
