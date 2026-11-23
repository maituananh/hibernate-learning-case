# G9 — Tình huống tổng hợp (có service layer)

**Câu hỏi trung tâm:** khi nhiều annotation (`cascade`, `orphanRemoval`, `@Version`, `LAZY`, `@OnDelete`, …) cùng xuất hiện trong một nghiệp vụ thật,
và có thêm ranh giới transaction của service, thì chuyện gì xảy ra? Nhóm này luyện kỹ năng **gỡ rối**, không dạy annotation mới.

**Model:** thương mại điện tử (`Customer`, `PurchaseOrder`, `OrderItem`, `Product`) cùng `Category` dạng cây. Mỗi tình huống có package + schema riêng.

Entity và service nằm ở [src/main/.../g9_integrated](../../../../../../main/java/com/example/hibernatelab/g9_integrated/).

## Tình huống và test

| Mã | Schema | Nội dung | Test |
|---|---|---|---|
| S01 ⭐ | `g9_s01_category_tree` | `Category` tự tham chiếu (cây 3 tầng) + `Product.category` | `G9S01CategoryTreeTest` |
| S02 ⭐ | `g9_s02_order_update` | DTO → entity qua `OrderUpdateService`: cách A (diff) vs cách B (merge) | `G9S02OrderUpdateTest` |
| S03 ⭐ | `g9_s03a_hash_by_id`, `g9_s03b_business_key` | `equals/hashCode` theo id vs business key, trong `Set` | `G9S03EqualsHashCodeTest` |
| S04 ➕ | `g9_s04_recursive_tostring` | `toString()` đệ quy hai chiều → `StackOverflowError` | `G9S04RecursiveToStringTest` |
| S05 ⭐ | `g9_s05_bulk_modifying` | Bulk `@Modifying` JPQL và persistence context bị lệch | `G9S05BulkModifyingTest` |
| S06 ⭐ | `g9_s06a_cascade_remove`, `g9_s06b_on_delete`, `g9_s06c_no_strategy` | So sánh 4 chiến lược xoá `Customer` | `G9S06DeleteStrategiesTest` |
| S07 ⭐ | `g9_s07_tx_boundary` | Ranh giới transaction: `OrderQueryService` | `G9S07TransactionBoundaryTest` |
| S08 ➕ | (dùng lại `g9_s02_order_update`) | Hai người cùng sửa một đơn (optimistic lock) | `G9S08S09OptimisticLockAndMoveItemTest` |
| S09 ➕ | (dùng lại `g9_s02_order_update`) | Chuyển item giữa hai đơn + tính lại tổng tiền | `G9S08S09OptimisticLockAndMoveItemTest` |
| S10 ➕ | — | Batch insert SEQUENCE vs IDENTITY: **chưa làm** (cần thêm thư viện `datasource-proxy`, tuỳ chọn) | — |
| S11 ➕🔬 | `g9_s11_soft_delete` | `@SoftDelete` trên `OrderItem` | `G9S11SoftDeleteTest` |

## S01 — Cây danh mục

- **Chuyển nhánh chỉ cần đổi owning side**: `phones.setParent(home)` → đúng **1 UPDATE** `parent_id`; cả nhánh con đi theo vì chúng trỏ vào `phones` (S01b).
- **Xoá node giữa** bằng `removeChild` + `orphanRemoval`: DELETE cả nhánh, con trước cha (FK của PostgreSQL bắt buộc thứ tự này). Còn `product` trỏ tới thì bị FK chặn và rollback (S01c, S01d).
- **Duyệt cây bằng `getChildren()` là N+1**: 4 node → 5 SELECT (S01e). Sửa: `select c from Category c` (1 SELECT) rồi ghép cây bằng `getParent()`. Lưu ý
  gọi `getChildren()` sau đó vẫn SELECT, vì collection lazy **không tự được điền** dù các node con đã nằm trong persistence context (S01f).

## S02 — Cập nhật đơn hàng từ DTO: cách A vs cách B

| | A: tải rồi áp diff (`updateByDiff`) | B: `new` + `merge` (`updateByMerge`) |
|---|---|---|
| Entity được thay đổi | chính bản MANAGED tải từ DB | bản `new` (DETACHED, có id) → `merge` copy đè lên bản MANAGED |
| `status`, `createdAt`, `customer`, `total` | **giữ nguyên** (DTO không đụng tới) | **bị ghi đè thành NULL** |
| Số câu SQL ghi | 1 UPDATE order, 1 UPDATE + 1 INSERT + 1 DELETE item | **giống hệt** |
| Số SELECT | 2 (order, rồi items) | 1 (order + items bằng left join) |
| Phát hiện lỗi | — | **không lỗi, không thể thấy qua đếm SQL**: chỉ lộ khi đọc dữ liệu thật |

Bài học: Hibernate mặc định `UPDATE` **mọi** cột, nên khác biệt giữa A và B chỉ nằm ở **giá trị bind**. Đó là lý do mọi test của lab đọc DB thật bằng JDBC.

