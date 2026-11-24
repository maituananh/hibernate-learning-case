## Chương 17. Ghi đồng thời và khóa dữ liệu

Chương 16 nói về một transaction đứng một mình. Chương này nói về **hai transaction chạm vào cùng một dòng cùng lúc**: hai khách cùng mua món hàng cuối cùng, hai nhân viên cùng sửa một đơn, hai worker cùng nhặt một job trong hàng đợi. Đây là chỗ những lỗi âm thầm nhất xuất hiện, vì mỗi transaction riêng lẻ đều chạy "đúng", chỉ có kết quả gộp là sai.

Chương đi theo một mạch:

1. Một lỗi cụ thể: **lost update**, hai lần trừ kho chỉ trừ được một (mục 17.1).
2. Ba cách sửa nó, mỗi cách có giá riêng (mục 17.2).
3. Khóa lạc quan (`@Version`) khi hai người cùng sửa một đối tượng (mục 17.3).
4. Khóa bi quan (`SELECT ... FOR UPDATE`) và các biến thể của nó (mục 17.4).
5. Hàng đợi job không giẫm chân nhau với `SKIP LOCKED` (mục 17.5).
6. Deadlock và cách khóa theo thứ tự để tránh (mục 17.6).

Mọi thí nghiệm dùng thật hai transaction chạy song song trên hai thread, với các "cổng" (gate) để ép thứ tự đan xen: ví dụ "transaction thứ nhất đã đọc xong, giờ mới cho transaction thứ hai chạy". Nhờ vậy kết quả lặp lại được, không phụ thuộc may rủi.

![Sơ đồ bảng product có cột version, dùng cho các thí nghiệm khóa lạc quan](img/g11-t2.png){width=60%}

**Trong JPA thuần.** `@Version`, `LockModeType` và hint `jakarta.persistence.lock.timeout` đều là JPA chuẩn, nên chương này gần như không phụ thuộc Hibernate. Điểm riêng của Hibernate hoặc Spring được nêu rõ ở từng mục (ví dụ giá trị `-2` của hint timeout, `@Lock` và `@QueryHints` của Spring Data).

### 17.1 Lost update: hai lần trừ kho, chỉ trừ được một

#### 17.1.1 Không có version, không có khóa: tồn kho 9 thay vì 8 {.cot-loi}

**Bối cảnh.** Kho còn 10 cây bút. Hai khách cùng bấm mua một cây ở cùng thời điểm. Mỗi request đọc tồn kho, trừ 1 trong Java, rồi lưu. Cuối cùng kho chỉ còn 9 thay vì 8: một cây bút đã bán mà kho không ghi nhận.

**Khi nào xảy ra.** Entity không có `@Version`, không có khóa, và hai transaction đọc cùng giá trị cũ trước khi một trong hai commit.

**Khi nào không xảy ra.** Khi bạn dùng một trong ba cách sửa ở mục 17.2, hoặc khi hai transaction chạy nối tiếp nhau (không đan xen).

**Ví dụ.**

```java
Running<Void> tx1 = concurrent.start(() -> {
    T1Product product = em.find(T1Product.class, productId); // stock = 10
    tx1HasRead.open();
    tx2Committed.await();     // DB đã là 9, nhưng tx1 vẫn giữ bản chụp 10
    product.decreaseStock(1); // 10 - 1 = 9, lúc commit: UPDATE stock = 9
});
tx1HasRead.await();
inTx(() -> em.find(T1Product.class, productId).decreaseStock(1)); // 10 thành 9
tx2Committed.open();
tx1.join();
```

**Kết quả.** Hai transaction đều thành công, không có exception nào. Tồn kho cuối cùng là **9**.

| Bảng product | stock |
|---|---|
| Trước | 10 |
| Sau | **9** (đúng ra phải là 8) |

Thay đổi: `stock` giảm đúng một lần. Giữ nguyên: các cột khác. Một lần trừ đã biến mất.

**Vì sao.** Transaction thứ nhất đọc `stock = 10` và giữ bản chụp đó trong persistence context. Transaction thứ hai trừ 1 và commit, nên database là 9. Rồi transaction thứ nhất tính `10 - 1 = 9` **từ số liệu cũ** và ghi `stock = 9`, đè lên kết quả của transaction kia. Hibernate không thấy gì sai: câu UPDATE của nó (`where id = ?`) khớp một dòng, vì dòng đó vẫn tồn tại. Chỉ có sự kiện "dòng đã đổi kể từ lúc tôi đọc" là không ai kiểm tra.

::: {.ghi-nho}
Lost update không có exception nào báo. Hai transaction cùng đọc rồi cùng ghi từ số liệu cũ, và lần commit sau đè mất lần commit trước.
:::

::: {.tracuu codes="G11-S05"}
:::

### 17.2 Ba cách sửa lost update

