package com.example.hibernatelab.g11_tx_lock.t2_versioned;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository của {@link T2Product} (cách sửa lost update bằng {@code @Version}, G11-S06a). */
public interface T2ProductRepository extends JpaRepository<T2Product, Long> {
}
