// Ghi build/book/reference.docx (style của sách) từ reference.docx mặc định của pandoc.
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { BUILD_DIR, PANDOC } from './lib/paths.mjs';
import { buildReferenceDocx } from './lib/reference-docx.mjs';

if (!fs.existsSync(PANDOC)) throw new Error(`Chưa có pandoc ở ${PANDOC}: chạy docs/book/build.sh để tải`);
const result = spawnSync(PANDOC, ['--print-default-data-file', 'reference.docx']);
if (result.status !== 0) throw new Error(`pandoc lỗi: ${result.stderr}`);
const out = path.join(BUILD_DIR, 'reference.docx');
fs.writeFileSync(out, await buildReferenceDocx(result.stdout));
console.log(`reference.docx: ${path.relative(process.cwd(), out)}`);
