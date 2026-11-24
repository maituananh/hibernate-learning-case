package com.example.hibernatelab.g13_performance.k4_read_only;

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
 * Tỷ giá, cache READ_ONLY (cho dữ liệu tham chiếu "không bao giờ đổi").
 *
 * BIẾN THỂ : K4 (g13_k4_read_only)
 * BASELINE : K1 (entity cache READ_WRITE)
 * KHÁC BIỆT: chiến lược cache READ_ONLY thay vì READ_WRITE
 * HẬU QUẢ  : sửa entity đã nằm trong cache READ_ONLY là lỗi (S10)
 */
@Entity(name = "G13K4_Rate")
@Table(schema = "g13_k4_read_only", name = "rate")
// Với shared-cache-mode ENABLE_SELECTIVE (xem src/test/resources/application.yml) phải có @Cacheable thì entity mới vào cache cấp 2.
// @Cache chọn chiến lược đồng bộ: READ_ONLY.
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
public class K4Rate {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g13k4_rate_seq")
	@SequenceGenerator(name = "g13k4_rate_seq", schema = "g13_k4_read_only", sequenceName = "rate_seq", allocationSize = 50)
	private Long id;

	@Column(name = "currency", nullable = false)
	private String currency;

	@Column(name = "price", nullable = false)
	private int price;

	protected K4Rate() {
		// JPA bắt buộc có constructor không tham số
	}

	public K4Rate(String currency, int price) {
		this.currency = currency;
		this.price = price;
	}

	public Long getId() {
		return id;
	}

	public String getCurrency() {
		return currency;
	}

	public int getPrice() {
		return price;
	}

	public void setPrice(int price) {
		this.price = price;
	}
}
