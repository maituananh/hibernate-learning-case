package com.example.hibernatelab.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.hibernatelab.support.SchemaInspector.Column;
import com.example.hibernatelab.support.SchemaInspector.ForeignKey;
import com.example.hibernatelab.support.selftest.SelfTestItem;
import com.example.hibernatelab.support.selftest.SelfTestOwner;

/**
 * Tự kiểm thử các helper đọc/dọn DB: {@link DbReader}, {@link DbCleaner}, {@link SchemaInspector},
 * {@code inNewConnection}.
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
class DbHelpersSelfTest extends AbstractScenarioTest {

	private static final String ITEM_TABLE = "support_selftest.item";

	@Autowired
	private DbCleaner dbCleaner;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { "support_selftest" };
	}

	@Test
	@DisplayName("SELFTEST | DbReader đọc đúng dòng, đếm đúng, snapshot rỗng khi không có dòng")
	void s01_dbReaderReadsRealRows() {
		// ── GIVEN ──
		Long itemId = inTxReturning(() -> {
			SelfTestItem item = new SelfTestItem("R-1", "hello");
			em.persist(item);
			return item.getId();
		});

		// ── THEN: DB thật ──
		db.print(ITEM_TABLE);
		Map<String, Object> row = db.rowById(ITEM_TABLE, itemId);
		assertThat(row.get("code")).as("rowById phải trả đúng dòng vừa insert").isEqualTo("R-1");
		assertThat(db.countOf(ITEM_TABLE)).as("bảng có đúng 1 dòng").isEqualTo(1);
		assertThat(db.rowsOf(ITEM_TABLE)).as("rowsOf trả mọi dòng").hasSize(1);
		assertThat(db.rowSnapshot(ITEM_TABLE, itemId)).as("snapshot chứa dữ liệu dòng").containsEntry("note", "hello");
		assertThat(db.rowSnapshot(ITEM_TABLE, -1L)).as("không có dòng thì snapshot rỗng, không ném lỗi").isEmpty();
		assertThatThrownBy(() -> db.rowById(ITEM_TABLE, -1L))
			.as("rowById không có dòng phải báo lỗi dễ hiểu thay vì trả null")
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("không có dòng nào");
	}

	@Test
	@DisplayName("SELFTEST | DbReader từ chối tên bảng không kèm schema (chống gõ nhầm / ghép SQL bậy)")
	void s02_dbReaderRequiresSchemaQualifiedName() {
		assertThatThrownBy(() -> db.countOf("item"))
			.as("tên bảng luôn phải kèm schema, kể cả khi gõ thiếu")
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> db.countOf("support_selftest.item; drop table x"))
			.as("tên bảng được ghép vào SQL nên phải bị chặn nếu chứa ký tự lạ")
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("SELFTEST | DbCleaner làm trống mọi bảng của schema (kể cả bảng có FK)")
	void s03_dbCleanerEmptiesTables() {
		// ── GIVEN: dữ liệu ở cả hai bảng có FK với nhau ──
		inTx(() -> {
			SelfTestOwner owner = new SelfTestOwner("Chủ A");
			em.persist(owner);
			SelfTestItem item = new SelfTestItem("C-1", null);
			item.setOwner(owner);
			em.persist(item);
		});
		assertThat(db.countOf(ITEM_TABLE)).as("điều kiện đầu: đã có dữ liệu").isEqualTo(1);

		// ── WHEN ──
		dbCleaner.truncateAllTablesIn("support_selftest");

		// ── THEN: DB thật ──
		assertThat(db.countOf(ITEM_TABLE)).as("TRUNCATE ... CASCADE phải làm trống bảng item").isZero();
		assertThat(db.countOf("support_selftest.owner")).as("và cả bảng owner").isZero();
	}

	@Test
	@DisplayName("SELFTEST | DbCleaner báo lỗi khi gõ nhầm tên schema thay vì im lặng không dọn gì")
	void s04_dbCleanerRejectsUnknownSchema() {
		assertThatThrownBy(() -> dbCleaner.truncateAllTablesIn("schema_khong_ton_tai"))
			.as("schema không có bảng thì hầu như chắc chắn là gõ nhầm, phải báo to")
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("schema_khong_ton_tai");
	}

	@Test
	@DisplayName("SELFTEST | inNewConnection chỉ thấy dữ liệu đã COMMIT (flush ≠ commit)")
	void s05_newConnectionSeesOnlyCommittedData() {
		inTx(() -> {
			em.persist(new SelfTestItem("N-1", null));
			em.flush();
			// [FLUSH] INSERT đã gửi xuống DB, nhưng transaction chưa commit
			long seenBySameTx = db.countOf(ITEM_TABLE);
			long seenByOtherConnection = inNewConnection(() -> db.countOf(ITEM_TABLE));
			assertThat(seenBySameTx).as("cùng transaction thấy dòng đã flush").isEqualTo(1);
			assertThat(seenByOtherConnection).as("connection khác không thấy dòng chưa commit").isZero();
		});
		assertThat(inNewConnection(() -> db.countOf(ITEM_TABLE))).as("sau commit ai cũng thấy").isEqualTo(1);
	}

	@Test
	@DisplayName("SELFTEST | SchemaInspector đọc đúng bảng, cột, PK, FK, unique")
	void s06_schemaInspectorReadsStructure() {
		// ── THEN: DDL ──
		schema.printDdl("support_selftest");
		assertThat(schema.tablesOf("support_selftest")).as("hai entity tự kiểm thử sinh hai bảng").containsExactly("item", "owner");
		assertThat(schema.primaryKeyOf("support_selftest", "item")).as("PK là cột id").containsExactly("id");

		List<Column> columns = schema.columnsOf("support_selftest", "item");
		Column codeColumn = columnNamed(columns, "code");
		Column noteColumn = columnNamed(columns, "note");
		assertThat(codeColumn.nullable()).as("code khai báo nullable = false").isFalse();
		assertThat(codeColumn.type()).as("code khai báo length = 30").isEqualTo("varchar(30)");
		assertThat(noteColumn.nullable()).as("note để mặc định nên cho phép NULL").isTrue();

		List<ForeignKey> foreignKeys = schema.foreignKeysOf("support_selftest", "item");
		assertThat(foreignKeys).as("item.owner_id là FK duy nhất").hasSize(1);
		assertThat(foreignKeys.get(0).column()).as("cột FK").isEqualTo("owner_id");
		assertThat(foreignKeys.get(0).targetTable()).as("FK trỏ sang bảng owner, kèm schema").isEqualTo("support_selftest.owner");
		assertThat(foreignKeys.get(0).onDelete()).as("không khai báo @OnDelete nên là NO ACTION").isEqualTo("NO ACTION");
		assertThat(schema.uniqueConstraintsOf("support_selftest", "item"))
			.as("code khai báo unique = true")
			.containsExactly(List.of("code"));
	}

	private Column columnNamed(List<Column> columns, String name) {
		for (Column column : columns) {
			if (column.name().equals(name)) {
				return column;
			}
		}
		throw new AssertionError("Không có cột " + name + " trong " + columns);
	}

}
