package com.example.hibernatelab.bootfail.g1s06;

import com.example.hibernatelab.g1_owning_side.v1_m2o_uni.Customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * [BOOT-FAIL] G1-S06: entity CỐ Ý SAI, nằm ở package {@code bootfail} nên entity scan không thấy.
 *
 * <p>Sai ở đâu: cột {@code customer_id} bị map HAI lần và cả hai đều cho phép ghi
 * ({@code Long customerId} và {@code @ManyToOne customer}). Bản ĐÚNG phải thêm
 * {@code insertable = false, updatable = false} vào một trong hai (xem {@code DualMappedOrder} của V1).
 */
@Entity(name = "BootFail_DuplicateColumnOrder")
@Table(schema = "g1_v1_m2o_uni", name = "order_dup_broken")
public class DuplicateColumnOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "customer_id")
	private Long customerId;

	// Thiếu insertable = false, updatable = false → hai property cùng ghi cột customer_id.
	@ManyToOne
	@JoinColumn(name = "customer_id")
	private Customer customer;
}
