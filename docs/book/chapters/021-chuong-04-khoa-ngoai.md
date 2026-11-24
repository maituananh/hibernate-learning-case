## Chương 4. Ai giữ khóa ngoại: ManyToOne, OneToMany và mappedBy

Khi hai bảng có quan hệ một-nhiều, câu hỏi đầu tiên không phải là "dùng annotation nào" mà là: **cột khóa ngoại nằm ở bảng nào, và phía nào trong code Java có quyền ghi nó?** Trả lời sai câu này, Hibernate vẫn chạy, nhưng dữ liệu có thể bị ghi thiếu, ghi hai lần, hoặc tốn thêm những câu SQL không cần thiết.

Chương này đi qua chín cách khai báo quan hệ giữa `Customer` (bảng cha) và `PurchaseOrder` (bảng con), từ cách đơn giản nhất (chỉ `@ManyToOne`) đến những cách dễ sai (quên `mappedBy`). Mỗi cách là một mục có sơ đồ bảng, ưu nhược điểm, và các tình huống chạy thật trên PostgreSQL.

Trong chương này:

- Khái niệm owning side và inverse side (mục 4.1).
- Chín cách khai báo quan hệ, từ mục 4.2 đến 4.9.
- Bảng tổng kết "đặt quan hệ ở bảng cha hay bảng con" (mục 4.10).

Mọi biến thể trong chương không dùng cascade: entity nào cũng được `persist` tường minh, để cascade không làm nhiễu kết quả. Cascade được bàn riêng ở Chương 5.

### 4.1 Owning side, inverse side và nơi đặt khóa ngoại

::: {.kien-thuc-nen}
Trong cơ sở dữ liệu, một quan hệ một-nhiều chỉ cần **một** cột khóa ngoại ở bảng con (ví dụ `purchase_order.customer_id`). Trong Java, ta có thể khai báo quan hệ ở một hoặc cả hai phía: `@ManyToOne` trong `PurchaseOrder`, `@OneToMany` trong `Customer`.

Hibernate cần biết **phía nào quyết định giá trị của cột khóa ngoại**. Phía đó gọi là **owning side**. Phía còn lại là **inverse side**: Hibernate chỉ dùng nó để đọc (duyệt từ cha xuống con), và **bỏ qua nó khi ghi** vào database. Dấu hiệu nhận biết: inverse side có `mappedBy`.

Hibernate không tự đồng bộ hai phía trong bộ nhớ. Nếu bạn chỉ thay đổi một phía, phía kia trong cùng persistence context vẫn giữ giá trị cũ.

**Một ví dụ cụ thể.** Giả sử database có customer An (id 1) và bạn muốn tạo order A-001 cho An. Trong database, việc đó chỉ có nghĩa là: dòng của order A-001 có `customer_id = 1`. Không có cột nào ở bảng `customer` ghi lại "An có order nào".

Trong Java, bạn có hai cách nói cùng một ý đó: `order.setCustomer(an)` ("order này thuộc An") và `an.getOrders().add(order)` ("An có order này"). Hai câu này **trông tương đương nhưng Hibernate không đối xử như nhau**. Khi flush, Hibernate nhìn vào owning side để biết cần ghi `customer_id` bằng bao nhiêu. Nếu bạn chỉ nói bằng câu thuộc inverse side, Hibernate không nghe thấy gì và ghi NULL.

Quy tắc dùng xuyên suốt chương: **tìm xem phía nào có quyền ghi cột khóa ngoại (owning side), và luôn đặt giá trị ở phía đó**. Phía còn lại chỉ để tiện đọc.
:::

### 4.2 ManyToOne một chiều: cách đơn giản và được khuyến nghị {.bien-the}

Chỉ `PurchaseOrder` biết `Customer`. `Customer` không có collection.

```java
@Entity(name = "G1V1_PurchaseOrder")
@Table(schema = "g1_v1_m2o_uni", name = "purchase_order")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "...")
    private Long id;

    @Column(name = "order_no", nullable = false)
    private String orderNo;

    // OWNING side: cột customer_id nằm ngay trong bảng này.
    @ManyToOne
    @JoinColumn(name = "customer_id",
            foreignKey = @ForeignKey(name = "fk_order_customer"))
    private Customer customer;
}
```

![Bảng purchase_order có cột customer_id là khóa ngoại tới customer.id; một customer có nhiều purchase_order. Bảng address được hai khóa ngoại khác trỏ tới](img/g1-v1.png){width=85%}

**Ưu điểm.** Một câu INSERT cho mỗi order. Không có bảng nối, không có câu UPDATE bù. Khóa ngoại nằm đúng chỗ người đọc DDL mong đợi. Muốn lấy các order của một customer vẫn làm được bằng query (tình huống 4.2.5).

**Nhược điểm.** Không duyệt được từ `Customer` xuống danh sách order bằng code Java, phải viết query.

**Nên dùng khi.** Đây là lựa chọn mặc định. Chỉ chuyển sang cách khác khi thật sự cần điều hướng từ cha xuống con.

#### 4.2.1 Khóa ngoại nằm ở bảng con, cho phép NULL {.cot-loi}

**Bối cảnh.** Bạn khai báo `@ManyToOne` và muốn biết Hibernate tạo bảng ra sao.

**Khi nào xảy ra.** Mọi `@ManyToOne` không ghi thêm `optional` hay `nullable`: cột `customer_id` nằm ở bảng con, cho phép NULL, và có ràng buộc khóa ngoại.

**Khi nào không xảy ra.** Bảng cha `customer` không bao giờ chứa cột khóa ngoại cho quan hệ này. Nếu thêm `optional = false` hay `nullable = false`, cột sẽ thành NOT NULL (xem mục 4.3).

**Ví dụ.** Chỉ cần khai báo như đoạn code đầu mục 4.2, rồi để Hibernate sinh bảng.

**Kết quả.** DDL của hai bảng:

::: {.ddl schema="g1_v1_m2o_uni" tables="customer,purchase_order"}
:::

Cột `customer_id` không có `not null`. Tên ràng buộc là `fk_order_customer` vì mapping đặt tên tường minh bằng `@ForeignKey`.

**Vì sao.** Hibernate đặt cột khóa ngoại vào bảng chứa `@ManyToOne`, và mặc định coi quan hệ là tùy chọn.

::: {.ghi-nho}
Khóa ngoại luôn nằm ở phía `@ManyToOne` (bảng con). Tên cột mặc định là tên field cộng `_id`.
:::

::: {.tracuu codes="G1-S01"}
:::

#### 4.2.2 Tạo order cho customer đã có: dùng getReference, không cần SELECT customer {.cot-loi}

**Bối cảnh.** Customer An đã nằm trong database từ trước (ví dụ khách hàng cũ quay lại mua hàng). Request chỉ mang theo `customerId` và danh sách sản phẩm, bạn cần tạo thêm một order cho họ. Thói quen thông thường là `findById(customerId)` để lấy entity customer, rồi `order.setCustomer(customer)`. Cách này chạy đúng, nhưng có một câu SELECT bị lãng phí: bạn tải toàn bộ dòng customer chỉ để lấy lại đúng cái id mà bạn đã có sẵn trong tay. Với hệ thống tạo hàng nghìn order mỗi phút, những câu SELECT thừa này cộng lại thành tải đáng kể.

`getReference` giải quyết đúng chỗ này. Nó không đụng vào database mà trả về một **proxy**: một đối tượng đóng vai customer, chỉ nhớ id, và chỉ chạy SELECT khi bạn đọc một thuộc tính khác (ví dụ `getName()`).

**Khi nào xảy ra.** Dùng `em.getReference(Customer.class, id)`: Hibernate trả về một proxy chỉ chứa id, đủ để ghi khóa ngoại.

**Khi nào không xảy ra.** Nếu dùng `em.find(...)`, Hibernate chạy một câu SELECT để tải customer, rồi mới ghi khóa ngoại. Kết quả trong database giống nhau nhưng tốn thêm một câu SQL.

**Ví dụ.**

```java
inTx(() -> {
    Customer customerRef = em.getReference(Customer.class, customerId);
    Product productRef = em.getReference(Product.class, productId);

    PurchaseOrder order = new PurchaseOrder("A-001");   // TRANSIENT
    order.setCustomer(customerRef);
    em.persist(order);                                  // MANAGED

    em.persist(new OrderItem(order, productRef, 1));
    em.persist(new OrderItem(order, productRef, 2));
});
// Sau transaction: order là DETACHED.
```

**Kết quả.** Không có câu SELECT nào cho customer hay product. SQL chạy lúc commit:

