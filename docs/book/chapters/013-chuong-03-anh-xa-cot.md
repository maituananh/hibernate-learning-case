## Chương 3. Ánh xạ cột và kiểu giá trị: Embeddable, Enum, AttributeConverter, NaturalId

Không phải mọi thứ trong entity đều là quan hệ. Một địa chỉ gồm nhiều field, một trạng thái đơn hàng là một enum, một danh sách tag cần lưu vào một cột văn bản, một mã SKU cần là duy nhất: đó là những giá trị mà Hibernate phải ánh xạ ra cột. Chương này đi qua bốn công cụ cho việc đó, và những hành vi bất ngờ của từng cái.

Trong chương này:

- `@Embeddable` và `@AttributeOverride`: gom nhiều cột thành một object (mục 3.1).
- `@Enumerated` và `AttributeConverter`: lưu enum và kiểu tự định nghĩa (mục 3.2).
- `@NaturalId`: khóa nghiệp vụ duy nhất (mục 3.3).

Chương này đặt trước phần quan hệ vì các khái niệm ở đây (cột, kiểu, ràng buộc) đơn giản hơn và được dùng lại ở nhiều chương sau.

### 3.1 Embeddable: gom nhiều cột thành một object {.bien-the}

Một địa chỉ có đường, thành phố, mã bưu điện. Thay vì để các field rời rạc trong `Customer`, ta gom chúng vào một class `Address` đánh dấu `@Embeddable`. Class này **không có bảng riêng**: các cột của nó nằm thẳng trong bảng của entity chứa nó. Vì customer có hai địa chỉ (thanh toán và giao hàng), mỗi lần dùng phải đổi tên cột bằng `@AttributeOverride`:

```java
@Embedded
@AttributeOverrides({
    @AttributeOverride(name = "street",
            column = @Column(name = "billing_street")),
    @AttributeOverride(name = "city",
            column = @Column(name = "billing_city")),
    @AttributeOverride(name = "zipCode",
            column = @Column(name = "billing_zip_code"))
})
private E1Address billingAddress;

// shippingAddress khai báo tương tự, với tiền tố shipping_
```

![Một bảng customer duy nhất chứa sáu cột địa chỉ phẳng: billing_street, billing_city, billing_zip_code và ba cột shipping tương ứng](img/g12-e1.png){width=60%}

**Ưu điểm.** Code Java gọn: `customer.getBillingAddress().getCity()`. Cùng một class `Address` dùng lại ở nhiều nơi. Không cần JOIN, vì dữ liệu nằm cùng một bảng.

**Nhược điểm.** Hibernate không phân biệt được "địa chỉ rỗng" với "không có địa chỉ" (mục 3.1.3). Mỗi lần dùng lại cùng một kiểu trong một entity đều phải khai `@AttributeOverride`.

**Nên dùng khi.** Với nhóm field luôn đi cùng nhau và không có định danh riêng. Nếu thứ đó cần được tham chiếu từ nhiều entity khác nhau, hãy dùng entity thật với quan hệ (Phần II).

#### 3.1.1 Dùng hai lần với AttributeOverride: sáu cột phẳng, không có bảng address {.cot-loi}

**Bối cảnh.** Bạn muốn customer có địa chỉ thanh toán và địa chỉ giao hàng, và có thói quen nghĩ "địa chỉ là một thứ riêng nên phải có bảng address". Với `@Embeddable` thì không như vậy.

**Khi nào xảy ra.** Một class `@Embeddable` dùng hai lần trong cùng entity, mỗi lần có `@AttributeOverride` đổi tên cột: bảng chứa entity có sáu cột phẳng, và **không có** bảng `address`.

**Khi nào không xảy ra.** Nếu bạn dùng `@ManyToOne Address` với `Address` là entity (Chương 4), thì có bảng `address` riêng và khóa ngoại.

**Ví dụ.** Mapping ở đầu mục 3.1, rồi để Hibernate sinh bảng.

