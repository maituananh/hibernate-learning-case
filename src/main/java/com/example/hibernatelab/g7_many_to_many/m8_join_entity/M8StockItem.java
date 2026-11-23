package com.example.hibernatelab.g7_many_to_many.m8_join_entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * Entity trung gian Product — Warehouse, có thêm cột quantity.
 *
 * BIẾN THỂ : M8 (g7_m8_join_entity)
 * BASELINE : M1 (đổi từ @ManyToMany sang join entity)
 * KHÁC BIỆT: bỏ @ManyToMany; StockItem là entity trung gian có thêm cột quantity, PK ghép @EmbeddedId (productId, warehouseId) suy ra từ 2 @MapsId
 * HẬU QUẢ  : thêm/sửa/xoá số lượng tồn là thao tác trên entity StockItem; sửa quantity chỉ UPDATE stock_item (G7-S10)
 */
@Entity(name = "G7M8_StockItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m8_join_entity", name = "stock_item")
public class M8StockItem {

	// PK ghép (product_id, warehouse_id). Khởi tạo sẵn để @MapsId có chỗ điền giá trị lúc persist.
	@EmbeddedId
	private M8StockItemId id = new M8StockItemId();

	// OWNING side — @MapsId("productId"): cột product_id vừa là FK trỏ product, vừa là một nửa PK (thuộc tính productId của id).
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId("productId")
	@JoinColumn(name = "product_id")
	private M8Product product;

	// OWNING side — @MapsId("warehouseId"): cột warehouse_id vừa là FK trỏ warehouse, vừa là nửa PK còn lại.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId("warehouseId")
	@JoinColumn(name = "warehouse_id")
	private M8Warehouse warehouse;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected M8StockItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public M8StockItem(M8Product product, M8Warehouse warehouse, int quantity) {
		this.product = product;
		this.warehouse = warehouse;
		this.quantity = quantity;
	}

	public M8StockItemId getId() {
		return id;
	}

	public M8Product getProduct() {
		return product;
	}

	public M8Warehouse getWarehouse() {
		return warehouse;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}
}