Cả ba cách dưới đây sửa đúng kịch bản của mục 17.1 và cho kết quả 8. Chúng khác nhau ở chỗ **ai gánh chi phí**: người gọi phải retry, người khác phải xếp hàng chờ, hoặc entity trong bộ nhớ bị lệch.

#### 17.2.1 Cách một: Version, transaction đến sau bị từ chối và phải retry {.cot-loi}

**Bối cảnh.** Bạn thêm cột `version` vào sản phẩm. Khi hai người cùng trừ kho, một người sẽ nhận lỗi thay vì âm thầm ghi đè.

**Khi nào xảy ra.** Entity có `@Version`, và hai transaction đọc cùng một version rồi cùng ghi.

**Khi nào không xảy ra.** Khi hai transaction không đan xen. Lúc đó UPDATE của cả hai đều khớp version.

**Ví dụ.**

```java
@Entity(name = "G11T2_Product")
@Table(schema = "g11_t2_versioned", name = "product")
public class T2Product {

    @Column(name = "stock", nullable = false)
    private int stock;

    // Mỗi UPDATE kèm "where version = ?" và tăng version thêm 1.
    @Version
    @Column(name = "version", nullable = false)
    private int version;
}
```

Kịch bản test giống mục 17.1 (transaction thứ nhất đọc, transaction thứ hai trừ rồi commit, transaction thứ nhất trừ sau), rồi gọi lại lần thứ ba trong transaction mới để thử retry.

**Kết quả.** Transaction đến sau nhận `ObjectOptimisticLockingFailureException` (lớp con của `OptimisticLockingFailureException`) với thông báo "Unexpected row count (expected row count 1 but was 0)", và bị rollback. Khi chạy lại (retry) trong transaction mới, nó đọc 9 và trừ ra 8.

| Bảng product | stock | version |
|---|---|---|
| Trước | 10 | 0 |
| Sau transaction thứ hai | 9 | 1 |
| Sau retry | **8** | **2** |

Thay đổi: `stock` giảm hai lần đúng, `version` tăng hai lần. Giữ nguyên: các cột khác.

**Vì sao.** Câu UPDATE của Hibernate có dạng `update product set stock=?, version=? where id=? and version=?`. Transaction đến sau mang `version = 0` nhưng dòng đã là version 1, nên **không dòng nào khớp**. Hibernate đếm số dòng bị ảnh hưởng, thấy 0 trong khi kỳ vọng 1, và ném exception. Chi phí: **người gọi phải tự retry** (đọc lại và làm lại), vì Hibernate không làm hộ.

::: {.ghi-nho}
`@Version` biến lost update thành một exception rõ ràng. Cái giá là code gọi phải biết retry.
:::

::: {.tracuu codes="G11-S06a"}
:::

#### 17.2.2 Cách hai: PESSIMISTIC_WRITE, transaction đến sau chờ khóa {.cot-loi}

**Bối cảnh.** Bạn không muốn có retry. Bạn muốn người đến sau **chờ** người đến trước xong, rồi đọc số liệu mới.

**Khi nào xảy ra.** Cả hai transaction đọc dòng bằng khóa ghi (`SELECT ... FOR UPDATE`).

**Khi nào không xảy ra.** Nếu một trong hai bên đọc không khóa (khi đó không có gì chặn).

**Ví dụ.**

```java
public interface T1ProductRepository extends JpaRepository<T1Product, Long> {

    // PESSIMISTIC_WRITE: người khác muốn khóa hoặc sửa dòng này phải chờ.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from G11T1_Product p where p.id = :id")
    Optional<T1Product> findByIdForUpdate(@Param("id") Long id);
}
```

Cả hai transaction gọi `findByIdForUpdate`, rồi trừ 1.

**Kết quả.** Transaction thứ hai **bị chặn ngay tại câu SELECT** cho tới khi transaction thứ nhất commit, rồi đọc số liệu mới (9) và trừ ra 8. Không có exception, không cần retry. Test dùng truy vấn hệ thống của PostgreSQL để chứng minh có một session đang chờ khóa.

| Bảng product | stock |
|---|---|
| Trước | 10 |
| Sau | **8** |

Thay đổi: `stock` giảm hai lần đúng. Giữ nguyên: các cột khác.

**Vì sao.** Câu `SELECT ... FOR UPDATE` làm PostgreSQL khóa dòng. Transaction thứ hai xin khóa cùng dòng sẽ bị chặn trong database, không phải trong Java. Khi transaction thứ nhất commit, khóa được nhả, transaction thứ hai nhận khóa và **đọc lại dòng lúc đó**, thấy 9. Chi phí: người khác phải xếp hàng, và khi giữ nhiều khóa theo thứ tự lộn xộn thì có nguy cơ deadlock (mục 17.6).

::: {.ghi-nho}
Khóa bi quan làm người đến sau chờ và đọc số liệu mới, nên không cần retry. Cái giá là xếp hàng và nguy cơ deadlock.
:::

