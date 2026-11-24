## Chương 15. Cập nhật và xóa hàng loạt

Chương 14 sửa từng entity: tải về, đổi field, để Hibernate tự sinh câu UPDATE lúc flush. Cách đó rất tiện nhưng chậm khi phải đổi hàng nghìn dòng: mỗi dòng phải được tải lên bộ nhớ. Vì vậy có câu lệnh **hàng loạt** (bulk): một câu `update ... where ...` hoặc `delete ... where ...` gửi thẳng xuống database, đổi nhiều dòng cùng lúc, không tải dòng nào lên.

Bulk rất nhanh, nhưng nó đi **vòng qua toàn bộ cơ chế mà các chương trước dạy bạn tin tưởng**. Bulk không biết persistence context đang giữ những entity nào, không chạy dirty checking, không chạy cascade, không chạy `orphanRemoval`, không chạy callback `@PreUpdate`, và không tăng `@Version`. Hậu quả quen thuộc nhất: dữ liệu trong database đã đổi, còn object trong bộ nhớ vẫn mang giá trị cũ ("stale").

Chương này chứng minh từng hậu quả đó bằng test, rồi tổng kết thành quy tắc dùng bulk an toàn.

![Sơ đồ bảng của mô hình bulk: product, purchase_order và order_item](img/g9-s05.png){width=85%}

Trong chương này:

- Bulk JPQL làm persistence context bị lệch, `clearAutomatically`, và bulk delete bỏ qua `orphanRemoval` (mục 15.1).
- Bulk delete bỏ qua cascade REMOVE của JPA (mục 15.2).
- Native UPDATE: bỏ qua cả persistence context lẫn `@Version` (mục 15.3).
- Quy tắc dùng bulk an toàn (mục 15.4).

**Trong JPA thuần.** Bulk JPQL và native query là JPA chuẩn (`em.createQuery(...).executeUpdate()`). `@Modifying` và `clearAutomatically` là của Spring Data; `@OnDelete` là của Hibernate. Mỗi mục có đoạn **Trong JPA thuần.** khi cần.

### 15.1 Bulk JPQL và persistence context bị lệch

Hai câu lệnh dưới đây là JPQL, viết trong repository của Spring Data. `@Modifying` là bắt buộc để Spring Data hiểu đây là câu thay đổi dữ liệu (không phải SELECT), và câu lệnh phải chạy trong một transaction.

```java
public interface BulkProductRepository extends JpaRepository<Product, Long> {

    // Bulk UPDATE: chạy thẳng xuống database, KHÔNG dọn persistence context.
    @Modifying
    @Query("update G9S05_Product p set p.price = p.price * 1.1")
    int raisePricesKeepingPersistenceContext();

    // Như trên, nhưng sau câu UPDATE, Spring gọi em.clear().
    @Modifying(clearAutomatically = true)
    @Query("update G9S05_Product p set p.price = p.price * 1.1")
    int raisePricesAndClearPersistenceContext();
}
```

#### 15.1.1 Bulk update: entity MANAGED giữ giá cũ, database đã là giá mới {.cot-loi}

**Bối cảnh.** Hôm nay cửa hàng tăng giá 10% cho mọi sản phẩm. Trong cùng một transaction, code đã tải sản phẩm "bút" (giá 100.00) lên để hiển thị, rồi chạy bulk update tăng giá. Sau đó code đọc lại `product.getPrice()` để in hóa đơn và thấy... 100.00.

**Khi nào xảy ra.** Entity đã MANAGED trong persistence context trước khi bulk update chạy, và bạn đọc giá trị từ chính object đó sau bulk update. Bulk update trên dòng hàng cũng vậy: sửa `quantity` bằng bulk khi dòng hàng đã được tải thì object vẫn mang số lượng cũ.

**Khi nào không xảy ra.** Nếu bạn tải entity **sau** bulk update (persistence context chưa có nó), hoặc nếu bạn dọn persistence context (mục 15.1.2).

**Ví dụ.**

