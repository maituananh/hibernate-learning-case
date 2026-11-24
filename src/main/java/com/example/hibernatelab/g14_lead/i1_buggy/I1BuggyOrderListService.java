package com.example.hibernatelab.g14_lead.i1_buggy;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case "trang danh sách đơn hàng": BẢN CÓ LỖI.
 *
 * BIẾN THỂ : I1 buggy
 * BASELINE : không có (G14 được miễn quy tắc một khác biệt, E7)
 * KHÁC BIỆT: lấy cả entity {@code PurchaseOrder} rồi duyệt từng entity để lấy tên khách và số dòng hàng
 * HẬU QUẢ  : N+1 hai tầng. Với trang N đơn: 1 SELECT trang + 1 SELECT count + N SELECT customer + N SELECT items
 *            = 2 + 2N câu. Mapping hoàn toàn đúng (mọi quan hệ LAZY) nên review mapping KHÔNG thấy lỗi này.
 */
@Service
public class I1BuggyOrderListService {

	private final I1BuggyOrderRepository orders;

	public I1BuggyOrderListService(I1BuggyOrderRepository orders) {
		this.orders = orders;
	}

	// readOnly = true vì chỉ đọc. Cần có transaction để các quan hệ LAZY còn đọc được (open-in-view đã tắt).
	@Transactional(readOnly = true)
	public Page<I1BuggyOrderRow> list(Pageable pageable) {
		return orders.findAll(pageable).map(order -> new I1BuggyOrderRow(
			order.getOrderNo(),
			order.getCustomer().getName(), // LAZY proxy: chạm vào tên là SELECT customer (1 câu cho MỖI khách khác nhau)
			order.getItems().size())); // LAZY collection: .size() là SELECT order_item (1 câu cho MỖI đơn)
	}
}
