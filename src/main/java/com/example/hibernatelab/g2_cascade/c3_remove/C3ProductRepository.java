package com.example.hibernatelab.g2_cascade.c3_remove;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data cho {@link C3Product}. Tên có tiền tố C3 vì tên bean Spring phải duy nhất trong toàn ứng dụng.
 * Chỉ dùng ở bước WHEN của test khi cần (ví dụ G2-S09, S13), không dùng để assert dữ liệu.
 */
public interface C3ProductRepository extends JpaRepository<C3Product, Long> {
}
