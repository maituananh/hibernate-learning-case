## Chương 24. Tình huống thực tế chưa có trong lab

Hai mươi ba chương trước có một điểm chung: mọi kết luận đều đến từ một test chạy trên PostgreSQL thật, và bạn có thể chạy lại để tự kiểm tra. Chương này khác. Nó nói về những chủ đề mà dự án thật nào cũng gặp, nhưng nằm **ngoài phạm vi lab**: không có test, không có SQL do chính chúng ta bắt được, không có bảng dữ liệu "Trước" và "Sau".

Vì vậy chương này tuân theo ba quy ước, để bạn không nhầm kiến thức đã kiểm chứng với kiến thức tham khảo:

- Mỗi tình huống bắt đầu bằng khung **Chưa kiểm chứng trong lab**, kèm đường dẫn tới **tài liệu chính thức** làm nguồn. Hãy đọc tài liệu đó, đừng coi cuốn sách là nguồn duy nhất.
- Sách **không đưa số câu SQL cụ thể**. Số câu SQL phụ thuộc phiên bản Hibernate, cấu hình và mapping của bạn; chỉ test của bạn mới trả lời đúng.
- Cuối mỗi tình huống có câu hỏi "tự chạy thử": cách biến kiến thức tham khảo thành kiến thức đã kiểm chứng, bằng đúng phương pháp của lab (một test, một câu hỏi, assert bằng `JdbcTemplate` và bộ đếm SQL).

Bảy mục dưới đây đi từ hạ tầng (migration, khóa chính, cột JSON), qua nghiệp vụ chéo (auditing, multi-tenancy), tới hai bài toán ghép nhiều chương lại: khóa lạc quan qua REST, và sự cố "sửa giá nhưng khách vẫn thấy giá cũ".

**Trong JPA thuần.** Mỗi mục đều nói rõ phần nào là JPA chuẩn và phần nào là của Hibernate hoặc của Spring. Quy tắc chung: migration, auditing của Spring Data và REST không thuộc JPA; `@Version` và `GenerationType.UUID` (từ JPA 3.1) thuộc JPA; `@UuidGenerator`, `@JdbcTypeCode`, `@TenantId` và Envers là của Hibernate.

### 24.1 Flyway hoặc Liquibase thay cho ddl-auto

#### 24.1.1 Từ create sang migration có phiên bản {.mo-rong}

::: {.chua-kiem-chung}
Lab dùng `hibernate.hbm2ddl.auto` để Hibernate tự tạo bảng, vì mục tiêu là **nhìn thấy DDL mà mapping sinh ra**. Lab không có test nào về Flyway hay Liquibase (lab cố ý không dùng công cụ migration). Nguồn: tài liệu Spring Boot về khởi tạo database, <https://docs.spring.io/spring-boot/how-to/data-initialization.html>; tài liệu Flyway, <https://documentation.red-gate.com/flyway>; tài liệu Liquibase, <https://docs.liquibase.com/>.
:::

**Bối cảnh.** Ứng dụng của bạn sắp lên production. Tới giờ mỗi lần chạy test, bảng được tạo mới từ mapping nên không ai lo chuyện "database cũ". Production thì khác: bảng đã có hàng triệu dòng, và mỗi lần đổi entity bạn phải đổi cấu trúc bảng **mà không mất dữ liệu**. Cho Hibernate tự sửa schema trên production là đánh cược: nó có thể thêm cột, nhưng không thể biết bạn muốn đổi tên cột hay xóa cột rồi tạo cột mới, và không để lại dấu vết nào cho người sau.

**Kết quả.** Theo tài liệu, hướng đi phổ biến như sau.

