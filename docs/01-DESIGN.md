# Hibernate Mapping Lab — Thiết kế

> Tài liệu thiết kế cho project học **mapping quan hệ trong Hibernate/JPA**.
> Đọc kèm: [02-SCENARIOS.md](02-SCENARIOS.md) (danh mục tình huống) · [03-PHASES.md](03-PHASES.md) (các phase triển khai) · [00-PROMPT.md](00-PROMPT.md) (prompt hoàn chỉnh để giao việc implement).

---

## 1. Mục đích (Why)

Người học chưa vững phần mapping và các thuộc tính của annotation mapping. Đọc tài liệu không đủ, vì tài liệu nói *nên làm gì* chứ ít khi cho thấy **chuyện gì thực sự xảy ra trong DB** khi làm khác đi.

Project này trả lời bằng code chạy thật trên PostgreSQL. Với **mỗi annotation hoặc thuộc tính**, nó cho thấy:

1. **DDL** Hibernate sinh ra: bảng nào, cột FK nằm ở đâu, có bảng nối không, có constraint gì.
2. **SQL** được gửi đi: bao nhiêu INSERT, UPDATE, DELETE, SELECT, và theo thứ tự nào.
3. **Dữ liệu thật** trong DB sau thao tác, đọc bằng JDBC chứ không qua Hibernate.
4. **Trạng thái vòng đời** (TRANSIENT, MANAGED, DETACHED, REMOVED) của từng entity ở từng bước.

Mỗi câu hỏi được xét ở 3 dạng: **có** annotation hoặc thuộc tính đó, **không có** nó, và **đặt nó ở bảng A hay bảng B**.

**Cách học:** chỉ cần đọc **từng test method từ trên xuống**. Mỗi test là một tình huống đọc riêng vẫn hiểu được.

---

## 2. Goals / Non-goals / Anti-goals

### Goals
- Project Spring Boot dùng **bản GA mới nhất** + Hibernate ORM 7.x + PostgreSQL.
- Danh mục tình huống ([02-SCENARIOS.md](02-SCENARIOS.md)) gom thành **10 nhóm theo annotation**, mỗi nhóm có quy mô thực tế với **từ 3 bảng trở lên**.
- Mỗi tình huống ứng với **1 test method**, trong đó có: comment nêu mục tiêu, mapping đang dùng, comment lifecycle ở từng bước, SQL kỳ vọng, và assert trên **DB thật**.
- Mỗi **biến thể mapping** ("đặt bên A", "đặt bên B", "bỏ thuộc tính X") là **một bộ entity riêng nằm trong schema Postgres riêng**, nhờ vậy so sánh được DDL của các biến thể cạnh nhau.
- Có **Spring Data repository** cho từng biến thể. Chỉ dùng **service layer** khi tình huống cần ranh giới transaction.
- Với các kết quả chưa chắc chắn (🔬), **chạy test để xác minh** rồi ghi lại hành vi thực tế kèm version Hibernate.

### Non-goals (không làm trong project này)
- Không có Controller, REST hay UI.
- Không dùng Flyway hay Liquibase. Schema **do Hibernate sinh ra (`ddl-auto`)**, vì mục đích chính là quan sát mapping sinh ra DB như thế nào.
- Chưa đụng tới: `@Inheritance`, `@ElementCollection`, `@Embeddable` dạng value object, second-level cache, Envers, multi-tenancy. Xem phụ lục *mở rộng sau* trong 03-PHASES.
- Không làm benchmark hay tối ưu hiệu năng thật. Số câu SQL chỉ dùng để **minh hoạ**.

### Anti-goals (vẫn là thất bại dù test xanh)
- **Assert bằng repository/EntityManager** thay vì đọc DB thật. Persistence context (cache cấp 1) có thể trả về object trong bộ nhớ dù DB khác.
- **Gắn `@Transactional` lên test class hoặc test method**. Khi đó mọi entity luôn MANAGED, không flush, cuối test lại rollback, nên che mất đúng những thứ cần học.
- **Biến thể khác baseline ở nhiều hơn 1 điểm**, khiến không biết điểm nào gây ra kết quả.
- Test **phụ thuộc thứ tự chạy** hoặc dữ liệu của test khác.
- Comment lifecycle **chỉ có chữ** mà không có assert trạng thái tương ứng.
- Ghi "kỳ vọng" theo trí nhớ mà **không chạy kiểm chứng**.
- Test quá dài, gộp nhiều câu hỏi. Mỗi test chỉ trả lời **một câu hỏi**.
- **Code "thông minh" làm người học không đọc nổi**: entity kế thừa nhau để đỡ lặp, `@ParameterizedTest` gộp biến thể, helper generic hay reflection, fixture giấu trong class cha, assert không có thông báo lý do. Xem quy tắc E1–E4, T1–T9 ở §7.10.

