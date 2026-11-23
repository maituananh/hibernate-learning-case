package com.example.hibernatelab.g1_owning_side.v4_bidi_mappedby;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data cho {@link OrderItem} của biến thể G1V4.
 *
 * <p>Tên có tiền tố {@code G1V4} vì tên bean Spring phải duy nhất trong toàn ứng dụng (các biến thể và G0 đều có
 * entity trùng tên class). Chỉ dùng ở bước WHEN của test khi cần, không dùng để assert dữ liệu.
 */
public interface G1V4OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
