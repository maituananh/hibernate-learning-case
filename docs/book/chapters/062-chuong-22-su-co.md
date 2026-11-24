## Chương 22. Bốn sự cố production và cách xử lý

Các chương trước đi từ **annotation** đến **hậu quả**: bạn biết mapping nào sinh ra SQL gì. Ngoài đời, thứ bạn nhận được đi theo chiều ngược lại. Không ai gọi bạn lúc ba giờ sáng để nói "`@ManyToOne` thiếu `fetch`". Họ nói "trang danh sách đơn hàng chậm quá", "kho còn âm", "khách nhận hai email". Kỹ năng quan trọng nhất của người làm việc với Hibernate lâu năm là đi từ **triệu chứng** về **nguyên nhân**, bằng bằng chứng chứ không bằng linh cảm.

Chương này tái hiện bốn sự cố điển hình, mỗi sự cố có hai phiên bản code chạy trên PostgreSQL thật: bản có lỗi và bản đã sửa. Mỗi sự cố đi qua bốn bước giống nhau:

1. **Tái hiện:** dựng lại triệu chứng bằng test, để có thứ chạy đi chạy lại được.
2. **Chẩn đoán:** tìm con số hoặc câu SQL **chứng minh** nguyên nhân. Đây là bước khó nhất và đáng luyện nhất.
3. **Sửa:** thay đổi nhỏ nhất đủ làm bất biến đúng trở lại.
4. **Rào chắn:** viết test sao cho lỗi này không thể quay lại mà không ai biết. Rào chắn phải **đỏ trên bản lỗi và xanh trên bản đã sửa**; rào chắn chưa từng đỏ là rào chắn chưa được chứng minh.

| Sự cố | Triệu chứng người dùng báo | Nguyên nhân | Chương kiến thức liên quan |
|---|---|---|---|
| 22.1 | Trang danh sách đơn hàng chậm dần | N+1 hai tầng | Chương 11, Chương 12 |
| 22.2 | Tồn kho sai sau flash sale | Lost update | Chương 17 |
| 22.3 | Job gửi email chạy hai lần | Hai worker nhận cùng một việc | Chương 17 |
| 22.4 | Import file lớn chậm dần | Id IDENTITY và persistence context phình | Chương 18 |

Cả bốn sự cố có chung một đặc điểm đáng nhớ: **mapping của chúng không sai** hoặc sai theo cách mà code vẫn chạy ra kết quả đúng. Dữ liệu trả về đúng ở sự cố 22.1, chỉ đếm SQL mới thấy lỗi. Không có exception nào ở sự cố 22.2 và 22.3. Sự cố 22.4 ghi đủ dữ liệu. Lỗi nằm ở **cách dùng**, nên review code đọc mapping không thấy.

**Trong JPA thuần.** Phần lớn code của chương là JPA chuẩn: `@Version`, JPQL `select new`, `size()`, `PESSIMISTIC_WRITE`, `persist`, `flush`, `clear`. Các điểm riêng của Hibernate (giá trị đặc biệt của hint `lock.timeout`, `session.setJdbcBatchSize`, `session.getStatistics`) được ghi chú ngay tại mục dùng chúng.

### 22.1 Danh sách đơn hàng chậm dần

**Triệu chứng.** Màn hình "Danh sách đơn hàng" lúc mới ra mắt tải rất nhanh. Vài tháng sau, khi số đơn tăng, người dùng than phiền trang chậm, và chậm hơn nữa mỗi khi có đợt khuyến mãi. Không có lỗi nào trong log, dữ liệu hiển thị hoàn toàn đúng.

**Nguyên nhân.** Service nạp cả entity `PurchaseOrder` rồi duyệt từng đơn để lấy tên khách và đếm số dòng hàng. Mọi quan hệ đều khai `LAZY` đúng như khuyến nghị, nhưng mỗi lần duyệt chạm vào quan hệ LAZY là một câu SELECT: một câu cho khách, một câu cho collection dòng hàng. Với trang N đơn, tổng cộng có 2 + 2N câu SELECT. Đây là N+1 hai tầng (Chương 11).

**Chương kiến thức liên quan.** N+1 và LAZY ở Chương 11 (mục 11.3, 11.4, 11.8); `Page`, count query và DTO projection ở Chương 12 (mục 12.3, 12.5).

![Sơ đồ bảng customer, purchase_order và order_item của sự cố danh sách đơn](img/g14-i1.png){width=70%}

Dữ liệu thử gồm 30 đơn, mỗi đơn có một khách riêng và 3 dòng hàng.

#### 22.1.1 Tái hiện: trang 10 đơn tốn 22 câu SELECT {.cot-loi}

**Bối cảnh.** Trang danh sách chỉ cần 10 dòng, mỗi dòng gồm số đơn, tên khách và số dòng hàng. Code của service trông rất vô hại, thậm chí đúng "sách giáo khoa": dùng `findAll(Pageable)` có sẵn của Spring Data rồi đổi mỗi entity thành một dòng hiển thị. Không ai nghĩ một vòng `map` lại trả về 22 chuyến đi tới database.

**Khi nào xảy ra.** Service đọc entity rồi chạm vào quan hệ LAZY của từng entity trong một vòng lặp, ở bất kỳ nơi nào số lần lặp tăng theo dữ liệu hoặc cỡ trang.

**Khi nào không xảy ra.** Khi service đọc thẳng DTO bằng một query (mục 22.1.3), hoặc khi quan hệ đã được tải sẵn bằng `join fetch` (Chương 11).

**Ví dụ.** Repository không viết query nào, và service duyệt từng đơn:

```java
public interface I1BuggyOrderRepository
        extends JpaRepository<I1BuggyOrder, Long> {
}

@Service
public class I1BuggyOrderListService {

    private final I1BuggyOrderRepository orders;

    public I1BuggyOrderListService(I1BuggyOrderRepository orders) {
        this.orders = orders;
    }

    // readOnly vì chỉ đọc. Cần transaction để quan hệ LAZY còn đọc được
    // (open-in-view đã tắt).
    @Transactional(readOnly = true)
    public Page<I1BuggyOrderRow> list(Pageable pageable) {
        return orders.findAll(pageable).map(order -> new I1BuggyOrderRow(
            order.getOrderNo(),
            order.getCustomer().getName(),  // LAZY proxy: chạm vào là SELECT
            order.getItems().size()));      // LAZY collection: size() là SELECT
    }
}
```

Test gọi `buggyService.list(PageRequest.of(0, 10, Sort.by("id")))` rồi đếm SQL.

**Kết quả.** Chạy đúng **22 câu SELECT**: 1 câu lấy trang, 1 câu count, 10 câu SELECT `customer` (mỗi đơn một khách riêng) và 10 câu SELECT `order_item` (mỗi đơn một collection). Dữ liệu trả về **hoàn toàn đúng**: 10 dòng, tổng 30 đơn, tên khách "Customer-1" và 3 dòng hàng ở dòng đầu.

```sql
select ibo1_0.id, ibo1_0.customer_id, ibo1_0.order_no
from g14_i1_buggy.purchase_order ibo1_0
order by ibo1_0.id
offset ? rows
fetch first ? rows only

select count(ibo1_0.id)
from g14_i1_buggy.purchase_order ibo1_0

select ibc1_0.id, ibc1_0.name
from g14_i1_buggy.customer ibc1_0
where ibc1_0.id=?

select i1_0.order_id, i1_0.id, i1_0.product_name, i1_0.quantity
from g14_i1_buggy.order_item i1_0
where i1_0.order_id=?

-- hai câu SELECT customer và order_item này lặp lại 10 lần,
-- mỗi lần với một id khác
```

