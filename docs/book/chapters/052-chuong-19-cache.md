## Chương 19. Second-level cache và query cache

Mỗi lần đọc một sản phẩm, ứng dụng hỏi database. Nếu 10 000 khách cùng xem một sản phẩm, đó là 10 000 câu SELECT giống hệt nhau cho một dòng dữ liệu hầu như không đổi. Cache sinh ra để tránh việc đó: lần đầu hỏi database, các lần sau lấy từ bộ nhớ.

Chương này trả lời hai câu hỏi, theo thứ tự khó dần:

1. **Cache giúp ở đâu?** Entity (mục 19.2), collection (mục 19.4), kết quả query (mục 19.5).
2. **Cache hại ở đâu?** Dữ liệu cũ khi database bị sửa ngoài tầm nhìn của Hibernate (mục 19.2.2 và 19.5.2), cache bị vô hiệu quá tay hoặc không đủ khi dùng bulk và native (mục 19.3), và cache collection không cứu được N+1 (mục 19.4).

Cache là chỗ sinh ra những sự cố khó chịu nhất, vì mọi thứ chạy đúng trong test và chỉ sai khi có một nguồn thay đổi dữ liệu mà cache không biết. Vì vậy mỗi mục dưới đây đều cố tình cho thấy cả mặt giúp lẫn mặt hại.

**Trong JPA thuần.** JPA chuẩn chỉ có `@Cacheable` (đánh dấu entity được phép vào cache cấp 2) và thuộc tính `shared-cache-mode`. Mọi thứ còn lại trong chương là của Hibernate: `@Cache` với chiến lược đồng bộ (`READ_WRITE`, `READ_ONLY`), cache collection, query cache và hint `org.hibernate.cacheable`. JPA không có query cache; nó chỉ có `jakarta.persistence.Cache` để xóa cache bằng tay. Từng mục nêu rõ phần nào là chuẩn, phần nào là riêng.

### 19.1 Second-level cache hoạt động thế nào {.kien-thuc-nen}

Hibernate có **hai tầng cache** cho entity, và nhầm lẫn giữa chúng là nguồn gốc của nhiều hiểu lầm:

| | Cache cấp 1 (persistence context) | Cache cấp 2 (second-level cache) |
|---|---|---|
| Sống bao lâu | Một transaction (một session) | Cả ứng dụng, giữa mọi transaction |
| Dùng chung giữa các request | Không | Có |
| Bật thế nào | Luôn bật, không tắt được | Phải bật và phải chọn entity |
| Chứa gì | Chính các object entity đang MANAGED | Dữ liệu đã tách thành từng cột (dạng "đóng băng") |

Thứ tự tra cứu khi bạn gọi `find`: persistence context trước (cấp 1), rồi mới tới cache cấp 2, rồi mới xuống database. Vì vậy trong **cùng một transaction**, cache cấp 1 luôn trả lời trước và bạn không thấy tác dụng của cache cấp 2. Mọi thí nghiệm trong chương này đều **tải lần hai ở một transaction khác**, để persistence context của lần hai rỗng và cache cấp 2 mới phải lên tiếng.

Cách lab cấu hình (và bạn cần cấu hình tương tự nếu muốn dùng):

- Thêm thư viện `hibernate-jcache` và một provider JCache (lab dùng Caffeine). Hibernate chỉ định nghĩa giao diện; bộ nhớ thật do provider quản lý.
- Đặt `shared-cache-mode` thành `ENABLE_SELECTIVE`. Chế độ này nghĩa là **chỉ entity có `@Cacheable` mới vào cache**, và query chỉ vào query cache khi bạn đòi tường minh. Lab chọn chế độ này để các thí nghiệm ở chương khác không bị cache làm lệch.
- Mỗi entity muốn cache khai `@Cacheable` và `@Cache(usage = ...)`. Giá trị `usage` là chiến lược đồng bộ: `READ_WRITE` cho dữ liệu có thể sửa (Hibernate khóa mục cache khi sửa để tránh đọc phải dữ liệu nửa vời), `READ_ONLY` cho dữ liệu không bao giờ sửa (mục 19.6).

