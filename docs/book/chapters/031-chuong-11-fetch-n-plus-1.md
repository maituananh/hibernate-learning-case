## Chương 11. FetchType, proxy và vấn đề N+1

Ở các chương trước, ta chỉ quan tâm dữ liệu được **ghi** thế nào. Từ chương này, ta quay sang chiều ngược lại: dữ liệu được **đọc** thế nào. Câu hỏi trung tâm: khi nào Hibernate tải một quan hệ, bằng bao nhiêu câu SQL, và chuyện gì xảy ra khi bạn chạm vào quan hệ đó sau khi transaction đã kết thúc.

Đây là chương gây thiệt hại thật nhất trong production. Một mapping sai cascade thường làm hỏng dữ liệu và bạn phát hiện ngay. Một mapping sai fetch thì mọi thứ vẫn **chạy đúng**, chỉ là chậm dần khi dữ liệu lớn lên, cho đến một ngày màn hình danh sách mất mười giây để mở.

Chương này dùng một mô hình duy nhất: `Customer` có nhiều `PurchaseOrder`, mỗi đơn có nhiều `OrderItem`, mỗi dòng hàng trỏ tới một `Product`, và `Customer` còn có nhiều `Payment`. Dữ liệu cố định cho mọi thí nghiệm:

- 5 customer, mỗi customer 2 đơn hàng, mỗi đơn 2 dòng hàng: tổng 10 đơn, 20 dòng hàng.
- 3 product (PEN, BOOK, BAG), các dòng hàng lần lượt dùng lại chúng.
- 3 payment, **tất cả thuộc về customer đầu tiên**. Nhờ vậy chỉ customer đầu tiên có cả 2 đơn lẫn 3 payment, đủ để thấy hiện tượng nhân dòng ở mục 11.5.

Dữ liệu được ghi thẳng bằng JDBC trước mỗi thí nghiệm, nên lúc bắt đầu đọc, persistence context hoàn toàn trống và mọi câu SELECT bạn thấy là SELECT thật chứ không phải cache cấp 1.

Trong chương này:

- Hai khái niệm nền: EAGER và LAZY, proxy là gì (mục 11.1).
- Để mặc định thì `@ManyToOne` là EAGER và điều đó gây N+1 (mục 11.2).
- Đặt mọi quan hệ là LAZY: proxy, `LazyInitializationException`, và vì sao LAZY chưa đủ (mục 11.3).
- N+1 trên collection và bốn cách sửa: `join fetch`, `@EntityGraph`, `@BatchSize`, `SUBSELECT` (mục 11.4).
- Khi sửa sai: hai List cùng fetch, nhân dòng với Set (mục 11.5).
- Phân trang kèm fetch collection (mục 11.6).
- Collection EAGER (mục 11.7).
- N+1 qua `@ManyToOne` và qua `@ManyToMany` (mục 11.8). Lối thoát bằng DTO projection nằm ở mục 12.5.

**Trong JPA thuần.** `FetchType`, `@EntityGraph` (dưới dạng `@NamedEntityGraph` hoặc `EntityGraph` API), `join fetch` đều là JPA chuẩn. `@BatchSize` và `@Fetch(FetchMode.SUBSELECT)` là riêng của Hibernate; mỗi mục dùng chúng đều có đoạn **Trong JPA thuần.** riêng. `Hibernate.initialize` và `Hibernate.isInitialized` cũng riêng của Hibernate, JPA có `PersistenceUnitUtil.isLoaded` để kiểm tra nhưng không có cách "ép tải" tương đương.

### 11.1 FetchType mặc định và proxy {.kien-thuc-nen}

**EAGER và LAZY chỉ trả lời câu hỏi "khi nào", không trả lời câu hỏi "bằng cách nào".**

- **EAGER**: tải quan hệ **ngay lúc** tải entity chứa nó.
- **LAZY**: hoãn, chỉ tải khi code thật sự chạm vào quan hệ.

JPA đặt mặc định như sau, và đây là chỗ nhiều người nhớ sai:

| Loại quan hệ | Mặc định |
|---|---|
| `@ManyToOne`, `@OneToOne` (phía "một") | **EAGER** |
| `@OneToMany`, `@ManyToMany` (phía "nhiều") | **LAZY** |

Quy tắc dễ nhớ: quan hệ trỏ tới **một** đối tượng thì mặc định EAGER, quan hệ trỏ tới **một tập** thì mặc định LAZY. Lý do lịch sử là một đối tượng thì "rẻ", một tập thì có thể rất lớn. Thực tế mặc định `@ManyToOne` EAGER là nguồn N+1 phổ biến nhất (mục 11.2).

**Proxy là gì.** Khi một quan hệ to-one là LAZY, Hibernate không để trống trường đó mà gắn một **proxy**: một object giả cùng kiểu với entity thật, mang sẵn **id** (lấy từ cột khóa ngoại), nhưng chưa có dữ liệu nào khác. Lần đầu bạn gọi một getter không phải getId trên proxy, proxy mới chạy câu SELECT để tải dữ liệu thật. Với collection LAZY, thay vì proxy, Hibernate gắn một collection đặc biệt (`PersistentBag`, `PersistentSet`) chưa có phần tử nào cho đến khi bạn chạm vào.

Hai công cụ kiểm tra trạng thái này: `Hibernate.isInitialized(x)` (đã tải chưa) và `Hibernate.initialize(x)` (ép tải ngay). Cả hai chỉ chạy được khi session còn mở, tức là còn trong transaction.

**Cách đọc số câu SQL trong chương này.** Ta đếm câu SELECT mà Hibernate gửi đi, bỏ qua lệnh lấy số từ sequence. "1 + 5" nghĩa là 1 câu SELECT đầu và 5 câu SELECT phụ, và đó chính là hình dạng của N+1: **1** câu lấy danh sách, rồi **N** câu, mỗi câu cho một phần tử của danh sách.

**Trong JPA thuần.** `FetchType` và proxy đều thuộc đặc tả JPA. Kiểm tra "đã tải chưa" dùng `entityManagerFactory.getPersistenceUnitUtil().isLoaded(x)`. Chỉ có một khác biệt: đặc tả cho phép provider **bỏ qua** gợi ý LAZY, và Hibernate thật sự bỏ qua trong một số trường hợp (mục 7.2 cho OneToOne phía inverse).

### 11.2 Để mặc định: ManyToOne là EAGER {.bien-the}

Biến thể này **không ghi `fetch`** ở bất kỳ quan hệ nào. Đây là cách nhiều người viết entity vì ngắn gọn và vì ví dụ trong sách giáo khoa thường như vậy.

```java
@Entity(name = "G5F0_PurchaseOrder")
@Table(schema = "g5_f0_defaults", name = "purchase_order")
public class F0PurchaseOrder {

    @Id
    private Long id;

    @Column(name = "order_no", nullable = false)
    private String orderNo;

    // OWNING side. Cố ý KHÔNG ghi fetch: mặc định của @ManyToOne là EAGER.
    @ManyToOne
    @JoinColumn(name = "customer_id")
    private F0Customer customer;

    // INVERSE side. Cố ý KHÔNG ghi fetch: mặc định của @OneToMany là LAZY.
    @OneToMany(mappedBy = "order")
    private List<F0OrderItem> items = new ArrayList<>();
}
```

![Sơ đồ bảng customer và purchase_order, purchase_order giữ khóa ngoại customer_id](img/g5-f0.png){width=70%}

