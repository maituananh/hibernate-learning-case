# G14 — Lead: sự cố tổng hợp và rào chắn

**Câu hỏi trung tâm:** khi một sự cố xảy ra ở production, làm sao đi từ **triệu chứng** ra **nguyên nhân**, chọn cách sửa, rồi đặt **rào chắn** để nó không quay lại?

Khác G0–G13 (mỗi test trả lời một câu hỏi về Hibernate), G14 luyện kỹ năng của người dẫn dắt team. Nhóm này được **miễn quy tắc một khác biệt** (quyết định E7, giống G9):
mỗi sự cố có hai package, hai schema, và hai bản khác nhau ở nhiều điểm.

Sản phẩm dùng được ngoài lab: [docs/LEAD-PLAYBOOK.md](../../../../../../../docs/LEAD-PLAYBOOK.md) (checklist review PR + bảng quyết định G0–G13) và
[docs/adr/](../../../../../../../docs/adr/) (5 ADR mẫu).

Code nằm ở [src/main/.../g14_lead](../../../../../../main/java/com/example/hibernatelab/g14_lead/). Người học chọn chỉ làm phần ⭐ (Phase 18): **không** làm G14-I5 và G14-R3 (➕).

## Cấu trúc mỗi sự cố (E7)

Mỗi sự cố có **đúng 4 test**:

| Test | Việc cần làm | Bằng chứng in ra |
|---|---|---|
| `S01` tái hiện | Dựng lại triệu chứng trên bản `*_buggy` | Số SQL / dữ liệu sai |
| `S02` chẩn đoán | Tìm **con số hoặc câu SQL** chứng minh nguyên nhân | SQL, số liệu đối chiếu |
| `S03` sửa | Chạy bản `*_fixed`, cùng dữ liệu | Kết quả đúng + SQL/dữ liệu sau sửa |
| `S04` rào chắn | Chứng minh rào chắn **đỏ trên bản buggy và xanh trên bản fixed** | `assertThatThrownBy(...)` + `doesNotThrowAnyException()` |

Rào chắn chưa từng đỏ là rào chắn chưa được chứng minh, nên `S04` luôn kiểm tra cả hai chiều.

## Sự cố

| Mã | Schema (`buggy` / `fixed`) | Triệu chứng | Nguyên nhân | Test |
|---|---|---|---|---|
| I1 | `g14_i1_buggy` / `g14_i1_fixed` | Trang danh sách đơn hàng chậm dần theo dữ liệu | N+1 hai tầng: service đọc entity rồi duyệt `customer` và `items` (mapping vẫn đúng LAZY) | [G14I1OrderListTest](G14I1OrderListTest.java) |
| I2 | `g14_i2_buggy` / `g14_i2_fixed` | Tồn kho sai sau flash sale | Lost update: đọc → kiểm tra → ghi, không `@Version` | [G14I2FlashSaleTest](G14I2FlashSaleTest.java) |
| I3 | `g14_i3_buggy` / `g14_i3_fixed` | Job gửi email chạy hai lần | Hai worker cùng đọc "việc NEW đầu tiên", không khoá | [G14I3EmailJobTest](G14I3EmailJobTest.java) |
| I4 | `g14_i4_buggy` / `g14_i4_fixed` | Import file lớn ngày càng chậm | `IDENTITY` tắt batch + không `flush`/`clear` nên persistence context phình | [G14I4BulkImportTest](G14I4BulkImportTest.java) |

## Rào chắn

| Mã | Nội dung | Test |
|---|---|---|
| G14-R1 | **Ngân sách SQL**: [SqlBudget](../support/SqlBudget.java) đặt trần số SELECT cho use case; vượt thì `AssertionError` kèm các SELECT thừa | [G14SqlBudgetTest](G14SqlBudgetTest.java) (S01–S03), và I1-S04 |
| G14-R2 | **Lint mapping**: [MappingLint](../support/MappingLint.java) báo EAGER to-one, EAGER collection, `cascade REMOVE/ALL` trên `@ManyToOne`/`@ManyToMany`, `IDENTITY`. Quét `*_fixed` và entity cố ý sai ở `bootfail/g14r2` | [G14MappingLintTest](G14MappingLintTest.java) (S01–S03), và I4-S04 |

