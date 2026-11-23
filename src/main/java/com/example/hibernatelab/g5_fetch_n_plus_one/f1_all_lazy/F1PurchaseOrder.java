package com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy;

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
 * Đơn hàng của biến thể F1.
 *
 * BIẾN THỂ : F1 (g5_f1_all_lazy)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: mọi quan hệ LAZY ghi tường minh, collection là List (BASELINE, khuyến nghị)
 * HẬU QUẢ  : không tải gì thừa; truy cập quan hệ nào mới tốn SQL đó; ngoài transaction thì LazyInitializationException (G5-S03..S07, S10, S12, S14..S16)
 */
// Tên bảng là purchase_order (không phải order) vì "order" là từ khoá SQL.
@Entity(name = "G5F1_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f1_all_lazy", name = "purchase_order")
public class F1PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f1_purchase_order_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f1_purchase_order_seq", schema = "g5_f1_all_lazy", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side — ghi FK purchase_order.customer_id.
	// fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private F1Customer customer;

	// INVERSE side — FK order_item.order_id do OrderItem.order quản lý.
	// fetch = LAZY ghi tường minh.
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
	private List<F1OrderItem> items = new ArrayList<>();

	protected F1PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public F1PurchaseOrder(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public F1Customer getCustomer() {
		return customer;
	}

	public List<F1OrderItem> getItems() {
		return items;
	}
}
