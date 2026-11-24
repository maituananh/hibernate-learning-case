## Chương 6. orphanRemoval: khi con bị tách khỏi cha

Chương 5 trả lời câu hỏi "cha bị xóa thì con ra sao". Chương này hỏi một câu khác, và khó hơn: **cha vẫn còn nguyên, chỉ có con bị gỡ khỏi collection** (hoặc chuyển sang cha khác) thì dòng của con trong database ra sao?

Người mới thường nghĩ `order.getItems().remove(item)` có nghĩa là "xóa item này khỏi đơn hàng", và database sẽ xóa hoặc ít nhất cập nhật. Thực tế, tùy mapping, câu lệnh đó có thể **không làm gì cả**, có thể để lại một dòng mồ côi (khóa ngoại NULL), hoặc xóa hẳn dòng. `orphanRemoval` là công tắc quyết định kết quả nào xảy ra, và nó có những bẫy riêng: thay cả collection là lỗi, thứ tự SQL lúc flush có thể làm vi phạm ràng buộc unique.

Trong chương này:

- "Con mồ côi" là gì, và `orphanRemoval` khác `CascadeType.REMOVE` ra sao (mục 6.1).
- Không có `orphanRemoval` (6.2) và có `orphanRemoval` (6.3).
- `orphanRemoval` mà không có cascade (6.4).
- Chuyển con sang cha khác, và thay con trùng giá trị unique (6.5).
- Merge đồ thị DETACHED bỏ bớt con (6.6).
- Bốn chiến lược xóa một customer cùng toàn bộ order và item (6.7).

Chương này dùng mô hình ba tầng: `PurchaseOrder` có nhiều `OrderItem`, `OrderItem` có nhiều `ItemDiscount`. Cả hai quan hệ đều hai chiều chuẩn (như mục 4.7) và có helper `addItem`, `removeItem`, `addDiscount`. Bảng `order_item` có ràng buộc **unique trên cặp (order_id, product_code)** và cột `order_id` cho phép NULL, để ta nhìn thấy dòng mồ côi.

### 6.1 Con mồ côi là gì, và orphanRemoval khác REMOVE chỗ nào

::: {.kien-thuc-nen}
**Con mồ côi (orphan)** là entity con từng nằm trong collection của cha, nhưng nay đã bị gỡ khỏi collection đó. Với `orphanRemoval = true`, Hibernate coi con mồ côi là thứ cần **xóa khỏi database**.

Cách Hibernate nhận ra con mồ côi: khi tải collection, nó giữ một **bản snapshot** của danh sách phần tử lúc đó. Lúc flush, nó so collection hiện tại với snapshot. Phần tử có trong snapshot mà không còn trong collection hiện tại là con mồ côi, và nó bị DELETE. Hai điều cần nhớ:

1. Việc phát hiện xảy ra **lúc flush**, không phải lúc bạn gọi `remove` khỏi list. Trong khoảng giữa, con vẫn là MANAGED.
2. Hibernate chỉ nhìn collection (phía inverse). Nó **không** nhìn `item.order` (owning side).

**So sánh với `CascadeType.REMOVE`:**

| | `CascadeType.REMOVE` | `orphanRemoval = true` |
|---|---|---|
| Kích hoạt khi | Cha bị `remove` | Con bị gỡ khỏi collection, hoặc cha bị `remove` |
| Cha bị xóa | Xóa con | Xóa con, kể cả khi không có cascade (mục 6.4) |
| `items.remove(item)` | Không làm gì (mục 6.2.1) | Xóa dòng item (mục 6.3.2) |
| Lưu con mới (`persist`) | Không liên quan | Không liên quan: vẫn cần cascade `PERSIST` (mục 6.4.2) |
| Gán collection mới | Bình thường | Gây lỗi (mục 6.3.4) |

`orphanRemoval` thường đi chung với `cascade = ALL`, nhưng chúng là hai khai báo độc lập.
:::

### 6.2 Không có orphanRemoval: gỡ khỏi collection không xóa gì {.bien-the}

Điểm xuất phát của chương: `items` có `cascade = ALL` nhưng **không** có `orphanRemoval`.

```java
@Entity(name = "G3O0_PurchaseOrder")
@Table(schema = "g3_o0_cascade_all", name = "purchase_order")
public class O0PurchaseOrder {

    // INVERSE side: cột order_id nằm ở order_item, do OrderItem.order ghi.
    // Có cascade ALL, KHÔNG có orphanRemoval.
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL)
    private List<O0OrderItem> items = new ArrayList<>();

    public void addItem(O0OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    // Gỡ cả hai phía: bỏ khỏi collection và xóa liên kết ở owning side.
    public void removeItem(O0OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }
}
```

![Bảng order_item có khóa ngoại order_id tới purchase_order.id và ràng buộc unique trên order_id cùng product_code; bảng item_discount có khóa ngoại item_id tới order_item.id](img/g3-o0.png){width=90%}

**Ưu điểm.** Hành vi dễ đoán: Hibernate không bao giờ xóa dòng con chỉ vì nó rời khỏi một collection. Muốn xóa phải gọi `em.remove` tường minh.

**Nhược điểm.** Gỡ con khỏi collection không có tác dụng gì (nếu chỉ gỡ ở một phía), hoặc để lại dòng mồ côi với khóa ngoại NULL (nếu gỡ cả hai phía). Người đọc code dễ tưởng đã xóa.

**Nên dùng khi.** Con có vòng đời độc lập với cha, hoặc bạn muốn tự quản lý việc xóa. Cũng là cấu hình hợp lý khi khóa ngoại NOT NULL và con không được phép "không thuộc ai".

#### 6.2.1 Chỉ items.remove(item): database không đổi gì {.cot-loi}

**Bối cảnh.** Một chức năng "bỏ dòng hàng khỏi đơn": code lấy order, gọi `order.getItems().remove(item)` rồi kết thúc transaction. Bản thân List đã không còn item, nên người viết tin rằng dòng hàng đã bị xóa. Khi tải lại đơn, dòng hàng vẫn còn đó.

**Khi nào xảy ra.** `items` là inverse side, không có `orphanRemoval`, và bạn chỉ gỡ item khỏi collection mà không đổi `item.order`.

