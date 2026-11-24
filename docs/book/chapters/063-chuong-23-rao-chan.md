## Chương 23. Rào chắn cho cả team

Chương 22 kết thúc bằng một nhận xét lặp lại bốn lần: **sửa lỗi xong chưa phải là xong**. Người sửa nhớ lỗi; ba tháng sau người khác không nhớ, và lỗi quay lại. Review code giúp được một phần, nhưng người review cũng là người, họ đọc mapping mà không đếm được SQL, và họ mệt vào chiều thứ sáu. Cách bền hơn là biến kinh nghiệm thành **rào chắn tự động**: một test đỏ khi ai đó lặp lại sai lầm cũ.

Chương này gom lại những rào chắn đã dùng ở Chương 22 và đưa chúng thành công cụ dùng chung cho cả team:

- **Ngân sách SQL** (mục 23.1): đặt trần số câu SELECT cho một use case, bắt N+1 mà review mapping không thấy.
- **Lint mapping** (mục 23.2): quét entity và báo vi phạm quy tắc của team, bắt lỗi ngay lúc review entity.
- **Checklist review PR** (mục 23.3): những câu hỏi cần hỏi khi thấy code JPA trong pull request, kèm chỉ dẫn đọc lại ở đâu trong sách.
- **Năm quyết định mẫu** (mục 23.4): năm ADR (Architecture Decision Record, bản ghi quyết định kiến trúc) đã chốt từ kết quả của lab, để team chép về và chỉnh theo hoàn cảnh của mình.

Nguyên tắc xuyên suốt: **một rào chắn chưa từng đỏ là rào chắn chưa được chứng minh**. Vì vậy mỗi rào chắn dưới đây đều có test chứng minh nó bắt được ví dụ sai, và cho qua ví dụ đúng. Rào chắn quá gắt (báo nhầm) cũng nguy hiểm không kém, vì team sẽ tắt nó đi.

**Trong JPA thuần.** `SqlBudget` đọc SQL qua cơ chế `StatementInspector` của Hibernate, và `MappingLint` đọc mapping qua `Metadata` của Hibernate. Hai công cụ này vì thế là code riêng của Hibernate, nhưng chúng kiểm tra những quy tắc của JPA (`fetch`, `cascade`, `@GeneratedValue`). Nếu dùng provider khác, bạn cần cách đọc tương đương.

### 23.1 Test ngân sách SQL

Ý tưởng đơn giản: mỗi use case đọc quan trọng có một con số "tối đa bao nhiêu câu SELECT". Test chạy use case, đếm số SELECT thật sự chạy, và đỏ nếu vượt. Thông báo lỗi liệt kê các câu SELECT đã chạy, nên người sửa thấy ngay câu nào thừa, không phải bật log rồi đi tìm.

Công cụ của lab là lớp `SqlBudget` với một hàm duy nhất:

```java
public final class SqlBudget {

    public static void assertSelectsAtMost(
            SqlCaptor captor, String useCase, int maxSelects) {
        List<String> selects = new ArrayList<>();
        for (String statement : captor.statements()) {
            if (statement.stripLeading().toLowerCase(Locale.ROOT)
                    .startsWith("select")) {
                selects.add(statement.strip());
            }
        }
        if (selects.size() > maxSelects) {
            // dựng thông báo: tên use case, trần, số thực tế,
            // rồi liệt kê từng SELECT đánh số #1, #2, ...
            throw new AssertionError(message.toString());
        }
    }
}
```

Cách dùng trong test luôn theo ba bước: xóa bộ đếm, chạy use case, kiểm tra ngân sách:

```java
sql.reset();
fixedOrderList.list(PageRequest.of(0, 10));
SqlBudget.assertSelectsAtMost(captor, "danh sách đơn", 2);
```

Ba tình huống dưới đây chứng minh ba tính chất của rào chắn này: ngân sách là **trần** (đúng bằng trần vẫn qua), thông báo lỗi **đủ thông tin để sửa**, và nó **chỉ đếm SELECT**.

#### 23.1.1 Dùng đúng ngân sách thì im lặng {.cot-loi}

**Bối cảnh.** Bạn đặt ngân sách 2 SELECT cho trang danh sách đơn (một query dữ liệu, một query count). Use case đã sửa ở mục 22.1.3 dùng đúng 2 câu. Một rào chắn tốt không được làm phiền người đã làm đúng: đúng bằng trần phải qua.

**Khi nào xảy ra.** Số SELECT thực tế nhỏ hơn hoặc bằng ngân sách.