```sql
insert into g1_v1_m2o_uni.purchase_order
    (billing_address_id, customer_id, order_no, shipping_address_id, id)
values (?, ?, ?, ?, ?)

insert into g1_v1_m2o_uni.order_item (order_id, product_id, quantity, id)
values (?, ?, ?, ?)

insert into g1_v1_m2o_uni.order_item (order_id, product_id, quantity, id)
values (?, ?, ?, ?)
```

Dữ liệu trong database sau khi chạy:

| Bảng | order_no | customer_id | quantity |
|---|---|---|---|
| purchase_order | A-001 | id của customer | |
| order_item | | | 1 |
| order_item | | | 2 |

Thay đổi: thêm một dòng `purchase_order` và hai dòng `order_item`. Giữ nguyên: bảng `customer` và `product`.

**Vì sao.** Hãy theo dõi từng bước. Khi bạn gọi `order.setCustomer(customerRef)`, Hibernate chỉ lưu lại tham chiếu tới proxy. Khi `persist(order)`, nó lấy id của order từ sequence nhưng chưa gửi INSERT. Đến lúc commit (flush), Hibernate đi qua các cột của order để dựng câu INSERT; với cột `customer_id`, nó hỏi proxy "id của bạn là gì?" và proxy trả lời ngay mà không cần chạm vào database. Cột khóa ngoại chỉ cần một con số, nên một proxy chưa được khởi tạo là đủ.

Bằng chứng là trong test, `Hibernate.isInitialized(customerRef)` vẫn trả về false sau khi order đã được persist: proxy chưa bao giờ phải tải dữ liệu.

Hệ quả cần nhớ: nếu id không tồn tại trong database, `getReference` vẫn trả về proxy bình thường, và lỗi chỉ xuất hiện lúc flush (vi phạm khóa ngoại) hoặc lúc bạn đọc một thuộc tính khác id.

::: {.ghi-nho}
Muốn gán khóa ngoại tới một entity đã có, dùng `getReference` (hoặc `getReferenceById` của Spring Data) để tiết kiệm một câu SELECT.
:::

::: {.tracuu codes="G1-S02"}
:::

#### 4.2.3 Con trỏ tới cha chưa được lưu: lỗi lúc flush {.cot-loi}

**Bối cảnh.** Một form đăng ký mua hàng cho phép khách mới nhập tên và đặt luôn đơn đầu tiên. Code của bạn làm theo thứ tự tự nhiên của câu chuyện: tạo `new Customer(...)`, tạo `new PurchaseOrder(...)`, gán customer vào order, rồi lưu order (vì order là thứ khách vừa đặt). Quên mất customer cũng là một entity mới, chưa có trong database. Không có cascade, nên Hibernate sẽ không tự lưu customer thay bạn.

Điều dễ gây hiểu nhầm là lệnh `persist(order)` **không báo lỗi gì**. Lỗi chỉ xuất hiện về sau, ở lúc commit, và thông báo nằm sâu trong một exception bị Spring bọc lại, nên nhiều người mất thời gian đi tìm nguyên nhân ở sai chỗ.

**Khi nào xảy ra.** Cha ở trạng thái TRANSIENT (chưa persist) và quan hệ không có cascade `PERSIST`. Lệnh `persist(order)` êm, nhưng lúc flush Hibernate ném `TransientPropertyValueException` ("references an unsaved transient instance").

**Khi nào không xảy ra.** Nếu bạn `persist(customer)` trước, hoặc quan hệ có cascade `PERSIST` (Chương 5), lỗi này không xảy ra.

**Ví dụ.**

```java
inTx(() -> {
    Customer customerNew = new Customer("Chưa lưu");   // TRANSIENT
    PurchaseOrder order = new PurchaseOrder("A-001");
    order.setCustomer(customerNew);
    em.persist(order);        // order MANAGED, customerNew vẫn TRANSIENT
    // Lúc commit, flush thất bại.
});
```

**Kết quả.** Exception nổi lên lúc commit, được Spring bọc thành `InvalidDataAccessApiUsageException`, nguyên nhân gốc là `TransientPropertyValueException`. Cả transaction rollback: bảng `purchase_order` và `customer` đều trống. Chỉ có một câu `select nextval(...)` lấy id từ sequence được chạy, không có INSERT nào.

**Vì sao.** Hãy xem Hibernate nhìn thấy gì lúc flush. Nó đi qua order đang MANAGED và thấy cột `customer_id` cần một giá trị. Nó nhìn vào `order.customer` và thấy một object **chưa có id** (vì chưa từng được persist). Hibernate không thể ghi một khóa ngoại trỏ tới một dòng chưa tồn tại, và cũng không được phép tự ý lưu thêm entity mà bạn chưa yêu cầu. Cách duy nhất còn lại là dừng lại và báo lỗi.

Lý do lỗi bị hoãn tới lúc flush thay vì báo ngay ở `persist(order)`: ở thời điểm đó, bạn vẫn có thể `persist(customer)` ở dòng tiếp theo để sửa tình hình. Hibernate chỉ kết luận "đã quá muộn" khi sắp ghi xuống database.

**Cách tránh.** Hoặc `persist(customer)` trước khi persist order, hoặc nếu customer thuộc về order theo nghiệp vụ, khai báo cascade `PERSIST` (Chương 5).

::: {.ghi-nho}
Không có cascade thì phải tự persist cha trước con. Lỗi xuất hiện lúc flush, không phải lúc gọi `persist`.
:::

::: {.tracuu codes="G1-S03"}
:::

#### 4.2.4 Hai khóa ngoại cùng trỏ tới một bảng {.mo-rong}

**Bối cảnh.** `PurchaseOrder` có địa chỉ thanh toán và địa chỉ giao hàng, cả hai trỏ tới bảng `address`, và mapping không ghi `@JoinColumn`.

**Kết quả.** Hibernate đặt tên cột theo **tên field**, không theo tên bảng đích: `billing_address_id` và `shipping_address_id`, mỗi cột có một khóa ngoại với tên khác nhau. Không có cột nào tên `address_id`.

::: {.ghi-nho}
Tên cột khóa ngoại mặc định lấy từ tên field, nên hai quan hệ tới cùng một bảng không đụng nhau.
:::

::: {.tracuu codes="G1-S05"}
:::

#### 4.2.5 Không có collection vẫn lấy được order của một customer {.mo-rong}

**Bối cảnh.** `Customer` không có collection `orders`. Làm sao lấy các order của một customer?

**Kết quả.** Hai cách, đều chỉ một câu SELECT lọc theo `customer_id`:

```java
// Cách 1: JPQL
em.createQuery(
        "select o from G1V1_PurchaseOrder o where o.customer = :customer",
        PurchaseOrder.class)
    .setParameter("customer", customerAn)
    .getResultList();

// Cách 2: derived query của Spring Data
List<PurchaseOrder> findByCustomerId(Long customerId);
```

Kết quả chỉ gồm order của customer được hỏi, order của người khác không lẫn vào. Chú ý: vì `customer` mặc định là EAGER, mỗi customer khác nhau trong kết quả còn kéo theo một câu SELECT customer (chủ đề của Chương 11).

::: {.ghi-nho}
Nhiều khi `@ManyToOne` một chiều cộng một query là đủ, không cần collection.
:::

::: {.tracuu codes="G1-S18a,G1-S18b"}
:::

### 4.3 Bắt buộc hay tùy chọn: nullable, optional {.bien-the}

Ba cách khai báo cho cùng một quan hệ `PurchaseOrder.customer`:

```java
// Mặc định: tùy chọn
@ManyToOne
@JoinColumn(name = "customer_id")

// Chỉ đặt nullable = false trên JoinColumn
@ManyToOne
@JoinColumn(name = "customer_id", nullable = false)

// Đặt optional = false trên ManyToOne
@ManyToOne(optional = false)
@JoinColumn(name = "customer_id")
```

![Bảng purchase_order với cột customer_id: cho phép NULL ở cách mặc định, NOT NULL ở hai cách còn lại](img/g1-v1nn.png){width=80%}

**Ưu điểm.** Hai cách `nullable = false` và `optional = false` đều khiến cột NOT NULL, Hibernate từ chối order thiếu customer **trước khi gửi SQL**, và tải order bằng `join` thay cho `left join`.

**Nhược điểm.** Cách mặc định cho phép order không có customer, và Hibernate phải dùng `left join` mỗi lần tải. Hai cách bắt buộc thì không lưu được order chưa biết customer.

**Nên dùng khi.** Nếu nghiệp vụ nói "order luôn có customer", khai báo `optional = false`: ý định rõ ràng nhất, và ba tác dụng (DDL, kiểm tra, join) đi cùng nhau.

#### 4.3.1 Mặc định: order không có customer vẫn lưu được, tải bằng left join {.cot-loi}

**Bối cảnh.** Mapping không ghi `nullable` hay `optional`. Order không có customer có hợp lệ không?