Trạng thái entity: mỗi `PurchaseOrder` là MANAGED trong suốt transaction. `getCustomer()` trả về proxy chưa nạp, và `getName()` kích hoạt SELECT.

**Vì sao.** Hãy đi theo vòng `map`. Câu SELECT đầu lấy 10 đơn, mỗi đơn mang theo `customer_id` nhưng chưa có thông tin khách, nên Hibernate đặt một proxy `LAZY` vào chỗ `customer`. `order.getCustomer().getName()` chạm vào proxy lần đầu nên Hibernate chạy `select ... from customer where id=?`. Mười đơn có mười khách khác nhau nên không có khách nào được dùng lại từ persistence context, mười câu SELECT. Tương tự `order.getItems().size()` buộc collection `LAZY` phải nạp, mười đơn là mười câu SELECT `order_item`. Cộng với câu lấy trang và câu count của `Page`: 2 + 10 + 10 = 22.

Điều làm lỗi này nguy hiểm là **mapping hoàn toàn đúng quy tắc**: `fetch = LAZY` ở mọi quan hệ, như Chương 11 khuyên. LAZY chỉ trì hoãn việc tải, nó không giảm số lần tải khi bạn vẫn chạm vào từng cái.

**Cách tránh.** Màn hình danh sách đọc DTO bằng một query (mục 22.1.3). Không lặp qua entity để chạm vào quan hệ LAZY.

::: {.ghi-nho}
Mapping LAZY đúng chưa đủ: duyệt N entity rồi chạm quan hệ LAZY của từng entity vẫn tốn N câu SELECT mỗi quan hệ. Dữ liệu đúng, chỉ có số câu SQL sai.
:::

::: {.tracuu codes="G14-I1-S01"}
:::

#### 22.1.2 Chẩn đoán: số SELECT bằng đúng cỡ trang {.cot-loi}

**Bối cảnh.** Người dùng nói "trang chậm". Nguyên nhân có thể là một query chậm (thiếu index, bảng quá lớn) hoặc là N+1. Hai nguyên nhân này cần hai cách sửa hoàn toàn khác nhau, nên bạn phải phân biệt được trước khi sửa. Đoán sai là mất cả ngày thêm index vào bảng mà vấn đề chưa hề nằm ở đó.

**Khi nào xảy ra.** Bạn nghi ngờ N+1 và muốn có bằng chứng, không chỉ nghi ngờ.

**Khi nào không xảy ra.** Nếu số câu SELECT **không đổi** khi đổi cỡ trang, nguyên nhân không phải N+1 mà là một câu query riêng lẻ chậm, cần xem kế hoạch thực thi (`explain`) của câu đó.

**Ví dụ.** Chạy cùng một use case với cỡ trang 5, rồi cỡ trang 10, và đếm số câu SELECT vào từng bảng:

```java
sql.reset();
buggyService.list(PageRequest.of(0, 5, Sort.by("id")));
sql.assertSelects("customer", 5);
sql.assertSelects("order_item", 5);

sql.reset();
buggyService.list(PageRequest.of(0, 10, Sort.by("id")));
sql.assertSelects("customer", 10);
sql.assertSelects("order_item", 10);
sql.assertSelects("purchase_order", 2); // trang và count
```

**Kết quả.** Với cỡ trang 5, có 5 câu SELECT vào `customer` và 5 câu vào `order_item`. Với cỡ trang 10, mỗi bảng có 10 câu. Riêng bảng chính `purchase_order` luôn chỉ có 2 câu (trang và count) bất kể cỡ trang.

| Cỡ trang | SELECT vào customer | SELECT vào order_item | SELECT vào purchase_order |
|---|---|---|---|
| 5 | **5** | **5** | 2 |
| 10 | **10** | **10** | 2 |

**Vì sao.** Con số **tăng tỉ lệ với N** là dấu vân tay của N+1. Một query chậm thì số câu không đổi, chỉ thời gian của câu đó đổi. Ở đây số câu tăng đúng bằng cỡ trang, và phần "N" nằm ở các bảng phụ (`customer`, `order_item`), còn bảng chính chỉ có hai câu cố định. Đó cũng là cách nhận ra nó trong log SQL: một câu `select ... from customer where id=?` lặp lại nhiều lần, chỉ khác giá trị tham số.

Quy trình chẩn đoán nên theo thứ tự: **đếm SQL trước, đoán sau**. Bật log SQL (hoặc dùng bộ đếm như `SqlCaptor` của lab), chạy use case một lần, nhìn xem có câu nào lặp lại không.

**Cách tránh.** Đừng sửa khi chưa có con số. "Thêm index cho nhanh" khi nguyên nhân là N+1 chỉ làm mỗi câu SELECT lặp lại nhanh hơn một chút, tổng số vẫn tăng theo N.

::: {.ghi-nho}
Muốn phân biệt N+1 với một query chậm: đổi cỡ trang rồi đếm lại. Số SELECT vào bảng phụ bằng đúng cỡ trang là N+1.
:::

::: {.tracuu codes="G14-I1-S02"}
:::

#### 22.1.3 Sửa: đọc DTO bằng một query, còn 2 câu SELECT {.cot-loi}

**Bối cảnh.** Bạn đã biết nguyên nhân và muốn sửa mà màn hình **vẫn hiện đúng dữ liệu cũ**, không đổi giao diện, không đổi API. Đổi mapping sang `EAGER` không giải quyết (nó chỉ đổi N+1 sang dạng khác, Chương 11). Cách đúng là đừng nạp entity: danh sách chỉ cần ba giá trị, nên hãy hỏi đúng ba giá trị đó.

**Khi nào xảy ra.** Use case chỉ đọc để hiển thị (danh sách, báo cáo), không cần sửa entity.

**Khi nào không xảy ra.** Nếu use case cần entity để **sửa** rồi lưu, DTO không phù hợp; khi đó dùng `join fetch` hoặc `@EntityGraph` (Chương 11) và đặt ngân sách SQL lớn hơn.

**Ví dụ.** Cùng entity, chỉ thay repository và service:

```java
public interface I1FixedOrderRepository
        extends JpaRepository<I1FixedOrder, Long> {

    @Query(value = """
        select new com.example.hibernatelab.g14_lead.i1_fixed.I1FixedOrderRow(
            o.orderNo, c.name, size(o.items))
        from G14I1F_Order o join o.customer c
        order by o.id
        """, countQuery = "select count(o) from G14I1F_Order o")
    Page<I1FixedOrderRow> findOrderRows(Pageable pageable);
}

@Service
public class I1FixedOrderListService {

    @Transactional(readOnly = true)
    public Page<I1FixedOrderRow> list(Pageable pageable) {
        return orders.findOrderRows(pageable);
    }
}
```

**Kết quả.** Đúng **2 câu SELECT** bất kể cỡ trang: một câu lấy trang (đã có sẵn `join` sang khách và subquery đếm dòng hàng) và một câu count. Dữ liệu trả về giống hệt bản có lỗi: 10 dòng, tổng 30 đơn, dòng đầu là đơn "ORD-1", khách "Customer-1", 3 dòng hàng.

```sql
select ifo1_0.order_no, c1_0.name,
    (select count(*)
     from g14_i1_fixed.order_item i1_0
     where ifo1_0.id=i1_0.order_id)
from g14_i1_fixed.purchase_order ifo1_0
join g14_i1_fixed.customer c1_0 on c1_0.id=ifo1_0.customer_id
order by ifo1_0.id
fetch first ? rows only

select count(ifo1_0.id)
from g14_i1_fixed.purchase_order ifo1_0
```

