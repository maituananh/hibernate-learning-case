## Chương 2. Vòng đời entity và persistence context

Gần như mọi hành vi "lạ" của Hibernate đều quy về một câu hỏi: **entity này đang ở trạng thái nào, và Hibernate có đang theo dõi nó không?** Tại sao `setName` không làm gì ở chỗ này nhưng lại UPDATE ở chỗ kia? Tại sao `persist` xong mà database vẫn trống? Tại sao `save()` đôi khi chạy thêm một câu SELECT thừa?

Chương này trả lời những câu đó từ gốc. Các chương sau đều mô tả hành vi bằng những khái niệm ở đây (MANAGED, DETACHED, flush, dirty checking), nên đây là chương nên đọc kỹ nhất của sách.

Trong chương này:

- Bốn trạng thái của entity, flush và commit (mục 2.1).
- `persist`, `flush`, SEQUENCE và IDENTITY (mục 2.2).
- Dirty checking và entity DETACHED (mục 2.3).
- `merge` (mục 2.4) và `remove` (mục 2.5).
- `getReference` và `find` (mục 2.6), cache cấp 1 (mục 2.7), flush tự động trước query (mục 2.8).
- `save()` của Spring Data (mục 2.9) và thứ tự các câu SQL khi flush (mục 2.10).

Mọi ví dụ dùng bảng `customer` đơn giản (id, name, email) trừ khi ghi khác. Các chủ đề transaction của Spring (`readOnly`, self-invocation) nằm ở Chương 16.

### 2.1 Bốn trạng thái của entity

::: {.kien-thuc-nen}
**Persistence context** là một vùng nhớ gắn với một transaction (hoặc một `EntityManager`), nơi Hibernate lưu các entity mà nó đang quản lý, cùng một "ảnh chụp" trạng thái lúc nạp để so sánh về sau. Mỗi entity ở một trong bốn trạng thái:

| Trạng thái | Ý nghĩa | Hibernate có theo dõi không |
|---|---|---|
| TRANSIENT | Mới `new`, chưa từng lưu | Không |
| MANAGED | Đang nằm trong persistence context (sau `persist`, `find`, hay `merge`) | Có |
| DETACHED | Từng MANAGED, nhưng persistence context đã đóng hoặc đã `detach`/`clear` | Không |
| REMOVED | Đã gọi `remove`, đang chờ bị DELETE | Có, tới lúc flush |

**Flush** là lúc Hibernate đẩy các thay đổi đang chờ trong persistence context xuống database bằng câu SQL. **Commit** là lúc transaction kết thúc và dữ liệu hiện ra với các transaction khác. Flush xảy ra trước commit (và đôi khi trước một query), nhưng **flush không phải là commit**.

Quy tắc dễ nhớ: chỉ entity MANAGED mới có thay đổi được ghi xuống database. Hết transaction, mọi entity MANAGED trở thành DETACHED.
:::

### 2.2 persist và flush: khi nào câu INSERT thật sự chạy {.bien-the}

Hai cách sinh id khác nhau cho hai hành vi khác nhau. Bảng `customer` dùng SEQUENCE, bảng `identity_customer` dùng IDENTITY:

```java
// SEQUENCE: Hibernate xin id từ sequence trước, INSERT sau.
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g0_customer_seq")
@SequenceGenerator(name = "g0_customer_seq", schema = "g0_lifecycle",
        sequenceName = "customer_seq", allocationSize = 50)
private Long id;
```