Một điều mọi tình huống sau đều dựa vào: **cache chỉ biết những gì đi qua Hibernate**. Khi thay đổi đi qua Hibernate (dirty checking, `remove`, bulk JPQL như mục 19.3.1), Hibernate vừa ghi database vừa cập nhật hoặc xóa mục cache liên quan. Khi ai đó sửa database bằng đường khác (script SQL, ứng dụng khác, công cụ quản trị), Hibernate không biết, và cache giữ nguyên dữ liệu cũ.

### 19.2 Entity cache

#### 19.2.1 find ở transaction thứ hai: 0 SELECT, cache hit {.cot-loi}

**Bối cảnh.** Trang chi tiết sản phẩm là trang được xem nhiều nhất, và sản phẩm hiếm khi đổi. Mỗi lượt xem hiện là một câu SELECT. Bạn muốn biết cache cấp 2 giúp được bao nhiêu và cách chứng minh nó có chạy.

**Khi nào xảy ra.** Entity có `@Cacheable` và `@Cache(usage = READ_WRITE)`, và lần tải thứ hai nằm ở một transaction khác lần đầu.

**Khi nào không xảy ra.** Khi lần tải thứ hai nằm trong cùng transaction (cache cấp 1 trả lời), hoặc khi entity không có `@Cacheable`.

**Ví dụ.**

```java
@Entity(name = "G13K1_Product")
@Table(schema = "g13_k1_entity_cache", name = "product")
@Cacheable                                          // được phép vào cache cấp 2
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE) // chiến lược đồng bộ
public class K1Product {
    @Id
    private Long id;
    private String name;
    private int price;
}
```

```java
inTx(() -> em.find(K1Product.class, 1L)); // lần 1: SELECT, rồi ghi vào cache
inTx(() -> {
    // transaction MỚI, persistence context MỚI: hỏi cache cấp 2
    K1Product product = em.find(K1Product.class, 1L);
    product.getName(); // vẫn đủ dữ liệu
});
```

**Kết quả.** Transaction thứ nhất có **một câu SELECT** product. Transaction thứ hai có **không câu SQL nào**, mà dữ liệu vẫn đầy đủ (tên "Pen").

| | Số SELECT product | Ghi vào cache | Cache hit |
|---|---|---|---|
| Transaction thứ nhất | 1 | 1 lần | 0 |
| Transaction thứ hai | **0** | 0 | **1** |

Thay đổi: không có dòng nào trong database đổi. Giữ nguyên: toàn bộ dữ liệu.

**Vì sao.** Lần đầu, cache cấp 2 chưa có gì (miss), nên Hibernate xuống database, rồi **ghi** dữ liệu vào cache. Lần hai, persistence context mới rỗng, Hibernate hỏi cache cấp 2, thấy có (hit) và dựng entity từ dữ liệu đó mà không cần database. Hibernate có bộ đếm thống kê cho việc này (hit, miss, put); test dùng chúng để chứng minh "đúng một lần hit, đúng một lần put" thay vì chỉ tin vào việc không thấy SQL.

**Trong JPA thuần.** `@Cacheable` là JPA chuẩn. `@Cache(usage = ...)` và việc chọn `READ_WRITE` là của Hibernate; JPA không có khái niệm chiến lược đồng bộ. Provider khác có thể có cách riêng.

::: {.ghi-nho}
Cache cấp 2 chỉ phát huy khi tải ở transaction khác. Lần đầu miss và ghi vào cache; lần sau hit và không cần SELECT.
:::

::: {.tracuu codes="G13-S06"}
:::

#### 19.2.2 Sửa database ngoài Hibernate: find vẫn trả giá cũ {.cot-loi}

**Bối cảnh.** Phòng kinh doanh nhờ DBA chạy một câu SQL sửa giá sản phẩm trực tiếp vào database cho nhanh. Ở ứng dụng, khách vẫn thấy giá cũ, dù database đã đúng. Restart ứng dụng thì giá mới hiện ra. Đây là chuỗi sự cố "admin sửa giá mà khách vẫn thấy giá cũ" mà Chương 22 sẽ kể lại như một sự cố hoàn chỉnh.

**Khi nào xảy ra.** Entity đã nằm trong cache cấp 2, rồi database bị sửa bằng đường **không qua Hibernate** (JDBC thuần, script SQL, ứng dụng khác).

**Khi nào không xảy ra.** Khi mọi thay đổi đều đi qua Hibernate (Hibernate tự cập nhật cache), hoặc khi mục cache hết hạn.

