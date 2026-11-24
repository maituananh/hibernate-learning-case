package com.example.hibernatelab.g10_query;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Dữ liệu chuẩn của nhóm G10, ghi thẳng bằng JDBC (KHÔNG qua Hibernate), cùng lý do như {@code G5Fixtures}:
 * lúc seed không có entity nào nằm trong persistence context, nên các SELECT ở bước WHEN là SELECT "thật".
 *
 * <p>Dữ liệu (đọc một lần là nhớ):
 * <ul>
 *   <li>3 customer: Customer-1 … Customer-3</li>
 *   <li>mỗi customer 2 order: order thứ nhất trạng thái <b>PAID</b>, order thứ hai trạng thái <b>NEW</b> → 6 order</li>
 *   <li>mỗi order 2 item → 12 item; 3 product (PEN, BOOK, BAG)</li>
 * </ul>
 */
final class G10Fixtures {

	static final int CUSTOMER_COUNT = 3;
	static final int ORDERS_PER_CUSTOMER = 2;
	static final int ORDER_COUNT = 6;
	static final int PAID_ORDER_COUNT = 3;

	private G10Fixtures() {
	}

	/** Seed dữ liệu chuẩn vào schema {@code g10_q1_baseline} (bảng đã được Hibernate tạo lúc khởi động). */
	static void seed(JdbcTemplate jdbc, String schema) {
		String[] productNames = { "PEN", "BOOK", "BAG" };
		for (int p = 1; p <= productNames.length; p++) {
			jdbc.update("insert into " + schema + ".product (id, name) values (?, ?)", p, productNames[p - 1]);
		}
		int itemId = 1;
		for (int c = 1; c <= CUSTOMER_COUNT; c++) {
			jdbc.update("insert into " + schema + ".customer (id, name) values (?, ?)", c, "Customer-" + c);
			for (int o = 1; o <= ORDERS_PER_CUSTOMER; o++) {
				int orderId = c * 10 + o; // 11, 12, 21, 22, 31, 32
				String status = (o == 1) ? "PAID" : "NEW";
				jdbc.update("insert into " + schema + ".purchase_order (id, order_no, status, customer_id) values (?, ?, ?, ?)",
					orderId, "ORD-" + orderId, status, c);
				for (int i = 1; i <= 2; i++) {
					int productId = (itemId - 1) % productNames.length + 1;
					jdbc.update("insert into " + schema + ".order_item (id, order_id, product_id, quantity) values (?, ?, ?, ?)",
						itemId, orderId, productId, i);
					itemId++;
				}
			}
		}
	}
}
