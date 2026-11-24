# ADR-004: Tắt Open Session In View (OSIV), controller trả DTO

**Trạng thái:** Mẫu · **Nhóm liên quan:** G5, G11, G9

## Bối cảnh

Spring Boot mặc định `spring.jpa.open-in-view=true`: persistence context mở suốt request, nên controller/serializer chạm quan hệ LAZY vẫn chạy được.
Hệ quả: SQL chạy **ngoài transaction** ở tầng web, N+1 bị che (không còn `LazyInitializationException` để báo), và connection bị giữ lâu hơn cần thiết.

## Quyết định

`spring.jpa.open-in-view: false` (đã đặt trong [application.yml](../../src/main/resources/application.yml)).
Service trả **DTO** (hoặc entity đã tải đủ dữ liệu cần), không trả entity có quan hệ LAZY chưa khởi tạo ra ngoài ranh giới transaction.

## Hệ quả

- Lợi: lỗi tải thiếu dữ liệu hiện ngay (`LazyInitializationException`) thay vì thành N+1 âm thầm; SQL chỉ chạy trong transaction; connection nhả sớm.
- Giá: mỗi use case phải tải đủ dữ liệu trong service (xem ADR-001, ADR-005).

## Bằng chứng

| Mã | Điều chứng minh |
|---|---|
| G11-S12a | Không OSIV: `LazyInitializationException` ngoài transaction |
| G11-S12b | OSIV giả lập: lazy load chạy được nhưng SELECT chạy ngoài transaction, che N+1 |
| G9-S07 | Ranh giới transaction: service trả DTO |
| G5-S04 | `LazyInitializationException` khi chạm proxy ngoài transaction |

## Rào chắn

Giữ `open-in-view: false` trong cấu hình chung (cả test); test tích hợp gọi service rồi chạm quan hệ ngoài service để chắc nó vẫn ném `LazyInitializationException`.
Thêm kiểm tra cấu hình khởi động (ví dụ test đọc `spring.jpa.open-in-view`) nếu team lo bị ai đó bật lại.

## Khi nào xem lại

Khi dự án thật sự chỉ phục vụ server-side rendering với view đọc entity (hiếm); vẫn nên ưu tiên DTO.
