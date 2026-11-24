import { test } from 'node:test';
import assert from 'node:assert/strict';
import { REPO_ROOT } from '../lib/paths.mjs';
import { buildIndex } from '../lib/scenario-index.mjs';
import { lintChapter, lintCoverage } from '../lib/lint.mjs';

const index = new Map(buildIndex(REPO_ROOT).map((e) => [e.code, e]));
const T4 = 'Ai giữ khóa ngoại: ManyToOne, OneToMany và mappedBy';
const ch4 = {
  id: '04', file: '021-chuong-04-khoa-ngoai.md', kind: 'chapter', number: 4, title: T4,
  scenarios: ['G1-S01..S18'], extra: [], status: 'done',
};
const H2 = `## Chương 4. ${T4}\n\n`;
const rules = (md, chapter = ch4) => lintChapter(md, chapter, index).map((i) => i.rule);

const VALID = `${H2}### 4.1 Hai chiều chuẩn {.bien-the}

![Bảng purchase_order có cột customer_id](img/g1-v4.png){width=80%}

**Ưu điểm.** Rõ ràng.

**Nhược điểm.** Phải đồng bộ hai phía.

**Nên dùng khi.** Cần điều hướng hai chiều.

#### 4.1.1 Chỉ thêm vào collection phía mappedBy: khóa ngoại bị để NULL {.cot-loi}

**Bối cảnh.** A.

**Khi nào xảy ra.** B.

**Khi nào không xảy ra.** C.

**Ví dụ.** D.

\`\`\`java
    customer.getOrders().add(order);
\`\`\`

**Kết quả.** E.

**Vì sao.** F.

::: {.ghi-nho}
G.
:::

::: {.tracuu codes="G1-S12"}
:::
`;

test('chương hợp lệ không có lỗi', () => {
  assert.deepEqual(lintChapter(VALID, ch4, index), []);
});

test('code-tab', () => {
  assert.deepEqual(rules(`${H2}\`\`\`java\n\tprivate Long id;\n\`\`\`\n`), ['code-tab']);
});

test('code-width', () => {
  assert.deepEqual(rules(`${H2}\`\`\`java\n${'x'.repeat(86)}\n\`\`\`\n`), ['code-width']);
  assert.deepEqual(rules(`${H2}\`\`\`java\n${'ố'.repeat(85)}\n\`\`\`\n`), []);
});

test('banned-char cho mũi tên ngoài code, không báo trong code', () => {
  assert.ok(rules(`${H2}Persist → INSERT\n`).includes('banned-char'));
  assert.ok(rules(`${H2}Mức độ ⭐\n`).includes('banned-char'));
  assert.ok(!rules(`${H2}\`\`\`text\nA → B\n\`\`\`\n`).includes('banned-char'));
});

test('banned-token: viết tắt PC, mã biến thể, mã tình huống trong văn bản', () => {
  assert.ok(rules(`${H2}Entity nằm trong PC.\n`).includes('banned-token'));
  assert.ok(rules(`${H2}Biến thể V1-NN thì sao.\n`).includes('banned-token'));
  assert.ok(rules(`${H2}Xem G1-S12.\n`).includes('banned-token'));
  assert.ok(!rules(`${H2}::: {.tracuu codes="G1-S12"}\n:::\n`).includes('banned-token'));
});

test('banned-token bỏ qua ở chương allowCodes', () => {
  const appendix = { id: 'pl-c', kind: 'appendix', letter: 'C', title: 'Bảng tra tình huống', allowCodes: true };
  assert.deepEqual(rules('## Phụ lục C. Bảng tra tình huống\n\n| G1-S12 | V1-NN |\n', appendix), []);
});

test('heading-chars', () => {
  assert.ok(rules(`${H2}### 4.1 Dùng \`@OneToMany\`\n`).includes('heading-chars'));
  assert.ok(rules(`${H2}### 4.1 Mã G1-S12\n`).includes('heading-chars'));
});

test('numbering: mục nhảy số', () => {
  assert.ok(rules(`${H2}### 4.1 A\n\n### 4.3 B\n`).includes('numbering'));
  assert.ok(rules(`${H2}### 4.1 A\n\n#### 4.1.2 B {.mo-rong}\n`).includes('numbering'));
});

test('numbering phụ lục dùng chữ cái', () => {
  const plA = { id: 'pl-a', kind: 'appendix', letter: 'A', title: 'Tra cứu annotation', allowCodes: true, status: 'done' };
  assert.deepEqual(lintChapter('## Phụ lục A. Tra cứu annotation\n\n### Bảng ManyToOne\n', plA, index), []);
});

