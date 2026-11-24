## Phụ lục F. Bảng thuật ngữ

Sách giữ nguyên thuật ngữ kỹ thuật tiếng Anh vì bạn sẽ gặp chúng trong tài liệu, log và exception. Bảng dưới đây giải thích nghĩa bằng tiếng Việt và chỉ mục nơi thuật ngữ được giải thích rõ nhất trong sách. Thuật ngữ xếp theo nhóm chủ đề, không theo thứ tự chữ cái.

### Entity và vòng đời

| Thuật ngữ | Nghĩa | Mục |
|---|---|---|
| entity | Class Java được ánh xạ tới một bảng; mỗi object là một dòng. | 1.2 |
| persistence context | Vùng nhớ của Hibernate giữ các entity đang được quản lý trong một transaction, và theo dõi thay đổi của chúng. Còn gọi là cache cấp 1. | 2.1 |
| TRANSIENT | Entity vừa tạo bằng `new`, Hibernate chưa biết tới, chưa có dòng trong database. | 2.1 |
| MANAGED | Entity đang nằm trong persistence context: Hibernate theo dõi và tự ghi thay đổi khi flush. | 2.1 |
| DETACHED | Entity đã có dòng trong database nhưng không còn nằm trong persistence context (ví dụ sau khi transaction kết thúc); sửa nó không ghi gì. | 2.1 |
| REMOVED | Entity đã được đánh dấu xóa; câu DELETE chạy lúc flush. | 2.1 |
| flush | Thời điểm Hibernate gửi các thay đổi đang chờ (INSERT, UPDATE, DELETE) xuống database. Khác với commit. | 2.2 |
| commit | Thời điểm database chốt transaction. Commit luôn flush trước, nhưng flush không có nghĩa là commit. | 2.2 |
| dirty checking | Cơ chế so sánh entity MANAGED với bản sao lúc tải để phát hiện thay đổi và tự sinh UPDATE. | 2.3 |
| snapshot | Bản sao trạng thái của entity (hoặc collection) lúc tải, dùng làm mốc cho dirty checking và orphanRemoval. | 2.3, 6.3 |
| merge | Sao chép trạng thái của entity DETACHED vào một bản MANAGED; trả về bản MANAGED, không biến object truyền vào thành MANAGED. | 2.4 |
| proxy | Object giả đại diện cho entity chưa tải; chạm vào dữ liệu mới SELECT. | 2.6 |
| getReference | Lấy proxy theo id mà không SELECT, thường để gán khóa ngoại. | 2.6 |
| cache cấp 1 | Tên khác của persistence context: trong một transaction, một dòng database ứng với đúng một instance. | 2.7 |
| Persistable | Interface của Spring Data cho phép entity tự báo "mới hay cũ", để `save` chọn persist hay merge. | 2.9 |

### Quan hệ và khóa

| Thuật ngữ | Nghĩa | Mục |
|---|---|---|
| owning side | Phía của quan hệ mà Hibernate đọc để ghi khóa ngoại (hoặc dòng bảng nối) vào database. | 4.1 |
| inverse side | Phía còn lại (có `mappedBy`): Hibernate không ghi gì từ phía này, chỉ dùng để đọc. | 4.1 |
| khóa ngoại (FK) | Cột trong bảng con trỏ tới khóa chính của bảng cha. | 1.3 |
| bảng nối (join table) | Bảng trung gian giữ cặp khóa của hai bảng, dùng cho quan hệ nhiều-nhiều (và OneToMany một chiều không có JoinColumn). | 4.5, 8.1 |
| bag | `List` không có thứ tự và không có khóa chính ở bảng nối; đổi collection thì Hibernate xóa hết dòng nối rồi ghi lại. | 8.3 |
| recreate | Hành vi của bag: DELETE toàn bộ dòng nối của cha rồi INSERT lại phần tử còn lại. | 8.3 |
| cascade | Cách để một thao tác (persist, merge, remove...) lan từ entity cha sang entity con. | 5.1 |
| orphanRemoval | Xóa dòng của con khi con bị gỡ khỏi collection của cha ("con mồ côi"). | 6.1 |
| surrogate key | Khóa chính do hệ thống sinh (id tự tăng, sequence), không mang ý nghĩa nghiệp vụ. | 1.3 |
| natural key | Khóa có ý nghĩa nghiệp vụ, ví dụ mã sản phẩm (sku). | 3.3, 9.4 |
| derived identity | Khóa chính của entity con lấy một phần hoặc toàn bộ từ khóa của entity cha (dùng `@MapsId`). | 7.3, 9.3 |
| composite key | Khóa chính (hoặc khóa ngoại) gồm nhiều cột. | 9.1, 9.2 |
| embeddable | Class giá trị gom nhiều cột vào bảng của entity chủ, không có bảng riêng. | 3.1 |
| discriminator | Cột phân biệt loại lớp con trong chiến lược kế thừa một bảng. | 10.3 |

