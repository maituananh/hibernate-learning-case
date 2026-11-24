## Chương 5. Cascade: thao tác nào lan từ cha sang con

Ở Chương 4, mọi `persist` đều được gọi tường minh cho từng entity: customer, order, rồi từng item. Trong code thật, không ai muốn viết bảy dòng `persist` để lưu một đơn hàng có sáu dòng hàng. Cascade sinh ra để giải quyết việc đó: bạn thao tác trên entity cha, Hibernate tự "truyền tiếp" thao tác sang các entity con.

Nhưng cascade là một trong những nơi dễ sai nhất của mapping. Nó có năm loại độc lập (`PERSIST`, `MERGE`, `REMOVE`, `DETACH`, `REFRESH`), mỗi loại chỉ lo đúng một thao tác. Thiếu một loại thì thao tác đó không lan, và đôi khi kết quả không phải là lỗi mà là **dữ liệu sai được ghi lặng lẽ**. Đặt cascade sai phía (trên `@ManyToOne`) còn có thể xóa mất dữ liệu master mà không báo gì.

Trong chương này:

- Cascade là gì, đặt ở đâu, và nó khác gì `ON DELETE CASCADE` của database (mục 5.1).
- Từng loại cascade, mỗi loại một mục: không cascade (5.2), `PERSIST` (5.3), `MERGE` (5.4), `REMOVE` và `@OnDelete` (5.5), `DETACH` và `REFRESH` (5.6).
- Cascade đặt sai chỗ, trên `@ManyToOne` (5.7).
- Bảng tổng kết thao tác và cascade (5.8).

Quy ước của chương: quan hệ giữa `PurchaseOrder` và `OrderItem` luôn là hai chiều chuẩn như mục 4.7 (`items` có `mappedBy`, `OrderItem.order` là owning side, có helper `addItem`). Chỉ có **một** điểm khác nhau giữa các mục: giá trị của `cascade`. Chương này **không dùng** `orphanRemoval`; đó là chủ đề của Chương 6.

### 5.1 Cascade là gì và đặt ở đâu

::: {.kien-thuc-nen}
**Cascade là thao tác của `EntityManager` được truyền tiếp cho entity liên quan. Nó không phải thao tác của database.** Khi bạn gọi `em.persist(order)`, Hibernate nhìn vào các quan hệ của `order`; quan hệ nào khai `cascade` có chứa `PERSIST` thì Hibernate gọi tiếp `persist` cho entity ở đầu bên kia. Cascade chỉ chạy khi đi qua các lệnh `persist`, `merge`, `remove`, `detach`, `refresh` của `EntityManager`, và khi flush (xem mục 5.3). Câu lệnh JPQL `delete from ...` chạy thẳng xuống database, không đi qua `EntityManager` từng entity, nên không có cascade nào.

**Năm loại cascade, mỗi loại độc lập:**

| CascadeType | Thao tác được truyền tiếp |
|---|---|
| `PERSIST` | `em.persist(cha)` kéo theo `persist(con)` |
| `MERGE` | `em.merge(cha)` kéo theo `merge(con)` |
| `REMOVE` | `em.remove(cha)` kéo theo `remove(con)` |
| `DETACH` | `em.detach(cha)` kéo theo `detach(con)` |
| `REFRESH` | `em.refresh(cha)` kéo theo `refresh(con)` |
| `ALL` | Cả năm loại trên |

`PERSIST` không giúp gì cho `MERGE`. Liệt kê thủ công mà thiếu một loại thì loại đó không lan. Mục 5.4 và 5.6 có những ví dụ mà thiếu một loại cho kết quả rất bất ngờ.

**Đặt ở đâu?** Cascade khai trên annotation của quan hệ (`@OneToMany`, `@ManyToOne`, `@OneToOne`, `@ManyToMany`), ở phía mà bạn muốn thao tác "đi ra". Quy tắc dùng chung: cascade đặt ở **phía cha** của quan hệ sở hữu (aggregate), nghĩa là phía mà vòng đời của con phụ thuộc vào cha. Một order sở hữu các item của nó: item không có ý nghĩa nếu thiếu order, nên `items` là chỗ hợp lý để đặt cascade. Ngược lại, một item chỉ **tham chiếu** product dùng chung; product sống độc lập với item, nên đặt cascade trên `OrderItem.product` thường là sai (mục 5.7).

**Cascade JPA khác `ON DELETE CASCADE` của database.** Cascade JPA chạy trong Java, qua từng entity, và Hibernate biết chính xác nó đã làm gì. `ON DELETE CASCADE` là một mệnh đề trong khóa ngoại: chính PostgreSQL xóa dây chuyền, Hibernate không được báo. Mục 5.5 so sánh hai cơ chế này.
:::

### 5.2 Không cascade: persist cha, con bị bỏ qua {.bien-the}

Đây là điểm xuất phát: `items` chỉ có `mappedBy`, không khai `cascade`. Mọi mục sau đều khác mục này đúng một điểm.

```java
@Entity(name = "G2C0_PurchaseOrder")
@Table(schema = "g2_c0_none", name = "purchase_order")
public class C0PurchaseOrder {

    // OWNING side: ghi cột customer_id của bảng purchase_order.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private C0Customer customer;

    // INVERSE side: cột order_id nằm ở order_item, do OrderItem.order ghi.
    // Không khai cascade: persist order sẽ không đụng tới item.
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<C0OrderItem> items = new ArrayList<>();

    // Helper đồng bộ hai phía trong bộ nhớ.
    public void addItem(C0OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
```

![Bảng order_item có hai khóa ngoại: order_id tới purchase_order.id và product_id tới product.id; một order có nhiều item, một product có nhiều item](img/g2-c0.png){width=90%}

**Ưu điểm.** Mọi thứ tường minh: bạn thấy từng lệnh `persist` và biết chính xác dòng nào được ghi. Không có bất ngờ khi xóa hay merge.

**Nhược điểm.** Phải nhớ `persist` từng item. Quên một item thì item đó không được lưu, và Hibernate không báo gì (tình huống dưới đây).

**Nên dùng khi.** Quan hệ giữa hai entity mà vòng đời độc lập (ví dụ `OrderItem` tới `Product`), hoặc khi bạn muốn kiểm soát từng thao tác lưu.

#### 5.2.1 persist order mà không cascade: item bị bỏ qua im lặng {.cot-loi}

**Bối cảnh.** Bạn viết code tạo đơn hàng: tạo `PurchaseOrder`, gọi `addItem` hai lần, rồi `persist(order)`. Trong đầu bạn, "lưu đơn hàng" nghĩa là lưu cả đơn lẫn hai dòng hàng. Test thủ công thấy không có lỗi nào, nên bạn tin là xong. Nhiều ngày sau mới có người hỏi vì sao đơn hàng không có dòng hàng nào. Lỗi không nằm ở chỗ nổ ra, mà ở chỗ **không có gì nổ ra**.

Bạn có thể nghĩ Hibernate sẽ báo lỗi, vì order đang giữ một list chứa những entity chưa được lưu. Thực tế thì không.

**Khi nào xảy ra.** `items` không có cascade `PERSIST`, bạn gọi `persist(order)` mà không gọi `persist` cho từng item.

