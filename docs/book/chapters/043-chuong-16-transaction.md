## Chương 16. Ranh giới transaction trong Spring

Các chương trước giả định mọi thao tác nằm trong một transaction mà test mở sẵn bằng `inTx(...)`. Ngoài đời thực, người mở và đóng transaction là **Spring**, thông qua annotation `@Transactional` trên method của service. Nếu bạn đặt annotation sai chỗ, hiểu sai nó làm gì, hoặc gọi method theo cách làm nó mất tác dụng, thì Hibernate vẫn chạy "đúng" mà dữ liệu của bạn thì sai: không UPDATE, mất cả hai thay đổi, hoặc `LazyInitializationException` ở tầng trên.

Chương này đi qua những tình huống xảy ra thường xuyên nhất ở ranh giới transaction. Mỗi tình huống có một service nhỏ, một test chạy trên PostgreSQL thật, và kết luận lấy từ database chứ không từ trí nhớ.

![Sơ đồ bảng của mô hình dùng trong chương: product, audit_log, purchase_order và order_item](img/g11-t1.png){width=85%}

Trong chương này:

- `@Transactional` hoạt động thông qua proxy, và proxy quyết định mọi thứ (mục 16.1).
- `readOnly = true`: sửa entity không ghi gì, `save` entity mới thì lỗi (mục 16.2).
- Self-invocation: gọi method trong cùng bean làm mất `@Transactional` (mục 16.3).
- Propagation `REQUIRED` và `REQUIRES_NEW` (mục 16.4).
- Exception nào thì rollback (mục 16.5).
- Service nên trả entity hay DTO (mục 16.6).
- Open Session in View (mục 16.7).

**Trong JPA thuần.** `@Transactional` là của Spring, không phải của JPA. Nếu không dùng Spring, JPA thuần quản lý transaction qua `EntityTransaction` (`em.getTransaction().begin()`, `commit()`, `rollback()`) hoặc qua JTA trong container. Những khái niệm của chương (persistence context sống cùng transaction, rollback) vẫn đúng; chỉ cú pháp khác. Mỗi mục nêu rõ phần nào là của Spring.

### 16.1 Transactional hoạt động qua proxy {.kien-thuc-nen}

Khi bạn viết:

```java
@Service
public class ReadOnlyService {

    @Transactional(readOnly = true)
    public int decreaseStockButReadOnly(Long productId) { ... }
}
```

Spring **không** sửa code của method. Thay vào đó, lúc khởi động nó tạo một **proxy**: một object bọc ngoài bean của bạn, có cùng các method. Khi code ở nơi khác (controller, test, service khác) gọi `service.decreaseStockButReadOnly(...)`, thực ra nó gọi vào proxy. Proxy làm ba việc theo thứ tự: mở transaction (nếu cần), gọi method thật của bạn, rồi commit hoặc rollback tùy kết quả.

Hai hệ quả quyết định mọi tình huống trong chương:

1. **Chỉ lời gọi đi qua proxy mới có transaction.** Nếu method thật tự gọi một method khác của chính nó bằng `this`, lời gọi đó không đi qua proxy, nên annotation trên method được gọi bị bỏ qua (mục 16.3).
2. **Transaction quyết định đời sống của persistence context.** Khi transaction mở, một persistence context được gắn vào; khi transaction kết thúc, persistence context đóng và mọi entity MANAGED thành DETACHED. Vì vậy ranh giới của `@Transactional` cũng là ranh giới của "entity còn tải lazy được hay không" (mục 16.6) và "thay đổi có được flush không" (mục 16.2).

Ba câu hỏi bạn nên tự hỏi với mọi method `@Transactional`:

- **Ai sở hữu transaction?** Method này mở transaction mới, hay tham gia transaction của nơi gọi (mục 16.4)?
- **Khi nào rollback?** Exception nào thoát ra thì rollback, exception nào thì vẫn commit (mục 16.5)?
- **Persistence context chung hay riêng?** Nếu tham gia transaction của nơi gọi thì dùng chung; nếu mở mới thì riêng (mục 16.4).

### 16.2 readOnly: sửa entity không ghi gì, save entity mới thì lỗi

`@Transactional(readOnly = true)` là cách báo "method này chỉ đọc". Spring dùng thông tin đó để tối ưu: đặt flush mode của persistence context sang `MANUAL` (không tự flush lúc commit) và báo cho connection là chỉ đọc. Hai tình huống dưới đây cho thấy hậu quả khi bạn vô tình **ghi** trong method đánh dấu chỉ đọc.

#### 16.2.1 Sửa entity MANAGED trong readOnly: không có UPDATE, không có lỗi {.cot-loi}

