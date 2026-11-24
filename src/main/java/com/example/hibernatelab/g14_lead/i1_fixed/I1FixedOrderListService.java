package com.example.hibernatelab.g14_lead.i1_fixed;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case "trang danh sách đơn hàng": BẢN ĐÃ SỬA.
 *
 * BIẾN THỂ : I1 fixed
 * BASELINE : I1 buggy
 * KHÁC BIỆT: đọc thẳng DTO bằng một query (join + subquery đếm) thay vì nạp entity rồi duyệt
 * HẬU QUẢ  : đúng 2 câu SELECT (trang + count) bất kể cỡ trang; quy tắc G5/G10: màn hình danh sách đọc DTO, không đọc entity
 */
@Service
public class I1FixedOrderListService {

	private final I1FixedOrderRepository orders;

	public I1FixedOrderListService(I1FixedOrderRepository orders) {
		this.orders = orders;
	}

	@Transactional(readOnly = true)
	public Page<I1FixedOrderRow> list(Pageable pageable) {
		return orders.findOrderRows(pageable);
	}
}
