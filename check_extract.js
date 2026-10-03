const fs = require('fs');
const html = fs.readFileSync('game/rain-eave.html', 'utf8');
const m = html.match(/<script type="module">([\s\S]*?)<\/script>/);
if (!m) { console.log('NO MODULE SCRIPT'); process.exit(1); }
fs.writeFileSync(process.env.TEMP + '/rain_check.mjs', m[1], 'utf8');
console.log('extracted lines:', m[1].split('\n').length);
console.log('has replacement char U+FFFD:', m[1].includes('\uFFFD'));
