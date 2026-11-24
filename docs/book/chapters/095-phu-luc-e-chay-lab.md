## Phụ lục E. Cách chạy lab

Mọi tình huống trong sách đều là test trong repository `https://github.com/maituananh/hibernate-learning-case`. Phụ lục này tóm tắt cách chạy chúng để bạn tự quan sát SQL và dữ liệu thật.

### Yêu cầu

- Java 21.
- Docker đang chạy, vì Testcontainers dựng PostgreSQL thật cho mỗi lần chạy test.
- Dùng Gradle wrapper có sẵn trong repository (`./gradlew`), không cần cài Gradle.
- Stack của lab: Spring Boot, Hibernate 7.4.15.Final, PostgreSQL 17. Lab không dùng Lombok, không có lớp web, không dùng Flyway hoặc Liquibase, không dùng H2.

### Các lệnh thường dùng

```bash
./gradlew test                       # chạy toàn bộ suite
./gradlew test --tests 'G1*'         # một nhóm (G1 khớp cả G10 đến G14)
./gradlew test --tests '*G1V4*'      # một class test
./gradlew test --tests '*G1V4*s12*'  # một test method (tên s12_...)
./gradlew :lab-enhanced:test         # chỉ subproject bytecode enhancement
./gradlew test --rerun               # chạy lại dù code không đổi
```

Nhóm G, tên class và tên method của từng tình huống nằm trong khung Tra cứu cuối mỗi tình huống và trong Phụ lục C. Lần chạy đầu tiên lâu hơn vì phải kéo image PostgreSQL.

### Ba điều dễ gây khó hiểu

- **Gradle coi task `test` là up-to-date** nếu code không đổi, nên lệnh chạy lần hai sẽ không làm gì và không in ra gì. Thêm `--rerun` để ép chạy lại.
- **Gradle mặc định giấu stdout của test.** Lab đã bật `showStandardStreams` trong `build.gradle`, nhờ đó bạn thấy câu SQL và các dòng trạng thái entity ngay trên terminal. Nếu bạn tự dựng project khác, nhớ bật cấu hình này.
- **Test chạy trong một JVM riêng**, nên tùy chọn `-Dspring.profiles.active=...` không truyền sang được. Lab dùng cờ `-Pinspect` của Gradle để đổi chế độ.

### Xem dữ liệu trong database

Mặc định mỗi lần chạy test dùng một container PostgreSQL riêng và xóa nó khi xong. Muốn giữ dữ liệu lại để xem, chạy vào PostgreSQL của docker compose:

```bash
docker compose up -d
./gradlew test -Pinspect --rerun
```

Sau đó mở Adminer ở `http://localhost:8081` (hệ thống `PostgreSQL`, server `postgres`, user, password và database đều là `hibernate_lab`). Dữ liệu được dọn **trước** mỗi test chứ không dọn sau, nên sau khi chạy một test bạn luôn còn dữ liệu của chính test đó để xem.

### Nơi tìm DDL

DDL của mọi biến thể mapping được xuất ra file `build/ddl/create.sql` sau khi chạy suite. Các sơ đồ bảng và khối DDL trong sách lấy từ file này.

### Subproject lab-enhanced

Bytecode enhancement (Chương 20) chỉ nằm ở subproject `lab-enhanced/`, vì plugin enhancement áp lên **mọi** entity của source set và sẽ làm đổi kết quả các test khác. Vì vậy `./gradlew test` chạy cả hai project, còn `./gradlew :lab-enhanced:test` chỉ chạy phần enhancement. Không khai plugin này ở project gốc.

### Kết quả lần chạy gần nhất

Suite có 365 test (357 ở project gốc và 8 ở `lab-enhanced`), tất cả đều xanh, chạy trong khoảng 30 giây khi image PostgreSQL đã có sẵn. Test chậm nhất là deadlock (khoảng 1 giây do `deadlock_timeout`) và batch 2000 dòng.

### Dựng lại cuốn sách này

Mã nguồn của sách nằm ở `docs/book/`. Chạy `docs/book/build.sh` để build file Word từ evidence có sẵn, hoặc `docs/book/build.sh --evidence` để chạy lại cả suite trước, lấy SQL và dữ liệu mới nhất (cần Docker). Chi tiết nằm trong `docs/book/README.md`.