**Bối cảnh.** Một đồng nghiệp đặt `@Transactional(readOnly = true)` lên cả class service "cho nhanh". Sau đó có người thêm method đổi tên khách hàng vào class đó. Test chạy không lỗi, giao diện báo "đã lưu", nhưng tên không đổi.

**Khi nào xảy ra.** Method nằm trong transaction `readOnly = true`, tải entity và đổi field của entity MANAGED mà không gọi gì thêm.

**Khi nào không xảy ra.** Nếu method không đánh dấu `readOnly` (transaction đọc-ghi bình thường), thì dirty checking tự sinh UPDATE lúc commit.

**Ví dụ.**

```java
@Transactional(readOnly = true)
public int decreaseStockButReadOnly(Long productId) {
    T1Product product = em.find(T1Product.class, productId);
    product.decreaseStock(1);
    return product.getStock(); // trong bộ nhớ đã là giá trị mới
}
```

**Kết quả.** Có một câu SELECT (lúc `find`) và **không có câu UPDATE nào**. Giá trị trả về là 9 (trong bộ nhớ), nhưng database vẫn là 10.

```sql
select tp1_0.id, tp1_0.name, tp1_0.stock
from g11_t1_baseline.product tp1_0
where tp1_0.id=?
```

| Bảng product | stock (trong bộ nhớ) | stock (trong database) |
|---|---|---|
| Trước | 10 | 10 |
| Sau | **9** | 10 |

Thay đổi: chỉ giá trị trong bộ nhớ. Giữ nguyên: database. Cùng kết quả với service đổi tên khách hàng: một câu SELECT, không có UPDATE, và tên trong database không đổi.

**Vì sao.** Spring đặt flush mode `MANUAL` cho transaction chỉ đọc, nên lúc commit Hibernate **không flush**, tức không chạy dirty checking, không sinh UPDATE. Entity đã đổi trong bộ nhớ, nhưng không ai ghi nó xuống database. Điều nguy hiểm là không có lỗi nào: thay đổi bị bỏ qua lặng lẽ.

**Cách tránh.** Chỉ đặt `readOnly = true` trên method thật sự chỉ đọc. Đặt trên từng method thay vì cả class, để method ghi mặc định là đọc-ghi.

**Trong JPA thuần.** Cờ `readOnly` là của Spring. Với JPA thuần, bạn đạt hiệu quả tương tự bằng `em.setFlushMode(FlushModeType.COMMIT)` hoặc không gọi `flush`; và hậu quả giống hệt: thay đổi không được ghi.

::: {.ghi-nho}
`readOnly = true` tắt flush: sửa entity MANAGED sẽ không được ghi, và không có lỗi nào báo.
:::

::: {.tracuu codes="G0-S12,G11-S01a"}
:::

#### 16.2.2 save entity mới trong readOnly: PostgreSQL từ chối ngay lúc lấy id {.cot-loi}

**Bối cảnh.** Cũng class service đặt `readOnly = true` ở trên, nhưng lần này có method tạo bản ghi nhật ký mới bằng `repository.save(new AuditLog(...))`.

**Khi nào xảy ra.** Entity mới dùng id kiểu `SEQUENCE`, và `save` chạy trong transaction chỉ đọc.

**Khi nào không xảy ra.** Nếu id sinh theo cách không cần gọi sequence ngay lúc `persist` (ví dụ `IDENTITY`, khi đó lỗi sẽ đến muộn hơn, ở lúc INSERT), hoặc transaction không phải chỉ đọc.

**Ví dụ.**

```java
@Transactional(readOnly = true)
public void saveNewInReadOnly(String message) {
    auditLogs.save(new T1AuditLog(message));
}
```

**Kết quả.** Method ném `JpaSystemException` với thông báo "read-only transaction". Câu SQL duy nhất Hibernate gửi đi là lấy giá trị sequence, và PostgreSQL từ chối nó. Không có câu INSERT nào, và không có dòng nào được ghi.

```sql
select nextval('g11_t1_baseline.audit_log_seq')
```

::: {.khac-du-doan codes="G11-S01b"}
**Điều nhiều người nghĩ:** flush mode `MANUAL` nên `save` sẽ im lặng không INSERT, giống như khi sửa entity (mục 16.2.1), hoặc PostgreSQL báo lỗi ở câu INSERT lúc flush.

**Thực tế:** lỗi đến **sớm hơn flush**, ngay ở `select nextval(...)`. `readOnly` làm Spring đặt connection ở chế độ chỉ đọc của PostgreSQL, và PostgreSQL cấm gọi `nextval()` trong transaction chỉ đọc. Không có INSERT nào được gửi.

Đã kiểm chứng trên Hibernate 7.4.15.Final.
:::

