package com.example.hibernatelab.g13_enhanced;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.Hibernate;
import org.hibernate.engine.spi.PersistentAttributeInterceptable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g13_enhanced.control.C2PlainDocument;
import com.example.hibernatelab.g13_enhanced.control.C3PlainProduct;
import com.example.hibernatelab.g13_enhanced.e1_inverse_one_to_one.E1Customer;
import com.example.hibernatelab.g13_enhanced.e2_lazy_basic.E2Document;
import com.example.hibernatelab.g13_enhanced.e3_dirty_tracking.E3Product;

/**
 * Nhóm G13b (bytecode enhancement): S11 (LAZY ở phía inverse của {@code @OneToOne}), S12 ({@code @Basic(fetch = LAZY)}),
 * S13 (dirty tracking).
 *
 * <p>Chạy trong subproject {@code lab-enhanced}: entity ở {@code src/main} bị Hibernate enhance sau khi biên dịch,
 * entity đối chứng ({@code control}, C2 và C3) ở {@code src/test} thì KHÔNG (plugin chỉ áp cho source set main).
 * Nhờ vậy so sánh "có" và "không" enhancement ngay trong một class. Dữ liệu seed bằng JDBC (không qua Hibernate).
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G13BytecodeEnhancementTest extends AbstractEnhancedTest {

	private static final String E1 = "g13_e1_inverse_one_to_one";
	private static final String E2 = "g13_e2_lazy_basic";
	private static final String C2 = "g13_c2_plain_basic";
	private static final String E3 = "g13_e3_dirty_tracking";
	private static final String C3 = "g13_c3_plain_dirty";

	@Autowired
	private JdbcTemplate jdbc;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { E1, E2, C2, E3, C3 };
	}

	private long seedCustomerWithCard() {
		jdbc.update("insert into " + E1 + ".customer (id, name) values (1, 'An')");
		jdbc.update("insert into " + E1 + ".loyalty_card (id, card_no, customer_id) values (1, 'CARD-1', 1)");
		return 1L;
	}

	/**
	 * G13-S11a — E1 🔬: {@code find(customer)} khi entity đã được enhance
	 *
	 * <p>Mapping: E1, {@code Customer.loyaltyCard @OneToOne(mappedBy = "customer", fetch = LAZY)} (inverse side), y hệt G6-V3.
	 * <p><b>Câu hỏi</b>: bytecode enhancement có làm LAZY ở phía inverse chạy được không (thử lại G6-S03a)?
	 * <p><b>Kết luận</b>: CÓ. {@code find} chỉ SELECT bảng customer (1 câu), field {@code loyaltyCard} chưa được tải.
	 * Lần đầu gọi {@code getLoyaltyCard()} mới có SELECT thứ hai (join sang loyalty_card). Khác hẳn G6-S03a (không enhancement):
	 * ở đó {@code find} phải tải thẻ ngay.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S11a | E1 🔬: có enhancement thì find(customer) KHÔNG tải loyalty_card, chạm getLoyaltyCard() mới tải")
	void s11a_e1EnhancedFindDefersInverseOneToOne() {
		// ── GIVEN: An có thẻ CARD-1 ──
		long customerId = seedCustomerWithCard();

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			E1Customer customerAn = em.find(E1Customer.class, customerId);
			assertState(em, customerAn, MANAGED);

			// ── THEN: SQL ──
			sql.print();
			sql.assertTotal(1);
			sql.assertSelects("loyalty_card", 0);
			assertThat(Hibernate.isPropertyInitialized(customerAn, "loyaltyCard"))
				.as("field loyaltyCard còn trống: enhancement đã chặn việc tải nó cho tới khi có người đọc").isFalse();

			// [LAZY LOAD] getLoyaltyCard() đi qua interceptor mà enhancement chèn vào, lúc này mới SELECT
			assertThat(customerAn.getLoyaltyCard().getCardNo()).as("thẻ đúng của An").isEqualTo("CARD-1");
			sql.print();
			sql.assertTotal(2);
			sql.assertSelects("loyalty_card", 1);
		});
	}

	/**
	 * G13-S11b — E1 🔬: customer chưa có thẻ
	 *
	 * <p>Mapping: E1. <b>Câu hỏi</b>: LAZY phía inverse không có FK để biết "có thẻ hay không"; khi không có thẻ thì sao?
	 * <p><b>Kết luận</b>: vẫn hoãn được. {@code find} chỉ có 1 SELECT; chạm {@code getLoyaltyCard()} thì SELECT thứ hai
	 * trả về không dòng nào và kết quả là {@code null}. Cái giá: "có thẻ hay không" chỉ biết được sau lần SELECT đó.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S11b | E1 🔬: customer không có thẻ: find 1 SELECT, chạm getLoyaltyCard() thì SELECT thêm và nhận null")
	void s11b_e1EnhancedCustomerWithoutCardGetsNull() {
		// ── GIVEN: Bình chưa có thẻ ──
		jdbc.update("insert into " + E1 + ".customer (id, name) values (2, 'Bình')");

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			E1Customer customerBinh = em.find(E1Customer.class, 2L);
			sql.assertTotal(1);

			// ── THEN ──
			assertThat(customerBinh.getLoyaltyCard()).as("không có dòng loyalty_card nào trỏ về Bình nên kết quả là null").isNull();
			sql.print();
			sql.assertTotal(2);
		});
	}

	/**
	 * G13-S11c — E1: entity có thật sự bị enhance không
	 *
	 * <p>Mapping: E1. <b>Câu hỏi</b>: làm sao biết plugin đã chạy (thay vì tin cấu hình Gradle)?
	 * <p><b>Kết luận</b>: class của entity ở {@code src/main} implements {@code PersistentAttributeInterceptable} (do plugin chèn
	 * vào file .class), còn entity đối chứng ở {@code src/test} thì không. Chú ý {@code getClass()} vẫn là chính entity,
	 * không phải proxy con: enhancement sửa class gốc chứ không sinh class phụ như proxy.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S11c | E1: entity ở src/main bị enhance (có interceptor), entity đối chứng ở src/test thì không")
	void s11c_e1ClassIsEnhancedButControlIsNot() {
		// ── THEN ──
		assertThat(E1Customer.class)
			.as("E1Customer phải implements PersistentAttributeInterceptable do plugin chèn vào")
			.isAssignableTo(PersistentAttributeInterceptable.class);
		assertThat(PersistentAttributeInterceptable.class.isAssignableFrom(C2PlainDocument.class))
			.as("entity đối chứng nằm ở src/test nên plugin không đụng tới").isFalse();
		assertThat(PersistentAttributeInterceptable.class.isAssignableFrom(C3PlainProduct.class))
			.as("C3PlainProduct cũng ở src/test nên không bị enhance").isFalse();
	}

	/**
	 * G13-S12a — E2 🔬: {@code @Basic(fetch = LAZY)} khi có enhancement
	 *
	 * <p>Mapping: E2, cột {@code body} (text, nội dung dài) khai báo {@code @Basic(fetch = LAZY)}.
	 * <p><b>Câu hỏi</b>: có enhancement thì cột lớn có được hoãn tải không?
	 * <p><b>Kết luận</b>: CÓ. {@code find} chỉ SELECT {@code id, title}; {@code getBody()} mới SELECT riêng cột {@code body}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S12a | E2 🔬: có enhancement thì cột body bị hoãn: find không SELECT body, getBody() mới SELECT")
	void s12a_e2EnhancedDefersLazyBasicColumn() {
		// ── GIVEN ──
		jdbc.update("insert into " + E2 + ".document (id, title, body) values (1, 'Hướng dẫn', 'NỘI DUNG DÀI')");

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			E2Document document = em.find(E2Document.class, 1L);

			// ── THEN: SQL ──
			sql.print();
			sql.assertTotal(1);
			sql.assertNoStatementContains("body");
			assertThat(Hibernate.isPropertyInitialized(document, "body")).as("body chưa được tải").isFalse();

			assertThat(document.getBody()).as("đọc body thì tải đúng nội dung").isEqualTo("NỘI DUNG DÀI");
			sql.print();
			sql.assertTotal(2);
			sql.assertSomeStatementContains("select ed1_0.body");
		});
	}

	/**
	 * G13-S12b — C2 🔬: cùng mapping nhưng KHÔNG enhancement
	 *
	 * <p>Mapping: C2 (đối chứng ở src/test), y hệt E2.
	 * <p><b>Câu hỏi</b>: không có enhancement thì {@code @Basic(fetch = LAZY)} làm được gì?
	 * <p><b>Kết luận</b>: KHÔNG làm gì. Hibernate lẳng lặng bỏ qua (không báo lỗi, không cảnh báo): {@code body} nằm ngay trong
	 * SELECT đầu tiên. Đây là cái bẫy: annotation trông như có tác dụng nhưng chỉ có khi bật enhancement.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S12b | C2 🔬: không enhancement thì @Basic(fetch = LAZY) bị bỏ qua, body nằm ngay trong SELECT đầu")
	void s12b_c2WithoutEnhancementIgnoresLazyBasic() {
		// ── GIVEN ──
		jdbc.update("insert into " + C2 + ".document (id, title, body) values (1, 'Hướng dẫn', 'NỘI DUNG DÀI')");

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			C2PlainDocument document = em.find(C2PlainDocument.class, 1L);

			// ── THEN: SQL ──
			sql.print();
			sql.assertTotal(1);
			sql.assertSomeStatementContains("body");
			assertThat(Hibernate.isPropertyInitialized(document, "body")).as("LAZY bị bỏ qua nên body đã có sẵn").isTrue();
			assertThat(document.getBody()).as("đọc body không phát sinh SQL nào nữa").isEqualTo("NỘI DUNG DÀI");
			sql.assertTotal(1);
		});
	}

	/**
	 * G13-S13a — E3 🔬: dirty tracking, sửa đúng một cột
	 *
	 * <p>Mapping: E3 (đã enhance với {@code enableDirtyTracking}). Sửa {@code price}, không đụng {@code name}/{@code description}.
	 * <p><b>Câu hỏi</b>: entity tự theo dõi field đã đổi thì câu UPDATE có chỉ chứa cột đổi không?
	 * <p><b>Kết luận</b>: KHÔNG. UPDATE vẫn liệt kê cả 3 cột. Dirty tracking chỉ đổi CÁCH PHÁT HIỆN entity có bẩn không
	 * (entity tự báo, không phải Hibernate so từng field với snapshot), không đổi CÁCH VIẾT câu UPDATE. Muốn UPDATE chỉ chứa
	 * cột đổi phải dùng {@code @DynamicUpdate} (G4-U4).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S13a | E3 🔬: có dirty tracking, sửa price: UPDATE vẫn ghi cả 3 cột (name, description, price)")
	void s13a_e3DirtyTrackingStillUpdatesAllColumns() {
		// ── GIVEN ──
		jdbc.update("insert into " + E3 + ".product (id, name, price, description) values (1, 'Bàn phím', 10, 'Cơ')");

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			E3Product keyboard = em.find(E3Product.class, 1L);
			keyboard.setPrice(20);
			// [FLUSH] lúc commit: entity tự báo "price đã đổi" nên Hibernate biết cần UPDATE
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("product", 1);
		sql.assertSomeStatementContains("set description=?,name=?,price=?");

		// ── THEN: DB thật ──
		db.print(E3 + ".product");
		assertThat(db.rowById(E3 + ".product", 1L).get("price")).as("price mới đã vào DB").isEqualTo(20);
		assertThat(db.rowById(E3 + ".product", 1L).get("name")).as("cột không đổi giữ nguyên giá trị").isEqualTo("Bàn phím");
	}

	/**
	 * G13-S13b — C3 🔬: cùng thao tác, KHÔNG enhancement
	 *
	 * <p>Mapping: C3 (đối chứng), y hệt E3. <b>Câu hỏi</b>: dirty checking truyền thống cho câu UPDATE khác E3 không?
	 * <p><b>Kết luận</b>: GIỐNG HỆT S13a, cùng 3 cột. Từ ngoài nhìn vào (SQL) không phân biệt được hai cách phát hiện thay đổi;
	 * khác biệt chỉ ở chi phí bên trong khi persistence context chứa rất nhiều entity (không có snapshot để so sánh).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S13b | C3 🔬: không enhancement, sửa price: câu UPDATE giống hệt, cũng ghi cả 3 cột")
	void s13b_c3PlainDirtyCheckingGivesSameUpdate() {
		// ── GIVEN ──
		jdbc.update("insert into " + C3 + ".product (id, name, price, description) values (1, 'Bàn phím', 10, 'Cơ')");

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			C3PlainProduct keyboard = em.find(C3PlainProduct.class, 1L);
			keyboard.setPrice(20);
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("product", 1);
		sql.assertSomeStatementContains("set description=?,name=?,price=?");
		assertThat(db.rowById(C3 + ".product", 1L).get("price")).as("price mới đã vào DB").isEqualTo(20);
	}

	/**
	 * G13-S13c — E3 🔬: không sửa gì
	 *
	 * <p>Mapping: E3. <b>Câu hỏi</b>: entity enhance mà chỉ đọc, không sửa: có UPDATE thừa không?
	 * <p><b>Kết luận</b>: không. Entity không báo thay đổi nên flush không sinh UPDATE nào (giống dirty checking truyền thống).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S13c | E3 🔬: chỉ đọc, không sửa thì không có UPDATE")
	void s13c_e3ReadOnlyUsageProducesNoUpdate() {
		// ── GIVEN ──
		jdbc.update("insert into " + E3 + ".product (id, name, price, description) values (1, 'Bàn phím', 10, 'Cơ')");

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			E3Product keyboard = em.find(E3Product.class, 1L);
			assertThat(keyboard.getPrice()).as("chỉ đọc giá").isEqualTo(10);
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);
		sql.assertUpdates("product", 0);
	}
}
