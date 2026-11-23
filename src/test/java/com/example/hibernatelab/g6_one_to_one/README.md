# G6 — `@OneToOne` & `@MapsId`

**Câu hỏi trung tâm:** FK đặt ở bảng nào? Phía inverse có LAZY được không? `@MapsId` khác FK thường thế nào?

**Model:** `Customer` 1—1 `LoyaltyCard` (FK thường) và `Customer` 1—1 `CustomerProfile` (chia sẻ khoá chính bằng `@MapsId`).

Entity nằm ở [src/main/.../g6_one_to_one](../../../../../../main/java/com/example/hibernatelab/g6_one_to_one/).
Mỗi biến thể là **một package = một schema Postgres**, tên class có tiền tố biến thể (`V3Customer`).

## Ba khái niệm phải nắm

1. **Hibernate chỉ biết "có hay không có" quan hệ nhờ cột FK.** Phía **owning** có cột FK nên đọc cột là biết ngay (có proxy, LAZY đúng).
   Phía **inverse** (`mappedBy`) không có cột nào, nên phải hỏi bảng bên kia và **LAZY bị bỏ qua** (S03, S04b, S08b).
2. **Chỉ owning side ghi FK.** `customer.setLoyaltyCard(card)` ở phía inverse không ghi gì (S05), `setLoyaltyCard(null)` cũng không (S09b).
3. **`@MapsId` biến FK thành PK**: profile không có id riêng, `profile.id == customer.id`. Muốn lấy profile chỉ cần
   `find(CustomerProfile.class, customerId)`, không cần map ngược (S06, S08).

## Biến thể

| Mã | Schema | Khác baseline | Test |
|---|---|---|---|
| V1 | `g6_v1_fk_on_customer` | `Customer.loyaltyCard @OneToOne @JoinColumn`: FK `customer.loyalty_card_id`, một chiều (**baseline**) | S01a, S11 |
| V2 | `g6_v2_fk_on_card` | FK chuyển sang `loyalty_card.customer_id`, một chiều | S01b, S02 |
| V3 | `g6_v3_bidi_owner_card` | V2 + `Customer.loyaltyCard (mappedBy, LAZY)` | S03, S05, S09b |
| V4 | `g6_v4_bidi_owner_customer` | V1 + `LoyaltyCard.customer (mappedBy, LAZY)` | S04 |
| V5 | `g6_v5_mapsid_uni` | `CustomerProfile` với `@MapsId`: `customer_id` vừa PK vừa FK | S06, S07, S08a, S13 |
| V6 | `g6_v6_mapsid_bidi` | V5 + `Customer.profile (mappedBy, LAZY)` | S08b |
| V7 | `g6_v7_orphan` | V3 + `cascade = ALL, orphanRemoval = true` trên `Customer.loyaltyCard` | S09a, S10 |

## Bảng: FK ở Customer vs FK ở LoyaltyCard

| | FK ở **Customer** (V1/V4) | FK ở **LoyaltyCard** (V2/V3) |
|---|---|---|
| Customer chưa có thẻ | `loyalty_card_id = NULL` | Không có dòng card |
| Ràng buộc "1 customer chỉ 1 thẻ" | Hibernate **tự thêm UNIQUE** `customer.loyalty_card_id` (S01a) | Hibernate **tự thêm UNIQUE** `loyalty_card.customer_id`; thẻ thứ hai bị DB từ chối (S01b, S02) |
| Tải Customer (`find`) | Phía owning: 1 SELECT, thẻ là **proxy**, LAZY đúng (S04a) | Phía inverse: **thêm 1 SELECT** `loyalty_card`, thẻ đã tải, LAZY bị bỏ qua (S03a) |
| JPQL 5 customer | 1 SELECT (thẻ là proxy) | **1 + 5 SELECT** (N+1, S03b) |
| Tải LoyaltyCard (`find`) | Phía inverse: thêm 1 SELECT customer (S04b) | Phía owning: LAZY đúng |
| `customer.setLoyaltyCard(null)` | `UPDATE customer SET loyalty_card_id = NULL`, dòng thẻ còn (S11) | Không có SQL, thẻ vẫn trỏ về customer (S09b) |
| Quy tắc chọn | Thực thể "đến sau" / tuỳ chọn nên giữ FK. Thẻ phụ thuộc customer nên **V2 tự nhiên hơn** | |

## `@MapsId` (V5, V6)

| | V5 (một chiều) | V6 (map ngược) |
|---|---|---|
| DDL | `customer_profile(customer_id PK + FK, bio)`, **không** có sequence riêng | như V5 |
| `find(customer)` | 1 SELECT, không đụng profile (S08a) | **thêm 1 SELECT** `customer_profile` (S08b) |
| Lấy profile | `find(CustomerProfile.class, customerId)`: 1 SELECT, không join (S06b) | như V5 |
| `save(profile)` Spring Data, `id == null` | Đi theo **persist**, INSERT thẳng (S06c) | |
| Profile thứ hai cho một customer | Cùng PC: `EntityExistsException`; khác PC: vi phạm PK ở DB (S07) | |

**Bài học:** ưu tiên `@MapsId` một chiều rồi `find` theo id, thay vì map ngược `@OneToOne(mappedBy)`.

## `orphanRemoval` trên `@OneToOne` (V7)

- `customer.setLoyaltyCard(null)`: V7 → `DELETE` thẻ; V3 → không có gì (S09).
- Thay thẻ cũ bằng thẻ mới trong một flush: **không vi phạm UNIQUE**, `DELETE` chạy **trước** `INSERT` (S10).
  Khác với collection ở G3-S10a (INSERT trước DELETE → vi phạm unique).

## Tình huống 🔬 khác giả thuyết

Chi tiết ở [docs/RESULTS.md](../../../../../../../docs/RESULTS.md).

- **S10a/S10b**: orphan removal của quan hệ to-one chạy trước INSERT nên không đụng UNIQUE, trái với collection (G3-S10a).
- **S13a**: `@MapsId` + `@GeneratedValue` trên cùng id **không** làm Hibernate lỗi lúc khởi động (không phải `[BOOT-FAIL]` như dự đoán);
  S13b cho thấy `@MapsId` thắng, `@GeneratedValue` bị bỏ qua trong im lặng.
- Không làm: **S12** (`optional = false`, ➕) không nằm trong phạm vi Phase 8 của 03-PHASES.

## DDL trích ra

```text
-- V1: customer(id PK, name NOT NULL, loyalty_card_id FK → loyalty_card(id), UNIQUE (loyalty_card_id))
-- V2: loyalty_card(id PK, card_no NOT NULL, customer_id FK → customer(id), UNIQUE (customer_id))
-- V5: customer_profile(customer_id PK + FK → customer(id), bio)   -- không có customer_profile_seq
```

## Chạy

```bash
./gradlew test --tests '*G6*'                    # cả nhóm
./gradlew test --tests '*G6FkPlacementTest*s03*' # một tình huống
```
