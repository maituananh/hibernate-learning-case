# hibernate-learning-case

Lab học **Hibernate mapping quan hệ** (`@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany`, cascade, `orphanRemoval`, fetch, `@MapsId`, …).
Mỗi tình huống mapping là **một test chạy trên PostgreSQL thật**, chứng minh được bốn thứ:

1. **DDL** Hibernate sinh ra,
2. **SQL** đã chạy (số câu, thứ tự),
3. **dữ liệu thật** trong DB (đọc bằng `JdbcTemplate`, không dùng `EntityManager` vì cache cấp 1 nói dối),
4. **trạng thái vòng đời** entity (TRANSIENT / MANAGED / DETACHED / REMOVED).

Stack: Java 21, Spring Boot, Hibernate 7.4.15.Final, PostgreSQL 17 (Testcontainers), Gradle wrapper. Không Lombok, không Web, không Flyway, không H2.

## Chạy

```bash
./gradlew test                                  # toàn bộ suite (Testcontainers tự dựng Postgres)
./gradlew test --tests 'G1*'                    # một nhóm (G0 … G9)
./gradlew test --tests '*G1V4*'                 # một class
./gradlew test --tests '*G1V4*s12*'             # một tình huống (method s12_…)
./gradlew test --rerun                          # chạy lại khi code không đổi
docker compose up -d && ./gradlew test -Pinspect --rerun   # chạy vào Postgres của compose, giữ dữ liệu để xem
```

Lưu ý:

- Gradle coi task `test` là *up-to-date* nếu code không đổi nên sẽ không chạy lại. Thêm `--rerun`.
- Dùng `-Pinspect`, **không** dùng `-Dspring.profiles.active=inspect` (Gradle fork JVM riêng nên cờ đó không truyền sang).
- Xem DB bằng trình duyệt: **Adminer** ở http://localhost:8081 (System `PostgreSQL`, server `postgres`, user/password/db đều là `hibernate_lab`).
  Chỉ có dữ liệu để xem sau khi chạy với `-Pinspect`; test thường dùng container riêng và xoá khi xong.
- DDL của mọi biến thể được xuất ra `build/ddl/create.sql`. Gradle mặc định giấu stdout, nên `build.gradle` đã bật `showStandardStreams` để thấy SQL và `[LIFECYCLE]`.
- Bộ dữ liệu được dọn **trước** mỗi test (không dọn sau), nên sau một test bạn luôn còn dữ liệu của nó để xem.

## Kết quả lần chạy gần nhất

| Chỉ số | Giá trị |
|---|---|
| Số test | **249** (50 class), 0 lỗi, 0 bỏ qua |
| Thời gian chạy | khoảng **19 giây** trên máy phát triển (Postgres container đã sẵn) |
| Hibernate | 7.4.15.Final |

## Lộ trình đọc

Đọc theo thứ tự G0 → G9. Mỗi nhóm có README riêng (bảng biến thể, so sánh A vs B, kết quả đã xác minh, DDL trích ra).

| Nhóm | Chủ đề | Số test | README |
|---|---|---|---|
| G0 | Vòng đời entity, flush, `merge`, `getReference`, `save()` của Spring Data | 24 | [g0_lifecycle](src/test/java/com/example/hibernatelab/g0_lifecycle/README.md) |
| G1 | **Owning side**: `@ManyToOne` / `@OneToMany` / `mappedBy` / `@JoinColumn` | 32 | [g1_owning_side](src/test/java/com/example/hibernatelab/g1_owning_side/README.md) |
| G2 | `CascadeType` và `@OnDelete` | 28 | [g2_cascade](src/test/java/com/example/hibernatelab/g2_cascade/README.md) |
| G3 | `orphanRemoval` và thứ tự flush | 17 | [g3_orphan_removal](src/test/java/com/example/hibernatelab/g3_orphan_removal/README.md) |
| G4 | Sửa con qua cha mà không đổi cha (`@Version`, `@DynamicUpdate`) | 15 | [g4_update_child_via_parent](src/test/java/com/example/hibernatelab/g4_update_child_via_parent/README.md) |
| G5 | `FetchType`, N+1 và cách sửa | 20 | [g5_fetch_n_plus_one](src/test/java/com/example/hibernatelab/g5_fetch_n_plus_one/README.md) |
| G6 | `@OneToOne` và `@MapsId` | 22 | [g6_one_to_one](src/test/java/com/example/hibernatelab/g6_one_to_one/README.md) |
| G7 | `@ManyToMany`, `@JoinTable`, join entity | 29 | [g7_many_to_many](src/test/java/com/example/hibernatelab/g7_many_to_many/README.md) |
| G8 | `@JoinColumns`, PK ghép, derived identity | 13 | [g8_composite_fk](src/test/java/com/example/hibernatelab/g8_composite_fk/README.md) |
| G9 | Tình huống tổng hợp có service layer | 32 | [g9_integrated](src/test/java/com/example/hibernatelab/g9_integrated/README.md) |

Phần còn lại (1 smoke test + 16 test của hạ tầng `support/`) kiểm tra chính các công cụ của lab (`SqlCaptor`, `assertState`, …).

## Tài liệu

| File | Nội dung |
|---|---|
| [docs/CHEATSHEET.md](docs/CHEATSHEET.md) | **Mỗi annotation một bảng**: mặc định, đặt ở phía nào, ảnh hưởng DDL, ảnh hưởng SQL, bẫy hay gặp, mã tình huống. Đọc sau khi xong G0–G9 để ôn. |
| [docs/RESULTS.md](docs/RESULTS.md) | Kết quả thực tế của các tình huống 🔬 (khác giả thuyết thì in đậm). |
| [docs/CONVENTIONS.md](docs/CONVENTIONS.md) | Checklist khi viết một tình huống mới. |
| [docs/00-PROMPT.md](docs/00-PROMPT.md) … [03-PHASES.md](docs/03-PHASES.md) | Yêu cầu, quyết định thiết kế, danh sách tình huống, các phase. |

## Cách đọc một test

Mở một test và đọc các khối comment theo thứ tự: `GIVEN` (dữ liệu ban đầu) → `WHEN` (thao tác cần học) → `THEN: SQL` →
`THEN: DB thật` → `THEN: DDL`. Chú ý các dòng `[LIFECYCLE]`, `[FLUSH]`, `[TX END]`: chúng cho biết entity đang ở trạng thái nào
và SQL thật sự được gửi đi lúc nào. Test mẫu để bắt đầu: **G0-S01** trong
[PersistFlushTest](src/test/java/com/example/hibernatelab/g0_lifecycle/PersistFlushTest.java).

Mỗi biến thể mapping là một package ở `src/main/java/com/example/hibernatelab/g<N>_<nhóm>/v<M>_<biến thể>/` và một **schema Postgres riêng**
(ví dụ `g1_v4_bidi_mappedby`). Biến thể chỉ khác baseline của nhóm đúng một điểm, ghi ở header đầu class entity.
