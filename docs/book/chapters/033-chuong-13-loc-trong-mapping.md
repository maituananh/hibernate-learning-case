## Chương 13. Lọc dữ liệu ngay trong mapping: Formula, SQLRestriction, Filter, SoftDelete

Chương 11 và 12 lọc và tải dữ liệu bằng **câu truy vấn**. Chương này xem một hướng khác: đặt điều kiện ngay **trong mapping**, để Hibernate tự gắn vào câu SQL mà nó sinh ra. Bốn công cụ cùng họ, và đều là annotation **riêng của Hibernate**:

| Công cụ | Làm gì |
|---|---|
| `@Formula` | một thuộc tính được tính bằng subquery, không có cột thật |
| `@SQLRestriction` | thêm điều kiện `where` cố định vào mọi câu SELECT của entity hoặc collection |
| `@Filter` | điều kiện `where` có tham số, mặc định tắt, bật theo từng session |
| `@SoftDelete` | xóa mềm: "xóa" thành cập nhật cờ, mọi SELECT tự loại dòng đã xóa |

Điểm chung (và là bài học lớn nhất của chương): **Hibernate chỉ gắn điều kiện vào câu SQL do chính nó sinh ra** (từ `find`, JPQL, Criteria, tải collection). Native SQL là văn bản của bạn, Hibernate không đụng vào, nên điều kiện biến mất (lab đã chứng minh với `@SQLRestriction` và `@SoftDelete`). Điểm riêng của từng công cụ nằm ở chỗ nó áp dụng cho đường truy cập nào: `find` theo id, JPQL, collection.

Chương này dùng hai mô hình nhỏ:

- Mô hình thứ nhất (mục 13.1 đến 13.3): `Customer` có đếm số đơn bằng `@Formula` và có collection `notes` (ghi chú, có cờ `deleted`); `Order` có `@Filter` theo trạng thái; `Coupon` (mã giảm giá) có cờ `active`.
- Mô hình thứ hai (mục 13.4): `PurchaseOrder` và `OrderItem`, trong đó `OrderItem` dùng `@SoftDelete`.

![Sơ đồ bảng của mô hình thứ nhất: customer, purchase_order, note và coupon](img/g12-f1.png){width=85%}

Trong chương này:

- `@Formula`: cột tính toán, khi nào được tính lại (mục 13.1).
- `@SQLRestriction`: trên entity, trên collection, và native query (mục 13.2).
- `@Filter`: mặc định tắt, bật theo session, và không áp cho `find` (mục 13.3).
- `@SoftDelete`: xóa mềm, collection tự lọc, `orphanRemoval`, native query (mục 13.4).
- So sánh bốn cách lọc (mục 13.5).

**Trong JPA thuần.** Cả bốn đều là annotation của Hibernate, JPA không có tương đương trực tiếp. Mỗi mục có đoạn **Trong JPA thuần.** nêu cách làm khi bạn không muốn phụ thuộc Hibernate. Nếu dự án bắt buộc chạy trên provider khác, đây là những chỗ bạn phải viết lại.

### 13.1 Formula: cột tính toán bằng subquery

`@Formula` cho phép một thuộc tính của entity lấy giá trị từ một biểu thức SQL thay vì từ một cột. Hibernate nhét biểu thức đó vào danh sách cột của câu SELECT.

```java
@Entity(name = "G12F1_Customer")
@Table(schema = "g12_f1_formula_filter", name = "customer")
public class F1Customer {

    @Id
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    // Không có cột order_count trong database. Mỗi lần SELECT customer,
    // Hibernate nhét subquery này vào danh sách cột.
    @Formula("(select count(*) from g12_f1_formula_filter.purchase_order o "
           + "where o.customer_id = id)")
    private int orderCount;
}
```

#### 13.1.1 Formula chỉ được tính lại khi tải lại entity {.mo-rong}

**Bối cảnh.** Màn hình khách hàng hiển thị "số đơn" lấy từ `customer.getOrderCount()`. Bạn thêm một đơn mới cho khách, flush, rồi đọc lại `getOrderCount()` ngay trong cùng transaction và thấy con số cũ.

