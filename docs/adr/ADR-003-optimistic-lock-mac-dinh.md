# ADR-003: Entity có "đọc rồi sửa" dùng `@Version` (optimistic lock) mặc định

**Trạng thái:** Mẫu · **Nhóm liên quan:** G4, G9, G11, G14

## Bối cảnh

Hai transaction cùng đọc `stock = 10`, cùng trừ rồi ghi: giao dịch ghi sau đè giao dịch ghi trước (lost update). Không có exception nào,
dữ liệu sai lặng lẽ. Dirty checking của Hibernate chỉ biết "khác với snapshot lúc tải", không biết DB đã đổi từ lúc đó.

## Quyết định

Entity nào có luồng "đọc → quyết định trong Java → ghi" (tồn kho, số dư, trạng thái đơn, hồ sơ) có cột `@Version`.
Service bắt `OptimisticLockingFailureException` và **chạy lại cả transaction** (retry có giới hạn, ví dụ 3 lần); retry nằm **ngoài** transaction.
Chỗ tranh chấp rất cao trên một dòng (số dư, tồn kho flash sale lớn) thì dùng `UPDATE … set stock = stock - ? where stock >= ?` nguyên tử hoặc `PESSIMISTIC_WRITE`.

## Hệ quả

- Lợi: không giữ khoá; xung đột thành lỗi rõ ràng thay vì mất dữ liệu; người đến sau đọc lại số mới nên bị chặn đúng bằng quy tắc nghiệp vụ.
- Giá: caller phải chấp nhận và test đường retry. Tranh chấp cao thì retry nhiều, lúc đó đổi sang khoá hoặc UPDATE nguyên tử.
- Cần nhớ: `@Version` không tự tăng khi chỉ sửa collection inverse của cha (G4); native/bulk update bỏ qua version (G10-S06).

## Bằng chứng

| Mã | Điều chứng minh |
|---|---|
| G11-S05 | Không `@Version`: stock 9 thay vì 8, không lỗi |
| G11-S06a, S06b, S06c | Ba cách sửa và cái giá của từng cách |
| G9-S08 | Hai người cùng sửa một đơn: người sau bị `OptimisticLock*Exception` |
| G14-I2-S01..S04 | Flash sale: bán 12 từ kho 10 → sửa bằng `@Version` + retry → rào chắn bất biến kho |

## Rào chắn

Test đua hai transaction bằng `ConcurrentTx` + kiểm tra bất biến nghiệp vụ, đỏ trên bản không version, xanh trên bản có version (G14-I2-S04).
Đưa test đua vào suite cho mọi đường "đọc rồi ghi" quan trọng.

## Khi nào xem lại

Khi retry xuất hiện thường xuyên trong số liệu vận hành: chuyển đường đó sang UPDATE nguyên tử hoặc khoá pessimistic.