**Khi nào không xảy ra.** Nếu bạn gọi `em.persist(item)` cho từng item (như ở Chương 4), hoặc nếu `items` có cascade `PERSIST` (mục 5.3).

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder orderA = new PurchaseOrder("A-001");  // TRANSIENT
    orderA.addItem(itemOne);                            // itemOne: TRANSIENT
    orderA.addItem(itemTwo);                            // itemTwo: TRANSIENT
    em.persist(orderA);
    // Trạng thái: orderA là MANAGED; hai item vẫn TRANSIENT.
});
// Sau transaction: orderA là DETACHED; hai item vẫn TRANSIENT.
```

**Kết quả.** Chỉ có một câu INSERT, cho `purchase_order`. Không có câu INSERT nào cho `order_item`, và không có exception:

```sql
insert into g2_c0_none.purchase_order (customer_id, order_no, id)
values (?, ?, ?)
```

Dữ liệu trong database sau khi chạy:

| Bảng | Số dòng |
|---|---|
| purchase_order | **1** (A-001) |
| order_item | **0** |

Hai item vẫn là TRANSIENT: chúng chưa từng vào persistence context, nên cũng không có id (`getId()` trả về null).

**Vì sao.** Hãy xem Hibernate làm gì ở lúc flush. Nó duyệt entity MANAGED là `orderA` và dựng câu INSERT từ các cột của bảng `purchase_order`. Collection `items` là **inverse side** (có `mappedBy`): nó không tương ứng với cột nào của bảng `purchase_order`, và FK `order_id` nằm ở bảng `order_item`, do `OrderItem.order` ghi. Vì không có item nào MANAGED, không có ai ghi FK đó. Hibernate không có dòng nào cần ghi cho collection này, nên cũng không có lý do để phàn nàn rằng phần tử của nó chưa được lưu.

Chú ý khác biệt với `@ManyToOne`: nếu `OrderItem.order` trỏ tới một order TRANSIENT, lúc flush Hibernate cần ghi FK và sẽ ném `TransientPropertyValueException`. Lỗi chỉ xuất hiện khi **owning side** trỏ tới entity chưa lưu; inverse side thì im lặng.

**Cách tránh.** Hoặc `persist` từng item tường minh, hoặc khai `cascade = PERSIST` trên `items` (mục 5.3). Nếu chọn cách đầu, nên có test đếm số dòng `order_item` sau khi tạo đơn.

::: {.ghi-nho}
Không có cascade thì `persist(cha)` chỉ lưu cha. Con nằm trong collection inverse bị bỏ qua mà **không có lỗi nào**. Đừng dựa vào "không thấy exception" để kết luận dữ liệu đã được lưu.
:::

::: {.tracuu codes="G2-S01"}
:::

### 5.3 CascadeType.PERSIST: lưu cha là lưu luôn con {.bien-the}

Khác mục 5.2 đúng một điểm: `items` thêm `cascade = CascadeType.PERSIST`.

```java
// INVERSE side. Khác biệt duy nhất so với mục 5.2 là cascade.
@OneToMany(mappedBy = "order", fetch = FetchType.LAZY,
        cascade = CascadeType.PERSIST)
private List<C1OrderItem> items = new ArrayList<>();
```

![Bảng order_item có hai khóa ngoại: order_id tới purchase_order.id và product_id tới product.id; cấu trúc bảng giống hệt mục 5.2, chỉ khác cách khai báo cascade trong Java](img/g2-c1.png){width=90%}

**Ưu điểm.** Một lệnh `persist(order)` lưu cả đồ thị. Thêm item vào order đang MANAGED cũng tự được lưu lúc flush. Code ngắn và khó quên.

**Nhược điểm.** Nếu collection chứa entity đã DETACHED, `persist` sẽ từ chối. `PERSIST` cũng không giúp cho `merge` (mục 5.4).

**Nên dùng khi.** Con là một phần của cha (order và item): đúng chỗ dùng cascade. Hầu như luôn đi kèm `REMOVE` (mục 5.5) để vòng đời hai phía khớp nhau.

#### 5.3.1 persist(order): INSERT order rồi từng item {.cot-loi}

**Bối cảnh.** Đây là tình huống 5.2.1 nhưng lần này bạn khai `cascade = PERSIST`. Bạn muốn xác nhận thứ tự INSERT và trạng thái của từng entity, vì con cần FK trỏ về cha nên cha bắt buộc phải được ghi trước.

**Khi nào xảy ra.** `items` có `cascade = PERSIST` (hoặc `ALL`), và bạn gọi `persist(order)` khi order còn TRANSIENT.

**Khi nào không xảy ra.** Nếu `items` không khai cascade `PERSIST`, item không được lưu (mục 5.2.1).

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder orderA = new PurchaseOrder("A-001");
    orderA.addItem(itemOne);        // TRANSIENT
    orderA.addItem(itemTwo);        // TRANSIENT
    em.persist(orderA);
    // Trạng thái ngay sau persist: orderA, itemOne, itemTwo đều MANAGED.
    // Chưa có câu INSERT nào chạy.
});
```

**Kết quả.** Ba entity cùng chuyển từ TRANSIENT sang MANAGED ngay tại lệnh `persist`, nhưng chưa có INSERT nào. Đến lúc commit, Hibernate gửi ba câu INSERT, **cha trước con**:

```sql
insert into g2_c1_persist.purchase_order (customer_id, order_no, id)
values (?, ?, ?)

insert into g2_c1_persist.order_item (order_id, product_id, quantity, id)
values (?, ?, ?, ?)

insert into g2_c1_persist.order_item (order_id, product_id, quantity, id)
values (?, ?, ?, ?)
```

Dữ liệu trong database sau khi chạy:

| Bảng | quantity | order_id |
|---|---|---|
| purchase_order (A-001) | | |
| order_item | 1 | id của A-001 |
| order_item | 2 | id của A-001 |

Cột `order_id` của hai item đều có giá trị, vì `addItem` đã gán `item.order` (owning side).

**Vì sao.** Lệnh `persist(orderA)` làm hai việc. Việc thứ nhất: đưa `orderA` vào persistence context và lấy id từ sequence. Việc thứ hai: vì `items` có cascade `PERSIST`, Hibernate duyệt từng phần tử và gọi `persist` cho chúng, nên hai item cũng được cấp id và chuyển sang MANAGED. Câu INSERT chưa chạy vì với SEQUENCE, Hibernate hoãn ghi tới lúc flush (xem Chương 2). Khi flush, Hibernate sắp xếp INSERT theo thứ tự đã persist và phụ thuộc: order trước, rồi item, vì INSERT của item cần giá trị `order_id`, nên phải biết dòng order đã có.

::: {.ghi-nho}
`cascade = PERSIST` chạy ngay trong lệnh `persist`: mọi entity trong đồ thị cùng thành MANAGED. SQL vẫn chờ tới flush và luôn đi từ cha xuống con.
:::

::: {.tracuu codes="G2-S02"}
:::

#### 5.3.2 Thêm item vào order đang MANAGED: item được lưu lúc flush {.cot-loi}

**Bối cảnh.** Order A-001 đã nằm trong database. Một request khác muốn thêm một dòng hàng: bạn `find` order, gọi `addItem(new OrderItem(...))`, rồi kết thúc transaction. Bạn không gọi `persist` cho item vì đã có cascade. Có người trong nhóm lo rằng "cascade chỉ chạy lúc gọi persist, mà ở đây không ai gọi persist", nên item sẽ mất.

**Khi nào xảy ra.** `items` có cascade `PERSIST`, order đang MANAGED, bạn thêm item TRANSIENT vào collection rồi commit.

**Khi nào không xảy ra.** Nếu không có cascade `PERSIST`, item mới không được lưu và không có lỗi (giống mục 5.2.1). Nếu order đã DETACHED thì flush không còn thấy nó, nên cũng không có gì được lưu.

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder orderA = em.find(PurchaseOrder.class, orderId); // MANAGED
    orderA.addItem(newItem);   // newItem vẫn TRANSIENT: chưa ai gọi persist
    // Không có em.persist(newItem).
});
```

**Kết quả.** Item vẫn được INSERT. Không có UPDATE nào cho `purchase_order`:

```sql
select cpo1_0.id, cpo1_0.customer_id, cpo1_0.order_no
from g2_c1_persist.purchase_order cpo1_0
where cpo1_0.id=?

