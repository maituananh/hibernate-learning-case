# 04 — Thiết kế giai đoạn mở rộng (G10–G14)

> Trạng thái: **bản nháp chờ người học duyệt**. Chưa có code nào được viết theo tài liệu này.
> Khi được duyệt, tài liệu này là nguồn để lập kế hoạch chi tiết (danh sách tình huống trong 02-SCENARIOS, các phase trong 03-PHASES).

## 1. Mục đích (Why)

Lab G0–G9 (Phase 0–12, 249 test xanh) đã dạy **mapping và vòng đời entity**. Người học muốn đi tiếp vì hai lý do:

1. **Làm việc tốt hơn hằng ngày**: viết query, transaction, khoá và cấu hình cache mà không tạo ra lỗi khó thấy.
2. **Làm lead công nghệ**: chọn được chiến lược và giải thích được vì sao, review PR bắt được lỗi JPA, đặt quy tắc
   và rào chắn tự động cho team.

Vì vậy giai đoạn mở rộng có **hai lớp**:

- **Lớp kiến thức** (G10–G13): giống G0–G9, mỗi tình huống là một test chứng minh một điều trên PostgreSQL thật.
- **Lớp lead** (mục "Quyết định cho lead" trong README mỗi nhóm, và nhóm G14): biến kiến thức thành quyết định, checklist
  và rào chắn mang về dự án thật dùng được.

Hai câu hỏi khởi đầu giai đoạn này (đặt ra sau Phase 12) phải được trả lời ngay ở nhóm đầu tiên:
- `join` khác `join fetch` thế nào?
- Native SQL có làm vòng đời entity khác JPQL không?

## 2. Goals / Non-goals / Anti-goals

### Goals
- Thêm 5 nhóm **G10–G14** (khoảng 55–65 tình huống), làm theo đúng mọi quy ước của 01-DESIGN §7 (trừ chỗ ghi rõ ở §5).
- Gộp đủ **6 mục "mở rộng sau"** ở cuối 03-PHASES: `@ElementCollection`, `@Inheritance`, `@Embeddable` + `@AttributeOverride`,
  bytecode enhancement (thử lại G6-S03), `@Formula` / `@SQLRestriction` / `@Filter`, second-level cache cho collection.
- Gộp đủ **4 cụm** đã bàn: truy vấn và vòng đời, transaction và khoá, mô hình dữ liệu, hiệu năng và cache.
- README của mỗi nhóm **mới** (G10–G13) có thêm mục **"Quyết định cho lead"**: bảng "khi nào dùng gì", mỗi dòng trỏ về mã tình huống chứng minh.
  README của G0–G9 **giữ nguyên** (người học đã quyết); quyết định rút ra từ G0–G9 được gom thẳng vào `docs/LEAD-PLAYBOOK.md` (G14-D1).
- G14 tạo ra **sản phẩm dùng được ngoài lab**: test rào chắn mẫu, `docs/LEAD-PLAYBOOK.md` (checklist review PR có JPA,
  bảng quyết định), và 3–5 ADR mẫu trong `docs/adr/`.
- Làm xong tình huống G9-S10 (batch insert SEQUENCE vs IDENTITY) đã bỏ dở, trong nhóm G13.

### Non-goals
- Vẫn không có Web/Controller, Flyway/Liquibase, H2, Lombok.
- **Envers, multi-tenancy, Hibernate Reactive, Jakarta Data repository**: vẫn để ngoài.
- **Không đo thời gian chạy** (không benchmark). Hiệu năng được chứng minh bằng thứ đếm được: số câu SQL, số lần
  prepare statement, số entity trong persistence context, số lần cache hit/miss.
- Không dựng Open Session in View thật (cần Web). Chỉ giả lập cơ chế của nó (xem §5, E5).

### Anti-goals (vẫn là thất bại dù test xanh)
- Mọi anti-goal của 01-DESIGN §2 vẫn áp dụng.
- **Bật một cấu hình toàn cục làm đổi kết quả của 249 test cũ.** Mọi thay đổi cấu hình chung phải chứng minh suite cũ vẫn xanh
  và không đổi số SQL.
