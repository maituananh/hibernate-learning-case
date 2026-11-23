package com.example.hibernatelab.g2_cascade.c2_merge;

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
 * Dòng hàng của biến thể C2. Xem header ở C2Customer để biết biến thể này khác baseline điểm nào.
 */
@Entity(name = "G2C2_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g2_c2_merge", name = "order_item")
public class C2OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g2c2_order_item_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g2c2_order_item_seq", schema = "g2_c2_merge", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK order_item.order_id nằm ở bảng này. fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private C2PurchaseOrder order;

	// OWNING side — FK order_item.product_id nằm ở bảng này. Không cascade (product là dữ liệu master dùng chung).
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private C2Product product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected C2OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	/** Chưa gán order: gọi {@code order.addItem(item)} để gán cả hai phía. */
	public C2OrderItem(C2Product product, int quantity) {
		this.product = product;
		this.quantity = quantity;
	}

	public Long getId() {
		return id;
	}

	public C2PurchaseOrder getOrder() {
		return order;
	}

	void setOrder(C2PurchaseOrder order) {
		this.order = order;
	}

	public C2Product getProduct() {
		return product;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}
}