::: {.tracuu codes="G11-S06b"}
:::

#### 17.2.3 Cách ba: UPDATE nguyên tử, để database tự trừ {.cot-loi}

**Bối cảnh.** Cách gọn nhất: đừng đọc tồn kho về Java. Bảo database trừ trực tiếp.

**Khi nào xảy ra.** Bạn dùng câu `update product set stock = stock - 1 where id = ?`.

**Khi nào không xảy ra.** Khi bạn đọc về Java, tính, rồi ghi lại (mục 17.1).

**Ví dụ.**

```java
@Modifying
@Query("update G11T1_Product p set p.stock = p.stock - :quantity where p.id = :id")
int decreaseStockInDatabase(@Param("id") Long id, @Param("quantity") int quantity);
```

**Kết quả.** Hai lần trừ đều có tác dụng: tồn kho **8**. Nhưng entity đã tải trước đó trong transaction thứ nhất vẫn nói tồn kho là **10**.

| | stock |
|---|---|
| Database sau hai lần trừ | **8** |
| Entity đã tải trong transaction thứ nhất | 10 |

Thay đổi: database đúng 8. Giữ nguyên: giá trị trong bộ nhớ vẫn là 10 (cũ).

**Vì sao.** `stock = stock - 1` được database tính **nguyên tử** trên giá trị hiện tại, và khóa dòng chỉ trong thời gian câu UPDATE chạy, nên hai lần trừ nối tiếp nhau đúng. Không cần version, không phải chờ lâu. Cái giá là đã bàn ở Chương 15: câu bulk đi vòng qua persistence context nên entity đã tải bị lệch. Nếu code sau đó còn dùng entity đó, phải `refresh` hoặc `clear`.

| Cách | Ai gánh chi phí | Khi nào chọn |
|---|---|---|
| `@Version` | Người gọi phải retry | Xung đột hiếm, thao tác phức tạp hơn "trừ một số" |
| `PESSIMISTIC_WRITE` | Người khác phải chờ | Xung đột thường xuyên, cần kết quả đúng ngay lần đầu |
| UPDATE nguyên tử | Entity trong bộ nhớ bị lệch | Phép tính đơn giản có thể viết gọn trong SQL |

::: {.ghi-nho}
UPDATE nguyên tử đúng và nhanh, nhưng đi vòng qua persistence context: entity đã tải giữ số cũ.
:::

::: {.tracuu codes="G11-S06c"}
:::

### 17.3 Optimistic lock khi hai người cùng sửa

Mục 17.2.1 dùng `@Version` cho hai transaction chạy song song. Ngoài đời, kịch bản phổ biến hơn là hai người mở cùng một màn hình **sửa** (hai request tách biệt hoàn toàn, mỗi cái là một transaction riêng), rồi lưu lần lượt. Hai bản entity là DETACHED, mang cùng một `version` cũ.

#### 17.3.1 Hai bản DETACHED của một đơn: lưu bản thứ hai thì bị từ chối {.mo-rong}

**Bối cảnh.** Hai nhân viên cùng mở đơn A-001 (version 0). Người thứ nhất sửa ghi chú và lưu. Người thứ hai sửa ghi chú khác và lưu sau. Nếu hệ thống không phát hiện, ghi chú của người thứ nhất bị ghi đè mà không ai biết (xem mục 14.8.2 cho một dạng mất dữ liệu liên quan).

**Khi nào xảy ra.** Đơn có `@Version`, và hai bản DETACHED được lưu lần lượt qua `repository.save` (tức `merge`).

**Khi nào không xảy ra.** Nếu entity không có `@Version`: lúc đó người thứ hai ghi đè mà không lỗi.

**Ví dụ.**

```java
// Hai request tải đơn ở hai transaction khác nhau: cả hai nhận version = 0.
PurchaseOrder copyOfUserOne = inTxReturning(() -> em.find(PurchaseOrder.class, id));
PurchaseOrder copyOfUserTwo = inTxReturning(() -> em.find(PurchaseOrder.class, id));
copyOfUserOne.setNote("bản của người 1");
copyOfUserTwo.setNote("bản của người 2");

orderRepository.save(copyOfUserOne); // thành công, version 0 thành 1
orderRepository.save(copyOfUserTwo); // ObjectOptimisticLockingFailureException
```

**Kết quả.** Lần lưu đầu thành công, `version` từ 0 lên 1. Lần lưu thứ hai ném `ObjectOptimisticLockingFailureException` và bị rollback. Database giữ ghi chú của người thứ nhất.

| Bảng purchase_order | note | version |
|---|---|---|
| Trước | NULL | 0 |
| Sau lần lưu thứ nhất | bản của người 1 | 1 |
| Sau lần lưu thứ hai (bị từ chối) | bản của người 1 | 1 |