```java
// IDENTITY: database tự sinh id khi INSERT, không có sequence.
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

![Hai bảng độc lập: customer dùng sequence để sinh id, identity_customer dùng cột tự tăng](img/g0-customer.png){width=75%}

**Ưu điểm.** SEQUENCE cho phép Hibernate hoãn và gom câu INSERT, vì đã có id từ trước. IDENTITY đơn giản, không cần sequence.

**Nhược điểm.** IDENTITY buộc Hibernate INSERT ngay để lấy id, nên mất khả năng hoãn và gom (ảnh hưởng tới hiệu năng ghi hàng loạt ở Chương 18).

**Nên dùng khi.** Mặc định dùng SEQUENCE. Chỉ dùng IDENTITY khi có lý do bắt buộc (ví dụ database cũ).

#### 2.2.1 persist không INSERT ngay, và flush khác commit {.cot-loi}

**Bối cảnh.** Nhiều người cho rằng `em.persist(customer)` là lưu vào database. Thực tế lệnh này gần như chỉ ghi nhận "entity này giờ do Hibernate quản lý". Hiểu sai điểm này dẫn tới những lỗi khó chịu: đọc lại bằng một connection khác thấy bảng trống, hoặc nghĩ rằng đã `flush` là xong nên dữ liệu đã an toàn.

**Khi nào xảy ra.** Với id sinh bằng SEQUENCE: `persist` chỉ xin id từ sequence và đưa entity vào persistence context. Câu INSERT chạy lúc flush (gọi tay hoặc tự động lúc commit). Sau flush, transaction vẫn chưa commit nên connection khác chưa thấy dòng mới.

**Khi nào không xảy ra.** Với IDENTITY (mục 2.2.2), INSERT chạy ngay ở `persist`.

**Ví dụ.**

```java
Customer customer = inTxReturning(() -> {
    Customer c = new Customer("An", "an@example.com");   // TRANSIENT
    em.persist(c);          // MANAGED, có id, 0 câu INSERT
    em.flush();             // INSERT chạy tại đây; connection khác vẫn thấy 0 dòng
    return c;
});                         // commit; entity thành DETACHED
```

**Kết quả.** Câu INSERT chỉ xuất hiện ở lúc `flush`, không phải lúc `persist`:

```sql
insert into g0_lifecycle.customer (email, name, id)
values (?, ?, ?)
```

Trạng thái và dữ liệu theo từng bước:

| Bước | Trạng thái entity | Số câu INSERT đã chạy | Connection khác thấy |
|---|---|---|---|
| Sau `new` | TRANSIENT | 0 | 0 dòng |
| Sau `persist` | MANAGED (đã có id) | 0 | 0 dòng |
| Sau `flush` | MANAGED | 1 | 0 dòng |
| Sau commit | DETACHED | 1 | 1 dòng |

Dữ liệu cuối cùng trong bảng `customer`: một dòng với name là An và email là an@example.com.

**Vì sao.** Hibernate hoãn mọi câu SQL ghi tới lúc cần thiết, để có thể gom, sắp xếp thứ tự, và bỏ những thay đổi triệt tiêu nhau. Với SEQUENCE nó làm được điều đó vì đã có id từ trước. Còn flush và commit là hai việc khác nhau: flush gửi SQL tới database (trong transaction đang mở), commit mới làm các thay đổi hiện ra với transaction khác. Nếu transaction rollback sau flush, những câu SQL đã gửi cũng bị hủy.

::: {.ghi-nho}
`persist` chỉ ghi nhận entity và xin id. SQL chạy lúc flush. Dữ liệu chỉ hiện ra với người khác sau commit.
:::

::: {.tracuu codes="G0-S01"}
:::

#### 2.2.2 IDENTITY: INSERT chạy ngay khi persist {.cot-loi}

**Bối cảnh.** Hai entity giống hệt nhau, chỉ khác cách sinh id, lại có hành vi `persist` khác nhau. Bạn sẽ gặp điều này khi đọc log SQL: thấy INSERT xuất hiện sớm hơn dự đoán, hoặc khi kết quả đo hiệu năng ghi hàng loạt không như mong đợi.

**Khi nào xảy ra.** Với `GenerationType.IDENTITY`: Hibernate cần id để đưa entity vào persistence context, mà id chỉ có sau khi database INSERT. Vì vậy INSERT chạy **ngay ở dòng `persist`**, trước mọi flush.

**Khi nào không xảy ra.** Với SEQUENCE, INSERT được hoãn tới flush (mục 2.2.1).

**Ví dụ.**

```java
inTx(() -> {
    IdentityCustomer customerBinh = new IdentityCustomer("Bình");
    em.persist(customerBinh);   // INSERT chạy NGAY tại đây
    em.flush();                 // không còn gì để gửi
});
```

**Kết quả.** Câu INSERT chỉ có cột `name`, không có `id` (database tự sinh):

```sql
insert into g0_lifecycle.identity_customer (name)
values (?)
```

Trong cùng connection thấy ngay một dòng sau `persist`. Connection khác vẫn chưa thấy cho tới khi commit: INSERT sớm không có nghĩa là commit sớm.

**Vì sao.** Persistence context lưu entity theo id. Không có id thì không thể đưa entity vào, nên Hibernate buộc phải chạy INSERT để database sinh id. Cái giá: Hibernate mất khả năng hoãn và gom các câu INSERT.

::: {.ghi-nho}
IDENTITY buộc INSERT chạy sớm. Đó là lý do SEQUENCE được ưu tiên khi cần ghi nhiều dòng.
:::

::: {.tracuu codes="G0-S02"}
:::

### 2.3 Dirty checking và entity DETACHED {.bien-the}

Hai khái niệm đi cùng nhau: entity MANAGED được theo dõi và tự lưu khi thay đổi; entity DETACHED thì không. Cả hai ví dụ dưới đây dùng bảng `customer`.

![Bảng customer (id, name, email) và bảng identity_customer dùng chung ví dụ vòng đời](img/g0-customer.png){width=75%}

**Ưu điểm.** Dirty checking cho phép viết code đơn giản: sửa entity MANAGED là đủ, không cần gọi lệnh lưu.

**Nhược điểm.** Cùng một lệnh `setName` có hai kết quả khác nhau tùy trạng thái entity, và Hibernate không báo lỗi khi sửa entity DETACHED. Đây là nguồn của nhiều lỗi "lưu mà không lưu".

**Nên dùng khi.** Luôn nhớ kiểm tra: tại dòng này entity là MANAGED hay DETACHED?

#### 2.3.1 Dirty checking: không gọi save mà vẫn có UPDATE {.cot-loi}

**Bối cảnh.** Một lập trình viên mới học Spring Data thấy ở nhiều chỗ code `repository.save(entity)` sau mỗi lần sửa, và tưởng đó là bắt buộc. Rồi họ gặp một đoạn code không gọi `save()` mà dữ liệu vẫn đổi, và không hiểu vì sao.

**Khi nào xảy ra.** Entity đang MANAGED (vừa `find` trong transaction) rồi bạn sửa field. Khi flush (lúc commit), Hibernate so sánh trạng thái hiện tại với "ảnh chụp" lúc nạp, thấy khác thì tự sinh UPDATE.

**Khi nào không xảy ra.** Entity DETACHED, hoặc transaction ở chế độ chỉ đọc (Chương 16).

**Ví dụ.**

```java
inTx(() -> {
    Customer customerAn = em.find(Customer.class, customerId);   // MANAGED
    customerAn.setName("Bình");
    // Không gọi save/merge. Lúc commit, flush sẽ UPDATE.
});
```

**Kết quả.** Hai câu SQL, một SELECT khi `find` và một UPDATE lúc commit:

```sql
select c1_0.id, c1_0.email, c1_0.name
from g0_lifecycle.customer c1_0
where c1_0.id=?

