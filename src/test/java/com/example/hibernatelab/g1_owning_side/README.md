# G1 — Owning side: `@ManyToOne` / `@OneToMany` / `mappedBy` / `@JoinColumn`

**Câu hỏi trung tâm:** FK nằm ở đâu? Phía nào **ghi** FK? Bỏ `mappedBy` hoặc `@JoinColumn` thì chuyện gì xảy ra?

**Quy tắc nhóm:** G1 **không dùng cascade**: entity nào cũng phải `persist` tường minh, để cascade không làm nhiễu kết quả.

Entity nằm ở [src/main/.../g1_owning_side](../../../../../../main/java/com/example/hibernatelab/g1_owning_side/). Mỗi biến thể là **một package = một schema Postgres**,
có đủ 4 entity (`Customer`, `PurchaseOrder`, `OrderItem`, `Product`). `OrderItem` luôn là `@ManyToOne` tới order và product; biến thể chỉ khác nhau ở quan hệ `Customer` ↔ `PurchaseOrder`.

## Hai khái niệm phải nắm

1. **Owning side = phía có quyền ghi FK.** Với `@ManyToOne` thì đó là phía chứa cột FK. Với `@OneToMany(mappedBy = "...")` phía collection chỉ là **inverse side**: Hibernate bỏ qua nó khi ghi DB.
2. **Hibernate không tự đồng bộ hai phía trong bộ nhớ.** Muốn bộ nhớ khớp DB phải dùng helper `addOrder()/removeOrder()` (G1-S14).

## Biến thể

| Mã | Schema | `Customer` (bảng cha) | `PurchaseOrder` (bảng con) | Test |
|---|---|---|---|---|
| V1 | `g1_v1_m2o_uni` | không có collection | `@ManyToOne customer` (**baseline**, khuyến nghị) | [G1V1ManyToOneTest](G1V1ManyToOneTest.java) |
| V1-NN | `g1_v1nn_m2o_nullable_false` | không có collection | `@ManyToOne @JoinColumn(nullable = false)` | [G1V1NullableOptionalTest](G1V1NullableOptionalTest.java) |
| V1-OPT | `g1_v1opt_m2o_optional_false` | không có collection | `@ManyToOne(optional = false)` | `G1V1NullableOptionalTest` |
| V2 | `g1_v2_o2m_uni_jointable` | `@OneToMany` (không `@JoinColumn`) → **bảng nối** | không có field customer | [G1V2JoinTableTest](G1V2JoinTableTest.java) |
| V3 | `g1_v3_o2m_uni_joincolumn` | `@OneToMany @JoinColumn(name = "customer_id")` | không có field customer | [G1V3JoinColumnTest](G1V3JoinColumnTest.java) |
| V3-NN | `g1_v3nn_o2m_uni_joincolumn_nn` | như V3 + `nullable = false` | không có field customer | [G1V3NNJoinColumnNullableFalseTest](G1V3NNJoinColumnNullableFalseTest.java) |
| V4 | `g1_v4_bidi_mappedby` | `@OneToMany(mappedBy = "customer")` | `@ManyToOne customer` | [G1V4BidirectionalTest](G1V4BidirectionalTest.java) |
| V5 | `g1_v5_bidi_no_mappedby` | `@OneToMany` (**quên** `mappedBy`) | `@ManyToOne customer` | [G1V5NoMappedByTest](G1V5NoMappedByTest.java) |
| V6 | `g1_v6_bidi_owner_parent` | `@OneToMany @JoinColumn` | `@ManyToOne @JoinColumn(insertable = false, updatable = false)` | [G1V6OwnerParentTest](G1V6OwnerParentTest.java) |

Thứ tự nên đọc: V1 → V1-NN/OPT → V2 → V3 → V3-NN → V4 → V5 → V6.

## Đặt annotation ở bảng A (cha) hay bảng B (con)?

| | Đặt ở **bảng con** (`@ManyToOne` trên PurchaseOrder) | Đặt ở **bảng cha** (`@OneToMany` trên Customer) |
|---|---|---|
| FK ở đâu | `purchase_order.customer_id` | Không `@JoinColumn`: **bảng nối** `customer_orders`. Có `@JoinColumn`: `purchase_order.customer_id` |
| SQL khi thêm 1 order | **1 INSERT** (V1) | Bảng nối: INSERT order + INSERT bảng nối (V2). JoinColumn: INSERT + **UPDATE bù** (V3) |
| Điều hướng | order → customer | customer → orders |
| Khuyến nghị | ✅ mặc định nên dùng | ⚠️ dùng khi thật sự cần, ưu tiên hai chiều V4 |

