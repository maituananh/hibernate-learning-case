# Kế hoạch: Chương 26 "Dùng JPA/Hibernate và MyBatis đúng lúc"

Ngày: 2026-11-24 · Nhánh đề xuất: `docs/book-ch26` (tạo từ `main` sau khi `docs/book` được merge) · Trạng thái: **đã duyệt hướng đi (2026-11-24)**: chương tài liệu thuần, không thêm code hay test vào lab

## 1. Vì sao cần chương này

Sách hiện có 25 chương, nhưng câu hỏi "khi nào nên dùng JPA, khi nào không, và MyBatis đứng ở đâu" mới được trả lời rải rác:

- Chương 25 chỉ có 72 dòng, mô tả MyBatis đúng một gạch đầu dòng và một dòng trong bảng so sánh. Không có ví dụ, không có test.
- Các bẫy (N+1, bulk update, flush, cache, equals) nằm ở Chương 11–21, nhưng người đọc phải tự ghép lại thành quyết định "ở tình huống này mình làm gì".
- Không có chỗ nào nói **cách kết hợp** JPA với MyBatis và những bug sinh ra khi kết hợp sai.

Chương mới biến kiến thức rời rạc thành **khả năng ra quyết định**: nhìn một yêu cầu nghiệp vụ, chọn đúng công cụ, và biết trước bug nào sẽ xuất hiện nếu chọn sai.

## 2. Người đọc và mục tiêu

Người đọc: dev đã dùng Spring Data JPA hằng ngày nhưng chưa nắm cơ chế, nên hay dùng JPA cho cả những việc nó không hợp, hoặc né JPA vì từng bị bug khó hiểu.

Sau chương này người đọc làm được:

1. Kể ra ưu và nhược điểm **thật** của JPA/Hibernate, mỗi điểm kèm một ví dụ chạy được.
2. Áp dụng checklist tối ưu theo từng lớp (mapping, fetch, query, ghi, transaction, cache, quan sát).
3. Giải thích MyBatis khác JPA ở đâu về mô hình (không có persistence context, không dirty checking, không lazy loading tự động).
4. Chọn công cụ cho ~14 tình huống cụ thể, và nói được vì sao.
5. Kết hợp hai công cụ trong một ứng dụng mà không dính các bug "dữ liệu cũ", "mất cập nhật", "flush sai thứ tự".

Thất bại nếu: chương chỉ liệt kê "ưu/nhược" chung chung mà thiếu ví dụ cụ thể; hoặc người đọc không phân biệt được điều nào đã được lab chứng minh, điều nào chỉ là kiến thức tham khảo.

## 3. Quyết định cần bạn chốt (kèm đề xuất)

| # | Câu hỏi | Đề xuất | Lý do |
|---|---|---|---|
| Q1 | MyBatis có test chạy thật không? | **Không: kiến thức tham khảo**, như Chương 24 (bạn đã chốt) | Người đọc chỉ cần phân biệt khi nào dùng công cụ nào, không cần code chạy. |
| Q2 | Vị trí trong sách | **Chương 26**, cuối Phần VII, không đánh số lại các chương cũ (đã chốt) | Đánh số lại làm vỡ lint, `chapters.json`, tham chiếu chéo. Chương 25 chỉ thêm một câu trỏ sang Chương 26. |
| Q3 | Phạm vi lab | **Không mở rộng lab.** Chỉ thêm một chương sách | Không có nhóm G15, không có Phase 20, không sửa code hay test. Đúng với spec sách ("không sửa code lab"). |
| Q4 | Cách đặt MyBatis trong repo | Không áp dụng | Không có code MyBatis nên không cần `lab-mybatis/`. |

**Hệ quả của việc không có test, và cách giữ chương trung thực:**

