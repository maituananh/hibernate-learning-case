package com.example.hibernatelab.g12_advanced_model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.bootfail.g12s01.MissingOverrideCustomer;
import com.example.hibernatelab.g12_advanced_model.e1_embedded.E1Customer;
import com.example.hibernatelab.support.AbstractScenarioTest;
import com.example.hibernatelab.support.MappingBootstrap;

/**
 * Nhóm G12, chủ đề {@code @Embeddable}: S01 (DDL và thiếu override), S02 (toàn NULL), S03 (sửa tại chỗ).
 *
 * <p>Model E1: {@code Customer} có hai {@code Address} (billing, shipping) nhúng thẳng vào bảng {@code customer}.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G12EmbeddableTest extends AbstractScenarioTest {

	private static final String E1 = "g12_e1_embedded";
	private static final String CUSTOMER = E1 + ".customer";

	@Autowired
	private JdbcTemplate jdbc;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { E1 };
	}

	/**
	 * G12-S01a — E1: một {@code @Embeddable} dùng hai lần + {@code @AttributeOverride} cho DDL phẳng, không có bảng address
	 *
	 * <p>Mapping: E1 (baseline). <b>Câu hỏi</b>: DDL của bảng customer trông thế nào, có bảng address không?
	 * <p><b>Kết luận</b>: không có bảng address. Bảng customer có 6 cột phẳng {@code billing_*} và {@code shipping_*}
	 * do {@code @AttributeOverride} đổi tên; embeddable chỉ là cách gom field trong Java, không phải thực thể trong DB.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S01a | @Embeddable dùng 2 lần + @AttributeOverride → 6 cột phẳng, không có bảng address")
	void s01a_embeddableBecomesFlatColumns() {
		// ── THEN: DDL ──
		schema.printDdl(E1);
		assertThat(schema.tablesOf(E1)).as("embeddable không có bảng riêng: chỉ còn bảng customer").containsExactly("customer");
		assertThat(schema.hasColumn(E1, "customer", "billing_street")).as("override đổi street → billing_street").isTrue();
		assertThat(schema.hasColumn(E1, "customer", "billing_zip_code")).as("override đổi zipCode → billing_zip_code").isTrue();
		assertThat(schema.hasColumn(E1, "customer", "shipping_city")).as("override đổi city → shipping_city").isTrue();
		assertThat(schema.hasColumn(E1, "customer", "street")).as("tên mặc định street không tồn tại vì cả hai lần dùng đều bị override").isFalse();
	}

	/**
	 * G12-S01b — [BOOT-FAIL] thiếu {@code @AttributeOverride}: hai field cùng kiểu trùng cột
	 *
	 * <p>Mapping: entity sai ở {@code bootfail/g12s01}. <b>Câu hỏi</b>: dùng cùng {@code @Embeddable} hai lần mà quên override thì sao?
	 * <p><b>Kết luận</b>: Hibernate không khởi động được vì cả hai field cùng ánh xạ vào cột {@code street}, {@code city}, {@code zip_code}.
	 * Lỗi nằm ở lúc dựng metadata, không cần DB.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S01b | [BOOT-FAIL] thiếu @AttributeOverride → trùng cột, không khởi động được")
	void s01b_missingOverrideFailsAtBoot() {
		// ── WHEN ──
		Throwable failure = MappingBootstrap.tryBuild(MissingOverrideCustomer.class);

		// ── THEN ──
		System.out.println("[BOOT-FAIL] " + failure);
		assertThat(failure).as("hai embedded cùng kiểu mà không override thì Hibernate từ chối khởi động").isNotNull();
		assertThat(failure.getMessage()).as("thông báo phải nói về cột bị khai báo trùng").containsIgnoringCase("duplicate");
	}

	/**
	 * G12-S02 — E1 🔬: mọi cột của embeddable đều NULL thì đọc lại là {@code null}, không phải object rỗng
	 *
	 * <p>Mapping: E1. <b>Câu hỏi</b>: shipping_* toàn NULL trong DB, {@code getShippingAddress()} trả gì?
	 * <p><b>Kết luận</b>: {@code null}. Hibernate coi "mọi cột NULL" là "không có giá trị". Hệ quả: không phân biệt được
	 * "địa chỉ rỗng" với "không có địa chỉ"; chỉ cần MỘT cột khác NULL là object được tạo (S02b).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S02a | 🔬 mọi cột embeddable NULL → đọc lại là null")
	void s02a_allColumnsNullReadsBackAsNull() {
		// ── GIVEN ──
		jdbc.update("insert into " + CUSTOMER + " (id, name, billing_street, billing_city, billing_zip_code) values (1, 'Alice', '1 Hang Bac', 'Ha Noi', '100000')");
		sql.reset();

		// ── WHEN ──
		E1Customer customerAlice = inTxReturning(() -> {
			E1Customer found = em.find(E1Customer.class, 1L);
			found.getBillingAddress().getCity(); // chạm vào để chắc chắn đã nạp
			return found;
		});

		// ── THEN ──
		assertThat(customerAlice.getBillingAddress()).as("billing có dữ liệu nên là object").isNotNull();
		assertThat(customerAlice.getShippingAddress()).as("shipping_* đều NULL nên Hibernate trả null, không phải Address rỗng").isNull();
	}

	/**
	 * G12-S02b — E1 🔬: chỉ MỘT cột khác NULL thì object được tạo, các field còn lại là {@code null}
	 *
	 * <p>Mapping: E1. <b>Câu hỏi</b>: shipping chỉ có city, các cột khác NULL?
	 * <p><b>Kết luận</b>: {@code getShippingAddress()} khác null, {@code street} là null. Ranh giới: tất cả NULL → null; ít nhất một giá trị → object.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S02b | 🔬 chỉ một cột embeddable khác NULL → object tồn tại, field còn lại null")
	void s02b_oneNonNullColumnCreatesObject() {
		// ── GIVEN ──
		jdbc.update("insert into " + CUSTOMER + " (id, name, shipping_city) values (1, 'Bob', 'Hue')");

		// ── WHEN ──
		E1Customer customerBob = inTxReturning(() -> em.find(E1Customer.class, 1L));

		// ── THEN ──
		assertThat(customerBob.getShippingAddress()).as("có một cột khác NULL nên Address được tạo").isNotNull();
		assertThat(customerBob.getShippingAddress().getCity()).as("cột có dữ liệu được nạp").isEqualTo("Hue");
		assertThat(customerBob.getShippingAddress().getStreet()).as("cột NULL thành field null").isNull();
		assertThat(customerBob.getBillingAddress()).as("billing toàn NULL nên là null").isNull();
	}

	/**
	 * G12-S03 — E1 🔬: sửa field của embeddable tại chỗ VẪN được dirty checking phát hiện (đối chiếu G8-S07)
	 *
	 * <p>Mapping: E1. <b>Câu hỏi</b>: {@code customer.getBillingAddress().setCity(...)} (không gán lại cả Address) có sinh UPDATE không?
	 * <p><b>Kết luận</b>: có 1 UPDATE vào bảng customer. Embeddable thường là một phần của entity nên nằm trong dirty checking;
	 * khác {@code @EmbeddedId} ở G8-S07 (id bất biến, sửa bị bỏ qua im lặng).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S03 | 🔬 sửa field của embeddable tại chỗ → có UPDATE (khác @EmbeddedId ở G8-S07)")
	void s03_mutatingEmbeddableInPlaceIsDirty() {
		// ── GIVEN ──
		jdbc.update("insert into " + CUSTOMER + " (id, name, billing_street, billing_city, billing_zip_code) values (1, 'Alice', '1 Hang Bac', 'Ha Noi', '100000')");
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			E1Customer customerAlice = em.find(E1Customer.class, 1L); // [LIFECYCLE] MANAGED
			customerAlice.getBillingAddress().setCity("Da Nang"); // sửa tại chỗ, KHÔNG gọi save/merge
			// [FLUSH] lúc commit: dirty checking so snapshot cũ với giá trị mới
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("customer", 1);
		// ── THEN: DB thật ──
		db.print(CUSTOMER);
		assertThat(db.rowById(CUSTOMER, 1L).get("billing_city")).as("city mới đã được ghi xuống DB").isEqualTo("Da Nang");
	}
}