Lint **không** quét package `*_buggy` (lỗi ở đó là có chủ đích) và không quét G0–G9 (cố ý chứa anti-pattern để học). Riêng lỗi `IDENTITY` của `i4_buggy`
được chứng minh bị lint bắt ở G14-I4-S04.

## Kết quả (Hibernate 7.4.15.Final)

| Mã | Tái hiện | Chẩn đoán | Sửa |
|---|---|---|---|
| I1 | Trang 10 đơn = **22** SELECT (1 trang + 1 count + 10 customer + 10 items) | SELECT vào `customer` và `order_item` **bằng đúng cỡ trang** (5 → 5, 10 → 10) | `select new …Row(o.orderNo, c.name, size(o.items))` + `countQuery`: **2** SELECT |
| I2 | Kho 10, hai khách mua 6: cả hai thành công, bán ra **12**, kho ghi **4**, không exception | Hai `UPDATE product` không có `where version`; kho + đã bán = 16 ≠ 10 | `@Version` + retry ngoài transaction: khách đến sau bị "hết hàng", chỉ 1 đơn, kho 4, version 1 |
| I3 | Hai worker cùng nhận việc 1: `email_log` có **2** dòng cho việc 1, việc 2 chưa ai làm | SQL nhận việc không có `for update`/`skip locked`; một `job_id` có hai worker | `PESSIMISTIC_WRITE` + `SKIP LOCKED`: A nhận việc 1, B nhận việc 2 |
| I4 | 200 dòng = **200** INSERT, persistence context giữ **200** entity | Số INSERT và cỡ persistence context đều bằng số dòng (100 → 100, 200 → 200) | `SEQUENCE` + `setJdbcBatchSize(50)` + `flush`/`clear` mỗi 50 dòng: **4 lô × 50**, persistence context ≤ 50 |

## Điều đáng nhớ

1. **Mapping đúng chưa đủ.** I1 có mọi quan hệ LAZY mà vẫn 22 SELECT: lỗi nằm ở cách đọc. Lint bắt được mapping, nhưng chỉ ngân sách SQL mới bắt được cách dùng.
2. **Chẩn đoán là tìm con số tăng theo N** (số SELECT theo cỡ trang, cỡ persistence context theo số dòng) hoặc **đối chiếu hai nguồn dữ liệu** (kho vs sổ bán hàng, một `job_id` có hai worker).
3. **Triệu chứng báo cáo không nhất thiết đúng như thực tế.** Thiết kế ghi "tồn kho âm" cho I2; chạy thật kho không âm (ghi 4) mà là bán 12 từ kho 10. Tìm bất biến, đừng tìm số âm.
4. **Retry phải nằm ngoài transaction.** Transaction đã lỗi version thì rollback-only; service của I2 bọc `TransactionTemplate` trong vòng lặp thử lại.
5. **Rào chắn phải chứng minh cả hai chiều** (đỏ trên đường lỗi, xanh trên đường sửa), nếu không không biết nó có bắt được gì không.

## Hạ tầng test bổ sung

- `ConcurrentTx.startOutsideTx(...)` ([ConcurrentTx](../support/ConcurrentTx.java)): chạy ở thread nền **không** mở transaction bao ngoài. Cần cho service tự quản lý transaction:
  với `start(...)` (có transaction bao ngoài) transaction của service tham gia vào nó nên lỗi commit và vòng retry bên trong service không còn đúng nghĩa. Phát hiện khi làm I2: retry không chạy dù code đúng.
- Hook `afterStockRead` / `afterJobRead` trong service **chỉ để test ép thứ tự xen kẽ** (cổng đồng bộ, không `Thread.sleep`); code thật không có tham số này.

## Quyết định cho lead

Xem bảng đầy đủ ở [LEAD-PLAYBOOK §4–5](../../../../../../../docs/LEAD-PLAYBOOK.md#4-từ-triệu-chứng-ra-nguyên-nhân-g14):

- Màn hình danh sách đọc DTO, có ngân sách SELECT; `Page` + `join fetch` luôn có `countQuery` (ADR-005).
- Entity "đọc rồi ghi" có `@Version` và retry ngoài transaction; tranh chấp cao thì UPDATE nguyên tử hoặc khoá (ADR-003).
- Hàng đợi việc dùng `SKIP LOCKED`.
- Import hàng loạt: `SEQUENCE` + batch size theo session + `flush`/`clear` theo lô.
- Đưa lint mapping vào suite của dự án thật, chỉ quét model sạch (ADR-001, ADR-002).
