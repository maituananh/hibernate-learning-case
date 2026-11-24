import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import JSZip from 'jszip';
import { PANDOC } from '../lib/paths.mjs';
import { buildReferenceDocx } from '../lib/reference-docx.mjs';

// Cần pandoc đã tải về build/book/tools (docs/book/build.sh tự tải).
let out;
before(async () => {
  const defaultDocx = spawnSync(PANDOC, ['--print-default-data-file', 'reference.docx']).stdout;
  out = await JSZip.loadAsync(await buildReferenceDocx(defaultDocx));
});

const part = (name) => out.file(name).async('string');

test('có đủ style riêng của sách', async () => {
  const styles = await part('word/styles.xml');
  for (const id of ['GhiNho', 'TraCuu', 'KetQuaKhacDuDoan', 'ChuaKiemChung', 'KienThucNen',
    'Nhan', 'CoverTitle', 'CoverSubtitle', 'CoverAuthor', 'CoverMeta', 'MucLuc', 'SourceCode']) {
    assert.match(styles, new RegExp(`w:styleId="${id}"`), id);
  }
  assert.match(styles, /<w:name w:val="Ghi nho"\/>/);
  assert.match(styles, /<w:name w:val="Tra cuu"\/>/);
});

test('font: Cambria cho thân bài, Calibri cho heading, Consolas cho code', async () => {
  const styles = await part('word/styles.xml');
  assert.match(styles, /w:styleId="Normal"[\s\S]*?w:ascii="Cambria"/);
  assert.match(styles, /w:styleId="Heading2"[\s\S]*?w:ascii="Calibri"[\s\S]*?<\/w:style>/);
  assert.match(styles, /w:styleId="VerbatimChar"[\s\S]*?w:ascii="Consolas"[\s\S]*?<w:sz w:val="18"\/>/);
});

test('Phần và Chương sang trang mới, giữ outline level cho mục lục', async () => {
  const styles = await part('word/styles.xml');
  const heading = (id) => new RegExp(`<w:style [^>]*w:styleId="${id}">[\\s\\S]*?</w:style>`).exec(styles)[0];
  assert.match(heading('Heading1'), /<w:pageBreakBefore\/>/);
  assert.match(heading('Heading2'), /<w:pageBreakBefore\/>/);
  assert.match(heading('Heading2'), /<w:outlineLvl w:val="1"\/>/);
  assert.doesNotMatch(heading('Heading3'), /pageBreakBefore/);
});

test('mỗi style chỉ có một định nghĩa', async () => {
  const ids = [...(await part('word/styles.xml')).matchAll(/w:styleId="([^"]+)"/g)].map((m) => m[1]);
  assert.equal(new Set(ids).size, ids.length);
});

test('A4, lề 2.5 cm, trang đầu khác, footer số trang', async () => {
  const doc = await part('word/document.xml');
  assert.match(doc, /<w:pgSz w:w="11906" w:h="16838"\/>/);
  assert.match(doc, /<w:pgMar w:top="1418" w:right="1418" w:bottom="1418" w:left="1418"/);
  assert.match(doc, /<w:titlePg\/>/);
  assert.match(doc, /<w:footerReference w:type="default" r:id="rIdBookFooter"\/>/);
  assert.match(await part('word/footer1.xml'), /PAGE/);
  assert.match(await part('word/_rels/document.xml.rels'), /Id="rIdBookFooter"[^>]*Target="footer1.xml"/);
  assert.match(await part('[Content_Types].xml'), /PartName="\/word\/footer1.xml"/);
});

test('updateFields nằm trước footnotePr', async () => {
  const settings = await part('word/settings.xml');
  assert.match(settings, /<w:updateFields w:val="true"\/>\s*<w:footnotePr/);
});
