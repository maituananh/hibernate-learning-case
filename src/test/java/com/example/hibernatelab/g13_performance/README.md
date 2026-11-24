# G13a — Hiệu năng và cache (project gốc)

**Câu hỏi trung tâm:** làm sao biết, bằng thứ **đếm được** (không bằng đồng hồ), một thao tác tốn kém; và cache giúp hay hại ở đâu?

Phase 16 làm phần **G13a** ở project gốc (batch, persistence context, `StatelessSession`, second-level cache).
Phần **G13b** (bytecode enhancement, S11–S13) nằm ở subproject `lab-enhanced/`, làm ở Phase 17.

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
