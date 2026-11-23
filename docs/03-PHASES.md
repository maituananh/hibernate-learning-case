# Các phase triển khai — Hibernate Mapping Lab

> Thiết kế: [01-DESIGN.md](01-DESIGN.md) · Tình huống: [02-SCENARIOS.md](02-SCENARIOS.md)
>
> **Thứ tự phase = thứ tự học.** Mỗi phase hoàn thành thì có một nhóm kiến thức trọn vẹn, đọc được ngay, không phải chờ cả project xong.
> **Mỗi phase làm ⭐ trước, ➕ sau.** Nếu thời gian có hạn thì chỉ làm ⭐ cũng đã đủ khung kiến thức.

## Tổng quan

| Phase | Nội dung | Số TH (⭐ / tổng) | Checkpoint với người học |
|---|---|---|---|
| 0 | Khởi tạo project, Postgres, smoke test | — | |
| 1 | Hạ tầng test + **1 test mẫu (golden test)** | 1 | ✅ **Duyệt định dạng test mẫu trước khi viết hàng loạt** |
| 2 | G0 — Vòng đời & Persistence Context | 9 / 14 | |
| 3 | G1 — Owning side `@ManyToOne` / `@OneToMany` | 13 / 18 | ✅ **Duyệt độ dễ đọc sau nhóm quan hệ đầu tiên** |
| 4 | G2 — `CascadeType` | 9 / 13 | |
| 5 | G3 — `orphanRemoval` | 10 / 11 | |
| 6 | G4 — Cập nhật con qua cha, không đổi cha | 8 / 13 | |
| 7 | G5 — `FetchType` & N+1 | 11 / 16 | |
| 8 | G6 — `@OneToOne` & `@MapsId` | 9 / 13 | |
| 9 | G7 — `@ManyToMany` & `@JoinTable` | 10 / 14 | |
| 10 | G8 — `@JoinColumns` & derived identity | 3 / 7 | |
| 11 | G9 — Tình huống tổng hợp (service layer) | 6 / 11 | |
| 12 | Tổng kết: RESULTS, CHEATSHEET, coverage | — | ✅ Bàn giao |

---

## Definition of Done áp dụng cho MỌI test tình huống

Một test chỉ được coi là xong khi đủ các điều sau:

- [ ] Có Javadoc gồm: mã tình huống, mapping đang dùng (ghi rõ phía owning/inverse), **câu hỏi**, **kết luận**, `Xác minh: Hibernate 7.x.y`.
- [ ] `@DisplayName("G?-S?? | …")` bằng tiếng Việt.
- [ ] Có các khối `GIVEN / WHEN / THEN: SQL / THEN: DB thật` theo đúng thứ tự (01-DESIGN §7.3).
- [ ] Mỗi bước đổi trạng thái có `// [LIFECYCLE] …` **và** assert trạng thái tương ứng.
- [ ] Mỗi điểm flush/commit có `// [FLUSH] …` liệt kê SQL dự kiến.
- [ ] Kết luận về dữ liệu được assert bằng **JDBC**, không dùng repository hay EntityManager.
- [ ] Không có `@Transactional` trên test.
- [ ] Chạy độc lập được (`./gradlew test --tests 'TênClass.tênMethod'`), không phụ thuộc test khác.
- [ ] **Rõ ràng (01-DESIGN §7.10):** GIVEN nhìn thấy ngay trong method (T1) · không `var`, không stream dài (T2) · tên biến mang nghĩa nghiệp vụ (T3) · mọi assert AssertJ có `.as("vì sao…")` (T4) · không `@ParameterizedTest` (T5) · có `db.print(...)` và `sql.print()` ở khối THEN (T7) · method ≤ ~40 dòng code (T8).
- [ ] **Entity rõ ràng (E1–E4):** không kế thừa giữa biến thể, thuộc tính quan trọng ghi tường minh (chỗ để mặc định có comment), comment OWNING/INVERSE cạnh mỗi quan hệ.
- [ ] Nếu gắn 🔬 thì đã chạy, viết assert theo **kết quả thực tế**, và ghi 1 dòng vào `docs/RESULTS.md`.
- [ ] Entity của biến thể có comment header `BIẾN THỂ / BASELINE / KHÁC BIỆT / HẬU QUẢ` (01-DESIGN §7.2).

