## Chương 18. Ghi số lượng lớn: batch, sequence và StatelessSession

Các chương trước đo đúng sai: dữ liệu có đúng không, SQL có đúng không. Chương này đo **chi phí**. Một job nhập 100 000 dòng hàng hóa, một job đánh dấu 2 000 khách hàng "đã xử lý", một lần import đầu tháng: chạy đúng thôi chưa đủ, chúng còn phải chạy trong thời gian và bộ nhớ chấp nhận được.

Điều khó là "tốn kém" thường được nói bằng cảm giác ("chậm", "nặng"). Lab chọn đo bằng những thứ **đếm được**, không đo bằng đồng hồ (đồng hồ phụ thuộc máy, và làm test chập chờn):

| Cần biết | Dụng cụ đếm |
|---|---|
| Hibernate gửi bao nhiêu câu SQL | bộ chặn câu SQL của lab (bỏ qua câu `nextval`) |
| Bao nhiêu lần hỏi sequence | bộ đếm riêng các câu `nextval` |
| Bao nhiêu lô batch JDBC | log mức TRACE của Hibernate (xem mục 18.1) |
| Persistence context đang giữ bao nhiêu entity | `session.getStatistics().getEntityCount()` |

Chương đi theo một mạch:

1. Gom nhiều câu INSERT thành một lô (mục 18.1), vì sao `IDENTITY` làm hỏng việc gom (mục 18.2), và vì sao `allocationSize` quyết định số lần hỏi sequence (mục 18.3).
2. Vòng lặp xử lý hàng nghìn dòng: dọn persistence context theo lô (mục 18.4).
3. `StatelessSession`, công cụ nhẹ hơn nhưng bỏ nhiều thứ quen thuộc (mục 18.5).
4. Một ghi chú về `order_inserts` và `order_updates` (mục 18.6).

![Sơ đồ bảng item, dùng cho các thí nghiệm batch insert](img/g13-b1.png){width=40%}

**Trong JPA thuần.** Phần lớn công cụ của chương này là của Hibernate: `session.setJdbcBatchSize(...)` và `StatelessSession` không có trong JPA. JPA chuẩn chỉ có `@GeneratedValue`, `@SequenceGenerator` (kể cả `allocationSize`), `EntityManager.flush()` và `clear()`. Muốn bật batch trong JPA thuần, bạn đặt thuộc tính `hibernate.jdbc.batch_size` ở cấu hình cả persistence unit (đây là thuộc tính riêng của Hibernate, không phải của JPA). Từng mục dưới đây nói rõ phần nào là chuẩn, phần nào là riêng.

### 18.1 Batch insert với SEQUENCE

Mặc định, mỗi entity mới là một câu `INSERT` riêng gửi xuống database: 100 entity là 100 lần đi và về qua mạng. **Batch JDBC** cho phép Hibernate gom nhiều câu giống nhau (cùng bảng, cùng cột) thành một lô và gửi một lần. Với PostgreSQL, 100 dòng chia thành 5 lô nghĩa là 5 lần đi về thay vì 100.

#### 18.1.1 Bật batch size 20: 100 entity chia thành 5 lô {.cot-loi}

**Bối cảnh.** Bạn viết job nhập danh mục hàng hóa từ file của nhà cung cấp, mỗi lần vài nghìn dòng. Job chạy đúng nhưng mất hàng chục giây vì mỗi dòng là một lần đi về với database. Bạn nghe nói "bật batch" sẽ nhanh hơn và muốn biết bật bằng cách nào, rồi chứng minh nó có tác dụng.

**Khi nào xảy ra.** Entity dùng id `SEQUENCE`, và bạn đặt batch size lớn hơn 1 cho session (hoặc cho cả ứng dụng bằng cấu hình).

**Khi nào không xảy ra.** Khi batch size không được đặt (xem mục 18.1.2), hoặc khi id là `IDENTITY` (mục 18.2).

**Ví dụ.**

```java
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "item_seq")
@SequenceGenerator(name = "item_seq", schema = "g13_b1_seq_batch",
        sequenceName = "item_seq", allocationSize = 50)
private Long id;
```

