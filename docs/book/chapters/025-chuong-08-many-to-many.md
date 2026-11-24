## Chương 8. Quan hệ nhiều-nhiều: ManyToMany và entity trung gian

Quan hệ nhiều-nhiều là loại quan hệ dễ khai báo nhất (một annotation) nhưng cũng dễ gây hại nhất khi chạy thật. Một sản phẩm có nhiều nhãn (tag), một nhãn gắn cho nhiều sản phẩm. Database không có cột nào biểu diễn được điều đó, nên cần một **bảng nối** (join table) chứa các cặp khóa ngoại.

Điều quan trọng nhất của chương này: `@ManyToMany` thực chất là **hai `@OneToMany` dính vào cùng một bảng nối**. Mọi quy tắc của owning side và inverse side ở Chương 4 vẫn đúng, chỉ khác là khóa ngoại không nằm ở bảng nào của hai entity mà nằm ở bảng nối.

Trong chương này:

- Phía nào ghi bảng nối, và khai báo `@JoinTable` hay để mặc định (mục 8.1).
- Quên `mappedBy` khiến Hibernate sinh hai bảng nối (mục 8.2).
- `Set`, `List` và `@OrderColumn` khác nhau ra sao khi bỏ một phần tử (mục 8.3).
- Xóa product, xóa tag và vì sao không nên cascade REMOVE (mục 8.4).
- Khi bảng nối cần thêm cột: biến bảng nối thành entity (mục 8.5).
- Khi nào nên bỏ `@ManyToMany` (mục 8.6).

Chương này dùng hai mô hình:

- `Product` nhiều-nhiều `Tag` (nhãn). Bảng nối `product_tag` chỉ có hai cột khóa ngoại.
- `Product` và `Warehouse` (kho) với entity trung gian `StockItem` (tồn kho), có thêm cột `quantity`.

**Trong JPA thuần.** Mọi annotation trong chương (`@ManyToMany`, `@JoinTable`, `@OrderColumn`, `@EmbeddedId`, `@MapsId`) là JPA chuẩn. Khái niệm "bag" ở mục 8.3 là thuật ngữ của Hibernate cho `List` không có thứ tự; JPA đặc tả chỉ nói "list không có `@OrderColumn` là không có thứ tự bền vững", còn việc ghi lại cả bảng nối là cách Hibernate cài đặt.

### 8.1 Owner của bảng nối {.bien-the}

Baseline của chương: `Product.tags` là **owning side** (có `@JoinTable`), `Tag.products` là **inverse side** (có `mappedBy`).

```java
@Entity(name = "G7M1_Product")
public class M1Product {

    // OWNING side: @JoinTable nằm ở đây, nên chỉ product.getTags()
    // mới ghi bảng nối product_tag.
    @ManyToMany(fetch = FetchType.LAZY, cascade = {})
    @JoinTable(schema = "g7_m1_owner_product", name = "product_tag",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<M1Tag> tags = new HashSet<>();
}

@Entity(name = "G7M1_Tag")
public class M1Tag {

    // INVERSE side: thêm product vào đây KHÔNG ghi gì xuống database.
    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY, cascade = {})
    private Set<M1Product> products = new HashSet<>();
}
```

Cấu hình `cascade = {}` được ghi tường minh: product và tag là hai thực thể độc lập, persist hay remove product không lan sang tag.

![Ba bảng product, tag và product_tag; product_tag có hai khóa ngoại product_id và tag_id, khóa chính ghép trên hai cột đó](img/g7-m1.png){width=85%}

**Ưu điểm.** Khai báo ngắn: hai annotation là đủ, không cần entity cho bảng nối. Bảng nối có khóa chính ghép nên database tự chặn cặp trùng.

**Nhược điểm.** Bảng nối chỉ có đúng hai cột khóa ngoại, không chỗ cho cột phụ. Chỉ **một** phía ghi được, phía còn lại gán sai là mất dữ liệu im lặng. Muốn đổi owner phải sửa mapping.

**Nên dùng khi.** Bảng nối thật sự chỉ là cặp khóa ngoại và không bao giờ cần thêm thông tin. Chọn owner là phía mà code hay thay đổi liên kết nhất, thường là phía "aggregate" (ở đây là Product).

#### 8.1.1 DDL của bảng nối với JoinTable tường minh {.cot-loi}

**Bối cảnh.** Bạn thêm `@ManyToMany` và muốn biết database nhận được gì: bao nhiêu bảng, cột nào, khóa chính nào.

**Khi nào xảy ra.** `Product.tags` là `Set` có `@JoinTable`, và `Tag.products` có `mappedBy`.

**Khi nào không xảy ra.** Nếu `tags` là `List` không có `@OrderColumn`, bảng nối không có khóa chính (mục 8.3).

**Ví dụ.** DDL do Hibernate sinh ra:

::: {.ddl schema="g7_m1_owner_product" tables="product,product_tag,tag"}
:::

**Kết quả.** Ba bảng: `product`, `tag` và đúng **một** bảng nối `product_tag`, dù quan hệ khai báo ở hai chiều. Bảng nối có hai khóa ngoại (`product_id` sang `product`, `tag_id` sang `tag`) và khóa chính ghép `(product_id, tag_id)`.

