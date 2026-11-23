package com.example.hibernatelab.g5_fetch_n_plus_one;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.hibernate.engine.spi.SessionImplementor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy.F1Customer;
import com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy.F1CustomerRepository;
import com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy.F1OrderSummary;
import com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy.F1OrderView;
import com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy.F1PurchaseOrderRepository;
import com.example.hibernatelab.g5_fetch_n_plus_one.f3_batch_size.F3Customer;
import com.example.hibernatelab.g5_fetch_n_plus_one.f3_batch_size.F3CustomerRepository;
import com.example.hibernatelab.g5_fetch_n_plus_one.f4_subselect.F4Customer;
import com.example.hibernatelab.g5_fetch_n_plus_one.f4_subselect.F4CustomerRepository;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G5, các cách SỬA N+1: S06 ({@code JOIN FETCH}), S07 ({@code @EntityGraph}), S08 ({@code @BatchSize}),
 * S09 ({@code SUBSELECT}), S15 (DTO projection).
 *
 * <p>Cùng một bài toán ở G5-S05 (lặp {@code getOrders()} của 5 customer = 1 + 5 SELECT), mỗi cách sửa đổi số SELECT một kiểu:
 * JOIN FETCH và EntityGraph gộp thành 1, BatchSize và SUBSELECT gộp thành 2, DTO không tải entity nào.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G5NPlusOneFixesTest extends AbstractScenarioTest {

	private static final String F1 = "g5_f1_all_lazy";
	private static final String F3 = "g5_f3_batch_size";
	private static final String F4 = "g5_f4_subselect";

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private F1CustomerRepository f1CustomerRepository;

	@Autowired
	private F1PurchaseOrderRepository f1OrderRepository;

	@Autowired
	private F3CustomerRepository f3CustomerRepository;

	@Autowired
	private F4CustomerRepository f4CustomerRepository;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { F1, F3, F4 };
	}

	/**
	 * G5-S06 — F1: sửa N+1 bằng {@code JOIN FETCH} 🔬
	 *
	 * <p>Mapping: {@code Customer.orders} LAZY (INVERSE side); sửa nằm ở câu JPQL, không phải ở mapping.
	 * <p><b>Câu hỏi</b>: {@code select c from Customer c join fetch c.orders} chạy mấy SELECT, và trả về 5 hay 10 customer
	 * (SQL join ra 10 dòng, mỗi customer 2 dòng)?
	 * <p><b>Kết luận</b>: 1 SELECT, orders đã khởi tạo nên lặp không tốn thêm SQL. Danh sách có 5 customer: từ Hibernate 6,
	 * kết quả của {@code join fetch} tự loại trùng nên KHÔNG cần {@code distinct} (Hibernate 5 trở xuống thì cần).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S06 | F1: JPQL join fetch c.orders → 1 SELECT và 5 customer (Hibernate 6+ tự loại trùng)")
	void s06_joinFetchLoadsCollectionInOneSelectWithoutDistinct() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F1);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			List<F1Customer> customers = f1CustomerRepository.findAllWithOrdersJoinFetch();
			int totalOrders = 0;
			for (F1Customer customer : customers) {
				totalOrders += customer.getOrders().size(); // đã khởi tạo bởi join fetch: không SQL
			}
			// [LIFECYCLE] customer MANAGED, orders đã có sẵn
			assertState(em, customers.get(0), MANAGED);
			assertThat(customers).as("SQL join ra 10 dòng nhưng Hibernate 6+ loại trùng → 5 customer").hasSize(G5Fixtures.CUSTOMER_COUNT);
			assertThat(totalOrders).as("mỗi customer vẫn đủ 2 order").isEqualTo(G5Fixtures.ORDER_COUNT);
			// [FLUSH] không có thay đổi
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);
		sql.assertSomeStatementContains("join");

		// ── THEN: DB thật ──
		db.print(F1 + ".customer");
		assertThat(db.countOf(F1 + ".customer")).as("DB có 5 customer").isEqualTo(G5Fixtures.CUSTOMER_COUNT);
	}

	/**
	 * G5-S07 — F1: sửa N+1 bằng {@code @EntityGraph(attributePaths = "orders")}
	 *
	 * <p>Mapping: như S06; sửa nằm ở annotation trên method của repository.
	 * <p><b>Câu hỏi</b>: {@code @EntityGraph} có cho kết quả giống {@code join fetch} không?
	 * <p><b>Kết luận</b>: có. 1 SELECT có join, 5 customer, orders đã khởi tạo. Khác biệt chỉ ở chỗ viết: câu JPQL giữ nguyên
	 * {@code select c from Customer c}, còn "tải kèm gì" khai báo bằng annotation nên dùng lại được cho derived query ({@code findByName…}).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S07 | F1: @EntityGraph(attributePaths = \"orders\") → 1 SELECT, kết quả như join fetch")
	void s07_entityGraphLoadsCollectionInOneSelect() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F1);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			List<F1Customer> customers = f1CustomerRepository.findAllWithOrdersEntityGraph();
			int totalOrders = 0;
			for (F1Customer customer : customers) {
				totalOrders += customer.getOrders().size(); // đã khởi tạo bởi entity graph: không SQL
			}
			// [LIFECYCLE] customer MANAGED, orders đã có sẵn
			assertState(em, customers.get(0), MANAGED);
			assertThat(customers).as("kết quả loại trùng như join fetch → 5 customer").hasSize(G5Fixtures.CUSTOMER_COUNT);
			assertThat(totalOrders).as("mỗi customer đủ 2 order").isEqualTo(G5Fixtures.ORDER_COUNT);
			// [FLUSH] không có thay đổi
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);
		sql.assertSomeStatementContains("join");

		// ── THEN: DB thật ──
		db.print(F1 + ".purchase_order");
		assertThat(db.countOf(F1 + ".purchase_order")).as("chỉ đọc nên seed nguyên vẹn").isEqualTo(G5Fixtures.ORDER_COUNT);
	}

	/**
	 * G5-S08 — F3: {@code @BatchSize(size = 10)} 🔬
	 *
	 * <p>Mapping: {@code Customer.orders} LAZY + {@code @BatchSize(size = 10)}.
	 * <p><b>Câu hỏi</b>: cùng vòng lặp như G5-S05 nhưng có {@code @BatchSize}, còn bao nhiêu SELECT?
	 * <p><b>Kết luận</b>: 1 + ⌈5/10⌉ = 2 SELECT. Khi chạm collection của customer đầu tiên, Hibernate nhặt luôn tối đa 10 customer
	 * chưa tải orders trong persistence context và tải orders của TẤT CẢ trong một câu. Câu thứ hai lọc theo danh sách id customer
	 * (🔬 trên Postgres, Hibernate 7 dùng {@code customer_id = any (?)} với MỘT tham số kiểu mảng, không phải {@code in (?, ?, …)}).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S08 | F3: @BatchSize(10) → findAll rồi lặp orders chỉ còn 2 SELECT")
	void s08_batchSizeGroupsLazyCollectionLoads() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F3);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			List<F3Customer> customers = f3CustomerRepository.findAll();
			int totalOrders = 0;
			for (F3Customer customer : customers) {
				totalOrders += customer.getOrders().size(); // chạm lần đầu: tải orders của cả lô 5 customer
			}
			// [LIFECYCLE] customer MANAGED
			assertState(em, customers.get(0), MANAGED);
			assertThat(totalOrders).as("5 customer × 2 order").isEqualTo(G5Fixtures.ORDER_COUNT);
			// [FLUSH] không có thay đổi
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("customer", 1);
		sql.assertSelects("purchase_order", 1);
		sql.assertTotal(2);
		sql.assertSomeStatementContains("customer_id = any (?)"); // Postgres: truyền cả mảng id thay vì "in (?, ?, …)"

		// ── THEN: DB thật ──
		db.print(F3 + ".purchase_order");
		assertThat(db.countOf(F3 + ".purchase_order")).as("chỉ đọc nên seed nguyên vẹn").isEqualTo(G5Fixtures.ORDER_COUNT);
	}

	/**
	 * G5-S09 — F4: {@code @Fetch(FetchMode.SUBSELECT)}
	 *
	 * <p>Mapping: {@code Customer.orders} LAZY + {@code @Fetch(SUBSELECT)}.
	 * <p><b>Câu hỏi</b>: SUBSELECT gom N+1 thành mấy SELECT, và câu thứ hai trông thế nào?
	 * <p><b>Kết luận</b>: 2 SELECT. Lần chạm collection đầu tiên, Hibernate tải orders của MỌI customer mà query gốc đã trả về,
	 * bằng câu {@code select … from purchase_order where customer_id in (select id from customer)}: lặp lại query gốc dưới dạng subquery.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S09 | F4: @Fetch(SUBSELECT) → 2 SELECT, câu thứ hai lặp lại query gốc làm subquery")
	void s09_subselectLoadsAllCollectionsWithOneSubquery() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F4);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			List<F4Customer> customers = f4CustomerRepository.findAll();
			int totalOrders = 0;
			for (F4Customer customer : customers) {
				totalOrders += customer.getOrders().size();
			}
			// [LIFECYCLE] customer MANAGED
			assertState(em, customers.get(0), MANAGED);
			assertThat(totalOrders).as("5 customer × 2 order").isEqualTo(G5Fixtures.ORDER_COUNT);
			// [FLUSH] không có thay đổi
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("purchase_order", 1);
		sql.assertSelects("customer", 2); // câu gốc, và câu thứ hai chứa subquery "from customer"
		sql.assertTotal(2);
		sql.assertSomeStatementContains("customer_id in (select"); // subquery lặp lại query gốc

		// ── THEN: DB thật ──
		db.print(F4 + ".purchase_order");
		assertThat(db.countOf(F4 + ".purchase_order")).as("chỉ đọc nên seed nguyên vẹn").isEqualTo(G5Fixtures.ORDER_COUNT);
	}

	/**
	 * G5-S15 — F1: DTO projection (record và interface)
	 *
	 * <p>Mapping: giữ nguyên F1 (LAZY); DTO không phụ thuộc mapping fetch.
	 * <p><b>Câu hỏi</b>: chỉ cần tên đơn hàng và tên khách thì sao phải tải cả entity? DTO tốn bao nhiêu SQL và có nằm trong persistence context không?
	 * <p><b>Kết luận</b>: mỗi kiểu 1 SELECT chỉ gồm đúng các cột cần ({@code order_no}, {@code name}), không có cột nào khác. Kết quả là record/proxy
	 * của Spring Data, KHÔNG phải entity nên persistence context có 0 entity: không dirty checking, không lazy loading, không N+1.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S15 | F1: DTO projection (record, interface) → 1 SELECT đúng cột cần, 0 entity trong PC")
	void s15_dtoProjectionSelectsOnlyNeededColumnsAndLoadsNoEntity() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F1);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			List<F1OrderSummary> summaries = f1OrderRepository.findAllSummaries();
			List<F1OrderView> views = f1OrderRepository.findAllViews();
			// [LIFECYCLE] DTO không có vòng đời; persistence context không giữ entity nào
			int managedEntities = em.unwrap(SessionImplementor.class).getPersistenceContextInternal().getNumberOfManagedEntities();
			assertThat(managedEntities).as("DTO không phải entity nên không được đưa vào persistence context").isZero();
			assertThat(summaries).as("1 dòng cho mỗi order").hasSize(G5Fixtures.ORDER_COUNT);
			assertThat(summaries.get(0).orderNo()).as("order nhỏ nhất theo id").isEqualTo("ORD-11");
			assertThat(summaries.get(0).customerName()).as("order 11 thuộc Customer-1").isEqualTo("Customer-1");
			assertThat(views).as("interface projection cũng 1 dòng cho mỗi order").hasSize(G5Fixtures.ORDER_COUNT);
			assertThat(views.get(0).getCustomerName()).as("interface projection đọc theo alias customerName").isEqualTo("Customer-1");
			// [FLUSH] không có entity nào nên không có gì để flush
		});

		// ── THEN: SQL ── (mỗi projection 1 SELECT; không có SELECT nào khác)
		sql.print();
		sql.assertTotal(2);
		sql.assertSelects("purchase_order", 2);
		sql.assertNoStatementContains("fpo1_0.id,"); // cột id của order KHÔNG nằm trong danh sách select (chỉ có trong order by)

		// ── THEN: DB thật ──
		db.print(F1 + ".purchase_order");
		assertThat(db.countOf(F1 + ".purchase_order")).as("chỉ đọc nên seed nguyên vẹn").isEqualTo(G5Fixtures.ORDER_COUNT);
	}
}