**Khi nào không xảy ra.** Nếu có `orphanRemoval` (mục 6.3.2), hoặc nếu bạn đổi `item.order` về null (mục 6.2.2).

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder orderA = em.find(PurchaseOrder.class, orderId);
    OrderItem itemPen = orderA.getItems().get(0);
    orderA.getItems().remove(itemPen);
    // Trạng thái: itemPen vẫn là MANAGED. Không ai đánh dấu nó REMOVED.
});
```

**Kết quả.** Không có câu UPDATE nào, không có câu DELETE nào cho `order_item`. Dữ liệu giữ nguyên:

| Bảng order_item | product_code | order_id |
|---|---|---|
| Trước | PEN | id của A-001 |
| Sau | PEN | id của A-001 |

Thay đổi: không có. Giữ nguyên: toàn bộ dòng, kể cả cột `order_id`. Tải lại order, item vẫn thuộc order.

**Vì sao.** Collection `items` là **inverse side**: Hibernate bỏ qua nó khi ghi. Cột `order_id` do `item.order` (owning side) quyết định, và field này vẫn trỏ về order. Lúc flush, dirty checking chạy trên item: không field nào của item đổi, nên không có UPDATE. Không có `orphanRemoval` nên cũng không có ai coi item là mồ côi để xóa.

::: {.ghi-nho}
`items.remove(item)` chỉ đổi danh sách trong bộ nhớ. Khóa ngoại do owning side quyết định; không ai đổi nó, nên database không đổi.
:::

::: {.tracuu codes="G3-S01"}
:::

#### 6.2.2 removeItem gỡ cả hai phía: dòng item thành mồ côi trong database {.cot-loi}

**Bối cảnh.** Lần này bạn đã học bài ở mục 4.7: dùng helper `removeItem` gỡ cả hai phía (xóa khỏi list và đặt `item.order = null`). Bạn mong chờ dòng hàng biến mất. Nhưng database còn dòng đó, chỉ là không thuộc order nào.

**Khi nào xảy ra.** Không có `orphanRemoval`; bạn gỡ đủ hai phía; cột `order_id` cho phép NULL.

**Khi nào không xảy ra.** Nếu có `orphanRemoval` (mục 6.3.1), dòng bị xóa thay vì thành mồ côi. Nếu cột `order_id` là NOT NULL, câu UPDATE này sẽ vi phạm ràng buộc và transaction thất bại.

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder orderA = em.find(PurchaseOrder.class, orderId);
    orderA.removeItem(orderA.getItems().get(0));
});
```

**Kết quả.** Hibernate chạy một câu UPDATE, không có câu DELETE nào:

```sql
update g3_o0_cascade_all.order_item
set order_id=?, product_code=?, quantity=?
where id=?
```

Dữ liệu `order_item`:

| Bảng order_item | product_code | order_id |
|---|---|---|
| Trước | PEN | id của A-001 |
| Sau | PEN | **NULL** |

Thay đổi: cột `order_id` thành NULL. Giữ nguyên: mọi cột khác. Dòng PEN vẫn còn trong database, nhưng không thuộc order nào: đó chính là "dòng mồ côi" theo nghĩa của database.

**Vì sao.** Helper đã đổi `item.order` từ order thành null. Đó là thay đổi ở owning side, nên dirty checking thấy và sinh câu UPDATE đặt `order_id = NULL`. Không có `orphanRemoval` nên không có gì xóa dòng. Hậu quả: bảng dần chất đầy các dòng không thuộc ai, mà không có lỗi nào.

**Cách tránh.** Nếu dòng không có ý nghĩa khi thiếu cha, dùng `orphanRemoval = true` (mục 6.3) hoặc đặt `order_id` NOT NULL để database từ chối ngay.

::: {.ghi-nho}
Không có `orphanRemoval`, gỡ item ở cả hai phía chỉ làm khóa ngoại thành NULL. Dòng item còn lại, thành mồ côi.
:::

::: {.tracuu codes="G3-S02"}
:::

### 6.3 Có orphanRemoval: gỡ khỏi collection là xóa dòng {.bien-the}

Khác mục 6.2 đúng một điểm: `items` và `discounts` thêm `orphanRemoval = true` (vẫn giữ `cascade = ALL`).

```java
// INVERSE side. Khác biệt duy nhất so với mục 6.2 là orphanRemoval = true.
@OneToMany(mappedBy = "order", fetch = FetchType.LAZY,
        cascade = CascadeType.ALL, orphanRemoval = true)
private List<O1OrderItem> items = new ArrayList<>();
```

![Bảng order_item có khóa ngoại order_id tới purchase_order.id và ràng buộc unique trên order_id cùng product_code; bảng item_discount có khóa ngoại item_id tới order_item.id](img/g3-o1.png){width=90%}

**Ưu điểm.** Gỡ con khỏi collection là xóa dòng: code ngắn, đúng với trực giác "con không còn thuộc cha thì không còn tồn tại". Không để lại dòng mồ côi.

**Nhược điểm.** Một thao tác tưởng vô hại (`clear()`, hay gán collection) có thể xóa hàng loạt dòng. Gán cả collection mới là lỗi (mục 6.3.4). Thứ tự SQL lúc flush có thể gây vi phạm unique (mục 6.5.3). Mỗi dòng bị xóa là một câu DELETE riêng.

**Nên dùng khi.** Con thuộc hẳn về cha và không tồn tại độc lập (dòng hàng của đơn, chi tiết của hóa đơn).

#### 6.3.1 removeItem: DELETE discount của item rồi DELETE item {.cot-loi}

**Bối cảnh.** Cùng thao tác `removeItem` như mục 6.2.2, nhưng lần này có `orphanRemoval`. Item còn có các `ItemDiscount` (giảm giá) trỏ tới nó, nên bạn muốn biết thứ tự xóa.

**Khi nào xảy ra.** `items` và `discounts` đều có `orphanRemoval = true`.