**Vì sao.** Hai phía của quan hệ là hai cách nhìn vào cùng một bảng nối. `mappedBy` nói "tôi không có bảng nối riêng, hãy dùng bảng nối của phía kia". `Set` không cho phép phần tử trùng nên Hibernate đặt khóa chính ghép lên hai cột khóa ngoại, và database cũng chặn cặp trùng.

::: {.ghi-nho}
Một quan hệ nhiều-nhiều hai chiều chỉ có một bảng nối, và chỉ phía không có `mappedBy` ghi vào đó.
:::

::: {.tracuu codes="G7-S01a"}
:::

#### 8.1.2 Không khai báo JoinTable: bảng nối rơi vào schema public {.cot-loi}

**Bối cảnh.** Bạn bỏ `@JoinTable` cho gọn vì "Hibernate tự biết". Sau đó DBA báo một bảng lạ xuất hiện ở schema `public`, còn schema của ứng dụng thì thiếu bảng nối.

**Khi nào xảy ra.** `@ManyToMany` không có `@JoinTable`, và các entity nằm trong schema riêng (không phải `public`).

**Khi nào không xảy ra.** Nếu entity dùng schema mặc định của database, hoặc bạn khai `@JoinTable(schema = ...)` tường minh.

**Ví dụ.**

```java
// Không có @JoinTable: mọi thứ để mặc định
@ManyToMany(fetch = FetchType.LAZY, cascade = {})
private Set<M2Tag> tags = new HashSet<>();
```

**Kết quả.** Schema của biến thể chỉ có hai bảng `product` và `tag`. Bảng nối được tạo ở schema `public`, tên ghép từ tên bảng của owner và tên field (`product` cộng `tags`), và tên cột lấy theo **tên field**:

```sql
create table product_tags (
    products_id bigint not null,
    tags_id bigint not null,
    primary key (products_id, tags_id)
);
```

Khóa ngoại vẫn trỏ đúng về bảng `product` và `tag` trong schema của biến thể.

**Vì sao.** Tên bảng, tên cột và schema mặc định do naming strategy quyết định. `@Table(schema = ...)` trên entity không lan sang bảng nối, vì bảng nối không thuộc entity nào. Kết quả: tên cột là `products_id` và `tags_id` thay vì `product_id` và `tag_id`, và bảng nằm sai chỗ.

**Cách tránh.** Luôn ghi tường minh `@JoinTable` với `schema`, `name`, `joinColumns` và `inverseJoinColumns`.

::: {.ghi-nho}
Đừng để Hibernate đặt tên bảng nối. Mặc định phụ thuộc naming strategy và bảng có thể rơi ra ngoài schema của ứng dụng.
:::

::: {.tracuu codes="G7-S01b"}
:::

#### 8.1.3 Thêm ở owning side ghi bảng nối, thêm ở inverse side thì không {.cot-loi}

**Bối cảnh.** Trang quản trị cho phép gắn tag vào sản phẩm (từ màn hình sản phẩm) và gắn sản phẩm vào tag (từ màn hình tag). Lập trình viên viết cả hai bằng cách `add` vào collection tương ứng. Một trong hai màn hình hoạt động, màn hình kia im lặng không lưu gì.

**Khi nào xảy ra.** `Product.tags` là owning side. Thêm tag vào `product.getTags()` thì có INSERT, thêm product vào `tag.getProducts()` thì không.

**Khi nào không xảy ra.** Nếu đổi owner sang Tag (mục 8.1.4), hai kết quả đảo ngược.

**Ví dụ.**

```java
// Thêm ở owner
inTx(() -> {
    M1Product phone = em.find(M1Product.class, phoneId);
    M1Tag tagNew = em.find(M1Tag.class, tagNewId);
    phone.getTags().add(tagNew);
    // [FLUSH] lúc commit: collection của owner đổi, INSERT bảng nối
});

// Thêm ở inverse
inTx(() -> {
    M1Product phone = em.find(M1Product.class, phoneId);
    M1Tag tagNew = em.find(M1Tag.class, tagNewId);
    tagNew.getProducts().add(phone);
    // [FLUSH] lúc commit: Hibernate bỏ qua collection inverse
});
```

**Kết quả.** Thêm ở owner: đúng một câu INSERT `product_tag`; bảng `product` và `tag` không bị UPDATE.

| Bảng product_tag | product_id | tag_id |
|---|---|---|
| Trước | (trống) | (trống) |
| Sau | **id của phone** | **id của tagNew** |

Thay đổi: xuất hiện một dòng nối. Giữ nguyên: các dòng product và tag.

Thêm ở inverse: không có câu INSERT nào, bảng nối vẫn trống.

**Vì sao.** Collection inverse chỉ là "hình chiếu" để đọc. Hibernate không dùng nó để tính SQL ghi. Quên đồng bộ phía owner là lỗi im lặng: code chạy, không có exception, dữ liệu không đổi.

**Cách tránh.** Viết method helper trên entity chứa owner, gán cả hai phía (giống `addItem` ở Chương 4) và chỉ gọi helper đó.

::: {.ghi-nho}
Chỉ owning side ghi bảng nối. Thêm vào collection inverse là thao tác không có tác dụng trong database.
:::

