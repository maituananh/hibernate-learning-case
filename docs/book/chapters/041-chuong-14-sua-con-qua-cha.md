## Chương 14. Sửa bảng con thông qua bảng cha

Hầu hết code nghiệp vụ không sửa `order_item` trực tiếp. Nó tải đơn hàng, đi qua `order.getItems()`, sửa một dòng hàng, thêm một dòng, bỏ một dòng, rồi để transaction commit. Câu hỏi của chương này đơn giản mà hay bị hiểu sai: **trong lúc đó, dòng của bảng cha `purchase_order` có bị đụng tới không?** Đụng tới nghĩa là có câu `UPDATE purchase_order`, cột `version` tăng, cột `updated_at` đổi, callback `@PreUpdate` chạy.

Câu trả lời quyết định nhiều thứ ngoài đời thực: hai người cùng sửa hai dòng hàng khác nhau của một đơn có bị báo xung đột không, cột "ngày cập nhật cuối" của đơn có phản ánh đúng không, và mỗi lần sửa tốn bao nhiêu câu SQL.

Chương này dùng một mô hình quen thuộc: `PurchaseOrder` (có `version`, `status`, `updated_at`) một-nhiều `OrderItem` (`quantity`, `unit_price`), mỗi `OrderItem` nhiều-một `Product`, và `PurchaseOrder` nhiều-một `Customer`.

![Sơ đồ bảng của mô hình dùng trong chương: customer, purchase_order, order_item và product](img/g4-u0.png){width=85%}

Hai ý lớn xuyên suốt chương:

1. **Dirty checking chạy riêng cho từng entity.** Sửa `item.quantity` làm *item* "dirty", còn *order* thì không. Collection `mappedBy` là inverse side, không phải state của order (mục 14.2 và 14.3).
2. **Collection phía owning thì khác.** Nếu collection tự ghi bảng nối thì nó thuộc state của cha: đổi nó làm cha dirty, và `version` tăng (mục 14.4).

Trong chương này:

- Cách chứng minh "bảng cha không đổi" (mục 14.1).
- Sửa con qua cha, có và không có cascade MERGE (mục 14.2).
- Thêm và xóa con qua collection `mappedBy` (mục 14.3).
- Collection phía owning và `@OptimisticLock(excluded = true)` (mục 14.4).
- Chủ động tăng version của cha (mục 14.5).
- `@DynamicUpdate` (mục 14.6).
- Đi qua cha hay đi thẳng repository của con (mục 14.7).
- Cập nhật đơn hàng từ request: cách đúng và cách làm mất dữ liệu (mục 14.8).
- Chuyển item giữa hai đơn và tính lại tổng (mục 14.9).

**Trong JPA thuần.** Phần lớn chương này là JPA chuẩn: dirty checking, cascade, `@Version`, `LockModeType`. Hai chỗ dùng annotation riêng của Hibernate là `@OptimisticLock` (mục 14.4) và `@DynamicUpdate` (mục 14.6); hai mục đó có đoạn **Trong JPA thuần.**

### 14.1 Cách kiểm chứng "bảng cha không đổi" {.kien-thuc-nen}

Để khẳng định "dòng cha không đổi", chỉ đếm câu SQL là chưa đủ. Mỗi test trong chương dùng hai bằng chứng độc lập:

1. **Snapshot dòng cha bằng JDBC.** Trước khi làm gì, test đọc **mọi cột** của dòng `purchase_order` (kể cả `version` và `updated_at`) thành một bản chụp. Sau thao tác, test đọc lại và so hai bản chụp bằng nhau. Đọc bằng JDBC chứ không bằng entity, vì entity trong persistence context có thể "nói dối" (nó giữ giá trị cũ dù database đã đổi).
2. **Đếm câu UPDATE.** Test ghi lại mọi câu SQL Hibernate chạy, rồi khẳng định số câu `UPDATE purchase_order` bằng 0.

Hai bằng chứng bổ sung cho nhau. Số câu UPDATE bằng 0 cho biết Hibernate không gửi gì. Snapshot giống hệt cho biết không có thứ gì khác (trigger, giá trị mặc định) lén đổi dòng cha.

Bạn có thể áp dụng cùng cách này vào dự án của mình khi muốn kiểm chứng một mapping: viết test, chụp dòng trước, thao tác, chụp dòng sau.

### 14.2 Sửa con qua cha: có và không có cascade MERGE {.bien-the}

Baseline của chương là mapping hai chiều quen thuộc: `OrderItem.order` là owning side (giữ khóa ngoại `order_id`), `PurchaseOrder.items` là inverse side với `cascade = ALL` và `orphanRemoval = true`.

```java
@Entity(name = "G4U0_PurchaseOrder")
@Table(schema = "g4_u0_baseline", name = "purchase_order")
public class U0PurchaseOrder {

    @Id @GeneratedValue
    private Long id;

    @Column(name = "status", nullable = false)
    private String status;

    // Hibernate tự tăng cột này mỗi khi dòng purchase_order bị UPDATE.
    @Version
    @Column(name = "version")
    private Long version;

    // Hibernate tự gán thời điểm hiện tại mỗi khi order được UPDATE.
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // INVERSE side: khóa ngoại order_item.order_id do OrderItem.order quản lý.
    // Vì là inverse nên collection này KHÔNG phải state của cha.
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    private List<U0OrderItem> items = new ArrayList<>();
}

@Entity(name = "G4U0_OrderItem")
@Table(schema = "g4_u0_baseline", name = "order_item")
public class U0OrderItem {

    // OWNING side: khóa ngoại order_id nằm ở bảng này.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private U0PurchaseOrder order;

    @Column(name = "quantity", nullable = false)
    private int quantity;
}
```

Biến thể đối chứng chỉ khác đúng một điểm: `items` dùng `cascade = PERSIST` (bỏ `MERGE`, vẫn giữ `orphanRemoval = true`). Điểm khác này chỉ lộ ra khi bạn lưu một đồ thị DETACHED (mục 14.2.3).

![Sơ đồ bảng của baseline: customer, purchase_order, order_item và product](img/g4-u0.png){width=85%}

**Ưu điểm.** Cascade `ALL` (có `MERGE`) cho phép `save(order)` mang theo mọi thay đổi của các dòng hàng, nên service chỉ cần làm việc với một aggregate. Vì `items` là inverse side nên sửa dòng hàng không làm đơn hàng bị UPDATE.

**Nhược điểm.** Thiếu `MERGE` thì thay đổi của dòng hàng bị mất **im lặng**, không lỗi nào báo (mục 14.2.3). Mà thiếu `MERGE` là điều dễ xảy ra khi bạn tự tay liệt kê cascade và quên một giá trị.

