## Chương 20. Bytecode enhancement

Suốt các chương trước, có hai điều Hibernate "không làm được" dù bạn đã viết đúng annotation:

- `@OneToOne(mappedBy = ..., fetch = LAZY)` ở phía inverse vẫn tải ngay (Chương 7).
- `@Basic(fetch = LAZY)` trên một cột lớn vẫn tải cùng các cột khác.

Lý do chung: Hibernate chỉ có hai công cụ trì hoãn việc tải, là proxy (một class con sinh ra lúc chạy, thay cho entity) và collection đặc biệt. Proxy không đặt được vào chỗ một field `String` hay vào chỗ mà Hibernate không biết có hay không có dòng liên quan (phía inverse của `@OneToOne`). Vì vậy `LAZY` chỉ là **chỉ dẫn**, không phải mệnh lệnh.

**Bytecode enhancement** là công cụ thứ ba. Thay vì sinh class phụ lúc chạy, Hibernate **sửa trực tiếp file `.class` của entity ngay sau khi biên dịch**, chèn thêm code chặn lúc đọc field. Khi bạn gọi `getBody()` hay `getLoyaltyCard()`, code chèn thêm đó mới đi SELECT. Chương này trả lời ba câu hỏi:

1. Có enhancement thì hai điều "không làm được" ở trên có làm được không? (mục 20.2 và 20.3)
2. Enhancement có làm câu UPDATE gọn hơn không? (mục 20.4)
3. Cái giá phải trả là gì, và vì sao lab phải tách riêng nó? (mục 20.1)

**Trong JPA thuần.** Enhancement là tính năng của Hibernate, JPA không có. `@Basic(fetch = LAZY)` và `@OneToOne(fetch = LAZY)` là annotation JPA chuẩn, nhưng JPA ghi rõ `LAZY` chỉ là gợi ý cho provider, nên không bảo đảm hành vi. Chương này cho thấy điều đó bằng thí nghiệm.

### 20.1 Vì sao enhancement nằm ở subproject riêng

Plugin Gradle `org.hibernate.orm` sửa file `.class` của **mọi** entity trong source set `main` của project được khai plugin. Nếu bật nó ở project gốc của lab, mọi entity của các chương trước cũng bị sửa bytecode, và nhiều kết quả đã đo sẽ đổi âm thầm (ví dụ, `LAZY` ở phía inverse của `@OneToOne` bỗng chạy được, làm sai kết luận ở Chương 7).

Vì vậy lab tách enhancement ra một subproject tên `lab-enhanced/`:

| Thứ | Nằm ở đâu | Ghi chú |
|---|---|---|
| Plugin `org.hibernate.orm` | Chỉ trong `lab-enhanced/build.gradle` | Cùng version với Hibernate mà Spring Boot quản lý |
| Entity **đã enhance** | `lab-enhanced/src/main` | Plugin chỉ áp cho source set `main` |
| Entity **đối chứng** (không enhance) | `lab-enhanced/src/test` | So "có" và "không" enhancement trong cùng một class test |
| Rào chắn "project gốc không bị enhance" | Một test ở project gốc | Đỏ nếu ai lỡ bật plugin ở gốc |

Cấu hình của subproject có ba công tắc:

```groovy
hibernate {
    enhancement {
        // cho phép LAZY ở mức thuộc tính và to-one inverse không cần proxy
        enableLazyInitialization = true
        // entity tự ghi nhận field nào đã đổi
        enableDirtyTracking = true
        // tự đồng bộ hai chiều của quan hệ: cố ý TẮT để không che mapping
        enableAssociationManagement = false
    }
}
```

Chạy `./gradlew test` sẽ chạy test của cả hai project; `./gradlew :lab-enhanced:test` chỉ chạy subproject.

![Sơ đồ bảng customer và loyalty_card của subproject enhancement](img/g13-e1.png){width=60%}

#### 20.1.1 Kiểm tra enhancement bằng chính file .class {.cot-loi}

**Bối cảnh.** Bạn đã thêm plugin vào `build.gradle` và tin rằng entity đã được enhance. Nhưng cấu hình Gradle có thể sai (sai source set, plugin không chạy) mà không báo lỗi gì. Làm sao biết chắc, thay vì tin cấu hình?

**Khi nào xảy ra.** Bạn muốn xác nhận một entity đã bị enhance (hoặc chưa bị).