```java
inTx(() -> {
    Session session = em.unwrap(Session.class);
    session.setJdbcBatchSize(20); // chỉ áp dụng cho session này
    for (int i = 1; i <= 100; i++) {
        em.persist(new B1Item("Item-" + i, i)); // TRANSIENT thành MANAGED
    }
    em.flush(); // đẩy 100 INSERT xuống JDBC, chia lô 20
});
```

**Kết quả.** Database có đủ 100 dòng. Hibernate gửi đúng **5 lô, mỗi lô 20 dòng**. Điều bất ngờ là công cụ chặn SQL của lab chỉ thấy **một** câu `INSERT`:

```sql
insert into g13_b1_seq_batch.item (name, quantity, id)
values (?, ?, ?)
```

| Cách đếm | Con số |
|---|---|
| Số lô batch JDBC (đọc từ log TRACE) | **5 lô**, mỗi lô 20 dòng |
| Số câu `INSERT` mà bộ chặn SQL nhìn thấy | **1** |
| Số entity Hibernate đếm là đã insert | 100 |
| Số dòng trong bảng item | 100 |

Thay đổi: 100 dòng mới trong bảng item. Giữ nguyên: các bảng khác.

**Vì sao.** Một lô JDBC dùng lại **một PreparedStatement**: Hibernate chuẩn bị câu `INSERT` một lần, rồi nạp lần lượt bộ giá trị của 20 entity, rồi gửi cả lô. Bộ chặn SQL chỉ được gọi lúc câu SQL được **chuẩn bị**, nên nó thấy một lần, dù có 5 lô gửi đi. Đây là cái bẫy khi đo: nếu bạn đếm số `INSERT` bằng bộ chặn SQL để kiểm tra batch, bạn sẽ nhầm. Hibernate cũng không có bộ đếm lô trong `Statistics`. Cách đo đáng tin là nghe log TRACE của `org.hibernate.orm.jdbc.batch`, nơi Hibernate ghi từng dòng "Executing JDBC batch (20 / 20)". Lab gói việc này trong một helper nhỏ cho test.

Vì sao `SEQUENCE` cho phép gom? Vì Hibernate lấy id **trước** khi INSERT (bằng `nextval`), nên nó không cần chạy INSERT ngay để biết id. Nó có thể giữ các entity MANAGED trong persistence context, chờ tới lúc flush rồi gửi cả loạt (Chương 2 đã nói INSERT của `SEQUENCE` được trì hoãn tới flush). Có loạt câu giống nhau nằm chờ thì mới gom được.

**Trong JPA thuần.** `session.setJdbcBatchSize(20)` là API của Hibernate. JPA không có tương đương theo từng session. Cách thay thế là đặt `hibernate.jdbc.batch_size=20` trong cấu hình persistence unit, nhưng khi đó nó áp dụng cho **mọi** thao tác, kể cả những chỗ không cần. Lab chọn đặt theo session vì lý do ấy: bật toàn cục sẽ làm đổi số câu SQL của mọi test khác.

::: {.ghi-nho}
Batch cần hai điều kiện: id không phải `IDENTITY`, và batch size đã được bật. Bộ chặn SQL không đếm được số lô, vì các lô dùng chung một PreparedStatement.
:::

::: {.tracuu codes="G13-S01a"}
:::

#### 18.1.2 Quên đặt batch size: 100 entity vẫn là 100 câu INSERT {.cot-loi}

**Bối cảnh.** Đồng nghiệp đổi id sang `SEQUENCE` "để batch được", merge xong, nhưng job vẫn chậm y như cũ. Không ai nhớ rằng còn một bước nữa.

**Khi nào xảy ra.** Entity dùng `SEQUENCE` nhưng batch size mặc định (không gom).

**Khi nào không xảy ra.** Khi bạn đã đặt batch size, theo session hoặc theo cấu hình (mục 18.1.1).

**Ví dụ.**

```java
inTx(() -> {
    for (int i = 1; i <= 100; i++) {
        em.persist(new B1Item("Item-" + i, i));
    }
    em.flush();
});
```

Cùng entity với mục 18.1.1, chỉ bỏ dòng `setJdbcBatchSize`.

**Kết quả.** Hibernate gửi **100 câu INSERT riêng lẻ** vào bảng item. Số entity được insert vẫn là 100, dữ liệu cuối cùng không khác gì. Chỉ cách gửi khác.