**Nên dùng khi.** Dùng `cascade = ALL` cho con thuộc sở hữu hoàn toàn của cha (dòng hàng của đơn). Nếu có lý do liệt kê riêng, hãy liệt kê `MERGE` và viết test chứng minh thay đổi của con được lưu.

#### 14.2.1 Sửa quantity của item qua đơn đang MANAGED: chỉ UPDATE order_item {.cot-loi}

**Bối cảnh.** Nhân viên bán hàng đổi số lượng bút trong đơn A-001 từ 1 lên 5. Code tải đơn, lấy dòng hàng đầu tiên, gọi `setQuantity(5)`. Câu hỏi: dòng `purchase_order` có bị ghi lại không?

**Khi nào xảy ra.** Đơn hàng đã MANAGED trong một transaction, và `items` là inverse side (`mappedBy`).

**Khi nào không xảy ra.** Nếu bạn tự đổi một field của chính đơn (ví dụ `status`) trong cùng transaction thì đơn bị UPDATE (xem mục 14.3.3 để đối chứng).

**Ví dụ.**

```java
inTx(() -> {
    U0PurchaseOrder orderA = em.find(U0PurchaseOrder.class, orderId);
    U0OrderItem firstItem = orderA.getItems().get(0);
    firstItem.setQuantity(5);
    // Cả hai đều MANAGED; chỉ item "dirty", order thì không.
});
```

**Kết quả.** Hibernate chạy hai câu SELECT (đơn, rồi toàn bộ dòng hàng của đơn) và **đúng một** câu UPDATE, vào bảng `order_item`. Không có câu `UPDATE purchase_order` nào.

```sql
select upo1_0.id, upo1_0.customer_id, upo1_0.order_no, upo1_0.status,
    upo1_0.updated_at, upo1_0.version
from g4_u0_baseline.purchase_order upo1_0
where upo1_0.id=?

select i1_0.order_id, i1_0.id, i1_0.product_id, i1_0.quantity, i1_0.unit_price
from g4_u0_baseline.order_item i1_0
where i1_0.order_id=?

update g4_u0_baseline.order_item
set order_id=?, product_id=?, quantity=?, unit_price=?
where id=?
```

Dữ liệu `order_item`:

| Bảng order_item | quantity (bút) | quantity (sách) |
|---|---|---|
| Trước | 1 | 2 |
| Sau | **5** | 2 |

Thay đổi: quantity của dòng bút. Giữ nguyên: dòng sách, và **toàn bộ dòng `purchase_order`**, kể cả `version` và `updated_at` (snapshot trước và sau giống hệt nhau).

**Vì sao.** Lúc flush, Hibernate chạy dirty checking riêng cho từng entity MANAGED: so state hiện tại với bản chụp lúc tải. Với dòng hàng, `quantity` khác bản chụp nên nó dirty. Với đơn hàng, các field của chính nó không đổi, còn `items` là inverse side nên **không được tính vào state ghi xuống bảng cha**. Đơn không dirty, nên không có UPDATE, `version` giữ nguyên, `updated_at` giữ nguyên.

Để ý thêm một chi tiết: câu UPDATE liệt kê **cả bốn cột** của `order_item` dù chỉ `quantity` đổi. Đó là hành vi mặc định của Hibernate; mục 14.6 nói về cách thay đổi.

::: {.ghi-nho}
Sửa con qua cha chỉ làm con dirty. Cha không bị UPDATE vì collection `mappedBy` không phải state của cha.
:::

::: {.tracuu codes="G4-S01"}
:::

#### 14.2.2 save(order DETACHED) với item đã sửa: merge, vẫn chỉ UPDATE order_item {.cot-loi}

**Bối cảnh.** Trong ứng dụng web, đơn hàng được tải ở request thứ nhất (rồi transaction đóng), người dùng sửa trên màn hình, và request thứ hai gọi `orderRepository.save(order)`. Lúc này đơn và các dòng hàng là DETACHED.

**Khi nào xảy ra.** Bạn gọi `save()` của Spring Data trên entity đã có id và `version`. Spring Data coi đó là entity đã tồn tại và gọi `merge()` (entity mới, chưa có id, mới gọi `persist()`).

**Khi nào không xảy ra.** Nếu `items` không cascade `MERGE` thì thay đổi của dòng hàng không được copy (mục 14.2.3).

**Ví dụ.**

```java
// Transaction 1: tải cả đồ thị rồi đóng transaction.
U0PurchaseOrder detachedOrder = inTxReturning(() -> {
    U0PurchaseOrder loadedOrder = em.find(U0PurchaseOrder.class, orderId);
    loadedOrder.getItems().size(); // khởi tạo collection LAZY trước khi đóng
    return loadedOrder;
});
// Ngoài transaction: đơn và item là DETACHED. Sửa trên bản DETACHED.
detachedOrder.getItems().get(0).setQuantity(5);

// Transaction 2: save = merge.
inTx(() -> u0OrderRepository.save(detachedOrder));
```

**Kết quả.** `merge()` đọc lại đơn cùng các dòng hàng bằng **một câu SELECT có left join**, copy state từ bản DETACHED sang bản MANAGED vừa đọc, rồi lúc commit chỉ UPDATE dòng hàng nào khác bản trong database.

```sql
select upo1_0.id, upo1_0.customer_id, upo1_0.order_no, upo1_0.status,
    upo1_0.updated_at, upo1_0.version, i1_0.order_id, i1_0.id, i1_0.product_id,
    i1_0.quantity, i1_0.unit_price
from g4_u0_baseline.purchase_order upo1_0
left join g4_u0_baseline.order_item i1_0 on upo1_0.id=i1_0.order_id
where upo1_0.id=?

update g4_u0_baseline.order_item
set order_id=?, product_id=?, quantity=?, unit_price=?
where id=?
```

Dữ liệu `order_item`:

| Bảng order_item | quantity (bút) | quantity (sách) |
|---|---|---|
| Trước | 1 | 2 |
| Sau | **5** | 2 |

Thay đổi: quantity của dòng bút. Giữ nguyên: dòng sách và toàn bộ dòng `purchase_order` (đơn không có field nào đổi nên không có UPDATE, `version` giữ nguyên).

**Vì sao.** `merge()` không biến bản DETACHED thành MANAGED. Nó **tải bản trong database** (MANAGED), rồi copy state của bản DETACHED lên đó. Cascade `MERGE` lan từ đơn xuống từng dòng hàng, nên state của dòng hàng cũng được copy. Sau đó dirty checking so từng bản MANAGED với bản chụp lúc tải: chỉ dòng bút khác, nên chỉ có một câu UPDATE.

::: {.ghi-nho}
`save()` trên entity đã có id là `merge()`. Merge tải lại đồ thị từ database rồi copy state vào, nên vẫn chỉ UPDATE phần thật sự khác.
:::

::: {.tracuu codes="G4-S02"}
:::

#### 14.2.3 Không cascade MERGE: thay đổi của item biến mất im lặng {.cot-loi}

