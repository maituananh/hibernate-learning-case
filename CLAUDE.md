# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Trạng thái hiện tại

Repo mới chỉ có **tài liệu kế hoạch** trong `docs/` (chưa có code, chưa có `build.gradle`). Khi bắt đầu implement, đọc theo thứ tự:

- [docs/00-PROMPT.md](docs/00-PROMPT.md): yêu cầu hoàn chỉnh, định dạng test bắt buộc, danh sách "Không được làm".
- [docs/01-DESIGN.md](docs/01-DESIGN.md): quyết định kỹ thuật D1–D18, cấu trúc project, quy ước (§7), rủi ro (§9).
- [docs/02-SCENARIOS.md](docs/02-SCENARIOS.md): ~130 tình huống theo nhóm G0–G9 (⭐ = ưu tiên, 🔬 = kết quả chưa chắc).
- [docs/03-PHASES.md](docs/03-PHASES.md): Phase 0–12, làm từng phase một, ⭐ trước ➕ sau.

Không tự mở sang phần "mở rộng sau" ở cuối 03-PHASES.

## Mục đích

Lab học **Hibernate mapping quan hệ** (người học đã biết Java/Spring, chưa vững `@OneToMany`, `@JoinColumn`, cascade, orphanRemoval, …). Mỗi tình huống mapping là **một test chạy trên PostgreSQL thật**, chứng minh được 4 thứ: DDL sinh ra, SQL đã chạy, dữ liệu thật trong DB, và trạng thái vòng đời entity (TRANSIENT/MANAGED/DETACHED/REMOVED).

Người học đọc code để hiểu → **comment và `@DisplayName` bằng tiếng Việt, giữ nguyên thuật ngữ tiếng Anh** (owning side, flush, proxy, persistence context).

## Stack và lệnh

Spring Boot (bản GA mới nhất, kiểm tra trên start.spring.io, không đoán) + Hibernate 7.x + PostgreSQL, Java 21, **Gradle wrapper** (Groovy DSL). Không Lombok, không Web/Controller, không Flyway/Liquibase, không H2.

```bash
./gradlew test                                   # chạy toàn bộ suite (Testcontainers Postgres)
./gradlew test --tests '*G1V4*'                  # một class
./gradlew test --tests '*G1V4*s03_*'             # một test method
./gradlew test --rerun                           # chạy lại khi code không đổi (Gradle coi test là up-to-date)
docker compose up -d && ./gradlew test -Pinspect --rerun   # chạy vào Postgres của compose, giữ dữ liệu để xem
```

Bẫy Gradle (D15–D18), phải cấu hình trong `build.gradle`:
- Gradle mặc định giấu stdout của test → bật `testLogging { showStandardStreams = true; … }`, nếu không sẽ không thấy SQL và `[LIFECYCLE]`.
- Test chạy trong JVM riêng nên `-Dspring.profiles.active` không truyền sang → dùng `-Pinspect` và gán `systemProperty` trong `build.gradle`.
- DDL của mọi biến thể được xuất ra `build/ddl/create.sql`.

## Kiến trúc

**Mỗi biến thể mapping = 1 package = 1 schema Postgres riêng** (ví dụ `g1_v4_bidi_mappedby`). Cả domain dùng chủ đề thương mại điện tử (`customer`, `purchase_order`, `order_item`, `product`; tránh tên `order`).

- Entity + repository nằm ở `src/main/java/com/example/hibernatelab/g<N>_<nhóm>/v<M>_<biến thể>/`.
- Test nằm ở `src/test/.../g<N>_<nhóm>/`, một class cho mỗi biến thể hoặc chủ đề (`G1V4...Test`), kèm `README.md` của nhóm (bảng biến thể, bảng A vs B, kết quả, DDL trích ra).
- Hạ tầng test dùng chung ở `src/test/.../support/` (`SqlCaptor`/`SqlAssert` qua Hibernate `StatementInspector`, `inTx(...)` qua `TransactionTemplate`, `assertState(...)`, `db.print(...)`).
- Entity `[BOOT-FAIL]` (cố ý sai) nằm ở `src/test/.../bootfail/` và **phải bị loại khỏi entity scan**.
- Service layer chỉ có ở nơi tình huống cần ranh giới transaction (nhóm G9).
- `@Entity(name = "G1V4_Customer")` **bắt buộc duy nhất** toàn persistence unit; JPQL viết theo tên này. Bật `hibernate.hbm2ddl.create_namespaces=true`.
- Mọi test class: `@SpringBootTest(webEnvironment = NONE)`, container Postgres singleton + `@ServiceConnection`, `@TestMethodOrder(MethodOrderer.MethodName.class)`, method đặt tên `s01_…`, `s02_…`.
- Dọn dữ liệu **trước** mỗi test (TRUNCATE … CASCADE), không dọn sau. ID mặc định `SEQUENCE`; không bật `jdbc.batch_size` hay `default_batch_fetch_size` toàn cục.

## Quy ước bắt buộc (tóm tắt, chi tiết ở 00-PROMPT và 01-DESIGN §7)

