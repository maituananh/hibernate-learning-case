package com.example.hibernatelab.g14_lead;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.bootfail.g14r2.BadLintCustomer;
import com.example.hibernatelab.bootfail.g14r2.BadLintItem;
import com.example.hibernatelab.bootfail.g14r2.BadLintOrder;
import com.example.hibernatelab.bootfail.g14r2.BadLintProfile;
import com.example.hibernatelab.bootfail.g14r2.BadLintTag;
import com.example.hibernatelab.g14_lead.i1_buggy.I1BuggyOrder;
import com.example.hibernatelab.g14_lead.i1_buggy.I1BuggyCustomer;
import com.example.hibernatelab.g14_lead.i1_buggy.I1BuggyOrderItem;
import com.example.hibernatelab.g14_lead.i1_fixed.I1FixedCustomer;
import com.example.hibernatelab.g14_lead.i1_fixed.I1FixedOrder;
import com.example.hibernatelab.g14_lead.i1_fixed.I1FixedOrderItem;
import com.example.hibernatelab.g14_lead.i2_fixed.I2FixedProduct;
import com.example.hibernatelab.g14_lead.i2_fixed.I2FixedSale;
import com.example.hibernatelab.g14_lead.i3_fixed.I3FixedEmailLog;
import com.example.hibernatelab.g14_lead.i3_fixed.I3FixedJob;
import com.example.hibernatelab.g14_lead.i4_fixed.I4FixedItem;
import com.example.hibernatelab.support.MappingLint;