**Bối cảnh.** Một đồng nghiệp viết `cascade = CascadeType.PERSIST` cho `items` vì "chỉ cần lưu khi tạo đơn". Mọi thứ chạy tốt ở màn hình tạo đơn. Về sau, màn hình sửa đơn gọi `save(order)` với dòng hàng đã sửa: giao diện báo thành công, nhưng mở lại đơn thì số lượng vẫn như cũ.

**Khi nào xảy ra.** Đồ thị DETACHED được lưu bằng `merge()` (qua `save()`), và `items` không có `MERGE` trong danh sách cascade.

**Khi nào không xảy ra.** Nếu cascade có `MERGE` (hoặc `ALL`), hoặc nếu bạn sửa dòng hàng khi nó đang MANAGED (mục 14.2.1).

**Ví dụ.**

```java
// cascade = PERSIST, không có MERGE. Các bước còn lại giống mục 14.2.2.
detachedOrder.getItems().get(0).setQuantity(5);
inTx(() -> u1OrderRepository.save(detachedOrder));
```

**Kết quả.** Không có câu UPDATE nào, không có lỗi. Hibernate chỉ chạy hai câu SELECT (đơn, rồi các dòng hàng của đơn).

```sql
select upo1_0.id, upo1_0.customer_id, upo1_0.order_no, upo1_0.status,
    upo1_0.updated_at, upo1_0.version
from g4_u1_no_merge.purchase_order upo1_0
where upo1_0.id=?

select i1_0.order_id, i1_0.id, i1_0.product_id, i1_0.quantity, i1_0.unit_price
from g4_u1_no_merge.order_item i1_0
where i1_0.order_id=?
```

Dữ liệu `order_item`:

| Bảng order_item | quantity (bút) | quantity (sách) |
|---|---|---|
| Trước | 1 | 2 |
| Sau | 1 | 2 |

Thay đổi: không có. Giữ nguyên: mọi dòng, cả `purchase_order`. Giá trị 5 mà người dùng nhập đã **biến mất** mà không ai được báo.

**Vì sao.** Merge tải đơn và các dòng hàng vào persistence context, nhưng chỉ copy state **dọc theo những quan hệ có cascade MERGE**. Không có `MERGE` thì Hibernate dừng ở đơn, không copy gì xuống các dòng hàng. Các dòng hàng vừa tải vẫn giữ `quantity` cũ nên không dirty, và không có UPDATE. Không có exception nào, vì với Hibernate đây là cách dùng hợp lệ.

**Cách tránh.** Viết `cascade = ALL` cho con thuộc sở hữu của cha. Nếu cố ý liệt kê, hãy liệt kê cả `MERGE`. Và với mọi chức năng "sửa rồi lưu", có một test mở lại dữ liệu bằng JDBC để chứng minh giá trị mới thật sự vào database.

::: {.ghi-nho}
"Lưu cha thì con tự lưu" chỉ đúng khi cascade có `MERGE`. Thiếu nó, thay đổi của con mất im lặng.
:::

::: {.tracuu codes="G4-S03"}
:::

### 14.3 Thêm và xóa con qua collection mappedBy

Mục 14.2 sửa một dòng hàng có sẵn. Mục này thêm dòng mới và bỏ dòng cũ, vẫn qua collection `mappedBy` của baseline, rồi xem `version` và callback của đơn.

#### 14.3.1 Thêm item mới qua đơn: chỉ INSERT order_item, version của đơn không tăng {.cot-loi}

**Bối cảnh.** Khách đặt thêm một cuốn sách vào đơn đã có. Nếu hai nhân viên cùng thêm hai món khác nhau vào một đơn, bạn muốn họ **không** bị báo xung đột. Điều đó chỉ đúng nếu thêm dòng hàng không làm `version` của đơn tăng.

**Khi nào xảy ra.** Thêm dòng hàng bằng helper `order.addItem(item)` vào collection `mappedBy` của đơn đang MANAGED.

**Khi nào không xảy ra.** Nếu collection là phía owning, có bảng nối (mục 14.4), thì version của đơn tăng.

**Ví dụ.**

```java
inTx(() -> {
    U0PurchaseOrder orderA = em.find(U0PurchaseOrder.class, orderId);
    U0Product productBook = em.find(U0Product.class, bookProductId);
    U0OrderItem itemBook = new U0OrderItem(productBook, 3, new BigDecimal("20.00"));
    orderA.addItem(itemBook);
    // Lúc flush: cascade PERSIST lan xuống itemBook, sinh INSERT.
});
```

**Kết quả.** Một câu INSERT vào `order_item`, không có UPDATE nào cho `purchase_order`.

```sql
select upo1_0.id, upo1_0.customer_id, upo1_0.order_no, upo1_0.status,
    upo1_0.updated_at, upo1_0.version
from g4_u0_baseline.purchase_order upo1_0
where upo1_0.id=?

select up1_0.id, up1_0.name
from g4_u0_baseline.product up1_0
where up1_0.id=?

insert into g4_u0_baseline.order_item (order_id, product_id, quantity, unit_price,
    id)
values (?, ?, ?, ?, ?)
```

| Bảng | Trước | Sau |
|---|---|---|
| order_item (số dòng) | 1 | **2** |
| purchase_order.version | 0 | 0 |

Thay đổi: có thêm một dòng `order_item`. Giữ nguyên: toàn bộ dòng `purchase_order`, kể cả `version` và `updated_at`.

**Vì sao.** `addItem` làm hai việc: thêm vào collection `items` (inverse side, Hibernate bỏ qua khi ghi) và gán `item.order` (owning side, quyết định khóa ngoại). Lúc flush, cascade `PERSIST` thấy một entity mới trong collection và INSERT nó với `order_id` lấy từ `item.order`. Đơn hàng không có field nào của chính nó đổi, nên không dirty.

::: {.ghi-nho}
Thêm con vào collection `mappedBy` chỉ INSERT con. Version của cha không tăng.
:::

::: {.tracuu codes="G4-S04"}
:::

#### 14.3.2 Xóa item qua đơn bằng orphanRemoval: chỉ DELETE order_item {.cot-loi}

**Bối cảnh.** Khách bỏ một món khỏi đơn. Code gọi `order.removeItem(item)`. Cũng như khi thêm, câu hỏi là đơn có bị đụng không.

**Khi nào xảy ra.** Collection `mappedBy` có `orphanRemoval = true`, và bạn gỡ phần tử khỏi collection của đơn đang MANAGED.

**Khi nào không xảy ra.** Nếu không có `orphanRemoval`, việc gỡ khỏi collection chỉ đặt `order_id` thành NULL (xem Chương 6).

**Ví dụ.**

