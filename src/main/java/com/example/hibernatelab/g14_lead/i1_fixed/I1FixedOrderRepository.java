package com.example.hibernatelab.g14_lead.i1_fixed;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface I1FixedOrderRepository extends JpaRepository<I1FixedOrder, Long> {

	/**
	 * DTO projection: chỉ lấy 3 cột màn hình cần, trong MỘT câu SELECT.
	 * {@code size(o.items)} được Hibernate dịch thành subquery đếm; không cần nạp collection.
	 * {@code countQuery} khai báo riêng (G10-S08b): không join, không đếm lệch.
	 */
	@Query(value = """
		select new com.example.hibernatelab.g14_lead.i1_fixed.I1FixedOrderRow(o.orderNo, c.name, size(o.items))
		from G14I1F_Order o join o.customer c
		order by o.id
		""", countQuery = "select count(o) from G14I1F_Order o")
	Page<I1FixedOrderRow> findOrderRows(Pageable pageable);
}
