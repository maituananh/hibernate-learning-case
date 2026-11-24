package com.example.hibernatelab.g11_tx_lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g11_tx_lock.t1_baseline.T1ProductRepository;
import com.example.hibernatelab.g11_tx_lock.t1_baseline.T1PurchaseOrder;
import com.example.hibernatelab.g11_tx_lock.t3_job.T3Job;
import com.example.hibernatelab.g11_tx_lock.t3_job.T3JobRepository;
import com.example.hibernatelab.support.AbstractScenarioTest;
import com.example.hibernatelab.support.ConcurrentTx;
import com.example.hibernatelab.support.ConcurrentTx.Gate;
import com.example.hibernatelab.support.ConcurrentTx.Running;

import jakarta.persistence.LockModeType;

/**
 * Nhóm G11, khoá pessimistic: SQL sinh ra (S07), NOWAIT (S08), SKIP LOCKED cho hàng đợi (S09), deadlock (S10),
 * khoá cùng {@code left join fetch} (S11). Các tình huống chờ nhau dùng {@link Gate}, không {@code Thread.sleep}.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G11PessimisticLockTest extends AbstractScenarioTest {

	private static final String PRODUCT = G11Fixtures.T1 + ".product";
	private static final String JOB = G11Fixtures.T3 + ".job";

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ConcurrentTx concurrent;
	@Autowired
	private T1ProductRepository products;
	@Autowired
	private T3JobRepository jobs;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { G11Fixtures.T1, G11Fixtures.T3 };
	}

	/**
	 * G11-S07a — T1: {@code PESSIMISTIC_WRITE} sinh {@code for no key update of <alias>} trên Postgres 🔬
	 *
	 * <p>Mapping: T1 baseline. <b>Câu hỏi</b>: {@code PESSIMISTIC_WRITE} sinh câu SQL khoá nào?
	 * <p><b>Kết luận</b>: {@code for no key update of tp1_0}, KHÔNG phải {@code for update} thuần như giả thuyết. Dialect PostgreSQL của
	 * Hibernate 7 chọn {@code no key update}: vẫn chặn mọi người SỬA dòng, nhưng không chặn người chèn dòng con có FK trỏ tới
	 * dòng này (nhẹ hơn {@code for update}). {@code of tp1_0} chỉ rõ bảng cần khoá (quan trọng khi có join, G11-S11).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S07a | PESSIMISTIC_WRITE → select … for no key update of <alias>")
	void s07a_pessimisticWriteSql() {
		// ── GIVEN ──
		G11Fixtures.seedProductT1(jdbc, 10);

		// ── WHEN ──
		sql.reset();
		inTx(() -> products.findByIdForUpdate(G11Fixtures.PRODUCT_ID)); // [TX END] commit nhả khoá

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);
		sql.assertSomeStatementContains("for no key update of");
		sql.assertNoStatementContains("skip locked");
		sql.assertNoStatementContains("nowait");
	}

	/**
	 * G11-S07b — T1: {@code PESSIMISTIC_READ} sinh {@code for share of <alias>} 🔬
	 *
	 * <p><b>Câu hỏi</b>: {@code PESSIMISTIC_READ} sinh câu SQL khoá nào?
	 * <p><b>Kết luận</b>: {@code for share of tp1_0}: nhiều transaction cùng giữ khoá share, nhưng chặn người ghi. Đúng giả thuyết.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S07b | PESSIMISTIC_READ → select … for share of <alias>")
	void s07b_pessimisticReadSql() {
		// ── GIVEN ──
		G11Fixtures.seedProductT1(jdbc, 10);

		// ── WHEN ──
		sql.reset();
		inTx(() -> products.findByIdForShare(G11Fixtures.PRODUCT_ID));

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);
		sql.assertSomeStatementContains("for share of");
	}

	/**
	 * G11-S08 — T1: khoá với timeout 0 (NOWAIT) trên dòng đang bị khoá → {@code CannotAcquireLockException} 🔬
	 *
	 * <p><b>Câu hỏi</b>: hint {@code jakarta.persistence.lock.timeout = 0} sinh SQL gì, và exception ra sao sau khi Spring dịch?
	 * <p><b>Kết luận</b>: SQL thêm {@code nowait}; Postgres báo "could not obtain lock on row" ngay lập tức; Hibernate bọc thành
	 * {@code LockTimeoutException}; Spring dịch thành {@code CannotAcquireLockException} (lớp con của
	 * {@code PessimisticLockingFailureException}). Caller nên bắt lớp cha {@code PessimisticLockingFailureException}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S08 | NOWAIT trên dòng đang bị khoá → CannotAcquireLockException ngay, không chờ")
	void s08_noWaitFailsImmediatelyOnLockedRow() {
		// ── GIVEN ──
		G11Fixtures.seedProductT1(jdbc, 10);
		Gate tx1HoldsLock = new Gate("tx1 đã giữ khoá");
		Gate tx1MayCommit = new Gate("cho tx1 commit");
		Running<Void> tx1 = concurrent.start(() -> {
			products.findByIdForUpdate(G11Fixtures.PRODUCT_ID); // giữ khoá cho tới khi cổng mở
			tx1HoldsLock.open();
			tx1MayCommit.await();
		});
		tx1HoldsLock.await();

		// ── WHEN ──
		sql.reset();
		assertThatThrownBy(() -> inTx(() -> products.findByIdForUpdateNoWait(G11Fixtures.PRODUCT_ID)))
			.as("dòng đang bị tx1 khoá, NOWAIT không chờ mà báo lỗi")
			.isInstanceOf(CannotAcquireLockException.class)
			.isInstanceOf(PessimisticLockingFailureException.class)
			.hasMessageContaining("could not obtain lock");
		tx1MayCommit.open();
		tx1.join();

		// ── THEN: SQL ──
		sql.print();
		sql.assertSomeStatementContains("nowait");
	}

	/**
	 * G11-S09a — T3: hai worker dùng {@code SKIP LOCKED} lấy hai việc KHÁC nhau 🔬
	 *
	 * <p>Mapping: T3 (bảng job). <b>Câu hỏi</b>: worker 1 đang giữ khoá việc đầu tiên; worker 2 chạy cùng truy vấn thì nhận gì?
	 * <p><b>Kết luận</b>: worker 2 bỏ qua dòng đang bị khoá và nhận việc kế tiếp. SQL: {@code … for no key update of tj1_0 skip locked},
	 * kèm {@code fetch first ? rows only} (Limit). Đây là mẫu hàng đợi việc chuẩn trên Postgres.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S09a | SKIP LOCKED → hai worker nhận hai việc khác nhau")
	void s09a_skipLockedGivesEachWorkerADifferentJob() {
		// ── GIVEN ──
		G11Fixtures.seedThreeJobsT3(jdbc);
		Gate worker1Claimed = new Gate("worker 1 đã nhận việc");
		Gate worker1MayCommit = new Gate("cho worker 1 commit");
		Running<Long> worker1 = concurrent.start(() -> {
			T3Job job = jobs.findNewJobsSkipLocked(Limit.of(1)).get(0); // khoá việc đầu tiên
			job.markDoneBy("worker-1");
			worker1Claimed.open();
			worker1MayCommit.await();
			return job.getId();
		});
		worker1Claimed.await();

		// ── WHEN ──
		sql.reset();
		Long worker2JobId = inTxReturning(() -> {
			T3Job job = jobs.findNewJobsSkipLocked(Limit.of(1)).get(0); // bỏ qua việc đang bị khoá
			job.markDoneBy("worker-2");
			return job.getId();
		});
		worker1MayCommit.open();
		Long worker1JobId = worker1.join();

		// ── THEN: SQL ──
		sql.print();
		sql.assertSomeStatementContains("skip locked");

		// ── THEN: DB thật ──
		db.print(JOB);
		assertThat(worker2JobId).as("worker 2 bỏ qua việc đang bị khoá nên không trùng worker 1").isNotEqualTo(worker1JobId);
		assertThat(jdbc.queryForObject("select count(*) from " + JOB + " where status = 'DONE'", Integer.class))
			.as("hai việc khác nhau cùng DONE").isEqualTo(2);
	}

	/**
	 * G11-S09b — T3: KHÔNG khoá, hai worker nhận CÙNG một việc (nguồn gốc sự cố "job chạy hai lần")
	 *
	 * <p><b>Câu hỏi</b>: nếu chỉ {@code select … where status = 'NEW' order by id limit 1} rồi đánh dấu DONE, không khoá?
	 * <p><b>Kết luận</b>: cả hai cùng đọc thấy việc đầu tiên còn NEW, cùng xử lý nó: việc chạy hai lần, và trong DB chỉ có
	 * MỘT việc DONE (không ai xử lý các việc còn lại). Đối chứng của S09a; tái hiện lại ở G14-I3.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S09b | không khoá → hai worker nhận cùng một việc (xử lý hai lần)")
	void s09b_withoutLockBothWorkersTakeTheSameJob() {
		// ── GIVEN ──
		G11Fixtures.seedThreeJobsT3(jdbc);
		Gate worker1Read = new Gate("worker 1 đã đọc");
		Gate worker2Committed = new Gate("worker 2 đã commit");
		Running<Long> worker1 = concurrent.start(() -> {
			T3Job job = jobs.findNewJobs(Limit.of(1)).get(0); // không khoá: thấy việc đầu tiên
			worker1Read.open();
			worker2Committed.await();
			job.markDoneBy("worker-1");
			return job.getId();
		});
		worker1Read.await();

		// ── WHEN ──
		Long worker2JobId = inTxReturning(() -> {
			T3Job job = jobs.findNewJobs(Limit.of(1)).get(0); // vẫn thấy việc đầu tiên còn NEW
			job.markDoneBy("worker-2");
			return job.getId();
		});
		worker2Committed.open();
		Long worker1JobId = worker1.join();

		// ── THEN: DB thật ──
		db.print(JOB);
		assertThat(worker1JobId).as("không khoá nên hai worker nhận trùng việc").isEqualTo(worker2JobId);
		assertThat(jdbc.queryForObject("select count(*) from " + JOB + " where status = 'DONE'", Integer.class))
			.as("hai worker xử lý nhưng chỉ một việc DONE: hai lần chạy cho cùng một việc").isEqualTo(1);
	}

	/**
	 * G11-S10a — T1: hai transaction khoá hai dòng theo thứ tự ngược nhau → Postgres huỷ MỘT transaction 🔬
	 *
	 * <p><b>Câu hỏi</b>: tx1 khoá dòng 1 rồi xin dòng 2; tx2 khoá dòng 2 rồi xin dòng 1: chuyện gì xảy ra?
	 * <p><b>Kết luận</b>: mỗi bên chờ bên kia mãi nên Postgres phát hiện deadlock (sau {@code deadlock_timeout}, mặc định 1 giây),
	 * huỷ ĐÚNG MỘT transaction ("deadlock detected"); Spring dịch thành {@code CannotAcquireLockException}. Bên còn lại chạy xong.
	 * Không biết trước bên nào bị huỷ, nên test chỉ assert "đúng một".
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S10a | khoá ngược thứ tự → deadlock, Postgres huỷ đúng một transaction")
	void s10a_oppositeLockOrderCausesDeadlock() {
		// ── GIVEN ──
		G11Fixtures.seedTwoProductsT1(jdbc);
		Gate tx1Locked = new Gate("tx1 đã khoá dòng 1");
		Gate tx2Locked = new Gate("tx2 đã khoá dòng 2");

		// ── WHEN ──
		Running<Void> tx1 = concurrent.start(() -> {
			products.findByIdForUpdate(1L);
			tx1Locked.open();
			tx2Locked.await(); // chắc chắn tx2 đã giữ dòng 2
			products.findByIdForUpdate(2L); // chờ tx2 → vòng chờ
		});
		Running<Void> tx2 = concurrent.start(() -> {
			products.findByIdForUpdate(2L);
			tx2Locked.open();
			tx1Locked.await(); // chắc chắn tx1 đã giữ dòng 1
			products.findByIdForUpdate(1L); // chờ tx1 → vòng chờ
		});
		Throwable failure1 = tx1.joinAndGetFailure();
		Throwable failure2 = tx2.joinAndGetFailure();

		// ── THEN ──
		System.out.println("[EXCEPTION] tx1: " + failure1);
		System.out.println("[EXCEPTION] tx2: " + failure2);
		Throwable victim = (failure1 != null) ? failure1 : failure2;
		assertThat((failure1 == null) != (failure2 == null)).as("Postgres huỷ đúng một transaction, bên kia chạy xong").isTrue();
		assertThat(victim).as("nạn nhân nhận exception khoá của Spring").isInstanceOf(CannotAcquireLockException.class)
			.hasMessageContaining("deadlock detected");
	}

	/**
	 * G11-S10b — T1: cùng việc nhưng LUÔN khoá theo thứ tự id tăng dần → không còn deadlock
	 *
	 * <p><b>Câu hỏi</b>: tx1 cần dòng 1 và 2, tx2 cần dòng 2 và 1 (thứ tự nghiệp vụ ngược nhau); nếu code luôn khoá id nhỏ trước?
	 * <p><b>Kết luận</b>: cả hai đều khoá dòng 1 trước, nên tx2 chỉ CHỜ tx1 (không vòng chờ) rồi chạy tiếp; không ai bị huỷ.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S10b | luôn khoá theo thứ tự id tăng dần → không deadlock, tx sau chỉ chờ")
	void s10b_lockingInIdOrderAvoidsDeadlock() {
		// ── GIVEN ──
		G11Fixtures.seedTwoProductsT1(jdbc);
		Gate tx1HoldsFirstLock = new Gate("tx1 đã khoá dòng nhỏ");
		Gate tx1MayContinue = new Gate("cho tx1 khoá tiếp");

		// ── WHEN ──
		Running<Void> tx1 = concurrent.start(() -> {
			products.findByIdForUpdate(1L); // id nhỏ trước
			tx1HoldsFirstLock.open();
			tx1MayContinue.await();
			products.findByIdForUpdate(2L);
		});
		tx1HoldsFirstLock.await();
		Running<Void> tx2 = concurrent.start(() -> lockInAscendingIdOrder(2L, 1L)); // nghiệp vụ cần 2 rồi 1, code sắp xếp lại
		concurrent.awaitSomeoneWaitingForLock(); // tx2 đang chờ dòng 1, chứ không giữ dòng 2
		tx1MayContinue.open();

		// ── THEN ──
		assertThat(tx1.joinAndGetFailure()).as("tx1 không bị ai chặn vòng").isNull();
		assertThat(tx2.joinAndGetFailure()).as("tx2 chỉ chờ rồi chạy tiếp, không deadlock").isNull();
	}

	/** Sắp xếp id tăng dần rồi mới khoá: mọi transaction cùng thứ tự thì không thể có vòng chờ. */
	private void lockInAscendingIdOrder(long firstWanted, long secondWanted) {
		long low = Math.min(firstWanted, secondWanted);
		long high = Math.max(firstWanted, secondWanted);
		products.findByIdForUpdate(low);
		products.findByIdForUpdate(high);
	}

	/**
	 * G11-S11 — T1: {@code PESSIMISTIC_WRITE} + {@code left join fetch}: KHÔNG lỗi, nhưng Hibernate tách thành HAI câu 🔬
	 *
	 * <p>Mapping: T1 baseline (order 1—N item, LAZY). <b>Câu hỏi</b>: Postgres cấm {@code FOR UPDATE} trên phía nullable của
	 * outer join; Hibernate có gây lỗi đó không?
	 * <p><b>Kết luận</b>: không lỗi. Hibernate chạy câu 1 là {@code left join} KHÔNG khoá (nạp order + items), rồi câu 2 riêng
	 * {@code select … from purchase_order where id in (?) for no key update of …} chỉ khoá bảng gốc. Cái giá: giữa hai câu có một
	 * khoảng hở (dữ liệu đọc ở câu 1 chưa được khoá), và các item KHÔNG bị khoá.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S11 | PESSIMISTIC_WRITE + left join fetch → không lỗi, Hibernate tách câu khoá riêng cho bảng gốc")
	void s11_lockWithLeftJoinFetchIsSplitIntoTwoStatements() {
		// ── GIVEN ──
		G11Fixtures.seedOrderWithTwoItemsT1(jdbc);

		// ── WHEN ──
		sql.reset();
		List<T1PurchaseOrder> orders = inTxReturning(() -> em
			.createQuery("select o from G11T1_PurchaseOrder o left join fetch o.items where o.id = :id", T1PurchaseOrder.class)
			.setParameter("id", 1L)
			.setLockMode(LockModeType.PESSIMISTIC_WRITE)
			.getResultList());

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(2); // 1 left join không khoá + 1 câu khoá riêng cho purchase_order
		sql.assertInOrder("left join", "for no key update of");

		// ── THEN ──
		assertThat(orders).as("vẫn trả đúng 1 order").hasSize(1);
		assertThat(Hibernate.isInitialized(orders.get(0).getItems())).as("join fetch vẫn nạp items").isTrue();
	}
}
