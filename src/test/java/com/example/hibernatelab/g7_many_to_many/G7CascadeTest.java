package com.example.hibernatelab.g7_many_to_many;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.hibernatelab.g7_many_to_many.m7_cascade_all.M7Product;
import com.example.hibernatelab.g7_many_to_many.m7_cascade_all.M7Tag;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G7, phần cascade: M7 = M1 + {@code cascade = ALL} trên {@code Product.tags} (ANTI-PATTERN). S08.
 *
 * <p>Điểm cần nắm: tag là dữ liệu DÙNG CHUNG, không thuộc về riêng product nào. {@code CascadeType.REMOVE} trên
 * {@code @ManyToMany} coi tag như "con" của product: xoá product là xoá luôn tag, bất kể product khác còn dùng hay không.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G7CascadeTest extends AbstractScenarioTest {

	private static final String M7 = "g7_m7_cascade_all";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { M7 };
	}

	/**
	 * G7-S08a — M7: xoá product khi tag còn được product khác dùng
	 *
	 * <p>Mapping: M7, {@code Product.tags} có {@code cascade = ALL}.
	 * <p><b>Câu hỏi</b>: P1 và P2 cùng có tag T, xoá P1 thì chuyện gì xảy ra?
	 * <p><b>Kết luận</b>: REMOVE lan sang T: Hibernate DELETE dòng nối của P1, rồi DELETE T (tag bị xoá TRƯỚC
	 * cả product). Nhưng P2 vẫn còn dòng nối trỏ tới T nên PostgreSQL từ chối ngay ở câu DELETE tag (vi phạm FK).
	 * Rollback, DB giữ nguyên.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S08a | M7: cascade ALL, xoá P1 cố xoá tag T mà P2 còn dùng nên vi phạm FK")
	void s08a_m7RemoveProductTriesToDeleteSharedTag() {
		// ── GIVEN: P1 và P2 cùng có tag T ──
		M7Product productP1 = new M7Product("P1");
		M7Product productP2 = new M7Product("P2");
		M7Tag tagShared = new M7Tag("T");
		inTx(() -> {
			em.persist(tagShared);
			productP1.getTags().add(tagShared);
			productP2.getTags().add(tagShared);
			em.persist(productP1);
			em.persist(productP2);
		});

		// ── WHEN: xoá P1 ──
		sql.reset();
		assertThatThrownBy(() -> inTx(() -> {
			M7Product managedP1 = em.find(M7Product.class, productP1.getId());
			em.remove(managedP1);
			// [CASCADE] REMOVE lan từ P1 sang tag T (và T là tag của cả P2)
			// [FLUSH] lúc commit: DELETE dòng nối P1, DELETE T → T còn bị dòng nối của P2 trỏ tới nên DB từ chối
		}))
			.as("cascade REMOVE xoá tag dùng chung nên DB từ chối vì dòng nối của P2 còn tham chiếu T")
			.isInstanceOf(DataIntegrityViolationException.class);

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("tag", 1);

		// ── THEN: DB thật ──
		assertThat(db.countOf(M7 + ".product")).as("rollback: cả hai product vẫn còn").isEqualTo(2);
		assertThat(db.countOf(M7 + ".tag")).as("rollback: tag T vẫn còn").isEqualTo(1);
		assertThat(db.countOf(M7 + ".product_tag")).as("rollback: hai liên kết vẫn còn").isEqualTo(2);
	}

	/**
	 * G7-S08b — M7: xoá product khi tag không ai khác dùng
	 *
	 * <p>Mapping: M7.
	 * <p><b>Câu hỏi</b>: nếu chỉ P3 dùng tag T3 thì sao?
	 * <p><b>Kết luận</b>: không lỗi, và đó mới là chỗ nguy hiểm: T3 MẤT ÂM THẦM cùng P3. Nếu sau này người khác muốn
	 * gắn T3 cho sản phẩm mới thì tag đã không còn. Bài học: không cascade REMOVE trên {@code @ManyToMany}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S08b | M7: cascade ALL, xoá P3 làm mất luôn tag T3 âm thầm (không lỗi)")
	void s08b_m7RemoveProductSilentlyDeletesUnusedTag() {
		// ── GIVEN: P3 là product duy nhất dùng tag T3 ──
		M7Product productP3 = new M7Product("P3");
		M7Tag tagOnlyForP3 = new M7Tag("T3");
		inTx(() -> {
			productP3.getTags().add(tagOnlyForP3);
			em.persist(productP3); // [CASCADE] PERSIST lan sang T3 nên không cần persist tag riêng
		});
		assertThat(db.countOf(M7 + ".tag")).as("cascade PERSIST đã lưu tag T3").isEqualTo(1);

		// ── WHEN: xoá P3 ──
		inTx(() -> {
			M7Product managedP3 = em.find(M7Product.class, productP3.getId());
			sql.reset();
			em.remove(managedP3);
			// [CASCADE] REMOVE lan sang T3
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("tag", 1);

		// ── THEN: DB thật ──
		db.print(M7 + ".tag");
		assertThat(db.countOf(M7 + ".product")).as("P3 đã bị xoá").isZero();
		assertThat(db.countOf(M7 + ".tag")).as("T3 cũng biến mất theo P3 mà không có lỗi nào báo").isZero();
	}
}
