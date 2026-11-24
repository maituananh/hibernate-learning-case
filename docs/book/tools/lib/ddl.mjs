// Đọc DDL Hibernate sinh ra (build/ddl/create.sql) và in lại DDL gọn cho sách.
// create.sql bị ghi lặp lại mỗi lần một SessionFactory khởi động, nên chỉ giữ lần xuất hiện đầu tiên.
import fs from 'node:fs';
import path from 'node:path';
import { REPO_ROOT } from './paths.mjs';

const CREATE_TABLE = /create table (\S+) \(\n([\s\S]*?)\n\s*\);/g;
const FOREIGN_KEY = new RegExp(
  'alter table if exists (\\S+)\\s+add constraint (\\S+)\\s+foreign key \\(([^)]+)\\)\\s+' +
  'references ([^\\s;(]+)(?:\\s*\\(([^)]+)\\))?(?:\\s+on delete (cascade|set null|set default|restrict|no action))?\\s*;',
  'g');
const UNIQUE_CONSTRAINT = /alter table if exists (\S+)\s+add constraint (\S+) unique \(([^)]+)\);/g;
const TYPE_END = /\s+(?:not null|null|unique|check|default|generated)\b/;

const list = (text) => text.split(',').map((item) => item.trim());

function parseColumn(line) {
  const [name] = line.split(/\s+/, 1);
  const definition = line.slice(name.length).trim();
  const withoutCheck = definition.replace(/check\s*\(.*\)/, '');
  const typeEnd = TYPE_END.exec(' ' + definition);
  return {
    name,
    definition,
    type: (typeEnd ? (' ' + definition).slice(0, typeEnd.index) : ' ' + definition).trim(),
    notNull: /\bnot null\b/.test(withoutCheck),
    unique: /\bunique\b/.test(withoutCheck),
  };
}

function parseTable(key, body) {
  const [schema, name] = key.split('.');
  const table = {
    schema, name, columns: [], primaryKey: [], uniques: [], foreignKeys: [],
    bodyConstraints: [], uniqueConstraints: [],
  };
  for (const rawLine of body.split('\n')) {
    const line = rawLine.trim().replace(/,$/, '');
    if (line === '') continue;
    const primaryKey = /^primary key \((.+)\)$/.exec(line);
    const unique = /^unique \((.+)\)$/.exec(line);
    if (primaryKey) {
      table.primaryKey = list(primaryKey[1]);
      table.bodyConstraints.push(line);
    } else if (unique) {
      table.uniques.push(list(unique[1]));
      table.bodyConstraints.push(line);
    } else {
      table.columns.push(parseColumn(line));
    }
  }
  return table;
}

export function parseDdl(...sqlTexts) {
  const tables = new Map();
  for (const text of sqlTexts) {
    for (const match of text.matchAll(CREATE_TABLE)) {
      if (!tables.has(match[1])) tables.set(match[1], parseTable(match[1], match[2]));
    }
  }
  for (const text of sqlTexts) {
    for (const match of text.matchAll(FOREIGN_KEY)) {
      const table = tables.get(match[1]);
      if (!table || table.foreignKeys.some((fk) => fk.name === match[2])) continue;
      table.foreignKeys.push({
        name: match[2],
        columns: list(match[3]),
        refTable: match[4],
        refColumns: match[5] ? list(match[5]) : [],
        onDelete: match[6] ?? null,
      });
    }
    for (const match of text.matchAll(UNIQUE_CONSTRAINT)) {
      const table = tables.get(match[1]);
      if (!table || table.uniqueConstraints.some((u) => u.name === match[2])) continue;
      table.uniqueConstraints.push({ name: match[2], columns: list(match[3]) });
      table.uniques.push(list(match[3]));
    }
  }
  return tables;
}

export function loadDdl() {
  const files = ['build/ddl/create.sql', 'lab-enhanced/build/ddl/create.sql']
    .map((rel) => path.join(REPO_ROOT, rel))
    .filter((file) => fs.existsSync(file));
  return parseDdl(...files.map((file) => fs.readFileSync(file, 'utf8')));
}

// Dòng quá 85 ký tự (thường là check constraint dài) ngắt sau dấu phẩy, thụt thêm 4.
function wrap(line, indent, width = 85) {
  if (line.length <= width) return [line];
  const cut = line.lastIndexOf(', ', width - 1);
  if (cut <= indent.length) return [line];
  return [line.slice(0, cut + 1), ...wrap(indent + '    ' + line.slice(cut + 2), indent + '    ', width)];
}

function renderCreate(table) {
  const body = [
    ...table.columns.map((c) => `${c.name} ${c.definition}`),
    ...table.bodyConstraints,
  ].flatMap((line, i, all) => wrap(`    ${line}${i < all.length - 1 ? ',' : ''}`, '    '));
  return [`create table ${table.schema}.${table.name} (`, ...body, ');'].join('\n');
}

function renderConstraints(table) {
  const key = `${table.schema}.${table.name}`;
  const foreignKeys = table.foreignKeys.map((fk) => [
    `alter table ${key}`,
    `    add constraint ${fk.name}`,
    `    foreign key (${fk.columns.join(', ')})`,
    `    references ${fk.refTable}${fk.refColumns.length > 0 ? ` (${fk.refColumns.join(', ')})` : ''}` +
      (fk.onDelete ? `\n    on delete ${fk.onDelete}` : '') + ';',
  ].join('\n'));
  const uniques = table.uniqueConstraints.map((u) =>
    `alter table ${key}\n    add constraint ${u.name}\n    unique (${u.columns.join(', ')});`);
  return [...uniques, ...foreignKeys];
}

export function renderDdl(tables) {
  return [...tables.map(renderCreate), ...tables.flatMap(renderConstraints)].join('\n\n');
}
