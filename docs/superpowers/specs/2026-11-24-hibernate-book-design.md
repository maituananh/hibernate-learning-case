# Thiết kế: sách "Hibernate Mapping in Practice"

Ngày: 2026-11-24 · Nhánh: `docs/book` · Trạng thái: người học đã duyệt (2026-11-24)

Tài liệu này vừa là bản thiết kế, vừa là **prompt hoàn chỉnh** (mục 15) để viết cuốn sách. Người thực hiện đọc hết tài liệu
này trước khi lập kế hoạch; không cần hỏi lại người học những điều đã ghi ở đây.

---

## 1. Mục đích

Gom toàn bộ kiến thức đã chứng minh trong lab (Phase 0–19, nhóm G0–G14, 365 test xanh, 20 PR) thành **một cuốn sách Word
(.docx) in được**, để người khác đọc mà không cần mở repo. Repo vẫn là nơi đọc sâu: mỗi tình huống trong sách chỉ đường về
đúng entity, test và PR.

**Vì sao cần sách:** tài liệu hiện có (README nhóm, 02-SCENARIOS, CHEATSHEET) viết cho người đang làm lab. Chúng dày ký hiệu
riêng (⭐ 🔬 ➕, `V1-NN`, `T/M/D/R`, `PC`, mũi tên) và DDL dồn một dòng, nên người ngoài đọc không hiểu.

## 2. Người đọc

Dev Việt Nam **đã dùng Hibernate/Spring Data JPA nhưng chưa thật sự hiểu** (giống người học lúc bắt đầu lab): viết được
entity và repository, nhưng chưa vững owning side, cascade, orphanRemoval, flush, proxy, N+1, transaction.

Hệ quả cho cách viết:
- Kiến thức cơ bản (entity, `@Id`, khóa ngoại, transaction) **chỉ nhắc ngắn**, đủ để hiểu tình huống, không giảng lại.
- Điều cần giải thích kỹ là **vì sao** Hibernate làm vậy và **hậu quả trong DB**.
- Người đọc có thể không chạy lab: mọi bằng chứng (SQL, dữ liệu) phải in ngay trong sách.

## 3. Mục tiêu, không làm, thất bại nếu

**Mục tiêu**
1. Đọc mục lục là tìm được tình huống mình đang gặp.
2. Mỗi tình huống: hiểu bối cảnh, khi nào xảy ra và khi nào không, thấy sơ đồ bảng, code, SQL, dữ liệu DB đổi gì và giữ gì,
   ưu nhược điểm, rồi biết đọc tiếp ở file nào trong repo.
3. Đi từ cơ bản đến nâng cao, không học lan man: chương sau chỉ dùng khái niệm đã có ở chương trước (hoặc trỏ tới rõ ràng).
4. In ra giấy vẫn đọc tốt: code giữ thụt lề như IDE, URL in đầy đủ.

**Không làm**
- Không sửa code, test, cấu hình của lab. Sách chỉ đọc repo và chạy test để lấy bằng chứng.
- Không viết thêm test mới cho các tình huống ngoài lab (Chương 24, 25 chỉ là kiến thức tham khảo, gắn nhãn rõ).
- Không dịch sang tiếng Anh (trừ trang bìa).

**Thất bại nếu** (dù sách đã đủ trang)
- Một kết luận trong sách mâu thuẫn với test thật (ví dụ chép giả thuyết của 02-SCENARIOS thay vì kết quả trong RESULTS.md).
- Người đọc gặp ký hiệu riêng của lab mà không được giải thích.
- Code hoặc DDL bị dồn một dòng, bị Word tự xuống dòng làm vỡ thụt lề.
- Tình huống dài dòng: phải đọc nửa trang mới biết vấn đề là gì.

## 4. Ràng buộc

| Ràng buộc | Chi tiết |
|---|---|
| Ngôn ngữ | Bìa: tiếng Anh. Còn lại: tiếng Việt **có dấu**, giữ nguyên thuật ngữ kỹ thuật tiếng Anh (owning side, flush, proxy, persistence context, dirty checking, ...). Lần đầu gặp một thuật ngữ thì giải thích ngắn trong ngoặc hoặc một câu. |
| Ký tự | Ngoài code block: không emoji, không mũi tên, không ký hiệu toán (xem 8.2). Không viết tắt nội bộ của lab. |
| Định dạng | File .docx, khổ A4, in được. Có mục lục tự động (Word field). |
| Nguồn sự thật | Test, README nhóm, `docs/RESULTS.md`, output khi chạy test. Không dùng giả thuyết của 02-SCENARIOS khi nó khác thực tế. Không viết kết luận theo trí nhớ (cùng tinh thần CLAUDE.md: "Không viết assert theo trí nhớ"). |
| Version | Hibernate ORM 7.4.15.Final, Spring Boot 4.2.0, PostgreSQL 17, Java 21. Ghi một lần ở Lời nói đầu và Phụ lục E. |
| Repo | Public: https://github.com/maituananh/hibernate-learning-case. Link PR và file mở được cho người ngoài. |

## 5. Các cách làm đã cân nhắc

