## Chương 10. Các mô hình đặc biệt: cây tự tham chiếu, ElementCollection, kế thừa

Các chương trước xoay quanh quan hệ giữa **hai entity khác nhau**. Chương này gom ba mô hình mà người mới hay gặp ngay khi dự án lớn dần, và mỗi mô hình có bẫy riêng:

- **Cây tự tham chiếu**: một bảng trỏ vào chính nó (danh mục cha và danh mục con, thư mục, sơ đồ tổ chức).
- **ElementCollection**: một danh sách giá trị đơn giản (ghi chú, nhãn) không đáng làm entity riêng.
- **Kế thừa**: nhiều kiểu thanh toán (thẻ, chuyển khoản) cùng chia sẻ phần chung.

Trong chương này:

- Cây danh mục: persist, chuyển nhánh, xóa nhánh, duyệt cây mà không bị N+1 (mục 10.1).
- `@ElementCollection` so với `@OneToMany`: khác nhau thế nào khi bỏ một phần tử (mục 10.2).
- Ba chiến lược kế thừa `SINGLE_TABLE`, `JOINED`, `TABLE_PER_CLASS` (mục 10.3).
- Quan hệ trỏ tới lớp cha trừu tượng và bẫy proxy (mục 10.4).

**Trong JPA thuần.** `@ManyToOne` tự tham chiếu, `@ElementCollection`, `@CollectionTable`, `@Inheritance` và `@DiscriminatorColumn` đều là JPA chuẩn. Phần cần chú ý về JPA thuần nằm ở mục 10.4, nơi test dùng `Hibernate.unproxy`.

### 10.1 Cây danh mục tự tham chiếu {.bien-the}

Danh mục sản phẩm là một cây: Electronics có con Phones và Laptops, Phones có con Android. Tất cả đều là **cùng một entity** `Category`, nằm trong cùng một bảng, nối với nhau bằng cột `parent_id` trỏ về chính `category.id`.

Mapping là mẫu hai chiều chuẩn của Chương 4 và `orphanRemoval` của Chương 6, chỉ khác là cha và con cùng một lớp:

```java
@Entity(name = "G9S01_Category")
@Table(schema = "g9_s01_category_tree", name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
            generator = "g9s01_category_seq")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    // OWNING side: cột parent_id nằm ở CHÍNH bảng category.
    // Node gốc có parent_id = NULL nên không đặt nullable = false.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    // INVERSE side: collection chỉ để duyệt và cascade.
    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Category> children = new ArrayList<>();

    public void addChild(Category child) {
        children.add(child);
        child.parent = this;
    }

    public void removeChild(Category child) {
        children.remove(child);
        child.parent = null;
    }
}
```

`fetch = LAZY` được ghi tường minh trên `parent`: `@ManyToOne` mặc định là `EAGER`, và trên cây nó sẽ kéo cả chuỗi cha lên mỗi lần tải một node.

![Bảng category có cột parent_id trỏ về chính category.id; bảng product có khóa ngoại category_id trỏ tới category](img/g9-s01.png){width=85%}

**Ưu điểm.** Một bảng, một entity cho cây có độ sâu bất kỳ. Thêm node mới chỉ là thêm một dòng với `parent_id` đúng. Chuyển cả nhánh chỉ cần đổi một cột.

**Nhược điểm.** Duyệt cây qua collection `LAZY` tốn một câu SELECT cho mỗi node (N+1). Truy vấn "mọi con cháu của node X" không có sẵn trong JPQL thuần (cần truy vấn đệ quy native).

**Nên dùng khi.** Cây nhỏ đến vừa, thao tác chủ yếu là thêm, chuyển và xóa nhánh. Cây rất lớn hoặc cần truy vấn tổ tiên và hậu duệ thường xuyên thì cân nhắc mô hình khác (path hoặc closure table).

#### 10.1.1 Persist cả cây bằng một lần persist gốc {.cot-loi}

**Bối cảnh.** Bạn dựng sẵn cây ba tầng trong bộ nhớ: Electronics, Phones, Android. Bạn muốn lưu cả cây bằng một lệnh `persist` ở gốc, và cần chắc rằng `parent_id` ghi đúng.

