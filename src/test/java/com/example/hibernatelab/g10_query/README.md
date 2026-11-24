# G10 — Truy vấn & persistence context

**Câu hỏi trung tâm:** cách viết query ảnh hưởng thế nào tới SQL, tới trạng thái collection và tới những gì nằm trong persistence context (PC)?

**Model:** `Customer` 1—N `PurchaseOrder` 1—N `OrderItem` N—1 `Product`, mọi quan hệ LAZY. Phần lớn tình huống chỉ khác **cách viết query**,
không khác mapping, nên dùng chung một bộ entity.

**Dữ liệu chuẩn** ([G10Fixtures](G10Fixtures.java), seed bằng JDBC): 3 customer × 2 order (một PAID, một NEW) × 2 item; 3 product.

Entity nằm ở [src/main/.../g10_query](../../../../../../main/java/com/example/hibernatelab/g10_query/). Mỗi biến thể là một package = một schema.

## Biến thể

| Mã | Schema | Khác baseline Q1 | Test |
|---|---|---|---|
| Q1 | `g10_q1_baseline` | **baseline**: mọi quan hệ `LAZY` tường minh, collection là `List` | S01–S05, S08–S10 |
| Q2 | `g10_q2_versioned` | `Product` có thêm `@Version` | S06 |
| (G9-S11) | `g9_s11_soft_delete` | dùng lại entity có `@SoftDelete` của G9 | S07 |

## Ba điều phải nhớ

1. **`join` để lọc, `join fetch` để nạp.** Alias của `join fetch` không dùng để lọc: `where` lên alias đó làm collection thiếu dữ liệu và PC giữ nguyên bản thiếu (S01–S03).
2. **Hibernate chỉ biết những gì đi qua nó.** Native query có kiểu entity vẫn cho entity MANAGED (đi qua PC, identity map, dirty checking);
   native không kiểu, `Tuple`, DTO thì không có entity nào; native `UPDATE` và native `SELECT` không được Hibernate thêm gì (`@Version`, `@SoftDelete`) (S04–S07, S10).
3. **`Page` + fetch collection cần count query riêng.** Count tự sinh đếm sai âm thầm; `Specification` có `fetch` thì count query ném lỗi (S08, S09).

## Bảng kết quả

| Tình huống | Kết quả thực tế | Ghi chú |
|---|---|---|
| S01a `join c.orders` | 1 SELECT chỉ cột customer; `orders` chưa khởi tạo; `getOrders()` thêm 1 SELECT | kết quả 3 customer (Hibernate 6+ loại trùng cả root của `join` thường) |
| S01b `join fetch c.orders` | 1 SELECT có cột order; `orders` đã khởi tạo; 0 SQL thêm | |
| S02 `join fetch … where o.status='PAID'` | collection 1 phần tử (DB có 2); đọc lại trong PC vẫn 1 | lỗi dữ liệu âm thầm |
| S03 `join … where` rồi chạm collection | 2 SELECT, collection đủ 2 phần tử | cách viết đúng cho S02 |
| S04 native + `Customer.class` | MANAGED, **cùng instance** với `find`; sửa tại chỗ → 1 UPDATE | native vẫn chạy SQL |
| S05 native `Object[]` | 0 entity trong PC | |
| S06 native `UPDATE` (có `@Version`) | entity giữ giá cũ; DB giá mới; version vẫn 0 | tự `version = version + 1` hoặc `em.refresh` |
| S07 native `SELECT` trên `@SoftDelete` | trả cả dòng đã xoá mềm (JPQL thì lọc) | native phải tự `where deleted = false` |
| S08a `Page` + `join fetch`, count tự sinh | **total = 6** thay vì 3, không báo lỗi | dữ liệu trang vẫn cắt đúng ở DB |
| S08b cùng S08a + `countQuery` | total = 3 | |
| S09a `Specification` không fetch | SQL có/không có `where` theo điều kiện; total đúng | trang rỗng thì Spring Data bỏ qua count |
| S09b `Specification` có `root.fetch` + `Page` | count query ném `SemanticException` | |
| S09c fetch chỉ khi `getResultType() != Long.class` | chạy được, total đúng | |
| S10a/b JPQL `select new` / native `Tuple` | 0 entity trong PC | |

## Quyết định cho lead

| Tình huống trong dự án | Nên dùng | Vì sao | Chứng minh |
|---|---|---|---|
| Cần danh sách cha **kèm** con để dùng ngay | `join fetch` (hoặc `@EntityGraph`, G5-S07) | 1 SELECT, collection đã khởi tạo | S01b |
| Chỉ cần **lọc** cha theo điều kiện trên con | `join` thường (hoặc `exists`) rồi tải con riêng | giữ collection đầy đủ | S01a, S03 |
| Điều kiện `where` trên phần tử của collection đã `join fetch` | **Không làm** | collection thiếu dữ liệu, PC giữ bản thiếu | S02 |
| Màn hình chỉ đọc vài cột | DTO (`select new` / interface projection) | 1 SELECT đúng cột, không entity, không N+1 | S10a, G5-S15 |
| Phân trang có `join fetch` collection | luôn khai báo `countQuery` (hoặc 2 query, G5-S12b) | count tự sinh đếm dòng sau join | S08 |
| Bộ lọc động bằng `Specification` + phân trang | chỉ `fetch` khi `getResultType() != Long.class` | count query không có entity gốc | S09 |
| Native query | chỉ khi JPQL không biểu diễn được; trả `Tuple`/DTO nếu chỉ đọc | native bỏ qua `@Version`, `@SoftDelete`, và PC không biết DB đã đổi | S04–S07 |
| Native `UPDATE`/bulk trong transaction đã tải entity | `em.refresh`/`em.clear` sau đó, tự tăng `version` | entity MANAGED giữ giá trị cũ | S06 |

Ghi chú review PR: thấy `join fetch` cùng `where` trên alias của nó, hoặc `Page<…>` có `join fetch` mà không có `countQuery`, hoặc native query trên bảng có `@SoftDelete`/`@Version`, thì hỏi lại tác giả.
