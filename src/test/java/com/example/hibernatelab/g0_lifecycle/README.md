# G0 — Vòng đời entity & persistence context

Schema Postgres: `g0_lifecycle`. Entity (trong [src/main/.../g0_lifecycle](../../../../../../main/java/com/example/hibernatelab/g0_lifecycle/)):

| Entity | Id | Dùng cho |
|---|---|---|
| `Customer` | `SEQUENCE` | Hầu hết tình huống (persist, flush, merge, remove, cache…) |
| `PurchaseOrder` | `SEQUENCE`, `@ManyToOne(LAZY) customer` | S08 (gán FK bằng `getReference`) |
| `IdentityCustomer` | `IDENTITY` | S02 (INSERT sớm) |
| `SkuProduct` | `String` gán tay, không `@Version` | S11b (save → merge, SELECT thừa) |
| `VersionedSkuProduct` | `String` gán tay + `@Version Long` | S11c (save → persist) |

Cùng `CustomerService` (readOnly, self-invocation) cho S12 và S14.

## Ba khái niệm phải nắm trước khi sang G1

1. **Mọi thay đổi bị HOÃN tới flush** (trừ `IDENTITY`). `persist`, `remove`, `setName` chỉ đổi bộ nhớ và xếp hàng SQL.
2. **flush ≠ commit.** Flush đẩy SQL xuống DB; chỉ commit mới làm transaction khác nhìn thấy.
3. **Chỉ entity MANAGED được theo dõi.** DETACHED thì sửa gì cũng không xuống DB; `merge()` trả về bản MANAGED *khác*.

## Vòng đời

```
            persist()                       flush() / commit
 TRANSIENT ──────────▶  MANAGED  ◀──────────────────────────── (dirty checking → UPDATE)
                         │  ▲  │
          remove()       │  │  │ detach() / clear() / hết transaction
        ┌────────────────┘  │  └──────────────▶ DETACHED ──merge()──▶ (bản MANAGED KHÁC)
        ▼                   │ persist() lại
     REMOVED ───────────────┘ (huỷ việc xoá)
        │ flush → DELETE
        ▼
      (gone)
```

## Tình huống

Thứ tự nên đọc: `PersistFlushTest` → `MergeDetachTest` → `ReferenceAndCacheTest` → `SpringDataSaveTest`.

| Mã | Test | Câu hỏi → Kết luận (đã xác minh) |
|---|---|---|
| G0-S01 ⭐ | [PersistFlushTest](PersistFlushTest.java) `s01_` | `persist()` INSERT lúc nào? → SEQUENCE: **0 INSERT** tới flush; flush xong người khác vẫn thấy 0 dòng, chỉ commit mới thấy. **flush ≠ commit.** |
| G0-S02 ⭐ | `PersistFlushTest` `s02_` | IDENTITY thì sao? → INSERT chạy **ngay ở `persist()`** (cần id từ DB), mất khả năng hoãn/gom SQL. Vẫn chưa commit nên người khác chưa thấy. |
| G0-S03 ⭐ | [MergeDetachTest](MergeDetachTest.java) `s03_` | Không gọi `save()` có UPDATE không? → Có: dirty checking so với ảnh chụp lúc load, tự UPDATE lúc flush. 1 SELECT + 1 UPDATE. |
| G0-S04a ⭐ | `MergeDetachTest` `s04a_` | Sửa entity sau khi hết transaction? → 0 UPDATE, DETACHED không ai theo dõi. |
| G0-S04b ⭐ | `MergeDetachTest` `s04b_` | `detach()` / `clear()` rồi sửa trong cùng tx? → 0 UPDATE. Quan trọng là còn trong persistence context hay không, không phải tx còn mở hay không. |
| G0-S05 ⭐ | `MergeDetachTest` `s05_` | `merge()` trả về gì? → Một bản MANAGED **khác** (`!=` bản truyền vào). Sửa tiếp bản DETACHED bị **mất**. `save()` của Spring Data cũng vậy: dùng giá trị trả về. |
| G0-S06a ⭐ | `MergeDetachTest` `s06a_` | `remove()` có DELETE ngay không? → Không: entity REMOVED, `em.contains` = false, DELETE chạy lúc flush. |
| G0-S06b ⭐ | `MergeDetachTest` `s06b_` | `em.remove(detached)`? → `IllegalArgumentException`. |
| G0-S06c ⭐🔬 | [SpringDataSaveTest](SpringDataSaveTest.java) `s06c_` | `repository.delete(detached)`? → Xoá được: Spring Data tự `find` (1 SELECT) rồi `remove` (1 DELETE). |
| G0-S07 ➕🔬 | `PersistFlushTest` `s07_` | `persist()` lại entity REMOVED? → Về MANAGED, DELETE bị huỷ, 0 SQL ghi. |
| G0-S08a ⭐ | [ReferenceAndCacheTest](ReferenceAndCacheTest.java) `s08a_` | `getReference()` có SELECT không? → Không, và `getId()` cũng không. Chỉ SELECT khi đọc field khác id (proxy khởi tạo). |
| G0-S08b ⭐ | `ReferenceAndCacheTest` `s08b_` | Gán FK bằng reference? → INSERT order, **0 SELECT customer**. Dùng `getReferenceById` thay `findById` khi chỉ cần FK. |
| G0-S08c ⭐ | `ReferenceAndCacheTest` `s08c_` | Reference của id không tồn tại? → Không lỗi lúc `getReference`; `EntityNotFoundException` khi đọc field. |
| G0-S08d ⭐ | `ReferenceAndCacheTest` `s08d_` | Dùng reference sai làm FK? → `persist` êm; lỗi vi phạm FK lúc flush, rollback. |
| G0-S09 ⭐ | `ReferenceAndCacheTest` `s09_` | `find` 2 lần? → 1 SELECT, **cùng instance** (cache cấp 1). Sau `clear()` SELECT lại, instance mới. |
| G0-S10a ➕ | `PersistFlushTest` `s10a_` | Sửa rồi chạy JPQL? → Auto flush: UPDATE chạy **trước** SELECT. |
| G0-S10b ➕🔬 | `PersistFlushTest` `s10b_` | Với native query? → Cũng auto flush (UPDATE trước SELECT) ở Hibernate 7.4.15. |
| G0-S11a ⭐ | `SpringDataSaveTest` `s11a_` | `save()` id tự sinh? → `persist`, 1 INSERT, trả về chính object. |
| G0-S11b ⭐ | `SpringDataSaveTest` `s11b_` | `save()` id gán tay, không `@Version`? → `merge`: **SELECT thừa rồi INSERT**, trả về bản khác. |
| G0-S11c ⭐ | `SpringDataSaveTest` `s11c_` | Thêm `@Version`? → `persist`, chỉ INSERT, version khởi đầu = 0. |
| G0-S12 ➕ | `SpringDataSaveTest` `s12_` | `readOnly = true` sửa entity? → 0 UPDATE, không báo lỗi. |
| G0-S13 ➕🔬 | `MergeDetachTest` `s13_` | `merge()` entity "ma" sau rollback? → `OptimisticLockException`, **không** INSERT. Đừng dùng lại object của tx đã rollback. |
| G0-S14a ➕ | `SpringDataSaveTest` `s14a_` | `@Transactional` gọi qua proxy? → Chạy đúng, 1 UPDATE. |
| G0-S14b ➕ | `SpringDataSaveTest` `s14b_` | Self-invocation `this.inner()`? → Mất transaction, 0 UPDATE. |

