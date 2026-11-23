package com.example.hibernatelab.g4_update_child_via_parent.u2_owning_collection;

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
 * Khách hàng của biến thể U2.
 *
 * BIẾN THỂ : U2 (g4_u2_owning_collection)
 * BASELINE : U0 (g4_u0_baseline)
 * KHÁC BIỆT: items là @OneToMany MỘT CHIỀU + @JoinTable, nên collection thuộc phía OWNING (item không còn trỏ ngược order)
 * HẬU QUẢ  : thêm item vào collection là đổi state của cha: ghi bảng nối và có thể tăng version cha (G4-S06)
 */
@Entity(name = "G4U2_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g4_u2_owning_collection", name = "customer")
public class U2Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g4u2_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g4u2_customer_seq", schema = "g4_u2_owning_collection", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy = "customer": FK purchase_order.customer_id do PurchaseOrder.customer quản lý.
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private List<U2PurchaseOrder> orders = new ArrayList<>();

	protected U2Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public U2Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<U2PurchaseOrder> getOrders() {
		return orders;
	}
}