### Đọc dữ liệu

| Thuật ngữ | Nghĩa | Mục |
|---|---|---|
| EAGER, LAZY | Tải quan hệ ngay khi tải entity, hoặc hoãn tới lúc chạm vào. | 11.1 |
| N+1 | Một câu SELECT lấy N dòng, rồi N câu SELECT nữa, mỗi câu cho một dòng liên quan. | 11.4 |
| join fetch | Cú pháp JPQL nối bảng và nạp luôn quan hệ trong một câu SELECT. | 11.4, 12.1 |
| entity graph | Khai báo "tải những quan hệ nào" cho một truy vấn cụ thể, không đổi mapping. | 11.4 |
| batch fetch | Tải quan hệ lazy theo lô (`@BatchSize`): thay N câu bằng vài câu `in` hoặc `any`. | 11.4 |
| Cartesian product | Kết quả nhân lên khi join hai collection cùng lúc (2 nhân 3 thành 6 dòng cho mỗi cha). | 11.5 |
| JPQL, HQL | Ngôn ngữ truy vấn theo entity. JPQL là chuẩn JPA; HQL là bản mở rộng của Hibernate. | 12.1 |
| native query | Câu SQL thô, gửi nguyên cho database. | 12.2 |
| DTO projection | Truy vấn chỉ lấy cột cần thiết vào object dữ liệu, không tạo entity. | 12.5 |
| Specification | Cách của Spring Data để ghép điều kiện lọc động thành một truy vấn. | 12.4 |
| soft delete | Xóa mềm: đánh dấu dòng đã xóa bằng một cột cờ thay vì DELETE thật. | 13.4 |

### Ghi dữ liệu, transaction và khóa

| Thuật ngữ | Nghĩa | Mục |
|---|---|---|
| bulk update | Câu UPDATE hoặc DELETE chạy trực tiếp trên database cho nhiều dòng, bỏ qua persistence context. | 15.1 |
| `@Version` | Cột đếm phiên bản để phát hiện hai người cùng sửa một dòng. | 14.4, 17.3 |
| optimistic lock | Không khóa lúc đọc; khi ghi, so version và báo lỗi nếu đã có người khác sửa. | 17.3 |
| pessimistic lock | Khóa dòng ngay lúc đọc (`SELECT ... FOR UPDATE`); người khác phải chờ. | 17.4 |
| lost update | Hai transaction cùng đọc rồi cùng ghi, một thay đổi bị ghi đè mất. | 17.1 |
| NOWAIT | Yêu cầu khóa mà không chờ: có người đang giữ thì lỗi ngay. | 17.4 |
| SKIP LOCKED | Bỏ qua các dòng đang bị khóa, thường dùng để nhiều worker lấy việc khác nhau. | 17.5 |
| deadlock | Hai transaction giữ khóa chéo nhau và cùng chờ; database hủy một bên. | 17.6 |
| propagation | Quy tắc transaction của method được gọi: dùng chung (`REQUIRED`) hay tạo mới (`REQUIRES_NEW`). | 16.4 |
| rollback-only | Cờ đánh dấu transaction chỉ còn đường rollback, dù code bên ngoài đã bắt exception. | 16.4 |
| self-invocation | Gọi method có `@Transactional` từ chính bean đó: không đi qua proxy nên annotation mất tác dụng. | 16.3 |
| Open Session in View (OSIV) | Giữ session mở suốt request web để lazy loading còn chạy ở lớp trên service. | 16.7 |

### Hiệu năng và công cụ

| Thuật ngữ | Nghĩa | Mục |
|---|---|---|
| batch insert | Gom nhiều câu INSERT thành một lô gửi một lần. | 18.1 |
| `SEQUENCE`, `IDENTITY` | Hai cách sinh id: hỏi sequence của database trước, hoặc để cột tự tăng sinh khi INSERT (làm tắt batch). | 1.3, 18.2 |
| allocationSize | Số id Hibernate xin một lần từ sequence để dùng dần, giảm số lần gọi `nextval`. | 18.3 |
| StatelessSession | Phiên làm việc không có persistence context: không dirty checking, không cascade, không lazy loading. | 18.5 |
| second-level cache | Cache dùng chung giữa các transaction, nằm ngoài persistence context. | 19.1 |
| query cache | Cache kết quả của một truy vấn cụ thể (cùng câu và cùng tham số). | 19.5 |
| bytecode enhancement | Hibernate sửa bytecode của entity lúc build để có LAZY thật ở nhiều chỗ và dirty tracking. | Chương 20 |
| statement inspector | Điểm móc của Hibernate cho phép nhìn (và sửa) mọi câu SQL trước khi gửi; lab dùng để đếm SQL. | 1.4 |
| Testcontainers | Thư viện chạy PostgreSQL thật trong Docker cho test. | Phụ lục E |
| ADR | Architecture Decision Record: tài liệu ngắn ghi một quyết định kỹ thuật và lý do. | 23.4 |
