package com.example.hibernatelab.g14_lead.i1_fixed;

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
 * Dòng hàng của một đơn. Dùng cho sự cố G14-I1.
 *
 * BIẾN THỂ : I1 (fixed), schema g14_i1_fixed
 * BASELINE : không có (G14 được miễn quy tắc một khác biệt, E7)
 * KHÁC BIỆT: giống hệt package còn lại của I1
 * HẬU QUẢ  : không có riêng; lỗi N+1 nằm ở service đọc danh sách
 */
@Entity(name = "G14I1F_OrderItem")
@Table(schema = "g14_i1_fixed", name = "order_item")
public class I1FixedOrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g14i1fixed_item_seq")
	@SequenceGenerator(name = "g14i1fixed_item_seq", schema = "g14_i1_fixed", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	@Column(name = "product_name", nullable = false)
	private String productName;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	// OWNING side: cột FK order_id nằm ở bảng order_item. LAZY tường minh (@ManyToOne mặc định là EAGER).
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private I1FixedOrder order;

	protected I1FixedOrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public I1FixedOrderItem(I1FixedOrder order, String productName, int quantity) {
		this.order = order;
		this.productName = productName;
		this.quantity = quantity;
	}

	public Long getId() {
		return id;
	}

	public String getProductName() {
		return productName;
	}

	public int getQuantity() {
		return quantity;
	}
}
