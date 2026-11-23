package com.example.hibernatelab.g2_cascade.c6_remove_on_m2o;

import java.util.ArrayList;
import java.util.List;

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
 * Đơn hàng của biến thể C6. Xem header ở C6Customer để biết biến thể này khác baseline điểm nào.
 * Tên bảng là {@code purchase_order} (không phải {@code order}) vì {@code order} là từ khoá SQL.
 */
@Entity(name = "G2C6_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g2_c6_remove_on_m2o", name = "purchase_order")
public class C6PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g2c6_purchase_order_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g2c6_purchase_order_seq", schema = "g2_c6_remove_on_m2o", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side — ghi FK purchase_order.customer_id. fetch = LAZY ghi tường minh để đọc order không kéo theo customer.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private C6Customer customer;

	// INVERSE side — FK order_item.order_id do OrderItem.order quản lý.
	// KHÔNG có cascade ở đây (đúng với baseline C0; ở C6/C7/C8 khác biệt nằm ở OrderItem).
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
	private List<C6OrderItem> items = new ArrayList<>();

	protected C6PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public C6PurchaseOrder(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public C6Customer getCustomer() {
		return customer;
	}

	public void setCustomer(C6Customer customer) {
		this.customer = customer;
	}

	public List<C6OrderItem> getItems() {
		return items;
	}

	/** Helper đồng bộ HAI phía: add vào collection (inverse) và gán item.order (owning, thứ ghi FK). */
	public void addItem(C6OrderItem item) {
		items.add(item);
		item.setOrder(this);
	}

	/** Gỡ cả hai phía. Không có orphanRemoval nên dòng item KHÔNG bị xoá, chỉ order_id thành NULL (xem G3). */
	public void removeItem(C6OrderItem item) {
		items.remove(item);
		item.setOrder(null);
	}
}
