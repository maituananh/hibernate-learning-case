package com.example.hibernatelab.g7_many_to_many.m9_join_entity_surrogate;

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
 * BIẾN THỂ : M9 (g7_m9_join_entity_surrogate)
 * BASELINE : M8
 * KHÁC BIỆT: như M8 nhưng StockItem có id SEQUENCE riêng, cặp (product_id, warehouse_id) chỉ bị ràng buộc bằng UNIQUE
 * HẬU QUẢ  : bảng khác muốn trỏ tới StockItem chỉ cần FK 1 cột; đổi lại phải tự giữ UNIQUE (G7-S12)
 */
@Entity(name = "G7M9_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m9_join_entity_surrogate", name = "product")
public class M9Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m9_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m9_product_seq", schema = "g7_m9_join_entity_surrogate", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// INVERSE side — FK nằm ở bảng stock_item (StockItem.product là owning side).
	// cascade = ALL + orphanRemoval = true: StockItem sống chết theo Product, bỏ khỏi list là bị DELETE (G7-S10).
	@OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<M9StockItem> stockItems = new ArrayList<>();

	protected M9Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public M9Product(String name) {
		this.name = name;
	}

	/** Thêm một dòng tồn kho. Warehouse phải đã được persist (cần id để @MapsId suy ra khoá). */
	public void addStock(M9Warehouse warehouse, int quantity) {
		stockItems.add(new M9StockItem(this, warehouse, quantity));
	}

	/** Gỡ dòng tồn kho của một kho khỏi list: orphanRemoval sẽ DELETE dòng đó (G7-S10). */
	public void removeStock(M9Warehouse warehouse) {
		stockItems.removeIf(item -> item.getWarehouse().getId().equals(warehouse.getId()));
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<M9StockItem> getStockItems() {
		return stockItems;
	}
}
