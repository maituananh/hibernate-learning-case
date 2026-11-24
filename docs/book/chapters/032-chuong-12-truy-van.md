## Chương 12. Viết truy vấn: JPQL, native SQL và phân trang

Chương 11 cho thấy mapping quyết định **khi nào** quan hệ được tải. Chương này nói về phần còn lại: **cách bạn viết câu truy vấn** quyết định SQL sinh ra, trạng thái của collection, và những gì nằm trong persistence context. Hai câu truy vấn trông gần giống nhau, chỉ khác một từ khóa, có thể cho dữ liệu khác hẳn.

Chương này dùng một mô hình nhỏ: `Customer` có nhiều `PurchaseOrder`, quan hệ `LAZY` ở mọi nơi. Mỗi đơn có trạng thái (`PAID` hoặc `NEW`). Dữ liệu cố định, ghi bằng JDBC trước mỗi thí nghiệm:

- 3 customer, mỗi customer 2 đơn: đơn thứ nhất `PAID`, đơn thứ hai `NEW`. Tổng 6 đơn.
- Các thí nghiệm phân trang dùng trang đầu cỡ 2 customer. Con số cần canh là tổng số customer: đúng là **3**, và nếu một câu đếm sai thì sẽ ra **6**.

![Sơ đồ bảng customer và purchase_order của mô hình dùng trong chương](img/g10-q1.png){width=70%}

Trong chương này:

- `join` để lọc, `join fetch` để nạp: khác nhau thế nào và bẫy khi trộn hai thứ (mục 12.1).
- Native query: khi nào entity vẫn là MANAGED, khi nào không có entity nào (mục 12.2).
- `Page` kèm `join fetch` và câu count (mục 12.3).
- Criteria và `Specification` cho bộ lọc động (mục 12.4).
- DTO projection cho màn hình chỉ đọc (mục 12.5).

**Trong JPA thuần.** JPQL, `join fetch`, Criteria API, native query và constructor expression (`select new`) đều là JPA chuẩn. Những thứ riêng của Spring Data: `Page`, `Pageable`, `Specification`, `@Query`, interface projection. Mỗi mục dùng chúng đều có đoạn **Trong JPA thuần.** nêu cách viết tương đương.

### 12.1 join và join fetch

Trong JPQL, cả `join` lẫn `join fetch` đều nối bảng cha với bảng con. Nhưng chúng làm hai việc khác nhau:

- `join`: nối bảng **để lọc hoặc để dùng điều kiện** trong câu truy vấn. Không đụng tới collection của entity.
- `join fetch`: nối bảng **để nạp dữ liệu vào** collection của entity.

Quy tắc ghi nhớ: **`join` để lọc, `join fetch` để nạp.** Ba tình huống dưới đây xếp theo thứ tự: khác biệt cơ bản, bẫy khi trộn hai thứ, và cách viết đúng.

#### 12.1.1 join thường không nạp collection {.cot-loi}

**Bối cảnh.** Bạn cần danh sách khách có đơn hàng, viết `select c from Customer c join c.orders o` và mong rằng sau đó `customer.getOrders()` đã có dữ liệu. Thực tế bạn thấy thêm một câu SELECT khi chạm vào collection.

**Khi nào xảy ra.** Bạn dùng `join` (không có `fetch`) trên một collection, và collection khai báo `LAZY`.

**Khi nào không xảy ra.** Nếu bạn thêm từ khóa `fetch` (mục 12.1.2), hoặc nếu collection là `EAGER` (khi đó Hibernate vẫn tải thêm, nhưng bằng cách khác, xem mục 11.7).

**Ví dụ.**

```java
inTx(() -> {
    List<Q1Customer> customers = em.createQuery(
            "select c from G10Q1_Customer c join c.orders o order by c.id",
            Q1Customer.class).getResultList();
    Q1Customer customerA = customers.get(0);

    Hibernate.isInitialized(customerA.getOrders()); // false
    customerA.getOrders().size();                   // thêm 1 câu SELECT
});
```

**Kết quả.** Câu SELECT đầu có `join` nhưng danh sách cột **chỉ có cột của customer**, không có cột nào của order. `orders` chưa khởi tạo. Lần đầu gọi `getOrders().size()` có thêm 1 câu SELECT `purchase_order`. Tổng 2 câu. Danh sách trả về có **3** customer, dù JOIN sinh ra 6 dòng.

```sql
select qc1_0.id, qc1_0.name
from g10_q1_baseline.customer qc1_0
join g10_q1_baseline.purchase_order o1_0 on qc1_0.id=o1_0.customer_id
order by qc1_0.id

-- chạm vào collection: câu thứ hai
select o1_0.customer_id, o1_0.id, o1_0.order_no, o1_0.status
from g10_q1_baseline.purchase_order o1_0
where o1_0.customer_id=?
```

