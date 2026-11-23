package com.example.hibernatelab.g9_integrated.s02_order_update;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO "cập nhật đơn hàng" như client gửi lên. Cố ý KHÔNG có status, createdAt, customer: đó là dữ liệu client không được sửa.
 *
 * @param orderId id đơn cần sửa
 * @param version version client đọc được lúc mở form (dùng cho optimistic lock, cần cho cách B)
 * @param items   danh sách item MONG MUỐN sau khi sửa: có itemId = sửa, itemId null = thêm mới, item cũ vắng mặt = xoá
 */
public record OrderUpdateRequest(Long orderId, Long version, String orderNo, String note, List<ItemRequest> items) {

	/** Một dòng hàng trong request. {@code itemId == null} nghĩa là dòng mới. */
	public record ItemRequest(Long itemId, Long productId, int quantity, BigDecimal unitPrice) {
	}
}
