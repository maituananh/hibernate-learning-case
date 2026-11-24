# Sách "Hibernate Mapping in Practice" — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build ra `build/book/hibernate-mapping-in-practice.docx`: cuốn sách tiếng Việt (bìa tiếng Anh) gom toàn bộ tình huống của lab, mỗi tình huống có bằng chứng thật và chỉ đường về repo.

**Architecture:** Nội dung viết bằng Markdown trong `docs/book/chapters/`, theo thứ tự của manifest `docs/book/chapters.json`. Bộ công cụ Node (`docs/book/tools/`) làm các việc sau: lập index tình huống từ test, bắt output từng test, sinh sơ đồ bảng từ `build/ddl/create.sql`, mở rộng các khối đặc biệt (Tra cứu, DDL, nhãn) và lint. Cuối cùng pandoc 3.13 build ra .docx với `reference.docx` do script sinh.

**Tech Stack:** Node 23 (`node:test`), pandoc 3.13 (standalone), `@resvg/resvg-js` 2.6.2, `jszip` 3.10.1, Gradle init script (bắt output), Docker (Testcontainers, để chạy suite).

**Spec:** [docs/superpowers/specs/2026-11-24-hibernate-book-design.md](../specs/2026-11-24-hibernate-book-design.md). Đọc spec trước; plan này chỉ ghi các quyết định spec để lại.

## Global Constraints