**Khi nào không xảy ra.** Số SELECT vượt ngân sách, dù chỉ một câu (mục 23.1.2).

**Ví dụ.**

```java
G14Fixtures.seedI1(jdbc, G14Fixtures.I1_FIXED); // 30 đơn, 3 dòng hàng mỗi đơn

sql.reset();
fixedOrderList.list(PageRequest.of(0, 10));

assertThatCode(() -> SqlBudget.assertSelectsAtMost(captor, "danh sách đơn", 2))
    .doesNotThrowAnyException();
```

**Kết quả.** Use case chạy 2 câu SELECT, bằng đúng ngân sách 2, nên `assertSelectsAtMost` không ném gì.

**Vì sao.** Phép so sánh trong code là `selects.size() > maxSelects`, tức là chỉ báo khi **vượt** trần. Ngân sách là trần ("tối đa"), không phải con số phải đạt đúng. Nếu viết thành "phải bằng" thì mỗi lần tối ưu thêm (giảm còn 1 câu) lại làm test đỏ, và người ta sẽ ngại tối ưu.

::: {.ghi-nho}
Ngân sách là trần: đúng bằng trần vẫn qua, chỉ vượt trần mới đỏ.
:::

::: {.tracuu codes="G14-R1-S01"}
:::

#### 23.1.2 Vượt ngân sách thì đỏ, kèm đúng các câu SELECT đã chạy {.cot-loi}

**Bối cảnh.** Một đồng nghiệp sửa use case danh sách và vô tình quay lại duyệt entity (đúng lỗi của mục 22.1.1). Test ngân sách đỏ. Nhưng "đỏ" chưa đủ: người đọc kết quả test cần biết **câu nào thừa** để sửa, nếu không họ phải mở log, chạy lại, và tự đếm.

**Khi nào xảy ra.** Use case chạy nhiều SELECT hơn ngân sách.

**Khi nào không xảy ra.** Use case nằm trong ngân sách (mục 23.1.1).

**Ví dụ.** Chạy đường có lỗi của sự cố 22.1 rồi chặn `AssertionError` để đọc thông báo:

```java
G14Fixtures.seedI1(jdbc, G14Fixtures.I1_BUGGY);

sql.reset();
buggyOrderList.list(PageRequest.of(0, 10, Sort.by("id")));

try {
    SqlBudget.assertSelectsAtMost(captor, "danh sách đơn", 2);
} catch (AssertionError expected) {
    System.out.println(expected.getMessage());
}
```

**Kết quả.** `AssertionError` được ném, và thông báo có dạng:

```text
Use case "danh sách đơn" vượt ngân sách SQL: được phép tối đa 2 câu SELECT
nhưng đã chạy 22. Các câu SELECT đã chạy:
    #1 select ... from g14_i1_buggy.purchase_order ... order by ...
    #2 select count(ibo1_0.id) from g14_i1_buggy.purchase_order ibo1_0
    #3 select ibc1_0.id, ibc1_0.name from g14_i1_buggy.customer ibc1_0 where ...
    ...
    #22 select i1_0.order_id, ... from g14_i1_buggy.order_item i1_0 where ...
```

Thông báo nêu **bốn thứ**: tên use case, trần, số câu thực tế (22) và từng câu SELECT đánh số từ `#1` đến `#22`.

**Vì sao.** Người đọc nhìn danh sách và thấy ngay câu `select ... from customer where ...` lặp lại, đây là dấu hiệu quen thuộc của N+1 (mục 22.1.2). Không cần mở log, không cần chạy lại, không cần đoán. Việc gộp khoảng trắng của mỗi câu thành một dòng (`replaceAll("\\s+", " ")`) giúp thông báo gọn trong báo cáo của CI.

::: {.ghi-nho}
Thông báo lỗi của rào chắn phải đủ để sửa mà không cần chạy lại: nêu use case, trần, số thực tế và liệt kê từng câu SELECT.
:::

::: {.tracuu codes="G14-R1-S02"}
:::

#### 23.1.3 Chỉ đếm SELECT, không đếm INSERT {.cot-loi}

**Bối cảnh.** Use case import ghi 50 dòng, tức là có nhiều câu INSERT. Nếu bạn đặt ngân sách "0 SELECT" cho nó, bạn muốn test qua, vì import không có lý do gì để đọc. Nhưng nếu rào chắn đếm cả INSERT, ngân sách 0 sẽ đỏ ngay.

**Khi nào xảy ra.** Use case có nhiều câu INSERT hoặc UPDATE nhưng không có SELECT.