| Cách | Mô tả | Được | Mất |
|---|---|---|---|
| **A. Markdown + pandoc** (**chọn**) | Mỗi chương một file Markdown trong `docs/book/`, build ra .docx bằng pandoc với file mẫu style (`reference.docx`) và Lua filter | Nội dung là text, review được qua PR, sửa dễ; pandoc lo sẵn mục lục, highlight code, bảng, hình, style tùy biến | Trang bìa và vài chi tiết layout phải viết bằng raw OpenXML hoặc Lua filter; máy chưa có pandoc, phải tải bản standalone |
| B. Thư viện `docx` của Node | Script JS sinh toàn bộ file .docx | Kiểm soát layout tuyệt đối | Nội dung nằm trong code hoặc phải tự viết parser Markdown; sửa một câu cũng phải đụng script; khó review |
| C. Claude Docs / Google Docs | Viết thẳng tài liệu online | Sửa và chia sẻ nhanh | Không phải file Word in được như yêu cầu; kiểm soát kém định dạng code và bố cục in |

Người học giao phần "làm thế nào" cho người viết thiết kế; cách A là lựa chọn của người viết thiết kế.

## 6. Cấu trúc sách (người học đã duyệt)

Chương sắp theo **chủ đề**, không theo thứ tự phase. Một số tình huống của G0, G2, G4, G5, G9, G10, G12 được chuyển sang chương
đúng chủ đề. Tên chương dưới đây là **tên in trong sách** (không `@`, không ký hiệu). Cột "Tình huống" chỉ dùng để đối chiếu,
không in vào mục lục.