- Không sửa code, test, cấu hình của lab (`src/`, `lab-enhanced/`, `build.gradle`, `settings.gradle`). Chỉ được tạo `docs/book/`, `docs/superpowers/`, và thêm dòng `docs/book/node_modules/` vào `.gitignore`.
- Output: `build/book/hibernate-mapping-in-practice.docx`. File .docx, `reference.docx` và PNG **không commit**; mọi thứ sinh ra nằm trong `build/book/`.
- Version ghim: pandoc **3.13** (`pandoc-3.13-linux-amd64.tar.gz`), `@resvg/resvg-js` **2.6.2**, `jszip` **3.10.1**.
- Ngôn ngữ: bìa tiếng Anh; còn lại tiếng Việt **có dấu**, giữ thuật ngữ kỹ thuật tiếng Anh.
- Ký tự cấm ngoài code block: bảng spec 8.2 (emoji, `→ ← ↔ ⇒`, `× ≠ ≤ ≥ ⌈ ⌉ §`, `PC`, `T/M/D/R`, `[BOOT-FAIL]`, `[LIFECYCLE]`, `[FLUSH]`, mã biến thể, mã tình huống ngoài khung Tra cứu và phụ lục).
- Tiêu đề: không `@`, không backtick, không mã tình huống. Tên chương đúng nguyên văn bảng spec mục 6.
- Code block: tối đa **85** ký tự mỗi dòng, **không có tab** (source Java của repo thụt lề bằng tab: phải đổi sang 4 khoảng trắng).
- Trang: A4 (`w:w="11906" w:h="16838"`), lề 2.5 cm (`1418` twips mỗi phía). Thân bài Cambria 11pt, heading Calibri, code Consolas 9pt. Phần và Chương bắt đầu trang mới. Footer có số trang, bìa không có footer.
- Bìa: nội dung nguyên văn spec 8.8, Author **Mai Tuấn Anh**.
- `REPO_URL = https://github.com/maituananh/hibernate-learning-case`; link file: `${REPO_URL}/blob/main/<path>`, link thư mục: `${REPO_URL}/tree/main/<dir>`, PR: `${REPO_URL}/pull/<n>`.
- Kết luận lấy từ assert trong test và `docs/RESULTS.md`, **không** lấy giả thuyết của `docs/02-SCENARIOS.md`.
- **Mức chi tiết (người học yêu cầu):** tình huống Cốt lõi viết chi tiết, Bối cảnh nêu tình huống đời thực, Vì sao giải thích từng bước; Phần II (Chương 4 đến 10) và Phần III (Chương 11 đến 13) viết chi tiết nhất. Xem spec 7.2a.
- **JPA thuần:** tình huống nào dùng API hoặc annotation riêng của Hibernate thì thêm đoạn `**Trong JPA thuần.**` (cách viết tương đương, hoặc "JPA không có cách tương đương"). Xem spec 7.2b. Chương 1 sẽ có mục 1.5 và sách có Phụ lục G (Task 15).
- Commit chỉ ở các bước "Commit" của plan (người học đã đồng ý khi duyệt plan). Message dạng `docs(book): ...`, kết thúc bằng `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Tab trong code Java chép từ repo.** Mọi source của repo thụt lề bằng tab. Người đọc mong code thẳng hàng như IDE, nên lint phải báo lỗi `code-tab` (test ở Task 4).
2. **Dấu tiếng Việt trong hình sơ đồ.** Nếu thiếu font, resvg vẽ chữ "nhiều" thành ô vuông. Script phải nạp font DejaVu theo đường dẫn cố định và dừng với thông báo rõ ràng khi thiếu font (test ở Task 3).
3. **Testcase không có output riêng.** Test `[BOOT-FAIL]` và test chạy song song qua `ConcurrentTx` có thể không có output riêng. Khi đó vẫn phải có file evidence kèm ghi chú, và lấy output cấp class thay thế (test ở Task 2).
4. **DisplayName có entity XML và ký tự lạ** (`&quot;`, `&amp;`, `→`). Tên phải được giải mã đúng thì mới tách được mã tình huống (test ở Task 1 và Task 2).
5. **`create.sql` lặp lại hàng trăm lần**, và còn một file riêng ở `lab-enhanced/build/ddl/`. Parser phải gộp hai file và chỉ giữ lần xuất hiện đầu tiên của mỗi bảng và constraint (test ở Task 3).

---

## Cấu trúc file

```
.gitignore                                 + docs/book/node_modules/
docs/book/
  README.md                                cách build, yêu cầu, lưu ý cập nhật mục lục trong Word
  package.json, package-lock.json          type: module; script "test"
  metadata.yaml                            title, author, lang: vi-VN
  chapters.json                            manifest: thứ tự file, tiêu đề, mã tình huống của từng chương
  build.sh                                 một lệnh build ra .docx
  chapters/*.md                            nội dung (tên file ở Task 1)
  diagrams/*.json                          spec sơ đồ
  tools/
    lib/paths.mjs                          REPO_ROOT, BOOK_DIR, BUILD_DIR, REPO_URL, FONT_FILES, PANDOC
    lib/codes.mjs                          tách và mở rộng mã tình huống
    lib/scenario-index.mjs                 index tình huống từ test, tier, khác dự đoán, PR
    lib/junit-xml.mjs                      đọc file JUnit XML
    lib/evidence.mjs                       tách khối output, SQL kèm bind, render evidence
    lib/sql-format.mjs                     định dạng SQL tối đa 85 ký tự
    lib/ascii-table.mjs                    bảng ASCII sang bảng Markdown
    lib/ddl.mjs                            parse create.sql, render DDL
    lib/diagram.mjs                        spec + bảng thành SVG, SVG thành PNG
    lib/preprocess.mjs                     mở rộng khối đặc biệt, phụ lục C
    lib/lint.mjs                           các rule lint
    lib/reference-docx.mjs                 sinh reference.docx
    build-index.mjs, capture-evidence.mjs, diagrams.mjs, preprocess.mjs, lint.mjs, make-reference.mjs   (CLI)
    capture-output.gradle                  init script: outputPerTestCase
    to-pdf.sh                              (tùy chọn) LibreOffice headless xuất PDF để tự kiểm tra
    test/*.test.mjs, test/fixtures/*
build/book/                                (bị .gitignore) index.json, evidence/, img/, md/, reference.docx, tools/, .docx
```

## Cú pháp Markdown dành cho người viết (preprocess xử lý trước khi đưa vào pandoc)

| Người viết gõ | Preprocess đổi thành |
|---|---|
| `## Chương 4. <tiêu đề>` | giữ nguyên (Heading 2) |
| `### 4.7 <tiêu đề> {.bien-the}` | bỏ `{.bien-the}`; lint yêu cầu mục có `**Ưu điểm.**`, `**Nhược điểm.**`, `**Nên dùng khi.**` và ít nhất một hình |
| `#### 4.7.2 <tiêu đề> {.cot-loi}` hoặc `{.mo-rong}` | bỏ class, chèn đoạn style `Nhan` chứa "Cốt lõi" hoặc "Mở rộng" ngay dưới heading |
| `::: {.tracuu codes="G1-S12,G1-S13"}` `:::` (rỗng) | khối style `Tra cuu`, sinh từ index (8.6 của spec) |
| `::: {.ddl schema="g1_v4_bidi_mappedby" tables="customer,purchase_order"}` `:::` | code block `sql` lấy từ `create.sql` |
| `::: {.ghi-nho}` … `:::` | style `Ghi nho`, có đoạn nhãn "Ghi nhớ" đứng đầu |
| `::: {.khac-du-doan codes="G1-S04b"}` … `:::` | style `Ket qua khac du doan`, nhãn "Kết quả khác dự đoán" |
| `::: {.chua-kiem-chung}` … `:::` | style `Chua kiem chung`, nhãn "Chưa kiểm chứng trong lab" |
| `::: {.kien-thuc-nen}` … `:::` | style `Kien thuc nen`, nhãn "Kiến thức nền" |
| `::: {.phu-luc-c}` `:::` | bảng Phụ lục C sinh tự động |
| `![<một dòng mô tả>](img/<id>.png){width=80%}` | giữ nguyên (pandoc lấy alt làm caption) |

Nhãn chạy đầu đoạn (run-in), viết đúng chữ: `**Bối cảnh.**`, `**Khi nào xảy ra.**`, `**Khi nào không xảy ra.**`, `**Ví dụ.**`, `**Kết quả.**`, `**Vì sao.**`, `**Cách tránh.**`.

---

### Task 1: Manifest chương và index tình huống

**Files:**
- Create: `docs/book/package.json`, `docs/book/chapters.json`, `docs/book/tools/lib/paths.mjs`, `docs/book/tools/lib/codes.mjs`, `docs/book/tools/lib/scenario-index.mjs`, `docs/book/tools/build-index.mjs`
- Modify: `.gitignore` (thêm `docs/book/node_modules/`)
- Test: `docs/book/tools/test/codes.test.mjs`, `docs/book/tools/test/scenario-index.test.mjs`

**Interfaces:**
- Produces:
  - `paths.mjs`: `REPO_ROOT`, `BOOK_DIR`, `BUILD_DIR` (string tuyệt đối), `REPO_URL`
  - `parseCode(name: string): {code: string, base: string} | null`. Regex `^(G\d+(?:-[IR]\d+)?-S\d+)([a-z]?)\s*\|`; `base` là mã bỏ chữ cái cuối.
  - `expandRange(spec: string): string[]`, ví dụ `"G1-S01..S18"`, `"G14-I1-S01..S04"`, `"G0-S12"`
  - `scanTests(repoRoot: string): Entry[]`, trong đó `Entry = {code, base, displayName, testClass, testFile, method, entityPackages: string[]}`. Đường dẫn tính từ gốc repo; thư mục kết thúc bằng `/`. Quét `src/test/java` và `lab-enhanced/src/test/java`.
  - `readTiers(md: string): Map<string, 'core'|'extra'>` (đọc ⭐ và ➕ của `02-SCENARIOS.md`, key là base hoặc mã cấp sự cố như `G14-I1`), `tierFor(base: string, tiers): 'core'|'extra'|null`
  - `readDiffers(md: string): Set<string>` (các dòng `RESULTS.md` có "KHÁC" ở cột "Khớp?"; `G1-S04d/e` mở thành `G1-S04d`, `G1-S04e`)
  - `prFor(code: string): number`
  - `buildIndex(repoRoot): IndexEntry[]` (`Entry` + `{tier, differs: boolean, pr}`), ghi ra `build/book/index.json`
  - `chapters.json`: `{chapters: Chapter[]}` với `Chapter = {id, file, kind: 'front'|'part'|'chapter'|'appendix', number?, letter?, title?, scenarios?: string[], extra?: string[], allowCodes?: boolean, status: 'todo'|'done'}`

- [ ] **Step 1: Viết test thất bại cho `codes.mjs`**

```js
test('parseCode tách mã có chữ cái', () => {
  assert.deepEqual(parseCode('G1-S04b | V1-NN chặn → PropertyValueException'),
    {code: 'G1-S04b', base: 'G1-S04'});
});
test('parseCode mã cấp sự cố G14', () => {
  assert.deepEqual(parseCode('G14-R1-S02 | vượt ngân sách'),
    {code: 'G14-R1-S02', base: 'G14-R1-S02'});
});
test('parseCode trả null với test hạ tầng', () => {
  assert.equal(parseCode('SqlAssert đếm đúng INSERT'), null);
});
test('expandRange', () => {
  assert.deepEqual(expandRange('G1-S01..S03'), ['G1-S01', 'G1-S02', 'G1-S03']);
  assert.deepEqual(expandRange('G14-I1-S01..S02'), ['G14-I1-S01', 'G14-I1-S02']);
  assert.deepEqual(expandRange('G0-S12'), ['G0-S12']);
});
```

- [ ] **Step 2: Chạy `cd docs/book && npm test`, kỳ vọng FAIL (module chưa có)**

- [ ] **Step 3: Tạo `package.json`** (`"type": "module"`, `"scripts": {"test": "node --test tools/test/"}`, dependencies `@resvg/resvg-js@2.6.2`, `jszip@3.10.1`), chạy `npm install` để có `package-lock.json`, thêm dòng vào `.gitignore`. Viết `paths.mjs` (REPO_ROOT = `path.resolve(import.meta.dirname, '../../../..')`) và `codes.mjs`.

- [ ] **Step 4: Chạy `npm test`, kỳ vọng PASS**

- [ ] **Step 5: Viết test thất bại cho `scenario-index.mjs` (chạy trên repo thật)**

```js
const entries = scanTests(REPO_ROOT);
test('G1-S12 trỏ đúng class, method, package entity', () => {
  const e = entries.find(x => x.code === 'G1-S12');
  assert.equal(e.testClass, 'G1V4BidirectionalTest');
  assert.match(e.method, /^s12_/);
  assert.ok(e.entityPackages.includes(
    'src/main/java/com/example/hibernatelab/g1_owning_side/v4_bidi_mappedby/'));
});
test('mã duy nhất, mọi file tồn tại', () => {
  assert.equal(new Set(entries.map(e => e.code)).size, entries.length);
  for (const e of entries) assert.ok(fs.existsSync(path.join(REPO_ROOT, e.testFile)), e.code);
});
test('có test của lab-enhanced', () => {
  assert.ok(entries.some(e => e.code.startsWith('G13-S11')));
});
test('tier', () => {
  const tiers = readTiers(read('docs/02-SCENARIOS.md'));
  assert.equal(tierFor('G1-S12', tiers), 'core');
  assert.equal(tierFor('G1-S05', tiers), 'extra');
  assert.equal(tierFor('G14-I1-S03', tiers), 'core');   // lấy theo dòng G14-I1
});
test('khác dự đoán', () => {
  const d = readDiffers(read('docs/RESULTS.md'));
  assert.ok(d.has('G1-S04b'));
  assert.ok(d.has('G1-S04e'));
  assert.ok(!d.has('G1-S08'));
});
test('PR', () => {
  assert.equal(prFor('G0-S01'), 1);  assert.equal(prFor('G0-S02'), 2);
  assert.equal(prFor('G1-S12'), 3);  assert.equal(prFor('G10-S01'), 14);
  assert.equal(prFor('G13-S10'), 17); assert.equal(prFor('G13-S11a'), 18);
  assert.equal(prFor('G14-I2-S01'), 19);
});
```

Bảng PR (spec 8.6): G0 là 2 (riêng G0-S01 là 1), G1 là 3, G2 là 4, G3 là 5, G4 là 6, G5 là 7, G6 là 8, G7 là 9, G8 là 10, G9 là 11, G10 là 14, G11 là 15, G12 là 16, G13-S01..S10 là 17, G13-S11 trở đi là 18, G14 là 19.

- [ ] **Step 6: Chạy test, kỳ vọng FAIL**

- [ ] **Step 7: Viết `scenario-index.mjs` và `build-index.mjs`**

Method là `void (\w+)\(` đầu tiên sau `@DisplayName`. `entityPackages` lấy từ các import `com.example.hibernatelab.g…` (bỏ `.support.`), đổi package thành thư mục rồi giữ những thư mục thật sự tồn tại trong `src/main/java`, `src/test/java`, `lab-enhanced/src/main/java`, `lab-enhanced/src/test/java`. Base không có trong `02-SCENARIOS` thì tier là `core` và in cảnh báo.

- [ ] **Step 8: Chạy test, kỳ vọng PASS; chạy `node tools/build-index.mjs`, kỳ vọng có `build/book/index.json` và in ra số mã**

- [ ] **Step 9: Viết `chapters.json`** (mọi chương `status: "todo"`, trừ `bia` và các file `part` để `done`)

| id | file | kind | number/letter | title | scenarios | extra |
|---|---|---|---|---|---|---|
| bia | 000-bia-va-muc-luc.md | front | | | | |
| loi-noi-dau | 005-loi-noi-dau.md | front | | Lời nói đầu: Cách đọc cuốn sách này | | |
| phan-1 | 010-phan-1.md | part | | Phần I. Nền tảng | | |
| 01 | 011-chuong-01-mo-hinh-mau.md | chapter | 1 | Mô hình mẫu và kiến thức tối thiểu | | |
| 02 | 012-chuong-02-vong-doi-entity.md | chapter | 2 | Vòng đời entity và persistence context | G0-S01..S11, G0-S13 | |
| 03 | 013-chuong-03-anh-xa-cot.md | chapter | 3 | Ánh xạ cột và kiểu giá trị: Embeddable, Enum, AttributeConverter, NaturalId | G12-S01..S03, G12-S10, G12-S11 | |
| phan-2 | 020-phan-2.md | part | | Phần II. Quan hệ giữa các bảng | | |
| 04 | 021-chuong-04-khoa-ngoai.md | chapter | 4 | Ai giữ khóa ngoại: ManyToOne, OneToMany và mappedBy | G1-S01..S18 | |
| 05 | 022-chuong-05-cascade.md | chapter | 5 | Cascade: thao tác nào lan từ cha sang con | G2-S01..S11, G2-S13 | |
| 06 | 023-chuong-06-orphan-removal.md | chapter | 6 | orphanRemoval: khi con bị tách khỏi cha | G3-S01..S11, G9-S06 | |
| 07 | 024-chuong-07-one-to-one.md | chapter | 7 | Quan hệ một-một: OneToOne và MapsId | G6-S01..S11, G6-S13 | G6-S12 |
| 08 | 025-chuong-08-many-to-many.md | chapter | 8 | Quan hệ nhiều-nhiều: ManyToMany và entity trung gian | G7-S01..S12 | G7-S14 |
| 09 | 026-chuong-09-khoa-ghep.md | chapter | 9 | Khóa chính ghép và khóa ngoại nhiều cột | G8-S01..S07 | |
| 10 | 027-chuong-10-mo-hinh-dac-biet.md | chapter | 10 | Các mô hình đặc biệt: cây tự tham chiếu, ElementCollection, kế thừa | G9-S01, G12-S04..S09 | |
| phan-3 | 030-phan-3.md | part | | Phần III. Đọc dữ liệu | | |
| 11 | 031-chuong-11-fetch-n-plus-1.md | chapter | 11 | FetchType, proxy và vấn đề N+1 | G5-S01..S14, G5-S16, G7-S13 | |
| 12 | 032-chuong-12-truy-van.md | chapter | 12 | Viết truy vấn: JPQL, native SQL và phân trang | G10-S01..S05, G10-S08..S10, G5-S15 | |
| 13 | 033-chuong-13-loc-trong-mapping.md | chapter | 13 | Lọc dữ liệu ngay trong mapping: Formula, SQLRestriction, Filter, SoftDelete | G12-S12..S14, G9-S11, G10-S07 | |
| phan-4 | 040-phan-4.md | part | | Phần IV. Ghi dữ liệu và đồng thời | | |
| 14 | 041-chuong-14-sua-con-qua-cha.md | chapter | 14 | Sửa bảng con thông qua bảng cha | G4-S01..S10, G4-S12, G9-S02, G9-S09 | |
| 15 | 042-chuong-15-cap-nhat-hang-loat.md | chapter | 15 | Cập nhật và xóa hàng loạt | G2-S12, G4-S13, G9-S05, G10-S06 | |
| 16 | 043-chuong-16-transaction.md | chapter | 16 | Ranh giới transaction trong Spring | G0-S12, G0-S14, G9-S07, G11-S01..S04, G11-S12 | |
| 17 | 044-chuong-17-khoa-du-lieu.md | chapter | 17 | Ghi đồng thời và khóa dữ liệu | G11-S05..S11, G4-S11, G9-S08 | |
| phan-5 | 050-phan-5.md | part | | Phần V. Hiệu năng | | |
| 18 | 051-chuong-18-ghi-so-luong-lon.md | chapter | 18 | Ghi số lượng lớn: batch, sequence và StatelessSession | G13-S01..S05 | |
| 19 | 052-chuong-19-cache.md | chapter | 19 | Second-level cache và query cache | G13-S06..S10 | |
| 20 | 053-chuong-20-bytecode-enhancement.md | chapter | 20 | Bytecode enhancement | G13-S11..S13 | |
| phan-6 | 060-phan-6.md | part | | Phần VI. Thực chiến | | |
| 21 | 061-chuong-21-bay-entity.md | chapter | 21 | Những bẫy khi viết lớp entity | G9-S03, G9-S04 | |
| 22 | 062-chuong-22-su-co.md | chapter | 22 | Bốn sự cố production và cách xử lý | G14-I1-S01..S04, G14-I2-S01..S04, G14-I3-S01..S04, G14-I4-S01..S04 | |
| 23 | 063-chuong-23-rao-chan.md | chapter | 23 | Rào chắn cho cả team | G14-R1-S01..S03, G14-R2-S01..S03 | |
| phan-7 | 070-phan-7.md | part | | Phần VII. Mở rộng tầm nhìn | | |
| 24 | 071-chuong-24-ngoai-lab.md | chapter | 24 | Tình huống thực tế chưa có trong lab | | G14-I5 |
| 25 | 072-chuong-25-so-sanh-cong-cu.md | chapter | 25 | Hibernate so với các công cụ khác và lộ trình học tiếp | | |
| phu-luc | 090-phu-luc.md | part | | Phụ lục | | |
| pl-a … pl-f | 091-phu-luc-a-annotation.md, 092-phu-luc-b-khac-du-doan.md, 093-phu-luc-c-tra-cuu-tinh-huong.md, 094-phu-luc-d-pull-request.md, 095-phu-luc-e-chay-lab.md, 096-phu-luc-f-thuat-ngu.md | appendix | A … F | Tra cứu annotation; Những kết quả khác dự đoán; Bảng tra tình huống; Danh sách Pull Request; Cách chạy lab; Bảng thuật ngữ | | | (mọi phụ lục: `allowCodes: true`) |

- [ ] **Step 10: Viết thêm test `chapters.json` phủ đúng một lần**

Với mỗi mã trong `index.json`, đúng một chương có `expandRange(...)` chứa `base` của mã đó. Chạy test, kỳ vọng PASS. Nếu có mã không thuộc chương nào (mã phát sinh trong test mà spec không liệt kê), gán nó vào chương có cùng nhóm và cùng chủ đề theo bảng spec mục 6, rồi ghi chú lại để báo người học ở checkpoint.

---

### Task 2: Bắt bằng chứng từ test (evidence)

**Files:**
- Create: `docs/book/tools/capture-output.gradle`, `docs/book/tools/lib/junit-xml.mjs`, `docs/book/tools/lib/evidence.mjs`, `docs/book/tools/lib/sql-format.mjs`, `docs/book/tools/lib/ascii-table.mjs`, `docs/book/tools/capture-evidence.mjs`
- Test: `docs/book/tools/test/junit-xml.test.mjs`, `evidence.test.mjs`, `sql-format.test.mjs`, `ascii-table.test.mjs`, fixture `docs/book/tools/test/fixtures/sample-junit.xml` (viết tay theo đúng định dạng thật: 1 testcase có output gồm `[SQL]`, `[DB]`, `[LIFECYCLE]`, log `org.hibernate.SQL` kèm dòng bind; 1 testcase tự đóng không có output; output cấp class; tên có `&quot;` và `→`)

**Interfaces:**
- Consumes: `parseCode`, `BUILD_DIR`, `build/book/index.json` (Task 1)
- Produces:
  - `parseJUnitXml(xml: string): {cases: {name, classname, systemOut: string|null}[], suiteOut: string}`
  - `parseBlocks(out: string): {tag, header, lines: string[]}[]` với tag thuộc `SQL|DB|LIFECYCLE|DDL|LINT|EXCEPTION|BOOT-FAIL|BUDGET`
  - `parseHibernateSql(out: string): {sql: string, binds: string[]}[]`
  - `formatSql(sql: string, width = 85): string`
  - `asciiToMarkdown(lines: string[]): string`
  - `renderEvidence(entry: IndexEntry|undefined, displayName: string, out: string|null, classFallback: boolean): string`
  - CLI `capture-evidence.mjs [--run]`: `--run` chạy `./gradlew test --rerun --init-script docs/book/tools/capture-output.gradle`, sau đó đọc `build/test-results/test/*.xml` và `lab-enhanced/build/test-results/test/*.xml`, rồi ghi `build/book/evidence/<code>.md` và `build/book/evidence/_class/<classname>.md`

- [ ] **Step 1: Viết test thất bại**

```js
test('giải mã tên testcase', () => {
  const {cases} = parseJUnitXml(fixture);
  assert.ok(cases.some(c => c.name.startsWith('G1-S12 | ') && c.name.includes('"inverse"')));
  assert.ok(cases.some(c => c.name.includes('→')));
});
test('testcase tự đóng có systemOut = null, output class vào suiteOut', () => {
  const {cases, suiteOut} = parseJUnitXml(fixture);
  assert.equal(cases.find(c => c.name.startsWith('G1-S17')).systemOut, null);
  assert.match(suiteOut, /\[DB\]/);
});
test('parseBlocks', () => {
  const blocks = parseBlocks(caseOut);
  const sqlBlock = blocks.find(b => b.tag === 'SQL');
  assert.equal(sqlBlock.lines.length, 3);
  assert.ok(!sqlBlock.lines.some(l => l.includes('(nextval:')));   // bỏ chú thích nextval
  assert.equal(blocks.find(b => b.tag === 'DB').header,
    'g1_v4_bidi_mappedby.purchase_order (1 dòng)');
});
test('parseHibernateSql gắn bind', () => {
  const stmts = parseHibernateSql(caseOut);
  const insert = stmts.find(s => s.sql.startsWith('insert'));
  assert.deepEqual(insert.binds, ['(1:BIGINT) 3', '(2:VARCHAR) A-001', '(3:BIGINT) 3']);
});
test('asciiToMarkdown, null thành NULL', () => {
  assert.equal(asciiToMarkdown([
    '  | customer_id | id | order_no |', '  +-------------+----+----------+',
    '  | null        | 3  | A-001    |']),
    '| customer_id | id | order_no |\n|---|---|---|\n| NULL | 3 | A-001 |');
});
test('formatSql insert', () => {
  assert.equal(formatSql('insert into s.t (a,b,id) values (?,?,?)'),
    'insert into s.t (a, b, id)\nvalues (?, ?, ?)');
});
test('formatSql select, join, where', () => {
  assert.equal(formatSql('select c1_0.id,c1_0.name from s.customer c1_0 left join s.card l1_0 on l1_0.id=c1_0.card_id where c1_0.id=?'),
    'select c1_0.id, c1_0.name\nfrom s.customer c1_0\nleft join s.card l1_0 on l1_0.id=c1_0.card_id\nwhere c1_0.id=?');
});
test('formatSql dòng dài được ngắt, không dòng nào quá 85', () => {
  const long = 'select ' + Array.from({length: 12}, (_, i) => `o1_0.column_number_${i}`).join(',') + ' from s.t o1_0';
  for (const line of formatSql(long).split('\n')) assert.ok(line.length <= 85, line);
});
test('evidence khi không có output riêng', () => {
  const md = renderEvidence(undefined, 'G1-S17 | [BOOT-FAIL] ...', null, true);
  assert.match(md, /Không có output ở cấp test/);
});
```

- [ ] **Step 2: Chạy `npm test`, kỳ vọng FAIL**

- [ ] **Step 3: Viết các module.** Một số quy tắc chưa có trong test:
  - CDATA bị tách bởi `]]]]><![CDATA[>` phải được nối lại.
  - Dòng thân của một khối là dòng bắt đầu bằng hai khoảng trắng, không phải dòng log có timestamp `^\d{4}-\d{2}-\d{2}T`.
  - `formatSql` chỉ ngắt dòng trước keyword ở độ sâu ngoặc 0: `from`, `where`, `values`, `set`, `(left |right |inner |cross )?join`, `order by`, `group by`, `having`, `limit`, `offset`, `fetch first`, `for (update|share|no key update)`, `union( all)?`, `returning`. Sau dấu phẩy thì thêm khoảng trắng. Dòng nào dài quá width thì ngắt ở `, ` và thụt 4 khoảng trắng.
  - `renderEvidence` gồm các phần: tiêu đề (mã, displayName, class#method lấy từ index); "Khối in ra từ test" (SQL đã định dạng trong code fence `sql`, DB thành bảng Markdown, các tag khác giữ nguyên trong code fence `text`); "Toàn bộ SQL kèm bind" (đánh số).
  - `capture-output.gradle`: `allprojects { tasks.withType(Test).configureEach { reports.junitXml.outputPerTestCase = true } }`, kèm comment tiếng Việt giải thích vì sao.

- [ ] **Step 4: Chạy `npm test`, kỳ vọng PASS**

- [ ] **Step 5: Chạy thật `node tools/capture-evidence.mjs --run`** (cần Docker; suite có 365 test)

Kỳ vọng:
- Gradle báo BUILD SUCCESSFUL.
- `build/book/evidence/G1-S12.md` có `insert into g1_v4_bidi_mappedby.purchase_order` và bảng có cột `customer_id` với giá trị `NULL`.
- Script in ra số file evidence, bằng số mã trong `index.json`.
- Script in danh sách mã phải dùng output cấp class.

Không commit (commit ở Task 6).

---

### Task 3: DDL và sơ đồ bảng

**Files:**
- Create: `docs/book/tools/lib/ddl.mjs`, `docs/book/tools/lib/diagram.mjs`, `docs/book/tools/diagrams.mjs`, `docs/book/diagrams/domain.json`
- Modify: `docs/book/tools/lib/paths.mjs` (thêm `FONT_FILES`)
- Test: `docs/book/tools/test/ddl.test.mjs`, `diagram.test.mjs`, fixture `docs/book/tools/test/fixtures/create-sample.sql` (chép các đoạn thật của `g1_v4_bidi_mappedby` và `g2_c8_ondelete` vào, cho lặp lại 2 lần; thêm một bảng có FK hai cột `foreign key (wh_country, wh_code)`, một cột `unique` inline, một FK tự tham chiếu `category.parent_id`, một cột có `check (status in (...))`)

**Interfaces:**
- Consumes: `REPO_ROOT`, `BUILD_DIR`
- Produces:
  - `parseDdl(...sqlTexts: string[]): Map<string, Table>`, key `"schema.table"`. `Table = {schema, name, columns: {name, definition, type, notNull, unique}[], primaryKey: string[], uniques: string[][], foreignKeys: {name, columns: string[], refTable: string, refColumns: string[], onDelete: string|null}[]}`. Mỗi bảng và mỗi constraint chỉ giữ lần xuất hiện đầu tiên. `refColumns` rỗng nghĩa là trỏ tới khóa chính.
  - `loadDdl(): Map<string, Table>`: đọc `build/ddl/create.sql` và `lab-enhanced/build/ddl/create.sql`
  - `renderDdl(tables: Table[]): string`: các `create table` (mỗi cột một dòng, thụt 4) rồi tới các constraint của chính các bảng đó, dạng `alter table <t>\n    add constraint <n>\n    foreign key (...)\n    references <t2>[ (cols)][\n    on delete cascade];`
  - `DiagramSpec = {id, schema, tables: string[], rows?: string[][]}`
  - `renderDiagram(spec: DiagramSpec, tables: Map<string, Table>): string` (SVG)
  - `svgToPng(svg: string, fontFiles = FONT_FILES): Buffer`
  - CLI `diagrams.mjs`: với mỗi `docs/book/diagrams/*.json`, ghi `build/book/img/<id>.png`
  - `FONT_FILES` = `/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf`, `DejaVuSans-Bold.ttf`, `DejaVuSansMono.ttf`

- [ ] **Step 1: Viết test thất bại**

```js
const t = parseDdl(sample, sample);           // lặp 2 lần vẫn không trùng
test('cột, khóa chính, FK', () => {
  const po = t.get('g1_v4_bidi_mappedby.purchase_order');
  assert.deepEqual(po.columns.map(c => c.name), ['customer_id', 'id', 'order_no']);
  assert.deepEqual(po.primaryKey, ['id']);
  assert.equal(po.foreignKeys.length, 1);
  assert.equal(po.foreignKeys[0].refTable, 'g1_v4_bidi_mappedby.customer');
});
test('on delete cascade', () => {
  const fk = t.get('g2_c8_ondelete.order_item').foreignKeys.find(f => f.columns[0] === 'order_id');
  assert.equal(fk.onDelete, 'cascade');
});
test('FK hai cột', () => {
  // fixture: foreign key (wh_country, wh_code) references g8_j1_explicit.warehouse (country_code, code)
  const fk = t.get('g8_j1_explicit.shipment').foreignKeys[0];
  assert.deepEqual(fk.columns, ['wh_country', 'wh_code']);
  assert.deepEqual(fk.refColumns, ['country_code', 'code']);
});
test('cột có check vẫn tách được type', () => {
  // fixture: status varchar(255) not null check (status in ('NEW','PAID'))
  const status = t.get('g10_q1_baseline.purchase_order').columns.find(c => c.name === 'status');
  assert.equal(status.type, 'varchar(255)');
  assert.equal(status.notNull, true);
});
test('renderDdl không dòng nào quá 85 ký tự và không có tab', () => {
  const text = renderDdl([t.get('g1_v4_bidi_mappedby.customer'), t.get('g1_v4_bidi_mappedby.purchase_order')]);
  assert.match(text, /create table g1_v4_bidi_mappedby\.purchase_order \(\n {4}customer_id bigint,/);
  for (const line of text.split('\n')) { assert.ok(line.length <= 85, line); assert.ok(!line.includes('\t')); }
});
test('sơ đồ: ghi "nhiều" ở phía FK, "1" ở phía bị trỏ', () => {
  const svg = renderDiagram({id: 'x', schema: 'g1_v4_bidi_mappedby',
    tables: ['customer', 'purchase_order']}, t);
  assert.match(svg, />nhiều</);
  assert.match(svg, />customer_id</);
  assert.match(svg, />PK</);
});
test('FK unique thì hai đầu đều là "1"', () => {
  // fixture: g6_v2_fk_on_card.loyalty_card (customer_id bigint unique, ...) + FK tới customer
  const svg = renderDiagram({id: 'y', schema: 'g6_v2_fk_on_card', tables: ['customer', 'loyalty_card']}, t);
  assert.doesNotMatch(svg, />nhiều</);
});
test('FK tự tham chiếu vẽ được', () => {
  // fixture: g9_s01_tree.category (parent_id bigint) + FK parent_id tới chính category
  const svg = renderDiagram({id: 'z', schema: 'g9_s01_tree', tables: ['category']}, t);
  assert.match(svg, /<path/);
});
test('svgToPng ra PNG thật', () => {
  const png = svgToPng(renderDiagram(spec, t));
  assert.deepEqual([...png.subarray(0, 4)], [0x89, 0x50, 0x4e, 0x47]);
});
test('thiếu font thì báo lỗi rõ ràng', () => {
  assert.throws(() => svgToPng('<svg/>', ['/khong/co/font.ttf']), /Thiếu font/);
});
```

- [ ] **Step 2: Chạy `npm test`, kỳ vọng FAIL**

- [ ] **Step 3: Viết `ddl.mjs`.** Type của cột là phần định nghĩa đứng trước keyword đầu tiên trong số `not null`, `null`, `unique`, `check`, `default`, `generated`.

- [ ] **Step 4: Viết `diagram.mjs`**

Bố cục:
- `rows` mặc định là tối đa 3 bảng mỗi hàng.
- Mỗi bảng là một khung: tên bảng in đậm (DejaVu Sans Bold) ở đầu. Mỗi cột một dòng, font DejaVu Sans Mono, gồm marker `PK`, `FK`, `PK FK`, `UQ` hoặc để trống, rồi tên cột, rồi type, rồi `not null` nếu cột không phải PK mà not null.
- Độ rộng khung tính theo số ký tự nhân 0.6 nhân cỡ chữ.
- Đường nối đi từ tâm khung này tới tâm khung kia, cắt ở mép khung. Nhãn đặt ở khoảng 15% và 85% chiều dài đường.
- FK tự tham chiếu vẽ thành vòng ở cạnh phải khung.

`svgToPng` dùng `new Resvg(svg, {font: {fontFiles, loadSystemFonts: false, defaultFontFamily: 'DejaVu Sans'}, fitTo: {mode: 'zoom', value: 3}})`. Trước khi render phải kiểm tra từng file font tồn tại; thiếu file nào thì ném `Error('Thiếu font: <path>')`.

- [ ] **Step 5: Chạy `npm test`, kỳ vọng PASS**

- [ ] **Step 6: Viết `diagrams/domain.json`** (`schema: "g5_f1_all_lazy"`, `tables: ["customer", "purchase_order", "order_item", "product", "payment"]`). Chạy `node tools/diagrams.mjs`, rồi xem `build/book/img/domain.png` bằng Read tool: chữ có dấu hiển thị đúng, không có ô vuông, đường nối không cắt qua chữ.

---

### Task 4: Preprocess và lint

**Files:**
- Create: `docs/book/tools/lib/preprocess.mjs`, `docs/book/tools/lib/lint.mjs`, `docs/book/tools/preprocess.mjs`, `docs/book/tools/lint.mjs`
- Test: `docs/book/tools/test/preprocess.test.mjs`, `lint.test.mjs`

**Interfaces:**
- Consumes: `index.json`, `chapters.json`, `expandRange`, `loadDdl`, `renderDdl`, `REPO_URL`
- Produces:
  - `preprocessChapter(md: string, ctx: {index: Map<string, IndexEntry>, ddl: Map<string, Table>, locations: Map<string, string>}): string`. Hàm mở rộng các khối trong bảng "Cú pháp Markdown" ở trên và ghi `locations.set(code, '<số heading 4 gần nhất>')` cho từng mã trong `.tracuu`. Mã không có trong index thì ném `Error('Mã tình huống không có trong index: <code>')`.
  - `buildAppendixC(index, locations, chapters): string`: bảng Markdown gồm các cột Mã tình huống, Mục, Test class, Method, Pull Request
  - CLI `preprocess.mjs`: xử lý mọi file trong manifest theo thứ tự, ghi ra `build/book/md/<file>`; Phụ lục C xử lý sau cùng
  - `lintChapter(md: string, chapter: Chapter, index: Map<string, IndexEntry>): Issue[]` và `lintCoverage(chapters: Chapter[], contents: Map<string, string>, index: Map<string, IndexEntry>): Issue[]` (`contents`: id chương sang Markdown), trong đó `Issue = {file, line, rule, message}`
  - CLI `lint.mjs [--chapter <id>]`: in `file:line [rule] message`, thoát với mã 1 nếu có lỗi. Rule `coverage` chỉ áp cho chương `status: "done"`.

Rule lint (tên rule viết đúng như dưới):

| Rule | Lỗi khi |
|---|---|
| `banned-char` | Ngoài code fence và khối `{=openxml}` có ký tự cấm (spec 8.2): `\u{1F300}-\u{1FAFF}`, `☀-➿`, `⭐`, `←-⇿`, `×≠≤≥⌈⌉§` |
| `banned-token` | Ngoài code fence: `\bPC\b`, `T/M/D/R`, `[BOOT-FAIL]`, `[LIFECYCLE]`, `[FLUSH]`, mã biến thể `\b(V[1-7](-NN\|-OPT)?\|C[0-8](-DEEP)?\|O[0-2]\|U[0-5]\|F[0-4]S?\|M[1-9]\|J[1-4])\b`, mã tình huống `\bG\d+-` ngoài thuộc tính `codes="..."`. Bỏ qua chương có `allowCodes` |
| `heading-chars` | Heading chứa `@`, backtick hoặc `G\d+-S` |
| `code-width` | Dòng trong code fence dài hơn 85 ký tự |
| `code-tab` | Code fence chứa ký tự tab |
| `numbering` | Chương N: H3 không theo `N.1, N.2, …` liên tục; H4 không theo `N.M.1, …` liên tục trong H3 của nó. Phụ lục: H2 là `Phụ lục <letter>.`, H3 được phép không đánh số |
| `title-match` | H2 khác `Chương <number>. <title>` (hoặc `Phụ lục <letter>. <title>`) trong manifest |
| `template` | H4 `.cot-loi` thiếu một trong `**Bối cảnh.**`, `**Khi nào xảy ra.**`, `**Khi nào không xảy ra.**`, `**Ví dụ.**`, `**Kết quả.**`, `**Vì sao.**`, khối `.ghi-nho`, khối `.tracuu`. H4 `.mo-rong` thiếu `**Bối cảnh.**`, `**Kết quả.**`, `.ghi-nho`, `.tracuu`. H4 thiếu cả hai class |
| `variant-section` | H3 `.bien-the` thiếu `**Ưu điểm.**`, `**Nhược điểm.**`, `**Nên dùng khi.**` hoặc không có hình nào |
| `tier-label` | H4 `.mo-rong` mà trong `.tracuu` của nó có mã tier `core` (hoặc ngược lại: `.cot-loi` mà mọi mã đều `extra`) |
| `coverage` | Mã trong index thuộc chương này mà không nằm trong `.tracuu` nào của chương; mã thuộc 0 chương hoặc 2 chương trở lên; mã `extra` không xuất hiện trong chương; mã có `differs` mà không nằm trong khối `.khac-du-doan` nào |

- [ ] **Step 1: Viết test thất bại cho preprocess**

```js
test('tracuu sinh đủ dòng và link đúng', () => {
  const out = preprocessChapter('#### 4.7.1 X {.cot-loi}\n\n::: {.tracuu codes="G1-S12"}\n:::\n', ctx);
  assert.match(out, /custom-style="Tra cuu"/);
  assert.match(out, /Mã tình huống: G1-S12/);
  assert.match(out, /blob\/main\/src\/test\/java\/com\/example\/hibernatelab\/g1_owning_side\/G1V4BidirectionalTest\.java/);
  assert.match(out, /https:\/\/github\.com\/maituananh\/hibernate-learning-case\/pull\/3/);
  assert.equal(ctx.locations.get('G1-S12'), '4.7.1');
});
test('nhãn tier', () => {
  assert.match(preprocessChapter('#### 4.7.1 X {.cot-loi}\n', ctx), /custom-style="Nhan"[\s\S]*Cốt lõi/);
});
test('mã lạ thì ném lỗi', () => {
  assert.throws(() => preprocessChapter('::: {.tracuu codes="G99-S01"}\n:::\n', ctx), /G99-S01/);
});
test('ddl sinh code block sql', () => {
  const out = preprocessChapter('::: {.ddl schema="g1_v4_bidi_mappedby" tables="customer"}\n:::\n', ctx);
  assert.match(out, /```sql\ncreate table g1_v4_bidi_mappedby\.customer \(/);
});
test('ghi-nho có nhãn', () => {
  const out = preprocessChapter('::: {.ghi-nho}\nOwning side ghi FK.\n:::\n', ctx);
  assert.match(out, /custom-style="Ghi nho"[\s\S]*Ghi nhớ[\s\S]*Owning side ghi FK\./);
});
```

`ctx` dựng trong `before()`: `index` từ `buildIndex(REPO_ROOT)` (đổi thành Map theo `code`), `ddl` từ `loadDdl()`, `locations` là `new Map()`. Phải chạy suite ít nhất một lần để có `build/ddl/create.sql`.

- [ ] **Step 2: Viết test thất bại cho lint, mỗi rule một test** (đoạn Markdown nhỏ sai đúng một rule, assert `issues.map(i => i.rule)` chứa đúng rule đó; cộng một test với chương hợp lệ thì ra `[]`). Bắt buộc có:

```js
test('code-tab', () => {
  const md = '## Chương 4. ' + T4 + '\n\n```java\n\tprivate Long id;\n```\n';
  assert.deepEqual(lintChapter(md, ch4, index).map(i => i.rule), ['code-tab']);
});
test('banned-char cho mũi tên ngoài code, không báo trong code', () => {
  assert.ok(rules('Persist → INSERT').includes('banned-char'));
  assert.ok(!rules('```text\nA → B\n```').includes('banned-char'));
});
test('numbering phụ lục dùng chữ cái', () => {
  const plA = {id: 'pl-a', kind: 'appendix', letter: 'A', title: 'Tra cứu annotation', allowCodes: true, status: 'done'};
  assert.deepEqual(lintChapter('## Phụ lục A. Tra cứu annotation\n\n### Bảng ManyToOne\n', plA, index), []);
});
test('coverage: mã thiếu trong chương done', () => {
  const ch = {id: '04', kind: 'chapter', number: 4, scenarios: ['G1-S12'], status: 'done'};
  const issues = lintCoverage([ch], new Map([['04', '## Chương 4. X\n']]), index);
  assert.ok(issues.some(i => i.rule === 'coverage' && i.message.includes('G1-S12')));
});
```

- [ ] **Step 3: Chạy `npm test`, kỳ vọng FAIL**

- [ ] **Step 4: Viết `preprocess.mjs`, `lint.mjs` (lib và CLI)**

- [ ] **Step 5: Chạy `npm test`, kỳ vọng PASS**

---

### Task 5: reference.docx, bìa, mục lục, build.sh: sách khung

**Files:**
- Create: `docs/book/tools/lib/reference-docx.mjs`, `docs/book/tools/make-reference.mjs`, `docs/book/build.sh`, `docs/book/metadata.yaml`, `docs/book/README.md`, `docs/book/tools/to-pdf.sh`, mọi file `chapters/*.md` trong manifest (file `chapter` và `appendix` chỉ có heading H2 cùng một đoạn "Nội dung đang được viết."; file `part` có H1 cùng 2–3 câu giới thiệu và danh sách chương; `000-bia-va-muc-luc.md` viết đầy đủ)
- Modify: `docs/book/tools/lib/paths.mjs` (thêm `PANDOC = BUILD_DIR/tools/pandoc-3.13/bin/pandoc`)
- Test: `docs/book/tools/test/reference-docx.test.mjs`

**Interfaces:**
- Consumes: mọi CLI của Task 1–4
- Produces: `buildReferenceDocx(defaultDocx: Buffer): Promise<Buffer>`; lệnh `docs/book/build.sh [--evidence] [--no-lint]`

Style cần có trong `reference.docx` (`w:name`, rồi `w:styleId` trong ngoặc):
- `Normal`: Cambria 11pt, dãn dòng 276, after 120.
- `Heading 1`: Calibri 28pt, đậm, `pageBreakBefore`, căn giữa, before 2400.
- `Heading 2`: Calibri 22pt, đậm, `pageBreakBefore`.
- `Heading 3`: Calibri 15pt, đậm, `keepNext`.
- `Heading 4`: Calibri 12.5pt, đậm, `keepNext`.
- `Source Code` (SourceCode): Consolas 9pt, nền F3F3F3, viền BFBFBF.
- `Verbatim Char`: Consolas 10pt.
- `Ghi nho` (GhiNho): viền trái 2E75B6, nền EAF1FB.
- `Tra cuu` (TraCuu): Calibri 9pt, nền F2F2F2, viền hộp BFBFBF.
- `Ket qua khac du doan` (KetQuaKhacDuDoan): viền trái C55A11, nền FBE9DD.
- `Chua kiem chung` (ChuaKiemChung): viền trái 7F7F7F nét đứt, chữ nghiêng.
- `Kien thuc nen` (KienThucNen): viền trái 70AD47, nền EEF6E8.
- `Nhan` (Nhan): Calibri 9pt, đậm, màu 2E75B6.
- `Cover Title` (CoverTitle): Calibri 36pt, đậm, 1F3864, căn giữa, before 3000.
- `Cover Subtitle` (CoverSubtitle): 16pt, nghiêng, căn giữa.
- `Cover Author` (CoverAuthor): 18pt, đậm, căn giữa.
- `Cover Meta` (CoverMeta): 11pt, 595959, căn giữa.
- `Muc luc` (MucLuc): Calibri 20pt, đậm.
- `Table`: header đậm, nền D9E2F3, viền BFBFBF.

Mọi font ghi tường minh `w:ascii`, `w:hAnsi`, `w:cs`, `w:eastAsia`. Trong `sectPr`: `pgSz` A4, `pgMar` 1418, `titlePg`, `footerReference` tới `footer1.xml` (đoạn căn giữa có field `PAGE`). Trong `settings.xml`: `<w:updateFields w:val="true"/>`.

- [ ] **Step 1: Viết test thất bại**

```js
const out = await JSZip.loadAsync(await buildReferenceDocx(defaultDocx));
// defaultDocx: chạy `pandoc -o - --print-default-data-file reference.docx` một lần trong before()
test('có đủ style', async () => {
  const styles = await out.file('word/styles.xml').async('string');
  for (const id of ['GhiNho', 'TraCuu', 'KetQuaKhacDuDoan', 'ChuaKiemChung', 'KienThucNen',
                    'Nhan', 'CoverTitle', 'CoverSubtitle', 'CoverAuthor', 'CoverMeta', 'MucLuc'])
    assert.match(styles, new RegExp(`w:styleId="${id}"`));
  assert.match(styles, /Consolas/);
});
test('A4, lề 2.5 cm, trang đầu khác, footer', async () => {
  const doc = await out.file('word/document.xml').async('string');
  assert.match(doc, /<w:pgSz w:w="11906" w:h="16838"/);
  assert.match(doc, /w:left="1418"/);
  assert.match(doc, /<w:titlePg\/>/);
  assert.match(await out.file('word/footer1.xml').async('string'), /PAGE/);
});
test('updateFields', async () => {
  assert.match(await out.file('word/settings.xml').async('string'), /<w:updateFields w:val="true"\/>/);
});
```

- [ ] **Step 2: Viết `build.sh` phần tải pandoc** (tải `https://github.com/jgm/pandoc/releases/download/3.13/pandoc-3.13-linux-amd64.tar.gz` và giải nén vào `build/book/tools/` nếu chưa có), chạy rồi kiểm tra `build/book/tools/pandoc-3.13/bin/pandoc --version` in `pandoc 3.13`. Chạy `pandoc --help | grep -i highlight` để biết tên option highlight của bản 3.13 (`--syntax-highlighting` hoặc `--highlight-style`), dùng tên đó ở Step 5.

- [ ] **Step 3: Chạy `npm test`, kỳ vọng FAIL ở `reference-docx.test.mjs`**

- [ ] **Step 4: Viết `reference-docx.mjs` và `make-reference.mjs`** (ghi `build/book/reference.docx`). Chạy `npm test`, kỳ vọng PASS.

- [ ] **Step 5: Viết hết `build.sh`, `metadata.yaml`, các file chương khung, `000-bia-va-muc-luc.md`, `README.md`**

Thứ tự trong `build.sh` (`set -euo pipefail`, chạy từ gốc repo):
1. Kiểm tra pandoc.
2. `npm ci` nếu chưa có `node_modules`.
3. `--evidence` thì chạy `capture-evidence.mjs --run`.
4. Chạy lần lượt `build-index.mjs`, `diagrams.mjs`, `make-reference.mjs`, `preprocess.mjs`.
5. Chạy `lint.mjs`, trừ khi có `--no-lint`.
6. Gọi pandoc: `build/book/md/*.md -f markdown -t docx --reference-doc build/book/reference.docx --resource-path build/book --metadata-file docs/book/metadata.yaml <option highlight>=tango -o build/book/hibernate-mapping-in-practice.docx`.

`000-bia-va-muc-luc.md`:
- Nội dung bìa (spec 8.8): các khối `custom-style` Cover, hình `img/domain.png` không có alt, `{width=60%}`.
- Ngắt trang bằng ```` ```{=openxml} ```` `<w:p><w:r><w:br w:type="page"/></w:r></w:p>`.
- Đoạn `Muc luc` "Mục lục tóm tắt", rồi field `TOC \o "1-2" \h \z \u`. Chữ giữ chỗ của field: "Mở file bằng Word và chọn Update Field (F9) để hiện mục lục."
- Ngắt trang, rồi "Mục lục chi tiết" với `TOC \o "1-4" \h \z \u`.

Metadata: `title: Hibernate Mapping in Practice`, `author: Mai Tuấn Anh`, `lang: vi-VN`.

- [ ] **Step 6: Chạy `docs/book/build.sh`, kỳ vọng tạo được file .docx, lint không lỗi**

Kiểm tra bằng `python3 -m zipfile -e build/book/hibernate-mapping-in-practice.docx build/book/unzipped/`, rồi grep:
- `word/document.xml` có `TOC \o "1-2"`, `TOC \o "1-4"`, `CoverTitle`, `Mai Tuấn Anh`;
- có `word/footer*.xml` chứa `PAGE`;
- `word/settings.xml` có `updateFields`.

Nếu pandoc không giữ footer hoặc `titlePg` từ reference, viết thêm `tools/postprocess-docx.mjs` để chèn vào file .docx đầu ra (dùng lại các hàm XML của `reference-docx.mjs`, kèm test), rồi build lại.

- [ ] **Step 7: (Tùy chọn) Xem trước bằng PDF.** Tải LibreOffice AppImage (bản "still" ở https://www.libreoffice.org/download/appimage/) vào `build/book/tools/`, dùng `--appimage-extract` nếu không có FUSE. `to-pdf.sh` chạy `soffice --headless --convert-to pdf --outdir build/book build/book/hibernate-mapping-in-practice.docx`. Xem trang bìa và trang mục lục bằng Read tool (PDF). Không tải được thì bỏ qua bước này và ghi chú; người học sẽ duyệt bằng Word ở Task 6.

---

## Quy trình viết một chương (QTC): áp dụng cho Task 6–14

1. **Test thất bại trước:** đổi `status` của chương thành `"done"` trong `chapters.json`, chạy `node tools/lint.mjs --chapter <id>`, kỳ vọng FAIL với danh sách mã `coverage` còn thiếu.
2. **Đọc nguồn**, theo thứ tự: README của nhóm; **method test** của từng mã (xem `index.json`); assert là sự thật, không lấy từ 02-SCENARIOS. Sau đó đọc entity trong `entityPackages`, các dòng `RESULTS.md` liên quan, và `build/book/evidence/<code>.md`.
3. **Sơ đồ:** mỗi mục `.bien-the` có ít nhất một `docs/book/diagrams/<nhóm>-<biến thể>.json` (id viết thường, ví dụ `g1-v4`; schema lấy theo README nhóm). Chạy `node tools/diagrams.mjs`, rồi xem PNG bằng Read tool.
4. **Viết** theo khuôn spec mục 7 (mức chi tiết 7.2a, bổ sung JPA 7.2b) và cú pháp Markdown ở đầu plan. Mẫu mức chi tiết: Chương 4, 2, 3 đã commit. Đoạn "Trong JPA thuần." là run-in label `**Trong JPA thuần.**` đặt sau `**Vì sao.**`:
   - Code entity chép từ source, đổi tab sang 4 khoảng trắng, mỗi dòng tối đa 85 ký tự, bỏ header comment dài và getter/setter không liên quan.
   - Code ví dụ rút gọn từ test: giữ GIVEN, WHEN và `inTx(...)`; bỏ assert và lệnh in.
   - SQL và bảng dữ liệu chép từ evidence. Bảng "Trước" và "Sau" in đậm giá trị đổi, kèm hai dòng "Thay đổi: ..." và "Giữ nguyên: ...". Không đưa id cụ thể vào kết luận.
   - Trạng thái entity ghi bằng tên đầy đủ (TRANSIENT, MANAGED, DETACHED, REMOVED).
   - Số câu SQL viết thành chữ ("1 câu INSERT customer và 2 câu INSERT purchase_order"), không viết "1 + 2 INSERT".
   - Mã có `differs` thì thêm khối `.khac-du-doan`.
5. **Lint xanh:** `node tools/lint.mjs --chapter <id>` không còn lỗi.
6. **Đối chiếu sự thật:** với từng khối `.tracuu`, so mọi con số (số câu SELECT, INSERT, UPDATE, DELETE, số dòng) và mọi kết luận trong "Kết quả" với assert của method test. Lệch thì sửa sách cho đúng test.
7. **Build:** `docs/book/build.sh` thành công. Nếu đã có `to-pdf.sh` thì xem vài trang của chương.

---

### Task 6: Chương mẫu, Chương 4 và CHECKPOINT

**Files:**
- Create: `docs/book/diagrams/g1-v1.json`, `g1-v1nn.json`, `g1-v2.json`, `g1-v3.json`, `g1-v4.json`, `g1-v5.json`, `g1-v6.json`
- Modify: `docs/book/chapters/021-chuong-04-khoa-ngoai.md`, `docs/book/chapters.json` (`04` thành done)

**Interfaces:**
- Consumes: toàn bộ pipeline (Task 1–5), QTC

Dàn ý (mục `.bien-the` là mục có sơ đồ và ưu nhược điểm; trong ngoặc là mã và tier):
- 4.1 Owning side, inverse side và nơi đặt khóa ngoại (khối `.kien-thuc-nen`, không có mã)
- 4.2 ManyToOne một chiều `{.bien-the}`: S01, S02, S03 (cốt lõi), S05, S18a/b (mở rộng)
- 4.3 Bắt buộc hay tùy chọn: mặc định, nullable bằng false, optional bằng false `{.bien-the}`: S04a–e (có `.khac-du-doan` cho S04b và S04e)
- 4.4 Map một cột khóa ngoại hai lần `{.bien-the}`: S06a–c
- 4.5 OneToMany một chiều không có JoinColumn: sinh bảng nối `{.bien-the}`: S07a/b, S08
- 4.6 OneToMany một chiều có JoinColumn: INSERT rồi UPDATE `{.bien-the}`: S09a/b, S11 (cốt lõi), S10a/b (mở rộng)
- 4.7 Hai chiều chuẩn: OneToMany có mappedBy và ManyToOne `{.bien-the}`: S12, S13, S14a/b (cốt lõi), S17 (mở rộng)
- 4.8 Quên mappedBy: hai owning side `{.bien-the}`: S15a–c
- 4.9 Owning side nằm ở phía một `{.bien-the}`: S16a/b (mở rộng)
- 4.10 Tổng kết: nên đặt quan hệ ở bảng cha hay bảng con (bảng so sánh từ README G1, viết lại không ký hiệu)

Sơ đồ (đều là schema `g1_*` trong README G1):
- `g1-v1`: customer, purchase_order, address
- `g1-v1nn`: customer, purchase_order
- `g1-v2`: customer, customer_orders, purchase_order
- `g1-v3`: customer, purchase_order
- `g1-v4`: customer, purchase_order
- `g1-v5`: customer, customer_orders, purchase_order
- `g1-v6`: customer, purchase_order

- [ ] **Step 1–7: Làm theo QTC bước 1 đến 7 cho chương `04`**

- [ ] **Step 8: CHECKPOINT: dừng, gửi người học đường dẫn file .docx** (và PDF nếu có) để mở bằng Word. Xin góp ý về:
  - trang bìa, hai mục lục (nhớ nói cần Update Field);
  - khuôn tình huống và độ dài;
  - sơ đồ, code, các khung.

  Báo kèm các mã phát sinh ở Task 1 Step 10 (nếu có). **Không làm tiếp khi chưa được duyệt.**

- [ ] **Step 9: Sửa theo góp ý.** Góp ý về định dạng chung thì sửa ở tầng công cụ (style, preprocess, lint) để các chương sau tự hưởng. Chạy `npm test` và `docs/book/build.sh`, kỳ vọng PASS.

- [ ] **Step 10: Commit**

```bash
git add .gitignore docs/superpowers docs/book
git commit -m "docs(book): pipeline build sách và chương mẫu (Chương 4)"
```

---

### Task 7: Phần I (Chương 1, 2, 3)

**Files:** `docs/book/chapters/011-…`, `012-…`, `013-…`, các file `diagrams/*.json` mới, `chapters.json`

Dàn ý:
- **Chương 1** (không có mã; mọi mục đều ngắn):
  - 1.1 Domain thương mại điện tử (hình `domain`)
  - 1.2 Nhắc nhanh entity và các annotation cơ bản (`.kien-thuc-nen`)
  - 1.3 Khóa chính, khóa ngoại và cách Hibernate sinh id
  - 1.4 Lab chứng minh một tình huống như thế nào (Testcontainers, đếm SQL, đọc DB bằng JDBC, `inTx(...)`, cách đọc khung Tra cứu)
- **Chương 2:**
  - 2.1 Bốn trạng thái của entity (`.kien-thuc-nen`)
  - 2.2 persist và flush: S01, S02
  - 2.3 Dirty checking và entity detached: S03, S04
  - 2.4 merge: S05, S13
  - 2.5 remove: S06, S07
  - 2.6 getReference và find: S08
  - 2.7 Cache cấp 1: S09
  - 2.8 Flush tự động trước query: S10
  - 2.9 save của Spring Data và isNew: S11
  - 2.10 Thứ tự SQL khi flush. Mục này không có mã; viết dựa trên test G3-S10, trỏ sang mục tương ứng ở Chương 6 bằng số mục, không ghi mã.
- **Chương 3:**
  - 3.1 Embeddable và AttributeOverride `{.bien-the}`: G12-S01..S03
  - 3.2 Enum và AttributeConverter `{.bien-the}`: G12-S11
  - 3.3 NaturalId: G12-S10

- [ ] **Step 1: QTC cho chương 01**
- [ ] **Step 2: QTC cho chương 02**
- [ ] **Step 3: QTC cho chương 03**
- [ ] **Step 4: `npm test` và `docs/book/build.sh` PASS. Báo người học: các chương đã xong, các điểm cần chú ý.**
- [ ] **Step 5: Commit** `docs(book): Phần I — nền tảng (Chương 1–3)`

### Task 8: Phần II, Chương 5 và 6 (cascade, orphanRemoval)

Dàn ý:
- **Chương 5:**
  - 5.1 Cascade là gì và đặt ở đâu (`.kien-thuc-nen`)
  - 5.2 Không cascade `{.bien-the}`: S01
  - 5.3 CascadeType.PERSIST `{.bien-the}`: S02, S03, S11
  - 5.4 CascadeType.MERGE `{.bien-the}`: S04
  - 5.5 CascadeType.REMOVE và OnDelete ở database `{.bien-the}`: S05, S10, S13
  - 5.6 DETACH và REFRESH: ALL so với chỉ PERSIST cộng MERGE `{.bien-the}`: S06, S07
  - 5.7 Cascade đặt trên ManyToOne `{.bien-the}`: S08, S09
  - 5.8 Tổng kết: ma trận thao tác và cascade (lấy từ README G2)
- **Chương 6:**
  - 6.1 Con mồ côi là gì; so sánh REMOVE và orphanRemoval (`.kien-thuc-nen` cộng bảng so sánh)
  - 6.2 Không có orphanRemoval `{.bien-the}`: S01, S02
  - 6.3 Có orphanRemoval `{.bien-the}`: S03, S04, S05, S07, S08
  - 6.4 orphanRemoval mà không có cascade `{.bien-the}`: S06
  - 6.5 Chuyển con sang cha khác và thay con trùng unique: S09, S10 (giải thích thứ tự ActionQueue)
  - 6.6 Merge đồ thị detached: S11
  - 6.7 Bốn chiến lược xóa Customer, Order, Item: G9-S06 (bảng so sánh số SQL và thứ tự SQL)

- [ ] **Step 1: QTC cho chương 05** · **Step 2: QTC cho chương 06** · **Step 3: test và build PASS, báo người học** · **Step 4: Commit** `docs(book): Phần II — cascade và orphanRemoval (Chương 5–6)`

### Task 9: Phần II, Chương 7 đến 10

Dàn ý:
- **Chương 7:**
  - 7.1 Khóa ngoại đặt ở bảng nào `{.bien-the}`: S01, S02, S11
  - 7.2 Hai chiều và LAZY ở phía inverse `{.bien-the}`: S03, S04, S05
  - 7.3 MapsId: dùng chung khóa chính `{.bien-the}`: S06, S07, S08, S13
  - 7.4 orphanRemoval một-một `{.bien-the}`: S09, S10
  - 7.5 optional bằng false trên OneToOne: G6-S12, khối `.chua-kiem-chung`, nhắc lại kết quả ManyToOne ở mục 4.3
- **Chương 8:**
  - 8.1 Owner của bảng nối `{.bien-the}`: S01, S02, S03
  - 8.2 Quên mappedBy: hai bảng nối `{.bien-the}`: S04
  - 8.3 Set, List và OrderColumn `{.bien-the}`: S05, S06, S09
  - 8.4 Xóa và cascade trên ManyToMany: S07, S08
  - 8.5 Entity trung gian có cột phụ `{.bien-the}`: S10, S11, S12
  - 8.6 Khi nào nên bỏ ManyToMany: G7-S14, lấy từ README G7
- **Chương 9:**
  - 9.1 EmbeddedId và IdClass (`.kien-thuc-nen`)
  - 9.2 Khóa ngoại hai cột với JoinColumns `{.bien-the}`: S01, S02, S03
  - 9.3 Derived identity với MapsId theo field `{.bien-the}`: S04, S05
  - 9.4 Khóa ngoại trỏ vào natural key `{.bien-the}`: S06
  - 9.5 Không được sửa khóa chính: S07
  - Biến thể IdClass gắn vào mục của mã có test dùng nó (xem `index.json`).
- **Chương 10:**
  - 10.1 Cây danh mục tự tham chiếu `{.bien-the}`: G9-S01
  - 10.2 ElementCollection so với OneToMany `{.bien-the}`: G12-S04, S05
  - 10.3 Ba chiến lược kế thừa `{.bien-the}`: G12-S06, S07, S08
  - 10.4 Quan hệ trỏ tới lớp cha trừu tượng: G12-S09

- [ ] **Step 1–4: QTC cho chương 07, 08, 09, 10** · **Step 5: test và build PASS, báo người học** · **Step 6: Commit** `docs(book): Phần II — OneToOne, ManyToMany, khóa ghép, mô hình đặc biệt (Chương 7–10)`

### Task 10: Phần III (Chương 11, 12, 13)

Dàn ý:
- **Chương 11:**
  - 11.1 FetchType mặc định và proxy (`.kien-thuc-nen`)
  - 11.2 Để mặc định: ManyToOne là EAGER `{.bien-the}`: S01, S02
  - 11.3 Mọi quan hệ LAZY `{.bien-the}`: S03, S04, S14
  - 11.4 N+1 trên collection và bốn cách sửa `{.bien-the}`: S05, S06, S07, S08, S09
  - 11.5 Fetch nhiều collection cùng lúc: S10, S11
  - 11.6 Phân trang kèm fetch collection: S12
  - 11.7 Collection EAGER `{.bien-the}`: S13
  - 11.8 N+1 qua ManyToOne và qua ManyToMany: S16, G7-S13
  - Trỏ tới mục 7.2 cho trường hợp OneToOne phía inverse.
- **Chương 12:**
  - 12.1 join và join fetch: G10-S01, S02, S03
  - 12.2 Native query có và không có kiểu entity: S04, S05
  - 12.3 Phân trang với Page và count query: S08
  - 12.4 Criteria và Specification cho bộ lọc động: S09
  - 12.5 DTO projection: G5-S15, G10-S10
- **Chương 13:**
  - 13.1 Formula: G12-S12
  - 13.2 SQLRestriction: G12-S13
  - 13.3 Filter: G12-S14
  - 13.4 SoftDelete: G9-S11, G10-S07
  - 13.5 So sánh bốn cách lọc

- [ ] **Step 1–3: QTC cho chương 11, 12, 13** · **Step 4: test và build PASS, báo người học** · **Step 5: Commit** `docs(book): Phần III — đọc dữ liệu (Chương 11–13)`

### Task 11: Phần IV (Chương 14 đến 17)

Dàn ý:
- **Chương 14:**
  - 14.1 Cách kiểm chứng "bảng cha không đổi" (snapshot dòng cha)
  - 14.2 Sửa con qua cha: có và không có cascade MERGE `{.bien-the}`: G4-S01, S02, S03
  - 14.3 Thêm và xóa con qua collection mappedBy: S04, S05, S08
  - 14.4 Collection phía owning và OptimisticLock excluded `{.bien-the}`: S06, S07
  - 14.5 Chủ động tăng version của cha: S09
  - 14.6 DynamicUpdate `{.bien-the}`: S10
  - 14.7 Đi qua cha hay đi thẳng repository của con: S12
  - 14.8 Cập nhật đơn hàng từ request: cách đúng và cách làm mất dữ liệu: G9-S02
  - 14.9 Chuyển item giữa hai đơn và tính lại tổng: G9-S09
- **Chương 15:**
  - 15.1 Bulk JPQL và persistence context bị lệch: G9-S05, G4-S13
  - 15.2 Bulk delete bỏ qua cascade: G2-S12
  - 15.3 Native UPDATE: G10-S06
  - 15.4 Quy tắc dùng bulk an toàn
- **Chương 16:**
  - 16.1 @Transactional hoạt động qua proxy (`.kien-thuc-nen`)
  - 16.2 readOnly: G0-S12, G11-S01
  - 16.3 Self-invocation: G0-S14
  - 16.4 Propagation REQUIRED và REQUIRES_NEW: G11-S02, S03
  - 16.5 Exception nào thì rollback: G11-S04
  - 16.6 Service trả entity hay DTO: G9-S07
  - 16.7 Open Session in View: G11-S12
- **Chương 17:**
  - 17.1 Lost update: G11-S05
  - 17.2 Ba cách sửa lost update: S06
  - 17.3 Optimistic lock khi hai người cùng sửa: G4-S11, G9-S08
  - 17.4 Pessimistic lock và NOWAIT: G11-S07, S08, S11
  - 17.5 Hàng đợi job với SKIP LOCKED: S09
  - 17.6 Deadlock và cách khóa theo thứ tự: S10

- [ ] **Step 1–4: QTC cho chương 14, 15, 16, 17** · **Step 5: test và build PASS, báo người học** · **Step 6: Commit** `docs(book): Phần IV — ghi dữ liệu và đồng thời (Chương 14–17)`

### Task 12: Phần V (Chương 18, 19, 20)

Dàn ý:
- **Chương 18:**
  - 18.1 Batch insert với SEQUENCE: G13-S01
  - 18.2 IDENTITY làm tắt batch: S02
  - 18.3 allocationSize: S03
  - 18.4 Persistence context phình to, flush và clear theo lô: S04
  - 18.5 StatelessSession: S05
  - 18.6 Ghi chú về order_inserts và order_updates (README G13, không có mã)
- **Chương 19:**
  - 19.1 Second-level cache hoạt động thế nào (`.kien-thuc-nen`)
  - 19.2 Entity cache: S06
  - 19.3 Bulk và native làm hỏng cache: S07
  - 19.4 Collection cache: S08
  - 19.5 Query cache: S09
  - 19.6 Cache READ_ONLY: S10
- **Chương 20:**
  - 20.1 Vì sao enhancement nằm ở subproject riêng (`lab-enhanced/`, `G13EnhancementGuardTest`)
  - 20.2 LAZY ở phía inverse của OneToOne: S11
  - 20.3 Basic LAZY cho cột lớn: S12
  - 20.4 Dirty tracking: S13

- [ ] **Step 1–3: QTC cho chương 18, 19, 20** · **Step 4: test và build PASS, báo người học** · **Step 5: Commit** `docs(book): Phần V — hiệu năng (Chương 18–20)`

### Task 13: Phần VI (Chương 21, 22, 23)

Dàn ý:
- **Chương 21:**
  - 21.1 equals và hashCode theo id sinh tự động trong HashSet: G9-S03
  - 21.2 equals với proxy (phần ➕ của S03)
  - 21.3 toString đệ quy trong quan hệ hai chiều: G9-S04
  - 21.4 Id gán tay và Persistable: không có mã, trỏ tới mục 2.9 và mục 9.3
- **Chương 22:** mỗi sự cố là một mục.
  - Các mục: 22.1 Danh sách đơn hàng chậm dần (I1); 22.2 Tồn kho sai sau flash sale (I2); 22.3 Job gửi email chạy hai lần (I3); 22.4 Import file lớn chậm dần (I4).
  - Mỗi mục có 4 tình huống H4 theo S01 đến S04: tái hiện, chẩn đoán, sửa, rào chắn.
  - Đoạn mở của mỗi mục gồm: triệu chứng, nguyên nhân, và chương kiến thức liên quan.
- **Chương 23:**
  - 23.1 Test ngân sách SQL: R1-S01..S03
  - 23.2 Lint mapping bằng Metamodel: R2-S01..S03
  - 23.3 Checklist review PR có JPA (viết lại từ `LEAD-PLAYBOOK.md` mục 1, bỏ ký hiệu)
  - 23.4 Năm ADR mẫu (tóm tắt mỗi ADR: bối cảnh, quyết định, hệ quả, chương chứng minh)

- [ ] **Step 1–3: QTC cho chương 21, 22, 23** · **Step 4: test và build PASS, báo người học** · **Step 5: Commit** `docs(book): Phần VI — thực chiến (Chương 21–23)`

### Task 14: Phần VII (Chương 24, 25)

Mỗi mục của hai chương này đặt trong khối `.chua-kiem-chung`, không đưa số SQL cụ thể, và dẫn nguồn là tài liệu chính thức (URL in đầy đủ). Tình huống dùng `{.mo-rong}` không có `.tracuu`, nên lint `template` phải cho phép điều này ở chương không có `scenarios`. Phải thêm test cho trường hợp này ở `lint.test.mjs` trước khi viết.

Dàn ý:
- **Chương 24:**
  - 24.1 Flyway hoặc Liquibase thay cho ddl-auto
  - 24.2 UUID làm khóa chính
  - 24.3 Cột JSON
  - 24.4 Auditing: CreatedDate và Envers
  - 24.5 Multi-tenancy
  - 24.6 Optimistic lock qua REST (version trong DTO)
  - 24.7 Sửa giá nhưng khách vẫn thấy giá cũ: G14-I5, trỏ tới mục 15.3 và 19.3
- **Chương 25:**
  - 25.1 Các cách viết truy vấn trong JPA: JPQL và HQL, Criteria, native SQL
  - 25.2 Công cụ khác: JdbcTemplate, Spring Data JDBC, MyBatis, jOOQ, Querydsl, Hibernate Reactive
  - 25.3 Bảng so sánh: ai viết SQL, có persistence context không, lazy loading, khi nào nên chọn
  - 25.4 Lộ trình học tiếp: Hibernate User Guide, Spring Data JPA reference, sách High-Performance Java Persistence, và các chủ đề nên học tiếp

- [ ] **Step 1: Thêm test lint cho chương không có `scenarios`, chạy FAIL rồi sửa `lint.mjs` cho PASS** · **Step 2–3: QTC (bỏ bước 6 đối chiếu test) cho chương 24, 25** · **Step 4: test và build PASS, báo người học** · **Step 5: Commit** `docs(book): Phần VII — mở rộng tầm nhìn (Chương 24–25)`

### Task 15: Phụ lục, Lời nói đầu, kiểm tra cuối và Pull Request

**Files:** `docs/book/chapters/005-loi-noi-dau.md`, `091-…` đến `096-…`, `chapters.json`

- [ ] **Step 1: Phụ lục A**: mỗi annotation trong `CHEATSHEET.md` một bảng nhỏ (mặc định, đặt ở đâu, ảnh hưởng DDL, ảnh hưởng SQL, bẫy, mục trong sách). Dòng "mã tình huống" đổi thành số mục trong sách. Viết lại không ký hiệu.
- [ ] **Step 2: Phụ lục B**: mỗi dòng "KHÁC" trong `RESULTS.md` gồm: điều nhiều người nghĩ, điều thực tế xảy ra, mục trong sách.
- [ ] **Step 3: Phụ lục C**: chỉ chứa `::: {.phu-luc-c}` `:::`. Kiểm tra bảng sinh ra có đủ mọi mã trong index.
- [ ] **Step 4: Phụ lục D**: lấy từ `gh pr list --state all --limit 40 --json number,title,url,body`. Mỗi PR có số, tiêu đề, một dòng nội dung, URL.
- [ ] **Step 4b: Chương 1 mục 1.5 và Phụ lục G.** Thêm vào `011-chuong-01-mo-hinh-mau.md` mục `### 1.5 JPA và Hibernate: cùng một việc, hai cách viết` (bảng `EntityManager` và `Session`, JPQL và HQL, `jakarta.persistence` và `org.hibernate.annotations`; quy ước ví dụ). Viết Phụ lục G (`097-phu-luc-g-jpa-hibernate.md`): quét toàn sách tìm mọi đoạn `**Trong JPA thuần.**` và API riêng của Hibernate, lập bảng đối chiếu kèm mục trong sách. Bổ sung entry `pl-g` vào `chapters.json` (đã có sẵn).
- [ ] **Step 5: Phụ lục E** (lấy từ `README.md` gốc và CLAUDE.md: lệnh Gradle, `--rerun`, `-Pinspect`, docker compose, `lab-enhanced`); **Phụ lục F** (mọi thuật ngữ tiếng Anh đã dùng, nghĩa tiếng Việt, mục gặp lần đầu).
- [ ] **Step 6: Lời nói đầu**: sách dành cho ai; 4 bằng chứng; cách đọc một tình huống (kèm một ví dụ đã thu nhỏ); ý nghĩa các nhãn và các khung; hai lộ trình đọc (người mới thì Phần I đến III theo thứ tự; người đang gặp sự cố thì vào Chương 22 và Phụ lục C); version; cách cập nhật mục lục trong Word.
- [ ] **Step 7: Kiểm tra cuối**: mọi chương `done`; `npm test` PASS; `docs/book/build.sh --evidence` PASS (chạy lại suite để evidence mới nhất). `node tools/lint.mjs` không lỗi, và không mã nào thiếu hoặc bị gán trùng. Nếu có PDF thì xem lướt toàn bộ và ghi lại số trang.
- [ ] **Step 8: Commit** `docs(book): phụ lục, lời nói đầu, hoàn thiện sách`
- [ ] **Step 9: Push và tạo PR**: chạy `git push -u origin docs/book`, rồi `gh pr create` với description tiếng Việt có dấu. Description gồm:
  - mỗi Phần: mục đích, cách trình bày, các chương;
  - cách build sách và yêu cầu công cụ;
  - số trang, số tình huống đã phủ, danh sách mục "Chưa kiểm chứng trong lab";
  - kết thúc bằng dòng `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
