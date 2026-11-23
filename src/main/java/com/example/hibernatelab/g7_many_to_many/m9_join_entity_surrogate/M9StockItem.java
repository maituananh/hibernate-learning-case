package com.example.hibernatelab.g7_many_to_many.m9_join_entity_surrogate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Entity trung gian Product — Warehouse với id riêng (surrogate key).
 *
 * BIẾN THỂ : M9 (g7_m9_join_entity_surrogate)
 * BASELINE : M8
 * KHÁC BIỆT: như M8 nhưng StockItem có id SEQUENCE riêng, cặp (product_id, warehouse_id) chỉ bị ràng buộc bằng UNIQUE
 * HẬU QUẢ  : bảng khác muốn trỏ tới StockItem chỉ cần FK 1 cột; đổi lại phải tự giữ UNIQUE (G7-S12)
 */
@Entity(name = "G7M9_StockItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g7_m9_join_entity_surrogate", name = "stock_item",
	// Không còn PK ghép nên PHẢI tự khai UNIQUE, nếu không một sản phẩm có thể có 2 dòng cho cùng một kho.
	uniqueConstraints = @UniqueConstraint(columnNames = { "product_id", "warehouse_id" }))
public class M9StockItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g7m9_stockitem_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g7m9_stockitem_seq", schema = "g7_m9_join_entity_surrogate", sequenceName = "stockitem_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK thường (không @MapsId): product_id chỉ là FK, không phải PK.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private M9Product product;

	// OWNING side — FK thường.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "warehouse_id", nullable = false)
	private M9Warehouse warehouse;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected M9StockItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public M9StockItem(M9Product product, M9Warehouse warehouse, int quantity) {
		this.product = product;
		this.warehouse = warehouse;
		this.quantity = quantity;
	}

	public Long getId() {
		return id;
	}

	public M9Product getProduct() {
		return product;
	}

	public M9Warehouse getWarehouse() {
		return warehouse;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}
}