```java
BigDecimal priceInMemory = inTxReturning(() -> {
    Product productPen = em.find(Product.class, productId);
    // productPen là MANAGED, giá 100.00 nằm trong persistence context.
    productRepository.raisePricesKeepingPersistenceContext();
    // UPDATE đã chạy xuống database; persistence context không hay biết.
    return productPen.getPrice();
});
```

**Kết quả.** Hibernate chạy một câu SELECT (lúc `find`) và đúng **một** câu UPDATE (chính câu bulk). Lúc commit không có UPDATE nào thêm.

```sql
select p1_0.id, p1_0.name, p1_0.price
from g9_s05_bulk_modifying.product p1_0
where p1_0.id=?

update g9_s05_bulk_modifying.product p1_0
set price=(p1_0.price*1.1)
```

| | Giá của bút |
|---|---|
| Trong bộ nhớ (object đã tải) | 100.00 |
| Trong database trước bulk update | 100.00 |
| Trong database sau bulk update | **110.00** |

Thay đổi: giá trong database. Giữ nguyên: giá trong object, vẫn là 100.00 và vẫn ở trạng thái MANAGED.

**Vì sao.** Bulk JPQL không đi qua persistence context: Hibernate dịch câu JPQL thành SQL rồi gửi đi, không tải entity nào, nên không biết object nào bị ảnh hưởng. Persistence context chỉ nạp lại một entity khi bạn gọi `find` mà entity **chưa có** trong đó; entity đã có thì `find` trả luôn bản cũ. Lúc commit, dirty checking so object với bản chụp lúc tải: hai bên giống nhau (cả hai đều 100.00), nên không có UPDATE nào, và giá 110.00 trong database không bị ghi đè.

Một chi tiết nguy hiểm hơn: nếu sau bulk update bạn lại `setPrice(...)` trên object cũ, dirty checking so với bản chụp cũ và sẽ ghi đè kết quả của bulk update.

::: {.ghi-nho}
Bulk update không cập nhật entity đã tải. Object trong bộ nhớ giữ giá trị cũ cho tới khi bạn tải lại.
:::

::: {.tracuu codes="G9-S05a,G4-S13a"}
:::

#### 15.1.2 clearAutomatically: tải lại thấy giá mới, nhưng entity cũ thành DETACHED {.cot-loi}

**Bối cảnh.** Để hết lệch, bạn thêm `clearAutomatically = true`. Giá hiển thị đúng 110.00 trở lại. Nhưng đâu đó trong cùng transaction, một thay đổi khác (chưa flush) bỗng biến mất.

**Khi nào xảy ra.** Repository method có `@Modifying(clearAutomatically = true)`. Sau câu bulk, Spring Data gọi `em.clear()`: **mọi** entity đang MANAGED trong persistence context bị DETACHED, không chỉ entity của bảng vừa đổi.

**Khi nào không xảy ra.** Nếu không bật `clearAutomatically` (mục 15.1.1: lệch nhưng không mất gì).

**Ví dụ.**

```java
inTx(() -> {
    Product staleProduct = em.find(Product.class, productId);
    // staleProduct: MANAGED
    productRepository.raisePricesAndClearPersistenceContext();
    // staleProduct: DETACHED (em.clear() đã tách nó)
    Product reloaded = em.find(Product.class, productId);
    // persistence context rỗng nên find phải SELECT lại: giá 110.00
    // staleProduct vẫn mang giá 100.00 và không còn được đồng bộ
});
```

**Kết quả.** Sau câu bulk, `staleProduct` chuyển từ MANAGED sang **DETACHED**. `find` kế tiếp đọc lại từ database và thấy 110.00. Object DETACHED cũ vẫn giữ 100.00.

| | Giá của bút |
|---|---|
| Object cũ (DETACHED) | 100.00 |
| Object tải lại (MANAGED) | **110.00** |
| Database | **110.00** |

Thay đổi: giá trong database và trong object tải lại. Giữ nguyên: object cũ, nhưng giờ nó là DETACHED và không còn được theo dõi.

