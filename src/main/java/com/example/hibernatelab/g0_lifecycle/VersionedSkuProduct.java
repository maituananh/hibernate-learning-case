package com.example.hibernatelab.g0_lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Giống {@link SkuProduct} (id gán tay) nhưng có thêm {@code @Version Long}. Dùng cho G0-S11 (c).
 *
 * <p>Khác biệt DUY NHẤT so với {@link SkuProduct} là field {@code version}. Hệ quả: khi {@code version == null}
 * Spring Data hiểu đây là entity MỚI nên gọi {@code persist()} (chỉ INSERT, không SELECT thừa).
 * Lưu ý kiểu phải là wrapper ({@code Long}, không phải {@code long}), vì {@code long} không bao giờ null.
 */
@Entity(name = "G0_VersionedSkuProduct")
@Table(schema = "g0_lifecycle", name = "versioned_sku_product")
public class VersionedSkuProduct {

	// Id gán tay, cột PK tên "id" (xem ghi chú ở SkuProduct).
	@Id
	@Column(name = "id")
	private String sku;

	@Column(name = "name", nullable = false)
	private String name;

	// Hibernate tự gán 0 lúc INSERT và tăng lên mỗi lần UPDATE. Trước khi lưu, version == null → "entity mới".
	@Version
	@Column(name = "version")
	private Long version;

	protected VersionedSkuProduct() {
		// JPA bắt buộc có constructor không tham số
	}

	public VersionedSkuProduct(String sku, String name) {
		this.sku = sku;
		this.name = name;
	}

	public String getSku() {
		return sku;
	}

	public String getName() {
		return name;
	}

	public Long getVersion() {
		return version;
	}
}
