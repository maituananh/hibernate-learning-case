## Chương 9. Khóa chính ghép và khóa ngoại nhiều cột

Đến đây mọi khóa chính trong sách đều là một cột số. Thực tế có những bảng mà khóa chính tự nhiên gồm **hai cột trở lên**: kho được định danh bằng cặp (mã quốc gia, mã kho), dòng hàng của một chuyến vận chuyển được định danh bằng cặp (chuyến, số dòng). Khi khóa chính của bảng cha có hai cột thì khóa ngoại trỏ tới nó cũng phải có hai cột, và Hibernate cần biết cột nào ghép với cột nào.

Chương này dùng chuỗi ba tầng của miền vận chuyển: `Warehouse` (khóa ghép `country_code` và `code`) một-nhiều `Shipment`, `Shipment` một-nhiều `ShipmentLine` (khóa ghép `shipment_id` và `line_no`), `ShipmentLine` một-nhiều `ShipmentLineNote`. Riêng mục 9.4 dùng `Product` và `OrderItem` của một mô hình khác.

Trong chương này:

- Hai cách khai báo khóa ghép: `@EmbeddedId` và `@IdClass` (mục 9.1).
- Khóa ngoại hai cột với `@JoinColumns` (mục 9.2).
- Derived identity: một nửa khóa chính của con chính là khóa ngoại trỏ về cha (mục 9.3).
- Khóa ngoại trỏ vào một cột khác khóa chính, và cái giá của nó (mục 9.4).
- Vì sao không được sửa khóa chính (mục 9.5).

**Trong JPA thuần.** `@EmbeddedId`, `@IdClass`, `@JoinColumns` và `@MapsId` đều là JPA chuẩn. Riêng giao diện `Persistable` ở mục 9.3.3 là của Spring Data, không thuộc JPA và không thuộc Hibernate.

### 9.1 EmbeddedId và IdClass

::: {.kien-thuc-nen}
**Khóa chính ghép** là khóa chính gồm nhiều cột. JPA cho hai cách mô tả nó trong code Java:

- `@EmbeddedId`: gom các cột khóa vào một class riêng đánh dấu `@Embeddable` (ví dụ `WarehouseId`), entity có đúng một field `id` kiểu class đó.
- `@IdClass`: các cột khóa là các field `@Id` đặt thẳng trong entity; một class riêng (ví dụ `WarehouseKey`) chỉ dùng để đóng gói giá trị khi gọi `find`. Tên field của class đó phải trùng tên field `@Id`.

Class id của cả hai cách đều phải `Serializable` và có `equals`, `hashCode` theo giá trị (xem mục 8.5.2).

| | `@EmbeddedId` | `@IdClass` |
|---|---|---|
| Id trong entity | Một field `id` kiểu `@Embeddable` | Các field `@Id` nằm thẳng trong entity |
| DDL | Khóa chính hai cột, khóa ngoại ghép hai cột | **Giống hệt** |
| `@JoinColumns` ở bảng con | Như nhau (chỉ nói tên cột) | Như nhau |
| `find` | `find(Warehouse.class, new WarehouseId("VN", "HN01"))` | `find(Warehouse.class, new WarehouseKey("VN", "HN01"))` |
| Đọc một phần id | `warehouse.getId().getCountryCode()` | `warehouse.getCountryCode()` |
| Dùng với `@MapsId("field")` | **Có** (tên field trong embeddable) | Không |

Hai cách cho cùng database; khác nhau ở cách bạn viết code Java. Chương này dùng `@EmbeddedId` làm chính và `@IdClass` để đối chiếu.
:::

### 9.2 Khóa ngoại hai cột với JoinColumns {.bien-the}

Khi `Warehouse` có khóa ghép hai cột, `Shipment` phải trỏ tới nó bằng hai cột. `@JoinColumns` (số nhiều) nói cho Hibernate cột nào ghép với cột nào.

```java
@Entity(name = "G8J1_Warehouse")
@Table(schema = "g8_j1_explicit", name = "warehouse")
public class J1Warehouse {

    @EmbeddedId
    private J1WarehouseId id;

    @Column(name = "name", nullable = false)
    private String name;
}

@Embeddable
public class J1WarehouseId implements Serializable {

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "code", length = 10)
    private String code;
    // equals và hashCode theo giá trị của hai field
}

@Entity(name = "G8J1_Shipment")
@Table(schema = "g8_j1_explicit", name = "shipment")
public class J1Shipment {

    // OWNING side: hai cột wh_country và wh_code nằm ở BẢNG NÀY.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "wh_country",
                referencedColumnName = "country_code", nullable = false),
        @JoinColumn(name = "wh_code",
                referencedColumnName = "code", nullable = false)
    })
    private J1Warehouse warehouse;
}
```

