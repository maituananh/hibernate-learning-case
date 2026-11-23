package com.example.hibernatelab.g8_composite_fk;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.State.DETACHED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.hibernatelab.g8_composite_fk.j1_explicit.J1Shipment;
import com.example.hibernatelab.g8_composite_fk.j1_explicit.J1ShipmentLine;
import com.example.hibernatelab.g8_composite_fk.j1_explicit.J1ShipmentLineRepository;
import com.example.hibernatelab.g8_composite_fk.j1_explicit.J1Warehouse;
import com.example.hibernatelab.g8_composite_fk.j1_explicit.J1WarehouseId;
import com.example.hibernatelab.g8_composite_fk.j1p_persistable.J1PShipment;
import com.example.hibernatelab.g8_composite_fk.j1p_persistable.J1PShipmentLine;
import com.example.hibernatelab.g8_composite_fk.j1p_persistable.J1PShipmentLineRepository;
import com.example.hibernatelab.support.AbstractScenarioTest;
import com.example.hibernatelab.support.SchemaInspector.ForeignKey;

/**
 * Nhóm G8, phần "derived identity" ({@code @MapsId("field")} với {@code @EmbeddedId}): S04 (DDL, persist,
 * Spring Data {@code save}, {@code Persistable}) và S07 (đổi một phần PK ghép).
 *
 * <p>Điểm cần nắm: {@code ShipmentLine} có PK ghép {@code (shipment_id, line_no)}; {@code shipment_id} do shipment quyết định
 * (derived), chỉ {@code line_no} do người dùng gán. Đó là lý do id "đã khác null một phần" và là gốc của bẫy {@code save()}.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G8DerivedIdentityTest extends AbstractScenarioTest {

	private static final String J1 = "g8_j1_explicit";
	private static final String J1P = "g8_j1p_persistable";

	@Autowired
	private J1ShipmentLineRepository lineRepository;

	@Autowired
	private J1PShipmentLineRepository persistableLineRepository;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { J1, J1P };
	}

	/**
	 * G8-S04a — J1: DDL của derived identity
	 *
	 * <p>Mapping: J1, {@code ShipmentLine} có {@code @EmbeddedId ShipmentLineId(shipmentId, lineNo)} và
	 * {@code @MapsId("shipmentId") shipment}.
	 * <p><b>Câu hỏi</b>: cột {@code shipment_id} là PK, FK, hay cả hai?
	 * <p><b>Kết luận</b>: CẢ HAI: nó nằm trong PK ghép và đồng thời là FK trỏ {@code shipment(id)}. Không có sequence riêng
	 * cho shipment_line. Thứ tự cột PK do Hibernate chọn, không nhất thiết theo thứ tự khai báo (xem docs/RESULTS.md).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G8-S04a | J1: shipment_id vừa là PK vừa là FK trong PK ghép (shipment_id, line_no)")
	void s04a_j1DerivedIdentityDdl() {
		// ── THEN: DDL ──
		schema.printDdl(J1);
		System.out.println("[S04a] PK shipment_line = " + schema.primaryKeyOf(J1, "shipment_line"));
		assertThat(schema.primaryKeyOf(J1, "shipment_line")).as("PK ghép gồm đúng hai cột")
			.containsExactlyInAnyOrder("shipment_id", "line_no");
		ForeignKey foreignKey = schema.foreignKeyOnColumn(J1, "shipment_line", "shipment_id");
		assertThat(foreignKey.targetTable()).as("shipment_id là FK trỏ bảng shipment").isEqualTo(J1 + ".shipment");
		assertThat(foreignKey.targetColumn()).as("trỏ tới id của shipment").isEqualTo("id");
		assertThat(schema.hasColumn(J1, "shipment_line", "id")).as("không có cột id riêng: PK chính là (shipment_id, line_no)").isFalse();
		assertThat(schema.sequencesOf(J1)).as("shipment_line không có sequence riêng")
			.doesNotContain("shipment_line_seq");
	}

	/**
	 * G8-S04b — J1: persist line, Hibernate tự điền {@code shipmentId}
	 *
	 * <p>Mapping: J1.
	 * <p><b>Câu hỏi</b>: persist line với {@code new ShipmentLineId(null, 1)} + {@code setShipment(s)} có chạy không?
	 * <p><b>Kết luận</b>: chạy, 1 INSERT, không SELECT. Lúc persist, {@code @MapsId} copy id của shipment vào
	 * {@code line.getId().getShipmentId()}, nên id đầy đủ ngay sau {@code persist}. Lưu ý: trước persist, object mới này đã bị
	 * Hibernate xếp là DETACHED (id khác null, không {@code @Version}); {@code persist} vẫn chấp nhận.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G8-S04b | J1: persist(line) với ShipmentLineId(null, 1) → Hibernate tự điền shipmentId từ shipment")
	void s04b_j1PersistFillsShipmentIdFromShipment() {
		// ── GIVEN: shipment đã persist ──
		J1Warehouse warehouseVn = new J1Warehouse(new J1WarehouseId("VN", "HN01"), "Kho Hà Nội");
		J1Shipment shipmentA = new J1Shipment("SHIP-A", warehouseVn);
		inTx(() -> {
			em.persist(warehouseVn);
			em.persist(shipmentA);
		});

		// ── WHEN: persist line mới ──
		inTx(() -> {
			J1Shipment managedShipment = em.find(J1Shipment.class, shipmentA.getId());
			J1ShipmentLine lineOne = new J1ShipmentLine(managedShipment, 1, "Bàn phím");
			// [LIFECYCLE] object mới new ra nhưng lineNo đã khác null, không có @Version: Hibernate KHÔNG phân biệt được
			// "mới" với "cũ" nên báo DETACHED (cùng gốc với bẫy save() ở S04c). persist bên dưới vẫn chạy bình thường.
			assertState(em, lineOne, DETACHED);
			assertThat(lineOne.getId().getShipmentId()).as("trước persist shipmentId còn null").isNull();
			sql.reset();
			em.persist(lineOne);
			// [LIFECYCLE] persist: TRANSIENT → MANAGED
			assertState(em, lineOne, MANAGED);
			assertThat(lineOne.getId().getShipmentId())
				.as("@MapsId đã copy id của shipment vào shipmentId ngay lúc persist").isEqualTo(managedShipment.getId());
			// [FLUSH] lúc commit: INSERT shipment_line
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("shipment_line", 1);
		sql.assertSelects("shipment_line", 0);

		// ── THEN: DB thật ──
		db.print(J1 + ".shipment_line");
		assertThat(db.countOf(J1 + ".shipment_line")).as("một dòng hàng").isEqualTo(1);
		assertThat(db.rowsOf(J1 + ".shipment_line").get(0).get("shipment_id")).as("shipment_id = id của shipment").isEqualTo(shipmentA.getId());
		assertThat(db.rowsOf(J1 + ".shipment_line").get(0).get("line_no")).as("line_no do người dùng gán").isEqualTo(1);
	}

	/**
	 * G8-S04c — 🔬 J1: Spring Data {@code save(line)} với id ghép đã gán một phần
	 *
	 * <p>Mapping: J1 + {@code J1ShipmentLineRepository}.
	 * <p><b>Câu hỏi</b>: {@code save(line)} đi theo persist hay merge?
	 * <p><b>Kết luận</b>: MERGE (giả thuyết đúng). {@code lineNo} đã khác null và không có {@code @Version} nên
	 * {@code isNew() = false}: Spring Data gọi {@code merge}, Hibernate SELECT dòng hàng (kèm left join notes, vì
	 * {@code notes} có cascade) để kiểm tra đã có chưa, không thấy mới INSERT. Tức thừa 1 SELECT so với persist (S04b).
	 * Dữ liệu cuối vẫn đúng, nên bẫy này chỉ tốn hiệu năng, không sai kết quả. Cách sửa: S04d.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G8-S04c | 🔬 J1: lineRepository.save(line) với id ghép đã khác null → merge, SELECT thừa rồi INSERT")
	void s04c_j1SpringDataSaveWithPartiallyAssignedCompositeIdMerges() {
		// ── GIVEN: shipment đã persist, line mới (lineNo = 1 đã gán, shipmentId còn null) ──
		J1Warehouse warehouseVn = new J1Warehouse(new J1WarehouseId("VN", "HN01"), "Kho Hà Nội");
		J1Shipment shipmentA = new J1Shipment("SHIP-A", warehouseVn);
		inTx(() -> {
			em.persist(warehouseVn);
			em.persist(shipmentA);
		});
		J1ShipmentLine lineOne = new J1ShipmentLine(shipmentA, 1, "Bàn phím");

		// ── WHEN: save (repository tự mở và commit transaction) ──
		sql.reset();
		J1ShipmentLine returnedLine = lineRepository.save(lineOne);

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("shipment_line", 1); // thừa: merge phải hỏi DB xem dòng đã có chưa
		sql.assertInserts("shipment_line", 1);
		sql.assertInOrder("select", "insert into g8_j1_explicit.shipment_line");
		assertThat(returnedLine).as("merge trả về bản MANAGED khác object truyền vào").isNotSameAs(lineOne);

		// ── THEN: DB thật ──
		db.print(J1 + ".shipment_line");
		assertThat(db.countOf(J1 + ".shipment_line")).as("dòng hàng vẫn được lưu đúng một dòng").isEqualTo(1);
		assertThat(db.rowsOf(J1 + ".shipment_line").get(0).get("shipment_id")).as("@MapsId vẫn điền shipment_id khi merge").isEqualTo(shipmentA.getId());
	}

	/**
	 * G8-S04d — J1P: {@code Persistable} sửa bẫy của {@code save()} (nối với G0-S11)
	 *
	 * <p>Mapping: J1P (như J1, nhưng {@code ShipmentLine implements Persistable} với cờ {@code isNew}).
	 * <p><b>Câu hỏi</b>: {@code isNew() = true} có giúp {@code save()} persist thẳng không?
	 * <p><b>Kết luận</b>: CÓ. Spring Data hỏi {@code isNew()} thay vì đoán theo id: 1 INSERT, 0 SELECT.
	 * Sau persist, cờ được hạ xuống false (qua {@code @PostPersist}) để lần save sau đi đúng đường cập nhật.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G8-S04d | J1P: Persistable.isNew() = true thì save(line) persist thẳng, không SELECT thừa")
	void s04d_j1pPersistableSaveAvoidsMerge() {
		// ── GIVEN: shipment đã persist, line mới ──
		J1PShipment shipmentA = new J1PShipment("SHIP-A");
		inTx(() -> em.persist(shipmentA));
		J1PShipmentLine lineOne = new J1PShipmentLine(shipmentA, 1, "Bàn phím");
		assertThat(lineOne.isNew()).as("object vừa new ra tự báo mình là mới").isTrue();

		// ── WHEN: save ──
		sql.reset();
		J1PShipmentLine returnedLine = persistableLineRepository.save(lineOne);

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("shipment_line", 0);
		sql.assertInserts("shipment_line", 1);
		assertThat(returnedLine).as("persist dùng lại chính object truyền vào (khác merge ở S04c)").isSameAs(lineOne);
		assertThat(lineOne.isNew()).as("@PostPersist đã hạ cờ isNew").isFalse();

		// ── THEN: DB thật ──
		db.print(J1P + ".shipment_line");
		assertThat(db.countOf(J1P + ".shipment_line")).as("một dòng hàng").isEqualTo(1);
		assertThat(db.rowsOf(J1P + ".shipment_line").get(0).get("shipment_id")).as("shipment_id vẫn lấy từ shipment").isEqualTo(shipmentA.getId());
	}

	/**
	 * G8-S07 — 🔬 J1: sửa một phần PK ghép của entity đã persist
	 *
	 * <p>Mapping: J1, {@code WarehouseId} (embeddable, có setter {@code setCode}).
	 * <p><b>Câu hỏi</b>: {@code warehouse.getId().setCode("HN02")} trên entity MANAGED thì sao?
	 * <p><b>Kết luận</b>: KHÁC giả thuyết "identifier altered": KHÔNG lỗi, KHÔNG có câu SQL nào, DB không đổi (vẫn
	 * {@code HN01}). Lý do: Hibernate giữ CHÍNH object id đó làm khoá trong persistence context nên không thấy "id khác đi",
	 * và PK không nằm trong dirty checking (không sinh UPDATE). Thay đổi bị bỏ qua trong im lặng, còn object trong bộ nhớ
	 * thì đã sai lệch so với DB. Bài học: PK là bất biến, id embeddable nên KHÔNG có setter (class này có setter chỉ để làm
	 * thí nghiệm). Muốn "đổi mã kho" thì tạo dòng mới rồi chuyển dữ liệu liên quan.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G8-S07 | 🔬 J1: setCode() trên id của warehouse đang MANAGED bị bỏ qua im lặng (không lỗi, không UPDATE)")
	void s07_j1ChangingPartOfCompositeIdIsSilentlyIgnored() {
		// ── GIVEN: kho VN/HN01 đã lưu ──
		J1Warehouse warehouseVn = new J1Warehouse(new J1WarehouseId("VN", "HN01"), "Kho Hà Nội");
		inTx(() -> em.persist(warehouseVn));

		// ── WHEN: sửa một phần id của entity MANAGED ──
		Throwable failure = catchThrowable(() -> inTx(() -> {
			J1Warehouse managedWarehouse = em.find(J1Warehouse.class, new J1WarehouseId("VN", "HN01"));
			// [LIFECYCLE] kho vừa tải nên MANAGED
			assertState(em, managedWarehouse, MANAGED);
			sql.reset();
			managedWarehouse.getId().setCode("HN02");
			// [FLUSH] lúc commit: dirty checking không xét PK nên không có UPDATE
			// [TX END] commit
		}));

		// ── THEN: SQL ──
		sql.print();
		assertThat(failure).as("không có lỗi kiểu 'identifier altered': Hibernate không so sánh id mới với id cũ").isNull();
		sql.assertTotal(0);

		// ── THEN: DB thật ──
		System.out.println("[S07] rows = " + db.rowsOf(J1 + ".warehouse"));
		assertThat(db.countOf(J1 + ".warehouse")).as("vẫn một kho").isEqualTo(1);
		assertThat(db.rowsOf(J1 + ".warehouse").get(0).get("code")).as("DB không đổi: vẫn HN01, thay đổi bị bỏ qua im lặng").isEqualTo("HN01");
	}
}