**Ví dụ.**

```java
inTx(() -> em.find(K1Product.class, 1L)); // vào cache, giá 10

// sửa NGOÀI Hibernate
jdbc.update("update g13_k1_entity_cache.product set price = 99 where id = 1");

inTx(() -> {
    K1Product product = em.find(K1Product.class, 1L);
    product.getPrice(); // vẫn 10
});
```

**Kết quả.** `find` ở transaction sau trả **giá cũ (10)** và **không có câu SELECT nào**. Trong khi đó database thật đã là 99.

| Giá của sản phẩm | Giá trị |
|---|---|
| Database thật | **99** |
| Giá mà `find` trả về | **10** |

Thay đổi: giá trong database (99). Giữ nguyên: giá trong cache (10).

**Vì sao.** Cache cấp 2 chỉ được làm mới hoặc xóa khi thay đổi đi **qua Hibernate**. Câu `update` của JDBC đi thẳng tới database, Hibernate không nhìn thấy, nên mục cache vẫn nguyên và tiếp tục được phục vụ. Cache không tự hỏi lại database xem dữ liệu còn đúng không.

**Cách tránh.** Đừng cache bảng bị hệ thống khác sửa, hoặc đặt thời hạn sống ngắn cho mục cache và chấp nhận dữ liệu cũ trong khoảng đó, hoặc xóa cache có chủ đích sau khi sửa ngoài (qua API `Cache` của JPA hoặc của Hibernate). Quy tắc gọn: **chỉ cache dữ liệu mà ứng dụng này là nơi duy nhất ghi**.

::: {.ghi-nho}
Cache cấp 2 không biết database bị sửa ngoài Hibernate. Dữ liệu cũ nằm đó cho tới khi mục cache bị xóa hoặc hết hạn.
:::

::: {.tracuu codes="G13-S06b"}
:::

### 19.3 Bulk và native làm hỏng cache

Chương 15 đã cho thấy bulk update đi vòng qua persistence context. Với cache cấp 2 còn một câu hỏi nữa: Hibernate xử lý cache ra sao khi nó chạy một câu mà nó **không** theo dõi từng entity? Ba tình huống dưới đây đều bắt đầu từ cùng một trạng thái: cả một sản phẩm và một thương hiệu (hai entity khác nhau, đều `@Cacheable`) đã nằm trong cache. Sau đó có một câu UPDATE đổi giá của sản phẩm.

#### 19.3.1 Bulk JPQL UPDATE: chỉ cache của entity bị đụng tới bị xóa {.cot-loi}

**Bối cảnh.** Bạn viết câu bulk "tăng giá toàn bộ sản phẩm" cho đợt khuyến mãi. Bạn lo rằng sau đó cache cũ sẽ trả giá cũ, hoặc tệ hơn, cả cache thương hiệu cũng bị xóa oan.

**Khi nào xảy ra.** Bạn chạy `update ... ` bằng JPQL (hoặc HQL) từ `em.createQuery(...).executeUpdate()`.

**Khi nào không xảy ra.** Khi bạn chạy câu native (mục 19.3.2 và 19.3.3), hoặc khi sửa bằng cách tải entity rồi `setPrice` (dirty checking, Hibernate tự cập nhật cache).

**Ví dụ.**

```java
inTx(() -> em.createQuery("update G13K1_Product p set p.price = 99")
        .executeUpdate()); // bulk: bỏ qua persistence context, đi thẳng DB
inTx(() -> {
    K1Product product = em.find(K1Product.class, 1L); // thấy giá mới
    em.find(K1Brand.class, 1L);                       // vẫn từ cache
});
```

**Kết quả.** Sau bulk update, `find` product phải **SELECT lại** (và thấy giá 99), còn `find` brand **không có SELECT nào**.

```sql
select kp1_0.id, kp1_0.name, kp1_0.price
from g13_k1_entity_cache.product kp1_0
where kp1_0.id=?
```

| Entity | Số SELECT sau bulk update |
|---|---|
| product | **1** (cache bị xóa) |
| brand | **0** (cache còn nguyên) |

Thay đổi: giá product thành 99. Giữ nguyên: brand và cache của brand.

