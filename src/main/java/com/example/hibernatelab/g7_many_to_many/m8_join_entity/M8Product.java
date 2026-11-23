package com.example.hibernatelab.g7_many_to_many.m8_join_entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm: cha của các dòng tồn kho (StockItem), mỗi dòng ứng với một kho.
 *
 * BIẾN THỂ : M8 (g7_m8_join_entity)
 * BASELINE : M1 (đổi từ @ManyToMany sang join entity)
 * KHÁC BIỆT: bỏ @ManyToMany; StockItem là entity trung gian có thêm cột quantity, PK ghép @EmbeddedId (productId, warehouseId) suy ra từ 2 @MapsId
 * HẬU QUẢ  : thêm/sửa/xoá số lượng tồn là thao tác trên entity StockItem; sửa quantity chỉ UPDATE stock_item (G7-S10)
 */
@Entity(name = "G7M8_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m8_join_entity", name = "product")
public class M8Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m8_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m8_product_seq", schema = "g7_m8_join_entity", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — FK nằm ở bảng stock_item (StockItem.product là owning side).
	// cascade = ALL + orphanRemoval = true: StockItem sống chết theo Product, bỏ khỏi list là bị DELETE (G7-S10).
	@OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<M8StockItem> stockItems = new ArrayList<>();

	protected M8Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public M8Product(String name) {
		this.name = name;
	}

	/** Thêm một dòng tồn kho. Warehouse phải đã được persist (cần id để @MapsId suy ra khoá). */
	public void addStock(M8Warehouse warehouse, int quantity) {
		stockItems.add(new M8StockItem(this, warehouse, quantity));
	}

	/** Gỡ dòng tồn kho của một kho khỏi list: orphanRemoval sẽ DELETE dòng đó (G7-S10). */
	public void removeStock(M8Warehouse warehouse) {
		stockItems.removeIf(item -> item.getWarehouse().getId().equals(warehouse.getId()));
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<M8StockItem> getStockItems() {
		return stockItems;
	}
}