| | Có batch size 20 | Không đặt batch size |
|---|---|---|
| Lần đi về tới database cho phần INSERT | 5 lô | **100 câu riêng** |
| Dữ liệu cuối cùng | 100 dòng | 100 dòng |

Thay đổi: số lần đi về. Giữ nguyên: nội dung dữ liệu.

**Vì sao.** Mặc định của Hibernate là **không batch** (batch size bằng 0 hoặc 1). `SEQUENCE` chỉ **cho phép** gom, chứ không tự bật gom. Đây là điểm dễ hiểu nhầm: nhiều người tưởng đổi sang `SEQUENCE` là tự có batch. Vì kết quả dữ liệu y hệt, bạn sẽ không nhận ra thiếu sót nếu không đếm.

::: {.ghi-nho}
`SEQUENCE` chỉ cho phép batch. Quên đặt batch size thì 100 entity vẫn là 100 câu INSERT riêng.
:::

::: {.tracuu codes="G13-S01b"}
:::

### 18.2 IDENTITY làm tắt batch

#### 18.2.1 Id IDENTITY: đặt batch size 20 vẫn là 100 INSERT, chạy ngay tại persist {.cot-loi}

**Bối cảnh.** Bảng cũ của công ty dùng cột tự tăng (`IDENTITY`). Bạn thêm `setJdbcBatchSize(20)` giống hệt mục 18.1.1 và kỳ vọng job nhanh lên. Nó không nhanh lên chút nào.

**Khi nào xảy ra.** Entity khai `@GeneratedValue(strategy = GenerationType.IDENTITY)`.

**Khi nào không xảy ra.** Khi id là `SEQUENCE` hoặc một chiến lược do ứng dụng tự sinh.

**Ví dụ.**

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

```java
inTx(() -> {
    Session session = em.unwrap(Session.class);
    session.setJdbcBatchSize(20); // giống hệt mục 18.1.1
    em.persist(new B2Item("Item-1", 1));
    // INSERT đã chạy NGAY ở dòng trên, chưa hề flush
    for (int i = 2; i <= 100; i++) {
        em.persist(new B2Item("Item-" + i, i));
    }
});
```

**Kết quả.** Hibernate gửi **100 câu INSERT riêng**, và câu đầu tiên chạy **ngay tại `persist`**, trước cả flush. Lưu ý trong câu INSERT không có cột id: cột này do database tự điền.

```sql
insert into g13_b2_identity.item (name, quantity)
values (?, ?)
```

Thay đổi: 100 dòng mới. Giữ nguyên: dữ liệu giống mục 18.1.1, chỉ khác cách gửi.

**Vì sao.** Với `IDENTITY`, id do database sinh **lúc INSERT**. Mà một entity MANAGED bắt buộc phải có id (persistence context dùng id làm khóa tra cứu). Vậy Hibernate **phải INSERT ngay** khi `persist` để lấy id về. Không còn khoảng "chờ tới flush" nào để gom câu. Mỗi entity một lần đi về, và batch size bị bỏ qua mà không báo gì.

Đây cũng giải thích một khác biệt đã gặp ở Chương 2: với `SEQUENCE` thì `persist` không chạy INSERT, với `IDENTITY` thì chạy.

**Trong JPA thuần.** `GenerationType.IDENTITY` là JPA chuẩn, và hệ quả "không gom được" nằm ở bản chất của cột tự tăng, nên áp dụng với mọi provider. Cách chọn id để batch được (`SEQUENCE`) cũng là JPA chuẩn.

::: {.ghi-nho}
`IDENTITY` buộc Hibernate INSERT ngay tại `persist` để lấy id, nên batch size bị bỏ qua. Cần batch thì dùng `SEQUENCE`.
:::

::: {.tracuu codes="G13-S02"}
:::

### 18.3 allocationSize: số lần hỏi sequence

Mục 18.1 đã chốt `SEQUENCE` cho phép gom INSERT. Nhưng Hibernate vẫn phải **lấy id** cho từng entity, và mỗi lần hỏi `nextval` cũng là một lần đi về. `allocationSize` quyết định mỗi lần hỏi đáng giá bao nhiêu id.

![Sơ đồ bảng item: mỗi lần hỏi sequence cấp cả một dải id](img/g13-b1.png){width=40%}