**Khi nào xảy ra.** Với `@ManyToOne` mặc định, `optional` là true. INSERT thành công với `customer_id` là NULL.

**Khi nào không xảy ra.** Với `optional = false` hoặc `nullable = false` (mục 4.3.2 đến 4.3.4), Hibernate từ chối.

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder order = new PurchaseOrder("A-001");   // customer = null
    em.persist(order);
});

// Tải lại bằng find (customer là EAGER theo mặc định)
inTx(() -> em.find(PurchaseOrder.class, orderId));
```

**Kết quả.** INSERT thành công. Dữ liệu:

| order_no | customer_id |
|---|---|
| A-001 | NULL |

Câu SELECT khi `find` dùng `left join`:

```sql
select po1_0.id, po1_0.billing_address_id, c1_0.id, c1_0.name,
    po1_0.order_no, po1_0.shipping_address_id
from g1_v1_m2o_uni.purchase_order po1_0
left join g1_v1_m2o_uni.customer c1_0 on c1_0.id=po1_0.customer_id
where po1_0.id=?
```

**Vì sao.** Vì quan hệ có thể NULL, nếu dùng inner join thì order không có customer sẽ biến mất khỏi kết quả. Hibernate buộc phải dùng `left join`.

::: {.ghi-nho}
Mặc định của `@ManyToOne` là tùy chọn: cho phép NULL và tải bằng left join.
:::

::: {.tracuu codes="G1-S04a"}
:::

#### 4.3.2 nullable bằng false: Hibernate cũng chặn trước, không đợi database {.cot-loi}

**Bối cảnh.** Nhiều tài liệu nói `nullable = false` trong `@JoinColumn` chỉ là chỉ dẫn cho DDL: database sẽ từ chối. Thực tế có đúng không?

**Khi nào xảy ra.** Persist order mà `customer` là null, khi `@JoinColumn(nullable = false)`. Hibernate ném `PropertyValueException` ngay lúc persist, **trước khi gửi INSERT**.

**Khi nào không xảy ra.** Với mapping mặc định, order không customer vẫn lưu được (mục 4.3.1).

**Ví dụ.**

```java
Throwable failure = catchThrowable(() -> inTx(() -> {
    PurchaseOrder order = new PurchaseOrder("A-001");   // customer = null
    em.persist(order);
}));
```

**Kết quả.** Exception là `org.hibernate.PropertyValueException` ("not-null property references a null or transient value"), ném thẳng, không bị Spring bọc. Không có câu INSERT nào, chỉ có `select nextval(...)`. Bảng `purchase_order` trống.

**Vì sao.** Hibernate 7.4.15 đọc `nullable = false` để coi property là bắt buộc, giống hệt `optional = false`.

::: {.khac-du-doan codes="G1-S04b"}
**Điều nhiều người nghĩ:** `nullable = false` chỉ ảnh hưởng DDL, database sẽ là nơi từ chối và Spring ném `DataIntegrityViolationException`.

**Thực tế:** Hibernate chặn trước bằng `PropertyValueException`, 0 câu INSERT.

Đã kiểm chứng trên Hibernate 7.4.15.Final.
:::

::: {.ghi-nho}
`nullable = false` không chỉ là chỉ dẫn cho DDL: Hibernate cũng kiểm tra và chặn trước khi gửi SQL.
:::

::: {.tracuu codes="G1-S04b"}
:::

#### 4.3.3 optional bằng false: Hibernate chặn trước {.cot-loi}

**Bối cảnh.** Cách khai báo "chính thống" để nói quan hệ bắt buộc là `optional = false`. Nó xử lý order thiếu customer ra sao?

**Khi nào xảy ra.** Persist order với `customer = null` khi `@ManyToOne(optional = false)`.

**Khi nào không xảy ra.** Với mapping mặc định, không có lỗi.

**Ví dụ.** Giống đoạn code ở mục 4.3.2, chỉ khác mapping.

**Kết quả.** `PropertyValueException` ném thẳng, 0 câu INSERT, bảng trống. DDL: cột `customer_id` NOT NULL. Kết quả giống hệt `nullable = false`.

**Vì sao.** Hibernate đọc `optional = false` để coi quan hệ là bắt buộc và kiểm tra ngay lúc persist, trước khi gửi SQL.

::: {.ghi-nho}
`optional = false` và `nullable = false` cho cùng kết quả quan sát được ở Hibernate 7.4.15.
:::

::: {.tracuu codes="G1-S04c"}
:::

#### 4.3.4 Cả hai cách bắt buộc đều tải bằng inner join {.cot-loi}

**Bối cảnh.** Khi tải order (customer là EAGER), Hibernate dùng loại join nào nếu quan hệ bắt buộc?

**Khi nào xảy ra.** Với `optional = false`, và cả với chỉ `nullable = false`: Hibernate dùng `join` (inner join).

**Khi nào không xảy ra.** Với mapping mặc định, nó dùng `left join` (mục 4.3.1).

**Ví dụ.** `em.find(PurchaseOrder.class, orderId)` trên hai biến thể.

**Kết quả.** Cả hai cho cùng câu SQL dạng:

```sql
select po1_0.id, po1_0.customer_id, c1_0.id, c1_0.name, po1_0.order_no
from g1_v1opt_m2o_optional_false.purchase_order po1_0
join g1_v1opt_m2o_optional_false.customer c1_0
    on c1_0.id=po1_0.customer_id
where po1_0.id=?
```

**Vì sao.** Hibernate biết mọi order đều có customer, nên không cần giữ dòng thiếu.

::: {.khac-du-doan codes="G1-S04d,G1-S04e"}
**Điều nhiều người nghĩ:** chỉ `optional = false` mới đổi sang inner join, còn `nullable = false` vẫn là left join vì nó chỉ là DDL.

**Thực tế:** cả hai đều dùng inner join.

Đã kiểm chứng trên Hibernate 7.4.15.Final.
:::

::: {.ghi-nho}
`optional` và `nullable` vừa ảnh hưởng DDL vừa ảnh hưởng hành vi runtime ở Hibernate 7.4.15. Chọn `optional = false` để ý định rõ ràng.
:::

::: {.tracuu codes="G1-S04d,G1-S04e"}
:::

### 4.4 Map cột khóa ngoại hai lần: Long customerId và ManyToOne {.bien-the}

Đôi khi code cần cả giá trị id thuần lẫn entity. Cách an toàn là map cùng cột `customer_id` hai lần, nhưng chỉ cho **một** property quyền ghi:

```java
// Property số thuần: DUY NHẤT có quyền ghi customer_id.
@Column(name = "customer_id")
private Long customerId;