---

## 3. Ràng buộc & môi trường

| Hạng mục | Giá trị |
|---|---|
| Máy hiện tại | JDK 21.0.13, Gradle 9.9.1, Docker 29.x. **Không có** `psql` (không cần Maven vì dự án dùng **Gradle**) |
| Folder | Trống, chưa `git init` |
| Spring Boot | **GA mới nhất trên start.spring.io tại thời điểm implement**. Hôm nay (2026-11-23) là 4.1.x; 4.2.0 đang RC, có thể GA cuối tháng 11 → chọn bản GA cao nhất đang có |
| Hibernate | 7.x do Spring Boot quản lý. **Không override version** |
| JPA | Jakarta Persistence 3.2 (`jakarta.persistence.*`) |
| PostgreSQL | Image chính thức, **pin major version** (vd. `postgres:18-alpine`, hoặc major ổn định mới nhất lúc implement) |
| Lưu ý Hibernate 7 | Đã **bỏ** `Session.save()/update()/saveOrUpdate()`. Chỉ dùng `persist/merge/remove/find/getReference` (JPA) và Spring Data |
| Lưu ý Spring Boot 4 | Starter và test slice đã được tách module. **Lấy dependency từ Initializr**, không tự gõ tay |

---

## 4. Các cách tiếp cận (Approaches)

### A. Một domain model chung cho cả project (kiểu app thật)
Chỉ có một bộ bảng (Customer, Order, Item, …), các test khác nhau ở **dữ liệu**.
- ✅ Gần app thật, ít class.
- ❌ Mapping là **tĩnh**, nên không thể cho thấy "bỏ `mappedBy` thì sao" hay "đặt FK bên A và bên B" trong cùng một project. Mỗi lần đổi annotation lại kéo theo các test khác. **Không đạt mục tiêu chính.**

### B. Mỗi biến thể là một package + một schema Postgres riêng ✅ (chọn)
Mỗi biến thể là một mini-model gồm 3–6 entity, nằm trong package riêng và `@Table(schema = "...")` riêng. Toàn bộ biến thể chạy chung một persistence unit và một Spring context.
- ✅ Có thể so sánh DDL các biến thể ngay trong DB (`\dn`, `\d g1_v2_*.*`).
- ✅ Spring context chỉ khởi động 1 lần cho tất cả test, nên chạy nhanh.
- ✅ Có repository thật, đúng yêu cầu.
- ❌ Nhiều class gần giống nhau. Chấp nhận được, vì **chính sự khác biệt 1 dòng đó là bài học**.
- ❌ Phải giữ quy tắc đặt tên chặt để tránh trùng entity name (xem §7.1).

### C. Mỗi test tự bootstrap `SessionFactory` riêng chỉ chứa vài class
- ✅ Cô lập tuyệt đối, test được cả những **mapping sai làm Hibernate không khởi động nổi**.
- ❌ Chậm, không dùng được Spring Data repository, xa với cách dùng thực tế.

**Quyết định:** dùng **B làm chính**. Chỉ dùng **C** cho các tình huống gắn nhãn `[BOOT-FAIL]`, là những mapping sai khiến Hibernate ném exception lúc khởi động. Nếu các entity sai đó nằm trong context chung thì toàn bộ test suite sẽ chết.

---

## 5. Quyết định kỹ thuật

> Đây là các **lựa chọn của người thiết kế**, chưa phải điều bạn đã đồng ý. Muốn đổi điểm nào thì sửa thẳng vào đây.