**Vì sao.** Hibernate **phân tích** câu JPQL nên biết nó chạm vào bảng product. Nó xóa vùng cache của entity Product (không xóa vùng khác), nên lần `find` sau không có gì trong cache và phải xuống database. Đây là hành vi đúng mà bạn muốn: Hibernate tự làm đúng việc dọn cache cho bulk JPQL.

::: {.ghi-nho}
Bulk JPQL tự xóa đúng vùng cache của entity bị đụng tới, không xóa vùng khác.
:::

::: {.tracuu codes="G13-S07a"}
:::

#### 19.3.2 Native UPDATE không khai báo bảng: cả cache bị xóa {.cot-loi}

**Bối cảnh.** Câu cập nhật của bạn quá phức tạp cho JPQL, nên bạn viết native SQL. Không ai nói với bạn rằng Hibernate xử lý cache cho native khác hẳn.

**Khi nào xảy ra.** Bạn chạy `em.createNativeQuery("update ...").executeUpdate()` mà không khai báo bảng bị đụng tới.

**Khi nào không xảy ra.** Khi bạn khai báo bảng bị đụng (mục 19.3.3), hoặc khi dùng JPQL (mục 19.3.1).

**Ví dụ.**

```java
inTx(() -> em.createNativeQuery(
        "update g13_k1_entity_cache.product set price = 99")
    .executeUpdate());
inTx(() -> {
    em.find(K1Product.class, 1L);
    em.find(K1Brand.class, 1L);
});
```

**Kết quả.** Cả product **và** brand đều phải SELECT lại.

| Entity | Số SELECT sau native update |
|---|---|
| product | **1** |
| brand | **1** (đáng lẽ vô can) |

Thay đổi: cache của cả hai entity đều bị xóa. Giữ nguyên: dữ liệu brand trong database không đổi, vậy mà cache của nó mất.

**Vì sao.** Hibernate **không phân tích được** câu SQL native (đó là chuỗi thô). Nó không biết bảng nào bị đổi, nên chọn phương án an toàn: xóa **toàn bộ** cache cấp 2. An toàn, nhưng đắt: một câu native nhỏ làm mất tác dụng của mọi cache trong ứng dụng, và lúc đó hàng loạt câu SELECT bất ngờ dội xuống database.

::: {.ghi-nho}
Native UPDATE không khai báo bảng làm Hibernate xóa toàn bộ cache cấp 2, kể cả entity không liên quan.
:::

::: {.tracuu codes="G13-S07b"}
:::

#### 19.3.3 Native UPDATE khai báo addSynchronizedEntityClass: chỉ xóa cache của Product {.cot-loi}

**Bối cảnh.** Bạn đã biết mục 19.3.2 và không muốn một câu native xóa sạch cache. Bạn cần nói cho Hibernate biết câu SQL chỉ chạm vào bảng nào.

**Khi nào xảy ra.** Bạn gọi `addSynchronizedEntityClass(...)` (hoặc khai báo query space tương đương) trên native query.

**Khi nào không xảy ra.** Khi bạn quên khai báo (mục 19.3.2).

**Ví dụ.**

```java
inTx(() -> {
    NativeQuery<?> update = em
        .createNativeQuery("update g13_k1_entity_cache.product set price = 99")
        .unwrap(NativeQuery.class);
    // "câu SQL này chỉ đụng bảng của Product"
    update.addSynchronizedEntityClass(K1Product.class);
    update.executeUpdate();
});
```

**Kết quả.** Chỉ cache của Product bị xóa: product SELECT lại **một lần**, brand **không có SELECT nào**, y như bulk JPQL ở mục 19.3.1.

| Entity | Số SELECT sau native update có khai báo |
|---|---|
| product | **1** |
| brand | **0** |

Thay đổi: cache của Product bị xóa. Giữ nguyên: cache của Brand.

**Vì sao.** Hibernate gọi phần khai báo này là **query space**: tập các bảng mà câu SQL đụng tới. Khi bạn khai báo, Hibernate chỉ xóa vùng cache thuộc các bảng đó. Việc khai báo là trách nhiệm của bạn: nếu bạn khai thiếu (câu SQL thật sự đổi cả bảng khác), cache của bảng bị bỏ sót sẽ trả dữ liệu cũ.

**Trong JPA thuần.** `addSynchronizedEntityClass` là của Hibernate (`NativeQuery`), JPA không có tương đương. Trong JPA thuần, sau native update bạn tự xóa cache bằng `em.getEntityManagerFactory().getCache().evict(Product.class)`.