update g0_lifecycle.customer
set email=?, name=?
where id=?
```

Dữ liệu trong bảng `customer` sau khi chạy:

| id | email | name |
|---|---|---|
| id của customer | an@example.com | Bình |

Thay đổi: cột `name` từ An thành Bình. Giữ nguyên: cột `email`. Lưu ý câu UPDATE ghi **cả** hai cột (`email`, `name`) dù chỉ một cột đổi, vì Hibernate mặc định cập nhật mọi cột (xem `@DynamicUpdate` ở Chương 14).

**Vì sao.** Persistence context giữ một bản chụp của entity lúc nạp. Lúc flush, Hibernate duyệt từng entity MANAGED và so sánh với bản chụp. Khác thì sinh UPDATE. Đó là dirty checking.

::: {.ghi-nho}
Với entity MANAGED, sửa field là đủ. Không cần (và không nên) gọi `save()`.
:::

::: {.tracuu codes="G0-S03"}
:::

#### 2.3.2 Sửa entity DETACHED sau khi transaction kết thúc: không có gì xảy ra {.cot-loi}

**Bối cảnh.** Một service tải customer trong transaction, trả về cho controller; controller sửa tên rồi mong là dữ liệu sẽ được lưu. Không có lỗi, không có UPDATE, và dữ liệu giữ nguyên. Đây là lỗi im lặng phổ biến nhất của người mới.

**Khi nào xảy ra.** Entity được `find` ở transaction 1. Hết transaction 1, persistence context đóng và entity thành DETACHED. Bạn `setName` bên ngoài, rồi mở transaction 2 mà không `merge`: không có UPDATE nào.

**Khi nào không xảy ra.** Nếu `merge` entity đó trong transaction 2 (mục 2.4), hoặc nếu bạn sửa khi entity còn MANAGED.

**Ví dụ.**

```java
Customer detachedCustomer = inTxReturning(
        () -> em.find(Customer.class, customerId));   // hết transaction: DETACHED

detachedCustomer.setName("Bình");                     // không ai theo dõi

inTx(() -> { /* transaction 2: không merge */ });
```

**Kết quả.** Không có câu UPDATE nào. Dữ liệu trong database vẫn giữ giá trị cũ:

| id | name |
|---|---|
| id của customer | An |

**Vì sao.** Transaction 2 có một persistence context hoàn toàn mới, không biết gì về object bạn đang cầm. Entity DETACHED không còn được ai theo dõi, nên thay đổi trên nó chỉ nằm trong bộ nhớ Java.

**Cách tránh.** Hoặc sửa entity khi còn MANAGED (trong cùng transaction với lúc tải), hoặc dùng `merge` có chủ đích (mục 2.4).

::: {.ghi-nho}
Sửa entity DETACHED không có hiệu lực, và Hibernate không báo lỗi.
:::

::: {.tracuu codes="G0-S04a"}
:::

#### 2.3.3 detach và clear trong cùng transaction: cũng không có UPDATE {.cot-loi}

**Bối cảnh.** Có người nghĩ miễn transaction còn mở thì mọi entity vẫn được theo dõi. Không đúng: điều quan trọng là entity còn nằm trong persistence context hay không.

**Khi nào xảy ra.** Trong một transaction đang mở, bạn gọi `em.detach(customerAn)` (gỡ một entity) hoặc `em.clear()` (gỡ tất cả), rồi mới `setName`. Entity đã thành DETACHED nên thay đổi bị bỏ qua.

**Khi nào không xảy ra.** Nếu entity vẫn nằm trong persistence context (chưa detach hay clear) thì dirty checking ghi thay đổi như bình thường.

**Ví dụ.**

```java
inTx(() -> {
    Customer customerAn = em.find(Customer.class, customerAnId);
    Customer customerBinh = em.find(Customer.class, customerBinhId);

    em.detach(customerAn);      // gỡ một entity
    customerAn.setName("An (đã sửa)");

    em.clear();                 // gỡ tất cả
    customerBinh.setName("Bình (đã sửa)");
});
```

**Kết quả.** Không có câu UPDATE nào, và cả hai dòng trong database giữ nguyên tên cũ, dù transaction vẫn còn mở khi sửa.

**Vì sao.** Dirty checking chỉ duyệt các entity đang nằm trong persistence context lúc flush. `detach` và `clear` loại entity khỏi đó, nên chúng không còn được so sánh.

::: {.ghi-nho}
Điều quyết định là entity còn nằm trong persistence context hay không, không phải transaction còn mở hay không.
:::

::: {.tracuu codes="G0-S04b"}
:::

### 2.4 merge: đưa thay đổi của entity DETACHED trở lại {.bien-the}

`merge` là cách chính thức để đưa thay đổi của một entity DETACHED vào persistence context hiện tại. Điều cần nhớ nhất: nó **không** biến object bạn truyền vào thành MANAGED.

![Bảng customer: merge đọc dòng hiện tại rồi sao chép trạng thái của entity DETACHED sang một bản MANAGED mới](img/g0-customer.png){width=75%}

**Ưu điểm.** Dùng được khi object đến từ nơi khác (một request, một transaction trước đó) và bạn muốn lưu thay đổi của nó.

**Nhược điểm.** Phải dùng giá trị **trả về**, và merge tốn thêm một câu SELECT. Với object đã mất trong database (mục 2.4.2), merge có thể thất bại.

**Nên dùng khi.** Khi thực sự cần lưu một object DETACHED. Với dữ liệu đến từ request, thường tốt hơn là tải entity ra rồi áp thay đổi lên (xem Chương 14).

#### 2.4.1 merge trả về một bản MANAGED khác {.cot-loi}

**Bối cảnh.** Bạn có một entity DETACHED đã sửa (ví dụ từ form sửa thông tin). Bạn gọi `em.merge(detached)`, rồi tiếp tục sửa chính object đó và nghĩ rằng nó đã MANAGED. Phần sửa sau merge bị mất lặng lẽ. Spring Data cũng dính bẫy này: `repository.save(entity)` trả về bản đã merge, và nhiều người bỏ qua giá trị trả về.

**Khi nào xảy ra.** Luôn luôn khi gọi `merge` với entity DETACHED. Hibernate SELECT dòng hiện tại, sao chép trạng thái từ `detached` sang một bản MANAGED mới, và trả về bản đó. Object truyền vào vẫn là DETACHED.

**Khi nào không xảy ra.** Nếu entity truyền vào đã MANAGED, `merge` trả về chính nó.

**Ví dụ.**

```java
inTx(() -> {
    Customer managedCustomer = em.merge(detachedCustomer);   // bản MANAGED khác
    detachedCustomer.setName("Y");   // sửa trên bản DETACHED: sẽ bị mất
});
```

**Kết quả.** Hai câu SQL: SELECT để đọc dòng hiện tại, UPDATE theo giá trị tại thời điểm merge:

```sql
select c1_0.id, c1_0.email, c1_0.name
from g0_lifecycle.customer c1_0
where c1_0.id=?

