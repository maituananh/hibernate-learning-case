package com.example.hibernatelab.g12_advanced_model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g12_advanced_model.f1_formula_filter.F1Coupon;
import com.example.hibernatelab.g12_advanced_model.f1_formula_filter.F1Customer;
import com.example.hibernatelab.g12_advanced_model.f1_formula_filter.F1Note;
import com.example.hibernatelab.g12_advanced_model.f1_formula_filter.F1Order;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G12 (➕), chủ đề cột tính toán và lọc: S12 ({@code @Formula}), S13 ({@code @SQLRestriction}), S14 ({@code @Filter}).
 *
 * <p>Model F1: {@code Customer} (có {@code @Formula orderCount}, collection {@code notes} có restriction), {@code Order} (có {@code @Filter}),
 * {@code Coupon} (restriction trên entity), {@code Note}.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G12FormulaFilterTest extends AbstractScenarioTest {

	private static final String F1 = "g12_f1_formula_filter";

	@Autowired
	private JdbcTemplate jdbc;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { F1 };
	}

	private void seedCustomerWithOneOrder() {
		jdbc.update("insert into " + F1 + ".customer (id, name) values (1, 'Alice')");
		jdbc.update("insert into " + F1 + ".purchase_order (id, order_no, status, customer_id) values (11, 'ORD-11', 'PAID', 1)");
	}

	/**
	 * G12-S12 — F1 🔬: {@code @Formula} chỉ được tính lại khi TẢI LẠI, không tự cập nhật sau khi thêm order trong cùng PC
	 *
	 * <p>Mapping: F1 ({@code orderCount} là subquery, không có cột thật). <b>Câu hỏi</b>: thêm 1 order rồi flush, {@code getOrderCount()} đổi không?
	 * <p><b>Kết luận</b>: không. Entity vẫn MANAGED với giá trị 1 đã nạp lúc đầu; DB đã có 2 order nhưng chỉ lần SELECT customer tiếp theo
	 * (transaction mới, hoặc {@code refresh}) mới ra 2. Cột tính toán là snapshot tại thời điểm nạp.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S12 | 🔬 @Formula chỉ tính lại khi tải lại: thêm order trong cùng PC thì orderCount vẫn cũ")
	void s12_formulaIsRecomputedOnlyOnReload() {
		// ── GIVEN ──
		seedCustomerWithOneOrder();
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			F1Customer customerAlice = em.find(F1Customer.class, 1L); // [LIFECYCLE] MANAGED, orderCount = 1
			assertThat(customerAlice.getOrderCount()).as("lúc nạp subquery đếm được 1 order").isEqualTo(1);
			em.persist(new F1Order("ORD-12", "NEW", customerAlice));
			em.flush(); // [FLUSH] order thứ hai đã nằm trong DB (chưa commit)

			// ── THEN (trong transaction) ──
			assertThat(customerAlice.getOrderCount()).as("entity không tự nạp lại: formula vẫn là giá trị cũ dù DB đã có 2 order").isEqualTo(1);
		});
		int countAfterReload = inTxReturning(() -> em.find(F1Customer.class, 1L).getOrderCount());

		// ── THEN: SQL + DDL ──
		sql.print();
		sql.assertSomeStatementContains("select count(*)");
		assertThat(countAfterReload).as("tải lại trong transaction mới thì subquery chạy lại và ra 2").isEqualTo(2);
		assertThat(schema.hasColumn(F1, "customer", "order_count")).as("@Formula không tạo cột trong DB").isFalse();
	}

	/**
	 * G12-S13a — F1 🔬: {@code @SQLRestriction} trên ENTITY: {@code find} theo id và JPQL đều bị lọc
	 *
	 * <p>Mapping: F1 ({@code Coupon} có {@code @SQLRestriction("active = true")}). <b>Câu hỏi</b>: coupon id 2 có {@code active = false} thì
	 * {@code find(2)} và JPQL trả gì?
	 * <p><b>Kết luận</b>: {@code find} trả {@code null} và JPQL không thấy dòng đó: restriction gắn vào mọi SELECT mà Hibernate sinh cho entity,
	 * kể cả load theo id. Không có cách tắt trong session (khác {@code @Filter}).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S13a | 🔬 @SQLRestriction trên entity: find theo id và JPQL đều không thấy dòng bị loại")
	void s13a_restrictionAppliesToFindAndJpql() {
		// ── GIVEN ──
		jdbc.update("insert into " + F1 + ".coupon (id, code, active) values (1, 'SALE10', true), (2, 'OLD50', false)");
		sql.reset();

		// ── WHEN ──
		F1Coupon inactiveCoupon = inTxReturning(() -> em.find(F1Coupon.class, 2L));
		List<F1Coupon> visibleCoupons = inTxReturning(() -> em.createQuery("select c from G12F1_Coupon c", F1Coupon.class).getResultList());

		// ── THEN ──
		sql.print();
		sql.assertSomeStatementContains("active = true");
		assertThat(inactiveCoupon).as("find theo id cũng bị thêm 'and active = true' nên coupon 2 như không tồn tại").isNull();
		assertThat(visibleCoupons).as("JPQL chỉ thấy coupon đang active").hasSize(1);
	}

	/**
	 * G12-S13b — F1 🔬: native SQL KHÔNG bị {@code @SQLRestriction} (Hibernate không sửa câu SQL của bạn)
	 *
	 * <p>Mapping: F1. <b>Câu hỏi</b>: native {@code select count(*) from coupon} đếm bao nhiêu?
	 * <p><b>Kết luận</b>: 2 (cả dòng không active). Restriction chỉ áp lên SQL do Hibernate tự sinh từ HQL/Criteria/find; native query phải tự
	 * thêm điều kiện. Cùng bài học với G10-S07 (native và {@code @SoftDelete}).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S13b | 🔬 native query không bị @SQLRestriction: đếm cả dòng không active")
	void s13b_nativeQueryIgnoresRestriction() {
		// ── GIVEN ──
		jdbc.update("insert into " + F1 + ".coupon (id, code, active) values (1, 'SALE10', true), (2, 'OLD50', false)");

		// ── WHEN ──
		Number nativeCount = inTxReturning(() ->
			(Number) em.createNativeQuery("select count(*) from " + F1 + ".coupon").getSingleResult());

		// ── THEN ──
		assertThat(nativeCount.intValue()).as("native SQL chạy nguyên văn nên thấy cả coupon không active").isEqualTo(2);
	}

	/**
	 * G12-S13c — F1 🔬: {@code @SQLRestriction} trên COLLECTION lọc phần tử khi nạp collection
	 *
	 * <p>Mapping: F1 ({@code Customer.notes} có {@code @SQLRestriction("deleted = false")}, entity {@code Note} không có). <b>Câu hỏi</b>:
	 * customer có 2 note (1 đã xoá mềm), {@code getNotes()} trả mấy?
	 * <p><b>Kết luận</b>: 1. Câu SELECT nạp collection được thêm {@code deleted = false}. Nhưng {@code find(Note, id)} theo id vẫn thấy note đã xoá,
	 * vì restriction nằm ở collection chứ không ở entity.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S13c | 🔬 @SQLRestriction trên collection: chỉ lọc khi nạp collection, find(Note) theo id vẫn thấy")
	void s13c_restrictionOnCollectionOnlyFiltersTheCollection() {
		// ── GIVEN ──
		jdbc.update("insert into " + F1 + ".customer (id, name) values (1, 'Alice')");
		jdbc.update("insert into " + F1 + ".note (id, text, deleted, customer_id) values (21, 'còn dùng', false, 1), (22, 'đã xoá mềm', true, 1)");
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			F1Customer customerAlice = em.find(F1Customer.class, 1L);
			int visibleNotes = customerAlice.getNotes().size();

			// ── THEN ──
			assertThat(visibleNotes).as("note đã xoá mềm bị loại khỏi collection").isEqualTo(1);
			assertThat(em.find(F1Note.class, 22L))
				.as("restriction nằm ở collection nên tải thẳng Note theo id vẫn thấy dòng đã xoá mềm").isNotNull();
		});
		sql.print();
		sql.assertSomeStatementContains("deleted = false");
	}

	/**
	 * G12-S14a — F1: {@code @Filter} mặc định TẮT: query thấy mọi order
	 *
	 * <p>Mapping: F1 ({@code Order} có {@code @Filter}). <b>Câu hỏi</b>: khai báo {@code @Filter} mà chưa bật thì sao?
	 * <p><b>Kết luận</b>: không có tác dụng, JPQL trả cả 2 order. Filter phải được bật theo TỪNG session.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S14a | @Filter chưa bật thì không lọc gì")
	void s14a_filterIsOffByDefault() {
		// ── GIVEN ──
		seedTwoOrders();

		// ── WHEN ──
		List<F1Order> orders = inTxReturning(() -> em.createQuery("select o from G12F1_Order o", F1Order.class).getResultList());

		// ── THEN ──
		assertThat(orders).as("filter chưa enable nên cả đơn PAID lẫn NEW đều được trả về").hasSize(2);
	}

	/**
	 * G12-S14b — F1 🔬: {@code @Filter} đã bật lọc JPQL nhưng KHÔNG áp cho {@code find} theo id
	 *
	 * <p>Mapping: F1. <b>Câu hỏi</b>: bật filter {@code status = 'PAID'}; JPQL trả mấy order, và {@code find} order NEW theo id trả gì?
	 * <p><b>Kết luận</b>: JPQL chỉ thấy order PAID, nhưng {@code find(NEW order)} VẪN trả order đó, vì filter không áp cho load theo id
	 * (khác {@code @SQLRestriction} ở S13a). Đừng coi {@code @Filter} là rào bảo mật dữ liệu.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S14b | 🔬 @Filter đã bật: JPQL bị lọc nhưng find theo id vẫn thấy dòng bị lọc")
	void s14b_enabledFilterDoesNotApplyToFindById() {
		// ── GIVEN ──
		seedTwoOrders();
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			Session session = em.unwrap(Session.class);
			session.enableFilter("orderStatus").setParameter("status", "PAID"); // bật cho riêng session này
			List<F1Order> paidOrders = em.createQuery("select o from G12F1_Order o", F1Order.class).getResultList();
			F1Order newOrderById = em.find(F1Order.class, 12L); // đơn NEW nhưng tải theo id

			// ── THEN ──
			assertThat(paidOrders).as("JPQL được thêm 'status = PAID' nên chỉ còn 1 order").hasSize(1);
			assertThat(newOrderById).as("find theo id không áp filter nên vẫn thấy đơn NEW").isNotNull();
		});
		sql.print();
	}

	private void seedTwoOrders() {
		jdbc.update("insert into " + F1 + ".customer (id, name) values (1, 'Alice')");
		jdbc.update("insert into " + F1 + ".purchase_order (id, order_no, status, customer_id) values (11, 'ORD-11', 'PAID', 1), (12, 'ORD-12', 'NEW', 1)");
	}
}
