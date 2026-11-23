package com.example.hibernatelab.g4_update_child_via_parent.u3_optimistic_excluded;

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
 * Dòng hàng (con) của biến thể U3.
 *
 * BIẾN THỂ : U3 (g4_u3_optimistic_excluded)
 * BASELINE : U0 (g4_u0_baseline)
 * KHÁC BIỆT: như U2 + @OptimisticLock(excluded = true) trên items
 * HẬU QUẢ  : thay đổi collection items không còn làm tăng version của cha (G4-S07)
 */
@Entity(name = "G4U3_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g4_u3_optimistic_excluded", name = "order_item")
public class U3OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g4u3_order_item_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g4u3_order_item_seq", schema = "g4_u3_optimistic_excluded", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// U2/U3: KHÔNG có field order. Quan hệ do collection PurchaseOrder.items (owning) ghi qua bảng nối.

	// OWNING side — FK order_item.product_id nằm ở bảng này. Không cascade (product là dữ liệu master dùng chung).
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private U3Product product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	@Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;

	protected U3OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	/** Chưa gán order: gọi {@code order.addItem(item)} để gắn vào cha. */
	public U3OrderItem(U3Product product, int quantity, BigDecimal unitPrice) {
		this.product = product;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
	}

	public Long getId() {
		return id;
	}

	public U3Product getProduct() {
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