**Khi nào không xảy ra.** Khi không có `orphanRemoval` (mục 6.2.2).

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder orderA = em.find(PurchaseOrder.class, orderId);
    OrderItem itemPen = orderA.getItems().get(0);
    orderA.removeItem(itemPen);
    // Trạng thái: itemPen vẫn là MANAGED. Orphan chỉ bị phát hiện lúc flush.
});
```

**Kết quả.** Hibernate xóa discount của item trước, rồi xóa item, không có câu UPDATE nào:

```sql
delete from g3_o1_orphan.item_discount where id=?
delete from g3_o1_orphan.order_item where id=?
```

| Bảng | Số dòng sau khi chạy |
|---|---|
| purchase_order | 1 (không bị đụng) |
| order_item | **0** |
| item_discount | **0** |

**Vì sao.** Ngay sau `removeItem`, item vẫn MANAGED: Hibernate chưa làm gì. Tới lúc flush, nó so collection `items` với snapshot, thấy `itemPen` có trong snapshot mà không còn trong collection, nên coi là mồ côi và xếp lệnh xóa. Vì `discounts` cũng có `orphanRemoval` và `cascade = ALL`, việc xóa item lan xuống discount của nó, và discount phải xóa **trước** vì giữ khóa ngoại tới item. Không có UPDATE vì dòng sắp bị xóa, đặt `order_id = NULL` là việc thừa.

::: {.ghi-nho}
Với `orphanRemoval`, item bị gỡ khỏi collection sẽ bị DELETE lúc flush, kèm toàn bộ con của nó, con trước cha sau.
:::

::: {.tracuu codes="G3-S03"}
:::

#### 6.3.2 Chỉ items.remove(item) cũng đủ để xóa {.cot-loi}

**Bối cảnh.** Ở mục 6.2.1, `items.remove(item)` không làm gì vì thiếu `orphanRemoval`. Bạn lo rằng với `orphanRemoval` mình vẫn phải nhớ đặt `item.order = null`. Điều đó có đúng không?

**Khi nào xảy ra.** Có `orphanRemoval`; bạn chỉ gỡ khỏi collection, không đổi `item.order`.

**Khi nào không xảy ra.** Không có `orphanRemoval` (mục 6.2.1).

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder orderA = em.find(PurchaseOrder.class, orderId);
    orderA.getItems().remove(0);   // item.order vẫn trỏ về orderA
});
```

**Kết quả.** Vẫn xóa: một câu DELETE `item_discount` và một câu DELETE `order_item`, kết quả giống mục 6.3.1. Bảng `order_item` trống.

**Vì sao.** `orphanRemoval` dựa trên việc so collection với snapshot, không nhìn `item.order`. Chỉ cần phần tử rời khỏi collection là đủ. Đây là điểm khác biệt quan trọng so với mục 6.2.1, nơi cùng câu lệnh không làm gì.

::: {.ghi-nho}
Với `orphanRemoval`, chỉ cần bỏ khỏi collection. Không cần đặt `item.order = null`. Helper `removeItem` vẫn nên dùng để đồng bộ hai phía trong bộ nhớ.
:::

::: {.tracuu codes="G3-S04"}
:::

#### 6.3.3 Xóa cha: cascade REMOVE và orphanRemoval cho kết quả giống nhau {.cot-loi}

**Bối cảnh.** Khi hủy cả đơn hàng, bạn muốn biết `orphanRemoval` có thay đổi gì so với chỉ có `CascadeType.REMOVE`.

**Khi nào xảy ra.** Cả hai cấu hình: `cascade = ALL` không có `orphanRemoval` (mục 6.2), và `cascade = ALL` có `orphanRemoval` (mục 6.3).

**Khi nào không xảy ra.** Nếu không có cả cascade `REMOVE` lẫn `orphanRemoval`: khi đó xóa cha vi phạm khóa ngoại (mục 5.5.1).

**Ví dụ.**

```java
inTx(() -> em.remove(em.find(PurchaseOrder.class, orderId)));
```

**Kết quả.** Với cả hai cấu hình, Hibernate chạy 1 câu DELETE `item_discount`, 1 câu DELETE `order_item`, rồi 1 câu DELETE `purchase_order`. Ba bảng đều trống sau khi chạy.

**Vì sao.** Cả hai cơ chế đều đánh dấu con REMOVED khi cha bị remove. `cascade = ALL` đã có `REMOVE`, nên kết quả giống nhau; `orphanRemoval` không thêm gì khi cha bị xóa. Khác biệt chỉ lộ ra khi cha **không** bị xóa (mục 6.3.1 và 6.3.2) hoặc khi không có cascade (mục 6.4).

::: {.ghi-nho}
Khi xóa cha, `orphanRemoval` và `CascadeType.REMOVE` cho cùng kết quả. Chúng chỉ khác nhau khi cha còn sống.
:::

::: {.tracuu codes="G3-S05a,G3-S05b"}
:::

#### 6.3.4 Gán collection mới cho items: Hibernate ném exception {.cot-loi}

**Bối cảnh.** Chức năng "cập nhật toàn bộ dòng hàng": bạn có sẵn danh sách item mới và viết `order.setItems(newItems)`. Với mapping không có `orphanRemoval`, câu này chạy bình thường. Thêm `orphanRemoval = true` vào mapping vì lý do khác, và đột nhiên chức năng này ném exception ở chỗ rất xa dòng code vừa sửa.

**Khi nào xảy ra.** Có `orphanRemoval`; bạn thay cả instance `List` của entity MANAGED bằng một List khác.

**Khi nào không xảy ra.** Không có `orphanRemoval`. Hoặc bạn sửa tại chỗ bằng `clear()` và `addAll()` (mục 6.3.5).

**Ví dụ.**

```java
assertThatThrownBy(() -> inTx(() -> {
    PurchaseOrder orderA = em.find(PurchaseOrder.class, orderId);
    orderA.getItems().size();     // khởi tạo collection cho Hibernate theo dõi
    orderA.setItems(new ArrayList<>(List.of(new OrderItem("INK", 2))));
})).rootCause().hasMessageContaining("orphan deletion was no longer referenced");
```

**Kết quả.** Lúc flush, Hibernate ném `HibernateException` với thông báo "A collection with orphan deletion was no longer referenced by the owning entity instance" (Spring bọc thành `JpaSystemException`). Transaction rollback. Bảng `order_item` vẫn chỉ có dòng PEN ban đầu; item INK chưa bao giờ được lưu.

**Vì sao.** Khi tải order, Hibernate thay `List` của bạn bằng một `PersistentBag` do nó điều khiển, và giữ snapshot bên trong đó. Muốn biết phần tử nào mồ côi, nó cần so **chính instance đó** với snapshot. Nếu bạn thay hẳn bằng `ArrayList` mới, Hibernate mất đối tượng để so, và không còn cách nào biết các item cũ có bị bỏ hay không. Thay vì đoán, nó từ chối bằng exception.

**Cách tránh.** Luôn sửa collection tại chỗ: `clear()` rồi `addAll()` (mục 6.3.5). Có thể làm setter không thay collection, hoặc bỏ setter của collection.

