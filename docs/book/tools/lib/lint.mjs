// Kiểm tra từng chương theo quy tắc của sách (spec mục 7, 8, 12).
// Mỗi lỗi: { file, line, rule, message }.
import { expandRange } from './codes.mjs';
import { DIV_OPEN, codesOf, parseAttributes, scanLines } from './markdown.mjs';

const MAX_CODE_WIDTH = 85;
const BANNED_CHAR = /[\u{1F300}-\u{1FAFF}☀-➿⭐←-⇿×≠≤≥⌈⌉§]/u;
const BANNED_TOKENS = [
  [/\bPC\b/, 'viết tắt PC (viết "persistence context")'],
  [/T\/M\/D\/R/, 'viết tắt T/M/D/R (viết tên trạng thái đầy đủ)'],
  [/\[(BOOT-FAIL|LIFECYCLE|FLUSH)\]/, 'tag nội bộ của lab'],
  [/\b(V[1-7](-NN|-OPT)?|C[0-8](-DEEP)?|O[0-2]|U[0-5]|F[0-4]S?|M[1-9]|J[1-4])\b/, 'mã biến thể (mô tả mapping bằng lời)'],
  [/\bG\d+-/, 'mã tình huống ngoài khung Tra cứu'],
];
const CORE_LABELS = ['**Bối cảnh.**', '**Khi nào xảy ra.**', '**Khi nào không xảy ra.**', '**Ví dụ.**', '**Kết quả.**', '**Vì sao.**'];
const EXTRA_LABELS = ['**Bối cảnh.**', '**Kết quả.**'];
const VARIANT_LABELS = ['**Ưu điểm.**', '**Nhược điểm.**', '**Nên dùng khi.**'];

const HEADING = /^(#{1,6}) (.*?)(?:\s*\{\.([\w-]+)\})?\s*$/;

function headingsOf(lines) {
  const headings = [];
  lines.forEach((line, i) => {
    if (line.fence !== null || line.delimiter) return;
    const match = HEADING.exec(line.text);
    if (match) headings.push({ index: i, level: match[1].length, text: match[2], cls: match[3] ?? null, lineNo: line.lineNo });
  });
  return headings;
}

// Nội dung của heading: từ heading tới heading kế tiếp có cấp nhỏ hơn hoặc bằng.
function sectionText(lines, headings, h) {
  const next = headings.find((other) => other.index > h.index && other.level <= h.level);
  return lines.slice(h.index, next ? next.index : lines.length).map((l) => l.text).join('\n');
}

const divCodes = (text, cls) => [...text.matchAll(/^:::+\s*\{([^}]*)\}\s*$/gm)]
  .map((m) => parseAttributes(m[1]))
  .filter((a) => cls === undefined || a.classes[0] === cls)
  .flatMap((a) => codesOf(a.attrs.codes));

function expectedTitle(chapter) {
  if (chapter.kind === 'chapter') return { level: 2, text: `Chương ${chapter.number}. ${chapter.title}` };
  if (chapter.kind === 'appendix') return { level: 2, text: `Phụ lục ${chapter.letter}. ${chapter.title}` };
  if (chapter.kind === 'part') return { level: 1, text: chapter.title };
  return null;
}