**Khi nào xảy ra.** `children` có `cascade = ALL`, và cả hai phía được gán bằng `addChild`.

**Khi nào không xảy ra.** Nếu thiếu cascade, chỉ node gốc được lưu và bạn gặp lỗi về entity TRANSIENT lúc flush (mục 5.2).

**Ví dụ.**

```java
Category electronics = new Category("Electronics");
Category phones = new Category("Phones");
Category android = new Category("Android");
electronics.addChild(phones);
phones.addChild(android);

inTx(() -> {
    em.persist(electronics);
    // [LIFECYCLE] cascade PERSIST: cả 3 node: TRANSIENT thành MANAGED,
    //             CHƯA có INSERT nào
    // [FLUSH] lúc commit: INSERT theo thứ tự cascade (cha trước con)
});
```

**Kết quả.** Ba câu INSERT vào `category`, theo thứ tự cha trước con.

| name | parent_id |
|---|---|
| Electronics | NULL |
| Phones | id của Electronics |
| Android | id của Phones |

**Vì sao.** Cascade PERSIST đi từ cha xuống con, nên Hibernate INSERT cha trước. Điều này cần thiết vì khóa ngoại `parent_id` của con trỏ về cha: con phải được INSERT sau cha. Cột `parent_id` ghi đúng nhờ owning side `parent` đã được gán trong `addChild`.

::: {.ghi-nho}
Persist gốc là đủ cho cả cây khi có cascade: Hibernate INSERT theo thứ tự từ cha xuống con, đúng thứ tự khóa ngoại yêu cầu.
:::

::: {.tracuu codes="G9-S01a"}
:::

#### 10.1.2 Chuyển cả nhánh sang cha khác chỉ cần một câu UPDATE {.cot-loi}

**Bối cảnh.** Danh mục Phones đang nằm dưới Electronics, nay cần chuyển sang dưới Home. Phones có con (Android). Bạn lo phải cập nhật cả nhánh.

**Khi nào xảy ra.** Bạn đổi owning side `parent` của node cần chuyển.

**Khi nào không xảy ra.** Không có. Điều này luôn đúng với cây tự tham chiếu: con trỏ vào cha, không phải cha trỏ vào con.

**Ví dụ.**

```java
inTx(() -> {
    Category phones = em.find(Category.class, phonesId);
    Category home = em.find(Category.class, homeId);
    phones.setParent(home); // chỉ đổi OWNING side
    // [FLUSH] lúc commit: UPDATE category set name=?, parent_id=? (Phones)
});
```

**Kết quả.** Đúng một câu UPDATE `category`, không INSERT, không DELETE.

| Bảng category | Phones.parent_id | Android.parent_id |
|---|---|---|
| Trước | id của Electronics | id của Phones |
| Sau | **id của Home** | id của Phones |

Thay đổi: `parent_id` của Phones. Giữ nguyên: mọi dòng khác, kể cả Android; tổng số dòng vẫn là năm.

**Vì sao.** Android trỏ vào Phones, không trỏ vào Electronics. Khi Phones đổi cha thì cả nhánh đi theo mà không cần đụng tới con cháu. Dirty checking chỉ thấy một cột đổi ở một dòng.

::: {.ghi-nho}
Trong cây tự tham chiếu, con trỏ vào cha. Chuyển nhánh chỉ cần đổi `parent_id` của gốc nhánh, đúng một câu UPDATE.
:::

::: {.tracuu codes="G9-S01b"}
:::

#### 10.1.3 Xóa node giữa: xóa cả nhánh, con trước cha, và khi còn sản phẩm trỏ tới {.cot-loi}

**Bối cảnh.** Danh mục Phones bị bỏ. Bạn gọi `removeChild`. Nhánh dưới Phones (Android) phải biến mất theo. Nhưng nếu còn sản phẩm Pixel đang thuộc danh mục Phones thì sao?

**Khi nào xảy ra.** `orphanRemoval = true` và `cascade = ALL` trên `children`. Xóa node thì cả nhánh bị xóa, con trước cha. Nếu có bảng khác (như `product`) còn trỏ tới node bị xóa mà không có cascade, database từ chối.

