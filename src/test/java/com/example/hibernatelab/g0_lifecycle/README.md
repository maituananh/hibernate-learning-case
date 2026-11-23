# G0 — Vòng đời entity & persistence context

Schema Postgres: `g0_lifecycle`. Entity: [Customer](../../../../../../main/java/com/example/hibernatelab/g0_lifecycle/Customer.java)
(id `SEQUENCE`, chưa có quan hệ nào).

> Phase 1 mới có tình huống đầu tiên (golden test). Các tình huống còn lại thêm ở Phase 2.

## Tình huống

| Mã | Test | Câu hỏi | Kết luận (đã xác minh) |
|---|---|---|---|
| G0-S01 ⭐ | [PersistFlushTest](PersistFlushTest.java) `s01_…` | `persist()` chạy INSERT lúc nào? Sau `flush()` người khác thấy dòng chưa? | `persist` chỉ lấy id từ sequence: **0 INSERT**. `flush` mới gửi INSERT, nhưng connection khác vẫn thấy 0 dòng. Chỉ sau **commit** người khác mới thấy. **flush ≠ commit.** |

## Vòng đời quan sát được trong G0-S01

```
new Customer()  ──persist()──▶  MANAGED (có id, chưa INSERT)  ──flush()──▶  MANAGED (đã INSERT, chưa commit)
                                                                  │
                                                          commit / hết transaction
                                                                  ▼
                                                              DETACHED
```

## SQL thực tế (Hibernate 7.4.15.Final)

```sql
-- WHEN 1: persist()
select nextval('g0_lifecycle.customer_seq')
-- WHEN 2: flush()
insert into g0_lifecycle.customer (email,name,id) values (?,?,?)
-- WHEN 3: commit → không còn câu SQL nào
```

## DDL trích ra (từ `build/ddl/create.sql`)

```sql
create sequence g0_lifecycle.customer_seq start with 1 increment by 50;

create table g0_lifecycle.customer (
    id bigint not null,
    email varchar(255),
    name varchar(255) not null,
    primary key (id)
);
```

## Chạy

```bash
./gradlew test --tests '*PersistFlushTest*s01_*'
```
