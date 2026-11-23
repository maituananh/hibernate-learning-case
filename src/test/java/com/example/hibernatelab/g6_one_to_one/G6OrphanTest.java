package com.example.hibernatelab.g6_one_to_one;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.g6_one_to_one.v3_bidi_owner_card.V3Customer;
import com.example.hibernatelab.g6_one_to_one.v3_bidi_owner_card.V3LoyaltyCard;
import com.example.hibernatelab.g6_one_to_one.v7_orphan.V7Customer;
import com.example.hibernatelab.g6_one_to_one.v7_orphan.V7LoyaltyCard;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G6, phần {@code orphanRemoval} trên {@code @OneToOne}: V7 (cascade ALL + orphanRemoval) so với V3 (baseline).
 * S09 (gán {@code null}), S10 (thay thẻ cũ bằng thẻ mới, liên hệ G3-S10).
 *
 * <p>Điểm cần nắm: {@code setLoyaltyCard(null)} trên phía inverse không đổi được FK (S05), nhưng orphanRemoval
 * không cần FK, nó so sánh giá trị property với snapshot lúc tải rồi DELETE phần "mồ côi".
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G6OrphanTest extends AbstractScenarioTest {

	private static final String V3 = "g6_v3_bidi_owner_card";
	private static final String V7 = "g6_v7_orphan";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { V3, V7 };
	}

	/**
	 * G6-S09a — V7: {@code customer.setLoyaltyCard(null)} có orphanRemoval
	 *
	 * <p>Mapping: V7, {@code Customer.loyaltyCard (mappedBy = "customer", cascade = ALL, orphanRemoval = true)}.
	 * <p><b>Câu hỏi</b>: gỡ thẻ khỏi customer thì dòng thẻ ra sao?
	 * <p><b>Kết luận</b>: thẻ bị DELETE lúc flush (orphanRemoval), dù Customer chỉ là inverse side.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S09a | V7: customer.setLoyaltyCard(null) → DELETE thẻ nhờ orphanRemoval")
	void s09a_v7SetNullDeletesTheCard() {
		// ── GIVEN: An có thẻ CARD-1 (cascade ALL nên persist customer là đủ) ──
		Long customerId = seedV7CustomerWithCard("An", "CARD-1");

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			V7Customer managedCustomer = em.find(V7Customer.class, customerId);
			V7LoyaltyCard oldCard = managedCustomer.getLoyaltyCard();
			assertState(em, oldCard, MANAGED); // [LIFECYCLE] thẻ đang MANAGED
			managedCustomer.setLoyaltyCard(null);
			// [FLUSH] lúc commit: thẻ có trong snapshot nhưng không còn trong property → orphan → DELETE
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("loyalty_card", 1);

		// ── THEN: DB thật ──
		db.print(V7 + ".loyalty_card");
		assertThat(db.countOf(V7 + ".loyalty_card")).as("thẻ bị orphanRemoval xoá").isZero();
		assertThat(db.countOf(V7 + ".customer")).as("customer còn nguyên").isEqualTo(1);
	}

	/**
	 * G6-S09b — V3: {@code customer.setLoyaltyCard(null)} KHÔNG có orphanRemoval
	 *
	 * <p>Mapping: V3, {@code Customer.loyaltyCard (mappedBy = "customer", fetch = LAZY)} (inverse, không cascade).
	 * <p><b>Câu hỏi</b>: cùng thao tác đó ở baseline thì sao?
	 * <p><b>Kết luận</b>: KHÔNG có SQL ghi nào. Phía inverse không ghi FK, nên {@code loyalty_card.customer_id} vẫn trỏ
	 * về customer: lần sau tải lại, thẻ vẫn "thuộc về" An. Muốn tách thật phải set {@code card.setCustomer(null)} (owning side).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S09b | V3: customer.setLoyaltyCard(null) không có SQL ghi nào, thẻ vẫn trỏ về customer")
	void s09b_v3SetNullOnInverseSideDoesNothing() {
		// ── GIVEN: An có thẻ CARD-1 ──
		V3Customer customerAn = new V3Customer("An");
		V3LoyaltyCard card = new V3LoyaltyCard("CARD-1");
		card.setCustomer(customerAn);
		inTx(() -> {
			em.persist(customerAn);
			em.persist(card);
		});

		// ── WHEN ──
		sql.reset();
		inTx(() -> {
			V3Customer managedCustomer = em.find(V3Customer.class, customerAn.getId());
			managedCustomer.setLoyaltyCard(null); // inverse side: không ghi FK, không orphanRemoval
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("loyalty_card", 0);
		sql.assertDeletes("loyalty_card", 0);

		// ── THEN: DB thật ──
		db.print(V3 + ".loyalty_card");
		assertThat(db.rowById(V3 + ".loyalty_card", card.getId()).get("customer_id"))
			.as("FK nằm ở owning side (card.customer) mà ta không đụng tới nên vẫn trỏ về An")
			.isEqualTo(customerAn.getId());
	}

	/**
	 * G6-S10a — V7 🔬: thay thẻ cũ bằng thẻ mới trong MỘT lần flush
	 *
	 * <p>Mapping: V7; UNIQUE {@code loyalty_card.customer_id} (xem G6-S01b).
	 * <p><b>Câu hỏi</b>: có vi phạm UNIQUE như G3-S10a không (thẻ mới INSERT trước, thẻ cũ DELETE sau)?
	 * <p><b>Kết luận</b>: KHÔNG vi phạm. Khác hẳn G3-S10a (collection): DELETE thẻ cũ chạy TRƯỚC, INSERT thẻ mới chạy sau.
	 * Lý do (theo ActionQueue của Hibernate): orphan removal của quan hệ to-one có hàng đợi riêng, được chạy trước mọi INSERT,
	 * chính là để không đụng UNIQUE của cột FK.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S10a | V7 🔬: thay thẻ cũ bằng thẻ mới trong một flush: DELETE chạy trước INSERT, không vi phạm unique")
	void s10a_v7ReplaceCardInOneFlush() {
		// ── GIVEN ──
		Long customerId = seedV7CustomerWithCard("An", "CARD-OLD");

		// ── WHEN ──
		sql.reset();
		Throwable failure = catchThrowable(() -> inTx(() -> {
			V7Customer managedCustomer = em.find(V7Customer.class, customerId);
			V7LoyaltyCard newCard = new V7LoyaltyCard("CARD-NEW");
			newCard.setCustomer(managedCustomer);
			managedCustomer.setLoyaltyCard(newCard);
		}));

		// ── THEN ──
		assertThat(failure).as("DELETE thẻ cũ chạy trước INSERT thẻ mới nên UNIQUE customer_id không bị vi phạm").isNull();

		// ── THEN: SQL ──
		sql.print();
		sql.assertInOrder("delete from " + V7 + ".loyalty_card", "insert into " + V7 + ".loyalty_card");

		// ── THEN: DB thật ──
		db.print(V7 + ".loyalty_card");
		assertThat(db.rowsOf(V7 + ".loyalty_card")).as("chỉ còn thẻ mới").hasSize(1);
		assertThat(db.rowsOf(V7 + ".loyalty_card").get(0).get("card_no")).as("đó là CARD-NEW").isEqualTo("CARD-NEW");
	}

	/**
	 * G6-S10b — V7 🔬: persist thẻ mới TƯỜNG MINH rồi mới gán vào customer
	 *
	 * <p>Mapping: V7; UNIQUE {@code loyalty_card.customer_id}.
	 * <p><b>Câu hỏi</b>: S10a không vi phạm UNIQUE vì thẻ mới do cascade PERSIST tạo ra lúc flush. Nếu ta tự
	 * {@code em.persist(newCard)} thì INSERT có chạy trước DELETE không?
	 * <p><b>Kết luận</b>: vẫn KHÔNG vi phạm, vẫn DELETE trước INSERT: orphan removal của to-one chạy trước INSERT bất kể
	 * thẻ mới được persist bằng cách nào. Ta không cần {@code em.flush()} xen giữa như G3-S10c.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G6-S10b | V7 🔬: em.persist(thẻ mới) tường minh trước khi gán vẫn DELETE trước INSERT")
	void s10b_v7ExplicitPersistOfNewCardBeforeAssigning() {
		// ── GIVEN ──
		Long customerId = seedV7CustomerWithCard("An", "CARD-OLD");

		// ── WHEN ──
		sql.reset();
		Throwable failure = catchThrowable(() -> inTx(() -> {
			V7Customer managedCustomer = em.find(V7Customer.class, customerId);
			V7LoyaltyCard newCard = new V7LoyaltyCard("CARD-NEW");
			newCard.setCustomer(managedCustomer);
			em.persist(newCard); // xếp INSERT thẻ mới vào ActionQueue ngay tại đây
			managedCustomer.setLoyaltyCard(newCard);
		}));

		// ── THEN ──
		assertThat(failure).as("orphan removal của to-one chạy trước INSERT nên không vi phạm UNIQUE").isNull();

		// ── THEN: SQL ──
		sql.print();
		sql.assertInOrder("delete from " + V7 + ".loyalty_card", "insert into " + V7 + ".loyalty_card");

		// ── THEN: DB thật ──
		db.print(V7 + ".loyalty_card");
		assertThat(db.rowsOf(V7 + ".loyalty_card")).as("chỉ còn thẻ mới").hasSize(1);
		assertThat(db.rowsOf(V7 + ".loyalty_card").get(0).get("card_no")).as("đó là CARD-NEW").isEqualTo("CARD-NEW");
	}

	/** V7: cascade ALL nên chỉ cần persist customer, thẻ đi theo. Trả về id của customer. */
	private Long seedV7CustomerWithCard(String customerName, String cardNo) {
		V7Customer customer = new V7Customer(customerName);
		V7LoyaltyCard card = new V7LoyaltyCard(cardNo);
		card.setCustomer(customer); // owning side: ghi FK
		customer.setLoyaltyCard(card); // inverse side: để cascade PERSIST thấy thẻ
		return inTxReturning(() -> {
			em.persist(customer);
			return customer.getId();
		});
	}
}