**Khi nào không xảy ra.** Nếu thiếu `orphanRemoval`, `removeChild` chỉ đặt `parent = null` và để lại node mồ côi (mục 6.2).

**Ví dụ.**

```java
// Xóa Phones khi không còn gì trỏ tới
inTx(() -> {
    Category electronics = em.find(Category.class, electronicsId);
    Category phones = em.find(Category.class, phonesId);
    electronics.removeChild(phones);
    // phones thành orphan: bị xóa lúc flush, cascade xuống Android
});

// Xóa Phones khi sản phẩm Pixel còn thuộc Phones
inTx(() -> {
    Category electronics = em.find(Category.class, electronicsId);
    electronics.removeChild(em.find(Category.class, phonesId));
});
```

**Kết quả.** Trường hợp thứ nhất: đúng hai câu DELETE, Android trước rồi Phones, không có UPDATE nào. Còn lại hai category là Electronics và Laptops, và Laptops vẫn trỏ về Electronics.

| Bảng category | Trước | Sau |
|---|---|---|
| Số dòng | 4 (Electronics, Phones, Laptops, Android) | **2** (Electronics, Laptops) |

Trường hợp thứ hai: câu DELETE Android chạy được, nhưng DELETE Phones bị PostgreSQL từ chối vì `product.category_id` còn trỏ tới. Spring dịch thành `DataIntegrityViolationException`, transaction rollback, cả bốn category và sản phẩm còn nguyên.

**Vì sao.** Cascade REMOVE đi từ cha xuống con, nhưng Hibernate xếp câu DELETE con trước cha để không vi phạm khóa ngoại `parent_id`. Sản phẩm là một quan hệ khác, một chiều từ `product` sang `category` và không có cascade, nên Hibernate không biết phải làm gì với nó. Database là hàng rào cuối cùng.

**Cách tránh.** Trước khi xóa danh mục, chuyển hoặc xóa sản phẩm thuộc nhánh đó; hoặc cấm xóa danh mục còn sản phẩm ở tầng service.

::: {.ghi-nho}
Xóa node giữa xóa cả nhánh (con trước cha) nhờ `orphanRemoval` và cascade. Bảng khác còn trỏ tới thì database từ chối và rollback.
:::

::: {.tracuu codes="G9-S01c,G9-S01d"}
:::

#### 10.1.4 Duyệt cây: N+1 theo từng node, và cách sửa bằng một truy vấn {.cot-loi}

**Bối cảnh.** Màn hình hiển thị toàn bộ cây danh mục duyệt đệ quy bằng `getChildren()`. Cây chỉ có bốn node mà log SQL có năm câu SELECT.

**Khi nào xảy ra.** `children` là `LAZY` và bạn duyệt đệ quy qua nó.

**Khi nào không xảy ra.** Nếu bạn tải mọi node bằng một truy vấn rồi ghép cây trong bộ nhớ qua `getParent()`.

**Ví dụ.**

```java
// Duyệt qua collection LAZY: N+1
int count(Category node) {
    int count = 1;
    for (Category child : node.getChildren()) {
        count += count(child);
    }
    return count;
}

// Sửa: một truy vấn cho mọi node, ghép cây bằng getParent()
inTx(() -> {
    List<Category> all = em.createQuery(
            "select c from G9S01_Category c", Category.class)
        .getResultList();
    Map<String, List<String>> tree = new HashMap<>();
    for (Category category : all) {
        Category parent = category.getParent(); // đã có trong persistence context
        if (parent != null) {
            tree.computeIfAbsent(parent.getName(), key -> new ArrayList<>())
                .add(category.getName());
        }
    }
});
```

**Kết quả.** Duyệt bằng `getChildren()`: một câu SELECT tìm gốc, cộng một câu SELECT nạp `children` cho **mỗi** node (kể cả node lá). Bốn node tạo ra năm câu SELECT. Cách sửa: đúng **một** câu SELECT, rồi ghép cây trong bộ nhớ: Electronics có hai con là Phones và Laptops, Phones có con là Android.