**Khi nào không xảy ra.** Nếu bạn cần canh cả số câu ghi, dùng công cụ đếm riêng (`SqlAssert` của lab) hoặc đo lô JDBC (Chương 18).

**Ví dụ.**

```java
sql.reset();
fixedImport.importItems(I4FixedImportService.CHUNK); // 50 dòng, chỉ INSERT

assertThatCode(() -> SqlBudget.assertSelectsAtMost(captor, "import", 0))
    .doesNotThrowAnyException();
```

**Kết quả.** Import 50 dòng với ngân sách 0 SELECT vẫn qua.

**Vì sao.** Hàm chỉ đếm câu bắt đầu bằng `select`. Đây là lựa chọn thiết kế có chủ ý: ngân sách này canh việc **đọc quá nhiều** (N+1, tải thừa). Việc **ghi quá nhiều** là mối quan tâm khác, với rào chắn khác (cỡ persistence context và lint IDENTITY ở mục 22.4.4). Trộn hai thứ vào một con số làm thông báo lỗi khó hiểu: "vượt ngân sách" nhưng không rõ vượt về đọc hay ghi.

Một chi tiết kỹ thuật: câu `select nextval(...)` lấy id từ sequence cũng bắt đầu bằng `select`, nhưng bộ đếm `SqlCaptor` đã loại sẵn chúng, nên ngân sách không bị tính nhầm vì chiến lược id.

::: {.ghi-nho}
Ngân sách SELECT chỉ canh việc đọc quá nhiều; số câu ghi là chuyện của rào chắn khác. Mỗi rào chắn nên canh đúng một mối quan tâm.
:::

::: {.tracuu codes="G14-R1-S03"}
:::

### 23.2 Lint mapping bằng Metadata

Ngân sách SQL bắt lỗi **cách dùng**. Phần còn lại là lỗi **mapping**, và mapping có thể kiểm tra mà không cần chạy use case nào, thậm chí không cần database. `MappingLint` làm việc đó: nó nhận danh sách class entity, nhờ Hibernate dựng mapping (đối tượng `Metadata`), rồi duyệt từng thuộc tính và báo vi phạm.

Bốn quy tắc, mỗi quy tắc ứng với một bài học đã chứng minh ở chương trước:

| Quy tắc | Điều cấm | Vì sao | Chương chứng minh |
|---|---|---|---|
| EAGER-TO-ONE | `@ManyToOne` hoặc `@OneToOne` không LAZY | Mặc định là EAGER, gây N+1 | Chương 11 |
| EAGER-COLLECTION | Collection không LAZY | Mỗi lần nạp cha kéo theo cả collection | Chương 11 |
| CASCADE-REMOVE | `cascade` REMOVE hoặc ALL trên `@ManyToOne` hay `@ManyToMany` | Xóa một bản ghi có thể xóa dữ liệu dùng chung | Chương 5, Chương 8 |
| IDENTITY-ID | Id dùng `IDENTITY` | Tắt gom lô INSERT | Chương 18 |

Cách dựng không cần thư viện nào ngoài Hibernate. Lint tạo một `MetadataSources`, thêm các class cần quét, rồi `buildMetadata()`. Vì không truy cập database (`allow_jdbc_metadata_access = false`), nó chạy trong vài trăm mili giây và không cần Testcontainers:

```java
public static List<String> check(Class<?>... entityClasses) {
    StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
        .applySetting("hibernate.dialect",
            "org.hibernate.dialect.PostgreSQLDialect")
        .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
        .build();
    try {
        MetadataSources sources = new MetadataSources(registry);
        for (Class<?> entityClass : entityClasses) {
            sources.addAnnotatedClass(entityClass);
        }
        Metadata metadata = sources.buildMetadata();
        List<String> violations = new ArrayList<>();
        for (PersistentClass entity : metadata.getEntityBindings()) {
            for (Property property : entity.getProperties()) {
                checkProperty(entity, property, violations);
            }
            checkIdentity(entity, violations);
        }
        return violations;
    } finally {
        StandardServiceRegistryBuilder.destroy(registry);
    }
}
```

Ba quy tắc đầu đọc từ mapping mà Hibernate dựng (`isLazy()`, `getCascade()`). Quy tắc `IDENTITY` đọc annotation trên field `@Id`, vì ở bước dựng mapping lúc khởi động chưa có sẵn đối tượng generator để hỏi.

Ba tình huống dưới đây chứng minh: model sạch qua, entity cố ý sai bị bắt đủ, và mapping đúng nhưng "trông giống lỗi" không bị báo nhầm.