`referencedColumnName` chỉ cột của bảng được trỏ tới. Nhờ nó, Hibernate biết `wh_country` ghép với `country_code` và `wh_code` ghép với `code`.

![Bảng warehouse có khóa chính ghép country_code và code; bảng shipment có hai cột wh_country và wh_code cùng thuộc một khóa ngoại trỏ về warehouse](img/g8-j1.png){width=85%}

**Ưu điểm.** Rõ ràng: người đọc thấy ngay cột nào ghép cột nào. Tên cột theo quy ước riêng của bạn thay vì tên mặc định dài dòng.

**Nhược điểm.** Dài dòng hơn `@JoinColumn` đơn. Mỗi quan hệ tới khóa ghép đều phải lặp lại bộ hai (hoặc nhiều) cột này.

**Nên dùng khi.** Bất cứ khi nào khóa chính của bảng được trỏ tới có từ hai cột. Mọi `@JoinColumn` bên trong phải ghi `referencedColumnName`, thiếu một cột là lỗi lúc khởi động (mục 9.2.5).

#### 9.2.1 Một khóa ngoại hai cột, không phải hai khóa ngoại rời {.cot-loi}

**Bối cảnh.** Kho ở Việt Nam có mã HN01 và kho ở Singapore cũng có mã HN01. Mã kho chỉ có nghĩa khi đi cùng mã quốc gia. Bạn cần chắc rằng database cũng hiểu cặp hai cột là một đơn vị, chứ không kiểm tra từng cột riêng lẻ.

**Khi nào xảy ra.** `@JoinColumns` trỏ tới khóa chính ghép hai cột.

**Khi nào không xảy ra.** Nếu bạn khai báo hai `@ManyToOne` riêng cho từng cột, bạn nhận hai khóa ngoại rời và mất bảo đảm về cặp.

**Ví dụ.** DDL của hai bảng liên quan:

::: {.ddl schema="g8_j1_explicit" tables="warehouse,shipment"}
:::

**Kết quả.** Bảng `shipment` có hai cột `wh_country` và `wh_code`, cả hai `NOT NULL`. Cả hai cột thuộc về **cùng một** ràng buộc khóa ngoại trỏ tới `warehouse(country_code, code)`, và khóa chính của `warehouse` cũng là hai cột. Cột thứ nhất ghép với `country_code`, cột thứ hai ghép với `code`, đúng như `referencedColumnName` đã khai.

**Vì sao.** Khóa ngoại phải khớp với khóa được trỏ tới. Nếu là hai khóa ngoại rời thì một chuyến hàng có thể trỏ tới cặp (SG, HN01) dù mỗi giá trị đều hợp lệ riêng lẻ ở đâu đó, nhưng cặp đó có thể không tồn tại. Một khóa ngoại ghép kiểm tra cả cặp.

::: {.ghi-nho}
Khóa chính ghép thì khóa ngoại trỏ tới nó cũng ghép: Hibernate sinh **một** ràng buộc hai cột, không phải hai khóa ngoại rời.
:::

::: {.tracuu codes="G8-S01a"}
:::

#### 9.2.2 INSERT ghi cả hai cột, query theo warehouse lọc thẳng trên hai cột {.cot-loi}

**Bối cảnh.** Hai chuyến hàng xuất phát từ hai kho cùng mã HN01 nhưng khác quốc gia. Bạn cần lọc đúng chuyến của kho Việt Nam và không lẫn với kho Singapore.

**Khi nào xảy ra.** `Shipment.warehouse` dùng `@JoinColumns` hai cột.

**Khi nào không xảy ra.** Nếu bạn lọc theo `code` đơn lẻ, kết quả lẫn cả hai quốc gia.

**Ví dụ.**

