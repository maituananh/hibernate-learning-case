# Architecture Decision Records (ADR) mẫu

Năm ADR mẫu rút ra từ lab, mỗi ADR dẫn chứng bằng mã tình huống `G<N>-S<NN>` (test chạy trên PostgreSQL thật, Hibernate 7.4.15.Final)
và chỉ ra **rào chắn** để quyết định không bị phá âm thầm. Đây là mẫu để team chép sang dự án thật rồi sửa cho hợp ngữ cảnh.

| ADR | Quyết định | Trạng thái |
|---|---|---|
| [ADR-001](ADR-001-moi-quan-he-lazy.md) | Mọi quan hệ khai `LAZY` tường minh | Mẫu |
| [ADR-002](ADR-002-cam-cascade-remove-tren-many-to-one.md) | Cấm `cascade REMOVE`/`ALL` trên `@ManyToOne` và `@ManyToMany` | Mẫu |
| [ADR-003](ADR-003-optimistic-lock-mac-dinh.md) | Entity có "đọc rồi sửa" dùng `@Version` (optimistic lock) mặc định | Mẫu |
| [ADR-004](ADR-004-tat-osiv.md) | Tắt Open Session In View, controller trả DTO | Mẫu |
| [ADR-005](ADR-005-danh-sach-doc-dto-va-ngan-sach-sql.md) | Màn hình danh sách đọc DTO và có ngân sách SQL | Mẫu |

Khuôn mỗi ADR: **Bối cảnh → Quyết định → Hệ quả (lợi, giá phải trả) → Bằng chứng → Rào chắn → Khi nào xem lại**.
Cẩm nang tổng hợp: [LEAD-PLAYBOOK.md](../LEAD-PLAYBOOK.md).