#### 23.2.1 Lint chạy trên model sạch và không báo gì {.cot-loi}

**Bối cảnh.** Team đã sửa xong bốn sự cố ở Chương 22. Bạn muốn một test canh để về sau ai thêm `@ManyToOne` trơn hay đổi id sang `IDENTITY` vào các entity đã sửa thì test đỏ ngay.

**Khi nào xảy ra.** Bạn quét các entity đã tuân thủ quy tắc.

**Khi nào không xảy ra.** Nếu quét lẫn cả entity cố ý sai (như phần lớn mô hình mẫu của sách để minh họa lỗi), lint đỏ đúng như mong đợi nhưng không phải điều bạn muốn ở rào chắn dùng thường trực. Vì vậy chỉ quét model sạch.

**Ví dụ.**

```java
List<String> violations = MappingLint.check(
    I1FixedCustomer.class, I1FixedOrder.class, I1FixedOrderItem.class,
    I2FixedProduct.class, I2FixedSale.class,
    I3FixedJob.class, I3FixedEmailLog.class,
    I4FixedItem.class);

assertThat(violations).isEmpty();
```

**Kết quả.** Tám entity của bốn sự cố đã sửa cho **0 vi phạm**.

**Vì sao.** Mỗi entity đã sửa khai quan hệ `LAZY` tường minh, không cascade REMOVE trên `@ManyToOne`, và dùng `SEQUENCE` cho id. Test này là hàng rào thường trực: nó chạy mỗi lần build. Nguyên tắc cần giữ: **chỉ quét model sạch**. Nếu bạn quét cả package chứa entity minh họa lỗi thì lint luôn đỏ, và một rào chắn luôn đỏ nhanh chóng bị bỏ qua.

::: {.ghi-nho}
Quét lint trên model sạch và yêu cầu 0 vi phạm. Model có anti-pattern cố ý phải nằm ngoài phạm vi quét.
:::

::: {.tracuu codes="G14-R2-S01"}
:::

#### 23.2.2 Lint bắt được entity cố ý sai ở cả bốn quy tắc {.cot-loi}

**Bối cảnh.** Rào chắn luôn xanh có thể vì nó tốt, hoặc vì nó không soi được gì. Cách duy nhất để biết là cho nó một ví dụ sai và xem nó có đỏ không. Entity `BadLintOrder` được viết **cố ý** vi phạm mọi quy tắc.

**Khi nào xảy ra.** Lint quét entity vi phạm quy tắc.

**Khi nào không xảy ra.** Entity cố ý sai nằm ở package `bootfail` của test để entity scan lúc khởi động ứng dụng không thấy nó. Khác với lỗi khởi động, mapping này **vẫn khởi động được**, nó chỉ sai quy tắc.

**Ví dụ.** Entity cố ý sai:

```java
@Entity(name = "G14R2_BadLintOrder")
@Table(name = "g14r2_order")
public class BadLintOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // vi phạm IDENTITY-ID
    private Long id;

    // vi phạm EAGER-TO-ONE (thiếu fetch = LAZY nên mặc định là EAGER)
    // và CASCADE-REMOVE (xóa đơn kéo theo xóa khách)
    @ManyToOne(cascade = CascadeType.REMOVE)
    private BadLintCustomer customer;

    // vi phạm EAGER-TO-ONE: @OneToOne cũng mặc định EAGER
    @OneToOne
    private BadLintProfile profile;

    // vi phạm EAGER-COLLECTION
    @OneToMany(mappedBy = "order", fetch = FetchType.EAGER)
    private List<BadLintItem> items = new ArrayList<>();

    // vi phạm EAGER-COLLECTION và CASCADE-REMOVE trên @ManyToMany
    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private Set<BadLintTag> tags;
}
```

Và lời gọi lint:

```java
List<String> violations = MappingLint.check(
    BadLintOrder.class, BadLintCustomer.class, BadLintProfile.class,
    BadLintItem.class, BadLintTag.class);
```

**Kết quả.** Lint trả về đủ các vi phạm đã cố ý cài:

| Quy tắc | Vị trí bị báo |
|---|---|
| EAGER-TO-ONE | `customer` (`@ManyToOne` trơn) và `profile` (`@OneToOne` trơn) |
| EAGER-COLLECTION | `items` (`@OneToMany` EAGER) và `tags` (`@ManyToMany` EAGER) |
| CASCADE-REMOVE | `customer` (trên `@ManyToOne`) và `tags` (cascade ALL trên `@ManyToMany`) |
| IDENTITY-ID | `id` của `BadLintOrder` (và của các entity bad khác) |

