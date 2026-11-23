# G2 — `CascadeType`: thao tác nào được **lan** từ cha xuống con?

**Câu hỏi trung tâm:** `persist / merge / remove / detach / refresh` được lan từ cha sang con khi nào? Đặt cascade ở phía nào thì nguy hiểm?

**Quy tắc nhóm:** quan hệ luôn hai chiều chuẩn (như G1-V4), có helper `addItem/removeItem`. Biến thể nằm ở `PurchaseOrder.items`
(trừ C6, C7, C8 nằm ở `OrderItem`). **Không dùng `orphanRemoval`** (đó là nhóm G3).

Entity nằm ở [src/main/.../g2_cascade](../../../../../../main/java/com/example/hibernatelab/g2_cascade/). Mỗi biến thể là **một package = một schema Postgres**,
có đủ 4 entity (`Customer`, `PurchaseOrder`, `OrderItem`, `Product`) và 4 repository. Tên class có tiền tố biến thể
(`C3PurchaseOrder`) để một class test dùng được nhiều biến thể mà không phải viết đầy đủ package.

## Hai khái niệm phải nắm

1. **Cascade là thao tác của `EntityManager` được "truyền tiếp" cho phần tử liên quan, không phải thao tác của DB.** Chỉ chạy khi đi qua
   `em.persist/merge/remove/detach/refresh` (và persist-on-flush). Bulk JPQL và `deleteAllInBatch()` **không** đi qua cascade (S12, S13c).
2. **Mỗi `CascadeType` độc lập.** `PERSIST` không giúp `MERGE`, `ALL` mới gồm đủ 5 loại. Liệt kê thủ công mà thiếu một loại thì loại đó không lan (S04c, S06b, S07b).

## Biến thể

| Mã | Schema | Thay đổi so với baseline C0 | Test |
|---|---|---|---|
| C0 | `g2_c0_none` | `items`: không cascade (**baseline**) | S01, S04a, S05a, S09d |
| C1 | `g2_c1_persist` | `cascade = PERSIST` | S02, S03, S04c |
| C2 | `g2_c2_merge` | `cascade = MERGE` | S04b |
| C3 | `g2_c3_remove` | `cascade = REMOVE` | S05b, S10a, S12a, S13 |
| C4 | `g2_c4_all` | `cascade = ALL` | S06a, S07a, S11b |
| C4-DEEP | `g2_c4deep_all_chain` | như C4, `Customer.orders` cũng `ALL` | S11a |
| C5 | `g2_c5_persist_merge` | `cascade = {PERSIST, MERGE}` (thiếu DETACH/REFRESH/REMOVE) | S06b, S07b |
| C6 | `g2_c6_remove_on_m2o` | `OrderItem.product` `cascade = REMOVE` (**anti-pattern**) | S08 |
| C7 | `g2_c7_persist_on_m2o` | `OrderItem.product` `cascade = PERSIST` | S09 |
| C8 | `g2_c8_ondelete` | `OrderItem.order` có `@OnDelete(CASCADE)`, `items` không cascade | S10b, S10c, S12b |

## Ma trận: thao tác × cascade → có lan xuống `items` không?

Điền từ kết quả test thật (Hibernate 7.4.15.Final). "✅" = lan, "❌" = không lan.

| Thao tác trên **order** | C0 (không) | C1 `PERSIST` | C2 `MERGE` | C3 `REMOVE` | C4 `ALL` | C5 `{PERSIST, MERGE}` |
|---|---|---|---|---|---|---|
| `persist(order)` | ❌ item bị bỏ qua im lặng (S01) | ✅ INSERT order rồi 2 item (S02) | — | — | ✅ (S11a, qua C4-DEEP) | — |
| thêm item vào order MANAGED, commit (persist-on-flush) | — | ✅ item được INSERT lúc flush (S03) | — | — | — | — |
| `merge(order DETACHED)` | ❌ thay đổi item **mất**, item mới bị bỏ (S04a) | ⚠️ item cũ **không** cập nhật, item mới thành **dòng ma rỗng** (S04c) | ✅ UPDATE cũ + INSERT mới (S04b) | — | — | — |
| `remove(order)` còn item | 💥 **vi phạm FK** (S05a) | — | — | ✅ SELECT, DELETE từng item rồi order (S05b) | — | — |
| `detach(order)` | — | — | — | — | ✅ item cũng DETACHED, 0 UPDATE (S06a) | ❌ item **vẫn MANAGED**, vẫn UPDATE (S06b) |
| `refresh(order)` | — | — | — | — | ✅ item nạp lại, thay đổi mất (S07a) | ❌ item giữ thay đổi, vẫn UPDATE (S07b) |

