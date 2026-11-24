package com.example.hibernatelab.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rào chắn "ngân sách SQL" (G14-R1): mỗi use case quan trọng có giới hạn số câu SELECT; vượt thì ném {@link AssertionError}
 * kèm danh sách SQL đã chạy, để người đọc thấy NGAY câu nào là thừa.
 *
 * <p>Cách dùng: {@code sql.reset(); chạyUseCase(); SqlBudget.assertSelectsAtMost(captor, "danh sách đơn", 2);}
 * Chỉ đếm câu {@code select} (bỏ qua {@code nextval}, đã được {@link SqlCaptor#statements()} loại sẵn).
 * Muốn đếm cả INSERT/UPDATE thì dùng {@link SqlAssert}; helper này cố ý chỉ lo chuyện "đọc quá nhiều".
 */
public final class SqlBudget {

	private SqlBudget() {
	}

	public static void assertSelectsAtMost(SqlCaptor captor, String useCase, int maxSelects) {
		List<String> selects = new ArrayList<>();
		for (String statement : captor.statements()) {
			if (statement.stripLeading().toLowerCase(Locale.ROOT).startsWith("select")) {
				selects.add(statement.strip());
			}
		}
		if (selects.size() > maxSelects) {
			StringBuilder message = new StringBuilder("Use case \"" + useCase + "\" vượt ngân sách SQL: được phép tối đa "
				+ maxSelects + " câu SELECT nhưng đã chạy " + selects.size() + ". Các câu SELECT đã chạy:");
			for (int i = 0; i < selects.size(); i++) {
				message.append("\n    #").append(i + 1).append(' ').append(selects.get(i).replaceAll("\\s+", " "));
			}
			throw new AssertionError(message.toString());
		}
	}
}
