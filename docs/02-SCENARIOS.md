# Danh mục tình huống — Hibernate Mapping Lab

> Mỗi tình huống ứng với **1 test method**. Quy ước định dạng xem [01-DESIGN.md §7](01-DESIGN.md).
>
> **Ký hiệu**
> - ⭐ **Cốt lõi**: làm trước, phải hiểu.  ➕ **Mở rộng**: làm sau.
> - 🔬 **Cần xác minh**: kết quả phụ thuộc version Hibernate. Test ghi lại hành vi thực tế vào `RESULTS.md`.
> - `[BOOT-FAIL]`: mapping sai làm Hibernate không khởi động. Test bằng `MappingBootstrap` (bootstrap riêng).
> - `PC` = persistence context (cache cấp 1). `T/M/D/R` = TRANSIENT / MANAGED / DETACHED / REMOVED.
>
> **Domain chung (thương mại điện tử):** `Customer` 1—N `PurchaseOrder` 1—N `OrderItem` N—1 `Product`. Ngoài ra có `Payment`, `LoyaltyCard`, `CustomerProfile`, `Tag`, `Warehouse`, `StockItem`, `Shipment`, `ShipmentLine`, `ItemDiscount`, `Category`.

## Mục lục nhóm (theo thứ tự học)

| Nhóm | Chủ đề | Annotation / thuộc tính chính | Số TH |
|---|---|---|---|
| [G0](#g0) | Vòng đời entity & Persistence Context | `persist/merge/remove/detach/getReference`, Spring Data `save()` | 14 |
| [G1](#g1) | Owning side: `@ManyToOne` / `@OneToMany` | `mappedBy`, `@JoinColumn(name, nullable, insertable, updatable, foreignKey)`, `optional` | 18 |
| [G2](#g2) | `CascadeType` | `PERSIST, MERGE, REMOVE, DETACH, REFRESH, ALL`, `@OnDelete` | 13 |
| [G3](#g3) | `orphanRemoval` | `orphanRemoval` vs `CascadeType.REMOVE`, thứ tự flush | 11 |
| [G4](#g4) | Cập nhật bảng con qua bảng cha **mà không đổi bảng cha** | `@Version`, `@OptimisticLock`, `@DynamicUpdate`, `LockModeType` | 13 |
| [G5](#g5) | `FetchType` & N+1 | `LAZY/EAGER`, `JOIN FETCH`, `@EntityGraph`, `@BatchSize`, `@Fetch` | 16 |
| [G6](#g6) | `@OneToOne` & `@MapsId` | FK bên A vs bên B, shared PK, `orphanRemoval` 1-1 | 13 |
| [G7](#g7) | `@ManyToMany` & `@JoinTable` | `joinColumns/inverseJoinColumns`, `Set` vs `List`, `@OrderColumn`, join entity | 14 |
| [G8](#g8) | `@JoinColumns` (FK nhiều cột) & derived identity | `@EmbeddedId`, `@IdClass`, `@MapsId("field")`, `referencedColumnName` | 7 |
| [G9](#g9) | Tình huống tổng hợp, phức tạp | self-reference, DTO → merge, `equals/hashCode`, bulk JPQL, service layer | 11 |

Bảng **annotation → tình huống** nằm ở [cuối file](#coverage).

---

<a id="g0"></a>
## G0 — Vòng đời entity & Persistence Context (nền tảng)

**Vì sao học trước:** mọi nhóm sau đều mô tả hành vi bằng trạng thái T/M/D/R và thời điểm flush.

**Model** (schema `g0_lifecycle`): `Customer` (id SEQUENCE), `PurchaseOrder` (`@ManyToOne(fetch = LAZY) customer`), `IdentityCustomer` (id IDENTITY), `SkuProduct` (id gán tay kiểu `String sku`, không `@Version`), `VersionedSkuProduct` (id gán tay + `@Version Long`).

**G0-S01 ⭐ `persist()` không INSERT ngay (SEQUENCE)**
- Làm: `new Customer()` → `persist` → kiểm tra SQL và DB → `flush` → kiểm tra lại → commit → đọc bằng connection khác.
- Kỳ vọng: sau `persist`, id đã có (từ sequence) nhưng **0 INSERT**. Sau `flush` có 1 INSERT; connection cùng transaction thấy 1 dòng, **connection khác thấy 0** (chưa commit). Sau commit thì connection khác thấy 1.
- Lifecycle: `T → M` (persist) `→ M` (flush không đổi trạng thái) `→ D` (commit, PC đóng).
- Bài học: **flush ≠ commit**. Flush đẩy SQL xuống DB, commit mới làm transaction khác nhìn thấy.

**G0-S02 ⭐ `IDENTITY` INSERT ngay khi `persist()`**
- Làm: như S01 với `IdentityCustomer`.
- Kỳ vọng: INSERT chạy **ngay ở dòng `persist`**, trước mọi flush.
- Bài học: IDENTITY buộc Hibernate INSERT sớm để lấy id, nên mất khả năng hoãn hay gom SQL. So sánh trực tiếp với S01.

**G0-S03 ⭐ Dirty checking: không gọi `save()` vẫn UPDATE**
- Làm: tx: `find` → `setName("X")` → kết thúc tx (không gọi save/merge).
- Kỳ vọng: 1 SELECT + 1 UPDATE. DB có name = X.
- Lifecycle: `M` (dirty) → flush lúc commit → `D`.

**G0-S04 ⭐ Sửa entity DETACHED thì không có gì xảy ra**
- Làm: tx1 `find` → tx1 kết thúc → `setName("X")` → tx2 mở rồi đóng (không merge). Biến thể trong cùng một tx: `em.detach(c)` hoặc `em.clear()` rồi mới sửa.
- Kỳ vọng: 0 UPDATE, DB giữ giá trị cũ.
- Lifecycle: `M → D` (hết tx / detach / clear). Thay đổi trên D không ai theo dõi.

**G0-S05 ⭐ `merge()` trả về bản MANAGED KHÁC**
- Làm: có `detached` (đã sửa) → tx2: `managed = em.merge(detached)` → sửa tiếp `detached.setName("Y")` → commit.
- Kỳ vọng: SELECT + UPDATE theo giá trị lúc merge. `managed != detached`. Lần sửa thành "Y" **bị mất**.
- Lifecycle: `detached` vẫn **D** sau merge, còn `managed` là **M**.
- Bài học: `repository.save(entity)` cũng trả về bản đã merge, nên **phải dùng giá trị trả về**.

**G0-S06 ⭐ `remove()`: REMOVED và DELETE lúc flush. Remove entity DETACHED**
- Làm: (a) tx: `find` → `remove` → assert trạng thái → `flush`. (b) `em.remove(detached)`. (c) `repository.delete(detached)`.
- Kỳ vọng: (a) sau `remove` thì `em.contains` = false, trạng thái **R**, chưa DELETE; DELETE chạy lúc flush. (b) `IllegalArgumentException` (không remove được entity detached). (c) Spring Data tự `find`/`merge` trước nên xoá được (🔬 ghi lại SQL thực tế).

**G0-S07 ➕ `persist()` lại một entity REMOVED trong cùng tx** 🔬
- Kỳ vọng (giả thuyết theo spec JPA): entity quay lại **M**, DELETE bị huỷ, không có SQL nào.

**G0-S08 ⭐ `getReference()` vs `find()`**
- Làm: (a) `getReference(Customer, id)` → `Hibernate.isInitialized` → `getId()` → `getName()`. (b) `order.setCustomer(em.getReference(...))` rồi persist order. (c) `getReference` với id không tồn tại → truy cập field. (d) Dùng reference sai đó làm FK.
- Kỳ vọng: (a) không có SELECT cho tới `getName()`, và `getId()` không kích hoạt proxy (mặc định Hibernate). (b) **INSERT order mà không SELECT customer**. (c) `EntityNotFoundException`. (d) Vi phạm FK lúc flush.
- Bài học: muốn gán FK thì dùng `getReferenceById` thay cho `findById`, tiết kiệm 1 SELECT.

**G0-S09 ⭐ Cache cấp 1 & identity**
- Làm: trong 1 tx gọi `find(id)` 2 lần → so sánh `==`. Sau đó `em.clear()` rồi `find` lại.
- Kỳ vọng: lần đầu 1 SELECT, lần 2 không có SELECT và cùng instance. Sau clear có SELECT mới và instance khác.

**G0-S10 ➕ FlushMode AUTO trước khi query** 🔬
- Làm: sửa `name` của entity M → chạy JPQL `select c from Customer c where c.name = :new`. Biến thể: chạy native query.
- Kỳ vọng: UPDATE được flush **trước** SELECT, nên query tìm thấy. Với native query thì ghi lại hành vi thực tế.

**G0-S11 ⭐ Spring Data `save()` & `isNew()`**
- Làm: (a) `save(new Customer())` (id sinh tự động). (b) `save(new SkuProduct("SKU-1"))` (id gán tay). (c) `save(new VersionedSkuProduct("SKU-2"))` (`version == null`).
- Kỳ vọng: (a) persist, 1 INSERT. (b) **merge: SELECT rồi mới INSERT** (thừa 1 SELECT). (c) persist, chỉ INSERT.
- Bài học: entity id gán tay nên dùng `@Version` hoặc implement `Persistable`. Liên quan tới `@MapsId` (G6, G8).

**G0-S12 ➕ `@Transactional(readOnly = true)` bỏ qua dirty checking**
- Làm: service readOnly tải customer rồi sửa name.
- Kỳ vọng: 0 UPDATE.

**G0-S13 ➕ Rollback & merge entity "ma"** 🔬
- Làm: persist trong tx rồi ném exception → rollback. Object vẫn giữ id. Sau đó `merge(object)` ở tx mới.
- Kỳ vọng: sau rollback, object trông như D (có id) nhưng DB không có dòng nào. Kết quả merge thì ghi lại: INSERT mới hay `OptimisticLockException`, vì hành vi này đã đổi ở Hibernate 6.6+.

**G0-S14 ➕ Self-invocation làm mất `@Transactional`**
- Làm: `CustomerService.outer()` (không tx) gọi `this.inner()` (`@Transactional`), trong đó `find` rồi sửa.
- Kỳ vọng: không có transaction, nên không UPDATE (hoặc `LazyInitializationException` nếu chạm vào quan hệ lazy). Gọi qua một bean khác thì chạy đúng.

---

<a id="g1"></a>
## G1 — Owning side: `@ManyToOne` / `@OneToMany` / `mappedBy` / `@JoinColumn`

**Câu hỏi trung tâm:** FK nằm ở đâu? Phía nào **ghi** FK? Bỏ `mappedBy` hoặc `@JoinColumn` thì chuyện gì xảy ra?
**Quy tắc nhóm:** G1 **không dùng cascade**. Entity nào cũng persist tường minh, để cascade không làm nhiễu kết quả.
**Model:** `Customer` ↔ `PurchaseOrder` (biến thể nằm ở quan hệ này). `OrderItem` → `PurchaseOrder` và `OrderItem` → `Product` giữ cố định là `@ManyToOne`, nên mỗi biến thể có đủ 4 bảng.

### Biến thể

| Mã | Schema | `Customer` (bảng cha) | `PurchaseOrder` (bảng con) | Ghi chú |
|---|---|---|---|---|
| V1 | `g1_v1_m2o_uni` | — (không có collection) | `@ManyToOne customer` | **Baseline**, khuyến nghị |
| V1-NN | `g1_v1nn_m2o_nullable_false` | — | `@ManyToOne @JoinColumn(nullable = false)` | Chỉ ảnh hưởng DDL |
| V1-OPT | `g1_v1opt_m2o_optional_false` | — | `@ManyToOne(optional = false)` | DDL + Hibernate tự kiểm tra |
| V2 | `g1_v2_o2m_uni_jointable` | `@OneToMany List<PurchaseOrder> orders` | — | Không `@JoinColumn` → **bảng nối** |
| V3 | `g1_v3_o2m_uni_joincolumn` | `@OneToMany @JoinColumn(name = "customer_id")` | — | FK ở bảng con, ghi từ bảng cha |
| V3-NN | `g1_v3nn_o2m_uni_joincolumn_nn` | như V3 + `nullable = false` | — | 🔬 có bỏ được UPDATE thừa không |
| V4 | `g1_v4_bidi_mappedby` | `@OneToMany(mappedBy = "customer")` | `@ManyToOne customer` | Hai chiều đúng chuẩn |
| V5 | `g1_v5_bidi_no_mappedby` | `@OneToMany` (**quên** mappedBy) | `@ManyToOne customer` | 2 owning side |
| V6 | `g1_v6_bidi_owner_parent` | `@OneToMany @JoinColumn(name = "customer_id")` | `@ManyToOne @JoinColumn(name = "customer_id", insertable = false, updatable = false)` | Owning side nằm ở phía "một" (hiếm) |

### Đặt annotation ở bảng A (cha) hay bảng B (con)?

| | Đặt ở **bảng con** (`@ManyToOne` trên PurchaseOrder) | Đặt ở **bảng cha** (`@OneToMany` trên Customer) |
|---|---|---|
| FK ở đâu | `purchase_order.customer_id` | Không `@JoinColumn`: **bảng nối** `customer_orders`. Có `@JoinColumn`: `purchase_order.customer_id` |
| SQL khi thêm 1 order | 1 INSERT | Bảng nối: 2 INSERT. JoinColumn: INSERT + UPDATE |
| Điều hướng | order → customer | customer → orders |
| Khuyến nghị | ✅ mặc định nên dùng | ⚠️ dùng khi thật sự cần, ưu tiên hai chiều V4 |

### Tình huống

**G1-S01 ⭐ DDL của `@ManyToOne` (V1)**
- Kỳ vọng: `purchase_order.customer_id` nullable, có FK constraint tới `customer(id)`. Tên cột mặc định = `<tên field>_<tên cột PK>`. Thêm field `buyer` thì cột là `buyer_id`. Tên constraint mặc định là chuỗi Hibernate sinh ra, còn khi có `@JoinColumn(foreignKey = @ForeignKey(name = "fk_order_customer"))` thì đúng tên đã đặt.
- Assert: đọc `SchemaInspector` và in DDL.

**G1-S02 ⭐ Persist con trỏ tới cha đã có (V1)**
- Làm: tx1 persist customer. tx2 persist order với `setCustomer(getReference)` cùng 2 item trỏ tới product.
- Kỳ vọng: không SELECT customer. Có 1 INSERT order và 2 INSERT item, FK đúng.
- Lifecycle: customer reference là proxy chưa khởi tạo. order và item `T → M`.

**G1-S03 ⭐ Con trỏ tới cha TRANSIENT, không cascade (V1)**
- Làm: `new Customer()` (không persist) gán vào order mới → persist order → commit.
- Kỳ vọng: lỗi lúc flush, `TransientPropertyValueException` ("references an unsaved transient instance") được Spring bọc lại (🔬 ghi loại exception). DB trống, vì cả tx rollback.
- Lifecycle: customer **T**, order **M** → flush thất bại.

**G1-S04 ⭐ Không có gì vs `nullable = false` vs `optional = false` (V1 / V1-NN / V1-OPT)**
- Làm: persist order với `customer = null` ở cả 3 biến thể. Sau đó tải order bằng `find` (EAGER mặc định) để so sánh SQL join.
- Kỳ vọng:
  - V1: INSERT thành công, `customer_id = NULL`. Câu SELECT dùng `left join`.
  - V1-NN: DDL `NOT NULL`. INSERT bị **DB** từ chối (constraint violation → `DataIntegrityViolationException`).
  - V1-OPT: DDL `NOT NULL`. **Hibernate** chặn trước khi gửi SQL (`PropertyValueException`, 🔬 ghi lại). Câu SELECT dùng `inner join`.
- Bài học: `nullable` chỉ ảnh hưởng DDL. `optional` vừa ảnh hưởng DDL vừa ảnh hưởng hành vi runtime.

**G1-S05 ➕ Hai FK cùng trỏ tới một bảng (V1 + `Address`)**
- Model: `PurchaseOrder.billingAddress` và `shippingAddress`, cả hai `@ManyToOne Address`.
- Kỳ vọng: 2 cột `billing_address_id`, `shipping_address_id`, mỗi cột một FK. Tên cột được suy ra từ tên field.

**G1-S06 ⭐ Map cột FK 2 lần: `Long customerId` + `@ManyToOne` (V1)**
- Model: `@Column(name = "customer_id") Long customerId` (ghi được) và `@ManyToOne @JoinColumn(name = "customer_id", insertable = false, updatable = false) Customer customer` (chỉ đọc).
- Làm: (a) chỉ set `customerId`. (b) chỉ set `customer`.
- Kỳ vọng: (a) FK được ghi. (b) FK = NULL, vì phía object đã bị tắt quyền ghi.
- `[BOOT-FAIL]` khi bỏ `insertable/updatable = false`: lỗi "column duplicated in mapping" (🔬 ghi message Hibernate 7).

**G1-S07 ⭐ `@OneToMany` một chiều KHÔNG có `@JoinColumn` → bảng nối (V2)**
- Kỳ vọng DDL: bảng `customer_orders` (tên cột mặc định 🔬 ghi lại) có **unique trên cột order**, vì đây là quan hệ một-nhiều (🔬). `purchase_order` **không có** `customer_id`.
- Làm: persist customer và 2 order rồi add vào `customer.getOrders()`.
- Kỳ vọng SQL: 1 + 2 INSERT entity + **2 INSERT bảng nối** = 5.
- Bài học: đây là lý do không nên dùng `@OneToMany` một chiều trơn.

**G1-S08 ⭐ Xoá 1 phần tử khỏi `List` của V2** 🔬
- Làm: customer có 3 order → `orders.remove(1)`.
- Kỳ vọng (giả thuyết, "bag semantics"): **DELETE toàn bộ dòng bảng nối của customer rồi INSERT lại 2 dòng**. Dòng order vẫn còn trong `purchase_order`, vì không có orphanRemoval. So sánh `Set` ở G7-S05.

**G1-S09 ⭐ `@OneToMany` + `@JoinColumn` một chiều: INSERT rồi UPDATE (V3)** 🔬
- Kỳ vọng DDL: `purchase_order.customer_id` nullable. Không có bảng nối.
- Kỳ vọng SQL khi thêm 2 order: 2 INSERT (`customer_id = NULL`) + **2 UPDATE set customer_id**.
- Bài học: phía "một" không nằm trong bảng con, nên phải UPDATE bù.

**G1-S10 ➕ V3-NN: `nullable = false` có ghi FK ngay trong INSERT không** 🔬
- Ghi lại: số UPDATE còn lại là 0 hay vẫn 2.

**G1-S11 ⭐ V3: bỏ order khỏi collection**
- Kỳ vọng: `UPDATE purchase_order set customer_id = NULL`. Dòng order **vẫn còn** (thành "mồ côi" trong DB).

**G1-S12 ⭐ V4: chỉ set phía inverse (mappedBy)**
- Làm: `customer.getOrders().add(order)` + `persist(order)`, **không** gọi `order.setCustomer(c)`.
- Kỳ vọng: INSERT order với **`customer_id = NULL`**.
- Lifecycle: cả hai đều **M**. Hibernate bỏ qua phía mappedBy khi ghi.

**G1-S13 ⭐ V4: chỉ set phía owning**
- Làm: customer đã M và collection đã được khởi tạo → `order.setCustomer(c)`, persist, flush → kiểm tra `c.getOrders()` → `em.clear()` → tải lại.
- Kỳ vọng: DB đúng (FK có giá trị), nhưng **trong cùng PC `c.getOrders()` không chứa order mới** (bộ nhớ lệch). Tải lại sau clear thì mới thấy.
- Bài học: phải đồng bộ cả hai phía bằng helper method.

**G1-S14 ⭐ V4: helper `addOrder()/removeOrder()` đồng bộ 2 phía**
- Kỳ vọng: add → INSERT với FK đúng, và bộ nhớ khớp. remove (không orphanRemoval) → `UPDATE … customer_id = NULL`.
- Đây là pattern khuyến nghị. Các nhóm sau đều dùng nó.

**G1-S15 ⭐ V5: quên `mappedBy` → 2 owning side**
- Kỳ vọng DDL: **vừa có** `purchase_order.customer_id` **vừa có** bảng nối `customer_orders`.
- Làm: (a) set cả hai phía → INSERT order (có FK) + INSERT bảng nối, tức dữ liệu ghi 2 lần. (b) chỉ set `order.setCustomer(A)` → FK = A, bảng nối trống. Khi đó query theo `customer.orders` **ra rỗng**, query theo `order.customer` **ra A**, hai nguồn sự thật mâu thuẫn nhau.

**G1-S16 ➕ V6: owning side ở phía "một"**
- Làm: (a) chỉ `order.setCustomer(c)` → FK **không** được ghi (insertable = false). (b) chỉ `customer.getOrders().add(order)` → FK được ghi (🔬 INSERT + UPDATE hay INSERT trực tiếp).
- Bài học: đây là V4 lật ngược, owning side là phía có quyền ghi.

**G1-S17 ➕ `[BOOT-FAIL]` `mappedBy` trỏ sai tên field**
- Kỳ vọng: `AnnotationException` lúc khởi động, message nêu tên property không tồn tại.

**G1-S18 ➕ V1: một chiều vẫn query ngược được**
- Làm: lấy order của customer bằng JPQL `where o.customer = :c` hoặc derived query `findByCustomerId`.
- Bài học: nhiều khi `@ManyToOne` một chiều cộng query là đủ, không cần collection.

---

<a id="g2"></a>
## G2 — `CascadeType`

**Câu hỏi trung tâm:** thao tác nào (`persist/merge/remove/detach/refresh`) được **lan** từ cha sang con? Đặt cascade ở phía nào thì nguy hiểm?
**Model:** `Customer` 1—N `PurchaseOrder` 1—N `OrderItem` N—1 `Product`. Tất cả quan hệ là hai chiều chuẩn V4, có helper `addItem/removeItem`. Biến thể nằm ở **`PurchaseOrder.items`**, trừ C6 và C7 (biến thể trên `OrderItem.product`). **Không orphanRemoval** trong nhóm này.

| Mã | Schema | Thay đổi |
|---|---|---|
| C0 | `g2_c0_none` | `items`: không cascade (**baseline**) |
| C1 | `g2_c1_persist` | `cascade = PERSIST` |
| C2 | `g2_c2_merge` | `cascade = MERGE` |
| C3 | `g2_c3_remove` | `cascade = REMOVE` |
| C4 | `g2_c4_all` | `cascade = ALL` |
| C4-DEEP | `g2_c4deep_all_chain` | như C4 + `Customer.orders` cũng `cascade = ALL` (chỉ dùng cho S11) |
| C5 | `g2_c5_persist_merge` | `cascade = {PERSIST, MERGE}` (thiếu DETACH/REFRESH/REMOVE) |
| C6 | `g2_c6_remove_on_m2o` | `OrderItem.product @ManyToOne(cascade = REMOVE)`, **anti-pattern** |
| C7 | `g2_c7_persist_on_m2o` | `OrderItem.product @ManyToOne(cascade = PERSIST)` |
| C8 | `g2_c8_ondelete` | `OrderItem.order` có `@OnDelete(action = CASCADE)`, `items` không cascade |

### Đặt cascade ở bảng A (cha) hay B (con)?

| | Trên **collection của cha** (`@OneToMany items`) | Trên **`@ManyToOne` của con** (`OrderItem.product`) |
|---|---|---|
| Ý nghĩa | Vòng đời con phụ thuộc cha (aggregate) | Con điều khiển vòng đời của thứ nó trỏ tới (thường sai) |
| PERSIST | ✅ hợp lý | ⚠️ dễ gặp "detached entity passed to persist" (S09) |
| REMOVE | ✅ hợp lý (với quan hệ sở hữu) | ❌ xoá 1 item kéo theo xoá product mà item khác vẫn đang dùng (S08) |

### Tình huống

**G2-S01 ⭐ C0: persist cha có con mới, không cascade** 🔬
- Làm: `order.addItem(new OrderItem())` ×2 → `persist(order)` → commit.
- Kỳ vọng (một trong hai, test ghi lại): (a) chỉ INSERT order, item **bị bỏ qua im lặng** vì collection là phía inverse; (b) `TransientObjectException`.
- Lifecycle: order **M**, item vẫn **T**.

**G2-S02 ⭐ C1: PERSIST lan xuống con**
- Kỳ vọng: INSERT order rồi 2 INSERT item. Item `T → M` cùng lúc với cha.

**G2-S03 ⭐ C1: thêm con vào cha đang MANAGED (persist-on-flush)**
- Làm: tx: `find(order)` → `addItem(new)` → commit (không gọi persist cho item).
- Kỳ vọng: INSERT item lúc flush, vì cascade PERSIST cũng chạy **lúc flush**.

**G2-S04 ⭐ C0 vs C2: merge đồ thị DETACHED**
- Làm: tx1 tải order + items (JOIN FETCH) → D → sửa `item.quantity`, thêm 1 item mới → tx2 `merge(order)`.
- Kỳ vọng:
  - C0: thay đổi item **bị mất**, item mới không được INSERT. Order không có UPDATE (không đổi gì).
  - C2: UPDATE item cũ + INSERT item mới.
  - C1 (chỉ PERSIST) + merge: 🔬 ghi lại.

**G2-S05 ⭐ C0 vs C3: xoá cha có con**
- Kỳ vọng:
  - C0: **vi phạm FK** (`DataIntegrityViolationException`), không xoá được gì.
  - C3: SELECT items, **DELETE từng item** (N câu), rồi DELETE order.
- Lifecycle (C3): order và items `M → R` → flush DELETE.

**G2-S06 ⭐ C4 vs C5: `detach(order)`**
- Làm: tx: tải order + items → `em.detach(order)` → sửa `item.quantity` → commit.
- Kỳ vọng:
  - C4 (ALL có DETACH): item cũng **D**, nên 0 UPDATE.
  - C5: item **vẫn M**, nên **vẫn UPDATE** dù cha đã detach. Đây là chỗ dễ bất ngờ.

**G2-S07 ➕ C4 vs C5: `refresh(order)`**
- Làm: sửa `item.quantity` trong bộ nhớ → `em.refresh(order)` → commit.
- Kỳ vọng: C4 thì item bị nạp lại từ DB, thay đổi mất. C5 thì item giữ thay đổi và bị flush thành UPDATE.

**G2-S08 ⭐ C6: `cascade = REMOVE` trên `@ManyToOne` (anti-pattern)**
- Làm: 2 item cùng trỏ tới product P → xoá item 1.
- Kỳ vọng: Hibernate cố DELETE cả product P, dẫn tới **vi phạm FK** vì item 2 còn trỏ tới P. Nếu P chỉ có 1 item trỏ tới thì P **bị xoá âm thầm** (mất dữ liệu master).

**G2-S09 ⭐ C7: PERSIST trên `@ManyToOne` + product DETACHED**
- Làm: tx1 tải product (→ D). tx2 `persist(new OrderItem(product))` hoặc `itemRepository.save(newItem)`.
- Kỳ vọng: `PersistentObjectException: detached entity passed to persist` (Spring bọc lại).
- Cách sửa (mỗi cách một test nhỏ): `getReferenceById(productId)`, hoặc `find` trong cùng tx, hoặc bỏ cascade.

**G2-S10 ⭐ C3 vs C8: cascade JPA vs `@OnDelete` (ON DELETE CASCADE ở DB)**
- Kỳ vọng DDL C8: FK `order_item.order_id … ON DELETE CASCADE`.
- Kỳ vọng SQL khi xoá order có 3 item: C3 có SELECT items + 3 DELETE + 1 DELETE. C8 có **1 DELETE** (🔬 Hibernate có còn SELECT items không). DB của cả hai đều trống item.
- ➕ Nếu item đã được tải vào PC trước khi xoá thì C8 có còn giữ object item ở trạng thái M không (🔬).

**G2-S11 ➕ C4-DEEP: cascade 3 tầng**
- Làm: `persist(customer)`, trong đó customer có 2 order, mỗi order có 2 item.
- Kỳ vọng: 1 + 2 + 4 INSERT theo thứ tự cha trước con.
- Biến thể: tắt cascade ở một tầng thì lỗi hoặc thiếu dữ liệu đúng tại tầng đó.

**G2-S12 ➕ Bulk JPQL `delete from PurchaseOrder` bỏ qua cascade**
- Kỳ vọng: C3 vẫn **vi phạm FK**, vì bulk không đi qua cascade JPA. C8 thành công nhờ DB cascade.

**G2-S13 ➕ Spring Data: `deleteById` vs `delete(entity)` vs `deleteAllInBatch()`** (trên C3)
- Kỳ vọng: hai cách đầu tải entity rồi cascade. `deleteAllInBatch` là bulk, nên không cascade và vi phạm FK.

---

<a id="g3"></a>
## G3 — `orphanRemoval`

**Câu hỏi trung tâm:** con bị **tách khỏi cha** (không phải cha bị xoá) thì dòng của con trong DB ra sao?
**Model:** `PurchaseOrder` 1—N `OrderItem` 1—N `ItemDiscount` (3 tầng, hai chiều chuẩn). `OrderItem` có **unique `(order_id, product_code)`**, giống nhau ở mọi biến thể. FK `order_item.order_id` nullable để thấy được dòng mồ côi.

| Mã | Schema | `PurchaseOrder.items` | `OrderItem.discounts` |
|---|---|---|---|
| O0 | `g3_o0_cascade_all` | `cascade = ALL`, `orphanRemoval = false` (**baseline**) | `cascade = ALL` |
| O1 | `g3_o1_orphan` | `cascade = ALL`, `orphanRemoval = true` | `cascade = ALL, orphanRemoval = true` |
| O2 | `g3_o2_orphan_no_cascade` | **không cascade**, `orphanRemoval = true` | như O1 |

> `orphanRemoval` chỉ có trên `@OneToMany` và `@OneToOne`. `@ManyToOne` và `@ManyToMany` **không có** thuộc tính này, nên không có chuyện "đặt bên B". Với 1-1 xem G6-S09/S10.

| | `CascadeType.REMOVE` | `orphanRemoval = true` |
|---|---|---|
| Xoá cha | Xoá con | Xoá con |
| Bỏ con khỏi collection | Không làm gì (hoặc set FK null nếu gọi helper) | **DELETE con** |
| Gán collection mới | Bình thường | **Exception** (S07) |

### Tình huống

**G3-S01 ⭐ O0: chỉ `items.remove(item)`, không set `item.order = null`**
- Kỳ vọng: **0 SQL**. Tải lại thì item vẫn thuộc order (collection là phía inverse).

**G3-S02 ⭐ O0: helper `removeItem()` (remove + `setOrder(null)`)**
- Kỳ vọng: `UPDATE order_item set order_id = NULL`. Dòng vẫn còn, thành mồ côi trong DB. Nếu FK là NOT NULL thì vi phạm constraint (ghi chú, không cần biến thể riêng).

**G3-S03 ⭐ O1: helper `removeItem()`**
- Kỳ vọng: DELETE các `item_discount` của item, rồi DELETE `order_item`.
- Lifecycle: item `M → R` (do orphan) → flush.

**G3-S04 ⭐ O1: chỉ `items.remove(item)` (quên set null)** 🔬
- Kỳ vọng (giả thuyết): vẫn **DELETE**, vì orphanRemoval dựa trên việc so collection với snapshot. Đây là điểm khác O0-S01.

**G3-S05 ⭐ O0 vs O1: xoá cha**
- Kỳ vọng: cả hai đều xoá item và discount, vì REMOVE lan theo cả hai cơ chế. Kết luận này lấp ô "Xoá cha" của bảng so sánh.

**G3-S06 ⭐ O2: `orphanRemoval` không có cascade** 🔬
- Làm: (a) xoá order. (b) persist order mới có item mới.
- Kỳ vọng (giả thuyết theo spec): (a) item **vẫn bị xoá**, vì orphanRemoval kéo theo remove khi cha bị remove. (b) item **không** được persist (không có PERSIST), test ghi lại im lặng hay exception.

**G3-S07 ⭐ O1: gán collection mới**
- Làm: `order.setItems(new ArrayList<>(…))` → flush.
- Kỳ vọng: `HibernateException: A collection with cascade="all-delete-orphan" was no longer referenced by the owning entity instance`.
- Cách sửa (test riêng): `items.clear(); items.addAll(…)`.

**G3-S08 ⭐ O1: `items.clear()`**
- Kỳ vọng: DELETE toàn bộ discount và item của order (N câu). Order còn nguyên.

**G3-S09 ⭐ O1: chuyển item từ đơn A sang đơn B trong cùng tx** 🔬
- Làm: `a.removeItem(item); b.addItem(item);` → commit.
- Giả thuyết (test ghi lại cái thực tế xảy ra): (a) item **bị xoá** (mất dữ liệu); (b) exception kiểu "deleted instance passed to merge / would be re-saved by cascade"; (c) `UPDATE order_id = B`.
- Cách an toàn (test riêng): đổi FK trực tiếp mà không đi qua `removeItem` của A, hoặc tạo item mới.

**G3-S10 ⭐ Thay item bằng item mới có cùng `product_code` (unique) — thứ tự flush** 🔬
- Làm: (a) O1: `removeItem(old); addItem(new cùng code)`. (b) O0: `em.remove(old); persist(new cùng code)`.
- Giả thuyết: (a) **thành công**, vì orphan removal chạy **trước** INSERT. (b) **vi phạm unique**, vì INSERT chạy trước DELETE.
- Cách sửa (b): gọi `em.flush()` sau `remove`, hoặc cập nhật item cũ thay vì thay mới.
- Bài học: thứ tự ActionQueue (01-DESIGN §8).

**G3-S11 ➕ O1: đồ thị DETACHED bỏ bớt item rồi `merge`** 🔬
- Kỳ vọng (giả thuyết): merge so collection và item bị bỏ ra sẽ bị DELETE. Ghi lại SQL thực tế.

---

<a id="g4"></a>
## G4 — Cập nhật bảng con thông qua bảng cha **mà không thay đổi bảng cha**

**Câu hỏi trung tâm:** sửa, thêm, xoá con bằng cách đi qua `order.getItems()`, thì dòng `purchase_order` có bị đụng tới không (UPDATE, `version`, `updated_at`)?
**Model:** `PurchaseOrder` (`@Version Long version`, `status`, `@UpdateTimestamp updatedAt`) 1—N `OrderItem` (`quantity`, `unitPrice`) N—1 `Product`, cộng `Customer`.
**Cách assert "cha không đổi":** chụp snapshot dòng cha bằng JDBC (mọi cột) trước và sau thao tác rồi so bằng nhau, đồng thời `sql.updates("purchase_order") == 0`.

| Mã | Schema | Thay đổi so với U0 |
|---|---|---|
| U0 | `g4_u0_baseline` | hai chiều mappedBy, `items: cascade = ALL, orphanRemoval = true` (**baseline**) |
| U1 | `g4_u1_no_merge` | `items: cascade = PERSIST` (không MERGE) |
| U2 | `g4_u2_owning_collection` | `items` là `@OneToMany` **một chiều + `@JoinTable`**, nên collection thuộc phía owning |
| U3 | `g4_u3_optimistic_excluded` | như U2 + `@OptimisticLock(excluded = true)` trên `items` |
| U4 | `g4_u4_dynamic_update` | `OrderItem` có `@DynamicUpdate` |
| U5 | `g4_u5_child_version` | `OrderItem` có `@Version` riêng |

### Tình huống

**G4-S01 ⭐ U0: sửa field của con qua cha đang MANAGED**
- Làm: tx: `find(order)` → `order.getItems().get(0).setQuantity(5)` → commit.
- Kỳ vọng: chỉ có `UPDATE order_item`. Snapshot cha giống hệt, version giữ nguyên.
- Lifecycle: order **M, không dirty**. item **M, dirty**.

**G4-S02 ⭐ U0: đồ thị DETACHED → `orderRepository.save(order)`**
- Làm: tx1 tải order + items → D → sửa quantity → tx2 `save(order)` (merge, cascade MERGE).
- Kỳ vọng: SELECT order + items, rồi **chỉ UPDATE `order_item`**. Cha không có UPDATE, version giữ nguyên.

**G4-S03 ⭐ U1: như S02 nhưng không cascade MERGE**
- Kỳ vọng: thay đổi của con **bị mất**, cha không đổi, 0 UPDATE.
- Bài học: "save cha thì con tự lưu" chỉ đúng khi có MERGE.

**G4-S04 ⭐ U0: thêm con mới qua cha (collection mappedBy)** 🔬
- Kỳ vọng: INSERT `order_item`. Test ghi lại **version của cha có tăng không** (giả thuyết: không, vì collection inverse không thuộc state của cha).

**G4-S05 ⭐ U0: xoá con qua cha (orphanRemoval)** 🔬
- Kỳ vọng: DELETE `order_item`. Ghi lại version cha như S04.

**G4-S06 ⭐ U2: thêm con vào collection owning (bảng nối)** 🔬
- Kỳ vọng (giả thuyết): INSERT item + INSERT bảng nối + **`UPDATE purchase_order set version = version + 1`**, vì collection owning thuộc state của cha.

**G4-S07 ⭐ U3: như S06 + `@OptimisticLock(excluded = true)`**
- Kỳ vọng: không còn UPDATE version của cha.

**G4-S08 ➕ U0: `@UpdateTimestamp` / `@PreUpdate` của cha không chạy khi chỉ con đổi**
- Kỳ vọng: `updated_at` giữ nguyên, callback `@PreUpdate` của cha không được gọi (assert bằng một counter).

**G4-S09 ➕ U0: muốn version cha TĂNG khi con đổi (aggregate root)**
- Làm: `em.find(order, OPTIMISTIC_FORCE_INCREMENT)` (hoặc `lock`) → sửa item.
- Kỳ vọng: UPDATE item + `UPDATE purchase_order set version`. Ngược với mục tiêu "không đổi cha", nhưng cho thấy cách chủ động làm điều đó.

**G4-S10 ⭐ U0 vs U4: `@DynamicUpdate`**
- Kỳ vọng: U0 có `UPDATE order_item set` **tất cả cột**. U4 chỉ `set quantity = ?`. So sánh nguyên văn câu SQL.

**G4-S11 ➕ U5: hai bản DETACHED của cùng một item (cập nhật đồng thời)**
- Làm: tx1, tx2 tải 2 bản → tx3 merge bản A (qua cha) → tx4 merge bản B.
- Kỳ vọng: tx4 nhận `ObjectOptimisticLockingFailureException`. Cha vẫn không đổi.

**G4-S12 ➕ U0: sửa con qua cha vs qua `OrderItemRepository`**
- Kỳ vọng: đi qua cha thì SELECT order + **toàn bộ** items. Đi trực tiếp thì chỉ SELECT 1 item. Kết quả DB như nhau. Bài học về chi phí.

**G4-S13 ➕ U0: bulk JPQL `update OrderItem set quantity = …`**
- Kỳ vọng: DB đổi, nhưng **object item đang M trong PC vẫn giữ giá trị cũ** (stale). `@Version` của item không tăng. Có `@Modifying(clearAutomatically = true)` thì PC được làm mới.

---

<a id="g5"></a>
## G5 — `FetchType` & N+1

**Câu hỏi trung tâm:** khi nào Hibernate tải quan hệ, bằng bao nhiêu câu SQL, và chuyện gì xảy ra khi truy cập quan hệ ngoài transaction?
**Model:** `Customer` 1—N `PurchaseOrder` 1—N `OrderItem` N—1 `Product`, và `Customer` 1—N `Payment`. Dữ liệu chuẩn: 5 customer, mỗi customer 2 order × 2 item, 3 payment.

| Mã | Schema | Thay đổi |
|---|---|---|
| F0 | `g5_f0_defaults` | **không ghi `fetch`**, dùng mặc định JPA (`@ManyToOne/@OneToOne` = EAGER, `@OneToMany/@ManyToMany` = LAZY) |
| F1 | `g5_f1_all_lazy` | mọi quan hệ `LAZY` tường minh (**khuyến nghị**). Collection là `List` |
| F1S | `g5_f1s_all_lazy_set` | như F1 nhưng collection là `Set` |
| F2 | `g5_f2_eager_collection` | `Customer.orders` = `EAGER` (anti-pattern) |
| F3 | `g5_f3_batch_size` | như F1 + `@BatchSize(size = 10)` trên collection và entity |
| F4 | `g5_f4_subselect` | như F1 + `@Fetch(FetchMode.SUBSELECT)` trên `Customer.orders` |

### Tình huống

**G5-S01 ⭐ F0: `find(order)` với `@ManyToOne` EAGER mặc định**
- Kỳ vọng: 1 SELECT có `left join customer`.

**G5-S02 ⭐ F0: JPQL `select o from PurchaseOrder o`, EAGER sinh N+1**
- Kỳ vọng: 1 SELECT order + **1 SELECT cho mỗi customer khác nhau** (5). EAGER không biến thành JOIN trong JPQL.

**G5-S03 ⭐ F1: proxy LAZY**
- Làm: `find(order)` → `Hibernate.isInitialized(order.getCustomer())` → `getId()` → `getName()`.
- Kỳ vọng: không join. Proxy chưa khởi tạo, `getId()` không có SELECT (mặc định Hibernate), `getName()` có 1 SELECT.

**G5-S04 ⭐ F1: `LazyInitializationException`**
- Làm: tx tải order → hết tx (**D**) → `order.getItems().size()`, và `order.getCustomer().getName()`.
- Kỳ vọng: cả hai ném `LazyInitializationException`.
- Lifecycle: order **D**, collection và proxy chưa khởi tạo, session đã đóng.
- Ghi chú: `spring.jpa.open-in-view` chỉ ảnh hưởng web request nên không liên quan ở đây. `hibernate.enable_lazy_load_no_trans` là anti-pattern, chỉ nhắc tới chứ không bật.

**G5-S05 ⭐ F1: N+1 trên collection**
- Làm: `findAll()` customers → lặp `c.getOrders().size()`.
- Kỳ vọng: 1 + 5 SELECT.

**G5-S06 ⭐ F1: sửa bằng `JOIN FETCH`** 🔬
- Kỳ vọng: 1 SELECT. Danh sách trả về có **5** customer, không phải 10, vì Hibernate 6+ tự loại trùng nên không cần `DISTINCT` (🔬 xác minh).

**G5-S07 ⭐ F1: sửa bằng `@EntityGraph(attributePaths = "orders")` trên repository**
- Kỳ vọng: 1 SELECT, kết quả như S06.

**G5-S08 ⭐ F3: `@BatchSize(10)`**
- Kỳ vọng: 1 + ⌈5/10⌉ = **2 SELECT**, câu thứ hai dùng `where customer_id in (…)` (🔬 Hibernate 6+ có thể dùng `= any(?)` với mảng trên Postgres).

**G5-S09 ➕ F4: `SUBSELECT`**
- Kỳ vọng: 2 SELECT. Câu thứ hai lặp lại câu query gốc dưới dạng subquery.

**G5-S10 ⭐ F1: `MultipleBagFetchException`**
- Làm: `select c from Customer c join fetch c.orders join fetch c.payments` (2 `List`).
- Kỳ vọng: exception ngay lúc tạo query (`MultipleBagFetchException` được bọc).
- Cách sửa (test riêng): 2 query nối tiếp trong cùng tx (query 2 fetch payments, PC tự ghép), hoặc dùng `Set` (xem S11).

**G5-S11 ➕ F1S: fetch 2 `Set` thì chạy được nhưng sinh tích Descartes**
- Kỳ vọng: chạy được. Câu SQL trả về 2 × 3 = 6 dòng cho mỗi customer (chứng minh bằng cách chạy lại SQL đó qua JDBC và đếm dòng). Bài học: chạy được không có nghĩa là rẻ.

**G5-S12 ⭐ F1: phân trang + `JOIN FETCH` collection** 🔬
- Làm: `join fetch c.orders` + `setMaxResults(2)`.
- Kỳ vọng: cảnh báo `HHH90003004` (phân trang trong bộ nhớ). SQL **không có LIMIT** và tải toàn bộ.
- Cách sửa (test riêng): query 1 lấy page id, query 2 `join fetch … where c.id in :ids`.
- Ghi chú: `hibernate.query.fail_on_pagination_over_collection_fetch = true` biến cảnh báo thành exception. Đây là setting toàn cục, nếu muốn test thì dùng bootstrap riêng (cách C).

**G5-S13 ⭐ F2: `@OneToMany` EAGER**
- Kỳ vọng: `find(customer)` luôn tải orders. `findAll()` sinh N+1 tự động. Ghi lại xem `@EntityGraph` có "tắt" được EAGER không (🔬, giả thuyết: không).

**G5-S14 ➕ F1: `Hibernate.initialize()` trong tx vs ngoài tx**
- Kỳ vọng: trong tx thì collection được khởi tạo và dùng được sau khi D. Ngoài tx thì exception.

**G5-S15 ➕ F1: DTO projection (record / interface projection)**
- Kỳ vọng: 1 SELECT chỉ lấy đúng các cột cần. **Không có entity nào trong PC** (DTO không có vòng đời).

**G5-S16 ➕ F1: N+1 qua `@ManyToOne` LAZY khi lặp**
- Làm: lặp items → `item.getProduct().getName()`.
- Kỳ vọng: 1 + (số product khác nhau) SELECT. Sửa bằng `join fetch i.product`.

---

<a id="g6"></a>
## G6 — `@OneToOne` & `@MapsId`

**Câu hỏi trung tâm:** FK đặt ở bảng nào? Phía inverse có LAZY được không? `@MapsId` khác FK thường thế nào?
**Model:** `Customer`, `LoyaltyCard` (1-1, FK thường), `CustomerProfile` (1-1, chia sẻ PK).

| Mã | Schema | Mapping |
|---|---|---|
| V1 | `g6_v1_fk_on_customer` | `Customer.loyaltyCard @OneToOne @JoinColumn` → FK `customer.loyalty_card_id` (một chiều) |
| V2 | `g6_v2_fk_on_card` | `LoyaltyCard.customer @OneToOne @JoinColumn` → FK `loyalty_card.customer_id` (một chiều) |
| V3 | `g6_v3_bidi_owner_card` | V2 + `Customer.loyaltyCard @OneToOne(mappedBy = "customer", fetch = LAZY)` |
| V4 | `g6_v4_bidi_owner_customer` | V1 + `LoyaltyCard.customer @OneToOne(mappedBy = "loyaltyCard", fetch = LAZY)` |
| V5 | `g6_v5_mapsid_uni` | `CustomerProfile`: `@Id Long id` + `@OneToOne(fetch = LAZY) @MapsId @JoinColumn(name = "customer_id") Customer customer` |
| V6 | `g6_v6_mapsid_bidi` | V5 + `Customer.profile @OneToOne(mappedBy = "customer", fetch = LAZY)` |
| V7 | `g6_v7_orphan` | V3 + `Customer.loyaltyCard (…, cascade = ALL, orphanRemoval = true)` |

### FK ở bảng A (Customer) hay bảng B (LoyaltyCard)?

| | FK ở **Customer** (V1/V4) | FK ở **LoyaltyCard** (V2/V3) |
|---|---|---|
| Customer chưa có thẻ | `loyalty_card_id = NULL` | Không có dòng card |
| Ràng buộc "1 customer chỉ 1 thẻ" | unique trên `customer.loyalty_card_id` (🔬 Hibernate có tự thêm không) | unique trên `loyalty_card.customer_id` |
| Tải Customer | Phía owning, LAZY dùng được | Phía inverse, **LAZY bị bỏ qua** (S03) |
| Quy tắc chọn | Thực thể "đến sau" / tuỳ chọn nên giữ FK. Thẻ phụ thuộc customer nên **V2 tự nhiên hơn** | |

### Tình huống

**G6-S01 ⭐ V1 vs V2: DDL** 🔬
- Kỳ vọng: FK nằm đúng bảng. Ghi lại Hibernate 7 có tự thêm **unique constraint** cho cột FK của `@OneToOne` không.

**G6-S02 ⭐ V2: hai thẻ cho cùng một customer**
- Kỳ vọng: vi phạm unique nếu có unique (theo kết quả S01). Nếu không có unique thì DB chấp nhận, nghĩa là quan hệ 1-1 chỉ tồn tại trong Java.

**G6-S03 ⭐ V3: LAZY trên phía inverse bị bỏ qua** 🔬
- Làm: `find(customer)`. JPQL lấy 5 customer.
- Kỳ vọng (giả thuyết): `find` có **thêm 1 SELECT loyalty_card** dù khai báo LAZY, vì Hibernate phải biết là null hay proxy và không có FK ở customer để dựa vào. JPQL sinh **N+1**.
- Bài học: phía inverse của `@OneToOne` thực tế luôn EAGER nếu không có bytecode enhancement.

**G6-S04 ⭐ V4: lật ngược FK**
- Kỳ vọng: `find(customer)` LAZY đúng (proxy). `find(loyaltyCard)` lúc này thành phía inverse và có thêm SELECT.

**G6-S05 ⭐ V3: chỉ set phía inverse**
- Làm: `customer.setLoyaltyCard(card)`, không gọi `card.setCustomer(c)`.
- Kỳ vọng: `loyalty_card.customer_id = NULL`.

**G6-S06 ⭐ V5: `@MapsId`, PK của profile chính là FK**
- Kỳ vọng DDL: `customer_profile.customer_id` là **PK và FK**, không có sequence riêng cho profile.
- Làm: persist profile (customer đã có) → `find(CustomerProfile.class, customerId)`.
- Kỳ vọng: `profile.getId() == customer.getId()`. `find` theo id của customer, không join. Spring Data `save(profile)` đi theo persist hay merge thì ghi lại (🔬, `id == null` trước khi persist).
- Lifecycle: profile `T → M`, id được **suy ra** từ customer lúc persist.

**G6-S07 ⭐ V5: profile thứ hai cho cùng một customer** 🔬
- Kỳ vọng: trong cùng PC thì `EntityExistsException`/`NonUniqueObjectException`, khác PC thì vi phạm PK ở DB. Ghi lại cả hai.

**G6-S08 ⭐ V5 vs V6: dùng `@MapsId` để tránh vấn đề của S03** 🔬
- Kỳ vọng: V5 (không map ngược) → `find(customer)` chỉ 1 SELECT, muốn lấy profile thì `find(CustomerProfile, customerId)`. V6 (map ngược mappedBy) có **còn thêm SELECT** không thì ghi lại.
- Bài học: ưu tiên `@MapsId` một chiều rồi find theo id.

**G6-S09 ⭐ V7 vs V3: `customer.setLoyaltyCard(null)`**
- Kỳ vọng: V7 thì DELETE card (orphanRemoval). V3 thì **không có gì xảy ra**, vì đó là phía inverse nên card vẫn trỏ tới customer.

**G6-S10 ➕ V7: thay thẻ cũ bằng thẻ mới (unique `customer_id`)** 🔬
- Kỳ vọng: ghi lại thứ tự DELETE thẻ cũ và INSERT thẻ mới. Có vi phạm unique không (so với G3-S10)?

**G6-S11 ➕ V1: owning side set null**
- Kỳ vọng: `UPDATE customer set loyalty_card_id = NULL`. Dòng card **vẫn còn**, mồ côi.

**G6-S12 ➕ `optional = false` trên `@OneToOne`** 🔬
- Kỳ vọng: DDL `NOT NULL` trên phía owning, `inner join`. Ghi lại xem đặt `optional = false` ở phía inverse có giúp LAZY như các bản Hibernate cũ không (giả thuyết: không).

**G6-S13 ➕ `[BOOT-FAIL]` `@MapsId` kết hợp với `@GeneratedValue` trên cùng id** 🔬
- Ghi lại: lỗi lúc khởi động, hay chạy được nhưng `@GeneratedValue` bị bỏ qua.

---

<a id="g7"></a>
## G7 — `@ManyToMany` & `@JoinTable`

**Câu hỏi trung tâm:** phía nào là owner của bảng nối? `Set` và `List` khác nhau thế nào? Khi nào phải chuyển sang entity trung gian?
**Model:** `Product` N—N `Tag`. `Product` — `StockItem` — `Warehouse` (join entity có thêm cột).

| Mã | Schema | Mapping |
|---|---|---|
| M1 | `g7_m1_owner_product` | `Product.tags @ManyToMany @JoinTable(name = "product_tag", joinColumns = product_id, inverseJoinColumns = tag_id) Set<Tag>`. `Tag.products (mappedBy = "tags")` (**baseline**) |
| M2 | `g7_m2_defaults` | như M1 nhưng **không** `@JoinTable` |
| M3 | `g7_m3_owner_tag` | owner là `Tag` (`@JoinTable` nằm trên `Tag.products`), `Product.tags (mappedBy)` |
| M4 | `g7_m4_no_mappedby` | cả hai phía đều không mappedBy |
| M5 | `g7_m5_list_bag` | như M1 nhưng `List<Tag>` |
| M6 | `g7_m6_order_column` | như M5 + `@OrderColumn(name = "position")` |
| M7 | `g7_m7_cascade_all` | như M1 + `cascade = ALL` (**anti-pattern**) |
| M8 | `g7_m8_join_entity` | `StockItem` có `@EmbeddedId StockItemId(productId, warehouseId)` + `@ManyToOne @MapsId("productId") product` + `@ManyToOne @MapsId("warehouseId") warehouse` + `quantity`. `Product.stockItems @OneToMany(mappedBy = "product", cascade = ALL, orphanRemoval = true)` |
| M9 | `g7_m9_join_entity_surrogate` | `StockItem` có id SEQUENCE riêng + `unique(product_id, warehouse_id)` |

### Owner là Product (A) hay Tag (B)?

| | Owner = **Product** (M1) | Owner = **Tag** (M3) |
|---|---|---|
| Ghi bảng nối khi | `product.getTags().add(tag)` | `tag.getProducts().add(product)` |
| Add ở phía còn lại | Bị bỏ qua | Bị bỏ qua |
| Xoá entity owner | Hibernate tự xoá dòng nối | Hibernate tự xoá dòng nối |
| Xoá entity inverse | **Vi phạm FK** nếu còn liên kết (S07) | **Vi phạm FK** nếu còn liên kết |
| Chọn owner | Phía thường được thao tác, thường là phía "aggregate" (Product) | |

### Tình huống

**G7-S01 ⭐ M1 vs M2: tên bảng/cột nối tường minh vs mặc định** 🔬
- Kỳ vọng: M1 đúng `product_tag(product_id, tag_id)`. M2 thì ghi lại tên mặc định theo naming strategy của Spring Boot. Ghi lại join table của `Set` có **PK `(product_id, tag_id)`** không.

**G7-S02 ⭐ M1: add ở owner vs add ở inverse**
- Kỳ vọng: `product.getTags().add(t)` sinh 1 INSERT `product_tag`. `tag.getProducts().add(p)` sinh **0 SQL**.

**G7-S03 ⭐ M3: lật owner**
- Kỳ vọng: ngược với S02. Đây là ví dụ trực tiếp cho câu hỏi "đặt bên A hay bên B".

**G7-S04 ⭐ M4: quên mappedBy → 2 bảng nối**
- Kỳ vọng DDL: `product_tags` **và** `tag_products`. Set cả hai phía thì mỗi bảng 1 dòng, set một phía thì hai bảng mâu thuẫn nhau.

**G7-S05 ⭐ M1 (`Set`) vs M5 (`List`): bỏ 1 trong 3 tag** 🔬
- Kỳ vọng: Set có **1 DELETE**. List có **DELETE toàn bộ 3 dòng rồi INSERT 2 dòng** (bag recreate).

**G7-S06 ➕ M6: `@OrderColumn`** 🔬
- Kỳ vọng DDL: có cột `position`. Bỏ phần tử ở giữa thì DELETE dòng cuối và UPDATE lại vị trí. Ghi lại SQL.

**G7-S07 ⭐ M1: xoá owner vs xoá inverse**
- Kỳ vọng: xoá Product thì DELETE `product_tag` của nó, rồi DELETE product. Xoá Tag còn liên kết thì **vi phạm FK**.
- Cách sửa (test riêng): helper gỡ tag khỏi mọi product trước, hoặc `@PreRemove` trên Tag.

**G7-S08 ⭐ M7: `cascade = ALL` trên `@ManyToMany`**
- Làm: P1 và P2 cùng có tag T → xoá P1.
- Kỳ vọng: Hibernate cố xoá T, dẫn tới **vi phạm FK** vì P2 còn dùng T (nếu T không ai dùng thì **mất T âm thầm**). Bài học: không cascade REMOVE trên N-N.

**G7-S09 ⭐ M1 vs M5: add trùng tag** 🔬
- Kỳ vọng: Set chỉ giữ 1 dòng. List giữ **2 dòng trùng** nếu bảng nối không có PK, hoặc vi phạm PK. Ghi lại.

**G7-S10 ⭐ M8: join entity có thêm cột**
- Làm: `product.addStock(warehouse, 10)` → commit → `product.getStockItems().get(0).setQuantity(20)` → commit → `removeStock(warehouse)`.
- Kỳ vọng: INSERT `stock_item(product_id, warehouse_id, quantity)` với PK suy ra từ 2 `@MapsId`. Sau đó **chỉ UPDATE `stock_item`**, product không đổi (nối với G4). Cuối cùng DELETE nhờ orphanRemoval.

**G7-S11 ⭐ M8: `StockItemId` (`@Embeddable`)**
- Kỳ vọng: `find(StockItem.class, new StockItemId(p, w))` chạy được. Javadoc giải thích vì sao id cần `Serializable` và `equals/hashCode` theo giá trị. Test chứng minh: hai `StockItemId` cùng giá trị phải `equals`.

**G7-S12 ➕ M8 vs M9: PK ghép vs surrogate id**
- So sánh: DDL, câu `find`, việc tham chiếu `StockItem` từ bảng khác (FK 1 cột hay 2 cột, nối sang G8).

**G7-S13 ➕ M1: N+1 khi tải product + tags**
- Kỳ vọng: giống G5-S05, sửa bằng `join fetch p.tags`.

**G7-S14 ➕ Khi nào bỏ `@ManyToMany`**
- Ghi chú trong README nhóm, không cần test riêng: cần cột phụ (quantity, createdAt), cần thao tác hoặc query trực tiếp trên quan hệ, hay cần audit, thì dùng M8.

---

<a id="g8"></a>
## G8 — `@JoinColumns` (FK nhiều cột) & derived identity

**Câu hỏi trung tâm:** khi bảng cha có PK ghép thì FK ở bảng con khai báo thế nào? `@MapsId("field")` hoạt động ra sao với `@EmbeddedId`?
**Model:** `Warehouse` (`@EmbeddedId WarehouseId(countryCode, code)`) 1—N `Shipment` 1—N `ShipmentLine` (`@EmbeddedId ShipmentLineId(shipmentId, lineNo)`) 1—N `ShipmentLineNote`.

| Mã | Schema | Mapping |
|---|---|---|
| J1 | `g8_j1_explicit` | `Shipment.warehouse @ManyToOne @JoinColumns({@JoinColumn(name = "wh_country", referencedColumnName = "country_code"), @JoinColumn(name = "wh_code", referencedColumnName = "code")})` (**baseline**) |
| J2 | `g8_j2_defaults` | `@ManyToOne` trơn, không `@JoinColumns` |
| J3 | `g8_j3_idclass` | `Warehouse` dùng `@IdClass` thay `@EmbeddedId` |
| J4 | `g8_j4_natural_key_ref` | `OrderItem.product @ManyToOne @JoinColumn(name = "product_sku", referencedColumnName = "sku")`, trỏ tới cột unique **không phải PK** |

### Tình huống

**G8-S01 ⭐ J1: DDL FK hai cột**
- Kỳ vọng: `shipment(wh_country, wh_code)` + **một FK constraint ghép** tới `warehouse(country_code, code)`. Persist và query theo warehouse.

**G8-S02 ⭐ J2: tên mặc định** 🔬
- Ghi lại tên 2 cột FK mà Hibernate sinh ra.

**G8-S03 ➕ `[BOOT-FAIL]` J1 thiếu `referencedColumnName` ở một cột** 🔬
- Ghi lại: lỗi khởi động, hay Hibernate tự suy ra theo thứ tự.

**G8-S04 ⭐ Derived identity: `ShipmentLine` với `@MapsId("shipmentId")`**
- Kỳ vọng DDL: PK `(shipment_id, line_no)`, trong đó `shipment_id` vừa là PK vừa là FK.
- Làm: persist line với `new ShipmentLineId(null, 1)` cộng `setShipment(s)`.
- Kỳ vọng: Hibernate tự điền `shipmentId`. Với `lineRepository.save(line)`: id khác null (`lineNo` đã có) nên `isNew = false`, thành **merge với SELECT thừa** (🔬). Cách sửa: `Persistable` (test riêng, nối với G0-S11).

**G8-S05 ➕ `ShipmentLineNote` trỏ tới `ShipmentLine` qua `@JoinColumns(shipment_id, line_no)`**
- Kỳ vọng: chuỗi khoá ghép 3 tầng, FK 2 cột. Xoá shipment khi có cascade thì thứ tự DELETE là note → line → shipment.

**G8-S06 ➕ J4: FK trỏ tới natural key (không phải PK)** 🔬
- Kỳ vọng DDL: FK tới `product(sku)`, trong đó `sku` phải unique. Ghi lại: với `LAZY` thì Hibernate có còn SELECT product ngay không (giả thuyết: có, vì không tạo được proxy theo non-PK).

**G8-S07 ➕ Đổi một phần PK ghép của entity đã persist** 🔬
- Làm: sửa `warehouse.getId().setCode("X")` trên entity M.
- Kỳ vọng: exception kiểu "identifier altered" lúc flush. Bài học: PK là bất biến.

---

<a id="g9"></a>
## G9 — Tình huống tổng hợp, phức tạp (có service layer)

**Mục tiêu:** kết hợp nhiều annotation trong bối cảnh nghiệp vụ, để luyện kỹ năng gỡ rối.

**G9-S01 ⭐ Cây danh mục tự tham chiếu (`Category`)**
- Model: `@ManyToOne(fetch = LAZY) parent` + `@OneToMany(mappedBy = "parent", cascade = ALL, orphanRemoval = true) children`. 1 bảng nhưng 3 tầng dữ liệu. Thêm `Product.category` để có ≥ 3 bảng.
- Làm: (a) persist cây 3 tầng. (b) chuyển một nhánh sang cha khác. (c) xoá node giữa. (d) duyệt cây.
- Kỳ vọng: (a) INSERT theo thứ tự cha trước con. (b) **chỉ 1 UPDATE `parent_id`** của node được chuyển. (c) DELETE cả nhánh, con trước cha. Nếu product còn trỏ tới node bị xoá thì vi phạm FK. (d) N+1 theo tầng. Sửa bằng cách tải toàn bộ category trong 1 query để PC tự ghép cây.

**G9-S02 ⭐ "Cập nhật đơn hàng từ request" (DTO → entity) qua service**
- Input DTO: các item có id (sửa), không có id (thêm mới), và item cũ không còn trong DTO (cần xoá).
- Cách A (khuyến nghị): tải order đang M → áp diff (sửa field, `addItem`, `removeItem`).
- Cách B (bẫy): `new PurchaseOrder()` gán id từ DTO → `save()` (merge). Các cột không có trong DTO (`createdAt`, `status`, …) **bị ghi đè thành NULL**.
- Kỳ vọng: so SQL và dữ liệu giữa A và B. Comment lifecycle ở từng bước.

**G9-S03 ⭐ `equals/hashCode` theo id sinh tự động + `HashSet`**
- Model: hai biến thể `OrderItem`: (a) `hashCode` theo id, (b) `hashCode` hằng số / theo business key.
- Làm: add item mới (id null) vào `Set` → persist (id được gán, hashCode đổi) → `set.remove(item)` / orphanRemoval.
- Kỳ vọng: (a) `remove` trả về false, item **không bị xoá** (orphanRemoval không chạy). (b) chạy đúng.
- ➕ Proxy: `equals` dùng `getClass()` sẽ sai với proxy Hibernate, cần `Hibernate.getClass()` hoặc `instanceof`.

**G9-S04 ➕ `toString()` đệ quy trong quan hệ hai chiều**
- Kỳ vọng: `StackOverflowError` (đúng bẫy `@Data`/`@ToString` của Lombok). Cách sửa là không in quan hệ.

**G9-S05 ⭐ Bulk `@Modifying` JPQL và PC bị lệch**
- Làm: tải product (M) → bulk `update Product set price = price * 1.1` → đọc `product.getPrice()`. Sau đó dùng `clearAutomatically = true`. Biến thể: bulk `delete OrderItem` bỏ qua orphanRemoval và cascade.
- Kỳ vọng: không có clear thì giá trong bộ nhớ cũ, DB mới. Có clear thì đồng bộ, nhưng entity cũ thành **D**.

**G9-S06 ⭐ So sánh chiến lược xoá `Customer` → `Order` → `Item`**
- So: (a) cascade REMOVE theo chuỗi, (b) `@OnDelete` theo chuỗi, (c) bulk delete từ dưới lên trong service, (d) không có chiến lược nào thì vi phạm FK.
- Kỳ vọng: bảng so sánh số SQL, thứ tự SQL và trạng thái PC sau khi xoá. Ghi vào README của nhóm.

**G9-S07 ⭐ Ranh giới transaction trong service**
- Làm: (a) service không tx gọi `findById` rồi chạm quan hệ lazy. (b) self-invocation (nối với G0-S14). (c) service `@Transactional` trả về entity rồi caller chạm quan hệ lazy. (d) service trả về DTO.
- Kỳ vọng: (a) và (c) ném `LazyInitializationException`, (b) không có tx, (d) an toàn.

**G9-S08 ➕ Hai người cùng sửa một đơn (optimistic lock)**
- Làm: 2 bản D của cùng order (`@Version`) → lưu bản 1 → lưu bản 2.
- Kỳ vọng: `ObjectOptimisticLockingFailureException` ở lần lưu thứ hai. DB giữ dữ liệu của bản 1.

**G9-S09 ➕ Chuyển item giữa hai đơn + tính lại tổng tiền**
- Kỳ vọng: lần này **cố ý** đổi cả hai cha (`total`, `version`). Đối chiếu với G4: khi nào nên và không nên đụng tới cha. Nối với G3-S09 khi có orphanRemoval.

**G9-S10 ➕ Batch insert: SEQUENCE vs IDENTITY + `hibernate.jdbc.batch_size`**
- Cần `datasource-proxy` để đếm batch. Kỳ vọng: SEQUENCE gom được batch, IDENTITY thì không. (Tuỳ chọn, chỉ làm nếu đã thêm thư viện.)

**G9-S11 ➕ `@SoftDelete` (Hibernate 6.4+) trên `OrderItem`** 🔬
- Ghi lại: `remove` sinh UPDATE cờ deleted. Collection của cha tự lọc item đã xoá. orphanRemoval cũng thành soft delete.

---

<a id="coverage"></a>
## Bảng phủ annotation → tình huống

| Annotation / thuộc tính | Tình huống |
|---|---|
| `@ManyToOne` (mặc định, `optional`, `fetch`, `cascade`) | G1-S01..S06, S18 · G2-S08, S09 · G5-S01..S03, S16 |
| `@OneToMany` (`mappedBy`, một chiều, `fetch`, `cascade`, `orphanRemoval`) | G1-S07..S17 · G2 · G3 · G4 · G5-S05..S13 |
| `@OneToOne` (owner A/B, `mappedBy`, `optional`, `orphanRemoval`) | G6-S01..S12 |
| `@ManyToMany` (owner A/B, không mappedBy, `Set`/`List`, `cascade`) | G7-S01..S09, S13 |
| `@JoinColumn` (`name`, `referencedColumnName`, `nullable`, `insertable`, `updatable`, `foreignKey`) | G1-S01, S04, S06, S09, S16 · G8-S06 |
| `@JoinColumns` | G8-S01..S03, S05 |
| `@JoinTable` (`name`, `joinColumns`, `inverseJoinColumns`) | G7-S01..S04 · G4-S06 · G1-S07 |
| `FetchType.LAZY / EAGER` | G5 (toàn bộ) · G6-S03, S04, S08 |
| `CascadeType.*` | G2 (toàn bộ) · G4-S02, S03 · G7-S08 |
| `orphanRemoval` | G3 (toàn bộ) · G6-S09, S10 · G7-S10 · G9-S01, S03 |
| `@MapsId` | G6-S06..S08, S13 · G7-S10, S11 · G8-S04 |
| `@EmbeddedId` / `@IdClass` | G7-S10..S12 · G8 |
| `@OnDelete` | G2-S10, S12 · G9-S06 |
| `@Version`, `@OptimisticLock`, `LockModeType` | G4-S04..S09, S11 · G9-S08 |
| `@DynamicUpdate` | G4-S10 |
| `@BatchSize`, `@Fetch(SUBSELECT)`, `@EntityGraph`, `JOIN FETCH` | G5-S06..S12 |
| `@OrderColumn` | G7-S06 |
| Vòng đời T/M/D/R, flush, `merge`, `getReference`, `save()` | G0 (toàn bộ), và có mặt trong mọi tình huống |

### Kết quả đối chiếu coverage (Phase 12)

Đã đối chiếu cả 130 mã tình huống trong file này với `src/test`: **127 mã có test xanh**, mọi annotation trong bảng trên đều có ít nhất một test.
Ba mã ➕ không có test riêng, không làm hụt annotation nào:

- **G6-S12** (`optional = false` trên `@OneToOne`): ngoài phạm vi Phase 8; `optional = false` trên `@ManyToOne` đã được test ở G1-S04c/d.
- **G7-S14** (khi nào bỏ `@ManyToMany`): chỉ là ghi chú, nằm trong README nhóm G7.
- **G9-S10** (batch insert SEQUENCE vs IDENTITY): cần `hibernate.jdbc.batch_size` toàn cục (trái quy ước) và thư viện đếm batch; để tuỳ chọn.

Tra cứu theo annotation: [CHEATSHEET.md](CHEATSHEET.md).