Mỗi dòng báo có dạng `QUY-TẮC: Entity.thuộc-tính ...`, ví dụ:

```text
EAGER-TO-ONE: G14R2_BadLintOrder.customer phải LAZY
CASCADE-REMOVE: G14R2_BadLintOrder.customer (@ManyToOne) không được ... ALL
EAGER-COLLECTION: G14R2_BadLintOrder.items phải LAZY
IDENTITY-ID: G14R2_BadLintOrder.id dùng IDENTITY (tắt batch insert)
```

Một thuộc tính có thể vi phạm hai quy tắc cùng lúc: `customer` bị báo cả EAGER-TO-ONE lẫn CASCADE-REMOVE.

**Vì sao.** Hai điểm đáng chú ý. Thứ nhất, `@OneToOne` cũng mặc định EAGER giống `@ManyToOne`, lint phải bắt cả hai kiểu to-one. Thứ hai, `cascade = ALL` **bao gồm** REMOVE, nên cascade ALL trên `@ManyToMany` cũng bị bắt (xóa đơn sẽ xóa luôn tag dùng chung, Chương 8). Lint cũng không báo nhầm: `@ManyToOne` LAZY ở `BadLintItem` đúng quy tắc nên không bị báo EAGER-TO-ONE (chỉ bị báo IDENTITY-ID như các entity bad khác).

::: {.ghi-nho}
Rào chắn phải chứng minh nó bắt được ví dụ sai: cho lint một entity cố ý vi phạm đủ bốn quy tắc và kiểm tra từng vi phạm đều được báo.
:::

::: {.tracuu codes="G14-R2-S02"}
:::

#### 23.2.3 Lint không báo nhầm mapping đúng quy tắc {.cot-loi}

**Bối cảnh.** Một rào chắn quá gắt còn tệ hơn không có rào chắn: nó báo "lỗi" ở chỗ không có lỗi, người ta mất niềm tin rồi tắt nó đi. `@OneToMany(cascade = PERSIST)` là mẫu cha con rất chuẩn, nhưng trông có vẻ "có cascade" nên dễ bị lint quá tay hiểu lầm.

**Khi nào xảy ra.** Lint quét mapping đúng quy tắc nhưng "trông giống" lỗi.

**Khi nào không xảy ra.** Nếu lint cấm mọi `cascade`, nó sẽ báo nhầm; lint của lab chỉ cấm REMOVE và ALL trên `@ManyToOne` và `@ManyToMany`.

**Ví dụ.** Mapping của sự cố 22.1 (bản có lỗi) có `@ManyToOne` LAZY và `@OneToMany(cascade = PERSIST)` LAZY:

```java
List<String> violations = MappingLint.check(
    I1BuggyCustomer.class, I1BuggyOrder.class, I1BuggyOrderItem.class);

assertThat(violations).isEmpty();
```

**Kết quả.** 0 vi phạm.

**Vì sao.** Bản có lỗi của sự cố 22.1 có mapping **hoàn toàn đúng quy tắc**: lỗi N+1 của nó nằm ở service, không ở mapping. Lint chỉ nhìn mapping nên (đúng) không thấy lỗi đó, và đó là lý do cần **cả hai** rào chắn: lint bắt lỗi mapping, ngân sách SQL bắt lỗi cách dùng. `cascade = PERSIST` trên `@OneToMany` là mẫu cha con chuẩn (Chương 5) nên không bị cấm.

::: {.ghi-nho}
Lint chỉ thấy mapping; N+1 ở service cần ngân sách SQL. Hai rào chắn bổ sung nhau, và rào chắn không được báo nhầm mẫu đúng.
:::

::: {.tracuu codes="G14-R2-S03"}
:::

### 23.3 Checklist review PR có JPA

Rào chắn tự động không bao giờ bao phủ hết. Phần còn lại là con người đọc pull request. Checklist dưới đây dùng lại kết quả của cả cuốn sách: mỗi dòng là một **câu hỏi** hỏi tác giả khi thấy một dấu hiệu trong PR, kèm chỉ dẫn đọc lại ở mục nào của sách để hiểu vì sao đó là vấn đề.

Cách dùng: đọc theo thứ tự các nhóm. Không cần hỏi hết mọi câu ở mọi PR; chỉ cần câu nào khớp với dấu hiệu bạn thấy.

**Mapping**

