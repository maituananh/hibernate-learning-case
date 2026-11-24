package com.example.hibernatelab.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Đọc DB THẬT bằng JDBC (không qua Hibernate), nên không bị persistence context (cache cấp 1) đánh lừa.
 * Test đặt tên biến là {@code db}.
 *
 * <p>Tên bảng LUÔN kèm schema, ví dụ {@code "g0_lifecycle.customer"}.
 *
 * <p>Lưu ý: nếu gọi bên trong {@code inTx(...)} thì {@link JdbcTemplate} dùng CHÍNH connection của
 * transaction đó, nên thấy cả dữ liệu đã flush nhưng chưa commit. Muốn nhìn như một transaction khác
 * thì bọc trong {@code inNewConnection(...)} (xem G0-S01).
 */
public class DbReader {

	/** Tên bảng được ghép thẳng vào SQL, nên chỉ cho phép chữ, số, gạch dưới và dấu chấm. */
	private static final Pattern SAFE_TABLE_NAME = Pattern.compile("[A-Za-z0-9_]+\\.[A-Za-z0-9_]+");

	private final JdbcTemplate jdbc;

	public DbReader(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Tất cả các dòng của bảng, sắp theo id (mỗi dòng là Map tên cột → giá trị).
	 * Bảng nối (join table) không có cột {@code id} nên được sắp theo hai cột đầu tiên.
	 */
	public List<Map<String, Object>> rowsOf(String table) {
		String orderBy = hasIdColumn(table) ? "id" : "1, 2";
		return jdbc.queryForList("select * from " + checked(table) + " order by " + orderBy);
	}

	private boolean hasIdColumn(String table) {
		String[] schemaAndTable = checked(table).split("\\.");
		Integer count = jdbc.queryForObject(
			"select count(*) from information_schema.columns where table_schema = ? and table_name = ? and column_name = 'id'",
			Integer.class, schemaAndTable[0], schemaAndTable[1]);
		return count != null && count > 0;
	}

	/** Một dòng theo id. Báo lỗi rõ ràng nếu không có dòng nào, để test đỏ với thông báo dễ hiểu. */
	public Map<String, Object> rowById(String table, Object id) {
		List<Map<String, Object>> rows = jdbc.queryForList("select * from " + checked(table) + " where id = ?", id);
		if (rows.isEmpty()) {
			throw new AssertionError("Bảng " + table + " không có dòng nào với id = " + id
				+ " (muốn kiểm tra 'không tồn tại' thì dùng countOf)");
		}
		return rows.get(0);
	}

	/** Số dòng của bảng. */
	public long countOf(String table) {
		Long count = jdbc.queryForObject("select count(*) from " + checked(table), Long.class);
		return count == null ? 0 : count;
	}

	/**
	 * Bản chụp (copy bất biến) của một dòng tại thời điểm gọi, để so sánh trước/sau.
	 * Khác {@link #rowById}: nếu dòng không tồn tại thì trả về Map rỗng thay vì báo lỗi.
	 */
	public Map<String, Object> rowSnapshot(String table, Object id) {
		List<Map<String, Object>> rows = jdbc.queryForList("select * from " + checked(table) + " where id = ?", id);
		if (rows.isEmpty()) {
			return Collections.emptyMap();
		}
		return Collections.unmodifiableMap(new LinkedHashMap<>(rows.get(0)));
	}

	/** In bảng ASCII các dòng hiện có ra console, để người học nhìn thấy dữ liệu thật. */
	public void print(String table) {
		List<Map<String, Object>> rows = rowsOf(table);
		System.out.println("[DB] " + table + " (" + rows.size() + " dòng)");
		if (rows.isEmpty()) {
			System.out.println("  (bảng trống)");
			return;
		}
		List<String> columns = new ArrayList<>(rows.get(0).keySet());
		int[] widths = new int[columns.size()];
		for (int c = 0; c < columns.size(); c++) {
			widths[c] = columns.get(c).length();
			for (Map<String, Object> row : rows) {
				widths[c] = Math.max(widths[c], String.valueOf(row.get(columns.get(c))).length());
			}
		}
		System.out.println("  " + line(columns, widths));
		System.out.println("  " + separator(widths));
		for (Map<String, Object> row : rows) {
			List<String> cells = new ArrayList<>();
			for (String column : columns) {
				cells.add(String.valueOf(row.get(column)));
			}
			System.out.println("  " + line(cells, widths));
		}
	}

	private String line(List<String> cells, int[] widths) {
		StringBuilder builder = new StringBuilder("| ");
		for (int c = 0; c < cells.size(); c++) {
			builder.append(String.format("%-" + widths[c] + "s", cells.get(c))).append(" | ");
		}
		return builder.toString().stripTrailing();
	}

	private String separator(int[] widths) {
		StringBuilder builder = new StringBuilder("+");
		for (int width : widths) {
			builder.append("-".repeat(width + 2)).append("+");
		}
		return builder.toString();
	}

	private String checked(String table) {
		if (!SAFE_TABLE_NAME.matcher(table).matches()) {
			throw new IllegalArgumentException("Tên bảng phải có dạng \"schema.table\", nhận được: " + table);
		}
		return table;
	}
}