**Kết quả.** DDL của bảng `customer`: không có bảng `address`, và không có cột nào tên `street` (vì cả hai lần dùng đều được đổi tên).

```text
bảng customer
  - id bigint PK NOT NULL
  - name varchar(255) NOT NULL
  - billing_street, billing_city, billing_zip_code     varchar(255)
  - shipping_street, shipping_city, shipping_zip_code  varchar(255)
```

**Vì sao.** `@Embeddable` chỉ là cách gom field trong Java. Với database, đó chỉ là các cột nằm trong bảng chủ. `@AttributeOverride` quyết định tên cột cho từng lần dùng.

::: {.ghi-nho}
Embeddable không có bảng riêng: các cột của nó nằm trong bảng của entity chủ.
:::

::: {.tracuu codes="G12-S01a"}
:::

#### 3.1.2 Quên AttributeOverride: Hibernate không khởi động {.cot-loi}

**Bối cảnh.** Bạn khai báo hai field cùng kiểu `Address` nhưng quên `@AttributeOverride` ở một trong hai.

**Khi nào xảy ra.** Hai field cùng ánh xạ vào cùng các cột `street`, `city`, `zip_code`. Hibernate ném lỗi "duplicate column" ngay lúc khởi động, không cần database.

**Khi nào không xảy ra.** Nếu mỗi lần dùng đều có override đổi tên cột (mục 3.1.1), hoặc embeddable chỉ dùng một lần.

**Ví dụ.** Một entity cố ý sai có hai field `Address` mà không override.

**Kết quả.** Hibernate không dựng được `SessionFactory`; thông báo lỗi nói về cột bị khai báo trùng.

**Vì sao.** Hai property cùng ghi một cột thì Hibernate không biết property nào là nguồn, nên chặn ngay từ đầu (giống bẫy map cột hai lần ở Chương 4, mục 4.4.3).

::: {.ghi-nho}
Dùng cùng một embeddable hai lần trong một entity thì bắt buộc phải `@AttributeOverride` ít nhất một lần.
:::

::: {.tracuu codes="G12-S01b"}
:::

#### 3.1.3 Mọi cột của embeddable đều NULL: đọc lại là null, không phải object rỗng {.cot-loi}

**Bối cảnh.** Customer chỉ có địa chỉ thanh toán, không có địa chỉ giao hàng, nên các cột `shipping_*` đều NULL. Khi đọc lại, `customer.getShippingAddress()` trả về gì: một `Address` rỗng hay `null`? Code gọi `customer.getShippingAddress().getCity()` sẽ có thể gặp `NullPointerException` nếu bạn đoán sai.

**Khi nào xảy ra.** Khi **mọi** cột của embeddable đều NULL, Hibernate trả về `null` cho cả object. Chỉ cần **một** cột khác NULL thì object được tạo, và các field còn lại là `null`.

**Khi nào không xảy ra.** Nếu có ít nhất một cột có dữ liệu thì object luôn tồn tại.

**Ví dụ.**

```java
// Customer Alice: có billing, shipping_* đều NULL.
Customer alice = em.find(Customer.class, aliceId);
alice.getBillingAddress();     // object
alice.getShippingAddress();    // null, không phải Address rỗng

// Customer Bob: chỉ có shipping_city = "Hue".
Customer bob = em.find(Customer.class, bobId);
bob.getShippingAddress();              // object
bob.getShippingAddress().getCity();    // "Hue"
bob.getShippingAddress().getStreet();  // null
bob.getBillingAddress();               // null (toàn NULL)
```

**Kết quả.** Với Alice, `getShippingAddress()` là `null`. Với Bob, `getShippingAddress()` là một object có `city` là "Hue" và `street` là `null`.

**Vì sao.** Trong database không có dòng nào cho embeddable, chỉ có các cột. Hibernate suy ra "không có giá trị" từ việc mọi cột đều NULL. Hệ quả: nó không thể phân biệt "địa chỉ rỗng" với "không có địa chỉ".