```java
inTx(() -> {
    U0PurchaseOrder orderA = em.find(U0PurchaseOrder.class, orderId);
    orderA.removeItem(orderA.getItems().get(0));
    // Lúc flush: item không còn trong collection, bị coi là mồ côi và bị DELETE.
});
```

**Kết quả.** Một câu DELETE vào `order_item`, không có UPDATE nào cho `purchase_order`. Bảng `order_item` từ một dòng còn không dòng nào; dòng `purchase_order` giữ nguyên mọi cột.

**Vì sao.** Cũng như thêm: collection `items` là inverse side nên việc nó đổi không làm đơn dirty. `orphanRemoval` chỉ làm một việc là lên lịch DELETE cho phần tử bị gỡ.

::: {.ghi-nho}
Xóa con qua `orphanRemoval` chỉ DELETE con. Cha giữ nguyên.
:::

::: {.tracuu codes="G4-S05"}
:::

#### 14.3.3 Callback và updated_at của đơn chỉ chạy khi chính đơn đổi {.mo-rong}

**Bối cảnh.** Đơn có cột `updated_at` (dùng `@UpdateTimestamp`) và callback `@PreUpdate` (ví dụ ghi audit). Bạn kỳ vọng "ngày cập nhật cuối" của đơn thay đổi mỗi khi dòng hàng đổi. Thực tế thì sao?

**Khi nào xảy ra.** Chỉ dòng hàng đổi, đơn hàng không dirty: `@PreUpdate` của đơn không chạy, `updated_at` không đổi.

**Khi nào không xảy ra.** Khi chính đơn đổi (ví dụ `status`), thì callback chạy và `updated_at` đổi.

**Ví dụ.**

```java
// Bước 1: chỉ sửa item.
inTx(() -> {
    U0PurchaseOrder orderA = em.find(U0PurchaseOrder.class, orderId);
    orderA.getItems().get(0).setQuantity(5);
});

// Bước 2 (đối chứng): sửa field của chính đơn.
inTx(() -> {
    U0PurchaseOrder orderA = em.find(U0PurchaseOrder.class, orderId);
    orderA.setStatus("PAID");
});
```

**Kết quả.** Sau bước 1, counter đếm số lần `@PreUpdate` chạy vẫn là **0**, và `updated_at` giữ nguyên. Sau bước 2, counter là **1**, `updated_at` đổi và `version` tăng thêm 1.

**Vì sao.** `@PreUpdate` và `@UpdateTimestamp` đều chỉ chạy khi Hibernate quyết định UPDATE **dòng của entity đó**. Dirty checking của đơn nói "không đổi" ở bước 1, nên không có UPDATE, và do đó không callback nào chạy.

**Cách tránh.** Nếu nghiệp vụ cần "ngày cập nhật cuối" của đơn phản ánh cả thay đổi của dòng hàng, hãy tự gán tường minh (ví dụ gọi `order.touch()` trong service) hoặc dùng `OPTIMISTIC_FORCE_INCREMENT` (mục 14.5). Đừng trông chờ Hibernate làm hộ.

::: {.ghi-nho}
Callback của cha chỉ chạy khi cha dirty. Con đổi không đủ để kích hoạt chúng.
:::

::: {.tracuu codes="G4-S08"}
:::

### 14.4 Collection phía owning và OptimisticLock excluded {.bien-the}

Mục 14.3 cho thấy collection `mappedBy` không làm cha dirty. Biến thể này đổi đúng một điều: `items` trở thành `@OneToMany` **một chiều** với `@JoinTable`, nên `OrderItem` không còn khóa ngoại `order_id`, và collection `items` là **owning side**, tự ghi bảng nối `purchase_order_items`.

```java
@Entity(name = "G4U2_PurchaseOrder")
@Table(schema = "g4_u2_owning_collection", name = "purchase_order")
public class U2PurchaseOrder {

    @Version
    @Column(name = "version")
    private Long version;

    // OWNING side: collection này tự ghi bảng nối purchase_order_items.
    // Vì thuộc state của cha nên đổi nó được tính là "cha đã đổi".
    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL,
               orphanRemoval = true)
    @JoinTable(schema = "g4_u2_owning_collection", name = "purchase_order_items",
        joinColumns = @JoinColumn(name = "purchase_order_id"),
        inverseJoinColumns = @JoinColumn(name = "item_id"))
    private List<U2OrderItem> items = new ArrayList<>();
}
```

![Sơ đồ bảng của biến thể collection owning: purchase_order, order_item và bảng nối purchase_order_items](img/g4-u2.png){width=95%}

**Ưu điểm.** Mô hình đơn giản khi dòng hàng không cần biết đơn của mình (không có `OrderItem.order`). Và nếu bạn **muốn** mọi thay đổi trong danh sách dòng hàng tính là thay đổi của đơn (để phát hiện xung đột bằng `@Version`), thì mapping này cho bạn điều đó miễn phí.

**Nhược điểm.** Mỗi lần đổi danh sách, Hibernate **xóa hết dòng nối của đơn rồi ghi lại toàn bộ** (với `List` không có `@OrderColumn`), và `version` của đơn tăng, nên hai người thêm hai món khác nhau vào cùng một đơn sẽ xung đột nhau. Thêm một bảng nối cũng có nghĩa là thêm một `JOIN` cho mỗi lần tải danh sách.

**Nên dùng khi.** Hiếm khi là lựa chọn đầu tiên cho quan hệ một-nhiều. Chỉ dùng khi dòng con thật sự không cần trỏ ngược về cha, hoặc bảng nối có sẵn trong schema cũ. Khi đó, nếu không muốn `version` của cha tăng, dùng `@OptimisticLock(excluded = true)`.

#### 14.4.1 Thêm item vào collection owning: version của đơn tăng, bảng nối bị ghi lại {.cot-loi}

**Bối cảnh.** Cùng thao tác "thêm một cuốn sách vào đơn" như mục 14.3.1, nhưng trên mapping có bảng nối. Bạn kỳ vọng kết quả giống: chỉ INSERT dòng hàng mới. Nhưng ở đây hai nhân viên cùng thêm hai món khác nhau sẽ đụng `OptimisticLockException`.

**Khi nào xảy ra.** Collection là owning side (`@OneToMany` một chiều kèm `@JoinTable`), và cha có `@Version`.

**Khi nào không xảy ra.** Khi collection là `mappedBy` (mục 14.3.1), hoặc khi bạn thêm `@OptimisticLock(excluded = true)` (mục 14.4.2).

**Ví dụ.**

```java
inTx(() -> {
    U2PurchaseOrder orderA = em.find(U2PurchaseOrder.class, orderId);
    U2Product productBook = em.find(U2Product.class, bookProductId);
    orderA.addItem(new U2OrderItem(productBook, 3, new BigDecimal("20.00")));
    // Lúc flush: collection owning đổi, cha dirty, version của cha tăng.
});
```

