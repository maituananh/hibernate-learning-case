package com.example.hibernatelab.g10_query.q1_baseline;

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
import java.util.ArrayList;
import java.util.List;

/**
 * Đơn hàng của biến thể Q1. Có {@code status} (NEW/PAID) để G10-S02 lọc theo trạng thái.
 *
 * BIẾN THỂ : Q1 (g10_q1_baseline)
 * BASELINE : Q1 (g10_q1_baseline)
 * KHÁC BIỆT: không có (đây là baseline của G10): mọi quan hệ LAZY ghi tường minh, collection là List
 * HẬU QUẢ  : không tải gì thừa; G10 chỉ đổi CÁCH VIẾT QUERY trên cùng một bộ mapping, nên kết quả khác nhau
 *            đến từ câu query chứ không từ mapping (G10-S01..S10)
 */
// Tên bảng là purchase_order (không phải order) vì "order" là từ khoá SQL.
@Entity(name = "G10Q1_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g10_q1_baseline", name = "purchase_order")
public class Q1PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g10q1_purchase_order_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g10q1_purchase_order_seq", schema = "g10_q1_baseline", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// Trạng thái là String (không dùng enum) để câu JPQL o.status = 'PAID' đọc thẳng được; enum là chủ đề G12-S11.
	@Column(name = "status", nullable = false)
	private String status;

	// OWNING side — ghi FK purchase_order.customer_id.
	// fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private Q1Customer customer;

	// INVERSE side — FK order_item.order_id do OrderItem.order quản lý.
	// fetch = LAZY ghi tường minh.
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
	private List<Q1OrderItem> items = new ArrayList<>();

	protected Q1PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public Q1PurchaseOrder(String orderNo, String status) {
		this.orderNo = orderNo;
		this.status = status;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public String getStatus() {
		return status;
	}

	public Q1Customer getCustomer() {
		return customer;
	}

	public List<Q1OrderItem> getItems() {
		return items;
	}
}