Với dòng hàng, hành vi giống hệt: sau bulk update có `clearAutomatically`, item cũ cũng thành DETACHED, và `find` lại cho giá trị mới.

**Vì sao.** `em.clear()` làm rỗng persistence context. Đó là cách đơn giản nhất để chắc chắn lần tải kế tiếp sẽ đọc dữ liệu mới. Cái giá: mọi entity MANAGED khác (kể cả loại không liên quan) cũng bị tách, và **thay đổi chưa flush của chúng bị mất**, vì chúng không còn nằm trong persistence context để dirty checking tìm thấy.

**Cách tránh.** Gọi `em.flush()` trước câu bulk để đẩy hết thay đổi đang chờ xuống database, rồi bulk, rồi `clear`. Hoặc tốt hơn: đặt bulk update ở đầu transaction, trước khi tải hay sửa bất cứ thứ gì.

**Trong JPA thuần.** `clearAutomatically` là của Spring Data. Với JPA thuần, bạn tự viết hai bước: `em.createQuery("update ...").executeUpdate();` rồi `em.clear();` (và `em.flush()` trước đó nếu có thay đổi đang chờ). Nếu chỉ cần làm mới một entity cụ thể, `em.refresh(entity)` đọc lại đúng entity đó mà không đụng các entity khác.

::: {.ghi-nho}
`clearAutomatically` hết lệch nhưng tách mọi entity khỏi persistence context. Flush trước, hoặc đặt bulk ở đầu transaction.
:::

::: {.tracuu codes="G9-S05b,G4-S13b"}
:::

#### 15.1.3 Bulk delete: bỏ qua orphanRemoval, collection trong bộ nhớ vẫn còn item "ma" {.cot-loi}

**Bối cảnh.** Hủy đơn hàng: code xóa mọi dòng hàng của đơn bằng một câu `delete from OrderItem where order.id = :id`. Ngay sau đó code đọc `order.getItems().size()` và vẫn thấy 2.

**Khi nào xảy ra.** Đơn đã được tải cùng collection `items` (inverse side, `cascade = ALL`, `orphanRemoval = true`), rồi bulk delete chạy trên bảng con.

**Khi nào không xảy ra.** Nếu bạn xóa bằng cách gỡ phần tử khỏi collection (`orphanRemoval`, Chương 6) hoặc `em.remove`, thì Hibernate biết và cập nhật persistence context.

**Ví dụ.**

```java
@Modifying
@Query("delete from G9S05_OrderItem i where i.order.id = :orderId")
int deleteAllOfOrder(@Param("orderId") Long orderId);
```

```java
inTx(() -> {
    PurchaseOrder loadedOrder = em.find(PurchaseOrder.class, orderId);
    loadedOrder.getItems().size();             // 2 item MANAGED
    int deletedRows = orderItemRepository.deleteAllOfOrder(orderId);
    // loadedOrder.getItems() vẫn có 2 phần tử, dù database không còn dòng nào.
});
```

**Kết quả.** Chỉ **một** câu DELETE, vào `order_item`. Câu bulk báo đã xóa 2 dòng.

```sql
delete
from g9_s05_bulk_modifying.order_item oi1_0
where oi1_0.order_id=?
```

| | order_item (số dòng) | `order.getItems().size()` | purchase_order (số dòng) |
|---|---|---|---|
| Trước | 2 | 2 | 1 |
| Sau (database) | **0** | 2 (bộ nhớ, không đổi) | 1 |

Thay đổi: database hết dòng hàng. Giữ nguyên: dòng `purchase_order`, và collection trong bộ nhớ vẫn chứa 2 item MANAGED mà dòng thật của chúng không còn.

**Vì sao.** `orphanRemoval` và cascade là cơ chế của **persistence context**: chúng chỉ chạy khi Hibernate theo dõi một thay đổi trên entity hoặc collection. Bulk delete không tải entity nào và không đổi collection nào, nên không có gì để theo dõi. Hai item "ma" tồn tại vì persistence context không được thông báo.

