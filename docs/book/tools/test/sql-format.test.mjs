import { test } from 'node:test';
import assert from 'node:assert/strict';
import { formatSql } from '../lib/sql-format.mjs';

test('insert: cột và values mỗi phần một dòng', () => {
  assert.equal(formatSql('insert into s.t (a,b,id) values (?,?,?)'),
    'insert into s.t (a, b, id)\nvalues (?, ?, ?)');
});

test('select, left join, where mỗi mệnh đề một dòng', () => {
  assert.equal(
    formatSql('select c1_0.id,c1_0.name from s.customer c1_0 left join s.card l1_0 on l1_0.id=c1_0.card_id where c1_0.id=?'),
    'select c1_0.id, c1_0.name\nfrom s.customer c1_0\nleft join s.card l1_0 on l1_0.id=c1_0.card_id\nwhere c1_0.id=?');
});

test('update set where', () => {
  assert.equal(formatSql('update s.purchase_order set customer_id=? where id=?'),
    'update s.purchase_order\nset customer_id=?\nwhere id=?');
});

test('subquery trong ngoặc không bị ngắt', () => {
  assert.equal(formatSql('delete from s.t where id in (select x.id from s.x x where x.a=?)'),
    'delete\nfrom s.t\nwhere id in (select x.id from s.x x where x.a=?)');
});

test('dòng dài được ngắt ở dấu phẩy, không dòng nào quá 85', () => {
  const columns = Array.from({ length: 12 }, (_, i) => `o1_0.column_number_${i}`).join(',');
  const lines = formatSql(`select ${columns} from s.t o1_0`).split('\n');
  for (const line of lines) assert.ok(line.length <= 85, line);
  assert.ok(lines.length > 2);
  assert.ok(lines[1].startsWith('    '));
});

test('for update đứng riêng một dòng', () => {
  assert.equal(formatSql('select p1_0.id from s.product p1_0 where p1_0.id=? for no key update'),
    'select p1_0.id\nfrom s.product p1_0\nwhere p1_0.id=?\nfor no key update');
});
