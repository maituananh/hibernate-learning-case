package com.example.hibernatelab.g9_integrated.s05_bulk_modifying;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository cho bulk DELETE của G9-S05: câu JPQL delete KHÔNG đi qua cascade REMOVE hay orphanRemoval của entity. */
public interface BulkOrderItemRepository extends JpaRepository<OrderItem, Long> {

	/** Xoá thẳng bằng một câu DELETE. Collection {@code order.items} trong persistence context (nếu đã tải) vẫn còn các item "ma". */
	@Modifying
	@Query("delete from G9S05_OrderItem i where i.order.id = :orderId")
	int deleteAllOfOrder(@Param("orderId") Long orderId);
}
