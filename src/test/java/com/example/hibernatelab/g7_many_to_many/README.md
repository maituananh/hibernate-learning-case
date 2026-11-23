# G7 — `@ManyToMany` & `@JoinTable`

**Câu hỏi trung tâm:** phía nào là owner của bảng nối? `Set` và `List` khác nhau thế nào? Khi nào phải chuyển sang entity trung gian?

**Model:** `Product` N—N `Tag` (M1…M7) và `Product` — `StockItem` — `Warehouse` (join entity có thêm cột, M8, M9).

Entity nằm ở [src/main/.../g7_many_to_many](../../../../../../main/java/com/example/hibernatelab/g7_many_to_many/).
Mỗi biến thể là **một package = một schema Postgres**, tên class có tiền tố biến thể (`M1Product`).

## Ba khái niệm phải nắm

1. **`@ManyToMany` = hai `@OneToMany` dính vào một bảng nối.** Bảng nối chỉ được **ghi** bởi MỘT phía (owning side, phía **không** có `mappedBy`).
   Add ở phía inverse không sinh SQL nào (S02b, S03b).
2. **Quên `mappedBy` = hai quan hệ độc lập = hai bảng nối** (`product_tags` và `tag_products`), dữ liệu dễ mâu thuẫn (S04).
3. **`List` không `@OrderColumn` là bag**: không có gì định danh một dòng nối, nên đổi collection thì Hibernate ghi lại cả bảng nối
   của owner (S05b), và bảng nối không có PK nên cho phép trùng (S09b). Dùng `Set` cho N—N.

## Biến thể

| Mã | Schema | Khác baseline | Test |
|---|---|---|---|
| M1 | `g7_m1_owner_product` | `Product.tags @ManyToMany @JoinTable` (`Set`), `Tag.products (mappedBy)` (**baseline**) | S01a, S02, S05a, S07, S09a, S13 |
| M2 | `g7_m2_defaults` | bỏ `@JoinTable` | S01b |
| M3 | `g7_m3_owner_tag` | owner là `Tag` (`@JoinTable` nằm trên `Tag.products`) | S03 |
| M4 | `g7_m4_no_mappedby` | cả hai phía đều là owning, không `mappedBy` | S04 |
| M5 | `g7_m5_list_bag` | `Product.tags` là `List` (bag) | S05b, S09b |
| M6 | `g7_m6_order_column` | M5 + `@OrderColumn(name = "position")` | S06 |
| M7 | `g7_m7_cascade_all` | `cascade = ALL` trên `Product.tags` (**anti-pattern**) | S08 |
| M8 | `g7_m8_join_entity` | `StockItem` là entity trung gian, PK ghép `@EmbeddedId` + 2 `@MapsId`, có cột `quantity` | S10, S11, S12 |
| M9 | `g7_m9_join_entity_surrogate` | như M8 nhưng id `SEQUENCE` riêng + `UNIQUE (product_id, warehouse_id)` | S12 |

## Bảng: owner `Product` (M1) vs owner `Tag` (M3)

| | Owner = **Product** (M1) | Owner = **Tag** (M3) |
|---|---|---|
| Ghi bảng nối khi | `product.getTags().add(tag)` (S02a) | `tag.getProducts().add(product)` (S03a) |
| Add ở phía còn lại | **0 SQL**, bảng nối vẫn trống (S02b) | **0 SQL** (S03b) |
| Thứ tự cột trong INSERT bảng nối | `(product_id, tag_id)` | `(tag_id, product_id)` |
| Xoá entity owner | Hibernate tự DELETE dòng nối rồi DELETE entity (S07a) | như vậy |
| Xoá entity inverse còn liên kết | **Vi phạm FK** (S07b); sửa: gỡ khỏi mọi owner trước (S07c) | như vậy |
| Chọn owner | Phía hay bị thay đổi liên kết, thường là phía "aggregate" (Product) | |

## `Set` vs `List` (bỏ 1 trong 3 tag, thêm trùng)

