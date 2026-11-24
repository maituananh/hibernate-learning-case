## Chương 25. Hibernate so với các công cụ khác và lộ trình học tiếp

Suốt cuốn sách này, Hibernate là công cụ duy nhất xuất hiện. Điều đó dễ tạo ấn tượng rằng mọi bài toán truy cập database đều nên giải bằng ORM. Không phải vậy. Hibernate mạnh ở chỗ nó **quản lý trạng thái entity** cho bạn (persistence context, dirty checking, cascade), nhưng sức mạnh đó có giá: bạn phải hiểu flush, proxy, N+1, và những bẫy đã đi qua ở các chương trước. Có bài toán không cần sức mạnh đó, và khi ấy một công cụ đơn giản hơn là lựa chọn tốt hơn.

Chương cuối này giúp bạn đặt Hibernate vào bản đồ chung. Giống Chương 24, mọi mục ở đây là **kiến thức tham khảo, chưa kiểm chứng trong lab**: sách dẫn nguồn từ tài liệu chính thức, không đưa số câu SQL, và không khẳng định điều gì về hiệu năng. Muốn biết công cụ nào hợp với dự án của bạn, hãy thử nó trên chính dữ liệu của bạn.

**Trong JPA thuần.** JPQL, Criteria và native query (mục 25.1) đều là JPA chuẩn, chạy được trên mọi provider. HQL là phần mở rộng của Hibernate cho JPQL. Các công cụ ở mục 25.2 đa số không phải JPA; riêng Querydsl chạy bên trên JPA.

### 25.1 Các cách viết truy vấn trong JPA

::: {.chua-kiem-chung}
Chương 12 đã kiểm chứng JPQL, native SQL và phân trang trong lab. Phần Criteria và so sánh ba cách viết dưới đây chưa có test. Nguồn: Hibernate User Guide, phần query language, <https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#query-language>, và phần criteria, <https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#criteria>.
:::

Khi bạn cần đọc dữ liệu mà `em.find` (tìm theo id) không đủ, JPA cho ba hướng:

- **JPQL (và HQL).** Viết truy vấn bằng tên entity và field, không dùng tên bảng và cột. Đây là cách các chương trước dùng nhiều nhất. HQL là phiên bản mở rộng của Hibernate, thêm cú pháp và hàm mà JPQL chuẩn không có. Ưu điểm: ngắn, dễ đọc. Nhược điểm: truy vấn là chuỗi, nên sai tên field chỉ lộ ra lúc chạy.
- **Criteria API.** Dựng truy vấn bằng code Java thay vì chuỗi, nên trình biên dịch giúp bắt lỗi khi kết hợp với Metamodel (tên field sinh thành hằng số). Ưu điểm: hợp với truy vấn có điều kiện thay đổi (màn hình tìm kiếm nhiều bộ lọc tùy chọn). Nhược điểm: dài dòng, khó đọc hơn JPQL tương đương.
- **Native SQL.** Viết SQL thuần. Ưu điểm: dùng được mọi tính năng của PostgreSQL, như `jsonb` hay hàm cửa sổ. Nhược điểm: mất tính di động giữa các database, và theo Chương 15 và 19, câu native đi vòng qua persistence context và có thể làm Hibernate xóa cả cache cấp 2.

Quy tắc chọn thực dụng: bắt đầu bằng JPQL; chuyển sang Criteria khi điều kiện lọc động quá nhiều; dùng native SQL cho báo cáo hoặc tính năng riêng của database, và nhớ rằng bạn đã tự gánh trách nhiệm đồng bộ với persistence context.

### 25.2 Các công cụ khác

