## Chương 26. Dùng JPA/Hibernate và MyBatis đúng lúc

Hai mươi lăm chương trước trả lời câu hỏi "Hibernate làm việc thế nào". Chương này trả lời câu hỏi mà bạn sẽ gặp mỗi tuần ở công việc thật: **với yêu cầu này, tôi dùng công cụ nào?** Cùng một đặc tính, ví dụ lazy loading hay dirty checking, có lúc là ưu điểm và có lúc là cái bẫy. Vì vậy chương không chia "tốt" và "xấu" chung chung, mà luôn đi kèm **điều kiện áp dụng**.

Chương này gom hai loại kiến thức, và sách đánh dấu để bạn biết mức tin cậy:

- **Đã được lab chứng minh.** Các mục 26.2, 26.3, 26.4 và phần cơ chế của 26.7 dựa vào test ở Chương 2 đến 21. Chương **trỏ tới** các mục đó thay vì chép lại; muốn thấy SQL và dữ liệu thật, hãy mở mục được dẫn.
- **Chỉ là kiến thức tham khảo.** MyBatis và cách kết hợp hai công cụ (mục 26.5 và 26.6) **không có test trong lab**. Mỗi mục đó nằm trong khung **Chưa kiểm chứng trong lab**, kèm đường dẫn tới tài liệu chính thức. Sách không đưa số câu SQL cho phần này, và mỗi mục kết bằng câu "tự chạy thử".

**Trong JPA thuần.** Chương này nói về JPA/Hibernate (thuộc JPA: persistence context, `@Version`, cascade; riêng Hibernate: `@BatchSize`, `@SQLRestriction`, `StatelessSession`) và MyBatis (không thuộc JPA, là một thư viện khác hẳn về mô hình).

### 26.1 Bản đồ quyết định

Nếu bạn chỉ đọc một mục của chương này, hãy đọc mục này. Các mục sau giải thích từng nhánh.

```text
Yêu cầu của bạn là gì?
 |
 +-- GHI dữ liệu theo quy tắc nghiệp vụ (một đơn, một khách, vài bảng liên quan)
 |     +-- Cần chống hai người ghi đè nhau?        JPA + @Version
 |     +-- Sửa hàng loạt cùng một quy tắc?         bulk query hoặc MyBatis
 |     +-- Hàng trăm nghìn dòng trở lên?           JDBC / MyBatis batch
 |
 +-- ĐỌC dữ liệu
 |     +-- Để sửa ngay sau đó?                     JPA (entity)
 |     +-- Để hiển thị, danh sách, lọc, phân trang? JPA projection hoặc MyBatis
 |     +-- Báo cáo: window function, CTE, 5+ bảng? MyBatis hoặc native query
 |
 +-- Schema có sẵn, khó khớp mô hình entity (legacy)?   MyBatis
 |
 +-- Ứng dụng nhỏ, vài bảng, đội giỏi SQL?              MyBatis hoặc JdbcTemplate
 |
 +-- Vừa ghi theo mô hình, vừa báo cáo nặng?            JPA ghi, MyBatis đọc
```

Bảng tóm tắt, mỗi dòng ứng với một tình huống ở mục 26.7:

| # | Tình huống | Chọn | Bẫy chính nếu chọn sai |
|---|---|---|---|
| 1 | Màn hình CRUD khách hàng, đơn hàng | JPA | Tự viết SQL: lặp code, dễ lệch mapping |
| 2 | Đặt hàng: tạo đơn, nhiều dòng, trừ kho | JPA kèm `@Version` | Tự giữ nhất quán và khóa |
| 3 | Danh sách đơn 20 cột, lọc, phân trang | JPA projection hoặc MyBatis | Trả entity: N+1, nạp cột thừa |
| 4 | Báo cáo doanh thu theo tháng, window function | MyBatis hoặc native query | JPQL không có hàm cửa sổ |
| 5 | Dashboard join nhiều bảng, chỉ đọc | MyBatis | Fetch nhiều collection: nhân dòng |
| 6 | Sửa giá 50.000 sản phẩm theo quy tắc | Bulk query hoặc MyBatis | Tải từng entity: chậm, tốn bộ nhớ |
| 7 | Import 1 triệu dòng từ file | JDBC hoặc MyBatis batch | `saveAll`: persistence context phình |
| 8 | Database legacy, bảng không có khóa chuẩn | MyBatis | JPA bắt buộc có id |
| 9 | Tìm kiếm theo nhiều tiêu chí tùy chọn | Specification hoặc MyBatis động | Nối chuỗi JPQL: injection |
| 10 | Xóa mềm khắp nơi | JPA với `@SQLRestriction` | Mỗi câu SQL phải nhớ điều kiện |
| 11 | Dịch vụ nhỏ 3 bảng, đội giỏi SQL | MyBatis hoặc JdbcTemplate | Chi phí học JPA lớn hơn lợi ích |
| 12 | Ghi bằng JPA, báo cáo bằng MyBatis | Kết hợp (mục 26.6) | Quên flush, đọc dữ liệu cũ |
| 13 | Chuyển dần ứng dụng MyBatis sang JPA | Chạy song song theo module | Hai công cụ ghi cùng bảng |
| 14 | Hàng nghìn UPDATE mỗi giây | MyBatis batch hoặc JDBC | Dirty checking quét mọi entity |

Ba câu hỏi ở Chương 25 vẫn là nền: **ai viết SQL, ai theo dõi thay đổi của object, và khi nào dữ liệu liên quan được tải.** Mọi dòng của bảng trên chỉ là ba câu hỏi đó áp vào một yêu cầu cụ thể.

### 26.2 JPA/Hibernate giỏi ở đâu

Sáu điểm mạnh dưới đây đều có test ở các chương trước. Điểm mạnh nào cũng có **điều kiện**: nó chỉ là ưu điểm khi bạn đang làm đúng loại việc.

- **Dirty checking: sửa field, không viết UPDATE.** Entity MANAGED bị sửa thì Hibernate tự sinh UPDATE lúc flush (mục 2.3). Ưu điểm khi nghiệp vụ là "tải một object, đổi vài thuộc tính, lưu". Không còn là ưu điểm khi bạn sửa hàng chục nghìn dòng: lúc flush Hibernate phải so từng entity với bản chụp ban đầu (mục 18.4).
- **Cascade và orphanRemoval: lưu cả aggregate bằng một lệnh.** Lưu `Customer` cùng các `PurchaseOrder` và `OrderItem` của nó mà không viết INSERT từng bảng (Chương 5), và gỡ con khỏi collection là xóa dòng (Chương 6). Ưu điểm khi quan hệ cha con có quy tắc vòng đời rõ ràng; cái bẫy là cascade đặt quá rộng (mục 5.7).
- **Optimistic locking với `@Version`: chống ghi đè mất cập nhật gần như miễn phí.** Thêm một field, Hibernate tự thêm điều kiện version vào UPDATE, và transaction đến sau bị từ chối thay vì âm thầm ghi đè (mục 17.2). Đây là điều rất khó tự làm đúng nếu viết SQL tay ở mọi nơi.
- **Ánh xạ giá trị:** Embeddable, Enum, AttributeConverter, NaturalId (Chương 3). Một value object nghiệp vụ thành một nhóm cột, không viết code chuyển đổi lặp lại.
- **Độc lập database ở mức vừa phải.** JPQL và API entity chạy trên nhiều database, đổi dialect mà giữ code. Lưu ý: ngay khi bạn dùng native query hay hàm riêng của PostgreSQL (Chương 12), phần đó mất tính di động. Lab này chạy trên PostgreSQL thật nên không kiểm chứng việc đổi sang database khác.
- **Quản lý quan hệ giữa các bảng bằng mô hình object.** Đây là lý do chính để chọn ORM: khi mô hình nghiệp vụ có nhiều quan hệ và quy tắc, việc giữ chúng nhất quán nằm ở mapping thay vì rải rác trong SQL.