**Ưu điểm.** Ít code, và `order.getCustomer().getName()` luôn chạy được, kể cả ngoài transaction, vì customer đã được tải sẵn. Với `find` theo id, Hibernate gộp cả hai bảng vào một câu SQL có `left join`, nên chi phí rất thấp.

**Nhược điểm.** EAGER là lời hứa "sẽ tải ngay", **không** phải lời hứa "tải bằng JOIN". Với truy vấn JPQL, Hibernate không tự thêm JOIN, và sẽ chạy thêm một câu SELECT cho mỗi customer chưa có trong persistence context. Bạn trả giá cho dữ liệu có thể không cần dùng.

**Nên dùng khi.** Gần như không bao giờ nên để mặc định cho `@ManyToOne`. Hãy ghi `fetch = FetchType.LAZY` tường minh (mục 11.3) và tải thêm khi cần. Ngoại lệ: một quan hệ **luôn** được dùng cùng entity chứa nó và bảng đích rất nhỏ (ví dụ bảng tra cứu loại tiền tệ).

#### 11.2.1 find một đơn: một câu SELECT có left join {.cot-loi}

**Bối cảnh.** Màn hình chi tiết đơn hàng gọi `em.find(PurchaseOrder.class, id)` rồi hiển thị tên khách. Entity không ghi `fetch`, và bạn muốn biết Hibernate tải customer thế nào.

**Khi nào xảy ra.** `@ManyToOne` để mặc định (EAGER) và bạn lấy entity bằng `find`.

**Khi nào không xảy ra.** Nếu bạn lấy đơn bằng JPQL hoặc Criteria thay vì `find`, Hibernate không nối JOIN (mục 11.2.2). Nếu quan hệ là LAZY, customer không được tải (mục 11.3.1).

**Ví dụ.**

```java
inTx(() -> {
    F0PurchaseOrder orderA = em.find(F0PurchaseOrder.class, orderId);
    // orderA: MANAGED. customer cũng MANAGED và đã tải đầy đủ (không proxy).
    Hibernate.isInitialized(orderA.getCustomer()); // true
});
```

**Kết quả.** Đúng 1 câu SELECT, có `left join` sang customer. Cả order lẫn customer đều là MANAGED, customer là object thật chứ không phải proxy.

```sql
select fpo1_0.id, c1_0.id, c1_0.name, fpo1_0.order_no
from g5_f0_defaults.purchase_order fpo1_0
left join g5_f0_defaults.customer c1_0 on c1_0.id=fpo1_0.customer_id
where fpo1_0.id=?
```

**Vì sao.** `find` nhận một id và biết trước phải lấy cả customer, nên Hibernate dựng được câu SQL có JOIN ngay từ đầu. Đây là lý do EAGER nhìn có vẻ "miễn phí" khi thử nghiệm nhỏ: kịch bản `find` một bản ghi luôn rẻ.

::: {.ghi-nho}
`find` biết trước nên nối JOIN: EAGER `@ManyToOne` cho 1 câu SELECT. Đừng kết luận EAGER rẻ chỉ từ kịch bản này.
:::

::: {.tracuu codes="G5-S01"}
:::

#### 11.2.2 JPQL lấy danh sách đơn: một SELECT order và năm SELECT customer {.cot-loi}

**Bối cảnh.** Màn hình danh sách đơn hàng chạy `select o from PurchaseOrder o` để lấy 10 đơn. Code không hề đụng tới customer, nhưng trong log bạn thấy hàng loạt câu SELECT customer.

**Khi nào xảy ra.** `@ManyToOne` EAGER và truy vấn là JPQL (hoặc Criteria, hoặc derived query của Spring Data). Số câu SELECT phụ bằng số customer **khác nhau** trong kết quả.

**Khi nào không xảy ra.** Nếu quan hệ LAZY thì không có SELECT phụ (nếu bạn không chạm vào). Nếu bạn viết `join fetch o.customer` thì chỉ còn 1 câu.

**Ví dụ.**

```java
inTx(() -> {
    List<F0PurchaseOrder> orders = em
        .createQuery("select o from G5F0_PurchaseOrder o",
                F0PurchaseOrder.class)
        .getResultList();
    // 10 order MANAGED, customer của chúng đã được tải (EAGER)
});
```

**Kết quả.** 1 câu SELECT `purchase_order` (không có JOIN), rồi **5** câu SELECT `customer`, mỗi câu `where id = ?` cho một customer khác nhau. Tổng 6 câu cho 10 đơn.

```sql
select fpo1_0.id, fpo1_0.customer_id, fpo1_0.order_no
from g5_f0_defaults.purchase_order fpo1_0
-- rồi lặp lại 5 lần, mỗi lần một customer khác:
select fc1_0.id, fc1_0.name
from g5_f0_defaults.customer fc1_0
where fc1_0.id=?
```

**Vì sao.** Hibernate dịch JPQL gần như nguyên văn sang SQL, **không tự thêm JOIN**. Sau câu đầu, nó có 10 order trỏ tới 5 customer chưa nằm trong persistence context, và vì quan hệ là EAGER, Hibernate buộc phải tải chúng, mỗi customer một câu. Customer đã tải rồi thì lần sau lấy từ persistence context, nên 10 đơn chỉ tốn 5 câu, không phải 10. Nhưng với 10.000 đơn của 3.000 khách thì bạn có 3.001 câu.

**Cách tránh.** Ghi `fetch = FetchType.LAZY` cho `@ManyToOne`, và dùng `join fetch` hoặc `@EntityGraph` ở những truy vấn cần customer (mục 11.4).

::: {.ghi-nho}
EAGER không có nghĩa là JOIN. Trong JPQL, EAGER `@ManyToOne` sinh thêm một câu SELECT cho mỗi phần tử khác nhau: đó là N+1.
:::

::: {.tracuu codes="G5-S02"}
:::

### 11.3 Mọi quan hệ LAZY {.bien-the}

Đây là baseline mà cả chương dùng làm chuẩn, và cũng là cách khuyến nghị: **mọi quan hệ ghi `fetch = FetchType.LAZY` tường minh**, collection là `List`.

```java
@Entity(name = "G5F1_PurchaseOrder")
@Table(schema = "g5_f1_all_lazy", name = "purchase_order")
public class F1PurchaseOrder {

    // OWNING side. LAZY ghi tường minh.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private F1Customer customer;

    // INVERSE side. LAZY ghi tường minh.
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<F1OrderItem> items = new ArrayList<>();
}

@Entity(name = "G5F1_Customer")
public class F1Customer {

    // INVERSE side: hai collection, cả hai LAZY.
    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    private List<F1PurchaseOrder> orders = new ArrayList<>();

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    private List<F1Payment> payments = new ArrayList<>();
}
```

![Sơ đồ năm bảng customer, purchase_order, order_item, product và payment với các khóa ngoại](img/g5-f1.png){width=95%}

**Ưu điểm.** Không tải gì thừa: bạn chỉ trả tiền cho quan hệ nào bạn thật sự dùng. Số câu SQL của mỗi use case nằm trong tay bạn, vì bạn quyết định `join fetch` gì trong từng truy vấn.

**Nhược điểm.** Hai thứ: (1) chạm vào quan hệ ngoài transaction thì `LazyInitializationException`; (2) LAZY **không** tự cứu bạn khỏi N+1 khi bạn lặp qua danh sách và chạm vào quan hệ của từng phần tử.

**Nên dùng khi.** Là lựa chọn mặc định cho mọi quan hệ trong entity mới. Tải thêm quan hệ theo từng truy vấn bằng `join fetch`, `@EntityGraph`, hoặc DTO projection.

#### 11.3.1 Proxy: getId không SELECT, getName mới SELECT {.cot-loi}