| Phần | Chương | Nội dung chính | Tình huống |
|---|---|---|---|
| Mở đầu | Lời nói đầu: Cách đọc cuốn sách này | Sách cho ai; 4 bằng chứng (DDL, SQL, dữ liệu DB, trạng thái entity); cách đọc khuôn tình huống; ý nghĩa nhãn; cách tìm code và PR; version | — |
| **I. Nền tảng** | 1. Mô hình mẫu và kiến thức tối thiểu | Sơ đồ toàn bộ domain thương mại điện tử; nhắc nhanh `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@Column`, khóa chính, khóa ngoại; lab kiểm chứng thế nào (Testcontainers, đếm SQL, đọc DB bằng JDBC, `inTx(...)`) | — |
| | 2. Vòng đời entity và persistence context | 4 trạng thái; flush khác commit; SEQUENCE và IDENTITY; dirty checking; sửa entity detached; merge trả bản khác; remove và persist lại; getReference và find; cache cấp 1; flush tự động trước query; `save()` và `isNew`; rollback rồi merge. **Mục mới:** thứ tự SQL khi flush (dựa trên G3-S10) | G0-S01..S11, S13 |
| | 3. Ánh xạ cột và kiểu giá trị: Embeddable, Enum, AttributeConverter, NaturalId | Embeddable và AttributeOverride; embeddable toàn NULL; sửa tại chỗ; STRING và ORDINAL; converter cho kiểu mutable; NaturalId | G12-S01..S03, S10, S11 |
| **II. Quan hệ giữa các bảng** | 4. Ai giữ khóa ngoại: ManyToOne, OneToMany và mappedBy | 9 biến thể, mỗi biến thể một mục; bảng "đặt bên cha hay bên con" | G1-S01..S18 |
| | 5. Cascade: thao tác nào lan từ cha sang con | PERSIST, MERGE, REMOVE, DETACH, REFRESH, ALL; cascade trên ManyToOne; OnDelete ở DB; ba cách delete của Spring Data; ma trận thao tác và cascade | G2-S01..S11, S13 |
| | 6. orphanRemoval: khi con bị tách khỏi cha | So với REMOVE; quên set null; gán collection mới; clear; chuyển con sang cha khác; trùng unique và thứ tự flush; merge đồ thị detached; **cuối chương:** so sánh 4 chiến lược xóa Customer, Order, Item | G3-S01..S11, G9-S06 |
| | 7. Quan hệ một-một: OneToOne và MapsId | FK ở bảng nào; unique; LAZY phía inverse bị bỏ qua; MapsId chia sẻ khóa chính; orphanRemoval một-một | G6-S01..S11, S13 (S12 ghi ngắn, nhãn "Chưa kiểm chứng trong lab") |
| | 8. Quan hệ nhiều-nhiều: ManyToMany và entity trung gian | Owner; tên mặc định; quên mappedBy; Set và List; OrderColumn; cascade ALL; entity trung gian có cột phụ; khi nào bỏ ManyToMany | G7-S01..S12, S14 (S13 ở Chương 11) |
| | 9. Khóa chính ghép và khóa ngoại nhiều cột | JoinColumns; EmbeddedId và IdClass; MapsId theo field; FK trỏ vào natural key; không được sửa khóa chính | G8-S01..S07 |
| | 10. Các mô hình đặc biệt: cây tự tham chiếu, ElementCollection, kế thừa | Cây danh mục; ElementCollection và OneToMany; SINGLE_TABLE, JOINED, TABLE_PER_CLASS; proxy lớp cha | G9-S01, G12-S04..S09 |
| **III. Đọc dữ liệu** | 11. FetchType, proxy và vấn đề N+1 | EAGER mặc định của ManyToOne; proxy LAZY; LazyInitializationException; N+1 và 4 cách sửa; MultipleBagFetchException; tích Descartes; phân trang kèm fetch; EAGER collection; `Hibernate.initialize` | G5-S01..S14, S16, G7-S13 |
| | 12. Viết truy vấn: JPQL, native SQL và phân trang | join và join fetch; join fetch có where làm thiếu collection; native có và không có kiểu entity; `Page` đếm sai; Criteria và Specification; DTO projection | G10-S01..S05, S08..S10, G5-S15 |
| | 13. Lọc dữ liệu ngay trong mapping: Formula, SQLRestriction, Filter, SoftDelete | Khi nào được tính lại, khi nào bị lọc, native có bị lọc không | G12-S12..S14, G9-S11, G10-S07 |
| **IV. Ghi dữ liệu và đồng thời** | 14. Sửa bảng con thông qua bảng cha | Version và `updated_at` của cha; cascade MERGE; collection owning và inverse; `@OptimisticLock(excluded)`; ép tăng version; DynamicUpdate; chi phí; cập nhật từ DTO (cách đúng và cách ghi đè NULL); chuyển item và tính lại tổng | G4-S01..S10, S12, G9-S02, S09 |
| | 15. Cập nhật và xóa hàng loạt | Bulk JPQL, native UPDATE, `@Modifying(clearAutomatically)`; persistence context bị lệch; bỏ qua cascade, orphanRemoval, `@Version` | G2-S12, G4-S13, G9-S05, G10-S06 |
| | 16. Ranh giới transaction trong Spring | readOnly; self-invocation; REQUIRED (rollback-only) và REQUIRES_NEW; checked exception và rollbackFor; service trả entity hay DTO; Open Session in View | G0-S12, S14, G9-S07, G11-S01..S04, S12 |
| | 17. Ghi đồng thời và khóa dữ liệu | Lost update; `@Version`; PESSIMISTIC_WRITE/READ; NOWAIT; SKIP LOCKED; deadlock; FOR UPDATE với outer join; hai người cùng sửa một đơn | G11-S05..S11, G4-S11, G9-S08 |
| **V. Hiệu năng** | 18. Ghi số lượng lớn: batch, sequence và StatelessSession | Batch insert; SEQUENCE và IDENTITY; allocationSize; flush và clear theo lô; StatelessSession | G13-S01..S05 (thay cho G9-S10) |
| | 19. Second-level cache và query cache | Entity cache; bulk và native làm hỏng cache; collection cache; query cache; READ_ONLY | G13-S06..S10 |
| | 20. Bytecode enhancement | LAZY phía inverse OneToOne; Basic LAZY cho cột lớn; dirty tracking; vì sao để ở subproject riêng | G13-S11..S13 |
| **VI. Thực chiến** | 21. Những bẫy khi viết lớp entity | equals và hashCode theo id sinh tự động trong HashSet; equals với proxy; toString đệ quy; id gán tay và `Persistable` (trỏ về Chương 2, 9) | G9-S03, S04 |
| | 22. Bốn sự cố production và cách xử lý | Danh sách đơn hàng chậm dần; tồn kho sai sau flash sale; job gửi email chạy hai lần; import file lớn chậm dần. Mỗi sự cố: triệu chứng, tái hiện, chẩn đoán, sửa, rào chắn | G14-I1..I4 |
| | 23. Rào chắn cho cả team | Test ngân sách SQL; lint mapping bằng Metamodel; checklist review PR; 5 ADR mẫu | G14-R1, R2, `LEAD-PLAYBOOK.md`, `docs/adr/` |
| **VII. Mở rộng tầm nhìn** | 24. Tình huống thực tế chưa có trong lab | Flyway hoặc Liquibase thay `ddl-auto`; UUID làm id; cột JSON; auditing (`@CreatedDate`, Envers); multi-tenancy; optimistic lock qua REST; sửa giá mà khách vẫn thấy giá cũ (G14-I5 chưa làm). **Mọi mục gắn nhãn "Chưa kiểm chứng trong lab"** | — |
| | 25. Hibernate so với các công cụ khác và lộ trình học tiếp | JPQL/HQL, Criteria, native SQL, JdbcTemplate, Spring Data JDBC, MyBatis, jOOQ, Querydsl, Hibernate Reactive; bảng so sánh; tài liệu nên đọc | — |
| **Phụ lục** | A. Tra cứu annotation | Từ `CHEATSHEET.md`: mặc định, đặt ở đâu, ảnh hưởng DDL, SQL, bẫy, chương | |
| | B. Những kết quả khác dự đoán | Từ `RESULTS.md`: các dòng "KHÁC", mỗi dòng trỏ về mục trong sách | |
| | C. Bảng tra tình huống | Mã tình huống, mục trong sách, test class, method, PR | |
| | D. Danh sách Pull Request | 20 PR, tiêu đề, nội dung, URL | |
| | E. Cách chạy lab | Lệnh Gradle, `--rerun`, `-Pinspect`, docker compose, subproject `lab-enhanced` | |
| | F. Bảng thuật ngữ | Thuật ngữ tiếng Anh, nghĩa tiếng Việt, chương gặp lần đầu | |