**Nơi JPA rất hợp:** thao tác theo aggregate (một đơn cùng các dòng của nó), nghiệp vụ ghi có quy tắc, mỗi lần xử lý chạm ít dòng, đội muốn đổi cột mà ít sửa code.

### 26.3 JPA/Hibernate dở ở đâu

Hibernate che giấu SQL, và sự che giấu đó rò rỉ (leaky abstraction) đúng lúc bạn không ngờ. Mỗi dòng dưới đây là một bug đã được tái hiện ở chương trước; cột cuối trỏ tới nơi bạn xem SQL và dữ liệu thật.

| Bẫy | Triệu chứng | Nguyên nhân gốc | Xem ở |
|---|---|---|---|
| Duyệt quan hệ LAZY trong vòng lặp | 1 câu SELECT thành N+1 câu | lazy | Mục 11.3.3 |
| Entity trả ra ngoài transaction | `LazyInitializationException` | lazy | Mục 16.6, 11.3.2 |
| Fetch hai List cùng lúc | `MultipleBagFetchException` | SQL do máy sinh | Mục 11.5.1 |
| Fetch hai Set cùng lúc | Chạy được nhưng nhân dòng | SQL do máy sinh | Mục 11.5.3 |
| Bulk UPDATE hoặc DELETE | Entity MANAGED giữ giá trị cũ | persistence context | Mục 15.1 |
| Native UPDATE trên bảng có version | `@Version` không còn bảo vệ | SQL do máy sinh | Mục 15.3 |
| Auto flush trước query | UPDATE chạy sớm hơn bạn nghĩ | flush | Mục 2.8, 2.10 |
| `IDENTITY` làm tắt batch | 100 entity thành 100 INSERT | SQL do máy sinh | Mục 18.2 |
| Xử lý số lượng lớn không clear | Persistence context giữ mọi entity | persistence context | Mục 18.4 |
| Cascade REMOVE đặt sai chỗ | Xóa lan sang dữ liệu không định xóa | SQL do máy sinh | Mục 5.5, 5.7 |
| `hashCode` theo id tự sinh | `orphanRemoval` không chạy | persistence context | Mục 21.1.1 |
| Cache cấp 2 và UPDATE ngoài Hibernate | Khách thấy giá cũ | persistence context | Mục 19.2.2, 24.7 |

**Điều nhiều người nghĩ về phân trang.** Nhiều bài viết cũ nói rằng `join fetch` collection kèm phân trang làm Hibernate tải hết rồi cắt trang trong bộ nhớ, kèm cảnh báo `HHH90003004`. Lab đo trên Hibernate 7.4 với PostgreSQL cho kết quả khác: câu SQL có `fetch first` và database cắt trang đúng ở bảng cha (mục 11.6.1). Đừng đưa bẫy này vào checklist của team nếu bạn chưa kiểm tra trên đúng phiên bản của mình.

**Bốn nguyên nhân gốc.** Các bẫy trông khác nhau nhưng thuộc bốn họ, và biết họ giúp bạn đoán bẫy kế tiếp trước khi nó xảy ra:

1. **Persistence context** giữ bản sao object trong bộ nhớ (cache cấp 1). Mọi thứ thay đổi *ngoài* nó, như bulk, native, MyBatis, hay chính database, làm bản sao thành "cũ". Chương 2, 15, 18, 19.
2. **Lazy loading** tải dữ liệu *khi bạn chạm vào*, nên SQL chạy ở nơi code không nhìn thấy. Chương 11, 16.
3. **Flush** quyết định *khi nào* câu ghi thật sự chạy, không phải lúc bạn gọi `persist` hay đổi field. Chương 2, 16.
4. **SQL do máy sinh** nghĩa là bạn không viết câu đó, nên bạn phải *đọc* nó. Cách duy nhất là bật log SQL và đếm câu (Chương 23).

Khi gặp một bug lạ ở JPA, hãy tự hỏi nó thuộc họ nào. Rất nhiều bug "bí ẩn" là một trong bốn họ này, và cách chẩn đoán là đọc SQL thật thay vì đọc code Java.

### 26.4 Cách dùng JPA tối ưu: checklist bảy lớp

Mỗi lớp dưới đây là tóm tắt của kết quả đã chứng minh; không có quy tắc nào mới ở đây, chỉ có cách xếp chúng thành thứ tự để rà một dự án.

1. **Mapping.** Đặt `LAZY` tường minh cho mọi `*ToOne` (mặc định của `ManyToOne` là EAGER, mục 11.2). Với `ManyToMany` ưu tiên `Set` (mục 11.5). Luôn ghi `mappedBy` đúng phía và dùng helper method giữ hai phía đồng bộ (Chương 4). Tránh `equals` và `hashCode` theo id tự sinh (mục 21.1).
2. **Fetch.** Chọn cách tải theo từng use case: `join fetch` hoặc `EntityGraph` khi bạn cần collection ngay (mục 11.4), `@BatchSize` làm lưới an toàn cho những chỗ bạn chưa kịp sửa. Không fetch hai List cùng lúc (mục 11.5).
3. **Đọc.** Danh sách hiển thị dùng projection DTO (mục 12.5); chỉ tải entity khi sắp sửa nó. Phân trang kèm collection thì kiểm tra SQL (mục 11.6).
4. **Ghi.** Dùng `SEQUENCE` và bật batch cho bảng ghi nhiều (mục 18.1), tránh `IDENTITY` ở đó (mục 18.2). Sửa hàng loạt bằng bulk query nhưng nhớ `flush` trước và `clear` sau (mục 15.4). Xử lý số lượng lớn thì flush kèm clear theo lô (mục 18.4).
5. **Transaction.** Ranh giới ở service (mục 16.1), `readOnly = true` cho nhánh chỉ đọc nhưng nhớ nó không chặn sửa entity một cách ồn ào (mục 16.2), service trả DTO thay vì entity (mục 16.6), tắt hoặc hiểu rõ Open Session in View (mục 16.7). Không gọi API ngoài (HTTP, gửi mail) trong transaction đang giữ khóa.
6. **Cache.** Chỉ cache dữ liệu đọc nhiều, ghi ít. Biết rằng cache cấp 2 không thấy UPDATE từ bên ngoài Hibernate (mục 19.2.2) và native update phải khai báo bảng nó đụng (mục 19.3).
7. **Quan sát.** Bật log SQL ở môi trường dev, viết test đếm số câu SELECT cho truy vấn quan trọng (mục 23.1), và lint mapping bằng Metadata (mục 23.2).