::: {.ghi-nho}
Với `orphanRemoval`, **không được gán collection mới** cho entity MANAGED. Sửa tại chỗ bằng `clear()` và `add`.
:::

::: {.tracuu codes="G3-S07a"}
:::

#### 6.3.5 Thay danh sách đúng cách: clear rồi thêm mới {.cot-loi}

**Bối cảnh.** Vẫn là chức năng "thay toàn bộ dòng hàng", lần này làm đúng theo mục 6.3.4.

**Khi nào xảy ra.** Có `orphanRemoval`; bạn `clear()` collection rồi thêm các item mới vào cùng collection đó.

**Khi nào không xảy ra.** Nếu item mới trùng `product_code` với item cũ trong cùng một lần flush (mục 6.5.3).

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder orderA = em.find(PurchaseOrder.class, orderId);
    orderA.getItems().clear();
    orderA.addItem(new OrderItem("INK", 2));
});
```

**Kết quả.** Lúc flush Hibernate INSERT item INK mới, rồi DELETE discount của PEN cũ và DELETE PEN cũ:

```sql
insert into g3_o1_orphan.order_item (order_id, product_code, quantity, id)
values (?, ?, ?, ?)

delete from g3_o1_orphan.item_discount where id=?
delete from g3_o1_orphan.order_item where id=?
```

| Bảng order_item | product_code | quantity |
|---|---|---|
| Trước | PEN | 1 |
| Sau | **INK** | **2** |

Thay đổi: PEN bị xóa, INK được thêm. Giữ nguyên: order cha. Ở đây INSERT chạy trước DELETE, và không sao vì hai item khác `product_code` (nếu trùng thì khác, xem mục 6.5.3).

**Vì sao.** `clear()` và `addItem` đều thao tác trên cùng `PersistentBag`, nên Hibernate vẫn so được với snapshot: PEN mất đi (mồ côi, bị xóa), INK xuất hiện (TRANSIENT trong collection có cascade `PERSIST`, được lưu lúc flush).

::: {.ghi-nho}
Thay toàn bộ phần tử: `getItems().clear()` rồi `add`. Giữ nguyên instance collection để Hibernate theo dõi được.
:::

::: {.tracuu codes="G3-S07b"}
:::

#### 6.3.6 items.clear(): xóa từng discount và item, order còn nguyên {.cot-loi}

**Bối cảnh.** Chức năng "xóa hết dòng hàng, giữ lại đơn": `order.getItems().clear()`. Bạn muốn biết chi phí SQL và chắc chắn order không bị đụng.

**Khi nào xảy ra.** Có `orphanRemoval`; bạn `clear()` một collection có nhiều phần tử.

**Khi nào không xảy ra.** Không có `orphanRemoval`: khi đó `clear()` không làm gì trong database (giống mục 6.2.1).

**Ví dụ.**

```java
// Order A-001 có 2 item, mỗi item 1 discount.
inTx(() -> em.find(PurchaseOrder.class, orderId).getItems().clear());
```

**Kết quả.** Cả hai item đều mồ côi: 2 câu DELETE `item_discount` và 2 câu DELETE `order_item`, tổng 4 câu, mỗi dòng một câu. Không có câu UPDATE hay DELETE nào cho `purchase_order`; order còn nguyên.

**Vì sao.** Mỗi phần tử mồ côi là một entity bị xóa, và mỗi entity là một câu DELETE riêng, kể cả discount của nó. Số câu SQL tăng theo số dòng con, giống cascade `REMOVE` (mục 5.5.3).

::: {.ghi-nho}
`clear()` với `orphanRemoval` xóa mọi con, từng dòng một. Cha không bị ảnh hưởng.
:::

::: {.tracuu codes="G3-S08"}
:::

### 6.4 orphanRemoval mà không có cascade {.bien-the}

Khác mục 6.3 đúng một điểm: `items` **bỏ `cascade`**, chỉ còn `orphanRemoval = true`.

```java
@OneToMany(mappedBy = "order", fetch = FetchType.LAZY,
        orphanRemoval = true)
private List<O2OrderItem> items = new ArrayList<>();
```

![Bảng order_item có khóa ngoại order_id tới purchase_order.id và ràng buộc unique trên order_id cùng product_code; bảng item_discount có khóa ngoại item_id tới order_item.id](img/g3-o2.png){width=90%}

**Ưu điểm.** Hầu như không có: cấu hình này chỉ hợp lý khi bạn cố ý muốn tự `persist` con nhưng vẫn muốn xóa tự động.

**Nhược điểm.** Dễ nhầm rằng `orphanRemoval` đủ để lưu con. Nó không lưu, và không báo lỗi khi bỏ sót.

**Nên dùng khi.** Hiếm khi. Thông thường nếu cần `orphanRemoval` thì cũng cần cascade `PERSIST`, nên dùng cùng `cascade = ALL`.

#### 6.4.1 Xóa cha: item vẫn bị xóa dù không có cascade REMOVE {.cot-loi}

**Bối cảnh.** Mapping chỉ có `orphanRemoval`, không có `CascadeType.REMOVE`. Bạn xóa order và tự hỏi liệu item có bị xóa hay vi phạm khóa ngoại như mục 5.5.1.

**Khi nào xảy ra.** Có `orphanRemoval = true`, không có cascade, và bạn `remove(order)`.

**Khi nào không xảy ra.** Nếu `orphanRemoval = false` và không có `REMOVE` (mục 5.5.1).

**Ví dụ.**

```java
inTx(() -> em.remove(em.find(PurchaseOrder.class, orderId)));
```

**Kết quả.** Item vẫn bị xóa: Hibernate chạy các câu SELECT để tải item và discount, rồi DELETE `order_item` và DELETE `purchase_order`. Cả hai bảng đều trống.

**Vì sao.** Đặc tả JPA quy định `orphanRemoval = true` ngầm kèm hành vi "remove con khi remove cha", dù không khai `CascadeType.REMOVE`. Hibernate làm theo: khi order bị remove, mọi item của nó được xem là mồ côi và bị xóa.

::: {.ghi-nho}
`orphanRemoval = true` đã bao hàm "xóa con khi xóa cha", không cần khai thêm `REMOVE`.
:::

::: {.tracuu codes="G3-S06a"}
:::

#### 6.4.2 persist order có item mới: item bị bỏ qua, không lỗi {.cot-loi}

**Bối cảnh.** Vì `orphanRemoval` làm "mọi thứ" về vòng đời con, bạn nghĩ nó cũng lo luôn việc lưu. Bạn persist order mới có một item mới trong collection.

**Khi nào xảy ra.** Có `orphanRemoval` nhưng không có cascade `PERSIST`; bạn `persist(order)` với item TRANSIENT.

**Khi nào không xảy ra.** Nếu có cascade `PERSIST` (mục 6.3), item được lưu.

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder orderA = new PurchaseOrder("A-001");
    orderA.addItem(new OrderItem("PEN", 1));
    em.persist(orderA);
    // Trạng thái: orderA là MANAGED; item vẫn TRANSIENT.
});
```

