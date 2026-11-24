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
 * Sản phẩm có giá, được cache cấp 2 (READ_WRITE).
 *
 * BIẾN THỂ : K1 (g13_k1_entity_cache)
 * BASELINE : K1 (entity cache READ_WRITE)
 * KHÁC BIỆT: không có (baseline)
 * HẬU QUẢ  : find lần hai ở transaction khác là cache hit (S06); bulk JPQL / native UPDATE làm vô hiệu cache (S07)
 */
@Entity(name = "G13K1_Product")
@Table(schema = "g13_k1_entity_cache", name = "product")
// Với shared-cache-mode ENABLE_SELECTIVE (xem src/test/resources/application.yml) phải có @Cacheable thì entity mới vào cache cấp 2.
// @Cache chọn chiến lược đồng bộ: READ_WRITE.
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class K1Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g13k1_product_seq")
	@SequenceGenerator(name = "g13k1_product_seq", schema = "g13_k1_entity_cache", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "price", nullable = false)
	private int price;

	protected K1Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public K1Product(String name, int price) {
		this.name = name;
		this.price = price;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getPrice() {
		return price;
	}

	public void setPrice(int price) {
		this.price = price;
	}
}