**Cách tránh.** Sau bulk delete, đừng dùng lại các entity đã tải của vùng bị xóa: `clear()` hoặc bắt đầu transaction mới. Hoặc nếu số dòng ít, xóa qua collection để `orphanRemoval` lo.

::: {.ghi-nho}
Bulk delete không đi qua `orphanRemoval` hay cascade, và không cập nhật collection trong bộ nhớ.
:::

::: {.tracuu codes="G9-S05c"}
:::

### 15.2 Bulk delete bỏ qua cascade REMOVE của JPA

Mục 15.1.3 xóa dòng con. Mục này xóa dòng **cha** bằng bulk, khi con còn đó. Hai biến thể của cùng mô hình `PurchaseOrder` một-nhiều `OrderItem` (Chương 5):

- Biến thể thứ nhất: `items` có `cascade = REMOVE` (cascade của JPA, chạy trong Java).
- Biến thể thứ hai: khóa ngoại có `ON DELETE CASCADE` do `@OnDelete` (cascade của database).

```java
// Biến thể thứ nhất: INVERSE side, cascade REMOVE chạy trong Java.
@OneToMany(mappedBy = "order", fetch = FetchType.LAZY,
           cascade = CascadeType.REMOVE)
private List<C3OrderItem> items = new ArrayList<>();

// Biến thể thứ hai: OWNING side. DDL của khóa ngoại có ON DELETE CASCADE.
@ManyToOne(fetch = FetchType.LAZY)
@OnDelete(action = OnDeleteAction.CASCADE)
@JoinColumn(name = "order_id")
private C8PurchaseOrder order;
```

#### 15.2.1 Bulk delete cha với cascade REMOVE của JPA: vi phạm khóa ngoại {.mo-rong}

**Bối cảnh.** Bạn đã đặt `cascade = REMOVE` nên tin rằng xóa đơn là xóa luôn dòng hàng. Rồi một job dọn dẹp dùng `delete from PurchaseOrder` để xóa hàng loạt đơn cũ, và lỗi vi phạm khóa ngoại xuất hiện.

**Khi nào xảy ra.** Bulk JPQL `delete` trên bảng cha, trong khi bảng con còn dòng và khóa ngoại không có `ON DELETE CASCADE`.

**Khi nào không xảy ra.** Nếu bạn xóa từng đơn bằng `em.remove(order)` hoặc `repository.deleteById(id)`: lúc đó Hibernate tải entity và cascade REMOVE chạy.

**Ví dụ.**

```java
// Database có đơn A-001 với 2 dòng hàng.
inTx(() -> em.createQuery("delete from G2C3_PurchaseOrder").executeUpdate());
```

**Kết quả.** Hibernate gửi **một** câu DELETE vào `purchase_order` và PostgreSQL từ chối vì còn dòng hàng trỏ vào. Hibernate ném `ConstraintViolationException` (vì gọi `EntityManager` trực tiếp nên Spring không dịch lỗi). Không có SELECT hay DELETE nào cho `order_item`.

```sql
delete
from g2_c3_remove.purchase_order cpo1_0
```

| | purchase_order | order_item |
|---|---|---|
| Trước | 1 | 2 |
| Sau (transaction bị rollback) | 1 | 2 |

Thay đổi: không có gì (rollback). Giữ nguyên: mọi dòng.

**Vì sao.** Cascade REMOVE của JPA chạy **trong Java**: nó cần có entity đã tải để đi dọc collection `items` và gọi `remove` cho từng phần tử. Bulk delete không tải entity nên không có gì để cascade. Hibernate chỉ gửi câu DELETE, và khóa ngoại của database làm đúng việc của nó: từ chối.

::: {.ghi-nho}
Cascade REMOVE của JPA chỉ chạy khi xóa từng entity qua `em.remove`. Bulk delete bỏ qua nó.
:::

::: {.tracuu codes="G2-S12a"}
:::

#### 15.2.2 Bulk delete cha với ON DELETE CASCADE của database: thành công {.mo-rong}

**Bối cảnh.** Cùng job dọn dẹp, nhưng khóa ngoại được khai báo `ON DELETE CASCADE` (bằng `@OnDelete`).

