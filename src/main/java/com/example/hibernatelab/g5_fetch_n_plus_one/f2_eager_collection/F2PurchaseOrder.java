package com.example.hibernatelab.g5_fetch_n_plus_one.f2_eager_collection;

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
 * Đơn hàng của biến thể F2.
 *
 * BIẾN THỂ : F2 (g5_f2_eager_collection)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: Customer.orders = EAGER (anti-pattern), phần còn lại như F1
 * HẬU QUẢ  : find(customer) luôn tải orders; findAll() tự sinh N+1 (G5-S13)
 */
// Tên bảng là purchase_order (không phải order) vì "order" là từ khoá SQL.
@Entity(name = "G5F2_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f2_eager_collection", name = "purchase_order")
public class F2PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f2_purchase_order_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f2_purchase_order_seq", schema = "g5_f2_eager_collection", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side — ghi FK purchase_order.customer_id.
	// fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private F2Customer customer;

	// INVERSE side — FK order_item.order_id do OrderItem.order quản lý.
	// fetch = LAZY ghi tường minh.
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
	private List<F2OrderItem> items = new ArrayList<>();

	protected F2PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public F2PurchaseOrder(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public F2Customer getCustomer() {
		return customer;
	}

	public List<F2OrderItem> getItems() {
		return items;
	}
}
