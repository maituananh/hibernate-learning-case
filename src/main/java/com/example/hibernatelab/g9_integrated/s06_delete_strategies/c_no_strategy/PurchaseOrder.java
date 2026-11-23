package com.example.hibernatelab.g9_integrated.s06_delete_strategies.c_no_strategy;

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
 * Đơn hàng (cha) ở giữa chuỗi Customer → PurchaseOrder → OrderItem.
 *
 * BIẾN THỂ : G9-S06C (g9_s06c_no_strategy)
 * BASELINE : G9-S06c (không chiến lược nào)
 * KHÁC BIỆT: không cascade JPA, không @OnDelete: baseline trần
 * HẬU QUẢ  : xoá customer còn order → vi phạm FK (S06d); muốn xoá phải bulk delete từ dưới lên (S06c)
 */
@Entity(name = "G9S06C_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s06c_no_strategy", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s06c_purchase_order_seq")
	@SequenceGenerator(name = "g9s06c_purchase_order_seq", schema = "g9_s06c_no_strategy", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side — FK purchase_order.customer_id.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private Customer customer;

	// INVERSE side — mappedBy = "order".
	// KHÔNG cascade.
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
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

	public Customer getCustomer() {
		return customer;
	}

	public void setCustomer(Customer customer) {
		this.customer = customer;
	}

	public List<OrderItem> getItems() {
		return items;
	}

	public void addItem(OrderItem item) {
		items.add(item);
		item.setOrder(this);
	}
}
