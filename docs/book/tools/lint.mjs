// Lint sách:  node tools/lint.mjs                 (mọi chương)
//             node tools/lint.mjs --chapter 04    (một chương, theo id trong chapters.json)
// In "file:dòng [rule] thông báo", thoát với mã 1 nếu có lỗi.
import { lintChapter, lintCoverage } from './lib/lint.mjs';
import { loadChapters, loadIndexMap, readChapter } from './lib/manifest.mjs';

const chapters = loadChapters();
const index = loadIndexMap();
const only = process.argv.includes('--chapter') ? process.argv[process.argv.indexOf('--chapter') + 1] : null;
if (only && !chapters.some((c) => c.id === only)) {
  console.error(`Không có chương id "${only}" trong chapters.json`);
  process.exit(2);
}

const contents = new Map();
const issues = [];
for (const chapter of chapters) {
  const md = readChapter(chapter);
  if (md === null) {
    issues.push({ file: chapter.file, line: 0, rule: 'missing-file', message: 'chưa có file chương' });
    continue;
  }
  contents.set(chapter.id, md);
  issues.push(...lintChapter(md, chapter, index));
}
issues.push(...lintCoverage(chapters, contents, index));

const selected = only
  ? issues.filter((i) => i.file === chapters.find((c) => c.id === only).file || i.file === 'chapters.json')
  : issues;
for (const issue of selected) console.log(`${issue.file}:${issue.line} [${issue.rule}] ${issue.message}`);
console.log(selected.length === 0 ? 'lint: không có lỗi' : `lint: ${selected.length} lỗi`);
process.exit(selected.length === 0 ? 0 : 1);
