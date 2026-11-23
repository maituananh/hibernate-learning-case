package com.example.hibernatelab.g2_cascade;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.State.REMOVED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.hibernatelab.g2_cascade.c0_none.C0OrderItem;
import com.example.hibernatelab.g2_cascade.c0_none.C0Product;
import com.example.hibernatelab.g2_cascade.c0_none.C0PurchaseOrder;
import com.example.hibernatelab.g2_cascade.c3_remove.C3OrderItem;
import com.example.hibernatelab.g2_cascade.c3_remove.C3Product;
import com.example.hibernatelab.g2_cascade.c3_remove.C3PurchaseOrder;
import com.example.hibernatelab.g2_cascade.c3_remove.C3PurchaseOrderRepository;
import com.example.hibernatelab.g2_cascade.c8_ondelete.C8OrderItem;
import com.example.hibernatelab.g2_cascade.c8_ondelete.C8Product;
import com.example.hibernatelab.g2_cascade.c8_ondelete.C8PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G2, thao tác REMOVE: xoá order đang có item.
 * S05 (C0 vs C3), S10 (cascade JPA C3 vs {@code @OnDelete} ở DB C8), S12 (bulk JPQL bỏ qua cascade),
 * S13 (Spring Data {@code deleteById} / {@code delete(entity)} / {@code deleteAllInBatch} trên C3).
 *
 * <p>Điểm cần nắm: cascade REMOVE của JPA chạy TRONG Java (Hibernate tải item rồi DELETE từng dòng),
 * còn {@code @OnDelete} là {@code ON DELETE CASCADE} do PostgreSQL thực hiện. Hai thứ khác hẳn nhau về SQL và về trạng thái entity.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G2CascadeRemoveTest extends AbstractScenarioTest {

	private static final String C0 = "g2_c0_none";
	private static final String C3 = "g2_c3_remove";
	private static final String C8 = "g2_c8_ondelete";

	@Autowired
	private C3PurchaseOrderRepository c3OrderRepository;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { C0, C3, C8 };
	}

	/**
	 * G2-S05a — C0: xoá order còn item, KHÔNG cascade
	 *
	 * <p>Mapping: {@code PurchaseOrder.items(mappedBy = "order")} không cascade.
	 * <p><b>Câu hỏi</b>: {@code remove(order)} khi order còn 2 item thì sao?
	 * <p><b>Kết luận</b>: Hibernate chỉ DELETE order, không biết gì về item. PostgreSQL từ chối vì FK
	 * {@code order_item.order_id} còn dòng trỏ tới ({@code DataIntegrityViolationException}). Transaction rollback, DB giữ nguyên.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S05a | C0 không cascade: xoá order còn item vi phạm FK, không xoá được gì")
	void s05a_withoutCascadeDeletingOrderWithItemsViolatesForeignKey() {
		// ── GIVEN: order A-001 có 2 item trong DB ──
		C0Product productPen = new C0Product("Bút bi");
		C0PurchaseOrder orderA = new C0PurchaseOrder("A-001");
		orderA.addItem(new C0OrderItem(productPen, 1));
		orderA.addItem(new C0OrderItem(productPen, 2));
		Long orderId = inTxReturning(() -> {
			em.persist(productPen);
			em.persist(orderA);
			em.persist(orderA.getItems().get(0));
			em.persist(orderA.getItems().get(1));
			return orderA.getId();
		});

		// ── WHEN: xoá order (item không bị đụng tới) ──
		sql.reset();
		assertThatThrownBy(() -> inTx(() -> {
			C0PurchaseOrder orderToDelete = em.find(C0PurchaseOrder.class, orderId);
			em.remove(orderToDelete);
			// [LIFECYCLE] order MANAGED → REMOVED (chưa có DELETE nào chạy)
			assertState(em, orderToDelete, REMOVED);
			// [FLUSH] lúc commit: DELETE purchase_order → PostgreSQL từ chối vì còn item trỏ tới
		}))
			.as("DB giữ FK order_item.order_id nên không cho xoá order còn item; Spring dịch lỗi thành DataIntegrityViolationException")
			.isInstanceOf(DataIntegrityViolationException.class);

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("order_item", 0);

		// ── THEN: DB thật ──
		db.print(C0 + ".purchase_order");
		assertThat(db.countOf(C0 + ".purchase_order")).as("transaction rollback nên order vẫn còn").isEqualTo(1);
		assertThat(db.countOf(C0 + ".order_item")).as("không cascade nên không có DELETE item nào").isEqualTo(2);
	}

	/**
	 * G2-S05b — C3: {@code cascade = REMOVE} xoá từng item rồi mới xoá order
	 *
	 * <p>Mapping: {@code PurchaseOrder.items(mappedBy = "order", cascade = REMOVE)}.
	 * <p><b>Câu hỏi</b>: cùng thao tác S05a nhưng có cascade REMOVE thì sao?
	 * <p><b>Kết luận</b>: {@code remove(order)} tải collection {@code items} (SELECT), đánh dấu từng item REMOVED, rồi lúc flush
	 * DELETE từng item (N câu) trước, DELETE order sau cùng (vì item giữ FK tới order).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S05b | C3 cascade REMOVE: SELECT item, DELETE từng item rồi DELETE order")
	void s05b_cascadeRemoveDeletesItemsOneByOneThenOrder() {
		// ── GIVEN: order A-001 có 2 item trong DB ──
		C3Product productPen = new C3Product("Bút bi");
		C3PurchaseOrder orderA = new C3PurchaseOrder("A-001");
		orderA.addItem(new C3OrderItem(productPen, 1));
		orderA.addItem(new C3OrderItem(productPen, 2));
		Long orderId = inTxReturning(() -> {
			em.persist(productPen);
			em.persist(orderA);
			em.persist(orderA.getItems().get(0));
			em.persist(orderA.getItems().get(1));
			return orderA.getId();
		});

		// ── WHEN: xoá order ──
		sql.reset();
		inTx(() -> {
			C3PurchaseOrder orderToDelete = em.find(C3PurchaseOrder.class, orderId);
			em.remove(orderToDelete);
			// [LIFECYCLE] cascade REMOVE chạy NGAY trong remove(): order và cả 2 item đều MANAGED → REMOVED
			List<C3OrderItem> items = orderToDelete.getItems();
			assertState(em, orderToDelete, REMOVED);
			assertState(em, items.get(0), REMOVED);
			assertState(em, items.get(1), REMOVED);
			// [FLUSH] lúc commit: DELETE order_item ×2 rồi DELETE purchase_order
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("order_item", 1);
		sql.assertDeletes("order_item", 2);
		sql.assertDeletes("purchase_order", 1);
		sql.assertInOrder("delete from " + C3 + ".order_item", "delete from " + C3 + ".purchase_order");

		// ── THEN: DB thật ──
		db.print(C3 + ".order_item");
		assertThat(db.countOf(C3 + ".order_item")).as("cascade REMOVE đã xoá cả 2 item").isZero();
		assertThat(db.countOf(C3 + ".purchase_order")).as("order bị xoá sau khi con đã xoá hết").isZero();
	}

	/**
	 * G2-S10a — C3: xoá order 3 item bằng cascade JPA
	 *
	 * <p>Mapping: như S05b.
	 * <p><b>Câu hỏi</b>: bao nhiêu câu SQL để xoá một order có 3 item bằng cascade REMOVE?
	 * <p><b>Kết luận</b>: 1 SELECT items + 3 DELETE item + 1 DELETE order. Số câu tăng theo số item (N+1 kiểu xoá).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S10a | C3 cascade JPA: xoá order 3 item tốn 1 SELECT + 3 DELETE item + 1 DELETE order")
	void s10a_jpaCascadeRemoveNeedsOneDeletePerItem() {
		// ── GIVEN: order A-001 có 3 item trong DB ──
		C3Product productPen = new C3Product("Bút bi");
		C3PurchaseOrder orderA = new C3PurchaseOrder("A-001");
		orderA.addItem(new C3OrderItem(productPen, 1));
		orderA.addItem(new C3OrderItem(productPen, 2));
		orderA.addItem(new C3OrderItem(productPen, 3));
		Long orderId = inTxReturning(() -> {
			em.persist(productPen);
			em.persist(orderA);
			for (C3OrderItem item : orderA.getItems()) {
				em.persist(item);
			}
			return orderA.getId();
		});

		// ── WHEN: xoá order ──
		sql.reset();
		inTx(() -> em.remove(em.find(C3PurchaseOrder.class, orderId)));
		// [FLUSH] lúc commit: DELETE từng item rồi DELETE order

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("order_item", 1);
		sql.assertDeletes("order_item", 3);
		sql.assertDeletes("purchase_order", 1);

		// ── THEN: DB thật ──
		assertThat(db.countOf(C3 + ".order_item")).as("cả 3 item đã bị xoá").isZero();
		assertThat(db.countOf(C3 + ".purchase_order")).as("order đã bị xoá").isZero();
	}

	/**
	 * G2-S10b — C8: {@code @OnDelete(CASCADE)} để DB tự xoá item
	 *
	 * <p>Mapping: {@code OrderItem.order} có {@code @OnDelete(action = CASCADE)}; {@code PurchaseOrder.items} KHÔNG có cascade JPA.
	 * <p><b>Câu hỏi</b>: DDL khác gì, và xoá order 3 item tốn bao nhiêu câu SQL?
	 * <p><b>Kết luận</b>: DDL: FK {@code order_item.order_id} có {@code ON DELETE CASCADE}. Hibernate chỉ gửi 1 DELETE order
	 * (không SELECT item, không DELETE item nào); PostgreSQL tự xoá 3 dòng item. DB cuối cùng giống hệt C3.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S10b | C8 @OnDelete: FK có ON DELETE CASCADE, xoá order chỉ cần 1 DELETE")
	void s10b_onDeleteCascadeLetsTheDatabaseDeleteItems() {
		// ── GIVEN: order A-001 có 3 item trong DB ──
		C8Product productPen = new C8Product("Bút bi");
		C8PurchaseOrder orderA = new C8PurchaseOrder("A-001");
		orderA.addItem(new C8OrderItem(productPen, 1));
		orderA.addItem(new C8OrderItem(productPen, 2));
		orderA.addItem(new C8OrderItem(productPen, 3));
		Long orderId = inTxReturning(() -> {
			em.persist(productPen);
			em.persist(orderA);
			for (C8OrderItem item : orderA.getItems()) {
				em.persist(item);
			}
			return orderA.getId();
		});

		// ── THEN: DDL ──
		assertThat(schema.foreignKeyOnColumn(C8, "order_item", "order_id").onDelete())
			.as("@OnDelete(CASCADE) làm Hibernate sinh FK với ON DELETE CASCADE")
			.isEqualTo("CASCADE");
		assertThat(schema.foreignKeyOnColumn(C3, "order_item", "order_id").onDelete())
			.as("đối chứng C3 (cascade JPA): FK mặc định NO ACTION, không tự xoá gì")
			.isEqualTo("NO ACTION");

		// ── WHEN: xoá order ──
		sql.reset();
		inTx(() -> em.remove(em.find(C8PurchaseOrder.class, orderId)));
		// [FLUSH] lúc commit: chỉ DELETE purchase_order; PostgreSQL tự xoá item nhờ ON DELETE CASCADE

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("purchase_order", 1);
		sql.assertDeletes("order_item", 0);
		sql.assertSelects("order_item", 0);

		// ── THEN: DB thật ──
		assertThat(db.countOf(C8 + ".order_item")).as("DB tự xoá 3 item nhờ ON DELETE CASCADE, không cần Hibernate").isZero();
		assertThat(db.countOf(C8 + ".purchase_order")).as("order đã bị xoá").isZero();
	}

	/**
	 * G2-S10c — C8 ➕ 🔬: item đã được tải vào persistence context trước khi xoá order
	 *
	 * <p>Mapping: như S10b.
	 * <p><b>Câu hỏi</b>: Hibernate có biết item đã biến mất khỏi DB không? Object item còn ở trạng thái nào?
	 * <p><b>Kết luận</b>: Hibernate KHÔNG biết (nó không thấy DB tự xoá): item vẫn MANAGED trong persistence context dù dòng
	 * đã mất khỏi DB. Đây là mặt trái của {@code @OnDelete}: bộ nhớ có thể lệch với DB.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S10c | C8 ➕ item đã tải vào bộ nhớ: sau khi DB tự xoá, item vẫn MANAGED (bộ nhớ lệch DB)")
	void s10c_onDeleteLeavesLoadedItemsManagedInMemory() {
		// ── GIVEN: order A-001 có 3 item trong DB ──
		C8Product productPen = new C8Product("Bút bi");
		C8PurchaseOrder orderA = new C8PurchaseOrder("A-001");
		orderA.addItem(new C8OrderItem(productPen, 1));
		orderA.addItem(new C8OrderItem(productPen, 2));
		orderA.addItem(new C8OrderItem(productPen, 3));
		Long orderId = inTxReturning(() -> {
			em.persist(productPen);
			em.persist(orderA);
			for (C8OrderItem item : orderA.getItems()) {
				em.persist(item);
			}
			return orderA.getId();
		});

		// ── WHEN: tải order cùng item vào persistence context, xoá order, flush ──
		sql.reset();
		inTx(() -> {
			C8PurchaseOrder orderToDelete = em.createQuery(
				"select o from G2C8_PurchaseOrder o join fetch o.items where o.id = :id", C8PurchaseOrder.class)
				.setParameter("id", orderId).getSingleResult();
			C8OrderItem loadedItem = orderToDelete.getItems().get(0);
			em.remove(orderToDelete);
			// [LIFECYCLE] order MANAGED → REMOVED; không có cascade JPA nên item KHÔNG bị đánh dấu gì, vẫn MANAGED
			assertState(em, orderToDelete, REMOVED);
			assertState(em, loadedItem, MANAGED);
			em.flush();
			// [FLUSH] DELETE purchase_order; PostgreSQL xoá 3 item. Hibernate không được thông báo gì
			// [LIFECYCLE] sau flush item VẪN MANAGED dù dòng của nó không còn trong DB (còn order thì đã bị gỡ khỏi persistence context)
			assertState(em, loadedItem, MANAGED);
			assertThat(db.countOf(C8 + ".order_item"))
				.as("trong DB item đã mất (cùng connection nên thấy kết quả của DELETE vừa flush)")
				.isZero();
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("order_item", 0);
	}

	/**
	 * G2-S12a — C3: bulk JPQL {@code delete from PurchaseOrder} bỏ qua cascade JPA
	 *
	 * <p>Mapping: như S05b (có {@code cascade = REMOVE}).
	 * <p><b>Câu hỏi</b>: bulk delete có đi qua cascade REMOVE không?
	 * <p><b>Kết luận</b>: Không. Bulk JPQL gửi thẳng 1 câu {@code DELETE purchase_order} xuống DB, không tải entity nên không có
	 * cascade; item còn nên FK bị vi phạm. Cascade của JPA chỉ chạy khi xoá từng entity qua {@code em.remove}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S12a | C3 bulk JPQL delete: bỏ qua cascade REMOVE nên vi phạm FK")
	void s12a_bulkDeleteSkipsJpaCascadeAndViolatesForeignKey() {
		// ── GIVEN: order A-001 có 2 item trong DB ──
		C3Product productPen = new C3Product("Bút bi");
		C3PurchaseOrder orderA = new C3PurchaseOrder("A-001");
		orderA.addItem(new C3OrderItem(productPen, 1));
		orderA.addItem(new C3OrderItem(productPen, 2));
		inTx(() -> {
			em.persist(productPen);
			em.persist(orderA);
			em.persist(orderA.getItems().get(0));
			em.persist(orderA.getItems().get(1));
		});

		// ── WHEN: bulk delete ──
		sql.reset();
		assertThatThrownBy(() -> inTx(() -> em.createQuery("delete from G2C3_PurchaseOrder").executeUpdate()))
			.as("bulk delete không đi qua cascade nên item còn nguyên và FK từ chối; "
				+ "dùng EntityManager trực tiếp thì Spring không dịch lỗi, Hibernate ném thẳng ConstraintViolationException")
			.isInstanceOf(ConstraintViolationException.class);

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("order_item", 0);
		sql.assertDeletes("order_item", 0);

		// ── THEN: DB thật ──
		assertThat(db.countOf(C3 + ".purchase_order")).as("bulk delete bị rollback nên order còn").isEqualTo(1);
		assertThat(db.countOf(C3 + ".order_item")).as("không có DELETE item nào").isEqualTo(2);
	}

	/**
	 * G2-S12b — C8: bulk JPQL thành công nhờ {@code ON DELETE CASCADE} của DB
	 *
	 * <p>Mapping: như S10b ({@code @OnDelete}).
	 * <p><b>Câu hỏi</b>: cùng bulk delete như S12a nhưng trên C8 thì sao?
	 * <p><b>Kết luận</b>: Thành công: bulk bỏ qua cascade JPA, nhưng cascade của DB vẫn chạy ở mọi đường xoá, kể cả bulk.
	 * Đó là điểm {@code @OnDelete} mạnh hơn cascade JPA (và là lý do bộ nhớ có thể lệch, xem S10c).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S12b | C8 bulk JPQL delete: thành công vì DB tự cascade")
	void s12b_bulkDeleteSucceedsBecauseTheDatabaseCascades() {
		// ── GIVEN: order A-001 có 2 item trong DB ──
		C8Product productPen = new C8Product("Bút bi");
		C8PurchaseOrder orderA = new C8PurchaseOrder("A-001");
		orderA.addItem(new C8OrderItem(productPen, 1));
		orderA.addItem(new C8OrderItem(productPen, 2));
		inTx(() -> {
			em.persist(productPen);
			em.persist(orderA);
			em.persist(orderA.getItems().get(0));
			em.persist(orderA.getItems().get(1));
		});

		// ── WHEN: bulk delete ──
		sql.reset();
		inTx(() -> em.createQuery("delete from G2C8_PurchaseOrder").executeUpdate());

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("purchase_order", 1);
		sql.assertDeletes("order_item", 0);

		// ── THEN: DB thật ──
		assertThat(db.countOf(C8 + ".purchase_order")).as("order đã bị xoá bởi bulk").isZero();
		assertThat(db.countOf(C8 + ".order_item")).as("PostgreSQL tự xoá item nhờ ON DELETE CASCADE").isZero();
	}

	/**
	 * G2-S13a — C3: {@code repository.deleteById(id)} đi qua cascade
	 *
	 * <p>Mapping: như S05b.
	 * <p><b>Câu hỏi</b>: {@code deleteById} có cascade không?
	 * <p><b>Kết luận</b>: Có. Spring Data tải entity ({@code em.find}) rồi {@code em.remove}, nên cascade REMOVE chạy như S05b.
	 * <p>Xác minh: Hibernate 7.4.15.Final, Spring Data JPA theo Spring Boot 4.2.0
	 */
	@Test
	@DisplayName("G2-S13a | C3 Spring Data deleteById: tải entity rồi cascade REMOVE xoá cả item")
	void s13a_deleteByIdLoadsTheEntityAndCascades() {
		// ── GIVEN: order A-001 có 2 item trong DB ──
		Long orderId = givenOrderWithTwoItemsInC3();

		// ── WHEN: deleteById (Spring Data tự mở transaction cho method này) ──
		sql.reset();
		c3OrderRepository.deleteById(orderId);

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("purchase_order", 1);
		sql.assertDeletes("order_item", 2);
		sql.assertDeletes("purchase_order", 1);

		// ── THEN: DB thật ──
		assertThat(db.countOf(C3 + ".order_item")).as("deleteById đi qua em.remove nên cascade xoá item").isZero();
		assertThat(db.countOf(C3 + ".purchase_order")).as("order đã bị xoá").isZero();
	}

	/**
	 * G2-S13b — C3: {@code repository.delete(entity)} với entity DETACHED
	 *
	 * <p>Mapping: như S05b.
	 * <p><b>Câu hỏi</b>: truyền order đã DETACHED vào {@code delete(entity)} thì cascade có chạy không?
	 * <p><b>Kết luận</b>: Có. Spring Data tải bản MANAGED tương ứng rồi {@code em.remove}, nên cascade REMOVE vẫn chạy.
	 * <p>Xác minh: Hibernate 7.4.15.Final, Spring Data JPA theo Spring Boot 4.2.0
	 */
	@Test
	@DisplayName("G2-S13b | C3 Spring Data delete(entity DETACHED): cascade REMOVE vẫn xoá cả item")
	void s13b_deleteDetachedEntityStillCascades() {
		// ── GIVEN: order A-001 có 2 item; tải order ra rồi đóng transaction → DETACHED ──
		Long orderId = givenOrderWithTwoItemsInC3();
		C3PurchaseOrder detachedOrder = inTxReturning(() -> em.find(C3PurchaseOrder.class, orderId));

		// ── WHEN: delete(entity) ──
		sql.reset();
		c3OrderRepository.delete(detachedOrder);

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("order_item", 2);
		sql.assertDeletes("purchase_order", 1);

		// ── THEN: DB thật ──
		assertThat(db.countOf(C3 + ".order_item")).as("delete(entity) đi qua em.remove nên cascade xoá item").isZero();
		assertThat(db.countOf(C3 + ".purchase_order")).as("order đã bị xoá").isZero();
	}

	/**
	 * G2-S13c — C3: {@code repository.deleteAllInBatch()} là bulk delete
	 *
	 * <p>Mapping: như S05b.
	 * <p><b>Câu hỏi</b>: {@code deleteAllInBatch} có cascade không?
	 * <p><b>Kết luận</b>: Không: nó là bulk JPQL ({@code delete from ...}) như S12a nên không cascade, item còn nên vi phạm FK.
	 * Lần này Spring Data dịch lỗi thành {@code DataIntegrityViolationException}.
	 * <p>Xác minh: Hibernate 7.4.15.Final, Spring Data JPA theo Spring Boot 4.2.0
	 */
	@Test
	@DisplayName("G2-S13c | C3 Spring Data deleteAllInBatch: bulk delete không cascade nên vi phạm FK")
	void s13c_deleteAllInBatchIsBulkAndViolatesForeignKey() {
		// ── GIVEN: order A-001 có 2 item trong DB ──
		givenOrderWithTwoItemsInC3();

		// ── WHEN: deleteAllInBatch ──
		sql.reset();
		assertThatThrownBy(() -> c3OrderRepository.deleteAllInBatch())
			.as("bulk delete bỏ qua cascade JPA nên item còn và FK từ chối")
			.isInstanceOf(DataIntegrityViolationException.class);

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("order_item", 0);

		// ── THEN: DB thật ──
		assertThat(db.countOf(C3 + ".purchase_order")).as("bulk delete bị rollback nên order còn").isEqualTo(1);
		assertThat(db.countOf(C3 + ".order_item")).as("không có DELETE item nào").isEqualTo(2);
	}

	/** GIVEN dùng chung cho S13a/b/c: một order trong schema C3 có 2 item; trả về id của order. */
	private Long givenOrderWithTwoItemsInC3() {
		C3Product productPen = new C3Product("Bút bi");
		C3PurchaseOrder orderA = new C3PurchaseOrder("A-001");
		orderA.addItem(new C3OrderItem(productPen, 1));
		orderA.addItem(new C3OrderItem(productPen, 2));
		return inTxReturning(() -> {
			em.persist(productPen);
			em.persist(orderA);
			em.persist(orderA.getItems().get(0));
			em.persist(orderA.getItems().get(1));
			return orderA.getId();
		});
	}
}
