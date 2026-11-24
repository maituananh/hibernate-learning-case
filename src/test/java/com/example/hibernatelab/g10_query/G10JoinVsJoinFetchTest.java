package com.example.hibernatelab.g10_query;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g10_query.q1_baseline.Q1Customer;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G10, câu hỏi khởi đầu của giai đoạn mở rộng: {@code join} khác {@code join fetch} thế nào?
 * S01a ({@code join}), S01b ({@code join fetch}), S02 (bẫy {@code join fetch} + {@code where}), S03 (cách viết đúng).
 *
 * <p>Cùng một mapping (Q1, mọi quan hệ LAZY), cùng dữ liệu (3 customer × 2 order), chỉ khác MỘT TỪ KHOÁ trong câu JPQL.
 * Điểm cần nhớ: {@code join} chỉ dùng để <b>lọc/nối trong câu query</b>, còn {@code join fetch} mới <b>nạp dữ liệu vào entity</b>.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G10JoinVsJoinFetchTest extends AbstractScenarioTest {

	private static final String Q1 = "g10_q1_baseline";

	@Autowired
	private JdbcTemplate jdbc;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { Q1 };
	}

	/**
	 * G10-S01a — Q1: {@code join} thường KHÔNG nạp collection
	 *
	 * <p>Mapping: {@code Customer.orders} LAZY (INVERSE side, mappedBy = "customer"); sự khác biệt nằm ở câu JPQL.
	 * <p><b>Câu hỏi</b>: {@code select c from Customer c join c.orders o} chạy mấy SQL, có kéo cột của order về không,
	 * và {@code customer.getOrders()} lúc đó đã được khởi tạo chưa?
	 * <p><b>Kết luận</b>: 1 SELECT có {@code join} nhưng danh sách cột chỉ của customer. {@code orders} vẫn chưa khởi tạo (proxy
	 * collection rỗng chờ tải), nên lần đầu {@code getOrders().size()} sinh thêm 1 SELECT nữa. Alias {@code o} chỉ dùng được trong
	 * {@code where}/{@code on} của câu query, không làm đầy entity. Ngoài ra Hibernate 7 loại trùng cả root của {@code join}
	 * thường: 6 dòng join ra vẫn chỉ có 3 customer (khác Hibernate 5, nơi phải {@code distinct}).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G10-S01a | Q1: JPQL join (không fetch) → chỉ cột customer, orders chưa khởi tạo, chạm vào thì thêm 1 SELECT")
	void s01a_plainJoinDoesNotInitializeCollection() {
		// ── GIVEN ──
		G10Fixtures.seed(jdbc, Q1);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			List<Q1Customer> customers = em
				.createQuery("select c from G10Q1_Customer c join c.orders o order by c.id", Q1Customer.class)
				.getResultList();
			Q1Customer customerA = customers.get(0);
			// [LIFECYCLE] customer MANAGED, nhưng collection orders chỉ là "hứa sẽ tải": chưa có dữ liệu
			assertState(em, customerA, MANAGED);
			assertThat(Hibernate.isInitialized(customerA.getOrders())).as("join không nạp dữ liệu vào collection").isFalse();
			assertThat(customers).as("6 dòng join ra, nhưng Hibernate 6+ loại trùng root → 3 customer").hasSize(G10Fixtures.CUSTOMER_COUNT);

			// ── THEN: SQL (sau câu query, trước khi chạm vào orders) ──
			sql.assertTotal(1);
			sql.assertSomeStatementContains("join");
			sql.assertNoStatementContains("order_no"); // cột của order KHÔNG có trong danh sách select

			// Chạm vào collection: lúc này mới có SELECT thứ hai
			assertThat(customerA.getOrders()).as("đọc size() buộc Hibernate tải orders của customerA").hasSize(G10Fixtures.ORDERS_PER_CUSTOMER);
			// [FLUSH] không có thay đổi → không có UPDATE
		});
		sql.print();
		sql.assertTotal(2);
		sql.assertSelects("g10_q1_baseline.purchase_order", 2); // 1 trong câu join + 1 câu tải collection riêng

		// ── THEN: DB thật ──
		db.print(Q1 + ".purchase_order");
		assertThat(db.countOf(Q1 + ".purchase_order")).as("query chỉ đọc, DB không đổi: vẫn 6 order").isEqualTo(G10Fixtures.ORDER_COUNT);
	}

	/**
	 * G10-S01b — Q1: {@code join fetch} nạp collection ngay trong câu query
	 *
	 * <p>Mapping: như S01a; chỉ thêm từ khoá {@code fetch} vào câu JPQL.
	 * <p><b>Câu hỏi</b>: {@code select c from Customer c join fetch c.orders} khác {@code join} ở SQL và ở trạng thái collection thế nào?
	 * <p><b>Kết luận</b>: vẫn 1 SELECT với {@code join}, nhưng danh sách cột có thêm cột của order, và {@code orders} đã khởi tạo
	 * nên {@code getOrders().size()} không tốn thêm SQL. Giá phải trả: mỗi dòng kết quả mang cả cột customer lẫn cột order
	 * (customer bị lặp theo số order), và collection chứa <b>đúng những order mà câu join chọn ra</b> (xem S02).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G10-S01b | Q1: JPQL join fetch → có cột order trong select, orders đã khởi tạo, đọc thêm không tốn SQL")
	void s01b_joinFetchInitializesCollection() {
		// ── GIVEN ──
		G10Fixtures.seed(jdbc, Q1);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			List<Q1Customer> customers = em
				.createQuery("select c from G10Q1_Customer c join fetch c.orders order by c.id", Q1Customer.class)
				.getResultList();
			Q1Customer customerA = customers.get(0);
			// [LIFECYCLE] customer MANAGED, collection orders đã có dữ liệu ngay từ câu SELECT đầu tiên
			assertState(em, customerA, MANAGED);
			assertThat(Hibernate.isInitialized(customerA.getOrders())).as("join fetch nạp dữ liệu vào collection").isTrue();
			assertThat(customerA.getOrders()).as("đọc collection đã khởi tạo: không phát sinh SQL").hasSize(G10Fixtures.ORDERS_PER_CUSTOMER);
			// [FLUSH] không có thay đổi → không có UPDATE
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);
		sql.assertSomeStatementContains("join");
		sql.assertSomeStatementContains("order_no"); // cột của order CÓ trong danh sách select: đây là điểm khác S01a

		// ── THEN: DB thật ──
		db.print(Q1 + ".purchase_order");
		assertThat(db.countOf(Q1 + ".purchase_order")).as("query chỉ đọc, DB không đổi: vẫn 6 order").isEqualTo(G10Fixtures.ORDER_COUNT);
	}

	/**
	 * G10-S02 — Q1: {@code join fetch} + {@code where} trên phần tử collection làm collection bị THIẾU dữ liệu 🔬
	 *
	 * <p>Mapping: như S01. Customer-1 có 2 order: ORD-11 (PAID) và ORD-12 (NEW).
	 * <p><b>Câu hỏi</b>: {@code join fetch c.orders o where o.status = 'PAID'} làm {@code customer.getOrders()} chứa gì, và đọc lại
	 * customer đó lần nữa trong cùng persistence context thì thấy gì?
	 * <p><b>Kết luận</b>: collection chỉ chứa order PAID (1 phần tử) dù DB có 2. Vì collection đã khởi tạo, nó được coi là "đầy đủ":
	 * {@code em.find} lần sau trả cùng instance, không SQL, vẫn 1 phần tử. Đây là lỗi dữ liệu âm thầm: code phía sau tin rằng
	 * customer chỉ có 1 order. Nếu code đó sửa collection (xoá phần tử, orphanRemoval) thì còn nguy hiểm hơn.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G10-S02 | Q1: join fetch + where o.status → collection chỉ còn order PAID, đọc lại trong cùng PC vẫn thiếu")
	void s02_joinFetchWithWhereLeavesCollectionIncomplete() {
		// ── GIVEN ──
		G10Fixtures.seed(jdbc, Q1);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			Q1Customer customerA = em
				.createQuery("select c from G10Q1_Customer c join fetch c.orders o where o.status = 'PAID' order by c.id", Q1Customer.class)
				.getResultList().get(0);
			assertState(em, customerA, MANAGED);
			assertThat(customerA.getOrders()).as("where lọc bớt dòng join → collection chỉ nhận order PAID").hasSize(1);

			Q1Customer sameCustomer = em.find(Q1Customer.class, customerA.getId());
			// [LIFECYCLE] find trong cùng persistence context: trả về chính instance đã có (identity map), không SELECT
			assertThat(sameCustomer).as("cùng persistence context → cùng instance").isSameAs(customerA);
			assertThat(sameCustomer.getOrders()).as("collection đã khởi tạo nên không tải lại: vẫn thiếu order NEW").hasSize(1);
			// [FLUSH] không có thay đổi
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);
		sql.assertSomeStatementContains("status");

		// ── THEN: DB thật ──
		db.print(Q1 + ".purchase_order");
		assertThat(jdbc.queryForObject("select count(*) from " + Q1 + ".purchase_order where customer_id = 1", Long.class))
			.as("DB thật: Customer-1 có 2 order, collection trong bộ nhớ nói dối").isEqualTo(2L);
	}

	/**
	 * G10-S03 — Q1: lọc bằng {@code join} thường, collection tải riêng nên đầy đủ (cách viết đúng cho S02)
	 *
	 * <p>Mapping: như S01.
	 * <p><b>Câu hỏi</b>: muốn "customer có ít nhất một order PAID" và vẫn cần ĐỦ order của customer đó thì viết thế nào?
	 * <p><b>Kết luận</b>: dùng {@code join} (không fetch) để lọc: collection không bị đụng tới, lần chạm đầu tiên tải đủ 2 order
	 * bằng 1 SELECT riêng. Tổng 2 SELECT thay vì 1, đổi lấy dữ liệu đúng. Quy tắc: {@code join} để lọc, {@code join fetch}
	 * để nạp, và đừng đặt điều kiện {@code where} lên alias của {@code join fetch}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G10-S03 | Q1: join thường để lọc, collection tải riêng → đủ 2 order, tốn 2 SELECT")
	void s03_plainJoinToFilterThenLoadFullCollection() {
		// ── GIVEN ──
		G10Fixtures.seed(jdbc, Q1);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			Q1Customer customerA = em
				.createQuery("select c from G10Q1_Customer c join c.orders o where o.status = 'PAID' order by c.id", Q1Customer.class)
				.getResultList().get(0);
			assertThat(Hibernate.isInitialized(customerA.getOrders())).as("join thường không nạp collection").isFalse();
			assertThat(customerA.getOrders()).as("lần chạm đầu tải đủ cả order PAID lẫn NEW").hasSize(G10Fixtures.ORDERS_PER_CUSTOMER);
			// [FLUSH] không có thay đổi
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(2);

		// ── THEN: DB thật ──
		assertThat(jdbc.queryForObject("select count(*) from " + Q1 + ".purchase_order where customer_id = 1", Long.class))
			.as("DB có 2 order của Customer-1, và collection giờ khớp với DB").isEqualTo(2L);
	}
}
