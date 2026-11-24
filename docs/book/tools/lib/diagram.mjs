// Vẽ sơ đồ bảng (SVG, rồi PNG) từ DDL thật: mỗi bảng một khung, cột có đánh dấu PK/FK/UQ,
// đường nối giữa bảng có khóa ngoại và bảng được trỏ tới, ghi "1" và "nhiều" bằng chữ.
import fs from 'node:fs';
import { Resvg } from '@resvg/resvg-js';
import { FONT_FILES } from './paths.mjs';

const MONO = 13;                 // cỡ chữ của dòng cột (DejaVu Sans Mono)
const CHAR = MONO * 0.6;         // bề rộng một ký tự monospace
const LINE = 20;
const HEADER = 28;
const PAD = 10;
const GAP_X = 120;
const GAP_Y = 80;
const MARGIN = 30;
const COLORS = { border: '#44546A', header: '#D9E2F3', text: '#1F1F1F', line: '#2E75B6', marker: '#C55A11' };

const escape = (text) => String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const sameSet = (a, b) => a.length === b.length && a.every((x) => b.includes(x));

function markersOf(table, column) {
  const markers = [];
  if (table.primaryKey.includes(column.name)) markers.push('PK');
  if (table.foreignKeys.some((fk) => fk.columns.includes(column.name))) markers.push('FK');
  const uniqueAlone = column.unique || table.uniques.some((u) => sameSet(u, [column.name]));
  if (uniqueAlone && !table.primaryKey.includes(column.name)) markers.push('UQ');
  return markers;
}

// Đầu phía khóa ngoại là "1" khi cột FK là duy nhất (OneToOne) hoặc chính là khóa chính (MapsId).
function isOneToOne(table, fk) {
  const uniqueColumns = fk.columns.length === 1 &&
    table.columns.find((c) => c.name === fk.columns[0])?.unique;
  return Boolean(uniqueColumns) || table.uniques.some((u) => sameSet(u, fk.columns)) ||
    sameSet(table.primaryKey, fk.columns);
}

function measure(table) {
  // Hibernate sắp cột theo thứ tự riêng; đưa cột khóa chính lên đầu cho quen mắt.
  const isPk = (column) => table.primaryKey.includes(column.name);
  const ordered = [...table.columns.filter(isPk), ...table.columns.filter((c) => !isPk(c))];
  const rows = ordered.map((column) => ({
    column,
    markers: markersOf(table, column),
    type: column.type + (column.notNull && !table.primaryKey.includes(column.name) ? ' not null' : ''),
  }));
  const markerWidth = Math.max(0, ...rows.map((r) => r.markers.length)) * 3;
  const nameWidth = Math.max(...rows.map((r) => r.column.name.length));
  const typeWidth = Math.max(...rows.map((r) => r.type.length));
  const width = Math.max(
    PAD * 2 + (markerWidth + nameWidth + 2 + typeWidth) * CHAR,
    PAD * 2 + `${table.schema}.${table.name}`.length * 8.5,
  );
  return { table, rows, markerWidth, nameWidth, width, height: HEADER + rows.length * LINE + PAD };
}

function clipToBox(box, towardX, towardY) {
  const cx = box.x + box.width / 2;
  const cy = box.y + box.height / 2;
  const dx = towardX - cx;
  const dy = towardY - cy;
  const scale = 1 / Math.max(Math.abs(dx) / (box.width / 2), Math.abs(dy) / (box.height / 2));
  return { x: cx + dx * scale, y: cy + dy * scale };
}

function label(text, x, y) {
  return `<text x="${x.toFixed(1)}" y="${y.toFixed(1)}" font-family="DejaVu Sans" font-size="13" ` +
    `font-weight="bold" fill="${COLORS.line}" text-anchor="middle">${escape(text)}</text>`;
}

function drawBox(box) {
  const { table, rows, markerWidth, nameWidth } = box;
  const parts = [
    `<rect x="${box.x}" y="${box.y}" width="${box.width}" height="${box.height}" fill="white" stroke="${COLORS.border}" stroke-width="1.5"/>`,
    `<rect x="${box.x}" y="${box.y}" width="${box.width}" height="${HEADER}" fill="${COLORS.header}" stroke="${COLORS.border}" stroke-width="1.5"/>`,
    `<text x="${box.x + PAD}" y="${box.y + 19}" font-family="DejaVu Sans" font-size="14" font-weight="bold" fill="${COLORS.text}">${escape(table.name)}</text>`,
  ];
  rows.forEach((row, i) => {
    const y = box.y + HEADER + LINE * (i + 1) - 5;
    row.markers.forEach((marker, m) => {
      parts.push(`<text x="${box.x + PAD + m * 3 * CHAR}" y="${y}" font-family="DejaVu Sans Mono" font-size="${MONO}" font-weight="bold" fill="${COLORS.marker}">${marker}</text>`);
    });
    const nameX = box.x + PAD + markerWidth * CHAR;
    parts.push(`<text x="${nameX}" y="${y}" font-family="DejaVu Sans Mono" font-size="${MONO}" fill="${COLORS.text}">${escape(row.column.name)}</text>`);
    parts.push(`<text x="${nameX + (nameWidth + 2) * CHAR}" y="${y}" font-family="DejaVu Sans Mono" font-size="${MONO}" fill="#595959">${escape(row.type)}</text>`);
  });
  return parts.join('\n');
}

