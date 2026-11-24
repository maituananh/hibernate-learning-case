package com.example.hibernatelab.g12_advanced_model.n1_natural_id;

import org.hibernate.annotations.NaturalId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm có mã SKU là khoá tự nhiên (business key), bên cạnh khoá thay thế {@code id}.
 *
 * BIẾN THỂ : N1 (g12_n1_natural_id)
 * BASELINE : chính nó (baseline của chủ đề natural id)
 * KHÁC BIỆT: không có (đây là baseline)
 * HẬU QUẢ  : DDL có unique constraint trên sku; tra theo SKU qua {@code session.byNaturalId(...)} được cache theo
 *            natural id trong persistence context (G12-S10)
 */
@Entity(name = "G12N1_Product")
@Table(schema = "g12_n1_natural_id", name = "product")
public class N1Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12n1_product_seq")
	@SequenceGenerator(name = "g12n1_product_seq", schema = "g12_n1_natural_id", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	// @NaturalId = giá trị nghiệp vụ định danh duy nhất. Hibernate sinh UNIQUE constraint và mặc định coi nó BẤT BIẾN:
	// đổi sau khi đã lưu sẽ bị từ chối (mutable = false là mặc định).
	@NaturalId
	@Column(name = "sku", nullable = false)
	private String sku;

	@Column(name = "name", nullable = false)
	private String name;

	protected N1Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public N1Product(String sku, String name) {
		this.sku = sku;
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getSku() {
		return sku;
	}

	public String getName() {
		return name;
	}

	/** Cố ý cho phép đổi SKU để G12-S10c chứng minh natural id bất biến bị Hibernate chặn lúc flush. */
	public void changeSku(String newSku) {
		this.sku = newSku;
	}
}
