package com.example.hibernatelab.g11_tx_lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.hibernatelab.g11_tx_lock.t1_baseline.OrderReadService;
import com.example.hibernatelab.g11_tx_lock.t1_baseline.T1PurchaseOrder;
import com.example.hibernatelab.support.AbstractScenarioTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * Nhóm G11, Open Session in View (S12). Lab không có Web nên OSIV được GIẢ LẬP bằng đúng cơ chế Spring dùng
 * (quyết định E5): trước khi gọi service, gắn một {@code EntityManager} vào thread bằng {@code bindResource}; sau đó gỡ và đóng.
 * Interceptor/filter OSIV thật cũng chỉ làm đúng hai việc bind/unbind này quanh mỗi request.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G11OpenSessionInViewTest extends AbstractScenarioTest {

	private static final String ORDER_ITEM = G11Fixtures.T1 + ".order_item";

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private OrderReadService orderReadService;
	@Autowired
	private EntityManagerFactory emf;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { G11Fixtures.T1 };
	}

	/**
	 * G11-S12a — T1: không OSIV, chạm collection LAZY sau khi service trả về → LazyInitializationException
	 *
	 * <p>Mapping: T1 baseline. <b>Câu hỏi</b>: service {@code @Transactional} trả entity; caller đọc {@code getItems()}: được không?
	 * <p><b>Kết luận</b>: không. Transaction và persistence context đóng khi service trả về (order thành DETACHED),
	 * collection LAZY chưa nạp nên {@code LazyInitializationException} (cùng cơ chế G9-S07).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S12a | không OSIV → getItems() ngoài service ném LazyInitializationException")
	void s12a_withoutOsivLazyCollectionFailsAfterServiceReturns() {
		// ── GIVEN ──
		G11Fixtures.seedOrderWithTwoItemsT1(jdbc);

		// ── WHEN ──
		T1PurchaseOrder order = orderReadService.findOrder(1L); // [TX END] trả về là persistence context đã đóng

		// ── THEN ──
		assertThatThrownBy(() -> order.getItems().size())
			.as("order đã DETACHED, items LAZY chưa nạp, không còn session để nạp")
			.isInstanceOf(LazyInitializationException.class);
	}

	/**
	 * G11-S12b — T1: có OSIV, lazy loading chạy được nhưng SQL nằm NGOÀI transaction 🔬
	 *
	 * <p><b>Câu hỏi</b>: cùng S12a nhưng persistence context được giữ mở suốt "request" (OSIV giả lập): chuyện gì khác đi?
	 * <p><b>Kết luận</b>: hết {@code LazyInitializationException}: {@code getItems().size()} chạy thêm 1 SELECT, nhưng lúc đó
	 * KHÔNG có transaction nào (câu SQL chạy ở chế độ auto-commit trên connection mượn tạm). Lỗi bị che: trong view/serializer,
	 * mỗi order chạm collection là thêm một SELECT (N+1) mà không ai thấy ở ranh giới service. Vì vậy dự án tắt
	 * {@code open-in-view} (xem application.yml).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G11-S12b | OSIV giả lập → lazy loading chạy được, nhưng SELECT chạy NGOÀI transaction")
	void s12b_withOsivLazyLoadingWorksButRunsOutsideTransaction() {
		// ── GIVEN ──
		G11Fixtures.seedOrderWithTwoItemsT1(jdbc);
		EntityManager requestScopedEntityManager = emf.createEntityManager();
		TransactionSynchronizationManager.bindResource(emf, new EntityManagerHolder(requestScopedEntityManager)); // "filter" OSIV bắt đầu
		try {
			// ── WHEN ──
			T1PurchaseOrder order = orderReadService.findOrder(1L); // service mở/đóng transaction, nhưng EntityManager vẫn mở
			sql.reset();
			boolean transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
			int itemCount = order.getItems().size(); // lazy load NGOÀI service, NGOÀI transaction

			// ── THEN: SQL ──
			sql.print();
			sql.assertSelects(ORDER_ITEM, 1);
			assertThat(transactionActive).as("lúc lazy load không có transaction nào đang chạy").isFalse();
			assertThat(itemCount).as("lazy loading chạy được vì persistence context còn mở").isEqualTo(2);
		} finally {
			TransactionSynchronizationManager.unbindResource(emf); // "filter" OSIV kết thúc: gỡ và đóng
			requestScopedEntityManager.close();
		}
	}
}
