## Phụ lục G. Đối chiếu JPA và Hibernate

Sách dùng Hibernate, nhưng nhiều thứ trong code ví dụ là JPA chuẩn (chạy được trên mọi provider), một số là của Hibernate, và một số khác là của Spring Data hoặc Spring. Phụ lục này gom lại, theo thứ tự xuất hiện trong sách, mọi chỗ mà các đoạn **Trong JPA thuần.** chỉ ra khác biệt. Nếu bạn muốn viết code ít phụ thuộc vào Hibernate, đây là danh sách những chỗ cần cân nhắc.

Cột "Thuộc về" có bốn giá trị: JPA (chuẩn Jakarta Persistence), Hibernate (riêng của Hibernate), Spring Data (riêng của Spring Data JPA) và Spring (riêng của Spring Framework). Quy ước đọc ví dụ trong sách nằm ở Chương 1, mục 1.5.

### Mapping và vòng đời (Phần I, Phần II)

| Thành phần dùng trong sách | Thuộc về | Cách làm trong JPA thuần | Mục |
|---|---|---|---|
| `@OnDelete(action = CASCADE)` | Hibernate | JPA không có. Tự viết DDL (migration) cho khóa ngoại có `ON DELETE CASCADE`, rồi để database xóa con. | 5.5 |
| `Persistable` | Spring Data | JPA không có. Dùng `@Version` hoặc tự quyết định `persist` hay `merge` trong code. | 9.3 |
| `Hibernate.unproxy` | Hibernate | JPA không có. Tải entity thật bằng truy vấn (`join fetch` quan hệ) để tránh proxy của lớp cha. | 10.4 |
| Annotation của Chương 7 và 8 (`@OneToOne`, `@ManyToMany`, `@JoinTable`, `@OrderColumn`, `@MapsId`) | JPA | Không cần thay đổi. Chỉ khái niệm "bag" là thuật ngữ của Hibernate cho `List` không có thứ tự. | Chương 7, 8 |

### Fetch và truy vấn (Phần III)

| Thành phần dùng trong sách | Thuộc về | Cách làm trong JPA thuần | Mục |
|---|---|---|---|
| `FetchType`, proxy, `join fetch` | JPA | Giữ nguyên. Kiểm tra đã tải chưa: `PersistenceUnitUtil.isLoaded(x)`. | 11.1, 11.4 |
| `Hibernate.initialize` | Hibernate | Chạm vào collection (ví dụ `size()`) trong transaction, hoặc dùng `join fetch`. | 11.3 |
| `@EntityGraph` trên method repository | Spring Data | `@NamedEntityGraph` trên entity, truyền qua hint `jakarta.persistence.fetchgraph`; hoặc dựng `EntityGraph` bằng API. | 11.4 |
| `@BatchSize` | Hibernate | JPA không có. Cấu hình toàn cục `hibernate.default_batch_fetch_size` cũng của Hibernate; hoặc tự viết truy vấn `in`. | 11.4 |
| `@Fetch(FetchMode.SUBSELECT)` | Hibernate | JPA không có. Tự viết truy vấn thứ hai `where c.id in (select ...)`. | 11.4 |
| `Page`, `Pageable`, `countQuery` | Spring Data | Tự chạy hai truy vấn: một với `setFirstResult` và `setMaxResults`, một câu count. | 12.3 |
| `Specification` | Spring Data | Criteria API (`CriteriaBuilder`, `CriteriaQuery`); dựng thêm một `CriteriaQuery<Long>` cho count. | 12.4 |
| Interface projection | Spring Data | Constructor expression (`select new ...`) hoặc `Tuple`. | 12.5 |
| `select new ...` (constructor expression) | JPA | Giữ nguyên. | 12.5 |
| `@Formula` | Hibernate | Viết truy vấn đếm khi cần, hoặc trả DTO chứa số đếm. | 13.1 |
| `@SQLRestriction` | Hibernate | Viết điều kiện vào từng truy vấn, hoặc đặt điều kiện mặc định ở một chỗ dùng chung (ví dụ Specification). | 13.2 |
| `@Filter` | Hibernate | Thêm điều kiện vào từng truy vấn, hoặc bọc trong một lớp repository. | 13.3 |
| `@SoftDelete` | Hibernate | Tự thêm field `deleted`, method đổi cờ, và nhớ thêm điều kiện lọc vào mọi truy vấn. | 13.4 |

### Ghi dữ liệu, transaction và khóa (Phần IV)

