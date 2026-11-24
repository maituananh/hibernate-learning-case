// Đọc file JUnit XML do Gradle sinh ra (build/test-results/test/TEST-*.xml).
// Không cần thư viện XML: cấu trúc chỉ có <testcase> và <system-out> (nội dung nằm trong CDATA).

const ENTITIES = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" };

function decodeEntities(text) {
  return text.replace(/&(#x[0-9a-fA-F]+|#\d+|\w+);/g, (whole, entity) => {
    if (entity.startsWith('#x')) return String.fromCodePoint(parseInt(entity.slice(2), 16));
    if (entity.startsWith('#')) return String.fromCodePoint(parseInt(entity.slice(1), 10));
    return ENTITIES[entity] ?? whole;
  });
}

function attributesOf(text) {
  const attributes = {};
  for (const match of text.matchAll(/(\w+)="([^"]*)"/g)) {
    attributes[match[1]] = decodeEntities(match[2]);
  }
  return attributes;
}

// Nội dung CDATA chứa "]]>" bị Gradle tách thành "]]]]><![CDATA[>": nối các đoạn CDATA lại.
function systemOutOf(fragment) {
  const match = /<system-out>([\s\S]*?)<\/system-out>/.exec(fragment);
  if (!match) return null;
  const content = match[1];
  const sections = [...content.matchAll(/<!\[CDATA\[([\s\S]*?)\]\]>/g)].map((m) => m[1]);
  const text = sections.length > 0 ? sections.join('') : decodeEntities(content);
  return text.trim() === '' ? null : text;
}

// Thuộc tính có thể chứa ">", nên khớp từng cặp key="value" thay vì [^>]*.
const TESTCASE = /<testcase((?:\s+\w+="[^"]*")*)\s*(?:\/>|>([\s\S]*?)<\/testcase>)/g;

export function parseJUnitXml(xml) {
  const cases = [];
  for (const match of xml.matchAll(TESTCASE)) {
    const { name, classname } = attributesOf(match[1]);
    cases.push({ name, classname, systemOut: match[2] === undefined ? null : systemOutOf(match[2]) });
  }
  const suiteOut = systemOutOf(xml.replace(TESTCASE, '')) ?? '';
  return { cases, suiteOut };
}
