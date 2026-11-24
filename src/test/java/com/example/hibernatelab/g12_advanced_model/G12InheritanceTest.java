package com.example.hibernatelab.g12_advanced_model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g12_advanced_model.h1_single_table.H1BankPayment;
import com.example.hibernatelab.g12_advanced_model.h1_single_table.H1CardPayment;
import com.example.hibernatelab.g12_advanced_model.h1_single_table.H1Payment;
import com.example.hibernatelab.g12_advanced_model.h1_single_table.H1Receipt;
import com.example.hibernatelab.g12_advanced_model.h2_joined.H2BankPayment;
import com.example.hibernatelab.g12_advanced_model.h2_joined.H2CardPayment;
import com.example.hibernatelab.g12_advanced_model.h2_joined.H2Payment;
import com.example.hibernatelab.g12_advanced_model.h3_table_per_class.H3BankPayment;
import com.example.hibernatelab.g12_advanced_model.h3_table_per_class.H3CardPayment;
import com.example.hibernatelab.g12_advanced_model.h3_table_per_class.H3Payment;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G12, chủ đề {@code @Inheritance}: S06 (SINGLE_TABLE), S07 (JOINED), S08 (TABLE_PER_CLASS), S09 (proxy tới lớp cha).
 *
 * <p>Dữ liệu chung cho cả ba chiến lược: 2 payment, một thẻ ({@code CardPayment}, id 1) và một chuyển khoản ({@code BankPayment}, id 2).
 * Câu hỏi chung: query đa hình {@code select p from Payment p} chạy bao nhiêu SQL, và SQL đó trông thế nào.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G12InheritanceTest extends AbstractScenarioTest {

	private static final String H1 = "g12_h1_single_table";
	private static final String H2 = "g12_h2_joined";
	private static final String H3 = "g12_h3_table_per_class";

	@Autowired
	private JdbcTemplate jdbc;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { H1, H2, H3 };
	}

	private void seedSingleTable() {
		jdbc.update("insert into " + H1 + ".payment (id, amount, payment_type, card_last4) values (1, 100, 'CARD', '4242')");
		jdbc.update("insert into " + H1 + ".payment (id, amount, payment_type, bank_name) values (2, 200, 'BANK', 'ACB')");
	}

	/**
	 * G12-S06a — H1 SINGLE_TABLE: DDL một bảng, cột lớp con phải NULLABLE
	 *
	 * <p>Mapping: H1 (baseline). <b>Câu hỏi</b>: DDL ra sao, và cột của lớp con có đặt NOT NULL được không?
	 * <p><b>Kết luận</b>: một bảng {@code payment} có cột {@code payment_type} (discriminator); {@code card_last4} và {@code bank_name}
	 * phải NULLABLE vì dòng của lớp con kia để trống. <b>Khác giả thuyết ban đầu</b>: Hibernate 7.4 KHÔNG bỏ qua
	 * {@code nullable = false} ở lớp con, nó vẫn sinh {@code NOT NULL} và INSERT dòng của lớp con kia bị DB từ chối
	 * (đã tái hiện khi chạy lần đầu, xem docs/RESULTS.md). Ràng buộc "thẻ thì bắt buộc có card_last4" chỉ kiểm tra được ở Java.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S06a | SINGLE_TABLE: 1 bảng + cột discriminator, cột lớp con phải nullable")
	void s06a_singleTableDdl() {
		// ── THEN: DDL ──
		schema.printDdl(H1);
		assertThat(schema.tablesOf(H1)).as("cả cây chỉ một bảng payment (cộng bảng receipt của S09)").containsExactlyInAnyOrder("payment", "receipt");
		assertThat(schema.hasColumn(H1, "payment", "payment_type")).as("cột discriminator phân biệt CARD/BANK").isTrue();
		assertThat(schema.columnNamed(H1, "payment", "card_last4").nullable())
			.as("dòng BANK để trống card_last4 nên cột phải nullable (nullable=false sẽ sinh NOT NULL và làm hỏng INSERT dòng BANK)").isTrue();
		assertThat(schema.columnNamed(H1, "payment", "bank_name").nullable()).as("tương tự: dòng CARD để trống bank_name").isTrue();
	}

	/**
	 * G12-S06b — H1 SINGLE_TABLE: query đa hình là 1 SELECT, không join
	 *
	 * <p>Mapping: H1. <b>Câu hỏi</b>: {@code select p from Payment p} chạy mấy SQL, trả đúng kiểu lớp con không?
	 * <p><b>Kết luận</b>: 1 SELECT trên bảng {@code payment}, không join; Hibernate đọc {@code payment_type} để tạo đúng
	 * {@code CardPayment} / {@code BankPayment}. Nhanh nhất ba chiến lược, đổi lại cột lớp con nullable.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S06b | SINGLE_TABLE: query đa hình = 1 SELECT, không join, đúng kiểu lớp con")
	void s06b_singleTablePolymorphicQuery() {
		// ── GIVEN ──
		seedSingleTable();
		sql.reset();

		// ── WHEN ──
		List<H1Payment> payments = inTxReturning(() -> em.createQuery("select p from G12H1_Payment p order by p.id", H1Payment.class).getResultList());

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);
		sql.assertNoStatementContains("join");
		// ── THEN: kiểu thật ──
		assertThat(payments.get(0)).as("dòng có payment_type = CARD phải thành CardPayment").isInstanceOf(H1CardPayment.class);
		assertThat(payments.get(1)).as("dòng có payment_type = BANK phải thành BankPayment").isInstanceOf(H1BankPayment.class);
	}

	/**
	 * G12-S07a — H2 JOINED: DDL mỗi lớp một bảng, cột lớp con được NOT NULL
	 *
	 * <p>Mapping: H2 (JOINED). <b>Câu hỏi</b>: DDL ra sao, {@code nullable = false} có được giữ không?
	 * <p><b>Kết luận</b>: 3 bảng; {@code card_payment.id} vừa là PK vừa là FK tới {@code payment.id}. Vì cột của lớp con nằm ở bảng riêng
	 * nên {@code NOT NULL} được giữ, đây là ưu điểm so với SINGLE_TABLE (S06a).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S07a | JOINED: 3 bảng, PK lớp con là FK tới lớp cha, cột lớp con NOT NULL")
	void s07a_joinedDdl() {
		// ── THEN: DDL ──
		schema.printDdl(H2);
		assertThat(schema.tablesOf(H2)).as("mỗi lớp một bảng, kể cả lớp cha").containsExactlyInAnyOrder("payment", "card_payment", "bank_payment");
		assertThat(schema.primaryKeyOf(H2, "card_payment")).as("PK của bảng lớp con chính là id").containsExactly("id");
		assertThat(schema.foreignKeyOnColumn(H2, "card_payment", "id").targetTable()).as("và đồng thời là FK trỏ về bảng lớp cha").endsWith("payment");
		assertThat(schema.columnNamed(H2, "card_payment", "card_last4").nullable()).as("cột lớp con nằm ở bảng riêng nên giữ được NOT NULL").isFalse();
	}

	/**
	 * G12-S07b — H2 JOINED: query đa hình join mọi bảng lớp con
	 *
	 * <p>Mapping: H2. <b>Câu hỏi</b>: {@code select p from Payment p} chạy SQL gì?
	 * <p><b>Kết luận</b>: 1 SELECT với {@code left join} tới cả {@code card_payment} và {@code bank_payment}; số join tăng theo số lớp con.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S07b | JOINED: query đa hình = 1 SELECT có left join tới mọi bảng lớp con")
	void s07b_joinedPolymorphicQuery() {
		// ── GIVEN ──
		jdbc.update("insert into " + H2 + ".payment (id, amount) values (1, 100), (2, 200)");
		jdbc.update("insert into " + H2 + ".card_payment (id, card_last4) values (1, '4242')");
		jdbc.update("insert into " + H2 + ".bank_payment (id, bank_name) values (2, 'ACB')");
		sql.reset();

		// ── WHEN ──
		List<H2Payment> payments = inTxReturning(() -> em.createQuery("select p from G12H2_Payment p order by p.id", H2Payment.class).getResultList());

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);
		sql.assertSomeStatementContains("left join");
		sql.assertSelects("card_payment", 1);
		sql.assertSelects("bank_payment", 1);
		// ── THEN: kiểu thật ──
		assertThat(payments.get(0)).as("id 1 có dòng ở card_payment").isInstanceOf(H2CardPayment.class);
		assertThat(payments.get(1)).as("id 2 có dòng ở bank_payment").isInstanceOf(H2BankPayment.class);
	}

	/**
	 * G12-S08 — H3 TABLE_PER_CLASS: query đa hình dùng {@code union all} 🔬
	 *
	 * <p>Mapping: H3. <b>Câu hỏi</b>: DDL có bảng cho lớp cha trừu tượng không, và query đa hình chạy SQL gì?
	 * <p><b>Kết luận</b>: không có bảng {@code payment} (lớp cha trừu tượng), chỉ {@code card_payment} và {@code bank_payment}, mỗi bảng
	 * lặp lại cột chung ({@code amount}). Query đa hình là 1 SELECT gồm {@code union all} giữa các bảng lớp con.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S08 | 🔬 TABLE_PER_CLASS: không có bảng lớp cha, query đa hình = union all")
	void s08_tablePerClassUsesUnionAll() {
		// ── GIVEN ──
		jdbc.update("insert into " + H3 + ".card_payment (id, amount, card_last4) values (1, 100, '4242')");
		jdbc.update("insert into " + H3 + ".bank_payment (id, amount, bank_name) values (2, 200, 'ACB')");
		sql.reset();

		// ── WHEN ──
		List<H3Payment> payments = inTxReturning(() -> em.createQuery("select p from G12H3_Payment p order by p.id", H3Payment.class).getResultList());

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);
		sql.assertSomeStatementContains("union all");
		// ── THEN: kiểu thật + DDL ──
		assertThat(payments.get(0)).as("id 1 nằm ở bảng card_payment").isInstanceOf(H3CardPayment.class);
		assertThat(payments.get(1)).as("id 2 nằm ở bảng bank_payment").isInstanceOf(H3BankPayment.class);
		assertThat(schema.tablesOf(H3)).as("lớp cha trừu tượng không có bảng; mỗi lớp cụ thể một bảng đủ cột")
			.containsExactlyInAnyOrder("card_payment", "bank_payment");
	}

	/**
	 * G12-S09 — H1: {@code @ManyToOne LAZY} trỏ tới lớp cha trừu tượng cho proxy của lớp CHA, {@code instanceof} lớp con sai 🔬
	 *
	 * <p>Mapping: H1 + {@code Receipt.payment} (LAZY). <b>Câu hỏi</b>: {@code receipt.getPayment()} là loại gì, {@code instanceof CardPayment} đúng hay sai?
	 * <p><b>Kết luận</b>: là proxy sinh từ {@code Payment} (chưa chạy SQL nào cho payment). Nó KHÔNG phải {@code CardPayment} nên
	 * {@code instanceof CardPayment} là {@code false} dù dòng thật là thẻ; ép kiểu sẽ lỗi {@code ClassCastException}.
	 * Phải gỡ proxy bằng {@code Hibernate.unproxy(...)} (lúc đó mới có 1 SELECT) rồi mới kiểm tra kiểu.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G12-S09 | 🔬 ManyToOne LAZY tới lớp cha trừu tượng: proxy là lớp cha, instanceof lớp con sai")
	void s09_proxyOfAbstractSuperclassIsNotTheSubclass() {
		// ── GIVEN ──
		seedSingleTable();
		jdbc.update("insert into " + H1 + ".receipt (id, receipt_no, payment_id) values (1, 'RC-1', 1)");
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			H1Receipt receiptOne = em.find(H1Receipt.class, 1L);
			H1Payment paymentOfReceipt = receiptOne.getPayment(); // [LIFECYCLE] proxy chưa nạp
			sql.assertSelects("payment", 0);

			// ── THEN ──
			assertThat(paymentOfReceipt).as("quan hệ LAZY nên nhận về proxy").isInstanceOf(HibernateProxy.class);
			assertThat(paymentOfReceipt instanceof H1CardPayment)
				.as("proxy sinh từ lớp cha nên KHÔNG phải CardPayment, dù dòng thật là thẻ").isFalse();
			assertThat(Hibernate.isInitialized(paymentOfReceipt)).as("chỉ so sánh kiểu, chưa chạm dữ liệu nên chưa SELECT").isFalse();

			H1Payment realPayment = (H1Payment) Hibernate.unproxy(paymentOfReceipt); // [FLUSH] không có, nhưng SELECT chạy ở đây
			assertThat(realPayment).as("sau unproxy mới ra đối tượng thật là CardPayment").isInstanceOf(H1CardPayment.class);
		});
		sql.print();
		sql.assertSelects("payment", 1);
	}
}
