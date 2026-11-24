// Đường dẫn dùng chung cho mọi script build sách.
// Script nằm ở docs/book/tools/lib, nên gốc repo là lùi 4 cấp.
import path from 'node:path';

export const REPO_ROOT = path.resolve(import.meta.dirname, '../../../..');
export const BOOK_DIR = path.join(REPO_ROOT, 'docs', 'book');
// Mọi thứ sinh ra (index, evidence, hình, docx) nằm trong build/ để không bị commit.
export const BUILD_DIR = path.join(REPO_ROOT, 'build', 'book');
export const REPO_URL = 'https://github.com/maituananh/hibernate-learning-case';

// Pandoc bản standalone, ghim version; docs/book/build.sh tự tải về nếu chưa có.
export const PANDOC_VERSION = '3.13';
export const PANDOC = path.join(BUILD_DIR, 'tools', `pandoc-${PANDOC_VERSION}`, 'bin', 'pandoc');

// Font cho hình sơ đồ: nạp theo đường dẫn cố định (không dùng font hệ thống) để chữ có dấu
// tiếng Việt hiển thị giống nhau ở mọi lần build. Gói fonts-dejavu-core của Ubuntu.
const DEJAVU = '/usr/share/fonts/truetype/dejavu';
export const FONT_FILES = [
  `${DEJAVU}/DejaVuSans.ttf`,
  `${DEJAVU}/DejaVuSans-Bold.ttf`,
  `${DEJAVU}/DejaVuSansMono.ttf`,
];