test('title-match', () => {
  assert.ok(rules('## Chương 4. Tên khác\n').includes('title-match'));
});

test('template: thiếu nhãn của tình huống cốt lõi', () => {
  const broken = VALID.replace('**Khi nào không xảy ra.** C.\n', '');
  assert.deepEqual(rules(broken), ['template']);
});

test('template: heading 4 thiếu class', () => {
  assert.ok(rules(VALID.replace(' {.cot-loi}', '')).includes('template'));
});

const ch24 = {
  id: '24', file: '071-chuong-24-ngoai-lab.md', kind: 'chapter', number: 24,
  title: 'Tình huống thực tế chưa có trong lab', scenarios: [], extra: [], status: 'done',
};
const NO_TRACUU = `## Chương 24. Tình huống thực tế chưa có trong lab

### 24.1 Flyway

#### 24.1.1 Thay ddl-auto bằng migration {.mo-rong}

**Bối cảnh.** A.

**Kết quả.** B.

::: {.ghi-nho}
C.
:::
`;

test('template: chương không có scenarios cho phép tình huống mở rộng không có khung Tra cứu', () => {
  assert.deepEqual(lintChapter(NO_TRACUU, ch24, index), []);
});

test('template: chương có scenarios vẫn bắt buộc khung Tra cứu', () => {
  const chapter = { ...ch24, scenarios: ['G1-S12'] };
  assert.deepEqual(lintChapter(NO_TRACUU, chapter, index).map((i) => i.rule), ['template']);
});

test('template: chương không có scenarios vẫn bắt buộc Ghi nhớ', () => {
  const broken = NO_TRACUU.replace(/::: \{\.ghi-nho\}\nC\.\n:::\n/, '');
  assert.deepEqual(lintChapter(broken, ch24, index).map((i) => i.rule), ['template']);
});

test('variant-section: mục biến thể thiếu ưu nhược điểm hoặc hình', () => {
  assert.deepEqual(rules(VALID.replace('**Nhược điểm.** Phải đồng bộ hai phía.\n', '')), ['variant-section']);
  assert.deepEqual(rules(VALID.replace(/!\[.*\n/, '')), ['variant-section']);
});

test('tier-label: tình huống cốt lõi bị gắn mở rộng', () => {
  const md = VALID.replace('{.cot-loi}', '{.mo-rong}');
  assert.deepEqual(rules(md), ['tier-label']);
});

test('coverage: mã thiếu trong chương done', () => {
  const chapter = { id: '04', kind: 'chapter', number: 4, file: 'x.md', scenarios: ['G1-S12'], status: 'done' };
  const issues = lintCoverage([chapter], new Map([['04', '## Chương 4. X\n']]), index);
  assert.ok(issues.some((i) => i.rule === 'coverage' && i.message.includes('G1-S12')));
});

test('coverage: mã khác dự đoán phải có khối khac-du-doan', () => {
  const chapter = { id: '04', kind: 'chapter', number: 4, file: 'x.md', scenarios: ['G1-S04'], status: 'done' };
  const codes = [...index.keys()].filter((c) => c.startsWith('G1-S04')).join(',');
  const md = `## Chương 4. X\n\n::: {.tracuu codes="${codes}"}\n:::\n`;
  const issues = lintCoverage([chapter], new Map([['04', md]]), index);
  assert.ok(issues.some((i) => i.rule === 'coverage' && i.message.includes('G1-S04b')));
  const fixed = `${md}\n::: {.khac-du-doan codes="G1-S04b,G1-S04d,G1-S04e"}\nA\n:::\n`;
  assert.deepEqual(lintCoverage([chapter], new Map([['04', fixed]]), index)
    .filter((i) => i.message.includes('khac-du-doan')), []);
});

test('coverage: mã extra phải xuất hiện', () => {
  const chapter = { id: '07', kind: 'chapter', number: 7, file: 'x.md', scenarios: [], extra: ['G6-S12'], status: 'done' };
  const issues = lintCoverage([chapter], new Map([['07', '## Chương 7. X\n']]), index);
  assert.ok(issues.some((i) => i.message.includes('G6-S12')));
});

test('coverage: chương todo không bị kiểm', () => {
  const chapter = { id: '04', kind: 'chapter', number: 4, file: 'x.md', scenarios: ['G1-S12'], status: 'todo' };
  assert.deepEqual(lintCoverage([chapter], new Map(), index).filter((i) => i.file === 'x.md'), []);
});
