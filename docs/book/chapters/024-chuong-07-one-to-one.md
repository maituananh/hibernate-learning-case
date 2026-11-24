## Chương 7. Quan hệ một-một: OneToOne và MapsId

Quan hệ một-một nghe đơn giản nhất trong ba loại quan hệ, nhưng lại có nhiều bất ngờ nhất khi chạy thật. Lý do: một-một không có "phía nhiều", nên không có collection để Hibernate dựa vào. Mọi thứ phụ thuộc vào **một cột khóa ngoại**, và cột đó nằm ở bảng nào sẽ quyết định cả cách ghi dữ liệu lẫn cách tải dữ liệu.

Chương này dùng hai cặp entity:

- `Customer` và `LoyaltyCard` (thẻ khách hàng thân thiết): khóa ngoại thông thường. Một khách có tối đa một thẻ.
- `Customer` và `CustomerProfile` (hồ sơ mở rộng): profile **dùng chung khóa chính** với customer nhờ `@MapsId`.

Trong chương này:

- Khóa ngoại đặt ở bảng customer hay bảng thẻ, và database có thật sự bảo đảm "một-một" không (mục 7.1).
- Khai báo hai chiều và vì sao `LAZY` ở phía inverse không có tác dụng (mục 7.2).
- `@MapsId`: khóa chính chính là khóa ngoại (mục 7.3).
- `orphanRemoval` trên quan hệ một-một và thứ tự SQL khi thay thẻ (mục 7.4).
- `optional = false` trên `@OneToOne` (mục 7.5).

**Trong JPA thuần.** Mọi annotation trong chương này (`@OneToOne`, `@JoinColumn`, `@MapsId`, `orphanRemoval`) đều là JPA chuẩn, không cần API riêng của Hibernate. Chỗ nào có khác biệt giữa JPA đặc tả và hành vi của Hibernate (như `LAZY` ở phía inverse), chương sẽ nói rõ.

### 7.1 Khóa ngoại đặt ở bảng nào {.bien-the}

Có hai cách đặt khóa ngoại cho cặp customer và thẻ. Hai cách cho cùng một mô hình nghiệp vụ, nhưng bảng khác nhau:

- **Khóa ngoại ở customer**: cột `customer.loyalty_card_id` trỏ sang `loyalty_card.id`.
- **Khóa ngoại ở thẻ**: cột `loyalty_card.customer_id` trỏ sang `customer.id`.

Mapping của cách thứ nhất (khóa ngoại ở customer, một chiều):

```java
@Entity(name = "G6V1_Customer")
@Table(schema = "g6_v1_fk_on_customer", name = "customer")
public class V1Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
            generator = "g6v1_customer_seq")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    // OWNING side: cột loyalty_card_id nằm ở BẢNG NÀY.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loyalty_card_id")
    private V1LoyaltyCard loyaltyCard;
}
```

Mapping của cách thứ hai (khóa ngoại ở thẻ, một chiều): chuyển annotation sang `LoyaltyCard`.

```java
@Entity(name = "G6V2_LoyaltyCard")
@Table(schema = "g6_v2_fk_on_card", name = "loyalty_card")
public class V2LoyaltyCard {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
            generator = "g6v2_loyalty_card_seq")
    private Long id;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    // OWNING side: cột customer_id nằm ở BẢNG NÀY.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private V2Customer customer;
}
```

![Hai sơ đồ bảng: ở cách thứ nhất customer có cột loyalty_card_id trỏ sang loyalty_card; ở cách thứ hai loyalty_card có cột customer_id trỏ sang customer](img/g6-v1.png){width=80%}

![Sơ đồ cách thứ hai: bảng loyalty_card giữ cột customer_id, bảng customer không có cột nào về thẻ](img/g6-v2.png){width=80%}

**Ưu điểm.** Cả hai cách đều là một-một thật: Hibernate tự thêm ràng buộc UNIQUE lên cột khóa ngoại, nên database từ chối dòng thứ hai. Khóa ngoại đặt ở bảng "đến sau" (thẻ phụ thuộc customer) cho mô hình tự nhiên: customer chưa có thẻ thì đơn giản là không có dòng thẻ nào.

**Nhược điểm.** Khóa ngoại ở customer khiến cột `loyalty_card_id` là NULL với mọi customer chưa có thẻ, và để gỡ thẻ phải sửa dòng customer. Khóa ngoại ở thẻ thì phía customer sẽ không có cột nào cho Hibernate biết "có thẻ hay chưa" (hệ quả ở mục 7.2).

**Nên dùng khi.** Chọn bảng giữ khóa ngoại theo phụ thuộc nghiệp vụ: thực thể phụ thuộc (thẻ, hồ sơ, hộ chiếu) giữ khóa ngoại trỏ về thực thể chính. Khóa ngoại ở thực thể chính chỉ hợp lý khi nó thật sự "chọn" một thực thể độc lập đã tồn tại.

#### 7.1.1 DDL của hai cách đặt khóa ngoại {.cot-loi}

**Bối cảnh.** Bạn khai báo `@OneToOne @JoinColumn` và muốn biết database nhận được gì: cột nào ở bảng nào, và "một-một" có được database bảo vệ hay chỉ là quy ước trong code Java.