```java
inTx(() -> {
    J1Shipment shipmentVn = new J1Shipment("SHIP-VN", warehouseVn);
    J1Shipment shipmentSg = new J1Shipment("SHIP-SG", warehouseSg);
    em.persist(shipmentVn);
    em.persist(shipmentSg);
    // [FLUSH] lúc commit: INSERT hai chuyến hàng
});

inTx(() -> em.createQuery(
        "select s from G8J1_Shipment s where s.warehouse = :w",
        J1Shipment.class)
    .setParameter("w", warehouseVn)
    .getResultList());
```

**Kết quả.** Mỗi INSERT `shipment` ghi cả hai cột khóa ngoại lấy từ id của kho. Không có INSERT nào cho `warehouse`.

```sql
insert into g8_j1_explicit.shipment (reference, wh_code, wh_country, id)
values (?, ?, ?, ?)
```

| wh_country | wh_code | reference |
|---|---|---|
| VN | HN01 | SHIP-VN |
| SG | HN01 | SHIP-SG |

Query lọc theo kho cho đúng một chuyến (SHIP-VN), không lẫn SHIP-SG dù cùng mã kho, và **không có join** sang `warehouse`:

```sql
select js1_0.id, js1_0.reference, js1_0.wh_code, js1_0.wh_country
from g8_j1_explicit.shipment js1_0
where (js1_0.wh_code, js1_0.wh_country)=(?, ?)
```

**Vì sao.** Hibernate dịch `s.warehouse = :w` thành so sánh **tuple** (hai cột cùng lúc) ngay trên bảng `shipment`, vì hai cột khóa ngoại đã chứa đủ giá trị của khóa chính kho. Không cần join.

::: {.ghi-nho}
So sánh theo entity có khóa ghép được dịch thành so sánh tuple hai cột trên bảng con, không cần join.
:::

::: {.tracuu codes="G8-S01b"}
:::

#### 9.2.3 IdClass cho DDL giống hệt EmbeddedId {.cot-loi}

**Bối cảnh.** Dự án cũ của bạn dùng `@IdClass`. Bạn muốn biết chuyển sang `@EmbeddedId` có thay đổi database không, và ngược lại.

**Khi nào xảy ra.** Entity khai các field `@Id` trực tiếp và `@IdClass` trỏ tới class đóng gói giá trị.

**Khi nào không xảy ra.** Không có: hai cách luôn cho cùng database.

**Ví dụ.**

```java
@Entity(name = "G8J3_Warehouse")
@Table(schema = "g8_j3_idclass", name = "warehouse")
@IdClass(J3WarehouseKey.class)
public class J3Warehouse {

    @Id
    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Id
    @Column(name = "code", length = 10)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;
}

inTx(() -> {
    J3Warehouse found = em.find(J3Warehouse.class,
            new J3WarehouseKey("VN", "HN01"));
});
```

**Kết quả.** Khóa chính vẫn là hai cột `country_code` và `code`. Bảng `shipment` vẫn có hai cột `wh_country` và `wh_code` thuộc **một** ràng buộc khóa ngoại ghép, `@JoinColumns` viết giống hệt biến thể `@EmbeddedId`. Cột `wh_country` lấy giá trị từ `countryCode`, `wh_code` lấy từ `code`. `find` nhận một object `WarehouseKey` cùng giá trị (so sánh theo `equals`) và trả về đúng kho.

**Vì sao.** `@EmbeddedId` và `@IdClass` chỉ khác nhau ở cách mô tả khóa trong Java. Hibernate cuối cùng đều nhìn thấy "khóa chính gồm hai cột có kiểu này", và sinh DDL giống nhau.

::: {.ghi-nho}
`@EmbeddedId` và `@IdClass` cho database y hệt nhau. Chọn theo sở thích viết code, và nhớ rằng `@MapsId("field")` chỉ đi với `@EmbeddedId`.
:::

::: {.tracuu codes="G8-S01c"}
:::

#### 9.2.4 Không khai báo JoinColumns: tên cột mặc định {.cot-loi}

**Bối cảnh.** Bạn viết `@ManyToOne` trơn tới `Warehouse` có khóa ghép và không nghĩ gì thêm. Bạn muốn biết Hibernate đặt tên cột gì.

**Khi nào xảy ra.** `@ManyToOne` trỏ tới entity có khóa ghép, không có `@JoinColumns`.

**Khi nào không xảy ra.** Nếu bạn khai `@JoinColumns` tường minh (mục 9.2.1), tên cột theo ý bạn.

**Ví dụ.**

```java
// Không có @JoinColumns: mọi thứ để mặc định
@ManyToOne(fetch = FetchType.LAZY)
private J2Warehouse warehouse;
```