**Bối cảnh.** Bạn `find` một đơn rồi gọi `order.getCustomer()`. Bạn muốn biết object trả về là gì, và đọc id của customer có tốn một câu SELECT không (ví dụ để hiển thị link tới trang khách).

**Khi nào xảy ra.** `@ManyToOne(fetch = LAZY)` và bạn lấy order bằng `find`. Hibernate chỉ SELECT bảng `purchase_order`, không JOIN.

**Khi nào không xảy ra.** Nếu customer đã nằm sẵn trong persistence context (đã `find` trước đó trong cùng transaction), Hibernate trả luôn object thật thay vì proxy.

**Ví dụ.**

```java
inTx(() -> {
    F1PurchaseOrder orderA = em.find(F1PurchaseOrder.class, orderId);
    // customer là proxy, chưa khởi tạo

    Long customerId = orderA.getCustomer().getId();   // KHÔNG SELECT
    String name = orderA.getCustomer().getName();     // SELECT customer
});
```

**Kết quả.** Đúng 2 câu SELECT cho cả đoạn: câu đầu cho `purchase_order` (không JOIN), câu thứ hai cho `customer`, chỉ chạy ở dòng `getName()`. Sau `find`, customer chưa khởi tạo. Sau `getId()`, vẫn chưa. Sau `getName()`, đã khởi tạo.

```sql
select fpo1_0.id, fpo1_0.customer_id, fpo1_0.order_no
from g5_f1_all_lazy.purchase_order fpo1_0
where fpo1_0.id=?
-- chỉ khi gọi getName() mới có câu này:
select fc1_0.id, fc1_0.name
from g5_f1_all_lazy.customer fc1_0
where fc1_0.id=?
```

**Vì sao.** Id của customer đã nằm sẵn trong cột `purchase_order.customer_id`, nên Hibernate dựng proxy mang id đó mà không cần hỏi database. `getId()` trên proxy trả thẳng giá trị đã biết. Mọi getter khác cần dữ liệu thật nên buộc proxy chạy SELECT.

::: {.ghi-nho}
Proxy mang sẵn id. `getId()` miễn phí, getter khác mới tải. Muốn chỉ cần id của quan hệ, đừng chạm vào thuộc tính nào khác.
:::

::: {.tracuu codes="G5-S03"}
:::

#### 11.3.2 Chạm vào quan hệ LAZY sau khi hết transaction {.cot-loi}

**Bối cảnh.** Service tải đơn hàng trong một transaction rồi trả entity cho tầng trên. Tầng trên (ví dụ lớp chuyển sang JSON) gọi `order.getItems().size()` và nhận `LazyInitializationException`. Đây là lỗi kinh điển nhất của JPA.

**Khi nào xảy ra.** Quan hệ LAZY chưa được khởi tạo, và entity đã DETACHED (session đóng). Cả collection lẫn proxy to-one đều dính.

**Khi nào không xảy ra.** Nếu quan hệ đã được tải trong lúc còn transaction (bằng `join fetch`, `@EntityGraph`, hoặc `Hibernate.initialize`). Trong web request, `open-in-view` giữ session mở lâu hơn (nhưng đó là một anti-pattern riêng, và không áp dụng ở bài thí nghiệm này).

**Ví dụ.**

```java
F1PurchaseOrder detachedOrder = inTxReturning(
    () -> em.find(F1PurchaseOrder.class, orderId));
// Hết transaction: detachedOrder là DETACHED.

detachedOrder.getItems().size();        // LazyInitializationException
detachedOrder.getCustomer().getName();  // LazyInitializationException
```

**Kết quả.** Cả hai lời gọi ném `LazyInitializationException`. Trước khi ném, Hibernate **không gửi câu SQL nào**: không có session nên không có đường nào để chạy SELECT. Dữ liệu trong database vẫn đủ 20 dòng hàng, lỗi hoàn toàn nằm ở phía ứng dụng.

**Vì sao.** Proxy và collection LAZY nhớ session đã tạo ra chúng, và chỉ tải được qua session đó. Khi transaction kết thúc, session đóng, entity thành DETACHED, và "đường đi tới database" của proxy biến mất. Có một cấu hình `hibernate.enable_lazy_load_no_trans` che lỗi này bằng cách mở một session tạm cho mỗi lần chạm, nhưng đó là anti-pattern (mỗi lần chạm là một transaction riêng, rất khó kiểm soát số câu SQL), nên lab không bật.

**Cách tránh.** Tải đủ dữ liệu cần thiết **trong** transaction: `join fetch`, `@EntityGraph`, hoặc trả DTO thay vì entity (mục 12.5).

::: {.ghi-nho}
`LazyInitializationException` nghĩa là bạn chạm vào quan hệ chưa tải sau khi session đóng. Sửa bằng cách tải sớm hơn, đừng sửa bằng cách chuyển sang EAGER.
:::

::: {.tracuu codes="G5-S04"}
:::

#### 11.3.3 LAZY không tránh được N+1 khi lặp qua danh sách {.cot-loi}

**Bối cảnh.** Màn hình thống kê cần số đơn của từng khách. Bạn `findAll()` customer rồi lặp `customer.getOrders().size()`. Mapping đã LAZY chuẩn mực, nhưng log vẫn đầy câu SELECT.

**Khi nào xảy ra.** Bạn lấy một danh sách N entity rồi chạm vào collection LAZY của **từng** entity. Mỗi lần chạm là một câu SELECT riêng.

**Khi nào không xảy ra.** Nếu bạn chỉ cần dữ liệu từ bảng cha thì không tốn gì thêm. Nếu bạn tải collection theo lô (mục 11.4) thì số câu giảm.

**Ví dụ.**

```java
inTx(() -> {
    List<F1Customer> customers = customerRepository.findAll();
    int totalOrders = 0;
    for (F1Customer customer : customers) {
        totalOrders += customer.getOrders().size(); // mỗi lần: 1 SELECT
    }
});
```

**Kết quả.** 1 câu SELECT `customer`, rồi **5** câu SELECT `purchase_order`, mỗi câu `where customer_id = ?` cho một customer. Tổng 6 câu. Đây là N+1 trên collection, với N bằng số customer.

```sql
select fc1_0.id, fc1_0.name
from g5_f1_all_lazy.customer fc1_0
-- rồi 5 lần, mỗi lần một customer:
select o1_0.customer_id, o1_0.id, o1_0.order_no
from g5_f1_all_lazy.purchase_order o1_0
where o1_0.customer_id=?
```

**Vì sao.** LAZY nghĩa là "chỉ tải khi chạm". Vòng lặp chạm vào collection của từng customer, nên mỗi collection tải riêng một lần. LAZY ngăn bạn tải **thừa**, nhưng nó không biết bạn sắp chạm vào cả 5 collection, nên không gộp lại được. Gộp lại là việc của bạn (mục 11.4).

::: {.ghi-nho}
LAZY hoãn việc tải, không gộp việc tải. Lặp qua N phần tử và chạm vào quan hệ LAZY của từng phần tử luôn tốn N câu SELECT.
:::

::: {.tracuu codes="G5-S05"}
:::

#### 11.3.4 Hibernate.initialize: chỉ cứu được khi còn trong transaction {.mo-rong}

**Bối cảnh.** Bạn muốn trả customer cùng danh sách đơn ra khỏi service, và nghĩ rằng gọi `Hibernate.initialize(customer.getOrders())` là đủ ở mọi nơi.

