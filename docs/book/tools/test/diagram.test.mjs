import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { parseDdl } from '../lib/ddl.mjs';
import { renderDiagram, svgToPng } from '../lib/diagram.mjs';

const tables = parseDdl(fs.readFileSync(new URL('./fixtures/create-sample.sql', import.meta.url), 'utf8'));
const v4 = { id: 'g1-v4', schema: 'g1_v4_bidi_mappedby', tables: ['customer', 'purchase_order'] };

test('sơ đồ ghi "nhiều" ở phía FK và "1" ở phía bị trỏ', () => {
  const svg = renderDiagram(v4, tables);
  assert.match(svg, />nhiều</);
  assert.match(svg, />1</);
  assert.match(svg, />customer_id</);
  assert.match(svg, />PK</);
  assert.match(svg, />FK</);
});

test('cột khóa chính đứng đầu khung', () => {
  const svg = renderDiagram(v4, tables);
  const orderBox = svg.slice(svg.indexOf('>purchase_order<'));
  assert.ok(orderBox.indexOf('>id<') < orderBox.indexOf('>customer_id<'));
});

test('nhãn "1"/"nhiều" vẽ sau khung để không bị khung che', () => {
  const svg = renderDiagram(v4, tables);
  assert.ok(svg.indexOf('>nhiều<') > svg.lastIndexOf('<rect'));
});

test('cột not null được ghi rõ', () => {
  assert.match(renderDiagram(v4, tables), /varchar\(255\) not null/);
});

test('FK unique thì hai đầu đều là "1"', () => {
  const svg = renderDiagram({ id: 'y', schema: 'g6_v2_fk_on_card', tables: ['customer', 'loyalty_card'] }, tables);
  assert.doesNotMatch(svg, />nhiều</);
  assert.match(svg, />UQ</);
});

test('FK tự tham chiếu vẽ thành vòng', () => {
  const svg = renderDiagram({ id: 'z', schema: 'g9_s01_tree', tables: ['category'] }, tables);
  assert.match(svg, /<path/);
});

test('bảng không có trong DDL thì báo lỗi rõ ràng', () => {
  assert.throws(() => renderDiagram({ id: 'x', schema: 'g1_v4_bidi_mappedby', tables: ['khong_co'] }, tables),
    /g1_v4_bidi_mappedby\.khong_co/);
});

test('svgToPng ra file PNG thật, chữ có dấu', () => {
  const png = svgToPng(renderDiagram(v4, tables));
  assert.deepEqual([...png.subarray(0, 4)], [0x89, 0x50, 0x4e, 0x47]);
});

test('thiếu font thì báo lỗi rõ ràng', () => {
  assert.throws(() => svgToPng('<svg xmlns="http://www.w3.org/2000/svg"/>', ['/khong/co/font.ttf']), /Thiếu font/);
});