**Kết quả.** Hibernate vẫn chạy, vẫn sinh **một** khóa ngoại ghép hai cột. Tên cột là tên field cộng dấu gạch dưới cộng tên cột khóa đích: `warehouse_country_code` và `warehouse_code`, ghép đúng thứ tự với `country_code` và `code`. Vì không có `optional = false`, cả hai cột cho phép NULL. Tên kiểu `wh_country` không xuất hiện.

**Vì sao.** Khi không có `@JoinColumns`, Hibernate suy ra từng cột từ khóa đích theo quy ước đặt tên mặc định. Tên mặc định vẫn dùng được, nhưng muốn đặt theo quy ước riêng (như `wh_country`) thì phải khai `@JoinColumns`.

::: {.ghi-nho}
Không khai `@JoinColumns` vẫn chạy với tên cột mặc định `<field>_<cột đích>`. Muốn tên riêng thì khai tường minh.
:::

::: {.tracuu codes="G8-S02"}
:::

#### 9.2.5 Thiếu referencedColumnName ở một cột: lỗi lúc khởi động {.mo-rong}

**Bối cảnh.** Bạn khai `@JoinColumns` nhưng quên `referencedColumnName` ở cột thứ hai, vì "Hibernate sẽ tự đoán theo thứ tự". Bạn muốn biết ứng dụng có chạy được không.

**Ví dụ.** Entity cố ý sai, nằm ngoài entity scan của Spring và được dựng riêng trong test:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumns({
    @JoinColumn(name = "wh_country", referencedColumnName = "country_code"),
    @JoinColumn(name = "wh_code")   // thiếu referencedColumnName
})
private J1Warehouse warehouse;
```

**Kết quả.** Ứng dụng **không khởi động được**. Hibernate ném `MappingException`:

```text
org.hibernate.MappingException: Column with logical name 'null' in table
'warehouse' cannot be mapped to column identifier
```

**Vì sao.** Khi khóa ngoại có nhiều cột, Hibernate không đoán theo thứ tự. Tên logic `null` trong thông báo chính là `referencedColumnName` bị bỏ trống. Lỗi nằm ở bước dựng metadata chứ không phải lỗi database, nên bạn biết ngay lúc khởi động, không phải lúc chạy.

**Cách tránh.** Khi khóa ngoại có từ hai cột, mọi `@JoinColumn` bên trong `@JoinColumns` đều phải có `referencedColumnName`.

::: {.ghi-nho}
Thiếu `referencedColumnName` ở bất kỳ cột nào của khóa ngoại ghép đều là lỗi lúc khởi động. Hibernate không đoán theo thứ tự cột.
:::

::: {.tracuu codes="G8-S03"}
:::

### 9.3 Derived identity với MapsId theo field {.bien-the}

Ở mục 7.3, `@MapsId` làm toàn bộ khóa chính của profile bằng khóa ngoại. Ở đây, **một nửa** khóa chính của `ShipmentLine` là khóa ngoại trỏ về `Shipment`, nửa còn lại (`line_no`) do người dùng gán. Đây gọi là **derived identity**: một phần danh tính của con được suy ra từ cha. Với `@EmbeddedId`, bạn chỉ rõ phần nào của id lấy từ quan hệ nào bằng `@MapsId("tên field trong id")`.

```java
@Embeddable
public class J1ShipmentLineId implements Serializable {

    @Column(name = "shipment_id")
    private Long shipmentId;      // suy ra từ shipment nhờ @MapsId

    @Column(name = "line_no")
    private int lineNo;           // do người dùng gán
}

@Entity(name = "G8J1_ShipmentLine")
@Table(schema = "g8_j1_explicit", name = "shipment_line")
public class J1ShipmentLine {

    @EmbeddedId
    private J1ShipmentLineId id = new J1ShipmentLineId();

    // OWNING side: shipment_id vừa là một nửa khóa chính vừa là khóa ngoại.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("shipmentId")
    @JoinColumn(name = "shipment_id")
    private J1Shipment shipment;

