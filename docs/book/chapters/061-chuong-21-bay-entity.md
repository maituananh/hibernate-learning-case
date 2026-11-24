## Chương 21. Những bẫy khi viết lớp entity

Mọi chương trước nói về **annotation**: gắn gì lên field thì Hibernate sinh ra SQL gì. Chương này nói về phần **code Java thường** của lớp entity, thứ mà annotation không che được. Có ba hàm cực kỳ quen thuộc là `equals`, `hashCode` và `toString`. IDE sinh ra chúng chỉ bằng một cú nhấp chuột, Lombok sinh ra chỉ bằng một annotation, nên chẳng ai nghĩ chúng có thể làm hỏng dữ liệu.

Nhưng một entity khác một class Java thường ở hai điểm: **id của nó đổi giá trị giữa chừng** (từ `null` sang một con số, ngay lúc `persist`), và nó **nằm trong quan hệ hai chiều** (cha trỏ con, con trỏ lại cha). Hai điểm này đủ để biến một `hashCode` trông hợp lý thành một lỗi im lặng, và một `toString` trông vô hại thành `StackOverflowError`.

Trong chương này:

- `equals` và `hashCode` dựa trên id tự sinh làm `Set` "đánh mất" phần tử, và vì sao đổi sang business key thì hết (mục 21.1);
- `equals` viết bằng `getClass()` thất bại với proxy (mục 21.2);
- `toString` in cả hai phía của quan hệ hai chiều (mục 21.3);
- id gán tay và `Persistable`, đã gặp ở các chương trước (mục 21.4).

**Trong JPA thuần.** Cả chương này không dùng API riêng của Hibernate ở phần nào quan trọng: `equals`, `hashCode`, `toString` là của Java. Chỉ có một chỗ nhắc đến `Hibernate.isInitialized`, và chỗ đó có ghi cách viết tương đương trong JPA.

### 21.1 equals và hashCode theo id sinh tự động trong HashSet

Quan hệ `@OneToMany` thường khai là `Set` thay vì `List` để tránh trùng phần tử và để tránh một số bẫy của `List` (Chương 11). `HashSet` xếp mỗi phần tử vào một "ngăn" theo `hashCode()` **tại thời điểm thêm vào**. Muốn tìm lại phần tử (`contains`, `remove`), `HashSet` tính lại `hashCode()` rồi đến đúng ngăn đó tìm. Nếu `hashCode()` của phần tử đổi sau khi đã vào Set, `HashSet` đến nhầm ngăn và không thấy gì, dù phần tử vẫn nằm trong Set.

Đó là lý do quy tắc cổ điển của Java: **không được dùng field có thể thay đổi để tính `hashCode`**. Với entity, field "có thể thay đổi" khó nhận ra nhất lại là `id`, vì id chỉ đổi đúng một lần, ngay lúc `persist`.

Mô hình của mục này giống mô hình hai chiều chuẩn ở Chương 4 và Chương 6: `PurchaseOrder` là phía inverse với `Set<OrderItem> items`, có `cascade = ALL` và `orphanRemoval = true`; `OrderItem` là phía owning giữ khóa ngoại `order_id`. Hai biến thể chỉ khác nhau đúng một điểm là `hashCode` của `OrderItem`.

![Sơ đồ bảng purchase_order và order_item của mục 21.1](img/g9-s03.png){width=60%}

#### 21.1.1 hashCode theo id: sau persist, remove trả về false và orphanRemoval không chạy {.cot-loi}

**Bối cảnh.** Bạn viết `OrderItem` và nhờ IDE sinh `equals` và `hashCode` dựa trên field `id`, vì "id là khóa chính, định danh của entity, quá hợp lý". Bạn tạo đơn mới, thêm một dòng hàng, lưu đơn. Sau đó, vẫn trong cùng một transaction, người dùng bấm "bỏ dòng hàng này", code gọi `order.removeItem(item)`. Test của bạn chạy `orderRepository.save(...)` rồi đọc lại đơn từ database, nên không bao giờ thấy lỗi. Lỗi chỉ lộ khi thao tác ngay trên đối tượng vừa tạo, đúng cái đường mà người dùng thật hay đi.

**Khi nào xảy ra.** Cả ba điều kiện cùng đúng: `hashCode()` của entity tính từ `id`; id sinh tự động (`SEQUENCE` hay `IDENTITY`) nên là `null` lúc `new`; và entity được thêm vào `HashSet` **trước** khi `persist`.

