package com.example.hibernatelab.g5_fetch_n_plus_one.f3_batch_size;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

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
 * Đơn hàng của biến thể F3.
 *
 * BIẾN THỂ : F3 (g5_f3_batch_size)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: như F1 + @BatchSize(size = 10) trên collection và trên entity
 * HẬU QUẢ  : N+1 gom thành 1 + ceil(5/10) = 2 SELECT (G5-S08)
 */
// Tên bảng là purchase_order (không phải order) vì "order" là từ khoá SQL.
@Entity(name = "G5F3_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f3_batch_size", name = "purchase_order")
@BatchSize(size = 10) // gom việc tải PROXY của entity này theo lô 10
public class F3PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f3_purchase_order_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f3_purchase_order_seq", schema = "g5_f3_batch_size", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side — ghi FK purchase_order.customer_id.
	// fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private F3Customer customer;

	// INVERSE side — FK order_item.order_id do OrderItem.order quản lý.
	// fetch = LAZY ghi tường minh.
	@BatchSize(size = 10) // gom việc tải COLLECTION này theo lô 10 owner
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
	private List<F3OrderItem> items = new ArrayList<>();

	protected F3PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public F3PurchaseOrder(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public F3Customer getCustomer() {
		return customer;
	}

	public List<F3OrderItem> getItems() {
		return items;
	}
}