**Khi nào xảy ra.** Entity đã MANAGED với giá trị formula nạp lúc đầu, và dữ liệu nền (bảng đơn) đổi sau đó trong cùng persistence context.

**Khi nào không xảy ra.** Nếu bạn tải lại entity (transaction mới, `em.refresh`, hoặc query mới trả entity chưa có trong persistence context), subquery chạy lại và cho giá trị mới.

**Ví dụ.**

```java
inTx(() -> {
    F1Customer customerAlice = em.find(F1Customer.class, 1L);
    customerAlice.getOrderCount();          // 1
    em.persist(new F1Order("ORD-12", "NEW", customerAlice));
    em.flush();                             // database đã có 2 đơn
    customerAlice.getOrderCount();          // vẫn 1
});
int countAfterReload = inTxReturning(
    () -> em.find(F1Customer.class, 1L).getOrderCount()); // 2
```

**Kết quả.** Lúc nạp lần đầu, subquery đếm được 1 đơn. Sau khi thêm đơn thứ hai và flush, `getOrderCount()` **vẫn trả 1** dù database đã có 2 đơn. Chỉ khi tải lại trong transaction mới, giá trị mới ra 2. Bảng `customer` **không có cột `order_count`**: formula không tạo cột.

```sql
select fc1_0.id, fc1_0.name,
    (select count(*) from g12_f1_formula_filter.purchase_order o
     where o.customer_id = fc1_0.id)
from g12_f1_formula_filter.customer fc1_0
where fc1_0.id=?
```

**Vì sao.** Giá trị formula là một ảnh chụp tại thời điểm SELECT. Entity MANAGED không tự nạp lại, vì Hibernate không theo dõi "bảng đơn đã đổi" để đoán xem cần tính lại cho entity nào. Thuộc tính cũng **chỉ đọc**: không ghi ngược xuống database.

**Trong JPA thuần.** `@Formula` không có trong JPA. Cách tương đương: viết một câu truy vấn đếm (`select count(o) from Order o where o.customer = :c`) khi cần, hoặc trả DTO chứa số đếm (mục 12.5), hoặc tạo một **view** trong database và map entity vào view đó.

::: {.ghi-nho}
`@Formula` là ảnh chụp lúc tải, chỉ đọc, không có cột thật. Muốn giá trị mới phải tải lại hoặc `refresh`.
:::

::: {.tracuu codes="G12-S12"}
:::

### 13.2 SQLRestriction: điều kiện cố định, không tắt được

`@SQLRestriction("điều kiện")` thêm điều kiện vào mọi câu SELECT mà Hibernate sinh ra cho entity (hoặc collection) được gắn. Khác `@Filter` ở mục 13.3: restriction **luôn bật**, không có tham số, không tắt được trong session.

```java
// Đặt trên ENTITY: mọi SELECT từ bảng coupon do Hibernate sinh đều thêm điều kiện.
@Entity(name = "G12F1_Coupon")
@Table(schema = "g12_f1_formula_filter", name = "coupon")
@SQLRestriction("active = true")
public class F1Coupon { /* ... */ }

// Đặt trên COLLECTION: chỉ lọc khi nạp collection này.
@Entity(name = "G12F1_Customer")
public class F1Customer {

    // INVERSE side. Thêm "and deleted = false" vào câu SELECT nạp collection.
    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    @SQLRestriction("deleted = false")
    private List<F1Note> notes = new ArrayList<>();
}
```

::: {.ddl schema="g12_f1_formula_filter" tables="coupon,note"}
:::

#### 13.2.1 Restriction trên entity: find theo id và JPQL đều không thấy dòng bị loại {.mo-rong}

**Bối cảnh.** Bảng `coupon` có mã đã hết hiệu lực (`active = false`). Bạn muốn code nghiệp vụ không bao giờ vô tình dùng mã đó.

