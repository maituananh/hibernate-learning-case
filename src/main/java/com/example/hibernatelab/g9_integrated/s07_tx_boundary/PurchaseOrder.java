package com.example.hibernatelab.g9_integrated.s07_tx_boundary;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Đơn hàng của G9-S07. Collection items là LAZY: chỉ nạp được khi persistence context (tức transaction) còn mở.
 *
 * BIẾN THỂ : G9-S07 (g9_s07_tx_boundary)
 * BASELINE : G5-F0 (LAZY tường minh)
 * KHÁC BIỆT: không có; khác biệt nằm ở service (ranh giới transaction)
 * HẬU QUẢ  : chạm items ngoài transaction → LazyInitializationException
 */
@Entity(name = "G9S07_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s07_tx_boundary", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s07_purchase_order_seq")
	@SequenceGenerator(name = "g9s07_purchase_order_seq", schema = "g9_s07_tx_boundary", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// INVERSE side — mappedBy = "order". LAZY ghi tường minh (cũng là mặc định của @OneToMany).
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderItem> items = new ArrayList<>();

	protected PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public PurchaseOrder(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public List<OrderItem> getItems() {
		return items;
	}

	public void addItem(OrderItem item) {
		items.add(item);
		item.setOrder(this);
	}
}