## Definition of Done cho MỖI phase nhóm (Phase 2–11)

- [ ] Toàn bộ test ⭐ của nhóm đều xanh (`./gradlew test --tests 'G<x>*' --rerun`).
- [ ] Có `src/test/java/.../g<x>_*/README.md` gồm: bảng biến thể, bảng **"đặt bên A vs bên B"** (nếu nhóm có), bảng kết quả từng tình huống (một dòng: câu hỏi → kết luận), và thứ tự nên đọc các file test.
- [ ] Đoạn DDL của các schema trong nhóm đã được dán vào README (trích từ `build/ddl/create.sql`).
- [ ] Đã cập nhật `docs/RESULTS.md` cho mọi 🔬 của nhóm.
- [ ] Toàn bộ suite vẫn xanh, không phá nhóm trước.

---

## Phase 0 — Khởi tạo project

**Mục tiêu:** có một project build và test được, kết nối được PostgreSQL thật.

**Việc cần làm**
1. `git init` + `.gitignore` (Java/Gradle/IDE; nhớ ignore `build/` và `.gradle/`).
2. Kiểm tra bản Spring Boot GA mới nhất trên start.spring.io (hiện là 4.1.x; nếu 4.2.0 đã GA thì dùng 4.2.x). Sinh project qua Initializr (curl hoặc web):
   - **Gradle - Groovy**, Java 21, packaging jar, group/package `com.example.hibernatelab` (đề xuất). Giữ nguyên `gradlew` và `gradle/wrapper/` mà Initializr sinh ra.
   - Dependencies: **Spring Data JPA**, **PostgreSQL Driver**, **Testcontainers**, **Docker Compose Support** (tuỳ chọn).
   - Không thêm Web, không thêm Lombok.
3. `docker-compose.yml`: `postgres:<major pin>` với volume, user/password/db `hibernate_lab`, port 5432. Tuỳ chọn: `adminer` ở port 8081 để xem DB qua browser, vì máy không có `psql`.
4. `application.yml` (main): datasource trỏ tới compose, `spring.jpa.open-in-view=false`, `ddl-auto=create` (chỉ dùng khi chạy app tay để khám phá).
5. `src/test/resources/application-test.yml` hoặc cấu hình chung cho test:
   - `spring.jpa.hibernate.ddl-auto=create-drop`
   - `spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true`
   - `spring.jpa.properties.hibernate.format_sql=true`
   - xuất DDL: `spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create` và `…create-target=build/ddl/create.sql`
   - logging: `org.hibernate.SQL=DEBUG`, `org.hibernate.orm.jdbc.bind=TRACE`
   - **không** đặt `jdbc.batch_size`, `default_batch_fetch_size`
6. `application-inspect.yml`: trỏ tới Postgres của compose, `ddl-auto=create`, để giữ lại dữ liệu sau test.
7. `build.gradle` (Gradle chạy test trong JVM riêng và mặc định giấu log nên phải cấu hình, xem D15–D18):
   - `test { useJUnitPlatform() }` kèm `testLogging { showStandardStreams = true; events "passed", "failed", "skipped"; exceptionFormat = "full" }`.
   - Đọc property `-Pinspect` rồi gán `systemProperty 'spring.profiles.active', 'inspect'` cho task `test` (D16).
   - Đảm bảo AssertJ có sẵn (đi kèm `spring-boot-starter-test`). Không thêm thư viện test nào khác nếu chưa cần.
   - Ghi chú ngay trong file: *"chạy lại không đổi code: `./gradlew test --rerun`"* (D17).
8. Smoke test: context khởi động được, `select version()` trả về chuỗi chứa `PostgreSQL`.