update g0_lifecycle.customer
set email=?, name=?
where id=?
```

Dữ liệu cuối: cột `name` giữ giá trị lúc merge (X). Lần sửa thành "Y" trên bản DETACHED **không** được lưu. `managedCustomer` và `detachedCustomer` là hai object khác nhau.

**Vì sao.** Persistence context chỉ theo dõi bản MANAGED. Object bạn truyền vào chỉ là nguồn để sao chép trạng thái, không bao giờ được đưa vào persistence context.

**Cách tránh.** Luôn dùng giá trị trả về: `customer = em.merge(customer)`, `customer = repository.save(customer)`.

::: {.ghi-nho}
`merge` trả về một object khác. Luôn dùng giá trị trả về, vì object truyền vào vẫn là DETACHED.
:::

::: {.tracuu codes="G0-S05"}
:::

#### 2.4.2 Rollback rồi merge một entity "ma" {.mo-rong}

**Bối cảnh.** Bạn `persist` một entity trong transaction rồi transaction bị rollback. Object vẫn giữ id (đã lấy từ sequence), nhưng database không có dòng nào. Nếu bạn `merge` lại object đó ở transaction mới thì sao?

**Kết quả.** Hibernate 7.4.15 không INSERT mà ném `OptimisticLockException`: nó thấy object có id nhưng database không có dòng, nên coi là dòng đã bị xóa bởi người khác. Không có dòng nào được tạo. Hành vi này đã đổi từ Hibernate 6.6 trở đi.

::: {.ghi-nho}
Đừng dùng lại object từ một transaction đã rollback. Hãy tạo object mới.
:::

::: {.tracuu codes="G0-S13"}
:::

### 2.5 remove: xóa entity {.bien-the}

`remove` đưa entity sang trạng thái REMOVED, và câu DELETE chỉ chạy lúc flush, giống mọi lệnh ghi khác.

![Bảng customer: remove đánh dấu entity để xóa, câu DELETE chạy lúc flush](img/g0-customer.png){width=75%}

**Ưu điểm.** Nhất quán với cách Hibernate xử lý persist và update: mọi thay đổi gom lại và gửi lúc flush.

**Nhược điểm.** `em.remove` chỉ chấp nhận entity MANAGED, nên với entity DETACHED phải tải lại trước.

**Nên dùng khi.** Xóa theo từng entity. Với xóa hàng loạt, xem Chương 15.

#### 2.5.1 remove: trạng thái REMOVED ngay, DELETE chỉ chạy lúc flush {.cot-loi}

**Bối cảnh.** Sau `em.remove(customer)`, dòng trong database mất ngay hay không? Nhiều người nghĩ có, nên gặp bất ngờ khi query ngay sau đó trong cùng transaction.

**Khi nào xảy ra.** `remove` chuyển entity sang REMOVED (`em.contains` trả về false) và xếp hàng câu DELETE. Database chưa đổi cho tới khi flush.

**Khi nào không xảy ra.** Sau `flush`, DELETE đã chạy trong transaction, nhưng các connection khác chỉ thấy sau commit.

**Ví dụ.**

```java
inTx(() -> {
    Customer customerAn = em.find(Customer.class, customerId);
    em.remove(customerAn);   // REMOVED; dòng vẫn còn trong database
    em.flush();              // DELETE chạy tại đây
});
```

**Kết quả.**

```sql
select c1_0.id, c1_0.email, c1_0.name
from g0_lifecycle.customer c1_0
where c1_0.id=?

