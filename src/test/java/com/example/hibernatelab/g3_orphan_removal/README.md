# G3 — `orphanRemoval`: con bị **tách khỏi cha** thì dòng của con ra sao?

**Câu hỏi trung tâm:** khác với G2 (cha bị xoá), ở đây cha vẫn còn, chỉ có con bị gỡ khỏi collection (hoặc chuyển đi). DB có DELETE không?

**Model:** `PurchaseOrder` 1—N `OrderItem` 1—N `ItemDiscount` (3 tầng, hai chiều chuẩn, có helper `addItem/removeItem/addDiscount`).
`OrderItem` có **UNIQUE `(order_id, product_code)`** (giống ở mọi biến thể, dùng cho S10). Cột FK `order_item.order_id` **nullable** để nhìn thấy "dòng mồ côi" (S02).

Entity nằm ở [src/main/.../g3_orphan_removal](../../../../../../main/java/com/example/hibernatelab/g3_orphan_removal/). Mỗi biến thể là **một package = một schema Postgres**,
có 3 entity (`PurchaseOrder`, `OrderItem`, `ItemDiscount`), tên class có tiền tố biến thể (`O1PurchaseOrder`).

## Hai khái niệm phải nắm

1. **`orphanRemoval` so collection HIỆN TẠI với bản SNAPSHOT lúc tải.** Phần tử có trong snapshot mà không còn trong collection
   bị coi là "mồ côi" và bị DELETE lúc **flush** (không phải lúc gọi `remove`). Nó **không** nhìn `item.order` (owning side), nên chỉ cần
   `items.remove(item)` là đủ (S04), khác với O0 nơi việc đó không làm gì cả (S01).
2. **Hibernate phải giữ nguyên instance collection** đã tải để theo dõi snapshot. Gán hẳn list mới (`setItems(new ArrayList<>())`) là lỗi (S07a);
   dùng `clear()` + `addAll()` (S07b).

## Biến thể

| Mã | Schema | `PurchaseOrder.items` | `OrderItem.discounts` | Test |
|---|---|---|---|---|
| O0 | `g3_o0_cascade_all` | `cascade = ALL`, `orphanRemoval = false` (**baseline**) | `cascade = ALL` | S01, S02, S05a |
| O1 | `g3_o1_orphan` | `cascade = ALL`, `orphanRemoval = true` | `cascade = ALL, orphanRemoval = true` | S03, S04, S05b, S07–S09, S10, S11 |
| O2 | `g3_o2_orphan_no_cascade` | **không cascade**, `orphanRemoval = true` | như O1 | S06 |

## `CascadeType.REMOVE` vs `orphanRemoval` (điền từ kết quả test thật)

| | `CascadeType.REMOVE` (O0) | `orphanRemoval = true` (O1, O2) |
|---|---|---|
| Xoá cha | ✅ Xoá con: DELETE discount → item → order (S05a) | ✅ Xoá con, kể cả khi KHÔNG có cascade (O2, S06a). O1 giống hệt O0 (S05b) |
| `items.remove(item)` (quên set null) | ❌ **0 SQL**, FK vẫn trỏ về order (S01) | ✅ **DELETE** item (S04) |
| `removeItem(item)` (remove + set null) | ⚠️ `UPDATE order_id = NULL`, dòng **mồ côi** còn mãi (S02) | ✅ DELETE discount rồi DELETE item, 0 UPDATE (S03) |
| `items.clear()` | — (không test riêng) | ✅ DELETE từng discount và item, order còn nguyên (S08) |
| Gán collection mới | Bình thường | 💥 `HibernateException: A collection with orphan deletion was no longer referenced…` (S07a). Sửa: `clear()` + `addAll()` (S07b) |
| Persist cha có con mới | ✅ cascade PERSIST (nếu khai) | ❌ không thay được PERSIST: ở O2 item bị bỏ qua **im lặng**, không lỗi (S06b) |
| Chuyển item sang order khác (cùng tx) | — | ✅ chỉ `UPDATE order_id`, **không** mất dữ liệu (S09a); đổi FK trực tiếp cũng vậy (S09b) |
| `merge` đồ thị DETACHED bỏ bớt item | — | ✅ item vắng mặt bị DELETE (S11) |

## ⚠️ Thứ tự ActionQueue lúc flush (S10)

Lúc flush Hibernate chạy theo thứ tự cố định: **INSERT → UPDATE → (thao tác collection) → DELETE**, bất kể thứ tự bạn viết trong code.
**DELETE do orphanRemoval cũng đứng cuối**, giống `em.remove`.

| Tình huống | Kết quả |
|---|---|
| O1 `removeItem(old); addItem(new cùng product_code)` một lần flush (S10a) | 💥 Vi phạm UNIQUE: INSERT mới chạy trước DELETE cũ. **Khác giả thuyết ban đầu** (xem `docs/RESULTS.md`) |
| O0 `em.remove(old); persist(new cùng code)` (S10b) | 💥 Vi phạm UNIQUE, cùng nguyên nhân |
| Cách sửa: `em.flush()` giữa `removeItem` và `addItem` (S10c) | ✅ DELETE chạy trước, rồi INSERT |

Ghi chú: ở O0, nếu `em.remove(old)` mà `old` vẫn nằm trong `order.items` (cascade ALL), cascade PERSIST lúc flush có thể "cứu" nó khỏi DELETE.
Vì vậy S10b gỡ `old` khỏi collection trước khi `em.remove`.

## Tình huống 🔬 khác giả thuyết ban đầu

- **S10a**: giả thuyết "thành công vì orphan removal chạy trước INSERT" **sai**. Thực tế vi phạm unique.
- **S07a**: hành vi đúng như dự kiến, nhưng câu chữ exception là `A collection with orphan deletion was no longer referenced…` (không còn `cascade="all-delete-orphan"`).

Chi tiết từng dòng ở [docs/RESULTS.md](../../../../../../../docs/RESULTS.md).