- **Test song song (G11) chạy chập chờn**: dùng `Thread.sleep` để "chờ cho chắc", hoặc kết quả phụ thuộc may rủi về thời điểm.
- **Quy tắc trong LEAD-PLAYBOOK không có test chứng minh.** Mỗi quy tắc phải trỏ về ít nhất một mã tình huống.
- **Tình huống G14 chỉ nhắc lại kiến thức cũ** mà không thêm kỹ năng lead (tìm nguyên nhân, chọn cách sửa, đặt rào chắn).

## 3. Ràng buộc và môi trường (đã kiểm tra trên repo)

| Ràng buộc | Hệ quả cho thiết kế |
|---|---|
| Spring Boot 4.2.0, spring-orm 7.1.0, Hibernate 7.4.15.Final, Java 21, Gradle Groovy DSL | Mọi 🔬 ghi version `Hibernate 7.4.15.Final` (hoặc version thật lúc chạy) |
| Classpath test **không có** `spring-web`, servlet API, thư viện cache nào | Open Session in View phải giả lập (E5); second-level cache cần thêm dependency (E3) |
| `Session`/`SharedSessionContract` của Hibernate 7.4.15 có `setJdbcBatchSize(Integer)` và `setFetchBatchSize(int)` (đã kiểm tra bằng `javap`) | Batch insert và batch fetch đặt được **theo từng session**, không cần phá D11 (E4) |
| `Session.byNaturalId(...)` có sẵn | Dùng cho `@NaturalId` ở G12 |
| D11: không bật `jdbc.batch_size` / `default_batch_fetch_size` toàn cục | Giữ nguyên. Cấu hình nào chỉ đặt được toàn cục (ví dụ `hibernate.order_inserts`) thì **không làm thành test**, chỉ ghi chú README |
| Một Spring context + một container Postgres cho cả suite (D2, D3) | Nhóm mới vẫn dùng chung context. Riêng bytecode enhancement phải nằm ngoài (E2) |
| `open-in-view: false` trong cả hai `application.yml` | Giữ nguyên; OSIV chỉ giả lập trong test |
| Dọn dữ liệu bằng TRUNCATE trước mỗi test (D14) | TRUNCATE **không** xoá second-level cache: phải evict cache trước mỗi test (E3) |

## 4. Các cách tiếp cận (Approaches)

| Cách | Mô tả | Được | Mất |
|---|---|---|---|
| A. Theo chủ đề | G10–G13 như G0–G9, README thêm "Quyết định cho lead" | Giữ quy tắc một khác biệt, dễ học | Kỹ năng lead (gỡ sự cố, đặt rào chắn) chỉ nằm trên giấy |
| B. Theo sự cố | Mỗi tình huống mở bằng triệu chứng production rồi lần ra nguyên nhân | Sát việc của lead nhất | Một sự cố dính nhiều annotation, phá quy tắc một khác biệt; khó học từng chủ đề |
| **C. Kết hợp ✅ (người học đã chọn)** | G10–G13 theo chủ đề như A, rồi **G14** gồm sự cố tổng hợp và bộ rào chắn cho team | Kiến thức học từng điểm một; kỹ năng lead có sản phẩm cụ thể | Thêm một nhóm; G14 được miễn quy tắc một khác biệt (giống G9) |

## 5. Quyết định kỹ thuật mới (E1–E8)

Đánh số `E` để không lẫn với D1–D18 của 01-DESIGN. Cột "Ai quyết" cho biết điểm nào do người học chọn, điểm nào là đề xuất
của người viết tài liệu (người học có thể sửa khi duyệt).