**Cách tránh.** Luôn kiểm tra `null` trước khi đọc embeddable, hoặc cho getter trả về một đối tượng mặc định.

::: {.ghi-nho}
Mọi cột NULL thì embeddable đọc ra `null`. Chỉ cần một cột có dữ liệu là object tồn tại.
:::

::: {.tracuu codes="G12-S02a,G12-S02b"}
:::

#### 3.1.4 Sửa field của embeddable tại chỗ: có UPDATE {.cot-loi}

**Bối cảnh.** Bạn đổi `customer.getBillingAddress().setCity("Da Nang")` mà không gán lại object. Hibernate có phát hiện không? Ở Chương 9 (khóa ghép) bạn sẽ thấy `@EmbeddedId` hành xử khác: đổi id là bất hợp lệ.

**Khi nào xảy ra.** Embeddable thường là một phần của entity, nên nằm trong dirty checking: sửa field tại chỗ cho UPDATE.

**Khi nào không xảy ra.** `@EmbeddedId` (khóa chính) là bất biến, đổi nó là lỗi.

**Ví dụ.**

```java
inTx(() -> {
    Customer alice = em.find(Customer.class, aliceId);   // MANAGED
    alice.getBillingAddress().setCity("Da Nang");        // sửa tại chỗ
});
```

**Kết quả.** Một SELECT rồi một UPDATE ghi cả các cột của entity (Hibernate mặc định cập nhật mọi cột):

```sql
update g12_e1_embedded.customer
set billing_city=?, billing_street=?, billing_zip_code=?, name=?,
    shipping_city=?, shipping_street=?, shipping_zip_code=?
where id=?
```

Dữ liệu: `billing_city` thành Da Nang, các cột khác giữ nguyên giá trị.

**Vì sao.** Embeddable nằm trong snapshot của entity, nên dirty checking thấy khác và sinh UPDATE.

::: {.ghi-nho}
Sửa field của embeddable tại chỗ vẫn được lưu, giống như sửa field thường của entity.
:::

::: {.tracuu codes="G12-S03"}
:::

### 3.2 Enum và AttributeConverter: lưu kiểu không phải số hay chuỗi {.bien-the}

Hai cách lưu enum, và một cách lưu kiểu tự định nghĩa:

```java
// Lưu số thứ tự của enum (0, 1, ...).
@Enumerated(EnumType.ORDINAL)
@Column(name = "status", nullable = false)
private ReviewStatus status;

// Lưu tên của enum ('NEW', 'PAID').
@Enumerated(EnumType.STRING)
@Column(name = "status", nullable = false)
private ReviewStatus status;

// Kiểu tự định nghĩa: converter đổi qua lại giữa Tags và chuỗi "a,b,c".
@Convert(converter = TagsConverter.class)
@Column(name = "tags")
private Tags tags;
```

![Ba bảng: v1_product có cột tags dạng chuỗi, v1_review_ordinal có status dạng số nhỏ, v1_review_string có status dạng chuỗi](img/g12-v1.png){width=90%}

**Ưu điểm.** `ORDINAL` gọn (smallint). `STRING` đọc được, và an toàn khi đổi thứ tự enum. Converter cho phép lưu bất kỳ kiểu nào vào một cột.

**Nhược điểm.** `ORDINAL` đọc sai **im lặng** nếu ai đó đổi thứ tự khai báo enum (mục 3.2.2). Converter có thể gây bất ngờ về cách phát hiện thay đổi (mục 3.2.1).

**Nên dùng khi.** Dùng `STRING` cho enum lưu lâu dài. Chỉ dùng `ORDINAL` khi bạn chắc thứ tự enum không bao giờ đổi.

#### 3.2.1 Converter cho kiểu mutable: sửa tại chỗ vẫn có UPDATE {.cot-loi}

