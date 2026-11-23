package com.example.hibernatelab.g9_integrated.s01_category_tree;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Danh mục sản phẩm dạng CÂY: một bảng tự tham chiếu (self-reference), nhưng có nhiều tầng dữ liệu.
 *
 * BIẾN THỂ : G9-S01 (g9_s01_category_tree)
 * BASELINE : mapping hai chiều chuẩn của G1-V4 (ManyToOne LAZY + OneToMany mappedBy) và G3 (orphanRemoval)
 * KHÁC BIỆT: cha và con là CÙNG MỘT entity (category.parent_id → category.id)
 * HẬU QUẢ  : thứ tự INSERT/DELETE theo tầng; duyệt cây bằng collection LAZY gây N+1 theo từng node (G9-S01)
 */
@Entity(name = "G9S01_Category") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s01_category_tree", name = "category")
public class Category {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s01_category_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g9s01_category_seq", schema = "g9_s01_category_tree", sequenceName = "category_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// OWNING side — FK category.parent_id nằm ở CHÍNH bảng category. Node gốc có parent_id = NULL nên không đặt nullable = false.
	// fetch = LAZY được ghi tường minh: @ManyToOne mặc định là EAGER, sẽ kéo cả chuỗi cha lên mỗi lần load.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private Category parent;

	// INVERSE side — mappedBy = "parent": FK do field `parent` ở trên quản lý, collection này chỉ để duyệt/cascade.
	// cascade = ALL: persist/remove node cha lan xuống toàn bộ nhánh con. orphanRemoval = true: gỡ con khỏi list là xoá dòng.
	@OneToMany(mappedBy = "parent", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Category> children = new ArrayList<>();

	protected Category() {
		// JPA bắt buộc có constructor không tham số
	}

	public Category(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Category getParent() {
		return parent;
	}

	/** Chỉ đổi owning side (FK). Dùng cho cách chuyển nhánh "chỉ 1 UPDATE" ở G9-S01b. */
	public void setParent(Category parent) {
		this.parent = parent;
	}

	public List<Category> getChildren() {
		return children;
	}

	/** Giữ HAI phía đồng bộ: owning (child.parent) để có FK, inverse (children) để cascade hoạt động. */
	public void addChild(Category child) {
		children.add(child);
		child.parent = this;
	}

	/** Gỡ con khỏi cha: nhờ orphanRemoval, dòng của con (và cả nhánh dưới nó) sẽ bị DELETE lúc flush. */
	public void removeChild(Category child) {
		children.remove(child);
		child.parent = null;
	}
}