**Khi nào không xảy ra.** Bạn chỉ cần so kết quả (ví dụ SQL) giữa bản có và bản không enhancement, không cần kiểm tra class.

**Ví dụ.** Plugin chèn các interface vào class của entity lúc enhance (`PersistentAttributeInterceptable` khi bật lazy, `SelfDirtinessTracker` khi bật dirty tracking), nên chỉ cần hỏi class có chúng không:

```java
// entity ở lab-enhanced/src/main: đã enhance
assertThat(E1Customer.class)
    .isAssignableTo(PersistentAttributeInterceptable.class);

// entity đối chứng ở lab-enhanced/src/test: plugin không đụng tới
assertThat(PersistentAttributeInterceptable.class
    .isAssignableFrom(C2PlainDocument.class)).isFalse();

// rào chắn ở project gốc: entity của chương trước không bị enhance
assertThat(PersistentAttributeInterceptable.class
    .isAssignableFrom(V3Customer.class)).isFalse();
assertThat(SelfDirtinessTracker.class
    .isAssignableFrom(V3Customer.class)).isFalse();
```

**Kết quả.** Class của entity ở `lab-enhanced/src/main` **có** interface `PersistentAttributeInterceptable`. Entity đối chứng ở `src/test` thì **không**. Entity ở project gốc không có cả `PersistentAttributeInterceptable` lẫn `SelfDirtinessTracker`. Một chi tiết đáng chú ý: `getClass()` của entity đã enhance **vẫn là chính class entity**, không phải một class con như khi dùng proxy.

| Class | Có bị enhance không |
|---|---|
| Entity trong `lab-enhanced/src/main` | **Có** |
| Entity đối chứng trong `lab-enhanced/src/test` | Không |
| Entity trong project gốc | Không |

Thay đổi: không có dữ liệu nào đổi, đây là kiểm tra cấu trúc. Giữ nguyên: mọi thứ.

**Vì sao.** Enhancement thật sự sửa file `.class`: nó thêm interface, thêm field và chèn code vào getter. Vì vậy cách kiểm tra đáng tin nhất là nhìn vào chính class đã biên dịch. Khác với proxy (một class con sinh lúc chạy, `getClass()` trả về class con), enhancement sửa class gốc nên không có class phụ. Rào chắn ở project gốc cũng là một test không cần Spring hay database: nó chỉ đọc class, và sẽ đỏ ngay nếu ai đó lỡ khai plugin ở project gốc.

::: {.ghi-nho}
Muốn biết entity có bị enhance không, hãy kiểm tra class có `PersistentAttributeInterceptable` không. Đừng tin riêng cấu hình Gradle.
:::

::: {.tracuu codes="G13-S11c,G13-S11d"}
:::

### 20.2 LAZY ở phía inverse của OneToOne

Ở phía inverse của `@OneToOne`, bảng của entity không có cột nào cho quan hệ (khóa ngoại nằm ở bảng kia), nên Hibernate không biết có hay không có entity liên quan nếu không hỏi database. Không đặt được proxy (vì không biết trả về proxy hay `null`), nên nó tải luôn. Enhancement giải quyết bằng cách để trống field và chỉ tải khi bạn đọc.

#### 20.2.1 find customer không tải loyalty_card, chạm getLoyaltyCard thì mới tải {.cot-loi}

**Bối cảnh.** Mỗi khách có thể có một thẻ thành viên. Trang danh sách khách chỉ cần tên, nhưng mỗi lần tải khách, Hibernate kéo theo cả thẻ (và với 100 khách là 100 truy vấn thẻ, xem N+1 ở Chương 11). Thiết kế lại schema thì không được vì bảng đang chạy. Bạn muốn thử enhancement.

**Khi nào xảy ra.** Entity đã enhance với `enableLazyInitialization`, quan hệ khai `@OneToOne(mappedBy = ..., fetch = LAZY)`.

**Khi nào không xảy ra.** Khi entity không được enhance: `LAZY` bị bỏ qua và thẻ được tải ngay (Chương 7).

**Ví dụ.**

```java
@Entity(name = "G13E1_Customer")
@Table(schema = "g13_e1_inverse_one_to_one", name = "customer")
public class E1Customer {
    @Id
    private Long id;
    private String name;

    // INVERSE side: FK nằm ở loyalty_card.customer_id.
    // Có enhancement thì field này để trống lúc load, getLoyaltyCard()
    // lần đầu mới đi SELECT.
    @OneToOne(mappedBy = "customer", fetch = FetchType.LAZY)
    private E1LoyaltyCard loyaltyCard;
}
```

