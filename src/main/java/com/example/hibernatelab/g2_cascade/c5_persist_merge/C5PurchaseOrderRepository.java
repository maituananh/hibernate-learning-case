package com.example.hibernatelab.g2_cascade.c5_persist_merge;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data cho {@link C5PurchaseOrder}. Tên có tiền tố C5 vì tên bean Spring phải duy nhất trong toàn ứng dụng.
 * Chỉ dùng ở bước WHEN của test khi cần (ví dụ G2-S09, S13), không dùng để assert dữ liệu.
 */
public interface C5PurchaseOrderRepository extends JpaRepository<C5PurchaseOrder, Long> {
}
