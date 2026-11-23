# G4 — Sửa **bảng con** qua cha mà **không đổi bảng cha**

**Câu hỏi trung tâm:** sửa, thêm, xoá con bằng cách đi qua `order.getItems()`, thì dòng `purchase_order` có bị đụng tới không
(UPDATE, `version`, `updated_at`, callback `@PreUpdate`)?

**Model:** `PurchaseOrder` (`@Version Long version`, `status`, `@UpdateTimestamp updatedAt`, callback `@PreUpdate` có counter)
1—N `OrderItem` (`quantity`, `unitPrice`) N—1 `Product`, cộng `Customer`.

**Cách chứng minh "cha không đổi":** chụp snapshot dòng cha bằng JDBC (`db.rowSnapshot`, mọi cột) trước và sau thao tác rồi so bằng nhau,
kèm `sql.assertUpdates("purchase_order", 0)`.

Entity nằm ở [src/main/.../g4_update_child_via_parent](../../../../../../main/java/com/example/hibernatelab/g4_update_child_via_parent/).
Mỗi biến thể là **một package = một schema Postgres**, tên class có tiền tố biến thể (`U0PurchaseOrder`).

## Hai khái niệm phải nắm

1. **Dirty checking chạy RIÊNG cho từng entity.** Sửa `item.quantity` làm *item* dirty, còn *order* thì không (collection **inverse**
   `mappedBy` không phải là state của order). Nên chỉ có `UPDATE order_item`; `version`, `updated_at`, `@PreUpdate` của cha đều không chạy (S01, S04, S05, S08).
2. **Collection OWNING thì khác:** nếu collection tự ghi bảng nối (`@OneToMany` một chiều + `@JoinTable`) thì nó **thuộc state của cha**,
   đổi nó làm cha dirty → `UPDATE purchase_order` và tăng `version` (S06). `@OptimisticLock(excluded = true)` tắt điều này (S07).

## Biến thể

| Mã | Schema | Khác baseline U0 | Test |
|---|---|---|---|
| U0 | `g4_u0_baseline` | hai chiều `mappedBy`, `items: cascade = ALL, orphanRemoval = true` (**baseline**) | S01, S02, S04, S05, S08, S09, S10a, S12, S13 |
| U1 | `g4_u1_no_merge` | `items: cascade = PERSIST` (không MERGE) | S03 |
| U2 | `g4_u2_owning_collection` | `items` là `@OneToMany` một chiều + `@JoinTable` (collection thuộc phía owning) | S06 |
| U3 | `g4_u3_optimistic_excluded` | như U2 + `@OptimisticLock(excluded = true)` | S07 |
| U4 | `g4_u4_dynamic_update` | `OrderItem` có `@DynamicUpdate` | S10b |
| U5 | `g4_u5_child_version` | `OrderItem` có `@Version` riêng | S11 |

## Bảng kết quả (điền từ test thật)

| Thao tác | Biến thể | `UPDATE order_item` | `UPDATE purchase_order` (version) | Ghi chú |
|---|---|---|---|---|
| Sửa quantity qua cha MANAGED (S01) | U0 | 1 | **0** | Cha giữ nguyên mọi cột |
| `save(order DETACHED)` có sửa item (S02) | U0 | 1 | **0** | `save` = `merge`: SELECT order, SELECT items, rồi UPDATE item |
| `save(order DETACHED)` không có MERGE (S03) | U1 | **0** | 0 | Thay đổi của item **mất im lặng** |
| Thêm item qua cha (S04) | U0 | (INSERT 1) | **0** | |
| Xoá item qua cha (S05) | U0 | (DELETE 1) | **0** | |
| Thêm item vào collection owning (S06) | U2 | (INSERT 1) | **1, version +1** | Bảng nối bị recreate: DELETE + INSERT 2 dòng |
| Như S06 + `excluded = true` (S07) | U3 | (INSERT 1) | **0** | Bảng nối vẫn recreate |
| `@PreUpdate` / `updated_at` khi chỉ con đổi (S08) | U0 | 1 | 0 | Counter `@PreUpdate` = 0; đối chứng đổi `status` cha thì counter = 1 |
| `find(…, OPTIMISTIC_FORCE_INCREMENT)` rồi sửa item (S09) | U0 | 1 | **1, chỉ `set version`** | Cách chủ động tăng version aggregate root |

## `@DynamicUpdate` (S10)

| | Câu `UPDATE order_item` khi chỉ đổi `quantity` |
|---|---|
| U0 (mặc định) | `update … set order_id=?,product_id=?,quantity=?,unit_price=? where id=?` (TẤT CẢ cột) |
| U4 (`@DynamicUpdate`) | `update … set quantity=? where id=?` |

Đổi lại, `@DynamicUpdate` buộc Hibernate sinh câu SQL mỗi lần flush; chỉ đáng dùng khi bảng nhiều cột/cột lớn.

## Đi qua cha vs đi thẳng, và bulk JPQL

- **S12 (chi phí):** DB kết quả như nhau, nhưng qua cha phải SELECT order + toàn bộ items; qua `OrderItemRepository` chỉ SELECT 1 item (order, product là LAZY).
- **S13 (bulk JPQL):** `update OrderItem set quantity = …` đi thẳng vào DB, bỏ qua persistence context: item đang MANAGED **vẫn giữ giá trị cũ** (S13a).
  `@Modifying(clearAutomatically = true)` gọi `em.clear()` sau câu UPDATE nên item thành DETACHED và `find` lại thấy giá trị mới (S13b).

## Cập nhật đồng thời (S11)

Với U5, hai người cùng tải đơn rồi cùng sửa một item: người lưu sau nhận `ObjectOptimisticLockingFailureException` (version item cũ),
DB giữ giá trị của người lưu trước. Version của **cha** không đổi, vì `@Version` của item là cơ chế khoá riêng.

## Tình huống 🔬 khác giả thuyết

- **S06**: version cha tăng đúng giả thuyết, nhưng bảng nối **không** chỉ INSERT 1 dòng: Hibernate xoá hết dòng nối của order rồi ghi lại cả hai
  (collection `List` không có `@OrderColumn` bị recreate). Chi tiết ở [docs/RESULTS.md](../../../../../../../docs/RESULTS.md).