**Checklist review PR một trang** (có thể chép vào mẫu PR của team; bản đầy đủ nằm ở mục 23.3):

```text
[ ] Mọi *ToOne mới có fetch = LAZY tường minh
[ ] Quan hệ hai chiều: có mappedBy và helper method đồng bộ hai phía
[ ] Không trả entity ra ngoài service; trả DTO hoặc record
[ ] Truy vấn danh sách mới: đã đếm số SELECT trong test (không N+1)
[ ] Không fetch hai List cùng lúc; phân trang kèm collection đã xem SQL
[ ] Bảng ghi nhiều: SEQUENCE, không IDENTITY; vòng lặp lớn có flush và clear
[ ] Bulk hoặc native UPDATE: có flush trước, clear sau, và xử lý cột version
[ ] equals và hashCode không dựa vào id tự sinh
[ ] Cascade REMOVE chỉ đặt ở quan hệ cha sở hữu con
[ ] Entity có cache cấp 2: đã nghĩ tới ai sửa bảng này ngoài Hibernate
```

### 26.5 MyBatis: mô hình khác ở đâu

::: {.chua-kiem-chung}
Mục này và mục 26.6 **không có test trong lab**. Mọi mô tả MyBatis dựa vào tài liệu chính thức (bản 3.5.19). Nguồn: MyBatis, tài liệu SQL map XML, <https://mybatis.org/mybatis-3/sqlmap-xml.html>; SQL động, <https://mybatis.org/mybatis-3/dynamic-sql.html>; cấu hình, <https://mybatis.org/mybatis-3/configuration.html>; Java API, <https://mybatis.org/mybatis-3/java-api.html>; MyBatis-Spring, <https://mybatis.org/spring/>. Ví dụ code trong mục là đoạn minh họa viết cho sách, không phải code của lab. Chỗ tài liệu không nêu thì sách ghi "tài liệu không nêu".
:::

**Bối cảnh.** MyBatis không phải ORM theo nghĩa của Hibernate. Bạn viết SQL (trong file XML hoặc annotation trên interface `Mapper`) và khai báo cách ánh xạ cột sang object. Hãy xem cùng một việc, "đổi giá một sản phẩm", ở hai công cụ:

```text
JPA / Hibernate                          MyBatis
---------------                          -------
Product p = em.find(id)     (SELECT)     productMapper.updatePrice(id, price)
p.setPrice(newPrice)        (đổi object)     |
   ... cuối transaction ...                  +-- chạy UPDATE ngay, đúng câu bạn viết
flush: so p với bản chụp, sinh UPDATE
```

Ở JPA câu UPDATE là **hệ quả** của việc đổi object, và chạy ở lúc flush. Ở MyBatis câu UPDATE là **chính lệnh bạn gọi**, và chạy ngay. Hệ quả: MyBatis không có "trạng thái entity" nào để lệch với database.

**Kết quả.** Theo tài liệu, các khác biệt về mô hình:

| Câu hỏi | JPA/Hibernate | MyBatis |
|---|---|---|
| Ai viết SQL | Hibernate sinh phần lớn | Bạn viết toàn bộ |
| Ai theo dõi thay đổi object | Persistence context, dirty checking | Không ai; bạn gọi lệnh ghi tường minh |
| Lazy loading | Mặc định theo kiểu quan hệ, qua proxy | Tắt mặc định (`lazyLoadingEnabled` mặc định `false`) |
| Cascade | Có (`CascadeType`) | Tài liệu không nêu khái niệm tương đương |
| Cache | Cache cấp 1 (persistence context), cấp 2 tùy chọn | Local cache theo session; cache toàn cục là tùy chọn |
| Phụ thuộc database | Tùy dialect, native thì mất | Phụ thuộc hoàn toàn vào SQL bạn viết |

Có một chữ "cache cấp 1" ở cả hai bên nhưng **không cùng nghĩa**. Theo tài liệu MyBatis, mỗi session có một local cache và nó bị xóa khi `update`, `commit`, `rollback` hoặc `close`; cấu hình `localCacheScope` quyết định cache sống theo `SESSION` (mặc định) hay chỉ trong một `STATEMENT`. Nó là cache kết quả truy vấn, không phải bản sao trạng thái entity để dirty checking.

**Ưu điểm khi:**

- **SQL phức tạp.** Window function, CTE, báo cáo nhiều bảng: bạn viết đúng câu cần, không phải ép vào JPQL.
- **Kiểm soát từng câu.** Chỗ nút cổ chai bạn tối ưu câu SQL trực tiếp và không phải đoán Hibernate sẽ sinh gì.
- **Schema có sẵn (legacy).** Bảng không có khóa chuẩn, tên cột lạ: MyBatis chỉ cần bạn khai báo ánh xạ.
- **SQL động rõ ràng.** `<if>`, `<where>`, `<foreach>` (tài liệu SQL động) dựng điều kiện tùy chọn ngay trong XML.

**Nhược điểm khi:**

- **Ánh xạ lặp tay.** Mỗi bảng và mỗi kết quả cần khai báo; đổi một cột thì sửa nhiều chỗ (câu SQL, `resultMap`, class).
- **Không dirty checking, không cascade.** Lưu một aggregate nhiều bảng là bạn tự gọi từng lệnh theo đúng thứ tự khóa ngoại và tự dọn khi lỗi.
- **`${}` không an toàn.** Tài liệu nói MyBatis không escape chuỗi thay bằng `${}` và cảnh báo trực tiếp về SQL Injection khi đưa dữ liệu người dùng vào đó. `#{}` mới là cách truyền tham số an toàn. Chỗ cần `${}` hợp lý là tên cột trong `ORDER BY`, và phải kiểm tra danh sách cho phép.
- **N+1 kiểu MyBatis.** Tài liệu mô tả "N+1 Selects Problem" khi dùng *nested select* (mỗi dòng cha chạy thêm một câu SELECT con) và cảnh báo nó có thể sinh hàng trăm hoặc hàng nghìn câu. Nested results (một câu join) tránh được điều đó. Đây là cùng họ bẫy với Chương 11, chỉ khác cách gọi.

**Ví dụ.** Báo cáo doanh thu tháng, viết bằng MyBatis annotation (đoạn minh họa):

```java
@Mapper
public interface ReportMapper {
    @Select("""
        select to_char(placed_at, 'YYYY-MM') as month,
               sum(total) as revenue,
               sum(sum(total)) over (order by to_char(placed_at, 'YYYY-MM'))
                   as running_revenue
        from purchase_order
        group by 1
        order by 1
        """)
    List<MonthlyRevenue> revenueByMonth();
}
```