::: {.tracuu codes="G7-S02a,G7-S02b"}
:::

#### 8.1.4 Lật owner sang Tag: thao tác nào có hiệu lực đổi theo {.cot-loi}

**Bối cảnh.** Nghiệp vụ thay đổi: giờ người dùng chủ yếu gắn sản phẩm cho nhãn từ màn hình nhãn. Bạn cân nhắc chuyển `@JoinTable` sang `Tag.products`.

**Khi nào xảy ra.** `@JoinTable` nằm trên `Tag.products`, và `Product.tags` có `mappedBy`.

**Khi nào không xảy ra.** Với owner là Product (mục 8.1.3), hai kết quả ngược lại.

**Ví dụ.**

```java
// Owner là Tag: mapping rút gọn
@Entity(name = "G7M3_Tag")
public class M3Tag {
    @ManyToMany(fetch = FetchType.LAZY, cascade = {})
    @JoinTable(schema = "g7_m3_owner_tag", name = "product_tag",
            joinColumns = @JoinColumn(name = "tag_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id"))
    private Set<M3Product> products = new HashSet<>();
}

inTx(() -> {
    M3Product phone = em.find(M3Product.class, phoneId);
    M3Tag tagNew = em.find(M3Tag.class, tagNewId);
    tagNew.getProducts().add(phone); // owner mới
});
```

**Kết quả.** `tag.getProducts().add(product)` sinh đúng một câu INSERT `product_tag`; cột `product_id` lưu id của phone, `tag_id` lưu id của tag. Còn `product.getTags().add(tag)` (giờ là inverse) không ghi gì: không có INSERT nào, bảng nối trống.

**Vì sao.** Owner được quyết định bởi phía **không** có `mappedBy`. Đổi chỗ `@JoinTable` là đổi phía có quyền ghi, và code đang chạy ổn có thể thành im lặng nếu bạn chỉ sửa mapping mà không sửa chỗ gọi.

::: {.ghi-nho}
Câu hỏi "đặt owner bên nào" quyết định thao tác nào có hiệu lực. Đặt owner ở phía mà code thường xuyên thay đổi liên kết.
:::

::: {.tracuu codes="G7-S03a,G7-S03b"}
:::

### 8.2 Quên mappedBy: hai bảng nối {.bien-the}

Một lỗi phổ biến: khai báo `@ManyToMany` ở cả hai phía và **không** dùng `mappedBy` ở đâu cả.

```java
// Product.tags: một quan hệ nhiều-nhiều hoàn chỉnh
@ManyToMany
private Set<M4Tag> tags = new HashSet<>();

// Tag.products: lại một quan hệ nhiều-nhiều khác, không mappedBy
@ManyToMany
private Set<M4Product> products = new HashSet<>();
```

Hibernate không suy ra được hai khai báo này là hai nửa của cùng một quan hệ. Nó coi chúng là **hai quan hệ độc lập**, mỗi quan hệ có bảng nối riêng.

![Bốn bảng: product, tag, product_tags và tag_products; hai bảng nối đều có hai khóa ngoại tới product và tag](img/g7-m4.png){width=90%}

**Ưu điểm.** Không có ưu điểm đáng kể: đây là mapping sai chứ không phải một lựa chọn thiết kế. Điều duy nhất nên rút ra là cách nhận ra lỗi.

**Nhược điểm.** Cùng một liên kết nghiệp vụ bị ghi hai lần ở hai nơi, và hai bảng dễ mâu thuẫn: một bảng nói "product có tag", bảng kia nói "tag không có product".

**Nên dùng khi.** Không bao giờ. Hai phía của cùng một quan hệ phải có một phía dùng `mappedBy`.

#### 8.2.1 DDL: hai bảng nối thay vì một {.cot-loi}

**Bối cảnh.** Bạn mở database và thấy hai bảng `product_tags` và `tag_products` giống hệt nhau về cấu trúc, trong khi mô hình nghiệp vụ chỉ có một quan hệ.

**Khi nào xảy ra.** Cả `Product.tags` và `Tag.products` đều là `@ManyToMany` không có `mappedBy`.

**Khi nào không xảy ra.** Khi một phía có `mappedBy` (mục 8.1).

**Ví dụ.** DDL:

::: {.ddl schema="g7_m4_no_mappedby" tables="product_tags,tag_products"}
:::

**Kết quả.** Schema có bốn bảng: `product`, `tag`, `product_tags` (bảng nối của phía Product) và `tag_products` (bảng nối của phía Tag). Mỗi bảng nối có hai khóa ngoại và khóa chính ghép.

**Vì sao.** Mỗi `@ManyToMany` không có `mappedBy` là owning side của chính nó, nên có bảng nối riêng. Tên bảng mặc định ghép từ tên entity và tên field.

::: {.ghi-nho}
Nếu thấy hai bảng nối đối xứng cho cùng một quan hệ, đó là dấu hiệu quên `mappedBy`.
:::

::: {.tracuu codes="G7-S04a"}
:::

#### 8.2.2 Gán một phía hay cả hai phía {.cot-loi}

**Bối cảnh.** Sau khi mapping sai đã chạy lên production, dữ liệu bắt đầu không khớp: có sản phẩm hiển thị đúng nhãn, nhưng nhãn lại không liệt kê sản phẩm đó.

