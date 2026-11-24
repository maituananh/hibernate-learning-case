// Xử lý mọi chương theo thứ tự manifest, ghi ra build/book/md/<file> cho pandoc.
// Phụ lục C (bảng tra tình huống) xử lý sau cùng vì cần vị trí của mọi mã trong sách.
import fs from 'node:fs';
import path from 'node:path';
import { BUILD_DIR } from './lib/paths.mjs';
import { loadDdl } from './lib/ddl.mjs';
import { loadChapters, loadIndexMap, readChapter } from './lib/manifest.mjs';
import { buildAppendixC, preprocessChapter } from './lib/preprocess.mjs';

const outDir = path.join(BUILD_DIR, 'md');
fs.rmSync(outDir, { recursive: true, force: true });
fs.mkdirSync(outDir, { recursive: true });

const chapters = loadChapters();
const ctx = { index: loadIndexMap(), ddl: loadDdl(), locations: new Map() };
const isAppendixC = (chapter) => chapter.id === 'pl-c';

for (const chapter of [...chapters.filter((c) => !isAppendixC(c)), ...chapters.filter(isAppendixC)]) {
  const md = readChapter(chapter);
  if (md === null) throw new Error(`Thiếu file chương: docs/book/chapters/${chapter.file}`);
  if (isAppendixC(chapter)) ctx.appendixC = buildAppendixC(ctx.index, ctx.locations);
  try {
    fs.writeFileSync(path.join(outDir, chapter.file), preprocessChapter(md, ctx));
  } catch (error) {
    throw new Error(`${chapter.file}: ${error.message}`);
  }
}
console.log(`preprocess: ${chapters.length} file, ${ctx.locations.size} mã có vị trí trong sách`);
