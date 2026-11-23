package com.example.hibernatelab.g4_update_child_via_parent.u1_no_merge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.annotations.UpdateTimestamp;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Đơn hàng (cha) của biến thể U1.
 *
 * BIẾN THỂ : U1 (g4_u1_no_merge)
 * BASELINE : U0 (g4_u0_baseline)
 * KHÁC BIỆT: items: cascade = PERSIST (bỏ MERGE; vẫn giữ orphanRemoval = true)
 * HẬU QUẢ  : merge(order DETACHED) không lan xuống item nên thay đổi của item bị MẤT (G4-S03)
 */
// Tên bảng là purchase_order (không phải order) vì "order" là từ khoá SQL.
@Entity(name = "G4U1_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g4_u1_no_merge", name = "purchase_order")
public class U1PurchaseOrder {

	/**
	 * Đếm số lần callback {@code @PreUpdate} của order chạy. Test reset về 0 trước WHEN rồi assert (G4-S08).
	 * Static vì callback là method của entity, không có cách nào tiêm bean vào; chỉ dùng cho mục đích học.
	 */
	public static final AtomicInteger PRE_UPDATE_CALLS = new AtomicInteger();

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g4u1_purchase_order_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g4u1_purchase_order_seq", schema = "g4_u1_no_merge", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	@Column(name = "status", nullable = false)
	private String status;

	// @Version: Hibernate tự tăng cột này mỗi khi dòng purchase_order bị UPDATE, và dùng nó để phát hiện ghi đè đồng thời.
	@Version
	@Column(name = "version")
	private Long version;

	// @UpdateTimestamp: Hibernate tự gán thời điểm hiện tại mỗi khi order được UPDATE (kể cả lúc INSERT lần đầu).
	@UpdateTimestamp
	@Column(name = "updated_at")
	private Instant updatedAt;

	// OWNING side — ghi FK purchase_order.customer_id.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private U1Customer customer;

	// INVERSE side — FK order_item.order_id do OrderItem.order quản lý (owning side).
	// Vì là inverse nên thay đổi collection này KHÔNG được tính là state của cha (không tăng version, không UPDATE cha).
	// cascade = PERSIST, orphanRemoval = true.
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST, orphanRemoval = true)
	private List<U1OrderItem> items = new ArrayList<>();

	protected U1PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public U1PurchaseOrder(String orderNo, String status) {
		this.orderNo = orderNo;
		this.status = status;
	}

	/** Chỉ chạy khi chính dòng purchase_order sắp bị UPDATE. Con đổi mà cha không đổi thì không chạy (G4-S08). */
	@PreUpdate
	void onPreUpdate() {
		PRE_UPDATE_CALLS.incrementAndGet();
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public String getStatus() {
		return status;
	}

	/** Đổi field của CHÍNH order: khác với đổi item, cái này làm dòng purchase_order bị UPDATE (đối chứng ở G4-S08). */
	public void setStatus(String status) {
		this.status = status;
	}

	public Long getVersion() {
		return version;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public U1Customer getCustomer() {
		return customer;
	}

	public void setCustomer(U1Customer customer) {
		this.customer = customer;
	}

	public List<U1OrderItem> getItems() {
		return items;
	}

	/** Helper đồng bộ HAI phía: add vào collection (inverse) và gán item.order (owning, thứ ghi FK). */
	public void addItem(U1OrderItem item) {
		items.add(item);
		item.setOrder(this);
	}

	/** Gỡ cả hai phía; orphanRemoval = true nên dòng item bị DELETE lúc flush. */
	public void removeItem(U1OrderItem item) {
		items.remove(item);
		item.setOrder(null);
	}
}
