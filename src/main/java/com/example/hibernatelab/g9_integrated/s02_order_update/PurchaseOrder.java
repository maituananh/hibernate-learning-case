package com.example.hibernatelab.g9_integrated.s02_order_update;

import java.math.BigDecimal;
import java.time.Instant;
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
import jakarta.persistence.Version;

/**
 * Đơn hàng (cha) của G9-S02, S08, S09. Có {@code @Version}, {@code total}, và các cột "không có trong request" (status, createdAt, customer).
 *
 * BIẾN THỂ : G9-S02 (g9_s02_order_update)
 * BASELINE : G4-U0 (collection items là INVERSE side, cascade ALL, orphanRemoval)
 * KHÁC BIỆT: status, created_at, customer_id, total đều NULLABLE để cách B (merge từ DTO thiếu field) chạy tới UPDATE rồi ghi NULL
 * HẬU QUẢ  : cách B làm mất dữ liệu âm thầm (DB không chặn vì cột cho phép NULL); xem G9-S02
 */
@Entity(name = "G9S02_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s02_order_update", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s02_purchase_order_seq")
	@SequenceGenerator(name = "g9s02_purchase_order_seq", schema = "g9_s02_order_update", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// Các cột dưới đây KHÔNG nằm trong request cập nhật. Cố ý để NULLABLE (xem KHÁC BIỆT ở đầu class).
	@Column(name = "status")
	private String status;

	@Column(name = "note")
	private String note;

	@Column(name = "created_at")
	private Instant createdAt;

	@Column(name = "total", precision = 12, scale = 2)
	private BigDecimal total;

	// @Version: Hibernate tăng cột này mỗi lần UPDATE và dùng nó để phát hiện hai người sửa cùng lúc (G9-S08).
	@Version
	@Column(name = "version")
	private Long version;

	// OWNING side — FK purchase_order.customer_id.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private Customer customer;

	// INVERSE side — mappedBy = "order": FK order_item.order_id do OrderItem.order quản lý.
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderItem> items = new ArrayList<>();

	protected PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public PurchaseOrder(String orderNo, String status) {
		this.orderNo = orderNo;
		this.status = status;
	}

	/**
	 * CHỈ ĐỂ MINH HOẠ BẪY của cách B (G9-S02): dựng một entity "mới" nhưng mang id và version lấy từ DTO,
	 * rồi đưa cho merge(). Mọi field không có trong DTO (status, createdAt, customer, total) sẽ là null.
	 * Id sinh tự động nên constructor thường không cho gán id; ở đây phải gán để merge hiểu đó là bản ghi cũ.
	 */
	public static PurchaseOrder detachedCopyFromRequest(Long id, Long version, String orderNo, String note) {
		PurchaseOrder copy = new PurchaseOrder(orderNo, null);
		copy.id = id;
		copy.version = version;
		copy.note = note;
		return copy;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public void setOrderNo(String orderNo) {
		this.orderNo = orderNo;
	}

	public String getStatus() {
		return status;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public Long getVersion() {
		return version;
	}

	public Customer getCustomer() {
		return customer;
	}

	public void setCustomer(Customer customer) {
		this.customer = customer;
	}

	public List<OrderItem> getItems() {
		return items;
	}

	/** Giữ hai phía đồng bộ: thêm vào collection (inverse) VÀ gán item.order (owning, ghi FK). */
	public void addItem(OrderItem item) {
		items.add(item);
		item.setOrder(this);
	}

	/** Gỡ khỏi collection: nhờ orphanRemoval, dòng order_item bị DELETE lúc flush. */
	public void removeItem(OrderItem item) {
		items.remove(item);
		item.setOrder(null);
	}

	/** Tính lại tổng tiền từ các item hiện có. Gọi tường minh vì total là cột thật, không tự cập nhật. */
	public void recalculateTotal() {
		BigDecimal sum = BigDecimal.ZERO;
		for (OrderItem item : items) {
			sum = sum.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
		}
		this.total = sum;
	}
}