**Vì sao.** Mỗi collection `LAZY` chưa nạp là một câu SELECT riêng khi bạn chạm vào. Sau khi tải mọi node bằng một truy vấn, `getParent()` trả về node đã nằm sẵn trong persistence context, nên không tốn SQL. Lưu ý: gọi `getChildren()` trong trường hợp này vẫn sinh SELECT, vì collection `LAZY` không tự được điền dù các node con đã được nạp.

::: {.ghi-nho}
Tải mọi node bằng một truy vấn rồi ghép cây qua `getParent()` (owning side, đã có trong bộ nhớ). Đừng duyệt cây bằng `getChildren()`.
:::

::: {.tracuu codes="G9-S01e,G9-S01f"}
:::

### 10.2 ElementCollection so với OneToMany {.bien-the}

Một danh sách ghi chú của đơn hàng (chuỗi ký tự) không cần entity riêng. `@ElementCollection` cho phép lưu danh sách giá trị đơn giản trong một **bảng con** không có entity. Cái giá: phần tử không có định danh riêng (không có id), nên Hibernate khó xác định dòng nào là dòng nào.

```java
@Entity(name = "G12C1_Order")
public class C1Order {

    // Phần tử là kiểu cơ bản (String), KHÔNG phải entity:
    // không có id riêng, không cascade, không truy vấn riêng được.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(schema = "g12_c1_element_collection",
            name = "c1_order_note",
            joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "note", nullable = false)
    private List<String> notes = new ArrayList<>();
}
```

Để đối chiếu, mapping thứ hai biến phần tử thành entity `Note` với `@OneToMany`. Hai biến thể còn lại đổi `List` thành `Set`, hoặc giữ `List` và thêm `@OrderColumn`.

![Bảng c1_order_note chỉ có order_id và note, không có khóa chính; bảng c2_note có id riêng và khóa ngoại order_id](img/g12-c1.png){width=90%}

![Bảng c3_order_label có khóa chính ghép order_id và label; bảng c4_order_step có khóa chính ghép order_id và step_order](img/g12-c3.png){width=90%}

**Ưu điểm.** `@ElementCollection` gọn: không cần class entity, không cần repository, vòng đời gắn chặt với chủ sở hữu (xóa chủ thì xóa theo).

**Nhược điểm.** Kiểu `List` thường (bag) ghi lại cả bảng con mỗi khi bỏ một phần tử. Phần tử không có id nên không truy vấn riêng, không cascade, không tham chiếu từ nơi khác.

**Nên dùng khi.** Tập giá trị nhỏ, ít thay đổi, và chắc chắn sẽ không bao giờ cần tham chiếu tới từng phần tử. Danh sách lớn hoặc hay sửa thì dùng entity con.

#### 10.2.1 Bag so với entity con: bỏ một phần tử {.cot-loi}

**Bối cảnh.** Đơn hàng có ba ghi chú "a", "b", "c". Người dùng xóa ghi chú "a". Bạn kỳ vọng một câu DELETE.

**Khi nào xảy ra.** `@ElementCollection` với `List<String>` không có `@OrderColumn`: bag.

**Khi nào không xảy ra.** Nếu phần tử là entity con (`@OneToMany`), hoặc dùng `Set`, hoặc `@OrderColumn` (mục 10.2.2).

**Ví dụ.**

```java
// ElementCollection List<String>
inTx(() -> {
    C1Order orderOne = em.find(C1Order.class, 1L);
    orderOne.getNotes().remove("a");
    // [FLUSH] lúc commit: bag, ghi lại cả collection
});

// OneToMany tới entity Note
inTx(() -> {
    C2Order orderOne = em.find(C2Order.class, 1L);
    C2Note firstNote = orderOne.getNotes().get(0); // [LIFECYCLE] MANAGED
    orderOne.getNotes().remove(firstNote);
    // [FLUSH] lúc commit: orphan, DELETE theo id
});
```

**Kết quả.** Với `ElementCollection` bag: một câu DELETE toàn bộ dòng của đơn hàng, rồi hai câu INSERT cho "b" và "c". Bảng con không có khóa chính. Kết quả cuối vẫn đúng, nhưng đi đường vòng.

