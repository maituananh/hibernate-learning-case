package com.example.hibernatelab.g3_orphan_removal.o1_orphan;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * BIẾN THỂ : O1 (g3_o1_orphan)
 * BASELINE : O0 (g3_o0_cascade_all)
 * KHÁC BIỆT: items VÀ discounts đều thêm orphanRemoval = true (giữ cascade = ALL)
 * HẬU QUẢ  : item/discount bị tách khỏi collection bị DELETE (G3-S03, S04, S07, S08, S09, S11)
 */
@Entity(name = "G3O1_OrderItem") // tên entity phải duy nhất toàn persistence unit
// UNIQUE (order_id, product_code): một order không có hai dòng cùng mã sản phẩm. Giống nhau ở MỌI biến thể, dùng cho G3-S10.
@Table(schema = "g3_o1_orphan", name = "order_item", uniqueConstraints = @UniqueConstraint(name = "uk_o1_order_item_order_product", columnNames = { "order_id", "product_code" }))
public class O1OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g3o1_order_item_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g3o1_order_item_seq", schema = "g3_o1_orphan", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK order_item.order_id nằm ở bảng này. Cột NULLABLE (cố ý) để nhìn thấy "dòng mồ côi" order_id = NULL (G3-S02).
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private O1PurchaseOrder order;

	@Column(name = "product_code", nullable = false)
	private String productCode;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	// INVERSE side — FK item_discount.item_id do ItemDiscount.item quản lý.
	// cascade = ALL, orphanRemoval = true (giống items).
	@OneToMany(mappedBy = "item", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<O1ItemDiscount> discounts = new ArrayList<>();

	protected O1OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	/** Chưa gán order: gọi {@code order.addItem(item)} để gán cả hai phía. */
	public O1OrderItem(String productCode, int quantity) {
		this.productCode = productCode;
		this.quantity = quantity;
	}

	public Long getId() {
		return id;
	}

	public O1PurchaseOrder getOrder() {
		return order;
	}

	/**
	 * PUBLIC (khác G2) để G3-S09 minh hoạ "đổi FK trực tiếp" từ test ở package khác.
	 * Code thật nên đi qua {@code PurchaseOrder.addItem/removeItem} để giữ hai phía đồng bộ.
	 */
	public void setOrder(O1PurchaseOrder order) {
		this.order = order;
	}

	public String getProductCode() {
		return productCode;
	}

	public int getQuantity() {
		return quantity;
	}

	public List<O1ItemDiscount> getDiscounts() {
		return discounts;
	}

	/** Helper đồng bộ HAI phía của quan hệ item ↔ discount. */
	public void addDiscount(O1ItemDiscount discount) {
		discounts.add(discount);
		discount.setItem(this);
	}
}
