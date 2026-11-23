package com.example.hibernatelab.g4_update_child_via_parent.u1_no_merge;

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
 * Khách hàng của biến thể U1.
 *
 * BIẾN THỂ : U1 (g4_u1_no_merge)
 * BASELINE : U0 (g4_u0_baseline)
 * KHÁC BIỆT: items: cascade = PERSIST (bỏ MERGE; vẫn giữ orphanRemoval = true)
 * HẬU QUẢ  : merge(order DETACHED) không lan xuống item nên thay đổi của item bị MẤT (G4-S03)
 */
@Entity(name = "G4U1_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g4_u1_no_merge", name = "customer")
public class U1Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g4u1_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g4u1_customer_seq", schema = "g4_u1_no_merge", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy = "customer": FK purchase_order.customer_id do PurchaseOrder.customer quản lý.
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private List<U1PurchaseOrder> orders = new ArrayList<>();

	protected U1Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public U1Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<U1PurchaseOrder> getOrders() {
		return orders;
	}
}