| | `Set` (M1) | `List` bag (M5) | `List` + `@OrderColumn` (M6) |
|---|---|---|---|
| PK bảng nối | `(product_id, tag_id)` | **không có PK** | `(product_id, position)` |
| Bỏ 1 trong 3 tag | **1 DELETE** đúng dòng (S05a) | **1 DELETE cả bảng nối của product + 2 INSERT** (S05b) | 1 DELETE dòng cuối + 1 UPDATE dồn vị trí (S06b) |
| Add trùng tag | Java từ chối, 1 dòng (S09a) | **2 dòng giống hệt** (S09b) | cho phép (khác vị trí) |

## `cascade = ALL` trên `@ManyToMany` (M7)

- Xoá P1 khi tag T còn được P2 dùng: Hibernate DELETE dòng nối P1 rồi DELETE T → **vi phạm FK**, rollback (S08a).
- Xoá P3 khi T3 chỉ có P3 dùng: **T3 mất âm thầm**, không lỗi (S08b).
- Tag là dữ liệu dùng chung, nên **không cascade REMOVE** trên `@ManyToMany`.

## Join entity: M8 vs M9

| | M8 (PK ghép) | M9 (id riêng) |
|---|---|---|
| PK | `(product_id, warehouse_id)` suy ra từ 2 `@MapsId` | `id` (sequence `stockitem_seq`) |
| Chặn trùng cặp | PK đã chặn | phải tự khai `UNIQUE (product_id, warehouse_id)` (S12b) |
| `find` | `find(StockItem.class, new StockItemId(productId, warehouseId))` (S11a) | `find(StockItem.class, id)` |
| `StockItemId` | `Serializable` + `equals/hashCode` theo giá trị (S11b) | không cần |
| Bảng khác muốn trỏ tới StockItem | FK **2 cột** (nối sang G8) | FK **1 cột** |
| Thêm / sửa / xoá | INSERT; sửa `quantity` **chỉ UPDATE `stock_item`**, 0 UPDATE product; DELETE nhờ `orphanRemoval` (S10) | tương tự |

## S14 — Khi nào bỏ `@ManyToMany` (chuyển sang join entity như M8/M9)?

`@ManyToMany` chỉ hợp khi bảng nối **chỉ có hai cột FK** và không bao giờ cần thêm gì. Dùng join entity khi:

- cần **cột phụ** (`quantity`, `createdAt`, `assignedBy`, …);
- cần **thao tác hoặc query trực tiếp trên quan hệ** (ví dụ "tổng tồn kho theo kho" là query trên `StockItem`);
- cần **audit** (ai thêm, khi nào) hoặc xoá mềm một liên kết;
- cần bỏ **bag recreate** của `List` mà vẫn muốn sửa từng dòng.

Trade-off: join entity nhiều code hơn (hai `@ManyToOne`, id ghép hoặc surrogate), nhưng mọi thay đổi là INSERT/UPDATE/DELETE đúng dòng.

## Tình huống 🔬 khác giả thuyết

Chi tiết ở [docs/RESULTS.md](../../../../../../../docs/RESULTS.md). Không có tình huống nào đảo ngược giả thuyết; phát hiện đáng nhớ:

- **S01b**: không `@JoinTable` thì bảng nối rơi vào schema `public` và tên cột theo **tên field** (`products_id`, `tags_id`).
- **S06a**: PK của bảng nối M6 là `(product_id, position)`, không phải cặp FK.
- **S08a**: Hibernate xoá **tag trước product**; lỗi FK nổ ngay ở câu `DELETE tag`.
- Không làm: **S14** chỉ là ghi chú (bảng trên), không có test riêng.

## DDL trích ra

```text
-- M1: product_tag(product_id FK, tag_id FK, PK (product_id, tag_id))
-- M2: public.product_tags(products_id, tags_id, PK (products_id, tags_id))   -- nằm ở schema public
-- M5: product_tag(product_id FK, tag_id FK)                                    -- không PK
-- M6: product_tag(position, product_id, tag_id, PK (product_id, position))
-- M8: stock_item(product_id PK+FK, warehouse_id PK+FK, quantity)               -- không có sequence
-- M9: stock_item(id PK, product_id FK, warehouse_id FK, quantity, UNIQUE (product_id, warehouse_id))
```

## Chạy

```bash
./gradlew test --tests '*G7*'                       # cả nhóm
./gradlew test --tests '*G7SetVsListTest*s05b*'     # một tình huống
```
