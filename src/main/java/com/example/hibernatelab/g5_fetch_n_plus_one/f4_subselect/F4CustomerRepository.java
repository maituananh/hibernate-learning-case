package com.example.hibernatelab.g5_fetch_n_plus_one.f4_subselect;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data cho {@link F4Customer}. Chỉ cần {@code findAll()} có sẵn (G5-S08/S09 đếm SQL khi lặp orders).
 */
public interface F4CustomerRepository extends JpaRepository<F4Customer, Long> {
}
