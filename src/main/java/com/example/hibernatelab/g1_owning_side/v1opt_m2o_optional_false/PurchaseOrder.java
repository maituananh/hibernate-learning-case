package com.example.hibernatelab.g1_owning_side.v1opt_m2o_optional_false;

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
 * BIẾN THỂ : V1-OPT — {@code @ManyToOne(optional = false)}
 * BASELINE : V1 (g1_v1_m2o_uni) — chỉ có @ManyToOne ở PurchaseOrder, Customer không có collection
 * KHÁC BIỆT: {@code @ManyToOne} thêm {@code optional = false} (không đụng tới {@code @JoinColumn})
 * HẬU QUẢ  : cột {@code customer_id} NOT NULL trong DDL; Hibernate chặn order thiếu customer lúc persist
 *            (trước khi gửi SQL) và tải bằng INNER JOIN. Quan sát được giống hệt V1-NN (G1-S04c, S04d).
 *
 * <p>Tên bảng là {@code purchase_order} (không phải {@code order}) vì {@code order} là từ khoá SQL.
 */
@Entity(name = "G1V1OPT_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g1_v1opt_m2o_optional_false", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v1opt_purchase_order_seq")
	@SequenceGenerator(name = "g1v1opt_purchase_order_seq", schema = "g1_v1opt_m2o_optional_false", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// OWNING side — FK purchase_order.customer_id. optional = false → DDL NOT NULL + Hibernate kiểm tra ở runtime.
	// cố ý KHÔNG ghi fetch → mặc định EAGER.
	@ManyToOne(optional = false)
	@JoinColumn(name = "customer_id")
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
