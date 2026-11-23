package com.example.hibernatelab.g7_many_to_many;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.g7_many_to_many.m1_owner_product.M1Product;
import com.example.hibernatelab.g7_many_to_many.m1_owner_product.M1Tag;
import com.example.hibernatelab.g7_many_to_many.m5_list_bag.M5Product;
import com.example.hibernatelab.g7_many_to_many.m5_list_bag.M5Tag;
import com.example.hibernatelab.g7_many_to_many.m6_order_column.M6Product;
import com.example.hibernatelab.g7_many_to_many.m6_order_column.M6Tag;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G7, phần "Set hay List?": M1 (Set, baseline), M5 (List = bag), M6 (List + @OrderColumn).
 * S05 (bỏ 1 trong 3 tag), S06 (@OrderColumn), S09 (add trùng tag).
 *
 * <p>Điểm cần nắm: {@code Set} có khoá tự nhiên (chính phần tử) nên Hibernate biết xoá/thêm đúng dòng nào.
 * {@code List} không có @OrderColumn là "bag": không có gì định danh một dòng nối, nên khi đổi collection
 * Hibernate buộc phải ghi lại cả bảng nối của product đó.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G7SetVsListTest extends AbstractScenarioTest {

	private static final String M1 = "g7_m1_owner_product";
	private static final String M5 = "g7_m5_list_bag";
	private static final String M6 = "g7_m6_order_column";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { M1, M5, M6 };
	}

	/** Giá trị của một cột trên mọi dòng của bảng (đọc DB thật). Bảng nối được sắp theo hai cột đầu. */
	private List<Object> columnValues(String table, String column) {
		List<Object> values = new ArrayList<>();
		for (Map<String, Object> row : db.rowsOf(table)) {
			values.add(row.get(column));
		}
		return values;
	}

	/**
	 * G7-S05a — M1 (Set): bỏ 1 trong 3 tag
	 *
	 * <p>Mapping: M1, {@code Product.tags} là {@code Set<Tag>}.
	 * <p><b>Câu hỏi</b>: bỏ tag "b" khỏi product đang có a, b, c thì Hibernate gửi gì?
	 * <p><b>Kết luận</b>: đúng 1 DELETE dòng (product_id, tag_id của b), 0 INSERT; hai dòng còn lại không bị đụng.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S05a | M1 (Set): bỏ 1 trong 3 tag chỉ sinh 1 DELETE")
	void s05a_m1SetRemoveOneTagDeletesOneRow() {
		// ── GIVEN: phone có 3 tag a, b, c ──
		M1Product phone = new M1Product("Phone");
		M1Tag tagA = new M1Tag("a");
		M1Tag tagB = new M1Tag("b");
		M1Tag tagC = new M1Tag("c");
		inTx(() -> {
			em.persist(phone);
			em.persist(tagA);
			em.persist(tagB);
			em.persist(tagC);
			phone.getTags().add(tagA);
			phone.getTags().add(tagB);
			phone.getTags().add(tagC);
		});

		// ── WHEN: bỏ tag b ──
		inTx(() -> {
			M1Product managedPhone = em.find(M1Product.class, phone.getId());
			managedPhone.getTags().size(); // tải collection trước để snapshot có đủ 3 phần tử
			sql.reset();
			managedPhone.getTags().removeIf(tag -> tag.getName().equals("b"));
			// [FLUSH] lúc commit: Set so với snapshot → biết đúng dòng nào mất → DELETE đúng dòng đó
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("product_tag", 1);
		sql.assertInserts("product_tag", 0);

		// ── THEN: DB thật ──
		db.print(M1 + ".product_tag");
		assertThat(columnValues(M1 + ".product_tag", "tag_id")).as("còn lại a và c, b đã bị gỡ")
			.containsExactlyInAnyOrder(tagA.getId(), tagC.getId());
	}

	/**
	 * G7-S05b — M5 (List) 🔬: bỏ 1 trong 3 tag
	 *
	 * <p>Mapping: M5, {@code Product.tags} là {@code List<Tag>} (bag), bảng nối không có PK.
	 * <p><b>Câu hỏi</b>: cùng thao tác như S05a, List ghi gì?
	 * <p><b>Kết luận</b>: đúng "bag recreate": 1 DELETE toàn bộ dòng nối của product (không điều kiện tag_id), rồi INSERT lại 2 dòng
	 * còn lại. Kết quả cuối vẫn đúng nhưng tốn 3 câu thay vì 1; với list lớn rất đắt. Dòng {@code product} không bị đụng.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S05b | M5 (List) 🔬: bỏ 1 trong 3 tag làm Hibernate ghi lại cả bảng nối")
	void s05b_m5ListRemoveOneTagRecreatesJoinRows() {
		// ── GIVEN: phone có 3 tag a, b, c ──
		M5Product phone = new M5Product("Phone");
		M5Tag tagA = new M5Tag("a");
		M5Tag tagB = new M5Tag("b");
		M5Tag tagC = new M5Tag("c");
		inTx(() -> {
			em.persist(phone);
			em.persist(tagA);
			em.persist(tagB);
			em.persist(tagC);
			phone.getTags().add(tagA);
			phone.getTags().add(tagB);
			phone.getTags().add(tagC);
		});

		// ── WHEN: bỏ tag b ──
		inTx(() -> {
			M5Product managedPhone = em.find(M5Product.class, phone.getId());
			managedPhone.getTags().size(); // tải collection trước
			sql.reset();
			managedPhone.getTags().removeIf(tag -> tag.getName().equals("b"));
			// [FLUSH] lúc commit: bag không định danh được từng dòng → xoá hết rồi ghi lại
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("product_tag", 1);
		sql.assertInserts("product_tag", 2);

		// ── THEN: DB thật ──
		db.print(M5 + ".product_tag");
		assertThat(columnValues(M5 + ".product_tag", "tag_id")).as("kết quả cuối vẫn đúng: còn a và c")
			.containsExactlyInAnyOrder(tagA.getId(), tagC.getId());
	}

	/**
	 * G7-S06a — M6 🔬: DDL của {@code @OrderColumn}
	 *
	 * <p>Mapping: M6 = M5 + {@code @OrderColumn(name = "position")}.
	 * <p><b>Câu hỏi</b>: bảng nối có gì khác M5?
	 * <p><b>Kết luận</b>: có thêm cột {@code position}, và {@code position} trở thành một phần của PK: PK là
	 * {@code (product_id, position)} (không còn là cặp product_id/tag_id), nên cùng một tag có thể xuất hiện ở nhiều vị trí.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S06a | M6 🔬: @OrderColumn thêm cột position vào bảng nối")
	void s06a_m6OrderColumnDdl() {
		// ── THEN: DDL ──
		schema.printDdl(M6);
		assertThat(schema.hasColumn(M6, "product_tag", "position")).as("@OrderColumn thêm cột position vào bảng nối").isTrue();
		assertThat(schema.primaryKeyOf(M6, "product_tag")).as("PK là (product_id, position): vị trí là thứ định danh một dòng nối, không phải tag_id")
			.containsExactlyInAnyOrder("product_id", "position");
	}

	/**
	 * G7-S06b — M6 🔬: bỏ phần tử ở GIỮA danh sách
	 *
	 * <p>Mapping: M6.
	 * <p><b>Câu hỏi</b>: bỏ tag b (vị trí 1) trong [a, b, c] thì Hibernate ghi gì?
	 * <p><b>Kết luận</b>: KHÔNG recreate như bag (S05b). Hibernate dồn vị trí bằng đúng 1 UPDATE (dòng vị trí 1 đổi
	 * {@code tag_id} từ b sang c) và 1 DELETE (dòng vị trí cuối, 2). Cuối cùng DB có a ở vị trí 0, c ở vị trí 1.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S06b | M6 🔬: bỏ phần tử giữa thì dồn lại position")
	void s06b_m6RemoveMiddleElementShiftsPositions() {
		// ── GIVEN: phone có [a, b, c] ──
		M6Product phone = new M6Product("Phone");
		M6Tag tagA = new M6Tag("a");
		M6Tag tagB = new M6Tag("b");
		M6Tag tagC = new M6Tag("c");
		inTx(() -> {
			em.persist(phone);
			em.persist(tagA);
			em.persist(tagB);
			em.persist(tagC);
			phone.getTags().add(tagA);
			phone.getTags().add(tagB);
			phone.getTags().add(tagC);
		});

		// ── WHEN: bỏ phần tử ở vị trí 1 ──
		inTx(() -> {
			M6Product managedPhone = em.find(M6Product.class, phone.getId());
			managedPhone.getTags().size(); // tải collection trước
			sql.reset();
			managedPhone.getTags().remove(1);
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("product_tag", 1);
		sql.assertUpdates("product_tag", 1);
		sql.assertInserts("product_tag", 0);

		// ── THEN: DB thật ──
		db.print(M6 + ".product_tag");
		assertThat(columnValues(M6 + ".product_tag", "position")).as("vị trí được dồn liên tục 0, 1").containsExactly(0, 1);
		assertThat(columnValues(M6 + ".product_tag", "tag_id")).as("thứ tự còn lại là a rồi c").containsExactly(tagA.getId(), tagC.getId());
	}

	/**
	 * G7-S09a — M1 (Set) 🔬: add trùng một tag
	 *
	 * <p>Mapping: M1.
	 * <p><b>Câu hỏi</b>: add cùng một tag hai lần thì có mấy dòng nối?
	 * <p><b>Kết luận</b>: {@code Set} loại trùng ngay trong Java ({@code add} lần hai trả về false), nên chỉ 1 INSERT, 1 dòng.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S09a | M1 (Set) 🔬: add trùng tag vẫn chỉ có 1 dòng nối")
	void s09a_m1SetIgnoresDuplicateTag() {
		// ── GIVEN ──
		M1Product phone = new M1Product("Phone");
		M1Tag tagNew = new M1Tag("new");
		inTx(() -> {
			em.persist(phone);
			em.persist(tagNew);
		});

		// ── WHEN: add cùng tag hai lần ──
		inTx(() -> {
			M1Product managedPhone = em.find(M1Product.class, phone.getId());
			M1Tag managedTag = em.find(M1Tag.class, tagNew.getId());
			sql.reset();
			boolean firstAdd = managedPhone.getTags().add(managedTag);
			boolean secondAdd = managedPhone.getTags().add(managedTag);
			assertThat(firstAdd).as("lần đầu thêm được").isTrue();
			assertThat(secondAdd).as("Set từ chối phần tử trùng ngay trong Java").isFalse();
		});

		// ── THEN: SQL + DB thật ──
		sql.print();
		sql.assertInserts("product_tag", 1);
		assertThat(db.countOf(M1 + ".product_tag")).as("chỉ 1 dòng nối").isEqualTo(1);
	}

	/**
	 * G7-S09b — M5 (List) 🔬: add trùng một tag
	 *
	 * <p>Mapping: M5, bảng nối không có PK.
	 * <p><b>Câu hỏi</b>: List cho add trùng thì DB nhận mấy dòng?
	 * <p><b>Kết luận</b>: 2 INSERT, 2 dòng GIỐNG HỆT nhau. Bảng nối của bag không có PK nên DB không chặn
	 * (còn với Set: S09a). Nếu cần "mỗi tag một lần" thì dùng Set.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S09b | M5 (List) 🔬: add trùng tag, bảng nối không PK nên có 2 dòng trùng")
	void s09b_m5ListKeepsDuplicateTag() {
		// ── GIVEN ──
		M5Product phone = new M5Product("Phone");
		M5Tag tagNew = new M5Tag("new");
		inTx(() -> {
			em.persist(phone);
			em.persist(tagNew);
		});

		// ── WHEN: add cùng tag hai lần ──
		inTx(() -> {
			M5Product managedPhone = em.find(M5Product.class, phone.getId());
			M5Tag managedTag = em.find(M5Tag.class, tagNew.getId());
			sql.reset();
			managedPhone.getTags().add(managedTag);
			managedPhone.getTags().add(managedTag);
		});

		// ── THEN: SQL + DB thật ──
		sql.print();
		sql.assertInserts("product_tag", 2);
		db.print(M5 + ".product_tag");
		assertThat(schema.primaryKeyOf(M5, "product_tag")).as("bag không có PK nên DB không thể chặn cặp trùng").isEmpty();
		assertThat(columnValues(M5 + ".product_tag", "tag_id")).as("cùng một tag xuất hiện hai lần")
			.containsExactly(tagNew.getId(), tagNew.getId());
	}
}
