package com.example.hibernatelab.g3_orphan_removal.o2_orphan_no_cascade;

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
 * Giảm giá của một dòng hàng (tầng thứ 3: order → item → discount). Xem header ở O2PurchaseOrder để biết biến thể khác baseline điểm nào.
 */
@Entity(name = "G3O2_ItemDiscount") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g3_o2_orphan_no_cascade", name = "item_discount")
public class O2ItemDiscount {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g3o2_item_discount_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g3o2_item_discount_seq", schema = "g3_o2_orphan_no_cascade", sequenceName = "item_discount_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK item_discount.item_id nằm ở bảng này.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "item_id")
	private O2OrderItem item;

	@Column(name = "label", nullable = false)
	private String label;

	protected O2ItemDiscount() {
		// JPA bắt buộc có constructor không tham số
	}

	/** Chưa gán item: gọi {@code item.addDiscount(discount)} để gán cả hai phía. */
	public O2ItemDiscount(String label) {
		this.label = label;
	}

	public Long getId() {
		return id;
	}

	public O2OrderItem getItem() {
		return item;
	}

	void setItem(O2OrderItem item) {
		this.item = item;
	}

	public String getLabel() {
		return label;
	}
}
