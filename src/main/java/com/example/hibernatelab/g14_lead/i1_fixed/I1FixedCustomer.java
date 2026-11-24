package com.example.hibernatelab.g14_lead.i1_fixed;

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
 * BIẾN THỂ : I1 (fixed), schema g14_i1_fixed
 * BASELINE : không có (G14 được miễn quy tắc một khác biệt, E7); hai package I1 giống hệt nhau về entity
 * KHÁC BIỆT: chỉ khác ở repository và service (cách đọc danh sách đơn hàng), xem I1FixedOrderListService
 * HẬU QUẢ  : xem I1FixedOrderListService
 */
@Entity(name = "G14I1F_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g14_i1_fixed", name = "customer")
public class I1FixedCustomer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g14i1fixed_customer_seq")
	@SequenceGenerator(name = "g14i1fixed_customer_seq", schema = "g14_i1_fixed", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected I1FixedCustomer() {
		// JPA bắt buộc có constructor không tham số
	}

	public I1FixedCustomer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