- Phần JPA/Hibernate dựa vào các test đã có (Chương 2–21); chương **trỏ tới** chúng thay vì chép lại.
- Phần MyBatis và phần kết hợp **không có bằng chứng của lab**. Mọi mục đó dùng khung `{.chua-kiem-chung}` kèm link tài liệu chính thức (MyBatis, MyBatis-Spring, Spring Framework về transaction, Hibernate User Guide về flush và bulk operation), đúng quy ước Chương 24.
- Không đưa số câu SQL cho phần chưa kiểm chứng. Ví dụ minh họa là đoạn code ngắn trong sách, không phải code của lab, và mỗi mục kết bằng câu "tự chạy thử".
- Trước khi viết từng khẳng định về MyBatis hay cách kết hợp, đọc lại đúng trang tài liệu chính thức; không viết theo trí nhớ. Chỗ tài liệu không nói rõ thì ghi "tài liệu không nêu".

## 4. Dàn ý chương

Mỗi tình huống dùng đúng khuôn của sách: Bối cảnh, Khi nào xảy ra, Khi nào không xảy ra, Ví dụ, Kết quả, Vì sao, Cách tránh. Chương nhắc lại bằng **liên kết tới chương cũ**, không chép lại.

### 26.1 Bản đồ quyết định (đặt đầu chương)
Một trang: cây câu hỏi và bảng tóm tắt, để người đọc vội đọc xong mục này đã dùng được. Chi tiết ở các mục sau.

### 26.2 JPA/Hibernate giỏi ở đâu
Mỗi ý kèm ví dụ ngắn và trỏ chương đã chứng minh:

- **Dirty checking:** sửa field, không viết UPDATE (Chương 2).
- **Cascade và orphanRemoval:** lưu cả aggregate bằng một lệnh (Chương 5, 6).
- **Optimistic locking `@Version`:** chống ghi đè mất cập nhật gần như miễn phí (Chương 17).
- **Độc lập database:** đổi dialect, giữ code.
- **Ánh xạ giá trị:** Embeddable, Enum, Converter (Chương 3).
- **Nơi JPA rất hợp:** CRUD theo aggregate, nghiệp vụ ghi có quy tắc, ít dòng/lần xử lý.

### 26.3 JPA/Hibernate dở ở đâu (leaky abstraction)
Mỗi điểm là một tình huống **bug thật**, nêu triệu chứng, nguyên nhân, cách chặn:

| Bẫy | Triệu chứng | Đã có ở |
|---|---|---|
| N+1 khi duyệt quan hệ | 1 câu thành 101 câu | Chương 11 |
| `Pageable` + `JOIN FETCH` collection | Phân trang trong bộ nhớ, cảnh báo HHH90003004 | Chương 12 |
| Entity làm DTO trả ra ngoài | LazyInitializationException, lộ cột | Chương 11, 21 |
| Bulk UPDATE/DELETE bỏ qua persistence context | Đọc lại thấy dữ liệu cũ | Chương 15 |
| Auto flush ngoài ý muốn | UPDATE chạy trước khi bạn tưởng | Chương 2, 16 |
| `IDENTITY` làm tắt batch insert | Insert 10.000 dòng chậm | Chương 18 |
| `saveAll` trong vòng lặp lớn | Persistence context phình, OOM | Chương 18 |
| Cascade REMOVE quá rộng | Xóa lan sang dữ liệu không định xóa | Chương 5 |
| equals/hashCode sai | orphanRemoval không chạy | Chương 21 |
| Cache cấp 2 + UPDATE ngoài Hibernate | Khách thấy giá cũ | Chương 19, 24.7 |
| Cartesian product khi fetch nhiều collection | Hàng nghìn dòng trả về dư | Chương 11 |

Điểm mới của mục này so với các chương trước: một **bảng phân loại nguyên nhân gốc** (chung quy do 4 thứ: persistence context, lazy, flush, SQL do máy sinh) giúp người đọc hiểu vì sao các bẫy trông khác nhau mà cùng họ.

### 26.4 Cách dùng JPA tối ưu: checklist 7 lớp
Mỗi lớp 4–6 dòng "nên / không nên" kèm ví dụ trước và sau, số câu SQL đo bằng `SqlCaptor`:

1. **Mapping:** `LAZY` cho mọi `*ToOne`, `Set` thay vì `List` cho `ManyToMany`, `mappedBy` đúng phía.
2. **Fetch:** `EntityGraph` hoặc `JOIN FETCH` theo từng use case, `@BatchSize` làm lưới an toàn.
3. **Đọc:** projection DTO cho màn hình danh sách, entity chỉ khi sắp sửa nó.
4. **Ghi:** batch, tránh `IDENTITY` cho bảng ghi nhiều, bulk query khi sửa hàng loạt, nhớ clear.
5. **Transaction:** ranh giới ở service, `readOnly = true` cho đọc, không gọi API ngoài trong transaction.
6. **Cache:** chỉ cache dữ liệu đọc nhiều, ghi ít; biết cache cấp 2 không thấy UPDATE ngoài.
7. **Quan sát:** bật SQL log, `hibernate.generate_statistics` ở môi trường dev, test đếm số câu SQL cho truy vấn quan trọng.

Kết mục: **checklist code review** một trang (copy vào PR template).

### 26.5 MyBatis: mô hình khác ở đâu
- Sơ đồ so sánh một luồng "đổi giá sản phẩm" ở JPA (dirty checking, flush) và MyBatis (viết UPDATE, chạy ngay).
- Bảng: SQL do ai viết, ai theo dõi trạng thái object, lazy loading, cascade, cache, phụ thuộc DB.
- **Ưu điểm** (kèm ví dụ): SQL phức tạp (window function, CTE, báo cáo), kiểm soát từng câu, tối ưu chỗ nút cổ chai, truy vấn tới schema có sẵn (legacy).
- **Nhược điểm** (kèm ví dụ): mapping lặp tay, không cascade, không dirty checking, đổi cột phải sửa nhiều chỗ, rủi ro SQL injection nếu dùng `${}`, `resultMap` lồng nhau dễ gây N+1 kiểu MyBatis (nested select).
- Ví dụ minh họa: **cùng một nghiệp vụ** ("đổi giá sản phẩm", "báo cáo doanh thu tháng") viết bằng cả hai, đặt cạnh nhau trong sách; so sánh bằng lời về độ dài code và chỗ dễ sai (không đo số câu SQL).

### 26.6 Kết hợp JPA và MyBatis
**Cơ chế kỹ thuật** (theo tài liệu chính thức, gắn khung `{.chua-kiem-chung}`):
- Dùng chung một `DataSource` và một `PlatformTransactionManager` (`JpaTransactionManager`) để hai công cụ có thể cùng một connection và cùng một transaction. Điều kiện chính xác (dialect, cấu hình) đọc từ tài liệu Spring và MyBatis-Spring khi viết.
- Hai quy tắc: một transaction manager; mọi thao tác của hai bên nằm trong cùng `@Transactional`.

**Mười bẫy khi kết hợp.** Mỗi bẫy là một mục ngắn: tình huống, hậu quả, cách tránh. Phần "Hậu quả" ghi theo tài liệu và theo cơ chế đã được lab chứng minh ở Chương 2, 15, 17, 19 (persistence context, flush, bulk operation, `@Version`, cache); phần riêng của MyBatis ghi rõ là chưa kiểm chứng.

| # | Bẫy | Hậu quả (theo cơ chế đã biết) | Cách tránh |
|---|---|---|---|
| 1 | Hai công cụ có cùng transaction không | Chỉ khi dùng chung DataSource và transaction manager; nếu không, rollback không đồng bộ | Một DataSource, một transaction manager |
| 2 | JPA vừa `persist` (chưa flush), MyBatis đọc | Auto flush của Hibernate không biết câu MyBatis, nên có thể không thấy dữ liệu mới | `flush()` thủ công trước khi gọi MyBatis |
| 3 | MyBatis UPDATE xong, entity JPA đang MANAGED | Persistence context giữ giá trị cũ (cùng cơ chế bulk update, Chương 15) | `refresh` hoặc `clear` sau khi MyBatis ghi |
| 4 | Sau bẫy 3, JPA flush entity cũ | Có thể ghi đè giá trị MyBatis vừa sửa (lost update); bug nguy hiểm nhất | Không giữ entity MANAGED qua lệnh ghi của MyBatis |
| 5 | `@Version` và MyBatis UPDATE | Nếu câu UPDATE không tăng cột version, optimistic lock không bảo vệ đường ghi này (Chương 17) | Tự tăng version trong SQL, hoặc chỉ cho một công cụ ghi bảng đó |
| 6 | Cache cấp 2 | Không biết MyBatis đã sửa dữ liệu, giống Chương 24.7 | Evict thủ công hoặc không cache bảng MyBatis ghi |
| 7 | Cache cấp 1 của MyBatis (session cache) | Trong cùng session, đọc lại cùng câu có thể không chạy SQL lần hai (đọc tài liệu MyBatis, chưa kiểm chứng) | Biết cơ chế, tắt hoặc xóa cache khi cần |
| 8 | Hai transaction manager | Rollback không đồng bộ, dữ liệu nửa vời | Chỉ một transaction manager |
| 9 | Object MyBatis trả về | Là object thường, không phải entity MANAGED hay proxy | Không `merge` hay truyền nó như entity của JPA một cách vô ý |
| 10 | Mô hình **JPA ghi, MyBatis đọc** | Báo cáo đọc ổn định, không N+1, không đụng persistence context; nhưng đọc được dữ liệu chưa flush là bẫy 2 | Mẫu khuyến nghị mặc định |