**Khi nào xảy ra.** `@SQLRestriction` đặt trên chính class entity. Mọi đường truy cập do Hibernate sinh SQL (`find`, JPQL, Criteria) đều bị lọc.

**Khi nào không xảy ra.** Native query (mục 13.2.2). Và nếu restriction đặt trên collection thay vì trên entity (mục 13.2.3).

**Ví dụ.**

```java
// Database có coupon 1 (active) và coupon 2 (không active)
F1Coupon inactiveCoupon = em.find(F1Coupon.class, 2L);   // null
List<F1Coupon> visibleCoupons = em.createQuery(
    "select c from G12F1_Coupon c", F1Coupon.class).getResultList(); // 1
```

**Kết quả.** `find` theo id của coupon không active trả **null**: coupon như không tồn tại. JPQL chỉ thấy 1 coupon (đang active). Cả hai câu SQL đều có điều kiện `active = true`.

```sql
select fc1_0.id, fc1_0.active, fc1_0.code
from g12_f1_formula_filter.coupon fc1_0
where fc1_0.id=? and ( fc1_0.active = true )

select fc1_0.id, fc1_0.active, fc1_0.code
from g12_f1_formula_filter.coupon fc1_0
where ( fc1_0.active = true )
```

**Vì sao.** Restriction gắn vào **mọi** SELECT mà Hibernate sinh cho entity, kể cả khi tải theo id. Không có cách tắt trong session. Hệ quả: nếu có chỗ nghiệp vụ cần đọc cả coupon hết hạn (báo cáo, quản trị) thì bạn không làm được qua entity này, phải dùng native query hoặc một entity khác map cùng bảng.

::: {.ghi-nho}
`@SQLRestriction` trên entity luôn bật: `find` và JPQL đều không thấy dòng bị loại, không tắt được trong session.
:::

::: {.tracuu codes="G12-S13a"}
:::

#### 13.2.2 Native query không bị restriction {.mo-rong}

**Bối cảnh.** Báo cáo dùng native SQL `select count(*) from coupon` để đếm mã giảm giá. Con số lớn hơn số mã mà ứng dụng hiển thị.

**Kết quả.** Native query đếm được **2**, gồm cả coupon không active. Restriction chỉ áp lên SQL do Hibernate **tự sinh** từ `find`, JPQL, Criteria. Native query chạy nguyên văn.

```sql
select count(*)
from g12_f1_formula_filter.coupon
```

Hệ quả: mọi native query trên bảng có `@SQLRestriction` phải **tự viết lại điều kiện**. Cùng bài học với native query trên bảng soft delete ở mục 13.4.4.

**Trong JPA thuần.** `@SQLRestriction` không có trong JPA. Cách tương đương: viết điều kiện vào từng câu truy vấn (hoặc dùng `Specification` làm điều kiện mặc định ở một chỗ), hoặc tạo view chỉ chứa dòng hợp lệ và map entity vào view. Không cách nào tự động bằng JPA chuẩn.

::: {.ghi-nho}
Native query không được Hibernate thêm restriction. Phải tự viết lại điều kiện trong câu SQL.
:::

::: {.tracuu codes="G12-S13b"}
:::

#### 13.2.3 Restriction trên collection: chỉ lọc khi nạp collection {.mo-rong}

**Bối cảnh.** Khách có 2 ghi chú, 1 cái đã xóa mềm (`deleted = true`). Bạn gắn restriction lên collection `notes` để `customer.getNotes()` chỉ trả ghi chú còn dùng.

**Khi nào xảy ra.** `@SQLRestriction` đặt trên `@OneToMany`, còn entity `Note` không có restriction.

**Khi nào không xảy ra.** Nếu entity `Note` được tải theo đường khác (`find` theo id, JPQL trực tiếp trên `Note`) thì restriction của collection không áp dụng.

**Ví dụ.**

```java
inTx(() -> {
    F1Customer customerAlice = em.find(F1Customer.class, 1L);
    customerAlice.getNotes().size();      // 1: ghi chú xóa mềm bị loại
    em.find(F1Note.class, 22L);           // vẫn thấy ghi chú đã xóa mềm
});
```

