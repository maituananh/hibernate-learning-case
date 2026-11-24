import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { parseDdl, renderDdl } from '../lib/ddl.mjs';

const sample = fs.readFileSync(new URL('./fixtures/create-sample.sql', import.meta.url), 'utf8');
// create.sql thật bị ghi lặp lại nhiều lần: truyền hai lần để chắc chắn không bị trùng.
const tables = parseDdl(sample, sample);

test('cột, khóa chính, FK không bị trùng', () => {
  const order = tables.get('g1_v4_bidi_mappedby.purchase_order');
  assert.deepEqual(order.columns.map((c) => c.name), ['customer_id', 'id', 'order_no']);
  assert.deepEqual(order.primaryKey, ['id']);
  assert.equal(order.foreignKeys.length, 1);
  assert.equal(order.foreignKeys[0].refTable, 'g1_v4_bidi_mappedby.customer');
  assert.deepEqual(order.foreignKeys[0].refColumns, []);
});

test('on delete cascade', () => {
  const fk = tables.get('g2_c8_ondelete.order_item').foreignKeys.find((f) => f.columns[0] === 'order_id');
  assert.equal(fk.onDelete, 'cascade');
});

test('FK hai cột giữ đúng thứ tự', () => {
  const fk = tables.get('g8_j1_explicit.shipment').foreignKeys[0];
  assert.deepEqual(fk.columns, ['wh_country', 'wh_code']);
  assert.deepEqual(fk.refColumns, ['country_code', 'code']);
});

test('khóa chính ghép', () => {
  assert.deepEqual(tables.get('g8_j1_explicit.warehouse').primaryKey, ['code', 'country_code']);
});

test('cột có check vẫn tách được type', () => {
  const status = tables.get('g10_q1_baseline.purchase_order').columns.find((c) => c.name === 'status');
  assert.equal(status.type, 'varchar(255)');
  assert.equal(status.notNull, true);
});

test('unique inline', () => {
  const column = tables.get('g6_v2_fk_on_card.loyalty_card').columns.find((c) => c.name === 'customer_id');
  assert.equal(column.unique, true);
  assert.equal(column.notNull, false);
});

test('renderDdl: mỗi cột một dòng, không quá 85 ký tự, không tab', () => {
  const text = renderDdl([
    tables.get('g1_v4_bidi_mappedby.customer'),
    tables.get('g1_v4_bidi_mappedby.purchase_order'),
  ]);
  assert.match(text, /create table g1_v4_bidi_mappedby\.purchase_order \(\n {4}customer_id bigint,/);
  assert.match(text, /alter table g1_v4_bidi_mappedby\.purchase_order\n {4}add constraint FK158vbkwgyf5r6ogk9nkugqv2c\n {4}foreign key \(customer_id\)\n {4}references g1_v4_bidi_mappedby\.customer;/);
  for (const line of text.split('\n')) {
    assert.ok(line.length <= 85, line);
    assert.ok(!line.includes('\t'));
  }
});

test('renderDdl có on delete cascade', () => {
  const text = renderDdl([tables.get('g2_c8_ondelete.order_item')]);
  assert.match(text, /references g2_c8_ondelete\.purchase_order\n {4}on delete cascade;/);
});
