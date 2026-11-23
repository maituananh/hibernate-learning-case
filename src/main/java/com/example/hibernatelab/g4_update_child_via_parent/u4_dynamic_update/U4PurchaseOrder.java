package com.example.hibernatelab.g4_update_child_via_parent.u4_dynamic_update;

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
 * Đơn hàng (cha) của biến thể U4.
 *
 * BIẾN THỂ : U4 (g4_u4_dynamic_update)
 * BASELINE : U0 (g4_u0_baseline)
 * KHÁC BIỆT: OrderItem có @DynamicUpdate
 * HẬU QUẢ  : UPDATE order_item chỉ liệt kê cột thực sự đổi thay vì tất cả cột (G4-S10)
 */
// Tên bảng là purchase_order (không phải order) vì "order" là từ khoá SQL.
@Entity(name = "G4U4_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g4_u4_dynamic_update", name = "purchase_order")
public class U4PurchaseOrder {

	/**
	 * Đếm số lần callback {@code @PreUpdate} của order chạy. Test reset về 0 trước WHEN rồi assert (G4-S08).
	 * Static vì callback là method của entity, không có cách nào tiêm bean vào; chỉ dùng cho mục đích học.
	 */
	public static final AtomicInteger PRE_UPDATE_CALLS = new AtomicInteger();

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g4u4_purchase_order_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g4u4_purchase_order_seq", schema = "g4_u4_dynamic_update", sequenceName = "purchase_order_seq", allocationSize = 50)
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
	private U4Customer customer;

	// INVERSE side — FK order_item.order_id do OrderItem.order quản lý (owning side).
	// Vì là inverse nên thay đổi collection này KHÔNG được tính là state của cha (không tăng version, không UPDATE cha).
	// cascade = ALL, orphanRemoval = true.
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<U4OrderItem> items = new ArrayList<>();

	protected U4PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public U4PurchaseOrder(String orderNo, String status) {
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

	public U4Customer getCustomer() {
		return customer;
	}

	public void setCustomer(U4Customer customer) {
		this.customer = customer;
	}

	public List<U4OrderItem> getItems() {
		return items;
	}

	/** Helper đồng bộ HAI phía: add vào collection (inverse) và gán item.order (owning, thứ ghi FK). */
	public void addItem(U4OrderItem item) {
		items.add(item);
		item.setOrder(this);
	}

	/** Gỡ cả hai phía; orphanRemoval = true nên dòng item bị DELETE lúc flush. */
	public void removeItem(U4OrderItem item) {
		items.remove(item);
		item.setOrder(null);
	}
}
