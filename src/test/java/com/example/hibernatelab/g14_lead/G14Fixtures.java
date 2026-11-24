package com.example.hibernatelab.g14_lead;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Dữ liệu chuẩn của nhóm G14, ghi thẳng bằng JDBC (KHÔNG qua Hibernate), cùng lý do như {@code G10Fixtures}:
 * lúc seed không có entity nào nằm trong persistence context, nên các SELECT ở bước WHEN là SELECT "thật".
 */
final class G14Fixtures {

	static final String I1_BUGGY = "g14_i1_buggy";
	static final String I1_FIXED = "g14_i1_fixed";
	static final String I2_BUGGY = "g14_i2_buggy";
	static final String I2_FIXED = "g14_i2_fixed";
	static final String I3_BUGGY = "g14_i3_buggy";
	static final String I3_FIXED = "g14_i3_fixed";
	static final String I4_BUGGY = "g14_i4_buggy";
	static final String I4_FIXED = "g14_i4_fixed";

	/** I1: 30 khách, mỗi khách 1 đơn, mỗi đơn 3 dòng hàng. */
	static final int I1_ORDER_COUNT = 30;
	static final int I1_ITEMS_PER_ORDER = 3;

	/** I2: một sản phẩm duy nhất id = 1. */
	static final long PRODUCT_ID = 1L;

	private G14Fixtures() {
	}

	static void seedI1(JdbcTemplate jdbc, String schema) {
		int itemId = 1;
		for (int n = 1; n <= I1_ORDER_COUNT; n++) {
			jdbc.update("insert into " + schema + ".customer (id, name) values (?, ?)", n, "Customer-" + n);
			jdbc.update("insert into " + schema + ".purchase_order (id, order_no, customer_id) values (?, ?, ?)", n, "ORD-" + n, n);
			for (int i = 1; i <= I1_ITEMS_PER_ORDER; i++) {
				jdbc.update("insert into " + schema + ".order_item (id, order_id, product_name, quantity) values (?, ?, ?, ?)",
					itemId++, n, "Product-" + i, i);
			}
		}
	}

	/** I2 buggy (không có cột version). */
	static void seedProductBuggy(JdbcTemplate jdbc, int stock) {
		jdbc.update("insert into " + I2_BUGGY + ".product (id, name, stock) values (?, ?, ?)", PRODUCT_ID, "Flash-Sale-Phone", stock);
	}

	/** I2 fixed (có cột version, bắt đầu từ 0). */
	static void seedProductFixed(JdbcTemplate jdbc, int stock) {
		jdbc.update("insert into " + I2_FIXED + ".product (id, name, stock, version) values (?, ?, ?, 0)", PRODUCT_ID, "Flash-Sale-Phone", stock);
	}

	/** I3: hai việc NEW, id 1 và 2 (thứ tự id quyết định "việc đầu tiên"). */
	static void seedTwoJobs(JdbcTemplate jdbc, String schema) {
		jdbc.update("insert into " + schema + ".job (id, recipient, status) values (1, 'an@example.com', 'NEW')");
		jdbc.update("insert into " + schema + ".job (id, recipient, status) values (2, 'binh@example.com', 'NEW')");
	}
}
