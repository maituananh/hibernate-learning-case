package com.example.hibernatelab.g5_fetch_n_plus_one.f3_batch_size;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data cho {@link F3Customer}. Chỉ cần {@code findAll()} có sẵn (G5-S08/S09 đếm SQL khi lặp orders).
 */
public interface F3CustomerRepository extends JpaRepository<F3Customer, Long> {
}