| # | Quyết định | Lý do |
|---|---|---|
| D1 | **Gradle** (yêu cầu của bạn), dùng **wrapper `./gradlew`**, build script **Groovy DSL** (`build.gradle`), Java **21** | Wrapper ghim version Gradle nên ai clone về cũng chạy giống nhau. Groovy DSL ngắn và dễ đọc hơn cho người học (builder có thể đổi sang Kotlin DSL nếu thích). Java 21 có sẵn trên máy, Boot 4 hỗ trợ |
| D2 | Test dùng **`@SpringBootTest(webEnvironment = NONE)`**, không dùng `@DataJpaTest` | Cần cả bean `@Service` cho nhóm G9. Mọi test class dùng chung 1 cấu hình nên context được cache và chỉ khởi động 1 lần |
| D3 | **Testcontainers PostgreSQL + `@ServiceConnection`**, dùng 1 container singleton cho cả suite | Không cần cài DB, chạy được trên CI, đúng PostgreSQL thật (không dùng H2, vì H2 xử lý DDL/constraint khác) |
| D4 | **Profile `inspect`**: chạy test vào Postgres của `docker-compose` (localhost:5432) với `ddl-auto=create` | Testcontainers xoá container sau khi chạy. Profile này giữ lại dữ liệu sau test để mở DB ra xem |
| D5 | **Không `@Transactional` trên test.** Mỗi bước tự mở transaction bằng helper `inTx(...)` (dùng `TransactionTemplate`) | Để thấy rõ lúc nào entity chuyển MANAGED → DETACHED, lúc nào flush, lúc nào commit |
| D6 | **Đọc DB thật bằng `JdbcTemplate`** để assert | Không bị cache cấp 1 đánh lừa |
| D7 | **Đếm SQL bằng `StatementInspector`** của Hibernate (`SqlCaptor`) | Không cần thêm thư viện. Nếu thấy đếm không ổn định thì chuyển sang `datasource-proxy` (để người build quyết định) |
| D8 | **Không dùng Lombok** cho entity. Getter/setter, `equals/hashCode`, `toString` viết tay | Người học thấy hết code. Tránh bẫy `@Data` (đệ quy `toString`, `hashCode` sai), và chính bẫy này có tình huống riêng (G9) |
| D9 | **Comment bằng tiếng Việt**, thuật ngữ giữ tiếng Anh (owning side, flush, proxy, …). `@DisplayName` tiếng Việt | Đúng ngôn ngữ người học |
| D10 | ID mặc định là **`SEQUENCE`** (mỗi entity một sequence trong schema của nó). Riêng G0-S02 dùng `IDENTITY` để so sánh | SEQUENCE cho thấy INSERT được hoãn tới lúc flush, đúng bản chất persistence context |
| D11 | **Không bật** `hibernate.jdbc.batch_size` và `default_batch_fetch_size` ở cấu hình chung | Để N+1 và thứ tự SQL hiện ra rõ. Tình huống nào cần thì bật cục bộ bằng annotation (`@BatchSize`) |
| D12 | **Xuất DDL ra file** `build/ddl/create.sql` (`jakarta.persistence.schema-generation.scripts.*`) | Đọc toàn bộ DDL của mọi biến thể trong 1 file, rất hợp để so sánh |
| D13 | Domain dùng chung một chủ đề **thương mại điện tử** cho mọi nhóm | Đọc một lần là quen tên bảng, tập trung được vào mapping |
| D14 | Dọn dữ liệu **TRƯỚC** mỗi test (TRUNCATE … CASCADE theo schema), **không dọn SAU** | Chạy xong một test vẫn mở DB ra xem được dữ liệu của test đó (khi dùng profile `inspect`) |
| D15 | **Gradle phải hiện log của test ra terminal**: `test { testLogging { showStandardStreams = true; events "passed", "failed", "skipped"; exceptionFormat = "full" } }` | Mặc định Gradle **giấu** stdout/log của test. Không bật thì không thấy SQL và dòng `[LIFECYCLE]` đã in, mất nửa giá trị học tập |
| D16 | Profile `inspect` bật bằng **`./gradlew test -Pinspect`**. `build.gradle` đọc property này rồi gán `systemProperty 'spring.profiles.active', 'inspect'` cho task `test` | Gradle chạy test trong một JVM riêng, cờ `-Dspring.profiles.active=…` ở dòng lệnh **không tự truyền sang** JVM đó |
| D17 | Chạy lại test không đổi code thì dùng `./gradlew test --rerun` (hoặc `cleanTest test`) | Gradle coi task `test` là *up-to-date* và bỏ qua nếu mã nguồn không đổi, người học sẽ tưởng test không chạy |
| D18 | Mọi class test gắn `@TestMethodOrder(MethodOrderer.MethodName.class)` và đặt tên method `s01_…`, `s02_…` | Thứ tự chạy khớp thứ tự đọc trong file, nên log in ra cũng theo đúng thứ tự người học đọc. Test vẫn độc lập, thứ tự **không ảnh hưởng đúng/sai** |