Mọi mã tình huống có test xanh (mã dạng `G<N>-...` trong `@DisplayName`; bỏ qua test hạ tầng và smoke test) phải xuất hiện
ít nhất một lần trong sách (ô "Tra cứu" hoặc Phụ lục C).

Trang mở đầu mỗi Phần: 2–3 câu giới thiệu Phần này trả lời câu hỏi gì, và danh sách chương của Phần.

**Ước tính độ dài:** 350–450 trang A4. Chương nặng: 4 (~40 trang), 11 (~35), 5 (~30), 6 (~25); các chương khác 10–20 trang.

## 7. Khuôn một tình huống

### 7.1 Ba cấp nội dung

| Cấp | Heading | Nội dung bắt buộc |
|---|---|---|
| Chương | Heading 2, `Chương 4. ...` | Đoạn mở 3–5 câu: câu hỏi trung tâm của chương, vì sao quan trọng. Một hình sơ đồ bảng chung của chương (nếu có). Danh sách ngắn "Trong chương này". |
| Mục (biến thể hoặc chủ đề) | Heading 3, `4.7 ...` | Mapping của biến thể (code entity); sơ đồ bảng của biến thể (hình + một dòng mô tả bằng chữ); DDL đã định dạng; **Ưu điểm / Nhược điểm / Nên dùng khi**. |
| Tình huống | Heading 4, `4.7.2 ...` | Theo khuôn 7.2 (Cốt lõi) hoặc 7.3 (Mở rộng). |

Sơ đồ bảng và ưu nhược điểm đặt ở **cấp mục**, để không lặp lại ở mỗi tình huống của cùng biến thể. Chương nào không chia
biến thể (ví dụ Chương 16) thì mục là chủ đề, ưu nhược điểm đặt ở tình huống nào là một lựa chọn.

### 7.2 Tình huống "Cốt lõi" (⭐ trong lab): viết đủ

```
4.7.2  Chỉ thêm vào collection phía mappedBy: khóa ngoại bị để NULL      [Cốt lõi]

Bối cảnh            Tối đa 4 câu: đang làm gì, mong đợi gì.
Khi nào xảy ra      Điều kiện cụ thể (mapping nào, thao tác nào).
Khi nào không xảy ra Điều kiện ngược lại, hoặc cách viết khác không bị.
Ví dụ               Code thao tác, rút gọn từ test (xem 8.4).
Kết quả             SQL đã chạy (từ output thật).
                    Dữ liệu DB trước và sau (bảng Word; giá trị đổi in đậm;
                    dòng "Thay đổi: ...", "Giữ nguyên: ...").
                    Trạng thái entity sau từng bước (TRANSIENT, MANAGED, ...).
Vì sao              Giải thích cơ chế, ngắn.
Cách tránh          Chỉ có khi tình huống là bẫy. (Nếu tình huống là một lựa
                    chọn mapping, ưu nhược điểm đã ở cấp mục.)
Ghi nhớ             1–2 câu, khung riêng.
Tra cứu             Khung riêng (xem 8.6).
```

Nếu `RESULTS.md` ghi "KHÁC" cho tình huống này thì thêm khung **"Kết quả khác dự đoán"**: điều nhiều người nghĩ, điều thực tế
xảy ra, version đã kiểm chứng.

### 7.3 Tình huống "Mở rộng" (➕ trong lab): viết ngắn

Bối cảnh (1–2 câu), Kết quả (SQL hoặc dữ liệu nếu cần cho hiểu), Ghi nhớ, Tra cứu. Bỏ qua "Khi nào không xảy ra" nếu hiển nhiên.

### 7.4 Đặt tên

- **Tên tình huống nói cả hành động và kết quả**, đọc trong mục lục là hiểu: "Quên mappedBy: dữ liệu được ghi ở hai nơi",
  "persist chưa chạy INSERT: flush khác commit", "Xóa cha khi không có cascade: vi phạm khóa ngoại".
- **Tên mục mô tả mapping bằng lời**, không dùng mã biến thể: "4.7 Hai chiều chuẩn: OneToMany có mappedBy và ManyToOne"
  (không phải "V4").
- Đánh số tay trong Markdown: `Phần II`, `Chương 4`, `4.7`, `4.7.2`. Script lint kiểm tra số liên tục.

## 8. Quy tắc trình bày

### 8.1 Mục lục

- Hai mục lục (Word TOC field): **Mục lục tóm tắt** (Phần và Chương, khoảng 2 trang) và **Mục lục chi tiết** (đến cấp tình
  huống), để tìm được case cụ thể.
- Trong tiêu đề: không `@`, không backtick, không emoji, không mũi tên, không mã tình huống. Annotation viết trơn
  (`ManyToOne`, `OneToMany`, `mappedBy`).

### 8.2 Ký tự và cách viết trong thân bài (ngoài code block)