**Vì sao.** Với `GenerationType.SEQUENCE`, Hibernate phải biết id của entity ngay khi `persist` (để đưa entity vào persistence context), nên nó chạy `nextval` lúc đó, không đợi flush. `nextval` ghi vào sequence, và PostgreSQL không cho phép ghi trong transaction chỉ đọc. Vậy là `readOnly` của Spring có hai mặt: tắt flush của Hibernate **và** khóa connection ở phía PostgreSQL.

Điểm đáng chú ý là hai tình huống trong mục này hành xử khác nhau dù cùng một nguyên nhân gốc. Sửa entity có sẵn thì im lặng mất dữ liệu; thêm entity mới thì ồn ào báo lỗi. Cả hai đều xuất phát từ việc ghi trong method đánh dấu chỉ đọc.

::: {.ghi-nho}
Trong transaction `readOnly`, sửa entity có sẵn thì im lặng mất thay đổi, còn `save` entity mới thì PostgreSQL từ chối lúc `nextval`.
:::

::: {.tracuu codes="G11-S01b"}
:::

### 16.3 Self-invocation: gọi method trong cùng bean làm mất Transactional

Mục 16.1 nói proxy chỉ chặn lời gọi từ bên ngoài. Mục này chứng minh điều đó và cho thấy hậu quả: method có `@Transactional` mà chạy như không có.

```java
@Service
public class CustomerService {

    // Có transaction: dirty checking sinh UPDATE lúc commit.
    @Transactional
    public void renameInTransaction(Long customerId, String newName) {
        Customer customer = customerRepository.findById(customerId).orElseThrow();
        customer.setName(newName);
    }

    // KHÔNG có @Transactional và gọi this.renameInTransaction(...).
    public void renameViaSelfInvocation(Long customerId, String newName) {
        this.renameInTransaction(customerId, newName); // không qua proxy
    }
}
```

#### 16.3.1 Gọi qua proxy: có transaction, có UPDATE {.mo-rong}

**Bối cảnh.** Đây là trường hợp đối chứng: gọi `renameInTransaction` từ bên ngoài bean (ví dụ từ controller hay test).

**Khi nào xảy ra.** Lời gọi đi từ một bean khác sang bean có `@Transactional`.

**Khi nào không xảy ra.** Khi lời gọi xuất phát từ bên trong chính bean đó (mục 16.3.2).

**Ví dụ.**

```java
customerService.renameInTransaction(customerId, "X"); // gọi từ bean khác
```

**Kết quả.** Một câu SELECT và một câu UPDATE. Tên khách hàng đổi từ "An" thành "X".

```sql
select c1_0.id, c1_0.email, c1_0.name
from g0_lifecycle.customer c1_0
where c1_0.id=?

update g0_lifecycle.customer
set email=?, name=?
where id=?
```

| Bảng customer | name |
|---|---|
| Trước | An |
| Sau | **X** |

Thay đổi: `name`. Giữ nguyên: `email`.

**Vì sao.** Lời gọi đi qua proxy nên transaction được mở. Customer tải về là MANAGED, `setName` đổi state, và lúc commit dirty checking sinh UPDATE.

::: {.ghi-nho}
Gọi từ bên ngoài bean thì đi qua proxy, nên `@Transactional` có hiệu lực.
:::

::: {.tracuu codes="G0-S14a"}
:::

#### 16.3.2 Gọi qua this: mất transaction, không có UPDATE {.mo-rong}

**Bối cảnh.** Một method không có `@Transactional` gọi method có `@Transactional` cùng class, ví dụ để "tái sử dụng logic". Annotation vẫn nằm đó, và đọc code ai cũng tin nó có hiệu lực.

**Khi nào xảy ra.** Lời gọi dùng `this.method()` (hoặc gọi trần `method()`) trong cùng bean.

**Khi nào không xảy ra.** Khi gọi qua một bean khác, hoặc khi method ngoài cũng có `@Transactional`.

**Ví dụ.**

```java
customerService.renameViaSelfInvocation(customerId, "X");
// bên trong: this.renameInTransaction(...), không qua proxy
```

**Kết quả.** Chỉ có một câu SELECT, **không có UPDATE**. Tên trong database vẫn là "An". Không có lỗi nào.

```sql
select c1_0.id, c1_0.email, c1_0.name
from g0_lifecycle.customer c1_0
where c1_0.id=?
```

| Bảng customer | name |
|---|---|
| Trước | An |
| Sau | An |

Thay đổi: không có. Giữ nguyên: mọi cột.

