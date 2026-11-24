# LEAD-PLAYBOOK — cẩm nang quyết định cho người dẫn dắt team dùng JPA/Hibernate

Tài liệu này biến kết quả của lab (G0–G14) thành **quyết định và checklist** dùng được ngoài lab. Mỗi dòng đều trỏ mã tình huống `G<N>-S<NN>`
(xem test trong README của nhóm, hoặc [RESULTS.md](RESULTS.md) nếu là 🔬) để bạn **kiểm chứng thay vì tin lời**. Mọi con số xác minh trên
PostgreSQL thật, Hibernate 7.4.15.Final. Tra cứu theo annotation: [CHEATSHEET.md](CHEATSHEET.md). Quyết định đã chốt thành văn bản: [docs/adr/](adr/).

Mục lục: [1. Checklist review PR](#1-checklist-review-pr-có-jpa) · [2. Bảng quyết định G0–G9](#2-bảng-quyết-định-g0g9) ·
[3. Bảng quyết định G10–G13](#3-bảng-quyết-định-g10g13) · [4. Từ triệu chứng ra nguyên nhân (G14)](#4-từ-triệu-chứng-ra-nguyên-nhân-g14) ·
[5. Rào chắn nên đưa vào dự án](#5-rào-chắn-nên-đưa-vào-dự-án)

---

## 1. Checklist review PR có JPA

Đọc theo thứ tự. Mỗi mục là một câu hỏi để hỏi tác giả khi thấy dấu hiệu bên phải.

### Mapping

| Câu hỏi | Dấu hiệu trong PR | Mã |
|---|---|---|
| Mọi `@ManyToOne`/`@OneToOne` có `fetch = LAZY` tường minh? | `@ManyToOne` trơn (mặc định EAGER) | G5-S01..S03, ADR-001 |
| Quan hệ hai chiều có `mappedBy` ở phía collection và helper `addX/removeX`? | Không `mappedBy` → thêm cột FK lẫn bảng nối; chỉ `getOrders().add()` → FK NULL | G1-S12, S15 |
| `cascade` chỉ đặt ở phía **cha → con**? | `CascadeType.REMOVE`/`ALL` trên `@ManyToOne` hoặc `@ManyToMany` | G2-S08, S09, G7-S08, ADR-002 |
| `@JoinTable` có `schema`? Quan hệ `@ManyToMany` có đáng giữ không? | Bảng nối rơi vào `public`; cần cột phụ mà vẫn `@ManyToMany` | G1-S07, G7 |
| Collection dùng `Set` (hoặc `@OrderColumn`) thay vì `List` bag? | `List` trong `@ManyToMany`: đổi một phần tử = xoá cả dòng nối rồi insert lại | G7-S05, S06 |
| `equals/hashCode` không đụng tới quan hệ LAZY hay id sinh sau? | `toString()`/`hashCode()` đọc cả collection hai chiều | G9-S03, S04 |
| Entity ghi nhiều có `@Version`? | Entity có "đọc rồi sửa" (tồn kho, số dư, trạng thái) mà không version | G11-S05, S06a, ADR-003 |
| Id có `IDENTITY` ở bảng sẽ insert hàng loạt không? | `GenerationType.IDENTITY` | G13-S02, S03 |

### Query và chuyển đổi dữ liệu

| Câu hỏi | Dấu hiệu trong PR | Mã |
|---|---|---|
| Màn hình danh sách đọc DTO hay đọc entity rồi duyệt? | Vòng lặp gọi `getX().getY()` trên quan hệ LAZY | G5-S05, S15, G10-S10, G14-I1 |
| `Page<…>` có `join fetch` collection thì có `countQuery` riêng? | Không `countQuery` → total sai (đếm dòng sau join) | G10-S08 |
| `join fetch` có `where` trên chính alias được fetch? | Collection thiếu dữ liệu, persistence context giữ bản thiếu | G10-S02 |
| Fetch hai `List` cùng lúc? | `MultipleBagFetchException` | G5-S10 |
| Native query/bulk `@Modifying` có `clear()`/`refresh()` sau đó? Bảng có `@Version`/`@SoftDelete`? | Entity MANAGED giữ giá trị cũ; `version` không tăng | G10-S06, G9-S05 |

### Transaction và khoá

| Câu hỏi | Dấu hiệu trong PR | Mã |
|---|---|---|
| Có `catch` lỗi của service `@Transactional` rồi đi tiếp? | Transaction chung đã rollback-only → `UnexpectedRollbackException` | G11-S02 |
| `throws Exception` có `rollbackFor`? | Checked exception mặc định vẫn **COMMIT** | G11-S04 |
| Lời gọi `this.methodKhac()` có cần transaction riêng? | Self-invocation bỏ qua proxy → mất `@Transactional` | G0-S14b |
| Hàng đợi việc nhiều worker có `SKIP LOCKED`? | `findFirstByStatus("NEW")` không khoá → gửi trùng | G11-S09, G14-I3 |
| Khoá nhiều dòng theo thứ tự id? | Hai transaction khoá ngược thứ tự → deadlock | G11-S10 |
| Controller có chạm collection LAZY (OSIV)? | `open-in-view` bật → SQL chạy ngoài transaction, che N+1 | G11-S12, ADR-004 |

### Hiệu năng và kiểm thử

| Câu hỏi | Dấu hiệu trong PR | Mã |
|---|---|---|
| Import/job nhiều dòng có `flush` + `clear` theo lô? | Vòng `persist` dài không `clear()` → persistence context phình | G13-S04, G14-I4 |
| Cache cấp 2 chỉ cho dữ liệu tham chiếu, chỉ ghi qua Hibernate? | `@Cacheable` trên bảng bị script khác sửa | G13-S06, S09c |
| Có test **đếm SQL** cho use case mới? | Use case danh sách/báo cáo không có ngân sách SELECT | G14-R1, ADR-005 |
| Test dữ liệu đọc bằng JDBC, không bằng repository? | Assert qua `findById` → cache cấp 1 nói dối | CLAUDE.md, G0-S09 |

---

## 2. Bảng quyết định G0–G9

> Phần này viết mới ở đây vì README của G0–G9 đã chốt và không sửa. Nội dung lấy từ [CHEATSHEET.md](CHEATSHEET.md) và README từng nhóm.

### G0 — Vòng đời entity

| Tình huống trong dự án | Nên làm | Vì sao | Mã |
|---|---|---|---|
| Sửa entity trong service | Tải entity trong transaction rồi **đổi field**; không cần `save()` | Dirty checking tự UPDATE lúc flush | G0-S03 |
| Sửa object đã ra khỏi transaction (DETACHED) | `merge` rồi dùng **bản trả về**; hoặc tải lại rồi sửa | `merge` trả bản MANAGED khác, sửa tiếp bản cũ bị mất | G0-S04, S05 |
| Chỉ cần gán FK, không cần dữ liệu cha | `getReferenceById` thay `findById` | 0 SELECT | G0-S08 |
| `save()` entity id gán tay | Thêm `@Version` (hoặc `Persistable`) | Không version → `save` đi `merge`: SELECT thừa trước INSERT | G0-S11 |
| Tin vào việc "INSERT đã chạy" trước commit | Không | SEQUENCE hoãn tới flush; flush ≠ commit | G0-S01, S02 |
| Method `@Transactional` gọi method cùng class | Tách bean riêng | Self-invocation mất transaction | G0-S14 |

### G1 — Owning side và vị trí FK

| Tình huống | Nên làm | Vì sao | Mã |
|---|---|---|---|
| Quan hệ cha–con một-nhiều | `@ManyToOne` ở con (owning) + `@OneToMany(mappedBy)` ở cha | FK nằm ở bảng con, không UPDATE bù, không bảng nối | G1-S01, S12 |
| Cần quan hệ hai chiều | Luôn viết helper `addOrder/removeOrder` đặt cả hai phía | Hibernate không tự đồng bộ bộ nhớ | G1-S14 |
| Quan hệ bắt buộc | `optional = false` **và** `@JoinColumn(nullable = false)` | `nullable` không chỉ là DDL: Hibernate chặn trước khi gửi SQL, dùng inner join | G1-S04 |
| Đặt tên cột/FK | Khai `@JoinColumn(name = …)` và `@JoinTable(schema = …)` | Mặc định đặt theo tên field, bảng nối rơi vào `public` | G1-S07, S09 |
| `@OneToMany` một chiều | Tránh (chỉ khi thật cần) | INSERT rồi UPDATE bù, hoặc bảng nối | G1-S09, S10 |

### G2 — Cascade

| Tình huống | Nên làm | Vì sao | Mã |
|---|---|---|---|
| Con sống chết theo cha (đơn → dòng hàng) | `cascade = {PERSIST, MERGE, REMOVE}` trên collection của **cha** (hoặc `ALL`) | Vòng đời con phụ thuộc cha | G2 |
| Quan hệ tới dữ liệu dùng chung (customer, product) | **Không** cascade REMOVE | Xoá một đơn xoá luôn khách | G2-S08, S09 |
| Xoá hàng loạt | Nhớ bulk JPQL/`deleteAllInBatch` **không** đi qua cascade | Cascade chỉ chạy qua `em.persist/merge/remove` | G2 |
| Merge đồ thị DETACHED | Cascade đủ `MERGE` | Chỉ `PERSIST`: dòng mới thành "dòng ma" rỗng | G2 |

### G3 — orphanRemoval

| Tình huống | Nên làm | Vì sao | Mã |
|---|---|---|---|
| Bỏ một dòng khỏi đơn là xoá dòng đó | `orphanRemoval = true` + `items.remove(item)` | So collection hiện tại với snapshot, DELETE lúc flush | G3 |
| Thay cả danh sách | `clear()` + `addAll()`, **không** `setItems(new …)` | Gán collection mới → `HibernateException` | G3-S07, S08 |
| Xoá dòng cũ rồi thêm dòng trùng unique key | `em.flush()` xen giữa | Thứ tự flush INSERT → UPDATE → DELETE vi phạm unique | G3-S10 |

### G4 — Sửa bảng con qua bảng cha

| Tình huống | Nên làm | Vì sao | Mã |
|---|---|---|---|
| Sửa con có phải tăng `version` của cha? | Biết rằng collection **inverse** không làm cha dirty; cần thì `OPTIMISTIC_FORCE_INCREMENT` | Version của cha không tự tăng | G4-S04..S09 |
| Bảng nhiều cột/cột lớn | `@DynamicUpdate` | UPDATE mặc định ghi mọi cột | G4-S10 |

### G5 — Fetch và N+1

| Tình huống | Nên làm | Vì sao | Mã |
|---|---|---|---|
| Mọi quan hệ | `LAZY` tường minh; tải thêm theo use case | EAGER biến JPQL thành N+1 | G5-S01..S03 |
| Cần cha kèm con để dùng ngay | `join fetch` / `@EntityGraph` | 1 SELECT | G5-S06, S07 |
| Cần nhiều collection | Tách query hoặc `@BatchSize`/`SUBSELECT` | Hai `List` fetch → `MultipleBagFetchException`; hai `Set` → tích Descartes | G5-S10, S11 |
| Phân trang có fetch collection | 2 query (id trước, dữ liệu sau) hoặc DTO | Cắt trang trong bộ nhớ ở nhiều version | G5-S12 |
| Màn hình chỉ đọc | DTO projection | Đúng cột cần, không entity | G5-S15 |

### G6 — OneToOne và MapsId

| Tình huống | Nên làm | Vì sao | Mã |
|---|---|---|---|
| Một-một cha–phụ thuộc | FK ở bảng phụ thuộc + `@MapsId`, `find` theo id | 1 SELECT không join | G6-S06 |
| Phía `mappedBy` của `@OneToOne` | Đừng tin LAZY | Luôn tải ngay (cần bytecode enhancement mới lazy, G13-S11) | G6-S03 |

### G7 — ManyToMany

| Tình huống | Nên làm | Vì sao | Mã |
|---|---|---|---|
| Quan hệ nhiều-nhiều thuần | `Set` + `@JoinTable` tường minh, chọn owner là phía hay đổi liên kết | `List` bag xoá-rồi-chèn-lại | G7-S01..S05 |
| Cần cột phụ (số lượng, thời điểm) | Chuyển sang entity trung gian (join entity) | `@ManyToMany` không có chỗ cho cột phụ | G7-S10, S11 |
| `cascade` trên `@ManyToMany` | Không `REMOVE`/`ALL` | Xoá tag dùng chung | G7-S08 |

### G8 — FK nhiều cột

| Tình huống | Nên làm | Vì sao | Mã |
|---|---|---|---|
| Khoá ghép | `@EmbeddedId` hoặc `@IdClass` (DDL giống hệt); `equals/hashCode` theo giá trị | Id dùng làm khoá trong persistence context | G8-S01, S04 |
| Quan hệ tới khoá ghép | `@JoinColumns` đủ `referencedColumnName` | Thiếu thì lỗi khởi động | G8-S02, S03 |
| Đổi một phần id của entity MANAGED | Không | Bị bỏ qua im lặng, không UPDATE | G8-S07 |

### G9 — Tình huống tổng hợp

| Tình huống | Nên làm | Vì sao | Mã |
|---|---|---|---|
| Cập nhật đơn từ DTO | So sánh với entity đã tải (diff) thay vì `merge` đồ thị DTO | `merge` có thể ghi đè NULL; đếm SQL không bắt được | G9-S02 |
| Entity trong `Set` | `equals/hashCode` theo business key | Theo id thì đổi hash sau khi persist | G9-S03 |
| `@Modifying` bulk | `clearAutomatically` hoặc `em.clear()` sau | Persistence context lệch DB | G9-S05 |
| Xoá cha có nhiều con | Cân nhắc `@OnDelete(CASCADE)` ở FK (1 DELETE) nhưng nhớ Hibernate không biết con đã mất | 11 SQL vs 2 vs 3 SQL | G9-S06 |
| Ranh giới transaction | Service trả DTO; không để entity LAZY lọt ra controller | `LazyInitializationException` | G9-S07 |
| Hai người cùng sửa một đơn | `@Version` + báo lỗi cho người sau | Optimistic lock | G9-S08 |
| Xoá mềm | `@SoftDelete` nhưng nhớ native/JDBC vẫn thấy dòng đã xoá | Dòng vẫn nằm trong bảng | G9-S11 |

---

## 3. Bảng quyết định G10–G13

Bảng đầy đủ nằm trong README từng nhóm (mục "Quyết định cho lead"). Dưới đây là bản rút gọn để tra nhanh.

| Chủ đề | Quyết định | Mã |
|---|---|---|
| `join` vs `join fetch` | Cần con để dùng ngay → `join fetch`; chỉ lọc → `join` thường rồi tải con riêng | G10-S01, S03 |
| Điều kiện trên collection đã fetch | Không làm | G10-S02 |
| Native query | Chỉ khi JPQL không diễn đạt được; nhớ nó bỏ qua `@Version`/`@SoftDelete` và persistence context không biết DB đổi | G10-S04..S07 |
| `Page` + `join fetch` | Luôn `countQuery` riêng | G10-S08 |
| `readOnly = true` | Cho method chỉ đọc; ghi (kể cả `save(new)` id SEQUENCE) sẽ lỗi | G11-S01 |
| Propagation | Để lỗi đi lên; `REQUIRES_NEW` ở bean riêng cho audit; luôn `rollbackFor` với checked exception | G11-S02..S04 |
| Lost update | Ít tranh chấp → `@Version` + retry; tranh chấp cao → `UPDATE … set stock = stock - ?` có điều kiện hoặc `PESSIMISTIC_WRITE` | G11-S05, S06 |
| Hàng đợi việc | `PESSIMISTIC_WRITE` + `SKIP LOCKED` | G11-S09 |
| Khoá nhiều dòng | Khoá theo thứ tự id tăng dần | G11-S10 |
| OSIV | Tắt (`open-in-view: false`), controller trả DTO | G11-S12 |
| Kế thừa | `SINGLE_TABLE` mặc định; `JOINED` khi cần NOT NULL; hiếm khi `TABLE_PER_CLASS` | G12-S06..S09 |
| Tập giá trị nhỏ | `@ElementCollection` (`Set`/`@OrderColumn`); cần sửa/xoá từng phần tử → entity con | G12-S04, S05 |
| Enum | `STRING` cho dữ liệu sống lâu; không đổi thứ tự enum đang lưu `ORDINAL` | G12-S11 |
| Chiến lược id | `SEQUENCE` (allocationSize 50) nếu có insert hàng loạt; `IDENTITY` không bao giờ batch được | G13-S02, S03 |
| Batch | Bật theo session/job (`setJdbcBatchSize`) kèm `flush` + `clear` theo lô | G13-S01, S04 |
| Nhiều dòng | `flush`/`clear` theo lô; `StatelessSession` khi không cần dirty checking | G13-S04, S05 |
| Cache cấp 2 | Dữ liệu tham chiếu, ít đổi, chỉ ghi qua Hibernate; bulk/native update phải evict có chủ đích | G13-S06..S10 |
| Bytecode enhancement | Chỉ khi thật cần LAZY phía inverse `@OneToOne`/cột lớn; ảnh hưởng mọi entity nên đặt ở module riêng | G13-S11..S13 |

---

## 4. Từ triệu chứng ra nguyên nhân (G14)

Bốn sự cố tổng hợp, mỗi sự cố có đủ bốn bước: **tái hiện → chẩn đoán → sửa → rào chắn** (4 test mỗi sự cố ở
[g14_lead](../src/test/java/com/example/hibernatelab/g14_lead/)). Kỹ năng cần luyện là bước **chẩn đoán**: tìm con số hoặc câu SQL chứng minh nguyên nhân.

| Triệu chứng người dùng báo | Bằng chứng cần tìm | Nguyên nhân | Cách sửa | Rào chắn | Mã |
|---|---|---|---|---|---|
| "Trang danh sách đơn chậm dần" | Số SELECT vào bảng phụ **bằng đúng cỡ trang**; câu `select … from customer where id=?` lặp lại | N+1 hai tầng (customer + items) ở service đọc entity rồi duyệt | DTO projection + `countQuery` riêng: 2 SELECT | Ngân sách SELECT | G14-I1, R1 |
| "Tồn kho sai sau flash sale" | Hai `UPDATE` không có `where version`; kho + đã bán ≠ kho ban đầu | Lost update: đọc → kiểm tra → ghi không khoá | `@Version` + retry cả transaction | Bất biến kho trong test đua | G14-I2 |
| "Job gửi email hai lần" | Câu SELECT nhận việc không có `for update`/`skip locked`; một `job_id` có hai worker trong log | Hai worker cùng đọc "việc NEW đầu tiên" | `PESSIMISTIC_WRITE` + `SKIP LOCKED` | Bất biến "mỗi việc một lần" trong test đua | G14-I3 |
| "Import file lớn ngày càng chậm" | Số INSERT = số dòng; cỡ persistence context = số dòng | `IDENTITY` tắt batch + không `flush`/`clear` | `SEQUENCE` + batch size + `flush`/`clear` theo lô | Trần cỡ persistence context + lint IDENTITY | G14-I4, R2 |

Quy trình chẩn đoán chung: (1) đếm SQL trước, đoán sau; (2) tìm con số **tăng theo N** (dấu vân tay N+1/phình persistence context);
(3) với lỗi đồng thời, đối chiếu hai nguồn số liệu (kho vs sổ bán hàng); (4) sửa xong thì viết rào chắn **đỏ trên đường lỗi, xanh trên đường sửa**.

Lưu ý nhỏ rút ra khi làm G14-I2: triệu chứng "tồn kho âm" trong báo cáo thực tế **không phải lúc nào cũng âm**: ở lab, kho ghi 4 trong khi đã bán 12 từ kho 10.
Đừng chỉ tìm số âm; hãy kiểm tra bất biến.

---

## 5. Rào chắn nên đưa vào dự án

| Rào chắn | Cách làm | Mẫu trong lab |
|---|---|---|
| **Ngân sách SQL** cho từng use case quan trọng | `SqlBudget.assertSelectsAtMost(captor, "tên use case", N)`; đỏ kèm danh sách SELECT thừa | [SqlBudget](../src/test/java/com/example/hibernatelab/support/SqlBudget.java), G14-R1 |
| **Lint mapping** | `MappingLint.check(entities…)`: EAGER to-one, EAGER collection, cascade REMOVE trên `@ManyToOne`/`@ManyToMany`, `IDENTITY` | [MappingLint](../src/test/java/com/example/hibernatelab/support/MappingLint.java), G14-R2 |
| **Test đua** hai transaction cho mọi đường "đọc rồi ghi" quan trọng | `ConcurrentTx` + `Gate`, không `Thread.sleep`; kiểm tra bất biến nghiệp vụ | [ConcurrentTx](../src/test/java/com/example/hibernatelab/support/ConcurrentTx.java), G14-I2, I3 |
| **Rào chắn phải chứng minh cả hai chiều** | Mỗi rào chắn có test: bắt được ví dụ sai (assert `AssertionError`), và cho qua ví dụ đúng | G14-I1-S04, I2-S04, I3-S04, I4-S04, R2-S02 |

Nguyên tắc: một rào chắn **chưa từng đỏ** là rào chắn chưa được chứng minh. Quét lint chỉ trên model sạch; model có anti-pattern cố ý (như phần lớn lab này) phải nằm ngoài phạm vi quét.
