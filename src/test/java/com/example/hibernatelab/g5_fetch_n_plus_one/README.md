# G5 — `FetchType` & N+1

**Câu hỏi trung tâm:** khi nào Hibernate tải quan hệ, bằng bao nhiêu câu SQL, và chuyện gì xảy ra khi truy cập quan hệ ngoài transaction?

**Model:** `Customer` 1—N `PurchaseOrder` 1—N `OrderItem` N—1 `Product`, và `Customer` 1—N `Payment`.

**Dữ liệu chuẩn** ([G5Fixtures](G5Fixtures.java), seed bằng JDBC để không có entity nào nằm sẵn trong persistence context):
5 customer × 2 order × 2 item = 10 order, 20 item; 3 product; 3 payment, **tất cả thuộc Customer-1**
(nên chỉ Customer-1 có 2 order × 3 payment, đủ để thấy tích Descartes ở S11).

Entity nằm ở [src/main/.../g5_fetch_n_plus_one](../../../../../../main/java/com/example/hibernatelab/g5_fetch_n_plus_one/).
Mỗi biến thể là **một package = một schema Postgres**, tên class có tiền tố biến thể (`F1Customer`).

## Ba khái niệm phải nắm

1. **EAGER/LAZY chỉ nói "tải khi nào", không nói "tải bằng gì".** `find()` biết trước nên nối `JOIN`; JPQL được dịch nguyên văn nên EAGER
   thành thêm SELECT cho từng phần tử, tức N+1 (S01 vs S02).
2. **LAZY không tự cứu bạn khỏi N+1.** LAZY chỉ hoãn việc tải; lặp qua N phần tử và chạm vào quan hệ của từng phần tử vẫn tốn N SELECT (S05, S16).
   Truy cập sau khi hết transaction thì `LazyInitializationException` (S04, S14).
3. **Số SELECT phụ phụ thuộc cách sửa:** `JOIN FETCH` / `@EntityGraph` gộp thành 1 SELECT (nhưng join collection làm nhân số dòng),
   `@BatchSize` / `SUBSELECT` gộp thành 2 SELECT mà không đổi câu query, DTO projection thì không tải entity nào.

## Biến thể

| Mã | Schema | Khác baseline F1 | Test |
|---|---|---|---|
| F0 | `g5_f0_defaults` | **không ghi `fetch`**: dùng mặc định JPA (`@ManyToOne` = EAGER, `@OneToMany` = LAZY) | S01, S02 |
| F1 | `g5_f1_all_lazy` | mọi quan hệ `LAZY` tường minh, collection là `List` (**baseline, khuyến nghị**) | S03–S07, S10, S12, S14–S16 |
| F1S | `g5_f1s_all_lazy_set` | collection là `Set` thay vì `List` | S11 |
| F2 | `g5_f2_eager_collection` | `Customer.orders` = `EAGER` (anti-pattern) | S13 |
| F3 | `g5_f3_batch_size` | `@BatchSize(size = 10)` trên collection và entity | S08 |
| F4 | `g5_f4_subselect` | `@Fetch(FetchMode.SUBSELECT)` trên `Customer.orders` | S09 |

## Bảng kết quả: số SELECT (5 customer, 10 order, 20 item)

| Tình huống | Biến thể | Số SELECT | Ghi chú |
|---|---|---|---|
| `find(order)` (S01) | F0 | 1 | có `left join customer` (EAGER `@ManyToOne`) |
| JPQL `select o from PurchaseOrder` (S02) | F0 | 1 + 5 | N+1: một SELECT cho mỗi customer khác nhau |
| `find(order)` rồi `getCustomer().getId()` / `.getName()` (S03) | F1 | 1, rồi +1 | `getId()` không SELECT; `getName()` mới SELECT |
| lặp `getOrders().size()` của 5 customer (S05) | F1 | 1 + 5 | N+1 trên collection |
| `join fetch c.orders` (S06) | F1 | **1** | 5 customer, không cần `distinct` (Hibernate 6+) |
| `@EntityGraph(attributePaths = "orders")` (S07) | F1 | **1** | cùng kết quả, dùng `left join` |
| lặp `getOrders().size()` (S08) | F3 | **2** | câu 2: `customer_id = any (?)` (mảng, Postgres) |
| lặp `getOrders().size()` (S09) | F4 | **2** | câu 2: `customer_id in (select … from customer)` |
| lặp `item.getProduct().getName()` (S16) | F1 | 1 + 3 | 3 = số product khác nhau; `join fetch i.product` còn 1 |
| DTO projection, record và interface (S15) | F1 | 1 mỗi kiểu | chỉ đúng cột cần; 0 entity trong persistence context |
| `findAll()` customers (S13b) | F2 | 1 + 5 | EAGER collection tự sinh N+1 dù không lặp gì |

## Cạm bẫy khi sửa N+1

| Cạm bẫy | Biến thể | Kết quả thực tế | Cách vòng |
|---|---|---|---|
| `join fetch` hai `List` (S10a) | F1 | `MultipleBagFetchException` khi **chạy** query, 0 SQL | 2 query nối tiếp trong cùng tx (S10b), hoặc `Set` |
| `left join fetch` hai `Set` (S11) | F1S | chạy được, nhưng Customer-1 trả 2 × 3 = **6 dòng** (14 dòng tổng cho 5 customer) | tải riêng từng collection: nhân → cộng |
| phân trang + `join fetch` collection (S12a) | F1 | Hibernate 7.4: **cắt trang ở DB** bằng subquery, 1 SELECT, không cảnh báo | cách kinh điển 2 query (S12b) vẫn đúng ở mọi version |
| `@OneToMany` EAGER (S13) | F2 | `find` luôn tải orders; `findAll` N+1; `@EntityGraph` ghi đè được | giữ LAZY, tải theo từng use case |

## Tình huống 🔬 khác giả thuyết

Chi tiết ở [docs/RESULTS.md](../../../../../../../docs/RESULTS.md).

- **S10a**: `MultipleBagFetchException` **không** ném lúc `createQuery` mà lúc chạy (`getResultList()`), vẫn trước khi có SQL nào.
- **S12a**: Hibernate 7.4 **không** phân trang trong bộ nhớ và không còn cảnh báo `HHH90003004` trên Postgres; subquery `fetch first` chạy ở DB.
  Đây là điểm khác lớn so với tài liệu và bài viết cũ (Hibernate 5/6).
- **S13c**: `@EntityGraph` **tắt được** EAGER của attribute không nằm trong graph (Spring Data dùng `fetchgraph`), trái giả thuyết ban đầu.

## Chạy

```bash
./gradlew test --tests '*G5*'                    # cả nhóm
./gradlew test --tests '*G5LazyTest*s05_*'       # một tình huống
```
