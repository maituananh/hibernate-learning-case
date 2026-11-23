package com.example.hibernatelab.g2_cascade.c4deep_all_chain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data cho {@link C4DeepCustomer}. Tên có tiền tố C4Deep vì tên bean Spring phải duy nhất trong toàn ứng dụng.
 * Chỉ dùng ở bước WHEN của test khi cần (ví dụ G2-S09, S13), không dùng để assert dữ liệu.
 */
public interface C4DeepCustomerRepository extends JpaRepository<C4DeepCustomer, Long> {
}
