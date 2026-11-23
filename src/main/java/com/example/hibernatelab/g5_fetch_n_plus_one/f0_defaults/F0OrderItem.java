package com.example.hibernatelab.g5_fetch_n_plus_one.f0_defaults;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Dòng hàng của biến thể F0.
 *
 * BIẾN THỂ : F0 (g5_f0_defaults)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: KHÔNG ghi `fetch` ở bất kỳ quan hệ nào: dùng mặc định JPA (@ManyToOne = EAGER, @OneToMany = LAZY)
 * HẬU QUẢ  : @ManyToOne EAGER nên find(order) JOIN luôn customer, còn JPQL thì sinh N+1 (G5-S01, S02)
 */
@Entity(name = "G5F0_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f0_defaults", name = "order_item")
public class F0OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f0_order_item_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f0_order_item_seq", schema = "g5_f0_defaults", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK order_item.order_id nằm ở bảng này.
	// fetch: CỐ Ý để mặc định. Mặc định của @ManyToOne là EAGER (ngược với @OneToMany là LAZY).
	@ManyToOne
	@JoinColumn(name = "order_id")
	private F0PurchaseOrder order;

	// OWNING side — FK order_item.product_id nằm ở bảng này.
	// fetch: CỐ Ý để mặc định. Mặc định của @ManyToOne là EAGER (ngược với @OneToMany là LAZY).
	@ManyToOne
	@JoinColumn(name = "product_id")
	private F0Product product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected F0OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public Long getId() {
		return id;
	}

	public F0PurchaseOrder getOrder() {
		return order;
	}

	public F0Product getProduct() {
		return product;
	}

	public int getQuantity() {
		return quantity;
	}
}
