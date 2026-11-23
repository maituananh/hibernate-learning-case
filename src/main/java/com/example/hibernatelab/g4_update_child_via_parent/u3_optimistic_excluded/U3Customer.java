package com.example.hibernatelab.g4_update_child_via_parent.u3_optimistic_excluded;

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
 * Khách hàng của biến thể U3.
 *
 * BIẾN THỂ : U3 (g4_u3_optimistic_excluded)
 * BASELINE : U0 (g4_u0_baseline)
 * KHÁC BIỆT: như U2 + @OptimisticLock(excluded = true) trên items
 * HẬU QUẢ  : thay đổi collection items không còn làm tăng version của cha (G4-S07)
 */
@Entity(name = "G4U3_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g4_u3_optimistic_excluded", name = "customer")
public class U3Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g4u3_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g4u3_customer_seq", schema = "g4_u3_optimistic_excluded", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — mappedBy = "customer": FK purchase_order.customer_id do PurchaseOrder.customer quản lý.
	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	private List<U3PurchaseOrder> orders = new ArrayList<>();

	protected U3Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public U3Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<U3PurchaseOrder> getOrders() {
		return orders;
	}
}
