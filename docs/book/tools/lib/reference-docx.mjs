// Sinh reference.docx cho pandoc: lấy bản mặc định của pandoc rồi thay style, khổ giấy, footer.
// pandoc lấy style, sectPr (khổ giấy, lề, footer) và settings từ file này khi build ra .docx.
import JSZip from 'jszip';

const W = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main';
const R = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships';

const font = (name) => `<w:rFonts w:ascii="${name}" w:hAnsi="${name}" w:cs="${name}" w:eastAsia="${name}"/>`;
const size = (halfPoints) => `<w:sz w:val="${halfPoints}"/><w:szCs w:val="${halfPoints}"/>`;
const bold = '<w:b/><w:bCs/>';
const italic = '<w:i/><w:iCs/>';
const color = (hex) => `<w:color w:val="${hex}"/>`;
const shade = (hex) => `<w:shd w:val="clear" w:color="auto" w:fill="${hex}"/>`;
const spacing = (before, after, line = 276) =>
  `<w:spacing w:before="${before}" w:after="${after}" w:line="${line}" w:lineRule="auto"/>`;
const border = (side, val, sz, hex) => `<w:${side} w:val="${val}" w:sz="${sz}" w:space="6" w:color="${hex}"/>`;
const leftBar = (hex, val = 'single') => `<w:pBdr>${border('left', val, 24, hex)}</w:pBdr>`;
const box = (hex) => `<w:pBdr>${['top', 'left', 'bottom', 'right'].map((s) => border(s, 'single', 4, hex)).join('')}</w:pBdr>`;
const indent = (left, right = 0) => `<w:ind w:left="${left}" w:right="${right}"/>`;

// Thứ tự phần tử trong <w:style> và <w:pPr>/<w:rPr> phải đúng schema thì Word mới không báo lỗi file.
function style({ type = 'paragraph', id, name, custom = true, isDefault = false, basedOn, next, link, extra = '', pPr = '', rPr = '', tail = '' }) {
  return `<w:style w:type="${type}"${isDefault ? ' w:default="1"' : ''}${custom ? ' w:customStyle="1"' : ''} w:styleId="${id}">` +
    `<w:name w:val="${name}"/>` +
    (basedOn ? `<w:basedOn w:val="${basedOn}"/>` : '') +
    (next ? `<w:next w:val="${next}"/>` : '') +
    (link ? `<w:link w:val="${link}"/>` : '') +
    extra + '<w:qFormat/>' +
    (pPr ? `<w:pPr>${pPr}</w:pPr>` : '') +
    (rPr ? `<w:rPr>${rPr}</w:rPr>` : '') +
    tail + '</w:style>';
}

const heading = (level, { pageBreak, before, after, align = '', size: sz, hex }) => style({
  id: `Heading${level}`, name: `heading ${level}`, custom: false, basedOn: 'Normal', next: 'BodyText',
  link: `Heading${level}Char`, extra: '<w:uiPriority w:val="9"/>',
  pPr: `<w:keepNext/><w:keepLines/>${pageBreak ? '<w:pageBreakBefore/>' : ''}${spacing(before, after, 240)}${align}<w:outlineLvl w:val="${level - 1}"/>`,
  rPr: `${font('Calibri')}${bold}${color(hex)}${size(sz)}`,
});

const calloutBox = (id, name, pBdr, fill, rPr = '') => style({
  id, name, basedOn: 'Normal',
  pPr: `<w:keepLines/>${pBdr}${shade(fill)}${spacing(40, 40)}${indent(284, 142)}`, rPr,
});

const centered = '<w:jc w:val="center"/>';

