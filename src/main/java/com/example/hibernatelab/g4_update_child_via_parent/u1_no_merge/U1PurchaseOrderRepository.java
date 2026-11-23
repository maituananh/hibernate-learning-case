package com.example.hibernatelab.g4_update_child_via_parent.u1_no_merge;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data cho {@link U1PurchaseOrder}. Tên có tiền tố U1 vì tên bean Spring phải duy nhất trong toàn ứng dụng.
 * Dùng ở bước WHEN của G4-S02, S03, S11 (save(order) của Spring Data = merge với entity DETACHED).
 */
public interface U1PurchaseOrderRepository extends JpaRepository<U1PurchaseOrder, Long> {
}