**Bối cảnh.** Bạn lưu danh sách tag vào một cột văn bản "a,b" bằng `AttributeConverter`. Rồi bạn gọi `product.getTags().add("c")`, sửa tại chỗ chứ không gán object mới. Nhiều tài liệu cảnh báo rằng với kiểu mutable thì Hibernate có thể không phát hiện thay đổi, trừ khi kiểu có `equals` đúng. Lab thử trực tiếp điều đó.

**Khi nào xảy ra.** Ở Hibernate 7.4.15, sửa kiểu mutable tại chỗ vẫn cho UPDATE, dù kiểu **không** có `equals` và cả khi **có** `equals` theo nội dung.

**Khi nào không xảy ra.** Không có trường hợp nào trong thí nghiệm mà thay đổi bị bỏ qua.

**Ví dụ.**

```java
// Converter: Tags <-> "a,b,c"
@Converter
public class TagsConverter implements AttributeConverter<Tags, String> {
    public String convertToDatabaseColumn(Tags tags) {
        return tags == null ? null : String.join(",", tags.values());
    }
    // convertToEntityAttribute: tách chuỗi theo dấu phẩy
}
```

```java
inTx(() -> {
    Product product = em.find(Product.class, productId);   // tags = "a,b"
    product.getTags().add("c");                             // sửa tại chỗ
});
```

**Kết quả.** Một SELECT rồi một UPDATE:

```sql
update g12_v1_converter.v1_product
set tags=?
where id=?
```

Dữ liệu: cột `tags` thành `a,b,c`. Kết quả giống nhau dù kiểu có hay không có `equals`.

::: {.khac-du-doan codes="G12-S11a,G12-S11b"}
**Điều nhiều người nghĩ:** với kiểu mutable, việc phát hiện thay đổi phụ thuộc vào `equals` của kiểu; kiểu không có `equals` thì sửa tại chỗ bị bỏ qua.

**Thực tế:** ở Hibernate 7.4.15, converter phát hiện sửa tại chỗ trong cả hai trường hợp, và `equals` không làm đổi hành vi trong thí nghiệm.

Đã kiểm chứng trên Hibernate 7.4.15.Final.
:::

**Vì sao.** Hibernate so sánh giá trị đã chuyển đổi (chuỗi) với bản chụp lúc nạp, nên thay đổi nội dung bên trong object vẫn dẫn tới chuỗi khác.

**Lưu ý.** Đừng xem đây là một hợp đồng bảo đảm. Để an toàn lâu dài, hãy làm kiểu của bạn bất biến (immutable) hoặc có `equals` theo nội dung.

::: {.ghi-nho}
Converter ở Hibernate 7.4.15 phát hiện sửa tại chỗ, nhưng hãy ưu tiên kiểu bất biến để không phụ thuộc vào chi tiết này.
:::

::: {.tracuu codes="G12-S11a,G12-S11b"}
:::

#### 3.2.2 ORDINAL: đổi thứ tự enum thì dữ liệu cũ đọc sai mà không báo lỗi {.cot-loi}

**Bối cảnh.** Enum trạng thái `ReviewStatus` ban đầu có `NEW, PAID`. Ai đó đổi thứ tự thành `PAID, NEW` cho "dễ nhìn". Database vẫn lưu các số cũ (0 và 1). Không có lỗi nào, nhưng mọi dòng cũ giờ mang ý nghĩa ngược lại. Đây là một lỗi dữ liệu thầm lặng rất đắt.

**Khi nào xảy ra.** Với `@Enumerated(ORDINAL)`, số 1 trước đây là `PAID` (enum cũ), sau khi đổi thứ tự thì cùng số 1 được đọc là `NEW`, không có exception.

**Khi nào không xảy ra.** Với `@Enumerated(STRING)`, database lưu tên nên thứ tự khai báo không ảnh hưởng: 'PAID' luôn đọc ra `PAID`.

**Ví dụ.** Hai enum cùng tên giá trị nhưng khác thứ tự mô phỏng "trước và sau khi đổi thứ tự", mỗi cái ánh xạ vào một bảng riêng, đọc cùng giá trị số 1:

```java
// Trước: enum ReviewStatus { NEW, PAID }        -> số 1 là PAID
// Sau:   enum ReorderedReviewStatus { PAID, NEW } -> cùng số 1 là NEW
```

**Kết quả.**

| Cách lưu | Giá trị trong database | Đọc ra khi enum cũ | Đọc ra khi enum đã đổi thứ tự |
|---|---|---|---|
| ORDINAL | 1 | PAID | NEW (sai, không có lỗi) |
| STRING | 'PAID' | PAID | PAID (đúng) |

**Vì sao.** `ORDINAL` lưu vị trí, nên ý nghĩa của dữ liệu phụ thuộc vào thứ tự khai báo trong code. `STRING` lưu tên, ổn định hơn nhiều.

**Cách tránh.** Dùng `STRING` cho dữ liệu sống lâu. Nếu muốn mã ngắn gọn, dùng converter với mã cố định, không phụ thuộc thứ tự.

::: {.ghi-nho}
Đổi thứ tự enum đang dùng ORDINAL làm dữ liệu cũ đọc sai mà không có lỗi nào. Hãy dùng STRING.
:::

::: {.tracuu codes="G12-S11c"}
:::

#### 3.2.3 DDL của ORDINAL và STRING: có check constraint chặn giá trị lạ {.cot-loi}

**Bối cảnh.** Có thể nghĩ rằng database sẽ cứu bạn khỏi dữ liệu sai. Liệu cột enum có ràng buộc gì không?

**Khi nào xảy ra.** Hibernate 7.4 sinh `check (status between 0 and 1)` cho ORDINAL (kiểu `smallint`) và `check (status in ('NEW','PAID'))` cho STRING (kiểu `varchar`). Số 5 hay chữ 'GHOST' đều bị database từ chối.

**Khi nào không xảy ra.** Check constraint **không** cứu được tình huống 3.2.2: số 1 vẫn là giá trị hợp lệ dù ý nghĩa đã đổi.

**Ví dụ.** Chạy DDL của hai biến thể rồi thử INSERT giá trị ngoài miền.

**Kết quả.**

| Cách lưu | Kiểu cột | Ràng buộc | Số 5 / chữ GHOST |
|---|---|---|---|
| ORDINAL | smallint | `between 0 and 1` | Bị từ chối |
| STRING | varchar | `in ('NEW','PAID')` | Bị từ chối |

**Vì sao.** Hibernate dịch miền giá trị của enum thành check constraint. Nhưng constraint chỉ biết miền giá trị, không biết ý nghĩa của từng giá trị.

::: {.ghi-nho}
Check constraint chặn giá trị lạ, nhưng không bảo vệ bạn khỏi việc đổi thứ tự enum.
:::

::: {.tracuu codes="G12-S11d"}
:::

### 3.3 NaturalId: khóa nghiệp vụ duy nhất {.bien-the}

Nhiều entity có một giá trị nghiệp vụ định danh duy nhất ngoài khóa chính: mã SKU, email, số hợp đồng. `@NaturalId` đánh dấu field đó:

```java
@NaturalId
@Column(name = "sku", nullable = false)
private String sku;
```

![Một bảng product có khóa chính id, cột sku duy nhất do natural id, và cột name](img/g12-n1.png){width=55%}

**Ưu điểm.** Hibernate tự sinh ràng buộc UNIQUE, cho phép tìm theo natural id (`byNaturalId`), và nhớ ánh xạ này trong persistence context để lần tìm thứ hai không tốn SELECT.

**Nhược điểm.** Mặc định natural id bất biến: đổi giá trị sau khi lưu là lỗi.

**Nên dùng khi.** Khi bạn thường xuyên tìm entity theo một giá trị nghiệp vụ duy nhất và giá trị đó không đổi.

#### 3.3.1 byNaturalId: lần đầu có SELECT, lần hai trong cùng persistence context không {.cot-loi}

