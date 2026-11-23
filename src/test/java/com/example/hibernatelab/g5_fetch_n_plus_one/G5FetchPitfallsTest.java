package com.example.hibernatelab.g5_fetch_n_plus_one;

import static com.example.hibernatelab.support.LifecycleInspector.State.DETACHED;
import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;
import org.hibernate.loader.MultipleBagFetchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy.F1Customer;
import com.example.hibernatelab.g5_fetch_n_plus_one.f1s_all_lazy_set.F1SCustomer;
import com.example.hibernatelab.g5_fetch_n_plus_one.f2_eager_collection.F2Customer;
import com.example.hibernatelab.g5_fetch_n_plus_one.f2_eager_collection.F2CustomerRepository;
import com.example.hibernatelab.support.AbstractScenarioTest;
import com.example.hibernatelab.support.SqlCaptor;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Nhóm G5, các CẠM BẪY của fetch: S10 ({@code MultipleBagFetchException}), S11 (tích Descartes với {@code Set}),
 * S12 (phân trang + {@code join fetch} collection), S13 ({@code @OneToMany} EAGER).
 *
 * <p>Điểm chung: cả bốn đều "chạy được" hoặc "có cách vòng" nhưng trả giá bằng hiệu năng hoặc bộ nhớ,
 * nên phải hiểu SQL thật sự được sinh ra thế nào chứ không tin vào kết quả trông đúng.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G5FetchPitfallsTest extends AbstractScenarioTest {

	private static final String F1 = "g5_f1_all_lazy";
	private static final String F1S = "g5_f1s_all_lazy_set";
	private static final String F2 = "g5_f2_eager_collection";

	@Autowired
	private JdbcTemplate jdbc;

	/** Bean ghi SQL: ở S11 cần lấy NGUYÊN VĂN câu SQL Hibernate đã chạy để chạy lại bằng JDBC và đếm dòng. */
	@Autowired
	private SqlCaptor sqlCaptor;

	@Autowired
	private F2CustomerRepository f2CustomerRepository;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { F1, F1S, F2 };
	}

	/**
	 * G5-S10a — F1: {@code MultipleBagFetchException}
	 *
	 * <p>Mapping: {@code Customer.orders} và {@code Customer.payments} đều là {@code List} (Hibernate gọi là "bag": không thứ tự, cho phép trùng).
	 * <p><b>Câu hỏi</b>: {@code join fetch c.orders join fetch c.payments} (hai {@code List}) có chạy không?
	 * <p><b>Kết luận</b>: không. Hibernate ném {@link MultipleBagFetchException} lúc CHẠY query ({@code getResultList()}), TRƯỚC khi có SQL nào
	 * gửi xuống DB (🔬 {@code createQuery} vẫn tạo được; lỗi nổi lên khi Hibernate dựng câu SQL). Lý do: join hai bag cùng lúc cho ra các dòng
	 * trùng lặp mà Hibernate không thể phân biệt dòng trùng "thật" với dòng trùng do tích Descartes. Exception bị bọc bên ngoài.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S10a | F1: join fetch hai List cùng lúc → MultipleBagFetchException khi chạy query, 0 SQL")
	void s10a_fetchingTwoBagsInOneQueryThrows() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F1);

		// ── WHEN + THEN: chạy query thì ném exception (createQuery chưa ném), và chưa có SQL nào được gửi ──
		sql.reset();
		inTx(() -> assertThatThrownBy(() -> em.createQuery(
				"select c from G5F1_Customer c join fetch c.orders join fetch c.payments", F1Customer.class).getResultList())
			.as("hai bag trong cùng một câu join fetch bị Hibernate từ chối khi dựng SQL, trước lúc gửi xuống DB")
			.hasRootCauseInstanceOf(MultipleBagFetchException.class));

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(0);

		// ── THEN: DB thật ──
		db.print(F1 + ".payment");
		assertThat(db.countOf(F1 + ".payment")).as("không có gì bị ghi, seed nguyên vẹn").isEqualTo(G5Fixtures.PAYMENT_COUNT);
	}

	/**
	 * G5-S10b — F1: cách sửa bằng 2 query nối tiếp trong cùng transaction
	 *
	 * <p>Mapping: như S10a (vẫn hai {@code List}).
	 * <p><b>Câu hỏi</b>: nếu vẫn muốn có cả orders và payments mà không đổi sang {@code Set} thì làm thế nào?
	 * <p><b>Kết luận</b>: chạy 2 query, mỗi query {@code join fetch} MỘT collection, trong cùng transaction. Cả hai trả về cùng các
	 * customer MANAGED trong persistence context nên query thứ hai chỉ "điền thêm" collection còn thiếu vào các entity đã có.
	 * Tổng 2 SELECT, không còn tích Descartes, và đọc orders/payments sau đó không tốn SQL.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S10b | F1: sửa bằng 2 query nối tiếp (mỗi query fetch 1 List) → 2 SELECT, PC tự ghép")
	void s10b_twoSequentialQueriesFixMultipleBagFetch() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F1);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			em.createQuery("select c from G5F1_Customer c left join fetch c.orders", F1Customer.class).getResultList();
			List<F1Customer> customers = em
				.createQuery("select c from G5F1_Customer c left join fetch c.payments", F1Customer.class)
				.getResultList();
			F1Customer customerWithPayments = customers.get(0);
			// [LIFECYCLE] cùng một instance MANAGED, cả hai collection đã khởi tạo
			assertState(em, customerWithPayments, MANAGED);
			assertThat(Hibernate.isInitialized(customerWithPayments.getOrders())).as("orders do query 1 điền vào").isTrue();
			assertThat(Hibernate.isInitialized(customerWithPayments.getPayments())).as("payments do query 2 điền vào").isTrue();
			assertThat(customers).as("5 customer, không bị nhân bản").hasSize(G5Fixtures.CUSTOMER_COUNT);
			// [FLUSH] không có thay đổi
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(2);

		// ── THEN: DB thật ──
		db.print(F1 + ".payment");
		assertThat(db.countOf(F1 + ".payment")).as("chỉ đọc nên seed nguyên vẹn").isEqualTo(G5Fixtures.PAYMENT_COUNT);
	}

	/**
	 * G5-S11 — F1S: fetch hai {@code Set} chạy được nhưng sinh tích Descartes
	 *
	 * <p>Mapping: {@code orders} và {@code payments} là {@code Set} (không phải bag nên không bị S10a).
	 * <p><b>Câu hỏi</b>: đổi sang {@code Set} thì {@code left join fetch} cả hai có "ổn" không?
	 * <p><b>Kết luận</b>: chạy được, 1 SELECT, kết quả đúng (5 customer, Customer-1 có 2 order và 3 payment). Nhưng SQL phải trả về
	 * 2 × 3 = 6 dòng cho Customer-1 (mỗi cặp order–payment một dòng), thay vì 2 + 3 = 5 nếu tải riêng. Càng nhiều phần tử, số dòng
	 * nhân lên (nhân, không cộng). Chạy được không có nghĩa là rẻ: chứng minh bằng cách chạy lại ĐÚNG câu SQL đó qua JDBC rồi đếm dòng.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S11 | F1S: left join fetch hai Set chạy được nhưng SQL trả 2×3 = 6 dòng cho Customer-1 (tích Descartes)")
	void s11_fetchingTwoSetsWorksButMultipliesRows() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F1S);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			List<F1SCustomer> customers = em
				.createQuery("select c from G5F1S_Customer c left join fetch c.orders left join fetch c.payments", F1SCustomer.class)
				.getResultList();
			F1SCustomer customerWithPayments = findById(customers, G5Fixtures.customerWithPaymentsId());
			// [LIFECYCLE] kết quả đúng: không bị nhân bản ở phía Java, vì Hibernate đã gộp lại theo id
			assertState(em, customerWithPayments, MANAGED);
			assertThat(customers).as("Hibernate gộp các dòng trùng nên vẫn 5 customer").hasSize(G5Fixtures.CUSTOMER_COUNT);
			assertThat(customerWithPayments.getOrders()).as("Customer-1 có 2 order").hasSize(2);
			assertThat(customerWithPayments.getPayments()).as("Customer-1 có 3 payment").hasSize(G5Fixtures.PAYMENT_COUNT);
			// [FLUSH] không có thay đổi
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);
		String joinSql = sqlCaptor.statements().get(0);

		// ── THEN: DB thật: chạy lại đúng câu SQL đó bằng JDBC và đếm dòng theo customer (cột đầu tiên là customer.id) ──
		List<Long> customerIdOfEachRow = jdbc.query(joinSql, (rs, rowNumber) -> rs.getLong(1));
		int rowsOfCustomerWithPayments = 0;
		for (Long customerId : customerIdOfEachRow) {
			if (customerId == G5Fixtures.customerWithPaymentsId()) {
				rowsOfCustomerWithPayments++;
			}
		}
		System.out.println("[DB] câu SQL trả " + customerIdOfEachRow.size() + " dòng, trong đó Customer-1 chiếm " + rowsOfCustomerWithPayments);
		assertThat(rowsOfCustomerWithPayments).as("Customer-1: 2 order × 3 payment = 6 dòng (tích Descartes), không phải 2 + 3 = 5").isEqualTo(6);
		assertThat(customerIdOfEachRow).as("6 dòng của Customer-1 + 4 customer × (2 order × 1 dòng payment NULL) = 14 dòng cho chỉ 5 customer").hasSize(14);
	}

	/**
	 * G5-S12a — F1: phân trang + {@code join fetch} collection 🔬
	 *
	 * <p>Mapping: {@code Customer.orders} LAZY, tải bằng {@code join fetch}.
	 * <p><b>Câu hỏi</b>: {@code join fetch c.orders} kèm {@code setMaxResults(2)}: Hibernate cắt trang ở DB hay trong bộ nhớ (cảnh báo HHH90003004)?
	 * <p><b>Kết luận</b>: 🔬 KHÁC kinh điển. Hibernate 5/6 tải TOÀN BỘ các dòng join rồi cắt trang trong bộ nhớ và ghi cảnh báo HHH90003004.
	 * Hibernate 7.4 trên Postgres thì KHÔNG: SQL bọc customer vào subquery có {@code offset ? rows fetch first ? rows only} rồi mới join orders,
	 * nên DB cắt trang đúng ở bảng customer (không còn cảnh báo), và mỗi customer vẫn đủ 2 order (collection không bị cắt cụt).
	 * Lưu ý: vì là {@code join fetch} (inner join) nên subquery chỉ chọn customer CÓ order ({@code where exists(…)}).
	 * Setting {@code hibernate.query.fail_on_pagination_over_collection_fetch} không cần bật ở đây.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S12a | F1: join fetch collection + setMaxResults(2) → Hibernate 7 cắt trang ở DB bằng subquery, không cảnh báo")
	void s12a_paginationWithCollectionFetchIsDoneInTheDatabaseInHibernate7() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F1);
		Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		ListAppender<ILoggingEvent> logCapture = new ListAppender<>();
		logCapture.start();
		rootLogger.addAppender(logCapture); // bắt log để chứng minh cảnh báo HHH90003004 KHÔNG xuất hiện

		// ── WHEN ──
		sql.reset();
		List<F1Customer> firstPage = inTxReturning(() -> {
			List<F1Customer> page = em
				.createQuery("select c from G5F1_Customer c join fetch c.orders order by c.id", F1Customer.class)
				.setMaxResults(2)
				.getResultList();
			// [LIFECYCLE] 2 customer MANAGED, orders đã khởi tạo
			assertState(em, page.get(0), MANAGED);
			// [FLUSH] không có thay đổi
			return page;
		});
		rootLogger.detachAppender(logCapture);

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);
		sql.assertSomeStatementContains("fetch first"); // LIMIT chạy ở DB (Postgres: "offset ? rows fetch first ? rows only")
		assertThat(hasWarning(logCapture, "HHH90003004"))
			.as("Hibernate 7 cắt trang ở DB nên KHÔNG còn cảnh báo 'phân trang trong bộ nhớ' HHH90003004")
			.isFalse();
		assertState(em, firstPage.get(0), DETACHED);
		assertThat(firstPage).as("đúng 2 customer của trang đầu").hasSize(2);
		assertThat(firstPage.get(0).getOrders()).as("mỗi customer vẫn đủ 2 order, collection không bị cắt cụt").hasSize(2);
		assertThat(firstPage.get(1).getOrders()).as("customer thứ hai cũng đủ 2 order").hasSize(2);

		// ── THEN: DB thật ──
		db.print(F1 + ".purchase_order");
		assertThat(db.countOf(F1 + ".purchase_order")).as("chỉ đọc nên seed nguyên vẹn").isEqualTo(G5Fixtures.ORDER_COUNT);
	}

	/**
	 * G5-S12b — F1: cách sửa kinh điển bằng 2 query (lấy id của trang, rồi fetch theo id)
	 *
	 * <p>Mapping: như S12a.
	 * <p><b>Câu hỏi</b>: cách vòng kinh điển (dùng được ở mọi version Hibernate) vẫn đúng không, và SQL thật sự có LIMIT?
	 * <p><b>Kết luận</b>: query 1 chỉ lấy id của trang (có LIMIT, không join collection nên phân trang đúng ở DB);
	 * query 2 {@code join fetch … where c.id in :ids} lấy đúng các customer đó kèm orders. Tổng 2 SELECT, không kéo dư dòng.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S12b | F1: cách kinh điển 2 query (page id rồi join fetch where id in) → 2 SELECT, query 1 có LIMIT")
	void s12b_paginateIdsFirstThenFetchByIds() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F1);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			List<Long> pageIds = em
				.createQuery("select c.id from G5F1_Customer c order by c.id", Long.class)
				.setMaxResults(2)
				.getResultList();
			List<F1Customer> firstPage = em
				.createQuery("select c from G5F1_Customer c join fetch c.orders where c.id in :ids order by c.id", F1Customer.class)
				.setParameter("ids", pageIds)
				.getResultList();
			// [LIFECYCLE] 2 customer MANAGED, orders đã khởi tạo
			assertState(em, firstPage.get(0), MANAGED);
			assertThat(firstPage).as("đúng 2 customer của trang đầu").hasSize(2);
			assertThat(firstPage.get(0).getOrders()).as("mỗi customer đủ 2 order").hasSize(2);
			// [FLUSH] không có thay đổi
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(2);
		sql.assertSomeStatementContains("fetch first"); // query 1: LIMIT chạy ở DB (Postgres dùng "fetch first ? rows only")

		// ── THEN: DB thật ──
		db.print(F1 + ".customer");
		assertThat(db.countOf(F1 + ".customer")).as("chỉ đọc nên seed nguyên vẹn").isEqualTo(G5Fixtures.CUSTOMER_COUNT);
	}

	/**
	 * G5-S13a — F2: {@code find(customer)} với {@code orders} EAGER
	 *
	 * <p>Mapping: {@code Customer.orders} INVERSE side, {@code fetch = EAGER} (anti-pattern).
	 * <p><b>Câu hỏi</b>: chỉ cần tên customer, nhưng orders có bị tải không?
	 * <p><b>Kết luận</b>: có, luôn luôn. {@code find()} nối {@code left join purchase_order} trong cùng 1 SELECT, orders đã khởi tạo
	 * dù ta chưa hề đụng tới, và dùng được cả sau khi customer DETACHED. Nơi nào cũng bị "tặng kèm" orders, kể cả nơi không cần.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S13a | F2: find(customer) với orders EAGER → luôn tải orders (1 SELECT có left join)")
	void s13a_eagerCollectionIsAlwaysLoadedByFind() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F2);
		Long customerId = G5Fixtures.customerWithPaymentsId();

		// ── WHEN ──
		sql.reset();
		F2Customer customerAn = inTxReturning(() -> {
			F2Customer found = em.find(F2Customer.class, customerId);
			// [LIFECYCLE] MANAGED, orders (EAGER) đã khởi tạo mà ta chưa đụng tới
			assertState(em, found, MANAGED);
			assertThat(Hibernate.isInitialized(found.getOrders())).as("EAGER: orders được tải cùng customer").isTrue();
			// [FLUSH] không có thay đổi
			return found;
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);
		sql.assertSomeStatementContains("left join");
		assertState(em, customerAn, DETACHED);
		assertThat(customerAn.getOrders()).as("dùng được sau khi D vì đã tải sẵn").hasSize(2);

		// ── THEN: DB thật ──
		db.print(F2 + ".purchase_order");
		assertThat(db.countOf(F2 + ".purchase_order")).as("chỉ đọc nên seed nguyên vẹn").isEqualTo(G5Fixtures.ORDER_COUNT);
	}

	/**
	 * G5-S13b — F2: {@code findAll()} với {@code orders} EAGER sinh N+1 tự động
	 *
	 * <p>Mapping: như S13a.
	 * <p><b>Câu hỏi</b>: {@code findAll()} (JPQL) không đụng tới orders, vậy có N+1 không?
	 * <p><b>Kết luận</b>: có, dù code không hề lặp {@code getOrders()}. JPQL không tự JOIN (giống G5-S02), nên sau 1 SELECT customer
	 * Hibernate chạy thêm 1 SELECT purchase_order cho MỖI customer chỉ vì orders là EAGER. LAZY (F1) thì không tốn gì khi ta không dùng orders.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S13b | F2: findAll customers với orders EAGER → 1 + 5 SELECT dù không lặp gì cả")
	void s13b_eagerCollectionMakesFindAllCauseNPlusOne() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F2);

		// ── WHEN: chỉ findAll, không đụng tới orders ──
		sql.reset();
		inTx(() -> {
			List<F2Customer> customers = f2CustomerRepository.findAll();
			// [LIFECYCLE] customer MANAGED, orders đã được tải ngầm cho từng người
			assertState(em, customers.get(0), MANAGED);
			assertThat(Hibernate.isInitialized(customers.get(0).getOrders())).as("EAGER: đã tải dù ta chưa dùng").isTrue();
			// [FLUSH] không có thay đổi
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("customer", 1);
		sql.assertSelects("purchase_order", G5Fixtures.CUSTOMER_COUNT);
		sql.assertTotal(1 + G5Fixtures.CUSTOMER_COUNT);

		// ── THEN: DB thật ──
		db.print(F2 + ".customer");
		assertThat(db.countOf(F2 + ".customer")).as("chỉ đọc nên seed nguyên vẹn").isEqualTo(G5Fixtures.CUSTOMER_COUNT);
	}

	/**
	 * G5-S13c — F2: {@code @EntityGraph} có "tắt" được EAGER không? 🔬
	 *
	 * <p>Mapping: như S13a; graph chỉ nhắc tới {@code payments}, không nhắc tới {@code orders}.
	 * <p><b>Câu hỏi</b>: nếu graph không liệt kê {@code orders} thì {@code orders} (EAGER theo mapping) còn được tải không?
	 * <p><b>Kết luận</b>: 🔬 KHÁC giả thuyết (giả thuyết ban đầu: không tắt được). Thực tế: 1 SELECT chỉ có {@code left join payment},
	 * {@code orders} KHÔNG được tải (chưa khởi tạo), tức là {@code @EntityGraph} đã biến orders từ EAGER thành "không tải". Lý do hợp lý nhất:
	 * Spring Data dùng ngữ nghĩa {@code fetchgraph} theo mặc định, nghĩa là attribute nào không có trong graph thì bị coi là LAZY.
	 * Bài học: graph là cách ghi đè mapping EAGER theo từng query, nhưng đừng coi đó là lý do để giữ EAGER trong mapping.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G5-S13c | F2: @EntityGraph(payments) làm orders (EAGER) không được tải → 1 SELECT")
	void s13c_entityGraphOverridesEagerOfAttributesNotInTheGraph() {
		// ── GIVEN ──
		G5Fixtures.seed(jdbc, F2);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			List<F2Customer> customers = f2CustomerRepository.findAllWithPaymentsEntityGraph();
			// [LIFECYCLE] customer MANAGED
			assertState(em, customers.get(0), MANAGED);
			assertThat(Hibernate.isInitialized(customers.get(0).getPayments())).as("payments có trong graph nên đã tải").isTrue();
			assertThat(Hibernate.isInitialized(customers.get(0).getOrders()))
				.as("orders không có trong graph: fetchgraph coi nó là LAZY dù mapping ghi EAGER")
				.isFalse();
			// [FLUSH] không có thay đổi
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);
		sql.assertSelects("payment", 1);
		sql.assertSelects("purchase_order", 0);

		// ── THEN: DB thật ──
		db.print(F2 + ".payment");
		assertThat(db.countOf(F2 + ".payment")).as("chỉ đọc nên seed nguyên vẹn").isEqualTo(G5Fixtures.PAYMENT_COUNT);
	}

	// ── helper nhỏ ──

	/** Tìm customer theo id trong danh sách (vòng for tường minh cho dễ đọc). */
	private F1SCustomer findById(List<F1SCustomer> customers, long id) {
		for (F1SCustomer customer : customers) {
			if (customer.getId() == id) {
				return customer;
			}
		}
		throw new AssertionError("Không có customer id = " + id + " trong kết quả");
	}

	/** Có sự kiện log nào chứa mã cảnh báo này không. */
	private boolean hasWarning(ListAppender<ILoggingEvent> capture, String code) {
		List<String> messages = new ArrayList<>();
		for (ILoggingEvent event : capture.list) {
			messages.add(event.getFormattedMessage());
		}
		for (String message : messages) {
			if (message.contains(code)) {
				return true;
			}
		}
		return false;
	}
}
