package com.example.hibernatelab.g1_owning_side.v1_m2o_uni;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data cho {@link OrderItem} của biến thể G1V1.
 *
 * <p>Tên có tiền tố {@code G1V1} vì tên bean Spring phải duy nhất trong toàn ứng dụng (các biến thể và G0 đều có
 * entity trùng tên class). Chỉ dùng ở bước WHEN của test khi cần, không dùng để assert dữ liệu.
 */
public interface G1V1OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
