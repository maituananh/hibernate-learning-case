package com.example.hibernatelab.g7_many_to_many;

import static com.example.hibernatelab.support.LifecycleInspector.State.MANAGED;
import static com.example.hibernatelab.support.LifecycleInspector.assertState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.hibernatelab.g7_many_to_many.m1_owner_product.M1Product;
import com.example.hibernatelab.g7_many_to_many.m1_owner_product.M1Tag;
import com.example.hibernatelab.g7_many_to_many.m2_defaults.M2Product;
import com.example.hibernatelab.g7_many_to_many.m2_defaults.M2Tag;
import com.example.hibernatelab.g7_many_to_many.m3_owner_tag.M3Product;
import com.example.hibernatelab.g7_many_to_many.m3_owner_tag.M3Tag;
import com.example.hibernatelab.g7_many_to_many.m4_no_mappedby.M4Product;
import com.example.hibernatelab.g7_many_to_many.m4_no_mappedby.M4Tag;
import com.example.hibernatelab.support.AbstractScenarioTest;
import com.example.hibernatelab.support.SchemaInspector.ForeignKey;

/**
 * Nhóm G7, phần "phía nào là owner của bảng nối?": M1 (owner = Product), M2 (không @JoinTable), M3 (owner = Tag),
 * M4 (quên mappedBy). S01 (DDL), S02 (add ở owner vs inverse), S03 (lật owner), S04 (hai bảng nối),
 * S07 (xoá owner vs xoá inverse), S13 (N+1).
 *
 * <p>Điểm cần nắm: {@code @ManyToMany} là hai {@code @OneToMany} dính vào một bảng nối. Bảng nối chỉ được GHI bởi
 * đúng MỘT phía (owning side, phía KHÔNG có {@code mappedBy}). Phía còn lại chỉ để ĐỌC.
 */
@TestMethodOrder(MethodOrderer.MethodName.class) // thứ tự chạy = thứ tự đọc trong file (D18)
class G7OwnerSideTest extends AbstractScenarioTest {

	private static final String M1 = "g7_m1_owner_product";
	private static final String M2 = "g7_m2_defaults";
	private static final String M3 = "g7_m3_owner_tag";
	private static final String M4 = "g7_m4_no_mappedby";

	@Override
	protected String[] schemasUnderTest() {
		return new String[] { M1, M2, M3, M4 };
	}

	/** Giá trị của một cột trên mọi dòng của bảng (đọc DB thật), để so sánh "bảng nối chứa những id nào". */
	private List<Object> columnValues(String table, String column) {
		List<Object> values = new ArrayList<>();
		for (Map<String, Object> row : db.rowsOf(table)) {
			values.add(row.get(column));
		}
		return values;
	}