Thay đổi: ghi chú và version, chỉ từ lần lưu đầu. Giữ nguyên: mọi thứ sau lần lưu thất bại.

::: {.khac-du-doan codes="G9-S08,G9-S08b"}
**Điều nhiều người nghĩ:** lưu bản thứ hai luôn ném `ObjectOptimisticLockingFailureException`, dù gọi qua đường nào.

**Thực tế:** đúng khi lưu qua `repository.save`, vì Spring dịch exception. Nhưng nếu gọi thẳng `em.merge` thì ném `jakarta.persistence.OptimisticLockException` của JPA, chưa được Spring dịch (merge so version ngay lúc gọi, không đi qua proxy của `@Repository`). Hai lớp exception khác nhau, nên khối `catch` của bạn phải khớp với đường gọi.

Đã kiểm chứng trên Hibernate 7.4.15.Final.
:::

**Vì sao.** `merge` tải bản trong database (version 1) rồi so với version của bản DETACHED (version 0). Hai version khác nhau nghĩa là "đã có người đổi kể từ lúc bạn đọc", nên Hibernate từ chối. Đây cũng là lý do chủ yếu để đặt `@Version` trên entity người dùng sửa qua màn hình.

**Cách tránh.** Bắt `ObjectOptimisticLockingFailureException` ở tầng gọi service và trả về thông báo "dữ liệu đã bị người khác sửa, hãy tải lại". Trong ứng dụng web, thường gửi version cùng request để so khớp.

::: {.ghi-nho}
`@Version` chặn người lưu sau ghi đè người lưu trước. Qua `repository.save` bạn nhận exception của Spring; qua `em.merge` bạn nhận exception của JPA.
:::

::: {.tracuu codes="G9-S08,G9-S08b"}
:::

#### 17.3.2 Version riêng của item: cha không đổi {.mo-rong}

**Bối cảnh.** Mục 17.3.1 dùng version của **đơn**. Mục này dùng version của **dòng hàng**: hai người cùng sửa số lượng của cùng một dòng hàng, nhưng bạn không muốn `version` của đơn bị đụng (Chương 14 đã cho thấy sửa dòng hàng qua collection `mappedBy` không đụng đơn).

**Khi nào xảy ra.** `OrderItem` có `@Version` riêng, và `PurchaseOrder.items` cascade có `MERGE`.

**Khi nào không xảy ra.** Nếu dòng hàng không có `@Version`: người lưu sau ghi đè người lưu trước mà không lỗi.

**Ví dụ.**

```java
// Cả hai người tải đơn (số lượng bút = 1, version dòng hàng = 0).
copyOfUserA.getItems().get(0).setQuantity(5);
copyOfUserB.getItems().get(0).setQuantity(7);

inTx(() -> u5OrderRepository.save(copyOfUserA)); // thành công
inTx(() -> u5OrderRepository.save(copyOfUserB)); // bị từ chối
```

**Kết quả.** Lần lưu của người A thành công với đúng một câu UPDATE dòng hàng kèm version. Lần lưu của người B bị `ObjectOptimisticLockingFailureException`. Database giữ 5 của người A.

```sql
update g4_u5_child_version.order_item
set order_id=?, product_id=?, quantity=?, unit_price=?, version=?
where id=? and version=?
```

| | quantity | version của dòng hàng | version của đơn |
|---|---|---|---|
| Trước | 1 | 0 | 0 |
| Sau | **5** | **1** | 0 |

Thay đổi: số lượng và version của dòng hàng. Giữ nguyên: toàn bộ dòng `purchase_order`, kể cả `version`.

**Vì sao.** Mỗi `@Version` chỉ bảo vệ **dòng của chính entity đó**. Bản của B mang version dòng hàng 0 nhưng database là 1, nên UPDATE của B không khớp dòng nào. Đơn không bị đụng vì Chương 14 đã chứng minh: sửa con qua collection `mappedBy` không làm cha dirty. Hệ quả thiết kế: hai người sửa **hai dòng hàng khác nhau** của cùng một đơn không xung đột nhau; chỉ hai người sửa **cùng một dòng** mới bị chặn. Nếu bạn muốn mọi thay đổi trong đơn đều tính là xung đột, dùng `OPTIMISTIC_FORCE_INCREMENT` (mục 14.5).

::: {.ghi-nho}
Version của con khóa riêng từng dòng con, không đụng cha. Hai người sửa hai dòng khác nhau không xung đột.
:::

::: {.tracuu codes="G4-S11"}
:::

### 17.4 Pessimistic lock và NOWAIT

Khóa bi quan nói với database: "tôi sắp sửa dòng này, đừng cho ai khác khóa hay sửa nó". Trong JPA, bạn chọn qua `LockModeType` và hint timeout.

#### 17.4.1 Hai chế độ khóa sinh SQL gì {.cot-loi}