**Kết quả.** Có câu `UPDATE purchase_order` kèm `version`, một INSERT dòng hàng mới, và bảng nối bị **xóa hết rồi ghi lại cả hai dòng**.

```sql
insert into g4_u2_owning_collection.order_item (product_id, quantity, unit_price,
    id)
values (?, ?, ?, ?)

update g4_u2_owning_collection.purchase_order
set customer_id=?, order_no=?, status=?, updated_at=?, version=?
where id=? and version=?

delete
from g4_u2_owning_collection.purchase_order_items
where purchase_order_id=?

insert into g4_u2_owning_collection.purchase_order_items (purchase_order_id, item_id)
values (?, ?)

insert into g4_u2_owning_collection.purchase_order_items (purchase_order_id, item_id)
values (?, ?)
```

| Dữ liệu | Trước | Sau |
|---|---|---|
| purchase_order.version | 0 | **1** |
| purchase_order_items (số dòng) | 1 | **2** (xóa hết rồi ghi lại cả dòng cũ lẫn dòng mới) |

Thay đổi: `version` của đơn tăng 1, bảng nối có thêm một dòng (sau khi bị ghi lại). Giữ nguyên: dòng hàng bút và các cột nghiệp vụ của đơn.

::: {.khac-du-doan codes="G4-S06"}
**Điều nhiều người nghĩ:** thêm một item vào collection owning chỉ INSERT thêm đúng một dòng vào bảng nối.

**Thực tế:** version của đơn tăng đúng như dự đoán, nhưng bảng nối **không** chỉ nhận thêm một dòng. Hibernate xóa toàn bộ dòng nối của đơn rồi ghi lại cả hai (collection kiểu `List` không có `@OrderColumn` bị recreate).

Đã kiểm chứng trên Hibernate 7.4.15.Final.
:::

**Vì sao.** Collection owning là một phần state của đơn, nên khi nó đổi, dirty checking coi đơn là dirty và sinh `UPDATE purchase_order ... where version=?` kèm tăng `version`. Còn với `List` không có `@OrderColumn`, Hibernate không biết phần tử nào mới, phần tử nào cũ theo vị trí, nên chọn cách an toàn: xóa sạch rồi ghi lại.

**Cách tránh.** Nếu danh sách dòng hàng có thể lớn hoặc nhiều người cùng sửa, ưu tiên mapping hai chiều `mappedBy` (mục 14.3). Nếu buộc phải giữ bảng nối, dùng `Set` hoặc thêm `@OrderColumn` để giảm việc ghi lại (Chương 4 và 8).

::: {.ghi-nho}
Collection owning là state của cha: đổi nó làm cha dirty và tăng `version`, và `List` không thứ tự bị ghi lại toàn bộ.
:::

::: {.tracuu codes="G4-S06"}
:::

#### 14.4.2 OptimisticLock excluded: tắt việc tăng version nhưng bảng nối vẫn bị ghi lại {.cot-loi}

**Bối cảnh.** Bạn giữ bảng nối nhưng không muốn mỗi lần thêm dòng hàng lại làm `version` của đơn tăng và gây xung đột oan.

**Khi nào xảy ra.** Collection owning được đánh dấu `@OptimisticLock(excluded = true)`.

**Khi nào không xảy ra.** Thiếu annotation đó thì `version` tăng như mục 14.4.1. Annotation chỉ tắt việc tăng version, không đổi cách Hibernate ghi bảng nối.

**Ví dụ.**

```java
// Chỉ khác biến thể ở mục 14.4.1 đúng một dòng: @OptimisticLock.
@OptimisticLock(excluded = true)
@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL,
           orphanRemoval = true)
@JoinTable(schema = "g4_u3_optimistic_excluded", name = "purchase_order_items",
    joinColumns = @JoinColumn(name = "purchase_order_id"),
    inverseJoinColumns = @JoinColumn(name = "item_id"))
private List<U3OrderItem> items = new ArrayList<>();
```

**Kết quả.** Vẫn có một INSERT dòng hàng, một DELETE và hai INSERT vào bảng nối, nhưng **không còn** câu `UPDATE purchase_order`.

| Dữ liệu | Trước | Sau |
|---|---|---|
| purchase_order.version | 0 | 0 |
| purchase_order_items (số dòng) | 1 | **2** |

Thay đổi: bảng nối có đủ hai dòng. Giữ nguyên: toàn bộ dòng `purchase_order`, kể cả `version`.

**Vì sao.** `@OptimisticLock(excluded = true)` bảo Hibernate: thay đổi của thuộc tính này **không tính** là "entity đã đổi" khi quyết định UPDATE và tăng version. Bảng nối vẫn phải ghi, vì đó là việc của collection owning; annotation không can thiệp vào đó.

**Trong JPA thuần.** JPA không có annotation tương đương cho từng thuộc tính. Cách làm gần nhất là đổi mapping: dùng quan hệ hai chiều với `mappedBy` (mục 14.3), vì khi đó collection không phải state của cha. Nếu buộc giữ bảng nối thì với JPA thuần không có cách tắt việc tăng version.

::: {.ghi-nho}
`@OptimisticLock(excluded = true)` chỉ tắt việc tăng version của cha. Nó không đổi cách bảng nối bị ghi.
:::

::: {.tracuu codes="G4-S07"}
:::

### 14.5 Chủ động tăng version của cha

Có lúc bạn muốn điều ngược lại: dù chỉ sửa con, `version` của cha phải tăng, vì cha là **aggregate root** và mọi thay đổi trong aggregate đều phải được xem là thay đổi của nó (ví dụ để phát hiện hai người cùng sửa hai dòng hàng của một đơn).

#### 14.5.1 OPTIMISTIC_FORCE_INCREMENT: tăng version dù cha không đổi gì {.mo-rong}

**Bối cảnh.** Hệ thống chốt đơn tính tổng dựa trên mọi dòng hàng. Nếu ai đó sửa một dòng hàng trong lúc người khác đang chốt đơn, bạn muốn bên chốt đơn bị báo xung đột. Muốn vậy `version` của đơn phải tăng mỗi khi bất kỳ dòng hàng nào đổi.

**Khi nào xảy ra.** Bạn tải đơn bằng `LockModeType.OPTIMISTIC_FORCE_INCREMENT`.

**Khi nào không xảy ra.** Tải bằng `find` thường thì `version` không tăng (mục 14.2.1).

**Ví dụ.**

```java
inTx(() -> {
    U0PurchaseOrder orderA = em.find(U0PurchaseOrder.class, orderId,
        LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    orderA.getItems().get(0).setQuantity(5);
    // Lúc flush: item dirty gây UPDATE order_item; cha bị buộc tăng version.
});
```

**Kết quả.** Hai câu UPDATE: một cho `order_item`, và một cho `purchase_order` **chỉ đặt `version`**, không đổi cột nào khác.

