package com.example.hibernatelab.g14_lead.i4_fixed;

import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Use case "import file lớn": BẢN ĐÃ SỬA.
 *
 * BIẾN THỂ : I4 fixed
 * BASELINE : I4 buggy
 * KHÁC BIỆT: entity id SEQUENCE + JDBC batch size cho session + cứ mỗi {@value #CHUNK} dòng thì flush rồi clear
 * HẬU QUẢ  : INSERT gom thành lô {@value #CHUNK} dòng; persistence context không bao giờ giữ quá {@value #CHUNK} entity,
 *            nên thời gian và RAM tăng tuyến tính theo số dòng thay vì phình lên
 */
@Service
public class I4FixedImportService {

	/** Cỡ một lô: vừa là batch size JDBC, vừa là chu kỳ flush/clear (hai số này nên bằng nhau). */
	public static final int CHUNK = 50;

	@PersistenceContext
	private EntityManager em;

	@Transactional
	public I4FixedImportReport importItems(int rows) {
		Session session = em.unwrap(Session.class);
		session.setJdbcBatchSize(CHUNK); // chỉ session này (E4); không bật batch toàn cục
		int peak = 0;
		for (int i = 1; i <= rows; i++) {
			em.persist(new I4FixedItem("Item-" + i, i)); // [LIFECYCLE] MANAGED, INSERT trì hoãn tới flush (SEQUENCE)
			peak = Math.max(peak, session.getStatistics().getEntityCount());
			if (i % CHUNK == 0) {
				em.flush(); // [FLUSH] đẩy cả lô CHUNK dòng xuống DB bằng một batch JDBC
				em.clear(); // [LIFECYCLE] MANAGED → DETACHED: giải phóng persistence context trước khi nạp tiếp
			}
		}
		return new I4FixedImportReport(rows, peak);
	}
}