**Khi nào xảy ra.** Mapping của mục 8.2.1, khi code chỉ gán một trong hai phía.

**Khi nào không xảy ra.** Nếu code luôn gán cả hai phía (nhưng khi đó mỗi liên kết bị ghi hai lần), hoặc nếu dùng `mappedBy`.

**Ví dụ.**

```java
// Gán cả hai phía
inTx(() -> {
    M4Product phone = em.find(M4Product.class, phoneId);
    M4Tag tagNew = em.find(M4Tag.class, tagNewId);
    phone.getTags().add(tagNew);
    tagNew.getProducts().add(phone);
});

// Chỉ gán một phía
inTx(() -> {
    M4Product phone = em.find(M4Product.class, phoneId);
    M4Tag tagNew = em.find(M4Tag.class, tagNewId);
    phone.getTags().add(tagNew);
});
```

**Kết quả.** Gán cả hai phía: mỗi bảng nối có một dòng, cùng một liên kết nghiệp vụ nằm ở hai nơi.

| Bảng | Số dòng sau khi gán cả hai phía |
|---|---|
| product_tags | 1 |
| tag_products | 1 |

Chỉ gán một phía: `product_tags` có một dòng còn `tag_products` có không dòng. Database vừa nói "product có tag" vừa nói "tag không có product".

**Vì sao.** Hai bảng nối là hai nguồn sự thật độc lập, không ai đồng bộ chúng. Cách sửa: thêm `mappedBy` vào một phía (mục 8.1), để chỉ còn một bảng nối.

::: {.ghi-nho}
Hai bảng nối cho một quan hệ là hai nguồn sự thật. Sớm muộn chúng sẽ mâu thuẫn nhau.
:::

::: {.tracuu codes="G7-S04b,G7-S04c"}
:::

### 8.3 Set, List và OrderColumn {.bien-the}

Kiểu collection của `@ManyToMany` quyết định cách Hibernate ghi bảng nối. Ba lựa chọn:

- `Set`: phần tử là khóa tự nhiên, bảng nối có khóa chính ghép trên hai khóa ngoại.
- `List` không có `@OrderColumn` (gọi là **bag**): không có gì định danh một dòng nối, bảng nối **không có khóa chính**.
- `List` có `@OrderColumn`: thêm cột `position`, khóa chính là `(product_id, position)`.

```java
// Biến thể: List + @OrderColumn
@ManyToMany(fetch = FetchType.LAZY, cascade = {})
@JoinTable(schema = "g7_m6_order_column", name = "product_tag",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id"))
@OrderColumn(name = "position")
private List<M6Tag> tags = new ArrayList<>();
```

![Bảng product_tag của biến thể OrderColumn có thêm cột position; khóa chính là product_id và position](img/g7-m6.png){width=85%}

**Ưu điểm.** `Set` đúng ngữ nghĩa của quan hệ nhiều-nhiều ("mỗi tag một lần") và ghi đúng từng dòng. `@OrderColumn` giữ thứ tự hiển thị bền vững.

**Nhược điểm.** `List` thường (bag) mỗi lần đổi collection thì ghi lại cả phần bảng nối của entity đó, và cho phép dòng trùng. `@OrderColumn` giữ được thứ tự nhưng phải dồn lại `position` mỗi khi bỏ phần tử giữa.

**Nên dùng khi.** Dùng `Set` cho nhiều-nhiều là mặc định. Chỉ dùng `List` với `@OrderColumn` khi thứ tự là dữ liệu nghiệp vụ. Tránh `List` không có `@OrderColumn`.

#### 8.3.1 Bỏ một trong ba tag: Set và bag khác nhau thế nào {.cot-loi}

**Bối cảnh.** Sản phẩm có ba tag. Người dùng bỏ đi một tag. Bạn kỳ vọng một câu DELETE. Với `Set` đúng như vậy, nhưng với `List`, log SQL hiện ra ba câu.

**Khi nào xảy ra.** `List` không có `@OrderColumn` (bag): Hibernate xóa cả phần bảng nối của entity rồi INSERT lại các dòng còn lại.

**Khi nào không xảy ra.** Với `Set`, hoặc `List` có `@OrderColumn` (mục 8.3.3).

**Ví dụ.**

```java
inTx(() -> {
    M1Product phone = em.find(M1Product.class, phoneId);
    M1Tag tagB = em.find(M1Tag.class, tagBId);
    phone.getTags().remove(tagB);
    // [FLUSH] lúc commit
});
```

**Kết quả.** Với `Set`: đúng một câu DELETE đúng dòng `(product_id, tag_id)` của tag bỏ, không INSERT nào. Với bag, Hibernate gửi:

```sql
delete
from g7_m5_list_bag.product_tag
where product_id=?

insert into g7_m5_list_bag.product_tag (product_id, tag_id)
values (?, ?)

insert into g7_m5_list_bag.product_tag (product_id, tag_id)
values (?, ?)
```

| Bảng product_tag (bag) | tag_id |
|---|---|
| Trước | tagA, tagB, tagC |
| Sau | tagA, tagC |