**Kết quả.** Gọi trong transaction: collection được tải bằng 1 câu SELECT `purchase_order`, và dùng được bình thường sau khi customer đã DETACHED (2 phần tử). Gọi ngoài transaction: ném `LazyInitializationException`, vì `initialize` chỉ là cách chạm vào collection một cách có chủ ý, nó không mở lại session.

```java
F1Customer customer = inTxReturning(() -> {
    F1Customer customerAn = em.find(F1Customer.class, customerId);
    Hibernate.initialize(customerAn.getOrders()); // tải ngay, trong tx
    return customerAn;
});
customer.getOrders().size(); // chạy được: đã tải trước khi session đóng
```

**Trong JPA thuần.** `Hibernate.initialize` là API riêng của Hibernate. JPA không có hàm ép tải; cách tương đương là tự chạm vào collection (ví dụ gọi `size()`) trong transaction, hoặc dùng `join fetch` trong truy vấn.

::: {.ghi-nho}
`Hibernate.initialize` tải collection có chủ đích, nhưng vẫn cần session mở. Nó không phải cách vá cho `LazyInitializationException` đã xảy ra.
:::

::: {.tracuu codes="G5-S14"}
:::

### 11.4 N+1 trên collection và bốn cách sửa {.bien-the}

Cùng một bài toán ở mục 11.3.3 (lặp qua customer và đọc đơn của từng người, 1 câu cộng 5 câu), mỗi cách sửa đưa số câu về một con số khác. Hai cách đổi **câu truy vấn** (`join fetch`, `@EntityGraph`), hai cách đổi **mapping** (`@BatchSize`, `SUBSELECT`).

Mapping cho hai cách đổi mapping, chỉ khác baseline một annotation:

```java
@Entity(name = "G5F3_Customer")
public class F3Customer {

    // Cách 3: @BatchSize. Khi chạm vào collection của một customer,
    // Hibernate nhặt luôn tối đa 10 customer chưa tải orders.
    @BatchSize(size = 10)
    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    private List<F3PurchaseOrder> orders = new ArrayList<>();
}

@Entity(name = "G5F4_Customer")
public class F4Customer {

    // Cách 4: SUBSELECT. Tải orders của MỌI customer mà query gốc
    // đã trả về, bằng một câu có subquery.
    @Fetch(FetchMode.SUBSELECT)
    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    private List<F4PurchaseOrder> orders = new ArrayList<>();
}
```

![Sơ đồ bảng của biến thể dùng BatchSize: customer và purchase_order, schema không đổi so với baseline](img/g5-f3.png){width=70%}

![Sơ đồ bảng của biến thể dùng SUBSELECT: cùng hai bảng, chỉ khác cách tải](img/g5-f4.png){width=70%}

Bảng so sánh số câu SELECT khi đọc đơn của 5 customer:

| Cách | Nằm ở đâu | Số câu SELECT | Ghi chú |
|---|---|---|---|
| Không sửa | | 1 + 5 = 6 | N+1 |
| `join fetch` | câu JPQL | 1 | nối JOIN, mỗi customer một hoặc nhiều dòng |
| `@EntityGraph` | annotation trên method | 1 | dùng `left join` |
| `@BatchSize(10)` | mapping | 2 | câu thứ hai lọc `= any (?)` |
| `SUBSELECT` | mapping | 2 | câu thứ hai chứa subquery |

**Ưu điểm.** Cả bốn cách đều không đổi kết quả nghiệp vụ, chỉ đổi số câu SQL. `join fetch` và `@EntityGraph` cho đúng 1 câu và nằm ở nơi bạn cần. `@BatchSize` và `SUBSELECT` áp dụng cho mọi truy vấn mà không phải sửa từng câu.

**Nhược điểm.** `join fetch` và `@EntityGraph` làm nhân dòng khi fetch collection (mục 11.5) và hỏng phân trang (mục 11.6). `@BatchSize` và `SUBSELECT` là mapping toàn cục: ảnh hưởng mọi use case, kể cả chỗ bạn không muốn tải collection. `SUBSELECT` tải collection của **mọi** customer trong kết quả gốc dù bạn chỉ chạm vào một người.

**Nên dùng khi.** `join fetch` hoặc `@EntityGraph` cho truy vấn **biết trước** cần collection (màn hình chi tiết, báo cáo). `@BatchSize` làm lưới an toàn chung cho mọi collection LAZY (chọn size gần với kích thước trang thường dùng). `SUBSELECT` khi kết quả gốc nhỏ và gần như luôn đọc hết các collection.

#### 11.4.1 join fetch: một câu SELECT và không cần distinct {.cot-loi}

**Bối cảnh.** Báo cáo đơn theo khách cần cả danh sách đơn của mọi khách. Bạn viết lại JPQL thành `select c from Customer c join fetch c.orders` để thoát N+1, nhưng lo rằng JOIN ra 10 dòng thì danh sách trả về có 10 customer (mỗi người hai lần).

**Khi nào xảy ra.** Bạn dùng `join fetch` trên một collection trong JPQL. Hibernate nối bảng và nạp collection từ chính các dòng kết quả.

**Khi nào không xảy ra.** Với `join fetch`, khách không có đơn nào **không** có mặt trong kết quả (inner join). Muốn giữ họ thì dùng `left join fetch`.

**Ví dụ.**

```java
@Query("select c from G5F1_Customer c join fetch c.orders")
List<F1Customer> findAllWithOrdersJoinFetch();

inTx(() -> {
    List<F1Customer> customers = customerRepository.findAllWithOrdersJoinFetch();
    for (F1Customer customer : customers) {
        customer.getOrders().size(); // đã tải sẵn: không SELECT thêm
    }
});
```

**Kết quả.** Đúng 1 câu SELECT có `join`. Danh sách trả về có **5** customer (không phải 10), mỗi customer đủ 2 đơn, và vòng lặp không tốn thêm câu nào.

```sql
select fc1_0.id, fc1_0.name, o1_0.customer_id, o1_0.id, o1_0.order_no
from g5_f1_all_lazy.customer fc1_0
join g5_f1_all_lazy.purchase_order o1_0 on fc1_0.id=o1_0.customer_id
```

**Vì sao.** SQL trả 10 dòng (mỗi đơn một dòng, lặp lại thông tin customer), nhưng từ Hibernate 6 kết quả của truy vấn entity **tự loại trùng** theo id. Ở Hibernate 5 trở xuống bạn phải viết `select distinct c` để có kết quả này, nhiều bài viết cũ vẫn nhắc, nhưng với bản hiện tại thì thừa.

**Trong JPA thuần.** `join fetch` là cú pháp JPQL chuẩn, chạy trên mọi provider. Nếu code cần chạy trên cả phiên bản Hibernate cũ hơn, hãy giữ `distinct` cho chắc.

::: {.ghi-nho}
`join fetch` gom N+1 về 1 câu. Từ Hibernate 6, kết quả tự loại trùng, không cần `distinct`.
:::

::: {.tracuu codes="G5-S06"}
:::

#### 11.4.2 EntityGraph: cùng kết quả, khai báo bằng annotation {.cot-loi}

**Bối cảnh.** Bạn không muốn nhúng `join fetch` vào chuỗi JPQL hoặc muốn dùng lại cho derived query kiểu `findByName`. Bạn thử `@EntityGraph` của Spring Data.

**Khi nào xảy ra.** Bạn gắn `@EntityGraph(attributePaths = "orders")` lên method của repository.

**Khi nào không xảy ra.** Entity graph chỉ áp dụng cho truy vấn gắn nó. Truy vấn khác vẫn LAZY như cũ.

**Ví dụ.**

