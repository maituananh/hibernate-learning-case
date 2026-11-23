package com.example.hibernatelab.g1_owning_side.v1_m2o_uni;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : V1 — {@code @ManyToOne} một chiều (BASELINE của nhóm G1, cách làm khuyến nghị)
 * BASELINE : chính nó
 * KHÁC BIỆT: không có (đây là mốc để các biến thể khác so sánh)
 * HẬU QUẢ  : FK {@code purchase_order.customer_id} nằm ở bảng CON, thêm 1 order = 1 INSERT là đủ.
 *            Customer không có collection nên không có inverse side.
 *
 * <p>Tên bảng là {@code purchase_order} (không phải {@code order}) vì {@code order} là từ khoá SQL.
 */
@Entity(name = "G1V1_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g1_v1_m2o_uni", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v1_purchase_order_seq")
	@SequenceGenerator(name = "g1v1_purchase_order_seq", schema = "g1_v1_m2o_uni", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side — FK purchase_order.customer_id nằm ở bảng này (bảng chứa cột FK luôn là owning side).
	// cố ý KHÔNG ghi fetch → mặc định của @ManyToOne là EAGER (G1-S04 cần thấy câu JOIN khi find order).
	// foreignKey đặt tên tường minh để G1-S01 chứng minh được tên constraint.
	// cố ý KHÔNG ghi optional / nullable → mặc định cho phép NULL (so sánh với V1-NN và V1-OPT).
	@ManyToOne
	@JoinColumn(name = "customer_id", foreignKey = @ForeignKey(name = "fk_order_customer"))
	private Customer customer;

	// G1-S05: HAI @ManyToOne cùng trỏ tới bảng address. Cố ý KHÔNG ghi @JoinColumn → Hibernate suy ra tên cột
	// từ tên FIELD (không phải từ tên bảng đích): billing_address_id và shipping_address_id.
	@ManyToOne(fetch = FetchType.LAZY)
	private Address billingAddress;

	@ManyToOne(fetch = FetchType.LAZY)
	private Address shippingAddress;

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

	public Address getBillingAddress() {
		return billingAddress;
	}

	public void setBillingAddress(Address billingAddress) {
		this.billingAddress = billingAddress;
	}

	public Address getShippingAddress() {
		return shippingAddress;
	}

	public void setShippingAddress(Address shippingAddress) {
		this.shippingAddress = shippingAddress;
	}
}
