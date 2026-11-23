package com.example.hibernatelab.g9_integrated.s05_bulk_modifying;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository cho bulk UPDATE của G9-S05. Tên có tiền tố Bulk vì tên bean Spring phải duy nhất toàn ứng dụng.
 * {@code @Modifying} là bắt buộc cho câu JPQL update/delete. Cần có transaction đang chạy (test dùng inTx).
 */
public interface BulkProductRepository extends JpaRepository<Product, Long> {

	/**
	 * Bulk UPDATE KHÔNG dọn persistence context: entity Product đã tải vẫn giữ giá cũ trong bộ nhớ.
	 * Cũng KHÔNG chạy @Version/@PreUpdate/dirty checking: Hibernate chỉ chuyển câu JPQL thành một câu UPDATE.
	 */
	@Modifying
	@Query("update G9S05_Product p set p.price = p.price * 1.1")
	int raisePricesKeepingPersistenceContext();

	/**
	 * Như trên nhưng {@code clearAutomatically = true}: sau câu UPDATE, Spring gọi {@code em.clear()}.
	 * Đồng bộ lại với DB, nhưng MỌI entity đang MANAGED (kể cả loại không liên quan) thành DETACHED, và thay đổi chưa flush mất sạch.
	 */
	@Modifying(clearAutomatically = true)
	@Query("update G9S05_Product p set p.price = p.price * 1.1")
	int raisePricesAndClearPersistenceContext();
}