```java
@EntityGraph(attributePaths = "orders")
@Query("select c from G5F1_Customer c")
List<F1Customer> findAllWithOrdersEntityGraph();
```

**Kết quả.** Đúng 1 câu SELECT có join, 5 customer, mỗi người đủ 2 đơn. Khác duy nhất so với `join fetch` là cách nối: entity graph dùng **`left join`**, nên khách không có đơn vẫn có mặt.

```sql
select fc1_0.id, fc1_0.name, o1_0.customer_id, o1_0.id, o1_0.order_no
from g5_f1_all_lazy.customer fc1_0
left join g5_f1_all_lazy.purchase_order o1_0 on fc1_0.id=o1_0.customer_id
```

**Vì sao.** Câu JPQL giữ nguyên là `select c from Customer c`. Phần "tải kèm gì" tách ra thành annotation, nên cùng một truy vấn có thể có nhiều graph khác nhau cho nhiều màn hình.

**Trong JPA thuần.** `@EntityGraph` ở đây là của Spring Data. JPA chuẩn có hai cách tương đương: khai báo `@NamedEntityGraph` trên entity rồi truyền qua hint `jakarta.persistence.fetchgraph` (hoặc `loadgraph`), hoặc dựng bằng `em.createEntityGraph(...)` trong code.

::: {.ghi-nho}
`@EntityGraph` cho kết quả như `join fetch` nhưng tách khai báo khỏi câu truy vấn, và dùng `left join`.
:::

::: {.tracuu codes="G5-S07"}
:::

#### 11.4.3 BatchSize: hai câu SELECT mà không đổi truy vấn {.cot-loi}

**Bối cảnh.** Codebase có hàng chục chỗ lặp qua danh sách và chạm vào collection. Sửa từng truy vấn thì lâu, bạn muốn một biện pháp ở mapping giảm N+1 cho tất cả.

**Khi nào xảy ra.** Collection khai báo `@BatchSize(size = 10)`. Khi bạn chạm vào collection của customer đầu tiên, Hibernate gom thêm tối đa 9 customer khác (trong persistence context, chưa tải orders) và tải orders của cả lô trong một câu.

**Khi nào không xảy ra.** Nếu chỉ có một customer trong persistence context thì lô chỉ có một phần tử và không tiết kiệm được gì. Khi số customer lớn hơn size thì cần nhiều lô (số câu bằng số customer chia size, làm tròn lên).

**Ví dụ.**

```java
inTx(() -> {
    List<F3Customer> customers = customerRepository.findAll();
    for (F3Customer customer : customers) {
        customer.getOrders().size(); // chạm lần đầu: tải cả lô 5 customer
    }
});
```

**Kết quả.** Đúng 2 câu SELECT: 1 cho `customer`, 1 cho `purchase_order` của cả 5 customer. Câu thứ hai lọc bằng **một tham số kiểu mảng** thay vì liệt kê từng dấu hỏi:

```sql
select o1_0.customer_id, o1_0.id, o1_0.order_no
from g5_f3_batch_size.purchase_order o1_0
where o1_0.customer_id = any (?)
-- bind: mảng [1, 2, 3, 4, 5]
```

**Vì sao.** Hibernate biết persistence context đang giữ 5 customer chưa tải orders. Khi một collection cần tải, nó tải luôn cho cả lô (tối đa `size`). Dạng `= any (?)` với một mảng là cách Hibernate 7 dùng trên PostgreSQL; trên database khác bạn có thể thấy `in (?, ?, ...)`.

**Trong JPA thuần.** `@BatchSize` là riêng của Hibernate, JPA không có. Cấu hình toàn cục tương đương là `hibernate.default_batch_fetch_size` (cũng riêng của Hibernate). Trong JPA thuần, cách gần nhất là tự viết truy vấn `where customer in :customers` để tải orders theo lô.

::: {.ghi-nho}
`@BatchSize` biến N+1 thành "1 cộng số lô", mà không sửa câu truy vấn nào. Rẻ để thêm, nhưng nó là cấu hình toàn cục của mapping.
:::

::: {.tracuu codes="G5-S08"}
:::

#### 11.4.4 SUBSELECT: tải tất cả collection bằng một subquery {.mo-rong}

**Bối cảnh.** Bạn muốn kết quả 2 câu như `@BatchSize` nhưng không phải chọn size: lần chạm đầu tiên tải orders cho **toàn bộ** customer đã lấy ra.

**Kết quả.** Đúng 2 câu SELECT. Câu thứ hai lặp lại truy vấn gốc dưới dạng subquery, nên không cần liệt kê id:

```sql
select o1_0.customer_id, o1_0.id, o1_0.order_no
from g5_f4_subselect.purchase_order o1_0
where o1_0.customer_id in (select fc1_0.id from g5_f4_subselect.customer fc1_0)
```

Điểm cần lưu ý: collection của **mọi** customer trong kết quả gốc được tải, kể cả khi bạn chỉ chạm vào một người. Nếu truy vấn gốc có điều kiện lọc phức tạp thì subquery cũng phải chạy lại điều kiện đó.

**Trong JPA thuần.** `@Fetch(FetchMode.SUBSELECT)` là riêng của Hibernate, JPA không có tương đương. Cách thay thế là viết tay truy vấn thứ hai với `where c.id in (select ...)` hoặc dùng `join fetch` của mục 11.4.1.

::: {.ghi-nho}
`SUBSELECT` tải collection của cả kết quả gốc trong một câu, bằng subquery. Gọn khi kết quả nhỏ, tốn khi bạn chỉ cần một phần.
:::

::: {.tracuu codes="G5-S09"}
:::

### 11.5 Fetch nhiều collection cùng lúc

Sau khi biết `join fetch`, cám dỗ tiếp theo là fetch **cả orders và payments** của customer trong một câu. Đây là chỗ Hibernate bắt đầu từ chối, hoặc chạy được nhưng với giá rất đắt. Mục này dùng customer đầu tiên (2 đơn, 3 payment) làm ví dụ.

#### 11.5.1 Hai List cùng join fetch: MultipleBagFetchException {.cot-loi}

**Bối cảnh.** Màn hình chi tiết khách hàng cần cả đơn lẫn payment. Bạn viết `select c from Customer c join fetch c.orders join fetch c.payments`, và ứng dụng ném `MultipleBagFetchException`.

**Khi nào xảy ra.** Hai collection kiểu `List` (Hibernate gọi là "bag": không thứ tự, cho phép trùng) cùng nằm trong một câu `join fetch`.

**Khi nào không xảy ra.** Nếu một trong hai là `Set` (mục 11.5.3), hoặc nếu bạn chỉ fetch một bag mỗi truy vấn (mục 11.5.2).

**Ví dụ.**

```java
inTx(() -> {
    List<F1Customer> customers = em.createQuery(
        "select c from G5F1_Customer c "
        + "join fetch c.orders join fetch c.payments",
        F1Customer.class).getResultList(); // ném exception ở đây
});
```

**Kết quả.** Ngoại lệ `MultipleBagFetchException` nằm ở **root cause** (bên ngoài bị Spring bọc lại). Không có câu SQL nào được gửi đi.

**Vì sao.** Join hai bag cùng lúc cho ra các dòng lặp mà Hibernate không thể phân biệt được "trùng thật" (cùng phần tử xuất hiện hai lần trong List) với "trùng do nhân dòng" (hệ quả của JOIN hai bảng con). Với `Set` thì trùng luôn bị loại nên không có nhập nhằng.

**Cách tránh.** Hai truy vấn nối tiếp (mục 11.5.2), hoặc đổi sang `Set`. Đổi sang `Set` chỉ để lách exception là quyết định nên cân nhắc, vì nó không hết vấn đề nhân dòng (mục 11.5.3).