**Khi nào xảy ra.** Mọi `@OneToOne` có `@JoinColumn` đều sinh cột khóa ngoại ở bảng của phía khai báo `@JoinColumn`, cộng thêm ràng buộc UNIQUE trên chính cột đó.

**Khi nào không xảy ra.** Phía có `mappedBy` (inverse side) không sinh cột nào. Nếu bạn dùng `@ManyToOne` thay cho `@OneToOne`, sẽ không có UNIQUE (một customer có thể có nhiều thẻ).

**Ví dụ.** DDL do Hibernate sinh ra cho hai cách (trích các bảng liên quan):

::: {.ddl schema="g6_v1_fk_on_customer" tables="customer,loyalty_card"}
:::

::: {.ddl schema="g6_v2_fk_on_card" tables="customer,loyalty_card"}
:::

**Kết quả.** Cách thứ nhất: `customer.loyalty_card_id` cho phép NULL, có khóa ngoại sang `loyalty_card(id)` và UNIQUE; bảng `loyalty_card` không có cột nào trỏ về customer. Cách thứ hai ngược lại: `loyalty_card.customer_id` có khóa ngoại sang `customer(id)` và UNIQUE; bảng `customer` không có cột nào về thẻ.

**Vì sao.** Hibernate hiểu `@OneToOne` là "mỗi giá trị khóa ngoại chỉ xuất hiện một lần", nên tự thêm UNIQUE để database cũng bảo đảm điều đó. Nếu thiếu ràng buộc này, "một-một" chỉ tồn tại trong code Java và bất kỳ ai ghi thẳng vào database cũng phá được.

::: {.ghi-nho}
`@OneToOne` với `@JoinColumn` luôn sinh khóa ngoại cộng UNIQUE ở bảng của phía khai báo `@JoinColumn`.
:::

::: {.tracuu codes="G6-S01a,G6-S01b"}
:::

#### 7.1.2 Thẻ thứ hai cho cùng một customer bị database từ chối {.cot-loi}

**Bối cảnh.** Hệ thống phát thẻ có hai nơi cùng chạy (quầy thu ngân và app). Cả hai cùng cấp thẻ cho khách An. Nếu không có gì chặn, An sẽ có hai thẻ và điểm tích lũy bị chia đôi.

**Khi nào xảy ra.** Khóa ngoại nằm ở bảng thẻ, UNIQUE trên `customer_id` còn hiệu lực, và hai dòng thẻ cùng trỏ về một customer.

**Khi nào không xảy ra.** Nếu bạn tự xóa UNIQUE (ví dụ DDL viết tay), database cho qua và bạn có hai thẻ.

**Ví dụ.**

```java
// An đã có thẻ CARD-1. Giờ cấp thêm thẻ thứ hai:
inTx(() -> {
    V2LoyaltyCard card2 = new V2LoyaltyCard("CARD-2");
    card2.setCustomer(em.find(V2Customer.class, customerId));
    em.persist(card2);
    // [FLUSH] lúc commit: INSERT loyalty_card lần thứ hai cho cùng An
});
```

**Kết quả.** Câu INSERT thứ hai bị database từ chối, Spring dịch thành `DataIntegrityViolationException`, transaction rollback. Chỉ có một câu INSERT thẻ được gửi đi trong bước này.

| Bảng loyalty_card | card_no | customer_id |
|---|---|---|
| Trước | CARD-1 | id của An |
| Sau | CARD-1 | id của An |

Thay đổi: không có. Giữ nguyên: thẻ CARD-1; thẻ CARD-2 không được lưu.

**Vì sao.** Hibernate không tự kiểm tra "customer này đã có thẻ chưa". Bảo vệ nằm ở ràng buộc UNIQUE của database, nên lỗi chỉ lộ ra lúc flush, khi câu INSERT chạm vào ràng buộc.

**Cách tránh.** Bắt `DataIntegrityViolationException` ở service và chuyển thành thông báo nghiệp vụ ("khách đã có thẻ"), hoặc kiểm tra trước khi cấp thẻ. Đừng bỏ UNIQUE để "cho lỗi biến mất".

::: {.ghi-nho}
UNIQUE trên cột khóa ngoại là hàng rào thật của quan hệ một-một. Hibernate không tự kiểm tra "đã có chưa", database mới là nơi từ chối.
:::

::: {.tracuu codes="G6-S02"}
:::

#### 7.1.3 Gỡ thẻ ở phía giữ khóa ngoại: dòng thẻ vẫn còn {.mo-rong}

**Bối cảnh.** Khóa ngoại nằm ở customer. Khách An đổi ý, bạn gọi `customer.setLoyaltyCard(null)` và tưởng thẻ đã bị hủy.

**Khi nào xảy ra.** Khóa ngoại ở customer (owning side), không có cascade và không có `orphanRemoval`.

**Khi nào không xảy ra.** Nếu có `orphanRemoval = true` (mục 7.4), dòng thẻ bị xóa theo.

**Ví dụ.**

