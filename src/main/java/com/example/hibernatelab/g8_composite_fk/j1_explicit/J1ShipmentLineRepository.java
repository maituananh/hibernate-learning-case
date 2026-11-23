package com.example.hibernatelab.g8_composite_fk.j1_explicit;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data cho {@link J1ShipmentLine}. Chỉ để G8-S04 xem {@code save(line)} đi theo persist hay merge
 * khi id ghép đã khác null một phần ({@code lineNo} có giá trị, {@code shipmentId} còn null).
 */
public interface J1ShipmentLineRepository extends JpaRepository<J1ShipmentLine, J1ShipmentLineId> {
}
