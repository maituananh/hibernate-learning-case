# G13a — Hiệu năng và cache (project gốc)

**Câu hỏi trung tâm:** làm sao biết, bằng thứ **đếm được** (không bằng đồng hồ), một thao tác tốn kém; và cache giúp hay hại ở đâu?

Phase 16 làm phần **G13a** ở project gốc (batch, persistence context, `StatelessSession`, second-level cache).
Phase 17 làm phần **G13b** (bytecode enhancement, S11–S13) ở subproject [`lab-enhanced/`](#g13b--bytecode-enhancement-subproject-lab-enhanced) (xem cuối file).

Entity nằm ở [src/main/.../g13_performance](../../../../../../main/java/com/example/hibernatelab/g13_performance/).
Mỗi biến thể là một package = một schema. Dữ liệu seed bằng JDBC (không qua Hibernate).

## Cách đo (và hai cái bẫy khi đo)

| Cần đo | Dụng cụ | Ghi chú |
|---|---|---|
| Số câu SQL | `SqlCaptor` / `sql.assert…` | **Không** đếm được số lô batch: các lô dùng lại một PreparedStatement nên inspector chỉ thấy 1 câu INSERT |
| Số lô batch JDBC | `BatchLogCapture` (nghe log TRACE `org.hibernate.orm.jdbc.batch`) | `Statistics` cũng không có bộ đếm lô; không cần thêm `datasource-proxy` |
| Số lần gọi `nextval` | `sql.sequenceCalls()` | `assertTotal`/`assertInserts` bỏ qua `nextval`, nên phải đếm riêng |
| Persistence context lớn cỡ nào | `session.getStatistics().getEntityCount()` | đếm entity, không đo bộ nhớ |
| Cache hit/miss/put | Hibernate `Statistics` (bật tại chỗ trong test) | `getSecondLevelCacheHitCount()`, `getQueryCacheHitCount()`… |

Batch size đặt **theo từng session** (`session.setJdbcBatchSize(20)`), không bật `hibernate.jdbc.batch_size` toàn cục vì sẽ làm lệch số SQL của test cũ.

## Hạ tầng cache (thêm ở Phase 16)

- Dependency: `hibernate-jcache` (version theo Hibernate) + `com.github.ben-manes.caffeine:jcache` (provider JCache, không cần file XML).
- `shared-cache-mode: ENABLE_SELECTIVE`: chỉ entity có `@Cacheable` vào cache; query chỉ vào query cache khi `setCacheable(true)`. Nhờ vậy test cũ không bị ảnh hưởng (đã so DDL và số SQL từng class trước/sau).
- `AbstractScenarioTest` gọi `evictAllRegions()` trước mỗi test: `TRUNCATE` chỉ xoá DB, không xoá cache.
- Mỗi Spring context có `CacheManager` riêng (xem `ScenarioTestConfiguration`): dùng chung thì context nào đóng trước sẽ đóng cache của context còn lại.

## Biến thể

| Mã | Schema | Nội dung | Khác baseline | Test |
|---|---|---|---|---|
| B1 | `g13_b1_seq_batch` | `Item`, id `SEQUENCE`, `allocationSize = 50` | **baseline** | `G13BatchTest` S01, S03a |
| B2 | `g13_b2_identity` | như B1 | id `IDENTITY` | S02 |
| B3 | `g13_b3_seq_alloc1` | như B1 | `allocationSize = 1` | S03b |
| P1 | `g13_p1_pc_stateless` | `Customer` 1—N `PurchaseOrder`, LAZY, `cascade = PERSIST` | **baseline** | `G13PersistenceContextTest` S04, S05 |
| K1 | `g13_k1_entity_cache` | `Product`, `Brand` đều `@Cacheable` READ_WRITE | **baseline** | `G13SecondLevelCacheTest` S06, S07, S09b |
| K2 | `g13_k2_collection_cache` | `Category` (entity + collection `entries` cache), `Entry` không cache | thêm collection cache | S08 |
| K3 | `g13_k3_query_cache` | `Tag` **không** `@Cacheable` | thiếu `@Cacheable` | S09a, S09c |
| K4 | `g13_k4_read_only` | `Rate` cache `READ_ONLY` | READ_ONLY thay READ_WRITE | S10 |

## Năm điều phải nhớ

1. **Batch cần hai thứ:** id không phải `IDENTITY` *và* batch size được bật. `SEQUENCE` mà quên batch size vẫn 100 INSERT riêng (S01b); `IDENTITY` thì batch size bị bỏ qua và INSERT chạy ngay tại `persist` (S02).
2. **`allocationSize = 50` giảm round-trip lấy id:** 100 entity chỉ cần 2 lần `nextval`, `allocationSize = 1` cần 100 (S03).
3. **Vòng lặp xử lý nhiều dòng phải `flush()` rồi `clear()` theo lô.** Không clear thì persistence context giữ cả 2 000 entity; clear mà quên flush thì mất cập nhật (S04). `StatelessSession` nhẹ hơn nhưng không dirty checking, không cascade, không lazy loading (S05).
4. **Cache cấp 2 chỉ làm mới khi thay đổi đi QUA Hibernate.** Sửa DB bằng SQL/JDBC tay thì cache entity và query cache trả dữ liệu cũ (S06b, S09c). Bulk JPQL tự evict đúng vùng; native phải khai `addSynchronizedEntityClass`, nếu không xoá hết cache (S07).
5. **Cache collection chỉ giữ id.** Phần tử không được cache thì đọc dữ liệu vẫn N lần SELECT (S08). Query cache ở Hibernate 7.4 trả được entity đủ dữ liệu mà không SELECT (S09a, khác giả thuyết ban đầu). `READ_ONLY` mà sửa entity thì lỗi lúc commit (S10).

## Bảng kết quả

| Tình huống | Kết quả thực tế | Ghi chú |
|---|---|---|
| S01a `SEQUENCE` + batch 20, 100 entity | đúng 5 lô × 20 dòng; inspector chỉ thấy 1 INSERT | đếm bằng `BatchLogCapture` |
| S01b không đặt batch size | 100 INSERT | |
| S02 `IDENTITY` + batch 20 | 100 INSERT, câu đầu chạy ngay tại `persist` | |
| S03a `allocationSize = 50` | 2 lần `nextval` | |
| S03b `allocationSize = 1` | 100 lần `nextval` | |
| S04a không `clear()` | PC giữ 2 000 entity; 2 000 UPDATE | |
| S04b `flush()` + `clear()` mỗi 100 | PC tối đa 100 entity; 2 000 UPDATE, DB giống hệt S04a | |
| S05a `StatelessSession` | `insert` chạy ngay; sửa field không UPDATE; `update()` tường minh mới UPDATE | |
| S05b không cascade | chỉ customer được lưu, 2 order con không | không báo lỗi |
| S05c không lazy | `LazyInitializationException … no session`, 0 SELECT | |
| S06 entity cache | transaction 2: 0 SELECT, 1 cache hit | |
| S06b sửa DB ngoài Hibernate | `find` vẫn trả giá cũ, 0 SELECT | nền của sự cố G14-I5 |
| S07a bulk JPQL `UPDATE` | product SELECT lại, brand 0 SELECT | evict đúng vùng |
| S07b native không khai query space | cả product và brand SELECT lại | xoá toàn bộ cache |
| S07c native + `addSynchronizedEntityClass` | product SELECT lại, brand 0 SELECT | |
| S08 collection cache | mở collection 0 SELECT; đọc 3 phần tử = 3 SELECT theo id | N+1 vẫn còn |
| S09a query cache, entity không cache | 0 SELECT, entity đủ dữ liệu | **khác giả thuyết** |
| S09b query cache, entity có cache | 0 SELECT | |
| S09c sửa DB ngoài Hibernate | query cache trả dữ liệu cũ | |
| S10 `READ_ONLY` mà sửa | UPDATE gửi đi, lúc commit `UnsupportedOperationException: Can't update read-only object`, rollback | |

## Quyết định cho lead

- **Chiến lược id:** dùng `SEQUENCE` với `allocationSize` đủ lớn (mặc định 50) nếu có insert hàng loạt; `IDENTITY` chấp nhận được cho bảng ít ghi nhưng không bao giờ batch được (S02, S03).
- **Khi nào bật batch:** chỉ khi thật sự insert/update nhiều dòng (import, job). Bật theo session/job (`setJdbcBatchSize`) thay vì toàn cục; luôn đi kèm `flush()` + `clear()` theo lô (S01, S04).
- **Xử lý rất nhiều dòng:** `flush` + `clear` theo lô là đủ cho đa số trường hợp; `StatelessSession` khi không cần dirty checking/cascade/lazy (S04, S05).
- **Cache cái gì:** dữ liệu tham chiếu, ít đổi, **chỉ ghi qua Hibernate** (S06). Bảng bị script/hệ thống khác sửa thì đừng cache, hoặc chấp nhận dữ liệu cũ (S06b, S09c). Bulk/native update phải đi kèm evict có chủ đích (S07). Muốn hết N+1 qua cache collection thì phải cache cả phần tử (S08).
- **`hibernate.order_inserts` / `order_updates`:** không làm thành test vì chỉ đặt được toàn cục (sẽ đổi thứ tự SQL của G3-S10). Chỉ ghi chú: bật khi dùng batch với entity có quan hệ để các INSERT cùng bảng được gom liền nhau.

---

# G13b — Bytecode enhancement (subproject `lab-enhanced/`)

**Câu hỏi trung tâm:** những thứ mà Hibernate "không làm được" ở G0–G12 (LAZY phía inverse của `@OneToOne`, `@Basic(fetch = LAZY)`) có làm được nếu sửa bytecode của entity không?

Test: [lab-enhanced/.../G13BytecodeEnhancementTest](../../../../../../../../lab-enhanced/src/test/java/com/example/hibernatelab/g13_enhanced/G13BytecodeEnhancementTest.java).
Entity (đã enhance): [lab-enhanced/src/main/.../g13_enhanced](../../../../../../../../lab-enhanced/src/main/java/com/example/hibernatelab/g13_enhanced/).

## Vì sao phải có subproject riêng

Plugin Gradle `org.hibernate.orm` sửa file `.class` của **mọi** entity trong source set `main` của project nó được khai.
Bật ở project gốc sẽ đổi kết quả test cũ (ví dụ G6-S03a). Nên:

| Thứ | Ở đâu | Ghi chú |
|---|---|---|
| Plugin `org.hibernate.orm` | chỉ `lab-enhanced/build.gradle` | cùng version Hibernate với Spring Boot (7.4.15.Final) |
| Entity **đã enhance** (E1, E2, E3) | `lab-enhanced/src/main` | plugin chỉ áp cho source set `main` |
| Entity **đối chứng** không enhance (C2, C3) | `lab-enhanced/src/test` | so "có" và "không" enhancement ngay trong một class test |
| Hạ tầng test (`SqlCaptor`, `DbReader`, Postgres container, …) | `src/testFixtures` của project gốc | `java-test-fixtures`; package `support` giữ nguyên nên test cũ không đổi import |
| Guard "project gốc không bị enhance" | `G13EnhancementGuardTest` (project gốc) | đỏ nếu ai lỡ bật plugin ở gốc |

`./gradlew test` chạy test của cả hai project; `./gradlew :lab-enhanced:test` chỉ chạy subproject.
Có enhancement hay chưa kiểm chứng bằng chính file `.class`: entity phải `implements PersistentAttributeInterceptable` (S11c).

## Biến thể

| Mã | Schema | Nội dung | Khác baseline | Test |
|---|---|---|---|---|
| E1 | `g13_e1_inverse_one_to_one` | `Customer` ⟷ `LoyaltyCard`, inverse `@OneToOne(mappedBy, LAZY)` | baseline = G6-V3, **thêm enhancement** | S11a, S11b, S11c |
| E2 | `g13_e2_lazy_basic` | `Document.body` `@Basic(fetch = LAZY)` | **thêm enhancement** | S12a |
| C2 | `g13_c2_plain_basic` | như E2 | **không** enhancement (đối chứng) | S12b |
| E3 | `g13_e3_dirty_tracking` | `Product` (name, price, description) | **thêm enhancement** (dirty tracking) | S13a, S13c |
| C3 | `g13_c3_plain_dirty` | như E3 | **không** enhancement (đối chứng) | S13b |

## Bảng kết quả

| Tình huống | Kết quả thực tế | Ghi chú |
|---|---|---|
| S11a E1 `find(customer)` | 1 SELECT (chỉ customer); field `loyaltyCard` chưa tải; `getLoyaltyCard()` mới SELECT thêm | **LAZY phía inverse chạy được** (G6-S03a thì không) |
| S11b customer không có thẻ | `find` 1 SELECT; `getLoyaltyCard()` SELECT thêm rồi trả `null` | biết "có thẻ hay không" vẫn tốn một SELECT |
| S11c class có bị enhance không | E1 implements `PersistentAttributeInterceptable`; C2, C3 thì không; `getClass()` vẫn là chính entity | không sinh class proxy phụ |
| S11d (guard, project gốc) | `V3Customer` không implements `PersistentAttributeInterceptable`/`SelfDirtinessTracker` | |
| S12a E2 có enhancement | `find` chỉ SELECT `id, title`; `getBody()` SELECT riêng cột `body` | |
| S12b C2 không enhancement | `body` nằm ngay trong SELECT đầu | `@Basic(fetch = LAZY)` bị bỏ qua, không báo gì |
| S13a E3 sửa `price` | UPDATE ghi cả 3 cột | dirty tracking **không** thu hẹp câu UPDATE |
| S13b C3 sửa `price` | UPDATE giống hệt S13a | từ SQL không phân biệt được hai cách |
| S13c E3 chỉ đọc | không có UPDATE | |

## Ba điều phải nhớ

1. **LAZY là chỉ dẫn, không phải mệnh lệnh.** `@OneToOne(mappedBy …, LAZY)` phía inverse và `@Basic(fetch = LAZY)` đều bị Hibernate lẳng lặng bỏ qua nếu không có bytecode enhancement. Thấy annotation LAZY trong code không có nghĩa là nó đang lazy (S11a, S12a so với S12b).
2. **Enhancement không phải viên đạn bạc.** Nó đổi cách entity được tải và theo dõi thay đổi, nhưng không đổi câu UPDATE (S13): muốn UPDATE chỉ chứa cột đổi vẫn cần `@DynamicUpdate`. Với `@OneToOne` inverse, "không có thẻ" vẫn tốn một SELECT để biết (S11b).
3. **Cái giá phải trả:** thêm bước build (plugin), bytecode chạy khác source bạn đọc, và mọi entity trong source set bị áp cùng cấu hình. Vì vậy lab tách subproject; trong dự án thật hãy bật khi đã đo được vấn đề (cột lớn, `@OneToOne` inverse hay gây N+1), không bật mặc định.

## Quyết định cho lead

| Tình huống | Nên | Vì sao |
|---|---|---|
| `@OneToOne` inverse hay gây N+1 (G6-S03b) | Ưu tiên đổi thiết kế: lật FK, hoặc `@MapsId` (G6-S08); enhancement chỉ khi không đổi được schema | S11a: enhancement làm được, nhưng thêm bước build và vẫn tốn SELECT khi chạm vào (S11b) |
| Bảng có cột rất lớn (văn bản dài, JSON, blob) mà hầu hết truy vấn không cần | Tách cột ra entity/bảng riêng, hoặc projection (G10); dùng `@Basic(fetch = LAZY)` chỉ khi đã bật enhancement | S12b: không enhancement thì annotation vô tác dụng |
| Muốn UPDATE chỉ ghi cột đổi | `@DynamicUpdate` (G4-U4), không trông chờ dirty tracking | S13a |
| Muốn giảm chi phí dirty checking khi persistence context lớn | `flush` + `clear` theo lô (S04) hoặc `StatelessSession` (S05) trước khi nghĩ tới enhancement | rẻ hơn và không đổi build |