## Tình huống

| Mã | Test | Câu hỏi → Kết luận (đã xác minh, Hibernate 7.4.15.Final) |
|---|---|---|
| G1-S01 ⭐ | `G1V1ManyToOneTest` `s01_` | DDL của `@ManyToOne`? → `customer_id` nằm ở bảng **con**, nullable, có FK tên đúng như `@ForeignKey(name)`. |
| G1-S02 ⭐ | `G1V1ManyToOneTest` `s02_` | Persist order cho customer đã có? → `getReference` đủ để ghi FK: **0 SELECT** customer, 1 INSERT order + 2 INSERT item. |
| G1-S03 ⭐ | `G1V1ManyToOneTest` `s03_` | Cha TRANSIENT, không cascade? → Lỗi lúc flush (`TransientPropertyValueException` bọc trong `InvalidDataAccessApiUsageException`), DB trống. |
| G1-S04a ⭐ | [G1V1NullableOptionalTest](G1V1NullableOptionalTest.java) `s04a_` | V1 mặc định? → Lưu được order không customer (`customer_id = NULL`); `find` dùng `left join`. |
| G1-S04b ⭐🔬 | `G1V1NullableOptionalTest` `s04b_` | V1-NN `nullable = false`? → **Hibernate chặn trước** (`PropertyValueException`, 0 INSERT), không phải DB từ chối. **Khác giả thuyết.** |
| G1-S04c ⭐ | `G1V1NullableOptionalTest` `s04c_` | V1-OPT `optional = false`? → Cũng `PropertyValueException`, 0 INSERT. |
| G1-S04d/e ⭐🔬 | `G1V1NullableOptionalTest` `s04d_`, `s04e_` | Loại JOIN? → V1-OPT **và V1-NN** đều `join` (inner); chỉ V1 mặc định `left join`. **V1-NN khác giả thuyết.** |
| G1-S05 ➕ | `G1V1ManyToOneTest` `s05_` | Hai FK cùng bảng? → Cột đặt tên theo **field**: `billing_address_id`, `shipping_address_id`, mỗi cột một FK. |
| G1-S06a ⭐ | `G1V1ManyToOneTest` `s06a_` | Map cột FK 2 lần, set `Long customerId`? → FK được ghi (property có quyền ghi). |
| G1-S06b ⭐ | `G1V1ManyToOneTest` `s06b_` | Chỉ set field `customer` (`insertable = false`)? → **FK = NULL** (bẫy: code trông đúng nhưng FK mất). |
| G1-S06c ⭐ | `G1V1ManyToOneTest` `s06c_` | `[BOOT-FAIL]` cả hai cùng ghi? → `MappingException` "Column 'customer_id' is duplicated in mapping…". |
| G1-S07a/b ⭐ | [G1V2JoinTableTest](G1V2JoinTableTest.java) `s07a_`, `s07b_` | `@OneToMany` không `@JoinColumn`? → **Bảng nối** `customer_orders` (unique trên `orders_id`), `purchase_order` không có FK; thêm 2 order = **5 INSERT**. |
| G1-S08 ⭐🔬 | `G1V2JoinTableTest` `s08_` | Gỡ 1/3 phần tử khỏi `List` (bag)? → **DELETE cả dòng bảng nối của customer rồi INSERT lại 2 dòng**; order vẫn còn. |
| G1-S09a/b ⭐🔬 | [G1V3JoinColumnTest](G1V3JoinColumnTest.java) `s09a_`, `s09b_` | `@OneToMany + @JoinColumn`? → Không bảng nối; thêm 2 order = 2 INSERT + **2 UPDATE bù** `customer_id`. |
| G1-S10a/b ➕🔬 | [G1V3NNJoinColumnNullableFalseTest](G1V3NNJoinColumnNullableFalseTest.java) `s10a_`, `s10b_` | `nullable = false` có bỏ được UPDATE? → **Không.** Persist order trước khi add: INSERT bind NULL → DB từ chối. Đúng thứ tự: INSERT có FK nhưng **vẫn còn 2 UPDATE thừa**. |
| G1-S11 ⭐ | `G1V3JoinColumnTest` `s11_` | Gỡ order khỏi collection V3? → `UPDATE … customer_id = NULL`, dòng order **vẫn còn** (mồ côi). |
| G1-S12 ⭐ | [G1V4BidirectionalTest](G1V4BidirectionalTest.java) `s12_` | Chỉ add vào collection `mappedBy`? → INSERT với **`customer_id = NULL`** (inverse side bị bỏ qua khi ghi). |
| G1-S13 ⭐ | `G1V4BidirectionalTest` `s13_` | Chỉ `order.setCustomer`? → DB đúng, nhưng `customer.getOrders()` trong cùng persistence context **vẫn thiếu** tới khi `clear()` và tải lại. |
| G1-S14a/b ⭐ | `G1V4BidirectionalTest` `s14a_`, `s14b_` | Helper `addOrder()/removeOrder()`? → add: 1 INSERT với FK đúng, bộ nhớ khớp. remove: `UPDATE … customer_id = NULL`, order còn. **Pattern khuyến nghị.** |
| G1-S15a/b/c ⭐ | [G1V5NoMappedByTest](G1V5NoMappedByTest.java) `s15a_`…`s15c_` | Quên `mappedBy`? → Có **cả** `customer_id` lẫn bảng nối; set cả hai phía ghi 2 lần; chỉ `setCustomer` thì FK = An nhưng `customer.getOrders()` rỗng (**mâu thuẫn**). |
| G1-S16a/b ➕🔬 | [G1V6OwnerParentTest](G1V6OwnerParentTest.java) `s16a_`, `s16b_` | Owning side ở phía "một"? → `order.setCustomer` **không** ghi FK; `customer.getOrders().add` ghi FK bằng INSERT + UPDATE. |
| G1-S17 ➕ | `G1V4BidirectionalTest` `s17_` | `[BOOT-FAIL]` `mappedBy` sai tên? → `AnnotationException` nêu tên property không tồn tại ("khachHang"). |
| G1-S18a/b ➕ | `G1V1ManyToOneTest` `s18a_`, `s18b_` | Một chiều có query ngược được không? → Được: JPQL `where o.customer = :c` hoặc derived query `findByCustomerId`, 1 SELECT, không cần collection. |