**Vì sao.** Lời gọi `this.renameInTransaction(...)` đi thẳng vào object thật, không qua proxy, nên annotation bị bỏ qua và không có transaction bao ngoài. `findById` của Spring Data tự mở một transaction ngắn riêng, đọc xong là đóng, nên customer thành DETACHED **trước** khi `setName` chạy. Thay đổi trên entity DETACHED không ai theo dõi, nên không có UPDATE. (Nếu code chạm một quan hệ LAZY chưa tải ở bước này, bạn sẽ nhận `LazyInitializationException`.)

**Cách tránh.** Tách method cần transaction sang một bean khác và tiêm vào, hoặc đặt `@Transactional` ngay trên method được gọi từ bên ngoài (method điểm vào). Đừng dựa vào annotation của method được gọi qua `this`.

::: {.ghi-nho}
Gọi qua `this` không đi qua proxy, nên `@Transactional` trên method được gọi bị bỏ qua. Không có lỗi, chỉ không có transaction.
:::

::: {.tracuu codes="G0-S14b"}
:::

#### 16.3.3 Cùng điều đó trong một service khác: kiểm tra trực tiếp có transaction hay không {.cot-loi}

**Bối cảnh.** Để chắc chắn đây không phải chuyện riêng của một test, lab dùng một service khác và hỏi thẳng Spring: "lúc này có transaction đang chạy không?"

**Khi nào xảy ra.** Giống mục 16.3.2.

**Khi nào không xảy ra.** Khi gọi qua proxy.

**Ví dụ.**

```java
@Transactional
public boolean isTransactionActive() {
    return TransactionSynchronizationManager.isActualTransactionActive();
}

public boolean isTransactionActiveViaSelfInvocation() {
    return this.isTransactionActive(); // không qua proxy
}
```

**Kết quả.** Gọi `isTransactionActive()` từ bên ngoài trả về **true**. Gọi `isTransactionActiveViaSelfInvocation()` (bên trong gọi `this.isTransactionActive()`) trả về **false**.

**Vì sao.** Cùng một method, hai kết quả trái ngược, chỉ vì khác đường gọi. Đây là phép thử đơn giản nhất để phát hiện self-invocation trong dự án của bạn: gọi thử và in ra `isActualTransactionActive()`.

::: {.ghi-nho}
Muốn biết có transaction hay không, hỏi `TransactionSynchronizationManager.isActualTransactionActive()` thay vì tin vào annotation.
:::

::: {.tracuu codes="G9-S07b"}
:::

### 16.4 Propagation: REQUIRED và REQUIRES_NEW

Khi method A (đã có transaction) gọi method B (cũng `@Transactional`), thuộc tính `propagation` của B quyết định B làm gì. Hai giá trị hay gặp:

- `REQUIRED` (mặc định): B **tham gia** transaction của A. Cùng một transaction, cùng một persistence context.
- `REQUIRES_NEW`: Spring **tạm dừng** transaction của A, mở transaction **mới** cho B trên một connection khác, với persistence context riêng. B commit hoặc rollback độc lập với A.

```java
// REQUIRED (mặc định): tham gia transaction của nơi gọi.
@Transactional(propagation = Propagation.REQUIRED)
public void writeAuditThenFail(String message) {
    auditLogs.save(new T1AuditLog(message));
    throw new IllegalStateException("inner lỗi (RuntimeException)");
}

// REQUIRES_NEW: transaction riêng, commit riêng.
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void writeAuditInNewTransaction(String message) {
    auditLogs.save(new T1AuditLog(message));
}
```

#### 16.4.1 REQUIRED: nuốt lỗi của B vẫn không cứu được transaction {.cot-loi}

**Bối cảnh.** Method A ghi một dòng nhật ký "outer", rồi gọi B. B ném `RuntimeException`. A bọc lời gọi trong `try ... catch` và đi tiếp: "lỗi đã xử lý rồi, A cứ commit". Thực tế không như vậy.

**Khi nào xảy ra.** B dùng `REQUIRED` (tham gia transaction của A) và ném `RuntimeException` ra khỏi proxy của B.

**Khi nào không xảy ra.** Nếu B dùng `REQUIRES_NEW` (mục 16.4.2): lỗi của B chỉ rollback transaction riêng của B.

**Ví dụ.**

```java
@Transactional
public void callInnerAndSwallowFailure() {
    auditLogs.save(new T1AuditLog("outer"));
    try {
        inner.writeAuditThenFail("inner-fail"); // REQUIRED, ném RuntimeException
    } catch (IllegalStateException swallowed) {
        // A nuốt lỗi, nhưng cờ rollback-only nằm ở transaction CHUNG.
    }
}
```

**Kết quả.** Lúc A kết thúc và định commit, Spring ném **`UnexpectedRollbackException`**. Cả hai dòng nhật ký ("outer" và "inner-fail") đều mất.

| Bảng audit_log | Số dòng |
|---|---|
| Trước | 0 |
| Sau | 0 |

