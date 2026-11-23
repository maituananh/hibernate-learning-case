package com.example.hibernatelab.g5_fetch_n_plus_one.f4_subselect;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

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
 * Đơn hàng của biến thể F4.
 *
 * BIẾN THỂ : F4 (g5_f4_subselect)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: như F1 + @Fetch(FetchMode.SUBSELECT) trên Customer.orders
 * HẬU QUẢ  : N+1 gom thành 2 SELECT, câu thứ hai lặp lại query gốc dưới dạng subquery (G5-S09)
 */
// Tên bảng là purchase_order (không phải order) vì "order" là từ khoá SQL.
@Entity(name = "G5F4_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f4_subselect", name = "purchase_order")
public class F4PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f4_purchase_order_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f4_purchase_order_seq", schema = "g5_f4_subselect", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side — ghi FK purchase_order.customer_id.
	// fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private F4Customer customer;

	// INVERSE side — FK order_item.order_id do OrderItem.order quản lý.
	// fetch = LAZY ghi tường minh.
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
	private List<F4OrderItem> items = new ArrayList<>();

	protected F4PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public F4PurchaseOrder(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public F4Customer getCustomer() {
		return customer;
	}

	public List<F4OrderItem> getItems() {
		return items;
	}
}