| # | Quyết định | Lý do | Ai quyết |
|---|---|---|---|
| E1 | Thứ tự nhóm: **G10 truy vấn → G11 transaction & khoá → G12 mô hình dữ liệu → G13 hiệu năng & cache → G14 lead** | G10 trả lời hai câu hỏi khởi đầu; G11 gặp nhiều nhất trong công việc; G13 cần hạ tầng mới nên để sau; G14 cần đủ kiến thức của các nhóm trước | Đề xuất |
| E2 | **Bytecode enhancement nằm trong Gradle subproject riêng** `lab-enhanced/` (plugin `org.hibernate.orm`, cùng version Hibernate). Hạ tầng test dùng chung (`support/`) được chuyển sang source set **`testFixtures`** của project gốc (plugin `java-test-fixtures`; package giữ nguyên nên không phải sửa import) để subproject dùng lại | Plugin enhancement áp lên **mọi** entity của source set, nếu bật ở project gốc sẽ đổi kết quả test cũ (ví dụ G6-S03). Subproject có context Spring và container riêng nên tách biệt hoàn toàn | Đề xuất |
| E3 | **Second-level cache nằm ở project gốc**: thêm `hibernate-jcache` + một provider JCache (Ehcache 3 hoặc Caffeine JCache, version do Spring Boot quản lý nếu có), `shared-cache-mode: ENABLE_SELECTIVE`, bật `use_second_level_cache` và `use_query_cache`. `AbstractScenarioTest` gọi `emf.getCache().evictAll()` trước mỗi test, cạnh TRUNCATE | Với `ENABLE_SELECTIVE`, chỉ entity có `@Cacheable`/`@Cache` mới vào cache, nên entity cũ không bị ảnh hưởng; query cache chỉ áp cho query tự đánh dấu cacheable. Bước đầu tiên của phase là chạy lại 249 test và so số SQL | Đề xuất |
| E4 | **Batch đặt theo từng session**: `session.setJdbcBatchSize(n)` / `setFetchBatchSize(n)` ngay trong test. Bằng chứng batching: Hibernate `Statistics` (`prepareStatementCount`, `entityInsertCount`) bật/tắt tại chỗ trong test, kết hợp `SqlCaptor`. Chỉ thêm `datasource-proxy` nếu cả hai cách không phân biệt được có batch hay không (🔬) | Giữ D11. `StatementInspector` có thể chỉ thấy câu SQL một lần cho cả batch, nên một mình nó chưa đủ chứng minh | Đề xuất |
| E5 | **Open Session in View giả lập** bằng chính cơ chế Spring dùng: `TransactionSynchronizationManager.bindResource(emf, new EntityManagerHolder(em))` trước khi gọi service, `unbindResource` sau. Ghi rõ trong Javadoc đây là giả lập | Không thêm `spring-web` (vi phạm "không Web"). Interceptor thật cũng chỉ bind/unbind như vậy | Đề xuất |
| E6 | **Hạ tầng chạy song song** cho G11: helper mới trong `support/` (ví dụ `ConcurrentTx`) chạy một `inTx` ở thread nền, đồng bộ bằng `CountDownLatch`, mọi chỗ chờ đều có timeout, không có `Thread.sleep`. Lock timeout đặt bằng hint `jakarta.persistence.lock.timeout` hoặc `SET LOCAL lock_timeout` | Test song song phải tất định. Postgres phát hiện deadlock sau `deadlock_timeout` (mặc định 1 giây), nên test deadlock tốn khoảng 1 giây; chấp nhận được | Đề xuất |
| E7 | **G14 được miễn quy tắc một khác biệt** (giống G9). Mỗi sự cố gồm đúng 4 test: *tái hiện* → *chẩn đoán* (in bằng chứng: SQL, Statistics, dữ liệu) → *sửa* → *rào chắn* (test sẽ đỏ nếu lỗi quay lại) | Đây là kỹ năng lead cần luyện: từ triệu chứng ra nguyên nhân, rồi chặn tái phát | Người học chọn cách C; cấu trúc 4 test là đề xuất |
| E8 | **Rào chắn mapping không cần thư viện mới**: một test đọc `Metamodel` của Hibernate và báo lỗi khi gặp quy tắc bị vi phạm (`@ManyToOne` EAGER, collection EAGER, `cascade REMOVE` trên `@ManyToOne`/`@ManyToMany`, …). Chạy trên model sạch của G14 và chứng minh bắt được một entity cố ý sai (dựng bằng `MappingBootstrap` như các `[BOOT-FAIL]`) | Lab cố ý chứa anti-pattern ở G0–G9, nên rào chắn không thể chạy trên toàn bộ entity. Không thêm ArchUnit để giữ dependency gọn | Đề xuất |

Mọi quy ước khác giữ nguyên: một biến thể = một package = một schema, `@Entity(name = "G10Q1_Customer")` duy nhất,
không `@Transactional` trên test, assert dữ liệu bằng `JdbcTemplate`, `.as("vì sao…")`, comment tiếng Việt có dấu,
🔬 → `docs/RESULTS.md`, test ≤ ~40 dòng, không `var`, không `@ParameterizedTest`.

## 6. Phạm vi từng nhóm

