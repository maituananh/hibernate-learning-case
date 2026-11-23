package com.example.hibernatelab.g0_lifecycle;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository Spring Data cho {@link VersionedSkuProduct} (id gán tay kiểu String). Dùng ở bước WHEN của G0-S11. */
public interface VersionedSkuProductRepository extends JpaRepository<VersionedSkuProduct, String> {
}
