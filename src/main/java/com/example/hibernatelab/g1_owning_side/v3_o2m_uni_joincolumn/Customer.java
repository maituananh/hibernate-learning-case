package com.example.hibernatelab.g1_owning_side.v3_o2m_uni_joincolumn;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * BIẾN THỂ : V3 — {@code @OneToMany} một chiều + {@code @JoinColumn(name = "customer_id")}
 * BASELINE : V1 (g1_v1_m2o_uni) — chỉ có @ManyToOne ở PurchaseOrder, Customer không có collection
 * KHÁC BIỆT: quan hệ chuyển sang phía cha; thêm {@code @JoinColumn} để KHÔNG dùng bảng nối
 * HẬU QUẢ  : FK nằm ở bảng con ({@code purchase_order.customer_id}) nhưng được GHI từ phía cha:
 *            INSERT order (customer_id = NULL) rồi UPDATE bù customer_id (G1-S09).
 */
@Entity(name = "G1V3_Customer") // tên entity phải duy nhất toàn persistence unit (JPQL viết theo tên này)
@Table(schema = "g1_v3_o2m_uni_joincolumn", name = "customer")
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v3_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g1v3_customer_seq", schema = "g1_v3_o2m_uni_joincolumn", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// OWNING side (duy nhất) — dù khai báo ở Customer, cột customer_id nằm ở bảng purchase_order.
	// Vì PurchaseOrder không có field customer, chỉ collection này mới ghi được FK → nó là owning side.
	// fetch = LAZY ghi tường minh. cố ý KHÔNG có cascade và KHÔNG có nullable = false (xem V3-NN).
	@OneToMany(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private List<PurchaseOrder> orders = new ArrayList<>();

	protected Customer() {
		// JPA bắt buộc có constructor không tham số
	}

	public Customer(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	/**
	 * Trả về CHÍNH list bên trong (không phải bản sao bất biến) để test được thao tác trực tiếp
	 * {@code customer.getOrders().add(...)}.
	 */
	public List<PurchaseOrder> getOrders() {
		return orders;
	}
}