Với JPA, cùng báo cáo phải dùng native query vì JPQL không có hàm cửa sổ (mục 12.5.3 cho cách trả `Tuple`). Điểm khác không phải độ dài mà là **ai chịu trách nhiệm**: ở cả hai bạn đều tự viết SQL; MyBatis chỉ không bắt bạn qua lớp entity khi không cần entity.

**Vì sao điều này quan trọng.** Chọn MyBatis không phải "bỏ ORM cho nhanh". Bạn đổi *sự tiện lợi tự động* (dirty checking, cascade, `@Version`) lấy *sự kiểm soát* (mọi câu SQL do bạn viết). Hãy chọn theo việc đang làm (mục 26.7), không theo thói quen.

**Tự chạy thử.** Viết mapper `revenueByMonth` trên dữ liệu của bạn và bật log SQL của MyBatis. Câu hỏi: số câu SQL cho một lần gọi là bao nhiêu, và nếu thêm `resultMap` có nested select cho danh sách đơn hàng thì con số đổi thế nào?

::: {.ghi-nho}
MyBatis chạy đúng câu SQL bạn viết và không theo dõi trạng thái object. Ưu điểm và nhược điểm của nó đều bắt nguồn từ điều đó. Toàn bộ mục này là kiến thức tham khảo, chưa kiểm chứng trong lab.
:::

### 26.6 Kết hợp JPA và MyBatis

::: {.chua-kiem-chung}
Không có test nào trong lab chạy JPA cùng MyBatis. Điều kiện kỹ thuật dưới đây lấy từ tài liệu, và **hai bộ tài liệu không gộp thành một lời hứa duy nhất**: tài liệu MyBatis-Spring nói về `DataSourceTransactionManager` và `JtaTransactionManager` và không nhắc tới `JpaTransactionManager`; tài liệu Spring cho biết `JpaTransactionManager` có thể cho code JDBC thuần dùng chung connection nếu cấu hình đúng. Việc ghép hai điều đó vào một ứng dụng là điều bạn **phải tự kiểm chứng**. Nguồn: MyBatis-Spring, giao dịch, <https://mybatis.org/spring/transactions.html>; `SqlSessionTemplate`, <https://mybatis.org/spring/sqlsession.html>; Spring Framework, `JpaTransactionManager`, <https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/orm/jpa/JpaTransactionManager.html>; Hibernate User Guide, <https://docs.hibernate.org/orm/current/userguide/html_single/Hibernate_User_Guide.html>. Sách chưa đối chiếu được chương Flushing của User Guide; phần flush và bulk dưới đây dựa vào test của lab ở Chương 2 và 15.
:::

**Cơ chế, theo tài liệu.**

- MyBatis-Spring yêu cầu: "the `DataSource` specified for the transaction manager must be the same one that is used to create the `SqlSessionFactoryBean`", nếu không quản lý transaction không hoạt động.
- Javadoc `JpaTransactionManager` nói nó hỗ trợ truy cập `DataSource` trực tiếp trong transaction (code JDBC thuần dùng chung connection), nhưng "requires a vendor-specific `JpaDialect` to be configured", và `DataSource` nên khớp với `DataSource` của `EntityManagerFactory`. Code JDBC thuần phải lấy connection qua `DataSourceUtils` hoặc `TransactionAwareDataSourceProxy`.
- Với mapper do Spring quản lý, tài liệu MyBatis-Spring nói bạn **không** gọi `SqlSession.commit()`, `rollback()` hay `close()` (sẽ ném `UnsupportedOperationException`); và ngoài một transaction của Spring, mỗi lần gọi mapper được commit tự động.

**Hai quy tắc rút ra:** (1) chỉ **một** transaction manager và một `DataSource` cho cả hai công cụ; (2) mọi thao tác của hai bên nằm trong cùng một `@Transactional`.

Các bẫy dưới đây có chung một gốc: Hibernate giữ trạng thái trong persistence context, còn MyBatis chạy SQL thẳng vào database và **hai bên không biết nhau**. Phần cơ chế của Hibernate đã được lab chứng minh (Chương 2, 15, 17, 19); phần hành vi của MyBatis là theo tài liệu và chưa kiểm chứng.

#### 26.6.1 Hai công cụ có thật sự dùng chung transaction không {.mo-rong}

**Bối cảnh.** Service gọi `orderRepository.save(order)` (JPA) rồi `reportMapper.insertAudit(...)` (MyBatis) trong một `@Transactional`, và tin rằng nếu một bên lỗi thì cả hai rollback.

**Kết quả.** Chỉ đúng khi hai bên cùng connection và cùng transaction. Theo tài liệu, điều đó đòi hỏi cùng `DataSource` và một transaction manager phù hợp. Nếu bạn cấu hình hai transaction manager, hoặc `DataSource` của MyBatis khác của JPA, mỗi bên commit riêng và bạn có dữ liệu nửa vời. Ngoài transaction của Spring, mapper tự commit từng lệnh.

**Cách tránh.** Một `DataSource`, một transaction manager (xem 26.6.8). **Tự chạy thử:** trong một service `@Transactional`, ghi bằng cả hai rồi ném `RuntimeException`; đọc bằng `JdbcTemplate` xem cả hai dòng có biến mất không.

::: {.ghi-nho}
Rollback chung chỉ có khi hai công cụ dùng chung connection và một transaction manager. Hãy kiểm chứng bằng test rollback trên dự án của bạn.
:::

#### 26.6.2 JPA vừa persist chưa flush, MyBatis đọc {.mo-rong}

**Bối cảnh.** Trong một transaction, `em.persist(order)` rồi ngay sau đó `reportMapper.countOrders()`. Bạn mong báo cáo đếm cả đơn vừa tạo.

**Kết quả.** Lab cho thấy `persist` không INSERT ngay với id `SEQUENCE`, câu INSERT chờ đến lúc flush (mục 2.2.1), và Hibernate tự flush trước *query của chính nó*, kể cả native (mục 2.8). Câu MyBatis là JDBC thẳng, **không đi qua Hibernate**, nên theo cơ chế này không có gì kích hoạt flush: báo cáo có thể không thấy đơn mới. Đây là suy luận từ cơ chế, tài liệu MyBatis không nêu.

**Cách tránh.** Gọi `em.flush()` thủ công trước khi gọi MyBatis. **Tự chạy thử:** persist một đơn, gọi mapper đếm, so với kết quả sau `flush()`.

::: {.ghi-nho}
Auto flush của Hibernate chỉ bảo vệ query của Hibernate. Trước khi gọi MyBatis đọc dữ liệu vừa ghi bằng JPA, hãy `flush()`.
:::

#### 26.6.3 MyBatis UPDATE xong, entity JPA đang MANAGED {.mo-rong}

**Bối cảnh.** Entity `Product` đang MANAGED trong transaction. Một mapper MyBatis đổi giá của chính dòng đó. Sau đó code đọc `product.getPrice()`.