```sql
update g4_u0_baseline.order_item
set order_id=?, product_id=?, quantity=?, unit_price=?
where id=?

update g4_u0_baseline.purchase_order
set version=?
where id=? and version=?
```

| Dữ liệu | Trước | Sau |
|---|---|---|
| order_item.quantity (bút) | 1 | **5** |
| purchase_order.version | 0 | **1** |

Thay đổi: quantity của dòng bút và `version` của đơn. Giữ nguyên: mọi cột khác của đơn, kể cả `status`.

**Vì sao.** Chế độ khóa này bảo Hibernate: "dù entity này không đổi, hãy tăng version của nó lúc flush". Hibernate thêm một câu UPDATE chỉ tăng `version`, kèm điều kiện `where version=?` nên nếu ai khác đã tăng trước đó thì giao dịch này thất bại.

::: {.ghi-nho}
`OPTIMISTIC_FORCE_INCREMENT` cho phép bạn chủ động tăng version của aggregate root khi chỉ con đổi.
:::

::: {.tracuu codes="G4-S09"}
:::

### 14.6 DynamicUpdate {.bien-the}

Ở mục 14.2.1 bạn đã thấy câu UPDATE liệt kê mọi cột dù chỉ một cột đổi. Biến thể này đổi đúng một điểm: `OrderItem` thêm `@DynamicUpdate`.

```java
@Entity(name = "G4U4_OrderItem")
@Table(schema = "g4_u4_dynamic_update", name = "order_item")
// Câu UPDATE chỉ liệt kê cột thật sự đổi (mặc định liệt kê TẤT CẢ cột).
@DynamicUpdate
public class U4OrderItem {
    // ... các field giống baseline
}
```

![Sơ đồ bảng purchase_order và order_item của biến thể DynamicUpdate](img/g4-u4.png){width=70%}

**Ưu điểm.** Câu UPDATE ngắn, chỉ ghi cột đổi. Có ích khi bảng có nhiều cột hoặc cột lớn (văn bản dài, JSON), hoặc khi nhiều transaction cùng sửa các cột khác nhau của một dòng và bạn không muốn ghi đè cột của nhau.

**Nhược điểm.** Hibernate phải **dựng câu SQL mới mỗi lần flush** (mặc định nó dùng sẵn một câu cố định đã chuẩn bị từ lúc khởi động). Tốn thêm CPU, và các câu SQL khác nhau cũng làm cache của prepared statement kém hiệu quả.

**Nên dùng khi.** Bảng nhiều cột hoặc có cột lớn, và đo được lợi ích. Với bảng nhỏ như `order_item`, mặc định là đủ.

**Trong JPA thuần.** `@DynamicUpdate` không có trong JPA. Cách tương đương: viết câu cập nhật có chủ đích (`update OrderItem set quantity = :q where id = :id`, mục 15.1) cho các thao tác chỉ đổi vài cột. JPA thuần không có cách bảo provider "chỉ ghi cột đổi" cho dirty checking.

#### 14.6.1 Không có DynamicUpdate: UPDATE ghi tất cả cột {.cot-loi}

**Bối cảnh.** Bạn đổi `quantity` rồi bật log SQL và thấy câu UPDATE ghi lại cả `order_id`, `product_id`, `unit_price`. Có phải Hibernate ghi thừa?

**Khi nào xảy ra.** Entity không có `@DynamicUpdate` (mặc định).

**Khi nào không xảy ra.** Entity có `@DynamicUpdate` (mục 14.6.2).

**Ví dụ.**

```java
inTx(() -> {
    U0PurchaseOrder orderA = em.find(U0PurchaseOrder.class, orderId);
    orderA.getItems().get(0).setQuantity(5);
});
```

**Kết quả.** Câu UPDATE liệt kê **tất cả cột** của `order_item`:

```sql
update g4_u0_baseline.order_item
set order_id=?, product_id=?, quantity=?, unit_price=?
where id=?
```

**Vì sao.** Mặc định Hibernate chuẩn bị sẵn **một** câu UPDATE cho mỗi entity lúc khởi động, liệt kê mọi cột, rồi dùng lại cho mọi lần flush. Cột không đổi được bind lại đúng giá trị cũ. Đổi lại, câu SQL cố định nên prepared statement được dùng lại hiệu quả.

::: {.ghi-nho}
Câu UPDATE ghi đủ mọi cột là hành vi mặc định, không phải lỗi: cột không đổi được ghi lại đúng giá trị cũ.
:::

::: {.tracuu codes="G4-S10a"}
:::

#### 14.6.2 Có DynamicUpdate: UPDATE chỉ ghi cột quantity {.cot-loi}

**Bối cảnh.** Cùng thao tác như mục 14.6.1, nhưng entity có `@DynamicUpdate`.

**Khi nào xảy ra.** Entity có `@DynamicUpdate`.

**Khi nào không xảy ra.** Không có annotation (mục 14.6.1).

**Ví dụ.** Code của test giống hệt mục 14.6.1; chỉ khác entity (`U4PurchaseOrder`).

**Kết quả.**

```sql
update g4_u4_dynamic_update.order_item
set quantity=?
where id=?
```

Câu UPDATE chỉ còn cột `quantity`.

**Vì sao.** Với `@DynamicUpdate`, lúc flush Hibernate so state hiện tại với bản chụp lúc tải để biết cột nào đổi, rồi **dựng câu UPDATE chỉ gồm các cột đó**. Chính vì phải dựng mỗi lần nên có chi phí đã nêu ở phần Nhược điểm.

::: {.ghi-nho}
Mặc định UPDATE ghi mọi cột. `@DynamicUpdate` chỉ ghi cột đổi, đổi lại là dựng câu SQL mỗi lần flush.
:::

::: {.tracuu codes="G4-S10b"}
:::

### 14.7 Đi qua cha hay đi thẳng repository của con

#### 14.7.1 Cùng kết quả trong database, chi phí khác nhau {.mo-rong}

**Bối cảnh.** Bạn cần sửa số lượng của một dòng hàng khi đã biết `itemId`. Có hai cách: tải đơn rồi đi qua `getItems()` (thói quen "làm việc qua aggregate root"), hoặc dùng `OrderItemRepository.findById(itemId)` rồi sửa.

**Khi nào xảy ra.** `order` và `product` trên dòng hàng là LAZY, và bạn chỉ cần sửa field của dòng hàng.

**Khi nào không xảy ra.** Nếu quy tắc nghiệp vụ buộc phải kiểm tra cả đơn (trạng thái, tổng tiền), thì đi qua cha là đúng, dù tốn hơn.

**Ví dụ.**

