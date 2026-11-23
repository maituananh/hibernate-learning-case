package com.example.hibernatelab.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Đọc CẤU TRÚC bảng thật từ catalog của PostgreSQL (bảng, cột, PK, FK, unique), để assert "DDL sinh ra".
 *
 * <p>Đọc từ DB đang chạy chứ không đọc file {@code build/ddl/create.sql}: cái cần chứng minh là
 * những gì PostgreSQL thực sự có, kể cả tên cột/bảng mặc định do naming strategy của Spring Boot sinh ra.
 */
public class SchemaInspector {

	/** Một cột: tên, kiểu (ví dụ {@code varchar(255)}, {@code bigint}), có cho NULL không. */
	public record Column(String name, String type, boolean nullable) {
	}

	/** Một cột khoá ngoại: cột này trỏ tới {@code targetTable.targetColumn}, {@code onDelete} là NO ACTION / CASCADE / SET NULL / ... */
	public record ForeignKey(String constraintName, String column, String targetTable, String targetColumn,
			String onDelete) {
	}

	private final JdbcTemplate jdbc;

	public SchemaInspector(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Tên các bảng trong schema, theo thứ tự abc. */
	public List<String> tablesOf(String schema) {
		return jdbc.queryForList("select tablename from pg_tables where schemaname = ? order by tablename",
			String.class, schema);
	}

	/** Các cột của bảng theo thứ tự khai báo. */
	public List<Column> columnsOf(String schema, String table) {
		return jdbc.query("""
				select column_name,
				       data_type,
				       character_maximum_length,
				       is_nullable
				from information_schema.columns
				where table_schema = ? and table_name = ?
				order by ordinal_position
				""",
			(rs, rowNum) -> {
				String type = rs.getString("data_type");
				int length = rs.getInt("character_maximum_length");
				if (!rs.wasNull()) {
					type = ("character varying".equals(type) ? "varchar" : type) + "(" + length + ")";
				}
				return new Column(rs.getString("column_name"), type, "YES".equals(rs.getString("is_nullable")));
			},
			schema, table);
	}

	/** Bảng có cột này không? (dùng để chứng minh "cột customer_id KHÔNG tồn tại" hoặc "có tồn tại"). */
	public boolean hasColumn(String schema, String table, String columnName) {
		for (Column column : columnsOf(schema, table)) {
			if (column.name().equals(columnName)) {
				return true;
			}
		}
		return false;
	}

	/** Lấy đúng một cột theo tên; báo lỗi rõ ràng (liệt kê các cột hiện có) nếu bảng không có cột đó. */
	public Column columnNamed(String schema, String table, String columnName) {
		List<Column> columns = columnsOf(schema, table);
		for (Column column : columns) {
			if (column.name().equals(columnName)) {
				return column;
			}
		}
		throw new AssertionError("Bảng " + schema + "." + table + " không có cột \"" + columnName + "\". Các cột hiện có: " + columns);
	}

	/** Lấy khoá ngoại nằm trên cột này; báo lỗi rõ ràng nếu cột không phải là FK. */
	public ForeignKey foreignKeyOnColumn(String schema, String table, String columnName) {
		List<ForeignKey> foreignKeys = foreignKeysOf(schema, table);
		for (ForeignKey foreignKey : foreignKeys) {
			if (foreignKey.column().equals(columnName)) {
				return foreignKey;
			}
		}
		throw new AssertionError("Bảng " + schema + "." + table + " không có FK nào trên cột \"" + columnName + "\". Các FK hiện có: " + foreignKeys);
	}

	/** Tên các cột tạo nên primary key (rỗng nếu bảng không có PK). */
	public List<String> primaryKeyOf(String schema, String table) {
		return jdbc.queryForList("""
				select a.attname
				from pg_index ix
				join pg_class t on t.oid = ix.indrelid
				join pg_namespace n on n.oid = t.relnamespace
				cross join lateral unnest(ix.indkey) with ordinality as k(attnum, ord)
				join pg_attribute a on a.attrelid = t.oid and a.attnum = k.attnum
				where ix.indisprimary and n.nspname = ? and t.relname = ?
				order by k.ord
				""",
			String.class, schema, table);
	}

	/** Các khoá ngoại của bảng (mỗi cột của FK nhiều cột là một phần tử). */
	public List<ForeignKey> foreignKeysOf(String schema, String table) {
		return jdbc.query("""
				select con.conname,
				       att.attname  as column_name,
				       tn.nspname || '.' || tc.relname as target_table,
				       ratt.attname as target_column,
				       con.confdeltype
				from pg_constraint con
				join pg_class c on c.oid = con.conrelid
				join pg_namespace n on n.oid = c.relnamespace
				join pg_class tc on tc.oid = con.confrelid
				join pg_namespace tn on tn.oid = tc.relnamespace
				cross join lateral unnest(con.conkey, con.confkey) with ordinality as k(attnum, refattnum, ord)
				join pg_attribute att on att.attrelid = con.conrelid and att.attnum = k.attnum
				join pg_attribute ratt on ratt.attrelid = con.confrelid and ratt.attnum = k.refattnum
				where con.contype = 'f' and n.nspname = ? and c.relname = ?
				order by con.conname, k.ord
				""",
			(rs, rowNum) -> new ForeignKey(rs.getString("conname"), rs.getString("column_name"),
				rs.getString("target_table"), rs.getString("target_column"),
				onDeleteText(rs.getString("confdeltype"))),
			schema, table);
	}

	/**
	 * Các ràng buộc unique (không tính PK). Mỗi phần tử là danh sách cột của một unique,
	 * ví dụ {@code [["email"], ["a","b"]]}. Gồm cả unique index (không phân biệt constraint hay index).
	 */
	public List<List<String>> uniqueConstraintsOf(String schema, String table) {
		List<String> columnLists = jdbc.queryForList("""
				select string_agg(a.attname, ',' order by k.ord)
				from pg_index ix
				join pg_class t on t.oid = ix.indrelid
				join pg_namespace n on n.oid = t.relnamespace
				cross join lateral unnest(ix.indkey) with ordinality as k(attnum, ord)
				join pg_attribute a on a.attrelid = t.oid and a.attnum = k.attnum
				where ix.indisunique and not ix.indisprimary and n.nspname = ? and t.relname = ?
				group by ix.indexrelid
				order by 1
				""",
			String.class, schema, table);
		List<List<String>> result = new ArrayList<>();
		for (String columnList : columnLists) {
			result.add(Arrays.asList(columnList.split(",")));
		}
		return result;
	}

	/** In ra console cấu trúc mọi bảng của schema, để người học nhìn thấy DDL thật. */
	public void printDdl(String schema) {
		System.out.println("[DDL] schema " + schema);
		for (String table : tablesOf(schema)) {
			System.out.println("  bảng " + schema + "." + table);
			List<String> primaryKey = primaryKeyOf(schema, table);
			for (Column column : columnsOf(schema, table)) {
				String flags = (primaryKey.contains(column.name()) ? " PK" : "")
					+ (column.nullable() ? "" : " NOT NULL");
				System.out.println("    - " + column.name() + " " + column.type() + flags);
			}
			for (ForeignKey foreignKey : foreignKeysOf(schema, table)) {
				System.out.println("    FK " + foreignKey.column() + " → " + foreignKey.targetTable() + "("
					+ foreignKey.targetColumn() + ") ON DELETE " + foreignKey.onDelete());
			}
			for (List<String> unique : uniqueConstraintsOf(schema, table)) {
				System.out.println("    UNIQUE " + unique);
			}
		}
	}

	private String onDeleteText(String code) {
		return switch (code) {
			case "a" -> "NO ACTION";
			case "r" -> "RESTRICT";
			case "c" -> "CASCADE";
			case "n" -> "SET NULL";
			case "d" -> "SET DEFAULT";
			default -> code;
		};
	}
}