---

## 6. Cấu trúc project (đề xuất)

```
hibernate-learning-case/
├── docs/                         ← các tài liệu này + RESULTS.md, CHEATSHEET.md, CONVENTIONS.md
├── docker-compose.yml            ← postgres (pin version) + adminer (tuỳ chọn, xem DB qua browser)
├── build.gradle, settings.gradle, gradlew, gradle/wrapper/
└── src/
    ├── main/java/com/example/hibernatelab/
    │   ├── g0_lifecycle/                 ← entity + repository của nhóm G0
    │   ├── g1_ownership/
    │   │   ├── v1_manytoone_uni/         ← mỗi biến thể = 1 package = 1 schema
    │   │   │   ├── Customer.java
    │   │   │   ├── PurchaseOrder.java
    │   │   │   ├── OrderItem.java
    │   │   │   ├── Product.java
    │   │   │   └── repo/ (CustomerRepository, …)
    │   │   ├── v2_onetomany_jointable/
    │   │   └── …
    │   ├── g2_cascade/ … g9_complex/
    │   └── g9_complex/service/           ← service layer (chỉ nơi cần)
    └── test/java/com/example/hibernatelab/
        ├── support/                      ← hạ tầng test (Phase 1)
        ├── g0_lifecycle/LifecycleTest.java
        ├── g1_ownership/
        │   ├── G1V1ManyToOneUniTest.java ← 1 test class / biến thể (hoặc / chủ đề)
        │   └── README.md                 ← bảng tóm tắt nhóm: biến thể, A vs B, kết quả
        ├── …
        └── bootfail/                     ← entity "sai" cho [BOOT-FAIL], KHÔNG được entity-scan
```

> Entity nằm trong `src/main` vì đề bài yêu cầu có repository. Entity của `[BOOT-FAIL]` nằm trong `src/test/.../bootfail` và **phải bị loại khỏi entity scan** (giới hạn `@EntityScan`/package scan về `src/main`).

---

## 7. Quy ước (conventions)

### 7.1 Đặt tên — tránh đụng nhau giữa các biến thể
- **Schema** = `g<nhóm>_v<biến thể>_<mô tả>`, ví dụ `g1_v4_bidi_mappedby`. Sequence cũng nằm trong schema đó.
- **Tên bảng** để tự nhiên (`customer`, `purchase_order`, `order_item`, `product`). Hai biến thể cùng có bảng `customer` nhưng nằm ở 2 schema khác nhau.
- **Tên class** để tự nhiên, phân biệt bằng package.
- **Entity name phải duy nhất** trong toàn persistence unit, nếu không Hibernate báo trùng lúc khởi động. Do đó bắt buộc ghi `@Entity(name = "G1V4_Customer")`. Câu JPQL viết theo entity name này.
- Bật `hibernate.hbm2ddl.create_namespaces=true` để Hibernate tự tạo schema.
- Tránh tên bảng `order` vì `ORDER` là từ khoá SQL. Dùng `purchase_order`.

### 7.2 Quy tắc "một khác biệt"
Mỗi biến thể **chỉ khác baseline của nhóm đúng 1 điểm**. Đầu class entity của biến thể có comment header:

```
// BIẾN THỂ : g1_v5_bidi_no_mappedby
// BASELINE : g1_v4_bidi_mappedby
// KHÁC BIỆT: Customer.orders KHÔNG có mappedBy → cả 2 phía đều là owning side
// HẬU QUẢ  : xem G1-S15 (sinh thêm bảng nối customer_orders)
```

### 7.3 Giải phẫu một test (mẫu định dạng, không phải implementation)
Mỗi test method có các phần sau, theo đúng thứ tự. Đây là **mẫu minh hoạ định dạng** (tên helper khớp bảng ở Phase 1). Người build sẽ viết lại cho đúng với code thật, và golden test G0-S01 mới là mẫu chính thức.