```java
inTx(() -> {
    E1Customer customerAn = em.find(E1Customer.class, customerId);
    // chưa có SELECT loyalty_card nào
    customerAn.getLoyaltyCard().getCardNo(); // lúc này mới SELECT
});
```

**Kết quả.** `find` chỉ có **một câu SELECT** vào bảng customer, không đụng bảng loyalty_card, và `Hibernate.isPropertyInitialized(customerAn, "loyaltyCard")` trả `false`. Lần đầu gọi `getLoyaltyCard()` có câu SELECT thứ hai (join sang loyalty_card), và trả đúng thẻ.

```sql
select ec1_0.id, ec1_0.name
from g13_e1_inverse_one_to_one.customer ec1_0
where ec1_0.id=?
```

```sql
select lc1_0.id, lc1_0.card_no, lc1_0.customer_id
from g13_e1_inverse_one_to_one.customer ec1_0
left join g13_e1_inverse_one_to_one.loyalty_card lc1_0
    on ec1_0.id=lc1_0.customer_id
where ec1_0.id=?
```

| Bước | Số câu SQL cộng dồn | loyalty_card được tải chưa |
|---|---|---|
| `find` customer | 1 | Chưa |
| `getLoyaltyCard()` lần đầu | 2 | Rồi |

Thay đổi: không có dòng nào đổi. Giữ nguyên: dữ liệu.

**Vì sao.** Plugin chèn code chặn vào chính field `loyaltyCard` của class entity. Lúc `find`, Hibernate tải các cột của customer và **bỏ trống** field này, rồi đánh dấu nó chưa khởi tạo. Khi code gọi `getLoyaltyCard()`, đoạn code chèn thêm thấy field chưa khởi tạo, gọi Hibernate đi SELECT, và trả kết quả. Đây là điều mà proxy không làm được ở phía inverse, và nó khác hẳn kết quả ở Chương 7, nơi `find` phải tải thẻ ngay.

**Trong JPA thuần.** `@OneToOne(fetch = LAZY)` là JPA chuẩn, nhưng JPA cho phép provider bỏ qua. Việc nó chạy được ở đây hoàn toàn nhờ enhancement của Hibernate.

::: {.ghi-nho}
Có enhancement thì `find` không tải thẻ ở phía inverse, và chạm vào `getLoyaltyCard()` mới tải. Không có enhancement thì `LAZY` ở đây vô tác dụng.
:::

::: {.tracuu codes="G13-S11a"}
:::

#### 20.2.2 Khách không có thẻ: vẫn tốn một SELECT để biết là không có {.cot-loi}

**Bối cảnh.** Phần lớn khách chưa có thẻ. Bạn mong rằng với khách như vậy, enhancement giúp tiết kiệm hoàn toàn. Nhưng khi code kiểm tra `if (customer.getLoyaltyCard() != null)`, vẫn có câu SELECT.

**Khi nào xảy ra.** Entity đã enhance, khách không có dòng loyalty_card nào, và code chạm vào `getLoyaltyCard()`.

**Khi nào không xảy ra.** Khi code không bao giờ chạm vào `getLoyaltyCard()` (khi đó cả hai trường hợp có thẻ và không có thẻ đều không tốn SELECT thứ hai).

**Ví dụ.**

```java
inTx(() -> {
    E1Customer customerBinh = em.find(E1Customer.class, 2L); // 1 SELECT
    customerBinh.getLoyaltyCard(); // SELECT thứ hai, kết quả là null
});
```

**Kết quả.** `find` có một câu SELECT. Gọi `getLoyaltyCard()` có thêm câu SELECT thứ hai (cùng câu join như mục 20.2.1) trả về không dòng nào, và kết quả là `null`. Tổng cộng hai câu.

| Bước | Số câu SQL cộng dồn | Kết quả của getLoyaltyCard |
|---|---|---|
| `find` customer | 1 | (chưa gọi) |
| `getLoyaltyCard()` | 2 | **null** |

Thay đổi: không có dòng nào đổi. Giữ nguyên: dữ liệu.

