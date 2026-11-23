package com.example.hibernatelab.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Các hàm assert SQL, đặt tên như câu văn: {@code sql.assertInserts("customer", 1)}.
 *
 * <p>Tên bảng truyền vào có thể có hoặc không kèm schema:
 * {@code "customer"} khớp mọi bảng tên customer, {@code "g0_lifecycle.customer"} chỉ khớp đúng bảng đó.
 *
 * <p>Cách phân loại câu SQL rất đơn giản (đọc từ khoá đầu câu, rồi tên bảng ngay sau
 * {@code insert into} / {@code update} / {@code delete from}); {@code select} được tính cho bảng nào
 * xuất hiện sau {@code from} hoặc {@code join}. Đủ dùng cho SQL Hibernate sinh ra, không phải trình parse SQL.
 *
 * <p>Mọi assert đều kèm thông báo lỗi liệt kê toàn bộ SQL thực tế đã chạy, nên khi đỏ là thấy ngay vì sao.
 */
public class SqlAssert {

	private static final Pattern INSERT = Pattern.compile("^\\s*insert\\s+into\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern UPDATE = Pattern.compile("^\\s*update\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern DELETE = Pattern.compile("^\\s*delete\\s+from\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern FROM_OR_JOIN = Pattern.compile("\\b(?:from|join)\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

	private final SqlCaptor captor;

	public SqlAssert(SqlCaptor captor) {
		this.captor = captor;
	}

	// ── điều khiển ghi SQL (chuyển tiếp sang SqlCaptor để test chỉ cần một biến `sql`) ──

	/** Xoá SQL đã ghi. Gọi trước bước WHEN. */
	public void reset() {
		captor.reset();
	}

	/** In dòng đánh dấu (ví dụ "WHEN") ra console và ghi vào danh sách SQL. */
	public void mark(String label) {
		captor.mark(label);
	}

	/** In ra console toàn bộ SQL Hibernate đã chạy từ lần {@link #reset()} cuối. */
	public void print() {
		System.out.println("[SQL] Hibernate đã chạy từ lần reset():");
		List<String> lines = captor.printableLines();
		if (lines.isEmpty()) {
			System.out.println("  (không có câu SQL nào)");
		}
		for (String line : lines) {
			System.out.println("  " + line);
		}
	}

	// ── assert số lượng ──

	public void assertInserts(String table, int expected) {
		assertCount("INSERT", INSERT, table, expected);
	}

	public void assertUpdates(String table, int expected) {
		assertCount("UPDATE", UPDATE, table, expected);
	}

	public void assertDeletes(String table, int expected) {
		assertCount("DELETE", DELETE, table, expected);
	}

	public void assertSelects(String table, int expected) {
		int actual = 0;
		for (String statement : captor.statements()) {
			if (!statement.stripLeading().toLowerCase(Locale.ROOT).startsWith("select")) {
				continue;
			}
			Matcher matcher = FROM_OR_JOIN.matcher(statement);
			while (matcher.find()) {
				if (tableMatches(matcher.group(1), table)) {
					actual++;
					break; // một câu select chạm bảng này nhiều lần (self-join) vẫn chỉ tính 1 câu
				}
			}
		}
		assertThat(actual).as(message("SELECT", table, expected)).isEqualTo(expected);
	}

	/** Tổng số câu SQL (không tính nextval). */
	public void assertTotal(int expected) {
		assertThat(captor.statements().size())
			.as("Tổng số câu SQL (không tính nextval) phải là " + expected + ". " + listing())
			.isEqualTo(expected);
	}

	/**
	 * Kiểm tra các mẫu xuất hiện theo ĐÚNG THỨ TỰ (không cần liên tiếp). Mỗi mẫu là một đoạn văn bản
	 * không phân biệt hoa thường, ví dụ {@code "insert into g1.customer"}, {@code "delete from g1.order_item"}.
	 * Chỉ dùng khi thứ tự chính là bài học (ví dụ thứ tự flush).
	 */
	public void assertInOrder(String... fragments) {
		List<String> statements = captor.statements();
		int searchFrom = 0;
		for (String fragment : fragments) {
			int found = -1;
			for (int i = searchFrom; i < statements.size(); i++) {
				if (statements.get(i).toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT))) {
					found = i;
					break;
				}
			}
			assertThat(found)
				.as("Câu SQL chứa \"" + fragment + "\" phải xuất hiện (và nằm SAU các mẫu đứng trước nó). " + listing())
				.isGreaterThanOrEqualTo(0);
			searchFrom = found + 1;
		}
	}

	/** Có ít nhất một câu SQL chứa đoạn văn bản này (không phân biệt hoa thường). Ví dụ {@code "left join"}. */
	public void assertSomeStatementContains(String fragment) {
		boolean found = false;
		for (String statement : captor.statements()) {
			if (statement.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT))) {
				found = true;
			}
		}
		assertThat(found).as("Phải có câu SQL chứa \"" + fragment + "\". " + listing()).isTrue();
	}

	/** KHÔNG câu SQL nào chứa đoạn văn bản này (không phân biệt hoa thường). */
	public void assertNoStatementContains(String fragment) {
		boolean found = false;
		for (String statement : captor.statements()) {
			if (statement.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT))) {
				found = true;
			}
		}
		assertThat(found).as("KHÔNG được có câu SQL chứa \"" + fragment + "\". " + listing()).isFalse();
	}

	// ── nội bộ ──

	private void assertCount(String kind, Pattern pattern, String table, int expected) {
		int actual = 0;
		for (String statement : captor.statements()) {
			Matcher matcher = pattern.matcher(statement);
			if (matcher.find() && tableMatches(matcher.group(1), table)) {
				actual++;
			}
		}
		assertThat(actual).as(message(kind, table, expected)).isEqualTo(expected);
	}

	/** "g0_lifecycle.customer" khớp "customer" và khớp chính nó; "customer_order" KHÔNG khớp "customer". */
	private boolean tableMatches(String tableInSql, String wanted) {
		String actualName = tableInSql.replace("\"", "").toLowerCase(Locale.ROOT);
		String wantedName = wanted.toLowerCase(Locale.ROOT);
		return actualName.equals(wantedName) || actualName.endsWith("." + wantedName);
	}

	private String message(String kind, String table, int expected) {
		return "Số câu " + kind + " vào bảng " + table + " phải là " + expected + ". " + listing();
	}

	private String listing() {
		List<String> statements = captor.statements();
		StringBuilder builder = new StringBuilder("SQL thực tế đã chạy (" + statements.size() + " câu, không tính nextval):");
		for (String statement : statements) {
			builder.append("\n    ").append(statement.strip());
		}
		return builder.toString();
	}
}
