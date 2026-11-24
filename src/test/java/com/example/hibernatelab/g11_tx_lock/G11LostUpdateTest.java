package com.example.hibernatelab.g11_tx_lock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g11_tx_lock.t1_baseline.T1Product;
import com.example.hibernatelab.g11_tx_lock.t1_baseline.T1ProductRepository;
import com.example.hibernatelab.g11_tx_lock.t2_versioned.T2Product;
import com.example.hibernatelab.g11_tx_lock.t2_versioned.T2ProductRepository;
import com.example.hibernatelab.support.AbstractScenarioTest;
import com.example.hibernatelab.support.ConcurrentTx;
import com.example.hibernatelab.support.ConcurrentTx.Gate;
import com.example.hibernatelab.support.ConcurrentTx.Running;

/**
 * Nhóm G11, lost update (S05) và ba cách sửa (S06a/b/c).
 *
 * <p>Kịch bản chung: tồn kho = 10, hai người mua mỗi người trừ 1, kết quả đúng phải là 8.
 * Thứ tự các bước được ép bằng {@link Gate} (không {@code Thread.sleep}) nên test tất định:
 * <ol>
 *   <li>Transaction 1 (thread nền) đọc stock = 10 rồi DỪNG ở một cổng.</li>
 *   <li>Transaction 2 (thread test) trừ 1 và COMMIT (DB = 9).</li>
 *   <li>Mở cổng: transaction 1 chạy tiếp với số liệu cũ (10).</li>
 * </ol>
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G11LostUpdateTest extends AbstractScenarioTest {

	private static final String PRODUCT_T1 = G11Fixtures.T1 + ".product";
	private static final String PRODUCT_T2 = G11Fixtures.T2 + ".product";

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ConcurrentTx concurrent;
	@Autowired
	private T1ProductRepository productsT1;
	@Autowired
	private T2ProductRepository productsT2;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { G11Fixtures.T1, G11Fixtures.T2 };
	}

	/**
	 * G11-S05 — T1: không có {@code @Version}, hai lần trừ 1 chỉ trừ được 1 (lost update)
	 *
	 * <p>Mapping: T1 baseline (không version, không khoá). <b>Câu hỏi</b>: hai transaction cùng đọc stock = 10, cùng trừ 1: kết quả?
	 * <p><b>Kết luận</b>: 9 thay vì 8. Transaction 1 tính {@code 10 - 1 = 9} từ số liệu CŨ rồi UPDATE {@code stock = 9},
	 * đè lên kết quả 9 của transaction 2. Không có exception nào: lỗi im lặng.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S05 | không @Version, hai transaction cùng trừ 1 → stock = 9 thay vì 8 (lost update)")
	void s05_lostUpdateWithoutVersion() {
		// ── GIVEN ──
		G11Fixtures.seedProductT1(jdbc, 10);
		Gate tx1HasRead = new Gate("tx1 đã đọc stock");
		Gate tx2Committed = new Gate("tx2 đã commit");

		// ── WHEN ──
		Running<Void> tx1 = concurrent.start(() -> {
			T1Product product = em.find(T1Product.class, G11Fixtures.PRODUCT_ID); // [LIFECYCLE] MANAGED, stock = 10
			tx1HasRead.open();
			tx2Committed.await(); // DB đã là 9, nhưng tx1 vẫn giữ snapshot 10
			product.decreaseStock(1); // 10 - 1 = 9 → [FLUSH] lúc commit: UPDATE stock = 9
		});
		tx1HasRead.await();
		inTx(() -> em.find(T1Product.class, G11Fixtures.PRODUCT_ID).decreaseStock(1)); // 10 → 9, commit
		tx2Committed.open();
		tx1.join();

		// ── THEN: DB thật ──
		db.print(PRODUCT_T1);
		assertThat(db.rowById(PRODUCT_T1, G11Fixtures.PRODUCT_ID).get("stock"))
			.as("hai lần trừ nhưng chỉ còn 1 lần tác dụng: tx1 đè lên tx2 bằng giá trị tính từ số liệu cũ").isEqualTo(9);
	}

	/**
	 * G11-S06a — T2: sửa bằng {@code @Version} (optimistic): transaction đến sau bị từ chối, phải retry
	 *
	 * <p>Mapping: T2 (thêm {@code @Version}). <b>Câu hỏi</b>: cùng kịch bản S05 nhưng entity có {@code @Version}?
	 * <p><b>Kết luận</b>: tx1 UPDATE kèm {@code where version = 0} nhưng dòng đã là version 1 nên 0 dòng khớp →
	 * Spring ném {@code OptimisticLockingFailureException}, tx1 rollback. Chạy lại (retry) trong transaction mới thì đúng: 8.
	 * Cái giá: caller phải tự retry.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S06a | @Version → tx đến sau bị OptimisticLockingFailureException, retry thì được 8")
	void s06a_fixWithVersionNeedsRetry() {
		// ── GIVEN ──
		G11Fixtures.seedProductT2(jdbc, 10);
		Gate tx1HasRead = new Gate("tx1 đã đọc stock");
		Gate tx2Committed = new Gate("tx2 đã commit");

		// ── WHEN ──
		Running<Void> tx1 = concurrent.start(() -> {
			T2Product product = em.find(T2Product.class, G11Fixtures.PRODUCT_ID); // stock 10, version 0
			tx1HasRead.open();
			tx2Committed.await();
			product.decreaseStock(1); // [FLUSH] lúc commit: update … where id=? and version=0 → 0 dòng khớp
		});
		tx1HasRead.await();
		inTx(() -> em.find(T2Product.class, G11Fixtures.PRODUCT_ID).decreaseStock(1)); // 10 → 9, version 0 → 1
		tx2Committed.open();
		Throwable tx1Failure = tx1.joinAndGetFailure();
		inTx(() -> em.find(T2Product.class, G11Fixtures.PRODUCT_ID).decreaseStock(1)); // retry: đọc lại 9, trừ → 8

		// ── THEN ──
		System.out.println("[EXCEPTION] tx1Failure = " + tx1Failure);
		db.print(PRODUCT_T2);
		assertThat(tx1Failure).as("@Version phát hiện dòng đã bị đổi → báo lỗi thay vì ghi đè im lặng")
			.isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(db.rowById(PRODUCT_T2, G11Fixtures.PRODUCT_ID).get("stock")).as("sau retry cả hai lần trừ đều có tác dụng").isEqualTo(8);
		assertThat(db.rowById(PRODUCT_T2, G11Fixtures.PRODUCT_ID).get("version")).as("hai lần UPDATE thành công → version 2").isEqualTo(2);
	}

	/**
	 * G11-S06b — T1: sửa bằng {@code PESSIMISTIC_WRITE}: transaction đến sau CHỜ khoá, không phải retry
	 *
	 * <p>Mapping: T1 baseline + khoá {@code SELECT … FOR UPDATE}. <b>Câu hỏi</b>: cùng kịch bản nhưng đọc bằng khoá ghi?
	 * <p><b>Kết luận</b>: tx2 bị Postgres chặn tại câu SELECT cho đến khi tx1 commit, rồi đọc số liệu MỚI (9) và trừ → 8.
	 * Cái giá: giữ khoá thì người khác phải xếp hàng (và nguy cơ deadlock, G11-S10).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S06b | PESSIMISTIC_WRITE → tx đến sau chờ khoá rồi đọc số mới, kết quả 8 không cần retry")
	void s06b_fixWithPessimisticWriteWaitsForLock() {
		// ── GIVEN ──
		G11Fixtures.seedProductT1(jdbc, 10);
		Gate tx1HoldsLock = new Gate("tx1 đã giữ khoá");
		Gate tx1MayCommit = new Gate("cho tx1 commit");

		// ── WHEN ──
		Running<Void> tx1 = concurrent.start(() -> {
			T1Product product = productsT1.findByIdForUpdate(G11Fixtures.PRODUCT_ID).orElseThrow(); // select … for update
			tx1HoldsLock.open();
			tx1MayCommit.await();
			product.decreaseStock(1); // 10 → 9
		});
		tx1HoldsLock.await();
		Running<Void> tx2 = concurrent.start(() -> {
			T1Product product = productsT1.findByIdForUpdate(G11Fixtures.PRODUCT_ID).orElseThrow(); // BỊ CHẶN ở đây
			product.decreaseStock(1);
		});
		concurrent.awaitSomeoneWaitingForLock(); // bằng chứng: Postgres báo có session đang chờ khoá
		tx1MayCommit.open();
		tx1.join();
		tx2.join();

		// ── THEN: DB thật ──
		db.print(PRODUCT_T1);
		assertThat(db.rowById(PRODUCT_T1, G11Fixtures.PRODUCT_ID).get("stock"))
			.as("tx2 phải chờ tx1 xong rồi mới đọc, nên thấy 9 và trừ ra 8").isEqualTo(8);
	}

	/**
	 * G11-S06c — T1: sửa bằng {@code UPDATE … SET stock = stock - 1}: Postgres trừ nguyên tử, bỏ qua persistence context
	 *
	 * <p>Mapping: T1 baseline + {@code @Modifying} query. <b>Câu hỏi</b>: không đọc stock về Java mà để DB tự trừ?
	 * <p><b>Kết luận</b>: kết quả 8, không cần version, không cần chờ lâu (khoá dòng chỉ trong câu UPDATE).
	 * Cái giá: entity MANAGED đã tải trước đó giữ số cũ (10): phải {@code refresh}/{@code clear} nếu còn dùng (nối với G9-S05, G10-S06).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S06c | UPDATE … stock = stock - 1 → 8, nhưng entity đã tải giữ số cũ (bỏ qua persistence context)")
	void s06c_fixWithAtomicUpdateBypassesPersistenceContext() {
		// ── GIVEN ──
		G11Fixtures.seedProductT1(jdbc, 10);
		Gate tx1HasRead = new Gate("tx1 đã đọc stock");
		Gate tx2Committed = new Gate("tx2 đã commit");

		// ── WHEN ──
		Running<Integer> tx1 = concurrent.start(() -> {
			T1Product product = em.find(T1Product.class, G11Fixtures.PRODUCT_ID); // stock 10 nằm trong PC
			tx1HasRead.open();
			tx2Committed.await();
			productsT1.decreaseStockInDatabase(G11Fixtures.PRODUCT_ID, 1); // update … set stock = stock - 1 (DB đang 9 → 8)
			return product.getStock(); // entity KHÔNG biết DB đã đổi
		});
		tx1HasRead.await();
		inTx(() -> productsT1.decreaseStockInDatabase(G11Fixtures.PRODUCT_ID, 1)); // 10 → 9, commit
		tx2Committed.open();
		int stockSeenByEntityInTx1 = tx1.join();

		// ── THEN: DB thật ──
		db.print(PRODUCT_T1);
		assertThat(db.rowById(PRODUCT_T1, G11Fixtures.PRODUCT_ID).get("stock")).as("DB trừ nguyên tử hai lần → 8").isEqualTo(8);
		assertThat(stockSeenByEntityInTx1).as("cái giá: entity đã tải vẫn giữ 10 vì UPDATE đi vòng qua persistence context").isEqualTo(10);
	}
}