**Vì sao.** Alias `o` của `join` chỉ có tác dụng **bên trong câu truy vấn** (trong `where`, `on`, `order by`). Hibernate không có lý do nạp dữ liệu từ đó vào entity, nên không chọn các cột của order. Việc kết quả có 3 customer chứ không phải 6 là do Hibernate 7 loại trùng cả entity gốc của `join` thường.

::: {.khac-du-doan codes="G10-S01a"}
**Điều nhiều người nghĩ:** `join` thường trả mỗi customer một lần **cho mỗi đơn** (6 phần tử cho 3 customer), và phải thêm `distinct` mới gọn lại. Đó là kinh nghiệm của Hibernate 5.

**Thực tế (Hibernate 7.4):** kết quả đã có 3 customer, không cần `distinct`.
:::

::: {.ghi-nho}
`join` chỉ nối trong câu truy vấn, không nạp collection. Chạm vào collection sau đó vẫn tốn thêm một câu SELECT.
:::

::: {.tracuu codes="G10-S01a"}
:::

#### 12.1.2 join fetch nạp collection ngay trong câu truy vấn {.cot-loi}

**Bối cảnh.** Bạn cần danh sách khách **kèm** đơn hàng để hiển thị ngay, không muốn thêm câu SELECT nào khi duyệt.

**Khi nào xảy ra.** Bạn thêm từ khóa `fetch`: `join fetch c.orders`.

**Khi nào không xảy ra.** Nếu bạn cần lọc theo điều kiện trên đơn mà vẫn muốn đủ đơn của khách, đừng viết `where` lên alias của `join fetch` (mục 12.1.3).

**Ví dụ.**

```java
inTx(() -> {
    List<Q1Customer> customers = em.createQuery(
            "select c from G10Q1_Customer c join fetch c.orders "
            + "order by c.id", Q1Customer.class).getResultList();
    Q1Customer customerA = customers.get(0);

    Hibernate.isInitialized(customerA.getOrders()); // true
    customerA.getOrders().size();                   // không SELECT thêm
});
```

**Kết quả.** Đúng 1 câu SELECT có `join`, và lần này danh sách cột **có thêm cột của order** (`order_no`, `status`). `orders` đã khởi tạo, có đủ 2 phần tử, đọc không tốn thêm câu SQL nào.

```sql
select qc1_0.id, qc1_0.name, o1_0.customer_id, o1_0.id, o1_0.order_no,
    o1_0.status
from g10_q1_baseline.customer qc1_0
join g10_q1_baseline.purchase_order o1_0 on qc1_0.id=o1_0.customer_id
order by qc1_0.id
```

**Vì sao.** Từ khóa `fetch` nói với Hibernate: "lấy cả cột của bảng con và dùng chúng để điền vào collection". Giá phải trả: mỗi dòng kết quả mang cả cột customer lẫn cột order, nên thông tin customer bị lặp theo số đơn. Và collection chứa **đúng những đơn mà câu truy vấn chọn ra**, điểm này dẫn tới bẫy ở mục tiếp theo.

::: {.ghi-nho}
`join fetch` nạp dữ liệu vào collection ngay trong một câu SELECT. Collection chứa đúng những dòng mà câu truy vấn trả về.
:::

::: {.tracuu codes="G10-S01b"}
:::

#### 12.1.3 Bẫy: where trên alias của join fetch làm collection thiếu dữ liệu {.cot-loi}

**Bối cảnh.** Báo cáo cần "khách có đơn đã thanh toán, kèm đơn hàng của họ". Bạn viết `join fetch c.orders o where o.status = 'PAID'`. Danh sách khách đúng, nhưng sau đó có chỗ trong code duyệt `customer.getOrders()` và tin rằng khách này chỉ có một đơn.

**Khi nào xảy ra.** Bạn đặt điều kiện `where` lên alias của một `join fetch` collection.

**Khi nào không xảy ra.** Nếu bạn dùng `join` thường để lọc (mục 12.1.4), hoặc điều kiện chỉ nằm trên bảng cha.

**Ví dụ.**

```java
inTx(() -> {
    Q1Customer customerA = em.createQuery(
            "select c from G10Q1_Customer c join fetch c.orders o "
            + "where o.status = 'PAID' order by c.id", Q1Customer.class)
        .getResultList().get(0);
    customerA.getOrders().size(); // 1, dù database có 2

    Q1Customer sameCustomer = em.find(Q1Customer.class, customerA.getId());
    sameCustomer.getOrders().size(); // vẫn 1: không tải lại
});
```

**Kết quả.** Đúng 1 câu SELECT. Collection `orders` của customer đầu tiên chỉ có **1 phần tử** (đơn `PAID`), trong khi database có 2 đơn của khách này. Đọc lại bằng `em.find` trong cùng persistence context trả về **chính instance** đó, không có câu SQL nào, và vẫn là 1 phần tử.

