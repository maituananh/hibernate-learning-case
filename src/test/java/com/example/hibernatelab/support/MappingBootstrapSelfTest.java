package com.example.hibernatelab.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.bootfail.selftest.BrokenMappedByEntity;
import com.example.hibernatelab.support.selftest.SelfTestItem;
import com.example.hibernatelab.support.selftest.SelfTestOwner;

import jakarta.persistence.metamodel.EntityType;

/**
 * Tự kiểm thử {@link MappingBootstrap} và việc loại entity {@code [BOOT-FAIL]} khỏi entity scan.
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
class MappingBootstrapSelfTest extends AbstractScenarioTest {

	@Override
	protected String[] schemasUnderTest() {
		return new String[] {};
	}

	@Test
	@DisplayName("SELFTEST | Mapping sai (mappedBy trỏ thuộc tính không tồn tại) → tryBuild trả về exception")
	void s01_brokenMappingReturnsException() {
		// ── WHEN ──
		Throwable failure = MappingBootstrap.tryBuild(BrokenMappedByEntity.class, SelfTestItem.class, SelfTestOwner.class);

		// ── THEN ──
		System.out.println("[BOOT-FAIL] " + failure);
		assertThat(failure).as("mappedBy trỏ tới thuộc tính không tồn tại thì Hibernate không khởi động được").isNotNull();
		assertThat(failure.getMessage()).as("thông báo lỗi nhắc tới tên thuộc tính sai").contains("khongTonTai");
	}

	@Test
	@DisplayName("SELFTEST | Mapping đúng → tryBuild trả về null")
	void s02_validMappingReturnsNull() {
		Throwable failure = MappingBootstrap.tryBuild(SelfTestItem.class, SelfTestOwner.class);

		assertThat(failure).as("mapping hợp lệ thì SessionFactory dựng được, không có exception").isNull();
	}

	@Test
	@DisplayName("SELFTEST | Entity [BOOT-FAIL] không nằm trong entity scan của Spring context")
	void s03_bootFailEntityIsNotScanned() {
		Set<EntityType<?>> entities = em.getMetamodel().getEntities();

		boolean bootFailEntityFound = false;
		boolean selfTestEntityFound = false;
		for (EntityType<?> entity : entities) {
			if (entity.getJavaType() == BrokenMappedByEntity.class) {
				bootFailEntityFound = true;
			}
			if (entity.getJavaType() == SelfTestItem.class) {
				selfTestEntityFound = true;
			}
		}
		assertThat(selfTestEntityFound).as("điều kiện đầu: entity hợp lệ phải được scan").isTrue();
		assertThat(bootFailEntityFound)
			.as("entity bootfail mà lọt vào scan thì cả suite không khởi động được; @EntityScan phải liệt kê tường minh")
			.isFalse();
	}
}