```java
@TestMethodOrder(MethodOrderer.MethodName.class)   // thứ tự chạy = thứ tự đọc trong file (D18)
class G1BidirectionalTest extends AbstractScenarioTest {

    /**
     * G1-S12 — Chỉ set phía inverse (mappedBy) → FK KHÔNG được ghi
     * Mapping : g1_v4_bidi_mappedby
     *           Customer.orders        @OneToMany(mappedBy = "customer")  ← INVERSE side
     *           PurchaseOrder.customer @ManyToOne                         ← OWNING side (giữ FK)
     * Câu hỏi : Chỉ add vào customer.getOrders() mà quên order.setCustomer(c) thì DB ra sao?
     * Kết luận: customer_id = NULL. Khi ghi FK, Hibernate chỉ nhìn phía owning.
     * Xác minh: Hibernate 7.x.y (điền khi chạy)
     */
    @Test
    @DisplayName("G1-S12 | Chỉ set phía mappedBy → FK = NULL")
    void s12_setOnlyInverseSide_fkIsNull() {
        // ── GIVEN: một customer đã có trong DB ─────────────────────────
        Long customerId = inTxReturning(() -> {
            Customer customer = new Customer("An");
            em.persist(customer);
            return customer.getId();
        });

        // ── WHEN: thêm order vào customer.orders, KHÔNG gọi order.setCustomer(...) ──
        sql.reset();
        Long orderId = inTxReturning(() -> {
            Customer customer = em.find(Customer.class, customerId);

            PurchaseOrder order = new PurchaseOrder("ORD-1");
            // [LIFECYCLE] order: TRANSIENT (mới new, chưa nằm trong persistence context)
            assertState(em, order, TRANSIENT);

            customer.getOrders().add(order);   // chỉ set phía INVERSE (mappedBy)
            em.persist(order);
            // [LIFECYCLE] order: TRANSIENT → MANAGED (đã có id từ sequence, CHƯA có INSERT nào)
            assertState(em, order, MANAGED);
            return order.getId();
        });
        // [FLUSH] commit → INSERT purchase_order với customer_id = NULL
        // [TX END] persistence context đóng → customer, order: MANAGED → DETACHED

        // ── THEN: SQL đã chạy ──────────────────────────────────────────
        sql.print();
        sql.assertInserts("purchase_order", 1);
        sql.assertUpdates("purchase_order", 0);

        // ── THEN: DB thật (JDBC) ───────────────────────────────────────
        db.print("g1_v4_bidi_mappedby.purchase_order");
        Map<String, Object> orderRow = db.rowById("g1_v4_bidi_mappedby.purchase_order", orderId);
        assertThat(orderRow.get("customer_id"))
                .as("customer_id phải NULL: Customer.orders là phía mappedBy (INVERSE) nên Hibernate "
                  + "KHÔNG ghi FK từ phía này. Phải gọi order.setCustomer(customer)")
                .isNull();
    }
}
```

Các khối `── THEN: DDL ──` (đọc `SchemaInspector`) chỉ xuất hiện ở tình huống nói về cấu trúc bảng. Tình huống nào cũng có đủ **lifecycle**, **SQL** và **DB thật**.

### 7.4 Comment & assert vòng đời (lifecycle)
- Ở **mỗi bước làm đổi trạng thái**, ghi comment dạng
  `// [LIFECYCLE] order: TRANSIENT → MANAGED (đã có id từ sequence, CHƯA INSERT)`
- Ngay sau comment là **assert tương ứng**, ví dụ `assertState(em, order, MANAGED)`.
- Ở **mỗi điểm flush/commit**, ghi comment `// [FLUSH] …` liệt kê SQL dự kiến.
- Ở **mỗi điểm transaction kết thúc**, ghi rõ: `// [TX END] persistence context đóng → mọi entity: MANAGED → DETACHED`.
- `LifecycleInspector` xác định trạng thái như sau:
  - MANAGED: `em.contains(e)` là true.
  - REMOVED: entry trong persistence context có status `DELETED` (dùng Hibernate SPI, chỉ để học).
  - TRANSIENT và DETACHED: không nằm trong persistence context, và id `null` → TRANSIENT, id khác null → DETACHED.
  - Cách phân biệt TRANSIENT/DETACHED **chỉ là heuristic**. Javadoc của helper phải ghi rõ điều này.
