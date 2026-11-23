package com.example.hibernatelab.g1_owning_side.v1nn_m2o_nullable_false;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Khách hàng của biến thể V1-NN. Giống V1: không có collection {@code orders}.
 */
@Entity(name = "G1V1NN_Customer") // tên entity phải duy nhất toàn persistence unit (JPQL viết theo tên này)
@Table(schema = "g1_v1nn_m2o_nullable_false", name = "customer")
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v1nn_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g1v1nn_customer_seq", schema = "g1_v1nn_m2o_nullable_false", sequenceName = "customer_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// cố ý KHÔNG có List<PurchaseOrder> orders: quan hệ chỉ khai báo ở PurchaseOrder (@ManyToOne một chiều).
	// Muốn lấy order của một customer thì dùng query (xem G1-S18).

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
}