| Bảng c1_order_note | note |
|---|---|
| Trước | a, b, c |
| Sau | b, c |

Với entity con: đúng một câu DELETE theo id, không INSERT nào. Hai dòng còn lại không bị đụng tới.

**Vì sao.** Bag không có khóa nên Hibernate không thể nói "xóa dòng chứa a": nó xóa hết rồi ghi lại. Entity con có id riêng nên Hibernate xóa chính xác dòng đó. Đây là lý do chọn entity con khi collection lớn hoặc hay sửa.

::: {.ghi-nho}
`@ElementCollection` kiểu bag ghi lại cả bảng con mỗi lần đổi. Entity con xóa đúng một dòng theo id.
:::

::: {.tracuu codes="G12-S04a,G12-S04b"}
:::

#### 10.2.2 Set và OrderColumn: có khóa nên không phải ghi lại {.cot-loi}

**Bối cảnh.** Bag đắt khi bỏ phần tử. Bạn thử `Set<String>` và `List` có `@OrderColumn`, hai cách để bảng con có khóa chính.

**Khi nào xảy ra.** `Set` có khóa chính `(order_id, label)`; `List` với `@OrderColumn` có khóa chính `(order_id, step_order)`.

**Khi nào không xảy ra.** Bag không có khóa chính (mục 10.2.1).

**Ví dụ.**

```java
// Set: bỏ nhãn "a"
inTx(() -> {
    C3Order orderOne = em.find(C3Order.class, 1L);
    orderOne.getLabels().remove("a");
});

// List + @OrderColumn: bỏ phần tử đầu
inTx(() -> {
    C4Order orderOne = em.find(C4Order.class, 1L);
    orderOne.getSteps().remove(0);
});
```

**Kết quả.** Với `Set`: một câu DELETE đúng phần tử bỏ, có điều kiện `where order_id=? and label=?`, không INSERT nào. Còn lại "b" và "c". Với `List` và `@OrderColumn` bỏ phần tử đầu: không INSERT, **một** câu DELETE (dòng cuối) và **hai** câu UPDATE (dồn các phần tử lên):

```sql
delete
from g12_c1_element_collection.c4_order_step
where order_id=? and step_order=?

update g12_c1_element_collection.c4_order_step
set step=?
where order_id=? and step_order=?

update g12_c1_element_collection.c4_order_step
set step=?
where order_id=? and step_order=?
```

Thứ tự được giữ nguyên sau khi dồn: "b" rồi "c".

**Vì sao.** Với `Set`, giá trị của phần tử vừa là dữ liệu vừa là khóa, nên Hibernate nhận ra đúng dòng. Với `@OrderColumn`, vị trí là khóa: bỏ phần tử đầu thì các phần tử sau dồn lên, tốn UPDATE cho mỗi dòng bị dịch. `@OrderColumn` rẻ khi bỏ phần tử cuối, đắt dần khi bỏ phần tử càng gần đầu (giống mục 8.3.3).

::: {.ghi-nho}
`Set` và `@OrderColumn` cho bảng con có khóa nên không ghi lại cả bảng. `@OrderColumn` đắt dần khi bỏ phần tử gần đầu danh sách.
:::

::: {.tracuu codes="G12-S05a,G12-S05b"}
:::

### 10.3 Ba chiến lược kế thừa {.bien-the}

Hệ thống thanh toán có hai loại: thanh toán bằng thẻ (`CardPayment`, có `card_last4`) và chuyển khoản (`BankPayment`, có `bank_name`). Cả hai cùng có `amount`. Trong Java đó là kế thừa; trong database có ba cách biểu diễn, chọn bằng `@Inheritance`:

- `SINGLE_TABLE`: cả cây trong **một bảng**, thêm cột phân biệt (discriminator).
- `JOINED`: **mỗi lớp một bảng**, bảng con nối với bảng cha bằng khóa chính.
- `TABLE_PER_CLASS`: **mỗi lớp cụ thể một bảng**, đủ mọi cột, không có bảng cho lớp cha trừu tượng.

