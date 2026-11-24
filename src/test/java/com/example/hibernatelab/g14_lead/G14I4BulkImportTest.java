package com.example.hibernatelab.g14_lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.hibernatelab.g14_lead.i4_buggy.I4BuggyImportReport;
import com.example.hibernatelab.g14_lead.i4_buggy.I4BuggyImportService;
import com.example.hibernatelab.g14_lead.i4_buggy.I4BuggyItem;
import com.example.hibernatelab.g14_lead.i4_fixed.I4FixedImportReport;
import com.example.hibernatelab.g14_lead.i4_fixed.I4FixedImportService;
import com.example.hibernatelab.g14_lead.i4_fixed.I4FixedItem;
import com.example.hibernatelab.support.AbstractScenarioTest;
import com.example.hibernatelab.support.BatchLogCapture;
import com.example.hibernatelab.support.MappingLint;

/**
 * Sự cố G14-I4: "Import file lớn ngày càng chậm". Bốn test theo E7: tái hiện, chẩn đoán, sửa, rào chắn.
 *
 * <p>Cách đo: test KHÔNG đo thời gian (chập chờn theo máy). Thay vào đó đo hai nguyên nhân gây chậm mà đếm được chắc chắn:
 * (1) số lần gửi INSERT xuống DB, (2) số entity persistence context phải giữ (mỗi lần flush Hibernate dirty-check từng entity
 * trong đó, nên cỡ này tăng thì mỗi lần flush càng đắt).
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G14I4BulkImportTest extends AbstractScenarioTest {

	private static final int ROWS = 200;

	@Autowired
	private I4BuggyImportService buggyService;
	@Autowired
	private I4FixedImportService fixedService;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { G14Fixtures.I4_BUGGY, G14Fixtures.I4_FIXED };
	}

	/** Bất biến của import: persistence context không được phình theo số dòng. Vi phạm thì ném AssertionError. */
	private void assertPersistenceContextStaysBounded(int peakManagedEntities) {
		assertThat(peakManagedEntities)
			.as("persistence context giữ tối đa " + peakManagedEntities + " entity; phải ≤ " + I4FixedImportService.CHUNK
				+ " (một lô) thì thời gian và RAM mới tăng tuyến tính theo số dòng")
			.isLessThanOrEqualTo(I4FixedImportService.CHUNK);
	}

	/**
	 * G14-I4-S01 — TÁI HIỆN: 200 dòng = 200 INSERT, persistence context giữ cả 200 entity
	 *
	 * <p>Mapping: I4 buggy (id {@code IDENTITY}, service không flush/clear). <b>Câu hỏi</b>: import 200 dòng tốn bao nhiêu
	 * lần gửi INSERT và persistence context phình tới cỡ nào?
	 * <p><b>Kết luận</b>: 200 câu INSERT riêng lẻ (IDENTITY bắt INSERT ngay khi persist, không gom lô được) và persistence context
	 * giữ đủ 200 entity. Dữ liệu vào DB đúng, nên triệu chứng chỉ là "chậm dần": mỗi dòng thêm một chuyến đi DB và một entity
	 * nữa để dirty-check.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-I4-S01 | tái hiện: import 200 dòng bản buggy → 200 INSERT riêng lẻ, persistence context giữ 200 entity")
	void s01_reproduceSlowImport() {
		// ── GIVEN ── (bảng rỗng)

		// ── WHEN ──
		sql.reset();
		I4BuggyImportReport report = buggyService.importItems(ROWS);
		sql.print();

		// ── THEN: SQL ──
		sql.assertInserts("item", ROWS); // mỗi dòng một INSERT: IDENTITY không cho phép gom lô

		// ── THEN: DB thật ──
		assertThat(db.countOf(G14Fixtures.I4_BUGGY + ".item")).as("dữ liệu vào DB đúng đủ: lỗi chỉ là hiệu năng").isEqualTo(ROWS);
		assertThat(report.peakManagedEntities()).as("không flush/clear nên persistence context giữ cả 200 entity").isEqualTo(ROWS);
	}

	/**
	 * G14-I4-S02 — CHẨN ĐOÁN: persistence context phình tỉ lệ thuận với số dòng
	 *
	 * <p>Mapping: I4 buggy. <b>Câu hỏi</b>: làm sao phân biệt "import chậm vì DB" với "chậm vì persistence context phình"?
	 * <p><b>Kết luận</b>: chạy cùng service với 100 rồi 200 dòng: cỡ persistence context lớn nhất bằng đúng số dòng (100 → 100, 200 → 200)
	 * và số INSERT cũng bằng số dòng. Hai con số tăng theo N là dấu hiệu cần flush/clear theo lô và đổi chiến lược id.
	 * Cách đo cỡ persistence context: {@code session.getStatistics().getEntityCount()}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-I4-S02 | chẩn đoán: persistence context lớn nhất và số INSERT đều bằng đúng số dòng (100 → 100, 200 → 200)")
	void s02_diagnosePersistenceContextGrowsWithRows() {
		// ── WHEN: 100 dòng ──
		sql.reset();
		I4BuggyImportReport smallImport = buggyService.importItems(100);
		sql.assertInserts("item", 100);

		// ── WHEN: 200 dòng ──
		sql.reset();
		I4BuggyImportReport bigImport = buggyService.importItems(ROWS);
		sql.print();
		sql.assertInserts("item", ROWS);

		// ── THEN ──
		System.out.println("[OBS-I4] persistence context lớn nhất: 100 dòng → " + smallImport.peakManagedEntities()
			+ ", 200 dòng → " + bigImport.peakManagedEntities());
		assertThat(smallImport.peakManagedEntities()).as("100 dòng → giữ 100 entity").isEqualTo(100);
		assertThat(bigImport.peakManagedEntities()).as("200 dòng → giữ 200 entity: tăng tuyến tính theo N, không có trần").isEqualTo(ROWS);
	}

	/**
	 * G14-I4-S03 — SỬA: SEQUENCE + batch size 50 + flush/clear mỗi 50 dòng
	 *
	 * <p>Mapping: I4 fixed (id {@code SEQUENCE}). <b>Câu hỏi</b>: sau khi sửa, 200 dòng được gửi xuống DB thế nào và persistence context cỡ nào?
	 * <p><b>Kết luận</b>: đúng 4 lô JDBC, mỗi lô 50 dòng (đo bằng {@link BatchLogCapture}, vì {@code StatementInspector} chỉ thấy
	 * một câu INSERT), và persistence context không bao giờ giữ quá 50 entity. DB vẫn đủ 200 dòng.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-I4-S03 | sửa: 200 dòng bản fixed → 4 lô × 50, persistence context không quá 50 entity")
	void s03_fixWithSequenceBatchAndChunkedFlush() {
		// ── GIVEN ── (bảng rỗng)

		// ── WHEN ──
		sql.reset();
		I4FixedImportReport report;
		BatchLogCapture batches = BatchLogCapture.start();
		try {
			report = fixedService.importItems(ROWS);
		} finally {
			batches.close();
		}
		sql.print();
		System.out.println("[OBS-I4] kích thước các lô: " + batches.sizes());

		// ── THEN: SQL ──
		assertThat(batches.sizes()).as("200 dòng / lô 50 = đúng 4 lô, mỗi lô 50 dòng").containsExactly(50, 50, 50, 50);

		// ── THEN: DB thật ──
		assertThat(db.countOf(G14Fixtures.I4_FIXED + ".item")).as("gom lô nhưng DB vẫn phải có đủ 200 dòng").isEqualTo(ROWS);
		assertThat(report.peakManagedEntities()).as("flush + clear mỗi 50 dòng nên persistence context không quá 50 entity")
			.isLessThanOrEqualTo(I4FixedImportService.CHUNK);
	}

	/**
	 * G14-I4-S04 — RÀO CHẮN: giới hạn cỡ persistence context và lint IDENTITY, cả hai bắt được bản buggy
	 *
	 * <p>Mapping: I4 buggy và fixed. <b>Câu hỏi</b>: làm sao lỗi này không quay lại?
	 * <p><b>Kết luận</b>: hai rào chắn bổ sung nhau. (1) Giới hạn cỡ persistence context lúc chạy: bắt cả lỗi quên flush/clear.
	 * (2) Lint mapping {@code IDENTITY-ID} (G14-R2): bắt ngay từ lúc review entity, không cần chạy import. Mỗi rào chắn phải
	 * đỏ trên bản buggy và xanh trên bản fixed.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G14-I4-S04 | rào chắn: giới hạn persistence context và lint IDENTITY đều đỏ trên bản buggy, xanh trên bản fixed")
	void s04_guardsCatchBuggyAndPassFixed() {
		// ── WHEN ──
		I4BuggyImportReport buggyReport = buggyService.importItems(ROWS);
		I4FixedImportReport fixedReport = fixedService.importItems(ROWS);

		// ── THEN: rào chắn lúc chạy ──
		assertThatThrownBy(() -> assertPersistenceContextStaysBounded(buggyReport.peakManagedEntities()))
			.as("bản buggy giữ 200 entity → rào chắn phải đỏ").isInstanceOf(AssertionError.class);
		assertThatCode(() -> assertPersistenceContextStaysBounded(fixedReport.peakManagedEntities()))
			.as("bản fixed giữ ≤ 50 entity → rào chắn xanh").doesNotThrowAnyException();

		// ── THEN: lint mapping ──
		assertThat(MappingLint.check(I4BuggyItem.class)).as("entity buggy dùng IDENTITY → lint phải báo")
			.anyMatch(violation -> violation.startsWith("IDENTITY-ID"));
		assertThat(MappingLint.check(I4FixedItem.class)).as("entity fixed dùng SEQUENCE → lint sạch").isEmpty();
	}
}