Thay đổi: tag B biến mất. Giữ nguyên: tag A và tag C vẫn còn (nhưng đã bị xóa rồi ghi lại).

**Vì sao.** Bag không có thứ gì định danh một dòng nối, nên Hibernate không thể nói "xóa dòng này". Cách duy nhất đúng là xóa hết rồi ghi lại. Kết quả cuối cùng đúng, nhưng tốn ba câu thay vì một, và với danh sách lớn thì rất đắt.

::: {.ghi-nho}
`Set` ghi đúng từng dòng. `List` không có `@OrderColumn` ghi lại cả phần bảng nối mỗi lần đổi.
:::

::: {.tracuu codes="G7-S05a,G7-S05b"}
:::

#### 8.3.2 Thêm tag trùng: Set loại trong Java, bag cho phép hai dòng giống hệt {.cot-loi}

**Bối cảnh.** Hai luồng cùng gắn tag "khuyến mãi" cho một sản phẩm. Bạn muốn biết liệu sản phẩm có thể có hai dòng giống hệt nhau trong bảng nối.

**Khi nào xảy ra.** `List` không có `@OrderColumn`: bảng nối không có khóa chính nên database không chặn dòng trùng.

**Khi nào không xảy ra.** `Set` tự loại phần tử trùng trong Java, và khóa chính ghép chặn thêm ở database.

**Ví dụ.**

```java
inTx(() -> {
    M5Product phone = em.find(M5Product.class, phoneId);
    M5Tag tagA = em.find(M5Tag.class, tagAId);
    phone.getTags().add(tagA);
    phone.getTags().add(tagA); // thêm lần hai
});
```

**Kết quả.** Với `Set`: lần `add` thứ hai trả về `false`, chỉ có một câu INSERT, một dòng nối. Với bag: hai câu INSERT, hai dòng nối giống hệt nhau, và bảng nối không có khóa chính.

**Vì sao.** `Set` dựa vào `equals` và `hashCode` để loại trùng, bag thì không. Không có khóa chính, database cũng không từ chối dòng trùng. Nếu nghiệp vụ là "mỗi tag một lần", hãy dùng `Set`.

::: {.ghi-nho}
Bag cho phép dòng nối trùng. Muốn "mỗi tag một lần" thì dùng `Set`.
:::

::: {.tracuu codes="G7-S09a,G7-S09b"}
:::

#### 8.3.3 OrderColumn: thêm cột position, bỏ phần tử giữa thì dồn vị trí {.mo-rong}

**Bối cảnh.** Thứ tự tag có ý nghĩa (tag đầu hiển thị nổi bật). Bạn thêm `@OrderColumn` để giữ thứ tự, rồi tự hỏi bỏ tag ở giữa thì Hibernate làm gì: ghi lại hết như bag, hay chỉ dồn lại?

**Ví dụ.**

```java
inTx(() -> {
    M6Product phone = em.find(M6Product.class, phoneId);
    phone.getTags().remove(1); // bỏ tag ở giữa (vị trí 1)
    // [FLUSH] lúc commit
});
```

**Kết quả.** DDL: bảng `product_tag` có thêm cột `position`, và khóa chính là `(product_id, position)`, không phải cặp khóa ngoại. Khi bỏ phần tử giữa, Hibernate **không** ghi lại cả bảng như bag. Nó gửi một câu UPDATE và một câu DELETE:

```sql
delete
from g7_m6_order_column.product_tag
where product_id=? and position=?

update g7_m6_order_column.product_tag
set tag_id=?
where product_id=? and position=?
```

| position | tag_id trước | tag_id sau |
|---|---|---|
| 0 | tagA | tagA |
| 1 | tagB | **tagC** |
| 2 | tagC | **(dòng bị xóa)** |

Thay đổi: dòng ở vị trí 1 nhận tag C, dòng ở vị trí cuối bị xóa. Giữ nguyên: dòng ở vị trí 0. Kết quả cuối: tag A ở vị trí 0, tag C ở vị trí 1.

**Vì sao.** `position` định danh từng dòng nên Hibernate sửa đúng dòng cần sửa. Vì vậy cùng một tag có thể xuất hiện ở nhiều vị trí. Cái giá là bỏ phần tử giữa phải dồn lại mọi dòng phía sau.

::: {.ghi-nho}
`@OrderColumn` tránh được "ghi lại cả bảng", nhưng bỏ phần tử giữa vẫn phải dồn vị trí. Chỉ dùng khi thứ tự là dữ liệu nghiệp vụ.
:::

::: {.tracuu codes="G7-S06a,G7-S06b"}
:::

### 8.4 Xóa và cascade trên ManyToMany

#### 8.4.1 Xóa owner thì dọn bảng nối, xóa inverse còn liên kết thì vi phạm khóa ngoại {.cot-loi}

**Bối cảnh.** Bạn cần xóa một sản phẩm, rồi xóa một tag không còn dùng. Xóa sản phẩm chạy suôn sẻ, nhưng xóa tag lại báo lỗi khóa ngoại dù cả hai thao tác đều là `em.remove`.

**Khi nào xảy ra.** Xóa **owner** (Product): Hibernate tự DELETE các dòng nối rồi DELETE entity. Xóa **inverse** (Tag) còn liên kết: Hibernate chỉ gửi DELETE tag, còn dòng nối trỏ tới tag, nên database từ chối.

