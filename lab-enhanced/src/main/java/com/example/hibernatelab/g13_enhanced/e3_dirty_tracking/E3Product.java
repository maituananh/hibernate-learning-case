package com.example.hibernatelab.g13_enhanced.e3_dirty_tracking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : E3 (g13_e3_dirty_tracking), ĐÃ BỊ bytecode enhancement (enableDirtyTracking = true)
 * BASELINE : C3 (g13_c3_plain_dirty, src/test/.../control): mapping y hệt, KHÔNG enhancement
 * KHÁC BIỆT: chỉ khác ở enhancement
 * HẬU QUẢ  : xem G13-S13 (câu UPDATE có chỉ chứa cột đổi hay không)
 */
@Entity(name = "G13E3_Product")
@Table(schema = "g13_e3_dirty_tracking", name = "product")
public class E3Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g13_e3_dirty_tracking_seq")
	@SequenceGenerator(name = "g13_e3_dirty_tracking_seq", schema = "g13_e3_dirty_tracking", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "price", nullable = false)
	private int price;

	@Column(name = "description")
	private String description;

	protected E3Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public E3Product(String name, int price, String description) {
		this.name = name;
		this.price = price;
		this.description = description;
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

	public int getPrice() {
		return price;
	}

	public void setPrice(int price) {
		this.price = price;
	}

	public String getDescription() {
		return description;
	}
}