**Vì sao.** Phía inverse không có cột nào để Hibernate biết "có thẻ hay không". Cách duy nhất để biết là hỏi bảng loyalty_card, nên "không có thẻ" cũng là một câu trả lời phải mua bằng một câu SELECT. Enhancement hoãn việc hỏi được, nhưng không xóa được nó khi bạn chạm vào quan hệ.

::: {.ghi-nho}
Với khách không có thẻ, enhancement vẫn tốn một SELECT khi bạn chạm vào quan hệ để biết là `null`. Nó chỉ hoãn chi phí, không xóa chi phí.
:::

::: {.tracuu codes="G13-S11b"}
:::

### 20.3 Basic LAZY cho cột lớn

#### 20.3.1 Cột body lớn: có enhancement thì hoãn, không có thì LAZY bị bỏ qua {.cot-loi}

**Bối cảnh.** Bảng tài liệu có cột `body` chứa hàng chục kilobyte văn bản. Trang danh sách chỉ cần tiêu đề, nhưng mỗi lần tải tài liệu, cả `body` bị kéo qua mạng. Bạn thêm `@Basic(fetch = LAZY)` lên cột này. Không có gì thay đổi, và không có cảnh báo nào.

**Khi nào xảy ra.** Entity khai `@Basic(fetch = LAZY)` trên một cột và **không** được enhance (khi đó annotation bị bỏ qua), hoặc **có** enhance (khi đó cột bị hoãn).

**Khi nào không xảy ra.** Ngược lại: bạn khai `@Basic(fetch = LAZY)` mà quên rằng nó cần enhancement.

**Ví dụ.** Hai entity có mapping y hệt nhau, khác duy nhất ở chỗ entity thứ nhất được enhance, entity thứ hai (đối chứng) thì không:

```java
@Entity(name = "G13E2_Document")
@Table(schema = "g13_e2_lazy_basic", name = "document")
public class E2Document {
    @Id
    private Long id;
    private String title;

    // Chỉ dẫn, không phải mệnh lệnh: cần enhancement thì mới hoãn được
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "body", columnDefinition = "text")
    private String body;
}
```

```java
inTx(() -> {
    E2Document document = em.find(E2Document.class, 1L);
    document.getBody(); // có enhancement: đến đây mới SELECT body
});
```

**Kết quả.** Với entity **có enhancement**, `find` chỉ SELECT `id` và `title`; `getBody()` mới có câu SELECT riêng chỉ lấy cột `body`.

```sql
select ed1_0.id, ed1_0.title
from g13_e2_lazy_basic.document ed1_0
where ed1_0.id=?
```

```sql
select ed1_0.body
from g13_e2_lazy_basic.document ed1_0
where ed1_0.id=?
```

Với entity **không enhancement** (cùng mapping), `body` nằm ngay trong câu SELECT đầu, `isPropertyInitialized` trả `true`, và đọc `body` không phát sinh SQL nào thêm. Hibernate **không báo lỗi hay cảnh báo gì**.

```sql
select cpd1_0.id, cpd1_0.body, cpd1_0.title
from g13_c2_plain_basic.document cpd1_0
where cpd1_0.id=?
```

| | Có enhancement | Không enhancement |
|---|---|---|
| Cột `body` trong SELECT đầu | **Không** | **Có** |
| Số câu SQL sau khi đọc `body` | 2 | 1 |

Thay đổi: không có dòng nào đổi. Giữ nguyên: dữ liệu `body` đọc ra giống nhau ở cả hai.

**Vì sao.** Hibernate cần một chỗ để chèn "chốt chặn" khi bạn đọc `body`. Với field `String`, không có proxy nào đặt vào được (`String` là final). Enhancement chính là chỗ chèn đó: nó sửa getter để kiểm tra và tải khi cần. Không có enhancement thì không có chỗ chèn, nên Hibernate lặng lẽ bỏ qua `LAZY` và tải cột như mọi cột khác. Đây là cái bẫy: annotation trông như có tác dụng, code review thấy `LAZY` và yên tâm, nhưng nó chỉ có tác dụng khi bật enhancement.

Nếu không muốn bật enhancement, các cách khác để tránh kéo cột lớn là tách cột ra một entity hoặc bảng riêng, hoặc chọn riêng các cột cần dùng bằng projection (Chương 12).

