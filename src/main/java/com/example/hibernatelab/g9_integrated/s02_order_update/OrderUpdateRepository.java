package com.example.hibernatelab.g9_integrated.s02_order_update;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data cho {@link PurchaseOrder} của G9-S02/S08. Dùng ở G9-S08: {@code save(detachedOrder)} = merge,
 * và lỗi optimistic lock đi qua lớp dịch exception của Spring nên đổi thành {@code ObjectOptimisticLockingFailureException}.
 */
public interface OrderUpdateRepository extends JpaRepository<PurchaseOrder, Long> {
}
