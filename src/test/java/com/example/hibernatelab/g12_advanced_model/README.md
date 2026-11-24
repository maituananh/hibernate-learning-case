# G12 — Mô hình dữ liệu nâng cao

**Câu hỏi trung tâm:** khi mô hình không chỉ là "entity trỏ tới entity", Hibernate ánh xạ ra bảng và SQL thế nào?

**Model:** mỗi chủ đề có baseline riêng (một chủ đề = một nhóm package/schema), biến thể khác baseline đúng một điểm.
Entity nằm ở [src/main/.../g12_advanced_model](../../../../../../main/java/com/example/hibernatelab/g12_advanced_model/).
Mỗi biến thể là một package = một schema. Dữ liệu seed bằng JDBC (không qua Hibernate) để các SELECT ở bước WHEN là SELECT thật.

## Biến thể

| Mã | Schema | Nội dung | Khác baseline | Test |
|---|---|---|---|---|
| E1 | `g12_e1_embedded` | `Customer` có hai `Address` nhúng (billing, shipping) + `@AttributeOverride` | **baseline** | `G12EmbeddableTest` S01–S03 |
| C1 | `g12_c1_element_collection` | `Order.notes` là `@ElementCollection List<String>` (bag) | **baseline** | `G12ElementCollectionTest` S04a |
| C2 | (cùng schema C1) | `Order.notes` là `@OneToMany` tới entity `Note` | phần tử là entity | S04b |
| C3 | (cùng schema C1) | `@ElementCollection Set<String>` | `Set` thay `List` | S05a |
| C4 | (cùng schema C1) | `@ElementCollection List` + `@OrderColumn` | thêm `@OrderColumn` | S05b |
| H1 | `g12_h1_single_table` | `Payment` → `CardPayment`, `BankPayment`; `Receipt` trỏ tới `Payment` | **baseline** `SINGLE_TABLE` | `G12InheritanceTest` S06, S09 |
| H2 | `g12_h2_joined` | như H1 | `JOINED` | S07 |
| H3 | `g12_h3_table_per_class` | như H1 | `TABLE_PER_CLASS` | S08 |
| N1 | `g12_n1_natural_id` | `Product` có `@NaturalId sku` | **baseline** | `G12NaturalIdTest` S10 |
| V1 | `g12_v1_converter` | `Product.tags` dùng `AttributeConverter`; `Review.status` là enum | **baseline** (kiểu không equals, ORDINAL) | `G12ConverterTest` S11 |
| F1 | `g12_f1_formula_filter` | `@Formula`, `@SQLRestriction` (entity và collection), `@Filter` | **baseline** | `G12FormulaFilterTest` S12–S14 |

`[BOOT-FAIL]` của S01 nằm ở [bootfail/g12s01](../bootfail/g12s01/MissingOverrideCustomer.java), không nằm trong entity scan.

## Năm điều phải nhớ

1. **Embeddable không có bảng riêng.** Cột của nó nhúng vào bảng chủ; dùng hai lần phải `@AttributeOverride`. Mọi cột NULL thì đọc lại là `null`; sửa field tại chỗ vẫn bị dirty checking phát hiện (S01–S03).
2. **`@ElementCollection` kiểu bag recreate cả collection** (DELETE hết, INSERT lại). `Set` hoặc `@OrderColumn` có PK nên không recreate; entity con (`@OneToMany`) xoá đúng 1 dòng theo id (S04, S05).
3. **Ba chiến lược `@Inheritance` đổi cái giá giữa DDL và SQL:** `SINGLE_TABLE` 1 SELECT nhưng cột lớp con phải nullable (đặt `nullable = false` là vỡ INSERT của lớp con khác);
   `JOINED` giữ NOT NULL nhưng join mọi bảng lớp con; `TABLE_PER_CLASS` dùng `union all`, lớp cha trừu tượng không có bảng (S06–S08).
4. **`@ManyToOne LAZY` tới lớp cha cho proxy của lớp CHA:** `instanceof` lớp con sai cho tới khi `Hibernate.unproxy` (S09). `@NaturalId`: lần tra thứ hai trong cùng PC là 0 SELECT, đổi giá trị đã lưu là lỗi (S10).
5. **Enum `ORDINAL` đọc sai im lặng khi đổi thứ tự**; dữ liệu sống lâu dùng `STRING` (S11). `@Formula` chỉ tính lại khi tải lại; `@SQLRestriction` luôn bật (kể cả `find`) nhưng native không bị lọc;
   `@Filter` phải bật theo session và **không** áp cho `find` theo id (S12–S14).

