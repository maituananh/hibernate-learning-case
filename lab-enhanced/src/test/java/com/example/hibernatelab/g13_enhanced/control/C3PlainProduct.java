package com.example.hibernatelab.g13_enhanced.control;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : C3 (g13_c3_plain_dirty), entity ĐỐI CHỨNG nằm ở src/test nên KHÔNG bị bytecode enhancement
 * BASELINE : E3 (g13_e3_dirty_tracking) ở src/main của lab-enhanced
 * KHÁC BIỆT: không enhancement
 * HẬU QUẢ  : dirty checking so sánh từng field với snapshot lúc load (G13-S13)
 */
@Entity(name = "G13C3_PlainProduct")
@Table(schema = "g13_c3_plain_dirty", name = "product")
public class C3PlainProduct {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g13_c3_plain_dirty_seq")
	@SequenceGenerator(name = "g13_c3_plain_dirty_seq", schema = "g13_c3_plain_dirty", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "price", nullable = false)
	private int price;

	@Column(name = "description")
	private String description;

	protected C3PlainProduct() {
		// JPA bắt buộc có constructor không tham số
	}

	public C3PlainProduct(String name, int price, String description) {
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