    @Column(name = "product_name", nullable = false)
    private String productName;
}
```

![Ba bảng shipment, shipment_line và shipment_line_note; shipment_line có khóa chính ghép line_no và shipment_id, trong đó shipment_id cũng là khóa ngoại tới shipment](img/g8-j1-lines.png){width=90%}

**Ưu điểm.** Số dòng được đánh theo từng chuyến (dòng 1, 2, 3 của mỗi chuyến) thay vì một dãy số toàn cục. Không cần sequence riêng cho `shipment_line`, và khóa chính chặn luôn việc trùng số dòng trong cùng chuyến.

**Nhược điểm.** Id luôn "khác null một phần" ngay từ đầu (vì `lineNo` đã gán), nên Hibernate và Spring Data khó phân biệt entity mới và entity cũ. Bảng con của `ShipmentLine` phải trỏ bằng khóa ngoại hai cột.

**Nên dùng khi.** Con chỉ có nghĩa trong phạm vi cha và được đánh số theo cha (dòng hóa đơn, dòng chuyến hàng). Nếu không bắt buộc, id riêng (mục 8.5.3) đơn giản hơn.

#### 9.3.1 DDL: shipment_id vừa là khóa chính vừa là khóa ngoại {.cot-loi}

**Bối cảnh.** Bảng `shipment_line` không có cột `id`. Bạn muốn biết khóa chính của nó là gì, và `shipment_id` đóng vai gì.

**Khi nào xảy ra.** `@EmbeddedId` có một field được `@MapsId("...")` lấy từ quan hệ.

**Khi nào không xảy ra.** Nếu `ShipmentLine` có id riêng bằng sequence, bảng có cột `id`.

**Ví dụ.** DDL:

::: {.ddl schema="g8_j1_explicit" tables="shipment_line,shipment_line_note"}
:::

**Kết quả.** Cột `shipment_id` **vừa** nằm trong khóa chính **vừa** là khóa ngoại trỏ về `shipment(id)`. Không có cột `id` riêng và không có sequence riêng cho `shipment_line`. Khóa chính gồm đúng hai cột `line_no` và `shipment_id`.

::: {.khac-du-doan codes="G8-S04a"}
**Điều nhiều người nghĩ:** khóa chính là `(shipment_id, line_no)`, theo thứ tự khai báo trong class id.

**Thực tế:** khóa chính là `(line_no, shipment_id)`. Hibernate đặt cột derived (từ `@MapsId`) **sau** cột thường, không theo thứ tự khai báo. Chỉ thứ tự cột khác; `shipment_id` vẫn vừa là khóa chính vừa là khóa ngoại.
:::

**Vì sao.** `@MapsId` biến cột khóa ngoại thành một phần của khóa chính, nên không cần cột id riêng. Thứ tự cột trong khóa chính là chi tiết cài đặt của Hibernate, đừng dựa vào nó.

::: {.ghi-nho}
Với derived identity, khóa ngoại là một phần của khóa chính. Thứ tự cột trong khóa do Hibernate chọn, không theo thứ tự khai báo.
:::

::: {.tracuu codes="G8-S04a"}
:::

#### 9.3.2 persist tự điền shipmentId, nhưng trạng thái trước persist là DETACHED {.cot-loi}

**Bối cảnh.** Bạn tạo `new ShipmentLine(shipment, 1, "Bàn phím")`, gán sẵn số dòng. Bạn thắc mắc `shipmentId` có được điền không, và entity vừa tạo đang ở trạng thái nào.

**Khi nào xảy ra.** `lineNo` được gán sẵn, id embeddable còn `shipmentId` là `null`, không có `@Version`.

**Khi nào không xảy ra.** Với id sinh bằng sequence, entity mới có id `null` và hiển nhiên là TRANSIENT.

**Ví dụ.**

```java
inTx(() -> {
    J1Shipment managedShipment = em.find(J1Shipment.class, shipmentId);
    J1ShipmentLine lineOne =
        new J1ShipmentLine(managedShipment, 1, "Bàn phím");
    // [LIFECYCLE] lineOne: DETACHED (id đã khác null một phần)
    // shipmentId còn null
    em.persist(lineOne);
    // [LIFECYCLE] lineOne: MANAGED, shipmentId = id của shipment
    // [FLUSH] lúc commit: INSERT shipment_line
});
```

**Kết quả.** `persist` chạy được: một câu INSERT, không có SELECT nào. Trước `persist`, `shipmentId` còn `null`; ngay sau `persist`, nó bằng id của shipment.

| line_no | shipment_id | product_name |
|---|---|---|
| 1 | id của chuyến | Bàn phím |

::: {.khac-du-doan codes="G8-S04b"}
**Điều nhiều người nghĩ:** một object vừa `new` ra phải ở trạng thái TRANSIENT.

**Thực tế:** Hibernate báo trạng thái DETACHED. Id đã khác null (vì `lineNo` gán sẵn) và không có `@Version`, nên nó không phân biệt được mới hay cũ. `persist` vẫn chấp nhận và chạy bình thường.
:::

**Vì sao.** `@MapsId` copy id của shipment vào `shipmentId` **lúc persist**, không phải lúc `new`. Còn trạng thái do Hibernate suy ra từ id và `@Version`: id khác null cộng không có `@Version` đồng nghĩa "trông như đã từng lưu".

::: {.ghi-nho}
Với id ghép đã gán một phần, entity mới `new` có thể bị báo DETACHED. `persist` vẫn chạy và `@MapsId` điền phần còn lại.
:::

::: {.tracuu codes="G8-S04b"}
:::

#### 9.3.3 Spring Data save: merge kèm SELECT thừa, và cách sửa bằng Persistable {.cot-loi}

**Bối cảnh.** Bạn dùng `repository.save(line)` cho dòng hàng mới. Dữ liệu lưu đúng, nhưng log có thêm một câu SELECT mà bạn không viết, và object trả về lại khác object bạn truyền vào.

**Khi nào xảy ra.** Spring Data hỏi `isNew()`, và với id đã khác null, không có `@Version`, nó trả `false`, nên `save` đi theo **merge**.

**Khi nào không xảy ra.** Nếu entity cài `Persistable` và tự báo `isNew() = true`, `save` đi theo persist.

**Ví dụ.**

```java
// Không có Persistable
inTx(() -> {
    J1ShipmentLine lineOne =
        new J1ShipmentLine(managedShipment, 1, "Bàn phím");
    J1ShipmentLine returned = lineRepository.save(lineOne);
});

