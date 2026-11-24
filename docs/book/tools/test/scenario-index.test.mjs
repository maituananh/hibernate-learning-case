import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { REPO_ROOT } from '../lib/paths.mjs';
import {
  scanTests, readTiers, tierFor, readDiffers, prFor, buildIndex,
} from '../lib/scenario-index.mjs';

const read = (rel) => fs.readFileSync(path.join(REPO_ROOT, rel), 'utf8');
const entries = scanTests(REPO_ROOT);

test('G1-S12 trỏ đúng class, method, package entity', () => {
  const entry = entries.find((x) => x.code === 'G1-S12');
  assert.equal(entry.testClass, 'G1V4BidirectionalTest');
  assert.match(entry.method, /^s12_/);
  assert.ok(entry.entityPackages.includes(
    'src/main/java/com/example/hibernatelab/g1_owning_side/v4_bidi_mappedby/'));
});

test('mã duy nhất và mọi file test tồn tại', () => {
  assert.equal(new Set(entries.map((e) => e.code)).size, entries.length);
  for (const entry of entries) {
    assert.ok(fs.existsSync(path.join(REPO_ROOT, entry.testFile)), entry.code);
  }
});

test('có test của lab-enhanced', () => {
  assert.ok(entries.some((e) => e.code.startsWith('G13-S11')));
});

test('tier đọc từ 02-SCENARIOS', () => {
  const tiers = readTiers(read('docs/02-SCENARIOS.md'));
  assert.equal(tierFor('G1-S12', tiers), 'core');
  assert.equal(tierFor('G1-S05', tiers), 'extra');
  assert.equal(tierFor('G14-I1-S03', tiers), 'core');
  assert.equal(tierFor('G10-S07', tiers), 'extra');
});

test('khác dự đoán đọc từ RESULTS', () => {
  const differs = readDiffers(read('docs/RESULTS.md'));
  assert.ok(differs.has('G1-S04b'));
  assert.ok(differs.has('G1-S04e'));
  assert.ok(differs.has('G12-S11b'));
  assert.ok(differs.has('G10-S08a'));   // "Khớp một nửa" vẫn tính là khác
  assert.ok(!differs.has('G1-S08'));
});

test('PR theo nhóm', () => {
  assert.equal(prFor('G0-S01'), 1);
  assert.equal(prFor('G0-S02'), 2);
  assert.equal(prFor('G1-S12'), 3);
  assert.equal(prFor('G10-S01'), 14);
  assert.equal(prFor('G13-S10'), 17);
  assert.equal(prFor('G13-S11a'), 18);
  assert.equal(prFor('G14-I2-S01'), 19);
});

test('buildIndex gắn tier, differs, pr', () => {
  const index = buildIndex(REPO_ROOT);
  const entry = index.find((e) => e.code === 'G1-S04b');
  assert.equal(entry.tier, 'core');
  assert.equal(entry.differs, true);
  assert.equal(entry.pr, 3);
});
