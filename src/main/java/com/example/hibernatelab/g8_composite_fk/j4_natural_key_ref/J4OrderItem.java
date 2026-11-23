package com.example.hibernatelab.g8_composite_fk.j4_natural_key_ref;

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
 * Dòng đơn hàng trỏ tới sản phẩm qua {@code sku} thay vì qua {@code id}.
 *
 * BIẾN THỂ : J4 (g8_j4_natural_key_ref)
 * BASELINE : J1 (g8_j1_explicit)
 * KHÁC BIỆT: FK một cột trỏ tới cột UNIQUE không phải PK (product.sku) nhờ referencedColumnName
 * HẬU QUẢ  : FK order_item.product_sku -> product(sku); LAZY không tạo được proxy "rỗng" như khi trỏ PK (G8-S06)
 */
@Entity(name = "G8J4_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g8_j4_natural_key_ref", name = "order_item")
public class J4OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g8j4_order_item_seq")
	@SequenceGenerator(name = "g8j4_order_item_seq", schema = "g8_j4_natural_key_ref", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	// OWNING side — referencedColumnName = "sku": FK trỏ tới cột sku (UNIQUE) chứ không phải PK của product.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_sku", referencedColumnName = "sku", nullable = false)
	private J4Product product;

	protected J4OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public J4OrderItem(J4Product product, int quantity) {
		this.product = product;
		this.quantity = quantity;
	}

	public Long getId() {
		return id;
	}

	public J4Product getProduct() {
		return product;
	}

	public int getQuantity() {
		return quantity;
	}
}