::: {.khac-du-doan codes="G5-S10a"}
**Điều nhiều người nghĩ:** exception nổi lên ngay khi gọi `createQuery`, vì câu truy vấn "sai cú pháp".

**Thực tế:** `createQuery` vẫn tạo được truy vấn bình thường. Exception chỉ ném khi **chạy** truy vấn (`getResultList()`), lúc Hibernate dựng câu SQL, và vẫn trước khi có câu SQL nào gửi xuống database.
:::

::: {.ghi-nho}
Không join fetch hai `List` trong cùng một truy vấn: Hibernate ném `MultipleBagFetchException` lúc chạy, trước khi có SQL.
:::

::: {.tracuu codes="G5-S10a"}
:::

#### 11.5.2 Hai truy vấn nối tiếp trong cùng transaction {.cot-loi}

**Bối cảnh.** Bạn vẫn cần cả đơn và payment, và không muốn đổi `List` sang `Set`.

**Khi nào xảy ra.** Bạn chạy hai truy vấn, mỗi truy vấn `join fetch` **một** collection, trong cùng transaction.

**Khi nào không xảy ra.** Nếu hai truy vấn nằm ở hai transaction khác nhau, entity ở truy vấn thứ nhất đã DETACHED và truy vấn thứ hai tạo ra các instance mới, không "điền" vào instance cũ.

**Ví dụ.**

```java
inTx(() -> {
    em.createQuery("select c from G5F1_Customer c left join fetch c.orders",
            F1Customer.class).getResultList();
    List<F1Customer> customers = em.createQuery(
            "select c from G5F1_Customer c left join fetch c.payments",
            F1Customer.class).getResultList();
    // cùng instance MANAGED, cả hai collection đã khởi tạo
});
```

**Kết quả.** Tổng 2 câu SELECT, mỗi câu một `left join`, không có nhân dòng. Danh sách vẫn 5 customer, và cả `getOrders()` lẫn `getPayments()` đều đã khởi tạo, không tốn thêm câu nào.

```sql
select fc1_0.id, fc1_0.name, o1_0.customer_id, o1_0.id, o1_0.order_no
from g5_f1_all_lazy.customer fc1_0
left join g5_f1_all_lazy.purchase_order o1_0 on fc1_0.id=o1_0.customer_id

select fc1_0.id, fc1_0.name, p1_0.customer_id, p1_0.id, p1_0.amount
from g5_f1_all_lazy.customer fc1_0
left join g5_f1_all_lazy.payment p1_0 on fc1_0.id=p1_0.customer_id
```

**Vì sao.** Cả hai truy vấn trả về **cùng những customer** nằm trong persistence context. Truy vấn thứ hai không tạo customer mới mà chỉ "điền" collection còn thiếu vào các instance đã có. Tổng số dòng là cộng (2 cộng 3 cho customer đầu tiên), không phải nhân.

::: {.ghi-nho}
Muốn tải nhiều List: mỗi truy vấn một collection, trong cùng transaction. Nhân dòng biến thành cộng dòng.
:::

::: {.tracuu codes="G5-S10b"}
:::

#### 11.5.3 Hai Set cùng join fetch: chạy được nhưng nhân dòng {.mo-rong}

**Bối cảnh.** Bạn đổi `orders` và `payments` sang `Set` để thoát `MultipleBagFetchException`. Mọi thứ chạy, kết quả đúng. Nhưng chạy được không có nghĩa là rẻ.

**Kết quả.** 1 câu SELECT với hai `left join fetch`, danh sách đúng 5 customer, customer đầu tiên có 2 đơn và 3 payment. Nhưng câu SQL trả về **6 dòng** cho customer đầu tiên (mỗi cặp đơn-payment một dòng: 2 nhân 3), thay vì 5 dòng (2 cộng 3) nếu tải riêng. Cả 5 customer cộng lại là 14 dòng: 6 dòng của customer đầu tiên và 8 dòng của bốn customer còn lại (mỗi người 2 đơn, payment là NULL).

```sql
select fs1_0.id, fs1_0.name, o1_0.customer_id, o1_0.id, o1_0.order_no,
    p1_0.customer_id, p1_0.id, p1_0.amount
from g5_f1s_all_lazy_set.customer fs1_0
left join g5_f1s_all_lazy_set.purchase_order o1_0 on fs1_0.id=o1_0.customer_id
left join g5_f1s_all_lazy_set.payment p1_0 on fs1_0.id=p1_0.customer_id
```

Con số 6 và 14 được kiểm chứng bằng cách chạy lại **đúng câu SQL đó** qua JDBC và đếm dòng. Hibernate gộp lại ở phía Java nên bạn không thấy nhân bản, nhưng database và mạng vẫn phải chuyển toàn bộ số dòng nhân lên. Với 100 đơn và 100 payment cho một khách, đó là 10.000 dòng thay vì 200.

::: {.ghi-nho}
Fetch hai collection trong một câu thì số dòng **nhân** với nhau. Tải từng collection riêng thì chỉ **cộng**.
:::

::: {.tracuu codes="G5-S11"}
:::

### 11.6 Phân trang kèm fetch collection

`join fetch` collection kèm `setMaxResults` là bẫy kinh điển. Vấn đề: SQL phân trang theo **dòng**, nhưng bạn muốn phân trang theo **customer**. Một customer có 2 đơn chiếm 2 dòng, nên "10 dòng đầu" không phải "10 customer đầu".

#### 11.6.1 join fetch collection kèm setMaxResults(2) {.cot-loi}

**Bối cảnh.** Màn hình danh sách khách cần hiển thị 2 khách mỗi trang, kèm đơn hàng của họ. Bạn viết `join fetch c.orders` và `setMaxResults(2)`. Bài viết cũ cảnh báo Hibernate sẽ "phân trang trong bộ nhớ".

**Khi nào xảy ra.** `join fetch` một collection kèm `setMaxResults` (hoặc `Pageable`).

**Khi nào không xảy ra.** Nếu bạn fetch quan hệ to-one (không nhân dòng) thì phân trang bình thường.

**Ví dụ.**

```java
inTx(() -> {
    List<F1Customer> page = em.createQuery(
            "select c from G5F1_Customer c join fetch c.orders "
            + "order by c.id", F1Customer.class)
        .setMaxResults(2)
        .getResultList();
});
```

**Kết quả.** Đúng 1 câu SELECT, **có cắt trang ở database**: customer được bọc trong một subquery có `offset ? rows fetch first ? rows only`, rồi mới join orders. Trang đầu có đúng 2 customer, và **mỗi customer vẫn đủ 2 đơn** (collection không bị cắt cụt). Log không có cảnh báo `HHH90003004`.

```sql
select fc1_0.id, fc1_0.name, o1_0.customer_id, o1_0.id, o1_0.order_no
from (select fc1_0.id, fc1_0.name
      from g5_f1_all_lazy.customer fc1_0
      where exists(select 1 from g5_f1_all_lazy.purchase_order o1_0
                   where fc1_0.id=o1_0.customer_id)
      order by fc1_0.id offset ? rows fetch first ? rows only) fc1_0(id, name)
join g5_f1_all_lazy.purchase_order o1_0 on fc1_0.id=o1_0.customer_id
order by fc1_0.id
```

**Vì sao.** Hibernate 7 nhận ra phải cắt trang theo customer chứ không theo dòng, nên chọn customer trước (subquery) rồi mới nối orders. Vì là inner join, subquery chỉ chọn customer có ít nhất một đơn.