**Output:** `build.gradle`, `settings.gradle`, `gradlew`, `gradle/wrapper/`, `docker-compose.yml`, `application*.yml`, `SmokeTest`.
**Done khi:**
- `./gradlew test` xanh, và **câu SQL của smoke test hiện ra trên terminal** (chứng minh D15 hoạt động).
- `docker compose up -d` cộng `./gradlew test -Pinspect --rerun` cũng xanh, và smoke test kết nối đúng Postgres của compose (chứng minh D16 hoạt động).

---

## Phase 1 — Hạ tầng test + golden test

**Mục tiêu:** có các helper để mọi test sau đó ngắn, đọc được, và đều assert trên DB thật.

**Việc cần làm** (package `support`)

| Thành phần | Trách nhiệm |
|---|---|
| `PostgresTestcontainer` | Container singleton với `@ServiceConnection`, pin image. Bị bỏ qua khi profile `inspect` đang bật |
| `AbstractScenarioTest` | `@SpringBootTest(webEnvironment = NONE)`. Inject `EntityManager` (shared), `TransactionTemplate`, `JdbcTemplate`. `@BeforeEach` dọn schema và reset `SqlCaptor`. Mỗi lớp con khai báo schema nó dùng |
| `inTx(...)` / `inTxReturning(...)` | Chạy lambda trong 1 transaction mới, trả về giá trị (thường là id) |
| `inNewConnection(...)` | Đọc DB bằng transaction `REQUIRES_NEW`, dùng để chứng minh flush ≠ commit |
| `SqlCaptor` | `StatementInspector` được đăng ký qua `hibernate.session_factory.statement_inspector`. Lưu danh sách SQL (thread-safe), có `reset()` và `mark("WHEN")` |
| `SqlAssert` | `assertInserts(table, n)`, `assertUpdates(table, n)`, `assertDeletes(table, n)`, `assertSelects(table, n)`, `assertTotal(n)`, `assertInOrder(...)`. Bỏ qua `nextval`. Có `sql.print()` in SQL đã chạy từ lúc `reset` (T7). Mọi assert đều có sẵn thông báo lỗi liệt kê SQL thực tế đã chạy |
| `DbReader` (đặt tên biến là `db`) | Đọc DB thật bằng JDBC: `rowsOf(table)`, `rowById(table, id)`, `countOf(table)`, `rowSnapshot(table, id)`. Có `db.print(table)` in bảng ASCII các dòng hiện có (T7). Tên bảng luôn kèm schema |
| `SchemaInspector` | Lấy từ `information_schema`/`pg_catalog`: bảng, cột (kiểu, nullable), PK, FK (cột, bảng đích, `ON DELETE`), unique. Có `printDdl(schema)` |
| `DbCleaner` | TRUNCATE … RESTART IDENTITY CASCADE mọi bảng của các schema chỉ định |
| `LifecycleInspector` + `assertState(em, entity, State)` | Trả về `TRANSIENT/MANAGED/DETACHED/REMOVED` (01-DESIGN §7.4). Javadoc nêu rõ đây là heuristic |
| `MappingBootstrap.tryBuild(Class<?>...)` | Bootstrap `SessionFactory` riêng cho các test `[BOOT-FAIL]`, trả về `Throwable` |
| `docs/CONVENTIONS.md` | Tóm tắt 01-DESIGN §7 thành checklist ngắn cho người viết test |

**Tự kiểm thử hạ tầng:** viết vài test nhỏ cho chính các helper (schema `support_selftest`, 1 entity). Ví dụ: `SqlAssert` đếm đúng, `LifecycleInspector` trả đúng 4 trạng thái, `DbCleaner` làm trống bảng.

**Golden test:** viết **G0-S01** thật hoàn chỉnh theo đầy đủ Definition of Done. Đây là mẫu chuẩn cho mọi test sau.

**✅ CHECKPOINT:** dừng lại, đưa người học đọc G0-S01 rồi chỉnh định dạng (độ dài comment, mức chi tiết lifecycle, cách in SQL) **trước khi** viết hàng loạt.

