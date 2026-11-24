# Sách "Hibernate Mapping in Practice"

Nguồn của cuốn sách gom toàn bộ tình huống của lab thành một file Word in được. Thiết kế:
[docs/superpowers/specs/2026-11-24-hibernate-book-design.md](../superpowers/specs/2026-11-24-hibernate-book-design.md).

## Build

```bash
docs/book/build.sh              # build từ evidence và DDL đã có
docs/book/build.sh --evidence   # chạy lại cả suite của lab trước (cần Docker), lấy SQL và dữ liệu mới nhất
```

Kết quả: `build/book/hibernate-mapping-in-practice.docx`. Lần build đầu cần mạng để tải pandoc 3.13 và thư viện Node.

Yêu cầu: Node 23 trở lên, `curl`, font DejaVu (`fonts-dejavu-core`) để vẽ sơ đồ, Docker nếu dùng `--evidence`.
Phải chạy suite của lab ít nhất một lần (`./gradlew test`) để có `build/ddl/create.sql`.

**Mục lục trong Word:** file .docx chứa hai TOC field. Word hỏi có cập nhật field khi mở file, chọn Yes. Nếu không hỏi:
chọn mục lục, nhấn F9 (Update Field), chọn "Update entire table".

## Cấu trúc

| Đường dẫn | Vai trò |
|---|---|
| `chapters.json` | Thứ tự file, tiêu đề, mã tình huống của từng chương |
| `chapters/*.md` | Nội dung sách |
| `diagrams/*.json` | Spec sơ đồ bảng (schema và các bảng cần vẽ); cột và khóa lấy từ `create.sql` |
| `metadata.yaml` | Thuộc tính của file .docx |
| `tools/` | Script Node: index tình huống, bắt output test, sơ đồ, reference.docx, preprocess, lint |
| `tools/capture-output.gradle` | Init script Gradle: ghi output vào từng testcase, không sửa `build.gradle` |

Thư mục `build/book/` (không commit) chứa: `index.json`, `evidence/<mã>.md` (SQL, giá trị bind, dữ liệu DB của từng
tình huống), `img/`, `md/` (Markdown đã xử lý), `reference.docx`, `tools/` (pandoc).

## Cú pháp khi viết chương

| Gõ | Ý nghĩa |
|---|---|
| `### 4.7 Tiêu đề {.bien-the}` | Mục biến thể: bắt buộc có hình sơ đồ, `**Ưu điểm.**`, `**Nhược điểm.**`, `**Nên dùng khi.**` |
| `#### 4.7.1 Tiêu đề {.cot-loi}` / `{.mo-rong}` | Tình huống, có nhãn Cốt lõi hoặc Mở rộng |
| `::: {.tracuu codes="G1-S12,G1-S13"}` `:::` | Khung Tra cứu sinh tự động (entity, test, method, PR) |
| `::: {.ddl schema="g1_v4_bidi_mappedby" tables="customer,purchase_order"}` `:::` | DDL lấy từ `create.sql` |
| `::: {.ghi-nho}`, `{.kien-thuc-nen}`, `{.chua-kiem-chung}`, `{.khac-du-doan codes="..."}` | Các khung có style riêng |
| `![Một dòng mô tả bảng](img/g1-v4.png){width=80%}` | Hình sơ đồ, caption là mô tả bằng chữ |

Nhãn chạy đầu đoạn của một tình huống: `**Bối cảnh.**`, `**Khi nào xảy ra.**`, `**Khi nào không xảy ra.**`,
`**Ví dụ.**`, `**Kết quả.**`, `**Vì sao.**`, `**Cách tránh.**`.

## Kiểm tra

```bash
cd docs/book
npm test                          # test của bộ công cụ
node tools/lint.mjs               # lint toàn bộ sách
node tools/lint.mjs --chapter 04  # lint một chương
```

Lint chặn: ký tự đặc biệt (emoji, mũi tên, ký hiệu toán) ngoài code, mã nội bộ của lab ngoài khung Tra cứu, tab hoặc
dòng dài hơn 85 ký tự trong code, đánh số sai, thiếu phần bắt buộc của khuôn tình huống, và thiếu mã tình huống.
