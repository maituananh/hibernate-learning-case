package com.example.hibernatelab.g13_performance;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.example.hibernatelab.g13_performance.b1_seq_batch.B1Item;
import com.example.hibernatelab.g13_performance.b2_identity.B2Item;
import com.example.hibernatelab.g13_performance.b3_seq_alloc1.B3Item;
import com.example.hibernatelab.support.AbstractScenarioTest;
import com.example.hibernatelab.support.BatchLogCapture;

/**
 * Nhóm G13a, chủ đề batch insert và chiến lược sinh id: S01 (SEQUENCE + batch), S02 (IDENTITY), S03 (allocationSize).
 *
 * <p>Cách đo (E4): KHÔNG bật {@code hibernate.jdbc.batch_size} toàn cục (sẽ làm lệch test cũ). Mỗi test tự đặt
 * {@code session.setJdbcBatchSize(n)} cho riêng session của mình, rồi đếm bằng thứ đếm được:
 * số câu INSERT mà {@code StatementInspector} thấy (mỗi batch JDBC chỉ chuẩn bị một PreparedStatement) và Hibernate {@code Statistics}.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G13BatchTest extends AbstractScenarioTest {

	private static final String B1 = "g13_b1_seq_batch";
	private static final String B2 = "g13_b2_identity";
	private static final String B3 = "g13_b3_seq_alloc1";
	private static final int ROWS = 100;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { B1, B2, B3 };
	}

	/** Bật và xoá Statistics của SessionFactory (E4: bật tại chỗ trong test, không bật toàn cục). */
	private Statistics statistics() {
		Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
		stats.setStatisticsEnabled(true);
		stats.clear();
		return stats;
	}

	/**
	 * G13-S01a — B1 🔬: SEQUENCE + {@code setJdbcBatchSize(20)}, persist 100 entity
	 *
	 * <p>Mapping: B1 (baseline). <b>Câu hỏi</b>: 100 entity thì Hibernate gửi bao nhiêu câu INSERT xuống DB?
	 * <p><b>Kết luận</b>: đúng 5 lô JDBC, mỗi lô 20 dòng. Bẫy khi đo: {@code StatementInspector} chỉ thấy 1 câu INSERT (5 lô dùng lại
	 * cùng một PreparedStatement) và {@code Statistics} không có bộ đếm lô, nên số lô đọc từ log của Hibernate ({@link BatchLogCapture}).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S01a | 🔬 SEQUENCE + batch size 20: 100 entity gom thành ít câu INSERT")
	void s01a_sequenceWithBatchSizeGroupsInserts() {
		// ── GIVEN ── (bảng rỗng, batch size đặt riêng cho session trong WHEN)
		Statistics stats = statistics();
		sql.reset();

		// ── WHEN ──
		BatchLogCapture batches = BatchLogCapture.start();
		try {
			inTx(() -> {
				Session session = em.unwrap(Session.class);
				session.setJdbcBatchSize(20); // chỉ session này, các test khác không bị ảnh hưởng
				for (int i = 1; i <= ROWS; i++) {
					em.persist(new B1Item("Item-" + i, i)); // [LIFECYCLE] TRANSIENT → MANAGED, chưa INSERT (SEQUENCE: INSERT trì hoãn tới flush)
				}
				em.flush(); // [FLUSH] đẩy 100 INSERT xuống JDBC, chia lô 20
			}); // [TX END] commit
		} finally {
			batches.close();
		}
		System.out.println("[OBS-S01a] kích thước các lô: " + batches.sizes());
		sql.print();

		// ── THEN: SQL ──
		assertThat(batches.sizes()).as("100 entity / batch size 20 = đúng 5 lô, mỗi lô 20 dòng").containsExactly(20, 20, 20, 20, 20);
		sql.assertInserts("item", 1); // StatementInspector chỉ thấy câu INSERT MỘT lần: 5 lô dùng lại cùng một PreparedStatement
		assertThat(stats.getEntityInsertCount()).as("Hibernate vẫn đếm đủ 100 entity đã insert").isEqualTo(ROWS);
		// ── THEN: DB thật ──
		assertThat(db.countOf(B1 + ".item")).as("dù gom lô, DB vẫn phải có đủ 100 dòng").isEqualTo(ROWS);
	}

	/**
	 * G13-S01b — B1: đối chứng, KHÔNG đặt batch size
	 *
	 * <p>Mapping: B1. <b>Câu hỏi</b>: nếu không đặt batch size thì sao? (mặc định của Hibernate là không batch)
	 * <p><b>Kết luận</b>: 100 câu INSERT riêng lẻ, dù id là SEQUENCE. SEQUENCE chỉ CHO PHÉP batch, phải bật batch size mới có.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S01b | 🔬 đối chứng: không đặt batch size thì 100 entity = 100 INSERT")
	void s01b_withoutBatchSizeEveryInsertIsSeparate() {
		// ── GIVEN ──
		Statistics stats = statistics();
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			for (int i = 1; i <= ROWS; i++) {
				em.persist(new B1Item("Item-" + i, i));
			}
			em.flush(); // [FLUSH]
		}); // [TX END]
		sql.print();

		// ── THEN: SQL ──
		sql.assertInserts("item", ROWS); // không batch: mỗi entity một câu INSERT
		assertThat(stats.getEntityInsertCount()).as("số entity insert không đổi, chỉ khác cách gửi").isEqualTo(ROWS);
	}

	/**
	 * G13-S02 — B2 🔬: IDENTITY + {@code setJdbcBatchSize(20)}
	 *
	 * <p>Mapping: B2 (id IDENTITY). <b>Câu hỏi</b>: cùng cách đặt batch size như S01a, IDENTITY có gom được không? (thay G9-S10)
	 * <p><b>Kết luận</b>: không. Id do DB sinh lúc INSERT nên Hibernate phải INSERT NGAY khi {@code persist} để biết id,
	 * không có gì để gom: 100 entity = 100 INSERT, và INSERT xảy ra ngay tại {@code persist} (trước cả flush).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S02 | 🔬 IDENTITY tắt batching: dù đặt batch size 20, 100 entity vẫn 100 INSERT")
	void s02_identityDisablesBatching() {
		// ── GIVEN ──
		Statistics stats = statistics();
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			Session session = em.unwrap(Session.class);
			session.setJdbcBatchSize(20); // cố ý đặt giống S01a để chỉ khác đúng một điểm: kiểu id
			em.persist(new B2Item("Item-1", 1)); // [LIFECYCLE] MANAGED
			sql.assertInserts("item", 1); // INSERT đã chạy ngay tại persist, CHƯA flush
			for (int i = 2; i <= ROWS; i++) {
				em.persist(new B2Item("Item-" + i, i));
			}
		}); // [TX END]
		sql.print();

		// ── THEN: SQL ──
		sql.assertInserts("item", ROWS);
		assertThat(stats.getEntityInsertCount()).as("100 entity đã insert").isEqualTo(ROWS);
		// ── THEN: DB thật ──
		assertThat(db.countOf(B2 + ".item")).as("DB có đủ 100 dòng").isEqualTo(ROWS);
	}

	/**
	 * G13-S03a — B1 🔬: {@code allocationSize = 50}, persist 100 entity: số lần gọi {@code nextval}
	 *
	 * <p>Mapping: B1. <b>Câu hỏi</b>: Hibernate hỏi sequence bao nhiêu lần để có 100 id?
	 * <p><b>Kết luận</b>: rất ít lần (khoảng 100 / 50): mỗi lần hỏi được cấp cả một dải 50 id (optimizer "pooled").
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S03a | 🔬 allocationSize = 50: 100 entity chỉ cần vài lần gọi nextval")
	void s03a_allocationSize50NeedsFewNextvalCalls() {
		// ── GIVEN ──
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			for (int i = 1; i <= ROWS; i++) {
				em.persist(new B1Item("Item-" + i, i));
			}
			em.flush(); // [FLUSH]
		});

		// ── THEN: SQL ──
		assertThat(sql.sequenceCalls()).as("allocationSize 50: mỗi lần nextval cấp 50 id nên 100 entity chỉ cần vài lần").isLessThanOrEqualTo(3);
	}

	/**
	 * G13-S03b — B3 🔬: {@code allocationSize = 1}, persist 100 entity: số lần gọi {@code nextval}
	 *
	 * <p>Mapping: B3 (allocationSize = 1). <b>Câu hỏi</b>: cùng 100 entity, nhưng mỗi lần sequence chỉ cấp 1 id.
	 * <p><b>Kết luận</b>: mỗi entity một lần round-trip xuống DB để lấy id: khoảng 100 lần nextval.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S03b | 🔬 allocationSize = 1: mỗi entity một lần gọi nextval")
	void s03b_allocationSize1CallsNextvalPerEntity() {
		// ── GIVEN ──
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			for (int i = 1; i <= ROWS; i++) {
				em.persist(new B3Item("Item-" + i, i));
			}
			em.flush(); // [FLUSH]
		});

		// ── THEN: SQL ──
		assertThat(sql.sequenceCalls()).as("allocationSize 1: mỗi persist một lần hỏi sequence").isGreaterThanOrEqualTo(ROWS);
	}
}
