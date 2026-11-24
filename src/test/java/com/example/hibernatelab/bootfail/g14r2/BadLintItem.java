package com.example.hibernatelab.bootfail.g14r2;

import jakarta.persistence.*;


/**
 * [BAD-MAPPING] G14-R2: entity CỐ Ý vi phạm quy tắc mapping của team, nằm ở package {@code bootfail} nên entity scan không thấy.
 * Khác {@code [BOOT-FAIL]}: mapping này VẪN khởi động được, chỉ là sai quy tắc; lint {@code MappingLint} phải bắt được.
 */
@Entity(name = "G14R2_BadLintItem")
@Table(name = "g14r2_item")
public class BadLintItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY) // ← vi phạm IDENTITY-ID
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	private BadLintOrder order;

	protected BadLintItem() {
		// JPA bắt buộc có constructor không tham số
	}
}
