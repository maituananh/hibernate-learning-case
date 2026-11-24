// Đổi bảng ASCII mà db.print(...) in ra thành bảng Markdown (pandoc dựng thành bảng Word).
//   | customer_id | id |        | customer_id | id |
//   +-------------+----+   =>   |---|---|
//   | null        | 3  |        | NULL | 3 |
export function asciiToMarkdown(lines) {
  const rows = lines
    .map((line) => line.trim())
    .filter((line) => line.startsWith('|'))
    .map((line) => line.slice(1, -1).split('|').map((cell) => cell.trim()));
  // Bảng không có dòng nào: db.print chỉ in "(bảng trống)", không có header.
  if (rows.length === 0) return `*${lines.map((line) => line.trim()).join(' ')}*`;
  const [header, ...data] = rows;
  const render = (cells) => `| ${cells.join(' | ')} |`;
  return [
    render(header),
    `|${header.map(() => '---').join('|')}|`,
    ...data.map((cells) => render(cells.map((cell) => (cell === 'null' ? 'NULL' : cell)))),
  ].join('\n');
}