// Phía object chỉ để đọc, nên tắt quyền ghi.
@ManyToOne
@JoinColumn(name = "customer_id", insertable = false, updatable = false)
private Customer customer;
```

![Bảng order_dual_mapping có cột customer_id được hai property cùng map; chỉ một property được ghi](img/g1-v1-dual.png){width=75%}

**Ưu điểm.** Đọc được cả id lẫn entity mà không cần tải customer. Hữu ích khi cần truyền id đi nơi khác.

**Nhược điểm.** Bẫy lớn: nếu code gán field entity thay vì field số, khóa ngoại **không được ghi** mà không báo lỗi. Hai property cho cùng một ý nghĩa cũng làm code dễ rối.

**Nên dùng khi.** Chỉ khi thực sự cần cả hai dạng. Nếu không, `@ManyToOne` thuần (mục 4.2) đơn giản hơn nhiều.

#### 4.4.1 Gán field số: khóa ngoại được ghi {.cot-loi}

**Bối cảnh.** Cột `customer_id` được hai property map. Property nào ghi khóa ngoại?

**Khi nào xảy ra.** Bạn gán `order.setCustomerId(id)`: property có quyền ghi, nên khóa ngoại xuống database đúng.

**Khi nào không xảy ra.** Nếu chỉ gán field entity `customer`, khóa ngoại không được ghi (mục 4.4.2).

**Ví dụ.**

```java
inTx(() -> {
    DualMappedOrder order = new DualMappedOrder("A-001");
    order.setCustomerId(customerId);
    em.persist(order);
});
```

**Kết quả.** Một câu INSERT có cột `customer_id`:

| order_no | customer_id |
|---|---|
| A-001 | id của customer |

**Vì sao.** Property số không bị tắt quyền ghi, nên là nguồn của cột.

::: {.ghi-nho}
Khi một cột được map hai lần, chỉ property có quyền ghi quyết định giá trị cột.
:::

::: {.tracuu codes="G1-S06a"}
:::

#### 4.4.2 Gán field entity: khóa ngoại bị NULL {.cot-loi}

**Bối cảnh.** Bạn quen gán entity: `order.setCustomer(customer)`. Với mapping này có còn đúng không?

**Khi nào xảy ra.** Chỉ gán field `customer` (đang `insertable = false`): Hibernate bỏ qua field này khi INSERT, nên `customer_id` là NULL, không có lỗi hay cảnh báo.

**Khi nào không xảy ra.** Nếu gán `customerId` (mục 4.4.1), khóa ngoại được ghi.

**Ví dụ.**

```java
inTx(() -> {
    DualMappedOrder order = new DualMappedOrder("A-001");
    Customer customerManaged = em.find(Customer.class, customerId);
    order.setCustomer(customerManaged);    // chỉ gán entity
    em.persist(order);
});
```

**Kết quả.** Câu INSERT không có giá trị khóa ngoại. Dữ liệu:

| order_no | customer_id |
|---|---|
| A-001 | NULL |

**Vì sao.** `insertable = false` và `updatable = false` bảo Hibernate đừng dùng field này để ghi cột.

::: {.ghi-nho}
Code trông đúng nhưng khóa ngoại mất: đây là bẫy phổ biến khi map cột khóa ngoại hai lần.
:::

::: {.tracuu codes="G1-S06b"}
:::

#### 4.4.3 Quên tắt quyền ghi: Hibernate không khởi động {.cot-loi}

**Bối cảnh.** Nếu bạn map cột hai lần mà cả hai property đều ghi được thì sao?

**Khi nào xảy ra.** Thiếu `insertable = false, updatable = false` ở một trong hai property: Hibernate không biết property nào là nguồn của cột, nên ném `MappingException` ngay lúc khởi động.

**Khi nào không xảy ra.** Khi một property bị tắt quyền ghi, như ở mục 4.4.1.

**Ví dụ.** Entity cố ý sai chứa hai property cùng map `customer_id` mà không tắt quyền ghi.

**Kết quả.** Hibernate không dựng được `SessionFactory`, thông báo:

```text
org.hibernate.MappingException: Column 'customer_id' is duplicated in
mapping for entity '...DuplicateColumnOrder' (use
'@Column(insertable=false, updatable=false)' when mapping multiple
properties to the same column)
```

**Vì sao.** Hibernate chặn từ sớm một mapping mơ hồ, để bạn không phát hiện lỗi muộn khi dữ liệu đã sai.

::: {.ghi-nho}
Lỗi mapping loại này xuất hiện ngay lúc khởi động ứng dụng, tốt hơn nhiều so với ghi sai dữ liệu âm thầm.
:::

::: {.tracuu codes="G1-S06c"}
:::

### 4.5 OneToMany một chiều không có JoinColumn: Hibernate tạo bảng nối {.bien-the}

Chỉ `Customer` biết các order. `PurchaseOrder` không có field `customer`. Mapping:

```java
// OWNING side duy nhất.
@OneToMany(fetch = FetchType.LAZY)
@JoinTable(schema = "g1_v2_o2m_uni_jointable", name = "customer_orders",
        joinColumns = @JoinColumn(name = "customer_id"),
        inverseJoinColumns = @JoinColumn(name = "orders_id"))
private List<PurchaseOrder> orders = new ArrayList<>();
```

`@JoinTable` ở đây chỉ để đặt bảng nối đúng schema và đặt tên cột. Hành vi không đổi nếu bỏ nó (xem lưu ý ở cuối mục).

![Ba bảng: customer, bảng nối customer_orders và purchase_order. Bảng nối có hai khóa ngoại, cột orders_id là duy nhất](img/g1-v2.png){width=90%}

**Ưu điểm.** Bảng `purchase_order` sạch: không có cột nào nhắc tới customer.

**Nhược điểm.** Mỗi order thêm vào tốn thêm một câu INSERT chỉ để ghi quan hệ. Gỡ một phần tử khỏi `List` làm Hibernate xóa hết rồi ghi lại các dòng bảng nối. Phải join thêm một bảng khi đọc.

**Nên dùng khi.** Hiếm khi. Đây là lý do không nên dùng `@OneToMany` một chiều trơn.

**Lưu ý về schema.** Bảng nối không tự nằm trong schema của entity: nếu không khai `@JoinTable(schema = ...)` thì nó rơi vào schema mặc định `public`. Lab khai tường minh để các biến thể không đụng nhau.

#### 4.5.1 DDL có bảng nối, bảng con không có khóa ngoại {.cot-loi}

**Bối cảnh.** Bạn chỉ khai `@OneToMany` ở `Customer` mà không nói gì thêm. Khóa ngoại nằm ở đâu?

**Khi nào xảy ra.** `@OneToMany` không có `@JoinColumn` và không có `mappedBy`: Hibernate tạo **bảng nối** gồm hai cột khóa ngoại, đặt UNIQUE trên cột order.

**Khi nào không xảy ra.** Có `@JoinColumn` (mục 4.6) thì không có bảng nối. Có `mappedBy` (mục 4.7) thì khóa ngoại nằm ở bảng con.

**Ví dụ.** Chỉ cần mapping ở đầu mục 4.5.

**Kết quả.** DDL của bảng nối và bảng con:

::: {.ddl schema="g1_v2_o2m_uni_jointable" tables="customer_orders,purchase_order"}
:::

Bảng `purchase_order` không có cột `customer_id`. Cột `orders_id` là `unique` vì mỗi order chỉ thuộc một customer.

**Vì sao.** Không có cột nào ở bảng con để ghi, nên Hibernate lưu quan hệ ở một bảng riêng.

::: {.ghi-nho}
`@OneToMany` một chiều không có `@JoinColumn` thì sinh bảng nối, và bảng con không có khóa ngoại.
:::

::: {.tracuu codes="G1-S07a"}
:::

#### 4.5.2 Thêm hai order tốn năm câu INSERT {.cot-loi}

**Bối cảnh.** Bạn thêm hai order cho một customer. Tốn bao nhiêu câu INSERT?

**Khi nào xảy ra.** Persist customer, persist hai order, rồi `add` order vào `customer.getOrders()`.

**Khi nào không xảy ra.** Với `@ManyToOne` một chiều (mục 4.2), mỗi order chỉ một câu INSERT, không có bảng nối.

**Ví dụ.**

```java
inTx(() -> {
    Customer customerAn = new Customer("An");
    PurchaseOrder orderA1 = new PurchaseOrder("A-001");
    PurchaseOrder orderA2 = new PurchaseOrder("A-002");
    em.persist(customerAn);
    em.persist(orderA1);
    em.persist(orderA2);

    customerAn.getOrders().add(orderA1);
    customerAn.getOrders().add(orderA2);
});
```

**Kết quả.** Năm câu INSERT: một cho customer, hai cho `purchase_order`, hai cho `customer_orders`:

```sql
insert into g1_v2_o2m_uni_jointable.customer (name, id) values (?, ?)
insert into g1_v2_o2m_uni_jointable.purchase_order (order_no, id) values (?, ?)
insert into g1_v2_o2m_uni_jointable.purchase_order (order_no, id) values (?, ?)
insert into g1_v2_o2m_uni_jointable.customer_orders (customer_id, orders_id)
values (?, ?)
insert into g1_v2_o2m_uni_jointable.customer_orders (customer_id, orders_id)
values (?, ?)
```

Dữ liệu bảng nối:

| customer_id | orders_id |
|---|---|
| id của An | id của A-001 |
| id của An | id của A-002 |

**Vì sao.** Mỗi order cần một dòng riêng trong bảng nối để ghi quan hệ.

::: {.ghi-nho}
Mỗi order thêm vào tốn thêm một câu INSERT cho bảng nối.
:::

::: {.tracuu codes="G1-S07b"}
:::

#### 4.5.3 Gỡ một order khỏi List: xóa hết bảng nối của customer rồi ghi lại {.cot-loi}

**Bối cảnh.** Customer An có ba order A-001, A-002, A-003, được ghi trong bảng nối thành ba dòng. Một order bị hủy nên bạn gỡ nó khỏi `customer.getOrders()`. Bạn mong đợi Hibernate chạy đúng một câu `DELETE` cho dòng tương ứng, vì "chỉ một phần tử thay đổi". Thực tế khác: Hibernate xóa **cả ba** dòng rồi ghi lại hai dòng còn lại. Với customer có hàng trăm order, đây là hàng trăm câu SQL cho một thao tác nhỏ.

**Khi nào xảy ra.** Collection là `List` không có `@OrderColumn` (gọi là bag): Hibernate không biết dòng nào là dòng nào.

**Khi nào không xảy ra.** Với `Set` hoặc `List` có `@OrderColumn`, Hibernate chỉ xóa đúng dòng cần xóa (Chương 8).

**Ví dụ.**

```java
inTx(() -> {
    Customer customerAn = em.find(Customer.class, customerId);
    customerAn.getOrders().remove(1);     // gỡ phần tử ở vị trí 1
});
```

**Kết quả.** Một câu DELETE toàn bộ dòng bảng nối của customer, rồi hai câu INSERT ghi lại:

```sql
delete from g1_v2_o2m_uni_jointable.customer_orders
where customer_id=?

