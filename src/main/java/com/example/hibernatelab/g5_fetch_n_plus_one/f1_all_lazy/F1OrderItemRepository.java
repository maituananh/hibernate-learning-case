package com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository Spring Data cho {@link F1OrderItem}, dùng ở G5-S16 (N+1 qua {@code @ManyToOne} LAZY và cách sửa).
 */
public interface F1OrderItemRepository extends JpaRepository<F1OrderItem, Long> {

	/** Cách sửa N+1 của {@code item.getProduct()}: kéo product về cùng câu SELECT bằng {@code join fetch}. */
	@Query("select i from G5F1_OrderItem i join fetch i.product")
	List<F1OrderItem> findAllWithProductJoinFetch();
}
