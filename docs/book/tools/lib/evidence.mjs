// Tách bằng chứng từ stdout của một test: các khối [SQL] [DB] [LIFECYCLE] ... do helper của lab in ra,
// và log org.hibernate.SQL kèm giá trị bind (org.hibernate.orm.jdbc.bind).
import { formatSql } from './sql-format.mjs';
import { asciiToMarkdown } from './ascii-table.mjs';

const TAG = /^\[(SQL|DB|LIFECYCLE|DDL|LINT|EXCEPTION|BOOT-FAIL|BUDGET)\]\s?(.*)$/;
const LOG_LINE = /^\d{4}-\d{2}-\d{2}T/;

// Thân khối là các dòng thụt 2 khoảng trắng ngay sau dòng tag.
// Dòng log chen giữa (ví dụ JdbcTemplate khi SchemaInspector truy vấn) không cắt khối; riêng log kết thúc
// bằng ":" (org.hibernate.SQL) có thân nhiều dòng thụt lề phía sau, các dòng đó thuộc log chứ không thuộc khối.
export function parseBlocks(out) {
  const blocks = [];
  let current = null;
  let inMultiLineLog = false;
  let inBracketLog = false;   // JdbcTemplate: "statement [select ...\nfrom ...\n]" (SQL nhiều dòng trong ngoặc vuông)
  for (const line of out.split('\n')) {
    const tag = TAG.exec(line);
    if (inBracketLog) {
      if (line.trimEnd().endsWith(']')) inBracketLog = false;
      continue;
    }
    if (LOG_LINE.test(line)) {
      inMultiLineLog = line.trimEnd().endsWith(':');
      inBracketLog = /\[[^\]]*$/.test(line.slice(line.indexOf(' : ')));
      continue;
    }
    if (inMultiLineLog && line.startsWith(' ')) continue;
    inMultiLineLog = false;
    if (tag) {
      current = { tag: tag[1], header: tag[2], lines: [] };
      blocks.push(current);
    } else if (current && line.startsWith('  ')) {
      let body = line.slice(2);
      if (current.tag === 'SQL') body = body.replace(/\s+\(nextval:.*\)$/, '');
      current.lines.push(body);
    } else {
      current = null;
    }
  }
  return blocks;
}

// Mỗi câu Hibernate log ra nằm ở các dòng thụt lề sau "org.hibernate.SQL :", theo sau là các dòng bind.
export function parseHibernateSql(out) {
  const statements = [];
  let collecting = null;
  for (const line of out.split('\n')) {
    if (/org\.hibernate\.SQL\s+:\s*$/.test(line)) {
      collecting = { sql: '', binds: [] };
      statements.push(collecting);
      continue;
    }
    const bind = /binding parameter \((\d+:\w+)\) <- \[(.*)\]$/.exec(line);
    if (bind && statements.length > 0) {
      statements[statements.length - 1].binds.push(`(${bind[1]}) ${bind[2]}`);
      collecting = null;
      continue;
    }
    if (collecting && line.startsWith(' ') && !LOG_LINE.test(line)) {
      collecting.sql += ' ' + line.trim();
    } else {
      collecting = null;
    }
  }
  for (const statement of statements) {
    statement.sql = statement.sql.replace(/\s+/g, ' ').trim();
  }
  return statements;
}

function renderBlock(block) {
  if (block.tag === 'SQL') {
    const sql = block.lines.map((line) => formatSql(line)).join('\n\n');
    return `### [SQL] ${block.header}\n\n\`\`\`sql\n${sql}\n\`\`\``;
  }
  if (block.tag === 'DB') return `### [DB] ${block.header}\n\n${asciiToMarkdown(block.lines)}`;
  if (block.tag === 'LIFECYCLE') return `- [LIFECYCLE] ${block.header}`;
  return `\`\`\`text\n[${block.tag}] ${block.header}\n${block.lines.join('\n')}\n\`\`\``;
}

export function renderEvidence(entry, displayName, out, classFallback) {
  const parts = [`# ${displayName}`];
  if (entry) parts.push(`Test: \`${entry.testClass}#${entry.method}\``);
  if (out === null) {
    parts.push('Không có output ở cấp test (thường gặp ở test [BOOT-FAIL] hoặc test chạy song song).');
    if (classFallback) parts.push('Xem output cấp class trong thư mục `_class/` của evidence.');
    return parts.join('\n\n') + '\n';
  }
  parts.push('## Khối in ra từ test');
  parts.push(...parseBlocks(out).map(renderBlock));
  parts.push('## Toàn bộ SQL kèm giá trị bind');
  parseHibernateSql(out).forEach((statement, i) => {
    const binds = statement.binds.length > 0 ? `\n\nBind: ${statement.binds.join(', ')}` : '';
    parts.push(`### Câu ${i + 1}\n\n\`\`\`sql\n${formatSql(statement.sql)}\n\`\`\`${binds}`);
  });
  return parts.join('\n\n') + '\n';
}
