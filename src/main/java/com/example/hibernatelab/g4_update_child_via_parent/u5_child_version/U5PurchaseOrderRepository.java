package com.example.hibernatelab.g4_update_child_via_parent.u5_child_version;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data cho {@link U5PurchaseOrder}. Tên có tiền tố U5 vì tên bean Spring phải duy nhất trong toàn ứng dụng.
 * Dùng ở bước WHEN của G4-S02, S03, S11 (save(order) của Spring Data = merge với entity DETACHED).
 */
public interface U5PurchaseOrderRepository extends JpaRepository<U5PurchaseOrder, Long> {
}
