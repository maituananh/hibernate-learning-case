package com.example.hibernatelab.g4_update_child_via_parent.u5_child_version;

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
import jakarta.persistence.Version;

/**
 * Dòng hàng (con) của biến thể U5.
 *
 * BIẾN THỂ : U5 (g4_u5_child_version)
 * BASELINE : U0 (g4_u0_baseline)
 * KHÁC BIỆT: OrderItem có @Version riêng
 * HẬU QUẢ  : hai bản DETACHED của cùng một item cập nhật đồng thời: bản sau bị OptimisticLock (G4-S11)
 */
@Entity(name = "G4U5_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g4_u5_child_version", name = "order_item")
public class U5OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g4u5_order_item_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g4u5_order_item_seq", schema = "g4_u5_child_version", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// KHÁC BIỆT CỦA U5: item có @Version riêng → hai bản DETACHED cùng sửa một item thì bản sau bị từ chối (G4-S11).
	@Version
	@Column(name = "version")
	private Long version;

	// OWNING side — FK order_item.order_id nằm ở bảng này. fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private U5PurchaseOrder order;

	// OWNING side — FK order_item.product_id nằm ở bảng này. Không cascade (product là dữ liệu master dùng chung).
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private U5Product product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	@Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;

	protected U5OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	/** Chưa gán order: gọi {@code order.addItem(item)} để gắn vào cha. */
	public U5OrderItem(U5Product product, int quantity, BigDecimal unitPrice) {
		this.product = product;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
	}

	public Long getId() {
		return id;
	}

	public Long getVersion() {
		return version;
	}

	public U5PurchaseOrder getOrder() {
		return order;
	}

	void setOrder(U5PurchaseOrder order) {
		this.order = order;
	}

	public U5Product getProduct() {
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