**Khi nào xảy ra.** Khóa ngoại trong database có `ON DELETE CASCADE`. Mọi cách xóa cha, kể cả bulk và kể cả SQL thô, đều kéo theo xóa con.

**Khi nào không xảy ra.** Nếu schema database không có mệnh đề đó (ví dụ bảng do công cụ migration tạo mà không khai báo).

**Ví dụ.**

```java
inTx(() -> em.createQuery("delete from G2C8_PurchaseOrder").executeUpdate());
```

**Kết quả.** Hibernate vẫn chỉ gửi **một** câu DELETE vào `purchase_order` và không gửi câu nào cho `order_item`. Lần này câu lệnh thành công.

```sql
delete
from g2_c8_ondelete.purchase_order cpo1_0
```

| | purchase_order | order_item |
|---|---|---|
| Trước | 1 | 2 |
| Sau | **0** | **0** |

Thay đổi: cả hai bảng trống. Giữ nguyên: không có dòng nào còn lại, và Hibernate không hề gửi câu DELETE cho bảng con.

**Vì sao.** Bulk vẫn bỏ qua cascade của JPA, nhưng cascade của **database** chạy ở mọi đường xóa, vì nó nằm trong ràng buộc khóa ngoại chứ không nằm trong Java. PostgreSQL tự xóa hai dòng hàng ngay khi dòng cha bị xóa. Đây là điểm mạnh của `@OnDelete`: nó không phụ thuộc đường nào đi tới database. Mặt trái: persistence context không hay biết các dòng hàng đã mất (xem lại mục 15.1.3).

**Trong JPA thuần.** `@OnDelete` là của Hibernate. JPA không có annotation tương đương, nhưng bạn có thể đạt cùng kết quả bằng cách khai báo `ON DELETE CASCADE` trong định nghĩa khóa ngoại: `@JoinColumn(name = "order_id", foreignKey = @ForeignKey(foreignKeyDefinition = "foreign key (order_id) references purchase_order (id) on delete cascade"))`. Cách này chỉ tác dụng khi schema do JPA sinh ra; nếu schema do công cụ migration quản lý thì viết mệnh đề đó trong script migration.

::: {.ghi-nho}
Cascade của database chạy ở mọi đường xóa, kể cả bulk. Cascade của JPA chỉ chạy qua `em.remove`.
:::

::: {.tracuu codes="G2-S12b"}
:::

### 15.3 Native UPDATE: bỏ qua cả persistence context lẫn Version

Native query là SQL thô của bạn. Hibernate gửi nguyên văn, không dịch, không phân tích, nên mọi hậu quả của bulk JPQL ở trên đều đúng, và thêm một hậu quả nữa: **cột `version` không tự tăng**.

#### 15.3.1 Native UPDATE trên entity có Version đang MANAGED {.cot-loi}

**Bối cảnh.** Sản phẩm có `@Version` để chặn hai người cùng sửa giá. Một job chạy câu native `update product set price = 99.00` để cập nhật nhanh. Bạn nghĩ cột `version` bảo vệ bạn: ai đang giữ bản cũ mà lưu sẽ bị báo xung đột.

**Khi nào xảy ra.** Cập nhật bằng SQL thô (hoặc bulk JPQL không có từ khóa `versioned`) trên bảng có cột version.

**Khi nào không xảy ra.** Nếu chính câu SQL của bạn tăng version (`version = version + 1`), hoặc nếu bạn cập nhật qua entity để Hibernate sinh câu UPDATE.

**Ví dụ.**

```java
inTx(() -> {
    Q2Product productPen = em.find(Q2Product.class, 1L);
    em.createNativeQuery(
        "update g10_q2_versioned.product set price = 99.00 where id = 1")
        .executeUpdate();
    // productPen vẫn MANAGED và vẫn mang giá cũ 10.00.
    // Lúc flush: productPen không dirty, nên Hibernate không UPDATE gì thêm.
});
```

**Kết quả.** Đúng một câu UPDATE: chính câu native. Hibernate không thêm câu nào.

