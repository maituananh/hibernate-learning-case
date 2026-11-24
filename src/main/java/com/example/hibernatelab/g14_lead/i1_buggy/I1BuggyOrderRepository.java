package com.example.hibernatelab.g14_lead.i1_buggy;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository "mặc định": {@code findAll(Pageable)} có sẵn từ JpaRepository, không viết query nào. */
public interface I1BuggyOrderRepository extends JpaRepository<I1BuggyOrder, Long> {
}
