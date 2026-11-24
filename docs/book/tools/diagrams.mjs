// Sinh build/book/img/<id>.png cho mỗi spec trong docs/book/diagrams/*.json.
// Cột và khóa lấy từ build/ddl/create.sql (phải chạy suite ít nhất một lần).
import fs from 'node:fs';
import path from 'node:path';
import { BOOK_DIR, BUILD_DIR } from './lib/paths.mjs';
import { loadDdl } from './lib/ddl.mjs';
import { renderDiagram, svgToPng } from './lib/diagram.mjs';

const specDir = path.join(BOOK_DIR, 'diagrams');
const outDir = path.join(BUILD_DIR, 'img');
fs.mkdirSync(outDir, { recursive: true });

const tables = loadDdl();
const specs = fs.readdirSync(specDir).filter((file) => file.endsWith('.json'));
for (const file of specs) {
  const spec = JSON.parse(fs.readFileSync(path.join(specDir, file), 'utf8'));
  if (`${spec.id}.json` !== file) throw new Error(`${file}: id phải trùng tên file (${spec.id})`);
  fs.writeFileSync(path.join(outDir, `${spec.id}.png`), svgToPng(renderDiagram(spec, tables)));
}
console.log(`sơ đồ: ${specs.length} hình trong ${path.relative(process.cwd(), outDir)}`);