```java
// Baseline: SINGLE_TABLE
@Entity(name = "G12H1_Payment")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "payment_type",
        discriminatorType = DiscriminatorType.STRING)
public abstract class H1Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
            generator = "g12h1_payment_seq")
    private Long id;

    @Column(name = "amount", nullable = false)
    private long amount;
}

@Entity(name = "G12H1_CardPayment")
@DiscriminatorValue("CARD")
public class H1CardPayment extends H1Payment {

    // PHẢI nullable: dòng của BankPayment để trống cột này.
    @Column(name = "card_last4", nullable = true)
    private String cardLast4;
}
```

Hai biến thể còn lại chỉ đổi chiến lược trên lớp cha thành `InheritanceType.JOINED` hoặc `InheritanceType.TABLE_PER_CLASS`; các lớp con giữ nguyên.

![Biến thể SINGLE_TABLE: bảng payment duy nhất có cột payment_type, amount, card_last4 và bank_name; bảng receipt trỏ tới payment](img/g12-h1.png){width=85%}

![Biến thể JOINED: ba bảng payment, card_payment và bank_payment; khóa chính của bảng con cũng là khóa ngoại trỏ về payment.id](img/g12-h2.png){width=85%}

![Biến thể TABLE_PER_CLASS: hai bảng card_payment và bank_payment, mỗi bảng đủ cột kể cả amount, không có bảng payment](img/g12-h3.png){width=85%}

**Ưu điểm.** `SINGLE_TABLE` nhanh nhất (một bảng, không join). `JOINED` giữ được `NOT NULL` cho cột lớp con và chuẩn hóa dữ liệu. `TABLE_PER_CLASS` không có bảng cha thừa khi lớp cha chỉ là khái niệm.

**Nhược điểm.** `SINGLE_TABLE` bắt cột lớp con phải nullable. `JOINED` phải join mọi bảng lớp con khi truy vấn đa hình, số join tăng theo số lớp con. `TABLE_PER_CLASS` lặp lại cột chung ở mọi bảng và truy vấn đa hình phải `union all`.

**Nên dùng khi.** Mặc định chọn `SINGLE_TABLE` khi cây nhỏ và lớp con ít cột riêng. Chọn `JOINED` khi cần ràng buộc `NOT NULL` ở lớp con và chấp nhận join. Hạn chế `TABLE_PER_CLASS` trừ khi gần như không bao giờ truy vấn đa hình.

Tổng kết ba chiến lược theo kết quả đã kiểm chứng:

| | `SINGLE_TABLE` | `JOINED` | `TABLE_PER_CLASS` |
|---|---|---|---|
| Số bảng | 1 | 3 (cha và hai con) | 2 (không có bảng cha) |
| Cột lớp con | Phải nullable | Giữ được `NOT NULL` | Giữ được `NOT NULL` |
| Truy vấn đa hình | 1 SELECT, không join | 1 SELECT, `left join` tới mọi bảng con | 1 SELECT, `union all` |

#### 10.3.1 SINGLE_TABLE: một bảng, cột lớp con phải nullable {.cot-loi}

**Bối cảnh.** Bạn chọn `SINGLE_TABLE` cho đơn giản. Ràng buộc "thanh toán bằng thẻ thì bắt buộc có 4 số cuối" nghe hiển nhiên, nên bạn khai `nullable = false` cho `card_last4`.

**Khi nào xảy ra.** Cột của lớp con nằm chung bảng với các lớp con khác.

**Khi nào không xảy ra.** Với `JOINED`, cột lớp con nằm ở bảng riêng nên `NOT NULL` giữ được (mục 10.3.2).

**Ví dụ.** DDL của biến thể `SINGLE_TABLE`:

::: {.ddl schema="g12_h1_single_table" tables="payment,receipt"}
:::

```java
inTx(() -> {
    List<H1Payment> payments = em.createQuery(
            "select p from G12H1_Payment p order by p.id", H1Payment.class)
        .getResultList();
});
```

**Kết quả.** Một bảng `payment` chứa cả cây, cùng cột phân biệt `payment_type` (CARD hoặc BANK). `card_last4` và `bank_name` đều **nullable**, vì dòng của lớp con kia để trống chúng. Truy vấn đa hình là **một** câu SELECT trên bảng `payment`, không join; Hibernate đọc `payment_type` để tạo đúng kiểu lớp con: dòng CARD thành `CardPayment`, dòng BANK thành `BankPayment`.