delete
from g0_lifecycle.customer
where id=?
```

Sau `remove` dòng vẫn còn trong database; sau `flush` trong cùng transaction thì biến mất; sau commit thì mọi connection đều không còn thấy dòng đó.

**Vì sao.** Cùng triết lý hoãn ghi: `remove` chỉ đổi trạng thái entity trong persistence context và xếp hàng thao tác, DELETE gửi lúc flush.

::: {.ghi-nho}
Mọi thao tác ghi (INSERT, UPDATE, DELETE) đều bị hoãn tới flush.
:::

::: {.tracuu codes="G0-S06a"}
:::

#### 2.5.2 remove trên entity DETACHED bị từ chối {.cot-loi}

**Bối cảnh.** Bạn có một customer DETACHED (tải ở request trước) và gọi `em.remove(customer)`. Hibernate từ chối.

**Khi nào xảy ra.** `em.remove` với entity DETACHED ném `IllegalArgumentException`, vì persistence context không biết object này nên không thể xếp hàng lệnh xóa.

**Khi nào không xảy ra.** Với entity MANAGED (mục 2.5.1), hoặc khi dùng `repository.delete` (mục 2.5.3).

**Ví dụ.**

```java
Customer detachedCustomer = inTxReturning(
        () -> em.find(Customer.class, customerId));

inTx(() -> em.remove(detachedCustomer));    // IllegalArgumentException
```

**Kết quả.** Exception, và dòng trong database vẫn còn (một dòng).

**Vì sao.** `remove` chỉ làm việc với entity mà persistence context đang quản lý.

**Cách tránh.** Tải lại bằng `find` (hoặc `getReference`) trong transaction hiện tại rồi mới `remove`.

::: {.ghi-nho}
`em.remove` chỉ nhận entity MANAGED.
:::

::: {.tracuu codes="G0-S06b"}
:::

#### 2.5.3 repository.delete trên entity DETACHED: Spring Data lo giúp {.cot-loi}

**Bối cảnh.** Mục 2.5.2 cho thấy `em.remove(detached)` thất bại. Vậy với Spring Data thì sao?

**Khi nào xảy ra.** `repository.delete(detached)` xóa được: Spring Data tự `find` lại để có bản MANAGED (một câu SELECT) rồi mới `remove` (một câu DELETE).

**Khi nào không xảy ra.** Không có lỗi như ở `em.remove`.

**Ví dụ.**

```java
Customer detachedCustomer = customerRepository.save(
        new Customer("An", "an@example.com"));

customerRepository.delete(detachedCustomer);
```

**Kết quả.** Một câu SELECT rồi một câu DELETE, và bảng còn 0 dòng.

```sql
select c1_0.id, c1_0.email, c1_0.name
from g0_lifecycle.customer c1_0
where c1_0.id=?

delete
from g0_lifecycle.customer
where id=?
```

**Vì sao.** Repository bọc thêm bước chuẩn bị mà `EntityManager` thô không làm. Đổi lại, bạn trả một câu SELECT.

::: {.ghi-nho}
`repository.delete` che lỗi của `em.remove(detached)`, nhưng tốn thêm một câu SELECT.
:::

::: {.tracuu codes="G0-S06c"}
:::

#### 2.5.4 persist lại entity đang REMOVED: việc xóa bị hủy {.mo-rong}

**Bối cảnh.** Trong cùng transaction, bạn `find`, `remove`, rồi `persist` lại chính entity đó.

**Kết quả.** Entity quay về MANAGED, câu DELETE bị hủy, không có SQL ghi nào, và dòng vẫn còn trong database. Đúng như đặc tả JPA.

::: {.ghi-nho}
`persist` trên entity REMOVED hủy lệnh xóa đang chờ.
:::

::: {.tracuu codes="G0-S07"}
:::

### 2.6 getReference và find: có cần tải entity không {.bien-the}

`find` luôn trả về entity đã nạp đầy đủ (nếu cần sẽ SELECT). `getReference` trả về một **proxy** chỉ chứa id, và chỉ SELECT khi bạn đọc một thuộc tính khác id.

![Bảng customer và purchase_order: order giữ khóa ngoại customer_id, chỉ cần id của customer để ghi](img/g0-order.png){width=80%}

**Ưu điểm.** `getReference` tiết kiệm một câu SELECT khi chỉ cần gán khóa ngoại.

**Nhược điểm.** Không kiểm tra sự tồn tại của dòng: id sai chỉ lộ ra khi chạm vào thuộc tính hoặc khi flush.

**Nên dùng khi.** Khi chỉ cần id để gán quan hệ. Khi cần dữ liệu thật hoặc cần biết dòng có tồn tại, dùng `find`.

#### 2.6.1 getReference: proxy chưa khởi tạo cho tới khi đọc thuộc tính khác id {.cot-loi}

**Bối cảnh.** Bạn thấy `getReference` ở nhiều nơi nhưng không rõ nó khác `find` ra sao, và vì sao đôi khi gọi getter mới thấy SELECT xuất hiện.

**Khi nào xảy ra.** `getReference` không SELECT và `getId()` cũng không (id có sẵn trong proxy). `getName()` mới kích hoạt SELECT và khởi tạo proxy.

**Khi nào không xảy ra.** `find` SELECT ngay lập tức.

**Ví dụ.**

```java
inTx(() -> {
    Customer customerProxy = em.getReference(Customer.class, customerId);
    Hibernate.isInitialized(customerProxy);   // false: chưa SELECT
    customerProxy.getId();                    // vẫn false, id có sẵn
    customerProxy.getName();                  // SELECT chạy ở đây
    Hibernate.isInitialized(customerProxy);   // true
});
```

**Kết quả.** Không có câu SELECT nào cho tới `getName()`. Sau `getName()` có một câu SELECT và proxy đã khởi tạo, dữ liệu đúng như trong database.

**Vì sao.** Proxy là một object do Hibernate tạo ra, đóng vai entity nhưng chỉ nhớ id. Mỗi lần gọi một getter khác, proxy kiểm tra "mình đã nạp dữ liệu chưa?", và nếu chưa thì SELECT lúc đó.

::: {.ghi-nho}
`getReference` và `getId()` không chạm database. Đọc thuộc tính khác id mới SELECT.
:::

::: {.tracuu codes="G0-S08a"}
:::

#### 2.6.2 Gán khóa ngoại bằng getReference: không cần SELECT {.cot-loi}

**Bối cảnh.** Đây là cách dùng `getReference` phổ biến nhất: tạo một order trỏ tới customer đã có, mà không tải customer lên. Chương 4 (mục 4.2.2) đi sâu hơn.

**Khi nào xảy ra.** `order.setCustomer(em.getReference(Customer.class, id))` rồi persist order: Hibernate chỉ cần id của proxy để ghi cột `customer_id`.

**Khi nào không xảy ra.** Với `find`, có thêm một câu SELECT customer.

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder orderA = new PurchaseOrder("A-001");
    orderA.setCustomer(em.getReference(Customer.class, customerId));
    em.persist(orderA);
});
```