**Kết quả.** Chỉ có một câu INSERT, cho `purchase_order`. Không có câu INSERT nào cho `order_item` và không có lỗi. Bảng `order_item` trống.

**Vì sao.** `orphanRemoval` chỉ lo việc **xóa**. Việc **lưu** thuộc về `CascadeType.PERSIST`. Hiện tượng giống mục 5.2.1: collection inverse không sinh SQL, nên item chưa lưu bị bỏ qua lặng lẽ.

::: {.ghi-nho}
`orphanRemoval` không phải `PERSIST`. Nó không lưu con mới, và không báo lỗi khi bỏ sót con.
:::

::: {.tracuu codes="G3-S06b"}
:::

### 6.5 Chuyển con sang cha khác, và thay con trùng giá trị unique

Hai tình huống đòi hỏi hiểu **thứ tự** Hibernate làm việc lúc flush: chuyển một item từ đơn này sang đơn khác, và thay một item bằng item mới có cùng `product_code`. Cả hai dùng mapping có `orphanRemoval` và unique `(order_id, product_code)` của mục 6.3.

::: {.kien-thuc-nen}
**Thứ tự lệnh lúc flush.** Hibernate không chạy SQL theo thứ tự bạn viết trong code. Nó gom các thao tác vào một hàng đợi (ActionQueue) và chạy theo thứ tự cố định:

1. Tất cả `INSERT`
2. Tất cả `UPDATE`
3. Thao tác trên collection
4. Tất cả `DELETE`

Điều này đúng với mọi loại DELETE, kể cả DELETE do `orphanRemoval` hoặc do `em.remove`. Hệ quả quan trọng: **INSERT luôn chạy trước DELETE** trong cùng một lần flush, bất kể bạn viết `remove` trước hay `add` trước.
:::

#### 6.5.1 Chuyển item từ đơn A sang đơn B trong cùng transaction: chỉ UPDATE khóa ngoại {.cot-loi}

**Bối cảnh.** Một chức năng "tách đơn": chuyển dòng hàng PEN từ đơn A sang đơn B. Code gỡ khỏi A rồi thêm vào B. Có hai nỗi lo: item có bị xóa vì "mồ côi" khỏi A không, và discount của nó có mất không.

**Khi nào xảy ra.** Có `orphanRemoval`; gỡ khỏi collection của A rồi thêm vào collection của B trong cùng transaction.

**Khi nào không xảy ra.** Nếu bạn chỉ gỡ khỏi A mà không thêm vào B, item bị xóa (mục 6.3.1).

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder managedA = em.find(PurchaseOrder.class, orderIdA);
    PurchaseOrder managedB = em.find(PurchaseOrder.class, orderIdB);
    OrderItem itemToMove = managedA.getItems().get(0);

    managedA.removeItem(itemToMove);
    managedB.addItem(itemToMove);
});
```

**Kết quả.** Chỉ có một câu UPDATE `order_item`, không có câu DELETE nào (cả `order_item` lẫn `item_discount`):

```sql
update g3_o1_orphan.order_item
set order_id=?, product_code=?, quantity=?
where id=?
```

| Bảng | Trước | Sau |
|---|---|---|
| order_item PEN, cột order_id | id của A | **id của B** |
| item_discount | 1 dòng | 1 dòng (đi theo item) |

Thay đổi: `order_id` của PEN. Giữ nguyên: dòng item, discount, và cả hai order.

**Vì sao.** Khi flush, Hibernate duyệt từng collection. PEN không còn trong collection của A, nhưng lại nằm trong collection của B. Một phần tử đang thuộc collection khác có `orphanRemoval` được coi là **đã có chủ mới**, không phải mồ côi, nên không bị xóa. Còn `item.order` đã đổi từ A sang B, nên dirty checking sinh UPDATE `order_id`.

::: {.ghi-nho}
Chuyển con sang cha khác trong cùng transaction an toàn: chỉ UPDATE khóa ngoại, con và các con của con không mất.
:::

::: {.tracuu codes="G3-S09a"}
:::

#### 6.5.2 Đổi thẳng item.setOrder(orderB): cũng chỉ UPDATE {.cot-loi}

**Bối cảnh.** Có cách ngắn hơn để chuyển item: chỉ đổi owning side `item.setOrder(orderB)`, không đụng collection của A.

**Khi nào xảy ra.** Có `orphanRemoval`; bạn chỉ gán lại `item.order`.

**Khi nào không xảy ra.** Tình huống này an toàn trong mọi cấu hình, nhưng collection `items` của A trong bộ nhớ sẽ không đồng bộ (vẫn chứa item) cho tới khi tải lại.

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder managedA = em.find(PurchaseOrder.class, orderIdA);
    PurchaseOrder managedB = em.find(PurchaseOrder.class, orderIdB);
    managedA.getItems().get(0).setOrder(managedB);
});
```

**Kết quả.** Một câu UPDATE `order_item`, không có câu DELETE nào. Cột `order_id` bằng id của B.

**Vì sao.** Collection của A không bị sửa, nên snapshot không đổi và không có mồ côi nào. Khóa ngoại do owning side quyết định, nên đổi `item.order` là đủ để UPDATE. Việc đồng bộ danh sách trong bộ nhớ của hai order là việc của code gọi.

::: {.ghi-nho}
Đổi owning side (`item.setOrder`) là cách ghi khóa ngoại trực tiếp nhất. Collection chỉ để đọc và để `orphanRemoval` theo dõi.
:::

::: {.tracuu codes="G3-S09b"}
:::

#### 6.5.3 Thay item bằng item mới cùng product_code: vi phạm unique {.cot-loi}