**Bối cảnh.** Bạn viết `@Lock(PESSIMISTIC_WRITE)` và muốn biết nó thực sự thành câu SQL gì trên PostgreSQL, vì mỗi database một khác.

**Khi nào xảy ra.** Truy vấn có `@Lock` (hoặc `setLockMode`) và chạy trong transaction.

**Khi nào không xảy ra.** Không có chế độ khóa thì câu SELECT thường, không có mệnh đề `for ...`.

**Ví dụ.**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select p from G11T1_Product p where p.id = :id")
Optional<T1Product> findByIdForUpdate(@Param("id") Long id);

@Lock(LockModeType.PESSIMISTIC_READ)
@Query("select p from G11T1_Product p where p.id = :id")
Optional<T1Product> findByIdForShare(@Param("id") Long id);
```

**Kết quả.** Mỗi lần gọi chỉ một câu SQL, kèm mệnh đề khóa ở cuối.

```sql
-- PESSIMISTIC_WRITE
select tp1_0.id, tp1_0.name, tp1_0.stock
from g11_t1_baseline.product tp1_0
where tp1_0.id=?
for no key update of tp1_0

-- PESSIMISTIC_READ
select tp1_0.id, tp1_0.name, tp1_0.stock
from g11_t1_baseline.product tp1_0
where tp1_0.id=?
for share of tp1_0
```

| Chế độ JPA | Mệnh đề PostgreSQL | Ý nghĩa |
|---|---|---|
| `PESSIMISTIC_WRITE` | `for no key update of <bí danh>` | Chặn mọi người **sửa** dòng, nhưng không chặn chèn dòng con có khóa ngoại trỏ tới dòng này |
| `PESSIMISTIC_READ` | `for share of <bí danh>` | Nhiều người cùng giữ khóa share, nhưng chặn người ghi |

**Vì sao.** Điều đáng chú ý: `PESSIMISTIC_WRITE` **không** sinh `for update` thuần mà là `for no key update`. Đây là lựa chọn của dialect PostgreSQL trong Hibernate 7: nó vẫn chặn mọi người sửa dòng, nhưng nhẹ hơn `for update` vì không chặn việc thêm dòng con trỏ tới dòng này qua khóa ngoại. Phần `of tp1_0` chỉ rõ bảng nào cần khóa, điều quan trọng khi truy vấn có join (mục 17.4.3).

::: {.ghi-nho}
Trên PostgreSQL, `PESSIMISTIC_WRITE` thành `for no key update`, `PESSIMISTIC_READ` thành `for share`. Luôn xem SQL thật thay vì đoán.
:::

::: {.tracuu codes="G11-S07a,G11-S07b"}
:::

#### 17.4.2 NOWAIT: dòng đang bị khóa thì báo lỗi ngay, không chờ {.cot-loi}

**Bối cảnh.** Có chức năng "chốt đơn" không nên đứng chờ vô hạn nếu ai đó đang giữ dòng. Bạn muốn báo "đơn đang được xử lý, thử lại sau" ngay lập tức.

**Khi nào xảy ra.** Truy vấn khóa kèm hint `jakarta.persistence.lock.timeout = 0`, và dòng đang bị transaction khác khóa.

**Khi nào không xảy ra.** Nếu không có hint, truy vấn chờ vô hạn (mục 17.2.2). Nếu dòng không bị ai khóa, truy vấn thành công như thường.

**Ví dụ.**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
@Query("select p from G11T1_Product p where p.id = :id")
Optional<T1Product> findByIdForUpdateNoWait(@Param("id") Long id);
```

**Kết quả.** SQL có thêm `nowait`. PostgreSQL trả lỗi "could not obtain lock on row" ngay lập tức. Hibernate bọc thành `LockTimeoutException`, Spring dịch tiếp thành `CannotAcquireLockException`, một lớp con của `PessimisticLockingFailureException`.

```sql
select tp1_0.id, tp1_0.name, tp1_0.stock
from g11_t1_baseline.product tp1_0
where tp1_0.id=?
for no key update of tp1_0 nowait
```

**Vì sao.** Giá trị timeout 0 là cách JPA nói "đừng chờ". Hibernate dịch nó thành `nowait` của PostgreSQL. Lỗi đến ngay vì database không xếp transaction này vào hàng chờ.

**Cách tránh.** Ở tầng gọi, bắt lớp cha `PessimisticLockingFailureException` chứ không bắt đúng `CannotAcquireLockException`, vì deadlock (mục 17.6) cũng ném `CannotAcquireLockException` còn timeout có thể ném lớp anh em khác.

**Trong JPA thuần.** `@Lock` và `@QueryHints` là của Spring Data. Với JPA thuần: `em.find(Product.class, id, LockModeType.PESSIMISTIC_WRITE, Map.of("jakarta.persistence.lock.timeout", 0))`, hoặc `query.setLockMode(...)` rồi `query.setHint("jakarta.persistence.lock.timeout", 0)`.