```sql
select qc1_0.id, qc1_0.name, o1_0.customer_id, o1_0.id, o1_0.order_no,
    o1_0.status
from g10_q1_baseline.customer qc1_0
join g10_q1_baseline.purchase_order o1_0 on qc1_0.id=o1_0.customer_id
where o1_0.status='PAID'
order by qc1_0.id
```

| Nơi đọc | Số đơn của khách đầu tiên |
|---|---|
| Database (đếm bằng JDBC) | 2 |
| Collection trong bộ nhớ sau câu truy vấn trên | **1** |

**Vì sao.** Khi `join fetch` kèm `where`, các dòng bị lọc **không bao giờ về đến Hibernate**, và Hibernate điền collection chỉ bằng những dòng nhận được. Collection được đánh dấu "đã khởi tạo" nên Hibernate coi nó là đầy đủ và không tải lại. Persistence context giữ nguyên bản thiếu, và mọi đoạn code phía sau trong cùng transaction tin vào nó.

Đây là lỗi dữ liệu **âm thầm**: không có exception, không có cảnh báo. Nếu code phía sau sửa collection này (xóa phần tử, hoặc có `orphanRemoval` ở Chương 6) thì hậu quả còn nặng hơn.

**Cách tránh.** Dùng `join` thường để lọc (mục 12.1.4), hoặc dùng `exists` trong `where`.

::: {.ghi-nho}
Không đặt `where` lên alias của `join fetch` collection. Collection sẽ thiếu dữ liệu và persistence context giữ nguyên bản thiếu đó.
:::

::: {.tracuu codes="G10-S02"}
:::

#### 12.1.4 Cách viết đúng: join để lọc, collection tải riêng {.cot-loi}

**Bối cảnh.** Vẫn là yêu cầu của mục 12.1.3: "khách có ít nhất một đơn đã thanh toán", nhưng bạn cần **đủ** đơn của khách đó.

**Khi nào xảy ra.** Bạn lọc bằng `join` thường, rồi để collection tải riêng khi cần.

**Khi nào không xảy ra.** Nếu bạn muốn một câu SELECT duy nhất kèm đủ dữ liệu thì cách này tốn thêm một câu: đổi lại là dữ liệu đúng.

**Ví dụ.**

```java
inTx(() -> {
    Q1Customer customerA = em.createQuery(
            "select c from G10Q1_Customer c join c.orders o "
            + "where o.status = 'PAID' order by c.id", Q1Customer.class)
        .getResultList().get(0);
    customerA.getOrders().size(); // tải riêng: đủ 2 đơn
});
```

**Kết quả.** Tổng 2 câu SELECT. Câu đầu lọc customer bằng `join` và `where` (không chọn cột của order). Câu thứ hai tải toàn bộ đơn của customer đầu tiên. Collection có **đủ 2 phần tử**, khớp với database.

```sql
select qc1_0.id, qc1_0.name
from g10_q1_baseline.customer qc1_0
join g10_q1_baseline.purchase_order o1_0 on qc1_0.id=o1_0.customer_id
where o1_0.status='PAID'
order by qc1_0.id

select o1_0.customer_id, o1_0.id, o1_0.order_no, o1_0.status
from g10_q1_baseline.purchase_order o1_0
where o1_0.customer_id=?
```

**Vì sao.** `join` thường không đụng tới collection, nên điều kiện `where` chỉ quyết định **customer nào** được chọn. Collection chưa khởi tạo, và lần chạm đầu tiên tải đủ mọi đơn, không có điều kiện nào áp lên. Tách "chọn ai" khỏi "tải gì" là cách an toàn.

::: {.ghi-nho}
`join` để lọc, `join fetch` để nạp. Muốn vừa lọc theo con vừa có đủ con, lọc bằng `join` rồi tải collection riêng.
:::

::: {.tracuu codes="G10-S03"}
:::

### 12.2 Native query có kiểu và không kiểu entity

Đôi khi JPQL không biểu diễn được câu bạn cần (hàm đặc thù của database, truy vấn đệ quy, tối ưu bằng hint). Khi đó bạn viết native SQL. Câu hỏi: Hibernate xử lý kết quả của native query thế nào, và nó có "biết" những gì đã xảy ra không?

Câu trả lời tách theo một điều kiện duy nhất: **bạn có chỉ định kiểu entity cho kết quả hay không.**

#### 12.2.1 Native query có kiểu entity trả entity MANAGED, cùng instance {.cot-loi}

**Bối cảnh.** Bạn viết `select * from customer where id = 1` bằng native query và truyền `Customer.class`. Customer này đã được `find` trước đó trong cùng transaction. Bạn muốn biết có ra instance thứ hai hay không.