- **Chỉ gọi `assertState` bên trong `inTx(...)`**, với `EntityManager` đang gắn vào transaction đó. Ngoài transaction, `EntityManager` dùng chung của Spring sẽ mở một PC mới, rỗng, cho mỗi lần gọi, nên `contains()` luôn trả về false. Vì vậy ngoài tx, helper không bao giờ được trả về MANAGED (entity có id → DETACHED).

### 7.5 Assert SQL
- `SqlCaptor` ghi lại mọi câu SQL. Dùng `sql.reset()` trước bước WHEN để chỉ đo đúng phần cần học.
- Đếm theo **loại + bảng**, ví dụ `sql.assertInserts("order_item", 2)`, `sql.assertUpdates("purchase_order", 0)`. **Bỏ qua** các câu `nextval` của sequence, vì optimizer pooled khiến chúng xuất hiện không đều.
- Mỗi bước in ra console một dòng đánh dấu (`── WHEN ──`), log SQL ngay bên dưới, để đọc log dễ.
- Chỉ assert thứ tự SQL khi thứ tự chính là bài học (ví dụ G3-S10 về flush order).

### 7.6 Assert DB thật
- Mọi kết luận về dữ liệu đều đọc bằng `JdbcTemplate` với tên bảng đầy đủ kèm schema.
- Muốn chứng minh "flush ≠ commit" thì đọc bằng một **connection khác**, bằng helper chạy trong transaction `REQUIRES_NEW`.
- Tình huống về DDL thì đọc `information_schema` / `pg_catalog` qua `SchemaInspector`: cột, nullable, PK, FK (kèm `ON DELETE`), unique.

### 7.7 Dọn dữ liệu & cô lập
- `@BeforeEach`: TRUNCATE mọi bảng trong (các) schema mà test class khai báo, kèm `CASCADE`.
- **Không assert giá trị id cụ thể**, vì sequence không reset. Dùng id được trả về từ bước GIVEN.
- Không chạy test song song.

### 7.8 Tình huống `[BOOT-FAIL]`
- Entity sai nằm trong `src/test/.../bootfail/<tình huống>/`.
- Helper `MappingBootstrap.tryBuild(Class<?>... classes)` dựng một `SessionFactory` riêng chỉ chứa các class đó (dùng URL của container), trả về exception để assert **loại exception + đoạn message chính**.

### 7.9 Nhãn 🔬 và `RESULTS.md`
- 🔬 nghĩa là **chưa chắc kết quả**: hành vi phụ thuộc version Hibernate hoặc hiếm gặp.
- Quy trình: chạy test ở chế độ quan sát (in SQL + dữ liệu), rồi viết assert theo **kết quả thực tế**, rồi ghi một dòng vào `docs/RESULTS.md`:
  `| G3-S09 | Chuyển item từ đơn A sang đơn B khi orphanRemoval=true | Giả thuyết | Thực tế | Hibernate 7.x.y |`
- Trong javadoc của test, ghi `Xác minh: Hibernate 7.x.y`.

### 7.10 Nguyên tắc code rõ ràng (ưu tiên số 1, đứng trên cả DRY)

Đây là project học tập: người đọc phải hiểu **chỉ bằng cách đọc code**, và phải **tự kiểm chứng được bằng test**. Vì vậy khi phải chọn giữa "ngắn gọn / tái sử dụng" và "rõ ràng", **luôn chọn rõ ràng**, kể cả khi phải lặp code.

**Entity (mapping)**

| # | Quy tắc | Lý do |
|---|---|---|
| E1 | Mỗi entity một file, ngắn, **không kế thừa**, không `@MappedSuperclass` dùng chung giữa các biến thể. Chấp nhận copy-paste giữa biến thể | Thấy đủ mapping của một biến thể trong **một chỗ**. Sự khác biệt 1 dòng giữa hai biến thể chính là bài học, không được giấu vào class cha |
| E2 | Ghi **tường minh** mọi thuộc tính annotation liên quan tới bài học (`fetch = FetchType.LAZY`, `@JoinColumn(name = "customer_id")`). Chỗ cố ý để mặc định thì thêm comment: `// cố ý KHÔNG ghi fetch → mặc định EAGER` | Người đọc phân biệt được "để mặc định" với "quên viết" |
| E3 | Cạnh mỗi quan hệ có comment nói rõ vai trò: `// OWNING side — giữ FK purchase_order.customer_id` hoặc `// INVERSE side — không ghi FK` | Owning/inverse là khái niệm khó nhất của nhóm G1 |
| E4 | Getter/setter, `equals/hashCode`, helper `addItem/removeItem` viết tay, helper có comment giải thích tại sao phải đồng bộ 2 phía | Không có phép màu nào bị giấu (xem D8) |