Từ 22 câu xuống 2 câu: giảm 20 câu, và quan trọng hơn, số câu không còn phụ thuộc cỡ trang.

**Vì sao.** Câu `select new ...Row(o.orderNo, c.name, size(o.items))` bảo Hibernate đọc đúng ba giá trị và đưa thẳng vào DTO. Không có entity nào vào persistence context, nên không có proxy, không có lazy loading, không có gì để chạm vào sau đó. `join o.customer c` lấy tên khách trong cùng câu SELECT, và `size(o.items)` được Hibernate dịch thành một subquery đếm ngay trong danh sách cột (không nạp collection). `countQuery` khai riêng để câu count đếm đơn hàng, không join và không bị lệch (Chương 12).

**Trong JPA thuần.** Mọi thứ ở đây là JPA chuẩn: `select new`, `size()`, `join`. `countQuery` là thuộc tính của `@Query` của Spring Data, không phải của JPA.

::: {.ghi-nho}
Màn hình danh sách đọc DTO bằng một query: số câu SQL cố định theo use case, không tăng theo cỡ trang, và dữ liệu hiển thị không đổi.
:::

::: {.tracuu codes="G14-I1-S03"}
:::

#### 22.1.4 Rào chắn: ngân sách 2 SELECT {.cot-loi}

**Bối cảnh.** Lỗi đã sửa, nhưng ba tháng sau có người "thêm nhanh" cột trạng thái bằng cách quay lại duyệt entity. Review code đọc mapping vẫn thấy `LAZY` đúng chuẩn. Làm sao để lỗi này không quay lại mà không cần ai nhớ?

**Khi nào xảy ra.** Use case đọc quan trọng không có test nào canh số câu SQL.

**Khi nào không xảy ra.** Nếu use case đã có **ngân sách SQL**: một test đặt trần số câu SELECT, vượt trần thì test đỏ (mục 23.1).

**Ví dụ.** Đặt ngân sách 2 SELECT cho use case, chạy trên cả hai đường:

```java
private static final int ORDER_LIST_SELECT_BUDGET = 2;

// Đường có lỗi: phải vượt ngân sách
sql.reset();
buggyService.list(PageRequest.of(0, 10, Sort.by("id")));
assertThatThrownBy(() -> SqlBudget.assertSelectsAtMost(
        captor, "danh sách đơn (buggy)", ORDER_LIST_SELECT_BUDGET))
    .isInstanceOf(AssertionError.class)
    .hasMessageContaining("vượt ngân sách SQL")
    .hasMessageContaining("from g14_i1_buggy.customer");

// Đường đã sửa: nằm trong ngân sách
sql.reset();
fixedService.list(PageRequest.of(0, 10));
assertThatCode(() -> SqlBudget.assertSelectsAtMost(
        captor, "danh sách đơn (fixed)", ORDER_LIST_SELECT_BUDGET))
    .doesNotThrowAnyException();
```

**Kết quả.** Trên đường có lỗi, `SqlBudget` ném `AssertionError` (22 câu vượt trần 2) và thông báo lỗi liệt kê các câu SELECT, trong đó thấy rõ câu `from g14_i1_buggy.customer`. Trên đường đã sửa (2 câu) test qua.

**Vì sao.** Test phải chứng minh **cả hai chiều**. Một rào chắn luôn xanh thì có thể do nó chưa từng soi đúng chỗ. Chứng minh rằng nó đỏ trên bản lỗi cho bạn biết rào chắn thật sự bắt được lỗi này. Ngoài ra, thông báo lỗi liệt kê SQL giúp người sửa thấy ngay câu nào thừa, không phải bật log rồi đi tìm.

**Cách tránh.** Mỗi use case đọc quan trọng (đặc biệt danh sách và báo cáo) có một test ngân sách SQL. Chi tiết cách hoạt động của `SqlBudget` ở mục 23.1.

::: {.ghi-nho}
Một rào chắn phải đỏ trên đường lỗi và xanh trên đường đã sửa. Ngân sách SELECT bắt được N+1 mà review mapping không thấy.
:::

::: {.tracuu codes="G14-I1-S04"}
:::

### 22.2 Tồn kho sai sau flash sale

**Triệu chứng.** Sau đợt flash sale, bộ phận kho báo số liệu không khớp: bán ra nhiều hơn số hàng thực có. Báo cáo ban đầu ghi "tồn kho âm", nhưng khi xem database thì kho không âm, và log không có exception nào.

**Nguyên nhân.** Hai khách mua cùng lúc. Service đọc tồn kho vào bộ nhớ, kiểm tra "đủ hàng" bằng code Java, trừ, rồi ghi. Không có khóa và entity không có `@Version`, nên hai giao dịch cùng thấy số tồn kho cũ, cùng qua bước kiểm tra, và lần ghi sau đè lên lần ghi trước. Đây là **lost update** (Chương 17).

**Chương kiến thức liên quan.** Chương 17 (mục 17.1, 17.2, 17.3).

![Sơ đồ bảng product và sale của sự cố flash sale](img/g14-i2.png){width=50%}

Dữ liệu thử: một sản phẩm tồn kho 10 cái, hai khách A và B, mỗi khách mua 6 cái. Để dựng đúng thời điểm xung đột mà không dùng `sleep`, test dùng cổng đồng bộ: khách A đọc tồn kho rồi dừng, khách B mua xong và commit, sau đó A mới chạy tiếp. Đó chính là thứ tự xen kẽ có thể xảy ra ngoài đời khi hai request chạm nhau trong vài mili giây.

#### 22.2.1 Tái hiện: kho 10, hai khách mua 6, cả hai đều thành công {.cot-loi}

**Bối cảnh.** Mỗi người mua 6 cái từ kho 10: hợp lý là một người mua được và người kia nhận "hết hàng". Service viết đúng theo logic nghiệp vụ: đọc kho, `if (stock < quantity) throw ...`, trừ kho, lưu đơn. Test đơn luồng chạy xanh vì không có ai chen vào giữa. Lỗi chỉ lộ khi hai request chạm nhau.

**Khi nào xảy ra.** Quy trình "đọc, kiểm tra trong Java, ghi" không có cơ chế phát hiện xung đột (không `@Version`, không khóa), và có hai giao dịch xen kẽ trên cùng một dòng.

**Khi nào không xảy ra.** Khi entity có `@Version` (mục 22.2.3), khi dùng `UPDATE ... set stock = stock - ? where stock >= ?` nguyên tử, hoặc khi khóa dòng bằng `PESSIMISTIC_WRITE` (Chương 17).

**Ví dụ.** Service của bản có lỗi:

```java
public void buy(Long productId, int quantity, Runnable afterStockRead) {
    tx.executeWithoutResult(status -> {
        // MANAGED, stock đã đọc vào bộ nhớ
        I2BuggyProduct product = products.findById(productId).orElseThrow();
        afterStockRead.run(); // chỉ để test ép thứ tự xen kẽ
        if (product.getStock() < quantity) { // kiểm tra trên số liệu có thể đã CŨ
            throw new I2BuggyInsufficientStockException(
                product.getStock(), quantity);
        }
        product.decreaseStock(quantity); // chỉ đổi trong bộ nhớ
        sales.save(new I2BuggySale(productId, quantity));
        // commit: flush, dirty checking sinh UPDATE product
    });
}
```

Tham số `afterStockRead` chỉ có trong test để ép thứ tự; code thật không có nó. Kịch bản: A đọc kho (thấy 10) rồi dừng; B mua trọn vẹn (kho 10 thành 4, commit); A chạy tiếp với số liệu cũ.

**Kết quả.** Không có exception nào, cả hai khách đều nhận "mua thành công". Database sau cùng:

