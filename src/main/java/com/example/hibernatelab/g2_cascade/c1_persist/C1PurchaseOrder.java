package com.example.hibernatelab.g2_cascade.c1_persist;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Đơn hàng của biến thể C1. Xem header ở C1Customer để biết biến thể này khác baseline điểm nào.
 * Tên bảng là {@code purchase_order} (không phải {@code order}) vì {@code order} là từ khoá SQL.
 */
@Entity(name = "G2C1_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g2_c1_persist", name = "purchase_order")
public class C1PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g2c1_purchase_order_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g2c1_purchase_order_seq", schema = "g2_c1_persist", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side — ghi FK purchase_order.customer_id. fetch = LAZY ghi tường minh để đọc order không kéo theo customer.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private C1Customer customer;

	// INVERSE side — FK order_item.order_id do OrderItem.order quản lý.
	// KHÁC BIỆT CỦA BIẾN THỂ NẰM Ở ĐÂY: cascade = CascadeType.PERSIST.
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
	private List<C1OrderItem> items = new ArrayList<>();

	protected C1PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public C1PurchaseOrder(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public C1Customer getCustomer() {
		return customer;
	}

	public void setCustomer(C1Customer customer) {
		this.customer = customer;
	}

	public List<C1OrderItem> getItems() {
		return items;
	}

	/** Helper đồng bộ HAI phía: add vào collection (inverse) và gán item.order (owning, thứ ghi FK). */
	public void addItem(C1OrderItem item) {
		items.add(item);
		item.setOrder(this);
	}

	/** Gỡ cả hai phía. Không có orphanRemoval nên dòng item KHÔNG bị xoá, chỉ order_id thành NULL (xem G3). */
	public void removeItem(C1OrderItem item) {
		items.remove(item);
		item.setOrder(null);
	}
}
