package com.example.hibernatelab.g8_composite_fk;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.hibernate.Hibernate;
import org.hibernate.MappingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.bootfail.g8s03.MissingReferencedColumnShipment;
import com.example.hibernatelab.g8_composite_fk.j1_explicit.J1Shipment;
import com.example.hibernatelab.g8_composite_fk.j1_explicit.J1ShipmentLine;
import com.example.hibernatelab.g8_composite_fk.j1_explicit.J1Warehouse;
import com.example.hibernatelab.g8_composite_fk.j1_explicit.J1WarehouseId;
import com.example.hibernatelab.g8_composite_fk.j3_idclass.J3Shipment;
import com.example.hibernatelab.g8_composite_fk.j3_idclass.J3Warehouse;
import com.example.hibernatelab.g8_composite_fk.j3_idclass.J3WarehouseKey;
import com.example.hibernatelab.g8_composite_fk.j4_natural_key_ref.J4OrderItem;
import com.example.hibernatelab.g8_composite_fk.j4_natural_key_ref.J4Product;
import com.example.hibernatelab.support.AbstractScenarioTest;
import com.example.hibernatelab.support.MappingBootstrap;
import com.example.hibernatelab.support.SchemaInspector.ForeignKey;

/**
 * Nhóm G8, phần "FK nhiều cột": J1 (khai báo tường minh), J2 (mặc định), J3 ({@code @IdClass}), J4 (FK trỏ tới cột unique
 * không phải PK). S01 (a, b, c), S02, S03 (BOOT-FAIL), S05, S06.
 *
 * <p>Điểm cần nắm: khi PK của bảng cha có HAI cột thì FK ở bảng con cũng phải có HAI cột, và Postgres chỉ chấp nhận
 * MỘT constraint ghép (không phải hai FK rời). {@code @JoinColumns} là cách nói cho Hibernate cột nào ghép cột nào.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G8CompositeFkTest extends AbstractScenarioTest {

	private static final String J1 = "g8_j1_explicit";
	private static final String J2 = "g8_j2_defaults";
	private static final String J3 = "g8_j3_idclass";
	private static final String J4 = "g8_j4_natural_key_ref";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { J1, J2, J3, J4 };
	}

	/**
	 * G8-S01a — J1: DDL của FK hai cột
	 *
	 * <p>Mapping: J1, {@code Shipment.warehouse} có {@code @JoinColumns} với hai {@code @JoinColumn(name, referencedColumnName)}.
	 * <p><b>Câu hỏi</b>: Hibernate sinh một FK ghép hay hai FK rời?
	 * <p><b>Kết luận</b>: MỘT constraint duy nhất gồm hai cột {@code (wh_country, wh_code)} trỏ tới
	 * {@code warehouse(country_code, code)}. Hai FK rời sẽ sai nghĩa: kho ("VN","HN01") tồn tại không có nghĩa
	 * country "SG" và code "HN01" hợp lệ riêng lẻ.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G8-S01a | J1: @JoinColumns sinh MỘT FK constraint hai cột tới PK ghép của warehouse")
	void s01a_j1GeneratesSingleTwoColumnForeignKey() {
		// ── THEN: DDL ──
		schema.printDdl(J1);
		List<ForeignKey> foreignKeys = schema.foreignKeysOf(J1, "shipment");
		assertThat(foreignKeys).as("hai cột FK, mỗi cột một phần tử trong danh sách").hasSize(2);
		assertThat(foreignKeys.stream().map(ForeignKey::constraintName).distinct().count())
			.as("cả hai cột thuộc CÙNG một constraint (FK ghép), không phải hai FK rời").isEqualTo(1);
		assertThat(foreignKeys.get(0).column()).as("cột FK thứ nhất theo thứ tự khai báo").isEqualTo("wh_country");
		assertThat(foreignKeys.get(0).targetColumn()).as("ghép với country_code nhờ referencedColumnName").isEqualTo("country_code");
		assertThat(foreignKeys.get(1).column()).as("cột FK thứ hai").isEqualTo("wh_code");
		assertThat(foreignKeys.get(1).targetColumn()).as("ghép với code nhờ referencedColumnName").isEqualTo("code");
		assertThat(foreignKeys.get(0).targetTable()).as("đích là bảng warehouse cùng schema").isEqualTo(J1 + ".warehouse");
		assertThat(schema.columnNamed(J1, "shipment", "wh_country").nullable()).as("nullable = false trên @JoinColumn").isFalse();
		assertThat(schema.primaryKeyOf(J1, "warehouse")).as("PK của warehouse cũng là hai cột").containsExactly("country_code", "code");
	}

	/**
	 * G8-S01b — J1: persist và query theo warehouse
	 *
	 * <p>Mapping: J1.
	 * <p><b>Câu hỏi</b>: lưu shipment trỏ tới kho PK ghép thì INSERT ghi những cột nào, query theo kho cần join không?
	 * <p><b>Kết luận</b>: INSERT ghi cả hai cột FK lấy từ id của kho. Query {@code where s.warehouse = :w} lọc thẳng trên hai
	 * cột FK của shipment bằng so sánh tuple {@code (wh_code, wh_country) = (?, ?)}, không join sang warehouse.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G8-S01b | J1: INSERT ghi 2 cột FK, query theo warehouse lọc thẳng trên 2 cột (không join)")
	void s01b_j1PersistAndQueryByWarehouse() {
		// ── GIVEN: kho VN/HN01 và kho SG/HN01 (cùng code, khác country) ──
		J1Warehouse warehouseVn = new J1Warehouse(new J1WarehouseId("VN", "HN01"), "Kho Hà Nội");
		J1Warehouse warehouseSg = new J1Warehouse(new J1WarehouseId("SG", "HN01"), "Kho Singapore");
		inTx(() -> {
			em.persist(warehouseVn);
			em.persist(warehouseSg);
		});

		// ── WHEN: tạo hai chuyến hàng, một chuyến mỗi kho ──
		inTx(() -> {
			J1Warehouse managedVn = em.find(J1Warehouse.class, warehouseVn.getId());
			J1Warehouse managedSg = em.find(J1Warehouse.class, warehouseSg.getId());
			sql.reset();
			em.persist(new J1Shipment("SHIP-VN", managedVn));
			em.persist(new J1Shipment("SHIP-SG", managedSg));
			// [FLUSH] lúc commit: INSERT shipment (wh_country, wh_code, ...) cho cả hai
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("shipment", 2);
		sql.assertInserts("warehouse", 0);

		// ── THEN: DB thật ──
		db.print(J1 + ".shipment");
		assertThat(db.countOf(J1 + ".shipment")).as("hai chuyến hàng").isEqualTo(2);
		assertThat(db.rowsOf(J1 + ".shipment").get(0).get("wh_country")).as("wh_country lấy từ countryCode của id kho").isEqualTo("VN");
		assertThat(db.rowsOf(J1 + ".shipment").get(0).get("wh_code")).as("wh_code lấy từ code của id kho").isEqualTo("HN01");

		// ── WHEN: query theo warehouse ──
		List<J1Shipment> shipmentsOfVn = inTxReturning(() -> {
			J1Warehouse managedVn = em.find(J1Warehouse.class, warehouseVn.getId());
			sql.reset();
			return em.createQuery("select s from G8J1_Shipment s where s.warehouse = :warehouse", J1Shipment.class)
				.setParameter("warehouse", managedVn)
				.getResultList();
		});

		// ── THEN ──
		sql.print();
		assertThat(shipmentsOfVn).as("chỉ chuyến của kho VN/HN01, không lẫn SG/HN01 dù cùng code").hasSize(1);
		assertThat(shipmentsOfVn.get(0).getReference()).as("đúng chuyến VN").isEqualTo("SHIP-VN");
		sql.assertNoStatementContains("join");
		sql.assertSomeStatementContains("(js1_0.wh_code,js1_0.wh_country)=(?,?)"); // so sánh theo tuple (cả hai cột cùng lúc)
	}

	/**
	 * G8-S01c — J3: {@code @IdClass} thay {@code @EmbeddedId}
	 *
	 * <p>Mapping: J3, {@code Warehouse} có {@code @IdClass(WarehouseKey)} và hai {@code @Id} nằm thẳng trong entity;
	 * {@code Shipment} khai báo {@code @JoinColumns} y hệt J1.
	 * <p><b>Câu hỏi</b>: {@code @IdClass} có làm đổi DDL hay cách khai báo FK không?
	 * <p><b>Kết luận</b>: KHÔNG. DDL (PK hai cột, một FK ghép hai cột) y hệt J1 và {@code @JoinColumns} viết giống hệt, vì
	 * {@code referencedColumnName} chỉ nói tên CỘT. Khác biệt chỉ ở code Java: {@code find} nhận một object
	 * {@code WarehouseKey}, và đọc id bằng {@code warehouse.getCountryCode()} thay vì {@code warehouse.getId().getCountryCode()}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G8-S01c | J3: @IdClass cho DDL y hệt J1, find nhận object WarehouseKey")
	void s01c_j3IdClassHasSameDdlAsEmbeddedId() {
		// ── GIVEN: kho dùng @IdClass, shipment trỏ tới kho ──
		J3Warehouse warehouseVn = new J3Warehouse("VN", "HN01", "Kho Hà Nội");
		inTx(() -> em.persist(warehouseVn));
		inTx(() -> em.persist(new J3Shipment("SHIP-VN", em.find(J3Warehouse.class, new J3WarehouseKey("VN", "HN01")))));

		// ── THEN: DDL ──
		schema.printDdl(J3);
		assertThat(schema.primaryKeyOf(J3, "warehouse")).as("@IdClass cũng cho PK hai cột").containsExactly("country_code", "code");
		List<ForeignKey> foreignKeys = schema.foreignKeysOf(J3, "shipment");
		assertThat(foreignKeys.stream().map(ForeignKey::column).toList())
			.as("FK hai cột giống J1").containsExactly("wh_country", "wh_code");
		assertThat(foreignKeys.stream().map(ForeignKey::constraintName).distinct().count())
			.as("vẫn là MỘT constraint ghép").isEqualTo(1);

		// ── THEN: DB thật ──
		assertThat(db.rowsOf(J3 + ".shipment").get(0).get("wh_country")).as("wh_country lấy từ @Id countryCode").isEqualTo("VN");
		assertThat(db.rowsOf(J3 + ".shipment").get(0).get("wh_code")).as("wh_code lấy từ @Id code").isEqualTo("HN01");

		// ── THEN: find bằng object id ──
		inTx(() -> {
			J3Warehouse foundWarehouse = em.find(J3Warehouse.class, new J3WarehouseKey("VN", "HN01"));
			assertThat(foundWarehouse).as("find nhận một object WarehouseKey có cùng giá trị (equals theo giá trị)").isNotNull();
			assertThat(foundWarehouse.getName()).as("đúng kho").isEqualTo("Kho Hà Nội");
		});
	}

	/**
	 * G8-S02 — 🔬 J2: tên cột FK mặc định khi không có {@code @JoinColumns}
	 *
	 * <p>Mapping: J2, {@code @ManyToOne} trơn tới {@code Warehouse} có PK ghép.
	 * <p><b>Câu hỏi</b>: Hibernate đặt tên hai cột FK là gì, và có sinh FK ghép không?
	 * <p><b>Kết luận</b>: tên cột = <i>tên field</i> + "_" + <i>tên cột PK đích</i>: {@code warehouse_country_code} và
	 * {@code warehouse_code}. Vẫn là MỘT FK ghép (không phải hai FK rời). Cột nullable vì {@code optional} mặc định là true.
	 * Tên mặc định vẫn chạy được, nhưng muốn đặt tên theo quy ước riêng (ví dụ {@code wh_country}) thì phải dùng {@code @JoinColumns}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G8-S02 | 🔬 J2: @ManyToOne trơn tới PK ghép, tên cột FK mặc định là warehouse_country_code, warehouse_code")
	void s02_j2DefaultForeignKeyColumnNames() {
		// ── THEN: DDL ──
		schema.printDdl(J2);
		List<ForeignKey> foreignKeys = schema.foreignKeysOf(J2, "shipment");
		assertThat(foreignKeys).as("hai cột FK").hasSize(2);
		assertThat(foreignKeys.stream().map(ForeignKey::constraintName).distinct().count())
			.as("vẫn là MỘT constraint ghép dù không có @JoinColumns").isEqualTo(1);
		assertThat(foreignKeys.stream().map(ForeignKey::column).toList())
			.as("tên cột = tên field (warehouse) + _ + tên cột PK đích").containsExactly("warehouse_country_code", "warehouse_code");
		assertThat(foreignKeys.stream().map(ForeignKey::targetColumn).toList())
			.as("cột đích là cột PK của warehouse, ghép đúng thứ tự").containsExactly("country_code", "code");
		assertThat(schema.columnNamed(J2, "shipment", "warehouse_code").nullable())
			.as("không có optional = false nên cột cho phép NULL").isTrue();
		assertThat(schema.hasColumn(J2, "shipment", "wh_country")).as("tên kiểu wh_country chỉ có khi tự khai @JoinColumns (J1)").isFalse();
	}

	/**
	 * G8-S03 — [BOOT-FAIL] 🔬 J1 thiếu {@code referencedColumnName} ở một cột
	 *
	 * <p>Mapping: entity sai {@code bootfail/g8s03/MissingReferencedColumnShipment}.
	 * <p><b>Câu hỏi</b>: lỗi lúc khởi động, hay Hibernate tự suy ra cột đích theo thứ tự?
	 * <p><b>Kết luận</b>: LỖI lúc khởi động: {@code MappingException: Column with logical name 'null' in table 'warehouse'
	 * cannot be mapped to column identifier}. Hibernate KHÔNG đoán theo thứ tự: khi FK nhiều cột thì mọi
	 * {@code @JoinColumn} đều phải có {@code referencedColumnName}. (Thông báo hơi khó hiểu: "logical name 'null'" chính là
	 * referencedColumnName bị thiếu.)
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G8-S03 | [BOOT-FAIL] 🔬 @JoinColumns thiếu referencedColumnName ở một cột: lỗi lúc khởi động")
	void s03_missingReferencedColumnNameInOneColumn() {
		// ── WHEN: dựng SessionFactory riêng chỉ gồm entity sai (+ J1Warehouse mà nó trỏ tới) ──
		Throwable failure = MappingBootstrap.tryBuild(MissingReferencedColumnShipment.class, J1Warehouse.class);

		// ── THEN ──
		System.out.println("[BOOT-FAIL] " + (failure == null ? "(không lỗi)" : failure.getClass().getName() + ": " + failure.getMessage()));
		assertThat(failure).as("Hibernate không tự suy ra cột đích, nên không khởi động được").isNotNull();
		assertThat(failure).as("lỗi mapping, không phải lỗi DB").isInstanceOf(MappingException.class);
		assertThat(failure.getMessage()).as("logical name 'null' chính là referencedColumnName bị bỏ trống")
			.contains("logical name 'null'").contains("warehouse");
	}

	/**
	 * G8-S05 — J1: chuỗi khoá ghép ba tầng (shipment → line → note)
	 *
	 * <p>Mapping: J1, {@code ShipmentLineNote.line} có {@code @JoinColumns(shipment_id, line_no)} tới PK ghép của line.
	 * <p><b>Câu hỏi</b>: FK của note trông thế nào, và xoá shipment (cascade ALL) thì DELETE theo thứ tự nào?
	 * <p><b>Kết luận</b>: FK hai cột (shipment_id, line_no) tới shipment_line. Xoá shipment: DELETE note → line → shipment
	 * (con trước, cha sau, để không vi phạm FK).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G8-S05 | J1: note trỏ tới line bằng FK 2 cột; xoá shipment DELETE note → line → shipment")
	void s05_j1ThreeLevelChainAndDeleteOrder() {
		// ── GIVEN: shipment có 1 line, line có 1 note ──
		J1Warehouse warehouseVn = new J1Warehouse(new J1WarehouseId("VN", "HN01"), "Kho Hà Nội");
		inTx(() -> em.persist(warehouseVn));
		J1Shipment shipmentA = new J1Shipment("SHIP-A", warehouseVn);
		J1ShipmentLine lineOne = shipmentA.addLine(1, "Bàn phím");
		lineOne.addNote("Hàng dễ vỡ");
		inTx(() -> em.persist(shipmentA)); // [CASCADE] PERSIST lan xuống line rồi note

		// ── THEN: DDL ──
		List<ForeignKey> noteForeignKeys = schema.foreignKeysOf(J1, "shipment_line_note");
		assertThat(noteForeignKeys).as("note có đúng một FK, gồm hai cột").hasSize(2);
		assertThat(noteForeignKeys.stream().map(ForeignKey::column).toList())
			.as("hai cột FK của note (thứ tự trong constraint do Hibernate chọn nên không assert thứ tự)")
			.containsExactlyInAnyOrder("shipment_id", "line_no");
		assertThat(noteForeignKeys.stream().map(ForeignKey::constraintName).distinct().count())
			.as("vẫn là MỘT constraint ghép hai cột").isEqualTo(1);
		assertThat(noteForeignKeys.get(0).targetTable()).as("đích là shipment_line").isEqualTo(J1 + ".shipment_line");
		assertThat(schema.primaryKeyOf(J1, "shipment_line")).as("PK ghép của line gồm hai cột (thứ tự cột xem G8-S04a)")
			.containsExactlyInAnyOrder("shipment_id", "line_no");

		// ── WHEN: xoá shipment ──
		inTx(() -> {
			J1Shipment managedShipment = em.find(J1Shipment.class, shipmentA.getId());
			sql.reset();
			em.remove(managedShipment);
			// [CASCADE] REMOVE lan xuống line rồi note
			// [FLUSH] lúc commit: DELETE theo thứ tự con trước cha
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInOrder("delete from " + J1 + ".shipment_line_note", "delete from " + J1 + ".shipment_line where", "delete from " + J1 + ".shipment where");

		// ── THEN: DB thật ──
		assertThat(db.countOf(J1 + ".shipment_line_note")).as("note đã bị xoá").isZero();
		assertThat(db.countOf(J1 + ".shipment_line")).as("line đã bị xoá").isZero();
		assertThat(db.countOf(J1 + ".shipment")).as("shipment đã bị xoá").isZero();
		assertThat(db.countOf(J1 + ".warehouse")).as("warehouse không bị đụng tới (không cascade)").isEqualTo(1);
	}

	/**
	 * G8-S06a — J4: DDL của FK trỏ tới cột unique không phải PK
	 *
	 * <p>Mapping: J4, {@code OrderItem.product} có {@code @JoinColumn(name = "product_sku", referencedColumnName = "sku")}.
	 * <p><b>Câu hỏi</b>: FK trỏ tới đâu, và cột đích cần điều kiện gì?
	 * <p><b>Kết luận</b>: FK {@code order_item.product_sku → product.sku}. Cột {@code sku} phải UNIQUE (hoặc PK), nếu không
	 * Postgres từ chối tạo FK. Cột FK lưu chuỗi SKU, không phải id.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G8-S06a | J4: FK order_item.product_sku trỏ tới product.sku (UNIQUE), không phải product.id")
	void s06a_j4ForeignKeyPointsToUniqueNaturalKey() {
		// ── THEN: DDL ──
		schema.printDdl(J4);
		ForeignKey foreignKey = schema.foreignKeyOnColumn(J4, "order_item", "product_sku");
		assertThat(foreignKey.targetTable()).as("đích là bảng product").isEqualTo(J4 + ".product");
		assertThat(foreignKey.targetColumn()).as("đích là cột sku nhờ referencedColumnName, không phải id").isEqualTo("sku");
		assertThat(schema.uniqueConstraintsOf(J4, "product")).as("sku phải UNIQUE thì mới làm đích FK được").contains(List.of("sku"));
		assertThat(schema.columnNamed(J4, "order_item", "product_sku").type()).as("cột FK mang kiểu của sku (chuỗi)").startsWith("varchar");
	}

	/**
	 * G8-S06b — 🔬 J4: LAZY có còn tạo proxy không
	 *
	 * <p>Mapping: J4, {@code product} khai báo {@code LAZY}.
	 * <p><b>Câu hỏi</b>: {@code find(orderItem)} có SELECT product ngay không?
	 * <p><b>Kết luận</b>: CÓ. Dù khai báo LAZY, product được tải NGAY bằng câu SELECT thứ hai {@code where sku = ?}.
	 * Lý do: proxy chỉ chứa sẵn id, mà cột FK lại lưu {@code sku} (không phải id), nên Hibernate không có id để làm proxy
	 * và phải truy vấn product theo sku. Hệ quả: trỏ FK tới khoá không phải PK làm mất lợi ích của LAZY (và dễ gây N+1).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G8-S06b | 🔬 J4: LAZY vô hiệu khi FK trỏ non-PK, find(orderItem) SELECT product ngay theo sku")
	void s06b_j4LazyAssociationToNaturalKeyIsLoadedEagerly() {
		// ── GIVEN: một dòng đơn hàng trỏ tới sản phẩm KB-001 ──
		J4Product keyboard = new J4Product("KB-001", "Bàn phím");
		inTx(() -> em.persist(keyboard));
		J4OrderItem itemA = new J4OrderItem(keyboard, 2);
		inTx(() -> em.persist(itemA));

		// ── WHEN: tải dòng đơn hàng ──
		inTx(() -> {
			sql.reset();
			J4OrderItem managedItem = em.find(J4OrderItem.class, itemA.getId());
			// [LIFECYCLE] dòng đơn hàng vừa tải nên MANAGED
			assertState(em, managedItem, MANAGED);

			// ── THEN: SQL ──
			sql.print();
			sql.assertSelects("order_item", 1);
			sql.assertSelects("product", 1);
			sql.assertSomeStatementContains(".sku=?");
			assertThat(Hibernate.isInitialized(managedItem.getProduct()))
				.as("LAZY nhưng đã được tải: không tạo được proxy theo id vì FK lưu sku").isTrue();
		});
	}
}