insert into g2_c1_persist.order_item (order_id, product_id, quantity, id)
values (?, ?, ?, ?)
```

Dòng item mới có `order_id` đúng bằng id của order A-001.

**Vì sao.** Cascade `PERSIST` không chỉ chạy ở lệnh `persist`. Hibernate còn chạy nó **một lần nữa lúc flush** (gọi là persist-on-flush): với mọi entity MANAGED, Hibernate duyệt các collection có cascade `PERSIST` và persist những phần tử đang TRANSIENT. Order là MANAGED, `newItem` nằm trong `items` và còn TRANSIENT, nên nó được persist ngay trước khi dựng câu INSERT. Hệ quả tiện lợi: bạn không cần gọi `persist` cho từng item mới thêm vào cha đang MANAGED.

::: {.ghi-nho}
Với `cascade = PERSIST`, item mới thêm vào order MANAGED được lưu lúc flush, không cần `em.persist(item)`. Đồng thời đây là lý do một entity TRANSIENT bị "quên" trong collection của entity MANAGED có thể bất ngờ được ghi xuống database.
:::

::: {.tracuu codes="G2-S03"}
:::

#### 5.3.3 Cascade đi qua ba tầng: customer, order, item {.mo-rong}

**Bối cảnh.** Mapping có ba tầng: `Customer.orders` và `PurchaseOrder.items` đều khai `cascade = ALL`. Bạn muốn biết persist một customer có hai order, mỗi order hai item, cần gọi `persist` mấy lần.

**Kết quả.** Một lần: `persist(customer)`. Cascade đi liên tiếp qua hai tầng. Hibernate gửi 1 câu INSERT customer, 2 câu INSERT purchase_order và 4 câu INSERT order_item, luôn cha trước con. Cả order và item đều MANAGED ngay sau lệnh `persist`.

::: {.ghi-nho}
Cascade là dây chuyền: mỗi tầng tự truyền thao tác xuống tầng dưới của nó.
:::

::: {.tracuu codes="G2-S11a"}
:::

#### 5.3.4 Thiếu cascade ở một tầng: dây chuyền đứt tại đó {.mo-rong}

**Bối cảnh.** Cùng ba tầng như 5.3.3, nhưng `Customer.orders` **không** khai cascade, trong khi `PurchaseOrder.items` vẫn có `ALL`. Bạn persist customer có một order, order có một item.

**Kết quả.** Chỉ có một câu INSERT, cho `customer`. Order và item đều không được lưu và không có lỗi. Dù `items` có cascade `ALL`, order chưa bao giờ được persist nên cascade ở tầng dưới không có dịp chạy: cascade chỉ đi từ entity **đang được xử lý** xuống, nó không tự bắc cầu qua một khâu thiếu cascade. Đây là lỗi im lặng giống mục 5.2.1, chỉ xảy ra ở tầng trên.

::: {.ghi-nho}
Khi đồ thị có nhiều tầng, kiểm tra cascade ở **từng** tầng. Một khâu thiếu là toàn bộ phần dưới bị bỏ qua.
:::

::: {.tracuu codes="G2-S11b"}
:::

### 5.4 CascadeType.MERGE: lưu cả đồ thị DETACHED {.bien-the}

Khác mục 5.2 đúng một điểm: `items` thêm `cascade = CascadeType.MERGE`.

```java
@OneToMany(mappedBy = "order", fetch = FetchType.LAZY,
        cascade = CascadeType.MERGE)
private List<C2OrderItem> items = new ArrayList<>();
```

![Bảng order_item có hai khóa ngoại: order_id tới purchase_order.id và product_id tới product.id; cấu trúc bảng không đổi, chỉ cách khai báo cascade khác](img/g2-c2.png){width=90%}

**Ưu điểm.** `merge(order)` copy thay đổi của cả order lẫn item vào database: cập nhật item cũ, thêm item mới.

**Nhược điểm.** `merge` luôn SELECT lại đồ thị từ database trước khi copy; đồ thị lớn thì tốn. Và `MERGE` đứng một mình không thay được `PERSIST` (lưu entity mới trực tiếp).

**Nên dùng khi.** Bạn nhận một đồ thị đã DETACHED (ví dụ từ form chỉnh sửa, hoặc từ một request trước) và muốn lưu lại toàn bộ thay đổi qua một lệnh `merge`.

Cả ba tình huống dưới đây dùng cùng một kịch bản: order A-001 có hai item (số lượng 1 và 2). Một transaction tải cả đồ thị rồi đóng, nên order và hai item thành DETACHED. Code ứng dụng sửa số lượng của item đầu thành 99 và thêm một item mới có số lượng 7, rồi gọi `merge(order)` trong một transaction mới.

```java
// Transaction 1: tải cả đồ thị rồi đóng; mọi entity thành DETACHED.
PurchaseOrder detachedOrder = inTxReturning(() -> em.createQuery(
        "select o from PurchaseOrder o join fetch o.items where o.id = :id",
        PurchaseOrder.class).setParameter("id", orderId).getSingleResult());

// Ngoài transaction: sửa đồ thị DETACHED.
detachedOrder.getItems().get(0).setQuantity(99);
detachedOrder.addItem(new OrderItem(productPen, 7));

// Transaction 2: merge cả đồ thị.
inTx(() -> em.merge(detachedOrder));
```

Dữ liệu `order_item` trước khi merge:

| quantity |
|---|
| 1 |
| 2 |

#### 5.4.1 Không có cascade MERGE: thay đổi của item bị mất {.cot-loi}

**Bối cảnh.** Một màn hình chỉnh sửa đơn hàng: người dùng đổi số lượng một dòng hàng và thêm một dòng mới, rồi bấm Lưu. Code gọi `merge(order)` và không báo lỗi gì. Màn hình quay về danh sách, mọi thứ trông bình thường. Nhưng khi mở lại đơn, số lượng cũ vẫn còn và dòng mới không có.

**Khi nào xảy ra.** `items` không có cascade `MERGE`, và bạn `merge` order DETACHED có item đã sửa hoặc item mới.

**Khi nào không xảy ra.** Nếu `items` có `MERGE` (mục 5.4.2). Hoặc nếu bạn tải item riêng và sửa trong transaction (MANAGED), rồi để dirty checking lo.

**Ví dụ.** Kịch bản chung ở đầu mục 5.4, trên mapping không có cascade.

**Kết quả.** Không có câu UPDATE nào, không có câu INSERT nào, không có lỗi. Hibernate chỉ chạy ba câu SELECT: một câu tải lại order, hai câu tải lại từng item theo id.

Dữ liệu `order_item` sau khi merge:

| quantity |
|---|
| **1** |
| **2** |

Thay đổi: không có. Giữ nguyên: cả hai dòng với số lượng cũ; số lượng 99 và item 7 biến mất.

**Vì sao.** `merge(order)` tải bản MANAGED của order từ database, rồi copy state từ bản DETACHED sang. Với collection `items`, nếu không có `MERGE` thì thao tác dừng ở ranh giới của quan hệ: Hibernate chỉ tải lại các item để giải quyết tham chiếu, **không copy state của chúng** và không persist item mới. Số lượng 99 chỉ tồn tại trên object DETACHED, nơi không ai theo dõi. Item 7 cũng vậy: nó không bao giờ vào persistence context.

**Cách tránh.** Khai `cascade = MERGE` trên `items` (mục 5.4.2), hoặc không dùng `merge` cho đồ thị: tải entity trong transaction, sửa, rồi để dirty checking ghi.

::: {.ghi-nho}
`merge(cha)` không có `MERGE` trên collection thì **bỏ qua thay đổi của con mà không báo lỗi**. Màn hình lưu "thành công" nhưng dữ liệu không đổi.
:::

::: {.tracuu codes="G2-S04a"}
:::

#### 5.4.2 Có cascade MERGE: UPDATE item cũ, INSERT item mới {.cot-loi}

**Bối cảnh.** Cùng kịch bản 5.4.1, nhưng lần này bạn khai `cascade = MERGE`. Bạn muốn biết chính xác Hibernate ghi những gì, đặc biệt order có bị UPDATE vô ích không.

**Khi nào xảy ra.** `items` có `MERGE` (hoặc `ALL`), và bạn `merge` order DETACHED.

**Khi nào không xảy ra.** Khi `items` không có `MERGE` (mục 5.4.1) hoặc chỉ có `PERSIST` (mục 5.4.3).

**Ví dụ.** Kịch bản chung ở đầu mục 5.4.

**Kết quả.** Hibernate tải order cùng các item trong một câu SELECT (có left join), rồi lúc commit gửi một câu INSERT cho item mới và một câu UPDATE cho item đã sửa:

```sql
select cpo1_0.id, cpo1_0.customer_id, cpo1_0.order_no, i1_0.order_id, i1_0.id,
    i1_0.product_id, i1_0.quantity
from g2_c2_merge.purchase_order cpo1_0
left join g2_c2_merge.order_item i1_0 on cpo1_0.id=i1_0.order_id
where cpo1_0.id=?

insert into g2_c2_merge.order_item (order_id, product_id, quantity, id)
values (?, ?, ?, ?)

