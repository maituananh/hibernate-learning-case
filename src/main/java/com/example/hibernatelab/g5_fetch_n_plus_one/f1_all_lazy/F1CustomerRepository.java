package com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository Spring Data cho {@link F1Customer}. Mỗi method là một CÁCH tải orders khác nhau (G5-S05..S07).
 */
public interface F1CustomerRepository extends JpaRepository<F1Customer, Long> {

	/** Cách 1 (G5-S06): JPQL {@code join fetch}. Chỉ đúng cho query này, nằm ngay trong câu JPQL. */
	@Query("select c from G5F1_Customer c join fetch c.orders")
	List<F1Customer> findAllWithOrdersJoinFetch();

	/**
	 * Cách 2 (G5-S07): {@code @EntityGraph} nói "lần tải này hãy kèm orders", câu JPQL giữ nguyên là
	 * {@code select c from Customer c}. Tiện vì tách chuyện "tải gì" ra khỏi câu query.
	 */
	@EntityGraph(attributePaths = "orders")
	@Query("select c from G5F1_Customer c")
	List<F1Customer> findAllWithOrdersEntityGraph();
}
