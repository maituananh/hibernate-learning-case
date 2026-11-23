package com.example.hibernatelab.g2_cascade.c8_ondelete;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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
 * Dòng hàng của biến thể C8. Xem header ở C8Customer để biết biến thể này khác baseline điểm nào.
 */
@Entity(name = "G2C8_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g2_c8_ondelete", name = "order_item")
public class C8OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g2c8_order_item_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g2c8_order_item_seq", schema = "g2_c8_ondelete", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK order_item.order_id nằm ở bảng này.
	// KHÁC BIỆT CỦA BIẾN THỂ C8: @OnDelete sinh FK có `ON DELETE CASCADE` trong DDL. Khi xoá order, chính
	// PostgreSQL xoá item; Hibernate không biết gì về việc đó (khác cascade JPA, vốn chạy trong Java).
	@ManyToOne(fetch = FetchType.LAZY)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(name = "order_id")
	private C8PurchaseOrder order;

	// OWNING side — FK order_item.product_id nằm ở bảng này. Không cascade (product là dữ liệu master dùng chung).
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private C8Product product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected C8OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	/** Chưa gán order: gọi {@code order.addItem(item)} để gán cả hai phía. */
	public C8OrderItem(C8Product product, int quantity) {
		this.product = product;
		this.quantity = quantity;
	}

	public Long getId() {
		return id;
	}

	public C8PurchaseOrder getOrder() {
		return order;
	}

	void setOrder(C8PurchaseOrder order) {
		this.order = order;
	}

	public C8Product getProduct() {
		return product;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}
}