update g2_c2_merge.order_item
set order_id=?, product_id=?, quantity=?
where id=?
```

Dữ liệu `order_item` sau khi merge:

| quantity |
|---|
| **99** |
| 2 |
| **7** |

Thay đổi: item đầu đổi từ 1 thành 99; thêm item mới 7. Giữ nguyên: item thứ hai (2). Không có câu UPDATE nào cho `purchase_order`, vì order không đổi gì.

**Vì sao.** `MERGE` được truyền xuống từng phần tử. Với item đã có id, Hibernate tìm bản MANAGED tương ứng và copy state vào (dirty checking sinh ra câu UPDATE khi state khác). Với item mới (không có id), Hibernate tạo một bản MANAGED mới từ state của nó, rồi INSERT lúc flush. Order không có cột nào đổi nên dirty checking không sinh UPDATE.

::: {.ghi-nho}
Muốn lưu cả đồ thị DETACHED bằng một lệnh `merge`, **mọi quan hệ cần lan phải có `MERGE`**. Kết quả là UPDATE cho phần đã có và INSERT cho phần mới.
:::

::: {.tracuu codes="G2-S04b"}
:::

#### 5.4.3 Chỉ có PERSIST mà gọi merge: sinh ra dòng ma {.cot-loi}

**Bối cảnh.** Mapping của bạn có `cascade = PERSIST` vì bạn thấy nó làm `persist(order)` chạy tốt (mục 5.3). Về sau có một chức năng chỉnh sửa đơn dùng `merge`. Bạn nghĩ: "đã có cascade rồi, chắc merge cũng lan". Đây là giả định rất tự nhiên, và nó sai theo cách tệ nhất.

**Khi nào xảy ra.** `items` chỉ có `PERSIST` (không có `MERGE`), và bạn `merge` order DETACHED có item mới.

**Khi nào không xảy ra.** Nếu `items` có `MERGE` thì item mới được tạo đúng state (mục 5.4.2).

**Ví dụ.** Kịch bản chung ở đầu mục 5.4, trên mapping chỉ có `PERSIST`.

**Kết quả.** Không có câu UPDATE nào, nên số lượng 99 mất, giống mục 5.4.1. Nhưng khác 5.4.1, **có một câu INSERT**:

```sql
insert into g2_c1_persist.order_item (order_id, product_id, quantity, id)
values (?, ?, ?, ?)
```

Câu INSERT mang các giá trị `null, null, 0`. Dữ liệu `order_item` sau khi merge:

| quantity | order_id | product_id |
|---|---|---|
| 1 | id của A-001 | id của sản phẩm |
| 2 | id của A-001 | id của sản phẩm |
| **0** | **NULL** | **NULL** |

Dòng thứ ba là **dòng ma**: số lượng 0 thay vì 7, không thuộc order nào, không trỏ tới sản phẩm nào. Không có lỗi nào được báo.

::: {.khac-du-doan codes="G2-S04c"}
**Điều nhiều người nghĩ:** `PERSIST` ít nhất giúp lưu được item mới khi merge; hoặc merge thiếu `MERGE` thì không làm gì (như mục 5.4.1).

**Thực tế:** item cũ không được cập nhật, còn item mới được INSERT dưới dạng **bản sao rỗng** (`quantity = 0`, hai khóa ngoại NULL).

Đã kiểm chứng trên Hibernate 7.4.15.Final.
:::

**Vì sao.** Hãy theo dõi từng bước. Không có `MERGE`, nên khi `merge` đi qua `items`, Hibernate **không copy state** của item mới sang bản MANAGED. Tuy vậy, nó vẫn cần đặt một thực thể vào collection của order MANAGED, nên tạo ra một object item **rỗng** (các field có giá trị mặc định, `quantity = 0` vì kiểu `int`). Sau đó tới lúc flush, persist-on-flush (mục 5.3.2) thấy một phần tử TRANSIENT trong collection có cascade `PERSIST` và lưu đúng bản rỗng đó. Cột `order_id` NULL vì object rỗng chưa được gán `order`, và nó nằm trong cột nullable nên database cho qua.

**Cách tránh.** Nếu dùng `merge` cho đồ thị, khai `MERGE` (hoặc `ALL`) trên mọi quan hệ cần lan. Nên đặt thêm `nullable = false` cho các khóa ngoại bắt buộc để dòng ma bị database chặn thay vì lặng lẽ được ghi.

::: {.ghi-nho}
Cascade `PERSIST` **không** giúp `merge`. Thiếu `MERGE` có thể còn tệ hơn không có cascade: dữ liệu rác được ghi xuống mà không có lỗi.
:::

::: {.tracuu codes="G2-S04c"}
:::

### 5.5 CascadeType.REMOVE và OnDelete: xóa cha thì con đi đâu {.bien-the}

Có hai cách để xóa con khi xóa cha, và chúng khác nhau về bản chất:

- `cascade = REMOVE` (cascade JPA): Hibernate tải từng con rồi `DELETE` từng dòng.
- `@OnDelete(action = CASCADE)` (Hibernate): khóa ngoại có mệnh đề `ON DELETE CASCADE`, PostgreSQL xóa dây chuyền.

```java
// Cách 1: cascade JPA. Khác baseline duy nhất ở cascade.
@OneToMany(mappedBy = "order", fetch = FetchType.LAZY,
        cascade = CascadeType.REMOVE)
private List<C3OrderItem> items = new ArrayList<>();

// Cách 2: @OnDelete ở OWNING side (OrderItem.order). PurchaseOrder.items
// vẫn không khai cascade nào.
@ManyToOne(fetch = FetchType.LAZY)
@OnDelete(action = OnDeleteAction.CASCADE)
@JoinColumn(name = "order_id")
private C8PurchaseOrder order;
```

![Bảng order_item có khóa ngoại order_id tới purchase_order.id; ở cách thứ hai khóa ngoại này có thêm mệnh đề on delete cascade trong DDL](img/g2-c8.png){width=90%}

**Ưu điểm.** Cascade JPA: Hibernate biết đã xóa gì, chạy được callback và listener của entity, trạng thái persistence context nhất quán. `@OnDelete`: một câu DELETE duy nhất, không tải gì, chạy cả với bulk delete.

**Nhược điểm.** Cascade JPA: một câu DELETE cho mỗi dòng con, và phải SELECT trước. `@OnDelete`: Hibernate không biết con đã biến mất nên bộ nhớ có thể lệch với database; không chạy được callback của entity con.

**Nên dùng khi.** Cascade JPA là mặc định cho aggregate nhỏ. `@OnDelete` khi cần xóa hàng loạt nhanh và chấp nhận những hạn chế trên. `@OnDelete` là annotation của Hibernate; mục JPA thuần ở tình huống 5.5.4.

#### 5.5.1 Xóa order còn item mà không cascade: vi phạm khóa ngoại {.cot-loi}

**Bối cảnh.** Chức năng "hủy đơn hàng" gọi `em.remove(order)`. Với đơn không có dòng hàng, mọi thứ chạy tốt. Khi có dòng hàng, người dùng thấy lỗi 500, và log chỉ có một câu lỗi về khóa ngoại. Nhiều người thử debug bằng cách đọc exception của Hibernate mà không thấy gì, vì đây là lỗi của database.

**Khi nào xảy ra.** `items` không có cascade `REMOVE`, nhưng database còn dòng `order_item` trỏ tới order cần xóa.

**Khi nào không xảy ra.** Nếu order không còn item nào, hoặc nếu có cascade `REMOVE` (mục 5.5.2), hoặc có `@OnDelete` (mục 5.5.4).

**Ví dụ.**

```java
assertThatThrownBy(() -> inTx(() -> {
    PurchaseOrder orderToDelete = em.find(PurchaseOrder.class, orderId);
    em.remove(orderToDelete);
    // Trạng thái: orderToDelete là REMOVED. Chưa có DELETE nào chạy.
})).isInstanceOf(DataIntegrityViolationException.class);
```

**Kết quả.** Hibernate chỉ gửi `DELETE` cho `purchase_order`; PostgreSQL từ chối vì hai dòng `order_item` còn trỏ tới. Spring dịch lỗi thành `DataIntegrityViolationException`. Transaction rollback. Không có câu DELETE nào cho `order_item`.

| Bảng | Số dòng sau khi chạy |
|---|---|
| purchase_order | **1** (rollback) |
| order_item | **2** (không bị đụng) |

**Vì sao.** Không có cascade, Hibernate không biết gì về các item: cho nó, `em.remove(order)` chỉ có nghĩa "xóa dòng của order". Việc bảo vệ dữ liệu là của khóa ngoại `order_item.order_id`; PostgreSQL kiểm tra và từ chối câu DELETE. Lỗi xuất hiện lúc flush (lúc commit), chứ không phải lúc gọi `remove`.

**Cách tránh.** Chọn một chiến lược xóa con: cascade `REMOVE` (mục 5.5.2), `@OnDelete` (mục 5.5.4), hoặc xóa item tường minh trước. Chương 6 có bảng so sánh bốn chiến lược trên một đồ thị ba tầng.

::: {.ghi-nho}
Khóa ngoại là người gác cổng cuối cùng. Không cascade thì xóa cha còn con sẽ bị database chặn và transaction rollback. Đây là lỗi tốt: nó báo thay vì làm hỏng dữ liệu.
:::

::: {.tracuu codes="G2-S05a"}
:::

#### 5.5.2 cascade REMOVE: SELECT item, DELETE từng item rồi DELETE order {.cot-loi}

**Bối cảnh.** Cùng thao tác "hủy đơn" như 5.5.1 nhưng `items` có `cascade = REMOVE`. Bạn muốn biết Hibernate xóa theo thứ tự nào, và trạng thái các entity ngay sau `remove`.

**Khi nào xảy ra.** `items` có `REMOVE` (hoặc `ALL`) và bạn gọi `em.remove(order)`.

**Khi nào không xảy ra.** Nếu bạn xóa bằng câu JPQL `delete from ...` (cascade không chạy, xem Chương 15), hoặc nếu `items` không có `REMOVE` (mục 5.5.1).

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder orderToDelete = em.find(PurchaseOrder.class, orderId);
    em.remove(orderToDelete);
    // Trạng thái ngay sau remove: order và cả hai item đều REMOVED.
    // Chưa có DELETE nào chạy; chúng chạy lúc commit.
});
```

