## Phụ lục B. Những kết quả khác dự đoán

Trước khi chạy test, lab ghi lại giả thuyết cho những tình huống mà kết quả chưa chắc (phụ thuộc phiên bản Hibernate hoặc hiếm gặp). Sau khi chạy trên PostgreSQL thật với Hibernate 7.4.15.Final, 25 tình huống cho kết quả **khác** giả thuyết (một số dòng dưới đây gộp hai tình huống gần nhau). Phụ lục này liệt kê từng trường hợp, vì đây là những chỗ trí nhớ và kinh nghiệm dễ dẫn bạn tới kết luận sai nhất.

Mỗi dòng gồm: điều nhiều người nghĩ, điều thực tế xảy ra, và mục trong sách có bằng chứng đầy đủ (SQL, dữ liệu thật). Trong sách, những chỗ này cũng được đánh dấu bằng khung "Kết quả khác dự đoán".

### Mapping quan hệ và vòng đời (Phần II)

| Điều nhiều người nghĩ | Điều thực tế xảy ra | Mục |
|---|---|---|
| `@JoinColumn(nullable = false)` chỉ ảnh hưởng DDL. Persist order không có customer thì câu INSERT vẫn được gửi và database từ chối. | Hibernate chặn **trước** khi gửi SQL: ném `PropertyValueException`, 0 câu INSERT. | 4.3 |
| Với `nullable = false`, Hibernate vẫn dùng `left join` khi `find` order, vì `nullable` chỉ là chuyện của DDL. | Hibernate dùng `inner join`. Chỉ khi để mặc định mới có `left join`. | 4.3 |
| `@OneToMany` một chiều với `@JoinColumn(nullable = false)` bỏ được câu UPDATE bù. | Vẫn còn 2 câu UPDATE bù. Hơn nữa, nếu persist order trước khi thêm vào collection thì INSERT bind `NULL` và PostgreSQL từ chối. | 4.6 |
| Thay một con bằng con mới có cùng giá trị unique, trong một lần flush, sẽ thành công vì orphanRemoval xóa con cũ trước. | Vi phạm unique: Hibernate chạy INSERT con mới **trước**, DELETE con mồ côi **sau**. Cần `flush` xen giữa. | 6.5 |
| Thay thẻ trong quan hệ một-một có orphanRemoval cũng vi phạm unique, giống collection. | Không vi phạm: với quan hệ to-one, DELETE thẻ cũ chạy **trước** INSERT thẻ mới. Gọi `persist` thẻ mới tường minh cũng không đổi kết quả. | 7.4 |
| `@MapsId` cộng `@GeneratedValue` trên cùng id sẽ báo lỗi lúc khởi động. | Không lỗi gì khi khởi động. `@GeneratedValue` bị bỏ qua, id lấy theo customer. | 7.3 |

### Khóa ghép (Chương 9)

| Điều nhiều người nghĩ | Điều thực tế xảy ra | Mục |
|---|---|---|
| Khóa chính ghép có thứ tự cột đúng như khai báo trong class id. | Cột derived (từ `@MapsId`) bị đặt **sau** cột thường: khóa chính là `(line_no, shipment_id)` chứ không phải `(shipment_id, line_no)`. | 9.3 |
| Entity mới tạo với id gán sẵn có trạng thái TRANSIENT. | Hibernate báo DETACHED (id khác null, không có `@Version` để phân biệt). `persist` vẫn chạy bình thường. | 9.3 |
| Sửa id embeddable của entity MANAGED sẽ báo lỗi "identifier altered" lúc flush. | Không lỗi, không câu SQL nào, database không đổi. Thay đổi bị bỏ qua im lặng. | 9.5 |

### Fetch, truy vấn và N+1 (Phần III)

