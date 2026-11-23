package com.example.hibernatelab.g9_integrated.s02_order_update;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hibernatelab.g9_integrated.s02_order_update.OrderUpdateRequest.ItemRequest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * G9-S02: hai cách biến một {@link OrderUpdateRequest} (DTO) thành thay đổi trong DB.
 * Mỗi method {@code @Transactional} là một ranh giới transaction: entity MANAGED trong method, DETACHED khi method trả về.
 */
@Service
public class OrderUpdateService {

	@PersistenceContext
	private EntityManager em;

	/**
	 * CÁCH A (khuyến nghị): TẢI order đang MANAGED, rồi áp "diff" từ DTO lên chính nó.
	 * Field không có trong DTO (status, createdAt, customer) không bị đụng tới nên giữ nguyên.
	 * Dirty checking lúc commit tự sinh UPDATE chỉ cho những cột thật sự đổi.
	 */
	@Transactional
	public void updateByDiff(OrderUpdateRequest request) {
		// [LIFECYCLE] order: có id → find() trả về MANAGED (1 SELECT)
		PurchaseOrder order = em.find(PurchaseOrder.class, request.orderId());
		order.setOrderNo(request.orderNo());
		order.setNote(request.note());

		Map<Long, ItemRequest> requestedById = new HashMap<>();
		for (ItemRequest itemRequest : request.items()) {
			if (itemRequest.itemId() != null) {
				requestedById.put(itemRequest.itemId(), itemRequest);
			}
		}

		// 1) Item cũ: có trong DTO → sửa field; vắng mặt → removeItem (orphanRemoval sẽ DELETE).
		List<OrderItem> currentItems = new ArrayList<>(order.getItems()); // copy để vừa duyệt vừa xoá
		for (OrderItem existingItem : currentItems) {
			ItemRequest itemRequest = requestedById.get(existingItem.getId());
			if (itemRequest == null) {
				order.removeItem(existingItem);
			} else {
				existingItem.setQuantity(itemRequest.quantity());
			}
		}

		// 2) Item mới (itemId null) → addItem: cascade PERSIST từ order lo phần INSERT.
		for (ItemRequest itemRequest : request.items()) {
			if (itemRequest.itemId() == null) {
				Product product = em.getReference(Product.class, itemRequest.productId()); // không SELECT: chỉ cần id làm FK
				order.addItem(new OrderItem(product, itemRequest.quantity(), itemRequest.unitPrice()));
			}
		}
		order.recalculateTotal();
		// [FLUSH] lúc commit: UPDATE purchase_order (chỉ cột đổi), UPDATE/INSERT/DELETE order_item tương ứng
	}

	/**
	 * CÁCH B (BẪY): dựng entity mới từ DTO, gán id/version rồi merge() (đúng thứ Spring Data {@code save()} làm).
	 * merge() COPY TOÀN BỘ state của bản "mới" đè lên bản trong DB, kể cả những field mà DTO không có (null).
	 * Kết quả: status, createdAt, customer, total bị ghi đè thành NULL mà không có lỗi nào.
	 */
	@Transactional
	public void updateByMerge(OrderUpdateRequest request) {
		PurchaseOrder rebuilt = PurchaseOrder.detachedCopyFromRequest(
			request.orderId(), request.version(), request.orderNo(), request.note());
		for (ItemRequest itemRequest : request.items()) {
			Product product = em.getReference(Product.class, itemRequest.productId());
			rebuilt.addItem(OrderItem.detachedCopyFromRequest(
				itemRequest.itemId(), product, itemRequest.quantity(), itemRequest.unitPrice()));
		}
		// [LIFECYCLE] rebuilt: DETACHED (có id) và KHÔNG trở thành MANAGED; merge() trả về bản MANAGED khác.
		em.merge(rebuilt);
		// [FLUSH] lúc commit: UPDATE purchase_order set status=NULL, created_at=NULL, customer_id=NULL, total=NULL, ...
	}
}