**Kết quả.** Hibernate gửi hai câu SELECT (tải order, tải các item), rồi lúc commit gửi hai câu DELETE cho `order_item` và cuối cùng một câu DELETE cho `purchase_order`:

```sql
select cpo1_0.id, cpo1_0.customer_id, cpo1_0.order_no
from g2_c3_remove.purchase_order cpo1_0
where cpo1_0.id=?

select i1_0.order_id, i1_0.id, i1_0.product_id, i1_0.quantity
from g2_c3_remove.order_item i1_0
where i1_0.order_id=?

delete from g2_c3_remove.order_item where id=?
delete from g2_c3_remove.order_item where id=?
delete from g2_c3_remove.purchase_order where id=?
```

Cả hai bảng đều trống sau khi chạy.

**Vì sao.** Khi `remove(order)` chạy, Hibernate phải biết các item để đánh dấu chúng REMOVED, nên nó tải collection `items` bằng một câu SELECT (đây là lý do có SELECT, dù bạn chỉ gọi `remove`). Mỗi item được đánh dấu REMOVED, rồi lúc flush mỗi entity REMOVED sinh một câu DELETE riêng. Thứ tự: con trước, cha sau, vì item giữ khóa ngoại tới order; nếu xóa order trước thì chính câu DELETE order sẽ vi phạm khóa ngoại.

::: {.ghi-nho}
`cascade = REMOVE` lan ngay trong `remove`: cha và con cùng REMOVED. SQL gồm một SELECT để tải con, rồi DELETE từng con, cha xóa cuối cùng.
:::

::: {.tracuu codes="G2-S05b"}
:::

#### 5.5.3 Chi phí: xóa order có 3 item tốn 5 câu SQL, và con số tăng theo số item {.cot-loi}

**Bối cảnh.** Một đơn hàng bán sỉ có 200 dòng hàng. Xóa đơn bằng cascade `REMOVE` nghe có vẻ chỉ một lệnh, nhưng database nhận bao nhiêu câu lệnh? Tình huống này đo chi phí đó trên đơn nhỏ để thấy quy luật.

**Khi nào xảy ra.** Cascade `REMOVE` trên collection có N phần tử: tải một lần và mỗi phần tử một câu DELETE.

**Khi nào không xảy ra.** Khi database tự xóa nhờ `@OnDelete` (mục 5.5.4), hoặc khi bạn dùng bulk delete (Chương 15).

**Ví dụ.**

```java
// Order A-001 có 3 item.
inTx(() -> em.remove(em.find(PurchaseOrder.class, orderId)));
```

**Kết quả.** Một câu SELECT cho `purchase_order` kèm một câu SELECT cho `order_item`, ba câu DELETE `order_item`, một câu DELETE `purchase_order`. Số câu DELETE của `order_item` bằng đúng số item. Với đơn 200 dòng, đó là 200 câu DELETE riêng lẻ.

**Vì sao.** Cascade JPA làm việc theo từng entity: mỗi item là một entity REMOVED, và mỗi entity REMOVED là một câu DELETE theo khóa chính. Hibernate không tự gộp thành `delete ... where order_id = ?`. Nếu bật batching (`jdbc.batch_size`) các câu này có thể được gom khi gửi qua JDBC, nhưng vẫn là N thao tác trên server.

::: {.ghi-nho}
Cascade `REMOVE` đắt tỉ lệ thuận với số con. Với đồ thị lớn, hãy cân nhắc `@OnDelete` hoặc xóa hàng loạt (Chương 15).
:::

::: {.tracuu codes="G2-S10a"}
:::

#### 5.5.4 OnDelete: DDL có ON DELETE CASCADE, xóa order chỉ cần một câu DELETE {.cot-loi}

**Bối cảnh.** Nhóm của bạn muốn xóa đơn hàng nhanh hơn, và không muốn Hibernate SELECT rồi DELETE từng dòng. Database đã có sẵn `ON DELETE CASCADE`; câu hỏi là làm sao để Hibernate sinh ra khóa ngoại như vậy và nó thay đổi hành vi thế nào.

**Khi nào xảy ra.** `OrderItem.order` có `@OnDelete(action = OnDeleteAction.CASCADE)`; `items` không có cascade JPA.

**Khi nào không xảy ra.** Nếu bạn tự tạo bảng bằng migration, khóa ngoại có thể có `ON DELETE CASCADE` mà mapping không cần annotation, nhưng khi đó Hibernate không "biết". Với mapping mặc định thì khóa ngoại là `NO ACTION` (mục 5.5.1).

**Ví dụ.**

```java
// Order A-001 có 3 item.
inTx(() -> em.remove(em.find(PurchaseOrder.class, orderId)));
```

**Kết quả.** DDL của khóa ngoại có thêm mệnh đề `on delete cascade`, so với khóa ngoại mặc định `NO ACTION` của cách cascade JPA:

::: {.ddl schema="g2_c8_ondelete" tables="order_item"}
:::

Hibernate chỉ gửi **một** câu DELETE, cho `purchase_order`. Không SELECT item nào, không DELETE item nào:

```sql
select cpo1_0.id, cpo1_0.customer_id, cpo1_0.order_no
from g2_c8_ondelete.purchase_order cpo1_0
where cpo1_0.id=?

delete from g2_c8_ondelete.purchase_order where id=?
```

Dữ liệu sau khi chạy giống hệt cách cascade JPA: cả `purchase_order` và `order_item` đều trống. Ba item bị PostgreSQL xóa.

**Vì sao.** `@OnDelete` không đổi hành vi của Hibernate lúc xóa; nó chỉ đổi **DDL**: khóa ngoại `order_item.order_id` được tạo với `on delete cascade`. Khi nhận câu `DELETE purchase_order`, chính PostgreSQL xóa các dòng `order_item` trỏ tới. Hibernate không cần tải item vì nó không làm gì với chúng, nên không có SELECT.

**Trong JPA thuần.** `@OnDelete` là annotation của Hibernate. JPA không có cách khai báo `ON DELETE CASCADE`. Cách gần nhất: tự viết DDL (migration) với khóa ngoại có `ON DELETE CASCADE`, rồi để mapping JPA như bình thường. Khi đó JPA thuần cũng chạy đúng, nhưng schema do Hibernate sinh ra sẽ không có mệnh đề đó nếu bạn dựa vào tự sinh.

::: {.ghi-nho}
`@OnDelete` chuyển việc xóa dây chuyền cho database: Hibernate chỉ gửi một DELETE cho cha. Dữ liệu cuối giống cascade JPA, nhưng ít SQL hơn nhiều.
:::

::: {.tracuu codes="G2-S10b"}
:::

#### 5.5.5 OnDelete: item đã tải vẫn MANAGED dù dòng đã mất {.cot-loi}

**Bối cảnh.** Trong cùng transaction, code tải order kèm item, sau đó xóa order. Đoạn sau trong transaction còn dùng một item đã tải. Với `@OnDelete`, item không bị đánh dấu REMOVED, và Hibernate không được database báo gì, nên entity này trở thành "ma": trong bộ nhớ nó vẫn sống nhưng dòng trong database không còn.

**Khi nào xảy ra.** `@OnDelete` kết hợp với entity con đã được tải vào persistence context trước khi xóa cha.