Mỗi bẫy kết bằng câu "tự chạy thử" để người đọc tự kiểm chứng trên dự án của mình.

**Mẫu kết hợp nên dùng:**
1. *JPA ghi, MyBatis đọc* (khuyến nghị mặc định).
2. *MyBatis cho báo cáo và truy vấn nặng, JPA cho phần còn lại.*
3. *MyBatis ghi hàng loạt* (`batch executor`) có `entityManager.clear()` sau đó.
4. **Không nên:** hai công cụ cùng ghi lên cùng một bảng trong cùng luồng nghiệp vụ mà không có quy tắc.

### 26.7 Mười bốn tình huống chọn công cụ
Mỗi tình huống một trang: **Yêu cầu, Chọn gì, Vì sao, Nếu chọn sai thì bug gì, Chốt**.

| # | Tình huống | Chọn | Bẫy nếu chọn sai |
|---|---|---|---|
| 1 | Màn hình CRUD khách hàng, đơn hàng | JPA | Tự viết SQL: lặp code, dễ lệch mapping |
| 2 | Đặt hàng: tạo đơn, nhiều dòng, trừ tồn kho | JPA + `@Version` | MyBatis: phải tự giữ tính nhất quán và khóa |
| 3 | Danh sách đơn hàng 20 cột, phân trang, lọc nhiều điều kiện | JPA projection hoặc MyBatis | Trả entity: N+1, nạp cột thừa |
| 4 | Báo cáo doanh thu theo tháng, window function | MyBatis (hoặc native query) | JPQL không có window function; ép vào JPA thì méo |
| 5 | Dashboard join 6 bảng, chỉ đọc | MyBatis | Fetch join nhiều collection: cartesian product |
| 6 | Sửa giá 50.000 sản phẩm theo quy tắc | Bulk query / MyBatis | Load từng entity: chậm, OOM |
| 7 | Import 1 triệu dòng từ file | JDBC/MyBatis batch | `saveAll`: persistence context phình |
| 8 | Database legacy, bảng không có khóa chuẩn | MyBatis | JPA ép `@Id`, quan hệ không rõ |
| 9 | Tìm kiếm động theo nhiều tiêu chí tùy chọn | JPA Specification/Criteria hoặc MyBatis `<if>` | Nối chuỗi JPQL tay: injection, khó đọc |
| 10 | Xóa mềm (soft delete) khắp nơi | JPA `@SQLRestriction` | MyBatis: mỗi câu phải nhớ `WHERE deleted = false` |
| 11 | Microservice nhỏ 3 bảng, đội giỏi SQL | MyBatis hoặc JdbcTemplate | JPA: chi phí học cao hơn lợi ích |
| 12 | Nghiệp vụ trộn: ghi bằng JPA, đọc báo cáo bằng MyBatis | Kết hợp (26.6) | Quên flush, đọc dữ liệu chưa ghi |
| 13 | Chuyển dần ứng dụng MyBatis cũ sang JPA | Chạy song song theo module | Hai công cụ ghi cùng bảng |
| 14 | Hiệu năng ghi: hàng nghìn UPDATE/giây | MyBatis batch hoặc JDBC | Dirty checking quét hàng nghìn entity mỗi flush |

