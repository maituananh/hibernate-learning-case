## Chương 1. Mô hình mẫu và kiến thức tối thiểu

Cuốn sách này giải thích Hibernate bằng cách chạy thật: mỗi tình huống là một test chạy trên PostgreSQL, và sách in lại những gì test chứng minh. Để đọc được các chương sau, bạn cần biết ba thứ: bộ bảng mẫu dùng xuyên suốt, vài khái niệm nền tảng của entity và khóa chính, và cách đọc một "bằng chứng" của lab. Chương này gom cả ba, ngắn gọn, vì phần lớn bạn đã biết.

Trong chương này:

- Bộ bảng thương mại điện tử dùng ở hầu hết các chương (mục 1.1).
- Nhắc nhanh entity và các annotation cơ bản (mục 1.2).
- Khóa chính, khóa ngoại và cách Hibernate sinh id (mục 1.3).
- Cách lab chứng minh một tình huống, và cách đọc khung Tra cứu (mục 1.4).

### 1.1 Bộ bảng thương mại điện tử dùng xuyên suốt

Hầu hết ví dụ dùng cùng một domain: khách hàng đặt đơn, mỗi đơn có nhiều dòng hàng, mỗi dòng trỏ tới một sản phẩm. Khách hàng còn có các khoản thanh toán.

![Năm bảng: customer có nhiều purchase_order và nhiều payment; purchase_order có nhiều order_item; mỗi order_item trỏ tới một product](img/domain.png){width=95%}

Bảng đơn hàng tên là `purchase_order` chứ không phải `order`, vì `order` là từ khóa của SQL.

Có ba điều cần biết về cách lab tổ chức bảng:

- **Mỗi biến thể mapping nằm trong một schema PostgreSQL riêng** (ví dụ `g1_v4_bidi_mappedby`). Nhờ vậy hàng chục cách khai báo khác nhau cùng tồn tại mà không đụng nhau, và bạn so sánh được DDL của từng cách.
- **Tên entity luôn có tiền tố** (ví dụ `@Entity(name = "G1V4_Customer")`), vì tên entity phải duy nhất trong toàn bộ ứng dụng. Khi sách in JPQL, bạn sẽ thấy tên này.
- **Ngoài các bảng trên**, từng chủ đề có thêm bảng riêng khi cần (thẻ khách hàng, kho, thẻ tag...). Sách sẽ giới thiệu đúng lúc.

### 1.2 Nhắc nhanh entity và các annotation cơ bản

::: {.kien-thuc-nen}
Một entity là một class Java được Hibernate ánh xạ tới một bảng. Những annotation sau xuất hiện ở mọi ví dụ:

- `@Entity` đánh dấu class là entity. `@Table(schema, name)` chọn bảng.
- `@Id` đánh dấu field là khóa chính. `@Column(name, nullable)` chọn cột và cho biết cột có cho phép NULL không.
- `@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany` khai báo quan hệ giữa các entity (Phần II).
- Class entity cần một constructor không tham số (có thể là `protected`) để Hibernate tạo object khi đọc từ database.
:::

Điểm mấu chốt khi đọc sách: một entity mà bạn đang cầm trong tay luôn ở một trong bốn trạng thái, và trạng thái đó quyết định Hibernate có theo dõi, có ghi xuống database hay không. Chương 2 dành riêng cho chủ đề này.

### 1.3 Khóa chính, khóa ngoại và cách Hibernate sinh id

::: {.kien-thuc-nen}
**Khóa chính** (primary key) định danh một dòng. **Khóa ngoại** (foreign key) là một cột trỏ tới khóa chính của bảng khác, và database dùng nó để giữ tính toàn vẹn: không cho ghi một order trỏ tới customer không tồn tại.

Có ba cách phổ biến để có id cho entity mới:

- **SEQUENCE** (mặc định của lab): Hibernate xin id từ một sequence của PostgreSQL trước, rồi dùng id đó khi INSERT. Mặc định lab dùng `allocationSize = 50`: một lần xin lấy trước 50 id, nên không phải gọi sequence cho từng entity.
- **IDENTITY**: database tự sinh id lúc INSERT (cột tự tăng). Hibernate chỉ biết id sau khi INSERT đã chạy.
- **Gán tay**: ứng dụng tự đặt id (ví dụ mã SKU).

Sự khác nhau giữa SEQUENCE và IDENTITY có hậu quả lớn tới thời điểm câu INSERT chạy. Chương 2 so sánh trực tiếp, và Chương 18 bàn về ảnh hưởng của chúng tới hiệu năng khi ghi hàng loạt.
:::

### 1.4 Lab chứng minh một tình huống như thế nào

Mỗi tình huống trong sách được chứng minh bằng bốn loại bằng chứng, đều lấy từ test thật chứ không viết theo trí nhớ:

1. **DDL**: câu `create table` mà Hibernate sinh ra, cho biết cột và khóa ngoại nằm ở đâu.
2. **SQL đã chạy**: danh sách câu SQL mà Hibernate gửi tới database, được ghi lại bằng một bộ chặn (statement inspector). Từ đó đếm được bao nhiêu câu SELECT, INSERT, UPDATE, DELETE.
3. **Dữ liệu thật trong database**: được đọc bằng JDBC thuần, **không** đọc qua Hibernate. Lý do: Hibernate có cache trong bộ nhớ (cache cấp 1), nên đọc lại qua `find` có thể trả về dữ liệu cũ mà không hỏi database.
4. **Trạng thái của entity**: TRANSIENT, MANAGED, DETACHED hay REMOVED, tại từng bước.

Ba quy ước trong code ví dụ:

- Test **không dùng `@Transactional` trên cả method**. Mỗi bước dùng một khối `inTx(() -> { ... })` riêng, tức một transaction riêng. Nhờ đó bạn thấy rõ entity chuyển từ MANAGED sang DETACHED khi transaction kết thúc, và thấy rõ lúc nào flush, lúc nào commit.
- Code trong sách được **rút gọn** từ test: giữ phần chuẩn bị dữ liệu và thao tác, bỏ phần kiểm tra (assert) và lệnh in.
- Dữ liệu trong bảng ở sách **không in id cụ thể** khi id không quan trọng, vì id sinh từ sequence có thể khác giữa các lần chạy.

**Cách đọc một tình huống.** Các tình huống cốt lõi đều theo cùng một khuôn: Bối cảnh, Khi nào xảy ra, Khi nào không xảy ra, Ví dụ, Kết quả, Vì sao, rồi Ghi nhớ. Cuối mỗi tình huống có khung **Tra cứu trong repository**, cho biết thư mục entity, file test, tên method, và link tới Pull Request. Nếu muốn đọc kỹ hoặc chạy lại, bạn mở đúng file đó trong repository. Cách chạy lab nằm ở Phụ lục E.
