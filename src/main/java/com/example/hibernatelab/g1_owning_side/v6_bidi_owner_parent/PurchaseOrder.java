package com.example.hibernatelab.g1_owning_side.v6_bidi_owner_parent;

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
 * BIẾN THỂ : V6 — owning side ở phía "một" (xem {@link Customer})
 * BASELINE : V4 (g1_v4_bidi_mappedby)
 * KHÁC BIỆT: {@code @JoinColumn} có {@code insertable = false, updatable = false}
 * HẬU QUẢ  : PurchaseOrder.customer chỉ ĐỌC được (giống inverse side), không ghi được FK.
 *
 * <p>Tên bảng là {@code purchase_order} (không phải {@code order}) vì {@code order} là từ khoá SQL.
 */
@Entity(name = "G1V6_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g1_v6_bidi_owner_parent", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v6_purchase_order_seq")
	@SequenceGenerator(name = "g1v6_purchase_order_seq", schema = "g1_v6_bidi_owner_parent", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// INVERSE side (về hành vi) — cùng cột customer_id với Customer.orders nhưng bị tắt quyền ghi.
	// Hibernate CẦN insertable/updatable = false, nếu không sẽ báo "cột bị map hai lần" (xem G1-S06).
	// cố ý KHÔNG ghi fetch → mặc định EAGER.
	@ManyToOne
	@JoinColumn(name = "customer_id", insertable = false, updatable = false)
	private Customer customer;

	protected PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public PurchaseOrder(String orderNo) {
		this.orderNo = orderNo;
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public Customer getCustomer() {
		return customer;
	}

	public void setCustomer(Customer customer) {
		this.customer = customer;
	}
}