::: {.khac-du-doan codes="G12-S06a"}
**Điều nhiều người nghĩ:** Hibernate sẽ tự nới `nullable` của cột lớp con trong `SINGLE_TABLE`, nên khai `nullable = false` ở lớp con là an toàn.

**Thực tế:** Hibernate **không** tự nới. Khai `nullable = false` ở lớp con thì DDL sinh `NOT NULL`, và INSERT dòng của lớp con khác vỡ (`null value in column "bank_name"`). Phải để `nullable` tường minh.
:::

**Vì sao.** Mọi lớp con dùng chung một bảng, nên một dòng CARD có `bank_name` trống và ngược lại. Ràng buộc "thẻ thì bắt buộc có `card_last4`" chỉ kiểm tra được ở tầng Java (bean validation), không đặt được trong DDL.

::: {.ghi-nho}
`SINGLE_TABLE`: một bảng, một SELECT, không join, nhưng cột lớp con phải nullable và Hibernate không tự nới giúp bạn.
:::

::: {.tracuu codes="G12-S06a,G12-S06b"}
:::

#### 10.3.2 JOINED: mỗi lớp một bảng, truy vấn đa hình join mọi bảng con {.cot-loi}

**Bối cảnh.** Bạn cần giữ `NOT NULL` cho `card_last4` ở tầng database, và chấp nhận tốn thêm join.

**Khi nào xảy ra.** `@Inheritance(strategy = JOINED)`.

**Khi nào không xảy ra.** Nếu truy vấn trực tiếp một lớp con cụ thể, chỉ join với bảng cha chứ không với các bảng con khác.

**Ví dụ.** DDL của biến thể `JOINED`:

::: {.ddl schema="g12_h2_joined" tables="payment,card_payment,bank_payment"}
:::

```java
inTx(() -> {
    List<H2Payment> payments = em.createQuery(
            "select p from G12H2_Payment p order by p.id", H2Payment.class)
        .getResultList();
});
```

**Kết quả.** Ba bảng: `payment`, `card_payment` và `bank_payment`. Khóa chính của `card_payment` là `id`, **đồng thời** là khóa ngoại trỏ về `payment.id`. Cột lớp con nằm ở bảng riêng nên `card_last4` giữ được `NOT NULL`. Truy vấn đa hình là một câu SELECT có `left join` tới **cả hai** bảng lớp con; dòng nào có mặt ở `card_payment` thì thành `CardPayment`, ở `bank_payment` thì thành `BankPayment`.

**Vì sao.** Phần chung nằm ở bảng cha, phần riêng nằm ở bảng con, nối nhau bằng khóa chính. Hibernate không biết trước dòng cha thuộc lớp con nào, nên phải `left join` mọi bảng con để tìm ra. Số join tăng theo số lớp con.

::: {.ghi-nho}
`JOINED` giữ được `NOT NULL` ở lớp con, nhưng truy vấn đa hình phải `left join` mọi bảng con.
:::

::: {.tracuu codes="G12-S07a,G12-S07b"}
:::

#### 10.3.3 TABLE_PER_CLASS: không có bảng lớp cha, truy vấn đa hình là union all {.cot-loi}

**Bối cảnh.** Lớp cha `Payment` chỉ là khái niệm trừu tượng. Bạn không muốn bảng cha thừa, và mỗi bảng con tự đủ mọi cột.

**Khi nào xảy ra.** `@Inheritance(strategy = TABLE_PER_CLASS)` và lớp cha là `abstract`.

**Khi nào không xảy ra.** Nếu lớp cha là entity cụ thể, nó cũng có bảng riêng.

**Ví dụ.**

```java
inTx(() -> {
    List<H3Payment> payments = em.createQuery(
            "select p from G12H3_Payment p order by p.id", H3Payment.class)
        .getResultList();
});
```

**Kết quả.** Schema không có bảng `payment`, chỉ có `card_payment` và `bank_payment`, mỗi bảng lặp lại cột chung `amount`. Truy vấn đa hình là **một** câu SELECT gồm `union all` giữa các bảng lớp con. Dòng ở `card_payment` thành `CardPayment`, ở `bank_payment` thành `BankPayment`.