**Khi nào không xảy ra.** Nếu con chưa được tải. Hoặc nếu dùng cascade JPA `REMOVE`: khi đó con chuyển sang REMOVED (mục 5.5.2).

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder orderToDelete = em.createQuery(
            "select o from PurchaseOrder o join fetch o.items where o.id = :id",
            PurchaseOrder.class).setParameter("id", orderId).getSingleResult();
    OrderItem loadedItem = orderToDelete.getItems().get(0);

    em.remove(orderToDelete);
    // Trạng thái: orderToDelete là REMOVED; loadedItem vẫn là MANAGED.

    em.flush();
    // Sau flush: loadedItem VẪN là MANAGED, dù dòng của nó đã mất trong DB.
});
```

**Kết quả.** Sau `remove`, order là REMOVED còn `loadedItem` vẫn MANAGED, vì không có cascade JPA nào đánh dấu nó. Sau `flush`, câu DELETE order đã chạy và PostgreSQL đã xóa ba dòng item (đọc bằng cùng connection thấy bảng `order_item` trống), nhưng `loadedItem` **vẫn MANAGED**. Không có câu DELETE nào cho `order_item`.

**Vì sao.** Hibernate chỉ biết những gì nó đã làm hoặc đã đọc. Việc xóa item diễn ra bên trong PostgreSQL, và PostgreSQL không có cơ chế báo ngược cho client. Persistence context vẫn giữ `loadedItem` như một entity MANAGED. Nếu tiếp tục dùng nó (ví dụ gán vào order khác, hoặc sửa field rồi flush), Hibernate sẽ cố UPDATE một dòng không còn tồn tại và gây lỗi khó hiểu.

**Cách tránh.** Với `@OnDelete`, không dùng lại entity con sau khi xóa cha. Nếu cần, gọi `em.clear()` hoặc kết thúc transaction ngay sau thao tác xóa.

::: {.ghi-nho}
`@OnDelete` làm persistence context **lệch** với database. Đây là cái giá của việc nhanh: Hibernate không thấy, không cập nhật trạng thái các con đã tải.
:::

::: {.tracuu codes="G2-S10c"}
:::

#### 5.5.6 Xóa bằng Spring Data: deleteById và delete(entity) cascade, deleteAllInBatch thì không {.mo-rong}

**Bối cảnh.** Trong ứng dụng Spring Data, bạn không gọi `em.remove` trực tiếp mà dùng repository. Ba cách xóa phổ biến cho kết quả khác nhau với cascade `REMOVE`.

**Kết quả.**

| Cách xóa | Có cascade `REMOVE` không | SQL |
|---|---|---|
| `deleteById(id)` | Có | SELECT order, SELECT item, 2 DELETE item, 1 DELETE order |
| `delete(entity DETACHED)` | Có | 2 DELETE item, 1 DELETE order |
| `deleteAllInBatch()` | **Không** | Một câu DELETE hàng loạt; vi phạm khóa ngoại, `DataIntegrityViolationException` |

`deleteById` tải entity (`em.find`) rồi `em.remove`; `delete(entity)` với entity DETACHED cũng nạp bản MANAGED rồi `em.remove`; cả hai đi qua `EntityManager` nên cascade chạy. `deleteAllInBatch` là câu JPQL `delete from ...` chạy thẳng, nên bỏ qua cascade và gặp khóa ngoại (xem thêm Chương 15).

::: {.ghi-nho}
Tên method chứa "InBatch" nghĩa là bulk: không cascade. Các method còn lại đi qua `EntityManager`, nên cascade chạy.
:::

::: {.tracuu codes="G2-S13a,G2-S13b,G2-S13c"}
:::

### 5.6 DETACH và REFRESH: ALL so với chỉ PERSIST cộng MERGE {.bien-the}

Mục này so sánh hai cách khai báo: `cascade = ALL` (đủ năm loại) và `cascade = {PERSIST, MERGE}` (thiếu `DETACH`, `REFRESH`, `REMOVE`). Hai thao tác ít dùng hơn là `detach` và `refresh`, nhưng chúng cho thấy rất rõ nguyên lý "mỗi loại độc lập".

```java
// Cách 1: đủ năm loại.
@OneToMany(mappedBy = "order", fetch = FetchType.LAZY,
        cascade = CascadeType.ALL)
private List<C4OrderItem> items = new ArrayList<>();

// Cách 2: thiếu DETACH, REFRESH, REMOVE.
@OneToMany(mappedBy = "order", fetch = FetchType.LAZY,
        cascade = { CascadeType.PERSIST, CascadeType.MERGE })
private List<C5OrderItem> items = new ArrayList<>();
```

![Bảng order_item có hai khóa ngoại: order_id tới purchase_order.id và product_id tới product.id; cấu trúc bảng giống mục 5.2, chỉ khác cách khai báo cascade](img/g2-c5.png){width=90%}

**Ưu điểm.** `ALL` đơn giản và nhất quán: mọi thao tác lan, không có bất ngờ. Liệt kê thủ công cho phép chọn chính xác thao tác nào được lan.

**Nhược điểm.** `ALL` bao gồm cả `REMOVE`, nên nó lan cả thao tác xóa mà bạn có thể không muốn. Liệt kê thủ công dễ thiếu: thiếu `DETACH` hoặc `REFRESH` gây ra các bất ngờ dưới đây.

**Nên dùng khi.** `ALL` cho aggregate nơi vòng đời con gắn chặt với cha. Liệt kê thủ công chỉ khi bạn thật sự muốn loại một thao tác ra, và nên có comment giải thích vì sao.

#### 5.6.1 detach(order) với ALL: item cũng DETACHED, sửa item không UPDATE {.cot-loi}

**Bối cảnh.** Bạn gọi `em.detach(order)` để "ngắt" order khỏi persistence context, rồi sửa một item vì muốn thử nghiệm mà không ghi vào database. Câu hỏi: item có bị ngắt theo không?

**Khi nào xảy ra.** `items` có `DETACH` (qua `ALL`), bạn `detach(order)` rồi sửa item.

**Khi nào không xảy ra.** Nếu thiếu `DETACH` (mục 5.6.2).

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder loadedOrder = em.createQuery(
            "select o from PurchaseOrder o join fetch o.items where o.id = :id",
            PurchaseOrder.class).setParameter("id", orderId).getSingleResult();
    OrderItem loadedItem = loadedOrder.getItems().get(0);

    em.detach(loadedOrder);
    // Trạng thái: loadedOrder là DETACHED; loadedItem cũng là DETACHED.

    loadedItem.setQuantity(99);   // item đã DETACHED, không ai theo dõi
});
```

**Kết quả.** Không có câu UPDATE nào cho `order_item`. Số lượng trong database giữ nguyên (1 và 2); số lượng 99 không bao giờ tới database.

**Vì sao.** `detach` được truyền xuống từng item nhờ `DETACH` trong `ALL`. Một entity DETACHED không còn nằm trong persistence context, nên dirty checking lúc flush không thấy nó và không sinh UPDATE.

::: {.ghi-nho}
Với `ALL`, `detach(cha)` ngắt cả con. Mọi sửa đổi sau đó trên con không được ghi.
:::

::: {.tracuu codes="G2-S06a"}
:::

#### 5.6.2 Thiếu DETACH: order đã detach nhưng item vẫn bị UPDATE {.cot-loi}

**Bối cảnh.** Cùng thao tác như 5.6.1 nhưng mapping liệt kê `{PERSIST, MERGE}`. Người viết mapping nghĩ: "mình chỉ cần lưu và merge thôi". Sau đó có code `detach(order)` để ngắt, rồi sửa item, và item vẫn được ghi.

**Khi nào xảy ra.** `items` thiếu `DETACH`; bạn `detach(order)` rồi sửa item.

**Khi nào không xảy ra.** Khi có `DETACH` (mục 5.6.1).

**Ví dụ.** Giống 5.6.1 trên mapping `{PERSIST, MERGE}`.

**Kết quả.** Order là DETACHED nhưng item vẫn MANAGED. Lúc commit có một câu UPDATE:

```sql
update g2_c5_persist_merge.order_item
set order_id=?, product_id=?, quantity=?
where id=?
```

Số lượng 99 được ghi xuống database.

| Bảng order_item | quantity trước | quantity sau |
|---|---|---|
| item đã sửa | 1 | **99** |
| item còn lại | 2 | 2 |

**Vì sao.** `detach(order)` chỉ đổi trạng thái của order. Vì quan hệ `items` không có `DETACH`, Hibernate không đi tiếp vào collection. Item vẫn nằm trong persistence context như MANAGED, nên dirty checking vẫn theo dõi và ghi thay đổi.

**Cách tránh.** Muốn ngắt cả đồ thị thì cần `DETACH` trên các quan hệ liên quan, hoặc dùng `em.clear()` để ngắt tất cả.

::: {.ghi-nho}
`detach(cha)` không có `DETACH` trên quan hệ thì chỉ ngắt cha; con vẫn được theo dõi và ghi. Đây là chỗ dễ bất ngờ khi thao tác ngắt không như mong đợi.
:::

::: {.tracuu codes="G2-S06b"}
:::

