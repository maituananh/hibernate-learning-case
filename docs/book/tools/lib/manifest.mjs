// Đọc docs/book/chapters.json và nội dung các chương.
import fs from 'node:fs';
import path from 'node:path';
import { BOOK_DIR, REPO_ROOT } from './paths.mjs';
import { buildIndex } from './scenario-index.mjs';

export const CHAPTERS_DIR = path.join(BOOK_DIR, 'chapters');

export function loadChapters() {
  return JSON.parse(fs.readFileSync(path.join(BOOK_DIR, 'chapters.json'), 'utf8')).chapters;
}

export function readChapter(chapter) {
  const file = path.join(CHAPTERS_DIR, chapter.file);
  return fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : null;
}

export function loadIndexMap() {
  return new Map(buildIndex(REPO_ROOT).map((entry) => [entry.code, entry]));
}