Ký hiệu như 02-SCENARIOS: ⭐ ưu tiên, ➕ làm sau, 🔬 kết quả chưa chắc (chạy chế độ quan sát rồi assert theo thực tế).
Cột "Giả thuyết" chỉ là điểm xuất phát để quan sát, **không** được dùng làm assert.
Mã tình huống ở đây là dự kiến; danh sách chính thức sẽ được viết vào 02-SCENARIOS khi lập kế hoạch.

### G10 — Truy vấn và persistence context

Model: `Customer` 1—N `PurchaseOrder` 1—N `OrderItem` N—1 `Product`. Biến thể chủ yếu là **cách viết query**, không phải mapping,
nên phần lớn dùng chung một bộ entity (baseline mọi quan hệ LAZY như G5-F1).

| Mã | Câu hỏi | Giả thuyết |
|---|---|---|
| G10-S01 ⭐ (golden) | `join` và `join fetch` khác nhau thế nào về SQL và trạng thái collection? | `join`: cột select chỉ của customer, `orders` chưa khởi tạo, đọc vào thì thêm SELECT; `join fetch`: lấy cả cột order, đã khởi tạo |
| G10-S02 ⭐🔬 | `join fetch c.orders o where o.status = 'PAID'`: collection chứa gì, và lần đọc sau trong cùng persistence context thấy gì? | Collection chỉ còn order PAID (thiếu dữ liệu), và bị giữ nguyên như vậy trong PC |
| G10-S03 ⭐ | `join` thường dùng để lọc, rồi tải collection riêng: cách viết đúng cho S02 | Collection đủ phần tử |
| G10-S04 ⭐🔬 | Native query có kiểu entity (`createNativeQuery(sql, Customer.class)`): entity trả về ở trạng thái nào? Có trùng instance đã có trong PC không? | MANAGED, cùng instance (identity map vẫn áp dụng), dirty checking chạy |
| G10-S05 ⭐ | Native query không có kiểu (`Object[]`/tuple): có gì trong PC không? | Không có entity nào, không vòng đời |
| G10-S06 ⭐🔬 | Native `UPDATE`: entity MANAGED có thấy giá trị mới không? `@Version` có tăng không? | Không thấy (lệch như bulk JPQL G9-S05); version không tăng |
| G10-S07 ➕🔬 | Native `SELECT` trên entity có `@SoftDelete` (G9-S11): có tự lọc dòng đã xoá không? | Không lọc |
| G10-S08 ⭐🔬 | Spring Data `Page` + `@Query` có `join fetch` collection: count query sinh ra thế nào, có lỗi không? Khi nào cần `countQuery` riêng? | Count query sinh tự động có thể lỗi hoặc sai; cần `countQuery` |
| G10-S09 ⭐ | Criteria API / `Specification` cho bộ lọc động: SQL thay đổi theo tham số, và `fetch` trong Specification gặp vấn đề gì với count query | Giống S08 |
| G10-S10 ➕ | DTO projection bằng JPQL `select new` so với native + `@SqlResultSetMapping`/tuple: kết quả không vào PC | 0 entity trong PC |

**Quyết định cho lead (dự kiến):** khi nào `join`, khi nào `join fetch`; khi nào được dùng native; quy tắc phân trang có fetch.

### G11 — Transaction và khoá

Model: `Product` (tồn kho `stock`), `PurchaseOrder`, và bảng `job` (hàng đợi việc). Service layer ở `src/main` vì cần ranh giới transaction.