Dấu "—" là tổ hợp nhóm này **không có test riêng**, nên không khẳng định gì (đừng suy từ tên cascade: S04c cho thấy suy đoán dễ sai).

## Đặt cascade ở bảng A (cha) hay B (con)?

| | Trên **collection của cha** (`@OneToMany items`) | Trên **`@ManyToOne` của con** (`OrderItem.product`) |
|---|---|---|
| Ý nghĩa | Vòng đời con phụ thuộc cha (aggregate) | Con điều khiển vòng đời của thứ nó trỏ tới (thường sai) |
| PERSIST | ✅ hợp lý (S02, S03) | ⚠️ product DETACHED → `detached entity passed to persist` (S09a). Sửa: `getReferenceById` (S09b), `find` cùng tx (S09c), hoặc bỏ cascade (S09d, **khuyến nghị**) |
| REMOVE | ✅ hợp lý (S05b) | ❌ xoá 1 item kéo theo xoá product dùng chung: lỗi FK (S08a) hoặc, tệ hơn, **mất product im lặng** (S08b) |

## cascade JPA (C3) vs `@OnDelete` ở DB (C8)

| | C3 `cascade = REMOVE` | C8 `@OnDelete(CASCADE)` |
|---|---|---|
| Ai xoá item | Hibernate, từng dòng một | PostgreSQL (`ON DELETE CASCADE` trong FK) |
| SQL xoá order 3 item | 1 SELECT + 3 DELETE item + 1 DELETE order (S10a) | **1 DELETE order**, không SELECT (S10b) |
| Bulk JPQL `delete from PurchaseOrder` | 💥 vi phạm FK (S12a) | ✅ thành công (S12b) |
| Entity item đã tải vào bộ nhớ | chuyển REMOVED | **vẫn MANAGED** dù dòng đã mất khỏi DB (S10c) |
| Khi nào dùng | Cần callback/listener JPA, hoặc con có quan hệ riêng cần cascade tiếp | Xoá hàng loạt, muốn nhanh; chấp nhận bộ nhớ có thể lệch |

## Tình huống

