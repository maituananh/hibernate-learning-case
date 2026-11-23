package com.example.hibernatelab.g5_fetch_n_plus_one.f1s_all_lazy_set;

import java.util.LinkedHashSet;
import java.util.Set;

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
 * Đơn hàng của biến thể F1S.
 *
 * BIẾN THỂ : F1S (g5_f1s_all_lazy_set)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: như F1 nhưng collection là Set thay vì List
 * HẬU QUẢ  : Set không phải 'bag' nên fetch 2 collection cùng lúc không bị MultipleBagFetchException, nhưng sinh tích Descartes (G5-S11)
 */
// Tên bảng là purchase_order (không phải order) vì "order" là từ khoá SQL.
@Entity(name = "G5F1S_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f1s_all_lazy_set", name = "purchase_order")
public class F1SPurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f1s_purchase_order_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f1s_purchase_order_seq", schema = "g5_f1s_all_lazy_set", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side — ghi FK purchase_order.customer_id.
	// fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private F1SCustomer customer;

	// INVERSE side — FK order_item.order_id do OrderItem.order quản lý.
	// fetch = LAZY ghi tường minh.
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
	private Set<F1SOrderItem> items = new LinkedHashSet<>();

	protected F1SPurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public F1SPurchaseOrder(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public F1SCustomer getCustomer() {
		return customer;
	}

	public Set<F1SOrderItem> getItems() {
		return items;
	}
}