**Kết quả.** Cùng cơ chế với bulk update ở Chương 15: persistence context không biết database đã đổi, entity giữ giá cũ trong khi database đã là giá mới (mục 15.1.1). `em.find` lần nữa vẫn trả bản trong cache cấp 1 (mục 2.7).

**Cách tránh.** `em.refresh(product)` hoặc `em.clear()` sau khi MyBatis ghi, hoặc ghi bằng MyBatis ở đầu transaction trước khi tải entity (quy tắc ở mục 15.4). **Tự chạy thử:** tải entity, ghi bằng mapper, đọc lại field, rồi `refresh` và đọc lần nữa.

::: {.ghi-nho}
Sau khi MyBatis ghi một dòng mà JPA đang giữ, entity trong persistence context là bản cũ. Hãy `refresh` hoặc `clear`.
:::

#### 26.6.4 Flush entity cũ ghi đè giá trị MyBatis vừa sửa {.mo-rong}

**Bối cảnh.** Tiếp tục bẫy trước, nhưng code còn sửa một field *khác* của entity, ví dụ `product.setName(...)`.

**Kết quả.** Đây là bug nguy hiểm nhất của cách kết hợp. Theo cơ chế dirty checking (mục 2.3.1), khi flush Hibernate sinh UPDATE từ trạng thái entity; Hibernate mặc định liệt kê mọi cột của entity trong câu UPDATE, nên cột `price` có thể bị ghi lại bằng **giá cũ** mà MyBatis vừa sửa. Kết quả: mất cập nhật mà không có lỗi nào. Điều Hibernate thật sự đặt vào UPDATE phụ thuộc cấu hình (`@DynamicUpdate`); đây là điều phải kiểm tra trên entity của bạn.

**Cách tránh.** Không giữ entity MANAGED qua lệnh ghi của MyBatis lên cùng dòng. Nếu bắt buộc, `clear` ngay sau lệnh MyBatis và tải lại. **Tự chạy thử:** bật log SQL, tải entity, ghi `price` bằng mapper, đổi `name` bằng entity, commit, rồi đọc `price` bằng `JdbcTemplate`.

::: {.ghi-nho}
Kết hợp sai có thể tạo lost update mà không có lỗi nào. Quy tắc an toàn: hai công cụ không cùng ghi một dòng trong một transaction nếu entity còn MANAGED.
:::

#### 26.6.5 Version và câu UPDATE của MyBatis {.mo-rong}

**Bối cảnh.** Entity `Product` có `@Version`. Một mapper MyBatis có câu `update product set stock = stock - 1 where id = ?`.

**Kết quả.** Lab chứng minh với native UPDATE: câu SQL thô không tăng cột version thì `@Version` không bảo vệ đường ghi đó (mục 15.3.1). MyBatis là cùng trường hợp: câu bạn viết tự quyết định có đụng cột version hay không, Hibernate không biết.

**Cách tránh.** Tự viết `version = version + 1` trong câu MyBatis, hoặc chỉ cho một công cụ ghi bảng đó. Với phép tính đơn giản (trừ kho) UPDATE nguyên tử cũng là cách hợp lệ (mục 17.2.3). **Tự chạy thử:** giữ một bản entity cũ, ghi bằng mapper không tăng version, rồi lưu bản cũ và xem có `OptimisticLockException` không.

::: {.ghi-nho}
Cột version chỉ bảo vệ những đường ghi chịu tăng nó. Câu MyBatis phải tự tăng version, nếu không đường ghi đó nằm ngoài bảo vệ.
:::

#### 26.6.6 Cache cấp 2 không biết MyBatis đã sửa dữ liệu {.mo-rong}

**Bối cảnh.** Entity `Product` bật cache cấp 2. Một mapper sửa giá.

**Kết quả.** Giống mục 24.7: cache cấp 2 không thấy thay đổi ngoài Hibernate (mục 19.2.2) và `find` vẫn trả giá cũ cho tới khi cache hết hạn hoặc bị xóa. Hibernate có thể xóa vùng cache khi nó biết bảng nào bị đụng (mục 19.3), nhưng với câu MyBatis nó không biết.

**Cách tránh.** Evict thủ công vùng cache liên quan sau khi MyBatis ghi, hoặc không bật cache cấp 2 cho bảng mà MyBatis ghi. **Tự chạy thử:** `find` hai lần có cache, ghi bằng mapper, `find` lần ba trong transaction mới.

::: {.ghi-nho}
Mọi thay đổi database ngoài Hibernate, kể cả MyBatis, làm cache cấp 2 cũ đi. Đừng cache bảng có hai đường ghi.
:::

#### 26.6.7 Local cache của MyBatis trong cùng session {.mo-rong}

**Bối cảnh.** Trong một transaction, bạn gọi `reportMapper.countOrders()` hai lần, giữa hai lần có JPA `flush` ghi thêm một đơn.

**Kết quả.** Theo tài liệu MyBatis, mỗi session có local cache theo `SESSION` mặc định và nó chỉ bị xóa khi `update`, `commit`, `rollback` hoặc `close` *qua session đó*. Lệnh ghi do Hibernate thực hiện không đi qua session MyBatis, nên theo tài liệu cache không có lý do để bị xóa: lần gọi thứ hai có thể không chạy SQL. Chỗ này chưa kiểm chứng; tài liệu MyBatis-Spring không nêu cụ thể trường hợp có Hibernate chen vào.

**Cách tránh.** Biết cơ chế; đặt `localCacheScope=STATEMENT` nếu muốn mỗi lệnh đọc luôn chạy SQL. **Tự chạy thử:** gọi mapper đếm, ghi bằng JPA và `flush`, gọi mapper đếm lần nữa, so hai số.

::: {.ghi-nho}
MyBatis có cache của riêng nó theo session, và nó chỉ biết các lệnh ghi đi qua chính nó. Hãy chọn `localCacheScope` có chủ đích.
:::

#### 26.6.8 Hai transaction manager {.mo-rong}

**Bối cảnh.** Dự án khai `JpaTransactionManager` cho JPA và thêm `DataSourceTransactionManager` cho MyBatis, mỗi bean một cái.

**Kết quả.** `@Transactional` mặc định chỉ chọn một transaction manager; bên còn lại chạy ngoài transaction đó (ngoài transaction của Spring mapper tự commit, theo tài liệu MyBatis-Spring). Rollback không đồng bộ giữa hai bên. Javadoc Spring nói với nhiều resource giao dịch khác nhau thì cần JTA, nằm ngoài phạm vi chương này.

**Cách tránh.** Một `DataSource`, một transaction manager, và cấu hình `JpaDialect` để JDBC dùng chung connection như Javadoc yêu cầu. **Tự chạy thử:** test rollback ở 26.6.1 là cách phát hiện cấu hình sai này.

::: {.ghi-nho}
Hai công cụ, một transaction manager. Hai manager nghĩa là rollback không còn đồng bộ.
:::

#### 26.6.9 Object MyBatis trả về không phải entity {.mo-rong}

**Bối cảnh.** Mapper trả về một object `Product` (cùng class với entity JPA). Code truyền nó cho `repository.save(product)`.

