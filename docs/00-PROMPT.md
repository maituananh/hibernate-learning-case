# Prompt hoàn chỉnh — Hibernate Mapping Lab

> Đây là bản đã hoàn thiện từ yêu cầu ban đầu. Dùng để giao việc implement cho một phiên làm việc mới, có thể dán nguyên văn.
> Tài liệu chi tiết nằm cùng thư mục: [01-DESIGN.md](01-DESIGN.md), [02-SCENARIOS.md](02-SCENARIOS.md), [03-PHASES.md](03-PHASES.md).

---

## Vai trò
Bạn là kỹ sư Java/Spring có kinh nghiệm sâu về Hibernate ORM, đồng thời là người hướng dẫn. Bạn viết code để **dạy**: mỗi dòng phải giúp người học hiểu Hibernate đang làm gì với DB.

## Bối cảnh người học
- Đã biết Java và Spring cơ bản. **Chưa vững mapping quan hệ** và các thuộc tính của annotation mapping.
- Học bằng cách **đọc từng test method**. Mỗi test phải tự đủ nghĩa và đọc từ trên xuống là hiểu.
- Ngôn ngữ: **tiếng Việt**, giữ nguyên thuật ngữ tiếng Anh (owning side, flush, proxy, persistence context, …).

## Mục tiêu
Xây project Spring Boot (bản GA mới nhất) + Hibernate 7.x + PostgreSQL. Trong đó, **mỗi tình huống mapping là một test chạy trên DB thật**, và test chứng minh được:
1. **DDL** sinh ra: bảng, cột FK nằm ở đâu, bảng nối, constraint.
2. **SQL** đã chạy: số lượng và thứ tự INSERT/UPDATE/DELETE/SELECT.
3. **Dữ liệu thật** trong DB sau thao tác (đọc bằng JDBC).
4. **Trạng thái vòng đời** (TRANSIENT / MANAGED / DETACHED / REMOVED) của từng entity ở từng bước, có comment **và** có assert.

Mỗi câu hỏi được xét theo 3 hướng: **có** annotation hoặc thuộc tính, **không có** nó, và **đặt ở bảng A vs bảng B**.

## Phạm vi kỹ thuật
- Spring Boot: bản GA cao nhất trên start.spring.io lúc bắt đầu (kiểm tra lại, không đoán). Hibernate và Jakarta Persistence dùng version do Boot quản lý.
- Java 21, **Gradle** (dùng wrapper `./gradlew`, build script Groovy DSL). Không Lombok, không Controller/Web, không Flyway/Liquibase (schema do Hibernate sinh).
- Gradle mặc định **giấu log của test** và chạy test trong JVM riêng: phải bật `testLogging.showStandardStreams`, truyền profile bằng `-Pinspect` (không phải `-Dspring.profiles.active`), chạy lại bằng `--rerun`. Chi tiết ở 01-DESIGN D15–D18.
- Test: `@SpringBootTest(webEnvironment = NONE)` + Testcontainers PostgreSQL (singleton, `@ServiceConnection`). Thêm profile `inspect` để chạy vào Postgres của `docker-compose` và giữ dữ liệu lại xem.
- Spring Data repository cho từng biến thể. Service layer **chỉ khi** tình huống cần ranh giới transaction.
- Annotation phải được phủ: `@OneToMany`, `@ManyToOne`, `@OneToOne`, `@ManyToMany`, `@JoinColumn`, `@JoinColumns`, `@JoinTable`, `FetchType`, `CascadeType`, `orphanRemoval`, `@MapsId`, cùng các annotation đi kèm: `@EmbeddedId`, `@IdClass`, `@OnDelete`, `@Version`, `@OptimisticLock`, `@DynamicUpdate`, `@BatchSize`, `@Fetch`, `@EntityGraph`, `@OrderColumn`.

## Cách tổ chức (bắt buộc)
- **Mỗi biến thể mapping = 1 package = 1 schema Postgres riêng** (`g1_v4_bidi_mappedby`, …). Tên bảng để tự nhiên, `@Entity(name = "G1V4_Customer")` phải duy nhất, bật `hibernate.hbm2ddl.create_namespaces=true`.
- **Quy tắc một khác biệt:** biến thể chỉ khác baseline của nhóm đúng 1 điểm. Đầu class có comment `BIẾN THỂ / BASELINE / KHÁC BIỆT / HẬU QUẢ`.
- Tình huống gom thành **10 nhóm G0–G9** theo annotation (xem 02-SCENARIOS). Học và làm theo thứ tự G0 → G9.
- Mapping sai làm Hibernate không khởi động (`[BOOT-FAIL]`) thì đặt entity trong `src/test/.../bootfail`, **không** cho entity scan thấy, và test bằng một bootstrap `SessionFactory` riêng.