#### 18.3.1 allocationSize 50 so với 1: số lần gọi nextval cho 100 entity {.cot-loi}

**Bối cảnh.** Job import chạy chậm dù đã bật batch. Khi bật log SQL, bạn thấy một câu `select nextval(...)` lặp lại gần như bằng số dòng. Bạn muốn biết vì sao, và cần chỉnh gì.

**Khi nào xảy ra.** Entity khai `@SequenceGenerator(allocationSize = 1)`: mỗi entity một lần hỏi sequence.

**Khi nào không xảy ra.** Khi `allocationSize` lớn (mặc định của JPA là 50): mỗi lần hỏi cấp cả một dải.

**Ví dụ.**

```java
// allocationSize = 50: mỗi lần nextval cấp một dải 50 id
@SequenceGenerator(name = "item_seq", schema = "g13_b1_seq_batch",
        sequenceName = "item_seq", allocationSize = 50)

// allocationSize = 1: mỗi persist một lần nextval
@SequenceGenerator(name = "item_seq", schema = "g13_b3_seq_alloc1",
        sequenceName = "item_seq", allocationSize = 1)
```

Test persist 100 entity ở mỗi bảng rồi đếm câu `nextval`.

**Kết quả.** Cùng 100 entity, số lần hỏi sequence khác hẳn nhau:

| Cấu hình | Số lần gọi `nextval` cho 100 entity |
|---|---|
| `allocationSize = 50` | **2** (test khẳng định không quá 3) |
| `allocationSize = 1` | **100** (test khẳng định ít nhất 100) |

Thay đổi: số lần đi về để lấy id. Giữ nguyên: dữ liệu insert vào bảng.

**Vì sao.** Sequence trong PostgreSQL được tạo với `increment by 50` (khớp `allocationSize`). Mỗi lần `nextval` trả về một số, và Hibernate (bộ tối ưu "pooled") hiểu rằng nó được cấp cả dải 50 số kế tiếp để tự phát cho các entity, không cần hỏi lại cho tới khi hết dải. Vậy 100 entity cần khoảng 2 lần hỏi. Với `allocationSize = 1` không có dải nào để dùng chung, nên mỗi entity là một lần hỏi.

Tại sao test chấp nhận "không quá 3" thay vì đúng 2? Vì dải 50 id có thể bắt đầu ở giữa chừng (sequence đã chạy từ các lần trước), nên 100 id có thể kéo qua ba dải thay vì hai.

Cái giá của dải lớn: khi ứng dụng tắt đột ngột, phần id còn chưa phát trong dải bị bỏ phí, tạo ra "lỗ hổng" trong dãy id. Với khóa chính thì lỗ hổng là vô hại. Nếu nghiệp vụ cần số liên tục (số hóa đơn), đừng dùng id kỹ thuật cho việc đó.

**Trong JPA thuần.** `@SequenceGenerator` và `allocationSize` là JPA chuẩn (mặc định 50). Tên bộ tối ưu "pooled" là chi tiết của Hibernate; điều bạn cần nhớ là Hibernate yêu cầu `increment by` của sequence trong database **bằng** `allocationSize`, nếu không id có thể trùng.

::: {.ghi-nho}
`allocationSize = 50` làm 100 entity chỉ cần vài lần hỏi sequence; `allocationSize = 1` làm mỗi entity một lần hỏi. Nếu có insert hàng loạt, đừng đặt `allocationSize = 1`.
:::

::: {.tracuu codes="G13-S03a,G13-S03b"}
:::

### 18.4 Persistence context phình to: flush và clear theo lô

Batch giải quyết chuyện **gửi** nhiều INSERT. Còn một bài toán khác: xử lý rất nhiều dòng **đã có sẵn** (đọc ra, sửa, ghi lại). Vấn đề ở đây không phải số lần đi về, mà là bộ nhớ và công kiểm tra thay đổi.

![Sơ đồ bảng customer và purchase_order, dùng cho các thí nghiệm xử lý nhiều dòng](img/g13-p1.png){width=60%}

#### 18.4.1 Vòng lặp xử lý 2 000 khách hàng: không clear và có clear {.cot-loi}