| Không dùng | Thay bằng |
|---|---|
| ⭐ 🔬 ➕ ✅ ⚠️ ❌ và mọi emoji | Nhãn chữ: "Cốt lõi", "Mở rộng", "Kết quả khác dự đoán", "Chưa kiểm chứng trong lab"; "Nên", "Tránh" |
| `→ ← ↔ ⇒` | "dẫn tới", "rồi", "thành", "trỏ tới", "hai chiều" |
| `× ≠ ≤ ≥ ⌈ ⌉ §` | "nhân", "khác", "tối đa", "tối thiểu", viết thành câu |
| `PC`, `T/M/D/R`, `TH`, `[BOOT-FAIL]`, `[LIFECYCLE]`, `[FLUSH]` | "persistence context", tên trạng thái đầy đủ, "tình huống", "Hibernate không khởi động được" |
| Mã biến thể `V1-NN`, `C4-DEEP`, `O2`, ... | Mô tả mapping bằng lời; mã chỉ nằm trong khung Tra cứu |
| `1 + 2 INSERT` | "1 câu INSERT customer và 2 câu INSERT purchase_order" |

Annotation trong câu vẫn giữ `@` và style code inline (`@ManyToOne`): đó là cú pháp Java mà người kỹ thuật nhận ra ngay.
Script lint (mục 12) quét các ký tự cấm ngoài code block.

### 8.3 Code block

- Font monospace (Consolas 9pt), nền xám nhạt, viền mảnh, highlight cú pháp in được cả bản đen trắng.
- **Mỗi dòng tối đa 85 ký tự**, ngắt tay trong source; không để Word tự xuống dòng. Thụt lề 4 khoảng trắng như IDE.
- Ngôn ngữ: `java`, `sql`, `groovy`, `yaml`, `text`.
- Comment trong code: tiếng Việt có dấu, viết lại cho người đọc sách (không dùng tag `[LIFECYCLE]`, viết "// Trạng thái: MANAGED").

### 8.4 Code ví dụ lấy từ test

- **Code entity (mapping):** chép gần nguyên văn từ `src/main`, giữ annotation và comment OWNING/INVERSE, bỏ header
  `BIẾN THỂ / BASELINE / ...` dài (đã có ở phần mô tả mục), bỏ getter/setter không liên quan.
- **Code thao tác:** rút gọn từ test: giữ GIVEN và WHEN, giữ ranh giới transaction bằng `inTx(...)` (giải thích một lần ở
  Chương 1), bỏ assert, `sql.print()`, `db.print()`, `sql.mark(...)`.
- Không được đổi ý nghĩa: số lệnh persist/flush, thứ tự gọi, transaction nào chứa lệnh nào phải giữ đúng như test.

### 8.5 Sơ đồ bảng, DDL, SQL, dữ liệu