- Mỗi thay đổi schema là một **file migration** có số phiên bản, ví dụ `V1__create_customer.sql`, `V2__add_phone.sql`. Công cụ (Flyway hoặc Liquibase) ghi vào một bảng riêng những file nào đã chạy, và chỉ chạy các file mới, theo thứ tự.
- Hibernate không còn tạo bảng. Cấu hình `hibernate.hbm2ddl.auto` thường đặt là `validate` (Hibernate kiểm tra bảng khớp mapping và dừng nếu lệch) hoặc `none`.
- Spring Boot tự chạy Flyway hoặc Liquibase khi khởi động, nếu thư viện có trên classpath.

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
```

Điểm đáng nhớ nhất là **thứ tự trách nhiệm đảo ngược** so với lab: ở lab, mapping sinh ra DDL; trên production, file migration là nguồn sự thật về schema, còn mapping phải khớp với nó. Do đó `validate` quan trọng: nó biến việc "mapping lệch schema" từ lỗi gặp lúc chạy thành lỗi lúc khởi động.

**Tự chạy thử.** Viết một test khởi động ứng dụng với Flyway và `validate`, rồi cố ý bỏ một cột khỏi file migration. Câu hỏi: Hibernate báo lỗi lúc nào, và thông báo có chỉ ra cột nào thiếu không?

::: {.ghi-nho}
Lab dùng Hibernate để sinh schema nhằm học mapping. Production cần migration có phiên bản, còn Hibernate chỉ kiểm tra (`validate`). Đây là kiến thức tham khảo, chưa được kiểm chứng trong lab.
:::

### 24.2 UUID làm khóa chính

#### 24.2.1 UUID thay cho SEQUENCE {.mo-rong}

::: {.chua-kiem-chung}
Toàn bộ lab dùng `SEQUENCE` (mục 18.1 giải thích vì sao nó hợp với batch) và có vài tình huống id gán tay (mục 2.9). Không có test nào dùng UUID. Nguồn: Hibernate User Guide, phần identifiers, <https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#identifiers>; đặc tả Jakarta Persistence, <https://jakarta.ee/specifications/persistence/>.
:::

**Bối cảnh.** Nhóm bạn muốn id sinh được ở phía ứng dụng, không cần hỏi database (để tạo entity ở nhiều service, hoặc để lộ id ra ngoài mà không lộ số lượng bản ghi như id tăng dần). UUID là lựa chọn quen thuộc.

**Kết quả.** Theo tài liệu:

- JPA 3.1 có `@GeneratedValue(strategy = GenerationType.UUID)`; Hibernate có thêm `@UuidGenerator` để chọn cách sinh UUID.
- PostgreSQL có kiểu cột `uuid` riêng, và Hibernate ánh xạ `java.util.UUID` vào đó. Đừng lưu UUID dạng chuỗi nếu không cần: cột `varchar(36)` tốn chỗ hơn và so sánh chậm hơn.
- UUID ngẫu nhiên làm các dòng mới rơi vào **vị trí ngẫu nhiên** trong index khóa chính, khác với `SEQUENCE` luôn thêm vào cuối. Hệ quả thực tế với bảng lớn là điều bạn nên đo, không nên đoán. Hibernate cho chọn kiểu UUID sắp theo thời gian để giảm vấn đề này (chi tiết từng kiểu phụ thuộc phiên bản, xem tài liệu).

```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;
```

Có một điều liên quan trực tiếp tới các chương trước: **id sinh ở phía ứng dụng** nên Hibernate biết id ngay khi `persist`, giống `SEQUENCE`; nhưng với id **gán tay**, Spring Data không phân biệt được entity mới với entity cũ (mục 2.9). Nếu bạn tự gán `UUID.randomUUID()` thay vì để Hibernate sinh, hãy đọc lại mục đó.

**Tự chạy thử.** Viết một test đo: insert cùng số lượng dòng với `SEQUENCE` và với UUID, rồi so sánh số câu SQL và kích thước index. Câu hỏi: batch insert (mục 18.1) còn hoạt động với UUID không?

::: {.ghi-nho}
UUID cho phép sinh id ở phía ứng dụng, đổi lại index khóa chính kém gọn hơn. Hãy dùng kiểu cột `uuid` của PostgreSQL và tự đo trên dữ liệu của bạn. Chưa kiểm chứng trong lab.
:::

### 24.3 Cột JSON

#### 24.3.1 Lưu thuộc tính linh hoạt vào cột jsonb {.mo-rong}

::: {.chua-kiem-chung}
Lab chưa có test về cột JSON. Nguồn: Hibernate User Guide, phần basic types (mục JSON), <https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#basic>; tài liệu PostgreSQL về kiểu JSON, <https://www.postgresql.org/docs/current/datatype-json.html>.
:::

**Bối cảnh.** Sản phẩm của bạn có "thuộc tính tùy loại": áo có kích cỡ và màu, điện thoại có dung lượng và chip. Tạo một cột cho mỗi thuộc tính thì bảng phình ra; tạo bảng thuộc tính riêng thì phải join. Cột JSON cất cả bộ thuộc tính vào một ô.

**Kết quả.** Theo tài liệu:

- Hibernate 6 trở lên ánh xạ một field sang cột JSON bằng `@JdbcTypeCode(SqlTypes.JSON)`. Hibernate cần một thư viện JSON trên classpath (ví dụ Jackson) để chuyển qua lại giữa object và chuỗi JSON.
- PostgreSQL có hai kiểu: `json` (lưu nguyên văn) và `jsonb` (lưu dạng nhị phân, có index). Thường dùng `jsonb`.
- Field có thể là `Map<String, Object>` hoặc một class riêng của bạn.

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "attributes", columnDefinition = "jsonb")
private Map<String, Object> attributes;
```