**Test**

| # | Quy tắc | Lý do |
|---|---|---|
| T1 | Dữ liệu GIVEN **nhìn thấy được ngay trong test method**: viết inline, hoặc gọi một helper `private` **nằm trong cùng file**, tên nói rõ nó tạo gì (`persistCustomerWith2Orders()`). `@BeforeEach` **chỉ để dọn DB**. Không đặt fixture ở class cha hay `@BeforeAll` | Người đọc chỉ mở một method là hiểu hết, không phải lần theo nhiều file |
| T2 | Không dùng `var`. Không chuỗi `stream()` dài hay lambda lồng nhau trong phần GIVEN/WHEN/THEN, dùng vòng `for` và biến có tên (lambda của `inTx(...)` là ngoại lệ vì không tránh được) | Kiểu dữ liệu và luồng xử lý phải hiện ra trên mặt chữ |
| T3 | Tên biến mang nghĩa nghiệp vụ: `orderA`, `itemWithSameCode`, `customerBeforeMerge`. Không dùng `o1`, `x`, `tmp` | Đọc test như đọc một câu chuyện |
| T4 | Assert bằng **AssertJ** kèm lý do: `.as("FK phải NULL vì Customer.orders là phía mappedBy, Hibernate không ghi nó")`. Câu `as(...)` giải thích **vì sao**, không chỉ mô tả giá trị | Khi assert đỏ, thông báo lỗi chính là bài học. Khi assert xanh, câu `as(...)` là lời kết luận cho người đọc |
| T5 | Một test = một câu hỏi, tối đa 3 nhóm assert (lifecycle, SQL, DB thật). So sánh A và B thì viết **hai test method đặt cạnh nhau** (hoặc một test có hai khối `── CÓ mappedBy ──` / `── KHÔNG mappedBy ──`). **Không dùng `@ParameterizedTest`** để gộp biến thể | Gộp bằng tham số sẽ che mất chỗ khác nhau, mà đó lại là thứ cần nhìn thấy |
| T6 | Helper trong `support` có tên đọc như câu văn: `db.rowsOf("purchase_order")`, `sql.assertInserts("order_item", 2)`, `assertState(em, order, MANAGED)`. Không reflection, không generics phức tạp. Helper dài quá ~30 dòng thì phải có Javadoc và test riêng | Người đọc không cần mở source của helper vẫn hiểu nó làm gì |
| T7 | **In bằng chứng ra console**: mỗi khối THEN gọi `db.print("purchase_order")` (in bảng ASCII nhỏ của các dòng hiện có) và `sql.print()` (in SQL đã chạy từ lúc `reset`). Chạy `./gradlew test --tests '…'` là đọc được kết quả, không cần mở DB | Người học **thấy** dữ liệu, không phải tin vào assert |
| T8 | Test method không quá ~40 dòng code (không tính comment và dòng trống). Dài hơn thì tình huống đang chứa nhiều hơn một câu hỏi, hãy tách | Giữ mỗi test vừa một màn hình |
| T9 | Comment giải thích **Hibernate đang làm gì và vì sao**, không lặp lại code. Tốt: `// [LIFECYCLE] order: TRANSIENT → MANAGED. id đã có từ sequence nhưng CHƯA có INSERT nào`. Vô ích: `// gọi persist` | Comment lifecycle là mục đích cốt lõi của project |

**Thứ tự đọc cho người học:** README của nhóm → comment header `BIẾN THỂ / BASELINE / KHÁC BIỆT` ở các entity → test từ `s01` đi xuống. Chạy từng test bằng `./gradlew test --tests '*G1V4*s12*'` để xem log của đúng tình huống đó.

---

## 8. Kiến thức nền — vòng đời entity (dùng khi viết comment)

