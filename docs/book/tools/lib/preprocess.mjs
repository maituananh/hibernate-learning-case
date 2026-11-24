// Mở rộng các khối đặc biệt mà người viết gõ trong docs/book/chapters/*.md thành Markdown pandoc hiểu:
// khung Tra cứu (sinh từ index), DDL (lấy từ create.sql), nhãn Cốt lõi/Mở rộng, các khung có style riêng.
import { REPO_URL } from './paths.mjs';
import { renderDdl } from './ddl.mjs';
import { DIV_CLOSE, DIV_OPEN, codesOf, parseAttributes, scanLines } from './markdown.mjs';

// class của người viết -> [tên paragraph style trong reference.docx, nhãn in đầu khung]
export const BOXES = {
  'ghi-nho': ['Ghi nho', 'Ghi nhớ'],
  'khac-du-doan': ['Ket qua khac du doan', 'Kết quả khác dự đoán'],
  'chua-kiem-chung': ['Chua kiem chung', 'Chưa kiểm chứng trong lab'],
  'kien-thuc-nen': ['Kien thuc nen', 'Kiến thức nền'],
};
const TIERS = { 'cot-loi': 'Cốt lõi', 'mo-rong': 'Mở rộng' };

const unique = (items) => [...new Set(items)];

function tracuuBlock(codes, ctx, location) {
  const entries = codes.map((code) => {
    const entry = ctx.index.get(code);
    if (!entry) throw new Error(`Mã tình huống không có trong index: ${code}`);
    ctx.locations.set(code, location);
    return entry;
  });
  const lines = ['::: {custom-style="Tra cuu"}', '**Tra cứu trong repository**', '', `Mã tình huống: ${codes.join(', ')}`, ''];
  for (const dir of unique(entries.flatMap((e) => e.entityPackages))) {
    lines.push(`Entity: [${dir}](${REPO_URL}/tree/main/${dir})`, '');
  }
  for (const file of unique(entries.map((e) => e.testFile))) {
    const methods = entries.filter((e) => e.testFile === file).map((e) => `\`${e.method}\``);
    lines.push(`Test: [${file}](${REPO_URL}/blob/main/${file})`, '', `Method: ${unique(methods).join(', ')}`, '');
  }
  const prs = unique(entries.map((e) => e.pr)).map((n) => `<${REPO_URL}/pull/${n}>`);
  lines.push(`Pull Request: ${prs.join(', ')}`, ':::');
  return lines;
}

function ddlBlock(attrs, ctx) {
  const tables = codesOf(attrs.tables).map((name) => {
    const key = `${attrs.schema}.${name}`;
    const table = ctx.ddl.get(key);
    if (!table) throw new Error(`Không có bảng ${key} trong DDL`);
    return table;
  });
  return ['```sql', renderDdl(tables), '```'];
}

export function preprocessChapter(md, ctx) {
  const lines = scanLines(md);
  const out = [];
  let location = '';
  for (let i = 0; i < lines.length; i++) {
    const { text, fence, delimiter } = lines[i];
    if (fence !== null || delimiter) {
      out.push(text);
      continue;
    }
    const heading = /^(#{2,4}) (\S+)(.*?)(?:\s*\{\.([\w-]+)\})?\s*$/.exec(text);
    if (heading) {
      const [, hashes, number, rest, cls] = heading;
      if (/^\d+(\.\d+)*$/.test(number)) location = number;
      out.push(`${hashes} ${number}${rest}`);
      if (TIERS[cls]) out.push('', '::: {custom-style="Nhan"}', TIERS[cls], ':::');
      continue;
    }
    const open = DIV_OPEN.exec(text);
    if (!open) {
      out.push(text);
      continue;
    }
    let close = i + 1;
    while (close < lines.length && !DIV_CLOSE.test(lines[close].text)) close++;
    const content = lines.slice(i + 1, close).map((line) => line.text);
    const { classes, attrs } = parseAttributes(open[1]);
    const cls = classes[0];
    if (cls === 'tracuu') out.push(...tracuuBlock(codesOf(attrs.codes), ctx, location));
    else if (cls === 'ddl') out.push(...ddlBlock(attrs, ctx));
    else if (cls === 'phu-luc-c') {
      if (ctx.appendixC === undefined) throw new Error('Phụ lục C phải được xử lý sau cùng (thiếu ctx.appendixC)');
      out.push(ctx.appendixC);
    } else if (BOXES[cls]) {
      const [style, label] = BOXES[cls];
      out.push(`::: {custom-style="${style}"}`, `**${label}**`, '', ...content, ':::');
    } else {
      out.push(...lines.slice(i, close + 1).map((line) => line.text));
    }
    i = close;
  }
  return out.join('\n');
}

export function buildAppendixC(index, locations) {
  const rows = [...index.values()].map((e) =>
    `| ${e.code} | ${locations.get(e.code) ?? ''} | ${e.testClass} | ${e.method} | #${e.pr} |`);
  return ['| Mã tình huống | Mục trong sách | Test class | Method | Pull Request |', '|---|---|---|---|---|', ...rows].join('\n');
}
