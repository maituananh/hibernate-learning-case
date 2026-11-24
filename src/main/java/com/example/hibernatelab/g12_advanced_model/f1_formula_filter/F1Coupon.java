package com.example.hibernatelab.g12_advanced_model.f1_formula_filter;

import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Mã giảm giá với {@code @SQLRestriction} đặt trên ENTITY: mọi SELECT từ bảng coupon do Hibernate sinh ra đều thêm
 * {@code where active = true}. Khác {@code @Filter}: luôn bật, không tắt được trong session.
 */
@Entity(name = "G12F1_Coupon")
@Table(schema = "g12_f1_formula_filter", name = "coupon")
@SQLRestriction("active = true")
public class F1Coupon {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12f1_coupon_seq")
	@SequenceGenerator(name = "g12f1_coupon_seq", schema = "g12_f1_formula_filter", sequenceName = "coupon_seq", allocationSize = 50)
	private Long id;

	@Column(name = "code", nullable = false)
	private String code;

	@Column(name = "active", nullable = false)
	private boolean active;

	protected F1Coupon() {
		// JPA bắt buộc có constructor không tham số
	}

	public F1Coupon(String code, boolean active) {
		this.code = code;
		this.active = active;
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}
}