**Kết quả.** Object do MyBatis tạo là object Java thường: không MANAGED, không proxy, không nằm trong persistence context. Với Spring Data, `save` chọn `persist` hay `merge` tùy cách nó đoán entity mới hay cũ (mục 2.9); id đã có sẽ thành `merge`, kèm một câu SELECT để tải bản hiện có (mục 2.9.2). Trộn lẫn hai nguồn object làm khó biết đâu là entity thật.

**Cách tránh.** Dùng DTO riêng cho kết quả MyBatis, không dùng chung class với entity. **Tự chạy thử:** bật log SQL, lưu object từ mapper bằng `repository.save` và đọc câu SQL.

::: {.ghi-nho}
Object từ MyBatis chỉ là dữ liệu. Đặt tên và class riêng (DTO), đừng đưa nó vào luồng entity của JPA.
:::

#### 26.6.10 JPA ghi, MyBatis đọc: mẫu mặc định nên dùng {.mo-rong}

**Bối cảnh.** Phần lớn trộn lẫn đau đầu nhất là khi hai bên cùng ghi. Mẫu ít rủi ro nhất là chia việc: JPA lo mọi lệnh ghi, MyBatis chỉ đọc.

**Kết quả.** Các bẫy 26.6.3 đến 26.6.6 đều xuất phát từ việc MyBatis *ghi*. Khi MyBatis chỉ đọc, persistence context và cache cấp 2 của Hibernate không bị đổi ngoài ý muốn. Báo cáo bằng MyBatis không dính N+1 của lazy loading và không nạp entity vào persistence context. Bẫy còn lại là 26.6.2: MyBatis đọc có thể không thấy dữ liệu JPA chưa flush, nên dùng `flush()` khi cần đọc ngay trong cùng transaction, hoặc đọc ở transaction sau.

**Cách tránh.** Thiết lập quy ước: mapper chỉ có `@Select`; mọi lệnh ghi đi qua repository. Có thể kiểm bằng một test kiến trúc hoặc review code. **Tự chạy thử:** viết một test quét các mapper và báo lỗi nếu có `@Insert`, `@Update` hoặc `@Delete`.

::: {.ghi-nho}
Mẫu an toàn nhất khi kết hợp: JPA ghi, MyBatis đọc, flush trước khi đọc trong cùng transaction.
:::

**Bốn mẫu kết hợp.** (1) *JPA ghi, MyBatis đọc*: mặc định. (2) *MyBatis cho báo cáo và truy vấn nặng, JPA cho phần còn lại*: gần giống mẫu một nhưng tách theo module. (3) *MyBatis ghi hàng loạt* với `ExecutorType.BATCH` (tài liệu: executor này gom các lệnh update và chỉ chạy khi cần, `flushStatements()` ép chạy ngay), kèm `entityManager.clear()` sau đó vì persistence context đã cũ; lưu ý tài liệu MyBatis-Spring nói không đổi `ExecutorType` khi đang có transaction với loại khác. (4) **Không nên:** hai công cụ cùng ghi một bảng trong cùng luồng nghiệp vụ mà không có quy tắc rõ về ai ghi cột nào.

### 26.7 Mười bốn tình huống chọn công cụ

Mỗi tình huống có cùng khuôn: yêu cầu, chọn gì, vì sao, **bug cụ thể nếu chọn sai**, và chốt. Phần nào dựa vào lab thì trỏ tới mục; phần nào về MyBatis là kiến thức tham khảo ở 26.5.

#### 26.7.1 Màn hình CRUD khách hàng và đơn hàng {.mo-rong}

**Bối cảnh.** Màn hình quản trị: danh sách, thêm, sửa, xóa khách hàng và đơn hàng, mỗi đơn có nhiều dòng.

**Kết quả.** Chọn JPA.

**Vì sao.** Thao tác theo aggregate, mỗi lần chạm ít dòng, quy tắc vòng đời rõ ràng: cascade, orphanRemoval và dirty checking làm hết việc lặp (Chương 5, 6, mục 2.3).

**Nếu chọn sai.** Viết SQL tay cho từng bảng: khi thêm cột, bạn sửa câu INSERT, UPDATE, `resultMap` và DTO; quên một chỗ thì dữ liệu mới không được lưu mà không có lỗi.

::: {.ghi-nho}
CRUD theo aggregate là việc JPA sinh ra để làm. Chọn JPA.
:::

#### 26.7.2 Đặt hàng: tạo đơn, nhiều dòng, trừ tồn kho {.mo-rong}

**Bối cảnh.** Một yêu cầu tạo đơn có nhiều dòng và trừ tồn kho của từng sản phẩm; nhiều khách đặt cùng lúc.

**Kết quả.** Chọn JPA kèm `@Version` cho tồn kho, hoặc UPDATE nguyên tử.

**Vì sao.** Lab tái hiện đúng bug này: hai khách cùng mua, tồn kho còn 9 thay vì 8 (mục 17.1.1) và sự cố flash sale (mục 22.2). `@Version` làm giao dịch đến sau bị từ chối rồi retry (mục 17.2.1).

**Nếu chọn sai.** Đọc tồn kho rồi ghi lại số mới bằng SQL tay mà không có version hay khóa: hai request cùng đọc 10, cùng ghi 4, tồn kho sai mà không báo lỗi.

::: {.ghi-nho}
Nghiệp vụ ghi có tranh chấp cần cơ chế khóa. JPA kèm `@Version` cho sẵn; MyBatis thì bạn tự làm.
:::

#### 26.7.3 Danh sách đơn 20 cột, phân trang, lọc nhiều điều kiện {.mo-rong}

**Bối cảnh.** Màn hình danh sách đơn hàng, chỉ hiển thị, có phân trang và bộ lọc.

**Kết quả.** Chọn JPA projection DTO hoặc MyBatis.

**Vì sao.** Màn hình không sửa entity nên không cần persistence context. Sự cố ở Chương 22 là đúng bài toán này: trang 10 đơn tốn 22 câu SELECT khi trả entity (mục 22.1.1) và còn 2 câu khi đọc DTO (mục 22.1.3).

**Nếu chọn sai.** Trả entity rồi lặp trong view: mỗi dòng chạm quan hệ LAZY là một SELECT thêm (mục 11.3.3).

::: {.ghi-nho}
Đọc để hiển thị thì dùng projection hoặc MyBatis. Entity chỉ khi sắp sửa nó.
:::

#### 26.7.4 Báo cáo doanh thu theo tháng với window function {.mo-rong}

**Bối cảnh.** Báo cáo doanh thu từng tháng kèm doanh thu cộng dồn.

**Kết quả.** Chọn MyBatis hoặc native query.

**Vì sao.** Hàm cửa sổ là SQL của database, không phải JPQL chuẩn. Native query của JPA làm được (mục 12.2, 12.5.3), nhưng đã thoát khỏi mô hình entity. Nếu cả báo cáo là SQL, MyBatis cho bạn chỗ đặt SQL rõ ràng hơn.