```java
inTx(() -> {
    V1Customer managedCustomer = em.find(V1Customer.class, customerId);
    managedCustomer.setLoyaltyCard(null);
    // [FLUSH] lúc commit: dirty checking thấy khóa ngoại đổi thành null
});
```

**Kết quả.** Một câu UPDATE `customer` đặt `loyalty_card_id` thành NULL. Không có câu DELETE nào cho `loyalty_card`.

| Bảng | Cột | Trước | Sau |
|---|---|---|---|
| customer | loyalty_card_id | id của thẻ | **NULL** |
| loyalty_card | số dòng | 1 | 1 |

Thay đổi: `customer.loyalty_card_id`. Giữ nguyên: dòng thẻ CARD-1 vẫn còn, chỉ là không ai trỏ tới.

**Vì sao.** Đổi quan hệ chỉ đổi khóa ngoại. Xóa dòng là một quyết định khác, và Hibernate chỉ làm khi bạn xin (cascade REMOVE, `orphanRemoval` hoặc `em.remove`).

::: {.ghi-nho}
Gỡ quan hệ chỉ đổi khóa ngoại. Muốn xóa dòng con phải xin tường minh: `orphanRemoval`, cascade REMOVE hoặc `em.remove`.
:::

::: {.tracuu codes="G6-S11"}
:::

### 7.2 Hai chiều và LAZY ở phía inverse {.bien-the}

Muốn đi từ customer sang thẻ **và** từ thẻ sang customer, ta khai báo hai chiều: một phía là owning side (giữ khóa ngoại), phía kia là inverse side với `mappedBy`. Ở mục 4.7 bạn đã thấy mẫu này với `OneToMany`. Với `OneToOne` có một khác biệt lớn: `fetch = LAZY` ở phía inverse **bị Hibernate bỏ qua**.

Cách thứ nhất: khóa ngoại ở thẻ, customer là inverse side.

```java
@Entity(name = "G6V3_Customer")
public class V3Customer {

    // INVERSE side: không có cột nào ở bảng customer.
    // fetch = LAZY khai báo nhưng Hibernate KHÔNG tôn trọng được.
    @OneToOne(mappedBy = "customer", fetch = FetchType.LAZY)
    private V3LoyaltyCard loyaltyCard;
}

@Entity(name = "G6V3_LoyaltyCard")
public class V3LoyaltyCard {

    // OWNING side: cột customer_id nằm ở bảng loyalty_card.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private V3Customer customer;
}
```

Cách thứ hai (khóa ngoại ở customer, thẻ là inverse side) có cấu trúc y hệt, chỉ đổi vai trò hai entity.

![Sơ đồ bảng của cách thứ nhất: loyalty_card.customer_id trỏ sang customer.id, bảng customer không có cột về thẻ](img/g6-v3.png){width=80%}

![Sơ đồ bảng của cách thứ hai: customer.loyalty_card_id trỏ sang loyalty_card.id, bảng loyalty_card không có cột về customer](img/g6-v4.png){width=80%}

**Ưu điểm.** Đi được cả hai chiều: từ customer lấy thẻ, từ thẻ lấy customer, không cần viết query.

**Nhược điểm.** Phía inverse luôn bị tải ngay, dù bạn khai báo `LAZY`. Với truy vấn nhiều customer, bạn nhận N+1 mà không hề đụng vào thẻ. Thêm nữa, quên gán ở owning side thì không có gì được ghi.

**Nên dùng khi.** Chỉ khi nghiệp vụ thật sự cần đi ngược chiều và bạn chấp nhận chi phí tải thêm. Nếu không, hãy giữ một chiều.

#### 7.2.1 find customer vẫn tải thẻ dù khai báo LAZY, và JPQL sinh N+1 {.cot-loi}

**Bối cảnh.** Màn hình danh sách khách hàng chỉ hiển thị tên. Bạn đã khai báo `LAZY` trên `loyaltyCard` để khỏi tải thẻ. Nhưng log SQL cho thấy với mỗi customer lại có thêm một câu SELECT vào bảng thẻ.

**Khi nào xảy ra.** Khóa ngoại ở bảng thẻ, và customer là inverse side của `@OneToOne`.

**Khi nào không xảy ra.** Nếu bạn đứng ở phía owning side (mục 7.2.2), hoặc bật bytecode enhancement (Chương 20).

**Ví dụ.**

```java
inTx(() -> {
    V3Customer customerAn = em.find(V3Customer.class, customerId);
    // [LIFECYCLE] customerAn: MANAGED
});

inTx(() -> {
    List<V3Customer> customers = em.createQuery(
            "select c from G6V3_Customer c", V3Customer.class)
        .getResultList();
});
```

**Kết quả.** `find` chạy hai câu SELECT riêng (không phải join):

```sql
select vc1_0.id, vc1_0.name
from g6_v3_bidi_owner_card.customer vc1_0
where vc1_0.id=?

select vlc1_0.id, vlc1_0.card_no, vlc1_0.customer_id
from g6_v3_bidi_owner_card.loyalty_card vlc1_0
where vlc1_0.customer_id=?
```

