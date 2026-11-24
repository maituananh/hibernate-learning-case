# ADR-001: Mọi quan hệ khai `LAZY` tường minh

**Trạng thái:** Mẫu · **Nhóm liên quan:** G5, G10, G14

## Bối cảnh

`@ManyToOne` và `@OneToOne` mặc định là `EAGER`. JPQL dịch nguyên văn nên `select o from PurchaseOrder o` không biết gì về EAGER: Hibernate chạy
thêm một SELECT cho mỗi giá trị khác nhau của quan hệ (N+1). Lỗi này không báo gì, chỉ chậm dần khi dữ liệu lớn.

## Quyết định

Mọi `@ManyToOne`, `@OneToOne` khai `fetch = FetchType.LAZY` **tường minh** (kể cả `@OneToMany`/`@ManyToMany` đã mặc định LAZY, vẫn ghi để người đọc khỏi phải nhớ).
Cần dữ liệu liên quan thì tải **theo từng use case** (`join fetch`, `@EntityGraph`, DTO projection), không tải sẵn trong mapping.

## Hệ quả

- Lợi: không còn N+1 "ngầm" do mapping; mỗi use case tự quyết định tải gì.
- Giá: phải nghĩ về cách tải ở từng use case; truy cập ngoài transaction gặp `LazyInitializationException` (xem ADR-004).
- LAZY không cứu được N+1: lặp N phần tử rồi chạm quan hệ vẫn tốn N SELECT (G5-S16). Cần thêm ADR-005.
- Ngoại lệ biết trước: phía `mappedBy` của `@OneToOne` không lazy được nếu không có bytecode enhancement (G6-S03, G13-S11).

## Bằng chứng

| Mã | Điều chứng minh |
|---|---|
| G5-S01, S02 | `@ManyToOne` EAGER: `find` nối `left join`, JPQL sinh N+1 |
| G5-S03, S16 | LAZY cho proxy, nhưng lặp trên proxy vẫn N+1 |
| G5-S13 | `@OneToMany` EAGER: `findAll()` tự sinh N+1 |
| G14-I1 | Mapping đúng LAZY nhưng service duyệt entity → 22 SELECT cho trang 10 đơn |

## Rào chắn

`MappingLint` quy tắc `EAGER-TO-ONE` và `EAGER-COLLECTION` ([MappingLint](../../src/test/java/com/example/hibernatelab/support/MappingLint.java), G14-R2-S01/S02).
Lint chỉ bắt được mapping; việc dùng LAZY sai cách bị bắt bởi ngân sách SQL (ADR-005).

## Khi nào xem lại

Khi một aggregate luôn luôn được đọc cùng quan hệ ở mọi use case (rất hiếm): vẫn nên dùng `@EntityGraph` mặc định thay vì EAGER.
