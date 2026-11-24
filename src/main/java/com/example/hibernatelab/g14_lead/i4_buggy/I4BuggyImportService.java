package com.example.hibernatelab.g14_lead.i4_buggy;

import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Use case "import file lớn": BẢN CÓ LỖI.
 *
 * BIẾN THỂ : I4 buggy
 * BASELINE : không có (G14 được miễn quy tắc một khác biệt, E7)
 * KHÁC BIỆT: persist toàn bộ trong một transaction, không flush/clear; entity dùng id IDENTITY
 * HẬU QUẢ  : N INSERT riêng lẻ (không batch) và persistence context giữ N entity. Khi flush Hibernate dirty-check cả N entity,
 *            nên file càng lớn càng chậm và càng tốn RAM (OutOfMemoryError nếu đủ lớn).
 */
@Service
public class I4BuggyImportService {

	@PersistenceContext
	private EntityManager em;

	@Transactional
	public I4BuggyImportReport importItems(int rows) {
		Session session = em.unwrap(Session.class);
		int peak = 0;
		for (int i = 1; i <= rows; i++) {
			em.persist(new I4BuggyItem("Item-" + i, i)); // [LIFECYCLE] MANAGED: INSERT ngay (IDENTITY) và entity nằm lại trong persistence context
			peak = Math.max(peak, session.getStatistics().getEntityCount());
		}
		return new I4BuggyImportReport(rows, peak);
	}
}
