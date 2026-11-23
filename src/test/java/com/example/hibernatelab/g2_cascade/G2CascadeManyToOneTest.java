package com.example.hibernatelab.g2_cascade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hibernate.PersistentObjectException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import com.example.hibernatelab.g2_cascade.c0_none.C0OrderItem;
import com.example.hibernatelab.g2_cascade.c0_none.C0Product;
import com.example.hibernatelab.g2_cascade.c6_remove_on_m2o.C6OrderItem;
import com.example.hibernatelab.g2_cascade.c6_remove_on_m2o.C6Product;
import com.example.hibernatelab.g2_cascade.c6_remove_on_m2o.C6PurchaseOrder;
import com.example.hibernatelab.g2_cascade.c7_persist_on_m2o.C7OrderItem;
import com.example.hibernatelab.g2_cascade.c7_persist_on_m2o.C7OrderItemRepository;
import com.example.hibernatelab.g2_cascade.c7_persist_on_m2o.C7Product;
import com.example.hibernatelab.g2_cascade.c7_persist_on_m2o.C7ProductRepository;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G2, cascade đặt trên {@code @ManyToOne} của CON ({@code OrderItem.product}) thay vì trên collection của cha.
 * S08 (C6, {@code cascade = REMOVE}, anti-pattern), S09 (C7, {@code cascade = PERSIST}, product DETACHED, kèm 3 cách sửa).
 *
 * <p>Product là dữ liệu master dùng chung cho nhiều item. Cho item quyền điều khiển vòng đời của product là đảo ngược
 * quan hệ sở hữu: đó là lý do cascade trên {@code @ManyToOne} thường sai.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G2CascadeManyToOneTest extends AbstractScenarioTest {

	private static final String C0 = "g2_c0_none";
	private static final String C6 = "g2_c6_remove_on_m2o";
	private static final String C7 = "g2_c7_persist_on_m2o";

	@Autowired
	private C7OrderItemRepository c7ItemRepository;

	@Autowired
	private C7ProductRepository c7ProductRepository;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { C0, C6, C7 };
	}

	/**
	 * G2-S08a — C6: xoá 1 item khi product còn được item khác dùng
	 *
	 * <p>Mapping: {@code OrderItem.product(@ManyToOne, cascade = REMOVE)} OWNING side của FK {@code product_id}.
	 * <p><b>Câu hỏi</b>: 2 item cùng trỏ tới product Bút bi, xoá item 1: chuyện gì xảy ra với product?
	 * <p><b>Kết luận</b>: {@code remove(item1)} lan sang product (đánh dấu REMOVED). Lúc flush Hibernate DELETE item 1 rồi DELETE
	 * product, nhưng item 2 vẫn trỏ tới product nên PostgreSQL từ chối (vi phạm FK). Transaction rollback.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S08a | C6 cascade REMOVE trên @ManyToOne: xoá 1 item kéo theo xoá product đang dùng chung, vi phạm FK")
	void s08a_removingOneItemTriesToDeleteTheSharedProduct() {
		// ── GIVEN: 2 item cùng trỏ tới product Bút bi ──
		C6Product productPen = new C6Product("Bút bi");
		C6PurchaseOrder orderA = new C6PurchaseOrder("A-001");
		orderA.addItem(new C6OrderItem(productPen, 1));
		orderA.addItem(new C6OrderItem(productPen, 2));
		Long itemOneId = inTxReturning(() -> {
			em.persist(productPen);
			em.persist(orderA);
			em.persist(orderA.getItems().get(0));
			em.persist(orderA.getItems().get(1));
			return orderA.getItems().get(0).getId();
		});

		// ── WHEN: xoá item 1 ──
		sql.reset();
		assertThatThrownBy(() -> inTx(() -> {
			C6OrderItem itemOne = em.find(C6OrderItem.class, itemOneId);
			em.remove(itemOne);
			// [FLUSH] lúc commit: DELETE order_item (item 1), rồi DELETE product → item 2 còn trỏ tới nên FK từ chối
		}))
			.as("cascade REMOVE trên @ManyToOne kéo product vào việc xoá, mà item 2 vẫn đang dùng product đó")
			.isInstanceOf(DataIntegrityViolationException.class);

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("product", 1);

		// ── THEN: DB thật ──
		assertThat(db.countOf(C6 + ".product")).as("rollback nên product còn").isEqualTo(1);
		assertThat(db.countOf(C6 + ".order_item")).as("rollback nên cả 2 item còn").isEqualTo(2);
	}

	/**
	 * G2-S08b — C6: product chỉ có 1 item dùng thì bị xoá IM LẶNG
	 *
	 * <p>Mapping: như S08a.
	 * <p><b>Câu hỏi</b>: nếu chỉ có 1 item trỏ tới product, xoá item đó thì sao?
	 * <p><b>Kết luận</b>: Thành công, không lỗi gì: product cũng bị DELETE. Dữ liệu master mất chỉ vì một dòng hàng bị xoá.
	 * Đây là lý do chính để KHÔNG đặt cascade REMOVE trên {@code @ManyToOne}: lỗi FK ở S08a còn là "may mắn" vì chặn được mất dữ liệu.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S08b | C6 cascade REMOVE trên @ManyToOne: product chỉ có 1 item bị xoá âm thầm theo")
	void s08b_theOnlyReferencedProductIsDeletedSilently() {
		// ── GIVEN: 1 item duy nhất trỏ tới product Bút bi ──
		C6Product productPen = new C6Product("Bút bi");
		C6PurchaseOrder orderA = new C6PurchaseOrder("A-001");
		orderA.addItem(new C6OrderItem(productPen, 1));
		Long itemId = inTxReturning(() -> {
			em.persist(productPen);
			em.persist(orderA);
			em.persist(orderA.getItems().get(0));
			return orderA.getItems().get(0).getId();
		});

		// ── WHEN: xoá item ──
		sql.reset();
		inTx(() -> em.remove(em.find(C6OrderItem.class, itemId)));
		// [FLUSH] lúc commit: DELETE order_item rồi DELETE product (không ai còn trỏ tới product nên DB cho phép)

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("order_item", 1);
		sql.assertDeletes("product", 1);
		sql.assertInOrder("delete from " + C6 + ".order_item", "delete from " + C6 + ".product");

		// ── THEN: DB thật ──
		assertThat(db.countOf(C6 + ".order_item")).as("item bị xoá đúng như yêu cầu").isZero();
		assertThat(db.countOf(C6 + ".product"))
			.as("product bị xoá theo dù không ai yêu cầu: MẤT DỮ LIỆU MASTER do cascade REMOVE đặt sai chỗ")
			.isZero();
	}

	/**
	 * G2-S09a — C7: persist item trỏ tới product DETACHED
	 *
	 * <p>Mapping: {@code OrderItem.product(@ManyToOne, cascade = PERSIST)}.
	 * <p><b>Câu hỏi</b>: product được tải ở transaction trước (nay DETACHED), tạo item mới trỏ tới nó rồi
	 * {@code itemRepository.save(item)} thì sao?
	 * <p><b>Kết luận</b>: Lỗi {@code detached entity passed to persist} ({@code PersistentObjectException}, Spring bọc thành
	 * {@code InvalidDataAccessApiUsageException}). Cascade PERSIST lan sang product, mà persist không nhận entity đã có id nhưng không còn MANAGED.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S09a | C7 cascade PERSIST trên @ManyToOne: product DETACHED gây 'detached entity passed to persist'")
	void s09a_cascadePersistRejectsADetachedProduct() {
		// ── GIVEN: product Bút bi đã có trong DB; sau khi transaction đóng nó là DETACHED ──
		C7Product detachedProduct = inTxReturning(() -> {
			C7Product product = new C7Product("Bút bi");
			em.persist(product);
			return product;
		});

		// ── WHEN: lưu item mới trỏ tới product DETACHED ──
		sql.reset();
		assertThatThrownBy(() -> c7ItemRepository.save(new C7OrderItem(detachedProduct, 1)))
			.as("save() gọi persist(item), cascade PERSIST sang product DETACHED thì Hibernate từ chối; Spring bọc lỗi lại")
			.isInstanceOf(InvalidDataAccessApiUsageException.class)
			.hasRootCauseInstanceOf(PersistentObjectException.class);

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("order_item", 0);

		// ── THEN: DB thật ──
		assertThat(db.countOf(C7 + ".order_item")).as("persist bị từ chối nên không có item nào").isZero();
	}

	/**
	 * G2-S09b — C7: cách sửa 1, dùng {@code getReferenceById} trong cùng transaction
	 *
	 * <p>Mapping: như S09a.
	 * <p><b>Câu hỏi</b>: lấy product bằng {@code getReferenceById(id)} (proxy MANAGED) thì save item có qua không?
	 * <p><b>Kết luận</b>: Có. Proxy do {@code getReferenceById} tạo ra thuộc persistence context hiện tại (MANAGED) nên cascade
	 * PERSIST bỏ qua nó; chỉ INSERT item, không SELECT product (proxy chưa cần khởi tạo).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S09b | C7 cách sửa 1: getReferenceById trong cùng transaction, chỉ INSERT item")
	void s09b_fixWithGetReferenceById() {
		// ── GIVEN: product Bút bi đã có trong DB ──
		Long productId = inTxReturning(() -> {
			C7Product product = new C7Product("Bút bi");
			em.persist(product);
			return product.getId();
		});

		// ── WHEN: trong CÙNG transaction, lấy proxy rồi save item ──
		sql.reset();
		inTx(() -> {
			C7Product productReference = c7ProductRepository.getReferenceById(productId);
			c7ItemRepository.save(new C7OrderItem(productReference, 1));
			// [FLUSH] lúc commit: INSERT order_item với product_id lấy từ proxy
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("order_item", 1);
		sql.assertSelects("product", 0);

		// ── THEN: DB thật ──
		assertThat(db.rowsOf(C7 + ".order_item").get(0).get("product_id"))
			.as("item trỏ đúng product đã có, FK được ghi từ id của proxy")
			.isEqualTo(productId);
	}

	/**
	 * G2-S09c — C7: cách sửa 2, {@code find} product trong cùng transaction
	 *
	 * <p>Mapping: như S09a.
	 * <p><b>Câu hỏi</b>: tải product bằng {@code em.find} trong cùng transaction với persist item thì sao?
	 * <p><b>Kết luận</b>: Có qua: product MANAGED nên cascade PERSIST bỏ qua nó. Tốn thêm 1 SELECT product.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S09c | C7 cách sửa 2: find product trong cùng transaction, tốn thêm 1 SELECT")
	void s09c_fixWithFindInTheSameTransaction() {
		// ── GIVEN: product Bút bi đã có trong DB ──
		Long productId = inTxReturning(() -> {
			C7Product product = new C7Product("Bút bi");
			em.persist(product);
			return product.getId();
		});

		// ── WHEN: trong CÙNG transaction, find product rồi persist item ──
		sql.reset();
		inTx(() -> {
			C7Product managedProduct = em.find(C7Product.class, productId);
			em.persist(new C7OrderItem(managedProduct, 1));
			// [FLUSH] lúc commit: INSERT order_item
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("product", 1);
		sql.assertInserts("order_item", 1);

		// ── THEN: DB thật ──
		assertThat(db.countOf(C7 + ".order_item")).as("product MANAGED nên persist item thành công").isEqualTo(1);
	}

	/**
	 * G2-S09d — C0: cách sửa 3, bỏ cascade trên {@code @ManyToOne}
	 *
	 * <p>Mapping: {@code OrderItem.product(@ManyToOne)} KHÔNG cascade (baseline C0).
	 * <p><b>Câu hỏi</b>: cùng thao tác S09a nhưng không có cascade PERSIST trên product thì sao?
	 * <p><b>Kết luận</b>: Thành công, không cần chiêu gì: với {@code @ManyToOne} không cascade, Hibernate chỉ cần id của product
	 * (đã có) để ghi FK, không quan tâm product MANAGED hay DETACHED. Đây là cách khuyến nghị: product là dữ liệu master có sẵn.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G2-S09d | C0 cách sửa 3: bỏ cascade, item trỏ tới product DETACHED vẫn lưu được")
	void s09d_fixByRemovingTheCascade() {
		// ── GIVEN: product Bút bi đã có trong DB; sau khi transaction đóng nó là DETACHED ──
		C0Product detachedProduct = inTxReturning(() -> {
			C0Product product = new C0Product("Bút bi");
			em.persist(product);
			return product;
		});

		// ── WHEN: persist item mới trỏ tới product DETACHED ──
		sql.reset();
		inTx(() -> em.persist(new C0OrderItem(detachedProduct, 1)));
		// [FLUSH] lúc commit: INSERT order_item với product_id lấy từ id của product DETACHED

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("order_item", 1);
		sql.assertSelects("product", 0);

		// ── THEN: DB thật ──
		assertThat(db.rowsOf(C0 + ".order_item").get(0).get("product_id"))
			.as("không cascade nên Hibernate chỉ đọc id của product để ghi FK, DETACHED hay MANAGED đều được")
			.isEqualTo(detachedProduct.getId());
	}
}