Thẻ trả về đã được khởi tạo sẵn, không phải proxy chưa tải. Với JPQL lấy năm customer: một câu SELECT customer và năm câu SELECT `loyalty_card`, tổng sáu câu, dù code không hề đụng vào thẻ.

**Vì sao.** Hibernate muốn đặt vào `customer.loyaltyCard` một trong hai thứ: `null` (không có thẻ) hoặc một proxy (có thẻ). Để biết là cái nào, nó cần biết **có dòng thẻ trỏ về customer này hay không**. Bảng customer không có cột nào nói điều đó. Cách duy nhất là hỏi bảng thẻ ngay lập tức. Và khi đã hỏi rồi thì có luôn dữ liệu thẻ, nên không còn gì để "lười" nữa.

**Cách tránh.** Dùng owning side để đi (mục 7.2.2), dùng `@MapsId` và `find` theo id (mục 7.3), hoặc không khai báo phía inverse. Truy vấn danh sách thì `join fetch` (Chương 11).

::: {.ghi-nho}
Hibernate chỉ tạo được proxy khi biết chắc có dòng, và chỉ cột khóa ngoại mới cho biết điều đó. Phía inverse không có cột khóa ngoại nên `LAZY` không có tác dụng.
:::

::: {.tracuu codes="G6-S03a,G6-S03b"}
:::

#### 7.2.2 Lật khóa ngoại sang customer: phía owning LAZY đúng, phía inverse thì không {.cot-loi}

**Bối cảnh.** Sau khi gặp N+1 ở mục 7.2.1, bạn thử đặt khóa ngoại ở customer (`customer.loyalty_card_id`) và để `LoyaltyCard.customer` là inverse side. Bạn muốn biết `LAZY` có hoạt động ở nơi mới không.

**Khi nào xảy ra.** Phía **có cột khóa ngoại** (customer) tải lười đúng. Phía **không có cột** (thẻ) bị tải ngay.

**Khi nào không xảy ra.** Nếu khóa ngoại ở bảng thẻ (mục 7.2.1), hai phía đổi vai: customer bị tải ngay, thẻ lười.

**Ví dụ.**

```java
inTx(() -> {
    V4Customer customerAn = em.find(V4Customer.class, customerId);
    // chưa chạm vào thẻ
    String cardNo = customerAn.getLoyaltyCard().getCardNo();
    // chạm vào dữ liệu thẻ: lúc này mới SELECT loyalty_card
});

inTx(() -> {
    V4LoyaltyCard card = em.find(V4LoyaltyCard.class, cardId);
    // đứng từ thẻ nhìn sang customer
});
```

**Kết quả.** Với `find(customer)`: một câu SELECT `customer` và **không** có SELECT `loyalty_card`. Thẻ là proxy chưa khởi tạo. Chỉ khi gọi `getCardNo()` mới có câu SELECT `loyalty_card` thứ nhất. Với `find(loyaltyCard)`: một câu SELECT thẻ, cộng một câu SELECT riêng `customer where loyalty_card_id = ?` để tìm customer đang trỏ tới thẻ, và customer đã khởi tạo sẵn.

**Vì sao.** Đọc cột `loyalty_card_id` của dòng customer là biết ngay có thẻ và id của nó, nên tạo được proxy. Thẻ thì không có cột nào về customer, nên phải đi hỏi bảng customer. Quy tắc chung: **khóa ngoại nằm ở đâu thì phía đó lười được, phía kia thì không.**

::: {.ghi-nho}
Khóa ngoại nằm ở đâu thì phía đó tải lười được, phía kia luôn bị tải ngay.
:::

::: {.tracuu codes="G6-S04a,G6-S04b"}
:::

#### 7.2.3 Chỉ gán phía inverse thì không có gì được ghi {.cot-loi}

**Bối cảnh.** Bạn viết `customer.setLoyaltyCard(card)` và quên `card.setCustomer(customer)`. Trong bộ nhớ hai object đã trỏ vào nhau, nên mọi thứ trông ổn. Nhưng sau khi commit, thẻ vẫn không thuộc customer nào.

**Khi nào xảy ra.** Khóa ngoại ở bảng thẻ (owning side là `LoyaltyCard.customer`), bạn chỉ gán phía inverse.

**Khi nào không xảy ra.** Nếu bạn gán ở owning side (`card.setCustomer(customer)`).

**Ví dụ.**

```java
inTx(() -> {
    V3Customer managedCustomer = em.find(V3Customer.class, customerId);
    V3LoyaltyCard managedCard = em.find(V3LoyaltyCard.class, cardId);
    managedCustomer.setLoyaltyCard(managedCard); // inverse side
    // cố ý KHÔNG gọi managedCard.setCustomer(managedCustomer)
});
```

**Kết quả.** Không có câu UPDATE nào cho `loyalty_card`.

| Bảng loyalty_card | card_no | customer_id |
|---|---|---|
| Trước | CARD-1 | NULL |
| Sau | CARD-1 | NULL |

Thay đổi: không có. Giữ nguyên: `customer_id` vẫn NULL.

**Vì sao.** Giống mục 4.7: Hibernate chỉ đọc khóa ngoại từ owning side. Phía inverse chỉ là "hình chiếu" để đọc, không phải nơi ghi. Điều này nhất quán với mọi quan hệ hai chiều, không riêng gì `OneToOne`.