**Bối cảnh.** Khách hàng đổi số lượng PEN từ 1 thành 5. Code chọn cách "xóa dòng cũ, thêm dòng mới": `removeItem(old)` rồi `addItem(new OrderItem("PEN", 5))`. Về logic, đây là thay thế sạch sẽ và nhiều người tin rằng `orphanRemoval` chạy trước INSERT nên không đụng ràng buộc unique `(order_id, product_code)`. Thực tế thì ngược lại, và lỗi xuất hiện ở database khi commit.

**Khi nào xảy ra.** Có `orphanRemoval`; gỡ item cũ và thêm item mới có cùng `product_code` trong cùng một lần flush; bảng có unique trên `(order_id, product_code)`.

**Khi nào không xảy ra.** Nếu `product_code` khác nhau (mục 6.3.5), hoặc nếu bạn flush giữa hai bước (mục 6.5.5), hoặc nếu bạn sửa chính item cũ thay vì tạo mới.

**Ví dụ.**

```java
assertThatThrownBy(() -> inTx(() -> {
    PurchaseOrder managedOrder = em.find(PurchaseOrder.class, orderId);
    OrderItem oldPen = managedOrder.getItems().get(0);

    managedOrder.removeItem(oldPen);
    managedOrder.addItem(new OrderItem("PEN", 5));
    // Cả hai thay đổi nằm trong cùng một lần flush (lúc commit).
})).isInstanceOf(DataIntegrityViolationException.class);
```

**Kết quả.** Hibernate chạy câu INSERT cho PEN mới **trước**, và PostgreSQL từ chối vì đã có dòng `(order_id, 'PEN')`. Câu DELETE PEN cũ chưa kịp chạy. Spring ném `DataIntegrityViolationException`, transaction rollback.

```sql
insert into g3_o1_orphan.order_item (order_id, product_code, quantity, id)
values (?, ?, ?, ?)
```

| Bảng order_item | Sau khi chạy |
|---|---|
| Số dòng | 1 (rollback) |
| quantity | **1** (item cũ còn nguyên, item 5 chưa được lưu) |

::: {.khac-du-doan codes="G3-S10a"}
**Điều nhiều người nghĩ:** `orphanRemoval` chạy trước INSERT, nên thay item cùng `product_code` trong một lần flush là hợp lệ.

**Thực tế:** INSERT item mới chạy trước, DELETE orphan chạy sau, nên vi phạm unique.

Đã kiểm chứng trên Hibernate 7.4.15.Final.
:::

**Vì sao.** Hãy theo dõi. Lúc flush, hàng đợi có một INSERT (item mới) và một DELETE (item mồ côi). Thứ tự cố định của ActionQueue là INSERT trước, DELETE sau (xem khung ở đầu mục 6.5), và DELETE do `orphanRemoval` đứng cuối giống DELETE do `em.remove`. Lúc INSERT PEN mới chạy, dòng PEN cũ vẫn còn trong bảng, nên cặp `(order_id, 'PEN')` bị trùng. Hibernate không tự sắp lại thứ tự để tránh unique của bạn.

**Cách tránh.** Có ba cách: flush giữa hai bước (mục 6.5.5), sửa chính item cũ (`itemPen.setQuantity(5)`: một câu UPDATE, không có INSERT), hoặc dùng unique có điều kiện hay deferrable ở database nếu thật sự cần thay thế.

::: {.ghi-nho}
INSERT luôn chạy trước DELETE trong một lần flush. "Xóa cũ rồi thêm mới trùng khóa unique" trong cùng flush sẽ vi phạm ràng buộc. Cách đơn giản nhất: sửa item cũ thay vì thay mới.
:::

::: {.tracuu codes="G3-S10a"}
:::

#### 6.5.4 em.remove rồi persist trùng khóa: cũng vi phạm unique {.cot-loi}

**Bối cảnh.** Bạn nghĩ lỗi ở mục 6.5.3 là do `orphanRemoval`, nên chuyển sang xóa tường minh bằng `em.remove(old)` rồi `persist` item mới. Kết quả có khác không?

**Khi nào xảy ra.** Cùng điều kiện mục 6.5.3 nhưng dùng `em.remove` thay cho `orphanRemoval`.

**Khi nào không xảy ra.** Khi có flush giữa hai bước (mục 6.5.5).

**Ví dụ.**

```java
assertThatThrownBy(() -> inTx(() -> {
    PurchaseOrder managedOrder = em.find(PurchaseOrder.class, orderId);
    OrderItem oldPen = managedOrder.getItems().get(0);

    managedOrder.removeItem(oldPen);
    em.remove(oldPen);
    managedOrder.addItem(new OrderItem("PEN", 5));
})).isInstanceOf(DataIntegrityViolationException.class);
```

**Kết quả.** Vi phạm unique, giống hệt mục 6.5.3. Không có câu DELETE nào chạy được; item cũ còn nguyên sau khi rollback.

**Vì sao.** `em.remove` cũng chỉ là một DELETE xếp hàng chờ, và DELETE luôn đứng sau INSERT. Thứ tự flush không phụ thuộc cơ chế xóa. Lưu ý: ở ví dụ này item cũ được gỡ khỏi collection trước khi `em.remove`. Nếu nó còn trong collection có cascade `PERSIST`, persist-on-flush có thể "cứu" nó khỏi bị xóa.

::: {.ghi-nho}
Thay `orphanRemoval` bằng `em.remove` không đổi thứ tự: INSERT vẫn đi trước DELETE.
:::

::: {.tracuu codes="G3-S10b"}
:::

#### 6.5.5 Cách sửa: flush ngay sau khi gỡ item cũ {.cot-loi}

**Bối cảnh.** Bạn bắt buộc phải thay item mới cùng `product_code` (ví dụ do quy tắc nghiệp vụ) và muốn ép DELETE chạy trước INSERT.

**Khi nào xảy ra.** Bạn gọi `em.flush()` giữa `removeItem` và `addItem`.

