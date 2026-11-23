# G8 — `@JoinColumns` (FK nhiều cột) & derived identity

**Câu hỏi trung tâm:** khi bảng cha có PK ghép thì FK ở bảng con khai báo thế nào? `@MapsId("field")` hoạt động ra sao với `@EmbeddedId`?

**Model:** `Warehouse` (PK ghép `country_code + code`) 1—N `Shipment` 1—N `ShipmentLine` (PK ghép `shipment_id + line_no`) 1—N `ShipmentLineNote`.
J4 dùng `Product` + `OrderItem` riêng.

Entity nằm ở [src/main/.../g8_composite_fk](../../../../../../main/java/com/example/hibernatelab/g8_composite_fk/).
Mỗi biến thể là **một package = một schema Postgres**, tên class có tiền tố biến thể (`J1Warehouse`).

## Ba khái niệm phải nắm

1. **PK ghép thì FK trỏ tới nó cũng ghép**: Hibernate sinh **MỘT constraint hai cột**, không phải hai FK rời (S01a, S02).
   `@JoinColumns({@JoinColumn(name, referencedColumnName), …})` nói cột nào ghép cột nào; **mọi** `@JoinColumn` đều phải có
   `referencedColumnName`, thiếu một cột là lỗi lúc khởi động (S03).
2. **Derived identity (`@MapsId`)**: một nửa PK của con chính là FK trỏ cha. Hibernate tự điền nửa đó lúc `persist` (S04b).
   Hệ quả: id đã khác null một phần ngay từ đầu, nên Spring Data `save()` coi là entity cũ → merge + SELECT thừa (S04c).
   Sửa bằng `Persistable` (S04d).
3. **PK là bất biến**: sửa một phần id của entity MANAGED không gây lỗi mà cũng không UPDATE gì, thay đổi bị bỏ qua im lặng (S07).

## Biến thể

| Mã | Schema | Khác baseline | Test |
|---|---|---|---|
| J1 | `g8_j1_explicit` | `Shipment.warehouse` với `@JoinColumns` tường minh (`wh_country`, `wh_code`) (**baseline**) | S01a, S01b, S04a–c, S05, S07 |
| J1P | `g8_j1p_persistable` | `ShipmentLine implements Persistable` (bỏ warehouse khỏi shipment vì không cần) | S04d |
| J2 | `g8_j2_defaults` | `@ManyToOne` trơn, không `@JoinColumns` | S02 |
| J3 | `g8_j3_idclass` | `Warehouse` dùng `@IdClass` thay `@EmbeddedId` | S01c |
| J4 | `g8_j4_natural_key_ref` | FK trỏ tới cột UNIQUE không phải PK (`product.sku`) | S06a, S06b |
| — | `[BOOT-FAIL]` | `@JoinColumns` thiếu `referencedColumnName` ở một cột | S03 |

## Bảng: `@EmbeddedId` (J1) vs `@IdClass` (J3)

| | `@EmbeddedId` (J1) | `@IdClass` (J3) |
|---|---|---|
| Id trong entity | một field `id` kiểu `@Embeddable` | các field `@Id` nằm thẳng trong entity |
| DDL | PK 2 cột, FK ghép 2 cột | **y hệt** |
| `@JoinColumns` ở bảng con | như nhau (chỉ nói tên cột) | như nhau |
| `find` | `find(W.class, new WarehouseId("VN","HN01"))` | `find(W.class, new WarehouseKey("VN","HN01"))` |
| Đọc một phần id | `warehouse.getId().getCountryCode()` | `warehouse.getCountryCode()` |
| Dùng với `@MapsId("field")` | **có** (tên field trong embeddable) | không (đây là cách của `@EmbeddedId`) |

## `save()` với derived identity (S04)

| | J1 (không `Persistable`) | J1P (`Persistable`) |
|---|---|---|
| `em.persist(line)` | 1 INSERT, 0 SELECT, `shipmentId` được điền ngay | như J1 |
| `repository.save(line)` | **merge**: 1 SELECT (kèm left join notes) + 1 INSERT, trả về object khác | **persist**: 1 INSERT, 0 SELECT, trả về chính object truyền vào |
| Vì sao | `lineNo` ≠ null, không `@Version` ⇒ `isNew() = false` | `isNew()` do entity tự báo |

## FK trỏ tới khoá không phải PK (J4, S06)

- DDL: FK `order_item.product_sku → product.sku`; `sku` phải `UNIQUE` thì Postgres mới cho tạo FK.
- **LAZY vô hiệu**: `find(orderItem)` SELECT product ngay (`where sku=?`) vì proxy cần id mà cột FK chỉ lưu sku.
  Nếu đơn hàng truy cập nhiều, hãy cân nhắc trỏ tới PK để giữ được LAZY.

## Tình huống 🔬 khác giả thuyết

Chi tiết ở [docs/RESULTS.md](../../../../../../../docs/RESULTS.md):

- **S04a**: PK của `shipment_line` là `(line_no, shipment_id)`: cột derived (`@MapsId`) đứng sau cột thường, không theo thứ tự khai báo.
- **S04b**: `new ShipmentLine(...)` có `lineNo` gán sẵn bị báo **DETACHED** trước persist (nhưng `persist` vẫn chạy).
- **S07**: đổi một phần id của entity MANAGED **không lỗi, không SQL, DB không đổi** (giả thuyết cũ là "identifier altered").

## DDL trích ra

```text
-- J1: shipment(id PK, reference, wh_country, wh_code, FK (wh_country, wh_code) -> warehouse(country_code, code))
-- J1: shipment_line(shipment_id PK+FK, line_no, PK (line_no, shipment_id))     -- không có sequence
-- J1: shipment_line_note(id PK, text, shipment_id, line_no, FK (shipment_id, line_no) -> shipment_line)
-- J2: shipment(id PK, reference, warehouse_country_code, warehouse_code, FK ghép)  -- tên mặc định
-- J3: warehouse(country_code, code, PK (country_code, code))                       -- giống J1
-- J4: order_item(product_sku FK -> product(sku)), product(id PK, sku UNIQUE)
```

## Chạy

```bash
./gradlew test --tests '*G8*'                              # cả nhóm
./gradlew test --tests '*G8DerivedIdentityTest*s04c*'      # một tình huống
```