**Khi nào không xảy ra.** Khi entity được nạp lại từ database (mục 21.1.3), vì khi đó id đã có sẵn ngay từ lúc phần tử vào Set. Cũng không xảy ra nếu bạn `persist` entity (để có id) rồi mới thêm vào Set, nhưng thứ tự này khó giữ được trong code thật, nhất là khi dùng `cascade`.

**Ví dụ.** Phía `OrderItem`, đây là hai hàm gây lỗi:

```java
@Entity(name = "G9S03A_OrderItem")
@Table(schema = "g9_s03a_hash_by_id", name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
        generator = "g9s03a_order_item_seq")
    private Long id;

    @Column(name = "line_code", nullable = false)
    private String lineCode;

    // OWNING side: khóa ngoại order_item.order_id nằm ở đây
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private PurchaseOrder order;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrderItem)) {
            return false;
        }
        OrderItem that = (OrderItem) other;
        return id != null && id.equals(that.id);
    }

    // BẪY: id đổi từ null sang một số ngay lúc persist()
    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
```

Và đây là thao tác gây ra lỗi:

```java
PurchaseOrder orderA = new PurchaseOrder("A-001");
OrderItem itemPen = new OrderItem("PEN-01");
orderA.addItem(itemPen); // id còn null, hashCode đang là hash của null

inTx(() -> {
    em.persist(orderA); // cascade PERSIST: itemPen nhận id từ sequence
    em.flush();         // INSERT purchase_order, INSERT order_item

    orderA.getItems().contains(itemPen); // false
    orderA.removeItem(itemPen);          // false, không gỡ được gì
});
```

**Kết quả.** Sau `persist`, `orderA.getItems().contains(itemPen)` trả `false`, dù `itemPen` rõ ràng nằm trong Set. `removeItem` gọi `Set.remove` và nhận `false`, nên nó không gán `item.order = null`. Hibernate không thấy item nào bị gỡ khỏi collection, nên **orphanRemoval không chạy**: không có câu DELETE nào. Không có exception, không có cảnh báo, chỉ có dữ liệu không đổi.

Trạng thái entity: sau `persist`, cả `orderA` và `itemPen` là MANAGED (đã có id, nhưng chưa có INSERT nào cho đến khi `flush`).

```sql
insert into g9_s03a_hash_by_id.purchase_order (order_no, id)
values (?, ?)

insert into g9_s03a_hash_by_id.order_item (line_code, order_id, id)
values (?, ?, ?)
```

Hai câu `nextval` lấy id từ sequence đã chạy trước hai câu INSERT, và hoàn toàn **không có** câu DELETE nào. Dữ liệu cuối cùng trong bảng `order_item`:

| id | order_id | line_code |
|---|---|---|
| (có giá trị) | (có giá trị) | PEN-01 |

Thay đổi: không có gì thay đổi sau thao tác "gỡ item". Giữ nguyên: dòng `PEN-01` vẫn nằm trong bảng, dù code tưởng đã gỡ nó.

**Vì sao.** Đi từng bước với `HashSet`:

1. `orderA.addItem(itemPen)` thêm item vào Set lúc `id == null`, nên `hashCode()` trả `Objects.hashCode(null)` là `0`. Item nằm ở ngăn số `0`.
2. `em.persist(orderA)` kéo theo `itemPen` (nhờ `cascade`). Hibernate lấy id từ sequence và gán vào field `id`. Lúc này `hashCode()` trả giá trị khác (hash của một con số).
3. `Set.contains(itemPen)` và `Set.remove(itemPen)` tính lại `hashCode()` ra giá trị mới, đến ngăn mới, thấy trống, trả `false`. Item vẫn nằm yên ở ngăn `0`, nhưng không còn đường nào tìm tới nó.
4. Vì `removeItem` chỉ gỡ owning side khi `remove` thành công, nó không làm gì cả. Hibernate so sánh collection lúc flush với trạng thái đã nạp và không thấy phần tử nào mất đi, nên không có "mồ côi" để xóa.

Điều nguy hiểm là toàn bộ chuỗi này **im lặng**. Người dùng thấy dòng hàng vẫn còn sau khi bấm xóa, và bạn không có stack trace nào để lần theo.

**Cách tránh.** Đừng tính `hashCode` từ id sinh tự động. Dùng business key bất biến (mục 21.1.2), hoặc, nếu entity không có business key tự nhiên, dùng một `hashCode` hằng số (ví dụ `return getClass().hashCode()`) kèm `equals` so sánh id khi id không null. Hằng số làm mọi phần tử vào cùng một ngăn, chậm với Set rất lớn, nhưng đúng.