| Bảng | Trước | Sau |
|---|---|---|
| product (stock) | 10 | **4** |
| sale (số dòng) | 0 | **2** (mỗi dòng 6 cái) |

Thay đổi: tồn kho giảm còn 4 và có hai dòng bán hàng, tổng 12 cái. Giữ nguyên: dòng sản phẩm duy nhất, không có exception.

Bán ra **12 cái** trong khi kho ban đầu chỉ có 10.

::: {.khac-du-doan codes="G14-I2-S01"}
**Điều nhiều người nghĩ:** lost update làm tồn kho **âm** (10 trừ 6 trừ 6 bằng âm 2), và báo cáo sự cố cũng hay ghi "kho âm".

**Thực tế:** kho **không âm**. Cả hai khách đều ghi `stock = 4` (A tính từ số liệu cũ 10 trừ 6, đè lên kết quả của B cũng là 4). Triệu chứng thật là **bán quá số hàng có** (12 cái từ kho 10) và con số tồn kho ghi sai (4 thay vì có lẽ phải từ chối một khách).

Đã kiểm chứng trên Hibernate 7.4.15.Final. Bài học: đừng tìm số âm, hãy tìm bất biến bị vỡ (mục 22.2.2).
:::

**Vì sao.** Đi từng bước:

1. A và B cùng đọc `stock = 10` vào persistence context riêng của mỗi giao dịch (hai persistence context khác nhau, không chia sẻ gì).
2. B qua kiểm tra (10 không nhỏ hơn 6), trừ còn 4, commit. Database lúc này có `stock = 4`.
3. A vẫn giữ bản `stock = 10` trong bộ nhớ, qua kiểm tra (10 không nhỏ hơn 6), trừ còn 4.
4. A commit: dirty checking thấy `stock` đổi từ 10 sang 4 so với snapshot của A, sinh `update product set ... stock=4 where id=?`. Câu UPDATE chỉ có điều kiện `id`, nên nó **thắng** mà không biết gì về việc B đã đổi dòng này.

Dirty checking của Hibernate chỉ so sánh với snapshot lúc nạp, không so với trạng thái hiện tại trong database. Nên "A đè lên B" là hoàn toàn hợp lệ theo cách Hibernate hiểu.

**Cách tránh.** Entity có luồng "đọc rồi quyết định rồi ghi" (tồn kho, số dư, trạng thái) cần có cơ chế phát hiện xung đột. Cách sửa ở mục 22.2.3.

::: {.ghi-nho}
Lost update không làm kho âm một cách rõ ràng; nó làm bán quá số hàng có, im lặng và không exception.
:::

::: {.tracuu codes="G14-I2-S01"}
:::

#### 22.2.2 Chẩn đoán: hai UPDATE không có where version, và kho cộng đã bán sai {.cot-loi}

**Bối cảnh.** Bộ phận kho báo "kho sai". Bạn không biết đó là lỗi tính toán trong code hay lỗi đồng thời. Chạy lại một mình thì code tính đúng. Cần bằng chứng cho thấy đây là lost update.

**Khi nào xảy ra.** Bạn nghi hai giao dịch ghi đè nhau.

**Khi nào không xảy ra.** Nếu bất biến vẫn đúng khi chạy đua, lỗi nằm chỗ khác (ví dụ công thức tính sai, thì chạy một mình cũng sai).

**Ví dụ.** Chạy lại kịch bản xen kẽ, in SQL, rồi kiểm tra hai thứ: câu UPDATE có điều kiện `version` không, và bất biến "kho còn lại cộng tổng đã bán bằng kho ban đầu":

```java
sql.reset();
twoBuyersCollideOnBuggy();

sql.assertUpdates("product", 2);         // cả hai đều UPDATE
sql.assertNoStatementContains("version"); // không cột version nào

// Bất biến: kho còn lại + tổng đã bán == kho ban đầu
int stockLeft = jdbc.queryForObject(
    "select stock from ... .product where id = ?", Integer.class, id);
int soldTotal = jdbc.queryForObject(
    "select coalesce(sum(quantity), 0) from ... .sale", Integer.class);
assertThat(stockLeft + soldTotal).isEqualTo(INITIAL_STOCK);
```

**Kết quả.** Có đúng **hai câu UPDATE `product`** cùng ghi `stock = 4` với điều kiện chỉ là `where id=?`. Không câu SQL nào có chữ `version`. Bất biến vỡ: kho còn 4 cộng đã bán 12 bằng 16, đáng ra phải bằng 10.

```sql
select ibp1_0.id, ibp1_0.name, ibp1_0.stock
from g14_i2_buggy.product ibp1_0
where ibp1_0.id=?

insert into g14_i2_buggy.sale (product_id, quantity, id)
values (?, ?, ?)

update g14_i2_buggy.product
set name=?, stock=?
where id=?
```

Đoạn trên là một lượt của một khách. Cả hai khách đều chạy đủ chu trình SELECT, INSERT sale, UPDATE product, nên trong log có hai câu SELECT đọc kho và hai câu UPDATE.

**Vì sao.** Hai bằng chứng bổ sung cho nhau. Bằng chứng thứ nhất ở tầng SQL: một UPDATE chỉ có `where id=?` thì database không có gì để phát hiện rằng dòng đã bị người khác đổi. Hai UPDATE giống nhau cùng thành công nghĩa là không ai bị từ chối. Bằng chứng thứ hai ở tầng dữ liệu: **đối chiếu hai nguồn số liệu** (kho và sổ bán hàng). Một con số đứng một mình (kho = 4) trông hoàn toàn hợp lệ; chỉ khi đối chiếu với tổng đã bán mới thấy 4 + 12 không bằng 10.

Lưu ý nhỏ: câu UPDATE ghi cả `name=?` dù chỉ `stock` đổi, vì Hibernate mặc định ghi mọi cột của entity. Điều này không gây lỗi ở đây nhưng chính là điều `@DynamicUpdate` thay đổi (Chương 14).

**Cách tránh.** Khi gặp "số liệu không khớp", đừng tìm con số sai, hãy viết ra **bất biến** nghiệp vụ (kho cộng đã bán bằng kho ban đầu) rồi kiểm tra nó bằng một truy vấn đối chiếu.

::: {.ghi-nho}
Bằng chứng của lost update: UPDATE không có điều kiện version và bất biến nghiệp vụ bị vỡ khi đối chiếu hai nguồn số liệu.
:::

::: {.tracuu codes="G14-I2-S02"}
:::

#### 22.2.3 Sửa: Version và retry cả transaction {.cot-loi}

**Bối cảnh.** Bạn muốn khách đến sau bị từ chối đúng cách: nhận thông báo "hết hàng" thay vì được ghi đè lặng lẽ. Giữ khóa trong suốt thời gian xử lý đơn thì nặng nề, trong khi xung đột thật sự hiếm. Optimistic lock (`@Version`) hợp với trường hợp này.

**Khi nào xảy ra.** Entity có `@Version`, hai giao dịch xen kẽ như trước.

**Khi nào không xảy ra.** Nếu tranh chấp rất cao trên một dòng (nhiều khách cùng mua một sản phẩm), retry xảy ra liên tục và nên chuyển sang UPDATE nguyên tử hoặc khóa pessimistic (Chương 17).

**Ví dụ.** Entity thêm một field, service thêm vòng retry **bao ngoài** transaction:

```java
@Version
@Column(name = "version", nullable = false)
private long version;
```

