// Bắt bằng chứng của từng tình huống từ file JUnit XML của Gradle.
//   node tools/capture-evidence.mjs --run   chạy lại cả suite (cần Docker) rồi đọc XML
//   node tools/capture-evidence.mjs         chỉ đọc XML đã có
// Ghi build/book/evidence/<mã>.md và build/book/evidence/_class/<class>.md
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { REPO_ROOT, BUILD_DIR } from './lib/paths.mjs';
import { parseCode } from './lib/codes.mjs';
import { buildIndex } from './lib/scenario-index.mjs';
import { parseJUnitXml } from './lib/junit-xml.mjs';
import { renderEvidence } from './lib/evidence.mjs';

const RESULT_DIRS = ['build/test-results/test', 'lab-enhanced/build/test-results/test'];
const EVIDENCE_DIR = path.join(BUILD_DIR, 'evidence');

function runSuite() {
  fs.mkdirSync(BUILD_DIR, { recursive: true });
  const logFile = path.join(BUILD_DIR, 'gradle-test.log');
  const log = fs.openSync(logFile, 'w');
  console.log(`Chạy ./gradlew test --rerun (log: ${logFile}) ...`);
  const result = spawnSync('./gradlew', [
    'test', '--rerun', '--init-script', 'docs/book/tools/capture-output.gradle',
  ], { cwd: REPO_ROOT, stdio: ['ignore', log, log] });
  fs.closeSync(log);
  const tail = fs.readFileSync(logFile, 'utf8').trimEnd().split('\n').slice(-5).join('\n');
  console.log(tail);
  if (result.status !== 0) {
    console.error(`Gradle thất bại (exit ${result.status}). Xem ${logFile}`);
    process.exit(1);
  }
}

if (process.argv.includes('--run')) runSuite();

const index = new Map(buildIndex(REPO_ROOT).map((entry) => [entry.code, entry]));
fs.rmSync(EVIDENCE_DIR, { recursive: true, force: true });
fs.mkdirSync(path.join(EVIDENCE_DIR, '_class'), { recursive: true });

const written = new Set();
const classFallback = [];
for (const dir of RESULT_DIRS) {
  const full = path.join(REPO_ROOT, dir);
  if (!fs.existsSync(full)) continue;
  for (const file of fs.readdirSync(full).filter((f) => f.endsWith('.xml'))) {
    const { cases, suiteOut } = parseJUnitXml(fs.readFileSync(path.join(full, file), 'utf8'));
    const className = file.replace(/^TEST-/, '').replace(/\.xml$/, '').split('.').pop();
    if (suiteOut.trim() !== '') {
      fs.writeFileSync(path.join(EVIDENCE_DIR, '_class', `${className}.md`),
        renderEvidence(undefined, `Output cấp class: ${className}`, suiteOut, false));
    }
    for (const testCase of cases) {
      const parsed = parseCode(testCase.name ?? '');
      if (!parsed) continue;
      if (testCase.systemOut === null) classFallback.push(parsed.code);
      fs.writeFileSync(path.join(EVIDENCE_DIR, `${parsed.code}.md`),
        renderEvidence(index.get(parsed.code), testCase.name, testCase.systemOut, true));
      written.add(parsed.code);
    }
  }
}

const missing = [...index.keys()].filter((code) => !written.has(code));
console.log(`evidence: ${written.size} file (index có ${index.size} mã)`);
if (classFallback.length > 0) console.log(`Không có output cấp test (${classFallback.length}): ${classFallback.join(', ')}`);
if (missing.length > 0) console.log(`Mã trong index chưa có evidence (${missing.length}): ${missing.join(', ')}`);