/**
 * G14-R2: rào chắn lint mapping (quyết định E8). Không cần DB và không khởi động Spring: {@link MappingLint} chỉ đọc mapping
 * mà Hibernate dựng được từ các class entity.
 *
 * <p>Phạm vi quét: chỉ các package {@code *_fixed} của G14 và các entity cố ý sai ở {@code bootfail/g14r2}. Package {@code *_buggy}
 * KHÔNG bị quét (lỗi ở đó là có chủ đích); riêng lỗi IDENTITY của I4 được chứng minh ở G14-I4-S04.
 * Cả lab G0–G9 cũng không bị quét vì cố ý chứa anti-pattern để học.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G14MappingLintTest {

	/**
	 * G14-R2-S01 — lint chạy trên toàn bộ model "sạch" của G14 và không báo gì
	 *
	 * <p><b>Câu hỏi</b>: model đã sửa của bốn sự cố có tuân thủ quy tắc mapping của team không?
	 * <p><b>Kết luận</b>: có, 0 vi phạm. Khi ai thêm một {@code @ManyToOne} EAGER hay đổi id sang IDENTITY vào các package
	 * {@code *_fixed}, test này đỏ ngay.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-R2-S01 | lint mapping trên mọi entity *_fixed của G14: 0 vi phạm")
	void s01_cleanModelPassesLint() {
		// ── WHEN ──
		List<String> violations = MappingLint.check(
			I1FixedCustomer.class, I1FixedOrder.class, I1FixedOrderItem.class,
			I2FixedProduct.class, I2FixedSale.class,
			I3FixedJob.class, I3FixedEmailLog.class,
			I4FixedItem.class);

		// ── THEN ──
		System.out.println("[LINT] vi phạm trên model sạch: " + violations);
		assertThat(violations).as("model đã sửa tuân thủ cả 4 quy tắc → lint không báo gì").isEmpty();
	}

	/**
	 * G14-R2-S02 — lint bắt được entity cố ý sai ở cả 4 quy tắc
	 *
	 * <p><b>Câu hỏi</b>: lint có thật sự bắt được lỗi không? (rào chắn luôn xanh thì vô dụng)
	 * <p><b>Kết luận</b>: entity {@code BadLintOrder} vi phạm cả 4 quy tắc nên lint trả đủ: EAGER-TO-ONE (cả {@code @ManyToOne} lẫn
	 * {@code @OneToOne}), EAGER-COLLECTION (cả {@code @OneToMany} lẫn {@code @ManyToMany}), CASCADE-REMOVE (cả trên {@code @ManyToOne}
	 * lẫn {@code @ManyToMany}) và IDENTITY-ID.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-R2-S02 | lint bắt được entity cố ý sai: EAGER to-one, EAGER collection, cascade REMOVE, IDENTITY")
	void s02_lintCatchesDeliberatelyBadEntity() {
		// ── WHEN ──
		List<String> violations = MappingLint.check(
			BadLintOrder.class, BadLintCustomer.class, BadLintProfile.class, BadLintItem.class, BadLintTag.class);

		// ── THEN ──
		violations.forEach(violation -> System.out.println("[LINT] " + violation));
		assertThat(violations).as("@ManyToOne không ghi fetch → EAGER → phải bị báo")
			.anyMatch(v -> v.startsWith("EAGER-TO-ONE") && v.contains("G14R2_BadLintOrder.customer"));
		assertThat(violations).as("@OneToOne không ghi fetch → EAGER → phải bị báo")
			.anyMatch(v -> v.startsWith("EAGER-TO-ONE") && v.contains("G14R2_BadLintOrder.profile"));
		assertThat(violations).as("@OneToMany EAGER → phải bị báo")
			.anyMatch(v -> v.startsWith("EAGER-COLLECTION") && v.contains("G14R2_BadLintOrder.items"));
		assertThat(violations).as("@ManyToMany EAGER → phải bị báo")
			.anyMatch(v -> v.startsWith("EAGER-COLLECTION") && v.contains("G14R2_BadLintOrder.tags"));
		assertThat(violations).as("cascade REMOVE trên @ManyToOne → phải bị báo")
			.anyMatch(v -> v.startsWith("CASCADE-REMOVE") && v.contains("G14R2_BadLintOrder.customer"));
		assertThat(violations).as("cascade ALL (gồm REMOVE) trên @ManyToMany → phải bị báo")
			.anyMatch(v -> v.startsWith("CASCADE-REMOVE") && v.contains("G14R2_BadLintOrder.tags"));
		assertThat(violations).as("id IDENTITY → phải bị báo")
			.anyMatch(v -> v.startsWith("IDENTITY-ID") && v.contains("G14R2_BadLintOrder.id"));
		assertThat(violations).as("@ManyToOne LAZY ở BadLintItem là ĐÚNG quy tắc nên không bị báo nhầm (chỉ IDENTITY-ID)")
			.noneMatch(v -> v.startsWith("EAGER-TO-ONE") && v.contains("BadLintItem"));
	}

	/**
	 * G14-R2-S03 — lint không báo nhầm trên mapping đúng quy tắc nhưng "trông giống" lỗi
	 *
	 * <p><b>Câu hỏi</b>: một {@code @OneToMany(cascade = PERSIST)} LAZY và {@code @ManyToOne} LAZY của I1 có bị lint hiểu lầm không?
	 * <p><b>Kết luận</b>: không. Lint chỉ cấm cascade REMOVE trên {@code @ManyToOne}/{@code @ManyToMany}, còn cascade PERSIST trên
	 * {@code @OneToMany} (đúng chuẩn cha-con) được phép. Rào chắn quá gắt thì team sẽ tắt nó đi.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-R2-S03 | lint không báo nhầm: @OneToMany LAZY cascade PERSIST và @ManyToOne LAZY của I1 đều qua")
	void s03_lintDoesNotFlagCorrectAssociations() {
		// ── WHEN: I1 buggy có mapping ĐÚNG quy tắc (lỗi của nó là cách đọc, không phải mapping) ──
		List<String> violations = MappingLint.check(I1BuggyCustomer.class, I1BuggyOrder.class, I1BuggyOrderItem.class);

		// ── THEN ──
		assertThat(violations).as("lint chỉ soi mapping; N+1 của I1 buggy nằm ở service nên lint (đúng) không thấy → cần ngân sách SQL G14-R1")
			.isEmpty();
	}
}
