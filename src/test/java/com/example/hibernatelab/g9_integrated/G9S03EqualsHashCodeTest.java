package com.example.hibernatelab.g9_integrated;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.g9_integrated.s03_equals_hashcode.a_hash_by_id.OrderItem;
import com.example.hibernatelab.g9_integrated.s03_equals_hashcode.a_hash_by_id.PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * G9-S03 ⭐ {@code equals/hashCode} theo id sinh tự động + {@code HashSet}.
 *
 * <p>Hai biến thể của {@code OrderItem}: (a) hashCode theo id, (b) hashCode theo business key {@code lineCode} bất biến.
 * Hai biến thể có tên class trùng nhau (khác package) nên phía dưới dùng tên đầy đủ cho biến thể (b).
 * <p>Điểm cần nắm: {@code HashSet} xếp phần tử vào "ngăn" theo hashCode LÚC THÊM VÀO. Nếu hashCode đổi sau đó thì phần tử vẫn nằm ngăn cũ,
 * tìm bằng hash mới sẽ trượt: {@code contains}/{@code remove} trả về false mà không báo lỗi gì.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G9S03EqualsHashCodeTest extends AbstractScenarioTest {

	private static final String SCHEMA_A = "g9_s03a_hash_by_id";
	private static final String SCHEMA_B = "g9_s03b_business_key";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { SCHEMA_A, SCHEMA_B };
	}

	/**
	 * G9-S03a — hashCode theo id: item mới (id null) nằm trong Set, persist gán id → hashCode đổi → remove() trượt
	 *
	 * <p>Mapping: {@code items} là {@code Set}, INVERSE, cascade ALL, orphanRemoval; {@code OrderItem.hashCode()} = hash của id.
	 * <p><b>Câu hỏi</b>: sau persist, {@code order.removeItem(item)} có gỡ được item và kích hoạt orphanRemoval không?
	 * <p><b>Kết luận</b>: không. {@code Set.remove} trả về false, item vẫn còn trong Set lẫn DB; orphanRemoval không chạy (0 DELETE).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S03a | hashCode theo id: sau persist remove() trả về false, orphanRemoval không chạy")
	void s03a_hashCodeBasedOnGeneratedIdBreaksSetRemoveAfterPersist() {
		// ── GIVEN ──
		PurchaseOrder orderA = new PurchaseOrder("A-001");
		OrderItem itemPen = new OrderItem("PEN-01");
		orderA.addItem(itemPen); // lúc này id = null → hashCode = hash(null), item nằm trong "ngăn" của hash(null)

		// ── WHEN ──
		sql.reset();
		boolean removed = inTxReturning(() -> {
			em.persist(orderA); // cascade PERSIST: itemPen nhận id từ sequence → hashCode ĐỔI, nhưng vẫn nằm ngăn cũ trong Set
			// [LIFECYCLE] orderA, itemPen: TRANSIENT → MANAGED (id đã có, CHƯA có INSERT)
			assertState(em, itemPen, MANAGED);
			em.flush(); // [FLUSH] INSERT purchase_order, INSERT order_item
			boolean stillContained = orderA.getItems().contains(itemPen);
			assertThat(stillContained).as("hashCode mới (theo id) trỏ sang ngăn khác nên Set không tìm thấy chính phần tử của nó").isFalse();
			return orderA.removeItem(itemPen); // Set.remove trượt → trả về false → không gỡ owning side, orphanRemoval không có gì để xoá
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes(SCHEMA_A + ".order_item", 0);

		// ── THEN: DB thật ──
		db.print(SCHEMA_A + ".order_item");
		assertThat(removed).as("Set.remove() không tìm thấy item vì hashCode đã đổi sau khi persist").isFalse();
		assertThat(db.countOf(SCHEMA_A + ".order_item")).as("item KHÔNG bị xoá: code tưởng đã gỡ nhưng thực tế không gỡ được gì").isEqualTo(1);
	}

	/**
	 * G9-S03b — hashCode theo business key: cùng thao tác nhưng remove() chạy đúng
	 *
	 * <p>Mapping: như S03a nhưng {@code OrderItem.hashCode()} = hash của {@code lineCode} (gán ở constructor, không đổi).
	 * <p><b>Câu hỏi</b>: cùng chuỗi thao tác như S03a, kết quả có khác không?
	 * <p><b>Kết luận</b>: có. hashCode không đổi nên {@code remove} trả về true, orphanRemoval chạy: đúng 1 DELETE order_item.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S03b | hashCode theo business key: remove() trả về true, orphanRemoval DELETE item")
	void s03b_hashCodeBasedOnBusinessKeyKeepsSetRemoveWorking() {
		// ── GIVEN ──
		com.example.hibernatelab.g9_integrated.s03_equals_hashcode.b_business_key.PurchaseOrder orderA =
			new com.example.hibernatelab.g9_integrated.s03_equals_hashcode.b_business_key.PurchaseOrder("A-001");
		com.example.hibernatelab.g9_integrated.s03_equals_hashcode.b_business_key.OrderItem itemPen =
			new com.example.hibernatelab.g9_integrated.s03_equals_hashcode.b_business_key.OrderItem("PEN-01");
		orderA.addItem(itemPen); // hashCode = hash("PEN-01"), không đổi suốt vòng đời

		// ── WHEN ──
		sql.reset();
		boolean removed = inTxReturning(() -> {
			em.persist(orderA); // [LIFECYCLE] TRANSIENT → MANAGED; id gán nhưng hashCode (theo lineCode) không đổi
			em.flush(); // [FLUSH] INSERT purchase_order, INSERT order_item
			boolean stillContained = orderA.getItems().contains(itemPen);
			assertThat(stillContained).as("hashCode theo lineCode không đổi nên Set vẫn tìm thấy phần tử").isTrue();
			return orderA.removeItem(itemPen); // gỡ khỏi Set → orphanRemoval sẽ DELETE lúc commit
			// [TX END] commit: flush cuối thấy item mồ côi (orphan) → DELETE order_item
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes(SCHEMA_B + ".order_item", 1);

		// ── THEN: DB thật ──
		assertThat(removed).as("remove() tìm thấy item vì hashCode ổn định").isTrue();
		assertThat(db.countOf(SCHEMA_B + ".order_item")).as("orphanRemoval đã xoá dòng order_item").isEqualTo(0);
	}

	/**
	 * G9-S03c — Đối chứng: cùng hashCode theo id, nhưng Set được NẠP LẠI từ DB thì remove() chạy được
	 *
	 * <p><b>Câu hỏi</b>: vậy hashCode theo id có luôn hỏng không?
	 * <p><b>Kết luận</b>: không. Lỗi chỉ xảy ra khi id đổi KHI phần tử đã nằm trong Set. Set nạp lại từ DB thì item có sẵn id ngay lúc được thêm,
	 * nên chạy đúng. Chính vì lỗi chỉ lộ ở đường "tạo mới rồi thao tác ngay" nên rất dễ lọt qua test sơ sài.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S03c | hashCode theo id nhưng Set nạp lại từ DB: remove() vẫn chạy đúng")
	void s03c_hashCodeBasedOnIdWorksWhenSetIsReloadedFromDatabase() {
		// ── GIVEN ──
		PurchaseOrder orderA = new PurchaseOrder("A-001");
		orderA.addItem(new OrderItem("PEN-01"));
		Long orderId = inTxReturning(() -> {
			em.persist(orderA);
			return orderA.getId();
		});

		// ── WHEN ──
		sql.reset();
		boolean removed = inTxReturning(() -> {
			PurchaseOrder loadedOrder = em.find(PurchaseOrder.class, orderId);
			OrderItem loadedItem = loadedOrder.getItems().iterator().next(); // nạp Set: item vào Set khi id đã có sẵn
			return loadedOrder.removeItem(loadedItem);
			// [TX END] commit: orphanRemoval DELETE order_item
		});

		// ── THEN: SQL + DB thật ──
		sql.print();
		sql.assertDeletes(SCHEMA_A + ".order_item", 1);
		assertThat(removed).as("id không đổi từ lúc item vào Set nên hash khớp và remove() thành công").isTrue();
		assertThat(db.countOf(SCHEMA_A + ".order_item")).as("orphanRemoval đã xoá dòng").isEqualTo(0);
	}

	/**
	 * G9-S03d ➕ — Proxy: {@code equals} dùng {@code getClass()} sai với proxy Hibernate, {@code instanceof} thì đúng
	 *
	 * <p><b>Câu hỏi</b>: so một entity thật với PROXY của chính dòng đó (từ {@code getReference}) thì equals trả về gì?
	 * <p><b>Kết luận</b>: {@code instanceof} + getter → true. {@code getClass()} → false vì proxy là lớp con sinh lúc chạy.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G9-S03d | equals với proxy: instanceof đúng, getClass() sai")
	void s03d_equalsUsingGetClassFailsAgainstHibernateProxy() {
		// ── GIVEN ──
		com.example.hibernatelab.g9_integrated.s03_equals_hashcode.b_business_key.PurchaseOrder orderA =
			new com.example.hibernatelab.g9_integrated.s03_equals_hashcode.b_business_key.PurchaseOrder("A-001");
		orderA.addItem(new com.example.hibernatelab.g9_integrated.s03_equals_hashcode.b_business_key.OrderItem("PEN-01"));
		inTx(() -> em.persist(orderA));
		Long itemId = ((Number) db.rowsOf(SCHEMA_B + ".order_item").get(0).get("id")).longValue();
		com.example.hibernatelab.g9_integrated.s03_equals_hashcode.b_business_key.OrderItem realItem = inTxReturning(() ->
			em.find(com.example.hibernatelab.g9_integrated.s03_equals_hashcode.b_business_key.OrderItem.class, itemId)); // DETACHED, class thật

		// ── WHEN ──
		inTx(() -> {
			com.example.hibernatelab.g9_integrated.s03_equals_hashcode.b_business_key.OrderItem proxyItem = em.getReference(
				com.example.hibernatelab.g9_integrated.s03_equals_hashcode.b_business_key.OrderItem.class, itemId); // proxy chưa nạp, chưa SELECT

			// ── THEN ──
			assertThat(Hibernate.isInitialized(proxyItem)).as("getReference chỉ tạo proxy, chưa chạm DB").isFalse();
			assertThat(proxyItem.getClass()).as("proxy là lớp con do Hibernate sinh, KHÁC class entity thật")
				.isNotEqualTo(realItem.getClass());
			assertThat(realItem.equals(proxyItem)).as("instanceof chấp nhận lớp con, getter kích hoạt nạp proxy → so lineCode đúng").isTrue();
			assertThat(realItem.equalsUsingGetClass(proxyItem)).as("BẪY: getClass() của proxy khác class thật nên luôn false").isFalse();
		});
	}
}