**Khi nào không xảy ra.** Nếu không flush giữa hai bước (mục 6.5.3).

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder managedOrder = em.find(PurchaseOrder.class, orderId);
    managedOrder.removeItem(managedOrder.getItems().get(0));

    em.flush();     // DELETE PEN cũ chạy NGAY tại đây

    managedOrder.addItem(new OrderItem("PEN", 5));
    // INSERT PEN mới chạy lúc commit.
});
```

**Kết quả.** Câu DELETE chạy trước, rồi câu INSERT. Thành công. Bảng `order_item` còn đúng một dòng là PEN mới (số lượng 5).

**Vì sao.** `em.flush()` ép Hibernate chạy hàng đợi ngay: lúc đó chỉ có DELETE trong hàng đợi, nên nó chạy trước. Sau flush, PEN cũ đã biến mất; lúc commit, INSERT của PEN mới không còn gì để va chạm. Cái giá: thêm một lần flush, và mọi thao tác chưa hoàn chỉnh trước đó cũng bị ghi.

::: {.ghi-nho}
Muốn DELETE chạy trước INSERT trong cùng transaction, gọi `em.flush()` giữa hai bước. Đây là cách ép thứ tự có chủ ý.
:::

::: {.tracuu codes="G3-S10c"}
:::

### 6.6 Merge đồ thị DETACHED bỏ bớt con

#### 6.6.1 merge: item vắng mặt trong đồ thị bị xóa {.mo-rong}

**Bối cảnh.** Màn hình sửa đơn hàng: bản DETACHED của order có hai item, người dùng xóa một dòng trong giao diện, và code `merge` đồ thị còn một item vào database. Bạn muốn biết dòng bị bỏ có bị xóa không.

**Kết quả.** Có. `merge` nạp lại order cùng collection từ database, copy state từ bản DETACHED, rồi so với snapshot. Item vắng mặt trở thành mồ côi và bị DELETE lúc flush; item còn lại không bị đụng. Hibernate chạy một câu SELECT (order kèm item), một câu SELECT discount và một câu DELETE `order_item`. Bảng `order_item` còn đúng một dòng.

Điều kiện để chạy đúng là `cascade` có `MERGE` (qua `ALL`) và `orphanRemoval`: `MERGE` để merge lan xuống item (mục 5.4), `orphanRemoval` để item vắng mặt bị xóa.

::: {.ghi-nho}
Với `MERGE` và `orphanRemoval`, merge đồ thị DETACHED đã bỏ bớt con sẽ xóa các con vắng mặt. Hữu ích cho form chỉnh sửa, nhưng nguy hiểm nếu đồ thị gửi lên bị thiếu do lỗi.
:::

::: {.tracuu codes="G3-S11"}
:::

### 6.7 Bốn chiến lược xóa Customer cùng toàn bộ order và item {.bien-the}

Tình huống phổ biến: xóa một khách hàng, kéo theo mọi đơn hàng và dòng hàng của họ. Dữ liệu dùng để so sánh: một customer, hai order, mỗi order hai item (tổng 7 dòng). Bốn chiến lược:

1. **Cascade REMOVE theo chuỗi:** `Customer.orders` và `PurchaseOrder.items` đều có `cascade = REMOVE`.
2. **`@OnDelete(CASCADE)`:** khóa ngoại có `ON DELETE CASCADE`; không cascade JPA.
3. **Bulk delete từ dưới lên:** service tự viết ba câu JPQL DELETE: item, rồi order, rồi customer.
4. **Không chiến lược nào:** mapping trần.

```java
// Chiến lược 1: cascade ở cả hai tầng, đặt ở INVERSE side.
@OneToMany(mappedBy = "customer", cascade = CascadeType.REMOVE)
private List<Order> orders = new ArrayList<>();

