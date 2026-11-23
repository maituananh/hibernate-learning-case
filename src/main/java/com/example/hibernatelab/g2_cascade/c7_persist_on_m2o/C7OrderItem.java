package com.example.hibernatelab.g2_cascade.c7_persist_on_m2o;

import jakarta.persistence.CascadeType;
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
 * Dòng hàng của biến thể C7. Xem header ở C7Customer để biết biến thể này khác baseline điểm nào.
 */
@Entity(name = "G2C7_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g2_c7_persist_on_m2o", name = "order_item")
public class C7OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g2c7_order_item_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g2c7_order_item_seq", schema = "g2_c7_persist_on_m2o", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK order_item.order_id nằm ở bảng này. fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private C7PurchaseOrder order;

	// OWNING side — FK order_item.product_id nằm ở bảng này.
	// KHÁC BIỆT CỦA BIẾN THỂ C7: cascade = CascadeType.PERSIST đặt trên @ManyToOne (phía CON trỏ tới thứ nó dùng chung).
	@ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
	@JoinColumn(name = "product_id")
	private C7Product product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected C7OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	/** Chưa gán order: gọi {@code order.addItem(item)} để gán cả hai phía. */
	public C7OrderItem(C7Product product, int quantity) {
		this.product = product;
		this.quantity = quantity;
	}

	public Long getId() {
		return id;
	}

	public C7PurchaseOrder getOrder() {
		return order;
	}

	void setOrder(C7PurchaseOrder order) {
		this.order = order;
	}

	public C7Product getProduct() {
		return product;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}
}