| Mã | Câu hỏi | Giả thuyết |
|---|---|---|
| G11-S01 ⭐🔬 | `@Transactional(readOnly = true)` thật sự tắt gì: dirty checking, snapshot, flush? Gọi `save(new)` trong đó thì sao? | Không UPDATE (đã biết ở G0-S12); `save(new)` không INSERT, hoặc Postgres báo "read-only transaction" nếu flush |
| G11-S02 ⭐ | Service A (`REQUIRED`) gọi service B (`REQUIRED`) ném RuntimeException, A bắt exception rồi đi tiếp: commit được không? | `UnexpectedRollbackException`: transaction đã bị đánh dấu rollback-only |
| G11-S03 ⭐ | `REQUIRES_NEW`: B commit dù A rollback; entity A đã tải có phải là cùng instance trong B không? | B có persistence context riêng, instance khác |
| G11-S04 ⭐ | Checked exception có rollback không? `rollbackFor` thay đổi gì? | Mặc định **commit**; `rollbackFor` mới rollback |
| G11-S05 ⭐ | Lost update: hai transaction cùng đọc `stock = 10`, cùng trừ 1, không `@Version` | Kết quả 9 thay vì 8 |
| G11-S06 ⭐ | Cùng S05, ba cách sửa: `@Version` / `PESSIMISTIC_WRITE` / `UPDATE … set stock = stock - 1` | Mỗi cách cho 8, với cái giá khác nhau (retry, chờ khoá, bỏ qua PC) |
| G11-S07 ⭐🔬 | `PESSIMISTIC_WRITE` / `PESSIMISTIC_READ` sinh SQL gì trên Postgres? | `for update` / `for share` (có thể là `for no key update`) |
| G11-S08 ⭐🔬 | Khoá với timeout 0 (NOWAIT) khi dòng đang bị khoá: exception gì sau khi Spring dịch? | `PessimisticLockingFailureException` hoặc lớp con |
| G11-S09 ⭐🔬 | Hàng đợi job: hai worker dùng `SKIP LOCKED` lấy việc cùng lúc | Mỗi worker lấy job khác nhau, không trùng |
| G11-S10 ⭐🔬 | Deadlock: hai transaction khoá hai dòng theo thứ tự ngược nhau; cách sửa bằng khoá theo thứ tự id | Postgres huỷ một transaction; sửa xong không còn deadlock |
| G11-S11 ➕🔬 | `PESSIMISTIC_WRITE` + `left join fetch`: Postgres có từ chối `FOR UPDATE` trên phía nullable của outer join không? | Có lỗi |
| G11-S12 ➕🔬 | Open Session in View (giả lập E5): lazy loading sau khi service trả về chạy được, nhưng sinh SQL ngoài transaction | Không còn `LazyInitializationException` (so với G9-S07), N+1 bị che |

**Quyết định cho lead (dự kiến):** optimistic hay pessimistic; khi nào `REQUIRES_NEW`; quy tắc rollback; vì sao tắt OSIV.

### G12 — Mô hình dữ liệu nâng cao

Mỗi chủ đề có baseline riêng và biến thể khác đúng một điểm (01-DESIGN §7.2).

| Mã | Câu hỏi | Giả thuyết |
|---|---|---|
| G12-S01 ⭐ | `@Embeddable Address` dùng hai lần (billing, shipping) với `@AttributeOverride`: DDL ra sao? Thiếu override thì sao? | Thiếu override là lỗi trùng cột lúc khởi động |
| G12-S02 ⭐🔬 | Mọi cột của embeddable đều NULL: đọc lại được `null` hay object rỗng? | `null` |
| G12-S03 ⭐🔬 | Sửa field của embeddable tại chỗ có UPDATE không? (đối chiếu G8-S07, nơi sửa `@EmbeddedId` bị bỏ qua im lặng) | Có UPDATE: embeddable thường nằm trong dirty checking |
| G12-S04 ⭐ | `@ElementCollection` `List<String>` so với `@OneToMany` tới entity: bỏ 1 phần tử thì SQL gì? | ElementCollection kiểu bag: DELETE hết rồi INSERT lại (như G1-S08) |
| G12-S05 ⭐🔬 | `@ElementCollection` dùng `Set` hoặc `@OrderColumn`: còn recreate không? Bảng có PK không? | Set: DELETE đúng phần tử |
| G12-S06 ⭐ | `@Inheritance(SINGLE_TABLE)`: DDL (cột discriminator, cột của lớp con phải nullable), query đa hình | 1 bảng, 1 SELECT |
| G12-S07 ⭐ | `JOINED`: DDL và SQL của query đa hình | Join mọi bảng lớp con |
| G12-S08 ⭐🔬 | `TABLE_PER_CLASS`: query đa hình | `union all` |
| G12-S09 ⭐🔬 | `@ManyToOne` trỏ tới lớp cha trừu tượng: `find` cho proxy kiểu gì, `instanceof` lớp con đúng hay sai? | Proxy là lớp cha, `instanceof` lớp con sai |
| G12-S10 ⭐ | `@NaturalId` + `session.byNaturalId(...)`: SQL, và gọi lần hai trong cùng PC | Lần hai 0 SELECT |
| G12-S11 ⭐🔬 | `AttributeConverter` cho kiểu mutable: sửa tại chỗ có được phát hiện không? `@Enumerated(ORDINAL)` khi đổi thứ tự enum thì dữ liệu cũ ra sao? | Phụ thuộc `equals` của kiểu; ORDINAL đọc sai giá trị im lặng |
| G12-S12 ➕🔬 | `@Formula` (cột tính bằng subquery): khi nào được tính lại sau UPDATE? | Chỉ khi tải lại |
| G12-S13 ➕🔬 | `@SQLRestriction` trên entity và trên collection: `find` theo id có bị lọc không? Native có bị lọc không? | Có / không |
| G12-S14 ➕🔬 | `@Filter`: phải bật theo session; `find` theo id có áp filter không? | Không áp cho `find` |