export function lintChapter(md, chapter, index) {
  const issues = [];
  const add = (line, rule, message) => issues.push({ file: chapter.file, line, rule, message });
  const lines = scanLines(md);
  const headings = headingsOf(lines);

  for (const line of lines) {
    if (line.delimiter) continue;
    if (line.fence !== null) {
      if (line.fence.startsWith('{=')) continue;   // raw OpenXML
      if (line.text.includes('\t')) add(line.lineNo, 'code-tab', 'code có ký tự tab (đổi sang 4 khoảng trắng)');
      if ([...line.text].length > MAX_CODE_WIDTH) add(line.lineNo, 'code-width', `dòng code dài ${[...line.text].length} ký tự (tối đa ${MAX_CODE_WIDTH})`);
      continue;
    }
    if (BANNED_CHAR.test(line.text)) add(line.lineNo, 'banned-char', `ký tự cấm: ${BANNED_CHAR.exec(line.text)[0]}`);
    if (!chapter.allowCodes) {
      const prose = line.text.replace(/([\w-]+)="[^"]*"/g, '');
      for (const [pattern, what] of BANNED_TOKENS) {
        if (pattern.test(prose)) add(line.lineNo, 'banned-token', `${what}: ${pattern.exec(prose)[0]}`);
      }
    }
  }

  for (const h of headings) {
    if (/[@`]|G\d+-S/.test(h.text)) add(h.lineNo, 'heading-chars', `tiêu đề có @, backtick hoặc mã tình huống: ${h.text}`);
  }

  const expected = expectedTitle(chapter);
  if (expected) {
    const title = headings.find((h) => h.level === expected.level);
    if (!title || title.text !== expected.text) {
      add(title?.lineNo ?? 1, 'title-match', `tiêu đề phải là "${expected.text}"`);
    }
  }

  if (chapter.kind === 'chapter') {
    let section = 0;
    let scenario = 0;
    for (const h of headings) {
      if (h.level === 3) {
        section++;
        scenario = 0;
        if (!h.text.startsWith(`${chapter.number}.${section} `)) {
          add(h.lineNo, 'numbering', `mục phải đánh số ${chapter.number}.${section}: ${h.text}`);
        }
      }
      if (h.level === 4) {
        scenario++;
        if (!h.text.startsWith(`${chapter.number}.${section}.${scenario} `)) {
          add(h.lineNo, 'numbering', `tình huống phải đánh số ${chapter.number}.${section}.${scenario}: ${h.text}`);
        }
      }
    }
  }

  for (const h of headings.filter((x) => x.level === 4)) {
    const text = sectionText(lines, headings, h);
    if (h.cls !== 'cot-loi' && h.cls !== 'mo-rong') {
      add(h.lineNo, 'template', `tình huống thiếu {.cot-loi} hoặc {.mo-rong}: ${h.text}`);
      continue;
    }
    const required = [...(h.cls === 'cot-loi' ? CORE_LABELS : EXTRA_LABELS), '::: {.ghi-nho}', '::: {.tracuu'];
    const missing = required.filter((label) => !text.includes(label));
    if (missing.length > 0) add(h.lineNo, 'template', `thiếu ${missing.join(', ')}: ${h.text}`);
    const tiers = divCodes(text, 'tracuu').map((code) => index.get(code)?.tier).filter(Boolean);
    if (h.cls === 'mo-rong' && tiers.includes('core')) add(h.lineNo, 'tier-label', `có mã Cốt lõi nhưng gắn {.mo-rong}: ${h.text}`);
    if (h.cls === 'cot-loi' && tiers.length > 0 && tiers.every((t) => t === 'extra')) {
      add(h.lineNo, 'tier-label', `mọi mã đều Mở rộng nhưng gắn {.cot-loi}: ${h.text}`);
    }
  }

  for (const h of headings.filter((x) => x.level === 3 && x.cls === 'bien-the')) {
    const text = sectionText(lines, headings, h);
    const missing = VARIANT_LABELS.filter((label) => !text.includes(label));
    if (!/!\[[^\]]*\]\([^)]+\)/.test(text)) missing.push('hình sơ đồ');
    if (missing.length > 0) add(h.lineNo, 'variant-section', `mục biến thể thiếu ${missing.join(', ')}: ${h.text}`);
  }
  return issues;
}

// contents: Map<id chương, Markdown>. Chỉ kiểm phủ mã cho chương status "done";
// kiểm "mỗi mã thuộc đúng một chương" trên cả manifest.
export function lintCoverage(chapters, contents, index) {
  const issues = [];
  const ownersOf = new Map();
  for (const chapter of chapters) {
    for (const base of (chapter.scenarios ?? []).flatMap(expandRange)) {
      ownersOf.set(base, [...(ownersOf.get(base) ?? []), chapter]);
    }
  }
  for (const entry of index.values()) {
    const owners = ownersOf.get(entry.base) ?? [];
    if (owners.length !== 1) {
      issues.push({ file: 'chapters.json', line: 0, rule: 'coverage',
        message: `mã ${entry.code} thuộc ${owners.length} chương (phải đúng 1)` });
    }
  }
  for (const chapter of chapters.filter((c) => c.status === 'done')) {
    const md = contents.get(chapter.id) ?? '';
    const add = (message) => issues.push({ file: chapter.file, line: 0, rule: 'coverage', message });
    const bases = new Set((chapter.scenarios ?? []).flatMap(expandRange));
    const owned = [...index.values()].filter((e) => bases.has(e.base));
    const inTracuu = new Set(divCodes(md, 'tracuu'));
    const inKhac = new Set(divCodes(md, 'khac-du-doan'));
    const anyCodes = new Set(divCodes(md));
    for (const entry of owned) {
      if (!inTracuu.has(entry.code)) add(`mã ${entry.code} thuộc chương này nhưng chưa có trong khung Tra cứu`);
      if (entry.differs && !inKhac.has(entry.code)) add(`mã ${entry.code} khác dự đoán nhưng chưa có khối khac-du-doan`);
    }
    for (const code of inTracuu) {
      if (!index.has(code)) add(`mã ${code} không có trong index`);
      else if (!bases.has(index.get(code).base)) add(`mã ${code} thuộc chương khác`);
    }
    for (const code of chapter.extra ?? []) {
      if (!anyCodes.has(code)) add(`mã ${code} (extra) chưa nằm trong thuộc tính codes của khung nào`);
    }
  }
  return issues;
}
