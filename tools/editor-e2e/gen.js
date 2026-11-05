// Genera harness.html: carga el editor.js real en un iframe por caso y ejecuta cada herramienta.
// Uso: node gen.js <carpeta de build>. Con ONLY=<caso> ejecuta solo ese caso.
const fs = require('fs'), path = require('path');
const build = path.resolve(process.argv[2]);
const assets = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'assets', 'editor');
const js = fs.readFileSync(path.join(assets, 'editor.js'), 'utf8');
const css = fs.readFileSync(path.join(assets, 'editor.css'), 'utf8');
const fixture = fs.readFileSync(path.join(build, 'fixture.html'), 'utf8');
const sample = fs.readFileSync(path.join(build, 'sample.html'), 'utf8');
const mediaUrl = 'file:///' + path.join(build, 'media').replace(/\\/g, '/') + '/';
const runner = fs.readFileSync(path.join(__dirname, 'cases.js'), 'utf8');
const data = Buffer.from(JSON.stringify({ js, css, fixture, mediaUrl, sample, only: process.env.ONLY || null }), 'utf8').toString('base64');
fs.writeFileSync(path.join(build, 'harness.html'),
  '<!DOCTYPE html><meta charset="utf-8"><body><pre id="results"></pre><script>const DATA=JSON.parse(new TextDecoder().decode(Uint8Array.from(atob("' + data + '"),c=>c.charCodeAt(0))));</script><script>' + runner + '</script></body>');