| Mã | Test | Câu hỏi → Kết luận (đã xác minh, Hibernate 7.4.15.Final) |
|---|---|---|
| G2-S01 ⭐🔬 | [G2CascadePersistTest](G2CascadePersistTest.java) `s01_` | C0: persist order có item mới? → Chỉ INSERT order; item **bị bỏ qua im lặng**, không lỗi (giả thuyết (a)). Item vẫn TRANSIENT. |
| G2-S02 ⭐ | `G2CascadePersistTest` `s02_` | C1: PERSIST lan xuống? → INSERT order rồi 2 item; cả 3 cùng TRANSIENT → MANAGED lúc `persist`. |
| G2-S03 ⭐ | `G2CascadePersistTest` `s03_` | C1: `addItem` vào order MANAGED? → Item được INSERT **lúc flush** (persist-on-flush), không cần gọi persist. |
| G2-S04a ⭐🔬 | [G2CascadeMergeTest](G2CascadeMergeTest.java) `s04a_` | C0: merge đồ thị DETACHED? → Thay đổi item **mất**, item mới không INSERT, 0 UPDATE, không lỗi. |
| G2-S04b ⭐ | `G2CascadeMergeTest` `s04b_` | C2: có MERGE? → 1 UPDATE item cũ + 1 INSERT item mới, order không UPDATE. |
| G2-S04c ⭐🔬 | `G2CascadeMergeTest` `s04c_` | C1: chỉ PERSIST mà gọi merge? → Item cũ **không** cập nhật; item mới thành **dòng ma** (`quantity = 0`, FK NULL). |
| G2-S05a ⭐ | [G2CascadeRemoveTest](G2CascadeRemoveTest.java) `s05a_` | C0: xoá order còn item? → Vi phạm FK (`DataIntegrityViolationException`), rollback, DB nguyên. |
| G2-S05b ⭐ | `G2CascadeRemoveTest` `s05b_` | C3: có REMOVE? → SELECT item, DELETE từng item rồi DELETE order; order và item cùng REMOVED lúc `remove`. |
| G2-S06a ⭐ | [G2CascadeDetachRefreshTest](G2CascadeDetachRefreshTest.java) `s06a_` | C4: `detach(order)`? → Item cũng DETACHED, sửa item không UPDATE. |
| G2-S06b ⭐ | `G2CascadeDetachRefreshTest` `s06b_` | C5: thiếu DETACH? → Item **vẫn MANAGED**, vẫn bị UPDATE dù order đã detach. |
| G2-S07a ➕ | `G2CascadeDetachRefreshTest` `s07a_` | C4: `refresh(order)`? → Item nạp lại, thay đổi chưa flush mất, 0 UPDATE. |
| G2-S07b ➕ | `G2CascadeDetachRefreshTest` `s07b_` | C5: thiếu REFRESH? → Item giữ thay đổi, bị flush thành UPDATE. |
| G2-S08a ⭐ | [G2CascadeManyToOneTest](G2CascadeManyToOneTest.java) `s08a_` | C6: xoá 1 trong 2 item cùng product? → Hibernate cố DELETE product, item kia còn trỏ tới → vi phạm FK. |
| G2-S08b ⭐ | `G2CascadeManyToOneTest` `s08b_` | C6: product chỉ có 1 item? → Product **bị xoá âm thầm** theo item (mất dữ liệu master). |
| G2-S09a ⭐ | `G2CascadeManyToOneTest` `s09a_` | C7: persist item với product DETACHED? → `PersistentObjectException` ("detached entity passed to persist"), Spring bọc thành `InvalidDataAccessApiUsageException`. |
| G2-S09b/c/d ⭐ | `G2CascadeManyToOneTest` `s09b_`, `s09c_`, `s09d_` | Cách sửa? → `getReferenceById` (0 SELECT), `find` cùng tx (1 SELECT), hoặc bỏ cascade (C0, 0 SELECT, đơn giản nhất). |
| G2-S10a ⭐ | `G2CascadeRemoveTest` `s10a_` | C3: xoá order 3 item? → 1 SELECT + 3 DELETE item + 1 DELETE order. |
| G2-S10b ⭐🔬 | `G2CascadeRemoveTest` `s10b_` | C8: DDL và SQL? → FK `ON DELETE CASCADE`; chỉ **1 DELETE order**, **không** SELECT item. |
| G2-S10c ➕🔬 | `G2CascadeRemoveTest` `s10c_` | C8: item đã tải trước khi xoá? → Item **vẫn MANAGED** (bộ nhớ lệch DB). |
| G2-S11a ➕ | `G2CascadePersistTest` `s11a_` | C4-DEEP: cascade 3 tầng? → `persist(customer)` INSERT 1 + 2 + 4 dòng, cha trước con. |
| G2-S11b ➕ | `G2CascadePersistTest` `s11b_` | C4: thiếu cascade ở tầng trên? → Chỉ customer được lưu; order và item bị bỏ qua im lặng. |
| G2-S12a ➕ | `G2CascadeRemoveTest` `s12a_` | C3: bulk JPQL delete? → Bỏ qua cascade, vi phạm FK (EntityManager trực tiếp: `ConstraintViolationException` ném thẳng). |
| G2-S12b ➕ | `G2CascadeRemoveTest` `s12b_` | C8: bulk JPQL delete? → Thành công nhờ DB cascade. |
| G2-S13a/b/c ➕ | `G2CascadeRemoveTest` `s13a_`, `s13b_`, `s13c_` | Spring Data trên C3? → `deleteById` và `delete(entity DETACHED)` đều cascade; `deleteAllInBatch()` là bulk nên vi phạm FK. |

## Kết quả đáng chú ý (🔬)

Ghi đầy đủ ở [docs/RESULTS.md](../../../../../../../docs/RESULTS.md). Không có tình huống nào **ngược** với giả thuyết, nhưng có hai điều chi tiết hơn giả thuyết:

1. **Merge thiếu `MERGE` có thể sinh dòng ma (S04c).** Chỉ có `PERSIST`: item cũ không được cập nhật, còn item mới được INSERT dưới dạng bản sao **rỗng** (`quantity = 0`, `order_id`/`product_id` = NULL). Không báo lỗi gì.
2. **`@OnDelete` không SELECT item, và Hibernate không biết item đã biến mất (S10b, S10c).**

## DDL trích ra (từ `build/ddl/create.sql`)

**C3** — FK mặc định (`NO ACTION`): DB từ chối xoá order còn item.

```sql
alter table if exists g2_c3_remove.order_item
   add constraint FKlbn1xgrugcgx68cetsqa6f7px foreign key (order_id) references g2_c3_remove.purchase_order;
```

**C8** — `@OnDelete(action = CASCADE)` chỉ thêm đúng một mệnh đề vào FK:

```sql
alter table if exists g2_c8_ondelete.order_item
   add constraint FKlbn1xgrugcgx68cetsqa6f7px foreign key (order_id) references g2_c8_ondelete.purchase_order
   on delete cascade;
```

## Chạy

```bash
./gradlew test --tests '*G2*'            # cả nhóm G2
./gradlew test --tests '*G2CascadeRemoveTest*'   # một class
./gradlew test --tests '*G2CascadeRemoveTest*s10b_*'   # một test method
```
