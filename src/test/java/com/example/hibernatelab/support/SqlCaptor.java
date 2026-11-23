package com.example.hibernatelab.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Ghi lại MỌI câu SQL mà Hibernate chuẩn bị gửi xuống DB.
 *
 * <p>Cách hoạt động: Hibernate gọi {@link #inspect(String)} ngay trước khi tạo PreparedStatement,
 * ta chỉ lưu lại câu SQL rồi trả nguyên văn. Đối tượng này được đăng ký vào Hibernate trong
 * {@link ScenarioTestConfiguration} qua thuộc tính {@code hibernate.session_factory.statement_inspector}.
 *
 * <p>Lưu ý quan trọng: chỉ thấy SQL do HIBERNATE gửi. Câu SQL chạy qua {@code JdbcTemplate}
 * (đọc DB để assert, TRUNCATE dọn dữ liệu) KHÔNG đi qua đây, nên không làm lệch số đếm.
 *
 * <p>Thread-safe (dùng {@link CopyOnWriteArrayList}). Test không chạy song song nên đơn giản là đủ.
 * Lớp {@link SqlAssert} bọc lớp này để cung cấp các hàm {@code assertInserts...} cho test.
 */
public class SqlCaptor implements StatementInspector {

	/** Mỗi phần tử là một câu SQL, hoặc một dòng đánh dấu (xem {@link #mark(String)}). */
	private final List<Entry> entries = new CopyOnWriteArrayList<>();

	private record Entry(String text, boolean isMarker) {
	}

	@Override
	public String inspect(String sql) {
		entries.add(new Entry(sql, false));
		return sql;
	}

	/** Xoá hết SQL đã ghi. Gọi ngay trước bước WHEN để chỉ đo đúng phần cần học. */
	public void reset() {
		entries.clear();
	}

	/**
	 * In ra console một dòng đánh dấu như {@code ── WHEN ──}, đồng thời ghi vào danh sách
	 * để {@link #printTo(java.io.PrintStream)} in lại đúng vị trí. Dòng log SQL của Hibernate
	 * (org.hibernate.SQL) sẽ hiện ngay bên dưới dòng đánh dấu này trên terminal.
	 */
	public void mark(String label) {
		System.out.println("── " + label + " ──");
		entries.add(new Entry(label, true));
	}

	/** Các câu SQL đã chạy từ lần {@link #reset()} cuối, KHÔNG gồm {@code nextval} của sequence. */
	public List<String> statements() {
		List<String> result = new ArrayList<>();
		for (Entry entry : entries) {
			if (!entry.isMarker() && !isSequenceCall(entry.text())) {
				result.add(entry.text());
			}
		}
		return result;
	}

	/** Mọi dòng đã ghi (kể cả nextval và dấu mốc), dùng để in ra cho người học xem. */
	List<String> printableLines() {
		List<String> lines = new ArrayList<>();
		for (Entry entry : entries) {
			if (entry.isMarker()) {
				lines.add("── " + entry.text() + " ──");
			} else if (isSequenceCall(entry.text())) {
				lines.add(entry.text().strip() + "   (nextval: lấy id từ sequence, KHÔNG tính khi assert)");
			} else {
				lines.add(entry.text().strip());
			}
		}
		return lines;
	}

	/**
	 * {@code nextval} của sequence bị bỏ qua khi đếm, vì optimizer "pooled" (allocationSize = 50)
	 * chỉ gọi DB mỗi 50 id nên chúng xuất hiện không đều giữa các lần chạy.
	 */
	static boolean isSequenceCall(String sql) {
		String lower = sql.toLowerCase(Locale.ROOT);
		return lower.contains("nextval") || lower.contains("next value for");
	}
}