::: {.ghi-nho}
Native UPDATE nên khai báo bảng bị đụng tới để chỉ xóa đúng vùng cache. Khai thiếu thì cache của bảng bị bỏ sót trả dữ liệu cũ.
:::

::: {.tracuu codes="G13-S07c"}
:::

### 19.4 Collection cache

![Sơ đồ bảng category và entry, dùng cho thí nghiệm cache collection](img/g13-k2.png){width=60%}

#### 19.4.1 Cache collection chỉ lưu id: phần tử không cache thì vẫn N lần SELECT {.cot-loi}

**Bối cảnh.** Mỗi danh mục có vài chục mục con, và trang danh mục nào cũng liệt kê toàn bộ mục con. Bạn bật cache cho collection `entries` để hết N+1 (Chương 11). Số câu SELECT mở collection đúng là biến mất, nhưng trang vẫn chậm và log vẫn đầy câu SELECT.

**Khi nào xảy ra.** Entity cha và collection có `@Cache`, nhưng entity phần tử **không** `@Cacheable`.

**Khi nào không xảy ra.** Khi entity phần tử cũng được cache (khi đó dữ liệu phần tử lấy từ cache của chính nó).

**Ví dụ.**

```java
@Entity(name = "G13K2_Category")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class K2Category {
    // INVERSE side. Collection có @Cache riêng: một vùng cache khác entity.
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    private List<K2Entry> entries = new ArrayList<>();
}
```

`K2Entry` cố ý **không** có `@Cacheable`.

```java
inTx(() -> {
    K2Category category = em.find(K2Category.class, 1L);   // cache hit
    List<K2Entry> entries = category.getEntries();        // cache hit: 3 id
    for (K2Entry entry : entries) {
        entry.getLabel(); // cần dữ liệu phần tử
    }
});
```

**Kết quả.** Category hit và collection hit, nên mở collection **không có SELECT nào**. Nhưng đọc dữ liệu của 3 phần tử thì có **3 câu SELECT riêng**, mỗi câu lấy một phần tử theo id.

```sql
select ke1_0.id, ke1_0.category_id, ke1_0.label
from g13_k2_collection_cache.entry ke1_0
where ke1_0.id=?
```

| Việc | Số SELECT |
|---|---|
| `find` category | 0 |
| Mở collection `entries` | 0 |
| Đọc dữ liệu của 3 phần tử | **3** (một câu cho mỗi phần tử) |

Thay đổi: không có dòng nào đổi. Giữ nguyên: dữ liệu. N+1 vẫn còn nguyên hình.

**Vì sao.** Cache collection **chỉ lưu danh sách id** của phần tử, không lưu dữ liệu của chúng. Khi collection hit, Hibernate có 3 id và tạo 3 proxy (như lazy loading ở Chương 11). Chạm vào dữ liệu của một proxy thì Hibernate hỏi cache của entity `Entry`; vì entity này không được cache nên nó xuống database, mỗi phần tử một câu. Muốn hết N+1 thì phải cache **cả** entity phần tử. Cache collection chỉ có ý nghĩa khi đi cùng cache entity phần tử.

**Trong JPA thuần.** JPA không có cache collection; `@Cache` trên collection là của Hibernate.

::: {.ghi-nho}
Cache collection chỉ giữ danh sách id. Muốn hết N+1 thì cache cả entity phần tử.
:::

::: {.tracuu codes="G13-S08"}
:::

### 19.5 Query cache

**Query cache** lưu kết quả của một câu query cụ thể (cùng câu, cùng tham số). Nó là cache thứ ba, tách biệt với cache entity và cache collection. Muốn dùng, bạn phải đòi tường minh trên từng query (`setHint("org.hibernate.cacheable", true)`); mặc định mọi query đều không vào cache.

#### 19.5.1 Query cache khi entity không được cache: vẫn 0 SELECT {.cot-loi}

**Bối cảnh.** Danh sách nhãn đang hoạt động được hiển thị trên mọi trang. Bạn bật query cache cho câu lấy danh sách này. Sách và bài viết cũ nói query cache chỉ lưu danh sách id, nên nếu entity không được cache thì mỗi nhãn vẫn tốn một câu SELECT. Bạn muốn kiểm tra điều đó với phiên bản bạn đang dùng.

