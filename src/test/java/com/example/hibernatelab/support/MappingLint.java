package com.example.hibernatelab.support;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.OneToMany;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.ToOne;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

/**
 * Rào chắn "lint mapping" (G14-R2, quyết định E8): đọc mapping mà Hibernate dựng được từ các entity và liệt kê vi phạm
 * quy tắc của team. Không thêm thư viện nào (không ArchUnit).
 *
 * <p>Quy tắc (mỗi quy tắc dẫn về tình huống đã chứng minh nó):
 * <ol>
 *   <li><b>EAGER-TO-ONE</b>: {@code @ManyToOne}/{@code @OneToOne} phải LAZY (mặc định là EAGER, gây N+1: nhóm G5, G10).</li>
 *   <li><b>EAGER-COLLECTION</b>: collection phải LAZY (nhóm G5).</li>
 *   <li><b>CASCADE-REMOVE</b>: không {@code cascade REMOVE/ALL} trên {@code @ManyToOne} hay {@code @ManyToMany}
 *       (xoá con kéo theo xoá cha/dữ liệu dùng chung: G2, G7).</li>
 *   <li><b>IDENTITY-ID</b>: id không dùng {@code IDENTITY} (tắt JDBC batch: G13-S02).</li>
 * </ol>
 *
 * <p>Cách dựng: {@link MetadataSources} không cần DB (giống {@link MappingBootstrap}). Ba quy tắc đầu đọc từ mapping của
 * Hibernate ({@code PersistentClass}, {@code Property}); quy tắc IDENTITY đọc annotation trên field {@code @Id} vì mapping
 * boot-time chưa dựng sẵn generator.
 */
public final class MappingLint {

	private MappingLint() {
	}

	/** Trả danh sách vi phạm (rỗng nghĩa là sạch). Mỗi phần tử có dạng {@code "QUY-TẮC: Entity.thuộc-tính ..."}. */
	public static List<String> check(Class<?>... entityClasses) {
		StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
			.applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
			.applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
			.build();
		try {
			MetadataSources sources = new MetadataSources(registry);
			for (Class<?> entityClass : entityClasses) {
				sources.addAnnotatedClass(entityClass);
			}
			Metadata metadata = sources.buildMetadata();
			List<String> violations = new ArrayList<>();
			for (PersistentClass entity : metadata.getEntityBindings()) {
				for (Property property : entity.getProperties()) {
					checkProperty(entity, property, violations);
				}
				checkIdentity(entity, violations);
			}
			return violations;
		} finally {
			StandardServiceRegistryBuilder.destroy(registry);
		}
	}

	private static void checkProperty(PersistentClass entity, Property property, List<String> violations) {
		String where = entity.getJpaEntityName() + "." + property.getName();
		String cascade = property.getCascade() == null ? "" : property.getCascade().toLowerCase(Locale.ROOT);
		boolean cascadesRemove = cascade.contains("remove") || cascade.contains("delete") || cascade.contains("all");

		if (property.getValue() instanceof ToOne toOne) {
			if (!toOne.isLazy()) {
				violations.add("EAGER-TO-ONE: " + where + " phải LAZY");
			}
			if (toOne instanceof ManyToOne && cascadesRemove) {
				violations.add("CASCADE-REMOVE: " + where + " (@ManyToOne) không được cascade REMOVE/ALL");
			}
		} else if (property.getValue() instanceof Collection collection) {
			if (!collection.isLazy()) {
				violations.add("EAGER-COLLECTION: " + where + " phải LAZY");
			}
			// OneToMany: phần tử là OneToMany. Còn lại (phần tử là ManyToOne của bảng nối) là @ManyToMany.
			boolean manyToMany = !(collection.getElement() instanceof OneToMany) && collection.getElement() instanceof ManyToOne;
			if (manyToMany && cascadesRemove) {
				violations.add("CASCADE-REMOVE: " + where + " (@ManyToMany) không được cascade REMOVE/ALL");
			}
		}
	}

	private static void checkIdentity(PersistentClass entity, List<String> violations) {
		for (Field field : entity.getMappedClass().getDeclaredFields()) {
			GeneratedValue generated = field.getAnnotation(GeneratedValue.class);
			if (field.isAnnotationPresent(jakarta.persistence.Id.class) && generated != null
				&& generated.strategy() == GenerationType.IDENTITY) {
				violations.add("IDENTITY-ID: " + entity.getJpaEntityName() + "." + field.getName() + " dùng IDENTITY (tắt batch insert)");
			}
		}
	}
}