**Bối cảnh.** Mỗi đêm một job đánh dấu 2 000 khách hàng từ trạng thái NEW sang DONE. Job đọc từng trang 100 khách, sửa trạng thái, rồi sang trang kế. Tuần đầu chạy ổn. Khi dữ liệu lên 200 000 dòng, job hết bộ nhớ và chậm dần theo từng trang.

**Khi nào xảy ra.** Vòng lặp chạy trong **một transaction duy nhất** (một persistence context duy nhất) và không bao giờ `clear()`.

**Khi nào không xảy ra.** Khi bạn `flush()` rồi `clear()` sau mỗi trang, hoặc khi mỗi trang là một transaction riêng.

**Ví dụ.** Cách một, không dọn gì:

```java
inTx(() -> {
    Session session = em.unwrap(Session.class);
    for (int first = 0; first < 2000; first += 100) {
        List<P1Customer> page = em.createQuery(
                "select c from G13P1_Customer c order by c.id", P1Customer.class)
            .setFirstResult(first).setMaxResults(100).getResultList();
        for (P1Customer customer : page) {
            customer.setStatus("DONE"); // dirty checking sẽ phát hiện
        }
    }
    // session.getStatistics().getEntityCount() lúc này là 2000
});
```

Cách hai, dọn sau mỗi trang (thêm hai dòng cuối vòng lặp ngoài):

```java
        em.flush(); // đẩy UPDATE của trang này xuống DB TRƯỚC khi clear
        em.clear(); // cả trang từ MANAGED thành DETACHED, persistence context rỗng
```

**Kết quả.** Hai cách cho **cùng kết quả dữ liệu** (2 000 câu UPDATE, cả 2 000 dòng thành DONE), nhưng khác hẳn về cái giá:

| | Không clear | flush và clear mỗi trang |
|---|---|---|
| Số entity persistence context giữ lúc cao nhất | **2 000** | **100** |
| Số câu UPDATE | 2 000 | 2 000 |
| Số dòng có trạng thái DONE trong bảng customer | 2 000 | 2 000 |

Thay đổi: kích thước persistence context. Giữ nguyên: kết quả dữ liệu.

**Vì sao.** Mọi entity MANAGED nằm trong persistence context kèm một **bản chụp** (snapshot) để dirty checking so sánh. Vòng lặp chỉ thêm vào mà không bỏ ra, nên cuối cùng persistence context giữ cả 2 000 entity cùng 2 000 bản chụp. Tệ hơn, mỗi lần chạy query hay flush, Hibernate phải dirty-check **toàn bộ** chúng một lần nữa, nên job càng về cuối càng chậm. Đây là chỗ nhiều job nhập liệu chết khi dữ liệu lớn lên.

`flush()` rồi `clear()` theo trang giữ persistence context nhỏ và hằng số. Thứ tự **flush trước, clear sau** là bắt buộc: `clear()` vứt bỏ mọi entity mà **không ghi** những thay đổi đang chờ. Nếu quên flush, các cập nhật của trang đó biến mất âm thầm, không có lỗi nào. Mục 14.8.2 đã kể một dạng mất dữ liệu khác, và đây là một dạng nữa.

**Trong JPA thuần.** `EntityManager.flush()`, `clear()` và `detach(...)` đều là JPA chuẩn, nên cách làm này mang đi được mọi nơi. Chỉ có `session.getStatistics().getEntityCount()` (dùng để đo trong lab) là của Hibernate.

**Cách tránh.** Đừng bao giờ để một vòng lặp qua dữ liệu lớn giữ mọi entity. Chọn một trong hai: flush và clear theo lô (như trên), hoặc chia mỗi lô thành một transaction riêng. Nếu job chỉ cần đổi một cột cho nhiều dòng, một câu bulk update (Chương 15) còn nhanh hơn cả hai, đổi lại bạn gánh những hạn chế đã nêu ở đó.

::: {.ghi-nho}
Vòng lặp không clear giữ mọi entity và dirty-check lại tất cả mỗi lần flush. Flush rồi clear theo lô giữ persistence context nhỏ; chỉ cần nhớ flush trước clear.
:::

::: {.tracuu codes="G13-S04a,G13-S04b"}
:::

### 18.5 StatelessSession: nhẹ hơn, nhưng bỏ nhiều thứ

