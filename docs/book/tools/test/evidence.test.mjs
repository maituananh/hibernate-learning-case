import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { parseJUnitXml } from '../lib/junit-xml.mjs';
import { parseBlocks, parseHibernateSql, renderEvidence } from '../lib/evidence.mjs';

const fixture = fs.readFileSync(new URL('./fixtures/sample-junit.xml', import.meta.url), 'utf8');
const caseOut = parseJUnitXml(fixture).cases[0].systemOut;

test('parseBlocks tách khối SQL, bỏ chú thích nextval', () => {
  const sqlBlock = parseBlocks(caseOut).find((b) => b.tag === 'SQL');
  assert.equal(sqlBlock.lines.length, 4);
  assert.ok(!sqlBlock.lines.some((line) => line.includes('(nextval:')));
  assert.equal(sqlBlock.lines[1], "select nextval('g1_v4_bidi_mappedby.purchase_order_seq')");
});

test('parseBlocks tách khối DB và LIFECYCLE', () => {
  const blocks = parseBlocks(caseOut);
  const dbBlock = blocks.find((b) => b.tag === 'DB');
  assert.equal(dbBlock.header, 'g1_v4_bidi_mappedby.purchase_order (1 dòng)');
  assert.equal(dbBlock.lines.length, 3);
  assert.equal(blocks.find((b) => b.tag === 'LIFECYCLE').header, 'PurchaseOrder → MANAGED');
});

test('dòng log một dòng chen giữa không cắt khối DDL; log SQL nhiều dòng không bị nuốt vào khối', () => {
  const out = [
    '[DDL] schema g1_v1_m2o_uni',
    '2026-11-24T13:14:15.882+07:00 DEBUG 1 --- [    Test worker] o.s.jdbc.core.JdbcTemplate : Executing prepared SQL query',
    '  bảng g1_v1_m2o_uni.customer',
    '2026-11-24T13:14:21.731+07:00 DEBUG 1 --- [    Test worker] o.s.jdbc.core.JdbcTemplate : Executing prepared SQL statement [select a',
    'from pg_index ix',
    'order by k.ord',
    ']',
    '    - id bigint NOT NULL PK',
    '2026-11-24T13:14:15.883+07:00 DEBUG 1 --- [    Test worker] org.hibernate.SQL                        : ',
    '    select',
    '        c1_0.id',
    '[LIFECYCLE] Customer → MANAGED',
  ].join('\n');
  const ddl = parseBlocks(out).find((b) => b.tag === 'DDL');
  assert.deepEqual(ddl.lines, ['bảng g1_v1_m2o_uni.customer', '  - id bigint NOT NULL PK']);
});

test('parseHibernateSql gắn giá trị bind vào đúng câu', () => {
  const statements = parseHibernateSql(caseOut);
  assert.equal(statements.length, 2);
  const insert = statements.find((s) => s.sql.startsWith('insert'));
  assert.equal(insert.sql, 'insert into g1_v4_bidi_mappedby.purchase_order (customer_id, order_no, id) values (?, ?, ?)');
  assert.deepEqual(insert.binds, ['(1:BIGINT) 3', '(2:VARCHAR) A-001', '(3:BIGINT) 3']);
});

test('renderEvidence có SQL định dạng, bảng Markdown và tên method', () => {
  const entry = { code: 'G1-S12', testClass: 'G1V4BidirectionalTest', method: 's12_onlyInverse' };
  const md = renderEvidence(entry, 'G1-S12 | V4 ...', caseOut, false);
  assert.match(md, /G1V4BidirectionalTest#s12_onlyInverse/);
  assert.match(md, /```sql\ninsert into g1_v4_bidi_mappedby\.purchase_order \(customer_id, order_no, id\)\nvalues \(\?, \?, \?\)/);
  assert.match(md, /\| NULL \| 3 \| A-001 \|/);
});

test('evidence khi không có output riêng', () => {
  const md = renderEvidence(undefined, 'G1-S17 | [BOOT-FAIL] ...', null, true);
  assert.match(md, /Không có output ở cấp test/);
  assert.match(md, /_class/);
});