export const BOOK_STYLES = [
  style({ id: 'Normal', name: 'Normal', custom: false, isDefault: true,
    pPr: spacing(0, 120), rPr: `${font('Cambria')}${size(22)}<w:lang w:val="vi-VN"/>` }),
  style({ id: 'BodyText', name: 'Body Text', custom: false, basedOn: 'Normal', link: 'BodyTextChar',
    pPr: spacing(0, 120) }),
  heading(1, { pageBreak: true, before: 2400, after: 600, align: centered, size: 56, hex: '1F3864' }),
  heading(2, { pageBreak: true, before: 0, after: 360, size: 44, hex: '1F3864' }),
  heading(3, { pageBreak: false, before: 360, after: 120, size: 30, hex: '2E75B6' }),
  heading(4, { pageBreak: false, before: 280, after: 80, size: 25, hex: '1F3864' }),
  // Code: 9pt để 85 ký tự vừa khổ A4 lề 2.5 cm. Verbatim Char là style gốc của mọi run trong code.
  style({ type: 'character', id: 'VerbatimChar', name: 'Verbatim Char', basedOn: 'BodyTextChar',
    rPr: `${font('Consolas')}${size(18)}` }),
  style({ id: 'SourceCode', name: 'Source Code', basedOn: 'Normal', link: 'VerbatimChar',
    pPr: `<w:wordWrap w:val="off"/>${box('BFBFBF')}${shade('F3F3F3')}${spacing(60, 160, 240)}${indent(113, 113)}`,
    rPr: `${font('Consolas')}${size(18)}` }),
  style({ type: 'character', id: 'Hyperlink', name: 'Hyperlink', custom: false, basedOn: 'BodyTextChar',
    rPr: `${color('2E75B6')}<w:u w:val="single"/>` }),
  style({ type: 'table', id: 'Table', name: 'Table', custom: false, isDefault: true, basedOn: 'TableNormal',
    rPr: size(20),
    tail: '<w:tblPr><w:tblBorders>' +
      ['top', 'left', 'bottom', 'right', 'insideH', 'insideV']
        .map((s) => `<w:${s} w:val="single" w:sz="4" w:space="0" w:color="BFBFBF"/>`).join('') +
      '</w:tblBorders><w:tblCellMar><w:top w:w="40" w:type="dxa"/><w:left w:w="100" w:type="dxa"/>' +
      '<w:bottom w:w="40" w:type="dxa"/><w:right w:w="100" w:type="dxa"/></w:tblCellMar></w:tblPr>' +
      `<w:tblStylePr w:type="firstRow"><w:rPr>${bold}</w:rPr><w:tcPr>${shade('D9E2F3')}</w:tcPr></w:tblStylePr>` }),
  calloutBox('GhiNho', 'Ghi nho', leftBar('2E75B6'), 'EAF1FB'),
  style({ id: 'TraCuu', name: 'Tra cuu', basedOn: 'Normal',
    pPr: `<w:keepLines/>${box('BFBFBF')}${shade('F2F2F2')}${spacing(0, 20, 240)}${indent(113, 113)}`,
    rPr: `${font('Calibri')}${size(18)}` }),
  calloutBox('KetQuaKhacDuDoan', 'Ket qua khac du doan', leftBar('C55A11'), 'FBE9DD'),
  calloutBox('ChuaKiemChung', 'Chua kiem chung', leftBar('7F7F7F', 'dashed'), 'F2F2F2', italic),
  calloutBox('KienThucNen', 'Kien thuc nen', leftBar('70AD47'), 'EEF6E8'),
  style({ id: 'Nhan', name: 'Nhan', basedOn: 'Normal',
    pPr: `<w:keepNext/>${spacing(0, 80, 240)}`, rPr: `${font('Calibri')}${bold}${color('2E75B6')}${size(18)}` }),
  style({ id: 'CoverTitle', name: 'Cover Title', basedOn: 'Normal',
    pPr: `${spacing(3000, 240, 240)}${centered}`, rPr: `${font('Calibri')}${bold}${color('1F3864')}${size(72)}` }),
  style({ id: 'CoverSubtitle', name: 'Cover Subtitle', basedOn: 'Normal',
    pPr: `${spacing(0, 360)}${centered}`, rPr: `${font('Calibri')}${italic}${color('2E75B6')}${size(32)}` }),
  style({ id: 'CoverAuthor', name: 'Cover Author', basedOn: 'Normal',
    pPr: `${spacing(480, 240)}${centered}`, rPr: `${font('Calibri')}${bold}${size(36)}` }),
  style({ id: 'CoverMeta', name: 'Cover Meta', basedOn: 'Normal',
    pPr: `${spacing(0, 60)}${centered}`, rPr: `${font('Calibri')}${color('595959')}${size(22)}` }),
  style({ id: 'MucLuc', name: 'Muc luc', basedOn: 'Normal',
    pPr: spacing(0, 240), rPr: `${font('Calibri')}${bold}${color('1F3864')}${size(40)}` }),
];

const FOOTER = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:ftr xmlns:w="${W}" xmlns:r="${R}"><w:p><w:pPr><w:jc w:val="center"/></w:pPr>` +
  '<w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText xml:space="preserve"> PAGE </w:instrText></w:r>' +
  '<w:r><w:fldChar w:fldCharType="separate"/></w:r><w:r><w:t>1</w:t></w:r><w:r><w:fldChar w:fldCharType="end"/></w:r>' +
  '</w:p></w:ftr>';

// A4 (11906 x 16838 twips), lề 2.5 cm = 1418 twips; titlePg: trang đầu (bìa) không có footer.
const SECTION = '<w:sectPr><w:footerReference w:type="default" r:id="rIdBookFooter"/>' +
  '<w:footnotePr><w:numRestart w:val="eachSect"/></w:footnotePr>' +
  '<w:pgSz w:w="11906" w:h="16838"/>' +
  '<w:pgMar w:top="1418" w:right="1418" w:bottom="1418" w:left="1418" w:header="709" w:footer="709" w:gutter="0"/>' +
  '<w:titlePg/></w:sectPr>';

function replaceStyles(stylesXml) {
  let xml = stylesXml;
  const ids = BOOK_STYLES.map((s) => /w:styleId="([^"]+)"/.exec(s)[1]);
  for (const id of ids) {
    xml = xml.replace(new RegExp(`<w:style\\b[^>]*w:styleId="${id}"[^>]*>[\\s\\S]*?</w:style>\\s*`), '');
  }
  xml = xml.replace(/(<w:rPrDefault>\s*<w:rPr>\s*)<w:rFonts[^>]*\/>/, `$1${font('Cambria')}`);
  return xml.replace('</w:styles>', `${BOOK_STYLES.join('\n')}\n</w:styles>`);
}

export async function buildReferenceDocx(defaultDocx) {
  const zip = await JSZip.loadAsync(defaultDocx);
  const edit = async (name, fn) => zip.file(name, fn(await zip.file(name).async('string')));
  await edit('word/styles.xml', replaceStyles);
  await edit('word/document.xml', (xml) => xml.replace(/<w:sectPr>[\s\S]*?<\/w:sectPr>/, SECTION));
  await edit('word/settings.xml', (xml) => xml.replace('<w:footnotePr', '<w:updateFields w:val="true"/>\n  <w:footnotePr'));
  await edit('word/_rels/document.xml.rels', (xml) => xml.replace('</Relationships>',
    `<Relationship Type="${R}/footer" Id="rIdBookFooter" Target="footer1.xml"/></Relationships>`));
  await edit('[Content_Types].xml', (xml) => xml.replace('</Types>',
    '<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/></Types>'));
  zip.file('word/footer1.xml', FOOTER);
  return zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' });
}