**Khi nào không xảy ra.** Nếu tag không còn liên kết nào, hoặc đã gỡ khỏi mọi product trước.

**Ví dụ.**

```java
// Xóa owner
inTx(() -> {
    em.remove(em.find(M1Product.class, phoneId));
});

// Xóa inverse còn liên kết: lỗi
inTx(() -> {
    em.remove(em.find(M1Tag.class, tagId));
});

// Cách sửa: gỡ tag khỏi mọi product (phía owner) rồi mới xóa tag
inTx(() -> {
    M1Tag tag = em.find(M1Tag.class, tagId);
    for (M1Product product : new ArrayList<>(tag.getProducts())) {
        product.getTags().remove(tag);
    }
    em.remove(tag);
});
```

**Kết quả.** Xóa owner: Hibernate gửi hai câu theo thứ tự, DELETE dòng nối trước rồi DELETE product:

```sql
delete
from g7_m1_owner_product.product_tag
where product_id=?

delete
from g7_m1_owner_product.product
where id=?
```

Tag không bị đụng (không cascade). Xóa inverse còn liên kết: `DataIntegrityViolation`, rollback, tag và liên kết còn nguyên. Với cách sửa: dòng nối bị xóa, rồi tag bị xóa, và hai product vẫn còn.

**Vì sao.** Hibernate đọc collection của owner để dọn bảng nối, nhưng không đọc collection inverse. Xóa inverse thì nó không biết có dòng nối nào cần dọn. Vì vậy phải dùng owning side để gỡ liên kết trước (duyệt `tag.getProducts()` chỉ để đọc, rồi gỡ qua `product.getTags()`).

::: {.ghi-nho}
Xóa owner thì Hibernate tự dọn bảng nối. Xóa inverse còn liên kết thì phải gỡ qua owner trước, nếu không database từ chối.
:::

::: {.tracuu codes="G7-S07a,G7-S07b,G7-S07c"}
:::

#### 8.4.2 cascade ALL: xóa product có thể xóa nhầm tag dùng chung {.cot-loi}

**Bối cảnh.** Một lập trình viên muốn "xóa product thì dọn luôn tag" và thêm `cascade = ALL` vào `Product.tags`. Tag lại là dữ liệu dùng chung: nhiều sản phẩm cùng gắn nhãn "khuyến mãi".

**Khi nào xảy ra.** `cascade = ALL` (có REMOVE) trên `@ManyToMany`.

**Khi nào không xảy ra.** Với `cascade = {}` (mục 8.1): product và tag độc lập.

**Ví dụ.**

```java
// Product.tags có cascade = ALL
@ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
@JoinTable(schema = "g7_m7_cascade_all", name = "product_tag",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id"))
private Set<M7Tag> tags = new HashSet<>();

// Trường hợp 1: tag T được cả P1 và P2 dùng; xóa P1
inTx(() -> em.remove(em.find(M7Product.class, productP1Id)));

// Trường hợp 2: tag T3 chỉ P3 dùng; xóa P3
inTx(() -> em.remove(em.find(M7Product.class, productP3Id)));
```

**Kết quả.** Trường hợp 1: REMOVE lan sang tag T. Hibernate DELETE dòng nối của P1, rồi DELETE tag T **trước cả product**. P2 vẫn còn dòng nối trỏ tới T nên database từ chối ở câu DELETE tag. Rollback, cả hai product, tag T và hai liên kết còn nguyên.

Trường hợp 2: không có lỗi nào. Product P3 và tag T3 **cùng biến mất**:

| Bảng | Trước | Sau |
|---|---|---|
| product | 1 dòng (P3) | **0 dòng** |
| tag | 1 dòng (T3) | **0 dòng** |

Thay đổi: cả product và tag bị xóa. Giữ nguyên: không có gì.

**Vì sao.** `CascadeType.REMOVE` coi tag như "con" của product. Nhưng tag dùng chung thì không thuộc riêng product nào. Nguy hiểm nhất là trường hợp 2: không lỗi, và nếu sau này người khác muốn gắn tag T3 cho một sản phẩm mới thì tag đã không còn.

**Cách tránh.** Không cascade REMOVE trên `@ManyToMany`. Nếu cần cascade lưu (PERSIST), chỉ dùng khi tag là dữ liệu riêng của product.

::: {.ghi-nho}
Tag là dữ liệu dùng chung. `cascade = ALL` trên `@ManyToMany` hoặc vi phạm khóa ngoại, hoặc xóa tag im lặng.
:::

::: {.tracuu codes="G7-S08a,G7-S08b"}
:::

### 8.5 Entity trung gian có cột phụ {.bien-the}

`@ManyToMany` không có chỗ cho cột phụ. Khi quan hệ cần thêm thông tin (số lượng tồn của sản phẩm trong từng kho), bảng nối phải trở thành một **entity**, và quan hệ nhiều-nhiều biến thành hai quan hệ một-nhiều: `Product` một-nhiều `StockItem`, `Warehouse` một-nhiều `StockItem`.

