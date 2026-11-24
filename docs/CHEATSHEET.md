# CHEATSHEET — mỗi annotation một bảng

Các dòng dưới đây dựa trên kết quả test chạy trên PostgreSQL thật, Hibernate 7.4.15.Final. Cột cuối của mỗi bảng là mã tình huống `G<N>-S<NN>`:
tìm theo mã trong [README của nhóm](../README.md#lộ-trình-đọc) để thấy test và kết luận đầy đủ, hoặc trong [RESULTS.md](RESULTS.md) nếu là 🔬.

Mỗi bảng có sáu hàng: **Mặc định → Đặt ở phía nào → Ảnh hưởng DDL → Ảnh hưởng SQL/runtime → Bẫy hay gặp → Mã tình huống**.

Mục lục: [`@ManyToOne`](#manytoone) · [`@OneToMany`](#onetomany) · [`mappedBy`](#mappedby) · [`@OneToOne`](#onetoone) · [`@ManyToMany`](#manytomany) ·
[`@JoinColumn`](#joincolumn) · [`@JoinColumns`](#joincolumns) · [`@JoinTable`](#jointable) · [`FetchType`](#fetchtype) · [`CascadeType`](#cascadetype) ·
[`orphanRemoval`](#orphanremoval) · [`@MapsId`](#mapsid) · [`@EmbeddedId` / `@IdClass`](#embeddedid--idclass) · [`@OnDelete`](#ondelete) ·
[`@Version` / `@OptimisticLock` / `LockModeType`](#version--optimisticlock--lockmodetype) · [`@DynamicUpdate`](#dynamicupdate) ·
[`@BatchSize` / `@Fetch` / `@EntityGraph` / `JOIN FETCH`](#batchsize--fetch--entitygraph--join-fetch) · [`@OrderColumn`](#ordercolumn) · [`@SoftDelete`](#softdelete) ·
[`@Embeddable`](#embeddable--attributeoverride) · [`@ElementCollection`](#elementcollection) · [`@Inheritance`](#inheritance) · [`@NaturalId`](#naturalid) · [`AttributeConverter` / `@Enumerated`](#attributeconverter--enumerated) · [`@Formula` / `@SQLRestriction` / `@Filter`](#formula--sqlrestriction--filter) · [`@Transactional`](#transactional-readonly-propagation-rollbackfor) · [Khoá pessimistic](#lockmodetypepessimistic_--skip-locked--deadlock) · [Batch / `StatelessSession`](#batch-insert--allocationsize--statelesssession) · [Cache cấp 2](#cacheable--cache--query-cache) · [Bytecode enhancement](#bytecode-enhancement)

> Mười một bảng cuối (từ `@Embeddable`) thuộc giai đoạn mở rộng G10–G13.

---

## `@ManyToOne`

| Mục | Nội dung |
|---|---|
| **Mặc định** | `fetch = EAGER`, `optional = true`, không cascade. Khai tường minh `LAZY` (F0 vs F1). |
| **Đặt ở phía nào** | Luôn là **owning side**: phía có cột FK (bảng "nhiều"). Là cách map mặc định nên dùng (baseline G1-V1). |
| **Ảnh hưởng DDL** | Cột `<tên field>_id` nằm ở bảng con, **nullable**, kèm FK. Hai FK cùng bảng thì cột đặt tên theo **field** (`billing_address_id`). |
| **Ảnh hưởng SQL/runtime** | `find` EAGER nối `left join` (inner `join` khi `optional = false`). JPQL EAGER thì thêm 1 SELECT cho mỗi giá trị khác nhau (N+1). LAZY cho proxy: `getId()` không SELECT, `getName()` mới SELECT. Gán FK bằng `getReference` thì **0 SELECT**. |
| **Bẫy hay gặp** | (1) Trỏ tới entity TRANSIENT mà không cascade: `TransientPropertyValueException` lúc flush. (2) `@JoinColumn(nullable = false)` **cũng** làm Hibernate chặn trước (`PropertyValueException`, 0 INSERT) và đổi sang inner join: `nullable` không chỉ là DDL. (3) Map cột FK hai lần mà không `insertable/updatable = false`: lỗi khởi động. (4) Field đã `insertable = false` thì `setCustomer()` không ghi gì: FK = NULL. (5) `cascade = REMOVE` trên `@ManyToOne` xoá nhầm dữ liệu dùng chung. |
| **Mã tình huống** | G1-S01..S06, S18 · G0-S08 · G2-S08, S09 · G5-S01..S03, S16 |

## `@OneToMany`

| Mục | Nội dung |
|---|---|
| **Mặc định** | `fetch = LAZY`, không cascade, không `orphanRemoval`. Không `mappedBy` và không `@JoinColumn` thì Hibernate tạo **bảng nối**. |
| **Đặt ở phía nào** | Thường là **inverse side** (`mappedBy`) của một `@ManyToOne`. Một chiều (V2, V3) thì collection là owning side, dùng khi thật sự cần. |
| **Ảnh hưởng DDL** | Có `mappedBy`: **không** cột/bảng nào thêm. Không `@JoinColumn`: bảng nối `<bảng chủ>_<field>` có unique trên cột phần tử. Có `@JoinColumn`: FK nằm ở bảng con, cột nullable. |
| **Ảnh hưởng SQL/runtime** | Bảng nối: thêm 1 order = INSERT order + INSERT bảng nối. `@JoinColumn` một chiều: INSERT rồi **UPDATE bù** `customer_id` (kể cả `nullable = false`). `List` không `@OrderColumn` (bag): đổi collection thì DELETE cả bảng nối của cha rồi INSERT lại. |
| **Bẫy hay gặp** | (1) Bảng nối KHÔNG theo schema của entity: phải khai `@JoinTable(schema = …)`, nếu không rơi vào `public`. (2) Quên `mappedBy` ở quan hệ hai chiều: sinh **cả hai** cột FK và bảng nối, ghi dữ liệu hai lần. (3) Chỉ `customer.getOrders().add(o)` ở phía inverse: INSERT với `customer_id = NULL`. (4) `[BOOT-FAIL]` `mappedBy` sai tên: `AnnotationException`. |
| **Mã tình huống** | G1-S07..S17 · G2 · G3 · G4 · G5-S05..S13 |

## `mappedBy`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Không có: không khai thì phía đó tự là owning side. |
| **Đặt ở phía nào** | Phía **inverse** (phía **không** có cột FK, hoặc phía không ghi bảng nối). Giá trị là **tên field** của owning side, không phải tên cột. |
| **Ảnh hưởng DDL** | Không sinh gì: chính `mappedBy` là thứ ngăn Hibernate sinh thêm cột/bảng nối. |
| **Ảnh hưởng SQL/runtime** | Hibernate **bỏ qua** phía inverse khi ghi DB, nhưng vẫn dùng nó khi đọc. Dirty checking của cha không thấy thay đổi collection inverse nên không UPDATE cha, không tăng `version`. |
| **Bẫy hay gặp** | Hibernate không tự đồng bộ hai phía trong bộ nhớ: dùng helper `addOrder/removeOrder` đặt cả hai phía. `@OneToOne(mappedBy)` LAZY bị bỏ qua (xem `@OneToOne`). |
| **Mã tình huống** | G1-S12..S17 · G4-S01, S04, S05 · G6-S03, S05 · G7-S02..S04 |

## `@OneToOne`

| Mục | Nội dung |
|---|---|
| **Mặc định** | `fetch = EAGER`, `optional = true`. |
| **Đặt ở phía nào** | Owning side là phía có `@JoinColumn`. Phía còn lại `mappedBy` (inverse). Chọn FK nằm ở thực thể "đến sau"/phụ thuộc (thẻ giữ `customer_id`: V2 tự nhiên hơn V1). |
| **Ảnh hưởng DDL** | Hibernate 7 **tự thêm `UNIQUE`** trên cột FK, FK nullable. Với `@MapsId`: cột FK đồng thời là PK. |
| **Ảnh hưởng SQL/runtime** | Phía owning: LAZY đúng (proxy). Phía **inverse: LAZY bị bỏ qua**, `find(customer)` thêm 1 SELECT; JPQL 5 customer = 1 + 5 SELECT. `setX(null)` ở phía inverse không ghi gì. |
| **Bẫy hay gặp** | (1) Tưởng LAZY ở phía `mappedBy` hoạt động. (2) `@OneToOne(mappedBy)` + `@MapsId` vẫn thêm SELECT: ưu tiên `@MapsId` một chiều rồi `find` theo id. (3) `orphanRemoval` ở to-one: DELETE chạy **trước** INSERT (khác collection). |
| **Mã tình huống** | G6-S01..S11 (S12 `optional = false` chưa làm, xem [coverage](#phạm-vi-chưa-làm)) |

## `@ManyToMany`

| Mục | Nội dung |
|---|---|
| **Mặc định** | `fetch = LAZY`, không cascade. Không `@JoinTable` thì tên bảng/cột nối theo **tên field**: `public.product_tags(products_id, tags_id)`. |
| **Đặt ở phía nào** | Owning side = phía **không** có `mappedBy` (nơi đặt `@JoinTable`). Chỉ phía này ghi bảng nối. Nên chọn phía hay bị thay đổi liên kết. |
| **Ảnh hưởng DDL** | Bảng nối: `Set` có PK ghép `(a_id, b_id)`; `List` bag **không PK**; `List` + `@OrderColumn` có PK `(a_id, position)`. |
| **Ảnh hưởng SQL/runtime** | Add ở phía inverse: **0 SQL**. `Set`: bỏ 1 phần tử = 1 DELETE đúng dòng. `List` bag: DELETE cả dòng nối của owner + INSERT lại. Xoá entity owner: Hibernate tự DELETE dòng nối. Xoá entity inverse còn liên kết: vi phạm FK. |
| **Bẫy hay gặp** | (1) Quên `mappedBy`: **hai bảng nối** độc lập, dữ liệu mâu thuẫn. (2) `cascade = ALL/REMOVE`: xoá product kéo xoá tag dùng chung (lỗi FK) hoặc **mất tag im lặng**. (3) `List` cho phép trùng dòng nối. (4) Cần cột phụ (`quantity`, `createdAt`): chuyển sang join entity (M8/M9). |
| **Mã tình huống** | G7-S01..S14 (S14 là ghi chú trong README G7) |

## `@JoinColumn`

| Mục | Nội dung |
|---|---|
| **Mặc định** | `name = <field>_<cột PK đích>`, `nullable = true`, `insertable = updatable = true`, FK tên do Hibernate tự sinh. |
| **Đặt ở phía nào** | Phía **owning** của quan hệ (cột FK nằm trong bảng của entity chứa annotation), trừ `@OneToMany` một chiều thì cột nằm ở bảng **con**. |
| **Ảnh hưởng DDL** | `name` đặt tên cột; `nullable = false` → `NOT NULL`; `foreignKey = @ForeignKey(name = …)` đặt tên constraint; `referencedColumnName` trỏ tới cột khác PK (phải `UNIQUE`). |
| **Ảnh hưởng SQL/runtime** | `nullable = false` còn làm Hibernate **chặn trước khi gửi SQL** và dùng inner join. `insertable/updatable = false` khiến field đó không ghi FK. FK trỏ tới cột không phải PK: **LAZY vô hiệu** (proxy cần id, cột chỉ có sku). |
| **Bẫy hay gặp** | Map cùng cột FK hai lần thì một trong hai phải `insertable = false, updatable = false` (G1-S06c là lỗi khởi động). `@OneToMany @JoinColumn(nullable = false)` không bỏ được UPDATE bù, và nếu persist con trước khi add vào collection thì INSERT bind NULL → DB từ chối. |
| **Mã tình huống** | G1-S01, S04, S06, S09, S10, S16 · G8-S06 |

## `@JoinColumns`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Không có `@JoinColumns`: `@ManyToOne` trơn tới PK ghép tự sinh tên cột `<field>_<cột PK>` (`warehouse_country_code`, `warehouse_code`), vẫn **một** FK ghép. |
| **Đặt ở phía nào** | Cùng phía với `@JoinColumn` (owning side), khi entity đích có PK ghép. |
| **Ảnh hưởng DDL** | **Một** constraint FK hai cột (không phải hai FK rời). `name` đặt tên cột, `referencedColumnName` chọn cột PK đích. |
| **Ảnh hưởng SQL/runtime** | Join và INSERT dùng đủ các cột. Với `@MapsId` một nửa PK của con là FK: Hibernate tự điền lúc `persist`. |
| **Bẫy hay gặp** | Thiếu `referencedColumnName` ở một cột: lỗi khởi động (`[BOOT-FAIL]`), Hibernate **không** tự suy ra thứ tự. Sửa một phần id của entity MANAGED bị bỏ qua im lặng, không lỗi, không UPDATE. |
| **Mã tình huống** | G8-S01..S03, S05, S07 |

## `@JoinTable`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Tên `<bảng owner>_<field owner>`, cột theo **tên field**, và nằm ở schema `public` chứ không theo schema của entity. |
| **Đặt ở phía nào** | Owning side của `@ManyToMany`, hoặc `@OneToMany` một chiều không dùng `@JoinColumn`. |
| **Ảnh hưởng DDL** | Tạo bảng nối kèm 2 FK. `name`, `schema`, `joinColumns` (về phía owner), `inverseJoinColumns` (về phía kia) đặt tên tường minh. |
| **Ảnh hưởng SQL/runtime** | Mỗi thay đổi liên kết = INSERT/DELETE ở bảng nối. Collection owning nằm trong **state của cha**: đổi nó làm cha dirty → `UPDATE` cha và tăng `version`. `@OptimisticLock(excluded = true)` tắt phần này. |
| **Bẫy hay gặp** | Quên `schema` thì V2 và V5 của G1 (hai biến thể, hai schema) **đụng nhau** trong `public`. Thứ tự cột INSERT theo owner: `(product_id, tag_id)` vs `(tag_id, product_id)`. |
| **Mã tình huống** | G7-S01..S04 · G4-S06 · G1-S07 |

## `FetchType`

| Mục | Nội dung |
|---|---|
| **Mặc định** | `@ManyToOne`/`@OneToOne` = **EAGER**; `@OneToMany`/`@ManyToMany` = **LAZY**. |
| **Đặt ở phía nào** | Thuộc tính của mọi annotation quan hệ. Khuyến nghị: **mọi quan hệ `LAZY` tường minh** (F1), tải thêm theo từng use case. |
| **Ảnh hưởng DDL** | Không ảnh hưởng. |
| **Ảnh hưởng SQL/runtime** | `find()` biết trước nên nối JOIN. JPQL dịch nguyên văn nên EAGER thành thêm SELECT cho từng phần tử (N+1). LAZY chỉ hoãn: lặp N phần tử và chạm quan hệ vẫn tốn N SELECT. Truy cập ngoài transaction: `LazyInitializationException`. |
| **Bẫy hay gặp** | (1) `@OneToMany` EAGER: `findAll()` tự sinh N+1. (2) Tưởng LAZY cứu được N+1. (3) Phía inverse của `@OneToOne` luôn tải ngay. (4) `@EntityGraph` ngữ nghĩa `fetchgraph`: attribute ngoài graph coi là LAZY, kể cả đang `EAGER`. |
| **Mã tình huống** | G5 (toàn bộ) · G6-S03, S04, S08 · G9-S07 |

## `CascadeType`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Không cascade. Mỗi loại độc lập: `PERSIST` không giúp `MERGE`; chỉ `ALL` mới đủ 5 loại. |
| **Đặt ở phía nào** | Trên **collection của cha** (vòng đời con phụ thuộc cha) là hợp lý. Trên `@ManyToOne` của con thường là sai. |
| **Ảnh hưởng DDL** | Không ảnh hưởng (cascade là thao tác của `EntityManager`, không phải của DB; khác `@OnDelete`). |
| **Ảnh hưởng SQL/runtime** | Chỉ chạy khi đi qua `em.persist/merge/remove/detach/refresh` (và persist-on-flush). Bulk JPQL và `deleteAllInBatch()` **không** đi qua cascade. Không cascade mà persist cha có con mới: con bị bỏ qua **im lặng**. |
| **Bẫy hay gặp** | (1) `merge` đồ thị DETACHED chỉ có `PERSIST`: item cũ không cập nhật, item mới thành **dòng ma rỗng** (`quantity = 0`, FK NULL). (2) `{PERSIST, MERGE}` thiếu `DETACH/REFRESH`: item vẫn MANAGED sau `detach(order)`. (3) `REMOVE` trên `@ManyToOne` hoặc `@ManyToMany` xoá dữ liệu dùng chung. (4) `remove(order)` còn item mà không cascade: vi phạm FK. |
| **Mã tình huống** | G2 (toàn bộ) · G4-S02, S03 · G7-S08 |

## `orphanRemoval`

| Mục | Nội dung |
|---|---|
| **Mặc định** | `false`. |
| **Đặt ở phía nào** | `@OneToMany` (và `@OneToOne`) phía cha, kèm `cascade` nếu muốn con cũng được persist. |
| **Ảnh hưởng DDL** | Không ảnh hưởng. |
| **Ảnh hưởng SQL/runtime** | So collection **hiện tại** với **snapshot** lúc tải: phần tử vắng mặt bị DELETE lúc **flush**. Chỉ cần `items.remove(item)`, không cần set `item.order = null`. Xoá cha thì xoá con kể cả khi không có cascade. Chuyển item sang order khác cùng tx: chỉ `UPDATE order_id`, không mất dữ liệu. |
| **Bẫy hay gặp** | (1) Gán `setItems(new ArrayList<>())`: `HibernateException: A collection with orphan deletion was no longer referenced…` → dùng `clear()` + `addAll()`. (2) Thứ tự flush **INSERT → UPDATE → DELETE**: `removeItem(old)` + `addItem(new cùng unique key)` vi phạm unique → `em.flush()` xen giữa. (3) Không thay được `PERSIST`. (4) Với `@SoftDelete`, orphan chỉ đặt cờ. |
| **Mã tình huống** | G3 (toàn bộ) · G6-S09, S10 · G7-S10 · G9-S01, S03 |

## `@MapsId`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Không có: FK và PK là hai thứ riêng. |
| **Đặt ở phía nào** | Phía owning (con): `@OneToOne @MapsId @JoinColumn` biến cột FK thành PK; `@MapsId("field")` lấy một phần của `@EmbeddedId`. |
| **Ảnh hưởng DDL** | Cột FK đồng thời là PK, **không** có sequence riêng. |
| **Ảnh hưởng SQL/runtime** | `profile.id == customer.id`; lấy con bằng `find(Profile.class, customerId)`, 1 SELECT, không join. Hibernate tự điền id lúc `persist`. Spring Data `save()` với `id == null` đi theo persist. |
| **Bẫy hay gặp** | (1) Map ngược `@OneToOne(mappedBy)` ở cha: `find(customer)` thêm 1 SELECT. (2) Derived identity: id đã khác null một phần nên `save()` coi là entity cũ → merge + SELECT thừa → dùng `Persistable`. (3) Hai profile cho một customer: `EntityExistsException` (cùng PC) hoặc vi phạm PK (khác PC). (4) `@MapsId` + `@GeneratedValue`: không lỗi, `@GeneratedValue` bị bỏ qua. |
| **Mã tình huống** | G6-S06..S08, S13 · G7-S10, S11 · G8-S04 |

## `@EmbeddedId` / `@IdClass`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Không có: PK đơn, `@Id` một cột. |
| **Đặt ở phía nào** | Trên entity có PK ghép. `@EmbeddedId`: một field kiểu `@Embeddable`. `@IdClass`: các field `@Id` nằm thẳng trong entity. |
| **Ảnh hưởng DDL** | **Giống hệt nhau**: PK hai cột, FK trỏ tới là FK ghép. Thứ tự cột PK: cột derived (`@MapsId`) có thể đứng SAU cột thường dù khai báo trước. |
| **Ảnh hưởng SQL/runtime** | `find(W.class, new WarehouseId(…))`. Class id phải `equals/hashCode` đúng giá trị: hai id cùng giá trị trả cùng một instance. `@MapsId("field")` chỉ đi với `@EmbeddedId`. |
| **Bẫy hay gặp** | Id embeddable mutate tại chỗ (`getId().setCode(…)`): không lỗi, **không UPDATE**, bị bỏ qua im lặng (PK không nằm trong dirty checking). `new Entity(...)` có sẵn id không `@Version` báo trạng thái DETACHED. |
| **Mã tình huống** | G7-S10..S12 · G8-S01c, S04, S07 |

## `@OnDelete`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Không có: không `ON DELETE` ở FK. |
| **Đặt ở phía nào** | Phía **con** (`@ManyToOne`) tại FK: `@OnDelete(action = OnDeleteAction.CASCADE)`; cha khai `mappedBy` và không cần cascade. |
| **Ảnh hưởng DDL** | FK có `ON DELETE CASCADE`. |
| **Ảnh hưởng SQL/runtime** | Xoá order 3 item = **1 DELETE** (0 SELECT item, 0 DELETE item): PostgreSQL tự xoá con. Nhanh hơn `CascadeType.REMOVE` (SELECT + N DELETE). |
| **Bẫy hay gặp** | Hibernate **không biết** DB đã xoá con: item đã nằm trong persistence context vẫn **MANAGED** sau flush. Bỏ qua mọi callback/cascade của JPA. |
| **Mã tình huống** | G2-S10, S12 · G9-S06 |

## `@Version` / `@OptimisticLock` / `LockModeType`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Không `@Version`: không phát hiện ghi chồng. Collection **owning** làm cha dirty; collection **inverse** thì không. |
| **Đặt ở phía nào** | `@Version` trên entity (thường là aggregate root). `@OptimisticLock(excluded = true)` trên collection không muốn tính vào version. `LockModeType` truyền vào `find`/query. |
| **Ảnh hưởng DDL** | Thêm cột `version`. |
| **Ảnh hưởng SQL/runtime** | `UPDATE … where id=? and version=?`, version +1. Sửa/thêm/xoá con qua collection **mappedBy** không tăng version cha. `find(…, OPTIMISTIC_FORCE_INCREMENT)` tăng chủ động (`update … set version=?` và chỉ cột đó). Hai bản D cùng version: người lưu sau bị `OptimisticLock*Exception`. |
| **Bẫy hay gặp** | (1) Tưởng sửa item làm version order tăng (không, trừ khi `FORCE_INCREMENT`). (2) Gọi `em.merge` trực tiếp ném `jakarta.persistence.OptimisticLockException`, qua repository mới được dịch thành `ObjectOptimisticLockingFailureException`. (3) `merge` entity "ma" sau rollback: `OptimisticLockException`, không INSERT. |
| **Mã tình huống** | G4-S04..S09, S11 · G9-S08 · G0-S13 |

## `@DynamicUpdate`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Tắt: `UPDATE` ghi **mọi cột** dù chỉ đổi một. |
| **Đặt ở phía nào** | Trên class entity. |
| **Ảnh hưởng DDL** | Không ảnh hưởng. |
| **Ảnh hưởng SQL/runtime** | `update … set quantity=? where id=?` (chỉ cột đổi). Đổi lại Hibernate sinh câu SQL mỗi lần flush, không dùng lại câu đã chuẩn bị. |
| **Bẫy hay gặp** | Chỉ đáng dùng khi bảng nhiều cột/cột lớn. Vì mặc định UPDATE mọi cột, đếm số câu SQL **không** phát hiện được lỗi ghi đè NULL: phải đọc dữ liệu thật (G9-S02b). |
| **Mã tình huống** | G4-S10 · G9-S02 |

## `@BatchSize` / `@Fetch` / `@EntityGraph` / `JOIN FETCH`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Không dùng: lazy tải từng phần tử một (N+1). |
| **Đặt ở phía nào** | `@BatchSize(size)`, `@Fetch(SUBSELECT)` trên collection/entity. `@EntityGraph` trên method repository. `JOIN FETCH` trong JPQL. |
| **Ảnh hưởng DDL** | Không ảnh hưởng. |
| **Ảnh hưởng SQL/runtime** | `JOIN FETCH` / `@EntityGraph`: **1 SELECT** (EntityGraph dùng `left join`, `join fetch` JPQL là inner join). `@BatchSize`: 2 SELECT, câu hai `customer_id = any (?)` (mảng, Postgres). `SUBSELECT`: 2 SELECT, câu hai `in (select … )`. DTO projection: chỉ đúng cột cần, 0 entity trong PC. |
| **Bẫy hay gặp** | (1) `join fetch` hai `List`: `MultipleBagFetchException` ném lúc **chạy** query, 0 SQL. (2) Hai `Set` chạy được nhưng nhân Descartes (2 × 3 = 6 dòng/customer). (3) Phân trang + `join fetch` collection: ở Hibernate 7.4 + Postgres DB cắt trang bằng subquery, không cảnh báo (cách 2 query vẫn đúng mọi version). (4) `join fetch` cần transaction/PC còn mở. |
| **Mã tình huống** | G5-S06..S13, S15, S16 |

## `@OrderColumn`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Không có: `List` là bag, không giữ thứ tự, không PK ở bảng nối. |
| **Đặt ở phía nào** | Trên `List` của owning side (`@ManyToMany` hoặc `@OneToMany`). |
| **Ảnh hưởng DDL** | Thêm cột `position integer`; PK bảng nối thành `(product_id, position)`, `tag_id` chỉ `NOT NULL`. |
| **Ảnh hưởng SQL/runtime** | Bỏ phần tử giữa `[a, b, c]` (index 1): **không** recreate; 1 DELETE dòng cuối + 1 UPDATE dồn vị trí. Add trùng cho phép (khác vị trí). |
| **Bẫy hay gặp** | PK bảng nối đổi sang `(product_id, position)`: `tag_id` không còn là phần của PK. Vẫn nên ưu tiên `Set` nếu không cần thứ tự. |
| **Mã tình huống** | G7-S06 |

## `@SoftDelete`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Không có: `remove` = `DELETE` thật. |
| **Đặt ở phía nào** | Trên class entity (Hibernate 6.4+). |
| **Ảnh hưởng DDL** | Thêm cột cờ `deleted` (boolean). |
| **Ảnh hưởng SQL/runtime** | `em.remove` → `UPDATE … set deleted = true`, 0 DELETE, dòng vẫn còn. Query và collection của cha tự thêm điều kiện lọc. `orphanRemoval` cũng chỉ đặt cờ. |
| **Bẫy hay gặp** | Dòng vẫn nằm trong bảng, nên truy vấn đọc thẳng bằng JDBC/native thấy cả dòng đã xoá mềm (test đọc DB thật để xác nhận cờ). |
| **Mã tình huống** | G9-S11 |

---

## `@Embeddable` / `@AttributeOverride`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Không có: field kiểu value object bị coi là không map được. |
| **Đặt ở phía nào** | `@Embeddable` trên class giá trị; `@Embedded` ở entity chủ; `@AttributeOverride` khi nhúng cùng một class từ hai lần trở lên. |
| **Ảnh hưởng DDL** | Không có bảng riêng; cột của embeddable nằm phẳng trong bảng chủ (`billing_city`, `shipping_city`, …). |
| **Ảnh hưởng SQL/runtime** | Sửa field tại chỗ (`setCity`) → 1 UPDATE nhờ dirty checking. Mọi cột đều NULL thì đọc lại ra `null` chứ không phải object rỗng. |
| **Bẫy hay gặp** | Nhúng hai lần mà thiếu override → `MappingException: Column 'city' is duplicated` lúc khởi động. Một cột khác NULL thì object tồn tại, field còn lại `null`. |
| **Mã tình huống** | G12-S01..S03 |

## `@ElementCollection`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Không có: `List<String>` không phải quan hệ, phải khai `@ElementCollection`. |
| **Đặt ở phía nào** | Trên collection của kiểu cơ bản hoặc embeddable; không phải entity. |
| **Ảnh hưởng DDL** | Bảng con riêng với FK về cha; kiểu bag (`List` không `@OrderColumn`) **không có PK**; `Set` có PK `(cha, giá trị)`; `@OrderColumn` có PK `(cha, vị trí)`. |
| **Ảnh hưởng SQL/runtime** | Bag: bỏ 1 phần tử → DELETE cả collection rồi INSERT lại phần còn lại (recreate). `Set`/`@OrderColumn`: DELETE đúng dòng. Entity con (`@OneToMany`): DELETE theo id. |
| **Bẫy hay gặp** | Dùng `List` mặc định là bẫy recreate. Cần sửa hoặc xoá từng phần tử, hay phần tử có logic riêng → dùng entity con. |
| **Mã tình huống** | G12-S04, S05 |

## `@Inheritance`

| Mục | Nội dung |
|---|---|
| **Mặc định** | `SINGLE_TABLE`. |
| **Đặt ở phía nào** | Trên lớp cha của cây kế thừa (`@Entity`). |
| **Ảnh hưởng DDL** | `SINGLE_TABLE`: 1 bảng + cột phân biệt, cột riêng của lớp con phải nullable. `JOINED`: 1 bảng mỗi lớp, PK lớp con cũng là FK, giữ được NOT NULL. `TABLE_PER_CLASS`: 1 bảng mỗi lớp cụ thể, lớp cha trừu tượng không có bảng. |
| **Ảnh hưởng SQL/runtime** | Query đa hình: `SINGLE_TABLE` 1 SELECT không join; `JOINED` 1 SELECT với `left join` tới mọi lớp con; `TABLE_PER_CLASS` 1 SELECT `union all`. |
| **Bẫy hay gặp** | `SINGLE_TABLE` mà đặt `nullable = false` ở cột lớp con thì INSERT của lớp con khác vỡ. `@ManyToOne LAZY` tới lớp cha cho proxy của lớp CHA: `instanceof` lớp con sai cho tới khi `Hibernate.unproxy`. |
| **Mã tình huống** | G12-S06..S09 |

## `@NaturalId`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Không có: Hibernate chỉ biết khoá chính. |
| **Đặt ở phía nào** | Trên field định danh nghiệp vụ (ví dụ `sku`). |
| **Ảnh hưởng DDL** | Thêm unique constraint trên cột đó. |
| **Ảnh hưởng SQL/runtime** | `byNaturalId` lần 1: 1 SELECT `where sku=?`; lần 2 trong cùng persistence context: 0 SELECT, cùng instance. |
| **Bẫy hay gặp** | Mặc định bất biến: đổi giá trị đã lưu → exception `An immutable natural identifier … was altered`, DB giữ giá trị cũ. Cần đổi thì `@NaturalId(mutable = true)`. |
| **Mã tình huống** | G12-S10 |

## `AttributeConverter` / `@Enumerated`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Enum lưu `ORDINAL` (số thứ tự). |
| **Đặt ở phía nào** | `@Convert` trên field (hoặc `@Converter(autoApply)`); `@Enumerated(EnumType.STRING)` trên enum. |
| **Ảnh hưởng DDL** | `ORDINAL` → `smallint` + check `between …`; `STRING` → `varchar` + check `in (…)`. |
| **Ảnh hưởng SQL/runtime** | Sửa object do converter trả về tại chỗ vẫn 1 UPDATE, dù có `equals` hay không. |
| **Bẫy hay gặp** | Đổi thứ tự (hoặc chèn giữa) enum đang lưu `ORDINAL` → dữ liệu cũ đọc sai **im lặng**, check constraint không cứu được. Dữ liệu sống lâu dùng `STRING`. |
| **Mã tình huống** | G12-S11 |

## `@Formula` / `@SQLRestriction` / `@Filter`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Không có. |
| **Đặt ở phía nào** | `@Formula` trên field; `@SQLRestriction` trên entity hoặc collection; `@Filter` định nghĩa ở entity, bật theo session. |
| **Ảnh hưởng DDL** | `@Formula` không có cột trong bảng. |
| **Ảnh hưởng SQL/runtime** | `@Formula` chỉ tính lại khi tải lại entity, không tự cập nhật trong cùng persistence context. `@SQLRestriction` luôn thêm điều kiện, kể cả `find`. `@Filter` chưa bật thì không lọc. |
| **Bẫy hay gặp** | `@SQLRestriction` không áp cho native query. `@Filter` **không** áp cho `find` theo id nên không phải rào bảo mật. `@SQLRestriction` trên collection chỉ lọc lúc nạp collection. |
| **Mã tình huống** | G12-S12..S14 |

## `@Transactional` (`readOnly`, propagation, `rollbackFor`)

| Mục | Nội dung |
|---|---|
| **Mặc định** | `REQUIRED`, `readOnly = false`, chỉ `RuntimeException`/`Error` mới rollback. |
| **Đặt ở phía nào** | Trên method hoặc class của service (không trên test, xem quy ước lab). |
| **Ảnh hưởng DDL** | Không có. |
| **Ảnh hưởng SQL/runtime** | `readOnly`: flush mode MANUAL, sửa entity không sinh UPDATE. `REQUIRED` lồng nhau dùng chung transaction; `REQUIRES_NEW` có transaction và persistence context riêng. |
| **Bẫy hay gặp** | Method B ném lỗi thì cả transaction chung bị đánh dấu rollback-only, A có `catch` vẫn `UnexpectedRollbackException`. Checked exception không rollback nếu thiếu `rollbackFor`. `readOnly` + id `SEQUENCE` → Postgres từ chối `nextval()`. Không OSIV thì chạm lazy ngoài transaction → `LazyInitializationException`. |
| **Mã tình huống** | G11-S01..S04, S12 |

## `LockModeType.PESSIMISTIC_*` / `SKIP LOCKED` / deadlock

| Mục | Nội dung |
|---|---|
| **Mặc định** | Không khoá; đọc xong có thể bị tx khác ghi đè (lost update). |
| **Đặt ở phía nào** | `em.find(…, lockMode)` / `Query.setLockMode` / `@Lock` ở repository; timeout qua hint `jakarta.persistence.lock.timeout`. |
| **Ảnh hưởng DDL** | Không có. |
| **Ảnh hưởng SQL/runtime** | `PESSIMISTIC_WRITE` → `for no key update of <alias>`; `PESSIMISTIC_READ` → `for share`. Timeout 0 thêm `nowait` (lỗi ngay); `SKIP LOCKED` cho hàng đợi việc. Ba cách chống lost update: `@Version` (retry), khoá (xếp hàng), `UPDATE … stock = stock - 1` (bỏ qua persistence context). |
| **Bẫy hay gặp** | Khoá hai dòng theo thứ tự ngược nhau → deadlock (Postgres huỷ đúng một tx, mất khoảng 1 giây); khoá theo thứ tự id thì tránh được. `PESSIMISTIC_WRITE` + `left join fetch` không lỗi nhưng thành 2 câu SQL, có khoảng hở và item không bị khoá. Worker không khoá nhận cùng một việc (job chạy hai lần). |
| **Mã tình huống** | G11-S05..S11 |

## Batch insert / `allocationSize` / `StatelessSession`

| Mục | Nội dung |
|---|---|
| **Mặc định** | Không batch: mỗi entity một INSERT; `SEQUENCE` `allocationSize = 50`. |
| **Đặt ở phía nào** | Batch size đặt theo session (`session.setJdbcBatchSize(n)`) hoặc toàn cục (`hibernate.jdbc.batch_size`, lab không bật); `allocationSize` ở `@SequenceGenerator`. |
| **Ảnh hưởng DDL** | `allocationSize` phải khớp bước tăng của sequence trong DB. |
| **Ảnh hưởng SQL/runtime** | Batch cần id không phải `IDENTITY` **và** batch size được bật; `IDENTITY` thì INSERT chạy ngay tại `persist`. `allocationSize = 50` → 100 entity chỉ 2 lần `nextval`. Xử lý nhiều dòng: `flush()` rồi `clear()` theo lô. `StatelessSession`: không dirty checking, cascade, lazy loading. |
| **Bẫy hay gặp** | Quên `flush()` trước `clear()` thì mất cập nhật. `SqlCaptor` chỉ thấy 1 INSERT cho cả lô (các lô dùng lại một PreparedStatement), phải đếm bằng log batch. |
| **Mã tình huống** | G13-S01..S05 |

## `@Cacheable` / `@Cache` / query cache

| Mục | Nội dung |
|---|---|
| **Mặc định** | Không cache cấp 2 (trong lab: `ENABLE_SELECTIVE`, chỉ entity `@Cacheable` mới vào cache). |
| **Đặt ở phía nào** | `@Cacheable` + `@Cache(usage = …)` trên entity hoặc collection; `setCacheable(true)` trên query. |
| **Ảnh hưởng DDL** | Không có. |
| **Ảnh hưởng SQL/runtime** | Lần đọc thứ hai (tx khác): 0 SELECT. Bulk JPQL tự evict đúng vùng; native phải khai `addSynchronizedEntityClass`, nếu không xoá toàn bộ cache. Cache collection chỉ giữ id: phần tử không cache thì vẫn N SELECT. |
| **Bẫy hay gặp** | Sửa DB bằng SQL/JDBC tay → cache entity và query cache trả dữ liệu cũ. `READ_ONLY` mà sửa entity → lỗi lúc commit. Chỉ cache dữ liệu ít đổi và chỉ ghi qua Hibernate. |
| **Mã tình huống** | G13-S06..S10 |

## Bytecode enhancement

| Mục | Nội dung |
|---|---|
| **Mặc định** | Tắt. Annotation LAZY ở phía inverse `@OneToOne` và `@Basic(fetch = LAZY)` bị bỏ qua lặng lẽ. |
| **Đặt ở phía nào** | Plugin Gradle `org.hibernate.orm` cho cả source set (lab: chỉ ở subproject `lab-enhanced/`). |
| **Ảnh hưởng DDL** | Không có. |
| **Ảnh hưởng SQL/runtime** | Có enhancement: `@OneToOne(mappedBy, LAZY)` và `@Basic(fetch = LAZY)` mới thật sự lazy; dirty tracking không làm UPDATE ngắn đi (vẫn ghi mọi cột nếu không `@DynamicUpdate`). |
| **Bẫy hay gặp** | Áp cho **mọi** entity của source set nên dễ đổi kết quả test cũ; bytecode chạy khác source đã đọc. Bật khi đã đo được vấn đề, không bật mặc định. Inverse `@OneToOne` không có thẻ vẫn tốn 1 SELECT. |
| **Mã tình huống** | G13-S11..S13 |

---

## Phạm vi chưa làm

Theo [02-SCENARIOS](02-SCENARIOS.md#coverage), sáu mã ➕ không có test riêng và **không** làm hụt annotation nào (annotation liên quan đã được test ở tình huống khác):

| Mã | Lý do |
|---|---|
| G6-S12 `optional = false` trên `@OneToOne` | Ngoài phạm vi Phase 8. Phần `optional = false` trên `@ManyToOne` đã có (G1-S04c/d). |
| G7-S14 Khi nào bỏ `@ManyToMany` | Chỉ là ghi chú, nằm trong README nhóm G7 (mục S14). |
| G9-S10 Batch insert SEQUENCE vs IDENTITY | Cần `hibernate.jdbc.batch_size` toàn cục (trái quy ước) nên không làm ở G9; **đã được làm bằng cách khác ở G13-S01..S03** (batch size đặt theo session, đếm lô bằng `BatchLogCapture`). |
| G14-I5 Admin sửa giá nhưng khách vẫn thấy giá cũ | Người học chốt chỉ làm ⭐ ở Phase 18. Nền kiến thức đã có ở G13-S06b, S07, S09c. |
| G14-R3 Đọc Hibernate `Statistics` để chẩn đoán | Như trên (chỉ ⭐). `Statistics` đã được dùng để đo cache ở G13. |
| G14-D1, G14-D2 | Không phải test: là tài liệu [LEAD-PLAYBOOK.md](LEAD-PLAYBOOK.md) và [adr/](adr/README.md), đã làm. |
