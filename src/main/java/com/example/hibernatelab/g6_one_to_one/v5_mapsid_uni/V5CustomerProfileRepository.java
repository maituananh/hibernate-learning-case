package com.example.hibernatelab.g6_one_to_one.v5_mapsid_uni;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data cho {@link V5CustomerProfile}. Chỉ để G6-S06 xem {@code save(profile)} đi theo persist hay merge
 * khi id của profile còn {@code null} (id được suy ra từ customer bởi {@code @MapsId}).
 */
public interface V5CustomerProfileRepository extends JpaRepository<V5CustomerProfile, Long> {
}
