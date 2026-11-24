// Mã tình huống của lab: G1-S04b, G14-I2-S03, G14-R1-S01.
// "base" là mã bỏ chữ cái cuối (G1-S04b có base G1-S04), dùng để gán tình huống vào chương.

const CODE_AT_START = /^(G\d+(?:-[IR]\d+)?-S\d+)([a-z]?)\s*\|/;

export function parseCode(name) {
  const match = CODE_AT_START.exec(name);
  if (!match) return null;
  return { code: match[1] + match[2], base: match[1] };
}

// "G1-S01..S18" mở thành G1-S01 ... G1-S18; "G0-S12" giữ nguyên.
export function expandRange(spec) {
  const range = /^(.*-S)(\d+)\.\.S(\d+)$/.exec(spec);
  if (!range) return [spec];
  const [, prefix, from, to] = range;
  const width = from.length;
  const result = [];
  for (let n = Number(from); n <= Number(to); n++) {
    result.push(prefix + String(n).padStart(width, '0'));
  }
  return result;
}

export function groupOf(code) {
  return /^G\d+/.exec(code)[0];
}