```java
public void buy(Long productId, int quantity, Runnable afterStockRead) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
        Runnable hook = (attempt == 1) ? afterStockRead : () -> { };
        try {
            // Retry phải bao NGOÀI transaction: transaction đã lỗi version
            // thì rollback-only, không thể thử tiếp bên trong nó.
            tx.executeWithoutResult(status ->
                buyOnce(productId, quantity, hook));
            return;
        } catch (OptimisticLockingFailureException conflict) {
            if (attempt == MAX_ATTEMPTS) {
                throw conflict;
            }
        }
    }
}
```

Hàm `buyOnce` giống hệt bản có lỗi: đọc, kiểm tra, trừ, lưu đơn.

**Kết quả.** Lần thử đầu của A bị từ chối ở bước ghi: câu UPDATE có điều kiện `version=0` không khớp dòng nào (B đã tăng version lên 1). Service chạy lại cả transaction; lần này A đọc `stock = 4`, thấy nhỏ hơn 6 và ném `InsufficientStockException` ("Kho còn 4, khách muốn mua 6"). Database sau cùng:

| Bảng | Trước | Sau |
|---|---|---|
| product (stock, version) | 10, 0 | **4, 1** |
| sale (số dòng) | 0 | **1** (6 cái, của B) |

Thay đổi: B mua thành công, kho giảm còn 4, version tăng lên 1. Giữ nguyên: không có dòng bán hàng nào của A. Bất biến đúng: 4 cộng 6 bằng 10.

**Vì sao.** Với `@Version`, Hibernate đổi câu UPDATE thành dạng `update product set ..., version=? where id=? and version=?`. Hai giao dịch cùng đọc version 0 thì chỉ giao dịch commit trước khớp điều kiện; giao dịch sau cập nhật 0 dòng, Hibernate hiểu là xung đột và ném `OptimisticLockingFailureException`. Retry ngoài transaction vì transaction đã lỗi là rollback-only; chạy lại bên trong nó không có tác dụng. Chạy lại cả transaction nghĩa là **đọc lại** dữ liệu mới nhất, nên A thấy kho 4 và bị chặn bằng quy tắc nghiệp vụ, đúng nơi nó cần bị chặn. Cái giá phải trả là người gọi phải chấp nhận và test đường retry, và có giới hạn số lần thử (ở đây là 3) để tranh chấp quá cao không làm vòng lặp chạy mãi.

**Trong JPA thuần.** `@Version` và `OptimisticLockException` là JPA chuẩn. `OptimisticLockingFailureException` ở trên là exception của Spring bọc lại.

::: {.ghi-nho}
`@Version` cộng với retry cả transaction: người đến sau bị từ chối ở bước ghi, đọc lại số mới và bị chặn bằng quy tắc nghiệp vụ. Retry luôn nằm ngoài transaction.
:::

::: {.tracuu codes="G14-I2-S03"}
:::

#### 22.2.4 Rào chắn: test đua hai khách và bất biến kho {.cot-loi}

**Bối cảnh.** Sửa xong, bạn muốn bảo đảm không ai vô tình bỏ `@Version` hay bỏ vòng retry sau này, và không muốn chờ khách phàn nàn mới biết.

**Khi nào xảy ra.** Đường "đọc rồi ghi" quan trọng không có test đua.

**Khi nào không xảy ra.** Nếu đã có test đua hai giao dịch kèm kiểm tra bất biến nghiệp vụ.

**Ví dụ.** Chạy kịch bản xen kẽ trên cả hai đường, rồi kiểm tra cùng một bất biến:

```java
// Đường có lỗi: bất biến phải vỡ
twoBuyersCollideOnBuggy();
assertThatThrownBy(() -> assertInventoryInvariant(I2_BUGGY))
    .isInstanceOf(AssertionError.class);

// Đường đã sửa: bất biến phải giữ nguyên
// (A chạy fixedService.buy(...), B chen vào như trước)
buyerA.joinAndGetFailure(); // lỗi "hết hàng" của A là kết quả mong đợi
assertThatCode(() -> assertInventoryInvariant(I2_FIXED))
    .doesNotThrowAnyException();
```

**Kết quả.** Trên đường có lỗi, bất biến vỡ và `AssertionError` được ném. Trên đường đã sửa, bất biến giữ nguyên.

**Vì sao.** Test này canh đúng thứ nghiệp vụ cần ("không bán ra hàng không có"), không canh chi tiết cài đặt. Vì vậy ai bỏ `@Version`, bỏ retry, hay đổi sang cách bảo vệ khác đều được test xét theo hiệu quả thật. Hai điểm kỹ thuật cần nhớ: dùng cổng đồng bộ chứ không dùng `Thread.sleep` (sleep làm test chậm và vẫn có thể may rủi), và service được chạy ngoài transaction của test (`startOutsideTx`) để vòng retry bên trong service còn nguyên nghĩa. Nếu bọc cả test trong một transaction thì transaction của service tham gia vào nó, lỗi version làm cả transaction ngoài rollback-only và retry không chạy dù code đúng.

::: {.ghi-nho}
Rào chắn cho lỗi đồng thời là test đua hai giao dịch kèm kiểm tra bất biến nghiệp vụ, đỏ trên bản lỗi và xanh trên bản đã sửa.
:::

::: {.tracuu codes="G14-I2-S04"}
:::

### 22.3 Job gửi email chạy hai lần

**Triệu chứng.** Khách hàng nhận hai email giống hệt nhau. Log của hai worker cho thấy cả hai cùng xử lý một việc, trong khi các việc khác trong hàng đợi vẫn chờ.

**Nguyên nhân.** Hai worker chạy song song, mỗi worker lấy việc bằng truy vấn "việc có trạng thái NEW đầu tiên" **không khóa**. Cả hai cùng thấy một việc là việc đầu tiên, cùng nhận và cùng gửi. Đây là phiên bản "hàng đợi" của lost update (Chương 17).

**Chương kiến thức liên quan.** Chương 17 (mục 17.4, 17.5).

![Sơ đồ bảng job và email_log của sự cố gửi email](img/g14-i3.png){width=50%}

Dữ liệu thử: hai việc trong bảng `job`, cả hai ở trạng thái NEW. Kịch bản: worker A nhận việc rồi dừng; worker B chạy trọn vẹn một lượt xử lý; sau đó A mới chạy tiếp.

#### 22.3.1 Tái hiện: hai worker cùng nhận việc 1 {.cot-loi}

**Bối cảnh.** Hàng đợi việc đơn giản: bảng `job` có cột `status` (NEW hoặc DONE). Mỗi worker chạy một transaction: lấy một việc NEW, gửi mail, ghi `email_log`, đánh dấu DONE. Chạy một worker thì hoàn hảo; chạy hai worker để xử lý nhanh hơn thì sinh lỗi, và lỗi chỉ lộ khi hai worker chạm nhau đúng một khoảng ngắn.

**Khi nào xảy ra.** Truy vấn lấy việc chỉ là `select ... where status = 'NEW' order by id` (không khóa dòng), và có từ hai worker trở lên.

**Khi nào không xảy ra.** Khi chỉ có một worker, hoặc khi truy vấn lấy việc khóa dòng và bỏ qua dòng đang bị khóa (mục 22.3.3).

**Ví dụ.** Repository và worker của bản có lỗi:

```java
public interface I3BuggyJobRepository
        extends JpaRepository<I3BuggyJob, Long> {

    // KHÔNG khóa: hai worker cùng gọi sẽ cùng thấy việc NEW đầu tiên
    @Query("select j from G14I3B_Job j where j.status = 'NEW' order by j.id")
    List<I3BuggyJob> findNewJobs(Limit limit);
}
```