::: {.ghi-nho}
`hashCode` dựa trên id tự sinh làm `Set` đánh mất phần tử vừa persist: `contains` và `remove` trả `false`, và orphanRemoval không chạy, không báo lỗi gì.
:::

::: {.tracuu codes="G9-S03a"}
:::

#### 21.1.2 hashCode theo business key: cùng thao tác, remove chạy đúng {.cot-loi}

**Bối cảnh.** Sau khi gặp lỗi ở mục 21.1.1, bạn đổi `equals` và `hashCode` sang dựa trên `lineCode`: mã dòng hàng, gán ngay lúc tạo `OrderItem` và không bao giờ đổi. Cùng thao tác như trước có còn lỗi không?

**Khi nào xảy ra.** Entity có một **business key** thật sự bất biến: có giá trị ngay từ constructor, không có setter, và cột khai `updatable = false`.

**Khi nào không xảy ra.** Khi business key có thể đổi (ví dụ email người dùng cho phép sửa), thì nó lặp lại đúng bẫy của id: `hashCode` đổi giữa chừng. Business key chỉ an toàn nếu bất biến.

**Ví dụ.** Chỉ có `equals`, `hashCode` và khai báo `lineCode` khác so với mục 21.1.1:

```java
// Business key: duy nhất trong một đơn, bất biến (không có setter)
@Column(name = "line_code", nullable = false, updatable = false)
private String lineCode;

@Override
public boolean equals(Object other) {
    if (this == other) {
        return true;
    }
    if (!(other instanceof OrderItem)) {
        return false;
    }
    OrderItem that = (OrderItem) other;
    return Objects.equals(lineCode, that.getLineCode());
}

@Override
public int hashCode() {
    return Objects.hashCode(lineCode);
}
```

Thao tác giữ y nguyên: tạo đơn, `addItem`, `persist`, `flush`, rồi `removeItem`.

**Kết quả.** Sau `persist`, `contains(itemPen)` trả `true` và `removeItem(itemPen)` trả `true`. Lúc commit, flush cuối cùng thấy item mồ côi và chạy **đúng một câu DELETE** vào `order_item`. Bảng `order_item` còn 0 dòng.

```sql
delete
from g9_s03b_business_key.order_item
where id=?
```

Trước câu DELETE này vẫn là hai câu INSERT như ở mục 21.1.1.

**Vì sao.** `lineCode` có ngay từ constructor và không đổi, nên `hashCode()` trả cùng một giá trị ở mọi thời điểm: trước persist, sau persist, sau khi flush. Item luôn nằm đúng ngăn, `Set.remove` tìm thấy và gỡ được, `removeItem` gán `item.order = null`, và orphanRemoval thấy một phần tử đã rời collection nên xóa dòng tương ứng.

Ba điều kiện của một business key dùng được cho `equals`: có giá trị trước khi persist, không đổi suốt vòng đời, và duy nhất trong phạm vi Set chứa nó (ở đây là duy nhất trong một đơn, không cần duy nhất toàn bảng).

::: {.ghi-nho}
Muốn `equals` và `hashCode` an toàn trong `Set`, dựa vào business key bất biến, có giá trị ngay từ constructor, thay vì id tự sinh.
:::

::: {.tracuu codes="G9-S03b"}
:::

#### 21.1.3 hashCode theo id nhưng Set nạp lại từ database: remove vẫn chạy {.cot-loi}

**Bối cảnh.** Bạn đọc mục 21.1.1 rồi lo: "vậy mọi chỗ dùng `hashCode` theo id của tôi đều hỏng sao?" Thực tế không hẳn: nhiều dự án dùng cách này nhiều năm mà ít khi gặp lỗi. Vì sao?

**Khi nào xảy ra.** Cùng `hashCode` theo id như mục 21.1.1, nhưng đơn được **nạp lại từ database** trước khi thao tác.

**Khi nào không xảy ra.** Lỗi ở mục 21.1.1 vẫn xảy ra ở đường "tạo mới rồi thao tác ngay" trong cùng một persistence context.

**Ví dụ.**

```java
PurchaseOrder orderA = new PurchaseOrder("A-001");
orderA.addItem(new OrderItem("PEN-01"));
Long orderId = inTxReturning(() -> {
    em.persist(orderA);
    return orderA.getId();
});

inTx(() -> {
    PurchaseOrder loadedOrder = em.find(PurchaseOrder.class, orderId);
    // nạp Set: item được thêm vào Set khi id đã có sẵn
    OrderItem loadedItem = loadedOrder.getItems().iterator().next();
    loadedOrder.removeItem(loadedItem); // true
});
```

