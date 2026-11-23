package com.example.hibernatelab.g4_update_child_via_parent.u2_owning_collection;

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
 * Dòng hàng (con) của biến thể U2.
 *
 * BIẾN THỂ : U2 (g4_u2_owning_collection)
 * BASELINE : U0 (g4_u0_baseline)
 * KHÁC BIỆT: items là @OneToMany MỘT CHIỀU + @JoinTable, nên collection thuộc phía OWNING (item không còn trỏ ngược order)
 * HẬU QUẢ  : thêm item vào collection là đổi state của cha: ghi bảng nối và có thể tăng version cha (G4-S06)
 */
@Entity(name = "G4U2_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g4_u2_owning_collection", name = "order_item")
public class U2OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g4u2_order_item_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g4u2_order_item_seq", schema = "g4_u2_owning_collection", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// U2/U3: KHÔNG có field order. Quan hệ do collection PurchaseOrder.items (owning) ghi qua bảng nối.

	// OWNING side — FK order_item.product_id nằm ở bảng này. Không cascade (product là dữ liệu master dùng chung).
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private U2Product product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	@Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;

	protected U2OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	/** Chưa gán order: gọi {@code order.addItem(item)} để gắn vào cha. */
	public U2OrderItem(U2Product product, int quantity, BigDecimal unitPrice) {
		this.product = product;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
	}

	public Long getId() {
		return id;
	}

	public U2Product getProduct() {
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