insert into g1_v2_o2m_uni_jointable.customer_orders (customer_id, orders_id)
values (?, ?)

insert into g1_v2_o2m_uni_jointable.customer_orders (customer_id, orders_id)
values (?, ?)
```

Dữ liệu sau khi chạy: bảng nối còn hai dòng (A-001 và A-003). Bảng `purchase_order` vẫn còn **cả ba dòng**.

Thay đổi: bảng nối bị xóa sạch rồi ghi lại hai dòng. Giữ nguyên: ba dòng order, vì không có orphanRemoval nên không order nào bị xóa.

**Vì sao.** Kiểu `List` không có `@OrderColumn` được Hibernate gọi là **bag**: một túi các phần tử, có thể trùng nhau, và **không có vị trí lưu trong database**. Bảng nối chỉ có hai cột (customer_id, orders_id), không có cột nào cho biết "đây là phần tử thứ mấy" hay một khóa riêng cho dòng đó.

Vì vậy, khi một phần tử bị gỡ, Hibernate không có cách đáng tin cậy để chỉ ra đúng dòng cần xóa trong bảng nối (với bag có phần tử trùng nhau, hai dòng giống hệt nhau thì không phân biệt được). Giải pháp an toàn nhất mà nó chọn là xóa toàn bộ dòng của chủ sở hữu, rồi chèn lại những phần tử còn lại.

Kiểu `Set` được bàn ở Chương 8 (quan hệ nhiều-nhiều), nơi lab so sánh trực tiếp số câu SQL của `Set` và `List` khi gỡ một phần tử.

::: {.ghi-nho}
Gỡ một phần tử khỏi `List` không có `@OrderColumn` làm tái tạo toàn bộ dòng bảng nối của chủ sở hữu.
:::

::: {.tracuu codes="G1-S08"}
:::

### 4.6 OneToMany một chiều có JoinColumn: INSERT rồi UPDATE bù {.bien-the}

Thêm `@JoinColumn` thì Hibernate dùng khóa ngoại ở bảng con thay cho bảng nối. Nhưng `PurchaseOrder` vẫn không có field `customer`:

```java
// OWNING side duy nhất, dù cột customer_id nằm ở bảng purchase_order.
@OneToMany(fetch = FetchType.LAZY)
@JoinColumn(name = "customer_id")
private List<PurchaseOrder> orders = new ArrayList<>();
```

![Bảng purchase_order có cột customer_id, nhưng class PurchaseOrder không có field customer; quan hệ do Customer ghi](img/g1-v3.png){width=80%}

**Ưu điểm.** Không có bảng nối. Khóa ngoại nằm ở bảng con đúng chỗ.

**Nhược điểm.** Hibernate phải INSERT order trước (với `customer_id` là NULL) rồi UPDATE bù khóa ngoại: mỗi order tốn hai câu SQL. Cột khóa ngoại phải cho phép NULL.

**Nên dùng khi.** Hiếm khi. Muốn quan hệ hai chiều đúng cách hãy dùng `mappedBy` (mục 4.7).

#### 4.6.1 DDL: không có bảng nối, khóa ngoại ở bảng con {.cot-loi}

**Bối cảnh.** Thêm `@JoinColumn` khác gì so với mục 4.5?

**Khi nào xảy ra.** Có `@JoinColumn(name = "customer_id")` trên `@OneToMany`: cột `customer_id` nằm ở `purchase_order`, cho phép NULL, có khóa ngoại tới `customer`, dù class `PurchaseOrder` không có field `customer`.

**Khi nào không xảy ra.** Không có `@JoinColumn` thì có bảng nối (mục 4.5).

**Ví dụ.** Chỉ cần mapping ở đầu mục 4.6.

**Kết quả.** DDL:

::: {.ddl schema="g1_v3_o2m_uni_joincolumn" tables="customer,purchase_order"}
:::

**Vì sao.** `@JoinColumn` nói rõ tên cột ở bảng con, nên Hibernate không cần bảng nối.

::: {.ghi-nho}
`@OneToMany` cộng `@JoinColumn` đặt khóa ngoại ở bảng con, nhưng phía cha vẫn là owning side.
:::

::: {.tracuu codes="G1-S09a"}
:::

#### 4.6.2 Thêm hai order: hai INSERT rồi hai UPDATE bù {.cot-loi}

**Bối cảnh.** Hãy đặt mình vào vị trí Hibernate. Bạn đưa cho nó hai order mới và nói "hai order này thuộc customer An" bằng cách thêm chúng vào `customer.getOrders()`. Nhưng class `PurchaseOrder` hoàn toàn không có field nào nhắc đến customer, nên khi dựng câu INSERT cho order, Hibernate không có giá trị nào để đặt vào cột `customer_id`.

Kết quả là một quy trình hai bước kỳ lạ nhưng có lý do: INSERT order trước (khóa ngoại để trống), rồi bổ sung khóa ngoại bằng UPDATE. Thoạt nhìn có vẻ chỉ là chi tiết nhỏ, nhưng nó có hậu quả thật: gấp đôi số câu SQL, và cột khóa ngoại buộc phải cho phép NULL (nếu không, bước INSERT đầu tiên sẽ bị database từ chối, như mục 4.6.4).

**Khi nào xảy ra.** Persist customer và hai order, rồi `add` order vào collection.

**Khi nào không xảy ra.** Với mapping hai chiều chuẩn (mục 4.7), `order.customer` được gán và khóa ngoại có ngay trong câu INSERT, không UPDATE bù.

**Ví dụ.** Giống đoạn code ở mục 4.5.2, nhưng với mapping `@JoinColumn`.

**Kết quả.**

```sql
insert into g1_v3_o2m_uni_joincolumn.customer (name, id) values (?, ?)
insert into g1_v3_o2m_uni_joincolumn.purchase_order (order_no, id) values (?, ?)
insert into g1_v3_o2m_uni_joincolumn.purchase_order (order_no, id) values (?, ?)

update g1_v3_o2m_uni_joincolumn.purchase_order
set customer_id=?
where id=?

