import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseCode, expandRange, groupOf } from '../lib/codes.mjs';

test('parseCode tách mã có chữ cái ở cuối', () => {
  assert.deepEqual(parseCode('G1-S04b | V1-NN chặn → PropertyValueException'),
    { code: 'G1-S04b', base: 'G1-S04' });
});

test('parseCode mã cấp sự cố của G14', () => {
  assert.deepEqual(parseCode('G14-R1-S02 | vượt ngân sách'),
    { code: 'G14-R1-S02', base: 'G14-R1-S02' });
});

test('parseCode trả null với test hạ tầng', () => {
  assert.equal(parseCode('SqlAssert đếm đúng INSERT'), null);
});

test('expandRange mở khoảng mã', () => {
  assert.deepEqual(expandRange('G1-S01..S03'), ['G1-S01', 'G1-S02', 'G1-S03']);
  assert.deepEqual(expandRange('G14-I1-S01..S02'), ['G14-I1-S01', 'G14-I1-S02']);
  assert.deepEqual(expandRange('G0-S12'), ['G0-S12']);
});

test('groupOf lấy nhóm', () => {
  assert.equal(groupOf('G14-I2-S01'), 'G14');
  assert.equal(groupOf('G1-S04b'), 'G1');
});