**Kết quả.** Một câu INSERT order, không có SELECT customer. Cột `customer_id` của dòng mới bằng id của customer.

**Vì sao.** Cột khóa ngoại chỉ cần một con số, và proxy đã có sẵn con số đó.

::: {.ghi-nho}
Muốn gán khóa ngoại, dùng `getReference` (hoặc `getReferenceById`) thay cho `find` để tiết kiệm một SELECT.
:::

::: {.tracuu codes="G0-S08b"}
:::

#### 2.6.3 getReference với id không tồn tại: lỗi chỉ xuất hiện khi chạm vào thuộc tính {.cot-loi}

**Bối cảnh.** Bạn gọi `getReference` với một id sai (gõ nhầm, hoặc dòng đã bị xóa). Không có lỗi gì, và bạn tưởng mọi thứ ổn.

**Khi nào xảy ra.** `getReference` không hỏi database nên không biết dòng có tồn tại hay không. Chỉ khi đọc thuộc tính khác id, proxy mới SELECT, không thấy dòng và ném `EntityNotFoundException`.

**Khi nào không xảy ra.** `find` với id không tồn tại trả về null, không ném exception.

**Ví dụ.**

```java
inTx(() -> {
    Customer missingProxy = em.getReference(Customer.class, MISSING_ID);
    Hibernate.isInitialized(missingProxy);   // false: chưa hỏi database
    missingProxy.getName();                  // EntityNotFoundException
});
```

**Kết quả.** `getReference` êm. `getName()` ném `EntityNotFoundException`.

**Vì sao.** Proxy chỉ kiểm tra database khi cần dữ liệu thật.

::: {.ghi-nho}
`getReference` đổi sự kiểm tra tồn tại lấy một câu SELECT tiết kiệm được.
:::

::: {.tracuu codes="G0-S08c"}
:::

#### 2.6.4 Dùng reference sai làm khóa ngoại: lỗi lúc flush {.cot-loi}

**Bối cảnh.** Kết hợp hai tình huống trước: gán `getReference` với id sai làm khóa ngoại của một order.

**Khi nào xảy ra.** `persist` êm, vì Hibernate không kiểm tra. Lỗi xảy ra lúc flush, khi database từ chối INSERT vì khóa ngoại trỏ tới dòng không tồn tại.

**Khi nào không xảy ra.** Nếu dùng `find` thì lỗi (null hoặc customer không có) hiện ra sớm hơn, ở lúc tải.

**Ví dụ.**

```java
inTx(() -> {
    PurchaseOrder orderA = new PurchaseOrder("A-001");
    orderA.setCustomer(em.getReference(Customer.class, MISSING_ID));
    em.persist(orderA);   // êm: không có SQL
    em.flush();           // INSERT chạy, database từ chối vì vi phạm khóa ngoại
});
```

**Kết quả.** Exception vi phạm khóa ngoại lúc flush. Transaction rollback, bảng `purchase_order` trống.

**Vì sao.** Hibernate tin bạn về id. Người kiểm tra duy nhất là ràng buộc khóa ngoại của database.

::: {.ghi-nho}
`getReference` không kiểm tra sự tồn tại: nếu id sai, lỗi nổ lúc flush chứ không nổ ở dòng gọi.
:::

::: {.tracuu codes="G0-S08d"}
:::

### 2.7 Cache cấp 1: một dòng database, một instance {.bien-the}

Persistence context cũng là **cache cấp 1** (first-level cache): trong một transaction, mỗi dòng database chỉ tương ứng với một object Java.