**Quyết định cho lead (dự kiến):** chọn chiến lược `@Inheritance`; `@ElementCollection` hay entity con; `STRING` hay `ORDINAL`;
`@SQLRestriction` hay `@Filter` hay `@SoftDelete`.

### G13 — Hiệu năng và cache

Chia hai phần vì hạ tầng khác nhau: **G13a** ở project gốc (batch, persistence context, second-level cache),
**G13b** ở subproject `lab-enhanced/` (bytecode enhancement, E2).

| Mã | Câu hỏi | Giả thuyết |
|---|---|---|
| G13-S01 ⭐🔬 | Batch insert 100 entity, `SEQUENCE` + `setJdbcBatchSize(20)` | 5 batch; bằng chứng qua Statistics (E4) |
| G13-S02 ⭐🔬 | Như S01 nhưng `IDENTITY` (thay G9-S10) | Batching bị tắt, 100 lần INSERT |
| G13-S03 ⭐🔬 | `allocationSize = 1` so với `50`: số lần gọi `nextval` khi insert 100 entity | 100 so với khoảng 2 |
| G13-S04 ⭐ | Vòng lặp xử lý 2 000 dòng: persistence context lớn dần thế nào; `flush()` + `clear()` theo lô giải quyết ra sao | Đếm số entity trong PC (`SessionStatistics`), không đo bộ nhớ |
| G13-S05 ⭐🔬 | `StatelessSession`: không PC, không dirty checking, không cascade, không lazy loading | Mỗi lệnh là một SQL ngay; chạm quan hệ lazy thì lỗi |
| G13-S06 ⭐ | Entity cache: `find` ở transaction thứ hai | 0 SELECT (cache hit) |
| G13-S07 ⭐🔬 | Bulk JPQL / native `UPDATE` có làm hỏng cache không? | Bulk JPQL xoá cả region; native xoá toàn bộ cache nếu không khai query space |
| G13-S08 ⭐🔬 | Collection cache chỉ lưu id: phần tử không được cache thì sao? | N+1 SELECT phần tử |
| G13-S09 ⭐🔬 | Query cache khi entity không được cache | Query cache trả id rồi SELECT từng entity |
| G13-S10 ➕🔬 | `READ_ONLY` cache mà sửa entity | Exception |
| G13-S11 ⭐🔬 | (G13b) Bytecode enhancement: thử lại G6-S03, LAZY phía inverse của `@OneToOne` | LAZY chạy được |
| G13-S12 ⭐🔬 | (G13b) `@Basic(fetch = LAZY)` cho cột lớn: không enhancement thì bị bỏ qua, có enhancement thì hoãn | Đúng như vậy |
| G13-S13 ➕🔬 | (G13b) Dirty tracking của enhancement: UPDATE chỉ cột đổi hay mọi cột? | Chưa rõ |

Không làm thành test: `hibernate.order_inserts` / `order_updates` (chỉ đặt được toàn cục, sẽ đổi thứ tự SQL của G3-S10); chỉ ghi chú README.

**Quyết định cho lead (dự kiến):** chọn chiến lược id; khi nào bật batch và cách xử lý lô; cache cái gì, không cache cái gì;
có nên bật bytecode enhancement cho dự án không.

### G14 — Lead: sự cố tổng hợp và rào chắn

Mỗi sự cố có 4 test theo E7. **Mỗi sự cố có hai package, hai schema** (giống cách G0–G9 tách biến thể):

