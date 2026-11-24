import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { REPO_ROOT } from '../lib/paths.mjs';
import { buildIndex } from '../lib/scenario-index.mjs';
import { loadDdl } from '../lib/ddl.mjs';
import { preprocessChapter, buildAppendixC } from '../lib/preprocess.mjs';

// Cần build/ddl/create.sql: chạy suite của lab ít nhất một lần.
let ctx;
before(() => {
  ctx = {
    index: new Map(buildIndex(REPO_ROOT).map((e) => [e.code, e])),
    ddl: loadDdl(),
    locations: new Map(),
  };
});

test('tracuu sinh đủ dòng và link đúng', () => {
  const out = preprocessChapter('#### 4.7.1 X {.cot-loi}\n\n::: {.tracuu codes="G1-S12"}\n:::\n', ctx);
  assert.match(out, /custom-style="Tra cuu"/);
  assert.match(out, /Mã tình huống: G1-S12/);
  assert.match(out, /blob\/main\/src\/test\/java\/com\/example\/hibernatelab\/g1_owning_side\/G1V4BidirectionalTest\.java/);
  assert.match(out, /tree\/main\/src\/main\/java\/com\/example\/hibernatelab\/g1_owning_side\/v4_bidi_mappedby\//);
  assert.match(out, /Method: `s12_/);
  assert.match(out, /https:\/\/github\.com\/maituananh\/hibernate-learning-case\/pull\/3/);
  assert.equal(ctx.locations.get('G1-S12'), '4.7.1');
});

test('tracuu nhiều mã cùng class gộp một dòng Test', () => {
  const out = preprocessChapter('::: {.tracuu codes="G1-S14a,G1-S14b"}\n:::\n', ctx);
  assert.equal(out.match(/^Test: /gm).length, 1);
  assert.match(out, /Method: `s14a_\w+`, `s14b_\w+`/);
});

test('nhãn tier chèn dưới heading, class bị bỏ', () => {
  const out = preprocessChapter('#### 4.7.1 Tiêu đề {.cot-loi}\n', ctx);
  assert.match(out, /^#### 4\.7\.1 Tiêu đề$/m);
  assert.match(out, /custom-style="Nhan"\}\nCốt lõi/);
  assert.match(preprocessChapter('#### 4.7.2 Y {.mo-rong}\n', ctx), /Mở rộng/);
});

test('class bien-the trên heading 3 bị bỏ', () => {
  assert.equal(preprocessChapter('### 4.7 Hai chiều {.bien-the}\n', ctx).trim(), '### 4.7 Hai chiều');
});

test('mã lạ thì ném lỗi', () => {
  assert.throws(() => preprocessChapter('::: {.tracuu codes="G99-S01"}\n:::\n', ctx), /G99-S01/);
});

test('ddl sinh code block sql', () => {
  const out = preprocessChapter('::: {.ddl schema="g1_v4_bidi_mappedby" tables="customer"}\n:::\n', ctx);
  assert.match(out, /```sql\ncreate table g1_v4_bidi_mappedby\.customer \(/);
});

test('ddl với bảng không có thì ném lỗi', () => {
  assert.throws(() => preprocessChapter('::: {.ddl schema="g1_v4_bidi_mappedby" tables="khong_co"}\n:::\n', ctx),
    /g1_v4_bidi_mappedby\.khong_co/);
});

test('ghi-nho có nhãn', () => {
  const out = preprocessChapter('::: {.ghi-nho}\nOwning side ghi FK.\n:::\n', ctx);
  assert.match(out, /custom-style="Ghi nho"[\s\S]*Ghi nhớ[\s\S]*Owning side ghi FK\./);
});

test('khac-du-doan, chua-kiem-chung, kien-thuc-nen có nhãn và style riêng', () => {
  assert.match(preprocessChapter('::: {.khac-du-doan codes="G1-S04b"}\nA\n:::\n', ctx),
    /custom-style="Ket qua khac du doan"[\s\S]*Kết quả khác dự đoán/);
  assert.match(preprocessChapter('::: {.chua-kiem-chung}\nA\n:::\n', ctx),
    /custom-style="Chua kiem chung"[\s\S]*Chưa kiểm chứng trong lab/);
  assert.match(preprocessChapter('::: {.kien-thuc-nen}\nA\n:::\n', ctx),
    /custom-style="Kien thuc nen"[\s\S]*Kiến thức nền/);
});

test('không đụng vào nội dung trong code fence', () => {
  const md = '```text\n::: {.tracuu codes="G99-S01"}\n#### 1.1.1 X {.cot-loi}\n```\n';
  assert.equal(preprocessChapter(md, ctx), md);
});

test('phụ lục C liệt kê mã kèm mục trong sách', () => {
  const locations = new Map([['G1-S12', '4.7.1']]);
  const table = buildAppendixC(ctx.index, locations);
  assert.match(table, /^\| Mã tình huống \| Mục trong sách \| Test class \| Method \| Pull Request \|$/m);
  assert.match(table, /^\| G1-S12 \| 4\.7\.1 \| G1V4BidirectionalTest \| s12_\w+ \| #3 \|$/m);
});