Các dòng 🔬 được ghi ở [docs/RESULTS.md](../../../../../../../docs/RESULTS.md).

## SQL thực tế (Hibernate 7.4.15.Final)

```sql
-- S01 (SEQUENCE): persist() chỉ lấy id, INSERT chờ tới flush
select nextval('g0_lifecycle.customer_seq')                          -- lúc persist()
insert into g0_lifecycle.customer (email,name,id) values (?,?,?)    -- lúc flush()

-- S02 (IDENTITY): INSERT chạy NGAY khi persist(), không đợi flush
insert into g0_lifecycle.identity_customer (name) values (?)

-- S03 dirty checking (không gọi save)
select ... from g0_lifecycle.customer where id=?
update g0_lifecycle.customer set email=?,name=? where id=?          -- lúc flush/commit

-- S11b save() với id gán tay = merge
select ... from g0_lifecycle.sku_product where id=?                  -- thừa
insert into g0_lifecycle.sku_product (name,id) values (?,?)
```

## DDL trích ra (từ `build/ddl/create.sql`)

```sql
create sequence g0_lifecycle.customer_seq start with 1 increment by 50;
create sequence g0_lifecycle.purchase_order_seq start with 1 increment by 50;

create table g0_lifecycle.customer (
    id bigint not null,
    email varchar(255),
    name varchar(255) not null,
    primary key (id)
);

create table g0_lifecycle.identity_customer (
    id bigint generated by default as identity,   -- IDENTITY: không có sequence
    name varchar(255) not null,
    primary key (id)
);

create table g0_lifecycle.purchase_order (
    customer_id bigint,                            -- cột FK nằm ở bảng này (OWNING side)
    id bigint not null,
    order_no varchar(255) not null,
    primary key (id)
);

create table g0_lifecycle.sku_product (
    id varchar(255) not null,                      -- id gán tay (field tên sku)
    name varchar(255) not null,
    primary key (id)
);

create table g0_lifecycle.versioned_sku_product (
    version bigint not null,                       -- @Version
    id varchar(255) not null,
    name varchar(255) not null,
    primary key (id)
);

alter table if exists g0_lifecycle.purchase_order
    add constraint FK158vbkwgyf5r6ogk9nkugqv2c     -- tên constraint do Hibernate sinh, đổi mỗi lần đổi mapping
    foreign key (customer_id) references g0_lifecycle.customer;
```

## Chạy

```bash
./gradlew test --tests '*g0_lifecycle*'            # cả nhóm G0
./gradlew test --tests '*SpringDataSaveTest*s11b_*' # một tình huống
```