Thay đổi: không có. Giữ nguyên: bảng rỗng, dù A đã `save` một dòng "outer" và đã nuốt lỗi.

**Vì sao.** B tham gia **cùng** transaction với A. Khi exception thoát qua proxy của B, Spring đánh dấu transaction chung là **rollback-only**. Việc A bắt exception sau đó không xóa được cờ này. Lúc A commit, Spring thấy cờ, từ chối commit và ném `UnexpectedRollbackException` để báo cho bạn biết rằng commit bạn nhờ đã không xảy ra.

**Cách tránh.** Nếu thật sự muốn lỗi của B không ảnh hưởng A, B phải chạy trong transaction riêng (`REQUIRES_NEW`), hoặc B không ném exception ra ngoài. Đừng `catch` exception của method `REQUIRED` rồi giả vờ không có chuyện gì.

::: {.ghi-nho}
B (`REQUIRED`) ném lỗi thì cả transaction chung bị đánh dấu rollback-only. A nuốt lỗi cũng vô ích, và sẽ nhận `UnexpectedRollbackException`.
:::

::: {.tracuu codes="G11-S02"}
:::

#### 16.4.2 REQUIRES_NEW: B commit dù A rollback {.cot-loi}

**Bối cảnh.** Bạn muốn ghi nhật ký kiểm toán (audit) **dù nghiệp vụ chính thất bại**. Nếu nhật ký nằm cùng transaction với nghiệp vụ thì khi nghiệp vụ rollback, nhật ký mất theo: đúng lúc bạn cần nó nhất.

**Khi nào xảy ra.** B dùng `REQUIRES_NEW`, rồi A ném lỗi sau khi B đã xong.

**Khi nào không xảy ra.** Nếu B dùng `REQUIRED`, B cùng số phận với A (mục 16.4.1).

**Ví dụ.**

```java
@Transactional
public void writeThenFailAfterRequiresNew() {
    auditLogs.save(new T1AuditLog("outer"));
    inner.writeAuditInNewTransaction("inner"); // REQUIRES_NEW, commit riêng
    throw new IllegalStateException("outer lỗi sau khi inner đã commit");
}
```

**Kết quả.** A ném `IllegalStateException` và rollback. Bảng `audit_log` còn **đúng một dòng**: dòng "inner" của B.

| Bảng audit_log | message |
|---|---|
| Trước | (rỗng) |
| Sau | **inner** |

Thay đổi: có thêm dòng "inner". Giữ nguyên: dòng "outer" không tồn tại (bị rollback cùng A).

**Vì sao.** `REQUIRES_NEW` tạm dừng transaction của A và mở transaction mới trên một connection khác. Transaction của B commit ngay khi B kết thúc, trước khi A ném lỗi. Sau đó rollback của A không thể đụng tới thứ đã được commit ở một transaction khác.

**Cách tránh.** Dùng `REQUIRES_NEW` cẩn thận: mỗi lần dùng là mượn **thêm một connection** từ pool trong khi connection đầu vẫn đang giữ. Nếu pool nhỏ và nhiều request cùng lúc lồng nhau kiểu này, bạn có thể làm cạn pool và treo cả hệ thống.

::: {.ghi-nho}
`REQUIRES_NEW` commit độc lập với nơi gọi. Hữu ích cho nhật ký kiểm toán, nhưng tốn thêm một connection.
:::

::: {.tracuu codes="G11-S03a"}
:::

#### 16.4.3 REQUIRES_NEW có persistence context riêng {.cot-loi}

**Bối cảnh.** A tải sản phẩm, rồi gọi B (`REQUIRES_NEW`) và truyền sản phẩm đó sang. Bạn nghĩ B làm việc trên cùng object. Không phải.

**Khi nào xảy ra.** B dùng `REQUIRES_NEW` và `find` cùng id với entity mà A đã tải.

**Khi nào không xảy ra.** Nếu B dùng `REQUIRED`: cùng persistence context nên `find` trả đúng object mà A đã tải.