| Thành phần dùng trong sách | Thuộc về | Cách làm trong JPA thuần | Mục |
|---|---|---|---|
| `@OptimisticLock(excluded = true)` | Hibernate | JPA không có theo từng thuộc tính. Gần nhất: dùng quan hệ hai chiều với `mappedBy`, vì collection inverse không làm cha dirty. | 14.4 |
| `@DynamicUpdate` | Hibernate | JPA không có. Viết câu cập nhật có chủ đích (`update ... set ... where id = :id`) cho thao tác cần ghi ít cột. | 14.6 |
| `@Modifying(clearAutomatically = true)` | Spring Data | `executeUpdate()` rồi tự gọi `em.clear()` (và `em.flush()` trước đó). | 15.1 |
| Bulk JPQL, native query | JPA | Giữ nguyên (`executeUpdate`, `createNativeQuery`). Hậu quả với persistence context và `@Version` áp dụng cho mọi provider. | 15.1, 15.3 |
| `@Transactional` | Spring | `EntityTransaction`: `begin()`, `commit()`, `rollback()`, tự quản lý trong code. | 16.1 |
| `@Transactional(readOnly = true)` | Spring | `em.setFlushMode(FlushModeType.COMMIT)` hoặc không gọi `flush`. | 16.2 |
| `rollbackFor` và quy tắc rollback theo loại exception | Spring | Tự gọi `rollback()` trong khối `catch`; JPA không có quy tắc "checked thì commit". | 16.5 |
| `@Version`, `LockModeType`, hint `jakarta.persistence.lock.timeout` | JPA | Giữ nguyên. | 17.3, 17.4 |
| `@Lock`, `@QueryHints` | Spring Data | `em.find(..., LockModeType.PESSIMISTIC_WRITE, Map.of("jakarta.persistence.lock.timeout", 0))` hoặc `query.setLockMode(...)`. | 17.4 |
| Giá trị `-2` của `lock.timeout` (chọn SKIP LOCKED) | Hibernate | JPA chỉ định nghĩa `0` và số dương; không có cách nói SKIP LOCKED. Dùng native query với `skip locked`. | 17.5 |

### Hiệu năng, cache và bytecode (Phần V)

| Thành phần dùng trong sách | Thuộc về | Cách làm trong JPA thuần | Mục |
|---|---|---|---|
| `session.setJdbcBatchSize(n)` | Hibernate | Đặt `hibernate.jdbc.batch_size` trong cấu hình persistence unit (cho cả ứng dụng, không theo từng session). | 18.1 |
| `GenerationType.IDENTITY` | JPA | Giữ nguyên. Hệ quả "không gom được batch" nằm ở bản chất cột tự tăng. | 18.2 |
| `@SequenceGenerator`, `allocationSize` | JPA | Giữ nguyên. Tên bộ tối ưu "pooled" là chi tiết của Hibernate. | 18.3 |
| `flush()`, `clear()`, `detach()` | JPA | Giữ nguyên. Chỉ `session.getStatistics().getEntityCount()` là của Hibernate. | 18.4 |
| `StatelessSession` | Hibernate | JPA không có. Dùng flush và clear theo lô, hoặc bulk update. | 18.5 |
| `@Cacheable` | JPA | Giữ nguyên (chỉ đánh dấu entity được phép vào cache). | 19.2 |
| `@Cache(usage = ...)` | Hibernate | JPA không có khái niệm chiến lược đồng bộ cache. | 19.2 |
| `addSynchronizedEntityClass` | Hibernate | Sau native update, tự xóa cache bằng `em.getEntityManagerFactory().getCache().evict(...)`. | 19.3 |
| Cache collection | Hibernate | JPA không có cache collection. | 19.4 |
| Query cache, hint `org.hibernate.cacheable` | Hibernate | JPA không có. Hint `retrieveMode` và `storeMode` của JPA chỉ điều khiển cache entity. | 19.5 |
| Bytecode enhancement | Hibernate | JPA không có. `@Basic(fetch = LAZY)` và `@OneToOne(fetch = LAZY)` là JPA, nhưng JPA cho phép provider bỏ qua; Hibernate chỉ thực hiện khi đã enhance. | 20.2, 20.3 |

### Bẫy khi viết entity và sự cố (Phần VI)

| Thành phần dùng trong sách | Thuộc về | Cách làm trong JPA thuần | Mục |
|---|---|---|---|
| `equals`, `hashCode`, `toString` | Java | Không liên quan tới provider. | 21.1, 21.3 |
| `Hibernate.isInitialized(proxy)` | Hibernate | `PersistenceUnitUtil.isLoaded(proxy)`. | 21.2 |
| `OptimisticLockingFailureException` | Spring | `jakarta.persistence.OptimisticLockException` (gọi `em.merge` trực tiếp thì gặp chính exception này). | 22.2 |
| `@Lock(PESSIMISTIC_WRITE)` | Spring Data | `LockModeType.PESSIMISTIC_WRITE` (JPA); giá trị `-2` của timeout là của Hibernate. | 22.3 |
| `session.getStatistics().getEntityCount()` | Hibernate | JPA không có cách đếm entity đang được quản lý. | 22.4 |
| `StatementInspector` (trong test ngân sách SQL) | Hibernate | JPA không có điểm móc tương đương; cần công cụ ngoài hoặc đo ở tầng JDBC. | 23.1 |
| `Metadata` (trong lint mapping) | Hibernate | JPA có `Metamodel` nhưng nghèo thông tin hơn; lint của lab đọc `Metadata` của Hibernate. | 23.2 |

### Cách dùng bảng này

- Nếu một dòng ghi "Hibernate" và dự án của bạn thật sự cần chạy trên provider khác, hãy xem cột giữa để biết thay bằng gì, và chấp nhận đánh đổi (thường là phải tự viết nhiều hơn).
- Nếu dòng ghi "Spring Data" hoặc "Spring", đó là lựa chọn về framework, không phải về provider: đổi Hibernate sang provider khác không ảnh hưởng.
- Dòng ghi "JPA" là phần an toàn nhất để dựa vào, nhưng nhớ rằng JPA cho phép provider bỏ qua một số gợi ý (như `LAZY`), nên hành vi thực tế vẫn nên được kiểm chứng bằng test như lab này.
