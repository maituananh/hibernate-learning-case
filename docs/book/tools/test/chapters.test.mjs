import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { BOOK_DIR, REPO_ROOT } from '../lib/paths.mjs';
import { expandRange } from '../lib/codes.mjs';
import { buildIndex } from '../lib/scenario-index.mjs';

const { chapters } = JSON.parse(fs.readFileSync(path.join(BOOK_DIR, 'chapters.json'), 'utf8'));
const index = buildIndex(REPO_ROOT);

test('mỗi mã có test thuộc đúng một chương', () => {
  const problems = [];
  for (const entry of index) {
    const owners = chapters.filter((chapter) =>
      (chapter.scenarios ?? []).flatMap(expandRange).includes(entry.base));
    if (owners.length !== 1) {
      problems.push(`${entry.code}: ${owners.map((c) => c.id).join(', ') || 'không chương nào'}`);
    }
  }
  assert.deepEqual(problems, []);
});

test('tên file và id không trùng', () => {
  assert.equal(new Set(chapters.map((c) => c.file)).size, chapters.length);
  assert.equal(new Set(chapters.map((c) => c.id)).size, chapters.length);
});
