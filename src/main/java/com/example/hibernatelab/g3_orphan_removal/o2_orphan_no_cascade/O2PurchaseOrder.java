package com.example.hibernatelab.g3_orphan_removal.o2_orphan_no_cascade;

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
 * BIẾN THỂ : O2 (g3_o2_orphan_no_cascade)
 * BASELINE : O1 (g3_o1_orphan)
 * KHÁC BIỆT: PurchaseOrder.items bỏ cascade, chỉ còn orphanRemoval = true (discounts giữ như O1)
 * HẬU QUẢ  : persist order không kéo theo item, nhưng xoá order vẫn xoá item (G3-S06)
 */
@Entity(name = "G3O2_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g3_o2_orphan_no_cascade", name = "purchase_order") // purchase_order vì "order" là từ khoá SQL
public class O2PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g3o2_purchase_order_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g3o2_purchase_order_seq", schema = "g3_o2_orphan_no_cascade", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// INVERSE side — FK order_item.order_id do OrderItem.order quản lý (owning side).
	// KHÁC BIỆT CỦA BIẾN THỂ NẰM Ở ĐÂY: KHÔNG cascade, chỉ orphanRemoval = true (cascade REMOVE và orphanRemoval là hai cơ chế tách rời).
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY, orphanRemoval = true)
	private List<O2OrderItem> items = new ArrayList<>();

	protected O2PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public O2PurchaseOrder(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public List<O2OrderItem> getItems() {
		return items;
	}

	/**
	 * Gán CẢ collection mới. Chỉ có để G3-S07 chứng minh BẪY: với orphanRemoval = true, thay hẳn collection
	 * (thay vì clear() + addAll()) làm Hibernate ném HibernateException lúc flush.
	 */
	public void setItems(List<O2OrderItem> items) {
		this.items = items;
	}

	/** Helper đồng bộ HAI phía: add vào collection (inverse) và gán item.order (owning, thứ ghi FK). */
	public void addItem(O2OrderItem item) {
		items.add(item);
		item.setOrder(this);
	}

	/** Gỡ cả hai phía: bỏ khỏi collection VÀ set item.order = null (xem G3-S02/S03 và S01/S04 khi chỉ làm một nửa). */
	public void removeItem(O2OrderItem item) {
		items.remove(item);
		item.setOrder(null);
	}
}