**Kết quả.** `getNotes()` trả **1** phần tử: câu SELECT nạp collection được thêm `deleted = false`. Nhưng `em.find(Note, 22)` theo id **vẫn trả** ghi chú đã xóa mềm, vì restriction nằm ở collection chứ không ở entity.

```sql
select n1_0.customer_id, n1_0.id, n1_0.deleted, n1_0.text
from g12_f1_formula_filter.note n1_0
where n1_0.customer_id=? and ( n1_0.deleted = false )

select fn1_0.id, fn1_0.customer_id, fn1_0.deleted, fn1_0.text
from g12_f1_formula_filter.note fn1_0
where fn1_0.id=?
```

**Vì sao.** Restriction là thuộc tính của **cách nạp collection này**, không phải của entity `Note`. Hai đường truy cập đến cùng một bảng có hai kết quả khác nhau. Nếu bạn muốn "ghi chú xóa mềm không bao giờ xuất hiện ở đâu cả" thì đặt restriction (hoặc `@SoftDelete`) ở entity, không ở collection.

::: {.ghi-nho}
`@SQLRestriction` đặt ở đâu thì lọc ở đó. Trên collection thì chỉ lọc khi nạp collection, `find` theo id vẫn thấy dòng bị loại.
:::

::: {.tracuu codes="G12-S13c"}
:::

### 13.3 Filter: điều kiện có tham số, bật theo session

`@Filter` giống restriction nhưng **có tham số** và **mặc định tắt**. Bạn bật nó trong từng session bằng `session.enableFilter(tên).setParameter(...)`. Dùng khi cùng một entity cần được lọc khác nhau ở những ngữ cảnh khác nhau (ví dụ lọc theo `tenant`, theo trạng thái).

```java
@Entity(name = "G12F1_Order")
@Table(schema = "g12_f1_formula_filter", name = "purchase_order")
@FilterDef(name = "orderStatus",
           parameters = @ParamDef(name = "status", type = String.class))
@Filter(name = "orderStatus", condition = "status = :status")
public class F1Order { /* ... */ }
```

#### 13.3.1 Filter chưa bật thì không lọc gì {.mo-rong}

**Bối cảnh.** Bạn khai báo `@Filter` trên `Order` và tin rằng từ giờ mọi truy vấn chỉ thấy đơn theo điều kiện. Khi chạy, truy vấn trả cả đơn bạn muốn loại.

**Kết quả.** JPQL `select o from Order o` trả **cả 2** đơn (PAID lẫn NEW), câu SQL không có điều kiện `status`. `@Filter` chỉ có tác dụng sau khi session gọi `enableFilter`.

```sql
select fo1_0.id, fo1_0.customer_id, fo1_0.order_no, fo1_0.status
from g12_f1_formula_filter.purchase_order fo1_0
```

Đây là điểm khác nền tảng với `@SQLRestriction`: khai báo thôi chưa đủ, phải bật, và phải bật **cho từng session** (thường làm ở một interceptor hoặc aspect quanh transaction). Quên bật ở một nơi là dữ liệu lộ ở nơi đó.

::: {.ghi-nho}
`@Filter` mặc định tắt. Khai báo thôi không lọc gì, phải bật theo từng session.
:::

::: {.tracuu codes="G12-S14a"}
:::

#### 13.3.2 Filter đã bật: JPQL bị lọc nhưng find theo id vẫn thấy {.mo-rong}

**Bối cảnh.** Bạn bật filter `status = 'PAID'` và muốn dùng nó như hàng rào: người dùng chỉ thấy đơn đã thanh toán. Nhưng một chỗ trong code vẫn lấy được đơn NEW bằng `find`.

**Khi nào xảy ra.** Bạn đã `enableFilter` cho session, rồi lấy entity bằng `find` theo id.

**Khi nào không xảy ra.** JPQL và Criteria thì bị lọc đúng như mong đợi.

**Ví dụ.**