**Khi nào xảy ra.** Bạn gọi `createNativeQuery(sql, Entity.class)`.

**Khi nào không xảy ra.** Nếu bạn không truyền kiểu entity (mục 12.2.3), kết quả là mảng giá trị, không phải entity.

**Ví dụ.**

```java
inTx(() -> {
    Q1Customer loadedByFind = em.find(Q1Customer.class, 1L);
    List<Q1Customer> fromNative = em.createNativeQuery(
        "select * from g10_q1_baseline.customer where id = 1",
        Q1Customer.class).getResultList();
    // fromNative.get(0) == loadedByFind  (cùng instance)
});
```

**Kết quả.** Entity trả về là MANAGED và là **chính instance** của `find` (so sánh bằng `==` là true). Có 2 câu SELECT customer: một từ `find`, một từ native query, vì native query luôn chạy SQL.

```sql
select qc1_0.id, qc1_0.name
from g10_q1_baseline.customer qc1_0
where qc1_0.id=?

select *
from g10_q1_baseline.customer
where id = 1
```

**Vì sao.** Hibernate vẫn đọc từng dòng kết quả, thấy id đã có trong persistence context và trả instance cũ. Nguyên tắc "một dòng database là một instance trong một persistence context" vẫn đúng, dù đi bằng native.

::: {.ghi-nho}
Native query có kiểu entity vẫn đi qua persistence context: entity MANAGED, cùng instance với entity đã có. Nhưng SQL luôn được chạy.
:::

::: {.tracuu codes="G10-S04a"}
:::

#### 12.2.2 Entity từ native query vẫn được dirty checking {.cot-loi}

**Bối cảnh.** Bạn lấy customer bằng native query có kiểu, đổi tên rồi commit mà không gọi `save` hay `persist`. Bạn không chắc Hibernate có ghi thay đổi này không, vì entity "đến từ native".

**Khi nào xảy ra.** Entity do native query có kiểu entity trả về, và bạn sửa nó khi nó còn MANAGED.

**Khi nào không xảy ra.** Với kết quả không phải entity (mục 12.2.3), không có gì để dirty checking.

**Ví dụ.**

```java
inTx(() -> {
    List<Q1Customer> fromNative = em.createNativeQuery(
        "select * from g10_q1_baseline.customer where id = 1",
        Q1Customer.class).getResultList();
    fromNative.get(0).rename("Customer-1-renamed"); // chỉ sửa field
    // [FLUSH] lúc commit: dirty checking thấy name đổi
});
```

**Kết quả.** Đúng 1 câu UPDATE `customer` lúc commit, và database có tên mới.

| Bảng customer | name |
|---|---|
| Trước | Customer-1 |
| Sau | **Customer-1-renamed** |

Thay đổi: cột `name` của dòng id 1. Giữ nguyên: hai customer còn lại.

```sql
update g10_q1_baseline.customer
set name=?
where id=?
```

**Vì sao.** Entity đã được đưa vào persistence context ở trạng thái MANAGED, nên Hibernate chụp snapshot ban đầu và so sánh lúc flush, giống mọi entity khác. Nguồn gốc của entity (JPQL, native, `find`) không quan trọng, quan trọng là nó có đi qua persistence context hay không.

::: {.ghi-nho}
Entity lấy bằng native query có kiểu entity là MANAGED thật: sửa field là có UPDATE lúc flush.
:::

::: {.tracuu codes="G10-S04b"}
:::

#### 12.2.3 Native query không kiểu không đưa gì vào persistence context {.cot-loi}

**Bối cảnh.** Báo cáo chọn vài cột bằng native query, không truyền kiểu entity. Bạn muốn biết kết quả là gì và có sửa dữ liệu qua đó được không.

**Khi nào xảy ra.** Bạn gọi `createNativeQuery(sql)` không có kiểu, kết quả là `Object[]` (mỗi dòng một mảng giá trị).

**Khi nào không xảy ra.** Nếu bạn truyền kiểu entity (mục 12.2.1) thì có entity MANAGED.

**Ví dụ.**

```java
inTx(() -> {
    List<Object[]> rows = em.createNativeQuery(
        "select id, name from g10_q1_baseline.customer order by id")
        .getResultList();
    // rows: 3 mảng giá trị, persistence context không có entity nào
});
```

**Kết quả.** Đúng 1 câu SELECT, đọc đủ 3 dòng. Persistence context có **0 entity**: kết quả chỉ là mảng giá trị.

```sql
select id, name
from g10_q1_baseline.customer
order by id
```

**Vì sao.** Không có kiểu entity thì Hibernate không có gì để dựng entity, chỉ chuyển giá trị cột thành object. Không có vòng đời, không có dirty checking, không có lazy loading. Muốn sửa dữ liệu từ kết quả này phải tự viết UPDATE.