**Ví dụ.**

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public String describeProductInNewTransaction(T1Product productFromOuter) {
    T1Product loadedHere = em.find(T1Product.class, productFromOuter.getId());
    boolean same = loadedHere == productFromOuter;
    boolean contains = em.contains(productFromOuter);
    return same + "|" + contains;
}
```

**Kết quả.** Hàm trả về `false|false`: `find` trong B trả về **một instance khác** với instance của A, và persistence context của B **không chứa** entity của A.

**Vì sao.** `REQUIRES_NEW` mở transaction mới nên cũng mở `EntityManager` và persistence context mới. Entity MANAGED trong persistence context của A chỉ là một object thường đối với B. Hệ quả thực tế: nếu B sửa sản phẩm rồi commit, object của A không biết gì và giữ giá trị cũ; hai bên có thể ghi đè nhau.

::: {.ghi-nho}
`REQUIRES_NEW` có persistence context riêng. Entity của nơi gọi là "người lạ" với nó.
:::

::: {.tracuu codes="G11-S03b"}
:::

### 16.5 Exception nào thì rollback

Spring chỉ rollback khi exception thuộc loại nó được dặn là rollback. Mặc định, chỉ `RuntimeException` và `Error` gây rollback; **checked exception thì mặc định commit**. Quy tắc này kế thừa từ EJB và làm nhiều lập trình viên bất ngờ.

#### 16.5.1 Checked exception: mặc định vẫn COMMIT {.cot-loi}

**Bối cảnh.** Method trừ tồn kho rồi phát hiện đơn không hợp lệ và ném `Exception` ("checked"). Người viết nghĩ "ném lỗi là rollback, tồn kho trở lại như cũ". Thực tế tồn kho đã bị trừ.

**Khi nào xảy ra.** Method `@Transactional` ném checked exception và không khai báo `rollbackFor`.

**Khi nào không xảy ra.** Nếu exception là `RuntimeException`, hoặc đã khai báo `rollbackFor` (mục 16.5.2).

**Ví dụ.**

```java
@Transactional
public void decreaseStockThenThrowChecked(Long productId) throws Exception {
    T1Product product = em.find(T1Product.class, productId);
    product.decreaseStock(1);
    throw new Exception("checked exception");
}
```

**Kết quả.** Exception vẫn thoát ra cho nơi gọi, **nhưng transaction đã commit**. Tồn kho từ 10 thành 9.

| Bảng product | stock |
|---|---|
| Trước | 10 |
| Sau | **9** |

Thay đổi: `stock` bị trừ. Giữ nguyên: các cột khác.

**Vì sao.** Spring xem checked exception là "tình huống nghiệp vụ có thể xử lý", không phải lỗi hệ thống, nên theo mặc định vẫn commit những gì đã làm. Dirty checking đã ghi UPDATE lúc commit.

::: {.ghi-nho}
Mặc định chỉ `RuntimeException` và `Error` gây rollback. Checked exception vẫn commit.
:::

::: {.tracuu codes="G11-S04a"}
:::

#### 16.5.2 rollbackFor = Exception.class: checked exception cũng rollback {.cot-loi}

**Bối cảnh.** Cùng thao tác như mục 16.5.1, nhưng bạn muốn mọi exception đều hủy thay đổi.

**Khi nào xảy ra.** Method khai báo `@Transactional(rollbackFor = Exception.class)`.

**Khi nào không xảy ra.** Không khai báo thì quay lại hành vi mặc định (mục 16.5.1).

**Ví dụ.**

```java
@Transactional(rollbackFor = Exception.class)
public void decreaseStockThenThrowCheckedWithRollbackFor(Long productId)
        throws Exception {
    T1Product product = em.find(T1Product.class, productId);
    product.decreaseStock(1);
    throw new Exception("checked exception");
}
```

**Kết quả.** Exception thoát ra, và transaction **rollback**. Tồn kho giữ nguyên 10.

| Bảng product | stock |
|---|---|
| Trước | 10 |
| Sau | 10 |

Thay đổi: không có. Giữ nguyên: mọi cột.

**Vì sao.** `rollbackFor` mở rộng danh sách exception gây rollback. Spring thấy exception thuộc danh sách, đánh dấu rollback, và Hibernate không flush thay đổi tồn kho.

**Cách tránh.** Quy tắc đơn giản: nếu method của bạn khai báo ném checked exception, **luôn** khai báo `rollbackFor` rõ ràng, để không phải nhớ quy tắc mặc định.

**Trong JPA thuần.** Với `EntityTransaction`, bạn tự quyết định: gọi `rollback()` trong khối `catch`. JPA thuần không có quy tắc "checked thì commit" vì không có cơ chế khai báo; mọi quyết định nằm trong code của bạn.

::: {.ghi-nho}
Method ném checked exception thì khai báo `rollbackFor` rõ ràng. Đừng dựa vào quy tắc mặc định.
:::

::: {.tracuu codes="G11-S04b"}
:::

### 16.6 Service trả entity hay DTO

Persistence context đóng khi method `@Transactional` kết thúc. Những gì service trả ra cho tầng trên phải còn dùng được **sau** thời điểm đó. Ba tình huống dưới đây chạy trên cùng mô hình `PurchaseOrder` một-nhiều `OrderItem` (collection `items` LAZY).

#### 16.6.1 Service không có transaction: chạm collection LAZY thì lỗi {.cot-loi}

**Bối cảnh.** Service quên `@Transactional`. Method tải đơn rồi đếm số dòng hàng. Chạy thử trên máy bạn, nó nổ `LazyInitializationException`.

**Khi nào xảy ra.** Method không có transaction, tải entity rồi chạm collection LAZY chưa tải.

**Khi nào không xảy ra.** Nếu method có `@Transactional` bao cả hai bước, hoặc collection đã được tải bằng `join fetch`.

**Ví dụ.**

```java
public int countItemsWithoutTransaction(Long orderId) {
    PurchaseOrder order = em.find(PurchaseOrder.class, orderId);
    // persistence context đã đóng ngay sau lời gọi find
    return order.getItems().size(); // LazyInitializationException
}
```

**Kết quả.** `LazyInitializationException`.

**Vì sao.** `em.find` không có transaction thì tự mở một persistence context tạm, tải xong là đóng ngay. Đơn trở thành DETACHED **trước** khi dòng kế tiếp chạy. Collection `items` là LAZY, chưa tải, và không còn session để tải. (Cùng cơ chế với mục 11.3.2.)

::: {.ghi-nho}
Không có transaction thì mỗi lần truy cập database là một persistence context riêng rồi đóng ngay. Entity lấy ra là DETACHED.
:::

::: {.tracuu codes="G9-S07a"}
:::

#### 16.6.2 Service có transaction nhưng trả về entity: vẫn lỗi ở tầng trên {.cot-loi}

**Bối cảnh.** Lần này service có `@Transactional` và trả về `PurchaseOrder`. Tầng trên (controller hoặc lớp chuyển sang JSON) gọi `order.getItems().size()` và vẫn nhận `LazyInitializationException`.

**Khi nào xảy ra.** Service trả entity có collection LAZY chưa tải.

**Khi nào không xảy ra.** Nếu service đã tải collection bên trong transaction (ví dụ gọi `getItems().size()`), hoặc trả DTO (mục 16.6.3).

**Ví dụ.**

```java
@Transactional(readOnly = true)
public PurchaseOrder findOrderEntity(Long orderId) {
    return em.find(PurchaseOrder.class, orderId);
}
```

**Kết quả.** Nơi gọi đọc được `getOrderNo()` ("A-001") vì cột thường đã tải sẵn. Nhưng `getItems().size()` ném `LazyInitializationException`.

**Vì sao.** Transaction kết thúc ở cuối method service, persistence context đóng, đơn thành DETACHED và `items` vẫn chưa tải. Có transaction ở service không có nghĩa tầng trên đọc được mọi thứ.

::: {.ghi-nho}
Có `@Transactional` ở service không cứu được entity trả ra ngoài: nó vẫn thành DETACHED khi method kết thúc.
:::

::: {.tracuu codes="G9-S07c"}
:::

#### 16.6.3 Service trả về DTO: dữ liệu đọc xong trong transaction {.cot-loi}

**Bối cảnh.** Cách sửa đơn giản và bền: service đọc mọi thứ cần dùng **trong** transaction, rồi sao chép sang một object thuần (DTO) và trả DTO ra.

**Khi nào xảy ra.** Service dựng DTO từ entity ngay trong method `@Transactional`.

**Khi nào không xảy ra.** Nếu DTO lại giữ tham chiếu tới entity hoặc collection LAZY (khi đó vấn đề quay lại).

**Ví dụ.**

```java
@Transactional(readOnly = true)
public OrderSummary findOrderSummary(Long orderId) {
    PurchaseOrder order = em.find(PurchaseOrder.class, orderId);
    List<String> skus = new ArrayList<>();
    for (OrderItem item : order.getItems()) { // LAZY tải ở đây, trong transaction
        skus.add(item.getSku());
    }
    return new OrderSummary(order.getOrderNo(), skus.size(), skus);
}
```

**Kết quả.** Có hai câu SELECT (đơn, rồi các dòng hàng), cả hai chạy trong transaction. DTO mang số đơn "A-001", số dòng hàng 2, và danh sách hai SKU. Nơi gọi đọc DTO thoải mái, không cần persistence context.

```sql
select po1_0.id, po1_0.order_no
from g9_s07_tx_boundary.purchase_order po1_0
where po1_0.id=?