Biến thể thứ nhất: khóa chính ghép bằng `@EmbeddedId` và hai `@MapsId` (giống mục 7.3, nhưng lấy hai phần của khóa từ hai quan hệ).

```java
@Entity(name = "G7M8_StockItem")
@Table(schema = "g7_m8_join_entity", name = "stock_item")
public class M8StockItem {

    @EmbeddedId
    private M8StockItemId id = new M8StockItemId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("productId")
    @JoinColumn(name = "product_id")
    private M8Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("warehouseId")
    @JoinColumn(name = "warehouse_id")
    private M8Warehouse warehouse;

    @Column(name = "quantity", nullable = false)
    private int quantity;
}
```

Phía `Product` giữ collection inverse với `cascade = ALL` và `orphanRemoval = true`, để `StockItem` sống và chết theo product:

```java
// INVERSE side: khóa ngoại nằm ở stock_item (StockItem.product là owning).
@OneToMany(mappedBy = "product", fetch = FetchType.LAZY,
        cascade = CascadeType.ALL, orphanRemoval = true)
private List<M8StockItem> stockItems = new ArrayList<>();
```

Biến thể thứ hai thay khóa ghép bằng id riêng (sequence) và tự khai `UNIQUE (product_id, warehouse_id)`.

![Bảng stock_item có khóa chính ghép product_id và warehouse_id, cột quantity, hai khóa ngoại tới product và warehouse](img/g7-m8.png){width=85%}

![Biến thể id riêng: stock_item có cột id làm khóa chính, hai khóa ngoại và ràng buộc UNIQUE trên cặp product_id và warehouse_id](img/g7-m9.png){width=85%}

**Ưu điểm.** Có chỗ cho cột phụ (`quantity`, ngày tạo, người thêm). Mọi thay đổi là INSERT, UPDATE hay DELETE đúng dòng. Có thể truy vấn trực tiếp trên quan hệ ("tổng tồn kho theo kho").

**Nhược điểm.** Nhiều code hơn: hai `@ManyToOne`, một class id (hoặc id riêng và UNIQUE tự khai), và không còn "add tag vào product" mà phải tạo `StockItem`.

**Nên dùng khi.** Bảng nối cần cột phụ, cần truy vấn hay audit trên chính quan hệ, hoặc bạn muốn tránh bag recreate của `List`. Chọn khóa ghép khi không có bảng nào khác cần trỏ tới dòng tồn kho; chọn id riêng khi có.

#### 8.5.1 Thêm, sửa và xóa một dòng tồn kho {.cot-loi}

**Bối cảnh.** Kho Hà Nội nhập thêm điện thoại, sau đó đếm lại và sửa số lượng, rồi ngừng bán sản phẩm ở kho này. Ba thao tác này đều là thao tác trên `StockItem`, không phải trên `Product`.

**Khi nào xảy ra.** `StockItem` là entity, `Product.stockItems` có `cascade = ALL` và `orphanRemoval = true`.

**Khi nào không xảy ra.** Với `@ManyToMany` thuần, không có cột `quantity` để sửa.

**Ví dụ.**

```java
// Thêm
inTx(() -> {
    M8Product phone = em.find(M8Product.class, phoneId);
    M8Warehouse hanoi = em.find(M8Warehouse.class, warehouseId);
    phone.addStock(hanoi, 10);
    // [CASCADE] PERSIST lan từ product sang stock_item lúc flush
});

// Sửa số lượng
inTx(() -> {
    M8Product phone = em.find(M8Product.class, phoneId);
    phone.getStockItems().get(0).setQuantity(20);
    // [FLUSH] lúc commit: dirty checking thấy quantity đổi
});

// Xóa
inTx(() -> {
    M8Product phone = em.find(M8Product.class, phoneId);
    M8Warehouse hanoi = em.find(M8Warehouse.class, warehouseId);
    phone.removeStock(hanoi);
    // [FLUSH] lúc commit: stock_item thành orphan
});
```

**Kết quả.** Thêm: đúng một câu INSERT `stock_item` với khóa ghép lấy từ product và warehouse; không có sequence cho `stock_item`:

```sql
insert into g7_m8_join_entity.stock_item (quantity, product_id, warehouse_id)
values (?, ?, ?)
```

| quantity | product_id | warehouse_id |
|---|---|---|
| 10 | id của phone | id của kho Hà Nội |

Sửa: đúng một câu UPDATE `stock_item`, không có UPDATE `product` nào, vì `StockItem` là owning side và product không đổi. Dòng tồn kho có `quantity` từ 10 thành **20**. Xóa: đúng một câu DELETE `stock_item` nhờ `orphanRemoval`; product và warehouse còn nguyên.

**Vì sao.** Mỗi thao tác là thao tác trên một entity thật, nên dirty checking và cascade hoạt động đúng như mọi quan hệ một-nhiều ở các chương trước. Không còn "bag recreate": mỗi dòng có khóa chính.

::: {.ghi-nho}
Biến bảng nối thành entity cho phép thêm, sửa, xóa đúng một dòng và có cột phụ.
:::

::: {.tracuu codes="G7-S10a,G7-S10b,G7-S10c"}
:::

#### 8.5.2 Tìm theo khóa ghép và vì sao class id phải có equals, hashCode {.cot-loi}

