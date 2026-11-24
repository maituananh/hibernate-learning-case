package com.example.hibernatelab.g10_query;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g10_query.q1_baseline.Q1Customer;
import com.example.hibernatelab.g10_query.q2_versioned.Q2Product;
import com.example.hibernatelab.g9_integrated.s11_soft_delete.OrderItem;
import com.example.hibernatelab.g9_integrated.s11_soft_delete.PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G10, câu hỏi thứ hai của giai đoạn mở rộng: native SQL có làm vòng đời entity khác JPQL không?
 * S04 (native có kiểu entity), S05 (native không kiểu), S06 (native UPDATE), S07 (native SELECT với {@code @SoftDelete}).
 *
 * <p>Ý chính: Hibernate chỉ "biết" những gì đi qua nó. Native query có kiểu entity vẫn tạo ra entity MANAGED (đi qua
 * persistence context), còn native query không kiểu hoặc native UPDATE thì Hibernate không theo dõi gì cả.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G10NativeQueryTest extends AbstractScenarioTest {

	private static final String Q1 = "g10_q1_baseline";
	private static final String Q2 = "g10_q2_versioned";
	private static final String G9_SOFT = "g9_s11_soft_delete";

	@Autowired
	private JdbcTemplate jdbc;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { Q1, Q2, G9_SOFT };
	}

	/**
	 * G10-S04a — Q1: native query có kiểu entity trả về entity MANAGED, cùng instance với entity đã có 🔬
	 *
	 * <p>Mapping: Q1 baseline. <b>Câu hỏi</b>: {@code createNativeQuery(sql, Q1Customer.class)} trả entity ở trạng thái nào, và nếu
	 * customer đó đã nằm trong persistence context thì có ra instance mới không?
	 * <p><b>Kết luận</b>: MANAGED, và là CHÍNH instance đã có (identity map vẫn áp dụng: một dòng DB = một instance trong một PC).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G10-S04a | native query có kiểu entity → MANAGED, cùng instance đã có trong persistence context")
	void s04a_typedNativeQueryReturnsManagedSameInstance() {
		// ── GIVEN ──
		G10Fixtures.seed(jdbc, Q1);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			Q1Customer loadedByFind = em.find(Q1Customer.class, 1L);
			@SuppressWarnings("unchecked")
			List<Q1Customer> fromNative = em
				.createNativeQuery("select * from " + Q1 + ".customer where id = 1", Q1Customer.class).getResultList();
			// [LIFECYCLE] entity từ native query: MANAGED, và trùng instance với find()
			assertState(em, fromNative.get(0), MANAGED);
			assertThat(fromNative.get(0)).as("identity map: cùng id trong cùng PC → cùng instance, dù đi bằng native").isSameAs(loadedByFind);
			// [FLUSH] không có thay đổi
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(2); // 1 từ find, 1 từ native: native luôn chạy SQL, nhưng không tạo instance thứ hai
		sql.assertSelects(Q1 + ".customer", 2);

		// ── THEN: DB thật ──
		assertThat(db.countOf(Q1 + ".customer")).as("chỉ đọc, DB không đổi").isEqualTo(G10Fixtures.CUSTOMER_COUNT);
	}

	/**
	 * G10-S04b — Q1: dirty checking chạy trên entity lấy từ native query 🔬
	 *
	 * <p><b>Câu hỏi</b>: đổi tên entity vừa lấy bằng native query có sinh UPDATE lúc flush không?
	 * <p><b>Kết luận</b>: có, đúng 1 UPDATE: vì entity MANAGED nên được snapshot và dirty checking như mọi entity khác.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G10-S04b | native query có kiểu entity → sửa tại chỗ vẫn được dirty checking và UPDATE lúc flush")
	void s04b_typedNativeQueryEntityIsDirtyChecked() {
		// ── GIVEN ──
		G10Fixtures.seed(jdbc, Q1);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			@SuppressWarnings("unchecked")
			List<Q1Customer> fromNative = em
				.createNativeQuery("select * from " + Q1 + ".customer where id = 1", Q1Customer.class).getResultList();
			fromNative.get(0).rename("Customer-1-renamed"); // không gọi save/persist: chỉ sửa field
			// [LIFECYCLE] vẫn MANAGED
			assertState(em, fromNative.get(0), MANAGED);
			// [FLUSH] commit → dirty checking thấy name đổi → UPDATE
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates(Q1 + ".customer", 1);

		// ── THEN: DB thật ──
		db.print(Q1 + ".customer");
		assertThat(db.rowById(Q1 + ".customer", 1L).get("name")).as("UPDATE đã ghi tên mới vào DB").isEqualTo("Customer-1-renamed");
	}

	/**
	 * G10-S05 — Q1: native query không kiểu ({@code Object[]}) không đưa gì vào persistence context
	 *
	 * <p><b>Câu hỏi</b>: native query chọn cột lẻ trả {@code Object[]}: persistence context có entity nào không?
	 * <p><b>Kết luận</b>: không (0 entity): kết quả chỉ là mảng giá trị, không có vòng đời, không dirty checking, không lazy loading.
	 * Muốn sửa dữ liệu từ kết quả này phải tự viết UPDATE.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G10-S05 | native query không kiểu (Object[]) → 0 entity trong persistence context")
	void s05_untypedNativeQueryPutsNothingInPersistenceContext() {
		// ── GIVEN ──
		G10Fixtures.seed(jdbc, Q1);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			@SuppressWarnings("unchecked")
			List<Object[]> rows = em.createNativeQuery("select id, name from " + Q1 + ".customer order by id").getResultList();
			int entitiesInPersistenceContext = em.unwrap(Session.class).getStatistics().getEntityCount();
			// [LIFECYCLE] không có entity nào để mà có vòng đời
			assertThat(rows).as("đọc đủ 3 dòng").hasSize(G10Fixtures.CUSTOMER_COUNT);
			assertThat(entitiesInPersistenceContext).as("Object[] chỉ là giá trị thô, Hibernate không tạo entity").isZero();
			// [FLUSH] không có gì để flush
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);

		// ── THEN: DB thật ──
		assertThat(db.countOf(Q1 + ".customer")).as("chỉ đọc, DB không đổi").isEqualTo(G10Fixtures.CUSTOMER_COUNT);
	}

	/**
	 * G10-S06 — Q2: native UPDATE đi vòng qua Hibernate: entity MANAGED không thấy giá mới, {@code @Version} không tăng 🔬
	 *
	 * <p>Mapping: Q2 ({@code Product} có {@code @Version}). Đối chiếu G9-S05 (bulk JPQL cũng lệch như vậy).
	 * <p><b>Câu hỏi</b>: sau native {@code UPDATE product set price = ...}, entity đã tải đọc ra giá nào, và cột version trong DB là bao nhiêu?
	 * <p><b>Kết luận</b>: entity vẫn giữ giá CŨ (Hibernate không biết DB đã đổi, không tải lại), DB có giá MỚI; version vẫn 0
	 * vì chính Hibernate không UPDATE. Hệ quả: một transaction khác đang giữ bản cũ vẫn ghi đè được mà không bị
	 * {@code OptimisticLockException}. Muốn an toàn phải tự {@code version = version + 1} trong SQL, hoặc {@code em.refresh}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G10-S06 | Q2: native UPDATE → entity MANAGED giữ giá cũ, DB giá mới, @Version không tăng")
	void s06_nativeUpdateBypassesPersistenceContextAndVersion() {
		// ── GIVEN ──
		jdbc.update("insert into " + Q2 + ".product (id, name, price, version) values (1, 'PEN', 10.00, 0)");

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			Q2Product productPen = em.find(Q2Product.class, 1L);
			em.createNativeQuery("update " + Q2 + ".product set price = 99.00 where id = 1").executeUpdate();
			// [LIFECYCLE] productPen vẫn MANAGED và vẫn mang giá cũ
			assertState(em, productPen, MANAGED);
			assertThat(productPen.getPrice()).as("Hibernate không biết native UPDATE đã đổi DB nên không tải lại entity")
				.isEqualByComparingTo(new BigDecimal("10.00"));
			// [FLUSH] productPen không dirty → không có UPDATE nào từ Hibernate
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates(Q2 + ".product", 1); // đúng 1 UPDATE: chính câu native; Hibernate không thêm UPDATE nào

		// ── THEN: DB thật ──
		db.print(Q2 + ".product");
		assertThat((BigDecimal) db.rowById(Q2 + ".product", 1L).get("price")).as("DB đã có giá mới từ native UPDATE")
			.isEqualByComparingTo(new BigDecimal("99.00"));
		assertThat(db.rowById(Q2 + ".product", 1L).get("version")).as("@Version chỉ tăng khi Hibernate UPDATE, native thì không").isEqualTo(0);
	}

	/**
	 * G10-S07 — G9-S11 ({@code @SoftDelete}): native SELECT không tự lọc dòng đã xoá mềm 🔬
	 *
	 * <p>Mapping: dùng lại entity của G9-S11 ({@code OrderItem} có {@code @SoftDelete}).
	 * <p><b>Câu hỏi</b>: sau khi xoá mềm 1 trong 2 item, native {@code select * from order_item} (có kiểu entity) trả mấy dòng?
	 * <p><b>Kết luận</b>: native trả CẢ 2 dòng (kể cả dòng đã xoá mềm), JPQL chỉ trả 1. {@code @SoftDelete} chỉ được Hibernate gắn vào câu SQL
	 * do chính nó sinh ra (JPQL, {@code find}, collection); native SQL là văn bản của mình nên không được thêm điều kiện.
	 * Hệ quả: mọi native query trên bảng soft delete phải tự viết {@code where deleted = false}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G10-S07 | @SoftDelete: native SELECT có kiểu entity có tự lọc dòng đã xoá mềm không?")
	void s07_nativeSelectVersusSoftDelete() {
		// ── GIVEN ──
		PurchaseOrder orderA = new PurchaseOrder("A-001");
		OrderItem itemPen01 = new OrderItem("PEN-01");
		orderA.addItem(itemPen01);
		orderA.addItem(new OrderItem("PEN-02"));
		inTx(() -> em.persist(orderA));
		inTx(() -> em.remove(em.find(OrderItem.class, itemPen01.getId()))); // xoá mềm PEN-01

		// ── WHEN ──
		sql.reset();
		List<?> viaNative = inTxReturning(() -> em
			.createNativeQuery("select * from " + G9_SOFT + ".order_item", OrderItem.class).getResultList());
		List<OrderItem> viaJpql = inTxReturning(() -> em.createQuery("select i from G9S11_OrderItem i", OrderItem.class).getResultList());

		// ── THEN: SQL ──
		sql.print();
		assertThat(viaNative).as("native SQL không được Hibernate thêm điều kiện loại dòng xoá mềm → thấy cả 2 dòng").hasSize(2);
		assertThat(viaJpql).as("JPQL do Hibernate dịch nên tự thêm điều kiện deleted → chỉ thấy PEN-02").hasSize(1);

		// ── THEN: DB thật ──
		assertThat(db.countOf(G9_SOFT + ".order_item")).as("DB vẫn giữ cả 2 dòng").isEqualTo(2);
	}
}