**Kết quả.** `removeItem` trả `true`, commit chạy **đúng một câu DELETE** vào `order_item`, và bảng còn 0 dòng.

**Vì sao.** Khi Hibernate nạp collection từ database, nó tạo từng `OrderItem` với `id` đã có giá trị rồi mới thêm vào Set. Hash tính từ id lúc thêm vào và hash tính lúc tìm là cùng một con số, không có gì đổi giữa chừng. Lỗi chỉ xảy ra khi id đổi **trong lúc phần tử đã nằm trong Set**, tức là đường tạo mới.

Chính điều này khiến lỗi khó bị phát hiện: test viết theo kiểu "lưu rồi nạp lại rồi thao tác" luôn xanh, còn lỗi nằm ở đường "tạo rồi thao tác ngay" mà ít test nào đi qua.

::: {.ghi-nho}
`hashCode` theo id chỉ hỏng ở đường "tạo mới rồi thao tác ngay"; nạp lại từ database thì chạy đúng, nên test sơ sài rất dễ bỏ lọt lỗi.
:::

::: {.tracuu codes="G9-S03c"}
:::

### 21.2 equals với proxy: instanceof đúng, getClass sai

Chương 2 và Chương 11 đã cho thấy `getReference` và quan hệ `LAZY` trả về **proxy**: một class con của entity do Hibernate sinh ra lúc chạy. Proxy trông như entity thật, nhưng `getClass()` của nó không phải class entity.

#### 21.2.1 equals viết bằng getClass so với proxy luôn trả false {.cot-loi}

**Bối cảnh.** Nhiều mẫu `equals` trên mạng (và nhiều template của IDE) mở đầu bằng `if (getClass() != other.getClass()) return false;`, vì "so sánh đúng loại thì mới an toàn". Với class Java thường điều đó đúng. Với entity thì sai ngay khi một trong hai vế là proxy: ví dụ item lấy từ `order.getItems()` là class thật, còn item lấy từ `payment.getItem()` (quan hệ `LAZY`) là proxy của cùng một dòng. Hai đối tượng đại diện cho cùng một dòng database nhưng `equals` nói "khác nhau", và lỗi chỉ hiện ở nơi nào đó so sánh chúng, rất xa chỗ viết `equals`.

**Khi nào xảy ra.** `equals` so sánh `getClass()`, và một trong hai vế là proxy (từ `getReference`, hoặc từ quan hệ `@ManyToOne(fetch = LAZY)` chưa nạp).

**Khi nào không xảy ra.** Khi `equals` dùng `instanceof` và đọc field của vế kia qua getter, như ở mục 21.1.2. Cũng không xảy ra nếu cả hai vế đều là class thật.

**Ví dụ.** Test so một `OrderItem` thật (đã DETACHED) với proxy của chính dòng đó, lấy bằng `getReference` (proxy chưa nạp, chưa có câu SELECT nào). Hai cách viết `equals` nằm cạnh nhau trong cùng entity:

```java
// Cách đúng: instanceof chấp nhận lớp con (proxy), getter kích hoạt nạp
@Override
public boolean equals(Object other) {
    if (this == other) {
        return true;
    }
    if (!(other instanceof OrderItem)) {
        return false;
    }
    OrderItem that = (OrderItem) other;
    return Objects.equals(lineCode, that.getLineCode());
}

// BẪY: getClass() của proxy là lớp con sinh lúc chạy
public boolean equalsUsingGetClass(Object other) {
    if (other == null || getClass() != other.getClass()) {
        return false;
    }
    return Objects.equals(lineCode, ((OrderItem) other).lineCode);
}
```

```java
inTx(() -> {
    OrderItem proxyItem = em.getReference(OrderItem.class, itemId);

    Hibernate.isInitialized(proxyItem);          // false, chưa chạm DB
    realItem.equals(proxyItem);                  // true
    realItem.equalsUsingGetClass(proxyItem);     // false
});
```

**Kết quả.** `Hibernate.isInitialized(proxyItem)` là `false` và `proxyItem.getClass()` **khác** `realItem.getClass()`. Bản `instanceof` trả `true`, còn bản `getClass()` trả `false` cho cùng một dòng database.

| Cách viết `equals` | Kết quả so với proxy |
|---|---|
| Dùng `instanceof` và đọc field qua getter | **true** |
| Dùng `getClass()` | **false** |

