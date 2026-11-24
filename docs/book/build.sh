#!/usr/bin/env bash
# Build sách "Hibernate Mapping in Practice" ra build/book/hibernate-mapping-in-practice.docx
#
#   docs/book/build.sh              build từ evidence và DDL đã có
#   docs/book/build.sh --evidence   chạy lại cả suite của lab trước (cần Docker), lấy SQL và dữ liệu mới nhất
#   docs/book/build.sh --no-lint    bỏ qua lint (chỉ dùng khi đang viết dở, không dùng khi bàn giao)
#
# Mọi thứ sinh ra nằm trong build/book/ (bị .gitignore): pandoc tải về, index, evidence, hình, file .docx.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BOOK="$ROOT/docs/book"
OUT="$ROOT/build/book"
PANDOC_VERSION="3.13"
PANDOC="$OUT/tools/pandoc-$PANDOC_VERSION/bin/pandoc"

RUN_EVIDENCE=false
RUN_LINT=true
for arg in "$@"; do
  case "$arg" in
    --evidence) RUN_EVIDENCE=true ;;
    --no-lint) RUN_LINT=false ;;
    *) echo "Không hiểu tham số: $arg" >&2; exit 2 ;;
  esac
done

# 1. Pandoc bản standalone, ghim version (không cài vào hệ thống).
if [[ ! -x "$PANDOC" ]]; then
  echo "Tải pandoc $PANDOC_VERSION ..."
  mkdir -p "$OUT/tools"
  curl -fsSL -o "$OUT/tools/pandoc.tar.gz" \
    "https://github.com/jgm/pandoc/releases/download/$PANDOC_VERSION/pandoc-$PANDOC_VERSION-linux-amd64.tar.gz"
  tar -xzf "$OUT/tools/pandoc.tar.gz" -C "$OUT/tools"
  rm "$OUT/tools/pandoc.tar.gz"
fi

# 2. Thư viện Node (resvg vẽ sơ đồ, jszip sửa reference.docx).
if [[ ! -d "$BOOK/node_modules" ]]; then
  (cd "$BOOK" && npm ci --silent)
fi

cd "$BOOK"
# 3. Bằng chứng từ test: chạy lại suite nếu được yêu cầu.
if $RUN_EVIDENCE; then
  node tools/capture-evidence.mjs --run
fi

# 4. Index tình huống, sơ đồ, reference.docx, Markdown đã xử lý.
node tools/build-index.mjs
node tools/diagrams.mjs
node tools/make-reference.mjs
node tools/preprocess.mjs

# 5. Lint: dừng nếu có lỗi.
if $RUN_LINT; then
  node tools/lint.mjs
fi

# 6. Pandoc: các file trong build/book/md đã đặt tên theo thứ tự (000-, 005-, 010-, ...).
DOCX="$OUT/hibernate-mapping-in-practice.docx"
"$PANDOC" "$OUT"/md/*.md \
  --from markdown \
  --to docx \
  --reference-doc "$OUT/reference.docx" \
  --resource-path "$OUT" \
  --metadata-file "$BOOK/metadata.yaml" \
  --syntax-highlighting=tango \
  --output "$DOCX"
echo "Xong: $DOCX ($(du -h "$DOCX" | cut -f1))"