- `i<N>_buggy`: model và service **chứa lỗi** cần tái hiện (ví dụ I2 không có `@Version`, I4 dùng `IDENTITY`). Test *tái hiện*
  và *chẩn đoán* chạy trên package này.
- `i<N>_fixed`: model và service **đã sửa**, theo đúng các quy tắc rút ra từ G0–G13. Test *sửa* chạy trên package này.
- Test *rào chắn* phải chứng minh **cả hai chiều** ngay trong suite xanh: chạy kiểm tra trên đường lỗi thì bắt được
  (assert rằng kiểm tra ném `AssertionError`), chạy trên đường đã sửa thì qua. Giống cách R2 phải bắt được một entity cố ý sai.
- Rào chắn lint mapping (R2, E8) chỉ quét các package `*_fixed` của G14, cộng với entity cố ý sai của R2. Package `*_buggy` không bị quét
  vì lỗi nằm ở đó là có chủ đích.

| Mã | Sự cố (triệu chứng) | Nguyên nhân tái hiện | Kiến thức dùng |
|---|---|---|---|
| G14-I1 ⭐ | "Trang danh sách đơn hàng chậm dần theo dữ liệu" | N+1 + phân trang có fetch + count query | G5, G10 |
| G14-I2 ⭐ | "Tồn kho âm sau flash sale" | Lost update | G11 |
| G14-I3 ⭐ | "Job gửi email chạy hai lần" | Hai worker lấy cùng việc | G11 |
| G14-I4 ⭐ | "Import file lớn ngày càng chậm" | PC phình to, IDENTITY tắt batch | G13 |
| G14-I5 ➕ | "Admin sửa giá nhưng khách vẫn thấy giá cũ" | Native update + second-level cache | G10, G13 |

Rào chắn và tài liệu:

| Mã | Nội dung |
|---|---|
| G14-R1 ⭐ | Test **ngân sách SQL**: mỗi use case quan trọng có giới hạn số SELECT; vượt thì đỏ, kèm in SQL thừa |
| G14-R2 ⭐ | Test **lint mapping** bằng Metamodel (E8), và chứng minh nó bắt được một entity cố ý sai |
| G14-R3 ➕ | Đọc Hibernate `Statistics` để chẩn đoán (cách bật tại chỗ, các con số nào đáng xem) |
| G14-D1 ⭐ | `docs/LEAD-PLAYBOOK.md`: checklist review PR có JPA, bảng quyết định của **mọi nhóm G0–G13** (phần G0–G9 viết mới tại đây vì README cũ không sửa), mỗi dòng trỏ mã tình huống |
| G14-D2 ⭐ | `docs/adr/`: 3–5 ADR mẫu (ví dụ "Mọi quan hệ LAZY", "Cấm cascade REMOVE trên `@ManyToOne`", "Dùng optimistic lock mặc định", "Tắt OSIV"), mỗi ADR dẫn chứng bằng mã tình huống |

## 7. Phase và checkpoint

Tiếp nối số phase hiện có. Mỗi phase vẫn: bắt đầu từ `main` mới nhất, một commit, suite xanh, cập nhật README nhóm, mở PR.

**Trước Phase 13:** sau khi người học duyệt, tài liệu này được merge vào `main` bằng **một PR riêng** (`docs: thiết kế giai đoạn mở rộng`),
để Phase 13 bắt đầu từ `main` đã có thiết kế và vẫn giữ đúng một commit cho mỗi phase.

**Cách chứng minh "test cũ không đổi"** (Phase 16 và 17): suite xanh là chưa đủ, vì nhiều test cũ chỉ assert một phần SQL. Thêm hai kiểm tra
**dùng một lần** (không commit công cụ, kết quả ghi vào description của PR):

1. So `build/ddl/create.sql` trước và sau: phải giống hệt (trừ phần của nhóm mới).
2. Đếm số câu SQL mà mỗi test cũ in ra (lấy từ stdout trong `build/test-results/test/*.xml`) trước và sau: mọi test cũ phải có cùng số câu.