// Chiến lược 2: @OnDelete ở OWNING side của từng khóa ngoại.
@ManyToOne(fetch = FetchType.LAZY)
@OnDelete(action = OnDeleteAction.CASCADE)
@JoinColumn(name = "customer_id")
private Customer customer;
```

![Bảng purchase_order có khóa ngoại customer_id tới customer.id; bảng order_item có khóa ngoại order_id tới purchase_order.id; ba bảng tạo thành chuỗi cha, con, cháu](img/g9-s06a.png){width=90%}

**Ưu điểm.** Mỗi chiến lược có chỗ mạnh: cascade REMOVE giữ nhất quán và chạy được callback; `@OnDelete` ít SQL nhất; bulk delete nhanh và rõ ràng nhất.

**Nhược điểm.** Cascade REMOVE tải toàn bộ rồi xóa từng dòng; `@OnDelete` làm bộ nhớ lệch với database; bulk delete bỏ qua persistence context và mọi callback; không chiến lược nào thì bị khóa ngoại chặn.

**Nên dùng khi.** Cascade REMOVE cho aggregate nhỏ. `@OnDelete` hoặc bulk delete khi khối lượng lớn và bạn kiểm soát được hệ quả với bộ nhớ.

#### 6.7.1 Cascade REMOVE theo chuỗi: tải hết rồi DELETE từng dòng {.cot-loi}

**Bối cảnh.** Cách mà hầu hết mọi người thử đầu tiên: gọi `em.remove(customer)` và để cascade lo phần còn lại. Nó đúng, nhưng bạn nên biết nó tốn bao nhiêu.

**Khi nào xảy ra.** Cả hai tầng có `cascade = REMOVE`.

**Khi nào không xảy ra.** Khi chuỗi không có cascade ở mọi tầng, hoặc dùng `@OnDelete` hay bulk delete.

**Ví dụ.**

```java
@Transactional
public void deleteByCascadeRemove(Long customerId) {
    Customer customer = em.find(Customer.class, customerId);
    em.remove(customer);
    // Customer, order, item đều chuyển MANAGED thành REMOVED.
}
```

**Kết quả.** Hibernate phải tải trước: một câu SELECT customer, một câu SELECT order, hai câu SELECT item (mỗi order một câu). Sau đó lúc commit gửi 4 câu DELETE `order_item`, 2 câu DELETE `purchase_order` và 1 câu DELETE `customer`, theo thứ tự con trước cha. Ba bảng đều trống sau khi chạy.

**Vì sao.** Cascade chạy qua từng entity: muốn đánh dấu REMOVED thì phải tải entity. Mỗi entity REMOVED là một câu DELETE theo khóa chính. Con trước cha vì khóa ngoại.

::: {.ghi-nho}
Cascade REMOVE theo chuỗi: tải hết, rồi xóa từng dòng. Số câu SQL tỉ lệ với số dòng.
:::

::: {.tracuu codes="G9-S06a"}
:::

#### 6.7.2 OnDelete(CASCADE): một câu DELETE customer, PostgreSQL xóa phần còn lại {.cot-loi}

**Bối cảnh.** Khối lượng lớn: một khách hàng có hàng trăm đơn. Bạn muốn xóa mà không tải gì.

**Khi nào xảy ra.** `@OnDelete(CASCADE)` ở `PurchaseOrder.customer` và `OrderItem.order`; không cascade JPA.

**Khi nào không xảy ra.** Khi khóa ngoại do migration tạo không có `ON DELETE CASCADE`.

**Ví dụ.**

```java
@Transactional
public void deleteByOnDelete(Long customerId) {
    Customer customer = em.find(Customer.class, customerId);
    em.remove(customer);
}
```

**Kết quả.** Chỉ có hai câu SQL: SELECT customer rồi DELETE customer. Không có câu DELETE nào cho `purchase_order` hay `order_item`. Ba bảng vẫn trống, vì PostgreSQL xóa dây chuyền order rồi item.

**Vì sao.** Hai khóa ngoại đều có `on delete cascade`: xóa customer thì xóa order, xóa order thì xóa item, hoàn toàn trong database. Hibernate không biết con đã biến mất (mục 6.7.5).

::: {.ghi-nho}
`@OnDelete` cho số SQL ít nhất: chỉ SELECT và DELETE cha. Đổi lại Hibernate không biết gì về con.
:::

::: {.tracuu codes="G9-S06b"}
:::

#### 6.7.3 Bulk delete từ dưới lên: ba câu DELETE, không SELECT {.cot-loi}

**Bối cảnh.** Mapping trần, không cascade, không `@OnDelete`. Service tự viết ba câu JPQL DELETE từ dưới lên.

**Khi nào xảy ra.** Bạn tự xóa theo thứ tự item, order, customer bằng JPQL.

**Khi nào không xảy ra.** Nếu thứ tự sai (xóa customer trước) thì vi phạm khóa ngoại.

**Ví dụ.**

```java
@Transactional
public void deleteByBulkBottomUp(Long customerId) {
    em.createQuery("delete from OrderItem i where i.order.id in "
            + "(select o.id from PurchaseOrder o "
            + "where o.customer.id = :customerId)")
            .setParameter("customerId", customerId).executeUpdate();
    em.createQuery("delete from PurchaseOrder o "
            + "where o.customer.id = :customerId")
            .setParameter("customerId", customerId).executeUpdate();
    em.createQuery("delete from Customer c where c.id = :customerId")
            .setParameter("customerId", customerId).executeUpdate();
}
```

**Kết quả.** Đúng ba câu DELETE, không câu SELECT nào. Ba bảng trống.

**Vì sao.** JPQL DELETE chạy thẳng xuống database, không tải entity. Phải đi từ dưới lên vì khóa ngoại. Đổi lại, nó bỏ qua persistence context và mọi callback; các entity đã tải (nếu có) trở thành entity "ma".

::: {.ghi-nho}
Bulk delete từ dưới lên: ít SQL và dễ đọc, nhưng bạn tự chịu trách nhiệm về thứ tự và về persistence context.
:::

::: {.tracuu codes="G9-S06c"}
:::

#### 6.7.4 Không chiến lược nào: DELETE customer vi phạm khóa ngoại {.cot-loi}

**Bối cảnh.** Mapping trần và chỉ `em.remove(customer)`. Đây là trạng thái mặc định nếu bạn chưa nghĩ tới chuyện xóa.

**Khi nào xảy ra.** Không cascade, không `@OnDelete`, và customer còn order.

**Khi nào không xảy ra.** Nếu customer không có order.

**Ví dụ.**

```java
@Transactional
public void deleteWithoutStrategy(Long customerId) {
    Customer customer = em.find(Customer.class, customerId);
    em.remove(customer);
}
```

**Kết quả.** PostgreSQL từ chối câu DELETE customer vì còn order trỏ tới, Spring ném `DataIntegrityViolationException` và transaction rollback. Dữ liệu nguyên vẹn: 1 customer, 2 order, 4 item.

**Vì sao.** Khóa ngoại bảo vệ dữ liệu. Giống mục 5.5.1.

::: {.ghi-nho}
Không chiến lược nào thì database chặn. Đó là kết quả an toàn, nhưng không phải tính năng xóa.
:::

::: {.tracuu codes="G9-S06d"}
:::

#### 6.7.5 Trạng thái entity sau khi xóa: REMOVED hay MANAGED ma {.cot-loi}

**Bối cảnh.** Sau khi `remove(customer)`, đoạn code phía sau còn dùng một order đã tải. Trạng thái của order đó khác nhau giữa hai chiến lược.

**Khi nào xảy ra.** Order đã được tải vào persistence context trước khi xóa customer.

**Khi nào không xảy ra.** Nếu order chưa được tải.

**Ví dụ.**

```java
inTx(() -> {
    Customer customerB = em.find(Customer.class, customerIdB);
    PurchaseOrder orderOfB = customerB.getOrders().get(0);   // đã tải
    em.remove(customerB);
    // Cascade REMOVE: order là REMOVED. @OnDelete: order vẫn là MANAGED.
});
```

**Kết quả.** Với cascade REMOVE, customer và cả order đã tải đều REMOVED. Với `@OnDelete`, chỉ customer REMOVED; order đã tải **vẫn MANAGED** dù dòng của nó sắp bị database xóa. Cuối cùng cả hai database đều trống.

**Vì sao.** Cascade JPA chạy trong Java nên đánh dấu được từng entity. `@OnDelete` chạy trong database, Hibernate không được báo. Dùng tiếp entity "ma" này (ví dụ gán lại làm cha của một item mới) gây lỗi khó hiểu.

::: {.ghi-nho}
`@OnDelete` để lại entity MANAGED "ma" trong persistence context. Sau khi xóa bằng cách này, đừng dùng tiếp các entity con đã tải.
:::

::: {.tracuu codes="G9-S06e"}
:::

**Bảng so sánh bốn chiến lược:**

| | Cascade REMOVE | `@OnDelete` | Bulk từ dưới lên | Không chiến lược |
|---|---|---|---|---|
| SQL | 4 SELECT, 7 DELETE | 1 SELECT, 1 DELETE | 3 DELETE | 1 SELECT, DELETE bị từ chối |
| Thứ tự | Con trước cha | Do database | Con trước cha, bạn tự viết | Không có |
| Hibernate biết đã xóa gì | Có | Không | Không | Không |
| Callback của entity | Chạy | Không | Không | Không |
| Entity đã tải | REMOVED | MANAGED (ma) | MANAGED (ma) | Không đổi |
| Kết quả | Xóa hết | Xóa hết | Xóa hết | Rollback, dữ liệu nguyên |

Chương 7 chuyển sang quan hệ một-một: nơi khóa ngoại có thể nằm ở một trong hai bảng, và có những cách chia sẻ khóa chính.
