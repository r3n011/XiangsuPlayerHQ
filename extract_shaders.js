// 用正则从 HTML 里抠出所有 GLSL 字符串，送 glslangValidator 检查
const fs = require('fs');
const html = fs.readFileSync('game/rain-eave.html', 'utf8');
const re = /\/\* glsl \*\/\s*`([\s\S]*?)`/g;
let m, i = 0, out = [];
while ((m = re.exec(html))) {
  i++;
  const name = 'shader_' + String(i).padStart(2, '0') + '.frag';
  fs.writeFileSync(process.env.TEMP + '/' + name, m[1], 'utf8');
  out.push(name + '  (' + m[1].split('\n').length + ' lines)');
}
console.log('extracted ' + i + ' shaders:');
console.log(out.join('\n'));
