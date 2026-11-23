package com.example.hibernatelab.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.support.selftest.SelfTestItem;

/**
 * Tự kiểm thử {@link SqlCaptor} và {@link SqlAssert}.
 *
 * <p>Câu hỏi: helper đếm SQL có đếm đúng loại + bảng, và bỏ qua nextval không?
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
class SqlAssertSelfTest extends AbstractScenarioTest {

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { "support_selftest" };
	}

	@Test
	@DisplayName("SELFTEST | SqlAssert đếm đúng INSERT/UPDATE/DELETE/SELECT theo bảng và bỏ qua nextval")
	void s01_countsStatementsByKindAndTable() {
		// ── GIVEN: một item đã có trong DB ──
		Long itemId = inTxReturning(() -> {
			SelfTestItem item = new SelfTestItem("A-1", "ghi chú");
			em.persist(item);
			return item.getId();
		});

		// ── WHEN: đọc rồi sửa (dirty checking) ──
		sql.reset();
		inTx(() -> {
			SelfTestItem item = em.find(SelfTestItem.class, itemId);
			item.setCode("A-2");
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("item", 1);
		sql.assertUpdates("item", 1);
		sql.assertInserts("item", 0);
		sql.assertDeletes("item", 0);
		sql.assertTotal(2);
		sql.assertInOrder("select", "update support_selftest.item");
	}

	@Test
	@DisplayName("SELFTEST | nextval của sequence không được tính vào số câu SQL")
	void s02_ignoresSequenceNextval() {
		// ── WHEN: persist một entity mới (cần nextval để lấy id) ──
		sql.reset();
		inTx(() -> em.persist(new SelfTestItem("B-1", null)));

		// ── THEN: SQL ──
		sql.assertInserts("item", 1);
		sql.assertTotal(1);
		sql.print();
	}

	@Test
	@DisplayName("SELFTEST | assert sai thì báo lỗi kèm danh sách SQL thực tế")
	void s03_failureMessageListsActualSql() {
		// ── WHEN ──
		sql.reset();
		inTx(() -> em.persist(new SelfTestItem("C-1", null)));

		// ── THEN: sai số lượng → AssertionError có nhắc tới câu INSERT thực tế ──
		assertThatThrownBy(() -> sql.assertInserts("item", 5))
			.as("khi đỏ, thông báo lỗi phải liệt kê SQL thực tế để người học biết vì sao")
			.hasMessageContaining("insert into support_selftest.item");
	}

	@Test
	@DisplayName("SELFTEST | tên bảng khớp trọn vẹn (không khớp một phần), có schema thì khớp đúng schema")
	void s04_tableNameMatchingIsExact() {
		// ── WHEN ──
		sql.reset();
		inTx(() -> em.persist(new SelfTestItem("D-1", null)));

		// ── THEN: SQL ──
		sql.assertInserts("support_selftest.item", 1);
		sql.assertInserts("other_schema.item", 0);
		sql.assertInserts("ite", 0); // "ite" chỉ là một phần của "item", không phải tên bảng
	}
}
