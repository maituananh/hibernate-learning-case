package com.example.hibernatelab.g2_cascade.c1_persist;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data cho {@link C1Product}. Tên có tiền tố C1 vì tên bean Spring phải duy nhất trong toàn ứng dụng.
 * Chỉ dùng ở bước WHEN của test khi cần (ví dụ G2-S09, S13), không dùng để assert dữ liệu.
 */
public interface C1ProductRepository extends JpaRepository<C1Product, Long> {
}