**Khi nào xảy ra.** Query có `setHint("org.hibernate.cacheable", true)`, entity **không** có `@Cacheable`, và lần chạy thứ hai nằm ở transaction khác.

**Khi nào không xảy ra.** Khi query không đòi cache (mặc định mọi query đều không vào query cache).

**Ví dụ.**

```java
String jpql = "select t from G13K3_Tag t where t.active = true order by t.id";
inTx(() -> em.createQuery(jpql, K3Tag.class)
    .setHint("org.hibernate.cacheable", true).getResultList()); // lần 1: xuống DB

inTx(() -> {
    List<K3Tag> tags = em.createQuery(jpql, K3Tag.class)
        .setHint("org.hibernate.cacheable", true).getResultList(); // lần 2
    tags.get(0).getLabel();  // đủ dữ liệu
});
```

**Kết quả.** Lần chạy thứ hai có **không câu SQL nào**. Danh sách có đủ 3 tag, mỗi tag đã có đầy đủ dữ liệu (không phải proxy chờ nạp). Query cache có đúng một lần hit, còn cache cấp 2 **không ghi entity nào** (số lần put bằng 0).

| Ở lần chạy thứ hai | Giá trị |
|---|---|
| Số câu SQL | **0** |
| Số lần query cache hit | 1 |
| Số entity được ghi vào cache cấp 2 | 0 |
| Tag có phải proxy chưa nạp | Không (đã đủ dữ liệu) |

Thay đổi: không có. Giữ nguyên: dữ liệu giống lần đầu.

::: {.khac-du-doan codes="G13-S09a"}
**Điều nhiều người nghĩ:** query cache chỉ lưu danh sách id. Entity không được cache thì Hibernate phải SELECT từng entity theo id, nên được lợi rất ít (thậm chí lại thành N+1).

**Thực tế:** với Hibernate 7.4 trong lab, query cache trả luôn entity đã khởi tạo **đủ dữ liệu**, không có SELECT nào, dù cache cấp 2 không có entity nào. Đừng khẳng định hành vi "chỉ lưu id" cho phiên bản mới mà chưa đo.

Đã kiểm chứng trên Hibernate 7.4.15.Final.
:::

**Vì sao.** Test không lý giải cơ chế bên trong, chỉ chứng minh hành vi quan sát được: query cache hit, 0 SELECT, entity đủ dữ liệu, và vùng cache entity trống. Điều bạn rút ra được và dùng được: ở phiên bản này, query cache **tự nó** đủ để tránh SELECT cho kết quả query, không cần đồng thời cache entity. Nhưng nếu bạn đang chạy một phiên bản Hibernate khác, hãy tự đo lại trước khi dựa vào kết luận này.

**Trong JPA thuần.** JPA không có query cache. Hint `org.hibernate.cacheable` là của Hibernate. Hint `jakarta.persistence.cache.retrieveMode` và `storeMode` của JPA chỉ điều khiển cache **entity**, không điều khiển query cache.

::: {.ghi-nho}
Ở Hibernate 7.4, query cache hit trả luôn entity đủ dữ liệu mà không cần SELECT, kể cả khi entity không được cache cấp 2.
:::

::: {.tracuu codes="G13-S09a"}
:::

#### 19.5.2 Query cache khi entity được cache, và khi database bị sửa ngoài {.cot-loi}

**Bối cảnh.** Cùng câu query ở trên, nhưng lần này entity là sản phẩm có `@Cacheable`. Rồi một hôm có người sửa dữ liệu trực tiếp trong database, và danh sách trên trang vẫn hiện nhãn cũ.

**Khi nào xảy ra.** Query cacheable đã vào query cache, rồi bảng bị sửa bằng đường không qua Hibernate.

**Khi nào không xảy ra.** Khi bảng không bị sửa ngoài Hibernate. Lab không có test riêng cho việc thay đổi qua Hibernate làm vô hiệu query cache, nên sách không khẳng định chi tiết phần đó.

**Ví dụ.** Hai thí nghiệm trong cùng mục:

