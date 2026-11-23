package com.example.hibernatelab.g9_integrated.s06_delete_strategies;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * G9-S06: bốn cách xoá một Customer cùng toàn bộ order và item của nó. Mỗi method {@code @Transactional}
 * là một transaction: thành công thì COMMIT, gặp lỗi thì ROLLBACK toàn bộ.
 */
@Service
public class CustomerDeletionService {

	@PersistenceContext
	private EntityManager em;

	/**
	 * (a) cascade REMOVE theo chuỗi: chỉ gọi {@code em.remove(customer)}, JPA lo phần còn lại.
	 * Hibernate phải TẢI customer, rồi TẢI orders, rồi TẢI items của TỪNG order (để biết xoá dòng nào),
	 * sau đó DELETE từng item, từng order, cuối cùng customer (con trước cha).
	 */
	@Transactional
	public void deleteByCascadeRemove(Long customerId) {
		com.example.hibernatelab.g9_integrated.s06_delete_strategies.a_cascade_remove.Customer customer = em.find(
			com.example.hibernatelab.g9_integrated.s06_delete_strategies.a_cascade_remove.Customer.class, customerId);
		em.remove(customer); // [LIFECYCLE] customer, orders, items: MANAGED → REMOVED (cascade); DELETE chạy lúc flush/commit
	}

	/**
	 * (b) {@code @OnDelete(CASCADE)}: chỉ 1 câu DELETE customer. PostgreSQL tự xoá order rồi item nhờ
	 * {@code ON DELETE CASCADE} trên FK. Hibernate KHÔNG tải con và KHÔNG biết con đã biến mất.
	 */
	@Transactional
	public void deleteByOnDelete(Long customerId) {
		com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete.Customer customer = em.find(
			com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete.Customer.class, customerId);
		em.remove(customer);
	}

	/**
	 * (c) bulk delete TỪ DƯỚI LÊN: item trước, rồi order, rồi customer. Đúng 3 câu DELETE, không SELECT nào,
	 * nhưng bỏ qua hoàn toàn persistence context (entity đã tải, nếu có, sẽ thành "ma"). Thứ tự bắt buộc vì FK.
	 */
	@Transactional
	public void deleteByBulkBottomUp(Long customerId) {
		em.createQuery("delete from G9S06C_OrderItem i where i.order.id in "
			+ "(select o.id from G9S06C_PurchaseOrder o where o.customer.id = :customerId)")
			.setParameter("customerId", customerId).executeUpdate();
		em.createQuery("delete from G9S06C_PurchaseOrder o where o.customer.id = :customerId")
			.setParameter("customerId", customerId).executeUpdate();
		em.createQuery("delete from G9S06C_Customer c where c.id = :customerId")
			.setParameter("customerId", customerId).executeUpdate();
	}

	/**
	 * (d) KHÔNG chiến lược nào: chỉ {@code em.remove(customer)} trên mapping trần. Lúc flush, DELETE customer
	 * bị PostgreSQL từ chối vì còn order trỏ tới (vi phạm FK) → transaction ROLLBACK, dữ liệu nguyên vẹn.
	 */
	@Transactional
	public void deleteWithoutStrategy(Long customerId) {
		com.example.hibernatelab.g9_integrated.s06_delete_strategies.c_no_strategy.Customer customer = em.find(
			com.example.hibernatelab.g9_integrated.s06_delete_strategies.c_no_strategy.Customer.class, customerId);
		em.remove(customer);
	}
}