// Có Persistable
public class J1PShipmentLine implements Persistable<J1PShipmentLineId> {

    @Transient
    private boolean isNewEntity = true;

    @Override
    public boolean isNew() {
        return isNewEntity;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        isNewEntity = false;
    }
}
```

**Kết quả.** Không có `Persistable`: một câu SELECT `shipment_line` (kèm `left join` sang `shipment_line_note` do cascade) rồi một câu INSERT; object trả về **khác** object truyền vào:

```sql
select jsl1_0.line_no, jsl1_0.shipment_id, jsl1_0.product_name, n1_0.line_no,
    n1_0.shipment_id, n1_0.id, n1_0.text
from g8_j1_explicit.shipment_line jsl1_0
left join g8_j1_explicit.shipment_line_note n1_0
    on jsl1_0.line_no=n1_0.line_no and jsl1_0.shipment_id=n1_0.shipment_id
where (jsl1_0.line_no, jsl1_0.shipment_id) in ((?, ?))

insert into g8_j1_explicit.shipment_line (product_name, line_no, shipment_id)
values (?, ?, ?)
```

Có `Persistable`: **không** có SELECT, một câu INSERT, và object trả về chính là object truyền vào. Dữ liệu cuối cùng như nhau ở cả hai cách:

| line_no | shipment_id | product_name |
|---|---|---|
| 1 | id của chuyến | Bàn phím |

Thay đổi: không có khác biệt về dữ liệu. Khác biệt chỉ ở số câu SQL và object trả về.

**Vì sao.** `SimpleJpaRepository.save` hỏi `isNew()`; mặc định nó coi entity là mới khi id là `null` (hoặc `@Version` null). Id ghép đã gán `lineNo` nên không bao giờ `null`, nên đường đi là `merge`, mà `merge` phải SELECT để xem dòng đã có chưa. Với `Persistable`, entity tự khai báo. Cờ được hạ xuống sau khi persist hoặc tải (`@PostPersist`, `@PostLoad`) để lần `save` sau đi đúng đường cập nhật.

**Cách tránh.** Cài `Persistable` cho entity có id ghép gán tay, hoặc gọi `em.persist` trực tiếp.

::: {.ghi-nho}
Id ghép đã gán thì Spring Data `save` đi theo merge và SELECT thừa. Cài `Persistable` để entity tự nói "tôi là mới".
:::

::: {.tracuu codes="G8-S04c,G8-S04d"}
:::

#### 9.3.4 Chuỗi ba tầng và thứ tự xóa {.mo-rong}

**Bối cảnh.** `ShipmentLineNote` (ghi chú của dòng hàng) trỏ tới `ShipmentLine`, mà `ShipmentLine` có khóa ghép hai cột. Vậy khóa ngoại của note cũng hai cột. Bạn xóa cả chuyến hàng và muốn biết thứ tự SQL.

**Ví dụ.**

```java
inTx(() -> {
    em.remove(em.find(J1Shipment.class, shipmentId));
    // [CASCADE] REMOVE lan xuống line rồi note
    // [FLUSH] lúc commit: DELETE theo thứ tự con trước cha
});
```

**Kết quả.** Bảng `shipment_line_note` có **một** khóa ngoại gồm hai cột `(shipment_id, line_no)` trỏ tới `shipment_line`. Xóa chuyến hàng gửi các câu DELETE theo thứ tự con trước, cha sau:

```sql
delete
from g8_j1_explicit.shipment_line_note
where id=?

