package com.example.hibernatelab.g8_composite_fk.j1p_persistable;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository Spring Data cho {@link J1PShipmentLine}, để G8-S04c so sánh {@code save()} với bản không Persistable (J1). */
public interface J1PShipmentLineRepository extends JpaRepository<J1PShipmentLine, J1PShipmentLineId> {
}