select i1_0.order_id, i1_0.id, i1_0.sku
from g9_s07_tx_boundary.order_item i1_0
where i1_0.order_id=?
```

**Vì sao.** DTO là object thuần (ở đây là `record`), không có proxy, không cần session. Mọi dữ liệu đã được sao chép ra trước khi persistence context đóng. Bạn cũng kiểm soát chính xác số câu SQL: ở đây là hai câu, không hơn.

**Cách tránh.** Trả DTO cho tầng trên, đừng trả entity. Nếu muốn một câu SELECT thay vì hai, dùng `join fetch` hoặc DTO projection (mục 12.5).

::: {.ghi-nho}
Trả DTO thay vì entity: đọc xong mọi thứ trong transaction rồi sao chép, tầng trên không cần persistence context.
:::

::: {.tracuu codes="G9-S07d"}
:::

### 16.7 Open Session in View

Open Session in View (OSIV) là cơ chế của Spring Boot web: giữ persistence context **mở suốt cả request web**, kể cả sau khi service đã trả về. Nó "chữa" `LazyInitializationException` ở mục 16.6 bằng cách để tầng view tải lazy được. Lab không có web nên **giả lập** OSIV bằng đúng cơ chế Spring dùng: gắn một `EntityManager` vào thread trước khi gọi service, gỡ và đóng sau đó. Dự án của lab tắt OSIV bằng `spring.jpa.open-in-view: false`.

#### 16.7.1 Không có OSIV: chạm collection sau khi service trả về thì lỗi {.mo-rong}

**Bối cảnh.** Đây là hành vi mặc định khi tắt OSIV, và là đối chứng cho mục 16.7.2.

**Khi nào xảy ra.** Service `@Transactional` trả entity, tầng trên chạm collection LAZY chưa tải, và không có OSIV.

**Khi nào không xảy ra.** Khi OSIV bật (mục 16.7.2), hoặc khi service đã tải sẵn collection.

**Ví dụ.**

```java
T1PurchaseOrder order = orderReadService.findOrder(1L); // transaction đã đóng
order.getItems().size();       // LazyInitializationException
```

**Kết quả.** `LazyInitializationException`.

**Vì sao.** Cùng cơ chế với mục 16.6.2: transaction và persistence context đóng khi service trả về, đơn thành DETACHED, `items` LAZY chưa tải.

::: {.ghi-nho}
Tắt OSIV thì `LazyInitializationException` hiện ra sớm, ngay lúc phát triển. Đó là điều bạn muốn.
:::

::: {.tracuu codes="G11-S12a"}
:::

#### 16.7.2 Có OSIV: lazy loading chạy được, nhưng SELECT chạy ngoài transaction {.mo-rong}

**Bối cảnh.** Bạn bật OSIV (hoặc quên rằng Spring Boot bật nó mặc định), lỗi `LazyInitializationException` biến mất, và mọi thứ "chạy được".

**Khi nào xảy ra.** Persistence context được giữ mở suốt request, và tầng trên chạm collection LAZY sau khi service đã trả về.

**Khi nào không xảy ra.** Khi OSIV tắt (mục 16.7.1).

**Ví dụ.**

```java
EntityManager requestScoped = emf.createEntityManager();
TransactionSynchronizationManager.bindResource(
    emf, new EntityManagerHolder(requestScoped)); // "filter" OSIV bắt đầu