::: {.ghi-nho}
Timeout 0 là `nowait`: không chờ, lỗi ngay. Bắt `PessimisticLockingFailureException` để xử lý cả nhóm lỗi khóa.
:::

::: {.tracuu codes="G11-S08"}
:::

#### 17.4.3 Khóa kèm left join fetch: Hibernate tách thành hai câu {.mo-rong}

**Bối cảnh.** Bạn muốn tải đơn cùng các dòng hàng (`left join fetch`) và khóa đơn. PostgreSQL không cho `FOR UPDATE` trên phía "có thể rỗng" của một outer join, nên bạn chờ một lỗi.

**Khi nào xảy ra.** Truy vấn có `left join fetch` kết hợp `PESSIMISTIC_WRITE`.

**Khi nào không xảy ra.** Truy vấn chỉ có một bảng, không join (mục 17.4.1).

**Ví dụ.**

```java
List<T1PurchaseOrder> orders = inTxReturning(() -> em
    .createQuery("select o from G11T1_PurchaseOrder o "
               + "left join fetch o.items where o.id = :id", T1PurchaseOrder.class)
    .setParameter("id", 1L)
    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
    .getResultList());
```

**Kết quả.** Không có lỗi. Hibernate chạy **hai câu**: câu đầu là `left join` **không khóa** (tải đơn kèm dòng hàng), câu thứ hai khóa riêng bảng gốc.

```sql
select tpo1_0.id, i1_0.order_id, i1_0.id, i1_0.sku, tpo1_0.order_no
from g11_t1_baseline.purchase_order tpo1_0
left join g11_t1_baseline.order_item i1_0 on tpo1_0.id=i1_0.order_id
where tpo1_0.id=?

select tbl.id, tbl.order_no
from g11_t1_baseline.purchase_order tbl
where tbl.id in (?)
for no key update of tbl
```

::: {.khac-du-doan codes="G11-S11"}
**Điều nhiều người nghĩ:** PostgreSQL từ chối `FOR UPDATE` trên phía nullable của outer join, nên truy vấn sẽ lỗi.

**Thực tế:** không có lỗi. Hibernate tách thành hai câu: câu `left join` không khóa, rồi câu riêng chỉ khóa bảng gốc.

Đã kiểm chứng trên Hibernate 7.4.15.Final.
:::

**Vì sao.** Hibernate biết giới hạn của PostgreSQL nên không gắn khóa vào câu có join. Cái giá nằm ở hai chỗ: giữa hai câu có một **khoảng hở**, nên dữ liệu đọc ở câu đầu chưa được khóa lúc đọc; và các **dòng hàng không bị khóa**, chỉ đơn bị khóa.

**Cách tránh.** Nếu cần khóa cả đơn và dòng hàng, khóa đơn trước bằng truy vấn riêng không join, rồi tải dòng hàng sau trong cùng transaction.

::: {.ghi-nho}
Khóa kèm `left join fetch` không lỗi nhưng bị tách đôi: chỉ bảng gốc được khóa, và có khoảng hở giữa hai câu.
:::

::: {.tracuu codes="G11-S11"}
:::

### 17.5 Hàng đợi job với SKIP LOCKED

Bài toán kinh điển: nhiều worker cùng nhặt job từ một bảng. Mỗi job phải được **đúng một** worker xử lý.

![Sơ đồ bảng job, dùng cho thí nghiệm hàng đợi](img/g11-t3.png){width=50%}

#### 17.5.1 Không khóa: hai worker nhận cùng một job {.cot-loi}

**Bối cảnh.** Hai worker cùng chạy `select ... where status = 'NEW' order by id limit 1` rồi đánh dấu DONE. Email được gửi hai lần cho cùng một khách.

**Khi nào xảy ra.** Truy vấn lấy job không khóa, và hai worker đọc trước khi bên nào commit.

**Khi nào không xảy ra.** Khi các worker chạy nối tiếp, hoặc dùng khóa (mục 17.5.2).

**Ví dụ.**

```java
@Query("select j from G11T3_Job j where j.status = 'NEW' order by j.id")
List<T3Job> findNewJobs(Limit limit);
```

Worker một đọc `findNewJobs(Limit.of(1))` và tạm dừng. Worker hai chạy cùng truy vấn, đánh dấu job và commit. Rồi worker một đánh dấu và commit.

**Kết quả.** Cả hai worker nhận **cùng một job**. Trong database chỉ **một** job được đánh dấu DONE, và hai job còn lại vẫn là NEW: một job chạy hai lần, hai job còn lại không ai chạm tới.

| Bảng job | id | status | worker |
|---|---|---|---|
| Sau | 1 | DONE | worker-1 |
| Sau | 2 | **NEW** | NULL |
| Sau | 3 | **NEW** | NULL |