## S03 — `equals/hashCode` trong `Set`

| | (a) theo id | (b) theo business key (`lineCode`) |
|---|---|---|
| Tạo mới, add vào `Set`, persist, `remove` ngay (cùng transaction) | `contains` = false, `remove` = **false**, 0 DELETE, item **còn trong DB** | `contains` = true, `remove` = true, 1 DELETE (orphanRemoval) |
| `Set` nạp lại từ DB rồi `remove` | chạy đúng (S03c) | chạy đúng |
| Vì sao | hashCode đổi (null → số) **sau khi** đã nằm trong `HashSet` | hashCode không đổi suốt vòng đời |

➕ Proxy (S03d): `equals` dùng `getClass()` trả về **false** khi so với proxy Hibernate; dùng `instanceof` + getter thì đúng.

## S05 — Bulk JPQL và persistence context

| | Bulk update không clear | `clearAutomatically = true` | Bulk delete `OrderItem` |
|---|---|---|---|
| DB | đã đổi | đã đổi | item đã mất |
| Entity đã tải | **giữ giá trị cũ** (MANAGED, nhưng cũ) | thành **DETACHED**, tải lại thấy giá trị mới | `order.getItems()` vẫn còn item "ma" |
| Cái giá | dữ liệu lệch | mọi thay đổi chưa flush của **mọi** entity bị mất | bỏ qua cascade REMOVE và orphanRemoval |

## S06 — So sánh bốn chiến lược xoá (1 customer, 2 order, 4 item)

| | (a) cascade REMOVE | (b) `@OnDelete(CASCADE)` | (c) bulk delete dưới lên | (d) không chiến lược |
|---|---|---|---|---|
| Số SQL | 11 (4 SELECT + 7 DELETE) | **2** (1 SELECT + 1 DELETE) | **3** (3 DELETE, 0 SELECT) | lỗi |
| Thứ tự | item, order, item, order, customer (con trước cha) | chỉ DELETE customer; PostgreSQL tự xoá con | item → order → customer (tự viết) | DELETE customer bị chặn |
| Trạng thái PC sau khi xoá | customer **và** order: `REMOVED` | customer `REMOVED`; order đã tải vẫn `MANAGED` ("ma") | PC không biết gì | rollback |
| Callback / cascade của entity | chạy | **không** | **không** | — |
| Ưu | đơn giản, đúng cho dữ liệu ít | ít SQL nhất | ít SQL, kiểm soát được | — |
| Nhược | nhiều SELECT/DELETE (N+1 khi xoá) | logic xoá nằm ở DB, PC có thể lệch | tự giữ đúng thứ tự FK, bỏ qua PC | `DataIntegrityViolationException` |

## S07 — Ranh giới transaction

| Cách | Kết quả | Vì sao |
|---|---|---|
| (a) service không `@Transactional` rồi chạm `getItems()` | `LazyInitializationException` | `find` tự mở transaction ngắn, đóng ngay; order thành DETACHED |
| (b) self-invocation `this.method()` | `@Transactional` bị bỏ qua (`isTransactionActive` = false) | gọi qua `this` không đi qua proxy (nối với G0-S14) |
| (c) `@Transactional` nhưng trả về entity | `LazyInitializationException` ở caller | transaction đóng ở cuối method, entity DETACHED |
| (d) `@Transactional` trả về DTO | an toàn | đọc xong mọi thứ trong transaction rồi sao chép sang `record` |

## S08 — Optimistic lock

Hai bản DETACHED cùng `version = 0`; lưu bản 1 (version → 1) rồi lưu bản 2:
- qua `repository.save(...)` → `ObjectOptimisticLockingFailureException` (Spring đã dịch);
- gọi thẳng `em.merge(...)` → `jakarta.persistence.OptimisticLockException` (chưa dịch, vì không qua bean `@Repository`).

DB giữ dữ liệu của bản 1, version = 1.

## S09 — Chuyển item giữa hai đơn

`orderA.removeItem(item)` rồi `orderB.addItem(item)` trong cùng transaction: **0 DELETE** (item được gắn sang collection khác nên orphanRemoval không xoá),
1 UPDATE `order_item` (đổi `order_id`), 2 UPDATE `purchase_order` (`total` đổi nên **cả hai** version tăng).
Khác với G4 (con đổi mà cha không đổi): ở đây đụng tới cha là **cố ý** vì `total` là cột thật của cha.

## S11 — `@SoftDelete` 🔬

`em.remove(item)` và orphanRemoval đều thành **1 UPDATE `deleted = true`** (0 DELETE); dòng vẫn nằm trong bảng; collection của cha tự lọc item đã xoá (SELECT thêm điều kiện `deleted`).

## Tình huống 🔬 khác giả thuyết

Chi tiết ở [docs/RESULTS.md](../../../../../../../docs/RESULTS.md): G9-S08 (exception kiểu nào tuỳ có qua repository hay không), G9-S09, G9-S11.