delete
from g8_j1_explicit.shipment_line
where line_no=? and shipment_id=?

delete
from g8_j1_explicit.shipment
where id=?
```

Warehouse không bị đụng tới (không có cascade). Cả ba bảng con đều trống sau khi xóa.

**Vì sao.** Mỗi tầng có khóa ngoại tới tầng trên, nên Hibernate phải xóa tầng dưới trước để không vi phạm khóa ngoại. Điều đáng chú ý: với khóa ghép, câu DELETE của `shipment_line` có điều kiện hai cột `where line_no=? and shipment_id=?`.

::: {.ghi-nho}
Khóa ghép lan xuống mọi tầng bên dưới: khóa ngoại hai cột ở mỗi cấp, và câu DELETE có điều kiện hai cột.
:::

::: {.tracuu codes="G8-S05"}
:::

### 9.4 Khóa ngoại trỏ vào natural key {.bien-the}

Khóa ngoại thường trỏ tới khóa chính. Nhưng JPA cho phép trỏ tới **một cột khác** của bảng cha, miễn cột đó có tính duy nhất, bằng `referencedColumnName`. Ví dụ: dòng đơn hàng lưu mã SKU của sản phẩm thay vì id số.

```java
@Entity(name = "G8J4_OrderItem")
@Table(schema = "g8_j4_natural_key_ref", name = "order_item")
public class J4OrderItem {

    // OWNING side: cột product_sku nằm ở bảng này và lưu giá trị SKU,
    // KHÔNG phải id của product.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_sku", referencedColumnName = "sku",
            nullable = false)
    private J4Product product;
}
```

![Bảng product có id là khóa chính và sku là cột duy nhất; bảng order_item có cột product_sku trỏ tới product.sku](img/g8-j4.png){width=85%}

**Ưu điểm.** Dữ liệu dễ đọc: nhìn bảng `order_item` thấy ngay mã SKU. Hữu ích khi tích hợp với hệ thống ngoài dùng mã nghiệp vụ.

**Nhược điểm.** Mất lợi ích của `LAZY` (mục 9.4.2), và khóa ngoại lưu chuỗi dài hơn số. Đổi SKU của sản phẩm kéo theo sửa mọi dòng đơn hàng.

**Nên dùng khi.** Chỉ khi có lý do tích hợp rõ ràng. Nếu đơn hàng được truy cập nhiều, hãy trỏ tới khóa chính để giữ được `LAZY`.

#### 9.4.1 DDL: khóa ngoại trỏ tới cột UNIQUE {.mo-rong}

**Bối cảnh.** Bạn trỏ `order_item` tới `product.sku` thay vì `product.id` và muốn biết database cần gì để chấp nhận.

**Ví dụ.** DDL:

::: {.ddl schema="g8_j4_natural_key_ref" tables="product,order_item"}
:::

**Kết quả.** Khóa ngoại là `order_item.product_sku` trỏ tới `product.sku`, không phải `product.id`. Cột `sku` phải có ràng buộc UNIQUE, nếu không PostgreSQL từ chối tạo khóa ngoại. Cột khóa ngoại mang kiểu của `sku` (chuỗi `varchar`), chứ không phải số.

**Vì sao.** Khóa ngoại phải trỏ tới thứ database bảo đảm duy nhất, và `referencedColumnName` nói thứ đó là `sku`.

::: {.ghi-nho}
Khóa ngoại có thể trỏ tới cột không phải khóa chính, miễn cột đó UNIQUE.
:::

::: {.tracuu codes="G8-S06a"}
:::

#### 9.4.2 LAZY vô hiệu khi khóa ngoại trỏ non-PK {.mo-rong}

**Bối cảnh.** Bạn khai `product` là `LAZY` để khỏi tải sản phẩm khi chỉ cần xem dòng đơn hàng. Nhưng log SQL cho thấy sản phẩm vẫn bị tải.

**Ví dụ.**

```java
inTx(() -> {
    J4OrderItem managedItem = em.find(J4OrderItem.class, itemId);
    // [LIFECYCLE] managedItem: MANAGED
    // chưa chạm vào product
});
```

**Kết quả.** `find` chạy hai câu SELECT: câu thứ nhất lấy `order_item`, câu thứ hai lấy `product` theo SKU, dù chưa ai chạm vào `product`:

```sql
select joi1_0.id, joi1_0.product_sku, joi1_0.quantity
from g8_j4_natural_key_ref.order_item joi1_0
where joi1_0.id=?

