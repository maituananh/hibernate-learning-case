package com.example.hibernatelab.g1_owning_side.v1_m2o_uni;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Khách hàng của biến thể V1. KHÔNG có collection {@code orders}: customer không biết order nào trỏ tới mình.
 *
 * <p>Đây là thiết kế "một chiều đơn giản" mà phần lớn trường hợp thực tế chỉ cần (xem G1-S18).
 */
@Entity(name = "G1V1_Customer") // tên entity phải duy nhất toàn persistence unit (JPQL viết theo tên này)
@Table(schema = "g1_v1_m2o_uni", name = "customer")
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v1_customer_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g1v1_customer_seq", schema = "g1_v1_m2o_uni", sequenceName = "customer_seq", allocationSize = 50)
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