#### 5.6.3 refresh(order) với ALL: item nạp lại, thay đổi chưa flush bị mất {.mo-rong}

**Bối cảnh.** Bạn sửa số lượng một item trong bộ nhớ rồi gọi `em.refresh(order)` để nạp lại order từ database.

**Kết quả.** Với `ALL`, `REFRESH` lan xuống item: item cũng được nạp lại, số lượng 99 (chưa flush) bị ghi đè bằng giá trị trong database. Không có câu UPDATE nào, và database không đổi (1 và 2).

::: {.ghi-nho}
Với `ALL`, `refresh(cha)` hủy cả thay đổi chưa flush của con.
:::

::: {.tracuu codes="G2-S07a"}
:::

#### 5.6.4 Thiếu REFRESH: refresh(order) không đụng tới item {.mo-rong}

**Bối cảnh.** Cùng thao tác như 5.6.3 nhưng mapping chỉ có `{PERSIST, MERGE}`. Người viết tưởng "refresh là hủy mọi thay đổi chưa flush".

**Kết quả.** `refresh(order)` chỉ nạp lại order. Item không bị nạp lại, vẫn giữ số lượng 99 trong bộ nhớ, và lúc commit bị flush thành một câu UPDATE. Số lượng 99 được ghi xuống database.

::: {.ghi-nho}
`refresh` chỉ lan khi có `REFRESH`. Thiếu nó, thay đổi chưa flush của con vẫn sống sót và được ghi.
:::

::: {.tracuu codes="G2-S07b"}
:::

### 5.7 Cascade đặt trên ManyToOne: thường là sai chỗ {.bien-the}

Mọi mục trước đặt cascade trên `OrderItem` của order (phía một-nhiều, `items`). Mục này đặt cascade trên `OrderItem.product`, tức là phía con trỏ tới một entity dùng chung. Có hai biến thể: `REMOVE` (nguy hiểm) và `PERSIST` (gây lỗi, nhưng có cách sửa).

```java
// Biến thể xấu: con điều khiển vòng đời của product dùng chung.
@ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
@JoinColumn(name = "product_id")
private C6Product product;

// Biến thể khác: PERSIST trên ManyToOne.
@ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
@JoinColumn(name = "product_id")
private C7Product product;
```

![Bảng order_item có khóa ngoại product_id tới product.id; nhiều item có thể trỏ tới cùng một product, nên xóa product theo item là nguy hiểm](img/g2-c6.png){width=90%}

**Ưu điểm.** `PERSIST` trên `@ManyToOne` hữu ích khi con và thứ nó trỏ tới luôn được tạo cùng nhau và không dùng chung (ví dụ order và địa chỉ giao hàng riêng của nó). `REMOVE` thì gần như không có ưu điểm khi entity được dùng chung.

**Nhược điểm.** `REMOVE` có thể xóa product khi xóa một item, và nếu product chỉ có một item thì mất dữ liệu master mà không báo gì. `PERSIST` làm `persist(item)` thất bại khi product là DETACHED.

**Nên dùng khi.** Chỉ khi thứ được trỏ tới thuộc riêng về con (không dùng chung) và có vòng đời gắn chặt. Với dữ liệu master dùng chung như `Product`, **không đặt cascade** trên `@ManyToOne` (mục 5.7.6).

#### 5.7.1 cascade REMOVE trên ManyToOne: xóa một item kéo theo xóa product đang dùng chung {.cot-loi}

**Bối cảnh.** Hai dòng hàng cùng trỏ tới sản phẩm "Bút bi". Người dùng xóa một dòng hàng. Nếu `OrderItem.product` có `cascade = REMOVE`, thao tác xóa lan sang product, và product vẫn còn dòng hàng khác tham chiếu tới nó.

**Khi nào xảy ra.** `@ManyToOne` có `REMOVE` và product còn được item khác dùng.

**Khi nào không xảy ra.** Khi `@ManyToOne` không có cascade `REMOVE` (mục 5.7.6), hoặc khi product chỉ được đúng item đang xóa dùng (mục 5.7.2, và kết quả còn tệ hơn).

**Ví dụ.**

```java
assertThatThrownBy(() -> inTx(() -> {
    OrderItem itemOne = em.find(OrderItem.class, itemOneId);
    em.remove(itemOne);
    // Cascade REMOVE lan sang itemOne.product: product cũng bị đánh dấu REMOVED.
})).isInstanceOf(DataIntegrityViolationException.class);
```

**Kết quả.** Hibernate gửi `DELETE order_item` rồi `DELETE product`. Item thứ hai vẫn trỏ tới product nên PostgreSQL từ chối câu DELETE product. Transaction rollback, cả hai item và product vẫn còn.

```sql
delete from g2_c6_remove_on_m2o.order_item where id=?
delete from g2_c6_remove_on_m2o.product where id=?
```

**Vì sao.** Cascade `REMOVE` trên `@ManyToOne` đi từ item tới product: `remove(item)` lan sang `item.product`. Hibernate không kiểm tra xem product còn được ai dùng. Việc bảo vệ lại là của khóa ngoại: nó chặn khi còn item khác trỏ tới.

::: {.ghi-nho}
Cascade `REMOVE` trên `@ManyToOne` biến "xóa một dòng hàng" thành "thử xóa luôn sản phẩm". Lỗi khóa ngoại ở đây là may mắn, vì nó còn chặn được thảm họa ở tình huống tiếp theo.
:::

::: {.tracuu codes="G2-S08a"}
:::

#### 5.7.2 Product chỉ có một item dùng: product bị xóa lặng lẽ {.cot-loi}

**Bối cảnh.** Cùng mapping 5.7.1, nhưng lần này product "Bút bi" chỉ có đúng một dòng hàng trỏ tới. Người dùng xóa dòng hàng đó, và không có khóa ngoại nào chặn lại.

**Khi nào xảy ra.** `@ManyToOne` có `REMOVE` và product không còn được item nào khác dùng.

**Khi nào không xảy ra.** Khi còn item khác dùng product (khi đó khóa ngoại chặn, mục 5.7.1), hoặc khi không có cascade `REMOVE` trên `@ManyToOne`.

**Ví dụ.**

```java
inTx(() -> em.remove(em.find(OrderItem.class, itemId)));
```

**Kết quả.** Hibernate gửi `DELETE order_item` rồi `DELETE product`. Không ai còn trỏ tới product nên PostgreSQL cho phép. Không có lỗi nào.

| Bảng | Số dòng sau khi chạy |
|---|---|
| order_item | 0 (đúng như yêu cầu) |
| product | **0** (không ai yêu cầu) |

Product "Bút bi", dữ liệu master dùng cho cả catalog, đã bị xóa chỉ vì một dòng hàng bị xóa.

**Vì sao.** Cùng cơ chế mục 5.7.1: `remove(item)` lan sang `item.product`. Điểm khác là lần này khóa ngoại không còn dòng nào để chặn, nên câu DELETE product thành công. Việc xóa mất sản phẩm là hệ quả hoàn toàn "hợp lệ" từ góc nhìn của database, nhưng sai từ góc nhìn nghiệp vụ.

**Cách tránh.** Không đặt `REMOVE` (hoặc `ALL`) trên `@ManyToOne` trỏ tới dữ liệu dùng chung.

::: {.ghi-nho}
Cascade `REMOVE` trên `@ManyToOne` có thể **làm mất dữ liệu master mà không có lỗi**. Đây là lý do chính để không đặt cascade ở phía con trỏ tới thứ dùng chung.
:::

::: {.tracuu codes="G2-S08b"}
:::

#### 5.7.3 cascade PERSIST trên ManyToOne: product DETACHED gây lỗi {.cot-loi}

**Bối cảnh.** Request mang theo id của một product có sẵn. Code tải product ở một transaction trước (product thành DETACHED sau khi transaction đóng), rồi tạo item mới trỏ tới product và gọi `itemRepository.save(item)`. Lỗi nhận được là "detached entity passed to persist", một thông báo khiến nhiều người bối rối vì họ không hề persist product.

**Khi nào xảy ra.** `OrderItem.product` có `PERSIST` và product là DETACHED (có id nhưng không còn MANAGED).

**Khi nào không xảy ra.** Nếu product là MANAGED trong cùng transaction (mục 5.7.4 và 5.7.5), hoặc nếu không có cascade (mục 5.7.6).

**Ví dụ.**

```java
// detachedProduct: tải ở transaction trước, nay đã DETACHED.
assertThatThrownBy(() -> itemRepository.save(new OrderItem(detachedProduct, 1)))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .hasRootCauseInstanceOf(PersistentObjectException.class);
```

