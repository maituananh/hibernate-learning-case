#!/usr/bin/env bash
# (Tùy chọn) Xuất file .docx ra PDF bằng LibreOffice headless để xem nhanh bố cục khi đang viết.
# Không phải bước bắt buộc của build: bản chính thức vẫn là file .docx mở bằng Word.
#
# Cần LibreOffice AppImage đã giải nén ở build/book/tools/squashfs-root
# (tải từ https://www.libreoffice.org/download/appimage/, chạy với --appimage-extract).
# Lưu ý: LibreOffice không tự cập nhật TOC field, nên trang mục lục trong PDF chỉ hiện chữ giữ chỗ.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="$ROOT/build/book"
SOFFICE="$(find "$OUT/tools/squashfs-root" -path '*program/soffice' -type f 2>/dev/null | head -1)"
if [[ -z "$SOFFICE" ]]; then
  echo "Chưa có LibreOffice ở $OUT/tools/squashfs-root" >&2
  exit 1
fi
"$SOFFICE" --headless --convert-to pdf --outdir "$OUT" "$OUT/hibernate-mapping-in-practice.docx" >/dev/null
echo "PDF: $OUT/hibernate-mapping-in-practice.pdf"
