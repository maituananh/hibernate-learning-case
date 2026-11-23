package com.example.hibernatelab.g4_update_child_via_parent.u1_no_merge;

import java.math.BigDecimal;

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
 * Dòng hàng (con) của biến thể U1.
 *
 * BIẾN THỂ : U1 (g4_u1_no_merge)
 * BASELINE : U0 (g4_u0_baseline)
 * KHÁC BIỆT: items: cascade = PERSIST (bỏ MERGE; vẫn giữ orphanRemoval = true)
 * HẬU QUẢ  : merge(order DETACHED) không lan xuống item nên thay đổi của item bị MẤT (G4-S03)
 */
@Entity(name = "G4U1_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g4_u1_no_merge", name = "order_item")
public class U1OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g4u1_order_item_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g4u1_order_item_seq", schema = "g4_u1_no_merge", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK order_item.order_id nằm ở bảng này. fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private U1PurchaseOrder order;

	// OWNING side — FK order_item.product_id nằm ở bảng này. Không cascade (product là dữ liệu master dùng chung).
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private U1Product product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	@Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;

	protected U1OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	/** Chưa gán order: gọi {@code order.addItem(item)} để gắn vào cha. */
	public U1OrderItem(U1Product product, int quantity, BigDecimal unitPrice) {
		this.product = product;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
	}

	public Long getId() {
		return id;
	}

	public U1PurchaseOrder getOrder() {
		return order;
	}

	void setOrder(U1PurchaseOrder order) {
		this.order = order;
	}

	public U1Product getProduct() {
		return product;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}
}
