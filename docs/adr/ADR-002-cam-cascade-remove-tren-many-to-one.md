# ADR-002: Cấm `cascade REMOVE`/`ALL` trên `@ManyToOne` và `@ManyToMany`

**Trạng thái:** Mẫu · **Nhóm liên quan:** G2, G7, G14

## Bối cảnh

`cascade` là thao tác của `EntityManager`, chạy khi `persist/merge/remove`: `REMOVE` nghĩa là "xoá cha thì xoá luôn thứ được tham chiếu".
Đặt nó ở phía con trỏ tới cha (`@ManyToOne`) hoặc ở quan hệ nhiều-nhiều thì xoá một bản ghi có thể xoá dữ liệu **dùng chung** (khách hàng, tag, sản phẩm).

## Quyết định

`cascade` chỉ đặt trên collection của **cha** để vòng đời con theo cha (đơn → dòng hàng): `{PERSIST, MERGE, REMOVE}` hoặc `ALL`.
**Cấm** `REMOVE` và `ALL` trên `@ManyToOne` và `@ManyToMany`. Muốn xoá nhanh cả cây con thì cân nhắc `@OnDelete(CASCADE)` ở FK (ADR riêng nếu dùng).

## Hệ quả

- Lợi: xoá một đơn không bao giờ kéo theo xoá khách hay tag dùng chung.
- Giá: phải tự quản lý vòng đời của thứ được tham chiếu (xoá ở nơi sở hữu nó).
- Cần nhớ: bulk JPQL và `deleteAllInBatch()` không đi qua cascade (G2); `@OnDelete` làm Hibernate không biết con đã mất (G2-S10, G9-S06).

## Bằng chứng

| Mã | Điều chứng minh |
|---|---|
| G2-S08, S09 | `REMOVE` trên `@ManyToOne` xoá nhầm dữ liệu dùng chung |
| G7-S08 | `cascade = ALL` trên `@ManyToMany`: xoá product kéo xoá tag dùng chung (lỗi FK hoặc mất tag im lặng) |
| G9-S06 | So sánh bốn chiến lược xoá (11 SQL, 2 SQL, 3 SQL) |

## Rào chắn

`MappingLint` quy tắc `CASCADE-REMOVE`: bắt `cascade REMOVE/ALL` trên `@ManyToOne` và `@ManyToMany` (G14-R2-S02 chứng minh bắt được; G14-R2-S03 chứng minh không báo nhầm cascade `PERSIST` trên `@OneToMany`).

## Khi nào xem lại

Khi mô hình có "thứ được tham chiếu" chỉ thuộc về đúng một cha (quan hệ `@OneToOne` thật sự sở hữu): dùng `orphanRemoval` ở phía cha thay vì cascade ngược.