**Cách tránh.** Viết một method helper gán cả hai phía, như `addItem` ở Chương 4.

::: {.ghi-nho}
Chỉ owning side ghi khóa ngoại. Gán một phía inverse trong Java không đổi gì trong database.
:::

::: {.tracuu codes="G6-S05"}
:::

### 7.3 MapsId: dùng chung khóa chính {.bien-the}

Có một cách khác để biểu diễn một-một mà không cần UNIQUE: để **khóa chính của bảng phụ chính là khóa ngoại**. Profile không có id riêng, `profile.id == customer.id`. Hibernate cho phép điều này bằng `@MapsId`.

```java
@Entity(name = "G6V5_CustomerProfile")
@Table(schema = "g6_v5_mapsid_uni", name = "customer_profile")
public class V5CustomerProfile {

    // KHÔNG có @GeneratedValue: id được SUY RA từ customer.
    @Id
    private Long id;

    // OWNING side: customer_id vừa là FK vừa là PRIMARY KEY.
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "customer_id")
    private V5Customer customer;

    @Column(name = "bio")
    private String bio;
}
```

`@MapsId` không có tham số nghĩa là: "id của tôi bằng id của entity được trỏ tới". Biến thể thứ hai thêm phía inverse `Customer.profile` (`mappedBy`, `LAZY`) để đi ngược chiều.

![Bảng customer_profile chỉ có cột customer_id vừa là khóa chính vừa là khóa ngoại trỏ sang customer.id, và cột bio](img/g6-v5.png){width=80%}

**Ưu điểm.** Một-một được bảo đảm bởi **khóa chính**, không cần UNIQUE riêng. Không tốn sequence và không có cột id thừa. Lấy profile chỉ cần `find(CustomerProfile.class, customerId)`: một câu SELECT, không join.

**Nhược điểm.** Profile không thể tồn tại trước customer, và không thể đổi customer của một profile (vì sẽ đổi khóa chính). Nếu map ngược `mappedBy` thì lại gặp lại vấn đề của mục 7.2.

**Nên dùng khi.** Thực thể phụ **sinh ra cùng và chết cùng** thực thể chính (hồ sơ mở rộng, cấu hình riêng, ảnh đại diện). Ưu tiên `@MapsId` một chiều rồi `find` theo id, thay vì map ngược `@OneToOne(mappedBy)`.

#### 7.3.1 DDL và id được suy ra từ customer {.cot-loi}

**Bối cảnh.** Bạn thêm bảng profile và thấy không có sequence, không có cột id. Bạn tự hỏi id của profile do đâu mà có, và tìm profile bằng id nào.

**Khi nào xảy ra.** Mapping có `@Id` không kèm `@GeneratedValue`, cộng `@OneToOne @MapsId`.

**Khi nào không xảy ra.** Nếu bạn dùng `@OneToOne @JoinColumn` thông thường (mục 7.1), profile có id riêng và cột `customer_id` riêng.

**Ví dụ.**

```java
V5CustomerProfile profileOfAn = inTxReturning(() -> {
    V5Customer managedCustomer = em.find(V5Customer.class, customerId);
    V5CustomerProfile profile =
        new V5CustomerProfile(managedCustomer, "Thích cà phê");
    // [LIFECYCLE] profile: TRANSIENT, id còn null
    em.persist(profile);
    // [LIFECYCLE] profile: TRANSIENT thành MANAGED, id = id của customer
    return profile;
});
// [TX END] profile: DETACHED

inTx(() -> em.find(V5CustomerProfile.class, customerId));
```

**Kết quả.** DDL:

::: {.ddl schema="g6_v5_mapsid_uni" tables="customer_profile"}
:::

Bảng `customer_profile` chỉ có `customer_id` (khóa chính và khóa ngoại sang `customer`) và `bio`. Schema chỉ có sequence của customer. Dữ liệu sau persist:

| customer_id | bio |
|---|---|
| id của An | Thích cà phê |

Lúc `find` theo id customer: **một** câu SELECT `customer_profile`, không có SELECT `customer` (customer là `LAZY` và chưa ai dùng).

**Vì sao.** `@MapsId` bảo Hibernate lấy id từ quan hệ `customer`. Khi `persist`, nó đọc id của customer đang MANAGED và gán vào `profile.id`. Vì id và khóa ngoại là một cột, chỉ cần một cột để biểu diễn cả hai.

::: {.ghi-nho}
Với `@MapsId`, profile không bao giờ có id riêng: `profile.id == customer.id`. Muốn lấy profile, tìm bằng id của customer.
:::

::: {.tracuu codes="G6-S06a,G6-S06b"}
:::

#### 7.3.2 Spring Data save với id null vẫn đi theo persist {.cot-loi}

**Bối cảnh.** Bạn dùng `repository.save(profile)` và lo rằng Spring Data sẽ chọn nhầm `merge` (vì id của profile sắp được suy ra từ customer, nhưng lúc gọi `save` còn null).

**Khi nào xảy ra.** Id của profile là `null` lúc gọi `save`.