try {
    T1PurchaseOrder order = orderReadService.findOrder(1L);
    boolean active =
        TransactionSynchronizationManager.isActualTransactionActive();
    int itemCount = order.getItems().size(); // lazy load NGOÀI service
} finally {
    TransactionSynchronizationManager.unbindResource(emf);
    requestScoped.close();
}
```

**Kết quả.** Không còn lỗi: `getItems().size()` trả 2 sau khi chạy thêm **một** câu SELECT. Nhưng lúc câu SELECT đó chạy, **không có transaction nào đang hoạt động** (`active` là false).

```sql
select i1_0.order_id, i1_0.id, i1_0.sku
from g11_t1_baseline.order_item i1_0
where i1_0.order_id=?
```

**Vì sao.** OSIV giữ `EntityManager` mở, nên đơn vẫn MANAGED và collection LAZY còn tải được. Nhưng transaction thì đã kết thúc cùng service, nên câu SELECT chạy ở chế độ auto-commit trên một connection mượn tạm.

Cái giá thật sự là che giấu vấn đề: trong view hoặc lớp serialize JSON, mỗi đơn chạm collection lại thêm một câu SELECT (đúng kiểu N+1 ở Chương 11), mà không ai thấy ở ranh giới service. Connection còn bị giữ lâu hơn cần thiết vì persistence context sống suốt request. Vì vậy dự án của lab tắt OSIV.

**Cách tránh.** Tắt `spring.jpa.open-in-view`. Tải đủ dữ liệu trong service (`join fetch`, `@EntityGraph`) hoặc trả DTO (mục 16.6.3).

::: {.ghi-nho}
OSIV che `LazyInitializationException` bằng cách cho lazy loading chạy ngoài transaction. Lỗi biến mất nhưng N+1 và connection giữ lâu thì ở lại.
:::

::: {.tracuu codes="G11-S12b"}
:::
