package com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete;

import java.util.ArrayList;
import java.util.List;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Đơn hàng (cha) ở giữa chuỗi Customer → PurchaseOrder → OrderItem.
 *
 * BIẾN THỂ : G9-S06B (g9_s06b_on_delete)
 * BASELINE : G9-S06c (không chiến lược nào)
 * KHÁC BIỆT: @OnDelete(CASCADE) trên PurchaseOrder.customer và OrderItem.order; KHÔNG cascade JPA
 * HẬU QUẢ  : chỉ 1 DELETE customer, PostgreSQL xoá dây chuyền; Hibernate không biết con đã mất (G9-S06b)
 */
@Entity(name = "G9S06B_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s06b_on_delete", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s06b_purchase_order_seq")
	@SequenceGenerator(name = "g9s06b_purchase_order_seq", schema = "g9_s06b_on_delete", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side — FK purchase_order.customer_id.
	@OnDelete(action = OnDeleteAction.CASCADE) // FK sinh ra có "on delete cascade": chính PostgreSQL xoá con khi cha bị xoá
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
