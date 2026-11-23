package com.example.hibernatelab.g6_one_to_one;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.hibernatelab.g6_one_to_one.v1_fk_on_customer.V1Customer;
import com.example.hibernatelab.g6_one_to_one.v1_fk_on_customer.V1LoyaltyCard;
import com.example.hibernatelab.g6_one_to_one.v2_fk_on_card.V2Customer;
import com.example.hibernatelab.g6_one_to_one.v2_fk_on_card.V2LoyaltyCard;
import com.example.hibernatelab.g6_one_to_one.v3_bidi_owner_card.V3Customer;
import com.example.hibernatelab.g6_one_to_one.v3_bidi_owner_card.V3LoyaltyCard;
import com.example.hibernatelab.g6_one_to_one.v4_bidi_owner_customer.V4Customer;
import com.example.hibernatelab.g6_one_to_one.v4_bidi_owner_customer.V4LoyaltyCard;
import com.example.hibernatelab.support.AbstractScenarioTest;
import com.example.hibernatelab.support.SchemaInspector.ForeignKey;

/**
 * Nhóm G6, phần "FK nằm ở bảng nào?": V1 và V4 giữ FK ở {@code customer}, V2 và V3 giữ FK ở {@code loyalty_card}.
 * S01 (DDL V1 vs V2), S02 (hai thẻ một customer), S03 (V3, LAZY ở phía inverse), S04 (V4, lật ngược FK),
 * S05 (V3, chỉ set phía inverse), S11 (V1, owning side set null).
 *
 * <p>Điểm cần nắm: Hibernate chỉ biết "có hay không có" quan hệ nhờ CỘT FK. Phía owning có cột nên biết ngay (proxy được);
 * phía inverse không có cột nên phải đi hỏi bảng bên kia, và LAZY không còn tác dụng (S03).
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G6FkPlacementTest extends AbstractScenarioTest {

	private static final String V1 = "g6_v1_fk_on_customer";
	private static final String V2 = "g6_v2_fk_on_card";
	private static final String V3 = "g6_v3_bidi_owner_card";
	private static final String V4 = "g6_v4_bidi_owner_customer";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { V1, V2, V3, V4 };
	}

	/**
	 * G6-S01a — V1 🔬: DDL khi FK nằm ở bảng customer
	 *
	 * <p>Mapping: {@code Customer.loyaltyCard @OneToOne @JoinColumn(name = "loyalty_card_id")}, một chiều.
	 * <p><b>Câu hỏi</b>: FK nằm ở bảng nào, và Hibernate 7 có tự thêm UNIQUE cho cột FK của {@code @OneToOne} không?
	 * <p><b>Kết luận</b>: FK {@code customer.loyalty_card_id → loyalty_card(id)}, nullable (customer chưa có thẻ thì NULL);
	 * bảng loyalty_card không có cột FK. Về UNIQUE: xem assert bên dưới và docs/RESULTS.md.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S01a | V1 🔬: FK loyalty_card_id nằm ở bảng customer, có UNIQUE hay không thì ghi lại")
	void s01a_v1ForeignKeyLivesInCustomerTable() {
		// ── THEN: DDL ──
		schema.printDdl(V1);
		ForeignKey fk = schema.foreignKeyOnColumn(V1, "customer", "loyalty_card_id");
		assertThat(fk.targetTable()).as("FK trỏ sang bảng loyalty_card").isEqualTo(V1 + ".loyalty_card");
		assertThat(schema.columnNamed(V1, "customer", "loyalty_card_id").nullable())
			.as("customer chưa có thẻ thì FK là NULL, nên cột phải cho phép NULL").isTrue();
		assertThat(schema.hasColumn(V1, "loyalty_card", "customer_id"))
			.as("phía không giữ FK thì bảng không có cột nào trỏ về customer").isFalse();
		assertThat(schema.uniqueConstraintsOf(V1, "customer"))
			.as("Hibernate tự thêm UNIQUE cho cột FK của @OneToOne để bảo đảm 1-1 ở tầng DB")
			.contains(List.of("loyalty_card_id"));
	}

	/**
	 * G6-S01b — V2 🔬: DDL khi FK nằm ở bảng loyalty_card
	 *
	 * <p>Mapping: {@code LoyaltyCard.customer @OneToOne @JoinColumn(name = "customer_id")}, một chiều.
	 * <p><b>Câu hỏi</b>: FK lật sang bảng bên kia thì DDL khác gì? UNIQUE có đi theo không?
	 * <p><b>Kết luận</b>: FK {@code loyalty_card.customer_id → customer(id)}; bảng customer không có cột nào về thẻ.
	 * UNIQUE đi theo cột FK, nằm ở bảng giữ FK.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S01b | V2 🔬: FK customer_id nằm ở bảng loyalty_card, UNIQUE đi theo cột FK")
	void s01b_v2ForeignKeyLivesInCardTable() {
		// ── THEN: DDL ──
		schema.printDdl(V2);
		ForeignKey fk = schema.foreignKeyOnColumn(V2, "loyalty_card", "customer_id");
		assertThat(fk.targetTable()).as("FK trỏ sang bảng customer").isEqualTo(V2 + ".customer");
		assertThat(schema.hasColumn(V2, "customer", "loyalty_card_id"))
			.as("customer không giữ FK nên không có cột loyalty_card_id").isFalse();
		assertThat(schema.uniqueConstraintsOf(V2, "loyalty_card"))
			.as("UNIQUE nằm trên cột FK của bảng giữ FK")
			.contains(List.of("customer_id"));
	}

	/**
	 * G6-S02 — V2: hai thẻ cho cùng một customer
	 *
	 * <p>Mapping: V2, FK {@code loyalty_card.customer_id} (UNIQUE theo S01b).
	 * <p><b>Câu hỏi</b>: quan hệ "1-1" có thật sự được DB bảo vệ không?
	 * <p><b>Kết luận</b>: có. Thẻ thứ hai vi phạm UNIQUE {@code customer_id} nên INSERT bị DB từ chối
	 * ({@code DataIntegrityViolationException}), DB chỉ còn thẻ đầu. (Nếu không có UNIQUE thì "1-1" chỉ tồn tại trong code Java.)
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S02 | V2: hai thẻ cho cùng một customer bị DB từ chối nhờ UNIQUE customer_id")
	void s02_v2SecondCardForSameCustomerViolatesUnique() {
		// ── GIVEN: customer An đã có thẻ CARD-1 ──
		V2Customer customerAn = new V2Customer("An");
		Long customerId = inTxReturning(() -> {
			em.persist(customerAn);
			return customerAn.getId();
		});
		inTx(() -> {
			V2LoyaltyCard card1 = new V2LoyaltyCard("CARD-1");
			card1.setCustomer(em.find(V2Customer.class, customerId));
			em.persist(card1);
		});

		// ── WHEN: gán thẻ thứ hai CARD-2 cho cùng customer ──
		sql.reset();
		assertThatThrownBy(() -> inTx(() -> {
			V2LoyaltyCard card2 = new V2LoyaltyCard("CARD-2");
			card2.setCustomer(em.find(V2Customer.class, customerId));
			em.persist(card2);
			// [FLUSH] lúc commit: INSERT loyalty_card (customer_id = An) lần thứ hai
		}))
			.as("UNIQUE trên loyalty_card.customer_id không cho hai dòng cùng customer")
			.isInstanceOf(DataIntegrityViolationException.class);

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("loyalty_card", 1);

		// ── THEN: DB thật ──
		db.print(V2 + ".loyalty_card");
		assertThat(db.countOf(V2 + ".loyalty_card")).as("rollback: chỉ còn thẻ CARD-1").isEqualTo(1);
		assertThat(db.rowsOf(V2 + ".loyalty_card").get(0).get("card_no")).as("thẻ còn lại là thẻ đầu tiên").isEqualTo("CARD-1");
	}

	/**
	 * G6-S03a — V3 🔬: {@code find(customer)} với LAZY khai báo ở phía inverse
	 *
	 * <p>Mapping: V3, {@code Customer.loyaltyCard @OneToOne(mappedBy = "customer", fetch = LAZY)} (inverse side);
	 * FK nằm ở {@code loyalty_card.customer_id}.
	 * <p><b>Câu hỏi</b>: LAZY ở phía inverse có hoãn được việc tải thẻ không?
	 * <p><b>Kết luận</b>: KHÔNG. Customer không có cột nào cho biết "có thẻ hay không", nên Hibernate phải truy vấn
	 * loyalty_card ngay lúc tải customer; kết quả là {@code getLoyaltyCard()} đã khởi tạo sẵn (không phải proxy chưa tải).
	 * Cách hiểu: proxy chỉ tạo được khi biết chắc có dòng (có id); không có FK thì không biết có hay không → phải đi hỏi.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S03a | V3 🔬: find(customer) vẫn tải loyalty_card dù inverse side khai báo LAZY")
	void s03a_v3FindLoadsCardEvenThoughLazyOnInverseSide() {
		// ── GIVEN: An có thẻ CARD-1 ──
		Long customerId = seedV3CustomerWithCard("An", "CARD-1");

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			V3Customer customerAn = em.find(V3Customer.class, customerId);
			assertState(em, customerAn, MANAGED);

			// ── THEN: SQL ──
			sql.print();
			sql.assertSelects("customer", 1);
			sql.assertSelects("loyalty_card", 1); // SELECT RIÊNG (không phải join): where customer_id = ?
			assertThat(Hibernate.isInitialized(customerAn.getLoyaltyCard()))
				.as("LAZY bị bỏ qua: thẻ đã được tải ngay trong find, không phải proxy chưa khởi tạo").isTrue();
			assertThat(customerAn.getLoyaltyCard().getCardNo()).as("thẻ đúng của An").isEqualTo("CARD-1");
		});
	}

	/**
	 * G6-S03b — V3 🔬: JPQL lấy 5 customer sinh N+1
	 *
	 * <p>Mapping: như S03a.
	 * <p><b>Câu hỏi</b>: nếu {@code find} đã phải tải thẻ, JPQL nhiều customer thì sao?
	 * <p><b>Kết luận</b>: N+1. Một SELECT customer, rồi mỗi customer thêm một SELECT loyalty_card (không cần chạm vào thẻ).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S03b | V3 🔬: JPQL lấy 5 customer sinh N+1 trên loyalty_card (1 + 5)")
	void s03b_v3JpqlOverFiveCustomersCausesNPlusOne() {
		// ── GIVEN: 5 customer, mỗi người một thẻ ──
		seedV3CustomerWithCard("Khách 1", "CARD-1");
		seedV3CustomerWithCard("Khách 2", "CARD-2");
		seedV3CustomerWithCard("Khách 3", "CARD-3");
		seedV3CustomerWithCard("Khách 4", "CARD-4");
		seedV3CustomerWithCard("Khách 5", "CARD-5");

		// ── WHEN: chỉ lấy customer, không đụng tới thẻ ──
		sql.reset();
		inTx(() -> {
			List<V3Customer> customers = em.createQuery("select c from G6V3_Customer c", V3Customer.class).getResultList();
			assertThat(customers).as("5 customer").hasSize(5);
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("customer", 1);
		sql.assertSelects("loyalty_card", 5);
		sql.assertTotal(6);
	}

	/**
	 * G6-S04a — V4 🔬: lật ngược FK, {@code find(customer)} lúc này LAZY đúng
	 *
	 * <p>Mapping: V4, FK ở {@code customer.loyalty_card_id} (owning side = Customer);
	 * {@code LoyaltyCard.customer @OneToOne(mappedBy = "loyaltyCard", fetch = LAZY)} là inverse.
	 * <p><b>Câu hỏi</b>: phía có cột FK thì LAZY có hoạt động không?
	 * <p><b>Kết luận</b>: CÓ. Đọc cột {@code loyalty_card_id} là biết ngay có thẻ (và id của nó) nên tạo được proxy;
	 * chỉ khi chạm vào dữ liệu của thẻ mới SELECT loyalty_card.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S04a | V4 🔬: find(customer) giữ FK nên LAZY đúng: proxy, chạm vào mới SELECT loyalty_card")
	void s04a_v4OwningSideLoadsCardLazily() {
		// ── GIVEN ──
		Long customerId = seedV4CustomerWithCard("An", "CARD-1");

		// ── WHEN: find customer, chưa chạm vào thẻ ──
		sql.reset();
		inTx(() -> {
			V4Customer customerAn = em.find(V4Customer.class, customerId);

			// ── THEN: SQL (chưa SELECT loyalty_card) ──
			sql.print();
			sql.assertSelects("customer", 1);
			sql.assertSelects("loyalty_card", 0);
			assertThat(Hibernate.isInitialized(customerAn.getLoyaltyCard()))
				.as("thẻ là proxy chưa khởi tạo vì customer giữ FK nên biết trước id của thẻ").isFalse();

			// ── WHEN: chạm vào dữ liệu của thẻ ──
			String cardNo = customerAn.getLoyaltyCard().getCardNo();

			// ── THEN: SQL (lúc này mới SELECT) ──
			sql.assertSelects("loyalty_card", 1);
			assertThat(cardNo).as("proxy tải đúng thẻ").isEqualTo("CARD-1");
		});
	}

	/**
	 * G6-S04b — V4 🔬: {@code find(loyaltyCard)}, phía inverse bị SELECT thêm
	 *
	 * <p>Mapping: như S04a; lúc này LoyaltyCard là phía inverse.
	 * <p><b>Câu hỏi</b>: đứng từ thẻ nhìn sang customer thì sao?
	 * <p><b>Kết luận</b>: thẻ không có cột FK nên không biết customer nào trỏ tới mình: Hibernate phải truy vấn customer ngay
	 * (LAZY bị bỏ qua), giống S03. FK đặt ở đâu thì phía đó lazy được, phía kia thì không.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S04b | V4 🔬: find(loyaltyCard) là phía inverse nên phải tải customer ngay dù khai báo LAZY")
	void s04b_v4InverseSideLoadsCustomerEagerly() {
		// ── GIVEN ──
		seedV4CustomerWithCard("An", "CARD-1");
		Long cardId = (Long) db.rowsOf(V4 + ".loyalty_card").get(0).get("id");

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			V4LoyaltyCard card = em.find(V4LoyaltyCard.class, cardId);

			// ── THEN: SQL ──
			sql.print();
			sql.assertSelects("loyalty_card", 1);
			sql.assertSelects("customer", 1); // SELECT RIÊNG: where loyalty_card_id = ? (tìm customer đang trỏ tới thẻ)
			assertThat(Hibernate.isInitialized(card.getCustomer()))
				.as("phía inverse không có FK nên customer đã được tải sẵn").isTrue();
			assertThat(card.getCustomer().getName()).as("customer sở hữu thẻ").isEqualTo("An");
		});
	}

	/**
	 * G6-S05 — V3: chỉ set phía inverse
	 *
	 * <p>Mapping: V3; owning side là {@code LoyaltyCard.customer}, inverse là {@code Customer.loyaltyCard}.
	 * <p><b>Câu hỏi</b>: {@code customer.setLoyaltyCard(card)} mà quên {@code card.setCustomer(customer)} thì sao?
	 * <p><b>Kết luận</b>: không có gì được ghi. Hibernate chỉ đọc FK từ owning side, nên {@code loyalty_card.customer_id}
	 * vẫn NULL, dù trong Java hai object đã trỏ vào nhau. Giống G1 với {@code @OneToMany(mappedBy)}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S05 | V3: chỉ set phía inverse thì loyalty_card.customer_id vẫn NULL")
	void s05_v3SettingOnlyInverseSideWritesNothing() {
		// ── GIVEN: customer An và thẻ CARD-1 đã có trong DB, CHƯA nối với nhau ──
		V3Customer customerAn = new V3Customer("An");
		V3LoyaltyCard cardOfAn = new V3LoyaltyCard("CARD-1");
		inTx(() -> {
			em.persist(customerAn);
			em.persist(cardOfAn);
		});

		// ── WHEN: chỉ set phía inverse ──
		sql.reset();
		inTx(() -> {
			V3Customer managedCustomer = em.find(V3Customer.class, customerAn.getId());
			V3LoyaltyCard managedCard = em.find(V3LoyaltyCard.class, cardOfAn.getId());
			managedCustomer.setLoyaltyCard(managedCard); // inverse side: Hibernate bỏ qua khi flush
			// (cố ý KHÔNG gọi managedCard.setCustomer(managedCustomer): đó mới là owning side)
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("loyalty_card", 0);

		// ── THEN: DB thật ──
		db.print(V3 + ".loyalty_card");
		assertThat(db.rowById(V3 + ".loyalty_card", cardOfAn.getId()).get("customer_id"))
			.as("chỉ owning side (LoyaltyCard.customer) mới ghi FK; inverse side bị Hibernate bỏ qua")
			.isNull();
	}

	/**
	 * G6-S11 — V1: owning side set null
	 *
	 * <p>Mapping: V1, owning side là {@code Customer.loyaltyCard} (FK {@code customer.loyalty_card_id}), không cascade.
	 * <p><b>Câu hỏi</b>: {@code customer.setLoyaltyCard(null)} làm gì với dòng thẻ?
	 * <p><b>Kết luận</b>: chỉ {@code UPDATE customer SET loyalty_card_id = NULL}. Dòng thẻ KHÔNG bị xoá (mồ côi, không ai trỏ tới),
	 * vì không có orphanRemoval/cascade REMOVE. Muốn xoá thẻ phải làm tường minh hoặc dùng orphanRemoval (G6-S09).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S11 | V1: customer.setLoyaltyCard(null) chỉ UPDATE FK về NULL, dòng thẻ vẫn còn (mồ côi)")
	void s11_v1OwningSideSetNullLeavesCardRowBehind() {
		// ── GIVEN: An có thẻ CARD-1 ──
		V1LoyaltyCard card = new V1LoyaltyCard("CARD-1");
		V1Customer customerAn = new V1Customer("An");
		inTx(() -> {
			em.persist(card); // không cascade nên phải persist thẻ TRƯỚC
			customerAn.setLoyaltyCard(card);
			em.persist(customerAn);
		});

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			V1Customer managedCustomer = em.find(V1Customer.class, customerAn.getId());
			managedCustomer.setLoyaltyCard(null);
			// [FLUSH] lúc commit: dirty checking thấy FK đổi từ id thẻ sang null
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("customer", 1);
		sql.assertDeletes("loyalty_card", 0);

		// ── THEN: DB thật ──
		db.print(V1 + ".customer");
		db.print(V1 + ".loyalty_card");
		assertThat(db.rowById(V1 + ".customer", customerAn.getId()).get("loyalty_card_id")).as("FK về NULL").isNull();
		assertThat(db.countOf(V1 + ".loyalty_card")).as("dòng thẻ không bị xoá: nó chỉ mất người trỏ tới").isEqualTo(1);
	}

	// ── dữ liệu mẫu dùng chung của file này (mỗi helper chỉ tạo đúng 1 customer + 1 thẻ) ──

	/** V3: thẻ giữ FK nên phải set {@code card.customer}. Trả về id của customer. */
	private Long seedV3CustomerWithCard(String customerName, String cardNo) {
		V3Customer customer = new V3Customer(customerName);
		V3LoyaltyCard card = new V3LoyaltyCard(cardNo);
		card.setCustomer(customer);
		return inTxReturning(() -> {
			em.persist(customer);
			em.persist(card);
			return customer.getId();
		});
	}

	/** V4: customer giữ FK nên phải persist thẻ trước rồi mới gán {@code customer.loyaltyCard}. Trả về id của customer. */
	private Long seedV4CustomerWithCard(String customerName, String cardNo) {
		V4LoyaltyCard card = new V4LoyaltyCard(cardNo);
		V4Customer customer = new V4Customer(customerName);
		return inTxReturning(() -> {
			em.persist(card);
			customer.setLoyaltyCard(card);
			em.persist(customer);
			return customer.getId();
		});
	}
}
