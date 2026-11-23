package com.example.hibernatelab.g0_lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Khách hàng của nhóm G0 (vòng đời và persistence context). Chưa có quan hệ nào: G0 chỉ học
 * persist / flush / commit / merge / detach trên MỘT entity đơn giản.
 *
 * <p>ID dùng SEQUENCE (D10): Hibernate xin id từ sequence ngay lúc {@code persist()} nhưng HOÃN câu INSERT
 * tới lúc flush. Đây là nền của G0-S01.
 */
@Entity(name = "G0_Customer") // tên entity phải duy nhất toàn persistence unit (JPQL viết theo tên này)
@Table(schema = "g0_lifecycle", name = "customer")
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g0_customer_seq")
	// Sequence nằm TRONG schema g0_lifecycle (không có schema = ... thì Hibernate tạo nó ở schema mặc định "public").
	@SequenceGenerator(name = "g0_customer_seq", schema = "g0_lifecycle", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "email")
	private String email;

	protected Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public Customer(String name, String email) {
		this.name = name;
		this.email = email;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}
}