::: {.ghi-nho}
Native query không kiểu cho `Object[]`: không entity, không dirty checking. Chỉ dùng cho đọc.
:::

::: {.tracuu codes="G10-S05"}
:::

### 12.3 Phân trang với Page và count query

Spring Data phân trang bằng `Page<T>`: một câu SELECT lấy dữ liệu của trang, và một câu **count** để biết tổng số phần tử (từ đó tính tổng số trang). Khi truy vấn có `join fetch` collection, câu count do Spring Data tự sinh trở thành nguồn lỗi âm thầm.

#### 12.3.1 Count tự sinh đếm sai: tổng 6 thay vì 3 {.cot-loi}

**Bối cảnh.** Màn hình danh sách khách phân trang, mỗi trang 2 khách, kèm đơn hàng. Bạn khai báo `Page<Customer>` trên một `@Query` có `join fetch c.orders`. Dữ liệu trang đúng, nhưng thanh phân trang hiện số trang nhiều gấp đôi thực tế.

**Khi nào xảy ra.** `@Query` có `join fetch` một collection, trả `Page`, và bạn **không** khai báo `countQuery`.

**Khi nào không xảy ra.** Nếu bạn khai báo `countQuery` riêng (mục 12.3.2), hoặc dùng hai truy vấn như ở mục 11.6.2.

**Ví dụ.**

```java
public interface Q1CustomerRepository extends JpaRepository<Q1Customer, Long> {

    @Query("select c from G10Q1_Customer c join fetch c.orders")
    Page<Q1Customer> findPageWithOrdersAutoCount(Pageable pageable);
}

// Gọi: trang đầu, cỡ 2
Page<Q1Customer> page = customerRepository
    .findPageWithOrdersAutoCount(PageRequest.of(0, 2));
```

**Kết quả.** Chạy được, **không báo lỗi**. Có 2 câu SELECT: câu dữ liệu (cắt trang ở database, trang có đúng 2 customer) và câu count. Nhưng `getTotalElements()` trả **6**, trong khi database chỉ có **3** customer.

```sql
select count(*)
from g10_q1_baseline.customer qc1_0
join g10_q1_baseline.purchase_order o1_0 on qc1_0.id=o1_0.customer_id
```

| Con số | Giá trị |
|---|---|
| Customer thật trong database | 3 |
| `getTotalElements()` do count tự sinh | **6** |

**Vì sao.** Spring Data biến câu truy vấn gốc thành câu count bằng cách thay danh sách chọn bằng `count(*)`, **giữ nguyên phần `join`**. Sau join, mỗi đơn là một dòng (3 customer nhân 2 đơn là 6 dòng), và `count(*)` đếm dòng chứ không đếm customer. Dữ liệu của trang vẫn đúng (xem mục 11.6.1), nên chỉ có tổng số trang sai: giao diện sẽ hiện nhiều trang hơn thực tế.

::: {.khac-du-doan codes="G10-S08a"}
**Điều nhiều người nghĩ:** count tự sinh trên truy vấn có `join fetch` collection hoặc ném lỗi, hoặc cho kết quả đúng.

**Thực tế:** không lỗi nhưng **sai âm thầm**: tổng 6 thay vì 3, vì câu count đếm số dòng sau join. Chỉ test hoặc người dùng tinh mắt mới phát hiện.
:::

::: {.ghi-nho}
`Page` kèm `join fetch` collection mà không có `countQuery` thì tổng bị đếm theo số dòng sau join, không theo số entity, và không có lỗi nào báo.
:::

::: {.tracuu codes="G10-S08a"}
:::

#### 12.3.2 Khai báo countQuery riêng {.cot-loi}

**Bối cảnh.** Sau khi phát hiện tổng sai ở mục 12.3.1, bạn cần sửa mà vẫn giữ một truy vấn dữ liệu có `join fetch`.

**Khi nào xảy ra.** Bạn khai báo `countQuery` trong `@Query`: câu count không có fetch, không có join.

**Khi nào không xảy ra.** Nếu điều kiện lọc của câu dữ liệu cần xuất hiện trong câu count thì bạn phải lặp lại điều kiện đó trong `countQuery`.

**Ví dụ.**

```java
@Query(value = "select c from G10Q1_Customer c join fetch c.orders",
       countQuery = "select count(c) from G10Q1_Customer c")
Page<Q1Customer> findPageWithOrdersExplicitCount(Pageable pageable);
```

**Kết quả.** Có 2 câu SELECT như trước, nhưng câu count giờ **kết thúc ở bảng customer, không còn join**. `getTotalElements()` trả đúng **3**, và trang có 2 customer.