```java
// Cách 1: đi qua cha để sửa dòng hàng PEN.
inTx(() -> {
    U0PurchaseOrder orderA = em.find(U0PurchaseOrder.class, orderId);
    for (U0OrderItem item : orderA.getItems()) {
        if (item.getId().equals(penItemId)) {
            item.setQuantity(5);
        }
    }
});

// Cách 2: đi thẳng qua repository của con để sửa dòng hàng BOOK.
inTx(() -> {
    U0OrderItem bookItem = u0ItemRepository.findById(bookItemId).orElseThrow();
    bookItem.setQuantity(7);
});
```

**Kết quả.** Database kết quả như nhau (mỗi cách một câu UPDATE `order_item`, cha giữ nguyên). Khác nhau ở số câu SELECT:

| | SELECT purchase_order | SELECT order_item | UPDATE order_item |
|---|---|---|---|
| Đi qua cha | 1 | 1 (lấy **toàn bộ** dòng hàng của đơn) | 1 |
| Đi thẳng qua repository của con | **0** | 1 (chỉ **một** dòng) | 1 |

```sql
select uoi1_0.id, uoi1_0.order_id, uoi1_0.product_id, uoi1_0.quantity,
    uoi1_0.unit_price
from g4_u0_baseline.order_item uoi1_0
where uoi1_0.id=?

update g4_u0_baseline.order_item
set order_id=?, product_id=?, quantity=?, unit_price=?
where id=?
```

Dữ liệu cuối: dòng bút 5, dòng sách 7.

**Vì sao.** Đi qua cha buộc Hibernate tải cha, rồi khi bạn chạm `getItems()` thì tải thêm **cả danh sách**. Với đơn 3 dòng thì chẳng đáng kể, nhưng đơn 500 dòng thì mỗi lần sửa một dòng là tải 500 dòng. Đi thẳng thì chỉ tải đúng dòng cần, và vì `order` là LAZY nên đơn không bị tải.

**Cách tránh.** Chọn theo nhu cầu, không theo thói quen: sửa một dòng hàng mà không cần luật của cả đơn thì đi thẳng; cần kiểm tra bất biến của aggregate (tổng tiền, trạng thái) thì đi qua cha.

::: {.ghi-nho}
Đi qua cha an toàn về nghiệp vụ nhưng tải cả collection. Đi thẳng rẻ hơn, miễn là không cần luật của cha.
:::

::: {.tracuu codes="G4-S12"}
:::

### 14.8 Cập nhật đơn hàng từ request: cách đúng và cách làm mất dữ liệu

Màn hình sửa đơn gửi lên một request chỉ chứa **những gì người dùng sửa được**: số đơn, ghi chú và danh sách dòng hàng. Request không có `status`, `createdAt`, `customer`, `total`. Service phải biến request đó thành thay đổi trong database mà không làm mất các cột không có trong request.

![Sơ đồ bảng của mô hình cập nhật đơn hàng: customer, purchase_order, order_item và product](img/g9-s02.png){width=85%}

Cả hai cách dưới đây dùng cùng một request và cùng dữ liệu ban đầu. Dữ liệu ban đầu: đơn A-001 trạng thái PAID, có khách An, ngày tạo `2026-01-01`, ghi chú "ghi chú cũ", hai dòng hàng (bút 1 cái giá 10.00, sách 2 cuốn giá 20.00), tổng 50.00. Request muốn: sửa ghi chú thành "giao nhanh", bút lên 5 cái, thêm chuột 1 cái giá 15.00, bỏ dòng sách.

#### 14.8.1 Cách đúng: tải đơn rồi áp thay đổi lên chính nó {.cot-loi}

**Bối cảnh.** Đây là cách service nên viết.

**Khi nào xảy ra.** Service tải đơn (MANAGED), rồi chỉ gán những field có trong request.

**Khi nào không xảy ra.** Không áp dụng cho trường hợp tạo mới (chưa có đơn để tải).

**Ví dụ.**

```java
@Transactional
public void updateByDiff(OrderUpdateRequest request) {
    PurchaseOrder order = em.find(PurchaseOrder.class, request.orderId());
    order.setOrderNo(request.orderNo());
    order.setNote(request.note());

    // Dòng hàng cũ: có trong request thì sửa, vắng mặt thì gỡ (orphanRemoval xóa).
    for (OrderItem existingItem : new ArrayList<>(order.getItems())) {
        ItemRequest itemRequest = requestedById.get(existingItem.getId());
        if (itemRequest == null) {
            order.removeItem(existingItem);
        } else {
            existingItem.setQuantity(itemRequest.quantity());
        }
    }
    // Dòng hàng mới (itemId null): thêm vào, cascade PERSIST lo phần INSERT.
    order.addItem(new OrderItem(product, 1, new BigDecimal("15.00")));
    order.recalculateTotal();
}
```

**Kết quả.** Một câu UPDATE `purchase_order` (chỉ các cột nghiệp vụ đổi), một UPDATE, một INSERT, một DELETE cho `order_item`.

```sql
update g9_s02_order_update.purchase_order
set created_at=?, customer_id=?, note=?, order_no=?, status=?, total=?, version=?
where id=? and version=?
```

| Bảng purchase_order | status | created_at | customer_id | note | total | version |
|---|---|---|---|---|---|---|
| Trước | PAID | 2026-01-01 | An | ghi chú cũ | 50.00 | 0 |
| Sau | PAID | 2026-01-01 | An | **giao nhanh** | **65.00** | **1** |

Dòng hàng sau khi cập nhật: bút số lượng **5**, chuột số lượng **1** (mới), dòng sách đã bị xóa.

Thay đổi: ghi chú, tổng tiền (tính lại: 5 cái giá 10.00 cộng 1 cái giá 15.00), version. Giữ nguyên: `status`, `created_at`, khách hàng.

**Vì sao.** Đơn được tải về là MANAGED, nên **mọi cột đã có sẵn giá trị đúng** trong entity. Service chỉ gán những field có trong request; field khác giữ giá trị vừa tải. Lúc flush, giá trị ghi xuống cho các cột không đụng tới chính là giá trị cũ. Câu UPDATE vẫn liệt kê mọi cột (mặc định Hibernate, xem mục 14.6), nhưng giá trị bind của cột không đổi là giá trị cũ nên không mất gì.

::: {.ghi-nho}
Tải đơn rồi gán từng field có trong request. Cột không có trong request giữ nguyên giá trị đã tải.
:::

::: {.tracuu codes="G9-S02a"}
:::

#### 14.8.2 Cách làm mất dữ liệu: dựng entity mới từ request rồi merge {.cot-loi}

**Bối cảnh.** Cách ngắn gọn nhất nghe rất hợp lý: map request sang một entity `PurchaseOrder` mới (gán id, version), rồi `save()` hoặc `merge()`. Code ngắn, test với dữ liệu đầy đủ thấy ổn. Nhưng nó làm mất dữ liệu.

**Khi nào xảy ra.** Entity dựng từ request **thiếu** một số field (vì request không có), rồi đưa vào `merge()`.

