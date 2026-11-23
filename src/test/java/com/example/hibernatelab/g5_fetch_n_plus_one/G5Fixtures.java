package com.example.hibernatelab.g5_fetch_n_plus_one;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Dữ liệu chuẩn của nhóm G5, ghi thẳng bằng JDBC (KHÔNG qua Hibernate).
 *
 * <p>Vì sao JDBC mà không persist entity: (1) cùng một đoạn seed dùng được cho cả 6 biến thể có entity khác nhau;
 * (2) lúc seed không có entity nào được tải vào persistence context, nên các SELECT ở bước WHEN là SELECT "thật"
 * chứ không phải cache cấp 1; (3) {@code SqlCaptor} chỉ ghi SQL của Hibernate nên seed không làm lệch số đếm.
 *
 * <p>Dữ liệu (đọc một lần là nhớ):
 * <ul>
 *   <li>5 customer: Customer-1 … Customer-5</li>
 *   <li>mỗi customer 2 order → 10 order; mỗi order 2 item → 20 item</li>
 *   <li>3 product (PEN, BOOK, BAG): item lần lượt dùng PEN, BOOK, BAG, PEN, … nên đủ cả 3 product</li>
 *   <li>3 payment, TẤT CẢ thuộc Customer-1. Nhờ vậy Customer-1 có 2 order × 3 payment (đủ để thấy tích Descartes ở S11)
 *       còn 4 customer kia không có payment nào</li>
 * </ul>
 */
final class G5Fixtures {

	static final int CUSTOMER_COUNT = 5;
	static final int ORDER_COUNT = 10;
	static final int ITEM_COUNT = 20;
	static final int PRODUCT_COUNT = 3;
	static final int PAYMENT_COUNT = 3;

	private G5Fixtures() {
	}

	/** Seed dữ liệu chuẩn vào schema của một biến thể (bảng đã được Hibernate tạo lúc khởi động). */
	static void seed(JdbcTemplate jdbc, String schema) {
		String[] productNames = { "PEN", "BOOK", "BAG" };
		for (int p = 1; p <= PRODUCT_COUNT; p++) {
			jdbc.update("insert into " + schema + ".product (id, name) values (?, ?)", p, productNames[p - 1]);
		}
		int itemId = 1;
		for (int c = 1; c <= CUSTOMER_COUNT; c++) {
			jdbc.update("insert into " + schema + ".customer (id, name) values (?, ?)", c, "Customer-" + c);
			for (int o = 1; o <= 2; o++) {
				int orderId = c * 10 + o; // 11, 12, 21, 22, … 51, 52
				jdbc.update("insert into " + schema + ".purchase_order (id, order_no, customer_id) values (?, ?, ?)",
					orderId, "ORD-" + orderId, c);
				for (int i = 1; i <= 2; i++) {
					int productId = (itemId - 1) % PRODUCT_COUNT + 1;
					jdbc.update("insert into " + schema + ".order_item (id, order_id, product_id, quantity) values (?, ?, ?, ?)",
						itemId, orderId, productId, i);
					itemId++;
				}
			}
		}
		for (int p = 1; p <= PAYMENT_COUNT; p++) {
			jdbc.update("insert into " + schema + ".payment (id, customer_id, amount) values (?, 1, ?)", p, p * 100);
		}
	}

	/** Id của order đầu tiên (ORD-11, thuộc Customer-1), để test dùng cho em.find. */
	static long firstOrderId() {
		return 11L;
	}

	/** Id của Customer-1: customer duy nhất có payment (3 payment) và có 2 order. */
	static long customerWithPaymentsId() {
		return 1L;
	}
}