![Bảng customer: hai lần find cùng id trong một transaction chỉ SELECT một lần](img/g0-customer.png){width=75%}

**Ưu điểm.** Tránh SELECT lặp lại, và đảm bảo cả code dùng chung một object cho cùng một dòng.

**Nhược điểm.** Đọc lại bằng `find` có thể trả về dữ liệu **cũ** mà không hỏi database. Vì vậy test của lab luôn đọc dữ liệu bằng JDBC.

**Nên dùng khi.** Đây là hành vi mặc định, không phải thứ bạn bật tắt.

#### 2.7.1 find hai lần chỉ SELECT một lần, cùng một instance {.cot-loi}

**Bối cảnh.** Hai đoạn code trong cùng một transaction đều gọi `find` cùng id. Bạn muốn biết có bị SELECT hai lần không và hai object có là một không.

**Khi nào xảy ra.** Lần `find` đầu SELECT. Lần hai trả về cùng instance từ persistence context (so sánh `==` đúng) và không SELECT.

**Khi nào không xảy ra.** Sau `em.clear()`, persistence context rỗng nên `find` lại SELECT và trả về một instance mới khác.

**Ví dụ.**

```java
inTx(() -> {
    Customer firstFind = em.find(Customer.class, customerId);    // SELECT
    Customer secondFind = em.find(Customer.class, customerId);   // không SELECT
    firstFind == secondFind;                                     // true

    em.clear();
    Customer thirdFind = em.find(Customer.class, customerId);    // SELECT mới
    thirdFind == firstFind;                                      // false
});
```

**Kết quả.** Hai câu SELECT tổng cộng (lần đầu và sau `clear`). Lần hai không có SELECT.

**Vì sao.** Persistence context lưu entity theo id. Trước khi hỏi database, `find` kiểm tra ở đó trước. Đây cũng là lý do assert dữ liệu trong lab phải đọc bằng JDBC: `find` có thể trả về object cũ trong bộ nhớ.

::: {.ghi-nho}
Trong một persistence context, một dòng ứng với đúng một object. `find` lặp lại không tốn SELECT.
:::

::: {.tracuu codes="G0-S09"}
:::

### 2.8 Flush tự động trước query {.bien-the}

Hibernate flush không chỉ lúc commit. Mặc định (FlushMode AUTO), nó cũng flush **trước khi chạy một query** có thể đụng tới dữ liệu đang chờ ghi.

![Bảng customer: UPDATE đang chờ được đẩy xuống trước khi query chạy](img/g0-customer.png){width=75%}

**Ưu điểm.** Query luôn thấy dữ liệu nhất quán với những gì code vừa sửa.

**Nhược điểm.** Có thể sinh câu UPDATE ở chỗ bạn không ngờ tới, ngay trước một query.

**Nên dùng khi.** Đây là mặc định. Hiểu nó để đọc log SQL cho đúng.

#### 2.8.1 JPQL kích hoạt flush UPDATE trước SELECT {.mo-rong}

**Bối cảnh.** Bạn `setName("Bình")` rồi chạy JPQL `where name = 'Bình'` (chưa flush).

**Kết quả.** Hibernate tự flush trước: câu UPDATE chạy trước câu SELECT, nên query tìm thấy customer với tên mới.

```sql
update g0_lifecycle.customer
set email=?, name=?
where id=?

select c1_0.id, c1_0.email, c1_0.name
from g0_lifecycle.customer c1_0
where c1_0.name=?
```

::: {.ghi-nho}
Trước một query JPQL, Hibernate tự đẩy các thay đổi đang chờ xuống database.
:::

::: {.tracuu codes="G0-S10a"}
:::

#### 2.8.2 Native query cũng kích hoạt flush {.mo-rong}

**Bối cảnh.** Native query là SQL thuần nên Hibernate không biết nó đụng bảng nào. Liệu nó có flush không?

**Kết quả.** Có, ở Hibernate 7.4.15 chạy theo chế độ JPA của Spring Boot, UPDATE vẫn chạy trước native query, và query thấy tên mới.

::: {.ghi-nho}
Native query cũng được flush trước, vì Hibernate coi nó có thể chạm mọi bảng.
:::

::: {.tracuu codes="G0-S10b"}
:::

### 2.9 save của Spring Data và isNew: persist hay merge {.bien-the}

`repository.save(entity)` không phải một lệnh duy nhất: Spring Data tự quyết định gọi `persist` (entity mới) hay `merge` (entity cũ), dựa vào hàm `isNew()`. Tùy loại id của entity, quyết định đó khác nhau, và hệ quả là có hay không một câu SELECT thừa.

```java
// Id tự sinh: id == null nghĩa là entity mới.
@Id @GeneratedValue Long id;

// Id gán tay, không @Version: id luôn khác null.
@Id @Column(name = "id") String sku;

// Id gán tay, có @Version: version == null nghĩa là entity mới.
@Id @Column(name = "id") String sku;
@Version Long version;
```

![Hai bảng: sku_product với id gán tay, và versioned_sku_product thêm cột version](img/g0-sku.png){width=80%}

**Ưu điểm.** Một lệnh `save` dùng được cho cả thêm mới lẫn cập nhật.

**Nhược điểm.** Với id gán tay không có `@Version`, mọi lần `save` entity mới đều tốn thêm một câu SELECT.

**Nên dùng khi.** Với id gán tay, hãy thêm `@Version` (hoặc implement `Persistable`) để `isNew()` đoán đúng.

