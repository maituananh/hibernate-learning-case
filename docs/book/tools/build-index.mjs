// Ghi build/book/index.json: danh sách mọi mã tình huống có test.
import fs from 'node:fs';
import path from 'node:path';
import { REPO_ROOT, BUILD_DIR } from './lib/paths.mjs';
import { buildIndex } from './lib/scenario-index.mjs';

const index = buildIndex(REPO_ROOT, { onWarn: (message) => console.warn(`[cảnh báo] ${message}`) });
fs.mkdirSync(BUILD_DIR, { recursive: true });
fs.writeFileSync(path.join(BUILD_DIR, 'index.json'), JSON.stringify(index, null, 2));
console.log(`index.json: ${index.length} mã tình huống`);
