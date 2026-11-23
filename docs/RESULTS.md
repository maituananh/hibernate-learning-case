# RESULTS — kết quả thực tế của các tình huống 🔬

Mỗi dòng ghi một tình huống mà kết quả **chưa chắc** (phụ thuộc version Hibernate hoặc hiếm gặp). Assert trong test được viết
theo cột **Thực tế**, không theo trí nhớ. Dòng nào cột Thực tế KHÁC giả thuyết thì in đậm ở cột "Khớp?".

| Mã | Tình huống | Giả thuyết | Thực tế | Khớp? | Version |
|---|---|---|---|---|---|
| G0-S06c | `repository.delete(detached)` | Spring Data tự find/merge trước nên xoá được | Xoá được: 1 SELECT (find) rồi 1 DELETE; bảng còn 0 dòng | Khớp | Hibernate 7.4.15.Final |
| G0-S07 | `persist()` lại entity REMOVED trong cùng tx | Quay về MANAGED, DELETE bị huỷ, không có SQL | Đúng: 0 DELETE, 0 UPDATE, dòng vẫn còn trong DB | Khớp | Hibernate 7.4.15.Final |
| G0-S10b | FlushMode AUTO trước **native** query | Chưa rõ | Có auto flush: UPDATE chạy trước native SELECT, query thấy tên mới | Khớp (JPQL S10a cũng vậy) | Hibernate 7.4.15.Final |
| G0-S13 | `merge()` entity "ma" sau rollback | INSERT mới hoặc `OptimisticLockException` | `OptimisticLockException` ("Row was already updated or deleted…"), 0 INSERT, DB vẫn trống | Khớp một trong hai khả năng đã nêu: **không** INSERT | Hibernate 7.4.15.Final |