| Câu hỏi | Dấu hiệu trong PR | Đọc lại ở |
|---|---|---|
| Mọi `@ManyToOne` và `@OneToOne` có `fetch = LAZY` tường minh không? | `@ManyToOne` trơn (mặc định là EAGER) | Mục 11.2, 11.3 |
| Quan hệ hai chiều có `mappedBy` ở phía collection và hàm `addX`, `removeX` không? | Không có `mappedBy` thì sinh thêm cột khóa ngoại lẫn bảng nối; chỉ gọi `getOrders().add()` thì khóa ngoại bị để NULL | Mục 4.7, 4.8 |
| `cascade` chỉ đặt ở phía cha trỏ xuống con không? | `CascadeType.REMOVE` hoặc `ALL` trên `@ManyToOne` hay `@ManyToMany` | Mục 5.7, 8.4 |
| `@JoinTable` có khai `schema` không? Quan hệ `@ManyToMany` có đáng giữ không? | Bảng nối rơi vào schema `public`; cần cột phụ mà vẫn dùng `@ManyToMany` | Mục 8.1, 8.6 |
| Collection dùng `Set` (hoặc `@OrderColumn`) thay vì `List` kiểu bag không? | `List` trong `@ManyToMany`: đổi một phần tử là xóa cả dòng nối rồi insert lại | Mục 8.3 |
| `equals`, `hashCode`, `toString` có tránh dùng id sinh sau và tránh đọc quan hệ không? | `toString()` hoặc `hashCode()` đọc cả collection hai chiều | Mục 21.1, 21.3 |
| Entity có luồng đọc rồi sửa có `@Version` không? | Tồn kho, số dư, trạng thái được đọc rồi ghi mà không có version | Mục 17.1, 17.2, 22.2 |
| Id có dùng `IDENTITY` ở bảng sẽ insert hàng loạt không? | `GenerationType.IDENTITY` | Mục 18.2, 18.3 |

**Query và chuyển đổi dữ liệu**

| Câu hỏi | Dấu hiệu trong PR | Đọc lại ở |
|---|---|---|
| Màn hình danh sách đọc DTO hay đọc entity rồi duyệt? | Vòng lặp gọi `getX().getY()` trên quan hệ LAZY | Mục 11.4, 12.5, 22.1 |
| `Page` có `join fetch` collection thì có `countQuery` riêng không? | Không có `countQuery` thì tổng số bị sai (đếm dòng sau khi join) | Mục 12.3 |
| `join fetch` có điều kiện `where` trên chính alias được fetch không? | Collection thiếu dữ liệu, và persistence context giữ bản thiếu | Mục 12.1 |
| Có fetch hai `List` cùng lúc không? | `MultipleBagFetchException` | Mục 11.5 |
| Native query hoặc bulk `@Modifying` có `clear()` hay `refresh()` sau đó không? Bảng có `@Version` hay `@SoftDelete` không? | Entity đang MANAGED giữ giá trị cũ; `version` không tăng | Mục 15.1, 15.3 |

**Transaction và khóa**

| Câu hỏi | Dấu hiệu trong PR | Đọc lại ở |
|---|---|---|
| Có `catch` lỗi của một service `@Transactional` rồi đi tiếp không? | Transaction chung đã rollback-only nên về sau ném `UnexpectedRollbackException` | Mục 16.4 |
| Method `throws Exception` có khai `rollbackFor` không? | Checked exception mặc định vẫn COMMIT | Mục 16.5 |
| Lời gọi `this.methodKhac()` có cần transaction riêng không? | Self-invocation bỏ qua proxy nên mất `@Transactional` | Mục 16.3 |
| Hàng đợi nhiều worker có dùng `SKIP LOCKED` không? | `findFirstByStatus("NEW")` không khóa nên gửi trùng | Mục 17.5, 22.3 |
| Khi khóa nhiều dòng, có khóa theo thứ tự id không? | Hai transaction khóa ngược thứ tự thì deadlock | Mục 17.6 |
| Controller có chạm collection LAZY (Open Session In View) không? | `open-in-view` bật nên SQL chạy ngoài transaction và che mất N+1 | Mục 16.7 |

**Hiệu năng và kiểm thử**

| Câu hỏi | Dấu hiệu trong PR | Đọc lại ở |
|---|---|---|
| Import hay job nhiều dòng có `flush` cộng `clear` theo lô không? | Vòng `persist` dài không `clear()` nên persistence context phình | Mục 18.4, 22.4 |
| Cache cấp 2 chỉ dùng cho dữ liệu tham chiếu và chỉ ghi qua Hibernate không? | `@Cacheable` trên bảng mà một script khác cũng sửa | Mục 19.3, 19.5 |
| Có test đếm SQL cho use case mới không? | Use case danh sách hoặc báo cáo không có ngân sách SELECT | Mục 23.1, 22.1 |
| Test dữ liệu có đọc bằng JDBC, không bằng repository không? | Assert qua `findById` nên cache cấp 1 nói dối | Mục 2.7 |