function drawRelation(from, to, fk) {
  const fromLabel = isOneToOne(from.table, fk) ? '1' : 'nhiều';
  if (from === to) {
    // Khóa ngoại tự tham chiếu: vòng ở cạnh phải khung.
    const x = from.x + from.width;
    const y1 = from.y + HEADER + 10;
    const y2 = y1 + 40;
    return {
      line: `<path d="M ${x} ${y1} h 45 V ${y2} H ${x}" fill="none" stroke="${COLORS.line}" stroke-width="1.5"/>`,
      labels: [label(fromLabel, x + 22, y1 - 5), label('1', x + 22, y2 + 16)].join('\n'),
    };
  }
  const start = clipToBox(from, to.x + to.width / 2, to.y + to.height / 2);
  const end = clipToBox(to, from.x + from.width / 2, from.y + from.height / 2);
  const along = (t) => ({ x: start.x + (end.x - start.x) * t, y: start.y + (end.y - start.y) * t });
  const length = Math.hypot(end.x - start.x, end.y - start.y) || 1;
  const normal = { x: -(end.y - start.y) / length, y: (end.x - start.x) / length };
  const near = (t) => {
    const p = along(t);
    return { x: p.x + normal.x * 14, y: p.y + normal.y * 14 + 4 };
  };
  // Đặt nhãn cách mép khung một đoạn cố định (khoảng 50px) để không đè lên khung.
  const offset = Math.min(0.35, 50 / length);
  const a = near(offset);
  const b = near(1 - offset);
  return {
    line: `<line x1="${start.x.toFixed(1)}" y1="${start.y.toFixed(1)}" x2="${end.x.toFixed(1)}" y2="${end.y.toFixed(1)}" stroke="${COLORS.line}" stroke-width="1.5"/>`,
    labels: [label(fromLabel, a.x, a.y), label('1', b.x, b.y)].join('\n'),
  };
}

export function renderDiagram(spec, tables) {
  const boxes = new Map();
  for (const name of spec.tables) {
    const key = `${spec.schema}.${name}`;
    const table = tables.get(key);
    if (!table) throw new Error(`Không có bảng ${key} trong DDL (sơ đồ ${spec.id})`);
    boxes.set(key, measure(table));
  }
  const rows = spec.rows ?? Array.from({ length: Math.ceil(spec.tables.length / 3) },
    (_, i) => spec.tables.slice(i * 3, i * 3 + 3));
  let y = MARGIN;
  let width = 0;
  for (const row of rows) {
    let x = MARGIN;
    const rowBoxes = row.map((name) => boxes.get(`${spec.schema}.${name}`));
    for (const box of rowBoxes) {
      Object.assign(box, { x, y });
      x += box.width + GAP_X;
    }
    width = Math.max(width, x - GAP_X + MARGIN + 60);
    y += Math.max(...rowBoxes.map((b) => b.height)) + GAP_Y;
  }
  const height = y - GAP_Y + MARGIN + 20;
  const relations = [];
  for (const box of boxes.values()) {
    for (const fk of box.table.foreignKeys) {
      const target = boxes.get(fk.refTable);
      if (target) relations.push(drawRelation(box, target, fk));
    }
  }
  return [
    `<svg xmlns="http://www.w3.org/2000/svg" width="${Math.ceil(width)}" height="${Math.ceil(height)}" viewBox="0 0 ${Math.ceil(width)} ${Math.ceil(height)}">`,
    `<rect width="100%" height="100%" fill="white"/>`,
    // Thứ tự vẽ: đường nối, rồi khung (che phần đường nằm dưới khung), cuối cùng là nhãn (không bị khung che).
    ...relations.map((r) => r.line),
    ...[...boxes.values()].map(drawBox),
    ...relations.map((r) => r.labels),
    '</svg>',
  ].join('\n');
}

export function svgToPng(svg, fontFiles = FONT_FILES) {
  for (const file of fontFiles) {
    if (!fs.existsSync(file)) throw new Error(`Thiếu font: ${file} (cài gói fonts-dejavu-core)`);
  }
  const resvg = new Resvg(svg, {
    font: { fontFiles, loadSystemFonts: false, defaultFontFamily: 'DejaVu Sans' },
    fitTo: { mode: 'zoom', value: 3 },
    background: 'white',
  });
  return resvg.render().asPng();
}