```java
public Long processNext(String workerName, Runnable afterJobRead) {
    return tx.execute(status -> {
        I3BuggyJob job = jobs.findNewJobs(Limit.of(1))
            .stream().findFirst().orElse(null);
        if (job == null) {
            return null;
        }
        afterJobRead.run(); // chỉ để test ép thứ tự xen kẽ
        emailLog.save(new I3BuggyEmailLog(job.getId(), workerName)); // "gửi mail"
        job.markDone(); // commit: update job set status='DONE'
        return job.getId();
    });
}
```

**Kết quả.** Không có exception. Bảng `email_log` có **2 dòng cho việc 1** (mỗi worker một dòng), còn việc 2 vẫn ở trạng thái NEW, vì cả hai worker đã dồn vào việc 1.

| Bảng | Trước | Sau |
|---|---|---|
| email_log (số dòng) | 0 | **2**, cả hai có job_id là việc 1 (worker-A và worker-B) |
| job, việc 1 | NEW | DONE |
| job, việc 2 | NEW | **NEW** (không ai làm) |

Thay đổi: hai dòng log cho cùng một việc, việc 1 thành DONE. Giữ nguyên: việc 2 vẫn NEW.

**Vì sao.** Câu SELECT không có khóa, nên PostgreSQL trả về cùng dòng cho bất kỳ ai hỏi, miễn dòng đó còn thỏa `status = 'NEW'` ở thời điểm đọc. Worker A đọc việc 1 (vẫn NEW), dừng. Worker B đọc việc 1 (vẫn NEW vì A chưa commit), gửi, commit. A chạy tiếp: nó đã có việc 1 trong bộ nhớ, gửi lần hai, rồi ghi DONE (UPDATE chỉ có `where id=?`, không kiểm tra trạng thái cũ). Hai giao dịch không chặn nhau, vì chỉ đọc không hề khóa gì.

**Cách tránh.** Hàng đợi nhiều worker cần một cơ chế để mỗi việc chỉ thuộc về một worker (mục 22.3.3).

::: {.ghi-nho}
Truy vấn lấy việc không khóa cho phép hai worker cùng nhận một việc: gửi trùng, việc kế tiếp bị bỏ đói, không có exception.
:::

::: {.tracuu codes="G14-I3-S01"}
:::

#### 22.3.2 Chẩn đoán: câu SELECT nhận việc không có for update {.cot-loi}

**Bối cảnh.** Có hai khâu có thể sai: khâu "nhận việc" (hai worker nhận trùng) hoặc khâu "gửi" (một worker gửi hai lần). Sửa nhầm khâu thì lỗi vẫn còn nguyên.

**Khi nào xảy ra.** Bạn có log SQL hoặc dữ liệu `email_log` và muốn xác định khâu nào gây lỗi.

**Khi nào không xảy ra.** Nếu cùng một worker ghi hai dòng log cho một việc, lỗi nằm ở khâu gửi (ví dụ vòng lặp gửi hai lần), không phải ở khâu nhận.

**Ví dụ.** Chạy lại kịch bản, in SQL, kiểm tra xem có khóa không, và nhóm log theo việc:

```java
sql.reset();
twoWorkersCollideOnBuggy();

sql.assertNoStatementContains("for update");
sql.assertNoStatementContains("skip locked");

// Một việc có hai worker khác nhau trong log
jdbc.queryForList("select worker from ...email_log "
    + "where job_id = 1 order by worker", String.class);
// -> [worker-A, worker-B]
```

**Kết quả.** SQL của cả hai worker chỉ có hai câu `select ... where status='NEW' order by id fetch first ? rows only`, không câu nào có `for update` hay `skip locked`. Trong `email_log`, việc 1 có hai dòng, một của worker-A và một của worker-B.

```sql
select ibj1_0.id, ibj1_0.recipient, ibj1_0.status
from g14_i3_buggy.job ibj1_0
where ibj1_0.status='NEW'
order by ibj1_0.id
fetch first ? rows only
```

Câu này xuất hiện hai lần, một lần cho mỗi worker, đứng trước các câu INSERT `email_log` và UPDATE `job`.

**Vì sao.** Hai worker khác tên nhưng cùng `job_id` là **dấu vân tay** của "hai worker lấy cùng việc": nếu lỗi nằm ở khâu gửi thì tên worker sẽ trùng. Còn việc thiếu `for update` giải thích **vì sao** chuyện đó xảy ra: PostgreSQL không có lý do gì để bắt worker này chờ hay tránh dòng mà worker kia đã đọc. Bất biến cần kiểm tra là "không việc nào được gửi mail từ hai lần trở lên", viết bằng một truy vấn `group by job_id having count(*) > 1`.

::: {.ghi-nho}
Bằng chứng gửi trùng do nhận trùng: câu SELECT lấy việc không có `for update` hoặc `skip locked`, và một `job_id` xuất hiện với hai worker trong log.
:::

::: {.tracuu codes="G14-I3-S02"}
:::

#### 22.3.3 Sửa: PESSIMISTIC_WRITE kèm SKIP LOCKED {.cot-loi}

**Bối cảnh.** Bạn muốn mỗi worker nhận một việc **khác nhau**, và worker thấy việc đang bị người khác giữ thì bỏ qua để lấy việc kế tiếp, thay vì chờ. Đó chính là mẫu hàng đợi `FOR UPDATE SKIP LOCKED` của PostgreSQL.

**Khi nào xảy ra.** Truy vấn lấy việc có khóa ghi và chế độ bỏ qua dòng đang bị khóa.

**Khi nào không xảy ra.** Nếu bạn dùng khóa mà không có `SKIP LOCKED`, worker thứ hai sẽ **chờ** worker thứ nhất, hàng đợi thành tuần tự. Với `NOWAIT` thì worker thứ hai nhận lỗi ngay (Chương 17).

**Ví dụ.** Chỉ thay truy vấn lấy việc:

```java
public interface I3FixedJobRepository
        extends JpaRepository<I3FixedJob, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout",
        value = "-2")) // -2: giá trị đặc biệt của Hibernate cho SKIP LOCKED
    @Query("select j from G14I3F_Job j where j.status = 'NEW' order by j.id")
    List<I3FixedJob> findNewJobsSkipLocked(Limit limit);
}
```

**Kết quả.** Worker A khóa việc 1 và giữ khóa đến lúc commit. Worker B chạy cùng truy vấn, bỏ qua việc 1 (đang bị khóa) và nhận việc 2. Mỗi việc đúng một dòng log và cả hai việc DONE.

```sql
select ifj1_0.id, ifj1_0.recipient, ifj1_0.status
from g14_i3_fixed.job ifj1_0
where ifj1_0.status='NEW'
order by ifj1_0.id
fetch first ? rows only
for no key update of ifj1_0 skip locked
```

| Bảng | Trước | Sau |
|---|---|---|
| email_log, việc 1 | không có | **1 dòng** (worker-A) |
| email_log, việc 2 | không có | **1 dòng** (worker-B) |
| job (số việc DONE) | 0 | **2** |

Thay đổi: mỗi việc đúng một dòng log và đều DONE. Giữ nguyên: tổng số việc là 2.

**Vì sao.** `PESSIMISTIC_WRITE` làm Hibernate thêm `for no key update` vào câu SELECT, tức là khóa các dòng trả về cho đến khi transaction kết thúc. `SKIP LOCKED` bảo PostgreSQL bỏ qua dòng đã bị khóa thay vì chờ. Vì A giữ khóa việc 1 đến lúc commit, câu SELECT của B bỏ qua việc 1 và trả việc 2. Nhờ vậy hai worker không bao giờ nhận cùng một dòng. Cái giá: khóa được giữ suốt lúc "gửi mail", nên transaction phải ngắn; gửi mail chậm sẽ giữ khóa lâu.