::: {.khac-du-doan codes="G5-S12a"}
**Điều nhiều người nghĩ:** Hibernate tải **toàn bộ** các dòng join, cắt trang trong bộ nhớ Java và ghi cảnh báo `HHH90003004`. Đây là hành vi của Hibernate 5 và 6, và vẫn còn trong nhiều bài viết.

**Thực tế (Hibernate 7.4 trên PostgreSQL):** không có cảnh báo, SQL có `fetch first`, database cắt trang đúng ở bảng customer. Nếu dự án của bạn còn chạy phiên bản cũ hơn thì hành vi có thể khác, hãy kiểm tra trước khi tin.
:::

::: {.ghi-nho}
Trên Hibernate 7.4 với PostgreSQL, `join fetch` collection kèm phân trang được cắt ở database. Đừng áp dụng nguyên lời khuyên "phân trang trong bộ nhớ" của phiên bản cũ.
:::

::: {.tracuu codes="G5-S12a"}
:::

#### 11.6.2 Cách kinh điển: lấy id của trang, rồi fetch theo id {.cot-loi}

**Bối cảnh.** Dự án của bạn chạy nhiều phiên bản Hibernate và database khác nhau, bạn cần cách phân trang đúng ở mọi nơi.

**Khi nào xảy ra.** Bạn dùng hai truy vấn: một lấy id của trang (không join collection, nên LIMIT chạy đúng), một `join fetch` theo `where c.id in :ids`.

**Khi nào không xảy ra.** Không phải lúc nào cũng cần: nếu bản Hibernate và database của bạn đã cắt trang đúng (mục 11.6.1) thì một truy vấn là đủ.

**Ví dụ.**

```java
inTx(() -> {
    List<Long> pageIds = em.createQuery(
            "select c.id from G5F1_Customer c order by c.id", Long.class)
        .setMaxResults(2)
        .getResultList();
    List<F1Customer> firstPage = em.createQuery(
            "select c from G5F1_Customer c join fetch c.orders "
            + "where c.id in :ids order by c.id", F1Customer.class)
        .setParameter("ids", pageIds)
        .getResultList();
});
```

**Kết quả.** Tổng 2 câu SELECT. Câu đầu chỉ lấy id, có `fetch first ? rows only`. Câu thứ hai lấy đúng 2 customer đó kèm orders. Trang đầu có 2 customer, mỗi người đủ 2 đơn.

```sql
select fc1_0.id
from g5_f1_all_lazy.customer fc1_0
order by fc1_0.id
fetch first ? rows only

select fc1_0.id, fc1_0.name, o1_0.customer_id, o1_0.id, o1_0.order_no
from g5_f1_all_lazy.customer fc1_0
join g5_f1_all_lazy.purchase_order o1_0 on fc1_0.id=o1_0.customer_id
where fc1_0.id in (?, ?)
order by fc1_0.id
```

**Vì sao.** Câu đầu không join collection nên mỗi customer đúng một dòng, và LIMIT chạy trên customer. Câu thứ hai không có LIMIT nên không có gì bị cắt sai. Cách này đúng ở mọi version, đổi lại là hai câu.

::: {.ghi-nho}
Phân trang kèm collection mà muốn chắc chắn ở mọi phiên bản: truy vấn 1 lấy id của trang, truy vấn 2 `join fetch where id in`.
:::

::: {.tracuu codes="G5-S12b"}
:::

### 11.7 Collection EAGER {.bien-the}

Biến thể này đổi đúng một điểm so với baseline: `Customer.orders` là **EAGER**. Đây là anti-pattern nhưng rất phổ biến trong code cũ, thường là hậu quả của việc "sửa" `LazyInitializationException` bằng cách bật EAGER.

```java
@Entity(name = "G5F2_Customer")
public class F2Customer {

    // INVERSE side. EAGER: anti-pattern, luôn tải orders cùng customer.
    @OneToMany(mappedBy = "customer", fetch = FetchType.EAGER)
    private List<F2PurchaseOrder> orders = new ArrayList<>();

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    private List<F2Payment> payments = new ArrayList<>();
}
```

![Sơ đồ bảng customer, purchase_order và payment của biến thể collection EAGER](img/g5-f2.png){width=80%}

**Ưu điểm.** Không bao giờ gặp `LazyInitializationException` với `orders`: collection luôn có sẵn, kể cả khi customer đã DETACHED.

**Nhược điểm.** Mọi nơi tải customer đều bị "tặng kèm" orders, kể cả nơi không cần. Truy vấn JPQL nhiều customer tự động sinh N+1. Và nó **không tắt được** từ phía truy vấn một cách đáng tin: bạn không thể nói "lần này đừng tải" trừ khi dùng entity graph (mục 11.7.3).

**Nên dùng khi.** Gần như không bao giờ. Giữ LAZY và tải theo từng use case. Chỉ cân nhắc EAGER cho collection **nhỏ, bị chặn kích thước**, và luôn được dùng cùng cha.

#### 11.7.1 find customer luôn tải orders {.cot-loi}

**Bối cảnh.** Màn hình đổi tên khách chỉ cần `customer.name`, nhưng log cho thấy cả bảng `purchase_order` bị đụng tới.

**Khi nào xảy ra.** `@OneToMany(fetch = EAGER)` và bạn lấy customer bằng `find`.

**Khi nào không xảy ra.** Nếu quan hệ là LAZY thì `find` không tải collection.

**Ví dụ.**

```java
F2Customer customerAn = inTxReturning(() -> {
    F2Customer found = em.find(F2Customer.class, customerId);
    Hibernate.isInitialized(found.getOrders()); // true, dù chưa chạm vào
    return found;
});
customerAn.getOrders().size(); // chạy được ngay cả khi DETACHED
```

**Kết quả.** Đúng 1 câu SELECT có `left join purchase_order`. `orders` đã khởi tạo mà code chưa hề đụng tới, và dùng được cả sau khi customer DETACHED (2 phần tử).

```sql
select fc1_0.id, fc1_0.name, o1_0.customer_id, o1_0.id, o1_0.order_no
from g5_f2_eager_collection.customer fc1_0
left join g5_f2_eager_collection.purchase_order o1_0
    on fc1_0.id=o1_0.customer_id
where fc1_0.id=?
```

**Vì sao.** EAGER là lời hứa "tải cùng cha". Với `find`, Hibernate thực hiện bằng cách nối bảng. Bạn trả giá (đọc cả collection, nhân dòng nếu có nhiều collection EAGER) ở **mọi** lần `find` customer, dù không cần.

::: {.ghi-nho}
Collection EAGER bị tải ở mọi lần `find` cha, dù code không dùng tới.
:::

::: {.tracuu codes="G5-S13a"}
:::

#### 11.7.2 findAll customer sinh N+1 dù không lặp gì cả {.cot-loi}

**Bối cảnh.** Danh sách khách chỉ hiển thị tên. Bạn không hề gọi `getOrders()`, nhưng log đầy câu SELECT `purchase_order`.

**Khi nào xảy ra.** Collection EAGER và bạn lấy danh sách bằng JPQL hoặc `findAll()`.

**Khi nào không xảy ra.** Collection LAZY (chuẩn baseline): không chạm thì không tốn câu nào.

**Ví dụ.**

```java
inTx(() -> {
    List<F2Customer> customers = f2CustomerRepository.findAll();
    // không lặp, không getOrders(): chỉ findAll
});
```

**Kết quả.** 1 câu SELECT `customer`, rồi **5** câu SELECT `purchase_order`, mỗi câu `where customer_id = ?` cho một customer. Tổng 6 câu mà code không có dòng nào chạm vào orders.

