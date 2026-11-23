# Checklist viết test (tóm tắt 01-DESIGN §7)

Dùng khi viết một tình huống mới. Mẫu chuẩn là test **G0-S01** trong
[PersistFlushTest](../src/test/java/com/example/hibernatelab/g0_lifecycle/PersistFlushTest.java).

## Khung test

- [ ] Class `extends AbstractScenarioTest`, gắn `@TestMethodOrder(MethodOrderer.MethodName.class)`, khai báo `schemasUnderTest()`.
- [ ] **Không** `@Transactional`, **không** thêm `@Import`/`@ActiveProfiles` riêng (làm vậy là tạo thêm một Spring context và một container Postgres).
- [ ] Method tên `s01_…`, `s02_…`; `@DisplayName("G1-S12 | …")` bằng tiếng Việt.
- [ ] Javadoc: mã tình huống, mapping (ghi rõ owning/inverse), **Câu hỏi**, **Kết luận**, `Xác minh: Hibernate 7.x.y`.
- [ ] Một test = một câu hỏi, ≤ ~40 dòng code. GIVEN nhìn thấy ngay trong method (hoặc helper `private` cùng file).
- [ ] Không `var`, không stream dài, không `@ParameterizedTest`; tên biến mang nghĩa nghiệp vụ (`orderA`).

## Thứ tự các khối comment

```
// ── GIVEN ──            dữ liệu ban đầu
// ── WHEN ──             thao tác cần học (gọi sql.reset() ngay trước)
// ── THEN: SQL ──        sql.print(); sql.assertInserts(...)
// ── THEN: DB thật ──    db.print(...); assertThat(...).as("vì sao…")
// ── THEN: DDL ──        chỉ khi tình huống nói về cấu trúc bảng: schema.printDdl(...)
```

Kèm theo ở các bước thích hợp:

```
// [LIFECYCLE] order: TRANSIENT → MANAGED (id đã có từ sequence, CHƯA có INSERT)
assertState(em, order, MANAGED);
// [FLUSH] INSERT purchase_order, …
// [TX END] persistence context đóng → customer, order: MANAGED → DETACHED
```

## Helper có sẵn (package `support`)

| Cần làm | Dùng |
|---|---|
| Chạy trong 1 transaction mới | `inTx(() -> …)` / `inTxReturning(() -> …)` (trả về entity hoặc id) |
| Đọc DB như một người khác (chỉ thấy phần đã COMMIT) | `inNewConnection(() -> db.countOf(…))` |
| Đếm SQL | `sql.reset()`, `sql.mark("WHEN")`, `sql.assertInserts/Updates/Deletes/Selects(table, n)`, `sql.assertTotal(n)`, `sql.assertInOrder(…)`, `sql.print()` |
| Đọc dữ liệu thật | `db.rowsOf`, `db.rowById`, `db.countOf`, `db.rowSnapshot`, `db.print("schema.table")` |
| Kiểm tra cấu trúc bảng | `schema.columnsOf/primaryKeyOf/foreignKeysOf/uniqueConstraintsOf`, `schema.printDdl("schema")` |
| Trạng thái vòng đời | `assertState(em, entity, TRANSIENT/MANAGED/DETACHED/REMOVED)` |
| Mapping sai khiến Hibernate không khởi động | `MappingBootstrap.tryBuild(Class<?>...)` trả về `Throwable` |

## Assert

- [ ] Dữ liệu chỉ assert bằng `db` (JDBC). **Không** dùng repository/`EntityManager` để kiểm tra kết quả (cache cấp 1 nói dối).
- [ ] Mọi assert AssertJ kèm `.as("vì sao…")`: câu `as` giải thích **lý do**, không chỉ mô tả giá trị.
- [ ] Không assert giá trị id cụ thể (sequence không reset); dùng id trả về từ bước GIVEN.
- [ ] `assertState` chỉ gọi **bên trong** `inTx(...)`. Ngoài transaction, entity có id chỉ có thể là DETACHED
      (hợp lệ để kiểm tra "sau khi transaction kết thúc").
- [ ] Gọi `em.flush()` trước `em.detach()` nếu entity còn INSERT chờ chạy, nếu không commit sẽ hỏng.

## Entity mới

- [ ] `@Entity(name = "G1V4_Customer")` duy nhất toàn persistence unit; `@Table(schema = "g1_v4_…", name = "customer")`.
- [ ] Sequence nằm trong schema của biến thể: `@SequenceGenerator(name = …, schema = "g1_v4_…", sequenceName = "customer_seq", allocationSize = 50)`.
      Thiếu `schema = …` thì Hibernate tạo sequence ở schema `public`.
- [ ] **Thêm package entity vào `@EntityScan` của
      [ScenarioTestConfiguration](../src/test/java/com/example/hibernatelab/support/ScenarioTestConfiguration.java)**.
      Quên bước này thì `DbCleaner` báo "Schema … không có bảng nào". Không bao giờ thêm package `bootfail`.
- [ ] Entity `[BOOT-FAIL]` đặt ở `src/test/.../bootfail/<tình huống>/`, chỉ nạp qua `MappingBootstrap`.