**Bối cảnh.** Bạn cần lấy dòng tồn kho của một sản phẩm tại một kho. Khóa chính là cặp hai số. Bạn tạo một object `StockItemId(productId, warehouseId)` và gọi `find`.

**Khi nào xảy ra.** Entity dùng `@EmbeddedId`.

**Khi nào không xảy ra.** Với id riêng (mục 8.5.3), `find` nhận một số.

**Ví dụ.**

```java
inTx(() -> {
    M8StockItem found = em.find(M8StockItem.class,
            new M8StockItemId(phoneId, hanoiId));
    M8StockItem notFound = em.find(M8StockItem.class,
            new M8StockItemId(phoneId, hueId));
});

inTx(() -> {
    M8StockItem first = em.find(M8StockItem.class,
            new M8StockItemId(phoneId, hanoiId));
    M8StockItem second = em.find(M8StockItem.class,
            new M8StockItemId(phoneId, hanoiId)); // object id khác, cùng giá trị
});
```

**Kết quả.** `find` với cặp đúng trả về dòng đúng (số lượng đúng); cặp không tồn tại trả về `null`. Trong cùng một transaction, hai lần `find` bằng hai object id **khác nhau nhưng cùng giá trị** trả về **cùng một instance**, và chỉ có một câu SELECT `stock_item`. Hai `StockItemId` cùng giá trị `equals` nhau và cùng `hashCode`; `StockItemId` khác `warehouseId` thì khác nhau; class id là `Serializable`.

**Vì sao.** Persistence context lưu entity trong một `Map` với khóa là id. Nếu `equals` và `hashCode` của id không theo giá trị, hai object id cùng giá trị sẽ là hai khóa khác nhau, và Hibernate tải cùng một dòng thành hai entity MANAGED riêng biệt. JPA cũng yêu cầu class id của khóa ghép phải `Serializable`.

::: {.ghi-nho}
Class id của khóa ghép phải `Serializable` và cài `equals`, `hashCode` theo giá trị. Thiếu chúng, persistence context hỏng ngầm.
:::

::: {.tracuu codes="G7-S11a,G7-S11b"}
:::

#### 8.5.3 Khóa ghép hay id riêng: so sánh hai biến thể {.mo-rong}

**Bối cảnh.** Bạn phải chọn giữa khóa chính ghép `(product_id, warehouse_id)` và một cột `id` riêng cho bảng `stock_item`.

**Ví dụ.** DDL của cả hai biến thể:

::: {.ddl schema="g7_m8_join_entity" tables="stock_item"}
:::

::: {.ddl schema="g7_m9_join_entity_surrogate" tables="stock_item"}
:::

**Kết quả.** Biến thể khóa ghép: khóa chính là `(product_id, warehouse_id)`, không cần UNIQUE vì khóa chính đã chặn cặp trùng, và không có sequence cho `stock_item`. Biến thể id riêng: khóa chính là `id` (có sequence `stockitem_seq`), và phải **tự khai** `UNIQUE (product_id, warehouse_id)`. Khi thêm dòng thứ hai cho cùng một cặp, database từ chối (vi phạm UNIQUE), rollback, chỉ còn dòng đầu. Bảng khác muốn trỏ tới một dòng tồn kho thì với id riêng chỉ cần khóa ngoại một cột, với khóa ghép phải dùng khóa ngoại hai cột (Chương 9).

**Vì sao.** Khóa ghép biến "không trùng cặp" thành tính chất của khóa chính, nên không thể quên. Id riêng tiện cho các bảng khác tham chiếu, nhưng bạn tự chịu trách nhiệm khai UNIQUE: quên khai thì một sản phẩm có thể có hai dòng cho cùng một kho.

::: {.ghi-nho}
Id riêng cần UNIQUE tự khai trên cặp khóa ngoại. Quên khai là mất ràng buộc "mỗi cặp một dòng".
:::

::: {.tracuu codes="G7-S12a,G7-S12b"}
:::

### 8.6 Khi nào nên bỏ ManyToMany

::: {.ghi-nho codes="G7-S14"}
Mục này là tổng kết thiết kế, không có test riêng trong lab.

`@ManyToMany` chỉ hợp khi bảng nối **chỉ có hai cột khóa ngoại** và sẽ không bao giờ cần thêm gì. Chuyển sang entity trung gian (mục 8.5) khi:

- cần **cột phụ** như `quantity`, `createdAt`, `assignedBy`;
- cần **thao tác hoặc truy vấn trực tiếp trên quan hệ** (ví dụ "tổng tồn kho theo kho" là truy vấn trên `StockItem`);
- cần **audit** (ai thêm, khi nào) hoặc xóa mềm một liên kết;
- cần tránh **bag recreate** của `List` mà vẫn muốn sửa từng dòng.

Đánh đổi: entity trung gian tốn nhiều code hơn (hai `@ManyToOne`, khóa ghép hoặc id riêng), nhưng mọi thay đổi là INSERT, UPDATE hay DELETE đúng dòng. Nếu còn phân vân, hãy bắt đầu bằng entity trung gian: thêm cột phụ vào `@ManyToMany` sau này là thay đổi lớn, còn bỏ cột phụ khỏi entity trung gian thì dễ.
:::
