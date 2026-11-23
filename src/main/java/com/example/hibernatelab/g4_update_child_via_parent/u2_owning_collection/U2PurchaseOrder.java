package com.example.hibernatelab.g4_update_child_via_parent.u2_owning_collection;

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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Đơn hàng (cha) của biến thể U2.
 *
 * BIẾN THỂ : U2 (g4_u2_owning_collection)
 * BASELINE : U0 (g4_u0_baseline)
 * KHÁC BIỆT: items là @OneToMany MỘT CHIỀU + @JoinTable, nên collection thuộc phía OWNING (item không còn trỏ ngược order)
 * HẬU QUẢ  : thêm item vào collection là đổi state của cha: ghi bảng nối và có thể tăng version cha (G4-S06)
 */
// Tên bảng là purchase_order (không phải order) vì "order" là từ khoá SQL.
@Entity(name = "G4U2_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g4_u2_owning_collection", name = "purchase_order")
public class U2PurchaseOrder {

	/**
	 * Đếm số lần callback {@code @PreUpdate} của order chạy. Test reset về 0 trước WHEN rồi assert (G4-S08).
	 * Static vì callback là method của entity, không có cách nào tiêm bean vào; chỉ dùng cho mục đích học.
	 */
	public static final AtomicInteger PRE_UPDATE_CALLS = new AtomicInteger();

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g4u2_purchase_order_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g4u2_purchase_order_seq", schema = "g4_u2_owning_collection", sequenceName = "purchase_order_seq", allocationSize = 50)
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
	private U2Customer customer;

	// OWNING side — collection này tự ghi bảng nối purchase_order_items (không còn FK order_id ở order_item).
	// Vì collection thuộc state của cha nên thay đổi nó được tính là "cha đã đổi" → có thể tăng @Version của cha (G4-S06).
	// KHÁC BIỆT CỦA BIẾN THỂ: @OneToMany MỘT CHIỀU + @JoinTable (không mappedBy). Khai schema cho bảng nối, nếu không nó rơi vào schema "public".
	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinTable(schema = "g4_u2_owning_collection", name = "purchase_order_items",
		joinColumns = @JoinColumn(name = "purchase_order_id"),
		inverseJoinColumns = @JoinColumn(name = "item_id"))
	private List<U2OrderItem> items = new ArrayList<>();

	protected U2PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public U2PurchaseOrder(String orderNo, String status) {
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

	public U2Customer getCustomer() {
		return customer;
	}

	public void setCustomer(U2Customer customer) {
		this.customer = customer;
	}

	public List<U2OrderItem> getItems() {
		return items;
	}

	/** Chỉ add vào collection (owning side): không có phía nào khác để đồng bộ vì item không trỏ ngược order. */
	public void addItem(U2OrderItem item) {
		items.add(item);
	}

	public void removeItem(U2OrderItem item) {
		items.remove(item);
	}
}
