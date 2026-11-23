package com.example.hibernatelab.g1_owning_side.v3nn_o2m_uni_joincolumn_nn;

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
 * BIẾN THỂ : V3-NN — như V3 nhưng {@code @JoinColumn(nullable = false)}
 * BASELINE : V3 (g1_v3_o2m_uni_joincolumn)
 * KHÁC BIỆT: thêm {@code nullable = false} vào {@code @JoinColumn}
 * HẬU QUẢ  : cột {@code customer_id} NOT NULL; Hibernate phải ghi FK ngay trong câu INSERT order (G1-S10 🔬).
 */
@Entity(name = "G1V3NN_Customer") // tên entity phải duy nhất toàn persistence unit (JPQL viết theo tên này)
@Table(schema = "g1_v3nn_o2m_uni_joincolumn_nn", name = "customer")
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v3nn_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g1v3nn_customer_seq", schema = "g1_v3nn_o2m_uni_joincolumn_nn", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// OWNING side (duy nhất). nullable = false → cột purchase_order.customer_id NOT NULL.
	@OneToMany(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id", nullable = false)
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