```sql
select count(qc1_0.id)
from g10_q1_baseline.customer qc1_0
```

**Vì sao.** Bạn tự quyết định cái gì được đếm: đếm customer, không đếm dòng sau join. Câu count cũng rẻ hơn vì không phải nối bảng. Quy tắc: truy vấn `Page` có `join fetch` collection thì luôn khai báo `countQuery`.

**Trong JPA thuần.** `Page` và `countQuery` là của Spring Data, JPA không có. Trong JPA thuần, bạn tự chạy hai truy vấn: một `createQuery(...).setFirstResult(...).setMaxResults(...)` cho dữ liệu, một `select count(c) ...` cho tổng. Cách đó cũng đúng ý hệt như `countQuery`.

::: {.ghi-nho}
Truy vấn `Page` có `join fetch` collection thì luôn khai báo `countQuery` đếm entity gốc, không có join.
:::

::: {.tracuu codes="G10-S08b"}
:::

### 12.4 Criteria và Specification cho bộ lọc động

Màn hình tìm kiếm có nhiều ô lọc tùy chọn, người dùng nhập ô nào thì câu truy vấn có điều kiện đó. Nối chuỗi JPQL dễ lỗi và dễ bị tấn công injection. Criteria API dựng truy vấn bằng code, và Spring Data đóng gói điều kiện thành `Specification`: mỗi `Specification` là một mảnh điều kiện có thể ghép lại.

Để dùng, repository kế thừa thêm `JpaSpecificationExecutor<T>`. Phần này dùng một `Specification` đơn giản: lọc customer theo tiền tố tên.

#### 12.4.1 Specification đổi SQL theo điều kiện {.cot-loi}

**Bối cảnh.** Tìm khách theo tên: ô tìm kiếm có thể có hoặc không. Bạn muốn xem SQL thay đổi thế nào, và `Page` có còn đúng không.

**Khi nào xảy ra.** Bạn gọi `findAll(spec, pageable)` với một `Specification` không có `fetch`.

**Khi nào không xảy ra.** Nếu `Specification` gọi `root.fetch(...)` thì xem mục 12.4.2.

**Ví dụ.**

```java
Specification<Q1Customer> nameStartsWithCustomer =
    (root, query, cb) -> cb.like(root.get("name"), "Customer-%");
Specification<Q1Customer> matchesNothing =
    (root, query, cb) -> cb.like(root.get("name"), "Nobody-%");

Page<Q1Customer> matching = customerRepository
    .findAll(nameStartsWithCustomer, PageRequest.of(0, 2));
Page<Q1Customer> none = customerRepository
    .findAll(matchesNothing, PageRequest.of(0, 2));
```

**Kết quả.** Tổng 3 câu SELECT cho hai lần gọi:

- Điều kiện khớp cả 3 customer: 1 câu dữ liệu có `where name like ?` và `fetch first`, và 1 câu count có cùng điều kiện. Tổng số đúng 3.
- Điều kiện không khớp dòng nào: chỉ 1 câu dữ liệu. Trang rỗng nên Spring Data **bỏ qua câu count** và tổng là 0.

```sql
select qc1_0.id, qc1_0.name
from g10_q1_baseline.customer qc1_0
where qc1_0.name like ? escape ''
offset ? rows
fetch first ? rows only

select count(qc1_0.id)
from g10_q1_baseline.customer qc1_0
where qc1_0.name like ? escape ''
```

**Vì sao.** `Specification` được áp dụng cho cả câu dữ liệu lẫn câu count, nên điều kiện nhất quán giữa hai câu. Khi không có điều kiện, `where` biến mất khỏi SQL. Spring Data còn có tối ưu: trang đầu rỗng thì chắc chắn tổng là 0, khỏi cần hỏi database.

**Trong JPA thuần.** `Specification` là của Spring Data. JPA thuần có Criteria API (`CriteriaBuilder`, `CriteriaQuery`): bạn dựng `CriteriaQuery<Customer>` và một `CriteriaQuery<Long>` cho count, rồi tự lặp lại điều kiện ở cả hai. Lambda `(root, query, cb)` ở trên chính là ba đối tượng của Criteria API.

::: {.ghi-nho}
`Specification` ghép điều kiện động, áp dụng cho cả câu dữ liệu lẫn câu count. Không có điều kiện thì không có `where`.
:::

::: {.tracuu codes="G10-S09a"}
:::

#### 12.4.2 root.fetch trong Specification làm count query ném lỗi {.cot-loi}

**Bối cảnh.** Bạn muốn `Specification` tải kèm đơn hàng, nên thêm `root.fetch("orders")`. Sau đó phân trang bằng `Page`, và ứng dụng ném `InvalidDataAccessApiUsageException`.

