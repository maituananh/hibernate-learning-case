package com.example.hibernatelab.g12_advanced_model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g12_advanced_model.c1_element_collection.C1Order;
import com.example.hibernatelab.g12_advanced_model.c1_element_collection.C2Note;
import com.example.hibernatelab.g12_advanced_model.c1_element_collection.C2Order;
import com.example.hibernatelab.g12_advanced_model.c1_element_collection.C3Order;
import com.example.hibernatelab.g12_advanced_model.c1_element_collection.C4Order;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G12, chủ đề {@code @ElementCollection}: S04 (bag so với entity con), S05 (Set và {@code @OrderColumn}).
 *
 * <p>Kịch bản chung: một đơn hàng có 3 phần tử, bỏ phần tử ĐẦU TIÊN ("a"), rồi đếm SQL. Con số cần canh là số DELETE/INSERT/UPDATE
 * vào bảng con: nó cho biết Hibernate có "biết" dòng nào là dòng nào hay phải xoá hết ghi lại.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G12ElementCollectionTest extends AbstractScenarioTest {

	private static final String C1 = "g12_c1_element_collection";

	@Autowired
	private JdbcTemplate jdbc;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { C1 };
	}

	/**
	 * G12-S04a — C1: bag {@code List<String>} bỏ 1 phần tử → DELETE HẾT rồi INSERT lại phần còn lại (như G1-S08)
	 *
	 * <p>Mapping: C1 (bag, bảng con không có PK). <b>Câu hỏi</b>: bỏ "a" khỏi [a, b, c] thì SQL gì?
	 * <p><b>Kết luận</b>: 1 DELETE toàn bộ dòng của đơn hàng + 2 INSERT ("b", "c"). Bag không có khoá nên không thể chỉ vào "dòng của a".
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S04a | @ElementCollection List (bag) bỏ 1 phần tử → DELETE hết + INSERT lại 2 phần tử")
	void s04a_bagIsRecreatedOnRemove() {
		// ── GIVEN ──
		jdbc.update("insert into " + C1 + ".c1_order (id, name) values (1, 'Order-1')");
		jdbc.update("insert into " + C1 + ".c1_order_note (order_id, note) values (1, 'a'), (1, 'b'), (1, 'c')");
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			C1Order orderOne = em.find(C1Order.class, 1L);
			orderOne.getNotes().remove("a"); // chỉ sửa collection, không gọi save
			// [FLUSH] lúc commit: bag → recreate
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("c1_order_note", 1);
		sql.assertInserts("c1_order_note", 2);
		// ── THEN: DB thật ──
		assertThat(jdbc.queryForList("select note from " + C1 + ".c1_order_note order by note", String.class))
			.as("kết quả cuối vẫn đúng ([b, c]) nhưng đi đường vòng: xoá hết rồi ghi lại").containsExactly("b", "c");
		// ── THEN: DDL ──
		assertThat(schema.primaryKeyOf(C1, "c1_order_note")).as("bag không có PK: Hibernate không thể xoá đúng một dòng").isEmpty();
	}

	/**
	 * G12-S04b — C2: {@code @OneToMany} tới entity con, bỏ 1 phần tử → chỉ DELETE đúng 1 dòng (so với S04a)
	 *
	 * <p>Mapping: C2 (entity con có id, orphanRemoval). <b>Câu hỏi</b>: cùng thao tác "bỏ phần tử đầu" nhưng phần tử là entity?
	 * <p><b>Kết luận</b>: 1 DELETE theo id, 0 INSERT. Entity có định danh riêng nên Hibernate xoá chính xác dòng đó.
	 * Đây là lý do chọn entity con khi collection lớn hoặc hay sửa; {@code @ElementCollection} hợp với tập giá trị nhỏ, ít đổi.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S04b | @OneToMany entity con bỏ 1 phần tử → chỉ 1 DELETE, 0 INSERT")
	void s04b_entityChildIsDeletedIndividually() {
		// ── GIVEN ──
		jdbc.update("insert into " + C1 + ".c2_order (id, name) values (1, 'Order-1')");
		jdbc.update("insert into " + C1 + ".c2_note (id, text, order_id) values (11, 'a', 1), (12, 'b', 1), (13, 'c', 1)");
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			C2Order orderOne = em.find(C2Order.class, 1L);
			C2Note firstNote = orderOne.getNotes().get(0); // [LIFECYCLE] MANAGED
			orderOne.getNotes().remove(firstNote); // mồ côi → orphanRemoval sẽ xoá
			// [FLUSH] lúc commit: DELETE theo id
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("c2_note", 1);
		sql.assertInserts("c2_note", 0);
		// ── THEN: DB thật ──
		assertThat(db.countOf(C1 + ".c2_note")).as("3 dòng bỏ 1 còn 2, các dòng còn lại không bị đụng tới").isEqualTo(2);
	}

	/**
	 * G12-S05a — C3: {@code Set<String>} bỏ 1 phần tử → DELETE đúng dòng đó 🔬
	 *
	 * <p>Mapping: C3 (Set, bảng con có PK (order_id, label)). <b>Câu hỏi</b>: Set có tránh được recreate của bag không?
	 * <p><b>Kết luận</b>: 1 DELETE đúng phần tử bỏ ({@code where order_id=? and label=?}), 0 INSERT. Giá trị phần tử cũng là khoá nên Hibernate nhận ra đúng dòng.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S05a | 🔬 @ElementCollection Set bỏ 1 phần tử → DELETE đúng dòng, PK (order_id, label)")
	void s05a_setDeletesOnlyTheRemovedElement() {
		// ── GIVEN ──
		jdbc.update("insert into " + C1 + ".c3_order (id, name) values (1, 'Order-1')");
		jdbc.update("insert into " + C1 + ".c3_order_label (order_id, label) values (1, 'a'), (1, 'b'), (1, 'c')");
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			C3Order orderOne = em.find(C3Order.class, 1L);
			orderOne.getLabels().remove("a");
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("c3_order_label", 1);
		sql.assertInserts("c3_order_label", 0);
		// ── THEN: DB thật ──
		assertThat(jdbc.queryForList("select label from " + C1 + ".c3_order_label order by label", String.class))
			.as("chỉ nhãn 'a' bị xoá").containsExactly("b", "c");
		// ── THEN: DDL ──
		assertThat(schema.primaryKeyOf(C1, "c3_order_label")).as("Set có PK gồm FK và giá trị phần tử").containsExactlyInAnyOrder("order_id", "label");
	}

	/**
	 * G12-S05b — C4: {@code List} + {@code @OrderColumn} bỏ phần tử ĐẦU → không recreate nhưng phải dồn chỉ số 🔬
	 *
	 * <p>Mapping: C4 (OrderColumn, PK (order_id, step_order)). <b>Câu hỏi</b>: có còn "xoá hết ghi lại" không? Bỏ phần tử đầu thì SQL gì?
	 * <p><b>Kết luận</b>: không recreate. Vì vị trí là khoá, bỏ phần tử đầu thì các phần tử sau dồn lên: UPDATE chỉ số/giá trị các dòng bị dịch
	 * rồi DELETE dòng cuối. Rẻ hơn bag khi bỏ phần tử cuối, đắt dần khi bỏ phần tử càng gần đầu.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S05b | 🔬 @ElementCollection List + @OrderColumn bỏ phần tử đầu → không recreate")
	void s05b_orderColumnShiftsInsteadOfRecreating() {
		// ── GIVEN ──
		jdbc.update("insert into " + C1 + ".c4_order (id, name) values (1, 'Order-1')");
		jdbc.update("insert into " + C1 + ".c4_order_step (order_id, step_order, step) values (1, 0, 'a'), (1, 1, 'b'), (1, 2, 'c')");
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			C4Order orderOne = em.find(C4Order.class, 1L);
			orderOne.getSteps().remove(0); // bỏ "a": b, c dồn lên vị trí 0, 1
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("c4_order_step", 0);
		sql.assertUpdates("c4_order_step", 2);
		sql.assertDeletes("c4_order_step", 1);
		// ── THEN: DB thật ──
		assertThat(jdbc.queryForList("select step from " + C1 + ".c4_order_step order by step_order", String.class))
			.as("thứ tự được giữ nguyên sau khi dồn").containsExactly("b", "c");
		// ── THEN: DDL ──
		assertThat(schema.primaryKeyOf(C1, "c4_order_step")).as("PK gồm FK và cột thứ tự").containsExactlyInAnyOrder("order_id", "step_order");
	}
}