## Bảng kết quả

| Tình huống | Kết quả thực tế | Ghi chú |
|---|---|---|
| S01a `@Embeddable` dùng 2 lần + override | bảng `customer` 6 cột phẳng `billing_*`/`shipping_*`, không có bảng address | |
| S01b thiếu override | `MappingException: Column 'city' is duplicated …` | `[BOOT-FAIL]` |
| S02a mọi cột NULL | `getShippingAddress() == null` | |
| S02b một cột khác NULL | object tồn tại, field còn lại `null` | |
| S03 sửa `setCity` tại chỗ | 1 UPDATE | khác `@EmbeddedId` (G8-S07) |
| S04a bag bỏ 1 phần tử | 1 DELETE (cả `order_id`) + 2 INSERT; bảng con không có PK | như G1-S08 |
| S04b `@OneToMany` entity con | 1 DELETE theo id, 0 INSERT | |
| S05a `Set` | `delete … where order_id=? and label=?`, 0 INSERT | PK `(order_id, label)` |
| S05b `@OrderColumn` bỏ phần tử đầu | 1 DELETE + 2 UPDATE (dồn chỉ số), 0 INSERT | PK `(order_id, step_order)` |
| S06a `SINGLE_TABLE` DDL | 1 bảng + `payment_type`; cột lớp con nullable | **`nullable = false` ở lớp con sinh `NOT NULL` và làm vỡ INSERT lớp con khác** |
| S06b query đa hình | 1 SELECT, không join, đúng kiểu lớp con | |
| S07a `JOINED` DDL | 3 bảng; PK lớp con cũng là FK; NOT NULL được giữ | |
| S07b query đa hình | 1 SELECT với `left join` tới cả 2 bảng lớp con | |
| S08 `TABLE_PER_CLASS` | 1 SELECT `union all`; không có bảng `payment` | |
| S09 proxy tới lớp cha | `HibernateProxy`, `instanceof CardPayment` = false, 0 SELECT; `unproxy` → `CardPayment` (1 SELECT) | |
| S10a `byNaturalId` | lần 1: 1 SELECT `where sku=?`; lần 2: 0 SELECT, cùng instance | |
| S10b DDL | unique constraint trên `sku` | |
| S10c đổi natural id | exception "An immutable natural identifier … was altered", DB giữ SKU cũ | `@NaturalId(mutable = true)` nếu cần đổi |
| S11a/b converter, sửa tại chỗ | **cả hai** (không equals / có equals) đều 1 UPDATE | khác giả thuyết "phụ thuộc equals" |
| S11c `ORDINAL` đổi thứ tự enum | số 1 đọc thành `PAID` (enum cũ) rồi `NEW` (enum đổi thứ tự), không lỗi; `STRING` không bị | |
| S11d DDL enum | `smallint` + `check (between 0 and 1)`; `varchar` + `check (in (…))` | check không cứu được S11c |
| S12 `@Formula` | cùng PC vẫn 1 sau khi thêm order; tải lại ra 2; không có cột `order_count` | |
| S13a `@SQLRestriction` entity | `find` → `null`; JPQL lọc | SQL `where id=? and (active = true)` |
| S13b native | đếm cả dòng không active | |
| S13c trên collection | chỉ lọc khi nạp collection; `find(Note)` vẫn thấy | |
| S14a `@Filter` chưa bật | không lọc | |
| S14b `@Filter` đã bật | JPQL còn 1 order; `find` order NEW vẫn trả entity | không phải rào bảo mật |

## Quyết định cho lead

- **`@Inheritance`:** mặc định nên cân nhắc `SINGLE_TABLE` (nhanh, đơn giản) nếu lớp con ít cột riêng và chấp nhận cột nullable; `JOINED` khi cần ràng buộc NOT NULL; `TABLE_PER_CLASS` hiếm khi hợp lý (union all, FK tới lớp cha khó).
- **`@ElementCollection` hay entity con:** tập giá trị nhỏ, ít đổi → `@ElementCollection` (nhớ dùng `Set`/`@OrderColumn`); cần sửa/xoá từng phần tử hay có logic riêng → entity con.
- **`STRING` hay `ORDINAL`:** `STRING` cho dữ liệu sống lâu; đừng bao giờ đổi thứ tự enum đang lưu `ORDINAL`.
- **`@SQLRestriction` / `@Filter` / `@SoftDelete`:** `@SQLRestriction` luôn bật (kể cả `find`) nhưng native bỏ qua; `@Filter` linh hoạt nhưng không áp cho `find` theo id.
