package com.example.hibernatelab.g10_query.q1_baseline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * Khách hàng của biến thể Q1.
 *
 * BIẾN THỂ : Q1 (g10_q1_baseline)
 * BASELINE : Q1 (g10_q1_baseline)
 * KHÁC BIỆT: không có (đây là baseline của G10): mọi quan hệ LAZY ghi tường minh, collection là List
 * HẬU QUẢ  : không tải gì thừa; G10 chỉ đổi CÁCH VIẾT QUERY trên cùng một bộ mapping, nên kết quả khác nhau
 *            đến từ câu query chứ không từ mapping (G10-S01..S10)
 */
@Entity(name = "G10Q1_Customer") // tên entity phải duy nhất toàn persistence unit; JPQL viết theo tên này
@Table(schema = "g10_q1_baseline", name = "customer")
public class Q1Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g10q1_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g10q1_customer_seq", schema = "g10_q1_baseline", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy = "customer": FK purchase_order.customer_id do PurchaseOrder.customer quản lý.
	// fetch = LAZY ghi tường minh: đọc customer thì CHƯA tải orders (G10-S01 chứng minh join/join fetch đổi điều này).
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private List<Q1PurchaseOrder> orders = new ArrayList<>();

	protected Q1Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public Q1Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	/** Đổi tên: dùng ở G10-S04b để chứng minh dirty checking chạy trên entity lấy từ native query. */
	public void rename(String newName) {
		this.name = newName;
	}

	public String getName() {
		return name;
	}

	public List<Q1PurchaseOrder> getOrders() {
		return orders;
	}
}