### 23.4 Năm ADR mẫu

ADR (Architecture Decision Record) là một trang ngắn ghi lại **một quyết định**: bối cảnh vì sao phải quyết, quyết định là gì, hệ quả (cả lợi lẫn giá phải trả), và bằng chứng. Giá trị của ADR là người mới vào team đọc được **lý do** chứ không chỉ thấy quy tắc, và có thể phản biện bằng bằng chứng khi hoàn cảnh đổi.

Năm ADR dưới đây rút ra từ kết quả của lab. Chúng là **mẫu**: chép về, đổi theo hoàn cảnh của team rồi mới dùng. Mỗi ADR đi kèm chương trong sách đã chứng minh nó.

**ADR 1. Mọi quan hệ khai LAZY tường minh**

**Bối cảnh.** `@ManyToOne` và `@OneToOne` mặc định là EAGER. Câu JPQL `select o from PurchaseOrder o` không biết gì về EAGER, nên Hibernate chạy thêm một câu SELECT cho mỗi giá trị khác nhau của quan hệ (N+1). Lỗi này không báo gì, chỉ chậm dần khi dữ liệu lớn.

**Quyết định.** Mọi `@ManyToOne` và `@OneToOne` khai `fetch = FetchType.LAZY` tường minh (kể cả `@OneToMany` và `@ManyToMany`, dù đã mặc định LAZY, vẫn ghi để người đọc khỏi phải nhớ). Cần dữ liệu liên quan thì tải theo từng use case bằng `join fetch`, `@EntityGraph` hoặc DTO projection, không tải sẵn trong mapping.

**Hệ quả.** Lợi: không còn N+1 ngầm do mapping, mỗi use case tự quyết định tải gì. Giá: phải nghĩ về cách tải ở từng use case, và truy cập ngoài transaction gặp `LazyInitializationException`. LAZY cũng không cứu được N+1 khi lặp N phần tử rồi chạm quan hệ (cần ADR về ngân sách SQL). Có một ngoại lệ biết trước: phía `mappedBy` của `@OneToOne` không lazy được nếu không có bytecode enhancement.

**Rào chắn.** Lint mapping, quy tắc EAGER-TO-ONE và EAGER-COLLECTION (mục 23.2).

**Chương chứng minh.** Chương 11 (mục 11.2, 11.3, 11.4), Chương 7 và Chương 20 (ngoại lệ của `@OneToOne`), mục 22.1.

**ADR 2. Cấm cascade REMOVE và ALL trên ManyToOne và ManyToMany**

**Bối cảnh.** `cascade` là thao tác của `EntityManager`: `REMOVE` nghĩa là "xóa cha thì xóa luôn thứ được tham chiếu". Đặt nó ở phía con trỏ tới cha (`@ManyToOne`) hoặc ở quan hệ nhiều-nhiều thì xóa một bản ghi có thể xóa dữ liệu **dùng chung** như khách hàng, tag hay sản phẩm.

**Quyết định.** `cascade` chỉ đặt trên collection của **cha** để vòng đời của con theo cha (đơn hàng và dòng hàng): `{PERSIST, MERGE, REMOVE}` hoặc `ALL`. **Cấm** `REMOVE` và `ALL` trên `@ManyToOne` và `@ManyToMany`. Muốn xóa nhanh cả cây con thì cân nhắc `@OnDelete(CASCADE)` ở khóa ngoại (viết ADR riêng nếu dùng).

**Hệ quả.** Lợi: xóa một đơn không bao giờ kéo theo xóa khách hay tag dùng chung. Giá: phải tự quản lý vòng đời của thứ được tham chiếu, xóa ở nơi sở hữu nó. Cần nhớ: bulk JPQL và `deleteAllInBatch()` không đi qua cascade, và `@OnDelete` làm Hibernate không biết con đã mất.

**Rào chắn.** Lint mapping, quy tắc CASCADE-REMOVE (mục 23.2).

**Chương chứng minh.** Chương 5 (mục 5.5, 5.7), Chương 8 (mục 8.4), Chương 15 (mục 15.2).

**ADR 3. Entity có đọc rồi sửa dùng Version mặc định**