Bảng này cũng là **nguồn cho cây quyết định** ở 26.1.

### 26.8 Anti-pattern và checklist cuối chương
- 10 dấu hiệu "đang lạm dụng JPA" (ví dụ: viết `@Query(nativeQuery)` khắp nơi, `findAll()` rồi lọc bằng stream).
- 10 dấu hiệu "đang lạm dụng MyBatis" (ví dụ: tự cài lại cascade bằng tay, copy resultMap mọi nơi).
- Checklist review PR một trang.
- Câu hỏi tự kiểm tra cuối chương (5 câu, có đáp án).

## 5. Công việc theo bước

Chỉ có một việc: viết chương. Không đụng code, test, `build.gradle`, `docs/RESULTS.md`.

1. Tạo nhánh `docs/book-ch26` từ `main` đã cập nhật (sau khi `docs/book` được merge), theo quy trình CLAUDE.md.
2. Thêm `073-chuong-26-...md` và mục trong `chapters.json` (`scenarios: []`).
3. Đọc tài liệu chính thức cho phần MyBatis và kết hợp (MyBatis, MyBatis-Spring, Spring transaction, Hibernate flush và bulk), ghi lại link và điều tài liệu thực sự nói.
4. Viết 26.2, 26.3, 26.4 trước (chủ yếu trỏ về chương cũ đã có bằng chứng).
5. Viết 26.5, 26.6 (khung `{.chua-kiem-chung}`, mỗi mục có "tự chạy thử").
6. Viết 26.7, 26.8; dựng cây quyết định ở 26.1 từ bảng 26.7.
7. Thêm sơ đồ: cây quyết định, luồng "JPA ghi, MyBatis đọc", hai thế giới persistence context và MyBatis session.
8. Sửa Chương 25 (một câu trỏ sang Chương 26) và Phụ lục F (thuật ngữ: MyBatis mapper, resultMap, session cache).
9. `node tools/lint.mjs`, `npm test`, `build.sh`, mở .docx kiểm tra bằng mắt.
10. Một commit `docs(book): Chương 26 ...`, push, mở PR (mô tả tiếng Việt có dấu, theo từng mục).

## 6. Quy ước viết (để không lệch với phần còn lại)

- Tiếng Việt có dấu, giữ thuật ngữ Anh; ngoài code không emoji, mũi tên, ký hiệu toán (lint chặn).
- **Phân biệt rõ hai loại kiến thức:** điều đã được lab chứng minh (trỏ tới chương và test) và điều chỉ là tham khảo (khung `{.chua-kiem-chung}` kèm link tài liệu chính thức). Người đọc nhìn khung là biết mức độ tin cậy.
- Không đưa số câu SQL cho kiến thức tham khảo.
- Mỗi tình huống ở 26.7 phải có **bug cụ thể** (mô tả được từng bước xảy ra thế nào) hoặc trỏ tới test của lab tái hiện nó, tránh lời khuyên chung chung.
- Ưu và nhược điểm luôn đi cặp với **điều kiện áp dụng** ("ưu điểm khi...", "nhược điểm khi..."), vì cùng một đặc tính (ví dụ lazy loading) là ưu hay nhược tùy ngữ cảnh.

## 7. Tiêu chí hoàn thành

- Chương 26 qua lint, build ra .docx, không làm đỏ test nào của lab (không đụng code).
- Người đọc mở mục 26.1, mô tả một yêu cầu bất kỳ trong 14 tình huống, và tìm được công cụ lẫn lý do trong dưới 1 phút.
- Mọi câu về MyBatis và cách kết hợp đều nằm trong khung chưa kiểm chứng và có link tài liệu chính thức.
- Mọi liên kết tới chương cũ trỏ đúng chương.

## 8. Ước lượng

Khoảng 1 phiên làm việc, 1.000–1.300 dòng Markdown (ngắn hơn Chương 21 vì không có khối bằng chứng SQL và dữ liệu).
