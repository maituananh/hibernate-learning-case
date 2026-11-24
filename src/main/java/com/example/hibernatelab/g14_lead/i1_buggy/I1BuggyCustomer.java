package com.example.hibernatelab.g14_lead.i1_buggy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Khách hàng. Dùng cho sự cố G14-I1 ("trang danh sách đơn hàng chậm dần").
 *
 * BIẾN THỂ : I1 (buggy), schema g14_i1_buggy
 * BASELINE : không có (G14 được miễn quy tắc một khác biệt, E7); hai package I1 giống hệt nhau về entity
 * KHÁC BIỆT: chỉ khác ở repository và service (cách đọc danh sách đơn hàng), xem I1BuggyOrderListService
 * HẬU QUẢ  : xem I1BuggyOrderListService
 */
@Entity(name = "G14I1B_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g14_i1_buggy", name = "customer")
public class I1BuggyCustomer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g14i1buggy_customer_seq")
	@SequenceGenerator(name = "g14i1buggy_customer_seq", schema = "g14_i1_buggy", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected I1BuggyCustomer() {
		// JPA bắt buộc có constructor không tham số
	}

	public I1BuggyCustomer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