**Nếu chọn sai.** Ép vào JPQL: bạn thành tải mọi đơn lên bộ nhớ rồi cộng dồn bằng Java, chậm và tốn bộ nhớ.

::: {.ghi-nho}
Báo cáo bằng SQL nặng thì viết SQL. Native query hoặc MyBatis, không ép vào JPQL.
:::

#### 26.7.5 Dashboard join sáu bảng, chỉ đọc {.mo-rong}

**Bối cảnh.** Một trang dashboard cần dữ liệu từ khách hàng, đơn, dòng hàng, sản phẩm, thanh toán và vận chuyển.

**Kết quả.** Chọn MyBatis hoặc projection native.

**Vì sao.** Chỉ đọc, nhiều bảng: không có gì để dirty checking. Fetch nhiều collection của JPA có hai bẫy: List thì `MultipleBagFetchException` (mục 11.5.1), Set thì chạy nhưng nhân dòng (mục 11.5.3).

**Nếu chọn sai.** Fetch join nhiều collection: số dòng trả về bằng tích số các collection, cartesian product.

::: {.ghi-nho}
Dashboard nhiều bảng chỉ đọc không cần ORM. Dùng câu SQL phẳng.
:::

#### 26.7.6 Sửa giá 50.000 sản phẩm theo một quy tắc {.mo-rong}

**Bối cảnh.** Cuối tháng tăng giá 5% cho toàn bộ sản phẩm một nhóm hàng.

**Kết quả.** Chọn bulk query (JPQL hoặc MyBatis).

**Vì sao.** Một câu UPDATE thay cho 50.000 lần tải và sửa. Với JPA hãy theo quy tắc ở mục 15.4: bulk đầu transaction, hoặc `flush` rồi `clear`; tự tăng version nếu có `@Version` (mục 15.3).

**Nếu chọn sai.** Tải từng entity: 50.000 object nằm trong persistence context, flush so từng cái (mục 18.4).

::: {.ghi-nho}
Sửa hàng loạt cùng một quy tắc thì dùng một câu UPDATE. Nhớ bù những gì bulk bỏ qua.
:::

#### 26.7.7 Import một triệu dòng từ file {.mo-rong}

**Bối cảnh.** Nhập dữ liệu khách hàng từ file CSV lớn.

**Kết quả.** Chọn JDBC hoặc MyBatis batch; nếu dùng Hibernate thì `StatelessSession` hoặc flush kèm clear theo lô.

**Vì sao.** Lab tái hiện sự cố import chậm dần (mục 22.4): 200 dòng là 200 INSERT và persistence context giữ 200 entity. `StatelessSession` bỏ persistence context nhưng cũng bỏ dirty checking, cascade và lazy loading (mục 18.5).

**Nếu chọn sai.** `saveAll` trên cả file: một triệu entity trong bộ nhớ, thời gian flush tăng dần, có thể hết bộ nhớ.

::: {.ghi-nho}
Dữ liệu cực lớn không cần quản lý trạng thái từng object. Dùng batch ở mức JDBC hoặc dọn persistence context theo lô.
:::

#### 26.7.8 Database legacy, bảng không có khóa chuẩn {.mo-rong}

**Bối cảnh.** Phải đọc ghi một database có sẵn: bảng không khóa chính, tên cột khó đọc, quan hệ chỉ có trong đầu người cũ.

**Kết quả.** Chọn MyBatis.

**Vì sao.** Mỗi entity JPA phải có định danh (`@Id`, mục 1.2) và dirty checking dựa vào nó. MyBatis chỉ cần câu SQL và ánh xạ cột.

**Nếu chọn sai.** Ép một `@Id` giả lên bảng không có khóa: hai dòng có giá trị trùng bị coi là cùng entity, và Hibernate có thể cập nhật nhầm dòng.

::: {.ghi-nho}
JPA đòi mỗi dòng có danh tính. Schema không có thì dùng công cụ không đòi điều đó.
:::

#### 26.7.9 Tìm kiếm động theo nhiều tiêu chí tùy chọn {.mo-rong}

**Bối cảnh.** Màn hình tìm kiếm với mười bộ lọc, người dùng chọn bộ lọc nào cũng được.

**Kết quả.** Chọn Specification (JPA) hoặc `<if>` động của MyBatis.

**Vì sao.** Specification đổi SQL theo điều kiện có thật (mục 12.4.1), nhưng lưu ý count query của phân trang (mục 12.4.2). MyBatis dựng điều kiện bằng `<where>` và `<if>` trong XML.

**Nếu chọn sai.** Nối chuỗi JPQL hoặc SQL bằng tay: dễ sai dấu `AND`, khó đọc, và nếu nối giá trị người dùng thẳng vào chuỗi thì dính SQL injection.

::: {.ghi-nho}
Điều kiện động phải dựng bằng công cụ có tham số hóa, đừng nối chuỗi.
:::

#### 26.7.10 Xóa mềm khắp nơi {.mo-rong}

**Bối cảnh.** Mọi bảng nghiệp vụ dùng cờ `deleted` thay vì xóa thật.

**Kết quả.** Chọn JPA với `@SQLRestriction` hoặc `@SoftDelete`.

**Vì sao.** Lab cho thấy `@SoftDelete` đổi `remove` thành UPDATE cờ và collection của cha tự lọc (mục 13.4), `@SQLRestriction` lọc JPQL và `find` (mục 13.2). Điều kiện nằm một chỗ trong mapping.

**Nếu chọn sai.** MyBatis: mỗi câu SELECT phải nhớ `where deleted = false`; quên một câu thì dòng đã xóa hiện ra. Cả bên JPA cũng có lỗ: native SELECT không tự lọc (mục 13.2.2, 13.4.4).

::: {.ghi-nho}
Quy tắc áp dụng khắp nơi nên đặt ở mapping. Nhưng native query vẫn đi ngoài quy tắc đó.
:::

#### 26.7.11 Dịch vụ nhỏ ba bảng, đội giỏi SQL {.mo-rong}

**Bối cảnh.** Một microservice đọc ghi ba bảng đơn giản, cả đội quen SQL và chưa ai dùng Hibernate.

**Kết quả.** Chọn MyBatis hoặc `JdbcTemplate`.

**Vì sao.** Bạn phải học persistence context, flush, proxy và N+1 (cả cuốn sách này) để lấy về ba bảng đơn giản. Lợi ích của ORM nhỏ khi mô hình nhỏ.

**Nếu chọn sai.** Dùng JPA mà không hiểu cơ chế: gặp những bug ở mục 26.3 và mất nhiều thời gian chẩn đoán hơn cả thời gian viết SQL tay.

::: {.ghi-nho}
Chi phí học ORM chỉ đáng khi mô hình đủ phức tạp. Dịch vụ nhỏ, đội giỏi SQL thì công cụ mỏng hơn là lựa chọn tốt.
:::

#### 26.7.12 Ghi bằng JPA, báo cáo bằng MyBatis {.mo-rong}

**Bối cảnh.** Hệ thống có phần nghiệp vụ ghi theo mô hình đơn hàng, và phần báo cáo nặng.

