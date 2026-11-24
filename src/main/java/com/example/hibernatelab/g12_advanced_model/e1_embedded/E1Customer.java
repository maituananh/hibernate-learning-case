package com.example.hibernatelab.g12_advanced_model.e1_embedded;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Khách hàng có hai địa chỉ (giao hàng, thanh toán) dùng chung một {@code @Embeddable}.
 *
 * BIẾN THỂ : E1 (g12_e1_embedded)
 * BASELINE : chính nó (baseline của chủ đề embeddable)
 * KHÁC BIỆT: không có (đây là baseline)
 * HẬU QUẢ  : bảng customer có 6 cột địa chỉ phẳng (billing_*, shipping_*); không có bảng address (G12-S01..S03)
 */
@Entity(name = "G12E1_Customer") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g12_e1_embedded", name = "customer")
public class E1Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12e1_customer_seq")
	@SequenceGenerator(name = "g12e1_customer_seq", schema = "g12_e1_embedded", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// Cùng kiểu E1Address dùng HAI lần → BẮT BUỘC @AttributeOverride để đổi tên cột, tránh trùng cột.
	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "street", column = @Column(name = "billing_street")),
		@AttributeOverride(name = "city", column = @Column(name = "billing_city")),
		@AttributeOverride(name = "zipCode", column = @Column(name = "billing_zip_code"))
	})
	private E1Address billingAddress;

	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "street", column = @Column(name = "shipping_street")),
		@AttributeOverride(name = "city", column = @Column(name = "shipping_city")),
		@AttributeOverride(name = "zipCode", column = @Column(name = "shipping_zip_code"))
	})
	private E1Address shippingAddress;

	protected E1Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public E1Customer(String name, E1Address billingAddress, E1Address shippingAddress) {
		this.name = name;
		this.billingAddress = billingAddress;
		this.shippingAddress = shippingAddress;
	}

	public Long getId() {
		return id;
	}

	public E1Address getBillingAddress() {
		return billingAddress;
	}

	public E1Address getShippingAddress() {
		return shippingAddress;
	}
}