#### 2.9.1 Id tự sinh: save gọi persist, chỉ một INSERT {.cot-loi}

**Bối cảnh.** Trường hợp quen thuộc nhất: entity có id tự sinh.

**Khi nào xảy ra.** Id là null nên Spring Data coi là entity mới và gọi `persist`.

**Khi nào không xảy ra.** Với id gán tay (mục 2.9.2), `isNew()` trả về false.

**Ví dụ.**

```java
Customer newCustomer = new Customer("An", "an@example.com");
Customer returned = customerRepository.save(newCustomer);
// returned == newCustomer (cùng một object)
```

**Kết quả.** Một câu INSERT, không có SELECT thừa. Giá trị trả về là chính object truyền vào.

**Vì sao.** `persist` đưa chính object vào persistence context, không tạo bản sao.

::: {.ghi-nho}
Id tự sinh: `save` là `persist`, đơn giản và không tốn SELECT.
:::

::: {.tracuu codes="G0-S11a"}
:::

#### 2.9.2 Id gán tay, không có Version: save thành merge, SELECT thừa rồi mới INSERT {.cot-loi}

**Bối cảnh.** Bảng sản phẩm dùng mã SKU làm khóa chính, do người dùng gán. Bạn gọi `save(new SkuProduct("SKU-1", ...))` và thấy log có một câu SELECT trước INSERT mà không biết vì sao.

**Khi nào xảy ra.** Id đã khác null ngay khi `new`, nên Spring Data đoán đây là entity cũ và gọi `merge`. `merge` SELECT trước để kiểm tra dòng đã có chưa, không thấy mới INSERT.

**Khi nào không xảy ra.** Nếu có `@Version` (mục 2.9.3) hoặc implement `Persistable`.

**Ví dụ.**

```java
SkuProduct newProduct = new SkuProduct("SKU-1", "Bàn phím");
SkuProduct returned = skuProductRepository.save(newProduct);
// returned != newProduct (merge trả về bản khác)
```

**Kết quả.** Một SELECT rồi một INSERT:

```sql
select sp1_0.id, sp1_0.name
from g0_lifecycle.sku_product sp1_0
where sp1_0.id=?

insert into g0_lifecycle.sku_product (name, id)
values (?, ?)
```

Giá trị trả về là bản MANAGED khác object truyền vào (như mục 2.4.1).

**Vì sao.** Spring Data không có cách nào biết entity này mới hay cũ từ một id gán tay. Nó chọn `merge` vì an toàn cho cả hai trường hợp, đổi lại tốn thêm một câu SELECT.

::: {.ghi-nho}
Id gán tay mà không có dấu hiệu "mới" thì `save` tốn thêm một câu SELECT.
:::

::: {.tracuu codes="G0-S11b"}
:::

#### 2.9.3 Id gán tay có Version: save là persist, chỉ một INSERT {.cot-loi}

**Bối cảnh.** Bạn muốn bỏ SELECT thừa của mục 2.9.2 mà vẫn giữ id gán tay.

**Khi nào xảy ra.** Entity có `@Version` kiểu wrapper (`Long`). Trước khi lưu, `version == null`, nên Spring Data coi là entity mới và gọi `persist`.

**Khi nào không xảy ra.** Entity không có `@Version` (mục 2.9.2).

**Ví dụ.**

```java
VersionedSkuProduct newProduct = new VersionedSkuProduct("SKU-2", "Chuột");
VersionedSkuProduct returned = versionedSkuProductRepository.save(newProduct);
```

**Kết quả.** Một câu INSERT, không SELECT, trả về chính object truyền vào:

```sql
insert into g0_lifecycle.versioned_sku_product (name, version, id)
values (?, ?, ?)
```

Dữ liệu trong database: dòng SKU-2 với cột `version` bằng 0.

**Vì sao.** `@Version` cho Spring Data một dấu hiệu rõ ràng: nếu version còn null thì chưa từng được lưu. Hibernate đặt version bằng 0 lúc INSERT rồi tăng lên mỗi lần UPDATE.

::: {.ghi-nho}
Với id gán tay, thêm `@Version` (hoặc implement `Persistable`) để `save` không tốn SELECT thừa.
:::

::: {.tracuu codes="G0-S11c"}
:::

### 2.10 Thứ tự các câu SQL khi flush

::: {.kien-thuc-nen}
Một điều ít người để ý: **thứ tự các câu SQL lúc flush không phải thứ tự bạn gọi lệnh trong code**. Hibernate gom các thao tác vào hàng đợi (action queue) và gửi theo thứ tự riêng của nó. Điều lab đã chứng minh bằng test là: các câu INSERT chạy **trước** các câu DELETE trong cùng một lần flush.

Hệ quả thực tế: nếu code của bạn `remove` một entity rồi `persist` một entity khác có cùng giá trị unique, câu INSERT mới sẽ chạy **trước** câu DELETE cũ, và database báo vi phạm ràng buộc unique, dù thứ tự trong code trông hợp lệ. Lab đã chứng minh điều này ở cả hai cơ chế xóa (xóa thủ công và `orphanRemoval`), và bàn kỹ ở Chương 6.

Cách xử lý chung: gọi `em.flush()` ngay sau lệnh xóa để ép DELETE chạy trước, hoặc cập nhật entity cũ thay vì xóa rồi thêm mới.
:::
