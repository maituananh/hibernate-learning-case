package com.example.hibernatelab.g12_advanced_model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;

import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g12_advanced_model.n1_natural_id.N1Product;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G12, chủ đề {@code @NaturalId}: S10 (tra theo khoá nghiệp vụ và cache trong persistence context).
 *
 * <p>Model N1: {@code Product} có khoá thay thế {@code id} và khoá tự nhiên {@code sku} (mã hàng người dùng nhìn thấy).
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G12NaturalIdTest extends AbstractScenarioTest {

	private static final String N1 = "g12_n1_natural_id";
	private static final String PRODUCT = N1 + ".product";

	@Autowired
	private JdbcTemplate jdbc;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { N1 };
	}

	/**
	 * G12-S10a — N1: {@code byNaturalId(...).load()} lần đầu chạy SELECT, lần hai trong cùng persistence context là 0 SELECT
	 *
	 * <p>Mapping: N1 (baseline). <b>Câu hỏi</b>: tra theo SKU hai lần liên tiếp trong cùng một transaction thì SQL gì?
	 * <p><b>Kết luận</b>: lần đầu 1 SELECT {@code where sku=?}; lần hai 0 SELECT và trả CÙNG instance, vì persistence context nhớ ánh xạ
	 * natural id → entity (giống cache cấp 1 theo id). Query JPQL {@code where p.sku = ?} thì không được hưởng điều này.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S10a | byNaturalId lần 1 có SELECT, lần 2 trong cùng PC là 0 SELECT, cùng instance")
	void s10a_secondNaturalIdLookupHitsPersistenceContext() {
		// ── GIVEN ──
		jdbc.update("insert into " + PRODUCT + " (id, sku, name) values (1, 'SKU-1', 'Pen')");
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			Session session = em.unwrap(Session.class);
			N1Product firstLookup = session.byNaturalId(N1Product.class).using("sku", "SKU-1").load(); // [LIFECYCLE] MANAGED
			sql.assertSelects("product", 1); // lần đầu phải xuống DB để tìm theo sku
			N1Product secondLookup = session.byNaturalId(N1Product.class).using("sku", "SKU-1").load();

			// ── THEN ──
			sql.assertSelects("product", 1); // tổng vẫn là 1: lần hai tra trong persistence context, không thêm SELECT nào
			assertThat(secondLookup).as("cùng một persistence context nên cùng một instance").isSameAs(firstLookup);
		});
		sql.print();
	}

	/**
	 * G12-S10b — N1: {@code @NaturalId} sinh UNIQUE constraint trên cột sku
	 *
	 * <p>Mapping: N1. <b>Câu hỏi</b>: DDL có gì cho cột sku?
	 * <p><b>Kết luận</b>: có unique constraint trên {@code sku}, nên hai sản phẩm trùng SKU bị DB từ chối.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S10b | @NaturalId sinh UNIQUE constraint trên sku")
	void s10b_naturalIdGeneratesUniqueConstraint() {
		// ── THEN: DDL ──
		schema.printDdl(N1);
		assertThat(schema.uniqueConstraintsOf(N1, "product")).as("natural id phải là duy nhất nên DDL có unique trên sku").contains(List.of("sku"));
	}

	/**
	 * G12-S10c — N1 🔬: đổi {@code sku} của entity đã lưu bị Hibernate chặn (natural id mặc định bất biến)
	 *
	 * <p>Mapping: N1. <b>Câu hỏi</b>: sửa {@code sku} của entity MANAGED rồi commit thì sao?
	 * <p><b>Kết luận</b>: ném exception lúc flush ("immutable natural identifier … was altered"), transaction rollback, DB giữ SKU cũ.
	 * Chọn {@code @NaturalId(mutable = true)} nếu nghiệp vụ cho phép đổi mã.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S10c | 🔬 đổi natural id của entity đã lưu → exception lúc flush, DB giữ SKU cũ")
	void s10c_changingImmutableNaturalIdIsRejected() {
		// ── GIVEN ──
		jdbc.update("insert into " + PRODUCT + " (id, sku, name) values (1, 'SKU-1', 'Pen')");

		// ── WHEN + THEN ──
		Throwable failure = catchThrowable(() -> inTx(() -> {
			N1Product productPen = em.find(N1Product.class, 1L); // [LIFECYCLE] MANAGED
			productPen.changeSku("SKU-2"); // [FLUSH] lúc commit Hibernate so natural id với bản chụp cũ
		}));
		System.out.println("[EXCEPTION] " + failure);
		assertThat(failure).as("natural id mặc định bất biến nên đổi nó phải lỗi").isNotNull();
		assertThat(failure.getMessage()).as("thông báo phải nói về natural id bất biến").containsIgnoringCase("natural");
		assertThat(db.rowById(PRODUCT, 1L).get("sku")).as("transaction rollback nên DB giữ SKU cũ").isEqualTo("SKU-1");
	}
}
