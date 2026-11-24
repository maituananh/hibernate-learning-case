# G11 — Transaction & khoá

**Câu hỏi trung tâm:** hai transaction cùng chạm một dữ liệu thì chuyện gì xảy ra, và ranh giới `@Transactional` quyết định điều gì?

**Model:** `Product` (có `stock`), `PurchaseOrder` 1—N `OrderItem`, `AuditLog`, và bảng `job` (hàng đợi việc). Có service layer ở
`src/main` vì tình huống cần ranh giới transaction (như G9). Entity nằm ở
[src/main/.../g11_tx_lock](../../../../../../main/java/com/example/hibernatelab/g11_tx_lock/). Mỗi biến thể là một package = một schema.

**Test song song** dùng helper [ConcurrentTx](../support/ConcurrentTx.java) (quyết định E6): chạy một transaction ở thread nền,
đồng bộ bằng `Gate` (cổng có timeout), **không có `Thread.sleep`**; muốn biết một transaction đang "bị chặn bởi khoá" thì hỏi chính Postgres
(`pg_stat_activity`). Nhờ vậy thứ tự các bước tất định và test không chập chờn.

## Biến thể

| Mã | Schema | Khác baseline T1 | Test |
|---|---|---|---|
| T1 | `g11_t1_baseline` | **baseline**: `Product(stock)` không version, `AuditLog`, `PurchaseOrder`/`OrderItem` (LAZY), các service | S01–S05, S06b/c, S07, S08, S10–S12 |
| T2 | `g11_t2_versioned` | `Product` có thêm `@Version` | S06a |
| T3 | `g11_t3_job` | model khác: bảng `job` (hàng đợi việc) | S09 |

## Ba điều phải nhớ

1. **`@Transactional` quyết định ai sở hữu transaction và khi nào rollback.** `readOnly` làm connection read-only (Postgres cấm cả `nextval()`);
   B (`REQUIRED`) ném lỗi là cả transaction chung rollback-only dù A có `catch`; `REQUIRES_NEW` cho transaction + persistence context riêng;
   checked exception **không** rollback nếu thiếu `rollbackFor` (S01–S04).
2. **Lost update là lỗi im lặng.** Không `@Version`/khoá thì giao dịch ghi sau đè giao dịch ghi trước. Ba cách sửa, ba cái giá:
   `@Version` (phải retry), `PESSIMISTIC_WRITE` (xếp hàng chờ), `UPDATE … stock = stock - 1` (bỏ qua persistence context) (S05, S06).
3. **Khoá pessimistic có công cụ riêng cho từng bài toán:** `NOWAIT` (báo lỗi ngay), `SKIP LOCKED` (hàng đợi việc), khoá theo thứ tự id (tránh deadlock) (S07–S10).

## Bảng kết quả