::: {.chua-kiem-chung}
Mô tả dưới đây dựa trên trang tài liệu của từng dự án, chưa có test trong lab. Nguồn: Spring Framework, `JdbcTemplate`, <https://docs.spring.io/spring-framework/reference/data-access/jdbc.html>; Spring Data JDBC, <https://docs.spring.io/spring-data/relational/reference/jdbc.html>; MyBatis, <https://mybatis.org/mybatis-3/>; jOOQ, <https://www.jooq.org/doc/latest/manual/>; Querydsl, <https://querydsl.com/>; Hibernate Reactive, <https://hibernate.org/reactive/documentation/>.
:::

- **JdbcTemplate.** Công cụ mỏng của Spring bọc JDBC. Bạn tự viết SQL và tự ánh xạ từng dòng kết quả sang object. Không có entity, không có theo dõi trạng thái. Lab đã dùng nó theo cách này: mọi assert dữ liệu đều đọc qua `JdbcTemplate`, đúng vì nó **không** có cache cấp 1 nói dối.
- **Spring Data JDBC.** Cũng của họ Spring Data, nhưng không phải JPA. Nó xoay quanh khái niệm aggregate (một gốc cùng các phần phụ thuộc vào nó), không có lazy loading và không có persistence context, nên ít "phép màu" hơn. Đổi lại, bạn bó theo mô hình aggregate.
- **MyBatis.** Bạn viết SQL (trong file XML hoặc annotation) và khai báo cách ánh xạ kết quả sang object. SQL nằm hoàn toàn trong tay bạn. Phù hợp khi đội đã quen SQL phức tạp và muốn kiểm soát từng câu.
- **jOOQ.** Dựng SQL bằng một DSL kiểu tĩnh trong Java, sinh từ schema database thật, nên đổi cột là code không biên dịch được. Bạn vẫn là người nghĩ ra câu SQL, nhưng trình biên dịch giúp kiểm tra.
- **Querydsl.** Cũng là DSL kiểu tĩnh, nhưng thường chạy **bên trên JPA**: nó sinh ra JPQL rồi giao cho Hibernate. Vì vậy mọi điều về persistence context và lazy loading ở các chương trước vẫn đúng.
- **Hibernate Reactive.** Phiên bản Hibernate dành cho lập trình không chặn luồng (non-blocking), chạy trên client database bất đồng bộ. Mapping vẫn quen thuộc, nhưng lazy loading không còn "tự động trong suốt" như ở Hibernate thường: bạn phải yêu cầu tải tường minh. Hãy đọc tài liệu trước khi chuyển mã hiện có sang.

### 25.3 Bảng so sánh

Bảng này tóm tắt ý của mục 25.2 để bạn so nhanh. Cột "khi nào nên chọn" là gợi ý chung, không phải quy luật; ràng buộc thật của dự án (kỹ năng của đội, độ phức tạp của truy vấn, yêu cầu hiệu năng) quan trọng hơn.

| Công cụ | Ai viết SQL | Persistence context | Lazy loading | Khi nào nên chọn |
|---|---|---|---|---|
| Hibernate (JPA) | Hibernate sinh phần lớn; bạn viết JPQL hoặc native khi cần | Có | Có (proxy) | Mô hình nghiệp vụ nhiều quan hệ, cần theo dõi thay đổi và cascade |
| JdbcTemplate | Bạn viết toàn bộ | Không | Không | Truy vấn đơn giản, báo cáo, hoặc bước kiểm tra dữ liệu |
| Spring Data JDBC | Công cụ sinh cho thao tác cơ bản; bạn viết phần còn lại | Không | Không | Mô hình theo aggregate gọn, muốn ít phép màu |
| MyBatis | Bạn viết toàn bộ | Không (theo nghĩa của JPA) | Có thể cấu hình cho kết quả lồng nhau | Đội mạnh về SQL, cần kiểm soát từng câu |
| jOOQ | Bạn viết bằng DSL kiểu tĩnh | Không | Không | SQL phức tạp mà vẫn muốn kiểm tra lúc biên dịch |
| Querydsl | Sinh JPQL từ DSL, rồi Hibernate sinh SQL | Có (chạy trên JPA) | Có | Truy vấn động bằng JPA, muốn an toàn kiểu |
| Hibernate Reactive | Giống Hibernate | Có (session riêng của Reactive) | Phải tải tường minh | Ứng dụng non-blocking từ đầu đến cuối |

