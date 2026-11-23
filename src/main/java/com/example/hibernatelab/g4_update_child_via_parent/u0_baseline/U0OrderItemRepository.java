package com.example.hibernatelab.g4_update_child_via_parent.u0_baseline;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository Spring Data cho {@link U0OrderItem}. Tên có tiền tố U0 vì tên bean Spring phải duy nhất trong toàn ứng dụng.
 * Dùng ở G4-S12 (sửa item trực tiếp, không qua cha) và G4-S13 (bulk JPQL update).
 */
public interface U0OrderItemRepository extends JpaRepository<U0OrderItem, Long> {

	/**
	 * Bulk JPQL UPDATE, KHÔNG làm mới persistence context: item đang MANAGED trong PC vẫn giữ quantity cũ (stale).
	 * Câu lệnh chạy thẳng vào DB, Hibernate không biết object nào bị ảnh hưởng.
	 */
	@Modifying
	@Query("update G4U0_OrderItem i set i.quantity = :quantity where i.id = :id")
	int bulkSetQuantity(@Param("id") Long id, @Param("quantity") int quantity);

	/**
	 * Như trên nhưng {@code clearAutomatically = true}: sau câu UPDATE, Spring Data gọi {@code em.clear()} nên mọi entity
	 * đang MANAGED bị DETACHED; lần {@code find} kế tiếp phải đọc lại từ DB và thấy giá trị mới.
	 */
	@Modifying(clearAutomatically = true)
	@Query("update G4U0_OrderItem i set i.quantity = :quantity where i.id = :id")
	int bulkSetQuantityAndClear(@Param("id") Long id, @Param("quantity") int quantity);
}
