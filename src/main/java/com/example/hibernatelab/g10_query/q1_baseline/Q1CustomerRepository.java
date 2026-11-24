package com.example.hibernatelab.g10_query.q1_baseline;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository Spring Data cho {@link Q1Customer}. Hai method {@code Page} dùng ở G10-S08, và
 * {@link JpaSpecificationExecutor} cho bộ lọc động bằng {@code Specification} ở G10-S09.
 */
public interface Q1CustomerRepository extends JpaRepository<Q1Customer, Long>, JpaSpecificationExecutor<Q1Customer> {

	/**
	 * G10-S08a: {@code join fetch} collection + {@code Page}, KHÔNG khai báo {@code countQuery}.
	 * Spring Data tự sinh câu count từ câu query trên.
	 */
	@Query("select c from G10Q1_Customer c join fetch c.orders")
	Page<Q1Customer> findPageWithOrdersAutoCount(Pageable pageable);

	/** G10-S08b: cùng câu query nhưng tự viết {@code countQuery} (không có fetch, đếm đúng customer). */
	@Query(value = "select c from G10Q1_Customer c join fetch c.orders",
		countQuery = "select count(c) from G10Q1_Customer c")
	Page<Q1Customer> findPageWithOrdersExplicitCount(Pageable pageable);
}
