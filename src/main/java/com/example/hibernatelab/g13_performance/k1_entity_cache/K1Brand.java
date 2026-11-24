package com.example.hibernatelab.g13_performance.k1_entity_cache;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Thương hiệu, entity cache thứ hai để so sánh phạm vi evict trong S07.
 *
 * BIẾN THỂ : K1 (g13_k1_entity_cache)
 * BASELINE : K1 (entity cache READ_WRITE)
 * KHÁC BIỆT: entity cache thứ hai, cùng schema
 * HẬU QUẢ  : dùng làm "nạn nhân vô can" để S07 thấy UPDATE bảng product có xoá luôn cache của brand hay không
 */
@Entity(name = "G13K1_Brand")
@Table(schema = "g13_k1_entity_cache", name = "brand")
// Với shared-cache-mode ENABLE_SELECTIVE (xem src/test/resources/application.yml) phải có @Cacheable thì entity mới vào cache cấp 2.
// @Cache chọn chiến lược đồng bộ: READ_WRITE.
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class K1Brand {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g13k1_brand_seq")
	@SequenceGenerator(name = "g13k1_brand_seq", schema = "g13_k1_entity_cache", sequenceName = "brand_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected K1Brand() {
		// JPA bắt buộc có constructor không tham số
	}

	public K1Brand(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