**Khi nào không xảy ra.** Nếu request chứa **đầy đủ** mọi cột của entity (không thực tế khi màn hình chỉ sửa một phần).

**Ví dụ.**

```java
@Transactional
public void updateByMerge(OrderUpdateRequest request) {
    PurchaseOrder rebuilt = PurchaseOrder.detachedCopyFromRequest(
        request.orderId(), request.version(), request.orderNo(), request.note());
    for (ItemRequest itemRequest : request.items()) {
        Product product = em.getReference(Product.class, itemRequest.productId());
        rebuilt.addItem(OrderItem.detachedCopyFromRequest(
            itemRequest.itemId(), product, itemRequest.quantity(),
            itemRequest.unitPrice()));
    }
    // rebuilt là DETACHED (có id) và không trở thành MANAGED.
    em.merge(rebuilt);
}
```

**Kết quả.** Số câu ghi **giống hệt cách đúng** (một UPDATE đơn, một UPDATE, một INSERT, một DELETE dòng hàng). Chỉ khác **giá trị**:

| Bảng purchase_order | status | created_at | customer_id | note | total | version |
|---|---|---|---|---|---|---|
| Trước | PAID | 2026-01-01 | An | ghi chú cũ | 50.00 | 0 |
| Sau | **NULL** | **NULL** | **NULL** | giao nhanh | **NULL** | 1 |

Thay đổi: ghi chú (đúng ý), **và** trạng thái, ngày tạo, khách hàng, tổng tiền đều thành NULL. Đơn mất khách và mất tổng tiền mà không có lỗi nào.

::: {.khac-du-doan codes="G9-S02b"}
**Điều nhiều người nghĩ:** cách dựng entity mới rồi merge sẽ sinh nhiều câu SQL hơn hoặc khác hơn cách tải rồi áp thay đổi, nên đếm SQL sẽ phát hiện được khác biệt.

**Thực tế:** số câu ghi giống hệt cách đúng, vì Hibernate mặc định UPDATE mọi cột. Chỉ khác giá trị bind (NULL thay cho giá trị cũ). Cách này còn tốn ít SELECT hơn (một left join thay vì hai SELECT). Lỗi chỉ lộ ra khi đọc dữ liệu thật.

Đã kiểm chứng trên Hibernate 7.4.15.Final.
:::

**Vì sao.** `merge()` coi bản dựng từ request là **trạng thái đầy đủ mong muốn** của entity, nên copy **toàn bộ** state của nó lên bản trong database, kể cả field `null`. Request không có `status`, nên `status` của bản dựng là `null`, và `null` đó đè lên `PAID`. Vì `merge()` không biết `null` nghĩa là "request không nói tới" hay "người dùng muốn xóa".

**Cách tránh.** Đừng dựng entity từ request rồi merge. Tải entity về và gán từng field (mục 14.8.1). Và viết test mở lại dòng bằng JDBC để so các cột **không có trong request**: đếm SQL không bắt được lỗi này.

::: {.ghi-nho}
`merge` copy toàn bộ state, kể cả `null`. Entity dựng từ request thiếu field sẽ làm mất dữ liệu mà không có lỗi nào.
:::

::: {.tracuu codes="G9-S02b"}
:::

### 14.9 Chuyển item giữa hai đơn và tính lại tổng

Mục này là đối trọng của cả chương: ở đây việc đụng tới cha là **cố ý**, vì cột `total` của cha là một cột thật phụ thuộc vào các dòng hàng.

#### 14.9.1 Chuyển một dòng hàng từ đơn A sang đơn B {.mo-rong}

**Bối cảnh.** Khách muốn dời cuốn sách từ đơn A-001 sang đơn B-001. Cả hai đơn đều lưu `total`, nên cả hai phải tính lại. Câu hỏi: cần những câu UPDATE nào, version của hai đơn có đổi không, và `orphanRemoval` có xóa nhầm dòng hàng vừa gỡ khỏi A không?

**Khi nào xảy ra.** Hai đơn cùng được tải trong một transaction, dòng hàng được gỡ khỏi A rồi thêm vào B.

**Khi nào không xảy ra.** Nếu bạn gỡ khỏi A nhưng **không** thêm vào đâu (trong cùng transaction), thì `orphanRemoval` xóa dòng hàng.

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder orderA = em.find(PurchaseOrder.class, orderAId);
    PurchaseOrder orderB = em.find(PurchaseOrder.class, orderBId);
    OrderItem movedItem = em.find(OrderItem.class, movedItemId);
    orderA.removeItem(movedItem); // gỡ khỏi A, đặt movedItem.order = null
    orderB.addItem(movedItem);    // thêm vào B, đặt movedItem.order = B
    orderA.recalculateTotal();
    orderB.recalculateTotal();
});
```

**Kết quả.** Không có câu DELETE nào. Có **một** UPDATE `order_item` (đổi `order_id`) và **hai** UPDATE `purchase_order` (mỗi đơn một câu, vì `total` đổi).

```sql
update g9_s02_order_update.purchase_order
set created_at=?, customer_id=?, note=?, order_no=?, status=?, total=?, version=?
where id=? and version=?

update g9_s02_order_update.order_item
set order_id=?, product_id=?, quantity=?, unit_price=?
where id=?
```

| Dữ liệu | Trước | Sau |
|---|---|---|
| Đơn A: total, version | 50.00, 0 | **10.00, 1** |
| Đơn B: total, version | 15.00, 0 | **55.00, 1** |
| Dòng sách: order_id | đơn A | **đơn B** |
| Tổng số dòng order_item | 3 | 3 |

Thay đổi: `total` và `version` của cả hai đơn, và `order_id` của dòng sách. Giữ nguyên: số dòng hàng (3), hai dòng còn lại.

**Vì sao.** Hai điều cần nhìn cho rõ. Một: **owning side** là `OrderItem.order`; khi `addItem` gán `item.order = B`, dirty checking thấy `order_id` đổi nên UPDATE dòng hàng. Hai: `orderA.removeItem` làm dòng hàng tạm thời "mồ côi", nhưng lúc flush nó đã nằm trong collection của B, nên không còn là mồ côi và `orphanRemoval` không xóa. Còn hai đơn bị UPDATE vì cột `total` của chúng đổi, và vì chúng có `@Version` nên `version` tăng theo.

Đối chiếu với mục 14.2: ở đó sửa dòng hàng không đụng cha. Ở đây cha bị đụng, nhưng **vì chính cha có field đổi** (`total`), không phải vì collection `mappedBy` đổi.

::: {.ghi-nho}
Chuyển con giữa hai cha: gỡ khỏi cha cũ, thêm vào cha mới trong cùng transaction, và nhớ tính lại cột tổng của cả hai.
:::

::: {.tracuu codes="G9-S09"}
:::
