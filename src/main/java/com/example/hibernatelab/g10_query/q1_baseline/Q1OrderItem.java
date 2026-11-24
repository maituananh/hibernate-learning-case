package com.example.hibernatelab.g10_query.q1_baseline;

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
 * Dòng hàng của biến thể Q1.
 *
 * BIẾN THỂ : Q1 (g10_q1_baseline)
 * BASELINE : Q1 (g10_q1_baseline)
 * KHÁC BIỆT: không có (đây là baseline của G10): mọi quan hệ LAZY ghi tường minh, collection là List
 * HẬU QUẢ  : không tải gì thừa; G10 chỉ đổi CÁCH VIẾT QUERY trên cùng một bộ mapping, nên kết quả khác nhau
 *            đến từ câu query chứ không từ mapping (G10-S01..S10)
 */
@Entity(name = "G10Q1_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g10_q1_baseline", name = "order_item")
public class Q1OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g10q1_order_item_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g10q1_order_item_seq", schema = "g10_q1_baseline", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK order_item.order_id nằm ở bảng này.
	// fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private Q1PurchaseOrder order;

	// OWNING side — FK order_item.product_id nằm ở bảng này.
	// fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private Q1Product product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected Q1OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public Long getId() {
		return id;
	}

	public Q1PurchaseOrder getOrder() {
		return order;
	}

	public Q1Product getProduct() {
		return product;
	}

	public int getQuantity() {
		return quantity;
	}
}