**Kết quả.** Exception gốc là `org.hibernate.PersistentObjectException` ("detached entity passed to persist"), Spring bọc thành `InvalidDataAccessApiUsageException`. Không có câu INSERT nào, bảng `order_item` trống.

**Vì sao.** `save(item)` với entity mới gọi `persist(item)`. Cascade `PERSIST` lan sang `item.product`, và Hibernate gọi `persist(product)`. Nhưng `persist` chỉ nhận entity TRANSIENT hoặc đã MANAGED; product có id mà không nằm trong persistence context hiện tại là DETACHED, và `persist` từ chối entity ở trạng thái đó.

**Cách tránh.** Có ba cách, xem ba mục tiếp theo. Cách ít phức tạp nhất là bỏ cascade (mục 5.7.6).

::: {.ghi-nho}
Cascade `PERSIST` trên `@ManyToOne` biến "gắn item vào product có sẵn" thành "persist product", và thất bại nếu product là DETACHED.
:::

::: {.tracuu codes="G2-S09a"}
:::

#### 5.7.4 Cách sửa 1: dùng getReferenceById trong cùng transaction {.cot-loi}

**Bối cảnh.** Bạn giữ cascade `PERSIST` (vì còn lý do khác để có nó) nhưng cần gắn item vào product có sẵn mà không phải SELECT product.

**Khi nào xảy ra.** Proxy do `getReferenceById` tạo ra là MANAGED trong persistence context hiện tại.

**Khi nào không xảy ra.** Nếu gọi `getReferenceById` ở một transaction khác rồi dùng ở transaction này, proxy đó sẽ DETACHED và quay lại lỗi mục 5.7.3.

**Ví dụ.**

```java
inTx(() -> {
    Product productReference = productRepository.getReferenceById(productId);
    itemRepository.save(new OrderItem(productReference, 1));
});
```

**Kết quả.** Chỉ có một câu INSERT `order_item`, với `product_id` đúng. Không có câu SELECT nào cho `product`.

**Vì sao.** `getReferenceById` trả về proxy MANAGED chỉ chứa id. Cascade `PERSIST` thấy product đã MANAGED nên bỏ qua. Cột `product_id` chỉ cần id, mà proxy trả lời được ngay mà không chạm database.

::: {.ghi-nho}
Proxy từ `getReferenceById` trong cùng transaction là MANAGED, nên qua được cascade `PERSIST` mà không tốn SELECT.
:::

::: {.tracuu codes="G2-S09b"}
:::

#### 5.7.5 Cách sửa 2: find product trong cùng transaction, tốn thêm một SELECT {.cot-loi}

**Bối cảnh.** Cùng mục tiêu như 5.7.4, nhưng bạn dùng `em.find` quen thuộc.

**Khi nào xảy ra.** Product được tải bằng `find` trong cùng transaction với `persist(item)`.

**Khi nào không xảy ra.** Nếu product được tải ở transaction khác.

**Ví dụ.**

```java
inTx(() -> {
    Product managedProduct = em.find(Product.class, productId);
    em.persist(new OrderItem(managedProduct, 1));
});
```

**Kết quả.** Một câu SELECT `product` và một câu INSERT `order_item`. Item được lưu thành công.

**Vì sao.** `find` trả về entity MANAGED, nên cascade `PERSIST` bỏ qua. Khác 5.7.4: có một câu SELECT thừa vì bạn tải cả dòng product chỉ để lấy id.

::: {.ghi-nho}
`find` cùng transaction cũng giải quyết lỗi, nhưng tốn một SELECT mà `getReferenceById` không cần.
:::

::: {.tracuu codes="G2-S09c"}
:::

#### 5.7.6 Cách sửa 3 (khuyến nghị): bỏ cascade, product DETACHED vẫn dùng được {.cot-loi}

**Bối cảnh.** Product là dữ liệu master đã có sẵn trong database. Item chỉ cần "trỏ tới" nó. Vậy vì sao phải có cascade?

**Khi nào xảy ra.** `@ManyToOne` không khai cascade; product là DETACHED hoặc MANAGED, đều được.

**Khi nào không xảy ra.** Nếu product thật sự chưa tồn tại (TRANSIENT), khi đó bạn phải `persist(product)` tường minh trước khi persist item.

**Ví dụ.**

```java
// detachedProduct: tải ở transaction trước, nay DETACHED.
inTx(() -> em.persist(new OrderItem(detachedProduct, 1)));
```

**Kết quả.** Chỉ một câu INSERT `order_item`, với `product_id` bằng id của product. Không có câu SELECT nào. Không lỗi.

**Vì sao.** Không cascade nghĩa là Hibernate không xử lý product theo bất kỳ cách nào. Để ghi cột `product_id`, nó chỉ cần đọc id của product; entity DETACHED vẫn có id, nên đủ. Cách này đơn giản nhất, nhanh nhất (0 SELECT), và cũng loại bỏ rủi ro xóa nhầm master ở mục 5.7.1 và 5.7.2.

::: {.ghi-nho}
Với `@ManyToOne` trỏ tới dữ liệu có sẵn: **không cascade**. Hibernate chỉ cần id để ghi khóa ngoại, entity MANAGED hay DETACHED đều được.
:::

::: {.tracuu codes="G2-S09d"}
:::

### 5.8 Tổng kết: thao tác nào lan khi nào

Bảng dưới đây trả lời câu hỏi "thao tác này trên order có lan xuống item không". Ô "Không có tình huống" nghĩa là lab không có test riêng cho tổ hợp đó, nên bảng không khẳng định gì (đừng suy từ tên cascade: mục 5.4.3 cho thấy suy đoán rất dễ sai).

| Thao tác trên order | Không cascade | Chỉ `PERSIST` | Chỉ `MERGE` | Chỉ `REMOVE` | `ALL` | `PERSIST` và `MERGE` |
|---|---|---|---|---|---|---|
| `persist(order)` | Item bị bỏ qua, không lỗi (5.2.1) | Item được INSERT (5.3.1) | Không có tình huống | Không có tình huống | Có (5.3.3) | Không có tình huống |
| Thêm item vào order MANAGED, commit | Không có tình huống | Item được INSERT lúc flush (5.3.2) | Không có tình huống | Không có tình huống | Không có tình huống | Không có tình huống |
| `merge(order DETACHED)` | Thay đổi item mất (5.4.1) | Item cũ không cập nhật, item mới thành dòng ma (5.4.3) | UPDATE cũ, INSERT mới (5.4.2) | Không có tình huống | Không có tình huống | Không có tình huống |
| `remove(order)` còn item | Vi phạm khóa ngoại (5.5.1) | Không có tình huống | Không có tình huống | SELECT, DELETE từng item rồi order (5.5.2) | Không có tình huống | Không có tình huống |
| `detach(order)` | Không có tình huống | Không có tình huống | Không có tình huống | Không có tình huống | Item cũng DETACHED (5.6.1) | Item vẫn MANAGED, vẫn UPDATE (5.6.2) |
| `refresh(order)` | Không có tình huống | Không có tình huống | Không có tình huống | Không có tình huống | Item nạp lại, thay đổi mất (5.6.3) | Item giữ thay đổi, vẫn UPDATE (5.6.4) |

**Cascade trên collection của cha hay trên `@ManyToOne` của con:**

| | Trên collection của cha (`items`) | Trên `@ManyToOne` của con (`product`) |
|---|---|---|
| Ý nghĩa | Vòng đời con phụ thuộc cha | Con điều khiển vòng đời của thứ nó trỏ tới (thường sai) |
| `PERSIST` | Hợp lý (5.3) | `persist` thất bại với product DETACHED; nên bỏ cascade (5.7) |
| `REMOVE` | Hợp lý (5.5.2) | Xóa product dùng chung, hoặc mất master lặng lẽ (5.7.1, 5.7.2) |

**Cascade JPA hay `@OnDelete`:**

| | `cascade = REMOVE` | `@OnDelete(CASCADE)` |
|---|---|---|
| Ai xóa item | Hibernate, từng dòng | PostgreSQL, qua khóa ngoại |
| Xóa order 3 item | 2 SELECT, 3 DELETE item, 1 DELETE order | 1 SELECT, 1 DELETE order |
| Entity item đã tải | Chuyển REMOVED | Vẫn MANAGED dù dòng đã mất |
| Khi nào dùng | Cần callback hoặc listener; aggregate nhỏ | Xóa hàng loạt, chấp nhận bộ nhớ có thể lệch |

Chương tiếp theo bàn về `orphanRemoval`: khi cha vẫn còn nhưng con bị gỡ khỏi collection thì dòng của con ra sao.
