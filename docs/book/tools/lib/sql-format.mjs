// Định dạng SQL một dòng (như SqlCaptor in ra) thành vài dòng dễ đọc, mỗi dòng tối đa `width` ký tự.
// Chỉ ngắt trước các mệnh đề ở độ sâu ngoặc 0; subquery trong ngoặc giữ nguyên trên một dòng.

const CLAUSES = [
  'for no key update', 'for update', 'for share', 'fetch first',
  'left join', 'right join', 'inner join', 'cross join', 'full join', 'join',
  'order by', 'group by', 'union all', 'union',
  'from', 'where', 'values', 'set', 'having', 'limit', 'offset', 'returning',
];

function startsClause(sql, index) {
  if (sql[index - 1] !== ' ') return null;
  const rest = sql.slice(index).toLowerCase();
  return CLAUSES.find((clause) => rest.startsWith(clause) && !/[\w.]/.test(rest[clause.length] ?? ' ')) ?? null;
}

function splitClauses(sql) {
  const lines = [];
  let depth = 0;
  let quoted = false;
  let current = '';
  for (let i = 0; i < sql.length; i++) {
    const ch = sql[i];
    if (ch === "'") quoted = !quoted;
    if (!quoted && ch === '(') depth++;
    if (!quoted && ch === ')') depth--;
    const clause = !quoted && depth === 0 ? startsClause(sql, i) : null;
    if (clause) {
      lines.push(current.trimEnd());
      // Nhảy qua cả cụm từ khóa để "join" trong "left join" không bị ngắt lần nữa.
      current = sql.slice(i, i + clause.length);
      i += clause.length - 1;
      continue;
    }
    current += ch;
  }
  lines.push(current.trimEnd());
  return lines.filter((line) => line !== '');
}

// Dòng quá dài: ngắt sau dấu phẩy, dòng tiếp theo thụt 4 khoảng trắng.
function wrap(line, width) {
  if (line.length <= width) return [line];
  const pieces = line.split(', ');
  const result = [];
  let current = pieces[0];
  for (const piece of pieces.slice(1)) {
    if ((current + ', ' + piece).length > width) {
      result.push(current + ',');
      current = '    ' + piece;
    } else {
      current += ', ' + piece;
    }
  }
  result.push(current);
  return result;
}

export function formatSql(sql, width = 85) {
  const normalized = sql.replace(/\s+/g, ' ').replace(/,\s*/g, ', ').trim();
  return splitClauses(normalized).flatMap((line) => wrap(line, width)).join('\n');
}