	/**
	 * G7-S01a — M1: DDL của bảng nối khai tường minh
	 *
	 * <p>Mapping: {@code Product.tags @ManyToMany @JoinTable(name = "product_tag", joinColumns = product_id,
	 * inverseJoinColumns = tag_id) Set<Tag>}; {@code Tag.products (mappedBy = "tags")}.
	 * <p><b>Câu hỏi</b>: bảng nối trông thế nào, và với {@code Set} nó có PK không?
	 * <p><b>Kết luận</b>: 3 bảng (product, tag, product_tag). {@code product_tag(product_id, tag_id)} có hai FK
	 * và PK ghép {@code (product_id, tag_id)}, nên DB tự chặn cặp trùng. Chỉ có MỘT bảng nối dù quan hệ hai chiều.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S01a | M1: bảng nối product_tag(product_id, tag_id) với Set có PK ghép")
	void s01a_m1JoinTableDdl() {
		// ── THEN: DDL ──
		schema.printDdl(M1);
		assertThat(schema.tablesOf(M1)).as("hai entity và đúng MỘT bảng nối").containsExactly("product", "product_tag", "tag");
		ForeignKey toProduct = schema.foreignKeyOnColumn(M1, "product_tag", "product_id");
		ForeignKey toTag = schema.foreignKeyOnColumn(M1, "product_tag", "tag_id");
		assertThat(toProduct.targetTable()).as("product_id trỏ sang bảng product").isEqualTo(M1 + ".product");
		assertThat(toTag.targetTable()).as("tag_id trỏ sang bảng tag").isEqualTo(M1 + ".tag");
		assertThat(schema.primaryKeyOf(M1, "product_tag"))
			.as("kiểu Set không cho phép trùng nên Hibernate đặt PK ghép trên hai cột FK")
			.containsExactly("product_id", "tag_id");
	}

	/**
	 * G7-S01b — M2 🔬: bảng nối khi KHÔNG có {@code @JoinTable}
	 *
	 * <p>Mapping: như M1 nhưng bỏ {@code @JoinTable}.
	 * <p><b>Câu hỏi</b>: Hibernate tự đặt tên bảng nối, tên cột, và đặt bảng nối ở schema nào?
	 * <p><b>Kết luận</b>: xem assert bên dưới và docs/RESULTS.md. Bài học: đừng để mặc định; tên mặc định phụ thuộc
	 * naming strategy và bảng nối có thể rơi ra ngoài schema của entity (giống G1-S07).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S01b | M2 🔬: không @JoinTable thì Hibernate tự đặt tên bảng nối và tên cột")
	void s01b_m2DefaultJoinTable() {
		// ── THEN: DDL ──
		schema.printDdl(M2);
		assertThat(schema.tablesOf(M2))
			.as("schema của M2 chỉ có hai bảng entity: bảng nối KHÔNG nằm trong schema của entity")
			.containsExactly("product", "tag");
		assertThat(schema.tablesOf("public"))
			.as("bảng nối mặc định rơi vào schema public, tên = <bảng owner>_<tên field của owner> (product + tags)")
			.contains("product_tags");
		assertThat(schema.columnsOf("public", "product_tags")).extracting(column -> column.name())
			.as("tên cột mặc định lấy theo TÊN FIELD (products_id, tags_id), không phải product_id/tag_id như M1")
			.containsExactly("products_id", "tags_id");
		assertThat(schema.primaryKeyOf("public", "product_tags"))
			.as("Set vẫn có PK ghép trên hai cột FK, giống M1")
			.containsExactly("products_id", "tags_id");
		assertThat(schema.foreignKeyOnColumn("public", "product_tags", "products_id").targetTable())
			.as("FK vẫn trỏ đúng sang bảng product trong schema của M2")
			.isEqualTo(M2 + ".product");
	}

	/**
	 * G7-S02a — M1: thêm tag ở phía OWNER ({@code product.getTags().add(tag)})
	 *
	 * <p>Mapping: M1, owner = Product.
	 * <p><b>Câu hỏi</b>: thêm tag vào collection của owner thì Hibernate ghi gì?
	 * <p><b>Kết luận</b>: đúng 1 INSERT vào {@code product_tag}; product và tag không bị UPDATE.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S02a | M1: add ở owner (Product.tags) sinh 1 INSERT product_tag")
	void s02a_m1AddOnOwnerSideWritesJoinTable() {
		// ── GIVEN: product và tag đã tồn tại, chưa liên kết ──
		M1Product phone = new M1Product("Phone");
		M1Tag tagNew = new M1Tag("new");
		inTx(() -> {
			em.persist(phone);
			em.persist(tagNew);
		});

		// ── WHEN: add tag vào Product.tags (phía owning) ──
		inTx(() -> {
			M1Product managedPhone = em.find(M1Product.class, phone.getId());
			M1Tag managedTag = em.find(M1Tag.class, tagNew.getId());
			// [LIFECYCLE] cả hai đều MANAGED trong transaction này
			assertState(em, managedPhone, MANAGED);
			assertState(em, managedTag, MANAGED);
			sql.reset();
			managedPhone.getTags().add(managedTag);
			// [FLUSH] lúc commit: Hibernate thấy collection của owner thay đổi → INSERT bảng nối
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("product_tag", 1);
		sql.assertUpdates("product", 0);
		sql.assertUpdates("tag", 0);

		// ── THEN: DB thật ──
		db.print(M1 + ".product_tag");
		assertThat(columnValues(M1 + ".product_tag", "product_id")).as("dòng nối trỏ về phone").containsExactly(phone.getId());
		assertThat(columnValues(M1 + ".product_tag", "tag_id")).as("dòng nối trỏ tới tag new").containsExactly(tagNew.getId());
	}

	/**
	 * G7-S02b — M1: thêm product ở phía INVERSE ({@code tag.getProducts().add(product)})
	 *
	 * <p>Mapping: M1, {@code Tag.products} là inverse side ({@code mappedBy = "tags"}).
	 * <p><b>Câu hỏi</b>: add ở phía mappedBy có ghi bảng nối không?
	 * <p><b>Kết luận</b>: KHÔNG. 0 INSERT, bảng nối vẫn trống. Collection inverse chỉ là "view" để đọc; Hibernate không
	 * dùng nó để tính SQL. Quên đồng bộ phía owner là lỗi im lặng.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S02b | M1: add ở inverse (Tag.products) KHÔNG ghi gì xuống bảng nối")
	void s02b_m1AddOnInverseSideIsIgnored() {
		// ── GIVEN: product và tag đã tồn tại, chưa liên kết ──
		M1Product phone = new M1Product("Phone");
		M1Tag tagNew = new M1Tag("new");
		inTx(() -> {
			em.persist(phone);
			em.persist(tagNew);
		});

		// ── WHEN: add product vào Tag.products (phía inverse) ──
		inTx(() -> {
			M1Product managedPhone = em.find(M1Product.class, phone.getId());
			M1Tag managedTag = em.find(M1Tag.class, tagNew.getId());
			sql.reset();
			managedTag.getProducts().add(managedPhone);
			// [FLUSH] lúc commit: Hibernate bỏ qua collection inverse → không có SQL ghi
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("product_tag", 0);

		// ── THEN: DB thật ──
		assertThat(db.countOf(M1 + ".product_tag")).as("owner (Product.tags) không đổi nên bảng nối vẫn trống").isZero();
	}

	/**
	 * G7-S03a — M3: lật owner sang Tag, add ở {@code Tag.products}
	 *
	 * <p>Mapping: M3, {@code @JoinTable} nằm trên {@code Tag.products}; {@code Product.tags (mappedBy = "products")}.
	 * <p><b>Câu hỏi</b>: nếu owner là Tag thì phía nào ghi được bảng nối?
	 * <p><b>Kết luận</b>: ngược với M1: {@code tag.getProducts().add(product)} sinh 1 INSERT.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S03a | M3: owner là Tag, add ở Tag.products sinh 1 INSERT product_tag")
	void s03a_m3AddOnOwnerTagWritesJoinTable() {
		// ── GIVEN ──
		M3Product phone = new M3Product("Phone");
		M3Tag tagNew = new M3Tag("new");
		inTx(() -> {
			em.persist(phone);
			em.persist(tagNew);
		});

		// ── WHEN: add product vào Tag.products (phía owning của M3) ──
		inTx(() -> {
			M3Product managedPhone = em.find(M3Product.class, phone.getId());
			M3Tag managedTag = em.find(M3Tag.class, tagNew.getId());
			sql.reset();
			managedTag.getProducts().add(managedPhone);
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInserts("product_tag", 1);

		// ── THEN: DB thật ──
		db.print(M3 + ".product_tag");
		assertThat(columnValues(M3 + ".product_tag", "product_id")).as("cột product_id lưu id phone").containsExactly(phone.getId());
		assertThat(columnValues(M3 + ".product_tag", "tag_id")).as("cột tag_id lưu id tag").containsExactly(tagNew.getId());
	}

	/**
	 * G7-S03b — M3: add ở {@code Product.tags} (giờ là inverse)
	 *
	 * <p>Mapping: M3.
	 * <p><b>Câu hỏi</b>: cùng thao tác {@code product.getTags().add(tag)} của M1, đổi owner thì sao?
	 * <p><b>Kết luận</b>: không còn ghi gì (0 INSERT). Câu "đặt owner bên A hay bên B" quyết định thao tác nào
	 * có hiệu lực: nên đặt owner ở phía mà code thường xuyên thay đổi liên kết.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S03b | M3: add ở Product.tags (inverse) bị bỏ qua, ngược với M1")
	void s03b_m3AddOnInverseProductIsIgnored() {
		// ── GIVEN ──
		M3Product phone = new M3Product("Phone");
		M3Tag tagNew = new M3Tag("new");
		inTx(() -> {
			em.persist(phone);
			em.persist(tagNew);
		});

		// ── WHEN: add tag vào Product.tags (phía inverse của M3) ──
		inTx(() -> {
			M3Product managedPhone = em.find(M3Product.class, phone.getId());
			M3Tag managedTag = em.find(M3Tag.class, tagNew.getId());
			sql.reset();
			managedPhone.getTags().add(managedTag);
		});

		// ── THEN: SQL + DB thật ──
		sql.print();
		sql.assertInserts("product_tag", 0);
		assertThat(db.countOf(M3 + ".product_tag")).as("inverse side không ghi bảng nối").isZero();
	}

	/**
	 * G7-S04a — M4: DDL khi quên {@code mappedBy}
	 *
	 * <p>Mapping: M4, cả {@code Product.tags} và {@code Tag.products} đều có {@code @JoinTable} riêng, không mappedBy.
	 * <p><b>Câu hỏi</b>: Hibernate hiểu "hai phía của cùng một quan hệ" hay "hai quan hệ độc lập"?
	 * <p><b>Kết luận</b>: hai quan hệ độc lập, nên có HAI bảng nối: {@code product_tags} và {@code tag_products}.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S04a | M4: quên mappedBy sinh hai bảng nối product_tags và tag_products")
	void s04a_m4TwoJoinTablesInDdl() {
		// ── THEN: DDL ──
		schema.printDdl(M4);
		assertThat(schema.tablesOf(M4))
			.as("quên mappedBy: mỗi phía tự có bảng nối của riêng mình")
			.containsExactly("product", "product_tags", "tag", "tag_products");
	}

	/**
	 * G7-S04b — M4: set liên kết ở CẢ HAI phía
	 *
	 * <p>Mapping: M4.
	 * <p><b>Câu hỏi</b>: set cả hai phía thì dữ liệu ra sao?
	 * <p><b>Kết luận</b>: mỗi bảng nối có 1 dòng, cùng một liên kết bị ghi 2 lần ở 2 nơi (dữ liệu dư thừa).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S04b | M4: set cả hai phía thì mỗi bảng nối có 1 dòng (ghi 2 lần)")
	void s04b_m4BothSidesWriteBothTables() {
		// ── GIVEN ──
		M4Product phone = new M4Product("Phone");
		M4Tag tagNew = new M4Tag("new");
		inTx(() -> {
			em.persist(phone);
			em.persist(tagNew);
		});

		// ── WHEN: add ở cả hai phía ──
		inTx(() -> {
			M4Product managedPhone = em.find(M4Product.class, phone.getId());
			M4Tag managedTag = em.find(M4Tag.class, tagNew.getId());
			sql.reset();
			managedPhone.getTags().add(managedTag);
			managedTag.getProducts().add(managedPhone);
		});

		// ── THEN: SQL + DB thật ──
		sql.print();
		sql.assertInserts("product_tags", 1);
		sql.assertInserts("tag_products", 1);
		assertThat(db.countOf(M4 + ".product_tags")).as("phía Product ghi bảng product_tags").isEqualTo(1);
		assertThat(db.countOf(M4 + ".tag_products")).as("phía Tag ghi bảng tag_products").isEqualTo(1);
	}

	/**
	 * G7-S04c — M4: chỉ set MỘT phía
	 *
	 * <p>Mapping: M4.
	 * <p><b>Câu hỏi</b>: chỉ {@code product.getTags().add(tag)} thì hai bảng nối có mâu thuẫn không?
	 * <p><b>Kết luận</b>: có. {@code product_tags} có 1 dòng, {@code tag_products} có 0: DB nói "product có tag"
	 * nhưng cũng nói "tag không có product". Cách sửa: dùng {@code mappedBy} (M1).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S04c | M4: chỉ set một phía thì hai bảng nối mâu thuẫn nhau")
	void s04c_m4OneSideLeavesTablesInconsistent() {
		// ── GIVEN ──
		M4Product phone = new M4Product("Phone");
		M4Tag tagNew = new M4Tag("new");
		inTx(() -> {
			em.persist(phone);
			em.persist(tagNew);
		});

		// ── WHEN: chỉ add ở Product.tags ──
		inTx(() -> {
			M4Product managedPhone = em.find(M4Product.class, phone.getId());
			M4Tag managedTag = em.find(M4Tag.class, tagNew.getId());
			managedPhone.getTags().add(managedTag);
		});

		// ── THEN: DB thật ──
		db.print(M4 + ".product_tags");
		db.print(M4 + ".tag_products");
		assertThat(db.countOf(M4 + ".product_tags")).as("phía đã set có 1 dòng").isEqualTo(1);
		assertThat(db.countOf(M4 + ".tag_products")).as("phía chưa set có 0 dòng: hai bảng nối mâu thuẫn nhau").isZero();
	}

	/**
	 * G7-S07a — M1: xoá product (owner) đang có tag
	 *
	 * <p>Mapping: M1.
	 * <p><b>Câu hỏi</b>: xoá owner thì dòng bảng nối xử lý thế nào?
	 * <p><b>Kết luận</b>: Hibernate tự DELETE các dòng {@code product_tag} của product đó TRƯỚC, rồi DELETE product.
	 * Tag không bị đụng (không cascade).
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S07a | M1: xoá Product (owner) tự xoá dòng bảng nối rồi mới xoá product")
	void s07a_m1RemoveOwnerCleansJoinTable() {
		// ── GIVEN: phone có tag new ──
		M1Product phone = new M1Product("Phone");
		M1Tag tagNew = new M1Tag("new");
		inTx(() -> {
			em.persist(phone);
			em.persist(tagNew);
			phone.getTags().add(tagNew);
		});

		// ── WHEN: xoá phone ──
		inTx(() -> {
			M1Product managedPhone = em.find(M1Product.class, phone.getId());
			sql.reset();
			em.remove(managedPhone);
			// [FLUSH] lúc commit: DELETE dòng nối trước, rồi DELETE product
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertInOrder("delete from " + M1 + ".product_tag", "delete from " + M1 + ".product where");

		// ── THEN: DB thật ──
		assertThat(db.countOf(M1 + ".product")).as("product đã bị xoá").isZero();
		assertThat(db.countOf(M1 + ".product_tag")).as("dòng nối đã bị Hibernate xoá theo").isZero();
		assertThat(db.countOf(M1 + ".tag")).as("tag vẫn còn vì không cascade").isEqualTo(1);
	}

	/**
	 * G7-S07b — M1: xoá tag (inverse) còn liên kết với product
	 *
	 * <p>Mapping: M1.
	 * <p><b>Câu hỏi</b>: xoá phía inverse thì sao?
	 * <p><b>Kết luận</b>: vi phạm FK. Hibernate không đọc collection inverse để dọn bảng nối nên chỉ gửi DELETE tag,
	 * còn dòng {@code product_tag} đang trỏ tới tag. Rollback, DB giữ nguyên.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S07b | M1: xoá Tag (inverse) còn liên kết thì vi phạm FK của bảng nối")
	void s07b_m1RemoveInverseWithLinksViolatesFk() {
		// ── GIVEN: phone có tag new ──
		M1Product phone = new M1Product("Phone");
		M1Tag tagNew = new M1Tag("new");
		inTx(() -> {
			em.persist(phone);
			em.persist(tagNew);
			phone.getTags().add(tagNew);
		});

		// ── WHEN: xoá tag mà không gỡ khỏi product ──
		sql.reset();
		assertThatThrownBy(() -> inTx(() -> {
			M1Tag managedTag = em.find(M1Tag.class, tagNew.getId());
			em.remove(managedTag);
			// [FLUSH] lúc commit: DELETE tag → PostgreSQL từ chối vì product_tag còn trỏ tới
		}))
			.as("bảng nối còn dòng tham chiếu tag nên DB từ chối DELETE tag")
			.isInstanceOf(DataIntegrityViolationException.class);

		// ── THEN: SQL + DB thật ──
		sql.print();
		sql.assertDeletes("product_tag", 0);
		assertThat(db.countOf(M1 + ".tag")).as("rollback: tag vẫn còn").isEqualTo(1);
		assertThat(db.countOf(M1 + ".product_tag")).as("rollback: liên kết vẫn còn").isEqualTo(1);
	}

	/**
	 * G7-S07c — M1: cách sửa, gỡ tag khỏi mọi product rồi mới xoá tag
	 *
	 * <p>Mapping: M1.
	 * <p><b>Câu hỏi</b>: xoá một tag dùng chung an toàn bằng cách nào?
	 * <p><b>Kết luận</b>: duyệt {@code tag.getProducts()} (phía inverse, chỉ để đọc), với mỗi product gọi
	 * {@code product.getTags().remove(tag)} (phía owner, thứ thật sự ghi DB), rồi {@code em.remove(tag)}.
	 * Kết quả: DELETE dòng nối, DELETE tag; product còn nguyên.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S07c | M1: cách sửa: gỡ tag khỏi mọi product (phía owner) rồi mới xoá tag")
	void s07c_m1DetachTagFromProductsBeforeRemoving() {
		// ── GIVEN: phone và laptop cùng có tag new ──
		M1Product phone = new M1Product("Phone");
		M1Product laptop = new M1Product("Laptop");
		M1Tag tagNew = new M1Tag("new");
		inTx(() -> {
			em.persist(phone);
			em.persist(laptop);
			em.persist(tagNew);
			phone.getTags().add(tagNew);
			laptop.getTags().add(tagNew);
		});

		// ── WHEN: gỡ khỏi mọi product rồi xoá tag ──
		inTx(() -> {
			M1Tag managedTag = em.find(M1Tag.class, tagNew.getId());
			sql.reset();
			for (M1Product product : managedTag.getProducts()) {
				product.getTags().remove(managedTag); // owner side: đây mới là thao tác ghi DB
			}
			em.remove(managedTag);
		});

		// ── THEN: SQL + DB thật ──
		sql.print();
		sql.assertDeletes("tag", 1);
		assertThat(db.countOf(M1 + ".tag")).as("tag đã bị xoá").isZero();
		assertThat(db.countOf(M1 + ".product_tag")).as("mọi liên kết tới tag đã được gỡ trước").isZero();
		assertThat(db.countOf(M1 + ".product")).as("hai product vẫn còn").isEqualTo(2);
	}

	/**
	 * G7-S13a — M1: N+1 khi tải nhiều product rồi đọc tags
	 *
	 * <p>Mapping: M1, {@code tags} LAZY.
	 * <p><b>Câu hỏi</b>: duyệt tags của 5 product tải bằng một JPQL thì tốn bao nhiêu SELECT?
	 * <p><b>Kết luận</b>: 1 SELECT product + 5 SELECT bảng nối (mỗi product một lần): N+1, giống G5-S05.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S13a | M1: tải 5 product rồi đọc tags gây N+1 SELECT")
	void s13a_m1LoadingTagsOfFiveProductsIsNPlusOne() {
		// ── GIVEN: 5 product, mỗi product có 1 tag ──
		M1Tag tagNew = new M1Tag("new");
		inTx(() -> {
			em.persist(tagNew);
			for (int i = 1; i <= 5; i++) {
				M1Product product = new M1Product("Product-" + i);
				em.persist(product);
				product.getTags().add(tagNew);
			}
		});

		// ── WHEN: JPQL rồi đọc tags từng product ──
		inTx(() -> {
			sql.reset();
			List<M1Product> products = em.createQuery("select p from G7M1_Product p", M1Product.class).getResultList();
			for (M1Product product : products) {
				product.getTags().size(); // chạm vào collection LAZY → mỗi product một SELECT
			}
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertSelects("product_tag", 5);
		sql.assertTotal(6);
	}

	/**
	 * G7-S13b — M1: sửa N+1 bằng {@code join fetch p.tags}
	 *
	 * <p>Mapping: M1.
	 * <p><b>Câu hỏi</b>: cùng dữ liệu như S13a, sửa thế nào?
	 * <p><b>Kết luận</b>: {@code join fetch} gom product, bảng nối và tag vào đúng 1 SELECT.
	 * <p>Xác minh: Hibernate 7.4.15.Final
	 */
	@Test
	@DisplayName("G7-S13b | M1: join fetch p.tags đưa N+1 về 1 SELECT")
	void s13b_m1JoinFetchFixesNPlusOne() {
		// ── GIVEN: 5 product, mỗi product có 1 tag ──
		M1Tag tagNew = new M1Tag("new");
		inTx(() -> {
			em.persist(tagNew);
			for (int i = 1; i <= 5; i++) {
				M1Product product = new M1Product("Product-" + i);
				em.persist(product);
				product.getTags().add(tagNew);
			}
		});

		// ── WHEN: join fetch ──
		inTx(() -> {
			sql.reset();
			List<M1Product> products = em.createQuery("select p from G7M1_Product p join fetch p.tags", M1Product.class)
				.getResultList();
			for (M1Product product : products) {
				product.getTags().size(); // đã tải sẵn, không còn SELECT nào
			}
		});

		// ── THEN: SQL ──
		sql.print();
		sql.assertTotal(1);
	}
}