Có hai nhận xét rút ra từ bảng. Thứ nhất, các công cụ không loại trừ nhau: nhiều dự án dùng Hibernate cho thao tác ghi theo mô hình nghiệp vụ, và `JdbcTemplate` hoặc jOOQ cho báo cáo nặng, nơi persistence context chỉ gây thêm việc. Thứ hai, ba cột giữa của bảng chính là ba câu hỏi bạn nên tự đặt trước khi chọn: *ai kiểm soát câu SQL, ai theo dõi thay đổi của đối tượng, và khi nào dữ liệu liên quan được tải*. Cả cuốn sách này xoay quanh ba câu hỏi đó.

### 25.4 Lộ trình học tiếp

::: {.chua-kiem-chung}
Danh sách dưới đây là gợi ý của tác giả, không phải kết quả của lab. Liên kết có thể thay đổi theo thời gian; nếu một liên kết không còn dùng được, hãy tìm theo tên tài liệu.
:::

Nếu bạn đọc tới đây, bạn đã có nền tảng chắc về mapping, vòng đời entity, truy vấn, transaction và hiệu năng. Những bước tiếp theo:

- **Hibernate User Guide.** Tài liệu chính thức, đầy đủ nhất, nên để mở khi làm việc: <https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html>. Các chủ đề lab chưa chạm tới (Envers, multi-tenancy, kiểu dữ liệu tùy biến, event listener) đều có ở đây.
- **Spring Data JPA reference.** Tài liệu về repository, derived query, projection, auditing và specification: <https://docs.spring.io/spring-data/jpa/reference/>. Mục 2.9 và Chương 16 của sách chỉ là phần giao nhau giữa Spring Data và Hibernate.
- **Đặc tả Jakarta Persistence.** Khi cần biết điều gì là JPA chuẩn và điều gì chỉ là Hibernate: <https://jakarta.ee/specifications/persistence/>. Đây cũng là nơi trả lời cho các đoạn "Trong JPA thuần" của sách.
- **High-Performance Java Persistence** của Vlad Mihalcea. Cuốn sách chuyên sâu về hiệu năng truy cập dữ liệu với JDBC và Hibernate, nội dung nối tiếp tự nhiên các Chương 11, 18 và 19: <https://vladmihalcea.com/books/high-performance-java-persistence/>.
- **Tài liệu PostgreSQL.** Hiểu database bên dưới giúp bạn đọc `EXPLAIN`, hiểu index và mức cô lập transaction: <https://www.postgresql.org/docs/current/>.

Những chủ đề đáng học tiếp, theo thứ tự gợi ý: đọc kế hoạch thực thi (`EXPLAIN ANALYZE`) cho các câu SQL mà bạn thấy chậm; mức cô lập transaction và hiện tượng đọc bất thường; migration có phiên bản (mục 24.1); và một công cụ truy vấn ngoài ORM (mục 25.2) để bạn biết khi nào không cần ORM.

Cuối cùng, hãy giữ lại thói quen mà lab muốn hình thành: **không tin trí nhớ, kể cả trí nhớ của chính cuốn sách này**. Khi gặp một câu hỏi về mapping, hãy viết một test nhỏ trên PostgreSQL thật, in SQL, đọc dữ liệu bằng `JdbcTemplate`, và để kết quả quyết định.

::: {.ghi-nho}
Hibernate là một công cụ mạnh nhưng không phải công cụ duy nhất. Hãy chọn theo ba câu hỏi: ai kiểm soát SQL, ai theo dõi thay đổi, và khi nào dữ liệu liên quan được tải. Mọi mục của chương này là kiến thức tham khảo, chưa kiểm chứng trong lab.
:::
