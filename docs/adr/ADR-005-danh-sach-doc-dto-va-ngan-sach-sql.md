# ADR-005: Màn hình danh sách đọc DTO và có ngân sách SQL

**Trạng thái:** Mẫu · **Nhóm liên quan:** G5, G10, G14

## Bối cảnh

Mapping có thể hoàn toàn đúng (mọi quan hệ LAZY, ADR-001) mà use case vẫn N+1 vì service nạp entity rồi duyệt từng entity để lấy tên khách và số dòng hàng.
Review mapping không thấy lỗi này, dữ liệu trả về cũng đúng; chỉ đếm SQL hoặc dữ liệu lớn mới lộ ra.

## Quyết định

1. Use case danh sách/báo cáo chỉ đọc đọc **DTO projection** bằng một query (`select new …`, interface projection), không nạp entity rồi duyệt.
2. `Page` có `join fetch` collection thì luôn khai báo `countQuery` riêng.
3. Mỗi use case đọc quan trọng có **ngân sách SQL**: một test đặt trần số câu SELECT; vượt trần thì test đỏ kèm danh sách SQL thừa.

## Hệ quả

- Lợi: số câu SQL cố định theo use case, không tăng theo cỡ trang/dữ liệu; N+1 không thể lọt qua review vì test đỏ.
- Giá: viết thêm DTO/query và một test ngân sách mỗi use case; khi cần thêm dữ liệu vào màn hình phải sửa cả ngân sách (là chủ ý: để người sửa nhìn lại chi phí).

## Bằng chứng

| Mã | Điều chứng minh |
|---|---|
| G5-S05, S16 | N+1 trên collection và trên `@ManyToOne` LAZY khi lặp |
| G5-S15, G10-S10 | DTO projection: đúng cột cần, 0 entity trong persistence context |
| G10-S08 | `Page` + `join fetch`: count tự sinh đếm dòng sau join (total 6 thay vì 3) |
| G14-I1-S01..S04 | Trang 10 đơn: 22 SELECT → 2 SELECT bằng DTO + `countQuery`; ngân sách bắt bản lỗi |
| G14-R1-S01..S03 | Hành vi của `SqlBudget`: trần, thông báo liệt kê SQL, chỉ đếm SELECT |

## Rào chắn

`SqlBudget.assertSelectsAtMost(captor, "tên use case", N)` ([SqlBudget](../../src/test/java/com/example/hibernatelab/support/SqlBudget.java)).
Test phải chứng minh cả hai chiều: đỏ trên đường lỗi, xanh trên đường đã sửa (G14-I1-S04).

## Khi nào xem lại

Khi use case cần tải đồ thị entity để sửa (không phải màn hình đọc): dùng `join fetch`/`@EntityGraph` và vẫn đặt ngân sách SQL, chỉ khác là trần có thể lớn hơn.
