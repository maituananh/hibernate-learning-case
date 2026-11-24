import { test } from 'node:test';
import assert from 'node:assert/strict';
import { asciiToMarkdown } from '../lib/ascii-table.mjs';

test('bảng ASCII thành bảng Markdown, null thành NULL', () => {
  assert.equal(asciiToMarkdown([
    '  | customer_id | id | order_no |',
    '  +-------------+----+----------+',
    '  | null        | 3  | A-001    |',
  ]), '| customer_id | id | order_no |\n|---|---|---|\n| NULL | 3 | A-001 |');
});

test('db.print của bảng trống chỉ in "(bảng trống)", không có header', () => {
  assert.equal(asciiToMarkdown(['  (bảng trống)']), '*(bảng trống)*');
});

test('bảng không có dòng dữ liệu', () => {
  assert.equal(asciiToMarkdown(['  | id | name |', '  +----+------+']),
    '| id | name |\n|---|---|');
});