```java
inTx(() -> {
    Session session = em.unwrap(Session.class);
    session.enableFilter("orderStatus").setParameter("status", "PAID");

    List<F1Order> paidOrders = em.createQuery(
        "select o from G12F1_Order o", F1Order.class).getResultList(); // 1
    F1Order newOrderById = em.find(F1Order.class, 12L); // đơn NEW vẫn thấy
});
```

**Kết quả.** JPQL chỉ trả **1** đơn (PAID): câu SQL có `where status = ?`. Nhưng `em.find` theo id của đơn NEW **vẫn trả đơn đó**, câu SQL chỉ có `where id=?`.

```sql
select fo1_0.id, fo1_0.customer_id, fo1_0.order_no, fo1_0.status
from g12_f1_formula_filter.purchase_order fo1_0
where fo1_0.status = ?

select fo1_0.id, fo1_0.customer_id, fo1_0.order_no, fo1_0.status
from g12_f1_formula_filter.purchase_order fo1_0
where fo1_0.id=?
```

**Vì sao.** Filter không áp cho tải theo id (khác `@SQLRestriction` ở mục 13.2.1). Vì vậy **đừng coi `@Filter` là rào bảo mật dữ liệu**: người biết id vẫn lấy được dòng bị lọc. Dùng nó để thu hẹp danh sách hiển thị, không dùng để cấm truy cập.

**Trong JPA thuần.** `@Filter` không có trong JPA. Cách tương đương: thêm điều kiện vào từng truy vấn (qua `Specification` dùng chung, hoặc một lớp repository bọc), hoặc dùng cơ chế row-level security của chính database cho trường hợp cần cấm thật.

::: {.ghi-nho}
`@Filter` đã bật lọc JPQL nhưng không áp cho `find` theo id. Không phải hàng rào bảo mật.
:::

::: {.tracuu codes="G12-S14b"}
:::

### 13.4 SoftDelete: xóa mềm

**Xóa mềm** nghĩa là không xóa dòng thật, chỉ đánh dấu "đã xóa" bằng một cột cờ. Lợi ích: có thể khôi phục, giữ lịch sử, giữ khóa ngoại của dữ liệu tham chiếu. `@SoftDelete` (Hibernate 6.4 trở lên) tự động hóa cả hai chiều: `remove` thành cập nhật cờ, và mọi SELECT tự loại dòng đã xóa.

```java
// Hibernate tự thêm và quản lý cột boolean `deleted`: không khai báo field.
@SoftDelete(columnName = "deleted")
@Entity(name = "G9S11_OrderItem")
@Table(schema = "g9_s11_soft_delete", name = "order_item")
public class OrderItem {

    @Id
    private Long id;

    // OWNING side: cột order_id nằm ở bảng này.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private PurchaseOrder order;
}

@Entity(name = "G9S11_PurchaseOrder")
public class PurchaseOrder {

    // INVERSE side, có cascade ALL và orphanRemoval.
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
}
```

![Sơ đồ bảng purchase_order và order_item của mô hình xóa mềm; order_item có cột deleted do Hibernate thêm](img/g9-sd.png){width=70%}

::: {.ddl schema="g9_s11_soft_delete" tables="order_item"}
:::

#### 13.4.1 remove thành UPDATE cờ deleted, dòng vẫn còn {.mo-rong}

**Bối cảnh.** Khách hủy một dòng hàng. Bạn muốn giữ lại dòng đó cho kế toán, nên dùng xóa mềm. Bạn gọi `em.remove(item)` như bình thường.

**Khi nào xảy ra.** Entity có `@SoftDelete` và bạn xóa bằng `em.remove`.

**Khi nào không xảy ra.** Nếu bạn xóa bằng native `DELETE` thì dòng bị xóa thật (Hibernate không can thiệp).

**Ví dụ.**

```java
inTx(() -> em.remove(em.find(OrderItem.class, itemId)));
```

**Kết quả.** **Không có** câu DELETE nào. Có đúng 1 câu UPDATE đặt cờ, và dòng vẫn nằm trong bảng.