```java
// 1. Entity CÓ cache: query cache cho id, cache entity cho dữ liệu
String jpql = "select p from G13K1_Product p where p.price > 5";
inTx(() -> em.createQuery(jpql, K1Product.class)
    .setHint("org.hibernate.cacheable", true).getResultList());
inTx(() -> em.createQuery(jpql, K1Product.class)
    .setHint("org.hibernate.cacheable", true).getResultList()); // 0 SELECT

// 2. Sửa NGOÀI Hibernate rồi chạy lại query cacheable
jdbc.update("update g13_k3_query_cache.tag set label = 'New' where id = 1");
inTx(() -> {
    List<K3Tag> tags = em.createQuery(
            "select t from G13K3_Tag t where t.active = true", K3Tag.class)
        .setHint("org.hibernate.cacheable", true).getResultList();
    tags.get(0).getLabel(); // vẫn "Old"
});
```

**Kết quả.** Khi entity có cache, lần chạy thứ hai là **0 SELECT**: query cache cho kết quả, cache entity cho dữ liệu, hai cache bổ sung cho nhau. Khi database bị sửa ngoài Hibernate, query cache **trả dữ liệu cũ** ("Old"), cũng 0 SELECT, trong khi database thật đã là "New".

| Tình huống | Số SELECT ở lần chạy thứ hai | Dữ liệu trả về |
|---|---|---|
| Entity có cache, không sửa ngoài | **0** | Đúng |
| Nhãn bị sửa ngoài Hibernate | **0** | **Cũ** ("Old"), database là "New" |

Thay đổi: ở thí nghiệm hai, nhãn trong database đổi thành "New". Giữ nguyên: kết quả query vẫn là "Old".

**Vì sao.** Giống mục 19.2.2: Hibernate chỉ biết bảng đã đổi khi thay đổi đi **qua nó**. Query cache giữ kết quả cho tới khi mục cache bị xóa hoặc hết hạn. Sửa ngoài Hibernate thì không có gì làm mục cache bị xóa, vì Hibernate không hay biết. Đây là cơ chế đứng sau sự cố "admin sửa giá mà khách vẫn thấy giá cũ".

**Cách tránh.** Cùng quy tắc với cache entity: chỉ bật query cache cho dữ liệu mà ứng dụng này là nơi duy nhất ghi, hoặc chấp nhận dữ liệu cũ trong một khoảng thời gian bạn đã chọn.

::: {.ghi-nho}
Query cache và cache entity bổ sung cho nhau, và đều trả dữ liệu cũ khi database bị sửa ngoài Hibernate.
:::

::: {.tracuu codes="G13-S09b,G13-S09c"}
:::

### 19.6 Cache READ_ONLY

#### 19.6.1 Sửa entity trong cache READ_ONLY: lỗi lúc commit, rollback {.mo-rong}

**Bối cảnh.** Bảng tỷ giá được khai báo cache `READ_ONLY` vì "tỷ giá không bao giờ sửa" (thực tế là một đợt sau vẫn có người cần sửa một dòng). Một chức năng mới tải tỷ giá rồi đổi giá.

**Kết quả.** Entity đã nằm trong cache. Khi một transaction tải nó rồi gọi `setPrice(200)`, Hibernate vẫn **gửi câu UPDATE** xuống database. Nhưng lúc commit, khi cập nhật cache, exception xuất hiện: nguyên nhân gốc là `UnsupportedOperationException` với thông báo "Can't update read-only object", bọc trong `TransactionSystemException`. Transaction bị rollback.

```sql
update g13_k4_read_only.rate
set currency=?, price=?
where id=?
```

| Bảng rate | price |
|---|---|
| Trước | 100 |
| Sau transaction bị rollback | **100** (giữ giá cũ) |

Thay đổi: không có gì được lưu. Giữ nguyên: giá 100 trong database.

Bài học: `READ_ONLY` là lời hứa "dữ liệu này không bao giờ sửa" mà Hibernate **kiểm tra bằng cách ném lỗi** khi bạn vi phạm. Nó nhanh hơn `READ_WRITE` vì không cần khóa mục cache, nhưng chỉ hợp với dữ liệu tham chiếu thật sự bất biến. Nếu có khả năng cần sửa, dùng `READ_WRITE`.

::: {.ghi-nho}
Sửa entity trong cache `READ_ONLY` làm transaction rollback lúc commit. Chỉ dùng chiến lược này cho dữ liệu không bao giờ đổi.
:::

::: {.tracuu codes="G13-S10"}
:::
