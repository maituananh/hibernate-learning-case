package com.example.hibernatelab.g1_owning_side.v6_bidi_owner_parent;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data cho {@link Product} của biến thể G1V6.
 *
 * <p>Tên có tiền tố {@code G1V6} vì tên bean Spring phải duy nhất trong toàn ứng dụng (các biến thể và G0 đều có
 * entity trùng tên class). Chỉ dùng ở bước WHEN của test khi cần, không dùng để assert dữ liệu.
 */
public interface G1V6ProductRepository extends JpaRepository<Product, Long> {
}