update g1_v3_o2m_uni_joincolumn.purchase_order
set customer_id=?
where id=?
```

Dữ liệu cuối cùng: hai order đều có `customer_id` của An. Nhưng để tới đó phải qua hai câu INSERT (với `customer_id` NULL) và hai câu UPDATE bù.

**Vì sao.** Entity `PurchaseOrder` không có field khóa ngoại, nên lúc INSERT order Hibernate chưa có giá trị để ghi. Quan hệ do collection của cha quản lý, nên khóa ngoại được ghi sau bằng UPDATE.

::: {.ghi-nho}
Phía "một" không nằm trong bảng con, nên Hibernate phải UPDATE bù khóa ngoại sau INSERT.
:::

::: {.tracuu codes="G1-S09b"}
:::

#### 4.6.3 Gỡ order khỏi collection: chỉ đặt khóa ngoại về NULL {.cot-loi}

**Bối cảnh.** Bạn gỡ một order khỏi `customer.getOrders()`. Order có bị xóa không?

**Khi nào xảy ra.** Collection có `@JoinColumn`, không cascade, không orphanRemoval: Hibernate chỉ `UPDATE ... set customer_id = null`, dòng order vẫn còn trong database.

**Khi nào không xảy ra.** Với `orphanRemoval = true` (Chương 6), order bị DELETE.

**Ví dụ.**

```java
inTx(() -> {
    Customer customer = em.find(Customer.class, customerId);
    customer.getOrders().remove(0);
});
```

**Kết quả.**

```sql
update g1_v3_o2m_uni_joincolumn.purchase_order
set customer_id=null
where customer_id=? and id=?
```

Dữ liệu `purchase_order` sau khi chạy:

| order_no | customer_id |
|---|---|
| A-001 | NULL |
| A-002 | id của customer |

Thay đổi: khóa ngoại của order bị gỡ thành NULL. Giữ nguyên: cả hai dòng order vẫn còn, order A-001 thành "mồ côi" (không thuộc customer nào).

**Vì sao.** Gỡ khỏi collection chỉ có nghĩa "quan hệ này không còn", chứ không có nghĩa "xóa entity".

::: {.ghi-nho}
Gỡ khỏi collection không xóa dòng. Muốn xóa hẳn cần `orphanRemoval` (Chương 6).
:::

::: {.tracuu codes="G1-S11"}
:::

#### 4.6.4 Thêm nullable bằng false: persist order trước khi add thì database từ chối {.mo-rong}

**Bối cảnh.** Có thể thêm `nullable = false` vào `@JoinColumn` để cột NOT NULL và hy vọng bỏ được UPDATE bù không?

**Kết quả.** Phụ thuộc thứ tự gọi. Nếu `persist(order)` **trước** khi `add` vào collection, lúc INSERT Hibernate chưa biết customer là ai nên bind NULL, và PostgreSQL từ chối bằng `DataIntegrityViolationException`. Khác với mục 4.3.2: ở đó Hibernate chặn trước, còn ở đây câu INSERT thực sự được gửi đi rồi mới bị database từ chối. Transaction rollback, không có order nào được ghi.

::: {.khac-du-doan codes="G1-S10a"}
**Điều nhiều người nghĩ:** `nullable = false` luôn khiến Hibernate đưa khóa ngoại vào INSERT, hoặc ít nhất Hibernate chặn trước như ở quan hệ `@ManyToOne`.

**Thực tế:** với `@OneToMany` một chiều, giá trị khóa ngoại phụ thuộc thứ tự `persist` và `add`. Sai thứ tự thì INSERT bind NULL và database từ chối.

Đã kiểm chứng trên Hibernate 7.4.15.Final.
:::

::: {.ghi-nho}
`nullable = false` ở `@OneToMany` một chiều thêm một bẫy phụ thuộc thứ tự gọi, chứ không giải quyết được vấn đề.
:::

::: {.tracuu codes="G1-S10a"}
:::

#### 4.6.5 Đúng thứ tự thì INSERT có khóa ngoại, nhưng UPDATE thừa vẫn còn {.mo-rong}

**Bối cảnh.** Nếu gọi đúng thứ tự (persist customer, `add` order vào collection, rồi persist order) thì UPDATE bù có biến mất không?

**Kết quả.** Không. INSERT mang `customer_id` đúng giá trị, nhưng Hibernate vẫn gửi hai câu `update ... set customer_id=? where id=?` cùng giá trị, tức là thừa. Tổng số câu SQL không ít hơn mục 4.6.2, lại thêm bẫy thứ tự. Dữ liệu cuối: hai order đều có `customer_id` của An.

::: {.khac-du-doan codes="G1-S10b"}
**Điều nhiều người nghĩ:** `nullable = false` cho phép đưa khóa ngoại vào INSERT nên bỏ được UPDATE bù.

**Thực tế:** UPDATE thừa vẫn còn.

Đã kiểm chứng trên Hibernate 7.4.15.Final.
:::

::: {.ghi-nho}
Muốn bỏ UPDATE thừa, dùng hai chiều với `mappedBy` (mục 4.7): một câu INSERT là đủ.
:::

::: {.tracuu codes="G1-S10b"}
:::

### 4.7 Hai chiều chuẩn: OneToMany có mappedBy và ManyToOne {.bien-the}

Đây là cách khuyến nghị khi cần điều hướng cả hai chiều. `PurchaseOrder` là owning side, `Customer.orders` là inverse side:

```java
// Trong Customer: INVERSE side.
// mappedBy = "customer" nghĩa là "khóa ngoại do field customer của
// PurchaseOrder quản lý, tôi chỉ đọc".
@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
private List<PurchaseOrder> orders = new ArrayList<>();

// Helper đồng bộ hai phía (pattern khuyến nghị).
public void addOrder(PurchaseOrder order) {
    orders.add(order);
    order.setCustomer(this);
}

