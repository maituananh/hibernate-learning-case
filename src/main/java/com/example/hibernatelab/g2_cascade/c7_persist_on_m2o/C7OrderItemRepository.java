package com.example.hibernatelab.g2_cascade.c7_persist_on_m2o;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data cho {@link C7OrderItem}. Tên có tiền tố C7 vì tên bean Spring phải duy nhất trong toàn ứng dụng.
 * Chỉ dùng ở bước WHEN của test khi cần (ví dụ G2-S09, S13), không dùng để assert dữ liệu.
 */
public interface C7OrderItemRepository extends JpaRepository<C7OrderItem, Long> {
}