**Khi nào xảy ra.** `Specification` gọi `root.fetch(...)` và bạn gọi `findAll(spec, pageable)` trả `Page`.

**Khi nào không xảy ra.** Nếu bạn dùng `findAll(spec)` không phân trang (không có câu count), hoặc đặt `fetch` có điều kiện (mục 12.4.3).

**Ví dụ.**

```java
Specification<Q1Customer> fetchOrders = (root, query, cb) -> {
    root.fetch("orders"); // đúng với câu dữ liệu, sai với câu count
    return cb.like(root.get("name"), "Customer-%");
};

customerRepository.findAll(fetchOrders, PageRequest.of(0, 2)); // lỗi
```

**Kết quả.** Câu dữ liệu chạy được. Nhưng câu count ném `SemanticException` với nội dung kiểu "join fetching, but the owner of the fetched association was not present in the select list". Lỗi nổi lên dưới dạng `InvalidDataAccessApiUsageException`, và `SemanticException` là root cause.

**Vì sao.** Spring Data dùng **cùng một** `Specification` cho cả câu dữ liệu lẫn câu count. Câu count chọn `count(...)` chứ không chọn entity `Customer`, nên không có "chủ" nào để gắn `fetch` vào. Hibernate từ chối dựng câu SQL.

Điểm khác với mục 12.3.1: ở đó sai âm thầm, ở đây lỗi to. Lỗi to thật ra dễ chịu hơn, vì bạn phát hiện ngay lúc chạy thử.

::: {.khac-du-doan codes="G10-S09b"}
**Điều nhiều người nghĩ:** `root.fetch` trong `Specification` cho kết quả giống mục 12.3.1 (total sai).

**Thực tế:** câu count ném `SemanticException` ngay khi chạy, vì câu count không chọn entity gốc. Đây là lỗi to chứ không phải sai âm thầm.
:::

::: {.ghi-nho}
`root.fetch` trong `Specification` dùng với `Page` thì câu count ném lỗi, vì câu count không chọn entity gốc.
:::

::: {.tracuu codes="G10-S09b"}
:::

#### 12.4.3 Chỉ fetch khi không phải câu count {.cot-loi}

**Bối cảnh.** Bạn vẫn muốn fetch trong `Specification` và vẫn cần `Page`.

**Khi nào xảy ra.** Bạn bọc `root.fetch` trong điều kiện kiểm tra kiểu kết quả của truy vấn.

**Khi nào không xảy ra.** Nếu `Specification` được dùng ở chỗ khác (ví dụ truy vấn `exists`, `delete`) thì kiểu kết quả có thể khác `Long`, hãy kiểm tra thêm.

**Ví dụ.**

```java
Specification<Q1Customer> fetchOrdersExceptInCount = (root, query, cb) -> {
    if (query.getResultType() != Long.class) { // Long.class: câu count
        root.fetch("orders");
    }
    return cb.like(root.get("name"), "Customer-%");
};

Page<Q1Customer> page = customerRepository
    .findAll(fetchOrdersExceptInCount, PageRequest.of(0, 2));
```

**Kết quả.** Chạy được, 2 câu SELECT. Câu dữ liệu có cột của order (đã fetch). Câu count **không có fetch và không có join**, nên `getTotalElements()` đúng bằng số customer thật (3), và trang có 2 customer.

```sql
select count(qc1_0.id)
from g10_q1_baseline.customer qc1_0
where qc1_0.name like ? escape ''
```

**Vì sao.** Câu count có kiểu kết quả là `Long`, câu dữ liệu có kiểu `Customer`. Kiểm tra `getResultType()` cho phép cùng một `Specification` hành xử khác nhau cho hai câu, nên fetch chỉ có ở nơi nó có nghĩa.

::: {.ghi-nho}
Trong `Specification` dùng với `Page`, chỉ gọi `root.fetch` khi `query.getResultType() != Long.class`.
:::

::: {.tracuu codes="G10-S09c"}
:::

### 12.5 DTO projection

Mọi thí nghiệm ở trên trả về **entity**. Entity đắt: nó mang theo vòng đời, dirty checking, lazy loading, và chiếm chỗ trong persistence context. Với màn hình **chỉ đọc**, bạn thường chỉ cần vài cột. DTO projection chọn đúng cột cần và không tạo entity nào.

#### 12.5.1 DTO của Spring Data: record và interface projection {.mo-rong}

**Bối cảnh.** Màn hình danh sách đơn chỉ hiển thị mã đơn và tên khách. Tải cả hai entity rồi bỏ đi phần lớn là phí. Bạn thử chỉ chọn đúng hai cột.

**Kết quả.** Mỗi kiểu projection (record qua `select new`, và interface projection) chạy **đúng 1 câu SELECT** chỉ gồm hai cột cần dùng (`order_no` và `name`), không có cột id trong danh sách chọn. Kết quả là record hoặc proxy của Spring Data, **không phải entity**, nên persistence context không chứa entity nào: không dirty checking, không lazy loading, không N+1.

