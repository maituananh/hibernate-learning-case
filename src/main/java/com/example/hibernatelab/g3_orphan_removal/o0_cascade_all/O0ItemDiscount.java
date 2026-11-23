package com.example.hibernatelab.g3_orphan_removal.o0_cascade_all;

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

/**
 * Giảm giá của một dòng hàng (tầng thứ 3: order → item → discount). Xem header ở O0PurchaseOrder để biết biến thể khác baseline điểm nào.
 */
@Entity(name = "G3O0_ItemDiscount") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g3_o0_cascade_all", name = "item_discount")
public class O0ItemDiscount {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g3o0_item_discount_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g3o0_item_discount_seq", schema = "g3_o0_cascade_all", sequenceName = "item_discount_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK item_discount.item_id nằm ở bảng này.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "item_id")
	private O0OrderItem item;

	@Column(name = "label", nullable = false)
	private String label;

	protected O0ItemDiscount() {
		// JPA bắt buộc có constructor không tham số
	}

	/** Chưa gán item: gọi {@code item.addDiscount(discount)} để gán cả hai phía. */
	public O0ItemDiscount(String label) {
		this.label = label;
	}

	public Long getId() {
		return id;
	}

	public O0OrderItem getItem() {
		return item;
	}

	void setItem(O0OrderItem item) {
		this.item = item;
	}

	public String getLabel() {
		return label;
	}
}