```sql
select qp1_0.id, qp1_0.name, qp1_0.price, qp1_0.version
from g10_q2_versioned.product qp1_0
where qp1_0.id=?

update g10_q2_versioned.product
set price = 99.00
where id = 1
```

| Bảng product | price | version |
|---|---|---|
| Trước | 10.00 | 0 |
| Sau | **99.00** | 0 |

Thay đổi: `price`. Giữ nguyên: `version` vẫn 0, và object trong bộ nhớ vẫn mang giá 10.00.

**Vì sao.** Hibernate chỉ tăng `version` khi **chính nó** sinh câu UPDATE cho entity. Câu native không đi qua đó, nên cột version đứng yên. Hệ quả nghiêm trọng: một transaction khác đang giữ bản cũ (version 0) lưu thay đổi vẫn khớp điều kiện `where version = 0`, ghi đè giá 99.00 mà **không bị `OptimisticLockException`**. Cơ chế bảo vệ của `@Version` âm thầm vô hiệu.

**Cách tránh.** Tự viết `version = version + 1` trong câu SQL, hoặc làm mới entity bằng `em.refresh` sau native update. Cả hai đều phải do bạn nhớ làm.

::: {.chua-kiem-chung}
HQL của Hibernate có dạng `update versioned Product set ...` để bulk update tự tăng version. Lab không có test cho cú pháp này, nên cuốn sách không khẳng định hành vi của nó. Nếu cần, hãy tự viết test trên phiên bản Hibernate của bạn.
:::

**Trong JPA thuần.** Native query là JPA chuẩn (`em.createNativeQuery(...)`), và hậu quả ở trên áp dụng cho mọi provider: SQL thô không đi qua cơ chế version của provider.

::: {.ghi-nho}
Native UPDATE không tăng `@Version` và không làm mới entity đã tải. Muốn giữ cơ chế khóa lạc quan, bạn phải tự tăng version trong câu SQL.
:::

::: {.tracuu codes="G10-S06"}
:::

### 15.4 Quy tắc dùng bulk an toàn

Bulk không xấu. Nó là công cụ đúng khi bạn đổi rất nhiều dòng bằng một quy tắc đồng nhất (tăng giá 10%, đánh dấu đơn quá hạn, xóa dữ liệu tạm). Nhưng vì nó đi vòng qua Hibernate, bạn phải gánh những việc Hibernate thường làm hộ. Bảng dưới tóm tắt những gì **không** xảy ra với bulk, và cách bù.

| Cơ chế thường có | Với bulk JPQL hoặc native | Cách bù |
|---|---|---|
| Persistence context cập nhật entity đã tải | Không. Object giữ giá trị cũ | Bulk ở đầu transaction, hoặc `flush` rồi `clear`, hoặc `refresh` từng entity |
| Dirty checking, `@PreUpdate`, `@UpdateTimestamp` | Không chạy | Tự gán cột đó trong câu SQL |
| `@Version` tự tăng | Không tăng | Tự viết `version = version + 1` |
| Cascade và `orphanRemoval` | Không chạy | `ON DELETE CASCADE` trong database, hoặc xóa từng entity |
| Collection trong bộ nhớ | Không cập nhật | Dọn persistence context |

Ba quy tắc ngắn gọn để nhớ:

1. **Đặt bulk ở đầu transaction**, trước khi tải hay sửa entity nào. Khi persistence context còn rỗng thì không có gì để lệch.
2. **Nếu phải chạy bulk giữa chừng, `flush` trước, `clear` sau.** Flush để thay đổi đang chờ không bị mất; clear để lần tải kế tiếp thấy dữ liệu mới.
3. **Đừng dùng bulk để thay thế cascade.** Quan hệ cha con cần xóa dây chuyền thì giao việc đó cho `ON DELETE CASCADE` của database, hoặc xóa từng entity.

Và với mọi chức năng dùng bulk, hãy viết test đọc dữ liệu bằng JDBC (như các test của lab) để chứng minh database đúng, vì object trong bộ nhớ không nói lên điều đó.
