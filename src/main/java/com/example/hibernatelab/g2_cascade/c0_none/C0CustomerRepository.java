package com.example.hibernatelab.g2_cascade.c0_none;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data cho {@link C0Customer}. Tên có tiền tố C0 vì tên bean Spring phải duy nhất trong toàn ứng dụng.
 * Chỉ dùng ở bước WHEN của test khi cần (ví dụ G2-S09, S13), không dùng để assert dữ liệu.
 */
public interface C0CustomerRepository extends JpaRepository<C0Customer, Long> {
}
