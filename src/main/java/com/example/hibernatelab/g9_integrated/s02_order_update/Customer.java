package com.example.hibernatelab.g9_integrated.s02_order_update;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Khách hàng của G9-S02. Chỉ cần để đơn hàng có FK customer_id: cách B (merge) sẽ làm FK này thành NULL.
 *
 * BIẾN THỂ : G9-S02 (g9_s02_order_update)
 * BASELINE : G4-U0
 * KHÁC BIỆT: không có collection orders (không cần duyệt từ customer)
 * HẬU QUẢ  : entity gọn hơn; không ảnh hưởng tình huống
 */
@Entity(name = "G9S02_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s02_order_update", name = "customer")
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s02_customer_seq")
	@SequenceGenerator(name = "g9s02_customer_seq", schema = "g9_s02_order_update", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
