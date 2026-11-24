package com.example.hibernatelab.support;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Dọn dữ liệu TRƯỚC mỗi test (D14): {@code TRUNCATE ... RESTART IDENTITY CASCADE} mọi bảng của các schema chỉ định.
 *
 * <p>Vì sao dọn TRƯỚC mà không dọn SAU: test chạy xong, dữ liệu của nó vẫn còn trong DB để người học
 * mở ra xem (nhất là với profile {@code -Pinspect}).
 *
 * <p>{@code CASCADE} để khỏi phải lo thứ tự bảng có FK. Chỉ chạm tới schema được liệt kê, không đụng schema khác.
 * Sequence (id kiểu SEQUENCE) không bị reset, nên test không bao giờ được assert giá trị id cụ thể.
 */
public class DbCleaner {

	private final JdbcTemplate jdbc;

	public DbCleaner(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void truncateAllTablesIn(String... schemas) {
		List<String> tables = new ArrayList<>();
		for (String schema : schemas) {
			List<String> tablesOfSchema = jdbc.queryForList(
				"select tablename from pg_tables where schemaname = ? order by tablename", String.class, schema);
			if (tablesOfSchema.isEmpty()) {
				// Gõ nhầm tên schema hoặc entity chưa được entity scan thấy: báo ngay thay vì dọn "không gì cả" trong im lặng.
				throw new IllegalStateException("Schema \"" + schema + "\" không có bảng nào. Tên schema có đúng không, "
					+ "và package entity đã nằm trong @EntityScan của ScenarioTestConfiguration chưa?");
			}
			for (String table : tablesOfSchema) {
				tables.add("\"" + schema + "\".\"" + table + "\"");
			}
		}
		if (!tables.isEmpty()) {
			jdbc.execute("truncate table " + String.join(", ", tables) + " restart identity cascade");
		}
	}
}