**Bối cảnh.** Bạn hay tìm sản phẩm theo SKU. Dùng JPQL `where p.sku = :sku` thì lần nào cũng SELECT. `byNaturalId` có thể tiết kiệm được.

**Khi nào xảy ra.** `session.byNaturalId(Product.class).using("sku", sku).load()`: lần đầu một câu SELECT, lần hai trong **cùng persistence context** không có SELECT và trả về cùng instance.

**Khi nào không xảy ra.** Query JPQL `where p.sku = :sku` không được hưởng điều này; mỗi lần gọi đều SELECT.

**Ví dụ.**

```java
inTx(() -> {
    Session session = em.unwrap(Session.class);
    Product first = session.byNaturalId(Product.class)
        .using("sku", "SKU-1").load();    // SELECT
    Product second = session.byNaturalId(Product.class)
        .using("sku", "SKU-1").load();    // không SELECT
    first == second;                       // true
});
```

**Kết quả.** Một câu SELECT duy nhất, lọc theo `sku`:

```sql
select np1_0.id, np1_0.name, np1_0.sku
from g12_n1_natural_id.product np1_0
where np1_0.sku=?
```

**Vì sao.** Persistence context nhớ ánh xạ natural id sang entity, giống cách cache cấp 1 nhớ theo id (Chương 2, mục 2.7).

::: {.ghi-nho}
`byNaturalId` được cache theo natural id trong persistence context; JPQL theo cùng cột thì không.
:::

::: {.tracuu codes="G12-S10a"}
:::

#### 3.3.2 NaturalId sinh ràng buộc UNIQUE {.cot-loi}

**Bối cảnh.** Natural id phải duy nhất. Ai đảm bảo điều đó, Hibernate hay database?

**Khi nào xảy ra.** Hibernate sinh `unique` trên cột `sku`, nên hai sản phẩm trùng SKU bị **database** từ chối.

**Khi nào không xảy ra.** Nếu bạn tự tạo bảng bằng script bỏ qua DDL của Hibernate thì phải tự thêm ràng buộc.

**Ví dụ.** Chạy DDL của bảng `product` có `@NaturalId`.

**Kết quả.** DDL có ràng buộc `unique (sku)`.

**Vì sao.** Natural id là khóa nghiệp vụ duy nhất, nên Hibernate đặt ràng buộc ở tầng bảo vệ cuối cùng là database.

::: {.ghi-nho}
`@NaturalId` đi kèm ràng buộc UNIQUE ở database, không chỉ là kiểm tra trong Java.
:::

::: {.tracuu codes="G12-S10b"}
:::

#### 3.3.3 Đổi natural id của entity đã lưu: exception lúc flush {.cot-loi}

**Bối cảnh.** Mã SKU bị gõ sai và ai đó sửa lại bằng `setSku`. Với `@NaturalId` mặc định, điều này bị cấm.

**Khi nào xảy ra.** Đổi `sku` của entity MANAGED rồi flush: Hibernate ném exception ("immutable natural identifier ... was altered"), transaction rollback, database giữ SKU cũ.

**Khi nào không xảy ra.** Với `@NaturalId(mutable = true)` thì đổi được.

**Ví dụ.**

```java
inTx(() -> {
    Product product = em.find(Product.class, productId);   // sku = "SKU-1"
    product.setSku("SKU-9");                               // đổi natural id
    // Lúc flush: exception, rollback
});
```

**Kết quả.** Exception (`JpaSystemException` bọc lỗi "immutable natural identifier ... was altered"). Bảng giữ nguyên `sku` là SKU-1.

**Vì sao.** Mặc định Hibernate coi natural id là bất biến để cache của nó luôn đúng.

**Cách tránh.** Nếu nghiệp vụ cho phép đổi mã, khai báo `@NaturalId(mutable = true)`.

::: {.ghi-nho}
Natural id mặc định bất biến. Muốn cho đổi, khai báo `mutable = true`.
:::

::: {.tracuu codes="G12-S10c"}
:::