**Trong JPA thuần.** `@Lock(PESSIMISTIC_WRITE)` là JPA chuẩn. Giá trị `-2` của `jakarta.persistence.lock.timeout` để chọn `SKIP LOCKED` là **quy ước riêng của Hibernate**; JPA chỉ định nghĩa timeout theo mili giây (và `0` là không chờ). Chuyển sang provider khác thì không có `SKIP LOCKED` theo cách này.

::: {.ghi-nho}
`PESSIMISTIC_WRITE` kèm `SKIP LOCKED` biến bảng thành hàng đợi: mỗi worker nhận một dòng khác nhau và bỏ qua dòng đang bị người khác giữ.
:::

::: {.tracuu codes="G14-I3-S03"}
:::

#### 22.3.4 Rào chắn: bất biến mỗi việc gửi một lần {.cot-loi}

**Bối cảnh.** Mai kia ai đó thấy truy vấn có `for update` và "tối ưu" nó đi cho nhanh, lỗi quay lại. Test một worker không bao giờ phát hiện được.

**Khi nào xảy ra.** Hàng đợi nhiều worker không có test đua.

**Khi nào không xảy ra.** Khi có test đua hai worker kèm bất biến "mỗi việc gửi một lần".

**Ví dụ.**

```java
twoWorkersCollideOnBuggy();
twoWorkersRaceOnFixed();

assertThatThrownBy(() -> assertNoJobEmailedTwice(I3_BUGGY))
    .isInstanceOf(AssertionError.class);  // bản lỗi gửi trùng: đỏ
assertThatCode(() -> assertNoJobEmailedTwice(I3_FIXED))
    .doesNotThrowAnyException();          // bản sửa không gửi trùng: xanh
```

**Kết quả.** Bất biến vỡ trên đường có lỗi (có việc xuất hiện hai lần trong `email_log`) và giữ nguyên trên đường đã sửa.

**Vì sao.** Cũng như sự cố 22.2, rào chắn canh bất biến nghiệp vụ thay vì canh cách cài đặt: ai bỏ khóa khỏi truy vấn lấy việc làm test này đỏ ngay, dù họ đổi bằng cách nào. Đây là loại lỗi **chỉ lộ khi đua**, nên chỉ test đua mới bắt được.

::: {.ghi-nho}
Rào chắn cho hàng đợi nhiều worker là test đua hai worker kèm bất biến mỗi việc gửi một lần.
:::

::: {.tracuu codes="G14-I3-S04"}
:::

### 22.4 Import file lớn chậm dần

**Triệu chứng.** Job import nhập file CSV vào bảng `item`. Ban đầu file vài trăm dòng chạy trong tích tắc. Khi file lên hàng trăm nghìn dòng, thời gian chạy tăng nhanh hơn số dòng, ngốn RAM, và có thể kết thúc bằng `OutOfMemoryError`. Dữ liệu ghi vào đủ và đúng.

**Nguyên nhân.** Hai lỗi cộng lại. Thứ nhất, id dùng `IDENTITY` nên mỗi `persist` buộc phải INSERT ngay để lấy id, Hibernate không gom lô được: N dòng là N câu INSERT riêng. Thứ hai, service `persist` toàn bộ trong một transaction mà không bao giờ `flush` và `clear`, nên persistence context giữ cả N entity, và mỗi lần flush Hibernate phải dirty-check tất cả.

**Chương kiến thức liên quan.** Chương 18 (mục 18.1, 18.2, 18.4).

![Sơ đồ bảng item của sự cố import](img/g14-i4.png){width=40%}

Dữ liệu thử là 200 dòng. Con số nhỏ nhưng đủ để thấy quy luật, vì ta đo **số câu INSERT** và **cỡ persistence context**, không đo thời gian (thời gian phụ thuộc máy và dễ nhiễu).

#### 22.4.1 Tái hiện: 200 dòng là 200 INSERT, persistence context giữ 200 entity {.cot-loi}

**Bối cảnh.** Service import trông rất bình thường: một vòng `for` gọi `persist`. Kết quả cuối cùng đúng, nên không ai nghi ngờ gì cho đến khi file đủ lớn để chậm. Triệu chứng "chậm dần" khó bắt vì nó không có lỗi nào để đọc.

**Khi nào xảy ra.** Entity dùng `GenerationType.IDENTITY`, và vòng `persist` dài không có `flush` và `clear`.

**Khi nào không xảy ra.** Với file nhỏ (vài chục dòng) hai lỗi này không đáng kể. Cũng không xảy ra khi dùng `SEQUENCE` kèm flush và clear theo lô (mục 22.4.3).

**Ví dụ.** Entity và service của bản có lỗi:

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

```java
@Transactional
public I4BuggyImportReport importItems(int rows) {
    Session session = em.unwrap(Session.class);
    int peak = 0;
    for (int i = 1; i <= rows; i++) {
        // MANAGED: INSERT ngay (IDENTITY), entity nằm lại trong persistence context
        em.persist(new I4BuggyItem("Item-" + i, i));
        peak = Math.max(peak, session.getStatistics().getEntityCount());
    }
    return new I4BuggyImportReport(rows, peak);
}
```

**Kết quả.** Có đúng **200 câu INSERT riêng lẻ** vào bảng `item`, và persistence context giữ đủ **200 entity** ở đỉnh. Dữ liệu vào database đúng và đủ 200 dòng.

**Vì sao.** Với `IDENTITY`, id do chính câu INSERT sinh ra (cột tự tăng của database). Hibernate cần id ngay lúc `persist` để entity có định danh trong persistence context, nên phải chạy INSERT **ngay lúc `persist`** thay vì đợi flush. Một câu INSERT chạy ngay không thể gom vào lô với câu sau (Chương 18). Còn không `flush` và `clear` thì mỗi entity đã persist nằm lại trong persistence context đến hết transaction; flush cuối phải dirty-check cả N entity, và RAM tăng theo N. Hai thứ cùng tăng theo số dòng: chuyến đi tới database và bộ nhớ.

**Trong JPA thuần.** `persist`, `flush`, `clear` là JPA chuẩn. `session.getStatistics().getEntityCount()` để đếm số entity đang được quản lý là API riêng của Hibernate; JPA thuần không có cách tương đương để hỏi persistence context đang giữ bao nhiêu entity.

::: {.ghi-nho}
`IDENTITY` buộc mỗi `persist` INSERT ngay nên không gom lô được, và không `flush` cộng `clear` thì persistence context giữ mọi entity đã persist.
:::

::: {.tracuu codes="G14-I4-S01"}
:::

#### 22.4.2 Chẩn đoán: hai con số đều bằng đúng số dòng {.cot-loi}

**Bối cảnh.** "Import chậm" có thể do database chậm (đĩa, mạng, khóa) hoặc do persistence context phình. Hai nguyên nhân cần cách sửa khác nhau. Đo thời gian không phân biệt được chúng; đo số lượng thì được.

**Khi nào xảy ra.** Bạn muốn biết import chậm vì đâu.

**Khi nào không xảy ra.** Nếu cỡ persistence context không tăng theo số dòng mà thời gian vẫn tăng, nguyên nhân nằm ở database hoặc câu SQL, không ở persistence context.

**Ví dụ.** Chạy cùng service với 100 rồi 200 dòng, đo hai số: số câu INSERT và cỡ persistence context lớn nhất.

```java
sql.reset();
I4BuggyImportReport smallImport = buggyService.importItems(100);
sql.assertInserts("item", 100);

sql.reset();
I4BuggyImportReport bigImport = buggyService.importItems(200);
sql.assertInserts("item", 200);

smallImport.peakManagedEntities(); // 100
bigImport.peakManagedEntities();   // 200
```

