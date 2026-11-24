package com.example.hibernatelab.g14_lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g14_lead.i1_buggy.I1BuggyOrderListService;
import com.example.hibernatelab.g14_lead.i1_buggy.I1BuggyOrderRow;
import com.example.hibernatelab.g14_lead.i1_fixed.I1FixedOrderListService;
import com.example.hibernatelab.g14_lead.i1_fixed.I1FixedOrderRow;
import com.example.hibernatelab.support.AbstractScenarioTest;
import com.example.hibernatelab.support.SqlBudget;
import com.example.hibernatelab.support.SqlCaptor;

/**
 * Sự cố G14-I1: "Trang danh sách đơn hàng chậm dần theo dữ liệu". Bốn test theo E7: tái hiện, chẩn đoán, sửa, rào chắn.
 *
 * <p>Dữ liệu: 30 đơn (mỗi đơn một khách riêng, 3 dòng hàng). Mapping của CẢ HAI package đều đúng quy tắc (mọi quan hệ LAZY),
 * nên lỗi không nằm ở mapping mà ở service đọc danh sách: bài học là review PR phải đọc cả CÁCH DÙNG quan hệ, không chỉ khai báo.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G14I1OrderListTest extends AbstractScenarioTest {

	/** Ngân sách SQL của use case "trang danh sách đơn": 1 query dữ liệu + 1 query count, không hơn. */
	private static final int ORDER_LIST_SELECT_BUDGET = 2;

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private SqlCaptor captor;
	@Autowired
	private I1BuggyOrderListService buggyService;
	@Autowired
	private I1FixedOrderListService fixedService;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { G14Fixtures.I1_BUGGY, G14Fixtures.I1_FIXED };
	}

	/**
	 * G14-I1-S01 — TÁI HIỆN: trang 10 đơn tốn 22 câu SELECT
	 *
	 * <p>Mapping: I1 buggy. <b>Câu hỏi</b>: trang danh sách chỉ cần 10 dòng, DB phải trả lời bao nhiêu câu SELECT?
	 * <p><b>Kết luận</b>: 22 = 1 (trang) + 1 (count) + 10 (customer, mỗi đơn một khách) + 10 (order_item, mỗi đơn một collection).
	 * Dữ liệu trả về hoàn toàn ĐÚNG, nên lỗi này chỉ lộ ra khi đếm SQL hoặc khi dữ liệu lớn.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-I1-S01 | tái hiện: trang 10 đơn của bản buggy chạy 22 câu SELECT (N+1 hai tầng)")
	void s01_reproduceNPlusOneOnOrderList() {
		// ── GIVEN ──
		G14Fixtures.seedI1(jdbc, G14Fixtures.I1_BUGGY);

		// ── WHEN ──
		sql.reset();
		Page<I1BuggyOrderRow> page = buggyService.list(PageRequest.of(0, 10, Sort.by("id")));

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(2 + 2 * 10);

		// ── THEN: DB thật ──
		assertThat(page.getContent()).as("kết quả đúng: lỗi chỉ nằm ở SỐ CÂU SQL, không nằm ở dữ liệu").hasSize(10);
		assertThat(page.getTotalElements()).as("tổng số đơn thật trong DB").isEqualTo(G14Fixtures.I1_ORDER_COUNT);
		assertThat(page.getContent().get(0).customerName()).as("tên khách đọc đúng qua proxy LAZY").isEqualTo("Customer-1");
		assertThat(page.getContent().get(0).itemCount()).as("số dòng hàng đọc đúng qua collection LAZY").isEqualTo(G14Fixtures.I1_ITEMS_PER_ORDER);
	}

	/**
	 * G14-I1-S02 — CHẨN ĐOÁN: số SELECT tăng tuyến tính theo cỡ trang
	 *
	 * <p>Mapping: I1 buggy. <b>Câu hỏi</b>: làm sao biết đây là N+1 chứ không phải một query chậm?
	 * <p><b>Kết luận</b>: chạy cùng use case với cỡ trang 5 rồi 10: số SELECT vào {@code customer} và {@code order_item} bằng đúng
	 * cỡ trang (5 → 5, 10 → 10). Con số tỉ lệ với N là dấu vân tay của N+1; còn query chậm thì số câu không đổi. Cách đọc SQL:
	 * câu {@code select … from customer where id=?} lặp lại với tham số khác nhau là dấu hiệu rõ nhất.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-I1-S02 | chẩn đoán: số SELECT vào customer và order_item bằng đúng cỡ trang (dấu vân tay N+1)")
	void s02_diagnoseSelectCountGrowsWithPageSize() {
		// ── GIVEN ──
		G14Fixtures.seedI1(jdbc, G14Fixtures.I1_BUGGY);

		// ── WHEN: cỡ trang 5 ──
		sql.reset();
		buggyService.list(PageRequest.of(0, 5, Sort.by("id")));
		sql.print();

		// ── THEN: SQL (cỡ trang 5) ──
		sql.assertSelects("customer", 5);
		sql.assertSelects("order_item", 5);

		// ── WHEN: cỡ trang 10 ──
		sql.reset();
		buggyService.list(PageRequest.of(0, 10, Sort.by("id")));
		sql.print();

		// ── THEN: SQL (cỡ trang 10) ──
		sql.assertSelects("customer", 10);
		sql.assertSelects("order_item", 10);
		sql.assertSelects("purchase_order", 2); // chỉ 2 câu cho bảng chính (trang + count): phần "N" nằm ở các bảng phụ
	}

	/**
	 * G14-I1-S03 — SỬA: đọc DTO bằng một query, còn đúng 2 câu SELECT
	 *
	 * <p>Mapping: I1 fixed (cùng entity, khác service/repository). <b>Câu hỏi</b>: sửa thế nào mà màn hình vẫn hiện đúng dữ liệu cũ?
	 * <p><b>Kết luận</b>: {@code select new …Row(o.orderNo, c.name, size(o.items)) … join o.customer} + {@code countQuery} riêng
	 * → 2 câu SELECT dù cỡ trang là bao nhiêu, dữ liệu trả về y hệt bản buggy.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-I1-S03 | sửa: DTO projection + countQuery → 2 câu SELECT, dữ liệu vẫn đúng")
	void s03_fixWithDtoProjection() {
		// ── GIVEN ──
		G14Fixtures.seedI1(jdbc, G14Fixtures.I1_FIXED);

		// ── WHEN ──
		sql.reset();
		Page<I1FixedOrderRow> page = fixedService.list(PageRequest.of(0, 10));

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(2);

		// ── THEN: DB thật ──
		assertThat(page.getContent()).as("vẫn đủ 10 dòng của trang đầu").hasSize(10);
		assertThat(page.getTotalElements()).as("countQuery riêng đếm đơn nên đúng 30").isEqualTo(G14Fixtures.I1_ORDER_COUNT);
		assertThat(page.getContent().get(0).orderNo()).as("trang đầu bắt đầu từ đơn nhỏ nhất").isEqualTo("ORD-1");
		assertThat(page.getContent().get(0).customerName()).as("tên khách giống hệt bản buggy").isEqualTo("Customer-1");
		assertThat(page.getContent().get(0).itemCount()).as("subquery size() đếm đúng 3 dòng hàng").isEqualTo(G14Fixtures.I1_ITEMS_PER_ORDER);
	}

	/**
	 * G14-I1-S04 — RÀO CHẮN (G14-R1): ngân sách 2 SELECT bắt được bản buggy và để bản fixed qua
	 *
	 * <p>Mapping: I1 buggy và fixed. <b>Câu hỏi</b>: làm sao để lỗi này không quay lại?
	 * <p><b>Kết luận</b>: đặt ngân sách {@value #ORDER_LIST_SELECT_BUDGET} SELECT cho use case. Chạy trên đường buggy thì
	 * {@code SqlBudget} ném {@code AssertionError} kèm SQL thừa; chạy trên đường fixed thì qua. Test phải chứng minh CẢ HAI chiều
	 * (rào chắn không bắt được gì thì vô dụng).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-I1-S04 | rào chắn: ngân sách 2 SELECT làm bản buggy đỏ (kèm SQL thừa) và bản fixed xanh")
	void s04_sqlBudgetCatchesBuggyAndPassesFixed() {
		// ── GIVEN ──
		G14Fixtures.seedI1(jdbc, G14Fixtures.I1_BUGGY);
		G14Fixtures.seedI1(jdbc, G14Fixtures.I1_FIXED);

		// ── WHEN + THEN: đường buggy phải vượt ngân sách ──
		sql.reset();
		buggyService.list(PageRequest.of(0, 10, Sort.by("id")));
		assertThatThrownBy(() -> SqlBudget.assertSelectsAtMost(captor, "danh sách đơn (buggy)", ORDER_LIST_SELECT_BUDGET))
			.as("22 SELECT > 2 → rào chắn phải ném AssertionError, nếu không thì rào chắn vô dụng")
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("vượt ngân sách SQL")
			.hasMessageContaining("from g14_i1_buggy.customer"); // thông báo lỗi chỉ ra đúng câu SELECT thừa

		// ── WHEN + THEN: đường fixed phải nằm trong ngân sách ──
		sql.reset();
		fixedService.list(PageRequest.of(0, 10));
		assertThatCode(() -> SqlBudget.assertSelectsAtMost(captor, "danh sách đơn (fixed)", ORDER_LIST_SELECT_BUDGET))
			.as("2 SELECT ≤ 2 → qua ngân sách")
			.doesNotThrowAnyException();
	}
}
