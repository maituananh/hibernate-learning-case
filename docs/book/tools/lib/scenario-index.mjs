// Index tình huống: mỗi @DisplayName("G<N>-S<NN> | ...") của test là một mã.
// Từ mã biết được test class, method, package entity, PR, tình huống Cốt lõi hay Mở rộng,
// và kết quả có khác dự đoán không (theo docs/RESULTS.md).
import fs from 'node:fs';
import path from 'node:path';
import { parseCode, groupOf } from './codes.mjs';

const TEST_ROOTS = ['src/test/java', 'lab-enhanced/src/test/java'];
const SOURCE_ROOTS = [
  'src/main/java', 'src/test/java', 'lab-enhanced/src/main/java', 'lab-enhanced/src/test/java',
];

function javaFiles(dir) {
  if (!fs.existsSync(dir)) return [];
  const found = [];
  for (const item of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, item.name);
    if (item.isDirectory()) found.push(...javaFiles(full));
    else if (item.name.endsWith('.java')) found.push(full);
  }
  return found;
}

function unescapeJava(text) {
  return text.replace(/\\u([0-9a-fA-F]{4})|\\(.)/g, (_, hex, ch) => {
    if (hex) return String.fromCharCode(parseInt(hex, 16));
    return { n: '\n', t: '\t', '"': '"', '\\': '\\' }[ch] ?? ch;
  });
}

function entityPackagesOf(source, repoRoot) {
  const dirs = new Set();
  const importLine = /^import\s+(com\.example\.hibernatelab\.[\w.]+)\.(?:\*|[A-Z]\w*);/gm;
  for (const match of source.matchAll(importLine)) {
    const pkg = match[1];
    if (pkg.includes('.support')) continue;
    const pkgPath = pkg.replaceAll('.', '/');
    for (const root of SOURCE_ROOTS) {
      if (fs.existsSync(path.join(repoRoot, root, pkgPath))) dirs.add(`${root}/${pkgPath}/`);
    }
  }
  return [...dirs].sort();
}

export function scanTests(repoRoot) {
  const entries = [];
  const displayName = /@DisplayName\("((?:[^"\\]|\\.)*)"\)/g;
  for (const root of TEST_ROOTS) {
    for (const file of javaFiles(path.join(repoRoot, root))) {
      const source = fs.readFileSync(file, 'utf8');
      const testFile = path.relative(repoRoot, file).split(path.sep).join('/');
      const entityPackages = entityPackagesOf(source, repoRoot);
      for (const match of source.matchAll(displayName)) {
        const name = unescapeJava(match[1]);
        const parsed = parseCode(name);
        if (!parsed) continue;
        const rest = source.slice(match.index + match[0].length);
        const method = /void\s+(\w+)\s*\(/.exec(rest);
        const classDecl = /\bclass\s+\w+/.exec(rest);
        // @DisplayName gắn trên class (không phải method) thì bỏ qua.
        if (!method || (classDecl && classDecl.index < method.index)) continue;
        entries.push({
          ...parsed,
          displayName: name,
          testClass: path.basename(file, '.java'),
          testFile,
          method: method[1],
          entityPackages,
        });
      }
    }
  }
  return entries.sort((a, b) => compareCodes(a.code, b.code));
}

// So sánh tự nhiên: G2 trước G10, S2 trước S10.
export function compareCodes(a, b) {
  const tokens = (code) => code.match(/\d+|\D+/g);
  const ta = tokens(a);
  const tb = tokens(b);
  for (let i = 0; i < Math.max(ta.length, tb.length); i++) {
    if (ta[i] === undefined) return -1;
    if (tb[i] === undefined) return 1;
    const na = Number(ta[i]);
    const nb = Number(tb[i]);
    const diff = Number.isNaN(na) || Number.isNaN(nb) ? ta[i].localeCompare(tb[i]) : na - nb;
    if (diff !== 0) return diff;
  }
  return 0;
}

// ⭐ = core, ➕ = extra. Có hai dạng: "**G0-S01 ⭐ ..." (G0–G9) và "| G10-S01 ⭐ |" (G10–G14).
export function readTiers(markdown) {
  const tiers = new Map();
  const patterns = [
    /^\*\*(G\d+-S\d+) (⭐|➕)/gm,
    /^\| (G\d+(?:-[A-Z]\d+)?(?:-S\d+)?) (⭐|➕)/gm,
  ];
  for (const pattern of patterns) {
    for (const match of markdown.matchAll(pattern)) {
      tiers.set(match[1], match[2] === '⭐' ? 'core' : 'extra');
    }
  }
  return tiers;
}

export function tierFor(base, tiers) {
  return tiers.get(base) ?? tiers.get(base.replace(/-S\d+$/, '')) ?? null;
}

// Dòng RESULTS.md có cột "Khớp?" (áp chót) chứa KHÁC hoặc "một nửa".
// "G1-S04d/e" mở thành G1-S04d và G1-S04e.
export function readDiffers(markdown) {
  const differs = new Set();
  for (const line of markdown.split('\n')) {
    if (!line.startsWith('| G')) continue;
    const cells = line.split('|');
    const matchCell = cells[cells.length - 3] ?? '';
    if (!/KHÁC|một nửa/.test(matchCell)) continue;
    const raw = cells[1].replace(/[`*]/g, '').trim();
    const code = /^(G\d+(?:-[IR]\d+)?-S\d+)([a-z](?:\/[a-z])*)?$/.exec(raw);
    if (!code) continue;
    if (!code[2]) {
      differs.add(code[1]);
      continue;
    }
    for (const letter of code[2].split('/')) differs.add(code[1] + letter);
  }
  return differs;
}

const PR_BY_GROUP = {
  G0: 2, G1: 3, G2: 4, G3: 5, G4: 6, G5: 7, G6: 8, G7: 9, G8: 10, G9: 11,
  G10: 14, G11: 15, G12: 16, G13: 17, G14: 19,
};

export function prFor(code) {
  if (code.startsWith('G0-S01')) return 1;   // golden test nằm ở PR hạ tầng
  const group = groupOf(code);
  if (group === 'G13' && Number(/-S(\d+)/.exec(code)[1]) >= 11) return 18;  // lab-enhanced
  return PR_BY_GROUP[group];
}

export function buildIndex(repoRoot, { onWarn = () => {} } = {}) {
  const read = (rel) => fs.readFileSync(path.join(repoRoot, rel), 'utf8');
  const tiers = readTiers(read('docs/02-SCENARIOS.md'));
  const differs = readDiffers(read('docs/RESULTS.md'));
  return scanTests(repoRoot).map((entry) => {
    let tier = tierFor(entry.base, tiers);
    if (!tier) {
      onWarn(`Không tìm thấy ⭐/➕ cho ${entry.code} trong 02-SCENARIOS, tạm coi là Cốt lõi`);
      tier = 'core';
    }
    return {
      ...entry,
      tier,
      differs: differs.has(entry.code) || differs.has(entry.base),
      pr: prFor(entry.code),
    };
  });
}