**Kết quả.**

| Số dòng import | Số câu INSERT | Persistence context lớn nhất |
|---|---|---|
| 100 | **100** | **100** |
| 200 | **200** | **200** |

**Vì sao.** Cả hai con số **tăng tuyến tính theo số dòng, không có trần**. Đó là dấu hiệu cần hành động: số INSERT bằng số dòng nghĩa là không có gom lô; cỡ persistence context bằng số dòng nghĩa là không bao giờ dọn. Cách đo cỡ persistence context: `session.getStatistics().getEntityCount()`. Cách đếm INSERT: bộ đếm SQL như `SqlCaptor`. Một điểm cần nhớ: những con số này đo **cấu trúc** của vấn đề (tăng theo N), không phụ thuộc máy chạy test, nên test ổn định hơn nhiều so với đo thời gian.

::: {.ghi-nho}
Muốn biết import chậm vì đâu: đo số câu INSERT và cỡ persistence context ở hai cỡ dữ liệu. Cả hai tăng theo số dòng thì cần gom lô và dọn theo lô.
:::

::: {.tracuu codes="G14-I4-S02"}
:::

#### 22.4.3 Sửa: SEQUENCE, batch size 50 và flush kèm clear mỗi 50 dòng {.cot-loi}

**Bối cảnh.** Bạn muốn gửi dữ liệu xuống database theo lô và giữ persistence context nhỏ, để thời gian và bộ nhớ tăng **tuyến tính theo số dòng** thay vì phình lên.

**Khi nào xảy ra.** Entity dùng `SEQUENCE`, session bật JDBC batch size, và service `flush` rồi `clear` mỗi một lô.

**Khi nào không xảy ra.** Nếu thiếu một trong ba thứ, hiệu quả mất: vẫn `IDENTITY` thì không gom lô; không `clear` thì persistence context vẫn phình; không đặt batch size thì mỗi câu INSERT vẫn gửi riêng.

**Ví dụ.**

```java
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE,
    generator = "g14i4fixed_item_seq")
@SequenceGenerator(name = "g14i4fixed_item_seq", schema = "g14_i4_fixed",
    sequenceName = "item_seq", allocationSize = 50)
private Long id;
```

```java
public static final int CHUNK = 50;

@Transactional
public I4FixedImportReport importItems(int rows) {
    Session session = em.unwrap(Session.class);
    session.setJdbcBatchSize(CHUNK); // chỉ session này, không bật toàn cục
    for (int i = 1; i <= rows; i++) {
        em.persist(new I4FixedItem("Item-" + i, i)); // MANAGED, INSERT trì hoãn
        if (i % CHUNK == 0) {
            em.flush(); // đẩy cả lô 50 dòng bằng một batch JDBC
            em.clear(); // MANAGED thành DETACHED: giải phóng persistence context
        }
    }
    // ...
}
```

**Kết quả.** Đúng **4 lô JDBC, mỗi lô 50 dòng** (đo bằng bộ ghi log lô, vì bộ đếm câu SQL chỉ thấy một câu INSERT cho cả lô). Persistence context không bao giờ giữ quá 50 entity. Database vẫn có đủ 200 dòng.

| Chỉ số | Bản có lỗi | Bản đã sửa |
|---|---|---|
| Cách gửi INSERT | 200 câu riêng lẻ | **4 lô, mỗi lô 50 dòng** |
| Persistence context lớn nhất | 200 entity | **không quá 50 entity** |
| Số dòng trong database | 200 | 200 |

**Vì sao.** Mỗi thay đổi giải quyết một nửa vấn đề. `SEQUENCE` cho phép Hibernate **lấy id trước** (từ sequence, với `allocationSize = 50` thì 50 id chỉ tốn một lần hỏi) và **trì hoãn INSERT** đến flush, nên gom được lô. `setJdbcBatchSize(50)` bật việc gom lô cho session này. `flush` đẩy cả lô xuống database; `clear` đưa các entity vừa ghi sang trạng thái DETACHED để Hibernate bỏ chúng khỏi persistence context, nên lô sau bắt đầu từ trạng thái nhỏ. Batch size và chu kỳ flush nên **bằng nhau** (cùng là `CHUNK`) để mỗi lần flush gửi đúng một lô đầy.

**Trong JPA thuần.** `SEQUENCE`, `persist`, `flush`, `clear` là JPA chuẩn. `session.setJdbcBatchSize` là API riêng của Hibernate, cho phép đặt batch size **cho riêng một session**. Cách JPA thuần là đặt thuộc tính cấu hình `hibernate.jdbc.batch_size` cho **toàn bộ** persistence unit (và thuộc tính này cũng là cấu hình của Hibernate).

::: {.ghi-nho}
Muốn import lớn chạy tuyến tính: dùng `SEQUENCE`, bật batch size và `flush` kèm `clear` mỗi lô, với batch size bằng chu kỳ flush.
:::

::: {.tracuu codes="G14-I4-S03"}
:::

#### 22.4.4 Rào chắn: giới hạn persistence context và lint IDENTITY {.cot-loi}

**Bối cảnh.** Có hai cách lỗi này quay lại: ai đó quên `flush` và `clear` khi sửa service, hoặc ai đó đổi id sang `IDENTITY` khi thêm entity mới. Hai kiểu sai cần hai rào chắn, mỗi rào chắn bắt ở một thời điểm khác nhau.

**Khi nào xảy ra.** Import không có rào chắn nào canh cỡ persistence context hay chiến lược id.

**Khi nào không xảy ra.** Khi có đủ cả hai rào chắn dưới đây.

**Ví dụ.**

```java
// Rào chắn 1, lúc chạy: persistence context không được phình theo số dòng
private void assertPersistenceContextStaysBounded(int peak) {
    assertThat(peak).isLessThanOrEqualTo(I4FixedImportService.CHUNK);
}

assertThatThrownBy(() -> assertPersistenceContextStaysBounded(
        buggyReport.peakManagedEntities()))
    .isInstanceOf(AssertionError.class);                // bản lỗi: đỏ
assertThatCode(() -> assertPersistenceContextStaysBounded(
        fixedReport.peakManagedEntities()))
    .doesNotThrowAnyException();                        // bản sửa: xanh

// Rào chắn 2, lúc review mapping: lint IDENTITY
assertThat(MappingLint.check(I4BuggyItem.class))
    .anyMatch(violation -> violation.startsWith("IDENTITY-ID"));
assertThat(MappingLint.check(I4FixedItem.class)).isEmpty();
```

**Kết quả.** Giới hạn cỡ persistence context đỏ trên bản có lỗi (200 entity vượt 50) và xanh trên bản đã sửa. Lint mapping báo `IDENTITY-ID` trên entity có lỗi và sạch trên entity đã sửa.

**Vì sao.** Hai rào chắn **bổ sung** cho nhau. Giới hạn cỡ persistence context chạy lúc import và bắt cả lỗi quên `flush` hoặc `clear`, dù entity dùng chiến lược id nào. Lint mapping (mục 23.2) bắt ngay lúc review entity, **không cần chạy import**, nên phản hồi sớm hơn nhiều, nhưng nó chỉ nhìn thấy mapping, không thấy service. Dùng cả hai thì mỗi rào chắn che chỗ hở của cái kia.

::: {.ghi-nho}
Hai rào chắn cho import lớn: giới hạn cỡ persistence context lúc chạy (bắt quên dọn) và lint mapping cấm IDENTITY (bắt ngay lúc review).
:::

::: {.tracuu codes="G14-I4-S04"}
:::
