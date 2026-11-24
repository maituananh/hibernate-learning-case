package com.example.hibernatelab.g14_lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g14_lead.i2_buggy.I2BuggyFlashSaleService;
import com.example.hibernatelab.g14_lead.i2_fixed.I2FixedFlashSaleService;
import com.example.hibernatelab.g14_lead.i2_fixed.I2FixedInsufficientStockException;
import com.example.hibernatelab.support.AbstractScenarioTest;
import com.example.hibernatelab.support.ConcurrentTx;
import com.example.hibernatelab.support.ConcurrentTx.Gate;
import com.example.hibernatelab.support.ConcurrentTx.Running;

/**
 * Sự cố G14-I2: "Tồn kho sai sau flash sale". Bốn test theo E7: tái hiện, chẩn đoán, sửa, rào chắn.
 *
 * <p>Kịch bản: kho 10 cái, hai khách MUA CÙNG LÚC, mỗi người 6 cái. Kết quả đúng: một người mua được (kho còn 4),
 * người kia bị báo hết hàng. Thứ tự xen kẽ được ép bằng cổng đồng bộ (không {@code Thread.sleep}):
 * khách A đọc stock xong thì DỪNG, khách B mua trọn vẹn và commit, rồi A mới chạy tiếp với số liệu cũ.
 *
 * <p>Ghi chú so với thiết kế: triệu chứng gốc là "tồn kho âm". Chạy thật cho thấy kho KHÔNG âm (kho ghi 4) mà là
 * <b>bán 12 cái từ kho 10</b>: kho ghi lệch so với đơn đã bán. Đây vẫn là lost update (G11-S05) nhưng triệu chứng quan sát được khác.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G14I2FlashSaleTest extends AbstractScenarioTest {

	private static final int INITIAL_STOCK = 10;
	private static final int QUANTITY_EACH = 6;

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ConcurrentTx concurrent;
	@Autowired
	private I2BuggyFlashSaleService buggyService;
	@Autowired
	private I2FixedFlashSaleService fixedService;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { G14Fixtures.I2_BUGGY, G14Fixtures.I2_FIXED };
	}

	/** Bất biến nghiệp vụ: kho còn lại + tổng đã bán phải bằng kho ban đầu. Vi phạm thì ném AssertionError. */
	private void assertInventoryInvariant(String schema) {
		int stockLeft = jdbc.queryForObject("select stock from " + schema + ".product where id = ?", Integer.class, G14Fixtures.PRODUCT_ID);
		int soldTotal = jdbc.queryForObject("select coalesce(sum(quantity), 0) from " + schema + ".sale", Integer.class);
		assertThat(stockLeft + soldTotal)
			.as("kho còn " + stockLeft + " + đã bán " + soldTotal + " phải bằng kho ban đầu " + INITIAL_STOCK + " (không bán ra hàng không có)")
			.isEqualTo(INITIAL_STOCK);
	}

	/** Khách A và B cùng mua 6 cái trên bản buggy, A dừng giữa chừng để B chen vào. */
	private void twoBuyersCollideOnBuggy() {
		Gate aHasReadStock = new Gate("A đã đọc stock");
		Gate bHasCommitted = new Gate("B đã commit");
		Running<Void> buyerA = concurrent.startOutsideTx(() -> buggyService.buy(G14Fixtures.PRODUCT_ID, QUANTITY_EACH, () -> {
			aHasReadStock.open();
			bHasCommitted.await(); // A giữ số liệu cũ (stock = 10) trong khi B mua xong
		}));
		aHasReadStock.await();
		buggyService.buy(G14Fixtures.PRODUCT_ID, QUANTITY_EACH); // B: 10 → 4, commit
		bHasCommitted.open();
		buyerA.join(); // A: vẫn thấy 10 ≥ 6, trừ → ghi stock = 4
	}

	/**
	 * G14-I2-S01 — TÁI HIỆN: kho 10, hai khách mua 6, cả hai đều "thành công"
	 *
	 * <p>Mapping: I2 buggy (không {@code @Version}). <b>Câu hỏi</b>: hai khách mua 6 cái cùng lúc từ kho 10, chuyện gì xảy ra?
	 * <p><b>Kết luận</b>: không exception nào, cả hai nhận "mua thành công": 2 dòng sale (tổng 12 cái) trong khi kho chỉ có 10,
	 * và kho ghi 4 (đáng ra phải là -2, tức là một người phải bị từ chối). Lỗi im lặng nên chỉ phát hiện khi đối chiếu.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-I2-S01 | tái hiện: kho 10, hai khách mua 6 → bán ra 12 cái, kho ghi 4, không báo lỗi")
	void s01_reproduceOversell() {
		// ── GIVEN ──
		G14Fixtures.seedProductBuggy(jdbc, INITIAL_STOCK);

		// ── WHEN ──
		twoBuyersCollideOnBuggy(); // không exception: nếu có thì test này đã đỏ ở buyerA.join()

		// ── THEN: DB thật ──
		db.print(G14Fixtures.I2_BUGGY + ".product");
		db.print(G14Fixtures.I2_BUGGY + ".sale");
		assertThat(db.countOf(G14Fixtures.I2_BUGGY + ".sale")).as("cả hai khách đều được ghi nhận đã mua").isEqualTo(2);
		assertThat(jdbc.queryForObject("select sum(quantity) from " + G14Fixtures.I2_BUGGY + ".sale", Integer.class))
			.as("bán ra 12 cái trong khi kho ban đầu chỉ có 10").isEqualTo(12);
		assertThat(db.rowById(G14Fixtures.I2_BUGGY + ".product", G14Fixtures.PRODUCT_ID).get("stock"))
			.as("A ghi stock = 10 - 6 = 4 từ số liệu cũ, đè lên kết quả của B (cũng là 4)").isEqualTo(4);
	}

	/**
	 * G14-I2-S02 — CHẨN ĐOÁN: hai UPDATE giống hệt nhau và không có gì để phát hiện xung đột
	 *
	 * <p>Mapping: I2 buggy. <b>Câu hỏi</b>: bằng chứng nào cho thấy đây là lost update chứ không phải lỗi tính toán?
	 * <p><b>Kết luận</b>: có đúng 2 câu {@code UPDATE product} cùng ghi {@code stock = 4} và điều kiện chỉ là {@code where id=?}
	 * (không có {@code version}); bất biến "kho + đã bán = kho ban đầu" bị vi phạm (4 + 12 = 16, đáng ra 10).
	 * Cách chẩn đoán: đối chiếu số liệu ở hai nơi (kho và sổ bán hàng) thay vì tin một con số.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-I2-S02 | chẩn đoán: hai UPDATE không có where version, và kho + đã bán = 16 ≠ 10")
	void s02_diagnoseUpdatesWithoutVersion() {
		// ── GIVEN ──
		G14Fixtures.seedProductBuggy(jdbc, INITIAL_STOCK);

		// ── WHEN ──
		sql.reset();
		twoBuyersCollideOnBuggy();
		sql.print();

		// ── THEN: SQL ──
		sql.assertUpdates("product", 2); // cả hai khách đều UPDATE: không ai bị từ chối
		sql.assertNoStatementContains("version"); // không có cột version nào trong SQL: DB không có cách phát hiện xung đột

		// ── THEN: DB thật ──
		assertThatThrownBy(() -> assertInventoryInvariant(G14Fixtures.I2_BUGGY))
			.as("bất biến kho bị vi phạm: kho 4 + đã bán 12 = 16, không phải 10")
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("kho còn 4 + đã bán 12");
	}

	/**
	 * G14-I2-S03 — SỬA: {@code @Version} + retry, người đến sau bị chặn đúng bằng "hết hàng"
	 *
	 * <p>Mapping: I2 fixed (có {@code @Version}). <b>Câu hỏi</b>: cùng kịch bản, kết quả đúng là gì và ai bị từ chối?
	 * <p><b>Kết luận</b>: A bị từ chối ở bước ghi (0 dòng khớp {@code where version=0}), service retry cả transaction, lần này đọc
	 * stock = 4 &lt; 6 nên ném {@code InsufficientStockException}. DB: 1 dòng sale (6 cái), kho 4, version 1: đúng bất biến.
	 * Cái giá: caller phải chấp nhận (và test) đường retry (G11-S06a).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-I2-S03 | sửa: @Version + retry → khách đến sau bị báo hết hàng, kho 4, chỉ 1 đơn bán")
	void s03_fixWithVersionAndRetry() {
		// ── GIVEN ──
		G14Fixtures.seedProductFixed(jdbc, INITIAL_STOCK);
		Gate aHasReadStock = new Gate("A đã đọc stock");
		Gate bHasCommitted = new Gate("B đã commit");

		// ── WHEN ──
		Running<Void> buyerA = concurrent.startOutsideTx(() -> fixedService.buy(G14Fixtures.PRODUCT_ID, QUANTITY_EACH, () -> {
			aHasReadStock.open();
			bHasCommitted.await();
		}));
		aHasReadStock.await();
		fixedService.buy(G14Fixtures.PRODUCT_ID, QUANTITY_EACH); // B: 10 → 4, version 0 → 1, commit
		bHasCommitted.open();
		Throwable buyerAFailure = buyerA.joinAndGetFailure();

		// ── THEN: DB thật ──
		System.out.println("[EXCEPTION] buyerAFailure = " + buyerAFailure);
		db.print(G14Fixtures.I2_FIXED + ".product");
		db.print(G14Fixtures.I2_FIXED + ".sale");
		assertThat(buyerAFailure).as("lần thử đầu A bị version từ chối; lần retry đọc stock = 4 nên bị chặn bằng lỗi nghiệp vụ")
			.isInstanceOf(I2FixedInsufficientStockException.class);
		assertThat(db.countOf(G14Fixtures.I2_FIXED + ".sale")).as("chỉ B mua được; đơn của A đã rollback").isEqualTo(1);
		assertThat(db.rowById(G14Fixtures.I2_FIXED + ".product", G14Fixtures.PRODUCT_ID).get("stock")).as("kho chỉ bị trừ một lần").isEqualTo(4);
		assertThat(db.rowById(G14Fixtures.I2_FIXED + ".product", G14Fixtures.PRODUCT_ID).get("version")).as("một UPDATE thành công → version 1").isEqualTo(1L);
		assertInventoryInvariant(G14Fixtures.I2_FIXED);
	}

	/**
	 * G14-I2-S04 — RÀO CHẮN: bất biến kho bắt được bản buggy và để bản fixed qua
	 *
	 * <p>Mapping: I2 buggy và fixed. <b>Câu hỏi</b>: làm sao lỗi này không quay lại mà không cần chờ khách phàn nàn?
	 * <p><b>Kết luận</b>: test đua hai transaction rồi kiểm tra bất biến "kho + đã bán = kho ban đầu". Trên đường buggy bất biến vỡ
	 * (AssertionError), trên đường fixed giữ nguyên. Đưa test này vào suite thì ai bỏ {@code @Version} hoặc bỏ retry đều làm suite đỏ.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-I2-S04 | rào chắn: test đua hai khách + bất biến kho làm bản buggy đỏ và bản fixed xanh")
	void s04_invariantGuardCatchesBuggyAndPassesFixed() {
		// ── GIVEN ──
		G14Fixtures.seedProductBuggy(jdbc, INITIAL_STOCK);
		G14Fixtures.seedProductFixed(jdbc, INITIAL_STOCK);

		// ── WHEN: đua trên đường buggy ──
		twoBuyersCollideOnBuggy();

		// ── THEN: rào chắn phải bắt được ──
		assertThatThrownBy(() -> assertInventoryInvariant(G14Fixtures.I2_BUGGY))
			.as("bản buggy bán 12 từ kho 10 → rào chắn phải đỏ").isInstanceOf(AssertionError.class);

		// ── WHEN: đua trên đường fixed ──
		Gate aHasReadStock = new Gate("A đã đọc stock");
		Gate bHasCommitted = new Gate("B đã commit");
		Running<Void> buyerA = concurrent.startOutsideTx(() -> fixedService.buy(G14Fixtures.PRODUCT_ID, QUANTITY_EACH, () -> {
			aHasReadStock.open();
			bHasCommitted.await();
		}));
		aHasReadStock.await();
		fixedService.buy(G14Fixtures.PRODUCT_ID, QUANTITY_EACH);
		bHasCommitted.open();
		buyerA.joinAndGetFailure(); // lỗi "hết hàng" của A là kết quả mong đợi, không phải lỗi của test

		// ── THEN: rào chắn phải cho qua ──
		assertThatCode(() -> assertInventoryInvariant(G14Fixtures.I2_FIXED)).as("bản fixed giữ đúng bất biến → xanh").doesNotThrowAnyException();
	}
}
