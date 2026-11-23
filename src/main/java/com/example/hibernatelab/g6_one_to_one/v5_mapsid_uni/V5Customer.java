package com.example.hibernatelab.g6_one_to_one.v5_mapsid_uni;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : V5 (g6_v5_mapsid_uni)
 * BASELINE : V2 (g6_v2_fk_on_card)
 * KHÁC BIỆT: Customer không có property nào về profile (một chiều từ phía profile)
 * HẬU QUẢ  : muốn lấy profile thì find(CustomerProfile.class, customerId) (G6-S06, S08)
 */
@Entity(name = "G6V5_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g6_v5_mapsid_uni", name = "customer")
public class V5Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g6v5_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g6v5_customer_seq", schema = "g6_v5_mapsid_uni", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected V5Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public V5Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
