package com.example.hibernatelab.g0_lifecycle;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository Spring Data cho {@link IdentityCustomer}. Chỉ dùng ở bước WHEN của test (G0-S06c, S11), không dùng để assert dữ liệu. */
public interface IdentityCustomerRepository extends JpaRepository<IdentityCustomer, Long> {
}
