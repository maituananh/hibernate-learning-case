package com.example.hibernatelab.support;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

/**
 * Dựng một {@link SessionFactory} RIÊNG chỉ gồm vài class, dùng cho tình huống {@code [BOOT-FAIL]}
 * (mapping sai khiến Hibernate không khởi động được).
 *
 * <p>Vì sao không dùng Spring context: entity sai mà lọt vào entity scan thì cả suite không khởi động.
 * Nên entity sai nằm ở {@code src/test/.../bootfail} (không được liệt kê trong
 * {@link ScenarioTestConfiguration}) và chỉ được nạp qua helper này.
 *
 * <p>Helper KHÔNG kết nối DB (tắt {@code allow_jdbc_metadata_access}, khai báo sẵn dialect, không sinh DDL):
 * mọi lỗi mapping (mappedBy sai, thiếu @Id, ...) đều xuất hiện ở bước dựng metadata, trước khi cần tới DB.
 * Hệ quả: lỗi chỉ xuất hiện khi thực thi DDL trên DB thì helper này KHÔNG bắt được.
 */
public final class MappingBootstrap {

	private MappingBootstrap() {
	}

	/**
	 * Thử dựng SessionFactory từ các class đã cho.
	 *
	 * @return exception khiến việc khởi động thất bại, hoặc {@code null} nếu mapping hợp lệ
	 *         (khi đó SessionFactory được đóng ngay).
	 */
	public static Throwable tryBuild(Class<?>... entityClasses) {
		StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
			.applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
			.applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
			.applySetting("jakarta.persistence.schema-generation.database.action", "none")
			.build();
		try {
			MetadataSources sources = new MetadataSources(registry);
			for (Class<?> entityClass : entityClasses) {
				sources.addAnnotatedClass(entityClass);
			}
			SessionFactory sessionFactory = sources.buildMetadata().buildSessionFactory();
			sessionFactory.close();
			return null;
		} catch (Throwable failure) {
			return failure;
		} finally {
			StandardServiceRegistryBuilder.destroy(registry);
		}
	}
}