```java
// Constructor expression của JPQL: kết quả là record, không phải entity.
@Query("select new com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy"
     + ".F1OrderSummary(o.orderNo, c.name) "
     + "from G5F1_PurchaseOrder o join o.customer c order by o.id")
List<F1OrderSummary> findAllSummaries();

// Interface projection: alias phải trùng tên getter của interface.
@Query("select o.orderNo as orderNo, c.name as customerName "
     + "from G5F1_PurchaseOrder o join o.customer c order by o.id")
List<F1OrderView> findAllViews();
```

```sql
select fpo1_0.order_no, c1_0.name
from g5_f1_all_lazy.purchase_order fpo1_0
join g5_f1_all_lazy.customer c1_0 on c1_0.id=fpo1_0.customer_id
order by fpo1_0.id
```

Ví dụ này dùng lại mô hình đơn hàng của Chương 11 (đơn thuộc về khách). DTO là lối thoát đúng cho màn hình **chỉ đọc**: bạn quyết định chính xác cột nào được chuyển và không phụ thuộc mapping fetch. Đổi lại, bạn không sửa được dữ liệu qua DTO. Đây cũng là cách thoát N+1 ở Chương 11 mà không cần tải entity.

**Trong JPA thuần.** `select new ...` (constructor expression) là JPQL chuẩn. Interface projection là tính năng của Spring Data, không thuộc JPA; trong JPA thuần dùng constructor expression hoặc `Tuple`.

::: {.ghi-nho}
DTO projection chọn đúng cột cần dùng và không đưa entity vào persistence context. Dùng cho màn hình chỉ đọc.
:::

::: {.tracuu codes="G5-S15"}
:::

#### 12.5.2 JPQL select new: DTO có hàm tổng hợp {.mo-rong}

**Bối cảnh.** Báo cáo cần tên khách và số đơn của từng khách. Dữ liệu này không phải một entity nào cả: nó là kết quả của phép đếm.

**Kết quả.** Đúng 1 câu SELECT có `join` và `group by`. Kết quả là 3 record (3 khách, mỗi người 2 đơn). Persistence context có **0 entity**.

```java
public record Q1CustomerSummary(String customerName, long orderCount) { }

List<Q1CustomerSummary> summaries = em.createQuery(
    "select new com.example.hibernatelab.g10_query.q1_baseline"
    + ".Q1CustomerSummary(c.name, count(o)) "
    + "from G10Q1_Customer c join c.orders o "
    + "group by c.id, c.name order by c.id",
    Q1CustomerSummary.class).getResultList();
```

```sql
select qc1_0.name, count(o1_0.id)
from g10_q1_baseline.customer qc1_0
join g10_q1_baseline.purchase_order o1_0 on qc1_0.id=o1_0.customer_id
group by qc1_0.id, qc1_0.name
order by qc1_0.id
```

Record chỉ là dữ liệu đọc: không vòng đời, không dirty checking, không lazy loading. Đổi lại bạn có một câu SELECT đúng cột cần và không lo N+1.

::: {.ghi-nho}
`select new` cho DTO có thể kèm hàm tổng hợp như `count`. Kết quả không nằm trong persistence context.
:::

::: {.tracuu codes="G10-S10a"}
:::

#### 12.5.3 Native query trả Tuple {.mo-rong}

**Bối cảnh.** Cùng báo cáo ở mục 12.5.2, nhưng bạn buộc phải viết native SQL (ví dụ để dùng hàm đặc thù của database).

**Kết quả.** Đúng 1 câu SELECT. Kết quả là 3 `Tuple`, đọc theo alias cột (`tuple.get("name", String.class)`). Persistence context có **0 entity**: giống DTO của JPQL.

```java
List<Tuple> tuples = em.createNativeQuery(
    "select c.name as name, count(o.id) as order_count "
    + "from g10_q1_baseline.customer c "
    + "join g10_q1_baseline.purchase_order o on o.customer_id = c.id "
    + "group by c.name order by c.name", Tuple.class).getResultList();
```

Khác biệt với JPQL: native SQL là văn bản của bạn, nên Hibernate **không thêm điều kiện** nào vào đó (ví dụ điều kiện của `@SoftDelete`, xem mục 13.4) và không kiểm tra tên bảng, tên cột lúc khởi động. Sai tên cột thì bạn chỉ biết lúc chạy.

::: {.ghi-nho}
Native query trả `Tuple` cũng không có entity nào. Đổi lại bạn tự chịu trách nhiệm cho điều kiện mà JPQL tự thêm.
:::

::: {.tracuu codes="G10-S10b"}
:::
