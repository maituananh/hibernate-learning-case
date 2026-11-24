package com.example.hibernatelab.g11_tx_lock.t1_baseline;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** G11-S12: service trả về ENTITY (không phải DTO) nên collection LAZY chưa nạp khi transaction kết thúc. */
@Service
public class OrderReadService {

	@PersistenceContext
	private EntityManager em;

	@Transactional(readOnly = true)
	public T1PurchaseOrder findOrder(Long orderId) {
		return em.find(T1PurchaseOrder.class, orderId);
	}
}
