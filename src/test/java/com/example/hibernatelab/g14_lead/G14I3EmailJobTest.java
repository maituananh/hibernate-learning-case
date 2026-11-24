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

import com.example.hibernatelab.g14_lead.i3_buggy.I3BuggyEmailWorker;
import com.example.hibernatelab.g14_lead.i3_fixed.I3FixedEmailWorker;
import com.example.hibernatelab.support.AbstractScenarioTest;
import com.example.hibernatelab.support.ConcurrentTx;
import com.example.hibernatelab.support.ConcurrentTx.Gate;
import com.example.hibernatelab.support.ConcurrentTx.Running;

/**
 * Sự cố G14-I3: "Job gửi email chạy hai lần". Bốn test theo E7: tái hiện, chẩn đoán, sửa, rào chắn.
 *
 * <p>Kịch bản: hàng đợi có 2 việc NEW (id 1 và 2); hai worker chạy cùng lúc. Kết quả đúng: mỗi worker nhận MỘT việc khác nhau,
 * mỗi email gửi đúng một lần. Thứ tự xen kẽ được ép bằng cổng đồng bộ: worker A nhận việc xong thì DỪNG,
 * worker B chạy trọn vẹn, rồi A mới chạy tiếp. "Gửi email" được thay bằng việc ghi một dòng vào {@code email_log}.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G14I3EmailJobTest extends AbstractScenarioTest {

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ConcurrentTx concurrent;
	@Autowired
	private I3BuggyEmailWorker buggyWorker;
	@Autowired
	private I3FixedEmailWorker fixedWorker;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { G14Fixtures.I3_BUGGY, G14Fixtures.I3_FIXED };
	}

	/** Bất biến nghiệp vụ: không việc nào được gửi email hai lần. Vi phạm thì ném AssertionError. */
	private void assertNoJobEmailedTwice(String schema) {
		Integer jobsEmailedTwice = jdbc.queryForObject("select count(*) from (select job_id from " + schema
			+ ".email_log group by job_id having count(*) > 1) duplicated", Integer.class);
		assertThat(jobsEmailedTwice).as("số việc bị gửi email từ hai lần trở lên phải là 0").isZero();
	}

	/** Worker A nhận việc rồi dừng; worker B chạy trọn vẹn; rồi A chạy tiếp. Bản buggy. */
	private void twoWorkersCollideOnBuggy() {
		Gate aHasJob = new Gate("A đã nhận việc");
		Gate bIsDone = new Gate("B đã xong");
		Running<Void> workerA = concurrent.startOutsideTx(() -> buggyWorker.processNext("worker-A", () -> {
			aHasJob.open();
			bIsDone.await();
		}));
		aHasJob.await();
		buggyWorker.processNext("worker-B");
		bIsDone.open();
		workerA.join();
	}

	/** Như trên nhưng cho bản fixed. */
	private void twoWorkersRaceOnFixed() {
		Gate aHasJob = new Gate("A đã nhận việc");
		Gate bIsDone = new Gate("B đã xong");
		Running<Void> workerA = concurrent.startOutsideTx(() -> fixedWorker.processNext("worker-A", () -> {
			aHasJob.open();
			bIsDone.await();
		}));
		aHasJob.await();
		fixedWorker.processNext("worker-B"); // việc 1 đang bị A khoá → SKIP LOCKED bỏ qua, B nhận việc 2
		bIsDone.open();
		workerA.join();
	}

	/**
	 * G14-I3-S01 — TÁI HIỆN: hai worker cùng nhận việc 1, email gửi hai lần
	 *
	 * <p>Mapping: I3 buggy (query lấy việc không khoá). <b>Câu hỏi</b>: hai worker chạy cùng lúc trên hàng đợi 2 việc, chuyện gì xảy ra?
	 * <p><b>Kết luận</b>: cả hai cùng đọc "việc NEW đầu tiên" = việc 1 và cùng gửi: {@code email_log} có 2 dòng cho việc 1,
	 * trong khi việc 2 không ai làm. Không exception nào.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-I3-S01 | tái hiện: hai worker cùng nhận việc 1 → email_log có 2 dòng cho việc 1, việc 2 chưa ai làm")
	void s01_reproduceDoubleSend() {
		// ── GIVEN ──
		G14Fixtures.seedTwoJobs(jdbc, G14Fixtures.I3_BUGGY);

		// ── WHEN ──
		twoWorkersCollideOnBuggy();

		// ── THEN: DB thật ──
		db.print(G14Fixtures.I3_BUGGY + ".email_log");
		assertThat(jdbc.queryForObject("select count(*) from " + G14Fixtures.I3_BUGGY + ".email_log where job_id = 1", Integer.class))
			.as("email của việc 1 bị gửi HAI lần (mỗi worker một lần)").isEqualTo(2);
		assertThat(jdbc.queryForObject("select status from " + G14Fixtures.I3_BUGGY + ".job where id = 2", String.class))
			.as("việc 2 không ai nhận: hai worker đã dồn vào việc 1").isEqualTo("NEW");
	}

	/**
	 * G14-I3-S02 — CHẨN ĐOÁN: câu SELECT lấy việc không có {@code for update}, và hai dòng log trùng job_id
	 *
	 * <p>Mapping: I3 buggy. <b>Câu hỏi</b>: bằng chứng nào chỉ ra thủ phạm là khâu "nhận việc" chứ không phải khâu "gửi"?
	 * <p><b>Kết luận</b>: SQL của cả hai worker chỉ có {@code select … where status='NEW'} thường, không {@code for update}/{@code skip locked},
	 * nên Postgres không có lý do gì để bắt worker này chờ hay tránh dòng của worker kia. Dữ liệu: nhóm theo {@code job_id} thấy
	 * một việc có 2 dòng log. Hai worker khác tên nhưng cùng {@code job_id} là dấu vân tay của "hai worker lấy cùng việc".
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-I3-S02 | chẩn đoán: SQL nhận việc không có for update; email_log cho thấy một việc có hai worker")
	void s02_diagnoseClaimQueryHasNoLock() {
		// ── GIVEN ──
		G14Fixtures.seedTwoJobs(jdbc, G14Fixtures.I3_BUGGY);

		// ── WHEN ──
		sql.reset();
		twoWorkersCollideOnBuggy();
		sql.print();

		// ── THEN: SQL ──
		sql.assertNoStatementContains("for update"); // không khoá
		sql.assertNoStatementContains("skip locked");

		// ── THEN: DB thật ──
		assertThat(jdbc.queryForList("select worker from " + G14Fixtures.I3_BUGGY + ".email_log where job_id = 1 order by worker", String.class))
			.as("cùng một việc nhưng có đủ hai worker ghi log: bằng chứng gửi trùng").containsExactly("worker-A", "worker-B");
		assertThatThrownBy(() -> assertNoJobEmailedTwice(G14Fixtures.I3_BUGGY))
			.as("bất biến 'mỗi việc gửi một lần' bị vi phạm").isInstanceOf(AssertionError.class);
	}

	/**
	 * G14-I3-S03 — SỬA: {@code PESSIMISTIC_WRITE + SKIP LOCKED}, mỗi worker nhận một việc khác nhau
	 *
	 * <p>Mapping: I3 fixed (query lấy việc khoá dòng và bỏ qua dòng đang bị khoá, G11-S09a). <b>Câu hỏi</b>: cùng kịch bản, ai nhận việc nào?
	 * <p><b>Kết luận</b>: A khoá việc 1 và giữ khoá đến lúc commit; B chạy {@code SELECT … FOR UPDATE SKIP LOCKED}, bỏ qua việc 1 và
	 * nhận việc 2. Kết quả: mỗi việc đúng một dòng log, cả hai việc DONE. Cái giá: giữ khoá trong lúc "gửi mail" thì worker khác
	 * không lấy được dòng đó (chấp nhận được vì đúng là muốn vậy), nhưng transaction nên ngắn.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-I3-S03 | sửa: SKIP LOCKED → worker B bỏ qua việc 1 đang bị khoá, nhận việc 2, không gửi trùng")
	void s03_fixWithSkipLocked() {
		// ── GIVEN ──
		G14Fixtures.seedTwoJobs(jdbc, G14Fixtures.I3_FIXED);

		// ── WHEN ──
		sql.reset();
		twoWorkersRaceOnFixed();
		sql.print();

		// ── THEN: SQL ──
		sql.assertSomeStatementContains("skip locked");

		// ── THEN: DB thật ──
		db.print(G14Fixtures.I3_FIXED + ".email_log");
		assertThat(jdbc.queryForList("select worker from " + G14Fixtures.I3_FIXED + ".email_log where job_id = 1", String.class))
			.as("việc 1 do A xử lý, đúng một lần").containsExactly("worker-A");
		assertThat(jdbc.queryForList("select worker from " + G14Fixtures.I3_FIXED + ".email_log where job_id = 2", String.class))
			.as("việc 2 do B xử lý, đúng một lần").containsExactly("worker-B");
		assertThat(jdbc.queryForObject("select count(*) from " + G14Fixtures.I3_FIXED + ".job where status = 'DONE'", Integer.class))
			.as("cả hai việc đều xong").isEqualTo(2);
	}

	/**
	 * G14-I3-S04 — RÀO CHẮN: bất biến "mỗi việc gửi một lần" bắt được bản buggy và để bản fixed qua
	 *
	 * <p>Mapping: I3 buggy và fixed. <b>Câu hỏi</b>: làm sao lỗi này không quay lại khi ai đó "tối ưu" query lấy việc?
	 * <p><b>Kết luận</b>: test đua hai worker rồi kiểm tra bất biến. Trên đường buggy bất biến vỡ (AssertionError), trên đường fixed
	 * giữ nguyên. Ai bỏ khoá khỏi query lấy việc sẽ làm test này đỏ.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-I3-S04 | rào chắn: test đua hai worker + bất biến 'gửi một lần' làm bản buggy đỏ và bản fixed xanh")
	void s04_invariantGuardCatchesBuggyAndPassesFixed() {
		// ── GIVEN ──
		G14Fixtures.seedTwoJobs(jdbc, G14Fixtures.I3_BUGGY);
		G14Fixtures.seedTwoJobs(jdbc, G14Fixtures.I3_FIXED);

		// ── WHEN ──
		twoWorkersCollideOnBuggy();
		twoWorkersRaceOnFixed();

		// ── THEN ──
		assertThatThrownBy(() -> assertNoJobEmailedTwice(G14Fixtures.I3_BUGGY))
			.as("bản buggy gửi trùng → rào chắn phải đỏ").isInstanceOf(AssertionError.class);
		assertThatCode(() -> assertNoJobEmailedTwice(G14Fixtures.I3_FIXED))
			.as("bản fixed không gửi trùng → rào chắn xanh").doesNotThrowAnyException();
	}
}