public void removeOrder(PurchaseOrder order) {
    orders.remove(order);
    order.setCustomer(null);
}
```

```java
// Trong PurchaseOrder: OWNING side, duy nhất ghi khóa ngoại.
@ManyToOne
@JoinColumn(name = "customer_id")
private Customer customer;
```

![Bảng purchase_order có khóa ngoại customer_id; collection orders của Customer không tạo cột hay bảng nào](img/g1-v4.png){width=80%}

**Ưu điểm.** Một câu INSERT cho mỗi order, giống mục 4.2. Vừa duyệt được từ cha xuống con, vừa từ con lên cha. Không có bảng nối hay UPDATE bù.

**Nhược điểm.** Phải nhớ đồng bộ hai phía trong bộ nhớ (dùng helper). Quên `mappedBy` gây ra lỗi nghiêm trọng (mục 4.8).

**Nên dùng khi.** Khi cần cả `customer.getOrders()` và `order.getCustomer()`. Luôn dùng helper `addOrder` và `removeOrder`.

#### 4.7.1 Chỉ thêm vào collection phía mappedBy: khóa ngoại bị để NULL {.cot-loi}

**Bối cảnh.** Đây là lỗi phổ biến nhất của quan hệ hai chiều. Một lập trình viên nghĩ theo hướng nghiệp vụ: "khách An mua thêm một đơn", nên viết `customer.getOrders().add(order)`, rồi `persist(order)`. Đọc code rất tự nhiên, và nó **chạy không lỗi**. Test đơn vị chỉ kiểm tra `customer.getOrders().size()` cũng xanh, vì trong bộ nhớ mọi thứ đều đúng.

Vấn đề chỉ lộ ra khi ai đó nhìn vào database, hoặc khi dữ liệu được đọc lại ở một request khác: order không thuộc customer nào. Lúc đó người ta thường mất rất lâu để lần ra nguyên nhân, vì chẳng có exception hay log nào chỉ đường.

**Khi nào xảy ra.** Mọi lúc bạn chỉ thay đổi inverse side. Hibernate chỉ ghi khóa ngoại theo owning side (`order.customer`), mà nó vẫn null.

**Khi nào không xảy ra.** Nếu gán `order.setCustomer(customer)` (hoặc dùng helper), khóa ngoại được ghi.

**Ví dụ.**

```java
inTx(() -> {
    Customer customerAn = em.find(Customer.class, customerId);
    PurchaseOrder order = new PurchaseOrder("A-001");
    customerAn.getOrders().add(order);    // chỉ đổi phía inverse
    em.persist(order);
    // customerAn và order đều MANAGED
});
```

**Kết quả.** Câu INSERT được gửi, nhưng khóa ngoại là NULL:

```sql
insert into g1_v4_bidi_mappedby.purchase_order (customer_id, order_no, id)
values (?, ?, ?)
```

Dữ liệu sau khi chạy:

| order_no | customer_id |
|---|---|
| A-001 | NULL |

Không có lỗi hay cảnh báo nào.

**Vì sao.** Khi flush, Hibernate dựng câu INSERT cho order bằng cách đọc các property của chính entity `PurchaseOrder`. Với cột `customer_id`, nó đọc `order.customer`. Giá trị đó vẫn là null vì bạn chưa gán, nên cột được ghi NULL.

Còn collection `customer.orders`? Nó có `mappedBy = "customer"`, tức là lời tuyên bố "tôi không sở hữu khóa ngoại này". Hibernate đọc collection đó để biết cần tải gì khi duyệt từ customer xuống, nhưng khi ghi nó **không dùng collection đó để suy ra khóa ngoại**. Nhìn từ Hibernate, việc bạn thêm order vào collection chỉ là một thay đổi trong bộ nhớ, không có hậu quả gì với database.

Thêm một chi tiết dễ gây nhầm: chính phiên làm việc đó vẫn thấy mọi thứ đúng (`customer.getOrders()` có order). Chỉ khi tải lại customer từ database ở một transaction khác thì order mới biến mất khỏi danh sách.

**Cách tránh.** Luôn gán owning side, tốt nhất qua helper `addOrder`.

::: {.ghi-nho}
`mappedBy` nghĩa là "tôi chỉ đọc". Chỉ thêm vào collection này sẽ không ghi được khóa ngoại.
:::

::: {.tracuu codes="G1-S12"}
:::

#### 4.7.2 Chỉ gán owning side: database đúng nhưng collection trong bộ nhớ bị lệch {.cot-loi}

**Bối cảnh.** Đây là chiều ngược lại của tình huống trước: bạn làm đúng, gán owning side `order.setCustomer(customer)`, và chỉ quên cập nhật collection của customer. Database sẽ đúng. Nhưng nếu cùng transaction (hoặc cùng request) có đoạn code khác đọc `customer.getOrders()` để tính tổng tiền, kiểm tra số đơn, hay gửi thông báo, đoạn code đó sẽ thấy danh sách **thiếu** order vừa tạo.

Đây là kiểu lỗi chập chờn: chạy đúng khi customer được tải mới từ database (collection được đọc từ khóa ngoại), nhưng sai khi customer đã nằm sẵn trong persistence context với collection đã khởi tạo. Cùng một đoạn code có thể đúng ở test này và sai ở test khác.

**Khi nào xảy ra.** Customer đã MANAGED và collection `orders` đã được khởi tạo. Khóa ngoại được ghi đúng, nhưng trong **cùng persistence context**, `customer.getOrders()` vẫn không chứa order mới.

**Khi nào không xảy ra.** Sau `em.clear()` và tải lại từ database, collection mới thấy order. Dùng helper thì bộ nhớ khớp ngay.

**Ví dụ.**

```java
inTx(() -> {
    Customer customerAn = em.find(Customer.class, customerId);
    // customerAn.getOrders() đang rỗng, đã khởi tạo

    PurchaseOrder orderA1 = new PurchaseOrder("A-001");
    orderA1.setCustomer(customerAn);      // chỉ đổi phía owning
    em.persist(orderA1);
    em.flush();

    customerAn.getOrders();               // vẫn rỗng!

    em.clear();                           // bỏ cache cấp 1
    Customer reloaded = em.find(Customer.class, customerId);
    reloaded.getOrders();                 // có 1 order
});
```

**Kết quả.** Sau `flush`, bảng `purchase_order` có một dòng với `customer_id` đúng. Nhưng collection trong bộ nhớ vẫn rỗng cho tới khi `clear()` và tải lại.

**Vì sao.** Persistence context giữ một đối tượng `Customer` duy nhất cho mỗi id, và collection `orders` của nó đã được nạp sẵn (lúc này rỗng). Hibernate không có cơ chế tự "đuổi theo" mọi thay đổi ở owning side để cập nhật các collection liên quan: nó coi bộ nhớ là việc của bạn, còn database là việc của nó. Khi bạn gán `order.customer`, Hibernate ghi nhận để lúc flush ghi khóa ngoại, nhưng không đụng tới danh sách trong bộ nhớ của customer.

Chỉ sau `em.clear()`, đối tượng customer cũ bị bỏ khỏi persistence context. Lần `find` tiếp theo tải customer mới từ database, collection được nạp lại bằng câu SELECT theo `customer_id`, và lúc này thấy order.

**Cách tránh.** Dùng helper đồng bộ cả hai phía (mục 4.7.3).

::: {.ghi-nho}
Database đúng nhưng bộ nhớ lệch: code đọc `customer.getOrders()` trong cùng transaction sẽ thấy thiếu.
:::

::: {.tracuu codes="G1-S13"}
:::

#### 4.7.3 Helper addOrder: khóa ngoại đúng, bộ nhớ khớp, một INSERT là đủ {.cot-loi}

**Bối cảnh.** Helper `addOrder` vừa thêm vào collection vừa gán `order.customer`. Có đạt cả hai mục tiêu không?

**Khi nào xảy ra.** Mọi lúc bạn dùng helper: khóa ngoại đúng ngay trong câu INSERT, bộ nhớ khớp.

**Khi nào không xảy ra.** Bỏ qua helper và chỉ đổi một phía thì sẽ rơi vào hai tình huống ở mục 4.7.1 và 4.7.2.

**Ví dụ.**

```java
inTx(() -> {
    Customer customerAn = em.find(Customer.class, customerId);
    PurchaseOrder order = new PurchaseOrder("A-001");
    customerAn.addOrder(order);     // add vào collection VÀ gán order.customer
    em.persist(order);
});
```

**Kết quả.** Một câu INSERT với khóa ngoại đúng, không có UPDATE:

| order_no | customer_id |
|---|---|
| A-001 | id của An |

**Vì sao.** Helper gán owning side nên khóa ngoại được ghi; cũng add vào collection nên bộ nhớ khớp.

::: {.ghi-nho}
Với quan hệ hai chiều, luôn dùng helper `addX` và `removeX` thay vì thao tác thẳng trên collection.
:::

::: {.tracuu codes="G1-S14a"}
:::

#### 4.7.4 Helper removeOrder: khóa ngoại về NULL, dòng order vẫn còn {.cot-loi}

**Bối cảnh.** Gỡ order khỏi customer bằng helper. Order có bị xóa không?

**Khi nào xảy ra.** Helper đặt `order.customer = null`, nên Hibernate chỉ `UPDATE ... set customer_id=?`. Dòng order vẫn còn.

**Khi nào không xảy ra.** Với `orphanRemoval = true` (Chương 6), dòng sẽ bị xóa.

**Ví dụ.**

```java
inTx(() -> {
    Customer customerAn = em.find(Customer.class, customerId);
    PurchaseOrder order = customerAn.getOrders().get(0);
    customerAn.removeOrder(order);
});
```

**Kết quả.**

```sql
update g1_v4_bidi_mappedby.purchase_order
set customer_id=?, order_no=?
where id=?
```

Dữ liệu sau khi chạy:

| order_no | customer_id |
|---|---|
| A-001 | NULL |

Thay đổi: `customer_id` thành NULL. Giữ nguyên: dòng order vẫn còn. Lưu ý: câu UPDATE ghi cả cột `order_no` vì Hibernate mặc định cập nhật mọi cột (chủ đề `@DynamicUpdate` ở Chương 14).

**Vì sao.** Helper đặt `order.customer` về null. Đó là owning side, nên dirty checking thấy thay đổi và UPDATE khóa ngoại.

::: {.ghi-nho}
Gỡ quan hệ bằng helper không xóa dòng order, chỉ đặt khóa ngoại về NULL.
:::

::: {.tracuu codes="G1-S14b"}
:::

#### 4.7.5 mappedBy trỏ sai tên field: Hibernate không khởi động {.mo-rong}

**Bối cảnh.** Bạn gõ `mappedBy = "khachHang"` trong khi field thật tên là `customer`.

**Kết quả.** Hibernate không khởi động được, ném `AnnotationException` với thông báo: "Collection '...BrokenMappedByCustomer.orders' is 'mappedBy' a property named 'khachHang' which does not exist". Lỗi bị bắt ngay lúc chạy ứng dụng, không âm thầm ghi sai dữ liệu như mục 4.7.1.

::: {.ghi-nho}
Sai tên trong `mappedBy` là lỗi rõ ràng lúc khởi động. Quên hẳn `mappedBy` mới là lỗi nguy hiểm (mục 4.8).
:::

::: {.tracuu codes="G1-S17"}
:::

### 4.8 Quên mappedBy: hai owning side cho cùng một quan hệ {.bien-the}

Giống mục 4.7 nhưng bỏ `mappedBy`. Đây là lỗi kinh điển:

```java
// Trong Customer: quên mappedBy. Hibernate coi đây là một quan hệ một chiều
// RIÊNG và tạo bảng nối, tách biệt với PurchaseOrder.customer.
@OneToMany(fetch = FetchType.LAZY)
@JoinTable(schema = "g1_v5_bidi_no_mappedby", name = "customer_orders",
        joinColumns = @JoinColumn(name = "customer_id"),
        inverseJoinColumns = @JoinColumn(name = "orders_id"))