**Khi nào không xảy ra.** Nếu id đã được gán sẵn (như khóa ghép ở Chương 9), Spring Data coi entity là "không mới" và đi theo `merge`.

**Ví dụ.**

```java
inTx(() -> {
    V5Customer managedCustomer = em.find(V5Customer.class, customerId);
    profileRepository.save(
        new V5CustomerProfile(managedCustomer, "Thích cà phê"));
});
```

**Kết quả.** Một câu INSERT `customer_profile` và **không** có câu SELECT `customer_profile` nào trước đó. (Chỉ có SELECT customer do `em.find` ở dòng đầu.)

**Vì sao.** Spring Data coi entity là "mới" khi `id == null`, nên gọi `persist`. `merge` sẽ phải SELECT trước để kiểm tra dòng tồn tại.

::: {.ghi-nho}
`save` với `id == null` đi theo `persist`, nên không có SELECT thừa trước INSERT.
:::

::: {.tracuu codes="G6-S06c"}
:::

#### 7.3.3 Profile thứ hai cho cùng một customer {.cot-loi}

**Bối cảnh.** Hai luồng xử lý cùng tạo profile cho An. Bạn muốn biết ai chặn được lỗi này, và chặn ở đâu.

**Khi nào xảy ra.** Hai profile cùng trỏ về một customer. Vì `@MapsId`, chúng có **cùng id**.

**Khi nào không xảy ra.** Nếu chỉ một luồng tạo profile.

**Ví dụ.**

```java
// Trường hợp 1: hai persist trong CÙNG một transaction
inTx(() -> {
    V5Customer managedCustomer = em.find(V5Customer.class, customerId);
    em.persist(new V5CustomerProfile(managedCustomer, "Profile 1"));
    em.persist(new V5CustomerProfile(managedCustomer, "Profile 2"));
});

// Trường hợp 2: profile thứ nhất đã commit, profile thứ hai ở transaction khác
inTx(() -> {
    V5Customer managedCustomer = em.find(V5Customer.class, customerId);
    em.persist(new V5CustomerProfile(managedCustomer, "Profile 2"));
});
```

**Kết quả.** Trường hợp 1: `EntityExistsException` ngay ở lần `persist` thứ hai. Database không nhận được gì, không có dòng nào. Trường hợp 2: INSERT được gửi đi và database từ chối vì trùng khóa chính `customer_id`, Spring dịch thành `DataIntegrityViolationException`. Sau rollback, bảng chỉ còn "Profile 1".

**Vì sao.** Trường hợp 1: persistence context đảm bảo **một id chỉ ứng với một entity MANAGED**. Hai profile cùng id thì cái sau bị từ chối, trước khi chạm database. Trường hợp 2: persistence context mới không biết gì về profile cũ, nên cho qua. Lúc này chính khóa chính của database là hàng rào cuối.

::: {.ghi-nho}
Với `@MapsId`, hai profile của một customer có cùng id: persistence context chặn trong cùng transaction, khóa chính của database chặn giữa các transaction.
:::

::: {.tracuu codes="G6-S07a,G6-S07b"}
:::

#### 7.3.4 Map ngược hay không: find customer có tải profile không {.cot-loi}

**Bối cảnh.** Ở mục 7.2.1 bạn thấy phía inverse luôn bị tải. Bạn hỏi: với `@MapsId`, có thoát được không?

**Khi nào xảy ra.** Có khai báo phía inverse `Customer.profile` (`mappedBy`, `LAZY`).

**Khi nào không xảy ra.** Nếu không khai báo phía inverse, `find(customer)` chỉ một câu SELECT.

**Ví dụ.**

```java
// Biến thể một chiều: Customer không biết về profile
inTx(() -> em.find(V5Customer.class, customerId));
inTx(() -> em.find(V5CustomerProfile.class, customerId));

// Biến thể có map ngược: Customer.profile là inverse side
inTx(() -> {
    V6Customer managedCustomer = em.find(V6Customer.class, customerId);
});
```

**Kết quả.** Một chiều: `find(customer)` một câu SELECT `customer`, không đụng `customer_profile`. Muốn profile thì `find(CustomerProfile.class, customerId)`, đúng một câu SELECT. Có map ngược: `find(customer)` chạy **thêm** một câu `SELECT customer_profile where customer_id = ?` và profile đã khởi tạo sẵn.

**Vì sao.** `@MapsId` không thay đổi nguyên nhân gốc: bảng customer không có cột nào cho biết có profile hay không. Phía inverse luôn phải hỏi bảng bên kia. Điểm hay của `@MapsId` là **id chung**, nên bạn không cần map ngược: `find` theo id customer là xong.

**Cách tránh.** Đừng map ngược. Lấy profile bằng `find(CustomerProfile.class, customerId)`.

::: {.ghi-nho}
Không cần map ngược với `@MapsId`: lấy profile bằng `find(Profile, customerId)`.
:::

::: {.tracuu codes="G6-S08a,G6-S08b"}
:::

#### 7.3.5 MapsId đi cùng GeneratedValue: bẫy im lặng {.mo-rong}

