package com.example.hibernatelab.g7_many_to_many;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Serializable;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.hibernatelab.g7_many_to_many.m8_join_entity.M8Product;
import com.example.hibernatelab.g7_many_to_many.m8_join_entity.M8StockItem;
import com.example.hibernatelab.g7_many_to_many.m8_join_entity.M8StockItemId;
import com.example.hibernatelab.g7_many_to_many.m8_join_entity.M8Warehouse;
import com.example.hibernatelab.g7_many_to_many.m9_join_entity_surrogate.M9Product;
import com.example.hibernatelab.g7_many_to_many.m9_join_entity_surrogate.M9StockItem;
import com.example.hibernatelab.g7_many_to_many.m9_join_entity_surrogate.M9Warehouse;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G7, phần "khi bảng nối cần thêm cột": M8 (join entity, PK ghép {@code @EmbeddedId} + {@code @MapsId}),
 * M9 (join entity, id riêng + UNIQUE). S10 (thêm/sửa/xoá), S11 (StockItemId), S12 (M8 vs M9).
 *
 * <p>Điểm cần nắm: {@code @ManyToMany} không có chỗ cho cột phụ (quantity). Cần cột phụ thì bảng nối phải trở thành
 * một ENTITY, và quan hệ N—N biến thành hai quan hệ 1—N ({@code Product 1—N StockItem N—1 Warehouse}).
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G7JoinEntityTest extends AbstractScenarioTest {

	private static final String M8 = "g7_m8_join_entity";
	private static final String M9 = "g7_m9_join_entity_surrogate";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { M8, M9 };
	}

	/**
	 * G7-S10a — M8: thêm dòng tồn kho, PK suy ra từ hai {@code @MapsId}
	 *
	 * <p>Mapping: M8, {@code StockItem} có {@code @EmbeddedId StockItemId(productId, warehouseId)},
	 * {@code @MapsId("productId") product}, {@code @MapsId("warehouseId") warehouse}, cột {@code quantity}.
	 * <p><b>Câu hỏi</b>: {@code product.addStock(warehouse, 10)} sinh SQL gì, PK lấy từ đâu?
	 * <p><b>Kết luận</b>: đúng 1 INSERT {@code stock_item(product_id, warehouse_id, quantity)} (cascade PERSIST từ product).
	 * Không có sequence cho stock_item: PK chính là hai FK. Product và warehouse không bị UPDATE.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S10a | M8: addStock INSERT stock_item với PK ghép lấy từ product và warehouse")
	void s10a_m8AddStockInsertsJoinEntity() {
		// ── GIVEN: product và warehouse đã tồn tại ──
		M8Product phone = new M8Product("Phone");
		M8Warehouse warehouseHanoi = new M8Warehouse("Hanoi");
		inTx(() -> {
			em.persist(phone);
			em.persist(warehouseHanoi);
		});

		// ── WHEN: thêm 10 sản phẩm vào kho Hà Nội ──
		inTx(() -> {
			M8Product managedPhone = em.find(M8Product.class, phone.getId());
			M8Warehouse managedWarehouse = em.find(M8Warehouse.class, warehouseHanoi.getId());
			sql.reset();
			managedPhone.addStock(managedWarehouse, 10);
			// [CASCADE] PERSIST lan từ product sang stock_item lúc flush
			// [FLUSH] lúc commit: INSERT stock_item
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("stock_item", 1);
		sql.assertUpdates("product", 0);

		// ── THEN: DB thật ──
		db.print(M8 + ".stock_item");
		assertThat(db.countOf(M8 + ".stock_item")).as("có đúng một dòng tồn kho").isEqualTo(1);
		assertThat(db.rowsOf(M8 + ".stock_item").get(0).get("product_id")).as("product_id lấy từ @MapsId(productId)").isEqualTo(phone.getId());
		assertThat(db.rowsOf(M8 + ".stock_item").get(0).get("warehouse_id")).as("warehouse_id lấy từ @MapsId(warehouseId)").isEqualTo(warehouseHanoi.getId());
		assertThat(db.rowsOf(M8 + ".stock_item").get(0).get("quantity")).as("số lượng ban đầu").isEqualTo(10);
		assertThat(schema.sequencesOf(M8)).as("stock_item không có sequence riêng vì PK suy ra từ hai FK")
			.containsExactly("product_seq", "warehouse_seq");
	}

	/**
	 * G7-S10b — M8: sửa {@code quantity} qua product
	 *
	 * <p>Mapping: M8.
	 * <p><b>Câu hỏi</b>: sửa quantity của một dòng tồn kho thì bảng nào bị UPDATE?
	 * <p><b>Kết luận</b>: chỉ {@code stock_item} (1 UPDATE). Product không đổi nên 0 UPDATE product
	 * (cùng bài học với G4: sửa con qua cha không ghi cha, vì {@code StockItem} là owning side).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S10b | M8: đổi quantity chỉ UPDATE stock_item, product không đổi")
	void s10b_m8ChangeQuantityUpdatesOnlyJoinEntity() {
		// ── GIVEN: phone có 10 sản phẩm ở kho Hà Nội ──
		M8Product phone = new M8Product("Phone");
		M8Warehouse warehouseHanoi = new M8Warehouse("Hanoi");
		inTx(() -> {
			em.persist(phone);
			em.persist(warehouseHanoi);
		});
		inTx(() -> em.find(M8Product.class, phone.getId()).addStock(em.find(M8Warehouse.class, warehouseHanoi.getId()), 10));

		// ── WHEN: sửa quantity thành 20 ──
		inTx(() -> {
			M8Product managedPhone = em.find(M8Product.class, phone.getId());
			M8StockItem stockItem = managedPhone.getStockItems().get(0);
			// [LIFECYCLE] stock_item được tải qua product nên MANAGED
			assertState(em, stockItem, MANAGED);
			sql.reset();
			stockItem.setQuantity(20);
			// [FLUSH] lúc commit: dirty checking thấy quantity đổi → UPDATE stock_item
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertUpdates("stock_item", 1);
		sql.assertUpdates("product", 0);
		sql.assertInserts("stock_item", 0);

		// ── THEN: DB thật ──
		assertThat(db.rowsOf(M8 + ".stock_item").get(0).get("quantity")).as("quantity đã là 20").isEqualTo(20);
	}

	/**
	 * G7-S10c — M8: gỡ dòng tồn kho khỏi product ({@code removeStock})
	 *
	 * <p>Mapping: M8, {@code Product.stockItems} có {@code orphanRemoval = true}.
	 * <p><b>Câu hỏi</b>: bỏ một dòng khỏi list thì dòng stock_item có bị xoá không?
	 * <p><b>Kết luận</b>: có, đúng 1 DELETE {@code stock_item} nhờ orphanRemoval. Product và warehouse còn nguyên.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S10c | M8: removeStock DELETE stock_item nhờ orphanRemoval")
	void s10c_m8RemoveStockDeletesJoinEntity() {
		// ── GIVEN: phone có tồn kho ở kho Hà Nội ──
		M8Product phone = new M8Product("Phone");
		M8Warehouse warehouseHanoi = new M8Warehouse("Hanoi");
		inTx(() -> {
			em.persist(phone);
			em.persist(warehouseHanoi);
		});
		inTx(() -> em.find(M8Product.class, phone.getId()).addStock(em.find(M8Warehouse.class, warehouseHanoi.getId()), 10));

		// ── WHEN: gỡ tồn kho kho Hà Nội ──
		inTx(() -> {
			M8Product managedPhone = em.find(M8Product.class, phone.getId());
			managedPhone.getStockItems().size(); // tải list trước
			sql.reset();
			managedPhone.removeStock(warehouseHanoi);
			// [FLUSH] lúc commit: stock_item thành orphan → DELETE
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertDeletes("stock_item", 1);

		// ── THEN: DB thật ──
		assertThat(db.countOf(M8 + ".stock_item")).as("dòng tồn kho đã bị xoá").isZero();
		assertThat(db.countOf(M8 + ".product")).as("product còn nguyên").isEqualTo(1);
		assertThat(db.countOf(M8 + ".warehouse")).as("warehouse còn nguyên").isEqualTo(1);
	}

	/**
	 * G7-S11a — M8: {@code find} bằng {@code StockItemId}
	 *
	 * <p>Mapping: M8, {@code @EmbeddedId StockItemId}.
	 * <p><b>Câu hỏi</b>: lấy lại một dòng tồn kho theo khoá ghép thế nào?
	 * <p><b>Kết luận</b>: {@code em.find(StockItem.class, new StockItemId(productId, warehouseId))} trả về đúng dòng
	 * (1 SELECT). Id khác giá trị thì trả về {@code null}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S11a | M8: find(StockItem, new StockItemId(productId, warehouseId)) trả đúng dòng")
	void s11a_m8FindByEmbeddedId() {
		// ── GIVEN: phone có 10 sản phẩm ở kho Hà Nội, kho Huế chưa có gì ──
		M8Product phone = new M8Product("Phone");
		M8Warehouse warehouseHanoi = new M8Warehouse("Hanoi");
		M8Warehouse warehouseHue = new M8Warehouse("Hue");
		inTx(() -> {
			em.persist(phone);
			em.persist(warehouseHanoi);
			em.persist(warehouseHue);
		});
		inTx(() -> em.find(M8Product.class, phone.getId()).addStock(em.find(M8Warehouse.class, warehouseHanoi.getId()), 10));

		// ── WHEN: find theo hai StockItemId khác nhau ──
		inTx(() -> {
			sql.reset();
			M8StockItem found = em.find(M8StockItem.class, new M8StockItemId(phone.getId(), warehouseHanoi.getId()));
			M8StockItem notFound = em.find(M8StockItem.class, new M8StockItemId(phone.getId(), warehouseHue.getId()));

			// ── THEN ──
			assertThat(found).as("có dòng cho (phone, Hà Nội)").isNotNull();
			assertThat(found.getQuantity()).as("đúng số lượng 10").isEqualTo(10);
			assertThat(notFound).as("không có dòng cho (phone, Huế)").isNull();
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("stock_item", 2);
	}

	/**
	 * G7-S11b — M8: {@code StockItemId} so sánh theo giá trị
	 *
	 * <p>Mapping: M8, {@code StockItemId implements Serializable} với {@code equals/hashCode} theo giá trị.
	 * <p><b>Câu hỏi</b>: vì sao id ghép phải có {@code equals/hashCode}?
	 * <p><b>Kết luận</b>: hai object khác nhau nhưng cùng (productId, warehouseId) phải {@code equals} và cùng
	 * {@code hashCode}, vì persistence context dùng id làm khoá của Map. Trong cùng một transaction, hai lần {@code find}
	 * bằng hai object id KHÁC NHAU nhưng cùng giá trị trả về CÙNG một instance (chỉ 1 SELECT).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S11b | M8: hai StockItemId cùng giá trị phải equals và find trả cùng một instance")
	void s11b_m8StockItemIdEqualsByValue() {
		// ── GIVEN: hai object id khác nhau, cùng giá trị (1, 2) ──
		M8StockItemId idFirst = new M8StockItemId(1L, 2L);
		M8StockItemId idSecond = new M8StockItemId(1L, 2L);

		// ── THEN: equals / hashCode theo giá trị ──
		assertThat(idFirst).as("cùng productId và warehouseId nên phải equals dù là hai object").isEqualTo(idSecond);
		assertThat(idFirst.hashCode()).as("equals thì hashCode phải bằng nhau, nếu không HashMap của persistence context hỏng")
			.isEqualTo(idSecond.hashCode());
		assertThat(idFirst).as("khác warehouseId thì là hai khoá khác nhau").isNotEqualTo(new M8StockItemId(1L, 3L));
		assertThat(idFirst).as("JPA yêu cầu composite id phải Serializable").isInstanceOf(Serializable.class);

		// ── GIVEN: dữ liệu thật để find ──
		M8Product phone = new M8Product("Phone");
		M8Warehouse warehouseHanoi = new M8Warehouse("Hanoi");
		inTx(() -> {
			em.persist(phone);
			em.persist(warehouseHanoi);
		});
		inTx(() -> em.find(M8Product.class, phone.getId()).addStock(em.find(M8Warehouse.class, warehouseHanoi.getId()), 10));

		// ── WHEN: find hai lần bằng hai object id khác nhau, cùng giá trị ──
		inTx(() -> {
			sql.reset();
			M8StockItem first = em.find(M8StockItem.class, new M8StockItemId(phone.getId(), warehouseHanoi.getId()));
			M8StockItem second = em.find(M8StockItem.class, new M8StockItemId(phone.getId(), warehouseHanoi.getId()));

			// ── THEN ──
			assertThat(second).as("cùng giá trị id nên persistence context trả lại đúng instance đã tải").isSameAs(first);
		});
		sql.assertSelects("stock_item", 1);
	}

	/**
	 * G7-S12a — M8 vs M9: DDL
	 *
	 * <p>Mapping: M8 (PK ghép) và M9 (id SEQUENCE + UNIQUE).
	 * <p><b>Câu hỏi</b>: hai cách làm join entity khác nhau thế nào ở DB?
	 * <p><b>Kết luận</b>: M8: PK = (product_id, warehouse_id), không có sequence, không cần UNIQUE vì PK đã chặn cặp trùng.
	 * M9: PK = id, có {@code stock_item_seq}, và UNIQUE (product_id, warehouse_id) do mình tự khai. Bảng khác muốn trỏ
	 * tới một dòng tồn kho: M9 chỉ cần FK 1 cột (id), M8 phải FK 2 cột (nối sang nhóm G8).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S12a | M8 vs M9: PK ghép hai cột so với id riêng + UNIQUE")
	void s12a_m8VersusM9Ddl() {
		// ── THEN: DDL ──
		schema.printDdl(M8);
		schema.printDdl(M9);
		assertThat(schema.primaryKeyOf(M8, "stock_item")).as("M8: PK là cặp hai FK").containsExactly("product_id", "warehouse_id");
		assertThat(schema.uniqueConstraintsOf(M8, "stock_item")).as("M8: PK đã chặn trùng nên không cần UNIQUE").isEmpty();
		assertThat(schema.sequencesOf(M8)).as("M8: không có sequence cho stock_item").containsExactly("product_seq", "warehouse_seq");

		assertThat(schema.primaryKeyOf(M9, "stock_item")).as("M9: PK là id riêng").containsExactly("id");
		assertThat(schema.uniqueConstraintsOf(M9, "stock_item")).as("M9: phải tự khai UNIQUE cho cặp (product_id, warehouse_id)")
			.containsExactly(List.of("product_id", "warehouse_id"));
		assertThat(schema.sequencesOf(M9)).as("M9: có sequence riêng cho id của stock_item").contains("stockitem_seq");
	}

	/**
	 * G7-S12b — M9: cặp (product, warehouse) trùng bị UNIQUE chặn
	 *
	 * <p>Mapping: M9, {@code @UniqueConstraint(product_id, warehouse_id)}.
	 * <p><b>Câu hỏi</b>: id riêng có làm mất bảo đảm "mỗi sản phẩm một dòng cho mỗi kho" không?
	 * <p><b>Kết luận</b>: chỉ khi quên khai UNIQUE. Có UNIQUE thì dòng thứ hai cùng cặp bị DB từ chối.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S12b | M9: thêm dòng thứ hai cho cùng (product, warehouse) vi phạm UNIQUE")
	void s12b_m9DuplicatePairViolatesUnique() {
		// ── GIVEN: phone đã có tồn kho ở kho Hà Nội ──
		M9Product phone = new M9Product("Phone");
		M9Warehouse warehouseHanoi = new M9Warehouse("Hanoi");
		inTx(() -> {
			em.persist(phone);
			em.persist(warehouseHanoi);
		});
		inTx(() -> em.find(M9Product.class, phone.getId()).addStock(em.find(M9Warehouse.class, warehouseHanoi.getId()), 10));

		// ── WHEN: thêm dòng thứ hai cho cùng cặp ──
		assertThatThrownBy(() -> inTx(() -> {
			M9Product managedPhone = em.find(M9Product.class, phone.getId());
			M9Warehouse managedWarehouse = em.find(M9Warehouse.class, warehouseHanoi.getId());
			managedPhone.getStockItems().add(new M9StockItem(managedPhone, managedWarehouse, 5));
			// [FLUSH] lúc commit: INSERT stock_item lần hai cho cùng cặp
		}))
			.as("UNIQUE (product_id, warehouse_id) không cho hai dòng cùng cặp")
			.isInstanceOf(DataIntegrityViolationException.class);

		// ── THEN: DB thật ──
		assertThat(db.countOf(M9 + ".stock_item")).as("rollback: chỉ còn dòng đầu").isEqualTo(1);
	}
}