- **Sơ đồ bảng:** hình PNG 300 dpi, mỗi bảng một khung (tên bảng, cột, đánh dấu khóa chính và khóa ngoại), đường nối ghi rõ
  "1" và "nhiều" bằng chữ. Dưới hình có **một dòng mô tả bằng chữ** (ví dụ: "Bảng purchase_order có cột customer_id là khóa
  ngoại tới customer.id; một customer có nhiều purchase_order"), để bản in đen trắng vẫn đọc được. Cột và khóa lấy tự động từ
  `build/ddl/create.sql` (mục 10.3), không vẽ tay.
- **DDL:** lấy từ `build/ddl/create.sql`, định dạng lại một cột một dòng, constraint tách riêng. Không dán nguyên dòng dài.
- **SQL:** lấy từ output thật của test (`format_sql` đã bật). Giữ `?`, liệt kê giá trị bind ở dòng chú thích bên dưới nếu cần.
  Bỏ `nextval` trừ khi tình huống nói về sequence.
- **Dữ liệu DB:** bảng Word chuyển từ output `db.print(...)`. Có bảng "trước" và "sau" khi có thay đổi; giá trị đổi in đậm;
  ngay dưới là hai dòng "Thay đổi: ..." và "Giữ nguyên: ...". Không đưa id cụ thể vào kết luận (id có thể khác giữa các lần chạy).

### 8.6 Khung "Tra cứu trong repository"

```
Tra cứu trong repository
  Mã tình huống : G1-S12
  Entity        : src/main/java/com/example/hibernatelab/g1_owning_side/v4_bidi_mappedby/
  Test          : src/test/java/com/example/hibernatelab/g1_owning_side/G1V4BidirectionalTest.java
  Method        : s12_...  (tên method đầy đủ)
  Pull Request  : https://github.com/maituananh/hibernate-learning-case/pull/3
```

- Đường dẫn file là hyperlink tới `https://github.com/maituananh/hibernate-learning-case/blob/main/<path>`; chữ hiển thị là
  đường dẫn trong repo. URL PR in đầy đủ vì bản in không bấm được.
- Bảng PR theo nhóm:

| Nội dung | PR |
|---|---|
| Hạ tầng test và golden test G0-S01 | #1 |
| G0 | #2 |
| G1 | #3 |
| G2 | #4 |
| G3 | #5 |
| G4 | #6 |
| G5 | #7 |
| G6 | #8 |
| G7 | #9 |
| G8 | #10 |
| G9 | #11 |
| Tổng kết giai đoạn đầu (CHEATSHEET, coverage) | #12 |
| Thiết kế giai đoạn mở rộng | #13 |
| G10 | #14 |
| G11 | #15 |
| G12 | #16 |
| G13a (batch, cache) | #17 |
| G13b (bytecode enhancement, `lab-enhanced/`) | #18 |
| G14 (sự cố, rào chắn, LEAD-PLAYBOOK, ADR) | #19 |
| Tổng kết giai đoạn mở rộng | #20 |

URL: `https://github.com/maituananh/hibernate-learning-case/pull/<số>`.

### 8.7 Các khung (Word paragraph style riêng)

| Khung | Dùng khi |
|---|---|
| Ghi nhớ | Cuối mỗi tình huống |
| Tra cứu trong repository | Cuối mỗi tình huống |
| Kết quả khác dự đoán | Tình huống có dòng "KHÁC" trong `RESULTS.md` |
| Chưa kiểm chứng trong lab | Mọi mục của Chương 24, 25 và G6-S12 |
| Kiến thức nền | Đoạn nhắc kiến thức cơ bản (ngắn, có thể bỏ qua nếu đã biết) |

### 8.8 Trang bìa (tiếng Anh)

- Title: **Hibernate Mapping in Practice**
- Subtitle: *Entity lifecycle, relationships, queries, transactions and performance, proven by real tests on PostgreSQL*
- Dòng giới thiệu: *A field guide for developers who use Hibernate and want to truly understand it*
- Stack, mỗi thứ một dòng nhỏ: Hibernate ORM 7.4, Spring Boot 4.2, PostgreSQL 17, Java 21
- Author: **Mai Tuấn Anh**
- Edition: First edition, 2026
- Repository URL
- Hình trang trí đơn giản (ví dụ một sơ đồ bảng thu nhỏ của domain), một màu nhấn; bìa là trang riêng, không header/footer.

### 8.9 Bố cục trang

- A4, lề 2.5 cm. Thân bài: font có hỗ trợ tiếng Việt và có sẵn trên Word Windows/macOS (Cambria 11pt hoặc tương đương);
  heading: font sans (Calibri hoặc tương đương).
- Mỗi Phần và mỗi Chương bắt đầu trang mới. Footer: số trang. Header: tên chương (STYLEREF), nếu làm được.
- Heading 1 = Phần, Heading 2 = Chương, Heading 3 = Mục, Heading 4 = Tình huống.

## 9. Nguồn sự thật và cách lấy bằng chứng

| Cần | Lấy từ |
|---|---|
| Kết luận của tình huống | Assert trong test (đọc file test của từng tình huống) và README nhóm; `RESULTS.md` cho 🔬 |
| Mapping | Entity trong `src/main/java/com/example/hibernatelab/...` (và `lab-enhanced/src/main` cho G13b) |
| SQL đã chạy, dữ liệu DB | Output của test khi chạy lại suite (mục 10.2) |
| DDL, cột cho sơ đồ | `build/ddl/create.sql` sau khi chạy suite |
| Mã tình huống ứng với method nào | `@DisplayName("G<N>-S<NN> | ...")` trong test |
| Tình huống Cốt lõi hay Mở rộng | Nhãn ⭐/➕ trong `02-SCENARIOS.md` và bảng tình huống của README nhóm |
| Phụ lục A, B, D | `CHEATSHEET.md`, `RESULTS.md`, `gh pr list` |
| Chương 22, 23 | Test G14, `LEAD-PLAYBOOK.md`, `docs/adr/` |
| Chương 24, 25 | Tài liệu chính thức (Hibernate User Guide, Spring Data reference, trang chủ MyBatis, jOOQ, ...). Không đưa số SQL cụ thể; gắn nhãn "Chưa kiểm chứng trong lab" |

**Quy tắc:** câu nào trong sách nói "Hibernate làm X" thì phải chỉ ra được test chứng minh X; nếu không có test thì câu đó
phải nằm trong khung "Chưa kiểm chứng trong lab".

## 10. Pipeline build

### 10.1 Cấu trúc thư mục

```
docs/book/
  README.md                 cách build sách, yêu cầu công cụ
  metadata.yaml             tiêu đề, tác giả, ngôn ngữ (vi)
  chapters/
    00-loi-noi-dau.md
    01-mo-hinh-mau.md
    ...
    25-so-sanh-cong-cu.md
    90-phu-luc-a-annotation.md
    ...
  chapters.json             manifest: thứ tự file, tiêu đề, mã tình huống của từng chương
  diagrams/                 spec sơ đồ (JSON): schema, bảng cần vẽ
  tools/                    script Node: index tình huống, bắt output test, sinh sơ đồ,
                            sinh reference.docx, preprocess (khung, Tra cứu, DDL), lint
  build.sh                  một lệnh build ra .docx
build/book/                 (đã bị .gitignore) evidence, hình, file .docx đầu ra, pandoc tải về
```

Output: `build/book/hibernate-mapping-in-practice.docx`. File .docx, `reference.docx` và hình PNG **không commit** (binary,
sinh lại được). Trang bìa và hai mục lục nằm trong chương đầu (`000-bia-va-muc-luc.md`), viết bằng style riêng và raw
OpenXML; các khung, khối Tra cứu, DDL được một bước preprocess bằng Node mở rộng trước khi đưa vào pandoc (thay cho Lua
filter, để test được bằng `node:test`). Chi tiết: plan `docs/superpowers/plans/2026-11-24-hibernate-book.md`.

### 10.2 Bắt output của từng test

Không sửa `build.gradle`. Dùng init script trong `docs/book/tools/`:

```groovy
allprojects {
    tasks.withType(Test).configureEach {
        // Ghi stdout vào từng <testcase> trong file XML, thay vì gộp cả class.
        reports.junitXml.outputPerTestCase = true
    }
}
```

Chạy `./gradlew test --rerun --init-script docs/book/tools/capture-output.gradle`, rồi script đọc
`build/test-results/test/*.xml` và `lab-enhanced/build/test-results/test/*.xml`, tách ra
`build/book/evidence/<TestClass>/<method>.txt`. Output từ thread khác (test song song của G11, G14 qua `ConcurrentTx`) có thể
không gắn vào testcase: khi đó lấy từ `system-out` cấp class. Phải chạy được bằng Docker (Testcontainers) như suite bình thường.

### 10.3 Sơ đồ bảng

Script Node đọc `build/ddl/create.sql` (`create table`, `alter table ... foreign key ... references`), lấy cột, khóa chính,
khóa ngoại của các bảng được liệt kê trong spec `docs/book/diagrams/<id>.json`, sinh SVG rồi chuyển sang PNG (ví dụ
`@resvg/resvg-js`, ghim version). Nhãn "1" và "nhiều" được suy ra từ khóa ngoại (cột FK unique thì hai đầu đều là "1");
câu mô tả bằng chữ nằm ở caption của hình trong Markdown. Font của hình phải có dấu tiếng Việt.

### 10.4 Pandoc

- Tải pandoc standalone (ghim một version 3.x cụ thể) về `build/book/tools/`; `build.sh` tự tải nếu chưa có.
- `reference.docx`: script lấy bản mặc định (`pandoc -o - --print-default-data-file reference.docx`), rồi chỉnh style (font,
  heading ngắt trang, style code, style các khung ở 8.7, bảng, footer số trang) và ghi vào `build/book/reference.docx`.
- Khung: preprocess đổi khối của người viết (ví dụ `::: {.ghi-nho}`) thành `::: {custom-style="Ghi nho"}`, ánh xạ sang
  paragraph style cùng tên trong `reference.docx`.
- Hai mục lục: hai TOC field raw OpenXML (`TOC \o "1-2"` và `TOC \o "1-4"`) trong chương đầu. Word cập nhật số trang khi mở
  file (bật `updateFields`); ghi chú điều này trong `docs/book/README.md`.

## 11. Quy trình viết, checkpoint, commit

1. **Dựng pipeline:** công cụ, `reference.docx`, trang bìa, hai mục lục, bắt output test, sinh sơ đồ, lint. Build được một
   file .docx khung (bìa, mục lục, Lời nói đầu, các chương rỗng).
2. **Chương mẫu:** viết trọn **Chương 4** (dùng đủ mọi thành phần của khuôn). Build. **CHECKPOINT: dừng cho người học mở bằng
   Word và duyệt** trang bìa, mục lục, khuôn tình huống, độ dài, sơ đồ, code, khung. Sửa theo góp ý trước khi viết tiếp.
3. **Viết các chương còn lại** theo thứ tự Phần I, II (5–10), III, IV, V, VI, VII, rồi Phụ lục. Cuối mỗi Phần: build, chạy
   kiểm tra mục 12, báo người học số trang và các điểm cần chú ý.
4. **Hoàn thiện:** Lời nói đầu (viết lại sau cùng cho khớp nội dung thật), Phụ lục C (sinh từ dữ liệu), kiểm tra toàn bộ.
5. **Commit và PR:** chỉ commit khi người học đồng ý. Đề xuất: một commit sau khi chương mẫu được duyệt (thiết kế, pipeline,
   Chương 4), mỗi Phần một commit, cuối cùng mở PR với description tiếng Việt có dấu (theo quy trình trong CLAUDE.md), kèm
   số trang, số tình huống đã phủ, danh sách mục "Chưa kiểm chứng trong lab".

## 12. Kiểm tra trước khi bàn giao (mỗi lần build)

| Kiểm tra | Cách |
|---|---|
| Phủ tình huống | Mọi mã trong `@DisplayName` của test xuất hiện trong sách; in danh sách mã thiếu |
| Ký tự cấm | Quét Markdown ngoài code block theo bảng 8.2; tiêu đề không có `@`, backtick, mã tình huống |
| Độ dài dòng code | Mọi dòng trong code block tối đa 85 ký tự |
| Đánh số | Chương, mục, tình huống liên tục, không trùng |
| Đường dẫn | Mọi đường dẫn trong khung Tra cứu tồn tại trong repo; tên method tồn tại trong test class |
| Link PR | Số PR khớp bảng 8.6 |
| Đúng sự thật | Với từng tình huống: số câu SQL và kết luận khớp assert trong test (đọc test khi viết, không chép 02-SCENARIOS) |
| Hiển thị | Mở thử bằng LibreOffice headless (nếu tải được bản AppImage) để xuất PDF xem nhanh; nếu không, người học duyệt bằng Word |

## 13. Quyết định của người viết thiết kế (người học duyệt lại)

1. Cách A (Markdown + pandoc), tải pandoc standalone, không cài hệ thống.
2. Tiêu đề trong mục lục không có `@`; thân bài giữ `@`.
3. Nhãn chữ thay biểu tượng (8.2, 8.7).
4. ⭐ viết đủ khuôn, ➕ viết ngắn.
5. Kết luận lấy từ test và `RESULTS.md`, SQL và dữ liệu lấy từ output thật.
6. Sơ đồ bảng là hình sinh tự động từ DDL, kèm một dòng mô tả bằng chữ.
7. Sơ đồ và ưu nhược điểm đặt ở cấp mục (biến thể).
8. Hai mục lục: tóm tắt và chi tiết.
9. Code tối đa 85 ký tự mỗi dòng; code thao tác giữ `inTx(...)`.
10. Link file trỏ tới nhánh `main` (lab đã xong, `main` ổn định).
11. File .docx không commit; nằm ở `build/book/`.
12. Chương mẫu là Chương 4; checkpoint duy nhất bắt buộc là sau chương mẫu, sau đó báo cáo cuối mỗi Phần.
13. Tên sách và nội dung trang bìa như 8.8.

## 14. Để lại cho người thực hiện

- Tên cụ thể của từng mục và tình huống (theo quy tắc 7.4).
- Font chính xác, màu nhấn, chi tiết style trong `reference.docx`.
- Version pandoc và `@resvg/resvg-js` (ghim version đã phát hành ít nhất hai tuần).
- Cách vẽ trang bìa (raw OpenXML trong Lua filter hay docx riêng ghép vào).
- Nội dung chi tiết Chương 24, 25 (trong phạm vi các chủ đề đã liệt kê).
- Schema dùng cho sơ đồ toàn domain ở Chương 1 (chọn schema có đủ bộ bảng, ví dụ của G9 hoặc G5).

---

## 15. Prompt hoàn chỉnh

> Bản này thay cho prompt ban đầu, dùng được để giao lại việc viết sách.

**Ngữ cảnh.** Repo `hibernate-learning-case` là lab học Hibernate mapping: 15 nhóm tình huống G0–G14, mỗi tình huống là một
test chạy trên PostgreSQL thật, chứng minh DDL, SQL, dữ liệu DB và trạng thái entity (Hibernate 7.4.15.Final, Spring Boot 4.2,
PostgreSQL 17, Java 21). Repo public, mỗi nhóm có một Pull Request.

**Nhiệm vụ.** Viết một cuốn sách Word (.docx), khổ A4, in được, gom toàn bộ kiến thức của lab cho người khác đọc.

**Người đọc.** Dev đã dùng Hibernate/Spring Data JPA nhưng chưa thật sự hiểu. Kiến thức cơ bản (entity, annotation, transaction)
chỉ nhắc ngắn; tập trung vào vì sao và hậu quả trong DB.

**Thiết kế sách.**
1. Trang bìa chỉn chu bằng tiếng Anh.
2. Các chương viết tiếng Việt có dấu, giữ nguyên thuật ngữ kỹ thuật tiếng Anh để người kỹ thuật nhận ra ngay vấn đề.
3. Hai mục lục (tóm tắt và chi tiết). Tên mục đọc vào là hiểu, tìm được đúng tình huống; không dùng ký tự đặc biệt, emoji,
   mũi tên, mã nội bộ của lab.
4. Sắp chương theo chủ đề, cơ bản trước nâng cao sau, không bắt buộc theo thứ tự đã code; chương sau chỉ dùng khái niệm đã có.

**Nội dung từng tình huống.**
1. Gom tình huống theo chủ đề và biến thể, để thấy rõ khi có hoặc không có một annotation hay thuộc tính thì chuyện gì xảy ra.
2. Mô tả bối cảnh ngắn gọn trước khi vào vấn đề; không lan man.
3. Nêu khi nào xảy ra và khi nào không xảy ra, rồi mới đến ví dụ.
4. Ví dụ có bảng thì phải có sơ đồ quan hệ: bảng nào, cột gì, khóa nào, quan hệ thế nào; dữ liệu DB trước và sau: cái gì đổi,
   cái gì không.
5. Code trình bày như trong IDE: giữ thụt lề, không dồn một dòng, tối đa 85 ký tự mỗi dòng.
6. Ưu nhược điểm của từng cách mapping, và cách tránh cho từng bẫy.
7. Cuối mỗi tình huống: chỉ rõ entity, test class, method, mã tình huống và link Pull Request trong repo.
8. Tình huống cốt lõi viết đầy đủ; tình huống phụ ghi ngắn.
9. Mọi kết luận phải lấy từ test và kết quả chạy thật, không viết theo trí nhớ; điều gì không có test thì ghi rõ "Chưa kiểm
   chứng trong lab".

**Cuối sách.** Tình huống thực tế chưa có trong lab; so sánh Hibernate với JPQL/HQL, Criteria, native SQL, JdbcTemplate,
Spring Data JDBC, MyBatis, jOOQ, Querydsl; lộ trình học tiếp; phụ lục tra cứu annotation, kết quả khác dự đoán, bảng tình
huống, danh sách PR, cách chạy lab, thuật ngữ.

**Độ dài.** Không giới hạn; ưu tiên rõ ràng, ví dụ cụ thể để người đọc hiểu ngay.

**Cách làm.** Theo thiết kế ở mục 6–12 của tài liệu này: viết chương mẫu (Chương 4), dừng cho người học duyệt, rồi viết tiếp.