```
          new Customer()
                │
                ▼
         ┌────────────┐   persist()    ┌────────────┐   remove()    ┌────────────┐
         │ TRANSIENT  │──────────────▶ │  MANAGED   │─────────────▶ │  REMOVED   │
         │ (chưa có   │                │ (PC theo   │ ◀──────────── │ (chờ DELETE│
         │  trong PC) │                │  dõi dirty)│   persist()   │  lúc flush)│
         └────────────┘                └────────────┘               └────────────┘
                                         │      ▲                         │ flush
           detach() / clear() / đóng PC  │      │ merge(detached)         ▼
           (Spring: kết thúc transaction)▼      │ → trả về BẢN MANAGED    DELETE trong DB
                                       ┌────────────┐  KHÁC, bản truyền vào
                                       │  DETACHED  │  vẫn DETACHED
                                       └────────────┘
```

| Thao tác | SQL chạy khi nào |
|---|---|
| `persist()` | INSERT **lúc flush**. Ngoại lệ: `IDENTITY` INSERT **ngay lập tức** vì cần id |
| Sửa field của entity MANAGED | UPDATE lúc flush (dirty checking), **không cần gọi save()** |
| `remove()` | DELETE lúc flush |
| `merge(detached)` | SELECT (nếu entity chưa có trong PC), rồi UPDATE lúc flush nếu khác |
| Flush xảy ra khi | commit; `em.flush()`; trước câu JPQL đụng tới bảng có thay đổi (FlushMode AUTO) |

**Thứ tự flush (ActionQueue)** cần nhớ vì gây nhiều lỗi khó hiểu: orphan removal → INSERT → UPDATE → thao tác collection → **DELETE sau cùng** (🔬 xác minh trong G3-S10).

**Spring:** persistence context gắn với transaction. Transaction kết thúc thì PC đóng và mọi entity chuyển sang **DETACHED**. Với service `@Transactional(readOnly = true)`, Spring đặt FlushMode MANUAL nên sửa entity không sinh UPDATE.

---

## 9. Rủi ro & lưu ý cho người build

| Rủi ro | Cách xử lý |
|---|---|
| Trùng entity name giữa các biến thể → context không khởi động | Luôn ghi `@Entity(name = "G<x>V<y>_<Tên>")` (§7.1) |
| Entity `[BOOT-FAIL]` lọt vào scan → cả suite chết | Để trong `src/test/.../bootfail`, giới hạn package scan |
| Đếm SQL lệch do `nextval` / batching | Bỏ qua `nextval`. Không bật batching chung (D11) |
| Kỳ vọng khác thực tế ở version Hibernate mới | Dùng 🔬 + RESULTS.md, assert theo thực tế |
| Testcontainers khởi động chậm | Dùng 1 container singleton, 1 Spring context chung |
| Tên mặc định (bảng nối, cột FK) phụ thuộc naming strategy của Spring Boot | Không đoán. Test đọc `information_schema` rồi in ra và ghi vào RESULTS.md |
| Suite quá lớn (~130 tình huống) | Làm hết ⭐ trước, ➕ sau. Có checkpoint với người học (03-PHASES) |
| Gradle giấu log của test nên không thấy SQL | Bật `showStandardStreams` (D15) và kiểm tra ngay ở Phase 0 bằng cách xem SQL của smoke test hiện ra |
| `./gradlew test` báo `UP-TO-DATE`, không chạy lại | Dùng `--rerun` (D17), ghi vào README gốc |
| Cờ `-Dspring.profiles.active=inspect` không có tác dụng với Gradle | Dùng `-Pinspect` (D16) |

---

## 10. Phần để người build tự quyết
- Tên package gốc (`com.example.hibernatelab` chỉ là đề xuất).
- Cách gom test class: một class cho mỗi biến thể, hay một class cho mỗi chủ đề có nhiều biến thể. Tiêu chí: người đọc mở **một file** là thấy được phép so sánh A vs B.
- Có dùng adminer trong docker-compose hay không.
- Groovy hay Kotlin DSL cho build script (D1). Khi đổi thì giữ nguyên D15–D17.
- `StatementInspector` hay `datasource-proxy` (D7).
- Field cụ thể của từng entity. Giữ **3–6 field**, đủ để có ý nghĩa thực tế (tên, giá, số lượng, trạng thái, thời gian).