```sql
update g9_s11_soft_delete.order_item
set deleted=true
where id=? and deleted=false
```

| Bảng order_item | Số dòng | Cờ deleted của dòng bị xóa |
|---|---|---|
| Trước | 2 | false |
| Sau | 2 | **true** |

Thay đổi: cờ `deleted` của dòng bị xóa. Giữ nguyên: số dòng trong bảng (vẫn 2).

**Vì sao.** `@SoftDelete` đổi câu lệnh mà Hibernate sinh cho `remove`: thay vì `DELETE`, nó sinh `UPDATE ... set deleted=true`. Điều kiện `and deleted=false` nằm sẵn trong câu UPDATE. Cũng vì thế mọi câu SELECT của entity này đều có `deleted=false` (câu `find` ở trên là ví dụ).

**Trong JPA thuần.** `@SoftDelete` là của Hibernate. Trong JPA thuần bạn tự thêm field `deleted` và viết method `softDelete()` đổi cờ, rồi nhớ thêm `where deleted = false` vào mọi truy vấn. Dễ quên một chỗ, đó chính là lý do `@SoftDelete` đáng giá.

::: {.ghi-nho}
`@SoftDelete` biến `remove` thành UPDATE cờ: 0 câu DELETE, dòng vẫn còn trong bảng.
:::

::: {.tracuu codes="G9-S11a"}
:::

#### 13.4.2 Collection của cha tự lọc dòng đã xóa mềm {.mo-rong}

**Bối cảnh.** Sau khi xóa mềm một dòng hàng, màn hình đơn hàng vẫn phải hiển thị danh sách dòng còn lại, không có dòng đã hủy.

**Kết quả.** `order.getItems()` chỉ trả **1** dòng (dòng còn lại). Câu SELECT nạp collection tự có điều kiện loại dòng đã xóa. Trong database vẫn còn cả 2 dòng.

```sql
select i1_0.order_id, i1_0.id, i1_0.sku
from g9_s11_soft_delete.order_item i1_0
where i1_0.order_id=? and i1_0.deleted=false
```

Bạn không phải viết điều kiện nào trong mapping của `items`: `@SoftDelete` đặt ở entity con đã đủ, vì **mọi** câu SELECT từ entity đó đều được thêm điều kiện, kể cả câu nạp collection.

::: {.ghi-nho}
Collection của cha tự lọc con đã xóa mềm, chỉ cần `@SoftDelete` ở entity con.
:::

::: {.tracuu codes="G9-S11b"}
:::

#### 13.4.3 orphanRemoval cũng thành xóa mềm {.mo-rong}

**Bối cảnh.** Mapping có `orphanRemoval = true` (Chương 6). Bạn gỡ một dòng khỏi collection bằng `order.removeItem(item)` và tự hỏi liệu có DELETE thật xảy ra không.

**Kết quả.** **Không có DELETE**. Dòng bị gỡ khỏi collection thành orphan, Hibernate xử lý như `remove`, nên ra câu UPDATE đặt cờ `deleted=true`. Dòng vẫn nằm trong bảng.

```sql
update g9_s11_soft_delete.order_item
set deleted=true
where id=? and deleted=false
```

**Vì sao.** `orphanRemoval` gọi cơ chế `remove` nội bộ của Hibernate, và `remove` của entity có `@SoftDelete` là UPDATE cờ. Nghĩa là toàn bộ vòng đời xóa (`remove`, cascade REMOVE, `orphanRemoval`) đều thành xóa mềm, không cần sửa từng chỗ.

::: {.ghi-nho}
Với `@SoftDelete`, cả `orphanRemoval` lẫn cascade REMOVE đều thành UPDATE cờ, không có DELETE thật.
:::

::: {.tracuu codes="G9-S11c"}
:::

#### 13.4.4 Native SELECT không tự lọc dòng đã xóa mềm {.mo-rong}

**Bối cảnh.** Báo cáo dùng native SQL `select * from order_item` trên bảng có xóa mềm. Kết quả có cả dòng đã hủy.

