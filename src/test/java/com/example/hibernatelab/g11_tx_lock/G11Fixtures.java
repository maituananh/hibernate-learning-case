package com.example.hibernatelab.g11_tx_lock;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Dữ liệu chuẩn của nhóm G11, ghi thẳng bằng JDBC (KHÔNG qua Hibernate) để lúc seed không có entity nào nằm trong
 * persistence context. Mỗi test tự gọi đúng hàm seed nó cần, nên GIVEN nhìn thấy ngay trong method.
 */
final class G11Fixtures {

	static final String T1 = "g11_t1_baseline";
	static final String T2 = "g11_t2_versioned";
	static final String T3 = "g11_t3_job";

	/** Product có id = 1 trong mọi schema (đọc ở test là {@code PRODUCT_ID}). */
	static final long PRODUCT_ID = 1L;

	private G11Fixtures() {
	}

	/** Seed 1 product "PEN" với tồn kho cho trước vào T1 (không có cột version). */
	static void seedProductT1(JdbcTemplate jdbc, int stock) {
		jdbc.update("insert into " + T1 + ".product (id, name, stock) values (?, ?, ?)", PRODUCT_ID, "PEN", stock);
	}

	/** Seed hai product (id 1 và 2) vào T1, dùng cho deadlock (S10): hai dòng để khoá ngược thứ tự. */
	static void seedTwoProductsT1(JdbcTemplate jdbc) {
		jdbc.update("insert into " + T1 + ".product (id, name, stock) values (1, 'PEN', 10)");
		jdbc.update("insert into " + T1 + ".product (id, name, stock) values (2, 'BOOK', 10)");
	}

	/** Seed 1 product "PEN" vào T2, version khởi đầu = 0. */
	static void seedProductT2(JdbcTemplate jdbc, int stock) {
		jdbc.update("insert into " + T2 + ".product (id, name, stock, version) values (?, ?, ?, 0)", PRODUCT_ID, "PEN", stock);
	}

	/** Seed 3 việc NEW (id 1, 2, 3) vào hàng đợi T3. */
	static void seedThreeJobsT3(JdbcTemplate jdbc) {
		for (int id = 1; id <= 3; id++) {
			jdbc.update("insert into " + T3 + ".job (id, payload, status) values (?, ?, 'NEW')", id, "email-" + id);
		}
	}

	/** Seed 1 order (id 1) có 2 item vào T1, dùng cho S11 và S12. */
	static void seedOrderWithTwoItemsT1(JdbcTemplate jdbc) {
		jdbc.update("insert into " + T1 + ".purchase_order (id, order_no) values (1, 'ORD-1')");
		jdbc.update("insert into " + T1 + ".order_item (id, order_id, sku) values (1, 1, 'SKU-A')");
		jdbc.update("insert into " + T1 + ".order_item (id, order_id, sku) values (2, 1, 'SKU-B')");
	}
}
