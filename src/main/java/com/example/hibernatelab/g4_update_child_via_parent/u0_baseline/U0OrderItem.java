package com.example.hibernatelab.g4_update_child_via_parent.u0_baseline;

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
 * Dòng hàng (con) của biến thể U0.
 *
 * BIẾN THỂ : U0 (g4_u0_baseline)
 * BASELINE : U0 (g4_u0_baseline)
 * KHÁC BIỆT: hai chiều mappedBy, items: cascade = ALL, orphanRemoval = true (BASELINE)
 * HẬU QUẢ  : đây là mốc so sánh: sửa/thêm/xoá item qua cha KHÔNG đụng dòng purchase_order (G4-S01, S04, S05, S08)
 */
@Entity(name = "G4U0_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g4_u0_baseline", name = "order_item")
public class U0OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g4u0_order_item_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g4u0_order_item_seq", schema = "g4_u0_baseline", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK order_item.order_id nằm ở bảng này. fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private U0PurchaseOrder order;

	// OWNING side — FK order_item.product_id nằm ở bảng này. Không cascade (product là dữ liệu master dùng chung).
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private U0Product product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	@Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;

	protected U0OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	/** Chưa gán order: gọi {@code order.addItem(item)} để gắn vào cha. */
	public U0OrderItem(U0Product product, int quantity, BigDecimal unitPrice) {
		this.product = product;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
	}

	public Long getId() {
		return id;
	}

	public U0PurchaseOrder getOrder() {
		return order;
	}

	void setOrder(U0PurchaseOrder order) {
		this.order = order;
	}

	public U0Product getProduct() {
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
