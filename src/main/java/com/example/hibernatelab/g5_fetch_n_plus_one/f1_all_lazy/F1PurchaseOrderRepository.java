package com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository Spring Data cho {@link F1PurchaseOrder}, dùng ở G5-S15: hai kiểu DTO projection, mỗi kiểu 1 SELECT.
 */
public interface F1PurchaseOrderRepository extends JpaRepository<F1PurchaseOrder, Long> {

	/** Constructor expression của JPQL: {@code select new ...}. Kết quả là record, không phải entity. */
	@Query("select new com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy.F1OrderSummary(o.orderNo, c.name) "
		+ "from G5F1_PurchaseOrder o join o.customer c order by o.id")
	List<F1OrderSummary> findAllSummaries();

	/** Interface projection: alias trong JPQL phải trùng tên thuộc tính của interface. */
	@Query("select o.orderNo as orderNo, c.name as customerName from G5F1_PurchaseOrder o join o.customer c order by o.id")
	List<F1OrderView> findAllViews();
}
