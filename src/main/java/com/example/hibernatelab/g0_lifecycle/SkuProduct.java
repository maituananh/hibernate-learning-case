package com.example.hibernatelab.g0_lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Sản phẩm có id GÁN TAY (kiểu {@code String sku}), KHÔNG có {@code @Version}. Dùng cho G0-S11.
 *
 * <p>Bẫy của Spring Data: {@code repository.save(entity)} quyết định "entity mới hay cũ" bằng {@code isNew()}.
 * Với id kiểu object mà đã khác {@code null} và không có {@code @Version}, Spring Data coi đây là entity
 * CŨ nên gọi {@code merge()} (SELECT trước rồi mới INSERT), dù thật ra nó là entity mới.
 * Giải pháp: thêm {@code @Version} (xem {@link VersionedSkuProduct}) hoặc implement {@code Persistable}.
 */
@Entity(name = "G0_SkuProduct")
@Table(schema = "g0_lifecycle", name = "sku_product")
public class SkuProduct {

	// Không có @GeneratedValue: id do người dùng tự gán (mã SKU).
	// Cột PK tên "id" (field tên sku) để DbReader.rowById ("where id = ?") dùng được cho mọi bảng của lab.
	@Id
	@Column(name = "id")
	private String sku;

	@Column(name = "name", nullable = false)
	private String name;

	protected SkuProduct() {
		// JPA bắt buộc có constructor không tham số
	}

	public SkuProduct(String sku, String name) {
		this.sku = sku;
		this.name = name;
	}

	public String getSku() {
		return sku;
	}

	public String getName() {
		return name;
	}
}