**Done khi:** test của hạ tầng xanh, golden test xanh, người học đã duyệt định dạng.

---

## Phase 2 — G0: Vòng đời & Persistence Context

- **Entity** (`g0_lifecycle`): `Customer`, `PurchaseOrder`, `IdentityCustomer`, `SkuProduct`, `VersionedSkuProduct`, cùng repository tương ứng.
- **Service:** `CustomerService` (readOnly, self-invocation) cho S12 và S14.
- **Test:** `g0_lifecycle/LifecycleTest.java` (hoặc tách `PersistFlushTest`, `MergeDetachTest`, `SpringDataSaveTest`).
- **Thứ tự:** S01 (đã có) → S02 → S03 → S04 → S05 → S06 → S08 → S09 → S11 → ➕ S07, S10, S12, S13, S14.
- **Lưu ý:** S13 có 🔬 vì hành vi merge đã đổi ở Hibernate 6.6+.

## Phase 3 — G1: Owning side `@ManyToOne` / `@OneToMany`

- **Entity:** 9 biến thể V1, V1-NN, V1-OPT, V2, V3, V3-NN, V4, V5, V6. Mỗi biến thể có `Customer`, `PurchaseOrder`, `OrderItem`, `Product` (V1 thêm `Address` cho S05).
- **Gợi ý gom test:** `G1ManyToOneUniTest` (V1*), `G1OneToManyUniTest` (V2, V3*), `G1BidirectionalTest` (V4, V5, V6). Mỗi file đặt các biến thể cạnh nhau để so sánh.
- **`[BOOT-FAIL]`:** S06 (cột bị map 2 lần), S17 (mappedBy sai).
- **Thứ tự:** S01 → S02 → S03 → S04 → S06 → S07 → S08 → S09 → S11 → S12 → S13 → S14 → S15 → ➕.
- **README nhóm:** bắt buộc có bảng "đặt bên A vs bên B" (đã phác trong 02-SCENARIOS).
- **✅ CHECKPOINT:** người học đọc nhóm G1 và góp ý trước khi làm tiếp.

## Phase 4 — G2: `CascadeType`

- **Entity:** C0…C8. Mỗi biến thể có `Customer`, `PurchaseOrder`, `OrderItem`, `Product`, đều dùng helper `addItem/removeItem`.
- **Test:** `G2CascadePersistTest` (S01–S03, S11), `G2CascadeMergeTest` (S04), `G2CascadeRemoveTest` (S05, S08, S10, S12, S13), `G2CascadeDetachRefreshTest` (S06, S07), `G2CascadeManyToOneTest` (S08, S09).
- **README:** ma trận "thao tác × CascadeType → có lan không", điền theo kết quả test.

## Phase 5 — G3: `orphanRemoval`

- **Entity:** O0, O1, O2. Mỗi biến thể có `PurchaseOrder`, `OrderItem` (unique `(order_id, product_code)`), `ItemDiscount`.
- **Test:** `G3OrphanRemovalTest`, và riêng `G3FlushOrderTest` cho S10.
- **Trọng tâm 🔬:** S04, S06, S09, S10, S11.
- **README:** bảng `CascadeType.REMOVE` vs `orphanRemoval`, kèm ghi chú thứ tự ActionQueue.

## Phase 6 — G4: Cập nhật con qua cha, không đổi cha

- **Entity:** U0…U5. `PurchaseOrder` có `@Version`, `@UpdateTimestamp updatedAt`, và `@PreUpdate` gắn counter để assert.
- **Helper thêm:** `rowSnapshot(schema.table, id)` trả về `Map<String,Object>` để so dòng cha trước và sau.
- **Test:** `G4UpdateChildViaParentTest`, `G4ParentVersionTest` (S04–S09), `G4DynamicUpdateTest` (S10), `G4ConcurrencyTest` (S11).

## Phase 7 — G5: `FetchType` & N+1

