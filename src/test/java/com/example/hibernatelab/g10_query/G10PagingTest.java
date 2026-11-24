package com.example.hibernatelab.g10_query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.hibernate.Session;
import org.hibernate.query.SemanticException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g10_query.q1_baseline.Q1Customer;
import com.example.hibernatelab.g10_query.q1_baseline.Q1CustomerRepository;
import com.example.hibernatelab.g10_query.q1_baseline.Q1CustomerSummary;
import com.example.hibernatelab.support.AbstractScenarioTest;

import jakarta.persistence.Tuple;

/**
 * Nhóm G10, phân trang và cách viết query động: S08 ({@code Page} + {@code join fetch}), S09 ({@code Specification}),
 * S10 (DTO projection).
 *
 * <p>Dữ liệu: 3 customer × 2 order. Trang đầu tiên cỡ 2 customer. Con số cần canh: tổng số customer phải là <b>3</b>,
 * nếu câu count đếm theo số dòng sau join thì ra <b>6</b>.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G10PagingTest extends AbstractScenarioTest {

	private static final String Q1 = "g10_q1_baseline";
	private static final String DTO_CLASS = "com.example.hibernatelab.g10_query.q1_baseline.Q1CustomerSummary";

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Q1CustomerRepository customerRepository;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { Q1 };
	}

	/**
	 * G10-S08a — Q1: {@code Page} + {@code join fetch} collection, để Spring Data TỰ SINH count query 🔬
	 *
	 * <p>Mapping: Q1; câu query {@code select c from Customer c join fetch c.orders} trả {@code Page<Customer>}.
	 * <p><b>Câu hỏi</b>: count query tự sinh có chạy được không, và {@code getTotalElements()} bằng bao nhiêu?
	 * <p><b>Kết luận</b>: chạy được và <b>không báo lỗi</b>, nhưng total = 6 thay vì 3: câu count đếm số dòng SAU khi join
	 * (mỗi order một dòng) chứ không đếm customer. Phân trang dữ liệu vẫn đúng (Hibernate 7.4 cắt trang ở DB bằng subquery,
	 * xem G5-S12a), nên chỉ có tổng số trang bị sai: UI sẽ hiện nhiều trang hơn thực tế. Đây là lỗi âm thầm, test mới bắt được.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G10-S08a | Page + join fetch collection, count tự sinh → total sai (6 thay vì 3), không báo lỗi")
	void s08a_autoCountQueryOverJoinFetchCountsJoinedRows() {
		// ── GIVEN ──
		G10Fixtures.seed(jdbc, Q1);

		// ── WHEN ──
		sql.reset();
		Page<Q1Customer> page = inTxReturning(() -> customerRepository.findPageWithOrdersAutoCount(PageRequest.of(0, 2)));

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(2); // 1 query dữ liệu + 1 query count
		sql.assertSomeStatementContains("offset"); // trang cắt ở DB
		sql.assertSomeStatementContains("count(*)");

		// ── THEN: DB thật ──
		assertThat(page.getContent()).as("trang 0 cỡ 2: đúng 2 customer").hasSize(2);
		assertThat(page.getTotalElements()).as("count tự sinh đếm dòng sau join (3 customer × 2 order = 6), không đếm customer").isEqualTo(6L);
		assertThat(db.countOf(Q1 + ".customer")).as("DB thật chỉ có 3 customer: total=6 là SAI").isEqualTo(3L);
	}

	/**
	 * G10-S08b — Q1: khai báo {@code countQuery} riêng cho {@code Page} + {@code join fetch}
	 *
	 * <p>Mapping: như S08a; thêm {@code countQuery = "select count(c) from Customer c"} (không fetch, không join).
	 * <p><b>Câu hỏi</b>: viết {@code countQuery} riêng có sửa được total không?
	 * <p><b>Kết luận</b>: có. total = 3, câu count không còn join nên rẻ hơn. Quy tắc: query {@code Page} có {@code join fetch}
	 * collection thì LUÔN khai báo {@code countQuery} (hoặc dùng cách 2 query của G5-S12b).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G10-S08b | Page + join fetch collection, countQuery tự viết → total đúng (3), count không join")
	void s08b_explicitCountQueryGivesCorrectTotal() {
		// ── GIVEN ──
		G10Fixtures.seed(jdbc, Q1);

		// ── WHEN ──
		sql.reset();
		Page<Q1Customer> page = inTxReturning(() -> customerRepository.findPageWithOrdersExplicitCount(PageRequest.of(0, 2)));

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(2);
		sql.assertSomeStatementContains("count(qc1_0.id) from g10_q1_baseline.customer qc1_0"); // câu count kết thúc ở bảng customer, không join

		// ── THEN: DB thật ──
		assertThat(page.getContent()).as("trang 0 cỡ 2: đúng 2 customer").hasSize(2);
		assertThat(page.getTotalElements()).as("countQuery đếm customer nên đúng 3").isEqualTo(db.countOf(Q1 + ".customer"));
	}

	/**
	 * G10-S09a — Q1: {@code Specification} cho bộ lọc động: SQL đổi theo điều kiện
	 *
	 * <p>Mapping: Q1; repository thêm {@code JpaSpecificationExecutor}.
	 * <p><b>Câu hỏi</b>: cùng một method {@code findAll(spec, page)}, SQL thay đổi ra sao khi có và không có điều kiện lọc?
	 * <p><b>Kết luận</b>: có điều kiện thì SQL có {@code where ... like}, không có thì không; câu count cũng đi theo. Specification
	 * không có fetch (như ở đây) thì phân trang và total đều đúng.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G10-S09a | Specification động (không fetch) → SQL có/không có where theo điều kiện, total đúng")
	void s09a_specificationBuildsDifferentSqlPerCondition() {
		// ── GIVEN ──
		G10Fixtures.seed(jdbc, Q1);
		Specification<Q1Customer> nameStartsWithCustomer = (root, query, cb) -> cb.like(root.get("name"), "Customer-%");
		Specification<Q1Customer> matchesNothing = (root, query, cb) -> cb.like(root.get("name"), "Nobody-%");

		// ── WHEN ──
		sql.reset();
		Page<Q1Customer> matching = inTxReturning(() -> customerRepository.findAll(nameStartsWithCustomer, PageRequest.of(0, 2)));
		sql.assertSomeStatementContains("like");
		Page<Q1Customer> none = inTxReturning(() -> customerRepository.findAll(matchesNothing, PageRequest.of(0, 2)));

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(3); // lọc khớp: data + count; lọc không khớp: chỉ data (trang rỗng nên Spring Data bỏ qua count)

		// ── THEN: DB thật ──
		assertThat(matching.getTotalElements()).as("điều kiện khớp cả 3 customer").isEqualTo(db.countOf(Q1 + ".customer"));
		assertThat(none.getTotalElements()).as("điều kiện không khớp dòng nào").isZero();
	}

	/**
	 * G10-S09b — Q1: {@code root.fetch("orders")} trong Specification làm count query ném lỗi 🔬
	 *
	 * <p>Mapping: như S09a; Specification gọi {@code root.fetch("orders")}.
	 * <p><b>Câu hỏi</b>: Spring Data dùng CÙNG Specification cho cả query dữ liệu và query count; {@code fetch} trong count query thì sao?
	 * <p><b>Kết luận</b>: query dữ liệu chạy được, nhưng query count ném {@code SemanticException}
	 * ("join fetching, but the owner of the fetched association was not present in the select list") vì count không chọn entity gốc.
	 * Lỗi nổi lên dưới dạng {@code InvalidDataAccessApiUsageException}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G10-S09b | Specification có root.fetch(\"orders\") + Page → count query ném SemanticException")
	void s09b_fetchInSpecificationBreaksCountQuery() {
		// ── GIVEN ──
		G10Fixtures.seed(jdbc, Q1);
		Specification<Q1Customer> fetchOrders = (root, query, cb) -> {
			root.fetch("orders"); // đúng với query dữ liệu, sai với query count
			return cb.like(root.get("name"), "Customer-%");
		};

		// ── WHEN / THEN ──
		sql.reset();
		assertThatThrownBy(() -> inTx(() -> customerRepository.findAll(fetchOrders, PageRequest.of(0, 2))))
			.as("count query chọn count(...) chứ không chọn Customer nên không thể fetch orders")
			.hasRootCauseInstanceOf(SemanticException.class);
		sql.print();
	}

	/**
	 * G10-S09c — Q1: cách sửa S09b, chỉ {@code fetch} khi query không phải count
	 *
	 * <p>Mapping: như S09b; Specification kiểm tra {@code query.getResultType()}.
	 * <p><b>Câu hỏi</b>: dùng {@code fetch} trong Specification mà vẫn phân trang được thì viết thế nào?
	 * <p><b>Kết luận</b>: bọc {@code root.fetch} trong {@code if (query.getResultType() != Long.class)}. Query dữ liệu có join fetch
	 * (orders đã khởi tạo), query count không có fetch nên total đúng 3.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G10-S09c | Specification chỉ fetch khi không phải count query → Page chạy được, total đúng")
	void s09c_fetchOnlyForDataQueryFixesPaging() {
		// ── GIVEN ──
		G10Fixtures.seed(jdbc, Q1);
		Specification<Q1Customer> fetchOrdersExceptInCount = (root, query, cb) -> {
			if (query.getResultType() != Long.class) { // Long.class = đây là câu count
				root.fetch("orders");
			}
			return cb.like(root.get("name"), "Customer-%");
		};

		// ── WHEN ──
		sql.reset();
		Page<Q1Customer> page = inTxReturning(() -> customerRepository.findAll(fetchOrdersExceptInCount, PageRequest.of(0, 2)));

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(2);
		sql.assertSomeStatementContains("order_no"); // query dữ liệu có cột order (đã fetch)

		// ── THEN: DB thật ──
		assertThat(page.getTotalElements()).as("count không có fetch/join nên đếm đúng customer").isEqualTo(db.countOf(Q1 + ".customer"));
		assertThat(page.getContent()).as("trang 0 cỡ 2").hasSize(2);
	}

	/**
	 * G10-S10a — Q1: DTO projection bằng JPQL {@code select new} không đưa entity vào persistence context
	 *
	 * <p>Mapping: Q1; DTO là record {@code Q1CustomerSummary(customerName, orderCount)}.
	 * <p><b>Câu hỏi</b>: kết quả {@code select new ...} có nằm trong persistence context không?
	 * <p><b>Kết luận</b>: không: 0 entity. Record chỉ là dữ liệu đọc, không vòng đời, không dirty checking, không lazy loading;
	 * đổi lại chỉ 1 SELECT đúng cột cần và không lo N+1.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G10-S10a | JPQL select new (DTO record) → 0 entity trong persistence context")
	void s10a_jpqlConstructorExpressionReturnsNoEntities() {
		// ── GIVEN ──
		G10Fixtures.seed(jdbc, Q1);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			List<Q1CustomerSummary> summaries = em.createQuery("select new " + DTO_CLASS + "(c.name, count(o)) "
				+ "from G10Q1_Customer c join c.orders o group by c.id, c.name order by c.id", Q1CustomerSummary.class).getResultList();
			// [LIFECYCLE] DTO không phải entity: không có trạng thái TRANSIENT/MANAGED/...
			assertThat(summaries).as("3 customer, mỗi người 2 order").hasSize(3);
			assertThat(summaries.get(0).orderCount()).as("Customer-1 có 2 order").isEqualTo(G10Fixtures.ORDERS_PER_CUSTOMER);
			assertThat(em.unwrap(Session.class).getStatistics().getEntityCount()).as("DTO không vào persistence context").isZero();
			// [FLUSH] không có gì để flush
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);

		// ── THEN: DB thật ──
		assertThat(db.countOf(Q1 + ".customer")).as("chỉ đọc, DB không đổi").isEqualTo(3L);
	}

	/**
	 * G10-S10b — Q1: native query trả {@code Tuple} cũng không có entity nào trong persistence context
	 *
	 * <p><b>Câu hỏi</b>: native + {@code Tuple} khác JPQL DTO ở chỗ nào?
	 * <p><b>Kết luận</b>: cùng kết quả 0 entity. Khác ở chỗ native SQL là văn bản của mình: không được Hibernate thêm điều kiện
	 * ({@code @SoftDelete}, G10-S07) và không kiểm tra tên bảng/cột lúc khởi động.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G10-S10b | native query trả Tuple → 0 entity trong persistence context")
	void s10b_nativeTupleReturnsNoEntities() {
		// ── GIVEN ──
		G10Fixtures.seed(jdbc, Q1);

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			@SuppressWarnings("unchecked")
			List<Tuple> tuples = em.createNativeQuery("select c.name as name, count(o.id) as order_count from " + Q1 + ".customer c join "
				+ Q1 + ".purchase_order o on o.customer_id = c.id group by c.name order by c.name", Tuple.class).getResultList();
			assertThat(tuples).as("3 customer có order").hasSize(3);
			assertThat(tuples.get(0).get("name", String.class)).as("đọc theo alias cột").isEqualTo("Customer-1");
			assertThat(em.unwrap(Session.class).getStatistics().getEntityCount()).as("Tuple không vào persistence context").isZero();
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);

		// ── THEN: DB thật ──
		assertThat(db.countOf(Q1 + ".customer")).as("chỉ đọc, DB không đổi").isEqualTo(3L);
	}
}
