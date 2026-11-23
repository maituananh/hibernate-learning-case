package com.example.hibernatelab.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Dựng một container PostgreSQL thật cho test.
 *
 * <p>"Singleton": mọi test class đều extend {@link AbstractScenarioTest} nên dùng chung một cấu hình,
 * Spring chỉ tạo MỘT application context (cache lại) và do đó chỉ MỘT container cho cả suite.
 * Nếu có test class nào khai báo cấu hình khác thì Spring sẽ dựng context thứ hai kèm container thứ hai
 * (chậm hơn nhiều), vì vậy đừng thêm {@code @Import}/{@code @MockBean}/{@code @ActiveProfiles} riêng cho từng class.
 *
 * <p>{@code @ServiceConnection} tự gán url/username/password của container vào datasource,
 * không cần khai báo trong yml.
 *
 * <p>{@code @Profile("!inspect")}: khi chạy {@code -Pinspect} thì KHÔNG dựng container,
 * test dùng Postgres của docker compose (xem application-inspect.yml).
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("!inspect")
public class PostgresTestcontainer {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// Ghim image (cùng major version với docker-compose.yml) để hai chế độ chạy cho kết quả giống nhau.
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}
}