- **Entity:** F0, F1, F1S, F2, F3, F4. Mỗi biến thể có `Customer`, `PurchaseOrder`, `OrderItem`, `Product`, `Payment`.
- **Fixture dùng chung:** `G5Fixtures.seed(schema)` tạo 5 customer × 2 order × 2 item, 3 payment.
- **Repository:** method có `@EntityGraph`, có JPQL `join fetch`, và có DTO projection (S15).
- **Test:** `G5DefaultFetchTest`, `G5LazyTest`, `G5NPlusOneFixesTest`, `G5FetchPitfallsTest` (S10–S13).

## Phase 8 — G6: `@OneToOne` & `@MapsId`

- **Entity:** V1…V7. Mỗi biến thể có `Customer`, `LoyaltyCard`, `CustomerProfile` (V5, V6).
- **Test:** `G6FkPlacementTest` (S01–S05, S11), `G6MapsIdTest` (S06–S08, S13), `G6OrphanTest` (S09, S10).
- **README:** bảng "FK ở Customer vs FK ở LoyaltyCard" cùng quy tắc chọn.

## Phase 9 — G7: `@ManyToMany` & `@JoinTable`

- **Entity:** M1…M9. Gồm `Product`, `Tag`, `Warehouse`, `StockItem`, `StockItemId`.
- **Test:** `G7OwnerSideTest` (S01–S04, S07), `G7SetVsListTest` (S05, S06, S09), `G7CascadeTest` (S08), `G7JoinEntityTest` (S10–S12).
- **README:** bảng "owner Product vs owner Tag", và ghi chú khi nào nên bỏ `@ManyToMany` (S14).

## Phase 10 — G8: `@JoinColumns` & derived identity

- **Entity:** J1…J4. Gồm `Warehouse` + `WarehouseId`, `Shipment`, `ShipmentLine` + `ShipmentLineId`, `ShipmentLineNote`, và `Product`/`OrderItem` cho J4.
- **Test:** `G8CompositeFkTest`, `G8DerivedIdentityTest`.
- **`[BOOT-FAIL]`:** S03.

## Phase 11 — G9: Tình huống tổng hợp

- **Entity:** `Category` (tree) cùng bộ e-commerce đầy đủ. Các biến thể `OrderItem` cho `equals/hashCode` (S03).
- **Service:** `OrderUpdateService` (S02, cách A và B), `CustomerDeletionService` (S06), `OrderQueryService` (S07).
- **Test:** mỗi tình huống ⭐ một file riêng, vì các tình huống này dài.

## Phase 12 — Tổng kết

1. `docs/RESULTS.md`: kiểm tra mọi 🔬 đều đã có kết quả và version Hibernate.
2. `docs/CHEATSHEET.md`: một bảng cho mỗi annotation gồm **giá trị mặc định → đặt ở phía nào → ảnh hưởng DDL → ảnh hưởng SQL/runtime → bẫy hay gặp → mã tình huống**.
3. Đối chiếu bảng coverage cuối 02-SCENARIOS với test thật, bảo đảm mọi annotation trong yêu cầu đều có test xanh.
4. `README.md` ở gốc: cách chạy (`./gradlew test`, một nhóm `--tests 'G1*'`, một tình huống `--tests '*G1V4*s12*'`, chạy lại `--rerun`, profile `-Pinspect`, mở adminer), lộ trình đọc G0 → G9, link sang các README nhóm.
5. Chạy toàn bộ suite và ghi thời gian chạy cùng số test.

---

## Phụ lục — mở rộng sau (ngoài phạm vi hiện tại)

- `@ElementCollection` so với `@OneToMany` tới entity.
- `@Inheritance` (`SINGLE_TABLE` / `JOINED` / `TABLE_PER_CLASS`) cùng quan hệ trỏ tới lớp cha.
- `@Embeddable` value object (Address) và `@AttributeOverride`.
- Bytecode enhancement, để thử lại G6-S03: LAZY phía inverse `@OneToOne` có chạy được không.
- `@Formula`, `@SQLRestriction` (thay cho `@Where`), `@Filter`.
- Second-level cache cho collection.