## Kết quả khác giả thuyết ban đầu (🔬)

Chi tiết trong [docs/RESULTS.md](../../../../../../../docs/RESULTS.md). Ba điểm cần người học biết:

1. **`nullable = false` KHÔNG chỉ là chỉ dẫn cho DDL** (G1-S04b, S04e): Hibernate 7.4.15 đọc nó để coi quan hệ là bắt buộc, nên chặn order thiếu customer trước khi gửi SQL và dùng `inner join` khi tải, y như `optional = false`.
2. **`nullable = false` ở V3 không bỏ được UPDATE thừa** (G1-S10) và còn thêm bẫy phụ thuộc thứ tự gọi `persist`/`add`.
3. **Bảng nối không tự nằm trong schema của entity** (G1-S07): không khai `@JoinTable(schema = …)` thì rơi vào schema `public`. V2 và V5 vì vậy khai `@JoinTable` tường minh (hành vi không đổi).

## DDL trích ra (từ `build/ddl/create.sql`)

**V1** — FK ở bảng con, nullable; FK `customer_id` có tên đặt tay, hai FK địa chỉ có tên Hibernate tự sinh:

```sql
create table g1_v1_m2o_uni.purchase_order (
    billing_address_id bigint, customer_id bigint, id bigint not null,
    shipping_address_id bigint, order_no varchar(255) not null, primary key (id));
alter table g1_v1_m2o_uni.purchase_order add constraint fk_order_customer
    foreign key (customer_id) references g1_v1_m2o_uni.customer;
```

**V2** — không có `customer_id` ở `purchase_order`; có bảng nối với `unique` trên cột order:

```sql
create table g1_v2_o2m_uni_jointable.customer_orders (
    customer_id bigint not null,
    orders_id bigint not null unique);
```

**V3** — `@JoinColumn`: FK về lại bảng con, không bảng nối:

```sql
create table g1_v3_o2m_uni_joincolumn.purchase_order (
    customer_id bigint, id bigint not null, order_no varchar(255) not null, primary key (id));
```

**V5** — quên `mappedBy`: có **cả hai** `purchase_order.customer_id` và bảng nối `customer_orders` (cùng một ý nghĩa, lưu hai nơi).

## Chạy

```bash
./gradlew test --tests '*G1*'            # cả nhóm G1
./gradlew test --tests '*G1V4*'          # một biến thể
```
