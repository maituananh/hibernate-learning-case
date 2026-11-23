# hibernate-learning-case

Lab học **Hibernate mapping quan hệ**: mỗi tình huống là một test chạy trên PostgreSQL thật.
Kế hoạch chi tiết nằm trong [docs/](docs/).

## Lệnh thường dùng

```bash
./gradlew test                                  # chạy toàn bộ suite (Testcontainers dựng Postgres)
./gradlew test --rerun                          # chạy lại khi code không đổi
docker compose up -d && ./gradlew test -Pinspect --rerun   # chạy vào Postgres của compose, giữ dữ liệu để xem
```

Lưu ý:
- Gradle coi task `test` là *up-to-date* nếu code không đổi, nên sẽ không chạy lại. Thêm `--rerun`.
- Dùng `-Pinspect`, **không** dùng `-Dspring.profiles.active=inspect` (Gradle fork JVM riêng nên cờ đó không truyền sang).
- Xem DB bằng trình duyệt: Adminer ở http://localhost:8081 (server `postgres`, user/password/db đều là `hibernate_lab`).
- DDL do Hibernate sinh ra được xuất ra `build/ddl/create.sql`.