Thay đổi: không có dữ liệu nào đổi, đây là phép so sánh trong bộ nhớ. Giữ nguyên: dòng `PEN-01` trong database.

**Vì sao.** Proxy của Hibernate là một **class con** của entity, sinh ra lúc chạy, nên `getClass()` của nó là một class khác hẳn (thường có hậu tố kiểu `$HibernateProxy$...`). `getClass() != other.getClass()` vì thế luôn đúng với proxy. `instanceof` thì chấp nhận mọi lớp con. Chi tiết thứ hai: field `lineCode` của proxy chưa nạp là `null`, vì dữ liệu thật nằm trong đối tượng đích mà proxy bọc bên trong. Chỉ có **getter** của proxy mới kích hoạt nạp (một câu SELECT) rồi trả giá trị thật. Vì thế `equals` phải viết `that.getLineCode()`, không phải `that.lineCode`.

**Cách tránh.** Viết `equals` bằng `instanceof`, và luôn đọc field của đối tượng kia qua getter. Đừng nhờ IDE sinh `equals` theo mẫu `getClass()` cho entity mà không sửa lại.

**Trong JPA thuần.** Kiểm tra proxy đã nạp chưa trong test dùng `Hibernate.isInitialized(proxy)`. JPA thuần tương đương là `entityManagerFactory.getPersistenceUnitUtil().isLoaded(proxy)`. Phần `equals` ở trên không cần API Hibernate.

::: {.ghi-nho}
Viết `equals` của entity bằng `instanceof` và đọc field của đối tượng kia qua getter; `getClass()` làm so sánh với proxy luôn sai.
:::

::: {.tracuu codes="G9-S03d"}
:::

### 21.3 toString đệ quy trong quan hệ hai chiều

#### 21.3.1 toString in cả hai phía quan hệ gây StackOverflowError {.mo-rong}

**Bối cảnh.** Bạn thêm `@Data` hoặc `@ToString` của Lombok vào entity (hay để IDE sinh `toString` có đủ mọi field), rồi viết một dòng log `log.info("order = {}", order)`. Không cần database, không cần Hibernate: chỉ cần hai đối tượng đã nối hai chiều là đủ.

**Kết quả.** `order.toString()` in `items`, mỗi item in lại `order`, order lại in `items`, và cứ thế mãi cho đến khi `StackOverflowError`. Test dựng đối tượng trong bộ nhớ (không có SQL nào) và xác nhận exception này.

Cách sửa là để `toString` chỉ in field của chính entity, hoặc chỉ in id của quan hệ:

```java
// Phía PurchaseOrder: không in items
public String toSafeString() {
    return "PurchaseOrder{id=" + id + ", orderNo=" + orderNo + "}";
}
```

Kết quả của hàm an toàn là chuỗi `PurchaseOrder{id=null, orderNo=A-001}`, không có `items`.

Ngoài việc tránh vòng lặp vô hạn, bỏ quan hệ khỏi `toString` còn tránh một lỗi thứ hai: với collection `LAZY`, in `items` **kích hoạt lazy loading** (một câu SELECT) chỉ vì bạn ghi log, và ngoài transaction thì thành `LazyInitializationException` (Chương 11).

**Trong JPA thuần.** Hoàn toàn là code Java thường, không liên quan API của Hibernate.

::: {.ghi-nho}
`toString` của entity chỉ nên in field của chính nó (hoặc id của quan hệ), không in collection hay quan hệ hai chiều.
:::

::: {.tracuu codes="G9-S04a,G9-S04b"}
:::

### 21.4 Id gán tay và Persistable

Bẫy cuối cùng của chương này không có bài test riêng, vì đã được kiểm chứng ở hai chỗ khác:

- Mục 2.9 cho thấy với **id gán tay** và không có `@Version`, `repository.save()` gọi `merge` thay vì `persist`, nên thừa một câu SELECT trước câu INSERT. Hai cách sửa là thêm `@Version` hoặc cho entity implement `Persistable` để tự khai báo `isNew()`.
- Mục 9.3 cho thấy khi một phần của khóa chính lấy từ khóa ngoại bằng `@MapsId`, id cũng không phải do Hibernate sinh ra, và bạn phải tự bảo đảm phần id do mình gán có giá trị trước khi `persist`.

Điểm chung với mục 21.1: **id không phải lúc nào cũng "tự có"**. Khi viết entity, luôn tự hỏi hai câu: id có giá trị lúc nào (trước hay sau `persist`), và đoạn code của mình có đang ngầm giả định điều đó không.