- **Rõ ràng đứng trên DRY.** Entity không kế thừa giữa các biến thể, không `@MappedSuperclass` dùng chung; copy-paste giữa biến thể là chấp nhận được. Ghi tường minh `fetch`, `cascade`, `@JoinColumn(name=…)`; chỗ cố ý để mặc định thì comment. Mỗi quan hệ có comment `OWNING side` / `INVERSE side`.
- **Quy tắc một khác biệt:** biến thể chỉ khác baseline của nhóm đúng 1 điểm; đầu class entity có header `BIẾN THỂ / BASELINE / KHÁC BIỆT / HẬU QUẢ`.
- **Không `@Transactional` trên test.** Mỗi bước dùng `inTx(...)` riêng để thấy rõ MANAGED → DETACHED, flush, commit.
- **Dữ liệu chỉ assert bằng `JdbcTemplate`**, không dùng repository/EntityManager (cache cấp 1 nói dối). Số SQL assert bằng `SqlCaptor` (bỏ qua `nextval`). Không assert giá trị id cụ thể.
- Mọi assert AssertJ kèm `.as("vì sao…")` giải thích lý do.
- Test: không `var`, không stream dài, không `@ParameterizedTest`; GIVEN nhìn thấy ngay trong method; mỗi test trả lời 1 câu hỏi và ≤ ~40 dòng; tên biến mang nghĩa nghiệp vụ (`orderA`).
- Khối comment theo thứ tự `// ── GIVEN ──`, `WHEN`, `THEN: SQL`, `THEN: DB thật`, `THEN: DDL`; kèm `// [LIFECYCLE] …` + `assertState(...)`, `// [FLUSH] …`, `// [TX END] …`. Javadoc ghi mã tình huống, owning/inverse, **Câu hỏi**, **Kết luận**, `Xác minh: Hibernate 7.x.y`.
- Dùng `@DisplayName("G1-S12 | …")` tiếng Việt.
- Hibernate 7 đã bỏ `Session.save()/update()/saveOrUpdate()`: không dùng. Không bật `enable_lazy_load_no_trans`.

## Tình huống 🔬 (kết quả chưa chắc)

Không viết assert theo trí nhớ. Chạy ở chế độ quan sát (in SQL + dữ liệu), viết assert theo **hành vi thực tế**, rồi ghi một dòng vào `docs/RESULTS.md` (mã tình huống | giả thuyết | thực tế | version Hibernate). Nếu thực tế khác 02-SCENARIOS thì báo lại người học, đừng âm thầm sửa.

## Quy trình

- **Trước khi bắt đầu phase mới:** chuyển sang nhánh `main` và pull code mới nhất (`git checkout main && git pull`). Luôn bắt đầu implement phase mới từ nhánh `main` (đã cập nhật), không tạo nhánh phase từ nhánh phase trước hay từ trạng thái HEAD detached.
- Làm từng phase theo 03-PHASES. **Dừng chờ người học duyệt** ở 2 checkpoint: (1) sau golden test G0-S01 (Phase 1), (2) sau khi xong G1 (Phase 3).
- Cuối mỗi phase: chạy toàn bộ suite, báo số test xanh/đỏ, liệt kê 🔬 khác giả thuyết, cập nhật README của nhóm.
- Bắt đầu triển khai từng phase của dự án theo thứ tự 03-PHASES. **Mỗi phase commit đúng 1 lần**, sau khi suite xanh và README nhóm đã cập nhật. Không gộp nhiều phase vào một commit, không commit giữa chừng khi phase chưa xong. Message theo dạng `feat(phase-N): <tên phase>` (hoặc `chore`/`docs` nếu phù hợp).
- **Sau khi commit xong thì push và tạo Pull Request** (`git push -u origin <nhánh>` rồi `gh pr create`). Description của PR viết tiếng Việt có dấu, mô tả **rõ ràng cho từng case (từng tình huống G<N>-S<NN> hoặc từng biến thể) trong phase**, mỗi case gồm:
  - **Mục đích:** tình huống này muốn chứng minh/trả lời câu hỏi gì.
  - **Cách giải quyết:** mapping/cấu hình nào được dùng và test kiểm chứng ra sao (SQL, dữ liệu thật, lifecycle).
  - Ngoài ra ghi kết quả suite (số test xanh/đỏ) và các tình huống 🔬 khác giả thuyết (nếu có). Kết thúc bằng dòng attribution theo system-reminder.

## Chú thích cho người mới

Người học là người mới với Hibernate mapping, nên mọi điểm quan trọng phải được **chú thích rõ ràng bằng tiếng Việt có dấu** (không viết không dấu, không viết tắt khó hiểu):

- Điểm dễ gây hiểu nhầm cần comment giải thích **vì sao**, không chỉ nói là gì: owning side và inverse side, nơi FK nằm, thời điểm flush, proxy và lazy loading, cascade, orphanRemoval, thứ tự SQL.
- Cấu hình dễ sai (testLogging, `-Pinspect`, `--rerun`, `create_namespaces`, loại `[BOOT-FAIL]` khỏi entity scan) phải có comment ngay tại chỗ cấu hình.
- README của nhóm và `docs/` cũng viết tiếng Việt có dấu, thuật ngữ kỹ thuật giữ nguyên tiếng Anh.
