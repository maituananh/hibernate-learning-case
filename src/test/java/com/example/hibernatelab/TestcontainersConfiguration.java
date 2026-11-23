package com.example.hibernatelab;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Dựng một container PostgreSQL thật cho test.
 *
 * <p>Mọi test class dùng chung cấu hình này nên Spring chỉ tạo một context (và một container)
 * cho cả suite, tránh khởi động Postgres lặp lại.
 *
 * <p>{@code @ServiceConnection} tự gán url/username/password của container vào datasource,
 * không cần khai báo trong yml.
 *
 * <p>{@code @Profile("!inspect")}: khi chạy {@code -Pinspect} thì KHÔNG dựng container,
 * test dùng Postgres của docker compose (xem application-inspect.yml).
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("!inspect")
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// Cùng major version với docker-compose.yml để hai chế độ chạy cho kết quả giống nhau.
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}
}
