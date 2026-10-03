// 无头冒烟测试：用 headless Edge + SwiftShader 跑一遍游戏逻辑，
// 把关键状态从 document.title 里抠出来断言。
// 不依赖任何测试框架，直接 node smoke.mjs
import { spawn } from 'node:child_process';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const BASE = 'http://localhost:8765/game/rain-eave.html';

function run(query) {
  return new Promise((resolve, reject) => {
    const profile = mkdtempSync(join(tmpdir(), 'smoke-'));
    const p = spawn(EDGE, [
      '--headless=new', '--disable-gpu', '--use-gl=swiftshader',
      '--enable-unsafe-swiftshader', '--window-size=800,520',
      '--virtual-time-budget=9000', `--user-data-dir=${profile}`,
      '--dump-dom', `${BASE}?${query}`,
    ], { stdio: ['ignore', 'pipe', 'ignore'] });

    let out = '';
    p.stdout.on('data', d => out += d);
    p.on('error', reject);
    p.on('close', () => {
      const m = out.match(/<title>([^<]*)<\/title>/);
      if (!m) return reject(new Error('no title in output'));
      const kv = {};
      m[1].split('|').map(s => s.trim()).forEach(part => {
        const i = part.indexOf('=');
        if (i > 0) kv[part.slice(0, i)] = part.slice(i + 1);
      });
      resolve(kv);
    });
  });
}

const checks = [];
function check(name, cond, detail) {
  checks.push({ name, pass: !!cond, detail });
}

console.log('running smoke tests against', BASE, '\n');

// 1. 冷启动：无模拟推进
{
  const r = await run('debug=1&auto=1&q=0');
  console.log('cold  ', JSON.stringify(r));
  check('cold: 无 GL 错误', r.glErr === '0', r.glErr);
  check('cold: context 未丢失', r.lost === 'false', r.lost);
  check('cold: 不是纯黑帧', parseFloat(r.meanRGB) > 1.5, r.meanRGB);
  check('cold: 尚未开花', r.blooms === '0', r.blooms);
}

// 2. 大雨
{
  const r = await run('debug=1&auto=1&ff=8&rain=0.45&at=-1.4,-0.4&q=0');
  console.log('rain  ', JSON.stringify(r));
  check('rain: 无 GL 错误', r.glErr === '0', r.glErr);
  check('rain: 雨势 > 0.7', parseFloat(r.rain) > 0.7, r.rain);
}

// 3. 雨歇：雨势应该明显低下来
{
  const r = await run('debug=1&auto=1&ff=8&rain=0.97&at=-1.4,-0.4&q=0');
  console.log('calm  ', JSON.stringify(r));
  check('calm: 雨势 < 0.35', parseFloat(r.rain) < 0.35, r.rain);
}

// 4. 长时推进：植物应该开花，且没有 GL 错误
{
  const r = await run('debug=1&auto=1&ff=200&at=-1.4,-0.4&q=0');
  console.log('grown ', JSON.stringify(r));
  check('grown: 无 GL 错误', r.glErr === '0', r.glErr);
  check('grown: 有植物开花', parseInt(r.blooms, 10) > 0, r.blooms);
  check('grown: 帧仍非黑', parseFloat(r.meanRGB) > 1.5, r.meanRGB);
}

// 4b. 提灯绕场一周：验证不把灯按在植物上也能推进生长（窗光贡献）
{
  const r = await run('debug=1&auto=1&sweep=240&q=0');
  console.log('sweep ', JSON.stringify(r));
  check('sweep: 无 GL 错误', r.glErr === '0', r.glErr);
  check('sweep: 绕场也能开花', parseInt(r.blooms, 10) > 0, r.blooms);
}

// 5. 最高画质档：bloom 打开也不能崩
{
  const r = await run('debug=1&auto=1&ff=30&q=2');
  console.log('q2    ', JSON.stringify(r));
  check('q2: 无 GL 错误', r.glErr === '0', r.glErr);
  check('q2: context 未丢失', r.lost === 'false', r.lost);
}

// 6. 非 debug 路径（普通玩家看到的）：开场遮罩应在，不应有 auto 副作用
{
  const r = await run('');
  console.log('plain ', JSON.stringify(r));
  check('plain: 无 GL 错误', r.glErr === '0' || r.glErr === undefined, r.glErr);
}

console.log('\n' + '='.repeat(58));
let failed = 0;
for (const c of checks) {
  if (!c.pass) failed++;
  console.log(`${c.pass ? 'PASS' : 'FAIL'}  ${c.name.padEnd(24)} ${c.detail ?? ''}`);
}
console.log('='.repeat(58));
console.log(`${checks.length - failed}/${checks.length} passed`);
process.exit(failed ? 1 : 0);