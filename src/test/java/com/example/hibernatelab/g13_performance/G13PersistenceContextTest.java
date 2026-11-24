package com.example.hibernatelab.g13_performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;

import org.hibernate.LazyInitializationException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g13_performance.p1_pc_stateless.P1Customer;
import com.example.hibernatelab.g13_performance.p1_pc_stateless.P1Order;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G13a, chủ đề persistence context và {@code StatelessSession}: S04 (xử lý nhiều dòng), S05 (StatelessSession).
 *
 * <p>Model P1: {@code Customer} 1—N {@code PurchaseOrder}, quan hệ LAZY. Dữ liệu seed bằng JDBC (không qua Hibernate).
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G13PersistenceContextTest extends AbstractScenarioTest {

	private static final String P1 = "g13_p1_pc_stateless";
	private static final String CUSTOMER = P1 + ".customer";
	private static final String ORDER = P1 + ".purchase_order";
	private static final int ROWS = 2000;
	private static final int PAGE = 100;

	@Autowired
	private JdbcTemplate jdbc;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { P1 };
	}

	/** Seed 2000 customer trạng thái NEW bằng đúng 1 câu SQL (generate_series của Postgres). */
	private void seedCustomers() {
		jdbc.update("insert into " + CUSTOMER + " (id, name, status) select g, 'Customer-' || g, 'NEW' from generate_series(1, " + ROWS + ") g");
	}

	/**
	 * G13-S04a — P1: xử lý 2 000 dòng theo trang NHƯNG KHÔNG {@code clear()}
	 *
	 * <p>Mapping: P1. <b>Câu hỏi</b>: vòng lặp đọc từng trang 100 dòng, sửa trạng thái, không dọn gì: persistence context lớn thế nào?
	 * <p><b>Kết luận</b>: entity chỉ vào mà không ra, cuối vòng lặp PC giữ cả 2 000 entity (cùng bản snapshot cho dirty checking),
	 * và mỗi lần query sau đó auto-flush phải dirty-check lại toàn bộ chúng. Đếm bằng {@code SessionStatistics.getEntityCount()}, không đo bộ nhớ.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S04a | không clear(): persistence context giữ cả 2000 entity")
	void s04a_withoutClearPersistenceContextKeepsEverything() {
		// ── GIVEN ──
		seedCustomers();
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			Session session = em.unwrap(Session.class);
			for (int first = 0; first < ROWS; first += PAGE) {
				List<P1Customer> page = em.createQuery("select c from G13P1_Customer c order by c.id", P1Customer.class)
					.setFirstResult(first).setMaxResults(PAGE).getResultList(); // [LIFECYCLE] MANAGED
				for (P1Customer customer : page) {
					customer.setStatus("DONE"); // sửa tại chỗ, dirty checking sẽ phát hiện
				}
			}
			// ── THEN: persistence context ──
			assertThat(session.getStatistics().getEntityCount()).as("không clear nên PC giữ cả %d entity đã đọc", ROWS).isEqualTo(ROWS);
		}); // [TX END] flush + commit
		sql.assertUpdates("customer", ROWS);

		// ── THEN: DB thật ──
		assertThat(jdbc.queryForObject("select count(*) from " + CUSTOMER + " where status = 'DONE'", Long.class))
			.as("cả 2000 dòng đã được cập nhật").isEqualTo((long) ROWS);
	}

	/**
	 * G13-S04b — P1: cùng vòng lặp nhưng {@code flush()} + {@code clear()} sau mỗi trang
	 *
	 * <p>Mapping: P1. <b>Câu hỏi</b>: dọn theo lô thì PC lớn nhất là bao nhiêu, kết quả DB có khác không?
	 * <p><b>Kết luận</b>: PC không bao giờ vượt một trang (100 entity); số UPDATE và dữ liệu cuối giống hệt S04a. Phải {@code flush()} TRƯỚC
	 * {@code clear()}, nếu không các thay đổi chưa flush bị vứt bỏ im lặng.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S04b | flush() + clear() theo trang: PC không vượt 100 entity, kết quả DB y hệt")
	void s04b_flushAndClearPerPageKeepsPersistenceContextSmall() {
		// ── GIVEN ──
		seedCustomers();
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			Session session = em.unwrap(Session.class);
			int largestPersistenceContext = 0;
			for (int first = 0; first < ROWS; first += PAGE) {
				List<P1Customer> page = em.createQuery("select c from G13P1_Customer c order by c.id", P1Customer.class)
					.setFirstResult(first).setMaxResults(PAGE).getResultList(); // [LIFECYCLE] MANAGED
				for (P1Customer customer : page) {
					customer.setStatus("DONE");
				}
				largestPersistenceContext = Math.max(largestPersistenceContext, session.getStatistics().getEntityCount());
				em.flush(); // [FLUSH] đẩy UPDATE của trang này xuống DB TRƯỚC khi clear
				em.clear(); // [LIFECYCLE] cả trang MANAGED → DETACHED, PC rỗng
			}
			// ── THEN: persistence context ──
			assertThat(largestPersistenceContext).as("mỗi trang được clear nên PC lớn nhất chỉ bằng một trang").isEqualTo(PAGE);
		});
		sql.assertUpdates("customer", ROWS);

		// ── THEN: DB thật ──
		assertThat(jdbc.queryForObject("select count(*) from " + CUSTOMER + " where status = 'DONE'", Long.class))
			.as("kết quả dữ liệu giống hệt S04a: flush trước clear nên không mất cập nhật nào").isEqualTo((long) ROWS);
	}

	/**
	 * G13-S05a — P1 🔬: {@code StatelessSession} không có persistence context và không có dirty checking
	 *
	 * <p>Mapping: P1. <b>Câu hỏi</b>: mỗi lệnh có chạy SQL ngay không; sửa entity đã đọc mà không gọi gì thì có UPDATE không?
	 * <p><b>Kết luận</b>: {@code insert} chạy INSERT ngay (không chờ flush); đọc rồi sửa field KHÔNG sinh UPDATE (không dirty checking),
	 * phải gọi {@code update(entity)} tường minh, lúc đó UPDATE ghi đè mọi cột.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S05a | 🔬 StatelessSession: insert chạy ngay, sửa field không tự UPDATE, phải gọi update()")
	void s05a_statelessSessionRunsEachCommandImmediatelyAndHasNoDirtyChecking() {
		// ── GIVEN ──
		jdbc.update("insert into " + CUSTOMER + " (id, name, status) values (9001, 'Alice', 'NEW')"); // id lớn để không trùng id sequence cấp cho Bob
		sql.reset();

		// ── WHEN ──
		StatelessSession stateless = emf.unwrap(SessionFactory.class).openStatelessSession();
		try {
			Transaction tx = stateless.beginTransaction();
			stateless.insert(new P1Customer("Bob", "NEW"));
			sql.assertInserts("customer", 1); // INSERT chạy NGAY tại insert(), không có flush nào cả
			P1Customer alice = stateless.get(P1Customer.class, 9001L); // không có PC: không "MANAGED"
			alice.setStatus("DONE"); // sửa tại chỗ, không ai theo dõi
			sql.assertUpdates("customer", 0); // không dirty checking nên không có UPDATE
			tx.commit(); // [TX END] không có flush để đẩy thay đổi của alice
			sql.assertUpdates("customer", 0);
			tx.begin();
			stateless.update(alice); // tường minh: UPDATE ngay lập tức
			sql.assertUpdates("customer", 1);
			tx.commit();
		} finally {
			stateless.close();
		}
		sql.print();

		// ── THEN: DB thật ──
		assertThat(db.rowById(CUSTOMER, 9001L).get("status")).as("chỉ sau update() tường minh mới có DONE").isEqualTo("DONE");
		assertThat(db.countOf(CUSTOMER)).as("Alice + Bob").isEqualTo(2L);
	}

	/**
	 * G13-S05b — P1 🔬: {@code StatelessSession} không cascade
	 *
	 * <p>Mapping: P1 ({@code orders} có {@code cascade = PERSIST}). <b>Câu hỏi</b>: insert customer đang giữ 2 order thì order có được lưu không?
	 * <p><b>Kết luận</b>: không. Chỉ customer được INSERT, bảng order rỗng, và không có lỗi nào báo trước. Với StatelessSession mọi entity phải
	 * insert thủ công, theo đúng thứ tự cha trước con.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S05b | 🔬 StatelessSession bỏ qua cascade: order con không được lưu, không báo lỗi")
	void s05b_statelessSessionIgnoresCascade() {
		// ── GIVEN ──
		P1Customer customer = new P1Customer("Alice", "NEW");
		customer.addOrder(new P1Order("ORD-1"));
		customer.addOrder(new P1Order("ORD-2"));
		sql.reset();

		// ── WHEN ──
		StatelessSession stateless = emf.unwrap(SessionFactory.class).openStatelessSession();
		try {
			Transaction tx = stateless.beginTransaction();
			stateless.insert(customer); // cascade = PERSIST có trên mapping nhưng StatelessSession không biết tới cascade
			tx.commit(); // [TX END]
		} finally {
			stateless.close();
		}
		sql.print();

		// ── THEN: SQL ──
		sql.assertInserts("customer", 1);
		sql.assertInserts("purchase_order", 0);
		// ── THEN: DB thật ──
		assertThat(db.countOf(CUSTOMER)).as("customer được lưu").isEqualTo(1L);
		assertThat(db.countOf(ORDER)).as("StatelessSession không cascade nên 2 order không được lưu").isEqualTo(0L);
	}

	/**
	 * G13-S05c — P1 🔬: {@code StatelessSession} không có lazy loading
	 *
	 * <p>Mapping: P1 ({@code Order.customer} LAZY). <b>Câu hỏi</b>: chạm vào quan hệ LAZY của entity do StatelessSession đọc thì sao?
	 * <p><b>Kết luận</b>: {@code order.getCustomer()} là proxy chưa nạp; chạm vào nó ném {@code LazyInitializationException (… no session)}, không có SELECT nào.
	 * Muốn dùng quan hệ phải {@code join fetch} ngay trong query.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S05c | 🔬 StatelessSession không lazy loading: chạm quan hệ LAZY thì lỗi")
	void s05c_statelessSessionCannotLazyLoad() {
		// ── GIVEN ──
		jdbc.update("insert into " + CUSTOMER + " (id, name, status) values (1, 'Alice', 'NEW')");
		jdbc.update("insert into " + ORDER + " (id, order_no, customer_id) values (11, 'ORD-1', 1)");
		sql.reset();

		// ── WHEN ──
		StatelessSession stateless = emf.unwrap(SessionFactory.class).openStatelessSession();
		Throwable failure;
		try {
			P1Order order = stateless.get(P1Order.class, 11L);
			failure = catchThrowable(() -> order.getCustomer().getName()); // chạm vào proxy LAZY
		} finally {
			stateless.close();
		}
		sql.print();

		// ── THEN ──
		assertThat(failure).as("không có persistence context để nạp quan hệ LAZY").isInstanceOf(LazyInitializationException.class)
			.hasMessageContaining("no session");
		sql.assertSelects("customer", 0); // không SELECT customer nào được chạy: proxy chưa bao giờ được nạp
	}
}