**Bối cảnh.** Một người trong nhóm quen thói thêm `@GeneratedValue` vào mọi `@Id`, kể cả id của profile. Bạn muốn biết Hibernate có báo lỗi không.

**Khi nào xảy ra.** Id của entity vừa có `@MapsId` vừa có `@GeneratedValue`.

**Khi nào không xảy ra.** Nếu id chỉ có `@MapsId`.

**Ví dụ.** Trong test, entity cố ý sai được dựng riêng trong một SessionFactory độc lập (không nằm trong entity scan của Spring), rồi persist một profile.

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)   // mâu thuẫn với @MapsId
private Long id;

@OneToOne(fetch = FetchType.LAZY)
@MapsId
@JoinColumn(name = "customer_id")
private V5Customer customer;
```

**Kết quả.** Hibernate khởi động bình thường, không có lỗi. Persist chạy được: dòng được INSERT với `customer_id` đúng bằng id của customer; `@GeneratedValue` không sinh ra giá trị nào khác.

**Vì sao.** Hibernate không kiểm tra sự mâu thuẫn ở bước dựng metadata, và khi persist, `@MapsId` thắng. Mâu thuẫn bị bỏ qua trong im lặng. Đó là bẫy: đọc code tưởng id tự sinh, thực tế id do customer quyết định.

::: {.khac-du-doan codes="G6-S13a"}
**Điều nhiều người nghĩ:** `@MapsId` cộng `@GeneratedValue` trên cùng id là mapping sai, Hibernate sẽ báo lỗi ngay lúc khởi động.

**Thực tế:** Hibernate dựng metadata thành công, không có lỗi nào. Đây không phải trường hợp "khởi động thất bại".
:::

::: {.ghi-nho}
`@MapsId` thắng `@GeneratedValue` trong im lặng. Hibernate không báo lỗi, nên chỉ code review mới bắt được.
:::

::: {.tracuu codes="G6-S13a,G6-S13b"}
:::

### 7.4 orphanRemoval một-một {.bien-the}

`orphanRemoval` ở Chương 6 hoạt động với collection. Với `@OneToOne`, nó hoạt động theo cùng nguyên tắc: nếu bạn gỡ thẻ khỏi customer (gán `null` hoặc gán thẻ khác), thẻ cũ là mồ côi và bị xóa. Biến thể này thêm `cascade = ALL` và `orphanRemoval = true` vào phía inverse.

```java
@Entity(name = "G6V7_Customer")
public class V7Customer {

