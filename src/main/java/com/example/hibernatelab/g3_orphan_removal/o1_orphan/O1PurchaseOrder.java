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
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : O1 (g3_o1_orphan)
 * BASELINE : O0 (g3_o0_cascade_all)
 * KHÁC BIỆT: items VÀ discounts đều thêm orphanRemoval = true (giữ cascade = ALL)
 * HẬU QUẢ  : item/discount bị tách khỏi collection bị DELETE (G3-S03, S04, S07, S08, S09, S11)
 */
@Entity(name = "G3O1_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g3_o1_orphan", name = "purchase_order") // purchase_order vì "order" là từ khoá SQL
public class O1PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g3o1_purchase_order_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g3o1_purchase_order_seq", schema = "g3_o1_orphan", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// INVERSE side — FK order_item.order_id do OrderItem.order quản lý (owning side).
	// KHÁC BIỆT CỦA BIẾN THỂ NẰM Ở ĐÂY: cascade = ALL, orphanRemoval = true: item bị tách khỏi collection sẽ bị DELETE lúc flush.
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<O1OrderItem> items = new ArrayList<>();

	protected O1PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public O1PurchaseOrder(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public List<O1OrderItem> getItems() {
		return items;
	}

	/**
	 * Gán CẢ collection mới. Chỉ có để G3-S07 chứng minh BẪY: với orphanRemoval = true, thay hẳn collection
	 * (thay vì clear() + addAll()) làm Hibernate ném HibernateException lúc flush.
	 */
	public void setItems(List<O1OrderItem> items) {
		this.items = items;
	}

	/** Helper đồng bộ HAI phía: add vào collection (inverse) và gán item.order (owning, thứ ghi FK). */
	public void addItem(O1OrderItem item) {
		items.add(item);
		item.setOrder(this);
	}

	/** Gỡ cả hai phía: bỏ khỏi collection VÀ set item.order = null (xem G3-S02/S03 và S01/S04 khi chỉ làm một nửa). */
	public void removeItem(O1OrderItem item) {
		items.remove(item);
		item.setOrder(null);
	}
}
