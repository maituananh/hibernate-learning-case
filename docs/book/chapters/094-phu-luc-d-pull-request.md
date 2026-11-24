## Phụ lục D. Danh sách Pull Request

Lab được xây qua 20 Pull Request, mỗi phase một PR. Khi một tình huống trong sách có khung Tra cứu, dòng PR trong khung đó chỉ về một trong các PR dưới đây: mở PR để xem code, mô tả từng tình huống và kết quả suite lúc phase đó hoàn thành.

Địa chỉ đầy đủ của mọi PR có dạng `https://github.com/maituananh/hibernate-learning-case/pull/<số>`.

### Giai đoạn đầu: Phase 1 đến Phase 12 (nhóm G0 đến G9)

| PR | Tiêu đề | Nội dung chính | URL |
|---|---|---|---|
| 1 | Phase 1: hạ tầng support và golden test | Bộ helper test dùng chung và test mẫu đầu tiên (persist, flush), làm khuôn cho mọi test về sau. | <https://github.com/maituananh/hibernate-learning-case/pull/1> |
| 2 | Phase 2: vòng đời và persistence context | Nhóm G0: persist, flush, dirty checking, merge, remove, getReference, cache cấp 1 và `save()` của Spring Data. | <https://github.com/maituananh/hibernate-learning-case/pull/2> |
| 3 | Phase 3: owning side | Nhóm G1: `@ManyToOne`, `@OneToMany`, `mappedBy`, `@JoinColumn`, bảng nối và INSERT rồi UPDATE bù. | <https://github.com/maituananh/hibernate-learning-case/pull/3> |
| 4 | Phase 4: CascadeType | Nhóm G2: từng loại cascade, `@OnDelete`, và ma trận thao tác theo loại cascade. | <https://github.com/maituananh/hibernate-learning-case/pull/4> |
| 5 | Phase 5: orphanRemoval | Nhóm G3: con bị tách khỏi cha, thứ tự flush INSERT trước DELETE, thay con trùng giá trị unique. | <https://github.com/maituananh/hibernate-learning-case/pull/5> |
| 6 | Phase 6: cập nhật con qua cha | Nhóm G4: sửa bảng con mà không đổi cha, `@Version`, `@OptimisticLock`, `@DynamicUpdate`. | <https://github.com/maituananh/hibernate-learning-case/pull/6> |
| 7 | Phase 7: FetchType và N+1 | Nhóm G5: EAGER và LAZY, N+1 và bốn cách sửa, fetch nhiều collection, phân trang. | <https://github.com/maituananh/hibernate-learning-case/pull/7> |
| 8 | Phase 8: OneToOne và MapsId | Nhóm G6: nơi đặt khóa ngoại, LAZY ở phía inverse, `@MapsId`, orphanRemoval một-một. | <https://github.com/maituananh/hibernate-learning-case/pull/8> |
| 9 | Phase 9: ManyToMany và JoinTable | Nhóm G7: owner của bảng nối, quên `mappedBy`, `Set` và `List`, `@OrderColumn`, entity trung gian. | <https://github.com/maituananh/hibernate-learning-case/pull/9> |
| 10 | Phase 10: JoinColumns và derived identity | Nhóm G8: khóa ngoại nhiều cột, khóa chính ghép, `@MapsId` theo field, khóa ngoại trỏ vào natural key. | <https://github.com/maituananh/hibernate-learning-case/pull/10> |
| 11 | Phase 11: tình huống tổng hợp | Nhóm G9: các tình huống có service layer và ranh giới transaction. | <https://github.com/maituananh/hibernate-learning-case/pull/11> |
| 12 | Phase 12: tổng kết | Chỉ tài liệu: cheatsheet theo annotation, bảng kết quả thực tế, đối chiếu phạm vi, README gốc. | <https://github.com/maituananh/hibernate-learning-case/pull/12> |

### Giai đoạn mở rộng: Phase 13 đến Phase 19 (nhóm G10 đến G14)

| PR | Tiêu đề | Nội dung chính | URL |
|---|---|---|---|
| 13 | Thiết kế giai đoạn mở rộng | Chỉ tài liệu: bản thiết kế phạm vi và quyết định cho G10 đến G14, đã được người học duyệt. | <https://github.com/maituananh/hibernate-learning-case/pull/13> |
| 14 | Phase 13: truy vấn và persistence context | Nhóm G10: `join` và `join fetch`, native query, `Page` kèm fetch collection, Specification. | <https://github.com/maituananh/hibernate-learning-case/pull/14> |
| 15 | Phase 14: transaction và khóa | Nhóm G11: `@Transactional`, lost update, khóa pessimistic, SKIP LOCKED, deadlock, OSIV, helper chạy song song. | <https://github.com/maituananh/hibernate-learning-case/pull/15> |
| 16 | Phase 15: mô hình dữ liệu nâng cao | Nhóm G12: Embeddable, ElementCollection, kế thừa, NaturalId, converter, Formula, SQLRestriction, Filter. | <https://github.com/maituananh/hibernate-learning-case/pull/16> |
| 17 | Phase 16: hiệu năng và cache | Nhóm G13a: batch insert, chiến lược sinh id, persistence context, StatelessSession, cache cấp 2. | <https://github.com/maituananh/hibernate-learning-case/pull/17> |
| 18 | Phase 17: bytecode enhancement | Nhóm G13b: subproject riêng cho enhancement, LAZY ở phía inverse OneToOne, `@Basic(fetch = LAZY)`, dirty tracking. | <https://github.com/maituananh/hibernate-learning-case/pull/18> |
| 19 | Phase 18: sự cố tổng hợp và rào chắn | Nhóm G14: bốn sự cố production, test ngân sách SQL, lint mapping, playbook và ADR mẫu. | <https://github.com/maituananh/hibernate-learning-case/pull/19> |
| 20 | Phase 19: tổng kết giai đoạn mở rộng | Chỉ tài liệu: đối chiếu lại kết quả khác giả thuyết và tổng kết toàn bộ lab. | <https://github.com/maituananh/hibernate-learning-case/pull/20> |

Cuốn sách này được viết trong một Pull Request riêng, sau PR 20.