**Bối cảnh.** Hai transaction cùng đọc `stock = 10`, cùng trừ rồi ghi: giao dịch ghi sau đè giao dịch ghi trước (lost update). Không có exception nào, dữ liệu sai lặng lẽ. Dirty checking của Hibernate chỉ biết "khác với snapshot lúc tải", không biết database đã đổi từ lúc đó.

**Quyết định.** Entity nào có luồng "đọc, quyết định trong Java, ghi" (tồn kho, số dư, trạng thái đơn, hồ sơ) có cột `@Version`. Service bắt `OptimisticLockingFailureException` và **chạy lại cả transaction** (retry có giới hạn, ví dụ 3 lần); retry nằm **ngoài** transaction. Chỗ tranh chấp rất cao trên một dòng thì dùng `UPDATE ... set stock = stock - ? where stock >= ?` nguyên tử hoặc `PESSIMISTIC_WRITE`.

**Hệ quả.** Lợi: không giữ khóa, xung đột thành lỗi rõ ràng thay vì mất dữ liệu, và người đến sau đọc lại số mới nên bị chặn đúng bằng quy tắc nghiệp vụ. Giá: người gọi phải chấp nhận và test đường retry; tranh chấp cao thì retry nhiều, lúc đó đổi sang khóa hoặc UPDATE nguyên tử. Cần nhớ: `@Version` không tự tăng khi chỉ sửa collection inverse của cha, và native hay bulk update bỏ qua version.

**Rào chắn.** Test đua hai transaction kèm kiểm tra bất biến nghiệp vụ (mục 22.2.4).

**Chương chứng minh.** Chương 17 (mục 17.1, 17.2, 17.3), Chương 14 (mục 14.4), Chương 15 (mục 15.3), mục 22.2.

**ADR 4. Tắt Open Session In View, controller trả DTO**

**Bối cảnh.** Spring Boot mặc định bật `spring.jpa.open-in-view=true`: persistence context mở suốt request, nên controller hay serializer chạm quan hệ LAZY vẫn chạy được. Hệ quả: SQL chạy **ngoài transaction** ở tầng web, N+1 bị che (không còn `LazyInitializationException` để báo), và connection bị giữ lâu hơn cần thiết.

**Quyết định.** Đặt `spring.jpa.open-in-view: false`. Service trả **DTO** (hoặc entity đã tải đủ dữ liệu cần), không trả entity có quan hệ LAZY chưa khởi tạo ra ngoài ranh giới transaction.

**Hệ quả.** Lợi: lỗi tải thiếu dữ liệu hiện ngay thành `LazyInitializationException` thay vì N+1 âm thầm; SQL chỉ chạy trong transaction; connection nhả sớm. Giá: mỗi use case phải tải đủ dữ liệu trong service.

**Rào chắn.** Giữ `open-in-view: false` trong cấu hình chung (cả test), và có test gọi service rồi chạm quan hệ ngoài service để chắc nó vẫn ném `LazyInitializationException`.

**Chương chứng minh.** Chương 16 (mục 16.6, 16.7), Chương 11 (mục 11.3).

**ADR 5. Màn hình danh sách đọc DTO và có ngân sách SQL**

**Bối cảnh.** Mapping có thể hoàn toàn đúng (mọi quan hệ LAZY) mà use case vẫn N+1 vì service nạp entity rồi duyệt từng entity. Review mapping không thấy lỗi này, dữ liệu trả về cũng đúng; chỉ đếm SQL hoặc dữ liệu lớn mới lộ ra.

**Quyết định.** Một, use case danh sách và báo cáo chỉ đọc thì đọc **DTO projection** bằng một query, không nạp entity rồi duyệt. Hai, `Page` có `join fetch` collection thì luôn khai báo `countQuery` riêng. Ba, mỗi use case đọc quan trọng có **ngân sách SQL**: một test đặt trần số câu SELECT, vượt trần thì test đỏ kèm danh sách SQL thừa.

**Hệ quả.** Lợi: số câu SQL cố định theo use case, không tăng theo cỡ trang hay dữ liệu; N+1 không thể lọt qua review vì test đỏ. Giá: viết thêm DTO, query và một test ngân sách mỗi use case; khi cần thêm dữ liệu vào màn hình phải sửa cả ngân sách, và đó là chủ ý để người sửa nhìn lại chi phí.

**Rào chắn.** `SqlBudget` (mục 23.1), với test chứng minh cả hai chiều: đỏ trên đường lỗi, xanh trên đường đã sửa.

**Chương chứng minh.** Chương 11 (mục 11.4), Chương 12 (mục 12.3, 12.5), mục 22.1 và mục 23.1.