| Điều nhiều người nghĩ | Điều thực tế xảy ra | Mục |
|---|---|---|
| `join fetch` hai `List` bị `MultipleBagFetchException` ngay lúc tạo query. | Tạo query vẫn thành công. Exception ném lúc **chạy** query, trước khi có câu SQL nào được gửi. | 11.5 |
| `join fetch` collection kèm `setMaxResults` làm Hibernate tải hết rồi cắt trong bộ nhớ, có cảnh báo. | Với Hibernate 7.4 trên PostgreSQL, không có cảnh báo: SQL bọc bảng cha trong subquery có `offset ... fetch first ...`, database cắt trang đúng. | 11.6 |
| `@EntityGraph` không tắt được EAGER của một collection khác. | Tắt được: attribute nằm ngoài graph bị coi là LAZY (ngữ nghĩa `fetchgraph`), nên chỉ còn 1 câu SELECT. | 11.7 |
| `join` thường (không fetch) trả mỗi customer nhiều lần, theo số order. Kinh nghiệm này đến từ Hibernate 5. | Hibernate 7 loại trùng cả root của `join` thường: 3 customer, không phải 6. Collection `orders` vẫn chưa được tải. | 12.1 |
| `Page` cộng `join fetch` collection: count tự sinh có thể báo lỗi. | Không lỗi nhưng **sai âm thầm**: đếm dòng sau join nên total là 6 thay vì 3. Cần viết `countQuery` tay. | 12.3 |
| Với `Specification` có `fetch`, lỗi giống trường hợp trên (count sai). | Khác hẳn: query dữ liệu chạy, query count ném `SemanticException`. Sửa bằng cách chỉ fetch khi kiểu kết quả không phải `Long`. | 12.4 |

### Ánh xạ cột và kế thừa (Chương 3, Chương 10)

| Điều nhiều người nghĩ | Điều thực tế xảy ra | Mục |
|---|---|---|
| Với `SINGLE_TABLE`, Hibernate tự nới cột của lớp con thành nullable. | Hibernate không tự nới: nếu bạn khai `nullable = false` ở lớp con thì DDL sinh `NOT NULL`, và INSERT của lớp con khác bị vỡ. | 10.3 |
| `AttributeConverter` cho kiểu mutable chỉ phát hiện sửa tại chỗ khi kiểu đó cài `equals` đúng. | Cả hai trường hợp (có và không có `equals`) đều sinh UPDATE trong thí nghiệm này. | 3.2 |

### Ghi dữ liệu, transaction và khóa (Phần IV)

| Điều nhiều người nghĩ | Điều thực tế xảy ra | Mục |
|---|---|---|
| Thêm item vào collection phía owning (`@JoinTable`, `List`) chỉ INSERT thêm một dòng bảng nối. | Bảng nối bị **recreate**: DELETE hết dòng nối của order rồi INSERT lại toàn bộ. Cộng thêm `UPDATE` bump version của cha. | 14.4 |
| Cách ghi "tạo object mới rồi merge" sinh nhiều SQL hơn cách "tải entity rồi sửa". | Số câu ghi giống hệt (Hibernate mặc định UPDATE mọi cột). Điểm khác là giá trị bind: các field không set bị ghi đè thành `NULL`. Chỉ đọc dữ liệu thật mới thấy lỗi. | 14.8 |
| Lưu bản cũ với `@Version` luôn ném exception của Spring. | Chỉ khi đi qua repository. Gọi thẳng `em.merge` ném `jakarta.persistence.OptimisticLockException`, vì không qua proxy của `@Repository` nên không được dịch. | 17.3 |
| `save` entity mới trong transaction `readOnly` thì không INSERT, hoặc lỗi lúc flush. | Lỗi sớm hơn flush: PostgreSQL cấm `nextval()` trong transaction read-only, mà id SEQUENCE cần `nextval` ngay lúc `persist`. | 16.2 |
| `PESSIMISTIC_WRITE` kèm `left join fetch` bị PostgreSQL từ chối `FOR UPDATE` trên phía nullable của outer join. | Không lỗi. Hibernate tách thành hai câu: câu một nối không khóa, câu hai khóa riêng bảng gốc. Item không được khóa. | 17.4 |

### Cache và sự cố (Phần V, Phần VI)

| Điều nhiều người nghĩ | Điều thực tế xảy ra | Mục |
|---|---|---|
| Query cache lưu id, rồi Hibernate SELECT từng entity nếu entity không được cache. | Không SELECT gì: query cache trả luôn dữ liệu đã khởi tạo đủ, dù entity không nằm trong cache cấp 2. | 19.5 |
| Flash sale không có `@Version` làm tồn kho thành số âm. | Kho **không** âm: kho ghi 4, nhưng đã bán 12 cho kho 10. Triệu chứng thật là bán quá số hàng và số liệu không khớp, không có exception. | 22.2 |

### Cách dùng bảng này

Khi gặp một hành vi lạ, hãy đối chiếu: nếu nó nằm trong bảng trên thì bạn không đơn độc, và sách đã có bằng chứng. Khi không nằm trong bảng, đừng tin trí nhớ: viết một test nhỏ theo khuôn của lab (Chương 1, mục 1.4) rồi quan sát SQL và dữ liệu thật.
