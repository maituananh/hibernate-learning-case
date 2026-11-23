package com.example.hibernatelab.g1_owning_side.v4_bidi_mappedby;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Dòng hàng của biến thể V4. Giống hệt ở mọi biến thể G1: LUÔN là {@code @ManyToOne} tới
 * {@link PurchaseOrder} và tới {@link Product}. Biến thể chỉ khác nhau ở quan hệ Customer ↔ PurchaseOrder,
 * còn hai quan hệ này giữ cố định để so sánh công bằng.
 *
 * <p>G1 KHÔNG dùng cascade: muốn có item thì phải {@code persist} item một cách tường minh.
 */
@Entity(name = "G1V4_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g1_v4_bidi_mappedby", name = "order_item")
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v4_order_item_seq")
	@SequenceGenerator(name = "g1v4_order_item_seq", schema = "g1_v4_bidi_mappedby", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK order_item.order_id nằm ở bảng này.
	// fetch = LAZY ghi tường minh: đọc item không kéo theo order (tránh câu JOIN gây nhiễu khi đếm SQL).
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private PurchaseOrder order;

	// OWNING side — FK order_item.product_id nằm ở bảng này.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private Product product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public OrderItem(PurchaseOrder order, Product product, int quantity) {
		this.order = order;
		this.product = product;
		this.quantity = quantity;
	}

	public Long getId() {
		return id;
	}

	public PurchaseOrder getOrder() {
		return order;
	}

	public Product getProduct() {
		return product;
	}

	public int getQuantity() {
		return quantity;
	}
}