**Vì sao.** Không có bảng chung nên Hibernate phải gộp kết quả từ từng bảng lớp con. `union all` là cách duy nhất để truy vấn "mọi thanh toán". Cột chung bị lặp lại ở mọi bảng, và đổi phần chung phải sửa nhiều bảng.

::: {.ghi-nho}
`TABLE_PER_CLASS`: không có bảng cha, mỗi bảng đủ cột, truy vấn đa hình là `union all`. Hạn chế dùng trừ khi hiếm khi truy vấn đa hình.
:::

::: {.tracuu codes="G12-S08"}
:::

### 10.4 Quan hệ trỏ tới lớp cha trừu tượng

#### 10.4.1 ManyToOne LAZY tới lớp cha: proxy là lớp cha, instanceof lớp con sai {.cot-loi}

**Bối cảnh.** Biên lai (`Receipt`) trỏ tới `Payment` bằng `@ManyToOne(fetch = LAZY)`. Code cần phân biệt thanh toán bằng thẻ hay chuyển khoản, nên viết `if (receipt.getPayment() instanceof CardPayment)`. Với dòng thật là thẻ, điều kiện lại luôn sai.

**Khi nào xảy ra.** Quan hệ `LAZY` trỏ tới lớp cha của một cây kế thừa. Hibernate không biết trước dòng thuộc lớp con nào nên dựng proxy từ **lớp cha**.

**Khi nào không xảy ra.** Nếu quan hệ là `EAGER` hoặc entity đã được tải (khi đó bạn nhận về object thật đúng kiểu).

**Ví dụ.**

```java
inTx(() -> {
    H1Receipt receiptOne = em.find(H1Receipt.class, receiptId);
    H1Payment paymentOfReceipt = receiptOne.getPayment();
    // [LIFECYCLE] proxy chưa nạp, chưa có SELECT nào cho payment

    boolean isCard = paymentOfReceipt instanceof H1CardPayment; // false

    H1Payment realPayment = (H1Payment) Hibernate.unproxy(paymentOfReceipt);
    // SELECT payment chạy ở đây
    boolean isCardNow = realPayment instanceof H1CardPayment;   // true
});
```

**Kết quả.** `getPayment()` trả về một `HibernateProxy` sinh từ lớp `Payment`, chưa chạy SQL nào cho `payment`. Phép kiểm tra `instanceof CardPayment` trên proxy cho **false** dù dòng thật là thanh toán thẻ, và proxy vẫn chưa được khởi tạo. Sau `Hibernate.unproxy`, mới có **một** câu SELECT `payment` và object thật là `CardPayment`.

**Vì sao.** Proxy là một lớp con được sinh động từ kiểu **đã khai báo** của quan hệ (`Payment`). Lúc dựng proxy, Hibernate chưa tải dòng nào nên chưa biết đó là thẻ hay chuyển khoản. Proxy `Payment` không phải `CardPayment`, nên `instanceof` sai cho đến khi gỡ proxy.

**Trong JPA thuần.** JPA không có cách tương đương trực tiếp với `Hibernate.unproxy`. Cách thuần JPA tránh bẫy này: tải entity thật bằng một truy vấn (`join fetch` quan hệ, hoặc `select p from Payment p where ...`) thay vì đi qua proxy; hoặc đưa hành vi phụ thuộc kiểu vào method đa hình của `Payment` (ví dụ `payment.describe()`), thay cho `instanceof`.

**Cách tránh.** Đừng kiểm tra kiểu bằng `instanceof` trên quan hệ `LAZY` tới lớp cha. Dùng method đa hình, hoặc gỡ proxy tường minh bằng `Hibernate.unproxy` khi bắt buộc.

::: {.ghi-nho}
Proxy của quan hệ `LAZY` tới lớp cha là lớp cha, không phải lớp con thật. `instanceof` lớp con chỉ đúng sau khi gỡ proxy. Ưu tiên method đa hình.
:::

::: {.tracuu codes="G12-S09"}
:::
