package com.example.hibernatelab.g4_update_child_via_parent.u0_baseline;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Khách hàng của biến thể U0.
 *
 * BIẾN THỂ : U0 (g4_u0_baseline)
 * BASELINE : U0 (g4_u0_baseline)
 * KHÁC BIỆT: hai chiều mappedBy, items: cascade = ALL, orphanRemoval = true (BASELINE)
 * HẬU QUẢ  : đây là mốc so sánh: sửa/thêm/xoá item qua cha KHÔNG đụng dòng purchase_order (G4-S01, S04, S05, S08)
 */
@Entity(name = "G4U0_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g4_u0_baseline", name = "customer")
public class U0Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g4u0_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g4u0_customer_seq", schema = "g4_u0_baseline", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy = "customer": FK purchase_order.customer_id do PurchaseOrder.customer quản lý.
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private List<U0PurchaseOrder> orders = new ArrayList<>();

	protected U0Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public U0Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<U0PurchaseOrder> getOrders() {
		return orders;
	}
}
