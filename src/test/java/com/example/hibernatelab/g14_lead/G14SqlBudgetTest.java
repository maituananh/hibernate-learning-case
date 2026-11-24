package com.example.hibernatelab.g14_lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g14_lead.i1_buggy.I1BuggyOrderListService;
import com.example.hibernatelab.g14_lead.i1_fixed.I1FixedOrderListService;
import com.example.hibernatelab.g14_lead.i4_fixed.I4FixedImportService;
import com.example.hibernatelab.support.AbstractScenarioTest;
import com.example.hibernatelab.support.SqlBudget;
import com.example.hibernatelab.support.SqlCaptor;

/**
 * G14-R1: rào chắn "ngân sách SQL". Các test dưới đây kiểm tra chính {@link SqlBudget}: khi nào nó im lặng, khi nào nó đỏ,
 * và thông báo lỗi có đủ thông tin để sửa hay không. (Việc áp ngân sách lên use case thật của I1 nằm ở G14-I1-S04.)
 *
 * <p>Cách dùng trong dự án thật: mỗi use case quan trọng một test: {@code sql.reset(); chạy use case;
 * SqlBudget.assertSelectsAtMost(captor, "tên use case", N);}. Con số N là hợp đồng của team; muốn tăng N phải sửa test
 * và giải thích trong PR, nên một vòng lặp N+1 lọt vào sẽ không thể "âm thầm" qua review.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G14SqlBudgetTest extends AbstractScenarioTest {

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private SqlCaptor captor;
	@Autowired
	private I1BuggyOrderListService buggyOrderList;
	@Autowired
	private I1FixedOrderListService fixedOrderList;
	@Autowired
	private I4FixedImportService fixedImport;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { G14Fixtures.I1_BUGGY, G14Fixtures.I1_FIXED, G14Fixtures.I4_FIXED };
	}

	/**
	 * G14-R1-S01 — nằm đúng ngân sách thì im lặng
	 *
	 * <p><b>Câu hỏi</b>: use case dùng đúng 2 SELECT với ngân sách 2 có bị báo không?
	 * <p><b>Kết luận</b>: không. Ngân sách là TRẦN ("tối đa"), đúng bằng trần vẫn qua.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-R1-S01 | use case dùng đúng ngân sách (2 SELECT / ngân sách 2) → không báo lỗi")
	void s01_withinBudgetPasses() {
		// ── GIVEN ──
		G14Fixtures.seedI1(jdbc, G14Fixtures.I1_FIXED);

		// ── WHEN ──
		sql.reset();
		fixedOrderList.list(PageRequest.of(0, 10));

		// ── THEN ──
		assertThatCode(() -> SqlBudget.assertSelectsAtMost(captor, "danh sách đơn", 2))
			.as("2 SELECT đúng bằng trần 2 → qua").doesNotThrowAnyException();
	}

	/**
	 * G14-R1-S02 — vượt ngân sách thì đỏ, kèm đúng các câu SELECT đã chạy
	 *
	 * <p><b>Câu hỏi</b>: khi vượt, thông báo lỗi có giúp người sửa thấy câu nào thừa không?
	 * <p><b>Kết luận</b>: có. Thông báo nêu tên use case, trần, số thực tế (22) và liệt kê từng SELECT đánh số #1…#22, nên người
	 * đọc thấy ngay câu {@code select … from customer where id=?} lặp lại 10 lần.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-R1-S02 | vượt ngân sách → AssertionError nêu use case, trần, số thực tế và liệt kê các SELECT")
	void s02_overBudgetFailsWithSqlListing() {
		// ── GIVEN ──
		G14Fixtures.seedI1(jdbc, G14Fixtures.I1_BUGGY);

		// ── WHEN ──
		sql.reset();
		buggyOrderList.list(PageRequest.of(0, 10, Sort.by("id")));

		// ── THEN ──
		Throwable failure = null;
		try {
			SqlBudget.assertSelectsAtMost(captor, "danh sách đơn", 2);
		} catch (AssertionError expected) {
			failure = expected;
		}
		System.out.println("[BUDGET] thông báo lỗi người dùng sẽ thấy:\n" + (failure == null ? "(không có lỗi)" : failure.getMessage()));
		assertThat(failure).as("22 SELECT vượt trần 2 → phải đỏ").isNotNull();
		assertThat(failure.getMessage()).as("nêu tên use case và trần").contains("danh sách đơn").contains("tối đa 2");
		assertThat(failure.getMessage()).as("nêu số thực tế").contains("đã chạy 22");
		assertThat(failure.getMessage()).as("liệt kê từng SELECT đánh số để thấy câu lặp").contains("#1 ").contains("#22 ");
	}

	/**
	 * G14-R1-S03 — chỉ đếm SELECT, không đếm INSERT
	 *
	 * <p><b>Câu hỏi</b>: use case ghi 50 dòng (50 INSERT) có làm ngân sách SELECT vượt không?
	 * <p><b>Kết luận</b>: không. Ngân sách này chỉ canh việc ĐỌC quá nhiều; số lần ghi là mối quan tâm khác (batch, G13) và có
	 * rào chắn riêng (G14-I4-S04). Trộn hai thứ vào một con số sẽ làm thông báo lỗi khó hiểu.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-R1-S03 | ngân sách SELECT không đếm INSERT: import 50 dòng với ngân sách 0 SELECT vẫn qua")
	void s03_onlySelectsAreCounted() {
		// ── WHEN ──
		sql.reset();
		fixedImport.importItems(I4FixedImportService.CHUNK);

		// ── THEN ──
		assertThatCode(() -> SqlBudget.assertSelectsAtMost(captor, "import", 0))
			.as("import chỉ INSERT, không SELECT nào → ngân sách 0 vẫn qua").doesNotThrowAnyException();
	}
}
