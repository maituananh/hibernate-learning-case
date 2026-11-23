package com.example.hibernatelab.g9_integrated;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.hibernatelab.g9_integrated.s01_category_tree.Category;
import com.example.hibernatelab.g9_integrated.s01_category_tree.CategoryProduct;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * G9-S01 ⭐ Cây danh mục tự tham chiếu: persist cây 3 tầng (a), chuyển nhánh (b), xoá node giữa (c, d), duyệt cây (e, f).
 *
 * <p>Mapping: {@code Category.parent} là OWNING side (FK parent_id ở chính bảng category, LAZY);
 * {@code Category.children} là INVERSE side (mappedBy, cascade ALL, orphanRemoval).
 * <p>Cây dùng chung: Electronics → [Phones → [Android], Laptops].
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G9S01CategoryTreeTest extends AbstractScenarioTest {

	private static final String SCHEMA = "g9_s01_category_tree";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { SCHEMA };
	}

	/** Dựng và lưu cây 3 tầng: Electronics → [Phones → [Android], Laptops]. Cascade PERSIST từ gốc lo toàn bộ. */
	private void seedTree() {
		Category electronics = new Category("Electronics");
		Category phones = new Category("Phones");
		Category laptops = new Category("Laptops");
		Category android = new Category("Android");
		electronics.addChild(phones);
		electronics.addChild(laptops);
		phones.addChild(android);
		inTx(() -> em.persist(electronics));
	}

	/** Id của category theo tên (đọc bằng JDBC). */
	private Long categoryId(String name) {
		for (Map<String, Object> row : db.rowsOf(SCHEMA + ".category")) {
			if (name.equals(row.get("name"))) {
				return ((Number) row.get("id")).longValue();
			}
		}
		throw new AssertionError("Không có category " + name);
	}

	/** parent_id của category theo tên, null nếu là gốc. */
	private Long parentIdOf(String name) {
		Object parentId = db.rowById(SCHEMA + ".category", categoryId(name)).get("parent_id");
		return parentId == null ? null : ((Number) parentId).longValue();
	}

	/**
	 * G9-S01a — Persist cây 3 tầng chỉ bằng một lần persist gốc
	 *
	 * <p><b>Câu hỏi</b>: persist node gốc thì INSERT chạy theo thứ tự nào, parent_id có đúng không?
	 * <p><b>Kết luận</b>: cascade PERSIST đi từ cha xuống con nên INSERT cha trước con; parent_id đúng nhờ owning side {@code parent}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S01a | Persist gốc: cascade INSERT cha trước con, parent_id đúng")
	void s01a_persistTreeInsertsParentsBeforeChildren() {
		// ── GIVEN ──
		Category electronics = new Category("Electronics");
		Category phones = new Category("Phones");
		Category android = new Category("Android");
		electronics.addChild(phones);
		phones.addChild(android);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			em.persist(electronics);
			// [LIFECYCLE] cascade PERSIST: cả 3 node TRANSIENT → MANAGED, CHƯA có INSERT nào
			assertState(em, phones, MANAGED);
			assertState(em, android, MANAGED);
			// [FLUSH] lúc commit: INSERT theo thứ tự cascade (cha trước con)
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts(SCHEMA + ".category", 3);
		sql.assertInOrder("insert into " + SCHEMA + ".category", "insert into " + SCHEMA + ".category");

		// ── THEN: DB thật ──
		db.print(SCHEMA + ".category");
		assertThat(parentIdOf("Electronics")).as("gốc không có cha nên parent_id NULL").isNull();
		assertThat(parentIdOf("Phones")).as("Phones là con của Electronics: owning side parent đã ghi FK")
			.isEqualTo(categoryId("Electronics"));
		assertThat(parentIdOf("Android")).as("Android là con của Phones (tầng 3)").isEqualTo(categoryId("Phones"));
	}

	/**
	 * G9-S01b — Chuyển cả nhánh sang cha khác bằng cách đổi owning side
	 *
	 * <p><b>Câu hỏi</b>: chuyển Phones (kèm Android) từ Electronics sang Home có cần đụng tới cả hai cha không?
	 * <p><b>Kết luận</b>: không. {@code phones.setParent(home)} là đủ: đúng 1 UPDATE parent_id của Phones, Android đi theo vì nó trỏ vào Phones.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S01b | Chuyển nhánh: chỉ 1 UPDATE parent_id của node được chuyển")
	void s01b_movingBranchIsSingleUpdateOfParentId() {
		// ── GIVEN ──
		seedTree();
		Long homeId = inTxReturning(() -> {
			Category home = new Category("Home");
			em.persist(home);
			return home.getId();
		});
		Long phonesId = categoryId("Phones");

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			Category phones = em.find(Category.class, phonesId);
			Category home = em.find(Category.class, homeId);
			phones.setParent(home); // chỉ đổi OWNING side: dirty checking sẽ thấy cột parent_id đổi
			// [FLUSH] lúc commit: UPDATE category set name=?, parent_id=? where id=? (Phones)
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates(SCHEMA + ".category", 1);
		sql.assertDeletes(SCHEMA + ".category", 0);
		sql.assertInserts(SCHEMA + ".category", 0);

		// ── THEN: DB thật ──
		assertThat(parentIdOf("Phones")).as("Phones giờ thuộc Home").isEqualTo(homeId);
		assertThat(parentIdOf("Android")).as("Android không bị đụng tới, vẫn là con của Phones nên đi theo cả nhánh")
			.isEqualTo(phonesId);
		assertThat(db.countOf(SCHEMA + ".category")).as("không dòng nào bị xoá hay thêm").isEqualTo(5);
	}

	/**
	 * G9-S01c — Xoá node giữa (Phones) bằng orphanRemoval: cả nhánh bị xoá, con trước cha
	 *
	 * <p><b>Câu hỏi</b>: {@code electronics.removeChild(phones)} xoá những dòng nào và theo thứ tự nào?
	 * <p><b>Kết luận</b>: orphanRemoval xoá Phones, cascade REMOVE lan xuống Android; DELETE Android chạy TRƯỚC DELETE Phones (nếu ngược lại FK sẽ chặn).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S01c | Xoá node giữa: DELETE cả nhánh, con trước cha")
	void s01c_removingMiddleNodeDeletesBranchChildrenFirst() {
		// ── GIVEN ──
		seedTree();
		Long electronicsId = categoryId("Electronics");
		Long phonesId = categoryId("Phones");

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			Category electronics = em.find(Category.class, electronicsId);
			Category phones = em.find(Category.class, phonesId);
			electronics.removeChild(phones); // phones thành orphan (mồ côi) → bị xoá lúc flush, cascade xuống Android
			// [FLUSH] lúc commit: DELETE Android, rồi DELETE Phones
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes(SCHEMA + ".category", 2);
		sql.assertUpdates(SCHEMA + ".category", 0);
		// Hai câu DELETE giống hệt nhau về text (chỉ khác tham số id) nên không phân biệt thứ tự bằng chuỗi SQL được.
		// Nhưng FK category.parent_id kiểm tra NGAY khi chạy từng câu: nếu DELETE Phones chạy trước Android thì
		// PostgreSQL đã từ chối và test này đỏ. Test xanh nghĩa là Android (con) đã bị xoá trước Phones (cha).

		// ── THEN: DB thật ──
		db.print(SCHEMA + ".category");
		assertThat(db.countOf(SCHEMA + ".category")).as("còn Electronics và Laptops; Phones và Android đã bị xoá").isEqualTo(2);
		assertThat(parentIdOf("Laptops")).as("nhánh Laptops không bị ảnh hưởng").isEqualTo(electronicsId);
	}

	/**
	 * G9-S01d — Xoá node khi còn sản phẩm trỏ tới: vi phạm FK
	 *
	 * <p><b>Câu hỏi</b>: xoá Phones khi một product vẫn có {@code category = Phones} thì sao?
	 * <p><b>Kết luận</b>: không cascade từ category sang product nên DELETE category bị PostgreSQL từ chối; transaction rollback, cây còn nguyên.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S01d | Xoá node còn product trỏ tới: DataIntegrityViolationException, DB nguyên vẹn")
	void s01d_removingNodeStillReferencedByProductViolatesForeignKey() {
		// ── GIVEN ──
		seedTree();
		Long phonesId = categoryId("Phones");
		inTx(() -> em.persist(new CategoryProduct("Pixel", em.getReference(Category.class, phonesId))));

		// ── WHEN + THEN ──
		assertThatThrownBy(() -> inTx(() -> {
			Category electronics = em.find(Category.class, categoryId("Electronics"));
			electronics.removeChild(em.find(Category.class, phonesId)); // DELETE Android OK, DELETE Phones bị FK product chặn
		})).as("product.category_id còn trỏ tới Phones nên DELETE Phones phải bị DB từ chối")
			.isInstanceOf(DataIntegrityViolationException.class);

		// ── THEN: DB thật ──
		assertThat(db.countOf(SCHEMA + ".category")).as("rollback: cả 4 category còn nguyên (kể cả Android đã DELETE trước đó)")
			.isEqualTo(4);
		assertThat(db.countOf(SCHEMA + ".product")).as("product vẫn còn").isEqualTo(1);
	}

	/**
	 * G9-S01e — Duyệt cây qua collection LAZY: N+1 theo từng node
	 *
	 * <p><b>Câu hỏi</b>: duyệt hết cây bằng {@code getChildren()} đệ quy tốn bao nhiêu SELECT?
	 * <p><b>Kết luận</b>: 1 SELECT tìm gốc + 1 SELECT nạp children cho MỖI node (kể cả lá): 4 node → 5 SELECT.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S01e | Duyệt cây bằng getChildren(): 1 + số node câu SELECT (N+1 theo tầng)")
	void s01e_walkingTreeThroughLazyChildrenIsNPlusOne() {
		// ── GIVEN ──
		seedTree();
		Long electronicsId = categoryId("Electronics");

		// ── WHEN ──
		sql.reset();
		int visited = inTxReturning(() -> countNodes(em.find(Category.class, electronicsId)));

		// ── THEN: SQL ──
		sql.print();
		assertThat(visited).as("cây có 4 node").isEqualTo(4);
		sql.assertSelects(SCHEMA + ".category", 5);
		sql.assertTotal(5);
	}

	private int countNodes(Category node) {
		int count = 1;
		for (Category child : node.getChildren()) { // mỗi lần gọi trên node chưa nạp là một SELECT riêng
			count += countNodes(child);
		}
		return count;
	}

	/**
	 * G9-S01f — Sửa N+1: tải TOÀN BỘ category bằng 1 query rồi ghép cây trong bộ nhớ
	 *
	 * <p><b>Câu hỏi</b>: có dựng được cả cây chỉ với 1 SELECT không?
	 * <p><b>Kết luận</b>: có. {@code select c from Category c} nạp mọi node vào persistence context; sau đó ghép cây bằng {@code getParent()}
	 * (owning side, đã nằm sẵn trong persistence context nên KHÔNG tốn SQL). Lưu ý: gọi {@code getChildren()} vẫn sẽ SELECT, vì collection lazy
	 * không được "tự điền" dù các node con đã được nạp.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S01f | Tải tất cả node bằng 1 query rồi ghép cây qua getParent(): 1 SELECT")
	void s01f_loadingAllNodesInOneQueryBuildsTreeWithoutExtraSelects() {
		// ── GIVEN ──
		seedTree();

		// ── WHEN ──
		sql.reset();
		Map<String, List<String>> childNamesByParentName = inTxReturning(() -> {
			List<Category> all = em.createQuery("select c from G9S01_Category c", Category.class).getResultList();
			Map<String, List<String>> tree = new HashMap<>();
			for (Category category : all) {
				Category parent = category.getParent(); // đã nằm trong persistence context → không SQL
				if (parent != null) {
					tree.computeIfAbsent(parent.getName(), key -> new ArrayList<>()).add(category.getName());
				}
			}
			return tree;
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects(SCHEMA + ".category", 1);
		sql.assertTotal(1);

		// ── THEN: kết quả ghép cây trong bộ nhớ ──
		assertThat(childNamesByParentName.get("Electronics")).as("con trực tiếp của gốc")
			.containsExactlyInAnyOrder("Phones", "Laptops");
		assertThat(childNamesByParentName.get("Phones")).as("tầng 3: con của Phones").containsExactly("Android");
	}
}
