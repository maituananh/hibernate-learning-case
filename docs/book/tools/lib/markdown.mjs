// Duyệt Markdown theo dòng, biết dòng nào nằm trong code fence (``` hoặc ~~~).
// fence = ngôn ngữ của khối đang mở ('' nếu không có), null nếu dòng nằm ngoài code.

export function scanLines(md) {
  const result = [];
  let open = null;   // { marker, info }
  md.split('\n').forEach((text, i) => {
    const delimiter = /^(```+|~~~+)(.*)$/.exec(text);
    if (!open && delimiter) {
      open = { marker: delimiter[1], info: delimiter[2].trim() };
      result.push({ text, lineNo: i + 1, fence: null, delimiter: true });
    } else if (open && delimiter && delimiter[1].startsWith(open.marker) && delimiter[2].trim() === '') {
      open = null;
      result.push({ text, lineNo: i + 1, fence: null, delimiter: true });
    } else {
      result.push({ text, lineNo: i + 1, fence: open ? open.info : null, delimiter: false });
    }
  });
  return result;
}

// "{.tracuu codes="G1-S12" width=80%}" -> { classes: ['tracuu'], attrs: { codes: 'G1-S12' } }
export function parseAttributes(text) {
  const classes = [...text.matchAll(/\.([\w-]+)/g)].map((m) => m[1]);
  const attrs = {};
  for (const match of text.matchAll(/([\w-]+)="([^"]*)"/g)) attrs[match[1]] = match[2];
  return { classes, attrs };
}

export const DIV_OPEN = /^:::+\s*\{([^}]*)\}\s*$/;
export const DIV_CLOSE = /^:::+\s*$/;

export const codesOf = (value) => (value ?? '').split(',').map((c) => c.trim()).filter(Boolean);