```sql
select fc1_0.id, fc1_0.name
from g5_f2_eager_collection.customer fc1_0
-- rồi 5 lần, mỗi lần một customer:
select o1_0.customer_id, o1_0.id, o1_0.order_no
from g5_f2_eager_collection.purchase_order o1_0
where o1_0.customer_id=?
```

**Vì sao.** Giống mục 11.2.2: JPQL không tự JOIN, và EAGER buộc Hibernate tải orders cho từng customer trong kết quả. Khác biệt duy nhất so với mục 11.3.3 là lần này bạn không có lỗi gì để sửa, vì code **không làm gì sai**: lỗi nằm hoàn toàn ở mapping.

**Cách tránh.** Chuyển về LAZY, tải thêm bằng `join fetch` hoặc `@EntityGraph` ở truy vấn cần.

::: {.ghi-nho}
Collection EAGER biến mọi `findAll` thành N+1 tự động, ngay cả khi code không dùng collection đó.
:::

::: {.tracuu codes="G5-S13b"}
:::

#### 11.7.3 EntityGraph ghi đè EAGER của attribute không có trong graph {.cot-loi}

**Bối cảnh.** Bạn thừa hưởng entity có collection EAGER mà không đổi được. Với một màn hình, bạn chỉ cần payment và muốn bỏ qua orders. Bạn thử `@EntityGraph(attributePaths = "payments")`.

**Khi nào xảy ra.** Entity graph dùng ngữ nghĩa `fetchgraph` (mặc định của Spring Data): attribute nào **không** nằm trong graph thì bị coi là LAZY, kể cả khi mapping ghi EAGER.

**Khi nào không xảy ra.** Nếu bạn dùng ngữ nghĩa `loadgraph` thì attribute ngoài graph giữ nguyên mapping (EAGER vẫn tải).

**Ví dụ.**

```java
@EntityGraph(attributePaths = "payments")
@Query("select c from G5F2_Customer c")
List<F2Customer> findAllWithPaymentsEntityGraph();
```

**Kết quả.** Đúng 1 câu SELECT chỉ có `left join payment`. `payments` đã khởi tạo, còn `orders` (EAGER theo mapping) **chưa khởi tạo** và không có câu SELECT `purchase_order` nào.

```sql
select fc1_0.id, fc1_0.name, p1_0.customer_id, p1_0.id, p1_0.amount
from g5_f2_eager_collection.customer fc1_0
left join g5_f2_eager_collection.payment p1_0 on fc1_0.id=p1_0.customer_id
```

**Vì sao.** Spring Data truyền entity graph dưới hint `fetchgraph`, nên Hibernate coi graph là danh sách đầy đủ những gì cần tải. Bài học: graph là cách ghi đè EAGER theo từng truy vấn, nhưng nó **không phải lý do** để giữ EAGER trong mapping.

::: {.khac-du-doan codes="G5-S13c"}
**Điều nhiều người nghĩ:** mapping EAGER là bất biến; `@EntityGraph` chỉ có thể **thêm** quan hệ cần tải chứ không **bớt** được quan hệ EAGER.

**Thực tế:** với `fetchgraph` (mặc định của Spring Data), attribute không có trong graph bị coi là LAZY, kể cả khi mapping ghi EAGER. Orders không được tải.
:::

::: {.ghi-nho}
`@EntityGraph` kiểu `fetchgraph` tắt được EAGER của attribute không có trong graph. Hữu ích để chữa cháy, nhưng nên sửa gốc ở mapping.
:::

::: {.tracuu codes="G5-S13c"}
:::

### 11.8 N+1 qua ManyToOne và qua ManyToMany

Các mục trước xoay quanh `@OneToMany`. Mục này xem N+1 xuất hiện ở hai quan hệ khác. Lối thoát không cần tải entity (DTO projection) nằm ở mục 12.5.

#### 11.8.1 Lặp qua dòng hàng và đọc product: một cộng ba, và join fetch còn một {.mo-rong}

**Bối cảnh.** Màn hình danh sách dòng hàng hiển thị tên sản phẩm của từng dòng. Bạn lặp qua 20 dòng và gọi `item.getProduct().getName()`.

**Kết quả.** 1 câu SELECT `order_item`, rồi **3** câu SELECT `product`, **không phải 20**. Số câu SELECT phụ bằng số product **khác nhau** (PEN, BOOK, BAG), vì mỗi product đã tải xong thì lần sau lấy từ persistence context. Thêm `join fetch i.product` thì chỉ còn 1 câu SELECT có `join`.

```sql
select foi1_0.id, foi1_0.order_id, foi1_0.product_id, foi1_0.quantity
from g5_f1_all_lazy.order_item foi1_0
-- rồi 3 lần, mỗi lần một product khác nhau:
select fp1_0.id, fp1_0.name from g5_f1_all_lazy.product fp1_0 where fp1_0.id=?

-- sau khi sửa bằng join fetch i.product, chỉ còn một câu:
select foi1_0.id, foi1_0.order_id, p1_0.id, p1_0.name, foi1_0.quantity
from g5_f1_all_lazy.order_item foi1_0
join g5_f1_all_lazy.product p1_0 on p1_0.id=foi1_0.product_id
```

Hệ quả thực tế: N+1 qua `@ManyToOne` thường **nhẹ hơn** bạn tưởng khi số giá trị khác nhau ít (ví dụ vài trăm product cho hàng triệu dòng hàng), và **nặng** khi mỗi dòng trỏ tới một đối tượng khác nhau.

::: {.ghi-nho}
N+1 qua `@ManyToOne` tốn số câu bằng số phần tử **khác nhau** của quan hệ, không phải số dòng. `join fetch` đưa về 1.
:::

::: {.tracuu codes="G5-S16"}
:::

#### 11.8.2 N+1 qua ManyToMany: giống hệt collection {.mo-rong}

**Bối cảnh.** Mô hình `Product` có `@ManyToMany` tới `Tag` qua bảng nối (Chương 8). Bạn tải 5 product rồi đọc tags của từng product.

**Kết quả.** 1 câu SELECT `product`, rồi **5** câu SELECT trên bảng nối, mỗi câu `join tag` cho một product. Tổng 6 câu: N+1 y hệt `@OneToMany`, vì `@ManyToMany` cũng mặc định LAZY và cũng tải theo từng collection. Một câu `join fetch p.tags` gom product, bảng nối và tag vào đúng 1 câu SELECT.

```sql
select t1_0.product_id, t1_1.id, t1_1.name
from g7_m1_owner_product.product_tag t1_0
join g7_m1_owner_product.tag t1_1 on t1_1.id=t1_0.tag_id
where t1_0.product_id=?

-- sau khi sửa bằng join fetch p.tags:
select mp1_0.id, mp1_0.name, t1_0.product_id, t1_1.id, t1_1.name
from g7_m1_owner_product.product mp1_0
join g7_m1_owner_product.product_tag t1_0 on mp1_0.id=t1_0.product_id
join g7_m1_owner_product.tag t1_1 on t1_1.id=t1_0.tag_id
```

Mọi cách sửa của mục 11.4 đều dùng được ở đây. Trường hợp `@OneToOne` phía inverse (luôn bị tải dù khai báo LAZY) đã nói ở mục 7.2.

::: {.ghi-nho}
N+1 không phụ thuộc loại quan hệ: bất cứ collection LAZY nào được lặp qua đều có thể gây ra nó, và cách sửa giống nhau.
:::

::: {.tracuu codes="G7-S13a,G7-S13b"}
:::