Điều cần nghĩ kỹ nằm ngoài cú pháp: cột JSON **không có khóa ngoại, không có ràng buộc kiểu** cho từng thuộc tính bên trong, và JPQL không truy vấn sâu vào trong JSON một cách chuẩn (muốn lọc theo thuộc tính con, thường phải dùng hàm riêng của PostgreSQL qua native query hoặc hàm Hibernate cung cấp, tùy phiên bản). Dữ liệu cần join, cần ràng buộc, cần báo cáo thì vẫn nên là cột và bảng thật, như mọi chương trước đã làm.

**Tự chạy thử.** Viết test lưu một `Map` có hai thuộc tính, đọc lại bằng `JdbcTemplate` để thấy chuỗi JSON thật trong cột. Câu hỏi: sửa một giá trị trong map rồi flush, Hibernate có nhận ra entity bị sửa (dirty checking, mục 2.3) không?

::: {.ghi-nho}
Cột JSON hợp với dữ liệu linh hoạt, ít join. Nó không thay được bảng quan hệ khi bạn cần khóa ngoại và ràng buộc. Chưa kiểm chứng trong lab.
:::

### 24.4 Auditing: CreatedDate và Envers

#### 24.4.1 Ai tạo, ai sửa, và lịch sử thay đổi {.mo-rong}

::: {.chua-kiem-chung}
Lab chưa có test về auditing. Nguồn: tài liệu Spring Data JPA về auditing, <https://docs.spring.io/spring-data/jpa/reference/auditing.html>; Hibernate User Guide, phần Envers, <https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#envers>.
:::

**Bối cảnh.** Bộ phận vận hành hỏi hai câu khác nhau: "Đơn này tạo lúc nào, bởi ai, sửa lần cuối lúc nào?" và "Giá sản phẩm này từng là bao nhiêu hồi tháng trước?". Câu đầu cần **dấu vết hiện tại**; câu sau cần **lịch sử đầy đủ**. Hai công cụ khác nhau trả lời hai câu này.

**Kết quả.** Theo tài liệu:

- **Auditing của Spring Data** thêm vài cột vào chính bảng của entity: `@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`, `@LastModifiedBy`. Bật bằng `@EnableJpaAuditing` và `@EntityListeners(AuditingEntityListener.class)` trên entity. Nó chỉ giữ **giá trị mới nhất**.
- **Hibernate Envers** giữ **mọi phiên bản**: đánh dấu entity bằng `@Audited`, Envers tạo thêm bảng lịch sử (mặc định tên có hậu tố `_AUD`) và một bảng ghi số revision. Nó cần thêm thư viện `hibernate-envers`.

```java
@Entity
@EntityListeners(AuditingEntityListener.class)
public class Customer {
    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
```

Một điểm liên quan tới Chương 15: auditing và Envers hoạt động qua **sự kiện của Hibernate** khi nó flush entity. Câu UPDATE hàng loạt hay native UPDATE đi vòng qua entity (mục 15.3) nên **không** kích hoạt `@LastModifiedDate`. Đây là suy luận từ cơ chế đã kiểm chứng ở chương đó, nhưng chính việc auditing bỏ sót hay không, bạn nên tự kiểm tra bằng test.

**Tự chạy thử.** Viết test sửa giá một sản phẩm bằng entity MANAGED, rồi lặp lại bằng bulk UPDATE. Câu hỏi: cột `updatedAt` đổi trong trường hợp nào?

::: {.ghi-nho}
Auditing của Spring Data lưu dấu vết mới nhất ngay trong bảng; Envers lưu toàn bộ lịch sử trong bảng riêng. Cả hai dựa vào entity đi qua flush, nên bulk và native UPDATE cần được kiểm tra riêng. Chưa kiểm chứng trong lab.
:::

### 24.5 Multi-tenancy

#### 24.5.1 Nhiều khách hàng thuê chung một ứng dụng {.mo-rong}

::: {.chua-kiem-chung}
Lab chưa có test về multi-tenancy. Nguồn: Hibernate User Guide, phần multitenancy, <https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#multitenacy>.
:::

**Bối cảnh.** Bạn bán phần mềm cho nhiều công ty (tenant). Dữ liệu của công ty A tuyệt đối không được lọt sang công ty B, nhưng bạn không muốn chạy một bản ứng dụng riêng cho mỗi công ty.

**Kết quả.** Theo tài liệu, Hibernate hỗ trợ ba cách tách dữ liệu:

| Cách | Tách ở đâu | Ý chính |
|---|---|---|
| Database riêng | Mỗi tenant một database | Cô lập mạnh nhất, vận hành tốn kém nhất |
| Schema riêng | Mỗi tenant một schema trong cùng database | Cô lập khá, giống cách lab tách mỗi biến thể một schema |
| Cột phân biệt | Mọi tenant chung bảng, mỗi dòng có cột tenant | Rẻ nhất, nhưng mọi truy vấn phải lọc đúng cột |

Với cách thứ ba, Hibernate có `@TenantId` đặt trên một field của entity, và nó tự thêm điều kiện lọc vào truy vấn. Để Hibernate biết tenant hiện tại là ai, bạn cung cấp một `CurrentTenantIdentifierResolver`, thường đọc từ thông tin đăng nhập của request.

```java
@TenantId
@Column(name = "tenant_id")
private String tenantId;
```

Nếu bạn đã đọc Chương 13, cách thứ ba trông quen: nó cùng họ với `@Filter` và `@SQLRestriction`, tức là lọc dữ liệu ngay trong mapping. Cũng giống các cơ chế đó, hãy nhớ rằng **native query và bulk UPDATE phải tự lọc tenant** nếu bạn không kiểm tra lại. Đây là suy luận từ Chương 13 và 15, cần được test xác nhận trên phiên bản của bạn.

**Tự chạy thử.** Viết test với hai tenant, tạo dữ liệu cho cả hai, rồi truy vấn dưới tenant A bằng JPQL và bằng native SQL. Câu hỏi: truy vấn nào lọt dữ liệu của tenant B?

::: {.ghi-nho}
Multi-tenancy là chọn mức cô lập: database riêng, schema riêng, hoặc cột phân biệt. Cách cột phân biệt rẻ nhưng dễ rò rỉ nhất khi có truy vấn đi vòng qua cơ chế lọc. Chưa kiểm chứng trong lab.
:::

### 24.6 Optimistic lock qua REST

#### 24.6.1 Đưa version vào DTO hoặc ETag {.mo-rong}

::: {.chua-kiem-chung}
Mục 17.3 đã kiểm chứng `@Version` khi **hai transaction chạy song song trong một process**. Lab không có tầng web (dự án cấm Web và Controller), nên không có test cho đường đi qua HTTP. Nguồn: Hibernate User Guide, phần locking, <https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#locking-optimistic>; đặc tả HTTP về tiêu đề `If-Match`, <https://www.rfc-editor.org/rfc/rfc9110.html#name-if-match>.
:::

**Bối cảnh.** Hai nhân viên cùng mở trang sửa sản phẩm. Họ nhìn thấy cùng một giá. Nhân viên thứ nhất lưu trước, nhân viên thứ hai lưu sau và **ghi đè** thay đổi của người đầu mà không biết. Mục 17.3 cho thấy `@Version` chặn được việc này. Nhưng ở đó hai bản entity đều nằm trong một JVM. Qua REST, giữa lúc "xem" và lúc "lưu" là hai request HTTP riêng, và entity đã DETACHED từ lâu.

**Kết quả.** Điều then chốt: `@Version` chỉ bảo vệ được nếu **số version mà người dùng đã thấy** quay về tới câu UPDATE. Nếu server lúc lưu chỉ đọc entity mới nhất từ database rồi áp thay đổi lên, version đó luôn là version mới nhất, và việc ghi đè xảy ra như thường. Theo tài liệu và cách làm phổ biến:

- Trả version cho client trong DTO (hoặc trong tiêu đề `ETag`). Client gửi lại version đó khi lưu (trong body, hoặc tiêu đề `If-Match`).
- Server so version nhận được với version hiện tại. Nếu lệch, từ chối. Hoặc gán version nhận được vào entity rồi để `merge` (mục 2.4) làm phần còn lại: câu UPDATE có điều kiện `where version = ?` sẽ không khớp dòng nào, và Hibernate ném exception khóa lạc quan.
- Exception đó cần được dịch thành mã HTTP. Với `If-Match` không khớp, đặc tả HTTP dùng 412 Precondition Failed; nhiều API dùng 409 Conflict. Cả hai đều chấp nhận được, miễn là client biết cách xử lý (tải lại dữ liệu, cho người dùng chọn giữ bản nào).

```java
public record ProductUpdateRequest(Long id, BigDecimal price, Long version) { }
```

Bẫy dễ gặp nhất không nằm ở Hibernate mà ở cách viết service: nếu service tải entity theo id rồi gọi `setPrice(...)` và **bỏ qua** field `version` của DTO, mọi cơ chế khóa lạc quan đều im lặng, giống hệt khi bạn quên `@Version`. Chương 15 cho thấy một cách khác khiến `@Version` im lặng: native UPDATE không tăng version (mục 15.3).