private List<PurchaseOrder> orders = new ArrayList<>();
```

![Cùng một ý nghĩa "order thuộc customer" được lưu ở hai nơi: cột customer_id của purchase_order và bảng nối customer_orders](img/g1-v5.png){width=90%}

**Ưu điểm.** Không có.

**Nhược điểm.** Cùng một sự thật bị lưu ở hai nơi, tốn thêm một câu INSERT so với mục 4.7, và hai nơi có thể mâu thuẫn nhau.

**Nên dùng khi.** Không bao giờ. Nếu bạn thấy DDL có cả cột khóa ngoại lẫn bảng nối cho cùng một quan hệ, hãy kiểm tra `mappedBy`.

#### 4.8.1 DDL có cả cột khóa ngoại lẫn bảng nối {.cot-loi}

**Bối cảnh.** Khai báo hai chiều mà quên `mappedBy`: Hibernate tạo cấu trúc bảng thế nào?

**Khi nào xảy ra.** `@ManyToOne` ở `PurchaseOrder` và `@OneToMany` không `mappedBy` ở `Customer`: Hibernate coi đây là hai quan hệ một chiều riêng biệt.

**Khi nào không xảy ra.** Có `mappedBy` thì chỉ còn một nơi lưu (mục 4.7).

**Ví dụ.** Mapping ở đầu mục 4.8.

**Kết quả.** Có cả `purchase_order.customer_id` lẫn bảng nối `customer_orders`:

::: {.ddl schema="g1_v5_bidi_no_mappedby" tables="customer_orders,purchase_order"}
:::

**Vì sao.** Mỗi owning side tạo cấu trúc riêng của mình.

::: {.ghi-nho}
Nếu DDL có cả cột khóa ngoại lẫn bảng nối cho một quan hệ, nghĩa là đã quên `mappedBy`.
:::

::: {.tracuu codes="G1-S15a"}
:::

#### 4.8.2 Gán cả hai phía: dữ liệu bị ghi hai lần {.cot-loi}

**Bối cảnh.** Quên `mappedBy` là lỗi khó phát hiện nhất của chương này, vì bạn làm mọi thứ đúng theo thói quen: dùng helper, gán cả hai phía, test xanh, dữ liệu đọc ra đều có vẻ hợp lý. Hibernate không hề phàn nàn. Hệ quả chỉ lộ ra khi ai đó xem DDL (thấy hai nơi lưu cùng một quan hệ) hoặc khi dữ liệu bắt đầu lệch giữa hai nơi (mục 4.8.3).

**Khi nào xảy ra.** Mỗi owning side tự ghi phần của mình.

**Khi nào không xảy ra.** Với `mappedBy`, chỉ owning side ghi và chỉ có một câu INSERT.

**Ví dụ.**

```java
inTx(() -> {
    Customer customerAn = new Customer("An");
    PurchaseOrder order = new PurchaseOrder("A-001");
    em.persist(customerAn);
    order.setCustomer(customerAn);        // owning side số 1: cột customer_id
    customerAn.getOrders().add(order);    // owning side số 2: bảng nối
    em.persist(order);
});
```

**Kết quả.**

```sql
insert into g1_v5_bidi_no_mappedby.customer (name, id) values (?, ?)
insert into g1_v5_bidi_no_mappedby.purchase_order (customer_id, order_no, id)
values (?, ?, ?)
insert into g1_v5_bidi_no_mappedby.customer_orders (customer_id, orders_id)
values (?, ?)
```

Dữ liệu: `purchase_order` có `customer_id` của An, và bảng nối `customer_orders` cũng có một dòng (An, order). Cùng một quan hệ lưu hai nơi.

**Vì sao.** Không có `mappedBy`, Hibernate không biết hai khai báo này nói về cùng một quan hệ. Với nó, `PurchaseOrder.customer` là một quan hệ nhiều-một với khóa ngoại nằm trong bảng `purchase_order`; còn `Customer.orders` là một quan hệ một-nhiều khác hẳn, lưu trong một bảng nối riêng. Khi flush, mỗi khai báo tự ghi dữ liệu của mình: một câu INSERT cho order (kèm `customer_id`), và một câu INSERT cho bảng nối.

Hệ quả thực tế: mỗi lần thêm order tốn thêm một câu SQL, và quan trọng hơn, **hai bản sao của cùng một sự thật** có thể lệch nhau bất cứ lúc nào, như mục 4.8.3 cho thấy.

::: {.ghi-nho}
Quên `mappedBy` tốn thêm một câu INSERT và nhân đôi nguồn dữ liệu.
:::

::: {.tracuu codes="G1-S15b"}
:::

#### 4.8.3 Chỉ gán ManyToOne: hai nguồn sự thật mâu thuẫn {.cot-loi}

**Bối cảnh.** Bạn chỉ `order.setCustomer(An)` như ở mục 4.7. `customer.getOrders()` ra gì?

**Khi nào xảy ra.** Khóa ngoại là An, nhưng bảng nối trống. Đọc từ phía order ra An; đọc từ phía customer ra danh sách rỗng.

**Khi nào không xảy ra.** Với `mappedBy`, `customer.getOrders()` đọc từ chính khóa ngoại, nên hai phía luôn nhất quán.

**Ví dụ.**

```java
inTx(() -> {
    Customer customerAn = em.find(Customer.class, customerId);
    PurchaseOrder order = new PurchaseOrder("A-001");
    order.setCustomer(customerAn);        // chỉ owning side số 1
    em.persist(order);
});
```

**Kết quả.** Dữ liệu trong database:

| Bảng | Nội dung |
|---|---|
| purchase_order | order A-001 có `customer_id` của An |
| customer_orders | trống |

Khi tải lại customer trong một transaction mới, `customer.getOrders()` trả về **rỗng** dù khóa ngoại đã trỏ về An.

**Vì sao.** `customer.getOrders()` đọc từ bảng nối (trống), không đọc từ khóa ngoại.

::: {.ghi-nho}
Quên `mappedBy` khiến cùng một câu hỏi "order này của ai?" có hai đáp án khác nhau tùy đi đường nào.
:::

::: {.tracuu codes="G1-S15c"}
:::

### 4.9 Owning side nằm ở phía một: biến thể hiếm gặp {.bien-the}

Đây là mục 4.7 lật ngược. Khóa ngoại vẫn ở bảng con, nhưng phía **cha** được ghi, còn phía con chỉ đọc:

```java
// Trong Customer: OWNING side (có @JoinColumn, không có mappedBy).
@OneToMany(fetch = FetchType.LAZY)
@JoinColumn(name = "customer_id")
private List<PurchaseOrder> orders = new ArrayList<>();
```

```java
// Trong PurchaseOrder: chỉ đọc, tắt quyền ghi.
@ManyToOne
@JoinColumn(name = "customer_id", insertable = false, updatable = false)
private Customer customer;
```

![Bảng purchase_order có khóa ngoại customer_id; phía Customer ghi, phía PurchaseOrder chỉ đọc](img/g1-v6.png){width=80%}

**Ưu điểm.** Hai chiều điều hướng, và phía cha chủ động kiểm soát quan hệ.

**Nhược điểm.** Ngược với thói quen: `order.setCustomer(...)` không ghi gì. Phía cha ghi bằng INSERT rồi UPDATE bù, như mục 4.6.

**Nên dùng khi.** Hiếm khi. Dùng mục 4.7 trừ khi có lý do đặc biệt.

#### 4.9.1 Chỉ gán order.customer: khóa ngoại không được ghi {.mo-rong}

**Bối cảnh.** Thói quen quen thuộc của mục 4.7 là `order.setCustomer(c)`. Với mapping này có còn đúng không?

**Kết quả.** Không. Field `customer` đã bị tắt quyền ghi nên Hibernate bỏ qua. Câu INSERT không có cột `customer_id`, khóa ngoại là NULL, và không có UPDATE nào.

::: {.ghi-nho}
Owning side là phía có quyền ghi cột khóa ngoại, không phụ thuộc phía đó nằm ở bảng nào.
:::

::: {.tracuu codes="G1-S16a"}
:::

#### 4.9.2 Chỉ add vào customer.getOrders: khóa ngoại được ghi bằng INSERT rồi UPDATE {.mo-rong}

**Bối cảnh.** Add order vào collection của cha (phía có quyền ghi): khóa ngoại có được ghi không, bằng mấy câu SQL?

**Kết quả.** Được. Giống mục 4.6: một câu INSERT order (chưa có khóa ngoại) rồi một câu `update ... set customer_id=? where id=?`. Dữ liệu cuối: `customer_id` đúng, dù `order.customer` trong bộ nhớ vẫn null.

::: {.ghi-nho}
Biến thể này là mục 4.7 lật ngược: ai có quyền ghi cột khóa ngoại thì người đó là owning side.
:::

::: {.tracuu codes="G1-S16b"}
:::

### 4.10 Tổng kết: đặt quan hệ ở bảng cha hay bảng con {.bien-the}

Nội dung tóm tắt các mục trước.

![Bảng purchase_order giữ khóa ngoại customer_id tới customer; cách khai báo ở phía nào quyết định số câu SQL](img/g1-v4.png){width=70%}

| | Đặt ở bảng con (`@ManyToOne` trong PurchaseOrder) | Đặt ở bảng cha (`@OneToMany` trong Customer) |
|---|---|---|
| Khóa ngoại nằm ở | `purchase_order.customer_id` | Không có `@JoinColumn`: bảng nối `customer_orders`. Có `@JoinColumn`: `purchase_order.customer_id` |
| SQL khi thêm một order | Một INSERT | Bảng nối: INSERT order và INSERT bảng nối. Có JoinColumn: INSERT và UPDATE bù |
| Điều hướng | Từ order tới customer | Từ customer tới các order |
| Khuyến nghị | Mặc định nên dùng | Dùng khi thật sự cần; ưu tiên hai chiều với `mappedBy` |

**Ưu điểm.** Bảng trên giúp chọn nhanh: chỉ cần điều hướng từ con lên cha thì dùng `@ManyToOne` (mục 4.2). Cần cả hai chiều thì dùng hai chiều chuẩn (mục 4.7).

**Nhược điểm.** Bảng không nói thay cho các bẫy: quên `mappedBy` (mục 4.8), chỉ đồng bộ một phía (mục 4.7), hay map cột hai lần (mục 4.4).

**Nên dùng khi.** Một quy tắc ngắn gọn: khóa ngoại luôn thuộc về phía `@ManyToOne`. Nếu cần collection ở phía cha thì thêm nó với `mappedBy` và dùng helper.

::: {.ghi-nho}
Quan hệ một-nhiều nên được ghi bởi phía `@ManyToOne` (bảng con). Collection ở phía cha chỉ để đọc, nên có `mappedBy` và có helper đồng bộ hai phía.
:::
