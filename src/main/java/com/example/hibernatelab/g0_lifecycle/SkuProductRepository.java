package com.example.hibernatelab.g0_lifecycle;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository Spring Data cho {@link SkuProduct} (id gán tay kiểu String). Dùng ở bước WHEN của G0-S11. */
public interface SkuProductRepository extends JpaRepository<SkuProduct, String> {
}