`StatelessSession` là một loại session của Hibernate **không có persistence context**. Nó không giữ entity, không giữ bản chụp, không có cache cấp 1. Với dữ liệu rất lớn, đó là điểm hấp dẫn: không có gì để phình ra. Nhưng cũng vì vậy nó bỏ mất những thứ bạn quen dùng. Ba tình huống dưới đây cho thấy từng thứ bị bỏ.

#### 18.5.1 Lệnh chạy ngay, sửa field không tự UPDATE {.cot-loi}

**Bối cảnh.** Bạn chuyển job sang `StatelessSession` để hết phình bộ nhớ. Code cũ đọc entity, `setStatus("DONE")` rồi commit. Sau khi chuyển, job chạy êm, nhưng không dòng nào được cập nhật.

**Khi nào xảy ra.** Bạn đọc entity bằng `StatelessSession`, sửa field và tin rằng commit sẽ lưu.

**Khi nào không xảy ra.** Khi bạn gọi `stateless.update(entity)` tường minh sau khi sửa.

**Ví dụ.**

```java
StatelessSession stateless = emf.unwrap(SessionFactory.class)
        .openStatelessSession();
Transaction tx = stateless.beginTransaction();
stateless.insert(new P1Customer("Bob", "NEW")); // INSERT chạy NGAY
P1Customer alice = stateless.get(P1Customer.class, 9001L);
alice.setStatus("DONE");  // sửa tại chỗ, không ai theo dõi
tx.commit();              // không có UPDATE nào
tx.begin();
stateless.update(alice);  // tường minh: UPDATE ngay lập tức
tx.commit();
```

**Kết quả.** `insert` chạy câu INSERT **ngay lập tức** (không chờ flush). Sửa `alice` rồi commit thì **không có câu UPDATE nào**. Chỉ khi gọi `update(alice)` mới có UPDATE, và nó ghi đè **mọi cột**.

```sql
update g13_p1_pc_stateless.customer
set name=?, status=?
where id=?
```

| Bảng customer | status của Alice |
|---|---|
| Trước | NEW |
| Sau khi sửa và commit (chưa gọi update) | NEW |
| Sau `update(alice)` | **DONE** |

Thay đổi: `status` của Alice, và chỉ sau lệnh `update` tường minh. Giữ nguyên: mọi thứ khác. Bob được lưu ngay ở bước insert, nên bảng có đủ Alice và Bob.

**Vì sao.** Dirty checking cần hai thứ: một persistence context để biết entity nào đang được quản lý, và một bản chụp để so sánh. `StatelessSession` không có cả hai. Vì thế nó cũng không có "flush": mỗi lệnh `insert`, `update`, `delete` là một câu SQL chạy ngay. Hệ quả: entity bạn đọc chỉ là một object Java bình thường, sửa nó chẳng ai hay.

**Trong JPA thuần.** JPA không có `StatelessSession` hay khái niệm tương đương. Muốn né persistence context phình to trong JPA thuần, bạn dùng flush và clear theo lô (mục 18.4) hoặc `em.detach(...)`.

::: {.ghi-nho}
`StatelessSession` chạy mỗi lệnh ngay và không có dirty checking. Sửa field mà không gọi `update()` là không lưu gì cả, và không có lỗi nào báo.
:::

::: {.tracuu codes="G13-S05a"}
:::

#### 18.5.2 Không cascade: order con không được lưu {.cot-loi}

**Bối cảnh.** Mapping của customer có `cascade = PERSIST` trên danh sách order. Với session thường, `persist(customer)` lưu luôn các order. Bạn mang nguyên đoạn code sang `StatelessSession`.

**Khi nào xảy ra.** Bạn `insert` entity cha đang giữ các entity con và tin vào `cascade`.

**Khi nào không xảy ra.** Khi bạn `insert` từng entity con một cách tường minh (cha trước, con sau).

**Ví dụ.**

```java
P1Customer customer = new P1Customer("Alice", "NEW");
customer.addOrder(new P1Order("ORD-1"));
customer.addOrder(new P1Order("ORD-2"));

stateless.insert(customer); // cascade = PERSIST có trên mapping nhưng bị bỏ qua
```

**Kết quả.** Chỉ có customer được INSERT. Hai order **không** được lưu, và **không có lỗi nào**.

| Bảng | Số dòng sau khi commit |
|---|---|
| customer | 1 |
| purchase_order | **0** (đúng ra phải là 2) |