| Phase | Nội dung | Checkpoint |
|---|---|---|
| 13 | Cập nhật tài liệu (§8), viết danh sách tình huống G10–G14 vào 02-SCENARIOS, rồi làm **G10** | ⏸ **Dừng chờ duyệt sau golden test G10-S01** |
| 14 | **G11** + helper chạy song song (E6) | |
| 15 | **G12** | |
| 16 | **G13a**: dependency cache (E3), evict cache trong `AbstractScenarioTest`, batch, PC, StatelessSession, cache | Đầu phase: chạy lại 249 test cũ, số SQL không đổi |
| 17 | **G13b**: chuyển `support/` sang `testFixtures`, tạo `lab-enhanced/` (E2), tình huống enhancement | Đầu phase: suite cũ vẫn xanh sau khi chuyển `support/` |
| 18 | **G14** | ⏸ **Dừng chờ duyệt trước khi viết sự cố**: chốt danh sách sự cố với người học |
| 19 | Tổng kết: RESULTS, CHEATSHEET (thêm annotation mới), coverage, README gốc, thời gian chạy | |

Trong mỗi phase: ⭐ trước, ➕ sau. Nếu một ➕🔬 tốn quá nhiều công (ví dụ cần thêm thư viện), ghi "chưa làm" kèm lý do như G9-S10.

## 8. Tài liệu hiện có phải sửa (làm ở Phase 13)

- `01-DESIGN.md` §2 Non-goals: bỏ `@Inheritance`, `@ElementCollection`, `@Embeddable`, second-level cache khỏi danh sách "chưa đụng tới",
  trỏ sang tài liệu này. Thêm E1–E8 hoặc dẫn link sang §5.
- `03-PHASES.md`: thêm Phase 13–19; mục "Phụ lục — mở rộng sau" ghi rõ đã được gộp vào G12/G13.
- `CLAUDE.md`: câu "Không tự mở sang phần mở rộng sau" đổi thành: phần mở rộng đã được duyệt theo `04-EXTENSION-DESIGN.md`;
  thêm hai checkpoint mới (G10-S01, trước G14).
- `02-SCENARIOS.md`: thêm G10–G14 và mở rộng bảng coverage.

## 9. Rủi ro và cách xử lý

| Rủi ro | Cách xử lý |
|---|---|
| Thêm cache provider/cấu hình làm đổi kết quả test cũ | `ENABLE_SELECTIVE`; chạy lại toàn bộ suite và so số SQL ngay đầu Phase 16 |
| Cache còn dữ liệu từ test trước (TRUNCATE không xoá cache) | `evictAll()` trước mỗi test trong `AbstractScenarioTest` |
| Plugin enhancement lỡ áp vào project gốc | Plugin chỉ khai trong `lab-enhanced/build.gradle`; có một test ở project gốc khẳng định entity G6 **không** bị enhance (`PersistentAttributeInterceptable`) |
| Chuyển `support/` sang `testFixtures` làm vỡ test cũ | Làm thành bước riêng đầu Phase 17, chạy suite trước khi viết gì mới. Nếu vướng, phương án dự phòng: subproject có bản sao tối thiểu của `support/` (chấp nhận được theo "rõ ràng hơn DRY") |
| Test song song chập chờn hoặc treo | Latch có timeout, lock timeout tường minh, không `sleep`; chạy lại suite 3 lần liên tiếp trước khi commit Phase 14 |
| Không chứng minh được batching bằng `StatementInspector` | Dùng `Statistics`; nếu vẫn không đủ thì thêm `datasource-proxy` (testImplementation) và ghi vào RESULTS |
| Suite chậm đi nhiều (deadlock ~1 giây, batch 2 000 dòng) | Giữ số dòng nhỏ nhất đủ thấy hiện tượng; ghi thời gian chạy ở Phase 19 |
| Hành vi khác giả thuyết ở §6 | 🔬 → chạy quan sát → assert theo thực tế → RESULTS.md → báo người học, không âm thầm sửa |

## 10. Phần để người build tự quyết

- Tên class test, cách gom tình huống vào class (tiêu chí như 01-DESIGN §10: mở một file là thấy được so sánh).
- Field cụ thể của entity mới (3–6 field có nghĩa nghiệp vụ).
- Chọn Ehcache 3 hay Caffeine JCache làm provider (E3), miễn cấu hình được bằng `application.yml` và có comment.
- Tên và API cụ thể của helper chạy song song (E6) và helper ngân sách SQL (G14-R1).
- Số lượng dòng dữ liệu cho G13 (miễn đủ thấy hiện tượng).
- Thêm hoặc bớt tình huống ➕ trong một nhóm nếu thấy trùng với tình huống đã có, miễn ghi lại lý do.
