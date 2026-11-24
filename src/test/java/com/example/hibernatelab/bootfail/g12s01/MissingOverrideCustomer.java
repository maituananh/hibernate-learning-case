package com.example.hibernatelab.bootfail.g12s01;

import com.example.hibernatelab.g12_advanced_model.e1_embedded.E1Address;

import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * [BOOT-FAIL] G12-S01: entity CỐ Ý SAI, nằm ở package {@code bootfail} nên entity scan không thấy.
 *
 * <p>Sai ở đâu: hai field cùng kiểu {@link E1Address} nhưng KHÔNG có {@code @AttributeOverride}, nên cả hai
 * cùng ánh xạ vào các cột {@code street}, {@code city}, {@code zip_code} → trùng cột.
 * Bản ĐÚNG xem {@code E1Customer}: mỗi field có override riêng ({@code billing_*}, {@code shipping_*}).
 */
@Entity(name = "BootFail_MissingOverrideCustomer")
@Table(schema = "g12_e1_embedded", name = "customer_bootfail")
public class MissingOverrideCustomer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Embedded
	private E1Address billingAddress; // ← không override

	@Embedded
	private E1Address shippingAddress; // ← không override, trùng cột với billingAddress

	protected MissingOverrideCustomer() {
		// JPA bắt buộc có constructor không tham số
	}
}
