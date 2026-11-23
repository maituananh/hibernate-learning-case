package com.example.hibernatelab.g9_integrated.s02_order_update;

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
 * Dòng hàng (con) của G9-S02, S08, S09.
 *
 * BIẾN THỂ : G9-S02 (g9_s02_order_update)
 * BASELINE : G4-U0
 * KHÁC BIỆT: không có
 * HẬU QUẢ  : không có
 */
@Entity(name = "G9S02_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s02_order_update", name = "order_item")
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s02_order_item_seq")
	@SequenceGenerator(name = "g9s02_order_item_seq", schema = "g9_s02_order_update", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK order_item.order_id nằm ở bảng này (đây là phía quyết định INSERT/UPDATE cột order_id).
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private PurchaseOrder order;

	// OWNING side — FK order_item.product_id. Không cascade: product là dữ liệu master dùng chung.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private Product product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	@Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;

	protected OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public OrderItem(Product product, int quantity, BigDecimal unitPrice) {
		this.product = product;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
	}

	/** CHỈ ĐỂ MINH HOẠ BẪY của cách B (G9-S02): item "mới" nhưng mang id lấy từ DTO để merge() hiểu là dòng cũ. */
	public static OrderItem detachedCopyFromRequest(Long id, Product product, int quantity, BigDecimal unitPrice) {
		OrderItem copy = new OrderItem(product, quantity, unitPrice);
		copy.id = id;
		return copy;
	}

	public Long getId() {
		return id;
	}

	public PurchaseOrder getOrder() {
		return order;
	}

	/** Chỉ đổi owning side (FK). Nên gọi order.addItem(...) để giữ đồng bộ cả collection, thay vì gọi trực tiếp. */
	public void setOrder(PurchaseOrder order) {
		this.order = order;
	}

	public Product getProduct() {
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
