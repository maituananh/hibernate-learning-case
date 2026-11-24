import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { parseJUnitXml } from '../lib/junit-xml.mjs';

const fixture = fs.readFileSync(new URL('./fixtures/sample-junit.xml', import.meta.url), 'utf8');

test('giải mã tên testcase có &quot; và &amp; và →', () => {
  const { cases } = parseJUnitXml(fixture);
  assert.equal(cases.length, 3);
  assert.ok(cases.some((c) => c.name.startsWith('G1-S12 | ') && c.name.includes('"inverse"')));
  assert.ok(cases.some((c) => c.name.includes('→')));
  assert.ok(cases.some((c) => c.name.includes('setCustomer & DB')));
});

test('testcase tự đóng có systemOut = null, output cấp class vào suiteOut', () => {
  const { cases, suiteOut } = parseJUnitXml(fixture);
  assert.equal(cases.find((c) => c.name.startsWith('G1-S17')).systemOut, null);
  assert.match(suiteOut, /\[DB\] g1_v4_bidi_mappedby\.customer/);
  assert.doesNotMatch(suiteOut, /PurchaseOrder → MANAGED/);
});

test('CDATA bị tách được nối lại', () => {
  const { cases } = parseJUnitXml(fixture);
  assert.match(cases.find((c) => c.name.startsWith('G1-S13')).systemOut, /chuỗi có \]\]> ở giữa/);
});

test('classname được giữ', () => {
  const { cases } = parseJUnitXml(fixture);
  assert.equal(cases[0].classname, 'com.example.hibernatelab.g1_owning_side.G1V4BidirectionalTest');
});
