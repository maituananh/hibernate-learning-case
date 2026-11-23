package com.example.hibernatelab.g5_fetch_n_plus_one.f2_eager_collection;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository Spring Data cho {@link F2Customer}, dùng ở G5-S13 để xem {@code @EntityGraph} có "tắt" được EAGER không.
 */
public interface F2CustomerRepository extends JpaRepository<F2Customer, Long> {

	/** Graph chỉ nhắc tới {@code payments}; {@code orders} (EAGER theo mapping) không được nhắc tới. */
	@EntityGraph(attributePaths = "payments")
	@Query("select c from G5F2_Customer c")
	List<F2Customer> findAllWithPaymentsEntityGraph();
}