## Định dạng mỗi test (bắt buộc)
1. Javadoc: mã tình huống, mapping đang dùng (ghi rõ owning/inverse), **Câu hỏi**, **Kết luận**, `Xác minh: Hibernate 7.x.y`.
2. `@DisplayName("G1-S12 | …")` bằng tiếng Việt.
3. Các khối `// ── GIVEN ──`, `// ── WHEN ──`, `// ── THEN: SQL ──`, `// ── THEN: DB thật ──`, và `// ── THEN: DDL ──` nếu tình huống nói về cấu trúc bảng.
4. `// [LIFECYCLE] x: TRANSIENT → MANAGED (…)` ở mỗi bước đổi trạng thái, kèm `assertState(...)` ngay sau đó.
5. `// [FLUSH] …` ở mỗi điểm flush/commit, liệt kê SQL dự kiến. `// [TX END] …` khi persistence context đóng.
6. Kết luận dữ liệu **chỉ** assert bằng `JdbcTemplate`. Số SQL assert bằng `SqlCaptor`/`SqlAssert` (bỏ qua `nextval`).
7. **Không `@Transactional` trên test.** Mỗi bước dùng `inTx(...)` riêng.
8. Mỗi test chỉ trả lời 1 câu hỏi và chạy độc lập được.

## Nguyên tắc code rõ ràng (ưu tiên số 1, đứng trên cả DRY)
Project này để học: người đọc phải hiểu **chỉ bằng cách đọc code** và **tự kiểm chứng bằng test**. Khi phải chọn giữa "ngắn gọn / tái sử dụng" và "rõ ràng" thì **chọn rõ ràng, kể cả phải lặp code**. Đầy đủ ở 01-DESIGN §7.10, tóm tắt:

**Entity**
- Mỗi entity một file ngắn, **không kế thừa** giữa các biến thể, không `@MappedSuperclass` dùng chung. Copy-paste giữa biến thể là chấp nhận được.
- Ghi **tường minh** thuộc tính annotation quan trọng (`fetch`, `cascade`, `@JoinColumn(name=…)`). Chỗ cố ý để mặc định thì có comment `// cố ý KHÔNG ghi fetch → mặc định EAGER`.
- Cạnh mỗi quan hệ có comment `OWNING side` hoặc `INVERSE side`.

**Test**
- Dữ liệu GIVEN nhìn thấy ngay trong test method (inline, hoặc helper `private` cùng file có tên rõ nghĩa). `@BeforeEach` chỉ để dọn DB.
- Không `var`, không stream dài, không `@ParameterizedTest`. Tên biến mang nghĩa nghiệp vụ (`orderA`, không phải `o1`).
- Mọi assert dùng **AssertJ kèm `.as("vì sao…")`**: câu `as` giải thích **lý do**, vì đó là kết luận người học đọc được khi test xanh, và là bài học khi test đỏ.
- Mỗi khối THEN in bằng chứng ra console: `db.print("schema.table")` và `sql.print()`. Chạy `./gradlew test --tests '…'` là thấy dữ liệu và SQL, không cần mở DB.
- Test method ≤ ~40 dòng code. Dài hơn nghĩa là đang chứa nhiều hơn một câu hỏi.
- Comment giải thích **Hibernate đang làm gì và vì sao**, không lặp lại code.
- Helper trong `support` có tên đọc như câu văn, không reflection, không generics phức tạp.

## Với kết quả chưa chắc (🔬)
Không viết assert theo trí nhớ. Chạy test ở chế độ quan sát trước (in SQL + dữ liệu), sau đó **viết assert theo hành vi thực tế**, rồi ghi một dòng vào `docs/RESULTS.md`: mã tình huống | giả thuyết | thực tế | version Hibernate. Nếu thực tế khác giả thuyết trong 02-SCENARIOS thì **báo lại cho người học**, đừng âm thầm sửa.

## Quy trình làm việc
- Làm theo [03-PHASES.md](03-PHASES.md), mỗi lần một phase. Trong mỗi phase làm ⭐ trước rồi ➕.
- **Dừng ở các checkpoint:** (1) sau golden test G0-S01 ở Phase 1, để người học duyệt định dạng; (2) sau khi xong G1 ở Phase 3, để người học duyệt độ dễ đọc.
- Cuối mỗi phase: chạy toàn bộ suite, báo số test xanh/đỏ, liệt kê các 🔬 có kết quả khác giả thuyết, cập nhật README của nhóm.
- Không tự ý mở rộng sang phần "mở rộng sau" ở cuối 03-PHASES.

## Không được làm
- Assert dữ liệu bằng repository hoặc EntityManager (cache cấp 1 có thể "nói dối").
- Làm code "thông minh" thay vì rõ ràng: entity kế thừa nhau để đỡ lặp, `@ParameterizedTest` gộp biến thể, fixture giấu trong class cha, assert không có `.as(...)`.
- Gộp nhiều câu hỏi vào một test, hoặc làm biến thể khác baseline nhiều hơn 1 điểm.
- Dùng `Session.save()/update()/saveOrUpdate()` (Hibernate 7 đã bỏ).
- Bật `hibernate.enable_lazy_load_no_trans`, hoặc bật batching/batch fetch toàn cục.
- Dùng H2 thay cho PostgreSQL.
- Assert giá trị id cụ thể.

## Đầu ra cuối cùng
- Project chạy `./gradlew test` xanh, gồm khoảng 130 tình huống (≈ 88 ⭐).
- Mỗi nhóm có `README.md` gồm bảng biến thể, bảng "bên A vs bên B", bảng kết quả, và DDL trích ra.
- `docs/RESULTS.md` (kết quả 🔬), `docs/CHEATSHEET.md` (tra cứu annotation), `README.md` gốc (cách chạy, lộ trình học).