**Kết quả.** Sau khi xóa mềm 1 trong 2 dòng hàng: native SELECT có kiểu entity trả **2** dòng (kể cả dòng đã xóa mềm), còn JPQL trên cùng bảng chỉ trả **1** dòng.

```sql
-- native: văn bản của bạn, Hibernate không thêm gì
select *
from g9_s11_soft_delete.order_item

-- JPQL: Hibernate dịch và tự thêm điều kiện
select oi1_0.id, oi1_0.order_id, oi1_0.sku
from g9_s11_soft_delete.order_item oi1_0
where oi1_0.deleted=false
```

Cùng nguyên tắc với mục 13.2.2: `@SoftDelete` chỉ gắn vào SQL do Hibernate sinh. Mọi native query trên bảng xóa mềm phải tự viết `where deleted = false`. Đây là một điểm nên đưa vào checklist review code.

::: {.ghi-nho}
Native query trên bảng `@SoftDelete` trả cả dòng đã xóa mềm. Phải tự thêm `where deleted = false`.
:::

::: {.tracuu codes="G10-S07"}
:::

### 13.5 So sánh bốn cách lọc

Bảng dưới tổng hợp điều mà các mục trên đã chứng minh. Cột "đường truy cập" cho biết điều kiện có được áp hay không.

| | `@Formula` | `@SQLRestriction` | `@Filter` | `@SoftDelete` |
|---|---|---|---|---|
| Mục đích | thuộc tính tính bằng subquery | loại dòng cố định | loại dòng theo ngữ cảnh | xóa mềm |
| Mặc định | luôn tính | luôn bật | **tắt** | luôn bật |
| Tham số | không | không | có | không |
| `find` theo id | tính lại khi nạp | **lọc** (trả null) | **không lọc** | lọc |
| JPQL, Criteria | tính lại khi nạp | lọc | lọc (nếu đã bật) | lọc |
| Collection | không áp dụng | lọc nếu đặt trên collection | không nêu trong lab | lọc (đặt ở entity con) |
| Native SQL | không áp dụng | **không lọc** | chưa kiểm chứng trong lab | **không lọc** |
| Ảnh hưởng tới `remove` | không | không | không | **đổi thành UPDATE** |
| Có cột thật trong database | không | dùng cột sẵn có | dùng cột sẵn có | Hibernate thêm cột cờ |

Cách chọn:

- Cần **giá trị tổng hợp chỉ đọc** (số đơn, tổng tiền) mà chấp nhận ảnh chụp lúc tải: `@Formula`. Nhưng nếu giá trị cần luôn mới thì dùng truy vấn hoặc DTO.
- Cần **loại dòng vĩnh viễn** khỏi mọi truy vấn (mã hết hạn, bản ghi lưu trữ): `@SQLRestriction` ở entity.
- Cần **lọc theo ngữ cảnh** (tenant, chế độ xem) và chấp nhận phải bật đúng ở mọi nơi: `@Filter`. Không dùng làm rào bảo mật.
- Cần **xóa mà vẫn giữ dữ liệu**: `@SoftDelete`. Kèm quy ước: mọi native query tự thêm điều kiện cờ.

Điểm yếu chung đã kiểm chứng với `@SQLRestriction` và `@SoftDelete`: **native SQL bỏ qua chúng**. Lab chưa chạy riêng tình huống native với `@Filter`, nhưng vì `@Filter` cũng là điều kiện Hibernate gắn vào SQL của nó, hãy giả định tương tự và kiểm tra trước khi dựa vào. Hãy coi native query trên bảng có lọc trong mapping là điểm cần review, và nếu có thể thì tạo view hoặc dùng row-level security của database để đảm bảo ở tầng thấp hơn.

::: {.ghi-nho}
Điều kiện trong mapping chỉ có tác dụng với SQL do Hibernate sinh. Native query bỏ qua `@SQLRestriction` và `@SoftDelete` (đã kiểm chứng), và `find` theo id chỉ bị lọc bởi `@SQLRestriction` và `@SoftDelete`, không bởi `@Filter`.
:::