select jp1_0.id, jp1_0.name, jp1_0.sku
from g8_j4_natural_key_ref.product jp1_0
where jp1_0.sku=?
```

`product` đã được khởi tạo sẵn, không phải proxy chưa tải.

**Vì sao.** Proxy chỉ chứa **id** của entity. Nhưng cột khóa ngoại lưu `sku` chứ không phải id, nên Hibernate không có id để dựng proxy. Cách duy nhất là truy vấn `product` theo `sku` ngay lập tức. Hệ quả: trỏ khóa ngoại tới khóa không phải khóa chính làm mất `LAZY` và dễ gây N+1 khi tải nhiều dòng đơn hàng.

::: {.ghi-nho}
Proxy cần id. Khóa ngoại không lưu id thì `LAZY` không dựng được proxy và bị tải ngay.
:::

::: {.tracuu codes="G8-S06b"}
:::

### 9.5 Không được sửa khóa chính

#### 9.5.1 Sửa một phần id của entity đang MANAGED: im lặng, không có gì xảy ra {.mo-rong}

**Bối cảnh.** Mã kho HN01 bị nhập sai, cần đổi thành HN02. Một lập trình viên viết `warehouse.getId().setCode("HN02")` trong transaction và nghĩ Hibernate sẽ UPDATE khóa chính hoặc báo lỗi.

**Ví dụ.**

```java
inTx(() -> {
    J1Warehouse warehouse = em.find(J1Warehouse.class, warehouseId);
    // [LIFECYCLE] warehouse: MANAGED
    warehouse.getId().setCode("HN02");
    // [FLUSH] lúc commit: dirty checking không xét khóa chính
    // [TX END] commit
});
```

(Class id trong lab có setter chỉ để làm thí nghiệm này. Trong code thật, class id không nên có setter.)

**Kết quả.** Không có lỗi, không có câu SQL nào. Dữ liệu trong database **không đổi**:

| Bảng warehouse | code |
|---|---|
| Trước | HN01 |
| Sau | HN01 |

Thay đổi: không có. Giữ nguyên: toàn bộ dòng, kể cả cột `code`. Object trong bộ nhớ lại mang `HN02`, nên bộ nhớ và database đã lệch nhau.

::: {.khac-du-doan codes="G8-S07"}
**Điều nhiều người nghĩ:** Hibernate sẽ ném lỗi kiểu "identifier of an instance was altered" lúc flush.

**Thực tế:** không có lỗi, không có SQL, database không đổi. Thay đổi bị bỏ qua trong im lặng.
:::

**Vì sao.** Id embeddable bị sửa tại chỗ, nên Hibernate không thấy sự khác biệt giữa id "trước" và "sau" (cùng một object). Khóa chính cũng không nằm trong dirty checking, nên không sinh UPDATE. Kết quả tệ hơn một exception: dữ liệu không đổi mà không ai biết.

**Cách tránh.** Xem khóa chính là bất biến: id embeddable không có setter. Muốn đổi mã kho thì tạo dòng mới với mã mới rồi chuyển dữ liệu liên quan sang.

::: {.ghi-nho}
Khóa chính là bất biến. Sửa nó trên entity MANAGED không gây lỗi mà cũng không được lưu, và bộ nhớ lệch khỏi database.
:::

::: {.tracuu codes="G8-S07"}
:::
