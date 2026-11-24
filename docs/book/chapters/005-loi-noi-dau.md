# Lời nói đầu: Cách đọc cuốn sách này

## Sách dành cho ai

Cuốn sách dành cho lập trình viên đã biết Java và Spring, đã từng dùng `@Entity` và repository, nhưng chưa thật vững chuyện mapping quan hệ: `@OneToMany` đặt `@JoinColumn` thì sinh SQL gì, `mappedBy` có tác dụng gì, cascade và `orphanRemoval` khác nhau chỗ nào, vì sao một trang danh sách lại chạy hàng trăm câu SELECT. Bạn không cần biết trước Hibernate. Bạn cần biết SQL ở mức `join`, khóa chính và khóa ngoại.

Sách cũng dành cho người đã dùng Hibernate vài năm và muốn thay trí nhớ, hay tin đồn, bằng bằng chứng. Phụ lục B liệt kê 25 chỗ mà kết quả thực tế khác điều người ta thường nghĩ, kể cả những chỗ khác với chính dự đoán của tác giả trước khi chạy test.

## Bốn loại bằng chứng

Sách này không viết theo trí nhớ. Mỗi tình huống đều là một test chạy trên PostgreSQL thật, và sách in lại những gì test chứng minh. Mỗi kết luận dựa trên bốn loại bằng chứng:

1. **DDL**: câu `create table` Hibernate sinh ra, cho biết cột và khóa ngoại nằm ở đâu.
2. **SQL đã chạy**: những câu SQL Hibernate thật sự gửi tới database, để đếm và xem thứ tự.
3. **Dữ liệu thật trong database**: đọc bằng JDBC thuần, không qua Hibernate, vì cache cấp 1 có thể nói dối.
4. **Trạng thái của entity** tại từng bước: TRANSIENT, MANAGED, DETACHED hoặc REMOVED.

## Cách đọc một tình huống

Một tình huống cốt lõi luôn đi theo cùng một khuôn. Dưới đây là một ví dụ đã thu nhỏ, dùng tình huống "persist không INSERT ngay" (Chương 2).

> **Bối cảnh.** Nhiều người cho rằng `persist` là lưu vào database. Hiểu sai điểm này dẫn tới những lỗi khó thấy.
>
> **Khi nào xảy ra.** Với id sinh bằng SEQUENCE, `persist` chỉ xin id; INSERT chạy lúc flush.
>
> **Khi nào không xảy ra.** Với IDENTITY, INSERT chạy ngay ở `persist`.
>
> **Ví dụ.** Đoạn code ngắn, `persist` rồi `flush`, kèm chú thích trạng thái từng dòng.
>
> **Kết quả.** Câu SQL đã chạy, bảng trạng thái từng bước, và dữ liệu cuối cùng.
>
> **Vì sao.** Hibernate hoãn SQL ghi để gom và sắp xếp; flush khác commit.
>
> Khung **Ghi nhớ** tóm tắt trong một hai câu. Khung **Tra cứu trong repository** chỉ tới test thật.

Thứ tự này không đổi, nên khi đã quen bạn có thể nhảy thẳng tới phần cần: nhìn **Kết quả** để biết điều gì xảy ra, nhìn **Vì sao** để hiểu cơ chế.

## Ý nghĩa của các nhãn và các khung

- **Cốt lõi** và **Mở rộng**: tình huống Cốt lõi được viết chi tiết đủ sáu phần. Tình huống Mở rộng viết ngắn gọn hơn, chỉ có Bối cảnh và Kết quả. Lần đầu đọc, bạn có thể bỏ qua các tình huống Mở rộng.
- **Ghi nhớ**: kết luận cần mang đi, ngắn gọn.
- **Kiến thức nền**: nhắc lại một khái niệm mà phần sau cần, dành cho người chưa quen.
- **Kết quả khác dự đoán**: kết quả thực tế khác điều giả thuyết ban đầu. Đây là những chỗ cần đọc kỹ nhất (tổng hợp ở Phụ lục B).
- **Chưa kiểm chứng trong lab**: điều sách nói dựa trên tài liệu hoặc kinh nghiệm, lab chưa có test chứng minh. Đừng coi nó chắc chắn như phần còn lại.
- **Trong JPA thuần**: cách viết tương đương khi không dùng API riêng của Hibernate hoặc của Spring (Chương 1, mục 1.5 và Phụ lục G).
- **Tra cứu trong repository**: nơi tìm entity, test và Pull Request của tình huống.

## Hai lộ trình đọc

**Người mới với Hibernate mapping.** Đọc theo thứ tự Phần I, Phần II, Phần III. Ba phần này (Chương 1 đến Chương 13) dạy mapping quan hệ, cascade, `orphanRemoval`, fetch và N+1, truy vấn. Phần IV trở đi (ghi dữ liệu, đồng thời, hiệu năng, thực chiến) đọc khi bạn đã vững ba phần đầu.

**Người đang gặp sự cố.** Mở Chương 22 (bốn sự cố production: danh sách chậm, tồn kho sai, job chạy hai lần, import chậm dần), mỗi sự cố đi theo trình tự tái hiện, chẩn đoán, sửa, rào chắn. Sau đó dùng Phụ lục C để tìm tình huống liên quan, và Phụ lục A để tra annotation đang nghi ngờ.

## Phiên bản và phạm vi

Mọi kết quả trong sách được xác minh với Hibernate 7.4.15.Final, PostgreSQL 17, Java 21 và Spring Boot, trên Testcontainers. Với phiên bản khác, hành vi có thể khác; cách chắc chắn nhất là chạy lại test tương ứng (Phụ lục E). Phụ lục A là bảng tra theo annotation, Phụ lục C theo mã tình huống, Phụ lục D liệt kê các Pull Request, Phụ lục F giải thích thuật ngữ, Phụ lục G đối chiếu JPA và Hibernate.

## Cập nhật mục lục trong Word

File Word có hai mục lục (tóm tắt và chi tiết) dạng TOC field. Khi mở file, Word hỏi có cập nhật field không; chọn Yes. Nếu không thấy hỏi, hãy chọn mục lục, nhấn F9 (Update Field) rồi chọn "Update entire table". Các trang trống ở mục lục cho tới khi bạn cập nhật lần đầu.
