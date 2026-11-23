package com.example.hibernatelab.g1_owning_side.v1_m2o_uni;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data cho {@link PurchaseOrder} của V1. Chỉ dùng ở G1-S18: V1 không có collection
 * {@code Customer.orders}, nên muốn lấy order của một customer thì hỏi bằng query.
 *
 * <p>Tên có tiền tố {@code G1V1} vì tên bean Spring phải duy nhất, mà G0 đã có {@code PurchaseOrderRepository}.
 */
public interface G1V1PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

	/** Derived query: Spring Data đọc tên method, đi qua {@code customer.id} và sinh {@code where customer_id = ?}. */
	List<PurchaseOrder> findByCustomerId(Long customerId);
}
