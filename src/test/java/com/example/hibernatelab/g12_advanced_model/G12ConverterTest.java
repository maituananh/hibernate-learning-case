package com.example.hibernatelab.g12_advanced_model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g12_advanced_model.v1_converter.ReorderedReviewStatus;
import com.example.hibernatelab.g12_advanced_model.v1_converter.ReviewStatus;
import com.example.hibernatelab.g12_advanced_model.v1_converter.V1Product;
import com.example.hibernatelab.g12_advanced_model.v1_converter.V1ProductWithEquals;
import com.example.hibernatelab.g12_advanced_model.v1_converter.V1ReviewOrdinal;
import com.example.hibernatelab.g12_advanced_model.v1_converter.V1ReviewReordered;
import com.example.hibernatelab.g12_advanced_model.v1_converter.V1ReviewString;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G12, chủ đề chuyển đổi kiểu: S11a/S11b ({@code AttributeConverter} cho kiểu MUTABLE), S11c ({@code @Enumerated} ORDINAL so với STRING).
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G12ConverterTest extends AbstractScenarioTest {

	private static final String V1 = "g12_v1_converter";

	@Autowired
	private JdbcTemplate jdbc;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { V1 };
	}

	/**
	 * G12-S11a — V1 🔬: converter cho kiểu MUTABLE KHÔNG có equals, sửa tại chỗ ({@code tags.add}) VẪN có UPDATE
	 *
	 * <p>Mapping: V1 ({@code Tags} mutable, không override equals). <b>Câu hỏi</b>: {@code product.getTags().add("c")} rồi commit có UPDATE không?
	 * <p><b>Kết luận</b>: có 1 UPDATE và DB là {@code a,b,c}. <b>Khác giả thuyết ban đầu</b> ("phụ thuộc equals của kiểu"):
	 * với Hibernate 7.4 việc phát hiện thay đổi không dựa vào {@code equals} của {@code Tags}, nên sửa tại chỗ vẫn được ghi xuống DB
	 * (ghi vào docs/RESULTS.md). Đừng dựa vào điều này như hợp đồng: kiểu của bạn nên tự nhất quán (immutable hoặc có equals) cho an toàn.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S11a | 🔬 converter + kiểu mutable KHÔNG có equals: sửa tại chỗ vẫn có UPDATE")
	void s11a_mutableTypeWithoutEqualsIsStillDetected() {
		// ── GIVEN ──
		jdbc.update("insert into " + V1 + ".v1_product (id, tags) values (1, 'a,b')");
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			V1Product productOne = em.find(V1Product.class, 1L); // [LIFECYCLE] MANAGED
			productOne.getTags().add("c"); // sửa tại chỗ, không gán lại field
			// [FLUSH] lúc commit
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("v1_product", 1);
		// ── THEN: DB thật ──
		assertThat(db.rowById(V1 + ".v1_product", 1L).get("tags")).as("giá trị mới đã được converter ghi xuống DB").isEqualTo("a,b,c");
	}

	/**
	 * G12-S11b — V1E 🔬: cùng S11a nhưng kiểu có {@code equals}/{@code hashCode} theo nội dung → cũng có UPDATE
	 *
	 * <p>Mapping: V1E ({@code TagsWithEquals}). <b>Câu hỏi</b>: có equals thì khác gì S11a?
	 * <p><b>Kết luận</b>: kết quả giống hệt S11a (1 UPDATE, DB {@code a,b,c}); trong thí nghiệm này {@code equals} không làm đổi hành vi.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S11b | 🔬 converter + kiểu mutable CÓ equals: sửa tại chỗ cũng có UPDATE (giống S11a)")
	void s11b_mutableTypeWithEqualsIsDetected() {
		// ── GIVEN ──
		jdbc.update("insert into " + V1 + ".v1_product_equals (id, tags) values (1, 'a,b')");
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			V1ProductWithEquals productOne = em.find(V1ProductWithEquals.class, 1L); // [LIFECYCLE] MANAGED
			productOne.getTags().add("c");
			// [FLUSH] lúc commit
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("v1_product_equals", 1);
		// ── THEN: DB thật ──
		assertThat(db.rowById(V1 + ".v1_product_equals", 1L).get("tags")).as("giá trị mới đã được ghi").isEqualTo("a,b,c");
	}

	/**
	 * G12-S11c — V1 🔬: {@code @Enumerated(ORDINAL)} khi enum bị đổi thứ tự → đọc dữ liệu cũ ra giá trị SAI, không báo lỗi
	 *
	 * <p>Mapping: V1ReviewOrdinal / V1ReviewReordered / V1ReviewString. <b>Câu hỏi</b>: DB lưu số 1 (hồi đó nghĩa là PAID); sau khi ai đó đổi
	 * thứ tự khai báo enum thành {@code PAID, NEW}, đọc ra gì?
	 * <p><b>Kết luận</b>: ORDINAL: số 1 đọc ra {@code PAID} với enum cũ nhưng {@code NEW} với enum đã đổi thứ tự: đọc sai im lặng.
	 * STRING: 'PAID' luôn đọc ra {@code PAID}. Dùng STRING (hoặc converter với mã cố định) cho dữ liệu sống lâu.
	 * (Hai enum là hai bảng riêng để mô phỏng "trước và sau khi đổi thứ tự", vì trong một lần chạy không thể có hai phiên bản cùng một enum.)
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S11c | 🔬 ORDINAL: đổi thứ tự enum → dữ liệu cũ đọc sai im lặng; STRING thì không")
	void s11c_ordinalMisreadsAfterReorder() {
		// ── GIVEN ──
		jdbc.update("insert into " + V1 + ".v1_review_ordinal (id, status) values (1, 1)"); // số 1 = PAID theo thứ tự cũ (NEW, PAID)
		jdbc.update("insert into " + V1 + ".v1_review_reordered (id, status) values (1, 1)"); // CÙNG số 1, nhưng enum nay là (PAID, NEW)
		jdbc.update("insert into " + V1 + ".v1_review_string (id, status) values (1, 'PAID')");

		// ── WHEN ──
		ReviewStatus statusBeforeReorder = inTxReturning(() -> em.find(V1ReviewOrdinal.class, 1L).getStatus());
		ReorderedReviewStatus statusAfterReorder = inTxReturning(() -> em.find(V1ReviewReordered.class, 1L).getStatus());
		ReviewStatus statusByName = inTxReturning(() -> em.find(V1ReviewString.class, 1L).getStatus());

		// ── THEN ──
		assertThat(statusBeforeReorder).as("số 1 với enum (NEW, PAID) là PAID").isEqualTo(ReviewStatus.PAID);
		assertThat(statusAfterReorder).as("cùng số 1 nhưng enum đã đổi thành (PAID, NEW) nên bị đọc thành NEW, không có lỗi nào").isEqualTo(ReorderedReviewStatus.NEW);
		assertThat(statusByName).as("STRING lưu tên nên không phụ thuộc thứ tự khai báo").isEqualTo(ReviewStatus.PAID);
	}

	/**
	 * G12-S11d — V1 🔬: DDL của ORDINAL (smallint) và STRING (varchar) kèm check constraint chặn giá trị lạ
	 *
	 * <p>Mapping: V1ReviewOrdinal / V1ReviewString. <b>Câu hỏi</b>: DB có tự bảo vệ khỏi giá trị ngoài enum không?
	 * <p><b>Kết luận</b>: có. Hibernate 7.4 sinh {@code check (status between 0 and 1)} cho ORDINAL và {@code check (status in ('NEW','PAID'))}
	 * cho STRING, nên số 5 hay chữ 'GHOST' đều bị DB từ chối. Nhưng check này KHÔNG cứu được S11c: số 1 vẫn hợp lệ.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S11d | 🔬 ORDINAL = smallint, STRING = varchar, cả hai có check constraint chặn giá trị lạ")
	void s11d_enumColumnsHaveCheckConstraints() {
		// ── WHEN ──
		Throwable ordinalFailure = catchThrowable(() -> jdbc.update("insert into " + V1 + ".v1_review_ordinal (id, status) values (9, 5)"));
		Throwable stringFailure = catchThrowable(() -> jdbc.update("insert into " + V1 + ".v1_review_string (id, status) values (9, 'GHOST')"));

		// ── THEN: DDL + DB thật ──
		assertThat(schema.columnNamed(V1, "v1_review_ordinal", "status").type()).as("ORDINAL lưu số nguyên nhỏ").isEqualTo("smallint");
		assertThat(schema.columnNamed(V1, "v1_review_string", "status").type()).as("STRING lưu chữ").startsWith("varchar");
		assertThat(ordinalFailure).as("số 5 nằm ngoài khoảng 0..1 của enum nên check constraint từ chối").isNotNull();
		assertThat(stringFailure).as("'GHOST' không thuộc enum nên check constraint từ chối").isNotNull();
	}
}