**Trong JPA thuần.** `@Basic(fetch = LAZY)` là JPA chuẩn, nhưng spec cho phép provider bỏ qua. Hibernate bỏ qua nếu không có enhancement.

::: {.ghi-nho}
`@Basic(fetch = LAZY)` chỉ có tác dụng khi bật bytecode enhancement. Không có enhancement thì Hibernate bỏ qua mà không báo gì.
:::

::: {.tracuu codes="G13-S12a,G13-S12b"}
:::

### 20.4 Dirty tracking

Bình thường (Chương 2), Hibernate phát hiện entity đã sửa bằng **dirty checking**: lúc flush, nó so từng field của entity với bản chụp lúc tải. Enhancement có thể thay bằng **dirty tracking**: entity tự ghi nhận field nào vừa bị gán, nên Hibernate khỏi cần so từng field. Câu hỏi tự nhiên: vì entity đã biết field nào đổi, câu UPDATE có chỉ chứa cột đó không?

#### 20.4.1 Sửa một cột, UPDATE vẫn ghi cả ba cột; chỉ đọc thì không UPDATE {.mo-rong}

**Bối cảnh.** Bảng sản phẩm có ba cột (`name`, `price`, `description`), trong đó `description` khá dài. Bạn bật dirty tracking và kỳ vọng sửa `price` chỉ gửi `price` xuống database.

**Kết quả.** Với entity có dirty tracking, sửa `price` rồi commit gửi một câu UPDATE chứa **cả ba cột**:

```sql
update g13_e3_dirty_tracking.product
set description=?, name=?, price=?
where id=?
```

Entity đối chứng **không** enhance, cùng thao tác, cho **câu UPDATE giống hệt**, cũng ghi cả ba cột. Từ phía SQL, bạn không phân biệt được hai cách phát hiện thay đổi. Và khi entity enhance chỉ được đọc mà không sửa, **không có câu UPDATE nào**.

| Thao tác | Có dirty tracking | Không enhancement |
|---|---|---|
| Sửa `price` | UPDATE ghi `description`, `name`, `price` | UPDATE y hệt |
| Chỉ đọc, không sửa | Không UPDATE | (lab không đo) |

| Bảng product | price | name | description |
|---|---|---|---|
| Trước | 10 | Bàn phím | Cơ |
| Sau | **20** | Bàn phím | Cơ |

Thay đổi: `price` từ 10 thành 20. Giữ nguyên: `name` và `description` (nhưng vẫn nằm trong câu UPDATE, với giá trị cũ).

Dirty tracking chỉ đổi **cách phát hiện** entity có bẩn không (entity tự báo, thay vì Hibernate so với bản chụp). Nó không đổi **cách viết** câu UPDATE. Muốn UPDATE chỉ chứa cột đã đổi, bạn vẫn cần `@DynamicUpdate` (mục 14.6). Lợi ích thật của dirty tracking nằm ở chi phí bên trong khi persistence context chứa rất nhiều entity, không phải ở hình dạng câu SQL.

::: {.ghi-nho}
Dirty tracking không thu hẹp câu UPDATE. Muốn UPDATE chỉ ghi cột đổi thì dùng `@DynamicUpdate`.
:::

::: {.tracuu codes="G13-S13a,G13-S13b,G13-S13c"}
:::

Ba điều cần mang theo từ chương này, trước khi quyết định có bật enhancement trong dự án thật hay không:

1. **`LAZY` là chỉ dẫn, không phải mệnh lệnh.** Thấy `LAZY` trong code không có nghĩa nó đang lazy. Với `@OneToOne` phía inverse và `@Basic`, nó chỉ chạy khi có enhancement.
2. **Enhancement không phải viên đạn bạc.** Nó đổi cách entity được tải và theo dõi, nhưng không đổi hình dạng câu UPDATE, và "không có thẻ" vẫn tốn một SELECT.
3. **Cái giá phải trả.** Thêm một bước build (plugin), bytecode chạy khác với source bạn đọc, và mọi entity trong source set bị áp cùng cấu hình. Hãy bật khi đã **đo** được vấn đề (cột lớn, `@OneToOne` inverse hay gây N+1), không bật mặc định. Với `@OneToOne` inverse, thường nên đổi thiết kế trước (đặt khóa ngoại ở phía khác, hoặc dùng `@MapsId` như Chương 7), và với cột lớn thì tách cột hoặc dùng projection, rồi mới nghĩ đến enhancement.
