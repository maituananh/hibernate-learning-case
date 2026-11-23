package com.example.hibernatelab.g9_integrated.s07_tx_boundary;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * G9-S07: bốn cách một service đọc đơn hàng, chỉ khác nhau ở RANH GIỚI TRANSACTION.
 * Spring dùng PROXY để mở transaction: chỉ lời gọi đi qua proxy (từ bean khác) mới có {@code @Transactional}.
 * {@code open-in-view=false} nên ngoài transaction không có session nào giữ giùm persistence context.
 */
@Service
public class OrderQueryService {

	@PersistenceContext
	private EntityManager em;

	/**
	 * (a) KHÔNG {@code @Transactional}: {@code em.find} tự mở một transaction ngắn riêng rồi đóng ngay,
	 * order trở thành DETACHED trước khi dòng sau chạy. Chạm {@code getItems()} (LAZY) → LazyInitializationException.
	 */
	public int countItemsWithoutTransaction(Long orderId) {
		PurchaseOrder order = em.find(PurchaseOrder.class, orderId); // persistence context đóng ngay sau lời gọi này
		return order.getItems().size(); // collection chưa nạp, session đã đóng → LazyInitializationException
	}

	/** Đối chứng cho (b): method này CÓ {@code @Transactional}, trả về việc có transaction đang chạy hay không. */
	@Transactional
	public boolean isTransactionActive() {
		return TransactionSynchronizationManager.isActualTransactionActive();
	}

	/**
	 * (b) SELF-INVOCATION: method này không có transaction và gọi {@code this.isTransactionActive()}.
	 * Gọi qua {@code this} thì bỏ qua proxy nên {@code @Transactional} bên kia bị phớt lờ → trả về false (nối với G0-S14).
	 */
	public boolean isTransactionActiveViaSelfInvocation() {
		return this.isTransactionActive();
	}

	/**
	 * (c) Có {@code @Transactional} nhưng TRẢ VỀ ENTITY: trong method order là MANAGED, nhưng transaction kết thúc
	 * khi method trả về nên caller nhận order DETACHED với collection LAZY chưa nạp.
	 */
	@Transactional(readOnly = true)
	public PurchaseOrder findOrderEntity(Long orderId) {
		return em.find(PurchaseOrder.class, orderId);
	}

	/**
	 * (d) Có {@code @Transactional} và TRẢ VỀ DTO: mọi dữ liệu cần dùng được đọc xong BÊN TRONG transaction
	 * (items nạp lúc gọi getItems().size()) rồi sao chép sang record thuần. Caller không cần persistence context.
	 */
	@Transactional(readOnly = true)
	public OrderSummary findOrderSummary(Long orderId) {
		PurchaseOrder order = em.find(PurchaseOrder.class, orderId);
		List<String> skus = new ArrayList<>();
		for (OrderItem item : order.getItems()) { // LAZY nạp ở đây, trong transaction
			skus.add(item.getSku());
		}
		return new OrderSummary(order.getOrderNo(), skus.size(), skus);
	}
}
