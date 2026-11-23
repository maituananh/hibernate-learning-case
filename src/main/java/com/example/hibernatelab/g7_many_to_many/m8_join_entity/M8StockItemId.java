package com.example.hibernatelab.g7_many_to_many.m8_join_entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Khoá chính ghép của M8StockItem: (productId, warehouseId).
 *
 * <p>Vì sao PHẢI {@code Serializable} và có {@code equals/hashCode} theo GIÁ TRỊ (G7-S11):
 * Hibernate dùng id làm khoá trong persistence context (một Map) để tìm "entity này đã tải chưa".
 * Hai object {@code StockItemId} khác nhau nhưng cùng (productId, warehouseId) phải coi là MỘT khoá,
 * nếu không {@code find} sẽ không nhận ra entity đã nằm trong persistence context. Serializable là yêu cầu của JPA cho
 * mọi composite id (id còn được dùng làm khoá cache và có thể bị serialize).
 *
 * BIẾN THỂ : M8 (g7_m8_join_entity)
 * BASELINE : M1 (đổi từ @ManyToMany sang join entity)
 * KHÁC BIỆT: id ghép là @Embeddable thay vì id SEQUENCE riêng (M9)
 * HẬU QUẢ  : find phải truyền new M8StockItemId(productId, warehouseId) (G7-S11)
 */
@Embeddable
public class M8StockItemId implements Serializable {

	@Column(name = "product_id")
	private Long productId;

	@Column(name = "warehouse_id")
	private Long warehouseId;

	protected M8StockItemId() {
		// JPA bắt buộc có constructor không tham số
	}

	public M8StockItemId(Long productId, Long warehouseId) {
		this.productId = productId;
		this.warehouseId = warehouseId;
	}

	public Long getProductId() {
		return productId;
	}

	public Long getWarehouseId() {
		return warehouseId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof M8StockItemId that)) {
			return false;
		}
		return Objects.equals(productId, that.productId) && Objects.equals(warehouseId, that.warehouseId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(productId, warehouseId);
	}
}