**Kết quả.** Kết hợp theo mẫu "JPA ghi, MyBatis đọc" (mục 26.6.10).

**Vì sao.** Mỗi công cụ làm đúng việc nó giỏi, và mẫu này tránh phần lớn bẫy kết hợp vì MyBatis không ghi.

**Nếu chọn sai.** Quên `flush` trước khi gọi mapper báo cáo: đơn vừa tạo chưa có trong báo cáo (mục 26.6.2). Hoặc để mapper ghi cùng bảng: entity cũ, lost update (mục 26.6.4).

::: {.ghi-nho}
Kết hợp được, nhưng giữ quy ước một chiều: JPA ghi, MyBatis đọc, flush trước khi đọc.
:::

#### 26.7.13 Chuyển dần ứng dụng MyBatis cũ sang JPA {.mo-rong}

**Bối cảnh.** Ứng dụng cũ viết bằng MyBatis; đội muốn chuyển từng phần sang JPA mà không viết lại một lần.

**Kết quả.** Chạy song song theo module: mỗi bảng có đúng một công cụ ghi tại một thời điểm.

**Vì sao.** Hai công cụ cùng ghi một bảng chính là các bẫy 26.6.3 đến 26.6.6. Chia theo module cho phép chuyển từng bảng: chuyển xong thì chỉ JPA ghi, MyBatis còn lại chỉ đọc.

**Nếu chọn sai.** Chuyển nửa vời, một bảng hai đường ghi: cache cấp 2 cũ, `@Version` không bảo vệ câu MyBatis, entity cũ ghi đè.

::: {.ghi-nho}
Khi chuyển dần, hãy định ra bảng nào thuộc công cụ nào. Một bảng một người ghi.
:::

#### 26.7.14 Hàng nghìn UPDATE mỗi giây {.mo-rong}

**Bối cảnh.** Một job cập nhật vị trí hoặc số liệu hàng nghìn dòng mỗi giây.

**Kết quả.** Chọn MyBatis batch hoặc JDBC.

**Vì sao.** Lab cho thấy dirty checking và persistence context phình là vấn đề thật ở số lượng lớn (mục 18.4), và batch cần `SEQUENCE`, `IDENTITY` thì tắt batch (mục 18.2). Với MyBatis, `ExecutorType.BATCH` gom các update (tài liệu), nhưng đây là hiệu năng mà bạn **phải tự đo**.

**Nếu chọn sai.** Mỗi lần flush Hibernate quét mọi entity MANAGED để tìm thay đổi; càng nhiều entity càng chậm.

::: {.ghi-nho}
Tốc độ ghi cao thì bỏ lớp theo dõi trạng thái. Và luôn đo trên dữ liệu thật trước khi kết luận.
:::

### 26.8 Dấu hiệu lạm dụng và checklist cuối chương

**Mười dấu hiệu đang lạm dụng JPA:**

1. Viết `@Query(nativeQuery = true)` khắp nơi: bạn đang dùng JPA như một lớp bọc SQL mà vẫn trả giá của entity.
2. `findAll()` rồi lọc, đếm hoặc cộng bằng stream trong Java.
3. Trả entity ra ngoài service hoặc ra API.
4. Màn hình danh sách tải entity đầy đủ với mọi quan hệ.
5. Vòng lặp gọi `save` từng dòng cho cả nghìn dòng.
6. Dùng `IDENTITY` cho bảng ghi nhiều rồi thắc mắc vì sao batch không chạy.
7. Bulk update giữa transaction không `clear`.
8. Gắn `CascadeType.ALL` lên mọi quan hệ cho đỡ phải nghĩ.
9. Bật cache cấp 2 cho bảng có nhiều đường ghi ngoài Hibernate.
10. Không có test nào đếm số câu SQL.

**Mười dấu hiệu đang lạm dụng MyBatis:**

1. Tự cài lại cascade bằng cách gọi tay nhiều lệnh theo thứ tự.
2. Copy `resultMap` giống nhau ở nhiều nơi.
3. Dùng `${}` với dữ liệu từ người dùng.
4. Nested select cho danh sách lớn (N+1 Selects).
5. Tự cài lại optimistic locking rồi quên ở vài câu UPDATE.
6. Mỗi lần đổi cột phải sửa nhiều file mà không có test bắt.
7. Quên `where deleted = false` ở một câu.
8. Dùng chung class cho kết quả MyBatis và entity JPA.
9. Hai công cụ cùng ghi một bảng không có quy ước.
10. Không biết local cache của session đang làm gì.

**Checklist chọn công cụ cho một tính năng mới:**

```text
[ ] Tính năng này ghi hay chỉ đọc?
[ ] Có quy tắc vòng đời giữa nhiều bảng (cascade, orphanRemoval) không?
[ ] Có tranh chấp ghi cần @Version hoặc khóa không?
[ ] Mỗi lần chạm bao nhiêu dòng: vài dòng hay hàng nghìn?
[ ] Có cần hàm riêng của database (window function, CTE, jsonb) không?
[ ] Nếu kết hợp hai công cụ: ai ghi, ai đọc, và một transaction manager?
[ ] Đã viết test chứng minh quyết định bằng dữ liệu thật?
```

**Năm câu tự kiểm tra.**

1. Một entity MANAGED, mapper MyBatis đổi giá dòng đó, rồi code đọc `getPrice()`. Nó trả giá nào, và vì sao? *Đáp án: giá cũ. Persistence context không biết database đã đổi (mục 26.6.3, cơ chế ở mục 15.1.1).*
2. Vì sao câu native UPDATE có thể làm `@Version` vô tác dụng? *Đáp án: câu SQL tự quyết định có tăng cột version hay không; nếu không thì ai giữ bản cũ vẫn lưu được (mục 15.3.1).*
3. Khi nào chọn `JdbcTemplate` hoặc MyBatis thay cho JPA? *Đáp án: khi việc là SQL phức tạp chỉ đọc, ghi số lượng rất lớn, schema legacy, hoặc mô hình quá nhỏ để đáng học ORM (mục 26.7).*
4. Mẫu kết hợp ít rủi ro nhất là gì? *Đáp án: JPA ghi, MyBatis đọc, flush trước khi đọc trong cùng transaction (mục 26.6.10).*
5. Vì sao "cache cấp 1" của Hibernate và local cache của MyBatis không giống nhau? *Đáp án: cái đầu là bản sao trạng thái entity dùng cho dirty checking và nhận diện cùng một dòng; cái sau là cache kết quả truy vấn theo session (mục 26.5).*

::: {.ghi-nho}
Không có công cụ tốt nhất, chỉ có công cụ hợp việc. JPA mạnh khi cần quản lý trạng thái theo aggregate; MyBatis và SQL thuần mạnh khi cần kiểm soát từng câu. Khi kết hợp, giữ một chiều ghi và một transaction manager. Phần MyBatis và kết hợp của chương là kiến thức tham khảo, hãy tự chạy thử trên dự án của bạn.
:::