    // INVERSE side: FK loyalty_card.customer_id do V7LoyaltyCard.customer quản lý.
    // KHÁC BIỆT: cascade = ALL và orphanRemoval = true.
    @OneToOne(mappedBy = "customer", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private V7LoyaltyCard loyaltyCard;
}
```

![Hai bảng customer và loyalty_card; loyalty_card.customer_id trỏ sang customer.id và có UNIQUE](img/g6-v7.png){width=80%}

**Ưu điểm.** Gỡ thẻ là xóa dòng thẻ, không để lại thẻ mồ côi. Thay thẻ cũ bằng thẻ mới chạy được trong một lần flush mà không đụng UNIQUE.

**Nhược điểm.** Xóa dữ liệu "ngầm": một dòng `setLoyaltyCard(null)` có thể xóa thẻ cùng lịch sử điểm. Cần chắc rằng thẻ không có nơi nào khác dùng.

**Nên dùng khi.** Thực thể phụ chỉ có nghĩa khi gắn với thực thể chính, và bạn muốn vòng đời của chúng đi cùng nhau.

#### 7.4.1 setLoyaltyCard null: có và không có orphanRemoval {.cot-loi}

**Bối cảnh.** Khách An hủy thẻ. Code gọi `customer.setLoyaltyCard(null)`. Có mapping thì thẻ bị xóa, có mapping thì không, và hai trường hợp nhìn code gần như giống hệt nhau.

**Khi nào xảy ra.** Có `orphanRemoval = true` trên phía inverse: dòng thẻ bị DELETE lúc flush.

**Khi nào không xảy ra.** Không có `orphanRemoval` (mapping ở mục 7.2): không có SQL ghi nào, thẻ vẫn trỏ về customer.

**Ví dụ.**

```java
// Có orphanRemoval
inTx(() -> {
    V7Customer managedCustomer = em.find(V7Customer.class, customerId);
    V7LoyaltyCard oldCard = managedCustomer.getLoyaltyCard();
    // [LIFECYCLE] oldCard: MANAGED
    managedCustomer.setLoyaltyCard(null);
    // [FLUSH] lúc commit: thẻ có trong snapshot nhưng không còn
    //         trong property, nên là orphan và bị DELETE
});

// Không có orphanRemoval
inTx(() -> {
    V3Customer managedCustomer = em.find(V3Customer.class, customerId);
    managedCustomer.setLoyaltyCard(null); // inverse side: không ghi gì
});
```

**Kết quả.** Có `orphanRemoval`: một câu DELETE `loyalty_card`; bảng thẻ trống, customer vẫn còn.

| Bảng | Trước | Sau |
|---|---|---|
| loyalty_card | 1 dòng (CARD-1) | **0 dòng** |
| customer | 1 dòng (An) | 1 dòng (An) |

Thay đổi: dòng thẻ biến mất. Giữ nguyên: customer.

Không có `orphanRemoval`: không có câu UPDATE hay DELETE nào cho `loyalty_card`, và `customer_id` vẫn trỏ về An.

**Vì sao.** `orphanRemoval` so sánh giá trị property với **bản snapshot** lúc tải. Thẻ có trong snapshot mà không còn trong property thì là mồ côi, và nó hoạt động dù đây là inverse side. Nếu không có `orphanRemoval`, phía inverse không ghi gì, nên khóa ngoại ở thẻ không đổi. Muốn tách thật mà không xóa thì gán `card.setCustomer(null)` ở owning side.

::: {.ghi-nho}
`orphanRemoval` so sánh với snapshot và hoạt động cả ở inverse side. Thiếu nó thì `setLoyaltyCard(null)` ở phía inverse không ghi gì.
:::

::: {.tracuu codes="G6-S09a,G6-S09b"}
:::

#### 7.4.2 Thay thẻ cũ bằng thẻ mới trong một lần flush {.mo-rong}

**Bối cảnh.** Khách An đổi thẻ: thẻ cũ bị hủy, thẻ mới được cấp, trong cùng một transaction. Cột `customer_id` có UNIQUE. Nếu Hibernate INSERT thẻ mới trước khi DELETE thẻ cũ thì sẽ vi phạm UNIQUE (như collection ở mục 6.5).

**Khi nào xảy ra.** `orphanRemoval = true` trên `@OneToOne`, UNIQUE trên cột khóa ngoại, thẻ mới được gán thay thẻ cũ.

**Khi nào không xảy ra.** Với collection (mục 6.5), thứ tự ngược lại: INSERT trước DELETE.

**Ví dụ.**

```java
inTx(() -> {
    V7Customer managedCustomer = em.find(V7Customer.class, customerId);
    V7LoyaltyCard newCard = new V7LoyaltyCard("CARD-NEW");
    newCard.setCustomer(managedCustomer);
    managedCustomer.setLoyaltyCard(newCard);
    // biến thể: em.persist(newCard) tường minh trước khi gán
});
```

**Kết quả.** Không có lỗi. Câu DELETE thẻ cũ chạy **trước** câu INSERT thẻ mới, kể cả khi bạn `em.persist(newCard)` tường minh:

```sql
delete
from g6_v7_orphan.loyalty_card
where id=?

insert into g6_v7_orphan.loyalty_card (card_no, customer_id, id)
values (?, ?, ?)
```

| Bảng loyalty_card | card_no |
|---|---|
| Trước | CARD-OLD |
| Sau | **CARD-NEW** |

Thay đổi: thẻ cũ bị thay bởi thẻ mới. Giữ nguyên: customer và số dòng của bảng thẻ (một dòng).

**Vì sao.** ActionQueue của Hibernate có hàng đợi riêng cho việc xóa orphan của quan hệ to-one, và hàng đợi này chạy **trước** mọi INSERT. Vì vậy UNIQUE của cột khóa ngoại không bị đụng, và bạn không cần `em.flush()` xen giữa.

::: {.khac-du-doan codes="G6-S10a,G6-S10b"}
**Điều nhiều người nghĩ:** thay thẻ trong một lần flush sẽ vi phạm UNIQUE, giống như thay phần tử trong collection ở Chương 6, vì INSERT của thẻ mới xếp hàng trước.

**Thực tế:** orphan removal của quan hệ to-one chạy trước INSERT, bất kể thẻ mới được persist bằng cách nào. Không có lỗi và không cần flush xen giữa.
:::

::: {.ghi-nho}
Orphan removal của quan hệ to-one chạy trước INSERT, nên thay thẻ trong một flush không đụng UNIQUE. Collection thì ngược lại.
:::

::: {.tracuu codes="G6-S10a,G6-S10b"}
:::

### 7.5 optional bằng false trên OneToOne

**Bối cảnh.** Nghiệp vụ nói "hồ sơ luôn có customer", và bạn muốn database và Hibernate cùng bảo đảm điều đó trên `@OneToOne`.

::: {.chua-kiem-chung codes="G6-S12"}
Lab **chưa có test** cho `optional = false` trên `@OneToOne`: tình huống này nằm ngoài phạm vi đã duyệt, nên cuốn sách không đưa kết quả. Thứ duy nhất đã được kiểm chứng là hành vi của `optional = false` trên `@ManyToOne` (mục 4.3): cột thành NOT NULL, Hibernate từ chối entity thiếu quan hệ **trước khi gửi SQL**, và khi tải thì dùng `inner join` thay cho `left join`.

Với `@OneToOne`, người ta kỳ vọng ba tác dụng giống `@ManyToOne` ở phía owning. Đặt `optional = false` ở phía **inverse** thì chưa rõ có giúp `LAZY` hay không: lý do không lười được (không có cột khóa ngoại, mục 7.2.1) vẫn còn nguyên. Hãy tự chạy thử trước khi dựa vào kỳ vọng này.
:::