Thay đổi: một dòng customer mới. Giữ nguyên: bảng purchase_order vẫn rỗng.

**Vì sao.** Cascade là việc của persistence context: khi persist cha, nó đi theo quan hệ để persist con. `StatelessSession` không có persistence context, nên chú thích cascade trên mapping bị bỏ qua hoàn toàn. Với `StatelessSession`, mọi entity phải được `insert` thủ công, theo đúng thứ tự cha trước con (để khóa ngoại hợp lệ).

::: {.ghi-nho}
`StatelessSession` bỏ qua cascade. Con không được lưu mà cũng không có lỗi, nên phải insert từng entity theo thứ tự cha trước con.
:::

::: {.tracuu codes="G13-S05b"}
:::

#### 18.5.3 Không lazy loading: chạm quan hệ LAZY thì lỗi {.cot-loi}

**Bối cảnh.** Job đọc các order bằng `StatelessSession` rồi in tên khách hàng của từng order. Với session thường, `order.getCustomer().getName()` chạy bình thường (Chương 11). Với `StatelessSession`, nó ném exception.

**Khi nào xảy ra.** Bạn đọc entity bằng `StatelessSession` rồi chạm vào quan hệ khai LAZY.

**Khi nào không xảy ra.** Khi bạn nạp sẵn quan hệ bằng `join fetch` ngay trong câu query.

**Ví dụ.**

```java
P1Order order = stateless.get(P1Order.class, 11L);
order.getCustomer().getName(); // chạm vào proxy LAZY
```

**Kết quả.** Chạm vào quan hệ ném `LazyInitializationException` với thông báo "Could not initialize proxy ... no session". Câu SQL duy nhất là SELECT order, **không có SELECT customer nào**.

```sql
select po1_0.id, po1_0.customer_id, po1_0.order_no
from g13_p1_pc_stateless.purchase_order po1_0
where po1_0.id=?
```

**Vì sao.** `order.getCustomer()` trả về một proxy chưa nạp, y như ở Chương 11. Muốn nạp, proxy cần một session đang mở để chạy SELECT, và cần persistence context để nhét kết quả vào. `StatelessSession` không có cái nào dành cho lazy loading, nên proxy báo "no session". Muốn dùng quan hệ, bạn phải nạp sẵn bằng `join fetch` ngay trong query (Chương 11).

Ba tình huống ở mục 18.5 gộp lại thành một quy tắc: `StatelessSession` chỉ hợp với công việc **đọc hoặc ghi từng dòng độc lập, dữ liệu phẳng**: import, export, đổ dữ liệu. Nếu code cần dirty checking, cascade hay lazy loading, hãy ở lại session thường và dọn bằng flush và clear (mục 18.4).

::: {.ghi-nho}
`StatelessSession` không nạp lazy được. Muốn dùng quan hệ thì `join fetch` ngay trong query.
:::

::: {.tracuu codes="G13-S05c"}
:::

### 18.6 Ghi chú về order_inserts và order_updates

Hai cấu hình `hibernate.order_inserts` và `hibernate.order_updates` làm Hibernate **sắp xếp lại** các câu INSERT và UPDATE theo bảng và theo khóa trước khi gửi. Mục đích: khi bạn persist đan xen nhiều loại entity (customer, order, customer, order, ...), các câu cùng bảng nằm liền nhau thì mới gom được thành một lô. Nếu không sắp xếp, loạt câu đan xen khiến lô bị cắt liên tục.

Lab **không có test** cho hai cấu hình này, và cuốn sách không khẳng định kết quả của chúng. Lý do: chúng chỉ đặt được cho cả ứng dụng, và nếu bật trong lab thì sẽ đổi thứ tự câu SQL của các tình huống đã đo ở những chương khác (ví dụ thứ tự INSERT ở Chương 4 và 5). Hướng dẫn thực dụng cho dự án của bạn: khi dùng batch với các entity có quan hệ, hãy bật hai cấu hình này rồi **tự đo lại** bằng log batch như ở mục 18.1.

::: {.chua-kiem-chung}
Tác dụng của `order_inserts` và `order_updates` lên số lô không được kiểm chứng bằng test trong lab. Hãy tự viết test trên phiên bản Hibernate của bạn trước khi dựa vào chúng.
:::
