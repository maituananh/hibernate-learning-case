package com.example.hibernatelab.g4_update_child_via_parent.u4_dynamic_update;

import java.math.BigDecimal;

import org.hibernate.annotations.DynamicUpdate;

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
 * Dòng hàng (con) của biến thể U4.
 *
 * BIẾN THỂ : U4 (g4_u4_dynamic_update)
 * BASELINE : U0 (g4_u0_baseline)
 * KHÁC BIỆT: OrderItem có @DynamicUpdate
 * HẬU QUẢ  : UPDATE order_item chỉ liệt kê cột thực sự đổi thay vì tất cả cột (G4-S10)
 */
@Entity(name = "G4U4_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g4_u4_dynamic_update", name = "order_item")
// KHÁC BIỆT CỦA U4: @DynamicUpdate → UPDATE chỉ liệt kê cột thật sự đổi (mặc định Hibernate liệt kê TẤT CẢ cột).
@DynamicUpdate
public class U4OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g4u4_order_item_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g4u4_order_item_seq", schema = "g4_u4_dynamic_update", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK order_item.order_id nằm ở bảng này. fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private U4PurchaseOrder order;

	// OWNING side — FK order_item.product_id nằm ở bảng này. Không cascade (product là dữ liệu master dùng chung).
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private U4Product product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	@Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;

	protected U4OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	/** Chưa gán order: gọi {@code order.addItem(item)} để gắn vào cha. */
	public U4OrderItem(U4Product product, int quantity, BigDecimal unitPrice) {
		this.product = product;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
	}

	public Long getId() {
		return id;
	}

	public U4PurchaseOrder getOrder() {
		return order;
	}

	void setOrder(U4PurchaseOrder order) {
		this.order = order;
	}

	public U4Product getProduct() {
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
