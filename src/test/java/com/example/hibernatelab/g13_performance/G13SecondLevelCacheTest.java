package com.example.hibernatelab.g13_performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;

import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.query.NativeQuery;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hibernatelab.g13_performance.k1_entity_cache.K1Brand;
import com.example.hibernatelab.g13_performance.k1_entity_cache.K1Product;
import com.example.hibernatelab.g13_performance.k2_collection_cache.K2Category;
import com.example.hibernatelab.g13_performance.k2_collection_cache.K2Entry;
import com.example.hibernatelab.g13_performance.k3_query_cache.K3Tag;
import com.example.hibernatelab.g13_performance.k4_read_only.K4Rate;
import com.example.hibernatelab.support.AbstractScenarioTest;

/**
 * Nhóm G13a, chủ đề second-level cache (cache cấp 2, dùng chung giữa các session/transaction):
 * S06 (entity cache), S07 (bulk update), S08 (collection cache), S09 (query cache), S10 (READ_ONLY).
 *
 * <p>Cấu hình (E3): {@code shared-cache-mode = ENABLE_SELECTIVE}, nên CHỈ entity có {@code @Cacheable} vào cache; query chỉ vào query cache khi
 * gọi {@code setCacheable(true)}. {@code AbstractScenarioTest} evict toàn bộ cache trước mỗi test (TRUNCATE không xoá cache).
 * Mỗi lần "tải lần hai" được làm ở TRANSACTION KHÁC, vì trong cùng transaction thì cache cấp 1 (persistence context) trả lời trước cache cấp 2.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G13SecondLevelCacheTest extends AbstractScenarioTest {

	private static final String K1 = "g13_k1_entity_cache";
	private static final String K2 = "g13_k2_collection_cache";
	private static final String K3 = "g13_k3_query_cache";
	private static final String K4 = "g13_k4_read_only";

	@Autowired
	private JdbcTemplate jdbc;

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { K1, K2, K3, K4 };
	}

	/** Bật và xoá Statistics của SessionFactory (E4). */
	private Statistics statistics() {
		Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
		stats.setStatisticsEnabled(true);
		stats.clear();
		return stats;
	}

	private void seedProductAndBrand() {
		jdbc.update("insert into " + K1 + ".product (id, name, price) values (1, 'Pen', 10)");
		jdbc.update("insert into " + K1 + ".brand (id, name) values (1, 'Acme')");
	}

	/**
	 * G13-S06 — K1: entity cache, {@code find} ở transaction thứ hai
	 *
	 * <p>Mapping: K1 ({@code @Cacheable} + READ_WRITE). <b>Câu hỏi</b>: tải cùng một sản phẩm ở hai transaction liên tiếp thì SQL gì?
	 * <p><b>Kết luận</b>: transaction 1 có 1 SELECT (rồi ghi vào cache cấp 2); transaction 2 có 0 SELECT, dữ liệu lấy từ cache (cache hit).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S06 | entity cache: find ở transaction thứ hai là 0 SELECT (cache hit)")
	void s06_secondTransactionFindIsServedFromCache() {
		// ── GIVEN ──
		seedProductAndBrand();
		Statistics stats = statistics();
		sql.reset();

		// ── WHEN ──
		inTx(() -> em.find(K1Product.class, 1L)); // [LIFECYCLE] MANAGED rồi DETACHED; cache chưa có nên xuống DB và ghi vào cache
		sql.assertSelects("product", 1);
		sql.reset();
		inTx(() -> {
			K1Product product = em.find(K1Product.class, 1L); // transaction MỚI = persistence context MỚI → hỏi cache cấp 2
			assertThat(product.getName()).as("dữ liệu vẫn đầy đủ dù không SELECT").isEqualTo("Pen");
		});
		sql.print();

		// ── THEN: SQL ──
		sql.assertSelects("product", 0);
		assertThat(stats.getSecondLevelCacheHitCount()).as("đúng một lần cache hit (transaction 2)").isEqualTo(1L);
		assertThat(stats.getSecondLevelCachePutCount()).as("chỉ lần tải đầu tiên ghi vào cache").isEqualTo(1L);
	}

	/**
	 * G13-S06b — K1 🔬: DB bị sửa NGOÀI Hibernate thì cache entity trả dữ liệu cũ
	 *
	 * <p>Mapping: K1. <b>Câu hỏi</b>: product đã nằm trong cache, rồi giá bị sửa thẳng bằng SQL/JDBC: {@code find} ở transaction sau thấy giá nào?
	 * <p><b>Kết luận</b>: giá cũ (10), 0 SELECT: cache chỉ được làm mới/xoá khi thay đổi đi qua Hibernate. Nền của sự cố G14-I5.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S06b | 🔬 sửa DB ngoài Hibernate: find vẫn trả giá cũ từ cache")
	void s06b_outOfBandUpdateLeavesCacheStale() {
		// ── GIVEN ──
		seedProductAndBrand();
		inTx(() -> em.find(K1Product.class, 1L)); // vào cache, giá 10

		// ── WHEN ──
		jdbc.update("update " + K1 + ".product set price = 99 where id = 1"); // sửa NGOÀI Hibernate
		sql.reset();
		inTx(() -> {
			K1Product product = em.find(K1Product.class, 1L);
			// ── THEN ──
			assertThat(product.getPrice()).as("cache không biết DB đã đổi nên vẫn trả giá cũ").isEqualTo(10);
		});

		// ── THEN: DB thật ──
		assertThat(db.rowById(K1 + ".product", 1L).get("price")).as("trong khi DB thật đã là 99").isEqualTo(99);
		sql.assertSelects("product", 0);
	}

	/**
	 * G13-S07a — K1 🔬: bulk JPQL {@code UPDATE} có làm hỏng cache không?
	 *
	 * <p>Mapping: K1 (hai entity cache: Product và Brand). <b>Câu hỏi</b>: bulk update bảng product thì cache của product và của brand ra sao?
	 * <p><b>Kết luận</b>: cache của Product bị xoá (lần {@code find} sau phải SELECT lại, thấy giá mới); cache của Brand còn nguyên.
	 * Bulk update đi thẳng xuống DB, không qua persistence context nên Hibernate phải tự evict vùng cache liên quan.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S07a | 🔬 bulk JPQL UPDATE xoá cache của entity bị đụng tới, entity khác còn nguyên")
	void s07a_bulkJpqlUpdateEvictsOnlyAffectedRegion() {
		// ── GIVEN ── (cả product và brand đã nằm trong cache cấp 2)
		seedProductAndBrand();
		inTx(() -> {
			em.find(K1Product.class, 1L);
			em.find(K1Brand.class, 1L);
		});

		// ── WHEN ──
		inTx(() -> em.createQuery("update G13K1_Product p set p.price = 99").executeUpdate()); // bulk: bỏ qua PC, đi thẳng DB
		sql.reset();
		inTx(() -> {
			K1Product product = em.find(K1Product.class, 1L);
			em.find(K1Brand.class, 1L);
			assertThat(product.getPrice()).as("cache product đã bị xoá nên đọc lại từ DB và thấy giá mới").isEqualTo(99);
		});
		sql.print();

		// ── THEN: SQL ──
		sql.assertSelects("product", 1);
		sql.assertSelects("brand", 0);
	}

	/**
	 * G13-S07b — K1 🔬: native {@code UPDATE} KHÔNG khai báo bảng bị đụng tới
	 *
	 * <p>Mapping: K1. <b>Câu hỏi</b>: Hibernate không parse được SQL native, nên nó xử lý cache thế nào?
	 * <p><b>Kết luận</b>: xoá TOÀN BỘ cache (cả Brand vô can), vì không biết bảng nào đã đổi.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S07b | 🔬 native UPDATE không khai query space: xoá cả cache của entity vô can")
	void s07b_nativeUpdateWithoutQuerySpaceEvictsEverything() {
		// ── GIVEN ──
		seedProductAndBrand();
		inTx(() -> {
			em.find(K1Product.class, 1L);
			em.find(K1Brand.class, 1L);
		});

		// ── WHEN ──
		inTx(() -> em.createNativeQuery("update " + K1 + ".product set price = 99").executeUpdate());
		sql.reset();
		inTx(() -> {
			em.find(K1Product.class, 1L);
			em.find(K1Brand.class, 1L);
		});
		sql.print();

		// ── THEN: SQL ──
		sql.assertSelects("product", 1);
		sql.assertSelects("brand", 1);
	}

	/**
	 * G13-S07c — K1 🔬: native {@code UPDATE} có khai báo {@code addSynchronizedEntityClass}
	 *
	 * <p>Mapping: K1. <b>Câu hỏi</b>: nói cho Hibernate biết native SQL chỉ đụng tới Product thì cache Brand có sống sót không?
	 * <p><b>Kết luận</b>: có; chỉ cache của Product bị xoá, như bulk JPQL ở S07a.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S07c | 🔬 native UPDATE khai addSynchronizedEntityClass: chỉ xoá cache của Product")
	void s07c_nativeUpdateWithQuerySpaceEvictsOnlyThatEntity() {
		// ── GIVEN ──
		seedProductAndBrand();
		inTx(() -> {
			em.find(K1Product.class, 1L);
			em.find(K1Brand.class, 1L);
		});

		// ── WHEN ──
		inTx(() -> {
			NativeQuery<?> update = em.createNativeQuery("update " + K1 + ".product set price = 99").unwrap(NativeQuery.class);
			update.addSynchronizedEntityClass(K1Product.class); // "câu SQL này chỉ đụng bảng của Product"
			update.executeUpdate();
		});
		sql.reset();
		inTx(() -> {
			em.find(K1Product.class, 1L);
			em.find(K1Brand.class, 1L);
		});
		sql.print();

		// ── THEN: SQL ──
		sql.assertSelects("product", 1);
		sql.assertSelects("brand", 0);
	}

	/**
	 * G13-S08 — K2 🔬: collection cache chỉ lưu danh sách id
	 *
	 * <p>Mapping: K2 (Category và collection {@code entries} được cache, {@code Entry} thì không). <b>Câu hỏi</b>: ở transaction thứ hai,
	 * đọc 3 phần tử của collection đã cache thì SQL gì?
	 * <p><b>Kết luận</b>: category và collection đều cache hit (0 SELECT khi mở collection: Hibernate chỉ có 3 id, tạo 3 proxy); nhưng chạm vào dữ liệu
	 * từng phần tử thì 3 SELECT riêng theo id, vì Entry không được cache (N+1 hiện lại dù có cache collection). Muốn hết N+1 phải cache cả Entry.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S08 | 🔬 collection cache chỉ lưu id: phần tử không được cache thì bị SELECT từng cái")
	void s08_collectionCacheStoresOnlyIds() {
		// ── GIVEN ──
		jdbc.update("insert into " + K2 + ".category (id, name) values (1, 'Stationery')");
		for (int i = 1; i <= 3; i++) {
			jdbc.update("insert into " + K2 + ".entry (id, label, category_id) values (?, ?, 1)", i, "Entry-" + i);
		}
		inTx(() -> em.find(K2Category.class, 1L).getEntries().size()); // làm nóng cache entity và cache collection
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			K2Category category = em.find(K2Category.class, 1L); // cache hit
			List<K2Entry> entries = category.getEntries(); // collection cache hit: chỉ có 3 id
			for (K2Entry entry : entries) {
				entry.getLabel(); // cần dữ liệu phần tử → entity Entry không có trong cache
			}
		});
		sql.print();

		// ── THEN: SQL ──
		sql.assertSelects("category", 0);
		sql.assertSelects("entry", 3);
	}

	/**
	 * G13-S09a — K3 🔬: query cache khi entity KHÔNG được cache
	 *
	 * <p>Mapping: K3 ({@code Tag} không {@code @Cacheable}). <b>Câu hỏi</b>: chạy lại một query cacheable ở transaction thứ hai, nếu entity
	 * không nằm trong cache cấp 2 thì có phải SELECT từng entity theo id không?
	 * <p><b>Kết luận</b>: KHÔNG như giả thuyết. Query cache của Hibernate 7.4 hit và trả luôn entity đã khởi tạo đủ dữ liệu, 0 SELECT, dù
	 * cache entity không có gì (cache cấp 2 không ghi/đọc entity nào). Xem RESULTS.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S09a | 🔬 query cache + entity không cache: vẫn 0 SELECT (khác giả thuyết), entity đủ dữ liệu")
	void s09a_queryCacheWithoutEntityCacheStillAvoidsSelects() {
		// ── GIVEN ──
		for (int i = 1; i <= 3; i++) {
			jdbc.update("insert into " + K3 + ".tag (id, label, active) values (?, ?, true)", i, "Tag-" + i);
		}
		Statistics stats = statistics();
		inTx(() -> em.createQuery("select t from G13K3_Tag t where t.active = true order by t.id", K3Tag.class)
			.setHint("org.hibernate.cacheable", true).getResultList()); // lần 1: xuống DB, ghi query cache
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			List<K3Tag> tags = em.createQuery("select t from G13K3_Tag t where t.active = true order by t.id", K3Tag.class)
				.setHint("org.hibernate.cacheable", true).getResultList(); // lần 2, transaction MỚI: hỏi query cache
			assertThat(tags).as("đủ 3 tag").hasSize(3);
			assertThat(tags.get(0).getLabel()).as("entity đã có đủ dữ liệu, không cần SELECT thêm").isEqualTo("Tag-1");
			assertThat(Hibernate.isInitialized(tags.get(2))).as("không phải proxy chờ nạp").isTrue();
		});
		sql.print();

		// ── THEN: SQL ──
		sql.assertTotal(0);
		assertThat(stats.getQueryCacheHitCount()).as("query cache có hit ở lần hai").isEqualTo(1L);
		assertThat(stats.getSecondLevelCachePutCount()).as("cache cấp 2 không ghi entity nào (Tag không @Cacheable)").isEqualTo(0L);
	}

	/**
	 * G13-S09c — K3 🔬: query cache trả dữ liệu CŨ khi DB bị sửa ngoài Hibernate
	 *
	 * <p>Mapping: K3. <b>Câu hỏi</b>: sau khi query đã vào cache, ai đó sửa thẳng DB bằng JDBC/SQL tay thì query cacheable trả gì?
	 * <p><b>Kết luận</b>: dữ liệu cũ. Hibernate chỉ biết bảng đã đổi khi thay đổi đi QUA Hibernate; sửa ngoài (script, người khác, service khác)
	 * thì cache cũ cứ tồn tại. Đây là cơ chế đứng sau sự cố "admin sửa giá mà khách vẫn thấy giá cũ" (G14-I5).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S09c | 🔬 query cache trả dữ liệu cũ khi DB bị sửa ngoài Hibernate")
	void s09c_queryCacheReturnsStaleDataAfterOutOfBandUpdate() {
		// ── GIVEN ──
		jdbc.update("insert into " + K3 + ".tag (id, label, active) values (1, 'Old', true)");
		inTx(() -> em.createQuery("select t from G13K3_Tag t where t.active = true", K3Tag.class).setHint("org.hibernate.cacheable", true).getResultList());

		// ── WHEN ──
		jdbc.update("update " + K3 + ".tag set label = 'New' where id = 1"); // sửa NGOÀI Hibernate: cache không hay biết
		sql.reset();
		inTx(() -> {
			List<K3Tag> tags = em.createQuery("select t from G13K3_Tag t where t.active = true", K3Tag.class)
				.setHint("org.hibernate.cacheable", true).getResultList();
			// ── THEN ──
			assertThat(tags.get(0).getLabel()).as("query cache không biết DB đã đổi nên vẫn trả giá trị cũ").isEqualTo("Old");
		});

		// ── THEN: DB thật ──
		assertThat(db.rowById(K3 + ".tag", 1L).get("label")).as("trong khi DB thật đã là New").isEqualTo("New");
		sql.assertTotal(0);
	}

	/**
	 * G13-S09b — K1 🔬: query cache khi entity ĐƯỢC cache
	 *
	 * <p>Mapping: K1 ({@code Product} {@code @Cacheable}). <b>Câu hỏi</b>: cùng cách làm như S09a nhưng entity có trong cache cấp 2.
	 * <p><b>Kết luận</b>: 0 SELECT: query cache cho id, cache entity cho dữ liệu. Hai cache bổ sung cho nhau.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S09b | 🔬 query cache + entity có cache: 0 SELECT ở lần chạy thứ hai")
	void s09b_queryCacheWithEntityCacheIsFullyServedFromCache() {
		// ── GIVEN ──
		seedProductAndBrand();
		inTx(() -> em.createQuery("select p from G13K1_Product p where p.price > 5", K1Product.class).setHint("org.hibernate.cacheable", true).getResultList());
		sql.reset();

		// ── WHEN ──
		inTx(() -> {
			List<K1Product> products = em.createQuery("select p from G13K1_Product p where p.price > 5", K1Product.class)
				.setHint("org.hibernate.cacheable", true).getResultList();
			assertThat(products.get(0).getName()).as("dữ liệu đầy đủ").isEqualTo("Pen");
		});
		sql.print();

		// ── THEN: SQL ──
		sql.assertTotal(0);
	}

	/**
	 * G13-S10 — K4 🔬: sửa entity nằm trong cache {@code READ_ONLY}
	 *
	 * <p>Mapping: K4 (READ_ONLY). <b>Câu hỏi</b>: entity đã vào cache READ_ONLY, sau đó có người sửa nó thì sao?
	 * <p><b>Kết luận</b>: UPDATE vẫn được gửi, nhưng lúc commit cập nhật cache thì {@code UnsupportedOperationException: Can't update read-only object}
	 * (bọc trong {@code TransactionSystemException}); transaction rollback, DB giữ giá cũ. READ_ONLY chỉ hợp với dữ liệu tham chiếu không bao giờ sửa.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G13-S10 | 🔬 sửa entity trong cache READ_ONLY: lỗi lúc commit, rollback")
	void s10_updatingReadOnlyCachedEntityFails() {
		// ── GIVEN ──
		jdbc.update("insert into " + K4 + ".rate (id, currency, price) values (1, 'USD', 100)");
		inTx(() -> em.find(K4Rate.class, 1L)); // vào cache

		// ── WHEN ──
		Throwable failure = catchThrowable(() -> inTx(() -> {
			K4Rate rate = em.find(K4Rate.class, 1L);
			rate.setPrice(200); // dirty checking sẽ UPDATE khi commit
		}));
		sql.print();

		// ── THEN ──
		Throwable root = failure;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		assertThat(root).as("READ_ONLY cache từ chối cập nhật entity, nguyên nhân gốc nằm ở cache chứ không ở DB")
			.isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("read-only");
		// ── THEN: DB thật ──
		assertThat(db.rowById(K4 + ".rate", 1L).get("price")).as("transaction rollback nên DB giữ giá cũ 100").isEqualTo(100);
	}
}