**Vì sao.** Cả hai worker đọc cùng một thời điểm khi job đầu tiên còn là NEW, nên cùng chọn nó. Không có gì cho worker thứ hai biết job đó "đã có chủ". Đây là một dạng lost update ở cấp hàng đợi.

::: {.ghi-nho}
Lấy job không khóa thì hai worker cùng chọn job đầu tiên. Job chạy hai lần, các job khác bị bỏ đói.
:::

::: {.tracuu codes="G11-S09b"}
:::

#### 17.5.2 SKIP LOCKED: mỗi worker nhận một job khác nhau {.cot-loi}

**Bối cảnh.** Bạn muốn worker thứ hai **bỏ qua** job đang bị worker thứ nhất giữ và nhận job kế tiếp, thay vì chờ hay nhận trùng.

**Khi nào xảy ra.** Truy vấn khóa ghi kèm hint timeout `-2`, một giá trị đặc biệt của Hibernate nghĩa là `SKIP LOCKED`.

**Khi nào không xảy ra.** Không có hint thì worker thứ hai chờ worker thứ nhất (mục 17.2.2).

**Ví dụ.**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
@Query("select j from G11T3_Job j where j.status = 'NEW' order by j.id")
List<T3Job> findNewJobsSkipLocked(Limit limit);
```

Worker một gọi `findNewJobsSkipLocked(Limit.of(1))`, đánh dấu job và giữ transaction mở. Worker hai chạy cùng truy vấn.

**Kết quả.** Worker hai bỏ qua job đang bị khóa và nhận job kế tiếp. Hai job khác nhau cùng DONE, job thứ ba vẫn NEW.

```sql
select tj1_0.id, tj1_0.payload, tj1_0.status, tj1_0.worker
from g11_t3_job.job tj1_0
where tj1_0.status='NEW'
order by tj1_0.id
fetch first ? rows only
for no key update of tj1_0 skip locked
```

| Bảng job | id | status | worker |
|---|---|---|---|
| Sau | 1 | DONE | worker-1 |
| Sau | 2 | DONE | worker-2 |
| Sau | 3 | NEW | NULL |

**Vì sao.** `skip locked` bảo PostgreSQL: "những dòng đang bị khóa thì coi như không tồn tại với truy vấn này". Mỗi worker nhận đúng dòng chưa ai giữ, và không ai phải chờ ai. Đây là mẫu hàng đợi việc chuẩn trên PostgreSQL.

**Trong JPA thuần.** Giá trị `-2` của hint `jakarta.persistence.lock.timeout` là **quy ước riêng của Hibernate** (JPA chỉ định nghĩa 0 và số dương). JPA thuần không có cách nói `SKIP LOCKED`; với provider khác bạn phải dùng native query: `select ... for update skip locked`.

::: {.ghi-nho}
`SKIP LOCKED` cho mỗi worker một job khác nhau, không chờ nhau. Với Hibernate, bật nó bằng hint timeout `-2`.
:::

::: {.tracuu codes="G11-S09a"}
:::

### 17.6 Deadlock và cách khóa theo thứ tự

Khóa bi quan giải quyết lost update nhưng mở ra một rủi ro mới: **deadlock**. Hai transaction mỗi bên giữ một khóa và chờ khóa của bên kia, không ai chạy tiếp được.

#### 17.6.1 Khóa ngược thứ tự: PostgreSQL hủy một transaction {.cot-loi}

**Bối cảnh.** Chuyển hàng giữa hai kho: transaction thứ nhất khóa kho 1 rồi xin kho 2; transaction thứ hai (chuyển ngược chiều) khóa kho 2 rồi xin kho 1.

**Khi nào xảy ra.** Hai transaction khóa hai dòng theo thứ tự ngược nhau.

**Khi nào không xảy ra.** Nếu cả hai khóa cùng thứ tự (mục 17.6.2), hoặc nếu một bên chỉ cần một dòng.

**Ví dụ.**

```java
Running<Void> tx1 = concurrent.start(() -> {
    products.findByIdForUpdate(1L);
    tx1Locked.open();
    tx2Locked.await();                  // chắc chắn tx2 đã giữ dòng 2
    products.findByIdForUpdate(2L);     // chờ tx2: vòng chờ
});
Running<Void> tx2 = concurrent.start(() -> {
    products.findByIdForUpdate(2L);
    tx2Locked.open();
    tx1Locked.await();                  // chắc chắn tx1 đã giữ dòng 1
    products.findByIdForUpdate(1L);     // chờ tx1: vòng chờ
});
```

**Kết quả.** Sau khoảng chờ `deadlock_timeout` (mặc định 1 giây), PostgreSQL phát hiện vòng chờ và **hủy đúng một** transaction với lỗi "deadlock detected". Spring dịch thành `CannotAcquireLockException`. Bên còn lại chạy xong bình thường.

```text
org.springframework.dao.CannotAcquireLockException: JDBC exception executing SQL
[ERROR: deadlock detected
Detail: Process 68 waits for ShareLock on transaction 2049; blocked by process 69.
```

Không thể biết trước bên nào bị hủy: lần chạy này là transaction thứ hai, nhưng lần khác có thể là transaction thứ nhất. Vì vậy test chỉ khẳng định "đúng một bên thất bại".

**Vì sao.** Mỗi bên chờ khóa của bên kia nên không bên nào nhả khóa của mình. PostgreSQL định kỳ kiểm tra vòng chờ; khi thấy, nó chọn một nạn nhân, rollback transaction đó để bên kia tiếp tục.

**Cách tránh.** Code gọi phải sẵn sàng retry khi nhận `CannotAcquireLockException` do deadlock. Nhưng tốt hơn là tránh deadlock từ gốc (mục 17.6.2).

::: {.ghi-nho}
Khóa ngược thứ tự gây deadlock. PostgreSQL hủy đúng một transaction, và bạn không chọn được bên nào.
:::

::: {.tracuu codes="G11-S10a"}
:::

#### 17.6.2 Luôn khóa theo thứ tự id tăng dần: không còn deadlock {.cot-loi}

**Bối cảnh.** Nghiệp vụ buộc transaction thứ nhất cần dòng 1 rồi 2, transaction thứ hai cần dòng 2 rồi 1. Bạn không đổi được nghiệp vụ, nhưng đổi được **thứ tự khóa** trong code.

**Khi nào xảy ra.** Mọi transaction cần nhiều khóa đều khóa theo cùng một thứ tự toàn cục (ví dụ id tăng dần).

**Khi nào không xảy ra.** Nếu chỉ một số transaction tuân theo thứ tự (cần tất cả cùng tuân).

**Ví dụ.**

```java
/** Sắp xếp id tăng dần rồi mới khóa: cùng thứ tự thì không thể có vòng chờ. */
private void lockInAscendingIdOrder(long firstWanted, long secondWanted) {
    long low = Math.min(firstWanted, secondWanted);
    long high = Math.max(firstWanted, secondWanted);
    products.findByIdForUpdate(low);
    products.findByIdForUpdate(high);
}
```

**Kết quả.** Cả hai transaction đều khóa dòng 1 trước. Transaction thứ hai chỉ **chờ** transaction thứ nhất xong (test xác nhận có session đang chờ khóa), rồi chạy tiếp. Không ai bị hủy, không có exception.

**Vì sao.** Deadlock cần một **vòng chờ**: A chờ B và B chờ A. Nếu mọi người xin khóa theo cùng một thứ tự, thì người xin khóa lớn hơn luôn đã có khóa nhỏ hơn, nên không thể có vòng. Người đến sau chỉ phải xếp hàng sau người đến trước, một hàng đợi thẳng chứ không phải một vòng tròn.

**Cách tránh.** Biến quy tắc thành một hàm dùng chung (như `lockInAscendingIdOrder` ở trên) để mọi chỗ cần khóa nhiều dòng đều đi qua nó, thay vì mỗi chỗ tự khóa theo thứ tự nghiệp vụ.

::: {.ghi-nho}
Muốn tránh deadlock, mọi transaction khóa nhiều dòng phải cùng thứ tự (ví dụ id tăng dần). Không vòng chờ thì không deadlock.
:::

::: {.tracuu codes="G11-S10b"}
:::

### 17.7 Chọn cách bảo vệ nào

| Tình huống | Gợi ý | Vì sao |
|---|---|---|
| Hai người sửa một màn hình (request tách biệt) | `@Version` | Phát hiện ghi đè mà không giữ khóa trong lúc người dùng suy nghĩ |
| Phép tính đơn giản trên một cột (trừ kho, cộng điểm) | UPDATE nguyên tử | Đúng, nhanh, không cần retry |
| Xung đột thường xuyên trên cùng dòng, cần đúng ngay lần đầu | `PESSIMISTIC_WRITE` | Người sau chờ rồi đọc số liệu mới |
| Không muốn chờ vô hạn | `PESSIMISTIC_WRITE` kèm timeout 0 | Lỗi ngay, báo người dùng thử lại |
| Nhiều worker nhặt việc từ bảng | `SKIP LOCKED` | Mỗi worker một việc, không chờ nhau |
| Một transaction cần khóa nhiều dòng | Khóa theo thứ tự id | Không có vòng chờ nên không deadlock |

Một quy tắc chung: **khóa lạc quan khi xung đột hiếm, khóa bi quan khi xung đột thường xuyên.** Và với mọi cách, viết một test chạy hai transaction đan xen thật sự (như các test của lab) để chứng minh nó hoạt động, vì lỗi đồng thời không bao giờ lộ ra khi bạn chỉ chạy từng transaction một.