**Tự chạy thử.** Viết test hai bước dùng `inTx(...)` riêng: bước một đọc DTO (có version), bước hai và ba là hai lần lưu lần lượt với cùng version cũ. Câu hỏi: lần lưu thứ hai bị từ chối ở đâu, và exception là gì?

::: {.ghi-nho}
`@Version` chỉ có tác dụng khi version người dùng đã thấy đi cùng yêu cầu lưu. Qua REST, bạn phải đưa version vào DTO hoặc `ETag`, và dịch exception thành 409 hoặc 412. Chưa kiểm chứng trong lab.
:::

### 24.7 Sửa giá nhưng khách vẫn thấy giá cũ

#### 24.7.1 Cache cấp 2 và câu UPDATE mà Hibernate không biết {.mo-rong}

::: {.chua-kiem-chung codes="G14-I5"}
Đây là sự cố thứ năm của nhóm sự cố (Chương 22 chỉ làm bốn sự cố đầu), và lab **chưa làm** nó. Hai nền tảng của nó đã được kiểm chứng riêng, ở mục 15.3 và 19.3; việc ghép lại thành một sự cố hoàn chỉnh thì chưa. Nguồn: Hibernate User Guide, phần caching, <https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#caching>.
:::

**Bối cảnh.** Quản trị viên sửa giá một sản phẩm từ 10.00 lên 12.00 và thấy màn hình quản trị báo thành công. Nhưng một số khách vẫn thấy giá 10.00 trên trang sản phẩm, có khi hàng giờ. Kiểm tra bảng `product` thì giá đã là 12.00.

**Kết quả.** Triệu chứng này là dấu hiệu quen thuộc của **dữ liệu trong cache lệch dữ liệu trong database**. Hai chương trước đã cho bạn các mảnh ghép đã kiểm chứng:

- Mục 19.3: khi Hibernate chạy một câu mà nó không phân tích được, nó phải chọn cách dọn cache. Với native UPDATE **không khai báo bảng**, kết quả đã kiểm chứng là cả cache cấp 2 bị xóa (mục 19.3.2); khai báo bảng bị đụng thì chỉ cache của entity đó bị xóa (mục 19.3.3). Nghĩa là nếu quản trị viên sửa giá **qua Hibernate** bằng native query, Hibernate có cơ hội dọn cache.
- Mục 15.3: native UPDATE không tăng `@Version` và không làm mới entity đang MANAGED. Một transaction đang giữ bản cũ vẫn có thể ghi đè giá mới mà không bị chặn.

Từ đó rút ra những hướng điều tra cho sự cố này. Chúng là **giả thuyết cần kiểm tra**, không phải kết luận:

- Giá bị sửa bởi **thứ gì đó ngoài Hibernate** (một script SQL chạy tay, một service khác, `JdbcTemplate` trong cùng ứng dụng). Hibernate không thấy câu đó nên không dọn cache nào. Cache vẫn giữ giá cũ cho tới khi hết hạn hoặc bị loại bỏ.
- Ứng dụng chạy **nhiều node**, mỗi node có cache cấp 2 cục bộ. Sửa giá ở node này không dọn cache của node kia, trừ khi nhà cung cấp cache hỗ trợ phân tán và bạn đã cấu hình.
- Thời gian sống của cache (time to live) đặt quá dài so với tần suất sửa giá.

Cách chẩn đoán gợi ý theo phương pháp của Chương 22: tái hiện bằng một test (nạp cache, sửa giá bằng một đường đi vòng qua Hibernate, đọc lại bằng `em.find`), rồi so sánh với `JdbcTemplate` để thấy cache đang nói dối. Cách sửa tùy nguyên nhân: sửa giá luôn qua một đường duy nhất có dọn cache, khai báo bảng bị đụng cho native query, hoặc giảm hay bỏ cache cho dữ liệu thay đổi thường xuyên như giá.

**Tự chạy thử.** Thêm sự cố này vào nhóm sự cố của lab theo khuôn bốn bước: tái hiện, chẩn đoán, sửa, rào chắn. Hãy ghi lại thực tế vào `docs/RESULTS.md` nếu kết quả khác điều bạn đoán ở trên.

::: {.ghi-nho}
Giá cũ sau khi sửa là triệu chứng của cache cấp 2 không biết về câu UPDATE. Các mục 15.3 và 19.3 đã kiểm chứng từng mảnh; sự cố ghép hoàn chỉnh vẫn chưa kiểm chứng trong lab.
:::