| Tình huống | Kết quả thực tế | Ghi chú |
|---|---|---|
| S01a `readOnly` + sửa entity | 0 UPDATE; bộ nhớ đổi, DB không đổi | flush mode MANUAL |
| S01b `readOnly` + `save(new)` | `JpaSystemException`: Postgres từ chối **`nextval()`** trong transaction read-only; 0 INSERT | lỗi đến sớm hơn flush vì id SEQUENCE |
| S02 A nuốt lỗi của B (cùng REQUIRED) | `UnexpectedRollbackException`, mất cả hai dòng | cờ rollback-only nằm ở transaction chung |
| S03a `REQUIRES_NEW` | B commit, A rollback → chỉ còn dòng của B | |
| S03b `REQUIRES_NEW` + entity của A | `sameInstance = false`, `contains = false` | persistence context riêng |
| S04a checked exception | COMMIT (stock bị trừ) | mặc định chỉ RuntimeException/Error rollback |
| S04b `rollbackFor = Exception.class` | ROLLBACK (stock giữ nguyên) | |
| S05 lost update không `@Version` | stock = 9 thay vì 8, không lỗi | |
| S06a `@Version` | tx sau: `ObjectOptimisticLockingFailureException` (`update … where id=? and version=?` 0 dòng); retry → 8, version 2 | |
| S06b `PESSIMISTIC_WRITE` | tx sau chờ khoá, đọc số mới → 8 | `pg_stat_activity` xác nhận có session chờ |
| S06c `UPDATE … stock = stock - 1` | 8; entity đã tải vẫn giữ 10 | bỏ qua persistence context |
| S07a `PESSIMISTIC_WRITE` | `for no key update of <alias>` | không phải `for update` thuần |
| S07b `PESSIMISTIC_READ` | `for share of <alias>` | |
| S08 timeout 0 (NOWAIT) | SQL thêm `nowait`; `CannotAcquireLockException` (lớp con `PessimisticLockingFailureException`) ngay lập tức | |
| S09a `SKIP LOCKED` | hai worker nhận hai việc khác nhau (`for no key update of … skip locked`) | |
| S09b không khoá | hai worker nhận **cùng** việc; chỉ một việc DONE | gốc của sự cố "job chạy hai lần" |
| S10a khoá ngược thứ tự | Postgres huỷ **đúng một** tx (`deadlock detected` → `CannotAcquireLockException`) | tốn khoảng 1 giây (`deadlock_timeout`) |
| S10b khoá theo thứ tự id | không deadlock, tx sau chỉ chờ | |
| S11 `PESSIMISTIC_WRITE` + `left join fetch` | **không lỗi**: 2 câu SQL, câu 1 `left join` không khoá, câu 2 khoá riêng bảng gốc | khoảng hở giữa hai câu; item không bị khoá |
| S12a không OSIV | `LazyInitializationException` | |
| S12b OSIV giả lập | lazy load chạy được; SELECT chạy **ngoài transaction** | che N+1 |

## Quyết định cho lead

| Tình huống trong dự án | Nên dùng | Vì sao | Chứng minh |
|---|---|---|---|
| Method đọc dữ liệu, không ghi | `@Transactional(readOnly = true)` | bỏ dirty checking/flush; nhưng ghi (kể cả `save(new)` id SEQUENCE) sẽ lỗi | S01 |
| Service gọi service, bên trong có thể lỗi | Để lỗi đi lên; **đừng** `catch` rồi đi tiếp trong cùng transaction | B lỗi thì transaction chung đã rollback-only | S02 |
| Việc phụ phải lưu dù việc chính rollback (nhật ký, audit) | `REQUIRES_NEW` ở bean riêng | commit độc lập; nhưng persistence context khác nên đừng truyền entity qua | S03 |
| Method ném checked exception | luôn khai báo `rollbackFor` | mặc định checked exception vẫn COMMIT | S04 |
| Dữ liệu ít tranh chấp (sửa hồ sơ, đơn hàng) | `@Version` + retry | không giữ khoá, lỗi rõ ràng thay vì mất dữ liệu | S05, S06a |
| Tranh chấp cao trên một dòng (tồn kho, số dư) | `UPDATE … set stock = stock - ?` có điều kiện, hoặc `PESSIMISTIC_WRITE` | nguyên tử/xếp hàng, không retry | S06b/c |
| Không muốn chờ khoá | `jakarta.persistence.lock.timeout` = 0, bắt `PessimisticLockingFailureException` | báo lỗi ngay | S08 |
| Hàng đợi việc nhiều worker | `PESSIMISTIC_WRITE` + `SKIP LOCKED` + giới hạn số dòng | mỗi worker một việc, không trùng | S09 |
| Khoá nhiều dòng trong một transaction | luôn khoá theo thứ tự id tăng dần | không có vòng chờ → không deadlock | S10 |
| Cần khoá + nạp collection | khoá bảng gốc trước (`find` + lock), rồi nạp collection; đừng tin `left join fetch` + lock | Hibernate tách hai câu, item không bị khoá | S11 |
| Controller đọc collection LAZY | trả DTO từ service; giữ `open-in-view: false` | OSIV che N+1 và chạy SQL ngoài transaction | S12 |

Ghi chú review PR: thấy `catch` quanh lời gọi service `@Transactional` rồi đi tiếp, hoặc `throws Exception` mà không có `rollbackFor`,
hoặc đọc-rồi-ghi `stock` mà không `@Version`/khoá/UPDATE nguyên tử, hoặc hàng đợi việc không có `SKIP LOCKED`, thì hỏi lại tác giả.
